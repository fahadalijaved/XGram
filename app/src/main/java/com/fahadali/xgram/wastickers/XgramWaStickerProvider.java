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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;

/**
 * XGram: the sticker packs exported for WhatsApp ({@link XgramWaStickerStore}), served the way WhatsApp's third-party
 * sticker API reads them (the provider of github.com/WhatsApp/stickers, Android sample). Exported, but readable only
 * with {@code com.whatsapp.sticker.READ}, which WhatsApp holds; read only.
 * <ul>
 *   <li>{@code content://<applicationId>.wastickers/metadata}: every pack, one row each</li>
 *   <li>{@code .../metadata/<identifier>}: one pack</li>
 *   <li>{@code .../stickers/<identifier>}: its stickers (file name, emojis)</li>
 *   <li>{@code .../stickers_asset/<identifier>/<file name>}: a sticker's WebP or the tray icon's PNG</li>
 * </ul>
 * Only files a listed pack names are opened, so no path reaches anything else in the app's files.
 */
public final class XgramWaStickerProvider extends ContentProvider {
  static final String METADATA = "metadata";
  static final String STICKERS = "stickers";
  static final String STICKERS_ASSET = "stickers_asset";

  // The pack columns, in the sample's order
  private static final String STICKER_PACK_IDENTIFIER = "sticker_pack_identifier";
  private static final String STICKER_PACK_NAME = "sticker_pack_name";
  private static final String STICKER_PACK_PUBLISHER = "sticker_pack_publisher";
  private static final String STICKER_PACK_ICON = "sticker_pack_icon";
  private static final String ANDROID_PLAY_STORE_LINK = "android_play_store_link";
  private static final String IOS_APP_DOWNLOAD_LINK = "ios_app_download_link";
  private static final String PUBLISHER_EMAIL = "sticker_pack_publisher_email";
  private static final String PUBLISHER_WEBSITE = "sticker_pack_publisher_website";
  private static final String PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website";
  private static final String LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website";
  private static final String IMAGE_DATA_VERSION = "image_data_version";
  private static final String AVOID_CACHE = "whatsapp_will_not_cache_stickers";
  private static final String ANIMATED_STICKER_PACK = "animated_sticker_pack";

  private static final String[] PACK_COLUMNS = {
    STICKER_PACK_IDENTIFIER,
    STICKER_PACK_NAME,
    STICKER_PACK_PUBLISHER,
    STICKER_PACK_ICON,
    ANDROID_PLAY_STORE_LINK,
    IOS_APP_DOWNLOAD_LINK,
    PUBLISHER_EMAIL,
    PUBLISHER_WEBSITE,
    PRIVACY_POLICY_WEBSITE,
    LICENSE_AGREEMENT_WEBSITE,
    IMAGE_DATA_VERSION,
    AVOID_CACHE,
    ANIMATED_STICKER_PACK
  };

  // The sticker columns
  private static final String STICKER_FILE_NAME = "sticker_file_name";
  private static final String STICKER_EMOJI = "sticker_emoji";
  private static final String STICKER_ACCESSIBILITY_TEXT = "sticker_accessibility_text";

  private static final String[] STICKER_COLUMNS = {
    STICKER_FILE_NAME,
    STICKER_EMOJI,
    STICKER_ACCESSIBILITY_TEXT
  };

  @Override
  public boolean onCreate () {
    return true;
  }

  private Context requireContextCompat () {
    Context context = getContext();
    if (context == null) {
      throw new IllegalStateException();
    }
    return context;
  }

  private boolean isOwnAuthority (Uri uri) {
    return XgramWaStickerStore.authority(requireContextCompat()).equals(uri.getAuthority());
  }

