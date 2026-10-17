@file:JvmName("Flavor")
@file:JvmMultifileClass

package tgx.flavor

import com.fahadali.xgram.call.XgramVideoBackend

// XGram: no calls in the legacy flavor (no WebRTC)

fun createVideoBackend(): XgramVideoBackend? = null

fun isVideoCallsSupported(): Boolean = false
