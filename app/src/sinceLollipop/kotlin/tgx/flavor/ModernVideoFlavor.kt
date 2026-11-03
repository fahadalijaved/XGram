@file:JvmName("Flavor")
@file:JvmMultifileClass

package tgx.flavor

import com.fahadali.xgram.call.XgramVideoBackend
import com.fahadali.xgram.call.video.XgramVideoBackendImpl

// XGram: video calls (WebRTC's camera capturer and renderers come with :tgcalls)

fun createVideoBackend(): XgramVideoBackend? = XgramVideoBackendImpl()

fun isVideoCallsSupported(): Boolean = true

// XGram: a photo edit with GIFs or animated stickers on it can be sent as a GIF (Media3 Transformer, image input)

fun isAnimatedPhotoEditSupported(): Boolean = true

// XGram: the GIF is encoded as it shows (a portrait one isn't turned sideways with a rotation tag, which Telegram for
// iOS doesn't play properly)

fun setGifPortraitEncoding(builder: androidx.media3.transformer.Transformer.Builder) {
  builder.setPortraitEncodingEnabled(true)
}