  @Nullable
  @Override
  public Cursor query (@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
    if (!isOwnAuthority(uri)) {
      throw new IllegalArgumentException("Unknown URI: " + uri);
    }
    final Context context = requireContextCompat();
    final List<String> segments = uri.getPathSegments();
    final MatrixCursor cursor;
    if (segments.size() == 1 && METADATA.equals(segments.get(0))) {
      cursor = packsCursor(XgramWaStickerStore.packs(context));
    } else if (segments.size() == 2 && METADATA.equals(segments.get(0))) {
      XgramWaStickerStore.Pack pack = XgramWaStickerStore.find(context, segments.get(1));
      cursor = packsCursor(pack != null ? Collections.singletonList(pack) : Collections.<XgramWaStickerStore.Pack>emptyList());
    } else if (segments.size() == 2 && STICKERS.equals(segments.get(0))) {
      cursor = new MatrixCursor(STICKER_COLUMNS);
      XgramWaStickerStore.Pack pack = XgramWaStickerStore.find(context, segments.get(1));
      if (pack != null) {
        for (XgramWaStickerStore.Sticker sticker : pack.stickers) {
          cursor.addRow(new Object[] {sticker.file, TextUtils.join(",", sticker.emojis), null});
        }
      }
    } else {
      throw new IllegalArgumentException("Unknown URI: " + uri);
    }
    cursor.setNotificationUri(context.getContentResolver(), uri);
    return cursor;
  }

  private static MatrixCursor packsCursor (List<XgramWaStickerStore.Pack> packs) {
    MatrixCursor cursor = new MatrixCursor(PACK_COLUMNS);
    for (XgramWaStickerStore.Pack pack : packs) {
      cursor.addRow(new Object[] {
        pack.identifier,
        pack.name,
        pack.publisher,
        pack.tray,
        "", // android_play_store_link
        "", // ios_app_download_link
        "", // publisher email
        "", // publisher website
        "", // privacy policy
        "", // license agreement
        String.valueOf(pack.version),
        0, // WhatsApp may cache the stickers
        0  // static stickers only (animated packs come later)
      });
    }
    return cursor;
  }

  @Nullable
  @Override
  public String getType (@NonNull Uri uri) {
    final List<String> segments = uri.getPathSegments();
    final String authority = uri.getAuthority();
    if (segments.isEmpty()) {
      return null;
    }
    switch (segments.get(0)) {
      case METADATA:
        return (segments.size() == 1 ? "vnd.android.cursor.dir/vnd." : "vnd.android.cursor.item/vnd.") + authority + "." + METADATA;
      case STICKERS:
        return "vnd.android.cursor.dir/vnd." + authority + "." + STICKERS;
      case STICKERS_ASSET:
        if (segments.size() == 3) {
          return XgramWaStickerStore.TRAY_FILE.equals(segments.get(2)) ? "image/png" : "image/webp";
        }
        return null;
    }
    return null;
  }

  @NonNull
  private File assetFile (@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) {
      throw new FileNotFoundException("Read only: " + uri);
    }
    final List<String> segments = uri.getPathSegments();
    if (!isOwnAuthority(uri) || segments.size() != 3 || !STICKERS_ASSET.equals(segments.get(0))) {
      throw new FileNotFoundException("Unknown URI: " + uri);
    }
    File file = XgramWaStickerStore.assetFile(requireContextCompat(), segments.get(1), segments.get(2));
    if (file == null) {
      throw new FileNotFoundException("No such sticker: " + uri);
    }
    return file;
  }

  @Nullable
  @Override
  public AssetFileDescriptor openAssetFile (@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
    File file = assetFile(uri, mode);
    return new AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length());
  }

  @Nullable
  @Override
  public ParcelFileDescriptor openFile (@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
    return ParcelFileDescriptor.open(assetFile(uri, mode), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Nullable
  @Override
  public Uri insert (@NonNull Uri uri, @Nullable ContentValues values) {
    throw new UnsupportedOperationException("Read only");
  }

  @Override
  public int delete (@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
    throw new UnsupportedOperationException("Read only");
  }

  @Override
  public int update (@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
    throw new UnsupportedOperationException("Read only");
  }
}
