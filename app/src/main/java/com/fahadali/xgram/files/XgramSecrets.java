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
package com.fahadali.xgram.files;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * XGram: small secrets (an SFTP password or private key) encrypted with a key that never leaves Android's keystore
 * (AES-256-GCM), so they are only readable by XGram on this phone. Backups and copies of the settings carry the
 * ciphertext only.
 */
public final class XgramSecrets {
  private XgramSecrets () { }

  private static final String TAG = "XgramSecrets";
  private static final String ALIAS = "xgram_secrets_v1";

  private static SecretKey key () throws Exception {
    final KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (!store.containsAlias(ALIAS)) {
      final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build());
      return generator.generateKey();
    }
    return ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
  }

  /** {@code plain} encrypted as "iv:ciphertext" (base64), or null when the keystore failed. */
  public static @Nullable String encrypt (@Nullable String plain) {
    if (plain == null || plain.isEmpty()) {
      return null;
    }
    try {
      final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key());
      final byte[] out = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(out, Base64.NO_WRAP);
    } catch (Throwable t) {
      Log.w(TAG, "encrypt", t);
      return null;
    }
  }

  /** The secret {@link #encrypt} made, or null. */
  public static @Nullable String decrypt (@Nullable String stored) {
    if (stored == null || stored.isEmpty()) {
      return null;
    }
    final int split = stored.indexOf(':');
    if (split <= 0) {
      return null;
    }
    try {
      final byte[] iv = Base64.decode(stored.substring(0, split), Base64.NO_WRAP);
      final byte[] data = Base64.decode(stored.substring(split + 1), Base64.NO_WRAP);
      final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
      return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    } catch (Throwable t) {
      Log.w(TAG, "decrypt", t);
      return null;
    }
  }
}
