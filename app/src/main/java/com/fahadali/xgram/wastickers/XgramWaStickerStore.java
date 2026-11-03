/*
 * This file is a part of XGram, a fork of Telegram X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fahadali.xgram.wastickers;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * XGram: the Telegram sticker packs exported for WhatsApp (sticker pack sheet ⋮ → Add to WhatsApp). Each pack's files
 * are in {@code filesDir/wastickers/<identifier>/} (the 512x512 WebP stickers and a 96x96 PNG tray icon), and
 * {@code filesDir/wastickers/packs.json} lists the packs; {@link XgramWaStickerProvider} serves exactly that list to
 * WhatsApp. The files dir, not the cache, so clearing the cache can't break packs WhatsApp already has.
 * <p>
 * Identifiers are {@code tg_<setId>_<part>}: a set over 30 stickers is split into parts "Title (1/N)". Re-exporting a
 * set replaces its parts and gives them a new {@code image_data_version} (one serial for all packs, so a version is
 * never reused, even for a pack removed and exported again). At most {@link #MAX_PACKS} packs stay: the oldest
 * exported go first.
 */
public final class XgramWaStickerStore {
  private XgramWaStickerStore () { }

  /** WhatsApp's limits: 3-30 stickers in a pack, 1-3 emojis a sticker, 128 characters in a name or identifier. */
  public static final int MIN_STICKERS = 3, MAX_STICKERS = 30;
  public static final int MAX_EMOJIS = 3;
  public static final int MAX_TEXT_LENGTH = 128;
  /** How many exported packs stay on disk (and so in WhatsApp). */
  public static final int MAX_PACKS = 10;

  static final String TRAY_FILE = "tray.png";

  private static final String DIR = "wastickers";
  private static final String INDEX = "packs.json";
  private static final String STAGING = ".staging";
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final Object LOCK = new Object();
  private static @Nullable Index cached;
  private static long cachedModified = -1, cachedLength = -1;

  public static final class Sticker {
    public final String file;
    public final String[] emojis;

    public Sticker (String file, String[] emojis) {
      this.file = file;
      this.emojis = emojis;
    }
  }

  public static final class Pack {
    public final String identifier;
    public final long setId;
    public final int part, parts;
    public final String name, publisher, tray;
    public final long version;
    public final long exportedAt;
    public final Sticker[] stickers;

    public Pack (String identifier, long setId, int part, int parts, String name, String publisher, String tray, long version, long exportedAt, Sticker[] stickers) {
      this.identifier = identifier;
      this.setId = setId;
      this.part = part;
      this.parts = parts;
      this.name = name;
      this.publisher = publisher;
      this.tray = tray;
      this.version = version;
      this.exportedAt = exportedAt;
      this.stickers = stickers;
    }

    Pack withVersion (long version, long exportedAt) {
      return new Pack(identifier, setId, part, parts, name, publisher, tray, version, exportedAt, stickers);
    }

    /** Whether {@code fileName} is one of this pack's files (the tray icon or a sticker): the provider serves only those. */
    boolean hasFile (String fileName) {
      if (fileName == null) {
        return false;
      }
      if (fileName.equals(tray)) {
        return true;
      }
      for (Sticker sticker : stickers) {
        if (fileName.equals(sticker.file)) {
          return true;
        }
      }
      return false;
    }
  }

  private static final class Index {
    final long serial;
    final List<Pack> packs;

    Index (long serial, List<Pack> packs) {
      this.serial = serial;
      this.packs = packs;
    }
  }

  // Names and places

  public static String authority (@NonNull Context context) {
    return context.getPackageName() + ".wastickers";
  }

  static Uri metadataUri (@NonNull Context context) {
    return new Uri.Builder().scheme("content").authority(authority(context)).appendPath(XgramWaStickerProvider.METADATA).build();
  }

  /** {@code tg_<setId>_<part>}: only [a-zA-Z0-9_], as WhatsApp allows (a negative id gets "n" for its minus). */
  public static String identifier (long setId, int part) {
    return "tg_" + String.valueOf(setId).replace('-', 'n') + "_" + part;
  }

  static File rootDir (@NonNull Context context) {
    return new File(context.getFilesDir(), DIR);
  }

  static File packDir (@NonNull Context context, String identifier) {
    return new File(rootDir(context), identifier);
  }

  /** A new, empty folder the export builds its files in; nothing in it is served until {@link #commit}. */
  static File newStagingDir (@NonNull Context context, long setId) throws IOException {
    // One export runs at a time: the whole staging folder goes, also what an export killed half way left for another set
    File stagingRoot = new File(rootDir(context), STAGING);
    deleteRecursively(stagingRoot);
    File dir = new File(stagingRoot, String.valueOf(setId).replace('-', 'n'));
    if (!dir.mkdirs() && !dir.isDirectory()) {
      throw new IOException("Cannot create " + dir);
    }
    return dir;
  }

