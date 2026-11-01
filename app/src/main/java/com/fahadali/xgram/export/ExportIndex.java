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

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * The exported message ids on disk (8 bytes each), so a chat of any size costs no memory. Pass 1 appends them newest
 * first through a buffer; pass 2 reads one page (1000 ids, oldest first) at a time. The first id of every page stays in
 * memory (8 bytes per 1000 messages) to find the page a reply points to.
 */
final class ExportIndex implements Closeable {
  static final int PAGE_SIZE = 1000;

  private final File file;
  private DataOutputStream out;
  private RandomAccessFile in;
  private int count;
  private long lastId = Long.MAX_VALUE;
  private long[] pageFirstIds = new long[0];

  ExportIndex (File file) throws IOException {
    this.file = file;
    this.out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file), 64 * 1024));
  }

  /** Pass 1: ids must come strictly newest first (each smaller than the one before). */
  void append (long messageId) throws IOException {
    if (out == null || messageId >= lastId) {
      throw new IllegalStateException();
    }
    out.writeLong(messageId);
    lastId = messageId;
    count++;
  }

  /** The end of pass 1: the file is closed for writing and opened for page reads. */
  void finishWriting () throws IOException {
    if (out != null) {
      out.close();
      out = null;
    }
    in = new RandomAccessFile(file, "r");
    final int pages = pageCount();
    pageFirstIds = new long[pages];
    for (int page = 0; page < pages; page++) {
      in.seek((long) (count - 1 - page * PAGE_SIZE) * 8L);
      pageFirstIds[page] = in.readLong();
    }
  }

  int count () {
    return count;
  }

  int pageCount () {
    return (count + PAGE_SIZE - 1) / PAGE_SIZE;
  }

  /** The ids of {@code page}, oldest first, read in one go. */
  long[] readPage (int page) throws IOException {
    final int from = page * PAGE_SIZE;
    final int to = Math.min(count, from + PAGE_SIZE);
    final int n = Math.max(0, to - from);
    final long[] ids = new long[n];
    if (n == 0) {
      return ids;
    }
    // Ascending positions [from, to) are the file's entries [count - to, count - from), newest first
    final byte[] bytes = new byte[n * 8];
    in.seek((long) (count - to) * 8L);
    in.readFully(bytes);
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    for (int j = 0; j < n; j++) {
      ids[n - 1 - j] = buffer.getLong(j * 8);
    }
    return ids;
  }

  /**
   * The page whose id range holds {@code messageId}, or -1 when it is older than everything exported. An id inside the
   * range that was not exported still gives its page: Desktop's script then says the message was not exported.
   */
  int pageOf (long messageId) {
    int lo = 0, hi = pageFirstIds.length - 1, found = -1;
    while (lo <= hi) {
      final int mid = (lo + hi) >>> 1;
      if (pageFirstIds[mid] <= messageId) {
        found = mid;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return found;
  }

  @Override
  public void close () {
    try {
      if (out != null) {
        out.close();
      }
    } catch (IOException ignored) { }
    out = null;
    try {
      if (in != null) {
        in.close();
      }
    } catch (IOException ignored) { }
    in = null;
  }
}
