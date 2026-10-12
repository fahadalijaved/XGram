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

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.helper.FoundUrls;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import me.vkryl.core.StringUtils;
import tgx.td.ChatId;
import tgx.td.Td;

/**
 * Link preview fixer (NagramX "Fix link preview"). Messages you send whose previewed link is a post on a site with
 * broken Telegram previews get the preview of an embed-friendly mirror through {@link TdApi.LinkPreviewOptions#url}.
 * The message text keeps the original link. Received messages are never changed.
 */
public final class XgramLinkFixer {
  private XgramLinkFixer () { }

  // Mirror hosts, in one place for when a service moves.
  private static final String MIRROR_X = "fixupx.com";
  private static final String MIRROR_TWITTER = "fxtwitter.com";
  private static final String MIRROR_INSTAGRAM = "kkinstagram.com";
  private static final String MIRROR_TIKTOK = "vxtiktok.com";
  private static final String MIRROR_REDDIT = "rxddit.com";
  private static final String MIRROR_BLUESKY = "fxbsky.app";
  private static final String MIRROR_PIXIV = "phixiv.net";

  /** Quick test before asking TDLib for the links of a text. */
  private static final Pattern MAYBE_SUPPORTED = Pattern.compile("(?i)(?:x|twitter|instagram|tiktok|reddit|pixiv)\\.(?:com|net)|bsky\\.app");
  private static final Pattern TWEET = Pattern.compile("/status(?:es)?/\\d+");
  private static final Pattern INSTAGRAM_POST = Pattern.compile("^/(?:[A-Za-z0-9._]+/)?(?:p|reels?|tv)/[A-Za-z0-9_-]+");
  private static final Pattern TIKTOK_POST = Pattern.compile("^/(?:@[^/]+/(?:video|photo)/\\d+|t/[A-Za-z0-9]+)");
  private static final Pattern TIKTOK_SHORT = Pattern.compile("^/[A-Za-z0-9]+/?$");
  private static final Pattern REDDIT_POST = Pattern.compile("^/(?:r|u|user)/[^/]+/(?:comments|s)/[A-Za-z0-9]+");
  private static final Pattern BLUESKY_POST = Pattern.compile("^/profile/[^/]+/post/[A-Za-z0-9]+");
  private static final Pattern PIXIV_ARTWORK = Pattern.compile("^/(?:[a-z]{2}/)?artworks/\\d+");

  /** Mirrors that had no preview in the composer during this session: those links are sent as they are. */
  private static final Set<String> failedMirrors = new LinkedHashSet<>();

  public static void onMirrorFailed (String mirrorUrl) {
    if (StringUtils.isEmpty(mirrorUrl)) {
      return;
    }
    synchronized (failedMirrors) {
      failedMirrors.add(mirrorUrl);
      if (failedMirrors.size() > 50) {
        failedMirrors.remove(failedMirrors.iterator().next());
      }
    }
  }

  private static boolean hasFailed (String mirrorUrl) {
    synchronized (failedMirrors) {
      return failedMirrors.contains(mirrorUrl);
    }
  }

  /** Mirror URL of a link to a supported post, or null. Doesn't look at the setting. */
  public static @Nullable String mirror (@Nullable String link) {
    if (StringUtils.isEmpty(link)) {
      return null;
    }
    try {
      String url = link.trim();
      if (!url.regionMatches(true, 0, "http://", 0, 7) && !url.regionMatches(true, 0, "https://", 0, 8)) {
        if (url.contains("://")) {
          return null;
        }
        url = "https://" + url;
      }
      Uri uri = Uri.parse(XgramLinks.clean(url)); // no tracking parameters in the mirror either
      String host = uri.getHost();
      if (host == null) {
        return null;
      }
      host = host.toLowerCase(Locale.ROOT);
      if (host.startsWith("www.")) {
        host = host.substring(4);
      }
      String path = uri.getEncodedPath();
      if (path == null) {
        path = "";
      }
      String query = null;
      String mirrorHost;
      switch (host) {
        case "x.com":
        case "mobile.x.com":
          mirrorHost = TWEET.matcher(path).find() ? MIRROR_X : null;
          break;
        case "twitter.com":
        case "mobile.twitter.com":
        case "m.twitter.com":
          mirrorHost = TWEET.matcher(path).find() ? MIRROR_TWITTER : null;
          break;
        case "instagram.com":
        case "m.instagram.com":
          mirrorHost = INSTAGRAM_POST.matcher(path).find() ? MIRROR_INSTAGRAM : null;
          query = uri.getEncodedQuery(); // img_index selects the photo of a carousel
          break;
        case "tiktok.com":
        case "m.tiktok.com":
          mirrorHost = TIKTOK_POST.matcher(path).find() ? MIRROR_TIKTOK : null;
          break;
        case "vm.tiktok.com":
        case "vt.tiktok.com":
          mirrorHost = TIKTOK_SHORT.matcher(path).find() ? host.substring(0, 3) + MIRROR_TIKTOK : null;
          break;
        case "reddit.com":
        case "old.reddit.com":
        case "new.reddit.com":
        case "m.reddit.com":
        case "np.reddit.com":
        case "sh.reddit.com":
          mirrorHost = REDDIT_POST.matcher(path).find() ? MIRROR_REDDIT : null;
          break;
        case "bsky.app":
          mirrorHost = BLUESKY_POST.matcher(path).find() ? MIRROR_BLUESKY : null;
          break;
        case "pixiv.net":
          mirrorHost = PIXIV_ARTWORK.matcher(path).find() ? MIRROR_PIXIV : null;
          break;
        default:
          return null;
      }
      if (mirrorHost == null) {
        return null;
      }
      return new Uri.Builder()
        .scheme("https")
        .encodedAuthority(mirrorHost)
        .encodedPath(path)
        .encodedQuery(StringUtils.isEmpty(query) ? null : query)
        .build()
        .toString();
    } catch (Throwable t) {
      return null;
    }
  }

