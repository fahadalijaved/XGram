/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 11/04/2017
 */
package org.thunderdog.challegram.mediaview.paint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Base64;

import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import com.fahadali.xgram.editor.XgramCutout;
import com.fahadali.xgram.editor.XgramLayer;
import com.fahadali.xgram.editor.XgramPaintBase;
import com.fahadali.xgram.editor.XgramStickerLayer;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import me.vkryl.core.FileUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.reference.ReferenceUtils;
import me.vkryl.core.util.Blob;

public class PaintState {
  public interface SimpleDrawingChangeListener {
    void onSimpleDrawingsChanged (PaintState state);
  }

  public interface UndoStateListener {
    void onUndoAvailableStateChanged (PaintState state, boolean isAvailable, int totalActionsCount);
  }

  private List<SimpleDrawing> drawingsList;
  private List<PaintAction> paintActions;
  // XGram: the undone actions, newest last; a new action clears them
  private List<PaintAction> redoActions;
  // XGram: the movable layers (text, emoji, stickers), drawn over the drawings, the last on top
  private List<XgramLayer> layers;
  // XGram: what the Cut out tool removed of the photo (com.fahadali.xgram.editor.XgramCutout), drawn under the drawings
  private @Nullable XgramCutout cutout;

  private List<Reference<SimpleDrawingChangeListener>> simpleDrawingListeners;
  private List<Reference<UndoStateListener>> undoStateListeners;

  public PaintState () { }

  public PaintState (PaintState copy) {
    if (copy.drawingsList != null && !copy.drawingsList.isEmpty()) {
      this.drawingsList = new ArrayList<>(copy.drawingsList.size());
      this.drawingsList.addAll(copy.drawingsList);
    }
    // XGram: layers change in place, so the copy (what Cancel goes back to) has copies of them, and its actions point
    // at those copies; the actions are copies too, as undo notes indexes in them
    final IdentityHashMap<XgramLayer, XgramLayer> layerCopies = new IdentityHashMap<>();
    if (copy.layers != null && !copy.layers.isEmpty()) {
      this.layers = new ArrayList<>(copy.layers.size());
      for (XgramLayer layer : copy.layers) {
        XgramLayer layerCopy = layer.copy();
        layerCopy.attach(this);
        layerCopies.put(layer, layerCopy);
        this.layers.add(layerCopy);
      }
    }
    if (copy.paintActions != null && !copy.paintActions.isEmpty()) {
      this.paintActions = new ArrayList<>(copy.paintActions.size());
      for (PaintAction action : copy.paintActions) {
        this.paintActions.add(copyAction(action, layerCopies));
      }
    }
    if (copy.redoActions != null && !copy.redoActions.isEmpty()) { // XGram
      this.redoActions = new ArrayList<>(copy.redoActions.size());
      for (PaintAction action : copy.redoActions) {
        this.redoActions.add(copyAction(action, layerCopies));
      }
    }
    // XGram: the cut-out's mask changes in place, so the copy has its own (its edits are shared: they never change)
    if (copy.cutout != null) {
      this.cutout = copy.cutout.copy();
    }
  }

  /** XGram: {@code layer}'s copy in a copied state (made here for a layer no longer in it, kept by undo). */
  private static XgramLayer copyOf (XgramLayer layer, IdentityHashMap<XgramLayer, XgramLayer> copies) {
    XgramLayer copy = copies.get(layer);
    if (copy == null) {
      copy = layer.copy();
      copies.put(layer, copy);
    }
    return copy;
  }

  private static PaintAction copyAction (PaintAction action, IdentityHashMap<XgramLayer, XgramLayer> copies) {
    Object data = action.getData();
    switch (action.getType()) {
      case PaintAction.LAYER_ADD:
      case PaintAction.LAYER_REMOVE:
      case PaintAction.LAYER_CHANGE: {
        PaintAction.LayerData layerData = (PaintAction.LayerData) data;
        data = new PaintAction.LayerData(copyOf(layerData.layer, copies), layerData.before, layerData.after);
        break;
      }
      case PaintAction.CLEAR: {
        PaintAction.ClearData clearData = (PaintAction.ClearData) data;
        List<XgramLayer> layers = new ArrayList<>(clearData.layers.size());
        for (XgramLayer layer : clearData.layers) {
          layers.add(copyOf(layer, copies));
        }
        data = new PaintAction.ClearData(clearData.drawings, layers, clearData.cutout);
        break;
      }
    }
    return action.copy(data);
  }

  // TODO save & restore paint

  private static SparseArrayCompat<PaintState> pendingPaints;

  public static void putPaintState (int paintId, PaintState paintState) {
    synchronized (PaintState.class) {
      if (pendingPaints == null) {
        pendingPaints = new SparseArrayCompat<>();
      }
      pendingPaints.put(paintId, paintState);
    }
  }

  public static void forgetPaintState (int paintId) {
    synchronized (PaintState.class) {
      if (pendingPaints != null) {
        pendingPaints.remove(paintId);
      }
    }
  }

  public static PaintState obtainPaintState (int paintId) {
    synchronized (PaintState.class) {
      return pendingPaints != null ? pendingPaints.get(paintId) : null;
    }
  }