  static void deleteRecursively (@Nullable File file) {
    if (file == null || !file.exists()) {
      return;
    }
    File[] children = file.isDirectory() ? file.listFiles() : null;
    if (children != null) {
      for (File child : children) {
        deleteRecursively(child);
      }
    }
    //noinspection ResultOfMethodCallIgnored
    file.delete();
  }

  // Reading

  /** All exported packs, newest export first. */
  @NonNull
  public static List<Pack> packs (@NonNull Context context) {
    synchronized (LOCK) {
      return Collections.unmodifiableList(new ArrayList<>(readIndex(context).packs));
    }
  }

  @Nullable
  public static Pack find (@NonNull Context context, @Nullable String identifier) {
    if (identifier == null) {
      return null;
    }
    synchronized (LOCK) {
      for (Pack pack : readIndex(context).packs) {
        if (pack.identifier.equals(identifier)) {
          return pack;
        }
      }
    }
    return null;
  }

  /** A pack's file, or null when the pack or the file isn't listed (no path outside the pack's own files). */
  @Nullable
  static File assetFile (@NonNull Context context, @Nullable String identifier, @Nullable String fileName) {
    Pack pack = find(context, identifier);
    if (pack == null || !pack.hasFile(fileName)) {
      return null;
    }
    File file = new File(packDir(context, pack.identifier), fileName);
    return file.isFile() ? file : null;
  }

  private static Index readIndex (Context context) {
    File file = new File(rootDir(context), INDEX);
    long modified = file.lastModified(), length = file.length();
    if (cached != null && modified == cachedModified && length == cachedLength) {
      return cached;
    }
    Index index;
    if (!file.isFile()) {
      index = new Index(0, new ArrayList<>());
    } else {
      try {
        index = parse(new String(readFully(file), UTF_8));
      } catch (Throwable t) {
        Log.w("XGram WhatsApp stickers: unreadable %s", t, file);
        index = new Index(0, new ArrayList<>());
      }
    }
    cached = index;
    cachedModified = modified;
    cachedLength = length;
    return index;
  }

  private static Index parse (String json) throws JSONException {
    JSONObject root = new JSONObject(json);
    long serial = root.optLong("serial", 0);
    JSONArray packsJson = root.optJSONArray("packs");
    List<Pack> packs = new ArrayList<>();
    if (packsJson != null) {
      for (int i = 0; i < packsJson.length(); i++) {
        JSONObject p = packsJson.getJSONObject(i);
        JSONArray stickersJson = p.getJSONArray("stickers");
        Sticker[] stickers = new Sticker[stickersJson.length()];
        for (int j = 0; j < stickers.length; j++) {
          JSONObject s = stickersJson.getJSONObject(j);
          JSONArray emojisJson = s.getJSONArray("emojis");
          String[] emojis = new String[emojisJson.length()];
          for (int k = 0; k < emojis.length; k++) {
            emojis[k] = emojisJson.getString(k);
          }
          stickers[j] = new Sticker(s.getString("file"), emojis);
        }
        packs.add(new Pack(
          p.getString("id"), p.getLong("setId"), p.getInt("part"), p.getInt("parts"),
          p.getString("name"), p.getString("publisher"), p.getString("tray"),
          p.getLong("version"), p.getLong("exportedAt"), stickers
        ));
      }
    }
    return new Index(serial, packs);
  }

  private static String toJson (Index index) throws JSONException {
    JSONObject root = new JSONObject();
    root.put("serial", index.serial);
    JSONArray packsJson = new JSONArray();
    for (Pack pack : index.packs) {
      JSONObject p = new JSONObject();
      p.put("id", pack.identifier);
      p.put("setId", pack.setId);
      p.put("part", pack.part);
      p.put("parts", pack.parts);
      p.put("name", pack.name);
      p.put("publisher", pack.publisher);
      p.put("tray", pack.tray);
      p.put("version", pack.version);
      p.put("exportedAt", pack.exportedAt);
      JSONArray stickersJson = new JSONArray();
      for (Sticker sticker : pack.stickers) {
        JSONObject s = new JSONObject();
        s.put("file", sticker.file);
        JSONArray emojisJson = new JSONArray();
        for (String emoji : sticker.emojis) {
          emojisJson.put(emoji);
        }
        s.put("emojis", emojisJson);
        stickersJson.put(s);
      }
      p.put("stickers", stickersJson);
      packsJson.put(p);
    }
    root.put("packs", packsJson);
    return root.toString();
  }

