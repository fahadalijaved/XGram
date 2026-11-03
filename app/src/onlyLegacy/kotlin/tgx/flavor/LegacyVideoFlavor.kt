@file:JvmName("Flavor")
@file:JvmMultifileClass

package tgx.flavor

import com.fahadali.xgram.call.XgramVideoBackend

// XGram: no calls in the legacy flavor (no WebRTC)

fun createVideoBackend(): XgramVideoBackend? = null

fun isVideoCallsSupported(): Boolean = false

// XGram: a photo edit with GIFs or animated stickers on it stays a still photo here (Media3 1.2.1)

fun isAnimatedPhotoEditSupported(): Boolean = false

// XGram: no setPortraitEncodingEnabled in Media3 1.2.1 (and no GIF of an edit here)

fun setGifPortraitEncoding(builder: androidx.media3.transformer.Transformer.Builder) { }