  public static File getPaintsDir () {
    return new File(AppContext.get().getFilesDir(), "paints");
  }

  public static PaintState parse (String in) {
    if (StringUtils.isEmpty(in)) {
      return null;
    }
    byte[] data;
    try {
      data = Base64.decode(in, Base64.NO_PADDING);
    } catch (Throwable t) {
      Log.e("Unable to decode painting", t);
      return null;
    }
    if (data == null || data.length == 0) {
      Log.w("Decoded painting is null");
      return null;
    }
    try {
      Blob blob = new Blob(data);
      int paintId = blob.readVarint();
      if (paintId != 0) {
        PaintState cachedState = obtainPaintState(paintId);
        if (cachedState == null) {
          File dir = getPaintsDir();
          if (dir.exists()) {
            File file = new File(dir, paintId + ".bin");
            if (file.exists()) {
              PaintState state;
              RandomAccessFile f = new RandomAccessFile(file, "r");
              try {
                int count = Blob.readVarint(f);
                final ArrayList<SimpleDrawing> drawings = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                  SimpleDrawing drawing = SimpleDrawing.restore(f);
                  drawings.add(drawing);
                }
                state = new PaintState();
                state.drawingsList = drawings;
                state.setLayers(readLayers(f)); // XGram
              } catch (Throwable t) {
                try { f.close(); } catch (Throwable ignored) { }
                Log.w("Unable to read paint file", t);
                throw t;
              }
              try { f.close(); } catch (Throwable ignored) { }
              return state;
            }
          }
        }
        if (cachedState == null) {
          throw new IllegalStateException("Unable to find paints/" + paintId + ".bin");
        }
        return cachedState;
      }

      int count = blob.readVarint();
      if (count < 0) { // XGram: no drawings is fine with layers after them
        Log.w("Decoded painting count is empty: %d", count);
        return null;
      }
      final ArrayList<SimpleDrawing> drawings = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        SimpleDrawing drawing = SimpleDrawing.restore(blob);
        drawings.add(drawing);
      }
      PaintState state = new PaintState();
      state.drawingsList = drawings;
      state.setLayers(readLayers(blob)); // XGram
      if (state.isEmpty()) {
        Log.w("Decoded painting is empty");
        return null;
      }
      return state;
    } catch (Throwable t) {
      Log.e("Unable to decode painting: %s", t, in);
    }
    return null;
  }

  public byte[] save () {
    // XGram: a state can have layers and no drawings; the layers go after the drawings (saveLayers)
    final List<SimpleDrawing> drawingsList = this.drawingsList != null ? new ArrayList<>(this.drawingsList) : Collections.emptyList();
    final byte[] layersData = saveLayers();
    int size = Blob.sizeOf(drawingsList.size());
    for (SimpleDrawing drawing : drawingsList) {
      size += drawing.getEstimatedOutputSize();
    }
    if (layersData != null) {
      size += layersData.length;
    }

    Blob b = null;

    if (size >= 256) {
      int paintId = Settings.instance().getPaintId();
      File paintCacheDirectory = new File(AppContext.get().getFilesDir(), "paints");
      if (FileUtils.createDirectory(paintCacheDirectory)) {
        File file;
        do {
          paintId++;
          Settings.instance().setPaintId(paintId);
          file = new File(paintCacheDirectory, paintId + ".bin");
        } while (file.exists());

        b = new Blob(Blob.sizeOf(paintId));
        b.writeVarint(paintId);

        putPaintState(paintId, this);
        final int paintIdFinal = paintId;
        final File fileFinal = file;
        final int sizeFinal = size;

        boolean ok = false;
        try {
          ok = file.createNewFile();
        } catch (Throwable t) {
          Log.w("Cannot create %d.bin paint file", t, paintId);
        }
        if (ok) {
          Background.instance().post(() -> {
            RandomAccessFile f = null;
            try {
              f = new RandomAccessFile(fileFinal, "rw");
              f.setLength(sizeFinal);
              Blob.writeVarint(f, drawingsList.size());
              for (SimpleDrawing drawing : drawingsList) {
                drawing.save(f);
              }
              if (layersData != null) { // XGram
                f.write(layersData);
              }
              f.setLength(f.getFilePointer()); // XGram: nothing after it (the size was an estimate)
              forgetPaintState(paintIdFinal);
            } catch (Throwable t) {
              Log.w("Cannot save paint file: %s", t, fileFinal.getName());
            }
            if (f != null) { try { f.close(); } catch (Throwable t) { Log.w(t); } }
          });
        }
      }
    }

    if (b != null) {
      return b.toByteArray();
    }

    b = new Blob(size);
    b.writeVarint(0);
    b.writeVarint(drawingsList.size());
    for (SimpleDrawing drawing : drawingsList) {
      drawing.save(b);
    }
    if (layersData != null) { // XGram
      b.writeRaw(layersData);
    }
    return b.toByteArray();
  }

  // XGram: the layers are saved after the drawings: a marker, a version, their count, then each one's type and data
  // (a byte array, so a type this version doesn't know is skipped). Telegram X stops reading after the drawings.

  private static final byte LAYERS_MARKER = 0x6c;
  private static final int LAYERS_VERSION = 1;
  private static final int MAX_LAYERS = 256;

  @Nullable
  private byte[] saveLayers () {
    final List<XgramLayer> layers = this.layers != null ? new ArrayList<>(this.layers) : null;
    if (layers == null || layers.isEmpty()) {
      return null;
    }
    Blob b = new Blob(64);
    b.writeByte(LAYERS_MARKER);
    b.writeVarint(LAYERS_VERSION);
    b.writeVarint(layers.size());
    for (XgramLayer layer : layers) {
      Blob data = new Blob(48);
      layer.save(data);
      b.writeByte((byte) layer.getType());
      b.writeByteArray(data.toByteArray());
    }
    return b.toByteArray();
  }

  @Nullable
  private static List<XgramLayer> readLayers (Blob blob) {
    try {
      if (blob.remaining() <= 0 || blob.readByte() != LAYERS_MARKER) {
        return null;
      }
      blob.readVarint(); // the version: later ones keep this layout
      final int count = blob.readVarint();
      if (count <= 0 || count > MAX_LAYERS) {
        return null;
      }
      List<XgramLayer> layers = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        final int type = blob.readByte();
        final byte[] data = blob.readByteArray();
        XgramLayer layer = XgramLayer.restore(type, data);
        if (layer != null) {
          layers.add(layer);
        }
      }
      return layers;
    } catch (Throwable t) {
      Log.w("Unable to read photo editor layers", t);
      return null;
    }
  }

  @Nullable
  private static List<XgramLayer> readLayers (RandomAccessFile f) {
    try {
      if (f.getFilePointer() >= f.length() || f.read() != LAYERS_MARKER) {
        return null;
      }
      Blob.readVarint(f); // the version
      final int count = Blob.readVarint(f);
      if (count <= 0 || count > MAX_LAYERS) {
        return null;
      }
      List<XgramLayer> layers = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        final int type = f.read();
        final byte[] data = Blob.readByteArray(f);
        XgramLayer layer = XgramLayer.restore(type, data);
        if (layer != null) {
          layers.add(layer);
        }
      }
      return layers;
    } catch (Throwable t) {
      Log.w("Unable to read photo editor layers", t);
      return null;
    }
  }

  private void setLayers (@Nullable List<XgramLayer> layers) {
    this.layers = layers != null && !layers.isEmpty() ? layers : null;
    if (this.layers != null) {
      for (XgramLayer layer : this.layers) {
        layer.attach(this);
      }
    }
  }

  public String saveAndSerializeToString () {
    if (isEmpty()) {
      return "";
    }
    byte[] data = save();
    return Base64.encodeToString(data, Base64.NO_PADDING);
  }

  // Listenrs

  public void addUndoStateListener (UndoStateListener listener) {
    if (this.undoStateListeners == null) {
      this.undoStateListeners = new ArrayList<>();
    }
    ReferenceUtils.addReference(undoStateListeners, listener);
  }

  public void removeUndoStateListener (UndoStateListener listener) {
    if (undoStateListeners != null) {
      ReferenceUtils.removeReference(undoStateListeners, listener);
    }
  }

  public void notifyUndoStateChanged () {
    if (undoStateListeners != null) {
      final int count = paintActions != null ? paintActions.size() : 0;
      final int size = undoStateListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        UndoStateListener listener = undoStateListeners.get(i).get();
        if (listener != null) {
          listener.onUndoAvailableStateChanged(this, count > 0, count);
        } else {
          undoStateListeners.remove(i);
        }
      }
    }
  }

  public void addSimpleDrawingChangeListener (SimpleDrawingChangeListener listener) {
    if (this.simpleDrawingListeners == null) {
      this.simpleDrawingListeners = new ArrayList<>();
    }
    ReferenceUtils.addReference(simpleDrawingListeners, listener);
  }

  public void removeSimpleDrawingChangeListener (SimpleDrawingChangeListener listener) {
    if (simpleDrawingListeners != null) {
      ReferenceUtils.removeReference(simpleDrawingListeners, listener);
    }
  }

  public void notifySimpleDrawingChanged () {
    notifySimpleDrawingChanged(-1, -1, -1, -1);
  }

  public void notifySimpleDrawingChanged (int left, int top, int right, int bottom) {
    if (simpleDrawingListeners != null) {
      final int size = simpleDrawingListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        SimpleDrawingChangeListener listener = simpleDrawingListeners.get(i).get();
        if (listener != null) {
          listener.onSimpleDrawingsChanged(this);
        } else {
          simpleDrawingListeners.remove(i);
        }
      }
    }
  }

  private void trackAction (PaintAction action) {
    if (paintActions == null) {
      paintActions = new ArrayList<>();
    }
    paintActions.add(action);
    if (redoActions != null) { // XGram: a new action ends the redo history
      redoActions.clear();
    }
    notifyUndoStateChanged();
  }

  private PaintAction removeLastAction () {
    if (paintActions != null && !paintActions.isEmpty()) {
      return paintActions.remove(paintActions.size() - 1);
    }
    return null;
  }

  // XGram: undo and redo go by the action (Telegram X removed the last drawing whatever the action was), redo is new,
  // and Clear is one action that can be undone (Telegram X's Clear dropped the history)

  public boolean canUndo () {
    return paintActions != null && !paintActions.isEmpty();
  }

  public boolean canRedo () {
    return redoActions != null && !redoActions.isEmpty();
  }

  /** Removes every drawing and layer (and brings back what the cut-out removed) as one action. False when there is nothing to remove. */
  public boolean clearAll () {
    if (isEmpty()) {
      return false;
    }
    List<SimpleDrawing> removed = drawingsList != null ? new ArrayList<>(drawingsList) : new ArrayList<>();
    List<XgramLayer> removedLayers = layers != null ? new ArrayList<>(layers) : new ArrayList<>();
    if (drawingsList != null) {
      drawingsList.clear();
    }
    if (layers != null) {
      layers.clear();
    }
    final XgramCutout.Edit cutoutEdit = cutout != null ? cutout.clear() : null; // XGram
    notifySimpleDrawingChanged();
    trackAction(new PaintAction(PaintAction.CLEAR, new PaintAction.ClearData(removed, removedLayers, cutoutEdit)));
    return true;
  }

  // XGram: the cut-out (the Cut out tool, com.fahadali.xgram.editor.XgramCutoutEditor). Made at its first change, in the
  // grid of the photo it was made on; each change is an action. It is drawn under the drawings where the photo is: a
  // checkerboard where it is removed (the editor, the viewer, thumbnails), never into the sent photo by itself (draw's
  // cutoutMode)

  public static final int CUTOUT_PREVIEW = 0, CUTOUT_APPLY = 1, CUTOUT_NONE = 2;

  @Nullable
  public XgramCutout getCutout () {
    return cutout;
  }

  /** XGram: whether the cut-out removes any of the photo. */
  public boolean hasCutout () {
    return cutout != null && cutout.hasRemoved();
  }

  /** XGram: the cut-out, made (nothing removed) for a photo bitmap of photoWidth x photoHeight if there is none. */
  public XgramCutout obtainCutout (int photoWidth, int photoHeight, boolean soft) {
    if (cutout == null) {
      cutout = XgramCutout.create(photoWidth, photoHeight, soft);
    }
    return cutout;
  }

  /** XGram: whether the newest action (the one Undo reverts) is the one with {@code data}. */
  public boolean isLastAction (@Nullable Object data) {
    return data != null && paintActions != null && !paintActions.isEmpty() && paintActions.get(paintActions.size() - 1).getData() == data;
  }

    /** XGram: records a change of the cut-out (null: nothing changed) as an action. */
  public void trackCutoutEdit (@Nullable XgramCutout.Edit edit) {
    if (edit != null) {
      trackAction(new PaintAction(PaintAction.CUTOUT, edit));
    }
  }

  // XGram: the movable layers (text, emoji, stickers)

  public boolean hasLayers () {
    return layers != null && !layers.isEmpty();
  }

  // XGram: the animated layers (GIFs, animated stickers) play while a view showing the drawing plays them
  // (SimpleDrawingView, while it is on screen), and stop, their decoders freed, once none does

  private @Nullable List<Object> animationPlayers;
  private @Nullable List<XgramStickerLayer> playingLayers;

  /** XGram: whether a layer is a GIF or an animated sticker. */
  public boolean hasAnimatedLayers () {
    final List<XgramLayer> layers = this.layers;
    if (layers != null) {
      for (int i = 0; i < layers.size(); i++) {
        final XgramLayer layer;
        try {
          layer = layers.get(i);
        } catch (IndexOutOfBoundsException ignored) {
          break;
        }
        if (layer instanceof XgramStickerLayer && ((XgramStickerLayer) layer).isAnimated()) {
          return true;
        }
      }
    }
    return false;
  }

  /** XGram, main thread: {@code player} (a view showing the drawing) starts or stops playing its animated layers. */
  public void setAnimationPlayer (Object player, boolean isPlaying) {
    if (isPlaying) {
      if (animationPlayers == null) {
        animationPlayers = new ArrayList<>();
      }
      if (!animationPlayers.contains(player)) {
        animationPlayers.add(player);
      }
    } else if (animationPlayers != null && animationPlayers.remove(player) && animationPlayers.isEmpty()) {
      stopAnimations();
    }
  }

  /**
   * XGram, main thread: moves the animated layers on to now (the drawing is drawn again when a frame changed), and
   * stops those no longer in it. Milliseconds until it is needed again, or -1 when nothing plays.
   */
  public long tickAnimations () {
    if (animationPlayers == null || animationPlayers.isEmpty()) {
      stopAnimations();
      return -1;
    }
    final long now = SystemClock.uptimeMillis();
    long delay = -1;
    boolean changed = false;
    List<XgramStickerLayer> playing = null;
    final List<XgramLayer> layers = this.layers;
    if (layers != null) {
      for (int i = 0; i < layers.size(); i++) {
        final XgramLayer layer = layers.get(i);
        if (!(layer instanceof XgramStickerLayer)) {
          continue;
        }
        final XgramStickerLayer sticker = (XgramStickerLayer) layer;
        if (!sticker.isAnimated()) {
          continue;
        }
        if (sticker.updateAnimation(now)) {
          changed = true;
        }
        if (!sticker.isAnimated()) {
          continue;
        }
        // still playing, or found still or unreadable by its decoder just now: the next tick (or stopAnimations) stops it
        final long layerDelay = sticker.getAnimationDelay(now);
        final long next = layerDelay >= 0 ? layerDelay : 10;
        delay = delay < 0 ? next : Math.min(delay, next);
        if (playing == null) {
          playing = new ArrayList<>();
        }
        playing.add(sticker);
      }
    }
    // the layers deleted, undone or cleared since, or found to be still, free their decoders
    final List<XgramStickerLayer> wasPlaying = this.playingLayers;
    this.playingLayers = playing;
    if (wasPlaying != null) {
      for (XgramStickerLayer layer : wasPlaying) {
        if (playing == null || !playing.contains(layer)) {
          layer.stopAnimation();
        }
      }
    }
    if (changed) {
      notifySimpleDrawingChanged();
    }
    return delay;
  }

  private void stopAnimations () {
    final List<XgramStickerLayer> playing = this.playingLayers;
    this.playingLayers = null;
    if (playing != null) {
      for (XgramStickerLayer layer : playing) {
        layer.stopAnimation();
      }
    }
  }

  // XGram: the GIF a sent edit with animated layers becomes (XgramAnimatedEditExport) bakes the drawings and the layers
  // under the lowest animated one into a still once, from a copy drawing only those (copyForAnimatedExport), and draws
  // copies of the others (copyLayersFrom) over it on each frame

  /** XGram: a copy for the GIF of an edit draws only the layers below this index; -1 draws them all. */
  private int drawLayersBelow = -1;

  /** XGram: the index of the lowest animated layer (a GIF, an animated sticker), or -1. */
  public int getFirstAnimatedLayerIndex () {
    final List<XgramLayer> layers = this.layers;
    if (layers != null) {
      for (int i = 0; i < layers.size(); i++) {
        final XgramLayer layer;
        try {
          layer = layers.get(i);
        } catch (IndexOutOfBoundsException ignored) {
          break;
        }
        if (layer instanceof XgramStickerLayer && ((XgramStickerLayer) layer).isAnimated()) {
          return i;
        }
      }
    }
    return -1;
  }

  /** XGram: copies of the layers from {@code fromIndex} up, the lowest first (the GIF of an edit draws them). */
  public List<XgramLayer> copyLayersFrom (int fromIndex) {
    final List<XgramLayer> layers = this.layers;
    final List<XgramLayer> copies = new ArrayList<>();
    if (layers != null) {
      for (int i = Math.max(0, fromIndex); i < layers.size(); i++) {
        final XgramLayer layer;
        try {
          layer = layers.get(i);
        } catch (IndexOutOfBoundsException ignored) {
          break;
        }
        copies.add(layer.copy());
      }
    }
    return copies;
  }

  /**
   * XGram: a copy (with copies of its layers, and no undo) that draws the drawings and only the layers below
   * {@code drawLayersBelow}: the still part of the GIF of an edit. It keeps the others, so it is as edited (isEmpty).
   */
  public PaintState copyForAnimatedExport (int drawLayersBelow) {
    final PaintState copy = new PaintState();
    final List<SimpleDrawing> drawings = this.drawingsList;
    if (drawings != null && !drawings.isEmpty()) {
      copy.drawingsList = new ArrayList<>(drawings);
    }
    final List<XgramLayer> layers = this.layers;
    if (layers != null && !layers.isEmpty()) {
      final List<XgramLayer> copies = new ArrayList<>(layers.size());
      for (int i = 0; i < layers.size(); i++) {
        final XgramLayer layer;
        try {
          layer = layers.get(i);
        } catch (IndexOutOfBoundsException ignored) {
          break;
        }
        copies.add(layer.copy());
      }
      copy.setLayers(copies);
    }
    copy.drawLayersBelow = Math.max(0, drawLayersBelow);
    return copy;
  }

  /** The topmost layer at (x, y) in a view of width x height, within {@code tolerance} pixels, or null. */
  @Nullable
  public XgramLayer findLayer (float x, float y, int width, int height, float tolerance) {
    if (layers != null) {
      for (int i = layers.size() - 1; i >= 0; i--) {
        XgramLayer layer = layers.get(i);
        if (layer.hitTest(x, y, width, height, tolerance)) {
          return layer;
        }
      }
    }
    return null;
  }

  /** Adds {@code layer} on top, as an action. */
  public void addLayer (XgramLayer layer) {
    insertLayer(layer, -1);
    notifySimpleDrawingChanged();
    trackAction(new PaintAction(PaintAction.LAYER_ADD, new PaintAction.LayerData(layer, null, null)));
  }

  /** Deletes {@code layer}, as an action that puts it back at its place. */
  public void removeLayer (XgramLayer layer) {
    final int index = layers != null ? layers.indexOf(layer) : -1;
    if (index == -1) {
      return;
    }
    layers.remove(index);
    notifySimpleDrawingChanged();
    PaintAction action = new PaintAction(PaintAction.LAYER_REMOVE, new PaintAction.LayerData(layer, null, null));
    action.setIndex(index);
    trackAction(action);
  }

  /**
   * Records that {@code layer} was moved, scaled, turned or edited from {@code before} (a copy made before the change),
   * unless nothing changed.
   */
  public void trackLayerChange (XgramLayer layer, XgramLayer before) {
    XgramLayer after = layer.copy();
    if (!before.compare(after)) {
      trackAction(new PaintAction(PaintAction.LAYER_CHANGE, new PaintAction.LayerData(layer, before, after)));
    }
    notifySimpleDrawingChanged();
  }

  private void insertLayer (XgramLayer layer, int index) {
    if (layers == null) {
      layers = new ArrayList<>();
    }
    layer.attach(this);
    if (index < 0 || index > layers.size()) {
      layers.add(layer);
    } else {
      layers.add(index, layer);
    }
  }

  /** Records the drawings one eraser gesture removed (see {@link #eraseSimpleDrawing}) as one action. */
  public void trackEraseAction (PaintAction.EraseData data) {
    if (data != null && !data.isEmpty()) {
      trackAction(new PaintAction(PaintAction.ERASE, data));
    }
  }

  /** Puts back what an eraser gesture removed without recording it (a pinch began the gesture, not the user). */
  public void restoreErased (PaintAction.EraseData data) {
    if (data != null && !data.isEmpty()) {
      for (int i = data.drawings.size() - 1; i >= 0; i--) {
        insertSimpleDrawing(data.drawings.get(i), data.indexes.get(i));
      }
      notifySimpleDrawingChanged();
    }
  }

  /** Removes {@code drawing} for the eraser, noting its place in {@code data}. */
  public boolean eraseSimpleDrawing (SimpleDrawing drawing, PaintAction.EraseData data) {
    int index = drawingsList != null ? drawingsList.indexOf(drawing) : -1;
    if (index == -1) {
      return false;
    }
    drawingsList.remove(index);
    data.add(drawing, index);
    notifySimpleDrawingChanged();
    return true;
  }

  /** The drawing the eraser is over at (x, y) in a view of width x height, the topmost first, or null. */
  @Nullable
  public SimpleDrawing findSimpleDrawing (float x, float y, int width, int height, float tolerance) {
    if (drawingsList != null) {
      for (int i = drawingsList.size() - 1; i >= 0; i--) {
        SimpleDrawing drawing = drawingsList.get(i);
        if (drawing.hitTest(x, y, width, height, tolerance)) {
          return drawing;
        }
      }
    }
    return null;
  }

  private void insertSimpleDrawing (SimpleDrawing drawing, int index) {
    if (drawingsList == null) {
      drawingsList = new ArrayList<>();
    }
    if (index < 0 || index > drawingsList.size()) {
      drawingsList.add(drawing);
    } else {
      drawingsList.add(index, drawing);
    }
  }

  @SuppressWarnings("unchecked")
  private boolean revertAction (PaintAction action) {
    switch (action.getType()) {
      case PaintAction.SIMPLE_DRAWING: {
        SimpleDrawing drawing = (SimpleDrawing) action.getData();
        int index = drawingsList != null ? drawingsList.indexOf(drawing) : -1;
        if (index != -1) {
          drawingsList.remove(index);
        }
        action.setIndex(index);
        return index != -1;
      }
      case PaintAction.ERASE: {
        PaintAction.EraseData data = (PaintAction.EraseData) action.getData();
        for (int i = data.drawings.size() - 1; i >= 0; i--) {
          insertSimpleDrawing(data.drawings.get(i), data.indexes.get(i));
        }
        return true;
      }
      case PaintAction.CLEAR: {
        PaintAction.ClearData data = (PaintAction.ClearData) action.getData();
        if (!data.drawings.isEmpty()) {
          if (drawingsList == null) {
            drawingsList = new ArrayList<>(data.drawings);
          } else {
            drawingsList.addAll(0, data.drawings);
          }
        }
        for (int i = data.layers.size() - 1; i >= 0; i--) {
          insertLayer(data.layers.get(i), 0);
        }
        if (data.cutout != null && cutout != null) { // XGram
          cutout.applyEdit(data.cutout, false);
        }
        return true;
      }
      case PaintAction.LAYER_ADD: {
        XgramLayer layer = ((PaintAction.LayerData) action.getData()).layer;
        int index = layers != null ? layers.indexOf(layer) : -1;
        if (index != -1) {
          layers.remove(index);
        }
        action.setIndex(index);
        return index != -1;
      }
      case PaintAction.LAYER_REMOVE: {
        insertLayer(((PaintAction.LayerData) action.getData()).layer, action.getIndex());
        return true;
      }
      case PaintAction.LAYER_CHANGE: {
        PaintAction.LayerData data = (PaintAction.LayerData) action.getData();
        if (data.before != null) {
          data.layer.set(data.before);
        }
        return true;
      }
      case PaintAction.CUTOUT: { // XGram
        return cutout != null && cutout.applyEdit((XgramCutout.Edit) action.getData(), false);
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private boolean applyAction (PaintAction action) {
    switch (action.getType()) {
      case PaintAction.SIMPLE_DRAWING: {
        insertSimpleDrawing((SimpleDrawing) action.getData(), action.getIndex());
        return true;
      }
      case PaintAction.ERASE: {
        PaintAction.EraseData data = (PaintAction.EraseData) action.getData();
        return drawingsList != null && drawingsList.removeAll(data.drawings);
      }
      case PaintAction.CLEAR: {
        PaintAction.ClearData data = (PaintAction.ClearData) action.getData();
        boolean changed = drawingsList != null && drawingsList.removeAll(data.drawings);
        if (layers != null && layers.removeAll(data.layers)) {
          changed = true;
        }
        if (data.cutout != null && cutout != null && cutout.applyEdit(data.cutout, true)) { // XGram
          changed = true;
        }
        return changed;
      }
      case PaintAction.LAYER_ADD: {
        insertLayer(((PaintAction.LayerData) action.getData()).layer, action.getIndex());
        return true;
      }
      case PaintAction.LAYER_REMOVE: {
        XgramLayer layer = ((PaintAction.LayerData) action.getData()).layer;
        int index = layers != null ? layers.indexOf(layer) : -1;
        if (index != -1) {
          layers.remove(index);
          action.setIndex(index);
        }
        return index != -1;
      }
      case PaintAction.LAYER_CHANGE: {
        PaintAction.LayerData data = (PaintAction.LayerData) action.getData();
        if (data.after != null) {
          data.layer.set(data.after);
        }
        return true;
      }
      case PaintAction.CUTOUT: { // XGram
        return cutout != null && cutout.applyEdit((XgramCutout.Edit) action.getData(), true);
      }
    }
    return false;
  }

  public void redoLastAction () {
    if (!canRedo()) {
      return;
    }
    PaintAction action = redoActions.remove(redoActions.size() - 1);
    boolean changed = applyAction(action);
    if (paintActions == null) {
      paintActions = new ArrayList<>();
    }
    paintActions.add(action);
    if (changed) {
      notifySimpleDrawingChanged();
    }
    notifyUndoStateChanged();
  }

  public void undoAllActions () {
    if (paintActions == null || paintActions.isEmpty()) {
      return;
    }
    // XGram: reverts every action, newest first (Telegram X removed one drawing from the end per action)
    boolean changed = false;
    for (int i = paintActions.size() - 1; i >= 0; i--) {
      if (revertAction(paintActions.remove(i))) {
        changed = true;
      }
    }
    if (redoActions != null) {
      redoActions.clear();
    }

    if (changed) {
      notifySimpleDrawingChanged();
    }

    notifyUndoStateChanged();
  }

  public void removeSimpleDrawing (SimpleDrawing drawing) {
    if (drawingsList != null && !drawingsList.isEmpty()) { // XGram: a drawing can have only layers (isEmpty)
      if (drawingsList.remove(drawing)) {
        notifySimpleDrawingChanged();
      }
    }
  }

  private void removeLastSimpleDrawing () {
    if (drawingsList != null && !drawingsList.isEmpty()) { // XGram: a drawing can have only layers (isEmpty)
      drawingsList.remove(drawingsList.size() - 1);
      notifySimpleDrawingChanged();
    }
  }

  public void undoLastAction () {
    PaintAction action = removeLastAction();
    if (action != null) {
      // XGram: reverts that action, which redo can bring back (Telegram X removed the last drawing)
      if (revertAction(action)) {
        notifySimpleDrawingChanged();
      }
      if (redoActions == null) {
        redoActions = new ArrayList<>();
      }
      redoActions.add(action);
      notifyUndoStateChanged();
    }
  }

  // Simple drawings

  public void addSimpleDrawing (SimpleDrawing drawing) {
    if (drawingsList == null) {
      drawingsList = new ArrayList<>();
    }
    drawingsList.add(drawing);
    notifySimpleDrawingChanged();
  }

  public void trackSimpleDrawingAction (SimpleDrawing drawing) {
    trackAction(new PaintAction(PaintAction.SIMPLE_DRAWING, drawing));
  }

  // Impl

  private static boolean compareDrawings (List<SimpleDrawing> a, List<SimpleDrawing> b) {
    boolean aEmpty = a == null || a.isEmpty();
    boolean bEmpty = b == null || b.isEmpty();

    if (aEmpty != bEmpty) {
      return false;
    }

    if (aEmpty) {
      return true;
    }

    final int size = a.size();
    if (size != b.size()) {
      return false;
    }

    int i = 0;
    for (SimpleDrawing aDrawing : a) {
      SimpleDrawing bDrawing = b.get(i);
      if (!aDrawing.compare(bDrawing)) {
        return false;
      }
      i++;
    }

    return true;
  }

  public boolean compare (PaintState b) {
    if (b == null) {
      return false;
    }

    if (b == this) {
      return true;
    }

    if (isEmpty() != b.isEmpty()) {
      return false;
    }

    if (isEmpty()) {
      return true;
    }

    return compareDrawings(b.drawingsList, drawingsList) && compareLayers(b.layers, layers) && compareCutouts(b.cutout, cutout); // XGram: and the layers, the cut-out
  }

  /** XGram: the same part of the photo removed, with the same edges (none removed is the same as no cut-out). */
  private static boolean compareCutouts (@Nullable XgramCutout a, @Nullable XgramCutout b) {
    final boolean aEmpty = a == null || !a.hasRemoved();
    final boolean bEmpty = b == null || !b.hasRemoved();
    if (aEmpty || bEmpty) {
      return aEmpty == bEmpty;
    }
    return a.contentEquals(b);
  }

  /** XGram: the same layers in the same order, at the same places. */
  private static boolean compareLayers (List<XgramLayer> a, List<XgramLayer> b) {
    final int aSize = a != null ? a.size() : 0;
    final int bSize = b != null ? b.size() : 0;
    if (aSize != bSize) {
      return false;
    }
    for (int i = 0; i < aSize; i++) {
      if (!a.get(i).compare(b.get(i))) {
        return false;
      }
    }
    return true;
  }

  // Getters

  public boolean isEmpty () {
    // XGram: layers count too (a photo with only a text on it is edited), and the cut-out
    return (drawingsList == null || drawingsList.isEmpty()) && (layers == null || layers.isEmpty()) && !hasCutout();
  }

  /** XGram: whether a drawing paints with the photo under it (blur, pixelate: {@link SimpleDrawing#needsBase()}). */
  public boolean hasBaseDrawings () {
    if (drawingsList != null) {
      for (SimpleDrawing drawing : drawingsList) {
        if (drawing.needsBase()) {
          return true;
        }
      }
    }
    return false;
  }

  // Drawing

  public void draw (Canvas c, final int x, final int y, final int viewWidth, final int viewHeight) {
    draw(c, x, y, viewWidth, viewHeight, null, false, false, null);
  }

  /**
   * XGram: {@code base} is the photo under the drawing, as drawn there: at the drawing's own rect, mirrored as shown.
   * The blur and pixelate brushes paint with it (nothing without it).
   */
  public void draw (Canvas c, final int x, final int y, final int viewWidth, final int viewHeight, @Nullable Bitmap base, boolean baseMirrorHorizontally, boolean baseMirrorVertically) {
    draw(c, x, y, viewWidth, viewHeight, base, baseMirrorHorizontally, baseMirrorVertically, null);
  }

  /** XGram: {@link #draw(Canvas, int, int, int, int, Bitmap, boolean, boolean)} with the photo at {@code baseRect} instead. */
  public void draw (Canvas c, final int x, final int y, final int viewWidth, final int viewHeight, @Nullable Bitmap base, boolean baseMirrorHorizontally, boolean baseMirrorVertically, @Nullable RectF baseRect) {
    draw(c, x, y, viewWidth, viewHeight, base, baseMirrorHorizontally, baseMirrorVertically, baseRect, CUTOUT_PREVIEW);
  }

  /**
   * XGram: {@code cutoutMode} is what the cut-out does where the photo is (at {@code baseRect}, or the drawing's rect,
   * mirrored as the base): CUTOUT_PREVIEW a checkerboard where it removed the photo (only with a base: over the photo),
   * CUTOUT_APPLY removes that much of what is on the canvas there (the photo, drawn first), CUTOUT_NONE nothing.
   */
  public void draw (Canvas c, final int x, final int y, final int viewWidth, final int viewHeight, @Nullable Bitmap base, boolean baseMirrorHorizontally, boolean baseMirrorVertically, @Nullable RectF baseRect, int cutoutMode) {
    final XgramCutout cutout = this.cutout;
    if (cutout != null && cutoutMode != CUTOUT_NONE && (base != null || cutoutMode == CUTOUT_APPLY)) {
      final boolean cut = cutoutMode == CUTOUT_APPLY;
      if (baseRect != null) {
        cutout.draw(c, baseRect.left, baseRect.top, baseRect.right, baseRect.bottom, baseMirrorHorizontally, baseMirrorVertically, cut);
      } else {
        cutout.draw(c, x, y, x + viewWidth, y + viewHeight, baseMirrorHorizontally, baseMirrorVertically, cut);
      }
    }
    if (drawingsList != null && !drawingsList.isEmpty()) {
      XgramPaintBase paintBase = null;
      for (SimpleDrawing drawing : drawingsList) {
        if (drawing.needsBase() && paintBase == null && base != null) {
          paintBase = baseRect != null ?
            new XgramPaintBase(base, baseMirrorHorizontally, baseMirrorVertically, baseRect.left, baseRect.top, baseRect.right, baseRect.bottom) :
            new XgramPaintBase(base, baseMirrorHorizontally, baseMirrorVertically, x, y, x + viewWidth, y + viewHeight);
        }
        drawing.draw(c, x, y, x + viewWidth, y + viewHeight, paintBase);
      }
    }
    // XGram: the layers over the drawings, the last on top (by index: the editor may change the list meanwhile); a copy
    // for the GIF of an edit only those below the animated ones
    final List<XgramLayer> layers = this.layers;
    if (layers != null) {
      final int count = drawLayersBelow >= 0 ? Math.min(drawLayersBelow, layers.size()) : layers.size();
      for (int i = 0; i < count && i < layers.size(); i++) {
        final XgramLayer layer;
        try {
          layer = layers.get(i);
        } catch (IndexOutOfBoundsException ignored) {
          break;
        }
        layer.draw(c, x, y, viewWidth, viewHeight);
      }
    }
  }
}