  /** The link TDLib will preview: the chosen one, else the first one, found the same way as the composer does. */
  private static @Nullable String previewedUrl (TdApi.FormattedText text, @Nullable TdApi.LinkPreviewOptions options) {
    if (options != null && !StringUtils.isEmpty(options.url)) {
      return options.url;
    }
    if (!mightHaveSupportedLink(text)) {
      return null;
    }
    FoundUrls found = new FoundUrls(text);
    return found.isEmpty() ? null : found.urls[0];
  }

  private static boolean mightHaveSupportedLink (TdApi.FormattedText text) {
    if (text.text != null && MAYBE_SUPPORTED.matcher(text.text).find()) {
      return true;
    }
    if (text.entities != null) {
      for (TdApi.TextEntity entity : text.entities) {
        if (entity.type != null && entity.type.getConstructor() == TdApi.TextEntityTypeTextUrl.CONSTRUCTOR && MAYBE_SUPPORTED.matcher(((TdApi.TextEntityTypeTextUrl) entity.type).url).find()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Options to send {@code text} with: a copy whose {@code url} is the mirror when the previewed link is a supported
   * post, otherwise {@code options} itself. Never changes {@code options}.
   */
  public static @Nullable TdApi.LinkPreviewOptions apply (long chatId, @Nullable TdApi.FormattedText text, @Nullable TdApi.LinkPreviewOptions options, int maxTextLength) {
    if (!XgramSettings.instance().fixLinkPreviews() || ChatId.isSecret(chatId) || Td.isEmpty(text) || (options != null && options.isDisabled)) {
      return options;
    }
    if (text.text.codePointCount(0, text.text.length()) > maxTextLength) {
      // TD.explodeText gives every part the same options: all parts would show this preview.
      return options;
    }
    String mirror = mirror(previewedUrl(text, options));
    if (mirror == null || hasFailed(mirror)) {
      return options;
    }
    TdApi.LinkPreviewOptions fixed = Td.copyOf(options);
    fixed.url = mirror;
    return fixed;
  }

  /** {@link #apply} for a ready content (text shared from other apps). Returns {@code content} when nothing changes. */
  public static TdApi.InputMessageContent applyToContent (long chatId, TdApi.InputMessageContent content, int maxTextLength) {
    if (content == null || content.getConstructor() != TdApi.InputMessageText.CONSTRUCTOR) {
      return content;
    }
    TdApi.InputMessageText text = (TdApi.InputMessageText) content;
    TdApi.LinkPreviewOptions options = apply(chatId, text.text, text.linkPreviewOptions, maxTextLength);
    return options == text.linkPreviewOptions ? content : new TdApi.InputMessageText(text.text, options, text.clearDraft);
  }

  /**
   * Edits keep the mirror a message already shows. A mirror is only added when the text changes, so opening and
   * closing the editor never counts as a change.
   */
  public static @Nullable TdApi.LinkPreviewOptions applyToEdit (long chatId, @Nullable TdApi.MessageText oldContent, @Nullable TdApi.FormattedText newText, @Nullable TdApi.LinkPreviewOptions newOptions, int maxTextLength) {
    if (oldContent == null || Td.isEmpty(newText) || (newOptions != null && newOptions.isDisabled)) {
      return newOptions;
    }
    String link = previewedUrl(newText, newOptions);
    if (link == null) {
      return newOptions;
    }
    TdApi.LinkPreviewOptions oldOptions = oldContent.linkPreviewOptions;
    if (isMirrorOf(oldOptions, link)) {
      TdApi.LinkPreviewOptions kept = Td.copyOf(newOptions);
      kept.url = oldOptions.url;
      return kept;
    }
    if (Td.equalsTo(oldContent.text, newText)) {
      return newOptions;
    }
    return apply(chatId, newText, newOptions, maxTextLength);
  }

  /** Mirror the composer previews for {@code url}, or null to preview the link itself. */
  public static @Nullable String composerPreviewUrl (long chatId, String url) {
    if (!XgramSettings.instance().fixLinkPreviews() || ChatId.isSecret(chatId)) {
      return null;
    }
    String mirror = mirror(url);
    return mirror != null && !hasFailed(mirror) ? mirror : null;
  }

  /** True if {@code options} use the mirror of {@code url}: the message already shows the fixed preview. */
  public static boolean isMirrorOf (@Nullable TdApi.LinkPreviewOptions options, @Nullable String url) {
    if (options == null || StringUtils.isEmpty(options.url)) {
      return false;
    }
    String mirror = mirror(url);
    return mirror != null && FoundUrls.compareUrls(options.url, mirror);
  }
}
