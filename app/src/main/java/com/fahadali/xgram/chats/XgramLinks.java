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
package com.fahadali.xgram.chats;

import android.net.Uri;

import com.fahadali.xgram.XgramSettings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Removes tracking parameters from links before they are opened (NagramX / exteraGram link tools). */
public final class XgramLinks {
  private XgramLinks () { }

  /** Tracking parameters removed from any site. */
  private static final Set<String> TRACKING = new HashSet<>(Arrays.asList(
    "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid", "li_fat_id",
    "igshid", "igsh", "mc_cid", "mc_eid", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id",
    "vero_id", "wickedid", "rb_clickid", "s_cid", "ref_src", "ref_url", "spm", "scm", "share_id"
  ));

  /** Parameters that are only tracking on some sites. */
  private static boolean isSiteTracking (String host, String name) {
    if (host.endsWith("youtube.com") || host.equals("youtu.be")) {
      return name.equals("si") || name.equals("feature") || name.equals("pp");
    }
    if (host.endsWith("spotify.com")) {
      return name.equals("si") || name.equals("context");
    }
    if (host.equals("x.com") || host.endsWith(".x.com") || host.endsWith("twitter.com")) {
      return name.equals("s") || name.equals("t");
    }
    if (host.endsWith("instagram.com") || host.endsWith("threads.net")) {
      // img_index is not tracking: it selects the photo of a carousel.
      return name.equals("utm_source") || name.equals("igsh");
    }
    if (host.endsWith("tiktok.com")) {
      return name.startsWith("_") || name.equals("is_from_webapp") || name.equals("sender_device") || name.equals("web_id");
    }
    if (host.endsWith("amazon.com") || host.contains(".amazon.")) {
      return name.equals("ref") || name.startsWith("pd_rd_") || name.startsWith("pf_rd_") || name.equals("psc") || name.equals("tag");
    }
    if (host.endsWith("reddit.com")) {
      return name.equals("share_id") || name.equals("utm_name") || name.equals("ref");
    }
    return false;
  }

  public static String clean (String url) {
    if (!XgramSettings.instance().cleanLinks() || url == null || url.indexOf('?') == -1) {
      return url;
    }
    try {
      Uri uri = Uri.parse(url);
      String scheme = uri.getScheme();
      if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
        return url;
      }
      String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
      String query = uri.getEncodedQuery();
      if (query == null || query.isEmpty()) {
        return url;
      }
      // Only tracking segments are dropped; everything else stays exactly as it was (order, encoding, flags), as
      // some servers check the raw query (signed links).
      StringBuilder kept = new StringBuilder(query.length());
      boolean changed = false;
      for (String segment : query.split("&", -1)) {
        int eq = segment.indexOf('=');
        String name = Uri.decode(eq == -1 ? segment : segment.substring(0, eq)).toLowerCase(Locale.ROOT);
        if (!name.isEmpty() && (name.startsWith("utm_") || TRACKING.contains(name) || isSiteTracking(host, name))) {
          changed = true;
          continue;
        }
        if (kept.length() > 0) {
          kept.append('&');
        }
        kept.append(segment);
      }
      if (!changed) {
        return url;
      }
      return uri.buildUpon().encodedQuery(kept.length() > 0 ? kept.toString() : null).build().toString();
    } catch (Throwable t) {
      return url;
    }
  }
}
