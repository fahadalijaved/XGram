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
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The export's .zip, written once, straight into its destination. Entries can't interleave, so the writers add them
 * one after another: text entries through {@link #beginText} / {@link #endText}, files through {@link #putFile}.
 * Everything is DEFLATED (STORED needs the CRC before the data); media goes in at level 0, text at the default level.
 */
final class ExportZip implements Closeable {
  private static final int BUFFER = 64 * 1024;

  private final ZipOutputStream zip;
  @Nullable
  private final ExportJob job;
  private final byte[] buffer = new byte[BUFFER];
  @Nullable
  private Writer text;
  private int entries;
  private long bytes;

  ExportZip (OutputStream out, @Nullable ExportJob job) {
    this.zip = new ZipOutputStream(new BufferedOutputStream(out, 256 * 1024));
    this.job = job;
  }

  int entryCount () {
    return entries;
  }

  /** Uncompressed bytes of the file entries so far. */
  long fileBytes () {
    return bytes;
  }

  private void putEntry (String path, long timeMs) throws IOException {
    ZipEntry entry = new ZipEntry(path);
    entry.setMethod(ZipEntry.DEFLATED);
    if (timeMs > 0) {
      entry.setTime(timeMs);
    }
    zip.putNextEntry(entry);
    entries++;
  }

  /** A UTF-8 text entry; the writer must not be closed, {@link #endText()} ends the entry. */
  Writer beginText (String path, long timeMs) throws IOException {
    endText();
    zip.setLevel(Deflater.DEFAULT_COMPRESSION);
    putEntry(path, timeMs);
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

  /** Copies {@code in} into a new entry, checking cancel between 64 KB chunks. Does not close {@code in}. */
  void putStream (String path, InputStream in, long timeMs, boolean compress) throws IOException {
    endText();
    zip.setLevel(compress ? Deflater.DEFAULT_COMPRESSION : Deflater.NO_COMPRESSION);
    putEntry(path, timeMs);
    int read;
    while ((read = in.read(buffer)) != -1) {
      if (job != null) {
        job.checkCanceled();
      }
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
}
