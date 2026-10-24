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
 * File created on 11/07/2017
 */
package org.thunderdog.challegram.mediaview.paint;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.editor.XgramLayer;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.collection.IntList;

public class PaintAction {
  public static final int SIMPLE_DRAWING = 0;
  // XGram: the eraser and an undoable Clear
  /** {@link EraseData}: the drawings one eraser gesture removed. */
  public static final int ERASE = 1;
  /** {@link ClearData}: everything Clear removed, in order. */
  public static final int CLEAR = 2;
  // XGram: the movable layers (text, emoji, stickers), each a {@link LayerData}
  /** A layer was added; its index is where it was when undone. */
  public static final int LAYER_ADD = 3;
  /** A layer was deleted from its index. */
  public static final int LAYER_REMOVE = 4;
  /** A layer was moved, scaled, turned or edited: before and after. */
  public static final int LAYER_CHANGE = 5;

  private final int type;
  private final Object data;
  private final long creationTimeMs;
  // XGram: where an undone drawing was in the list, so redo puts it back there
  private int index = -1;

  public PaintAction (int type, Object data) {
    this.type = type;
    this.data = data;
    this.creationTimeMs = SystemClock.uptimeMillis();
  }

  public int getType () {
    return type;
  }

  public Object getData () {
    return data;
  }

  int getIndex () {
    return index;
  }

  void setIndex (int index) {
    this.index = index;
  }

  public long getCreationTimeMs () {
    return creationTimeMs;
  }

  /** XGram: the drawings one eraser gesture removed, each with its place in the list at the moment it went. */
  public static final class EraseData {
    final List<SimpleDrawing> drawings = new ArrayList<>();
    final IntList indexes = new IntList(4);

    void add (SimpleDrawing drawing, int index) {
      drawings.add(drawing);
      indexes.append(index);
    }

    public boolean isEmpty () {
      return drawings.isEmpty();
    }
  }

  /** XGram: a copy for another PaintState (the editor's copy to go back to), with its own data and index. */
  PaintAction copy (Object data) {
    PaintAction copy = new PaintAction(type, data);
    copy.index = index;
    return copy;
  }

  /** XGram: what Clear removed: the drawings and the layers, each in order. */
  public static final class ClearData {
    final List<SimpleDrawing> drawings;
    final List<XgramLayer> layers;

    ClearData (@NonNull List<SimpleDrawing> drawings, @NonNull List<XgramLayer> layers) {
      this.drawings = drawings;
      this.layers = layers;
    }
  }

  /** XGram: a layer action's layer and, for a change, the layer before and after it (copies, never changed). */
  public static final class LayerData {
    final XgramLayer layer;
    final @Nullable XgramLayer before, after;

    LayerData (@NonNull XgramLayer layer, @Nullable XgramLayer before, @Nullable XgramLayer after) {
      this.layer = layer;
      this.before = before;
      this.after = after;
    }
  }
}
