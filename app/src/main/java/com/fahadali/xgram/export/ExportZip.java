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
package com.fahadali.xgram.export;

import android.os.Build;

import androidx.annotation.Nullable;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The export's .zip, written once, straight into its destination. Entries can't interleave, so the writers add them
 * one after another: text entries through {@link #beginText} / {@link #endText}, media through {@link #putFileStored}
 * and {@link #putBytesStored}. Text is DEFLATED; media is STORED (it is compressed already), which needs the CRC
 * before the data: a file is read once for it, then copied. Before Android 7 the zip has no Zip64, so media stop
 * short of 65,535 entries and 4 GB ({@link #canAddMedia}).
 */
final class ExportZip implements Closeable {
  private static final int BUFFER = 64 * 1024;
  // Below Android 7 (no Zip64): the entry and size limits, with room left for what still comes after the media
  private static final int LEGACY_MAX_ENTRIES = 65_000;
  private static final long LEGACY_MAX_BYTES = 0xFFFFFFFFL - 64L * 1024L * 1024L;
  /** Local header, data descriptor and central directory record of one entry, generously. */
  private static final int ENTRY_OVERHEAD = 512;

  /** Reading the source failed before its entry started: nothing was written, the zip is fine. */
  static final class SourceException extends IOException {
    SourceException (IOException cause) {
      super(cause.getMessage(), cause);
    }
  }

  private final ZipOutputStream zip;
  private final CountingStream counter;
  @Nullable
  private final ExportJob job;
  private final byte[] buffer = new byte[BUFFER];
  private final PathSet usedPaths = new PathSet();
  @Nullable
  private Writer text;
  private int entries;
  private long bytes;

  ExportZip (OutputStream out, @Nullable ExportJob job) {
    this.counter = new CountingStream(new BufferedOutputStream(out, 256 * 1024));
    this.zip = new ZipOutputStream(counter);
    this.job = job;
  }

  int entryCount () {
    return entries;
  }

  /** Uncompressed bytes of the file entries so far. */
  long fileBytes () {
    return bytes;
  }

  /** Bytes of the .zip written so far. */
  long zipBytes () {
    return counter.count;
  }

  static boolean isLegacy () {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.N;
  }

  /**
   * Whether a media file of {@code size} bytes (with {@code extraEntries} thumbnails) still fits below Android 7's
   * zip limits, keeping {@code reserveBytes} and {@code reserveEntries} for the pages and result.json still to come.
   * Always true on Android 7+.
   */
  boolean canAddMedia (long size, int extraEntries, long reserveBytes, int reserveEntries) {
    if (!isLegacy()) {
      return true;
    }
    final int newEntries = 1 + extraEntries;
    return entries + newEntries + reserveEntries <= LEGACY_MAX_ENTRIES &&
      counter.count + size + (long) newEntries * ENTRY_OVERHEAD + reserveBytes <= LEGACY_MAX_BYTES;
  }

  /**
   * {@code path} or, when it is taken, Desktop's " (1)", " (2)", … before the first dot of the file name
   * (export_output_file.cpp PrepareRelativePath), and marks it taken. Compared case-insensitively, as the .zip may be
   * unpacked on a file system that is.
   */
  String uniquePath (String path) {
    if (usedPaths.add(key(path))) {
      return path;
    }
    final int slash = path.lastIndexOf('/');
    final int dot = path.indexOf('.', slash + 1);
    final String head = dot >= 0 ? path.substring(0, dot) : path;
    final String tail = dot >= 0 ? path.substring(dot) : "";
    for (int k = 1; ; k++) {
      final String candidate = head + " (" + k + ")" + tail;
      if (usedPaths.add(key(candidate))) {
        return candidate;
      }
    }
  }

  /** Marks a fixed path (an asset, a page) as taken, so no media file gets its name. */
  void reservePath (String path) {
    usedPaths.add(key(path));
  }

  private static long key (String path) {
    // 64-bit FNV-1a of the lower-cased path: a collision only adds a needless " (k)"
    final String lower = path.toLowerCase(Locale.ROOT);
    long hash = 0xcbf29ce484222325L;
    for (int i = 0; i < lower.length(); i++) {
      hash ^= lower.charAt(i);
      hash *= 0x100000001b3L;
    }
    return hash;
  }

  private void putEntry (ZipEntry entry, long timeMs) throws IOException {
    if (timeMs > 0) {
      entry.setTime(timeMs);
    }
    zip.putNextEntry(entry);
    entries++;
  }

  private void putDeflatedEntry (String path, long timeMs) throws IOException {
    final ZipEntry entry = new ZipEntry(path);
    entry.setMethod(ZipEntry.DEFLATED);
    putEntry(entry, timeMs);
  }

  private void putStoredEntry (String path, long size, long crc, long timeMs) throws IOException {
    final ZipEntry entry = new ZipEntry(path);
    entry.setMethod(ZipEntry.STORED);
    entry.setSize(size);
    entry.setCompressedSize(size);
    entry.setCrc(crc);
    putEntry(entry, timeMs);
  }

  /** A UTF-8 text entry; the writer must not be closed, {@link #endText()} ends the entry. */
  Writer beginText (String path, long timeMs) throws IOException {
    endText();
    reservePath(path);
    zip.setLevel(Deflater.DEFAULT_COMPRESSION);
    putDeflatedEntry(path, timeMs);
    text = new BufferedWriter(new OutputStreamWriter(new NonClosingStream(zip), StandardCharsets.UTF_8), BUFFER);
    return text;
  }

  /** Flushes the text writer before the entry is closed: its buffered bytes would land in the next entry otherwise. */
  void endText () throws IOException {
    final Writer writer = text;
    if (writer != null) {
      text = null;
      writer.flush();
      zip.closeEntry();
    }
  }

  /** Copies {@code in} into a new DEFLATED entry, checking cancel between 64 KB chunks. Does not close {@code in}. */
  void putStream (String path, InputStream in, long timeMs, boolean compress) throws IOException {
    endText();
    reservePath(path);
    zip.setLevel(compress ? Deflater.DEFAULT_COMPRESSION : Deflater.NO_COMPRESSION);
    putDeflatedEntry(path, timeMs);
    int read;
    while ((read = in.read(buffer)) != -1) {
      checkCanceled();
      zip.write(buffer, 0, read);
      bytes += read;
    }
    zip.closeEntry();
  }

  /** The file is opened before its entry starts, so a file that is gone leaves no empty entry behind. */
  void putFile (String path, File file, long timeMs, boolean compress) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      putStream(path, in, timeMs, compress);
    }
  }

  /**
   * A media file as a STORED entry: one read for the CRC, then the copy from the same open file (it survives a cache
   * cleanup meanwhile). Opening or reading it before the entry starts throws {@link SourceException} and leaves the zip
   * as it was; a failure after that is the zip's. Returns the bytes copied.
   */
  long putFileStored (String path, File file, long timeMs) throws IOException {
    endText();
    final FileInputStream in;
    try {
      in = new FileInputStream(file);
    } catch (IOException e) {
      throw new SourceException(e);
    }
    try {
      final CRC32 crc = new CRC32();
      long size = 0;
      try {
        int read;
        while ((read = in.read(buffer)) != -1) {
          checkCanceled();
          crc.update(buffer, 0, read);
          size += read;
        }
        in.getChannel().position(0);
      } catch (IOException e) {
        throw new SourceException(e);
      }
      putStoredEntry(path, size, crc.getValue(), timeMs);
      long copied = 0;
      int read;
      while (copied < size && (read = in.read(buffer, 0, (int) Math.min(buffer.length, size - copied))) != -1) {
        checkCanceled();
        zip.write(buffer, 0, read);
        copied += read;
      }
      zip.closeEntry(); // a file that changed in between fails here (size or CRC), not silently
      bytes += copied;
      return copied;
    } finally {
      try {
        in.close();
      } catch (IOException ignored) { }
    }
  }

  /** A generated image (thumbnail, icon) as a STORED entry. */
  void putBytesStored (String path, byte[] data, long timeMs) throws IOException {
    endText();
    final CRC32 crc = new CRC32();
    crc.update(data, 0, data.length);
    putStoredEntry(path, data.length, crc.getValue(), timeMs);
    zip.write(data, 0, data.length);
    zip.closeEntry();
    bytes += data.length;
  }

  private void checkCanceled () {
    if (job != null) {
      job.checkCanceled();
    }
  }

  /** Writes the central directory; {@link #close()} then closes the destination stream. */
  void finish () throws IOException {
    endText();
    zip.finish();
    zip.flush();
  }

  @Override
  public void close () throws IOException {
    text = null;
    zip.close();
  }

  /** Lets a text writer flush into the zip without closing it. */
  private static final class NonClosingStream extends FilterOutputStream {
    NonClosingStream (OutputStream out) {
      super(out);
    }

    @Override
    public void write (byte[] b, int off, int len) throws IOException {
      out.write(b, off, len); // FilterOutputStream would write byte by byte
    }

    @Override
    public void close () throws IOException {
      flush();
    }
  }

  /** Counts what the zip writes: its size so far, for Android 6 and older's 4 GB limit. */
  private static final class CountingStream extends FilterOutputStream {
    long count;

    CountingStream (OutputStream out) {
      super(out);
    }

    @Override
    public void write (int b) throws IOException {
      out.write(b);
      count++;
    }

    @Override
    public void write (byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);
      count += len;
    }
  }

  /** The 64-bit keys of the paths in the .zip (open addressing): 8 bytes per media file, whatever the chat's size. */
  private static final class PathSet {
    private long[] keys = new long[1024];
    private boolean hasZero;
    private int size;

    /** False when {@code key} was there already. */
    boolean add (long key) {
      if (key == 0) {
        if (hasZero) {
          return false;
        }
        hasZero = true;
        return true;
      }
      if ((size + 1) * 2 > keys.length) {
        grow();
      }
      if (!insert(keys, key)) {
        return false;
      }
      size++;
      return true;
    }

    private static boolean insert (long[] table, long key) {
      final int mask = table.length - 1;
      int i = (int) (key ^ (key >>> 32)) & mask;
      while (table[i] != 0) {
        if (table[i] == key) {
          return false;
        }
        i = (i + 1) & mask;
      }
      table[i] = key;
      return true;
    }

    private void grow () {
      final long[] old = keys;
      keys = new long[old.length * 2];
      for (long key : old) {
        if (key != 0) {
          insert(keys, key);
        }
      }
    }
  }
}
