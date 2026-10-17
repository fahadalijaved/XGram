@file:JvmName("Flavor")
@file:JvmMultifileClass

package tgx.flavor

import com.fahadali.xgram.call.XgramVideoBackend
import com.fahadali.xgram.call.video.XgramVideoBackendImpl

// XGram: video calls (WebRTC's camera capturer and renderers come with :tgcalls)

fun createVideoBackend(): XgramVideoBackend? = XgramVideoBackendImpl()

fun isVideoCallsSupported(): Boolean = true