  // Writing

  /**
   * Puts the parts of one sticker set in place: each {@code stagedDirs[i]} (in the staging folder) becomes
   * {@code packs[i]}'s folder, replacing what an earlier export of the set left, and the set's parts from before that
   * aren't in {@code packs} any more are removed. Every new part gets the next {@code image_data_version}. Then the
   * oldest other sets go, all their parts together, until at most {@link #MAX_PACKS} packs are left. Returns the packs
   * as listed.
   */
  @NonNull
  static List<Pack> commit (@NonNull Context context, long setId, @NonNull List<Pack> packs, @NonNull List<File> stagedDirs) throws IOException {
    if (packs.size() != stagedDirs.size()) {
      throw new IllegalArgumentException();
    }
    synchronized (LOCK) {
      Index index = readIndex(context);
      long serial = index.serial;
      final long now = System.currentTimeMillis();

      // The set's older parts out of the list first: WhatsApp never sees a pack whose files are being swapped
      List<Pack> kept = new ArrayList<>();
      List<Pack> removed = new ArrayList<>();
      for (Pack pack : index.packs) {
        if (pack.setId == setId) {
          removed.add(pack);
        } else {
          kept.add(pack);
        }
      }
      if (!removed.isEmpty()) {
        writeIndex(context, new Index(serial, kept));
      }

      List<Pack> committed = new ArrayList<>(packs.size());
      for (int i = 0; i < packs.size(); i++) {
        Pack pack = packs.get(i);
        File target = packDir(context, pack.identifier);
        deleteRecursively(target);
        File staged = stagedDirs.get(i);
        if (!staged.renameTo(target)) {
          throw new IOException("Cannot move " + staged + " to " + target);
        }
        committed.add(pack.withVersion(++serial, now));
      }
      Set<String> committedIds = new HashSet<>();
      for (Pack pack : committed) {
        committedIds.add(pack.identifier);
      }
      for (Pack pack : removed) {
        if (!committedIds.contains(pack.identifier)) {
          deleteRecursively(packDir(context, pack.identifier));
        }
      }

      // Newest first; over the limit, the oldest exports of other sets go, a whole set at a time (never only some of its parts)
      Collections.sort(kept, (a, b) -> Long.compare(b.exportedAt, a.exportedAt));
      List<Pack> all = new ArrayList<>(committed);
      all.addAll(kept);
      final int limit = Math.max(MAX_PACKS, committed.size());
      while (all.size() > limit) {
        final long oldestSet = all.get(all.size() - 1).setId;
        for (int i = all.size() - 1; i >= committed.size(); i--) {
          if (all.get(i).setId == oldestSet) {
            deleteRecursively(packDir(context, all.remove(i).identifier));
          }
        }
      }

      writeIndex(context, new Index(serial, all));
      removeStrayFolders(context, all);
      return committed;
    }
  }

  private static void writeIndex (Context context, Index index) throws IOException {
    File dir = rootDir(context);
    if (!dir.isDirectory() && !dir.mkdirs()) {
      throw new IOException("Cannot create " + dir);
    }
    final byte[] data;
    try {
      data = toJson(index).getBytes(UTF_8);
    } catch (JSONException e) {
      throw new IOException(e);
    }
    File temp = new File(dir, INDEX + ".tmp");
    try (OutputStream out = new FileOutputStream(temp)) {
      out.write(data);
      out.flush();
    }
    File file = new File(dir, INDEX);
    if (!temp.renameTo(file)) {
      //noinspection ResultOfMethodCallIgnored
      file.delete();
      if (!temp.renameTo(file)) {
        throw new IOException("Cannot write " + file);
      }
    }
    cached = index;
    cachedModified = file.lastModified();
    cachedLength = file.length();
  }

  /** Folders no listed pack owns (left by an export that stopped half way), except the staging folder. */
  private static void removeStrayFolders (Context context, List<Pack> packs) {
    File[] children = rootDir(context).listFiles();
    if (children == null) {
      return;
    }
    Set<String> ids = new HashSet<>();
    for (Pack pack : packs) {
      ids.add(pack.identifier);
    }
    for (File child : children) {
      if (child.isDirectory() && !STAGING.equals(child.getName()) && !ids.contains(child.getName())) {
        deleteRecursively(child);
      }
    }
  }

  // Files

  static byte[] readFully (File file) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(1024, Math.min(file.length(), 8 * 1024 * 1024)));
      byte[] buffer = new byte[16 * 1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    }
  }

  static void writeFully (File file, byte[] data) throws IOException {
    try (OutputStream out = new FileOutputStream(file)) {
      out.write(data);
      out.flush();
    }
  }
}
