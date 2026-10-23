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
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fahadali.xgram.update

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fahadali.xgram.appearance.XgramMotion
import com.fahadali.xgram.appearance.XgramR6B3Shell
import com.fahadali.xgram.compose.XgramComposeFlags
import com.fahadali.xgram.compose.XgramComposeOwner
import com.fahadali.xgram.compose.theme.XgramM3Theme
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.pages.URL_SUPPORT
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.xdrive.XDriveThumbs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.vkryl.core.lambda.RunnableBool
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.BaseActivity
import org.thunderdog.challegram.BuildConfig
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.telegram.TdlibUi
import org.thunderdog.challegram.theme.Theme
import org.thunderdog.challegram.tool.Strings
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.util.AppUpdater
import java.lang.ref.WeakReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SCRIM = .32f
private val CARD_MAX_WIDTH = 560.dp
private val CARD_MARGIN = 16.dp
private const val ENTER_SCALE = .9f

/** The hero takes 30% of the height there is, 120 to 200dp; below [HERO_SPACE] (split screen, landscape) there is none. */
private const val HERO_SHARE = .3f
private val HERO_MIN_HEIGHT = 120.dp
private val HERO_MAX_HEIGHT = 200.dp
private val HERO_SPACE = 360.dp

/** The top of the hero kept for the chips: the logo is centered below it. */
private val HERO_CHIPS = 40.dp
private val CHIPS_INSET = 16.dp

/** The hero folded as the body scrolls up: a band with the chips, Open post and a small logo in its middle. */
private val HERO_BAND = 72.dp
private val BAND_LOGO = 36.dp

/** The decorations are gone by two thirds of the fold. */
private const val DECOR_FADE = 1.5f
private const val HERO_PHOTO_SIZE = 1280
private const val DRIFT_MS = 40_000
private const val BREATHE_MS = 2_600

private val BIG_BUTTON = 56.dp
private val FLAT_WAVE: (Float) -> Float = { 0f }

/** See all changes stops What's new this far under the top of the body. */
private val REVEAL_MARGIN = 16.dp

/** Frames (a quarter to half a second) the reveal follows the new content's own size spring after its glide. */
private const val REVEAL_FOLLOW_FRAMES = 30

/** A download the updater hasn't started after this long is given up, with an error line. */
private const val START_TIMEOUT_MS = 15_000L

private const val PREVIEW_SIZE = 65_640_448L // 62.6 MB, an arm64 APK
private const val PREVIEW_STEPS = 48
private const val PREVIEW_DOWNLOAD_MS = 4_000L
private const val PREVIEW_CHECK_MS = 1_600L
private const val PREVIEW_TIMEOUT_MS = 8_000L

/**
 * XGram's update screen, "Software update": a card over whatever screen is open. On top a hero (the app's icon in a
 * shape morphing between a soft burst and a 12-sided cookie, over Material shapes drifting on the theme's gradient, or
 * the photo of the release notes when the bot posted one; it folds to a band as the notes scroll up), then the version,
 * the top three changes of the notes with "See all changes" (every change, grouped by section, in their place), and one
 * button that downloads, checks (SHA-256) and installs the update through [AppUpdater]:
 * the download's progress, Continue in background and Cancel, then "Checking the file…" and the installer, without a
 * second tap. Later, the scrim and Back close it; the update is offered once, as before.
 *
 * It is the update prompt (Ask first) in both designs, What's new, the Updates page's actions and the chat list's
 * Install. Settings → tap the version → Application Logs → Testing Utilities → Preview update screen shows it with the
 * newest post, whatever the installed build, and only pretends to download and install.
 *
 * The window is a platform [Dialog] holding a ComposeView with its own lifecycle, as XgramR5ChatPickers: the 32% scrim,
 * the app's Compose theme (dynamic colors, Telegram X's dark mode). The window only fades; the card springs in and out
 * itself (fades only with Reduce motion, which also stops the hero's motion).
 */
object XgramUpdateScreen {
  private var showing: WeakReference<ScreenState>? = null

  /** The update found as it stands (the prompt, Settings → Updates). False when there is none or it can't be shown. */
  @JvmStatic
  fun show(controller: ViewController<*>): Boolean = showUpdate(controller, installNow = false)

  /** The downloaded update, straight on to the file check and the installer (the chat list's Install). */
  @JvmStatic
  fun install(controller: ViewController<*>): Boolean = showUpdate(controller, installNow = true)

  /** What's new: [found] with every change shown. False when it can't be shown. */
  @JvmStatic
  fun showWhatsNew(controller: ViewController<*>, found: XgramUpdates.Found): Boolean {
    val live = controller.context().appUpdater().xgramUpdate() === found
    return open(controller, ScreenState(UpdateModel.of(found), preview = false, live = live, expanded = true, installNow = false))
  }

  /**
   * Testing Utilities: the screen with the newest post of the update channel even when it isn't newer than this build
   * (a sample when there is none, or offline). Install now pretends: about 4 seconds of download, the file check, then
   * "Preview: nothing was installed".
   */
  @JvmStatic
  fun preview(controller: ViewController<*>) {
    val screen = ScreenState(null, preview = true, live = false, expanded = false, installNow = false)
    if (!open(controller, screen)) {
      return
    }
    val tdlib = controller.tdlib()
    if (tdlib == null) {
      screen.model = UpdateModel.sample()
      return
    }
    XgramUpdates.findLatest(tdlib) { found ->
      UI.post {
        if (screen.model == null) {
          screen.model = if (found != null) UpdateModel.of(found) else UpdateModel.sample()
        }
      }
    }
  }

  private fun showUpdate(controller: ViewController<*>, installNow: Boolean): Boolean {
    val found = controller.context().appUpdater().xgramUpdate() ?: return false
    return open(controller, ScreenState(UpdateModel.of(found), preview = false, live = true, expanded = false, installNow = installNow))
  }

  private fun open(controller: ViewController<*>, screen: ScreenState): Boolean {
    if (controller.isDestroyed) {
      return false
    }
    val current = showing?.get()
    val currentDialog = current?.dialog
    if (current != null && currentDialog != null && currentDialog.isShowing && current.activity === controller.context()) {
      // One card at a time: the prompt and a tap on Install never stack. Over the preview the prompt isn't shown, so
      // the updater offers that update again at its next check
      return current.preview == screen.preview
    }
    return try {
      val context = controller.context()
      val owner = XgramComposeOwner()
      val dialog: Dialog = object : Dialog(context) {
        @Deprecated("Deprecated in Java")
        override fun onBackPressed() {
          // The card leaves the way it came in; a running download goes on, as with Continue in background
          val close = screen.close
          if (close != null) {
            close()
          } else {
            dismiss()
          }
        }
      }
      screen.dialog = dialog
      screen.owner = owner
      screen.activity = context
      dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
      val dark = Theme.isDark()
      XgramComposeFlags.ensure()
      val view = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner.lifecycle))
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent {
          XgramM3Theme(dark = dark, themeVersion = 0) {
            // The window has no page around it: Telegram X's language direction, as XgramComposeApp gives pages
            val direction = if (Lang.rtl()) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
              UpdateScreen(controller, screen)
            }
          }
        }
      }
      dialog.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
      dialog.setCanceledOnTouchOutside(false)
      dialog.window?.let { window ->
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(SCRIM)
        // The window and its scrim only fade: the card springs in and out itself
        window.setWindowAnimations(R.style.XgramDialogFade)
      }
      dialog.setOnDismissListener {
        screen.closing = true
        context.removeActivityListener(screen)
        owner.destroy()
        if (showing?.get() === screen) {
          showing = null
        }
        val after = screen.afterClose
        screen.afterClose = null
        after?.invoke()
      }
      // RESUMED only while the activity is: opened in the background, the card waits for it (see ScreenState)
      owner.moveTo(if (context.activityState == UI.State.RESUMED) Lifecycle.State.RESUMED else Lifecycle.State.CREATED)
      dialog.show()
      context.addActivityListener(screen)
      showing = WeakReference(screen)
      true
    } catch (t: Throwable) {
      android.util.Log.e("XGram", "Update screen failed", t)
      false
    }
  }
}

/**
 * One card: what it shows ([model]; the preview's comes later) and how it was opened. It follows the activity, which
 * holds it weakly (the dialog strongly): in the background its lifecycle stops, so Compose's frame clock and the
 * hero's motion pause, and it goes with the activity. Main thread.
 */
private class ScreenState(model: UpdateModel?, val preview: Boolean, val live: Boolean, val expanded: Boolean, val installNow: Boolean) : BaseActivity.ActivityListener {
  var model by mutableStateOf(model)
  var dialog: Dialog? = null
  var owner: XgramComposeOwner? = null
  var activity: BaseActivity? = null

  /** The card's animated close, while it is shown: Back goes through it. */
  var close: (() -> Unit)? = null

  /** The card is leaving, or gone: a file check that ends now isn't shown on it. */
  var closing = false

  /** Runs once the window is gone: the post opens, the preview's snackbar shows. */
  var afterClose: (() -> Unit)? = null

  fun dismissNow() {
    val window = dialog
    if (window != null && window.isShowing) {
      window.dismiss()
    }
  }

  override fun onActivityPause() {
    if (closing) {
      dismissNow() // the installer opened over it: the exit would wait for the user to come back
    } else {
      owner?.moveTo(Lifecycle.State.CREATED)
    }
  }

  override fun onActivityResume() {
    owner?.moveTo(Lifecycle.State.RESUMED)
  }

  override fun onActivityDestroy() = dismissNow()

  override fun onActivityPermissionResult(code: Int, granted: Boolean) = Unit
}

/** What the card shows of an update: a post ([found]), or the preview's sample. */
private class UpdateModel(
  val version: String,
  val size: Long,
  val isStable: Boolean,
  val notes: XgramUpdateNotes,
  val photo: TdApi.Photo?,
  val tdlib: Tdlib?,
  val found: XgramUpdates.Found?
) {
  companion object {
    fun of(found: XgramUpdates.Found): UpdateModel {
      val document = found.file.document.document
      val size = if (document.size > 0) document.size else document.expectedSize
      return UpdateModel(found.versionName, size, found.isStable, XgramUpdateNotes.parse(found.changelog), found.changelogPhoto, found.tdlib, found)
    }

    fun sample(): UpdateModel = UpdateModel(
      BuildConfig.VERSION_NAME, PREVIEW_SIZE, XgramUpdates.channel() != XgramUpdates.CHANNEL_CI,
      XgramUpdateNotes.parse(s(R.string.XgramUpdPreviewSample)), null, null, null
    )
  }
}

/** What the card's button drives: [AppUpdater], or the preview's pretend download. Compose state, main thread. */
private abstract class UpdateFlow {
  var state by mutableIntStateOf(AppUpdater.State.AVAILABLE)
  var downloaded by mutableLongStateOf(0L)
  var total by mutableLongStateOf(0L)

  open fun start() = Unit

  open fun stop() = Unit

  abstract fun download()

  abstract fun cancel()

  /** Checks the file and opens the installer; [onChecked] gets false for a bad file. False when nothing started. */
  abstract fun install(onChecked: (Boolean) -> Unit): Boolean
}

/** The real update: [AppUpdater]'s state and progress, its download, cancel and install. */
private class LiveFlow(private val updater: AppUpdater) : UpdateFlow(), AppUpdater.Listener {
  /** The file check this card waits for: forgotten when the card goes, so a bad file gets the snackbar instead. */
  private var checked: RunnableBool? = null

  init {
    sync()
  }

  private fun sync() {
    state = updater.state()
    downloaded = updater.bytesDownloaded()
    total = updater.totalBytesToDownload()
  }

  override fun start() {
    updater.addListener(this)
    sync()
  }

  override fun stop() {
    updater.removeListener(this)
    checked?.let { XgramUpdates.forgetVerifyCallback(it) }
    checked = null
  }

  override fun onAppUpdateStateChanged(state: Int, oldState: Int, isApk: Boolean) = sync()

  override fun onAppUpdateDownloadProgress(bytesDownloaded: Long, totalBytesToDownload: Long) {
    downloaded = bytesDownloaded
    total = totalBytesToDownload
  }

  override fun download() = updater.downloadUpdate()

  override fun cancel() = updater.cancelDownload()

  override fun install(onChecked: (Boolean) -> Unit): Boolean {
    if (updater.state() != AppUpdater.State.READY_TO_INSTALL) {
      return false
    }
    val callback = RunnableBool { ok ->
      checked = null
      onChecked(ok)
    }
    checked = callback
    updater.installUpdate(callback)
    return true
  }
}

/** The preview: a pretend download of [size] bytes in about 4 seconds and a pretend check; nothing is installed. */
private class PreviewFlow(private val scope: CoroutineScope, size: Long) : UpdateFlow() {
  private var job: Job? = null

  init {
    total = size
  }

  override fun download() {
    if (state != AppUpdater.State.AVAILABLE) {
      return
    }
    downloaded = 0L
    state = AppUpdater.State.DOWNLOADING
    job = scope.launch {
      for (step in 1..PREVIEW_STEPS) {
        delay(PREVIEW_DOWNLOAD_MS / PREVIEW_STEPS)
        downloaded = total * step / PREVIEW_STEPS
      }
      state = AppUpdater.State.READY_TO_INSTALL
    }
  }

  override fun cancel() {
    job?.cancel()
    job = null
    if (state == AppUpdater.State.DOWNLOADING) {
      state = AppUpdater.State.AVAILABLE // the bytes stay, as AppUpdater's: the progress fades out where it stopped
    }
  }

  override fun install(onChecked: (Boolean) -> Unit): Boolean {
    scope.launch {
      delay(PREVIEW_CHECK_MS)
      onChecked(true)
    }
    return true
  }
}

private enum class Phase { OFFER, DOWNLOADING, CHECKING }

/** The card's own state over the flow's: what the user asked for, and what went wrong. */
private class CardState(installNow: Boolean, var previousState: Int) {
  /** Install now was tapped: once the file is here, the check and the installer follow without another tap. */
  var installRequested by mutableStateOf(installNow)

  /** The SHA-256 check is running. */
  var checking by mutableStateOf(false)

  /** A download was asked for and the updater hasn't said it started. */
  var downloadPending by mutableStateOf(false)

  /** The updater said the download asked for started: its block shows bytes, also while it fades out (done, canceled). */
  var downloadStarted by mutableStateOf(previousState == AppUpdater.State.DOWNLOADING)

  /** Download again after a bad file: it starts once the deleted file makes the update available again. */
  var downloadAgainPending = false
  var userCanceled = false
  var badFile by mutableStateOf(false)
  var error by mutableStateOf<String?>(null)

  fun phase(state: Int): Phase = when {
    checking || (state == AppUpdater.State.READY_TO_INSTALL && installRequested && !badFile) -> Phase.CHECKING
    state == AppUpdater.State.DOWNLOADING || downloadPending -> Phase.DOWNLOADING
    else -> Phase.OFFER
  }
}

/**
 * The hero folding as the body scrolls, as a collapsing top app bar: a scroll up first folds it down to its band, a
 * scroll down unfolds it once the body is back at its top. It folds only as far as the body has left to scroll, so a
 * card whose notes fit never shrinks under the finger; the hero's height changes the body's range, never the other way
 * round. [range] is set by the card ([resize]): 0 without a hero.
 */
private class HeroFold(private val body: ScrollState) : NestedScrollConnection {
  /** How far the hero can fold, px: its height less the band's. */
  var range by mutableFloatStateOf(0f)
    private set

  /** How far it is folded, px: 0 (open) to [range] (the band). */
  var folded by mutableFloatStateOf(0f)
    private set

  private var width = 0.dp

  /**
   * The hero's [range] in a card [width] wide. A new size (rotated, folded or unfolded: the notes reflow) opens it
   * again, as the fold may be more than the body has left to scroll now; the body clamps its own scroll.
   */
  fun resize(range: Float, width: Dp) {
    if (range != this.range || width != this.width) {
      this.range = range
      this.width = width
      folded = 0f
    }
  }

  /** 0 open, 1 folded down to the band. */
  val fraction: Float
    get() = if (range > 0f) (folded / range).coerceIn(0f, 1f) else 0f

  /**
   * Folds to [target], clamped (a spring's overshoot never goes past the band or the full height); further only by
   * [room] px.
   */
  fun foldTo(target: Float, room: Float) {
    val current = folded.coerceIn(0f, range)
    val next = target.coerceIn(0f, range)
    folded = if (next > current) minOf(next, current + room.coerceAtLeast(0f)) else next
  }

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (available.y >= 0f) {
      return Offset.Zero
    }
    // Up: the hero folds first, by what the body has left to scroll
    val before = folded.coerceIn(0f, range)
    foldTo(before - available.y, (body.maxValue - body.value).toFloat())
    return Offset(0f, before - folded)
  }

  override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
    if (available.y <= 0f) {
      return Offset.Zero
    }
    // Down past the body's top: the hero unfolds
    val before = folded.coerceIn(0f, range)
    foldTo(before - available.y, 0f)
    return Offset(0f, before - folded)
  }
}

/** Where What's new is in the body, for See all changes and Show less, and the reveal running. Main thread. */
private class ChangesReveal {
  /** The body's content, inside its scroll. */
  var content: LayoutCoordinates? = null
  var label: LayoutCoordinates? = null
  var job: Job? = null

  /** Show less is on its way up, the swap after it: a second tap changes nothing. */
  var hiding = false

  /** What's new's top in the body's content, px; null until both are placed. */
  fun labelTop(): Float? {
    val inside = content
    val of = label
    if (inside == null || of == null || !inside.isAttached || !of.isAttached) {
      return null
    }
    return inside.localPositionOf(of, Offset.Zero).y
  }

  val labelHeight: Int
    get() = label?.size?.height ?: 0
}

/**
 * See all changes, once the swap is laid out: the body glides so What's new is [margin] under its top, the changes
 * filling the card, and the hero folds with it ([glide]).
 */
private suspend fun revealChanges(
  scroll: ScrollState,
  hero: HeroFold,
  reveal: ChangesReveal,
  margin: Float,
  spec: AnimationSpec<Float>?
) {
  // The swap is composed in the next frame and laid out in it: the frame after knows where things are
  withFrameNanos { }
  withFrameNanos { }
  if (scroll.isScrollInProgress) {
    return // the user took over
  }
  val labelTop = reveal.labelTop() ?: return
  glide(scroll, hero, (labelTop - margin).coerceAtLeast(0f), hero.range, spec)
}

/**
 * Show less, before the swap, on the whole list (What's new is above it: it stays where it is): back to the top with
 * the hero open when What's new shows there that way, else What's new to the top as it is ([glide]). The list then
 * shrinks below the view and the top crossfades in place; the card never shrinks under a folded hero to grow back.
 */
private suspend fun hideChanges(
  scroll: ScrollState,
  hero: HeroFold,
  reveal: ChangesReveal,
  margin: Float,
  spec: AnimationSpec<Float>?
) {
  // A fling still going keeps the scroll (its priority): the swap only
  val labelTop = reveal.labelTop() ?: return
  val fromFold = hero.folded.coerceIn(0f, hero.range)
  // With the hero open the body is shorter by the fold (the card at its full height)
  val toTop = labelTop + reveal.labelHeight + margin <= scroll.viewportSize - fromFold
  glide(scroll, hero, if (toTop) 0f else (labelTop - margin).coerceAtLeast(0f), if (toTop) 0f else fromFold, spec)
}

/**
 * The body to [scrollTarget] and the hero to [foldTarget] together. The steps are absolute positions on [spec] (at once
 * without one), so new content growing on its own spring only clamps them, and a few frames more follow it to its end.
 * A touch on the body stops it (the scroll's own priority).
 */
private suspend fun glide(
  scroll: ScrollState,
  hero: HeroFold,
  scrollTarget: Float,
  foldTarget: Float,
  spec: AnimationSpec<Float>?
) {
  val fromScroll = scroll.value.toFloat()
  val fromFold = hero.folded.coerceIn(0f, hero.range)
  scroll.scroll {
    val step: (Float) -> Unit = { p ->
      scrollBy(fromScroll + (scrollTarget - fromScroll) * p - scroll.value)
      // Then the fold, by what the body has left after it: no clamp, no jump, on the next layout
      hero.foldTo(fromFold + (foldTarget - fromFold) * p, (scroll.maxValue - scroll.value).toFloat())
    }
    if (spec == null) {
      step(1f)
    } else {
      animate(0f, 1f, animationSpec = spec) { p, _ -> step(p) }
    }
    var frames = 0
    while (frames < REVEAL_FOLLOW_FRAMES && (scroll.value != scrollTarget.roundToInt() || hero.folded != foldTarget.coerceIn(0f, hero.range))) {
      withFrameNanos { }
      step(1f)
      frames++
    }
  }
}

/** Fades for state changes; instant with Reduce motion. */
@Composable
private fun <T> effectsSpec(still: Boolean): FiniteAnimationSpec<T> =
  if (still) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

/** The card's height following a new content; instant with Reduce motion. */
private fun sizeTransform(still: Boolean): SizeTransform =
  if (still) SizeTransform(clip = false) { _, _ -> snap() } else SizeTransform(clip = false)

@Composable
private fun UpdateScreen(controller: ViewController<*>, screen: ScreenState) {
  val motion = MaterialTheme.motionScheme
  val still = remember { XgramMotion.reduce() }
  val scope = rememberCoroutineScope()
  val grow = remember { Animatable(if (still) 1f else ENTER_SCALE) }
  val fade = remember { Animatable(0f) }
  val close: () -> Unit = remember {
    {
      if (!screen.closing) {
        screen.closing = true
        scope.launch {
          coroutineScope {
            if (!still) {
              launch { grow.animateTo(ENTER_SCALE, motion.fastSpatialSpec()) }
            }
            fade.animateTo(0f, motion.fastEffectsSpec())
          }
          screen.dismissNow()
        }
      }
    }
  }
  DisposableEffect(screen) {
    screen.close = close
    onDispose { screen.close = null }
  }
  LaunchedEffect(Unit) {
    launch { fade.animateTo(1f, motion.defaultEffectsSpec()) }
    if (!still) {
      grow.animateTo(1f, motion.defaultSpatialSpec())
    }
  }
  val effects = effectsSpec<Float>(still)
  // The body's scroll lives here for the hero, which folds as the body scrolls up
  val bodyScroll = rememberScrollState()
  val heroFold = remember { HeroFold(bodyScroll) }
  // The scrim: a tap on it closes the card (no semantics: TalkBack closes it with Back or Later)
  Box(
    Modifier
      .fillMaxSize()
      .pointerInput(Unit) { detectTapGestures(onTap = { close() }) }
  ) {
    BoxWithConstraints(
      Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(CARD_MARGIN),
      contentAlignment = Alignment.Center
    ) {
      // A card, not a stretched sheet, on the unfolded Fold and tablets; 312dp on the 344dp cover screen
      val width = if (maxWidth < CARD_MAX_WIDTH) maxWidth else CARD_MAX_WIDTH
      val heroHeight = if (maxHeight < HERO_SPACE) 0.dp else (maxHeight * HERO_SHARE).coerceIn(HERO_MIN_HEIGHT, HERO_MAX_HEIGHT)
      val heroRange = with(LocalDensity.current) { (heroHeight - HERO_BAND).coerceAtLeast(0.dp).toPx() }
      SideEffect { heroFold.resize(heroRange, width) }
      Column(
        Modifier
          .width(width)
          .heightIn(max = maxHeight)
          .graphicsLayer {
            scaleX = grow.value
            scaleY = grow.value
            alpha = fade.value.coerceIn(0f, 1f)
          }
          .clip(MaterialTheme.shapes.extraLargeIncreased)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh)
          .pointerInput(Unit) { detectTapGestures { } } // taps on the card never reach the scrim
          .nestedScroll(heroFold)
      ) {
        val model = screen.model
        val found = model?.found
        val openPost: (() -> Unit)? = if (found == null) null else ({
          screen.afterClose = { XgramUpdates.openPost(controller, found) }
          close()
        })
        val hero = heroHeight > 0.dp
        if (hero) {
          Hero(model, screen.preview, heroHeight, heroFold, still, openPost)
        }
        AnimatedContent(
          targetState = model,
          modifier = Modifier.weight(1f, fill = false),
          transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) using sizeTransform(still) },
          contentKey = { it != null },
          label = "xgramUpdateBody"
        ) { shown ->
          if (shown == null) {
            LoadingBody(screen, still, close)
          } else {
            // Without the hero, its Open post and Preview tag go to the title block
            UpdateBody(
              controller, screen, shown, still, bodyScroll, heroFold,
              openPost = if (hero) null else openPost, previewTag = screen.preview && !hero, close = close
            )
          }
        }
      }
    }
  }
}

/** The preview while it looks for the newest post: the sample after [PREVIEW_TIMEOUT_MS] (offline). */
@Composable
private fun LoadingBody(screen: ScreenState, still: Boolean, close: () -> Unit) {
  val c = MaterialTheme.colorScheme
  LaunchedEffect(Unit) {
    delay(PREVIEW_TIMEOUT_MS)
    if (screen.model == null) {
      screen.model = UpdateModel.sample()
    }
  }
  Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)) {
    Text(
      s(R.string.XgramUpdTitle),
      style = MaterialTheme.typography.headlineSmallEmphasized,
      color = c.onSurface,
      modifier = Modifier.semantics { heading() }
    )
    Text(
      s(R.string.XgramUpdLooking),
      style = MaterialTheme.typography.bodyLarge,
      color = c.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp)
    )
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
      if (still) {
        StillIndicator(64.dp)
      } else {
        LoadingIndicator(modifier = Modifier.size(64.dp))
      }
    }
    TextButton(onClick = close, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
      Text(s(R.string.XgramUpdLater))
    }
  }
}

@Composable
private fun UpdateBody(
  controller: ViewController<*>,
  screen: ScreenState,
  model: UpdateModel,
  still: Boolean,
  scroll: ScrollState,
  hero: HeroFold,
  openPost: (() -> Unit)?,
  previewTag: Boolean,
  close: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val flow = remember(model) {
    if (screen.preview) {
      PreviewFlow(scope, if (model.size > 0) model.size else PREVIEW_SIZE)
    } else {
      LiveFlow(controller.context().appUpdater())
    }
  }
  DisposableEffect(flow) {
    flow.start()
    onDispose { flow.stop() }
  }
  val card = remember(flow) { CardState(screen.installNow, flow.state) }
  // What's new for an update that isn't the updater's any more: the notes and Later only
  val actionable = screen.preview || screen.live
  val state = flow.state

  fun startInstall() {
    if (card.checking || flow.state != AppUpdater.State.READY_TO_INSTALL) {
      return
    }
    card.checking = true
    card.error = null
    val started = flow.install { ok ->
      if (screen.closing) {
        // Closed while the file was checked: a bad file gets the snackbar with Download again, as without the card
        if (!ok && !screen.preview) {
          XgramUpdates.showBadFile(controller.context().appUpdater()::downloadUpdate)
        }
        return@install
      }
      card.checking = false
      card.installRequested = false
      if (ok) {
        if (screen.preview) {
          screen.afterClose = { XgramR6B3Shell.snackbar(s(R.string.XgramUpdPreviewDone), false) }
        }
        close() // the installer is up (or, in the preview, nothing is installed)
      } else {
        card.badFile = true
        card.error = s(R.string.XgramUpdBadFile)
      }
    }
    if (!started) {
      card.checking = false
      card.installRequested = false
    }
  }

  LaunchedEffect(state) {
    val previous = card.previousState
    when (state) {
      AppUpdater.State.DOWNLOADING -> {
        card.downloadPending = false
        card.downloadStarted = true
      }
      AppUpdater.State.READY_TO_INSTALL -> {
        card.downloadPending = false
        if (card.installRequested && !card.badFile) {
          startInstall() // downloaded while the card is open: on to the check and the installer
        }
      }
      AppUpdater.State.AVAILABLE -> {
        if (previous == AppUpdater.State.DOWNLOADING) {
          // Stopped: by Cancel, or by TDLib (an error, the file gone). The preview's pretend download stops only by
          // Cancel: no line without it
          card.installRequested = false
          card.downloadPending = false
          card.error = when {
            card.userCanceled -> s(R.string.XgramUpdCanceled)
            screen.preview -> null
            else -> s(R.string.XgramUpdStopped)
          }
          card.userCanceled = false
        }
        if (card.downloadAgainPending) {
          card.downloadAgainPending = false
          card.badFile = false
          card.installRequested = true
          card.downloadPending = true
          flow.download()
        }
      }
      else -> if (screen.live) {
        close() // the update went away (another update channel)
      }
    }
    card.previousState = state
  }

  LaunchedEffect(card.downloadPending) {
    if (card.downloadPending) {
      delay(START_TIMEOUT_MS)
      if (card.downloadPending && flow.state != AppUpdater.State.DOWNLOADING) {
        card.downloadPending = false
        card.downloadAgainPending = false
        card.installRequested = false
        card.error = s(R.string.XgramUpdNotStarted)
      }
    }
  }

  val onPrimary: () -> Unit = {
    card.error = null
    when (flow.state) {
      AppUpdater.State.READY_TO_INSTALL -> if (card.badFile) {
        // The bad file is on its way out: the download starts once the update is offered again
        card.downloadAgainPending = true
        card.downloadPending = true
        card.downloadStarted = false
      } else {
        card.installRequested = true
        startInstall()
      }
      AppUpdater.State.AVAILABLE -> {
        card.badFile = false
        card.userCanceled = false
        card.installRequested = true
        card.downloadPending = true
        card.downloadStarted = false
        flow.download()
      }
      else -> Unit
    }
  }

  val onCancel: () -> Unit = {
    card.userCanceled = true
    if (flow.state != AppUpdater.State.DOWNLOADING) {
      // Asked for, not started yet
      card.downloadPending = false
      card.downloadAgainPending = false
      card.installRequested = false
      card.error = s(R.string.XgramUpdCanceled)
    }
    flow.cancel()
  }

  // What's new as opened (What's new: every change); See all changes and Show less bring the changes into view
  var expanded by remember { mutableStateOf(screen.expanded) }
  val reveal = remember { ChangesReveal() }
  val margin = with(LocalDensity.current) { REVEAL_MARGIN.toPx() }
  val revealSpec: AnimationSpec<Float>? = if (still) null else MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
  val toggle: (Boolean) -> Unit = { expand ->
    if (expand) {
      reveal.job?.cancel()
      expanded = true
      reveal.job = scope.launch { revealChanges(scroll, hero, reveal, margin, revealSpec) }
    } else if (!reveal.hiding) {
      // Up first on the whole list, then the swap: also when a touch stops the glide
      reveal.job?.cancel()
      reveal.hiding = true
      reveal.job = scope.launch {
        try {
          hideChanges(scroll, hero, reveal, margin, revealSpec)
        } finally {
          reveal.hiding = false
          expanded = false
        }
      }
    }
  }

  Column(Modifier.fillMaxWidth()) {
    // The notes scroll; the buttons stay at the bottom, whatever the font size
    Column(Modifier.weight(1f, fill = false).verticalScroll(scroll).onPlaced { reveal.content = it }) {
      TitleBlock(model, ready = state == AppUpdater.State.READY_TO_INSTALL, previewTag = previewTag, openPost = openPost)
      WhatsNew(model.notes, expanded, toggle, still, reveal)
      SupportLinks(controller, model.tdlib) { after ->
        screen.afterClose = after
        close()
      }
    }
    // A line over the buttons while more of the notes is below
    val divider by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, effectsSpec(still), label = "xgramUpdateDivider")
    Box(
      Modifier
        .fillMaxWidth()
        .height(1.dp)
        .graphicsLayer { alpha = divider }
        .background(MaterialTheme.colorScheme.outlineVariant)
    )
    Actions(flow, card, actionable, still, onPrimary, onCancel, close)
  }
}

/** Without the hero (less than [HERO_SPACE]): its Open post next to the title, the Preview tag after the kind. */
@Composable
private fun TitleBlock(model: UpdateModel, ready: Boolean, previewTag: Boolean, openPost: (() -> Unit)?) {
  val c = MaterialTheme.colorScheme
  val kindName = s(if (model.isStable) R.string.XgramUpdateKindStable else R.string.XgramUpdateKindCi)
  val kind = if (previewTag) s(R.string.XgramUpdSizeKind, kindName, s(R.string.XgramUpdPreviewTag)) else kindName
  // The 48dp button takes the title's place a little higher, so the title stays where it is without it
  Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = if (openPost != null) 12.dp else 20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        s(R.string.XgramUpdTitle),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        color = c.onSurface,
        modifier = Modifier.weight(1f).semantics { heading() }
      )
      if (openPost != null) {
        // Its icon lines up with the card's content; the button's own padding sits outside it
        IconButton(onClick = openPost, modifier = Modifier.padding(start = 8.dp).offset(x = 12.dp)) {
          Icon(tgxIcon(R.drawable.baseline_open_in_browser_24), contentDescription = s(R.string.XgramUpdateOpenPost), tint = c.onSurfaceVariant)
        }
      }
    }
    Text(
      s(if (ready) R.string.XgramUpdReady else R.string.XgramUpdAvailable, model.version),
      style = MaterialTheme.typography.bodyLarge,
      color = c.onSurface,
      modifier = Modifier.padding(top = 8.dp)
    )
    Text(
      if (model.size > 0) s(R.string.XgramUpdSizeKind, Strings.buildSize(model.size), kind) else kind,
      style = MaterialTheme.typography.bodyMedium,
      color = c.onSurfaceVariant,
      modifier = Modifier.padding(top = 2.dp)
    )
  }
}

/**
 * "What's new": the top three changes as rows with "See all changes (14)", which swaps them, in place, for every change
 * of the notes grouped by section, with Show less at its end ([toggle] brings them into view: [revealChanges],
 * [hideChanges]).
 */
@Composable
private fun WhatsNew(notes: XgramUpdateNotes, expanded: Boolean, toggle: (Boolean) -> Unit, still: Boolean, reveal: ChangesReveal) {
  val c = MaterialTheme.colorScheme
  val motion = MaterialTheme.motionScheme
  Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp)) {
    Text(
      s(R.string.XgramUpdWhatsNew),
      style = MaterialTheme.typography.labelLarge,
      color = c.primary,
      modifier = Modifier
        .padding(horizontal = 24.dp)
        .onPlaced { reveal.label = it }
        .semantics { heading() }
    )
    val body = notes.body
    when {
      notes.topNotes.isEmpty() -> Text(
        body ?: s(R.string.XgramUpdateNoChangelog),
        style = MaterialTheme.typography.bodyMedium,
        color = c.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)
      )
      !notes.hasMore -> TopChanges(notes, onMore = null)
      else -> {
        val effects = effectsSpec<Float>(still)
        AnimatedContent(
          targetState = expanded,
          transitionSpec = {
            fadeIn(effects) togetherWith fadeOut(effects) using SizeTransform(clip = true) { initial, target ->
              when {
                still -> snap<IntSize>()
                target.height > initial.height -> motion.defaultSpatialSpec<IntSize>()
                // Shrinking on the effects spring: no overshoot below the rows
                else -> motion.defaultEffectsSpec<IntSize>()
              }
            }
          },
          label = "xgramUpdateChanges"
        ) { all ->
          if (all) {
            AllChanges(notes, onLess = { toggle(false) })
          } else {
            TopChanges(notes, onMore = { toggle(true) })
          }
        }
      }
    }
  }
}

/** The top three changes, then See all changes with how many there are ([onMore] null: they are all there is). */
@Composable
private fun TopChanges(notes: XgramUpdateNotes, onMore: (() -> Unit)?) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
    notes.topNotes.forEachIndexed { index, note ->
      FeatureRow(note, index, Modifier.padding(top = if (index == 0) 16.dp else 20.dp))
    }
    if (onMore != null) {
      // Its text lines up with the rows; the button's own padding sits outside them
      TextButton(
        onClick = onMore,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.padding(top = 12.dp).offset(x = (-12).dp)
      ) {
        Text(s(R.string.XgramUpdSeeAll, Strings.buildCounter(notes.count.toLong())))
        Icon(
          tgxIcon(R.drawable.baseline_keyboard_arrow_down_24),
          contentDescription = null,
          modifier = Modifier.padding(start = 4.dp).size(18.dp)
        )
      }
    }
  }
}

/** The top features' containers: a different shape for each row. */
private fun featureShape(index: Int): RoundedPolygon = when (index % 3) {
  0 -> MaterialShapes.Cookie6Sided
  1 -> MaterialShapes.Clover4Leaf
  else -> MaterialShapes.Arch
}

/**
 * A top change by an icon picked from its words: "Popup menu" with "Message menu → Popup menu · off by default" and
 * what it does, or a line without a name as it is, in the body's style (it isn't a title).
 */
@Composable
private fun FeatureRow(note: XgramUpdateNotes.Note, index: Int, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  val container = if (index % 2 == 0) c.secondaryContainer else c.tertiaryContainer
  val content = if (index % 2 == 0) c.onSecondaryContainer else c.onTertiaryContainer
  val icon = remember(note) { XgramUpdateNotes.iconFor(note) }
  Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
    Box(
      Modifier.size(48.dp).clip(featureShape(index).toShape()).background(container),
      contentAlignment = Alignment.Center
    ) {
      Icon(tgxIcon(icon), contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
    }
    // A short change sits level with its icon, a longer one starts at its top
    Column(
      Modifier.weight(1f).heightIn(min = 48.dp).padding(start = 16.dp),
      verticalArrangement = Arrangement.Center
    ) {
      val name = note.name
      if (name == null) {
        Text(
          note.body,
          style = MaterialTheme.typography.bodyLarge,
          color = c.onSurface,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis
        )
      } else {
        Text(
          name,
          style = MaterialTheme.typography.titleMedium,
          color = c.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        val where = note.where
        if (where != null) {
          Text(
            where,
            style = MaterialTheme.typography.labelMedium,
            color = c.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
          )
        }
        Text(
          note.body,
          style = MaterialTheme.typography.bodyMedium,
          color = c.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 4.dp)
        )
      }
    }
  }
}

/**
 * Every change of the notes, the top three too: the intro, then each section under its header as one segmented group
 * (its own lines after it), then Show less and the closing line as small print. The groups are 16dp in, their text
 * and the headers' icons line up with the card's 24dp.
 */
@Composable
private fun AllChanges(notes: XgramUpdateNotes, onLess: () -> Unit) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    for (line in notes.intro) {
      Paragraph(line)
    }
    notes.sections.forEachIndexed { index, section ->
      val look = sectionLook(section.kind)
      val first = index == 0 && notes.intro.isEmpty()
      val titled = section.title.isNotEmpty()
      if (titled) {
        SectionHeader(section, look, Modifier.padding(top = if (first) 16.dp else 28.dp))
      }
      Column(
        Modifier.fillMaxWidth().padding(top = if (titled) 12.dp else if (first) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
      ) {
        section.notes.forEachIndexed { i, note ->
          ChangeItem(note, look, ListItemDefaults.segmentedShapes(index = i, count = section.notes.size))
        }
      }
      for (line in section.paragraphs) {
        Paragraph(line)
      }
    }
    // Its text lines up with the headers; the button's own padding sits outside it
    TextButton(
      onClick = onLess,
      shapes = ButtonDefaults.shapes(),
      modifier = Modifier.padding(top = 12.dp).offset(x = (-4).dp)
    ) {
      Text(s(R.string.XgramUpdSeeLess))
      Icon(
        tgxIcon(R.drawable.baseline_keyboard_arrow_up_24),
        contentDescription = null,
        modifier = Modifier.padding(start = 4.dp).size(18.dp)
      )
    }
    val footer = notes.footer
    if (footer != null) {
      Footer(footer)
    }
  }
}

/** A section's icon, the shape it sits in and their colors, by what the section is. */
private class SectionLook(@DrawableRes val icon: Int, val shape: RoundedPolygon, val container: Color, val content: Color)

@Composable
private fun sectionLook(kind: XgramUpdateNotes.Kind): SectionLook {
  val c = MaterialTheme.colorScheme
  return when (kind) {
    XgramUpdateNotes.Kind.NEW -> SectionLook(R.drawable.baseline_auto_awesome_24, MaterialShapes.SoftBurst, c.primaryContainer, c.onPrimaryContainer)
    XgramUpdateNotes.Kind.IMPROVED -> SectionLook(R.drawable.baseline_tune_24, MaterialShapes.Cookie6Sided, c.secondaryContainer, c.onSecondaryContainer)
    XgramUpdateNotes.Kind.FIXED -> SectionLook(R.drawable.baseline_bug_report_24, MaterialShapes.Clover4Leaf, c.tertiaryContainer, c.onTertiaryContainer)
    XgramUpdateNotes.Kind.KNOWN_ISSUES -> SectionLook(R.drawable.baseline_warning_24, MaterialShapes.Cookie4Sided, c.errorContainer, c.onErrorContainer)
    XgramUpdateNotes.Kind.TESTERS -> SectionLook(R.drawable.templarian_baseline_flask_24, MaterialShapes.Pentagon, c.surfaceContainerHighest, c.onSurface)
    XgramUpdateNotes.Kind.OTHER -> SectionLook(R.drawable.baseline_article_24, MaterialShapes.Circle, c.surfaceContainerHighest, c.onSurfaceVariant)
  }
}

/**
 * "🧪 For testers: members of …": the kind's icon in its shape, "For testers" with how many lines it has, the rest
 * under it. One heading for TalkBack ("For testers, 2 changes, members of …").
 */
@Composable
private fun SectionHeader(section: XgramUpdateNotes.Section, look: SectionLook, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  Row(
    modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp)
      .semantics(mergeDescendants = true) { heading() },
    verticalAlignment = Alignment.Top
  ) {
    Box(
      Modifier.size(40.dp).clip(look.shape.toShape()).background(look.container),
      contentAlignment = Alignment.Center
    ) {
      Icon(tgxIcon(look.icon), contentDescription = null, tint = look.content, modifier = Modifier.size(20.dp))
    }
    // A title alone sits level with the icon; with its text under it, it starts at the icon's top
    Column(
      Modifier.weight(1f).heightIn(min = 40.dp).padding(start = 16.dp),
      verticalArrangement = Arrangement.Center
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          section.title,
          style = MaterialTheme.typography.titleMedium,
          color = c.onSurface,
          modifier = Modifier.weight(1f, fill = false)
        )
        CountBadge(section.notes.size, look, Modifier.padding(start = 8.dp))
      }
      val description = section.description
      if (description != null) {
        Text(
          description,
          style = MaterialTheme.typography.bodyMedium,
          color = c.onSurfaceVariant,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
    }
  }
}

/** How many lines a section has, in a pill of its colors; TalkBack reads "6 changes". */
@Composable
private fun CountBadge(count: Int, look: SectionLook, modifier: Modifier) {
  val counter = Strings.buildCounter(count.toLong())
  val description = s(if (count == 1) R.string.XgramUpdChangesOne else R.string.XgramUpdChangesMany, counter)
  Text(
    counter,
    style = MaterialTheme.typography.labelMedium,
    color = look.content,
    textAlign = TextAlign.Center,
    maxLines = 1,
    modifier = modifier
      .clip(CircleShape)
      .background(look.container)
      .widthIn(min = 24.dp)
      .padding(horizontal = 8.dp, vertical = 2.dp)
      .clearAndSetSemantics { contentDescription = description }
  )
}

/**
 * One line of the notes in its section's group, whole: its name with what it does under it (and where it is, in the
 * accent), or a line without a name as it is, in the body's style. A small icon from its words (else the section's).
 * The rows are the card's lowest surface: white on its grey in the light, a recess in the dark and in Pure black.
 */
@Composable
private fun ChangeItem(note: XgramUpdateNotes.Note, look: SectionLook, shapes: ListItemShapes) {
  val c = MaterialTheme.colorScheme
  val icon = remember(note, look.icon) { XgramUpdateNotes.iconFor(note, look.icon) }
  val name = note.name
  SegmentedListItem(
    shapes = shapes,
    modifier = Modifier.fillMaxWidth(),
    leadingContent = { Icon(tgxIcon(icon), contentDescription = null, modifier = Modifier.size(20.dp)) },
    supportingContent = if (name == null) null else {
      {
        Column {
          val where = note.where
          if (where != null) {
            Text(
              where,
              style = MaterialTheme.typography.labelMedium,
              color = c.primary,
              modifier = Modifier.padding(bottom = 2.dp)
            )
          }
          Text(note.body)
        }
      }
    },
    colors = ListItemDefaults.segmentedColors(containerColor = c.surfaceContainerLowest),
    content = {
      if (name != null) {
        Text(name, style = MaterialTheme.typography.bodyLargeEmphasized)
      } else {
        Text(note.body)
      }
    }
  )
}

/** A line of the notes that isn't a "• " line, at the card's 24dp. */
@Composable
private fun Paragraph(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp)
  )
}

/**
 * Source code and Donate at the end of the notes (the post's footer lines about them become these buttons). Like Open
 * post, the card closes first: a link that asks before opening would ask under it.
 */
@Composable
private fun SupportLinks(controller: ViewController<*>, tdlib: Tdlib?, closeThen: (after: () -> Unit) -> Unit) {
  val c = MaterialTheme.colorScheme
  val source = BuildConfig.SOURCES_URL.ifEmpty { BuildConfig.REMOTE_URL }
  val open = { url: String ->
    closeThen {
      val ui = (tdlib ?: controller.tdlib())?.ui()
      if (!controller.isDestroyed && ui != null) {
        ui.openUrl(controller, url, TdlibUi.UrlOpenParameters().disableInstantView())
      }
    }
  }
  Row(
    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    FilledTonalButton(
      onClick = { open(source) },
      shapes = ButtonDefaults.shapes(),
      modifier = Modifier.weight(1f).heightIn(min = 48.dp)
    ) {
      Icon(
        tgxIcon(if (source.contains("github.com")) R.drawable.baseline_github_24 else R.drawable.baseline_code_24),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize)
      )
      Spacer(Modifier.width(ButtonDefaults.IconSpacing))
      Text(s(R.string.XgramUpdSource))
    }
    FilledTonalButton(
      onClick = { open(URL_SUPPORT) },
      shapes = ButtonDefaults.shapes(),
      colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.tertiaryContainer, contentColor = c.onTertiaryContainer),
      modifier = Modifier.weight(1f).heightIn(min = 48.dp)
    ) {
      Icon(tgxIcon(R.drawable.baseline_favorite_24), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
      Spacer(Modifier.width(ButtonDefaults.IconSpacing))
      Text(s(R.string.XgramUpdDonate))
    }
  }
}

/** "Based on Telegram X 0.29.1.1818." in italics, as in the post, then the rest (the Source and Support lines are buttons). */
@Composable
private fun Footer(footer: String) {
  val text = remember(footer) {
    footer.lines().filterNot { line ->
      val words = line.trimStart { !it.isLetterOrDigit() }
      words.startsWith("Source", ignoreCase = true) || words.startsWith("Support XGram", ignoreCase = true)
    }.joinToString("\n").trim()
  }
  if (text.isEmpty()) {
    return
  }
  val styled = remember(text) {
    val end = if (text.startsWith("Based on")) text.indexOf(". ").let { if (it < 0) text.length else it + 1 } else 0
    buildAnnotatedString {
      withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
        append(text.substring(0, end))
      }
      append(text.substring(end))
    }
  }
  Text(
    styled,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)
  )
}

/** The pinned bottom: Install now and Later, the download's progress, or the file check. */
@Composable
private fun Actions(
  flow: UpdateFlow,
  card: CardState,
  actionable: Boolean,
  still: Boolean,
  onPrimary: () -> Unit,
  onCancel: () -> Unit,
  close: () -> Unit
) {
  val phase = if (actionable) card.phase(flow.state) else Phase.OFFER
  val effects = effectsSpec<Float>(still)
  AnimatedContent(
    targetState = phase,
    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp),
    transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) using sizeTransform(still) },
    label = "xgramUpdateActions"
  ) { shown ->
    when (shown) {
      Phase.OFFER -> OfferActions(card, actionable, onPrimary, close)
      Phase.DOWNLOADING -> DownloadActions(flow, card, still, onCancel, close)
      Phase.CHECKING -> CheckingActions(still)
    }
  }
}

@Composable
private fun OfferActions(card: CardState, actionable: Boolean, onPrimary: () -> Unit, close: () -> Unit) {
  val c = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth()) {
    val error = card.error
    if (error != null) {
      Row(
        Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp)
          .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(tgxIcon(R.drawable.baseline_error_24), contentDescription = null, tint = c.error, modifier = Modifier.size(20.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = c.error, modifier = Modifier.padding(start = 8.dp))
      }
    }
    if (actionable) {
      Button(
        onClick = onPrimary,
        shapes = ButtonDefaults.shapesFor(BIG_BUTTON),
        modifier = Modifier.fillMaxWidth().heightIn(min = BIG_BUTTON),
        contentPadding = ButtonDefaults.contentPaddingFor(BIG_BUTTON)
      ) {
        Icon(
          tgxIcon(if (card.badFile) R.drawable.baseline_file_download_24 else R.drawable.baseline_system_update_24),
          contentDescription = null,
          modifier = Modifier.size(ButtonDefaults.iconSizeFor(BIG_BUTTON))
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(BIG_BUTTON)))
        // Two lines at large font sizes rather than cut: the button grows
        Text(
          s(if (card.badFile) R.string.XgramUpdDownloadAgain else R.string.XgramUpdInstallNow),
          style = ButtonDefaults.textStyleFor(BIG_BUTTON),
          textAlign = TextAlign.Center
        )
      }
    }
    TextButton(
      onClick = close,
      shapes = ButtonDefaults.shapes(),
      modifier = Modifier.fillMaxWidth().padding(top = if (actionable) 8.dp else 0.dp)
    ) {
      Text(s(R.string.XgramUpdLater))
    }
  }
}

@Composable
private fun DownloadActions(flow: UpdateFlow, card: CardState, still: Boolean, onCancel: () -> Unit, close: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val total = flow.total
  // Not the live state: fading out (downloaded, canceled), the block keeps the last percent the updater kept
  val known = card.downloadStarted && total > 0
  val progress = if (known) (flow.downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
  val percent = (progress * 100f).toInt()
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      // Announced once (Starting, then Downloading); the percent is read on the indicator, not at every step
      Text(
        s(if (known) R.string.XgramUpdDownloading else R.string.XgramUpdStarting),
        style = MaterialTheme.typography.titleSmall,
        color = c.onSurface,
        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
      )
      if (known) {
        Text(
          s(R.string.XgramUpdPercent, percent),
          style = MaterialTheme.typography.labelLarge,
          color = c.primary,
          modifier = Modifier.padding(start = 12.dp).clearAndSetSemantics { }
        )
      }
    }
    if (known) {
      // The determinate wavy indicator moves on the motion scheme's spatial spring between updates; flat with Reduce motion
      val shown by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (still) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "xgramUpdateProgress"
      )
      val amplitude: (Float) -> Float = if (still) FLAT_WAVE else WavyProgressIndicatorDefaults.indicatorAmplitude
      LinearWavyProgressIndicator(
        progress = { shown.coerceIn(0f, 1f) },
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp)
          .semantics { stateDescription = s(R.string.XgramUpdPercentDownloaded, percent) },
        amplitude = amplitude
      )
      Text(
        s(R.string.XgramUpdDownloadedOf, Strings.buildSize(flow.downloaded), Strings.buildSize(total)),
        style = MaterialTheme.typography.bodySmall,
        color = c.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
      )
    } else if (still) {
      // Reduce motion: an empty track, no looping wave (the line above says what happens)
      LinearWavyProgressIndicator(
        progress = { 0f },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clearAndSetSemantics { },
        amplitude = FLAT_WAVE
      )
    } else {
      LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
    FilledTonalButton(
      onClick = close,
      shapes = ButtonDefaults.shapes(),
      modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp)
    ) {
      Text(s(R.string.XgramUpdBackground), textAlign = TextAlign.Center)
    }
    TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
      Text(s(R.string.XgramUpdCancel))
    }
  }
}

@Composable
private fun CheckingActions(still: Boolean) {
  val c = MaterialTheme.colorScheme
  Row(Modifier.fillMaxWidth().heightIn(min = 104.dp), verticalAlignment = Alignment.CenterVertically) {
    if (still) {
      StillIndicator(56.dp)
    } else {
      LoadingIndicator(modifier = Modifier.size(56.dp))
    }
    Column(Modifier.weight(1f).padding(start = 16.dp)) {
      Text(
        s(R.string.XgramUpdChecking),
        style = MaterialTheme.typography.titleSmall,
        color = c.onSurface,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
      )
      Text(
        s(R.string.XgramUpdCheckingInfo),
        style = MaterialTheme.typography.bodySmall,
        color = c.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
      )
    }
  }
}

/** Reduce motion's loading indicator: the update icon on a still cookie, nothing looping. */
@Composable
private fun StillIndicator(size: Dp) {
  val c = MaterialTheme.colorScheme
  Box(
    Modifier.size(size).clip(MaterialShapes.Cookie9Sided.toShape()).background(c.primaryContainer),
    contentAlignment = Alignment.Center
  ) {
    Icon(tgxIcon(R.drawable.baseline_system_update_24), contentDescription = null, tint = c.onPrimaryContainer, modifier = Modifier.size(24.dp))
  }
}

// Hero

/**
 * The top of the card, clipped by its corners: the generated art, or the notes' photo once it has loaded (a fade
 * between them), with the kind ("CI build" or "Stable") and Open post in its corners. It folds as the body scrolls up
 * ([fold]), from [height] down to the band, which keeps the chips and Open post. Decoration only for TalkBack, but for
 * Open post.
 */
@Composable
private fun Hero(model: UpdateModel?, preview: Boolean, height: Dp, fold: HeroFold, still: Boolean, onOpenPost: (() -> Unit)?) {
  val photo = rememberHeroPhoto(model)
  val photoShown by animateFloatAsState(if (photo != null) 1f else 0f, effectsSpec(still), label = "xgramUpdatePhoto")
  // The chips' row, so the folded logo keeps clear of them and lines up with them
  var chips by remember { mutableStateOf(IntSize.Zero) }
  Box(
    Modifier
      .fillMaxWidth()
      .layout { measurable, constraints ->
        // The fold is read here, not while composing: a scroll only lays the card out again. Never below the band or
        // above the full height, whatever a spring does
        val full = height.roundToPx()
        val shown = (full - fold.folded.roundToInt())
          .coerceIn(minOf(HERO_BAND.roundToPx(), full), full)
          .coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = shown, maxHeight = shown))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
      }
      .clipToBounds()
  ) {
    if (photo == null || photoShown < 1f) {
      // Laid out at the full height from the top: folding uncovers less of it, nothing in it moves but the logo
      HeroArt(
        still,
        fold,
        chips = { chips },
        modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(height)
      )
    }
    if (photo != null) {
      // Cropped at the full height too: folding uncovers less of it, it never zooms or slides
      val image = remember(photo) { photo.asImageBitmap() }
      Image(
        image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(Alignment.Top, unbounded = true)
          .height(height)
          .graphicsLayer { alpha = photoShown }
      )
    }
    // The chips keep clear of Open post, whatever the font size
    Box(
      Modifier
        .align(Alignment.TopStart)
        .fillMaxWidth()
        .padding(start = CHIPS_INSET, top = CHIPS_INSET, end = if (onOpenPost != null) 72.dp else CHIPS_INSET)
        .clearAndSetSemantics { }
    ) {
      Row(Modifier.onSizeChanged { chips = it }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (model != null) {
          HeroChip(s(if (model.isStable) R.string.XgramUpdateKindStable else R.string.XgramUpdateKindCi))
        }
        if (preview) {
          HeroChip(s(R.string.XgramUpdPreviewTag))
        }
      }
    }
    if (onOpenPost != null) {
      FilledTonalIconButton(onClick = onOpenPost, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
        Icon(tgxIcon(R.drawable.baseline_open_in_browser_24), contentDescription = s(R.string.XgramUpdateOpenPost))
      }
    }
  }
}

@Composable
private fun HeroChip(text: String) {
  val c = MaterialTheme.colorScheme
  Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = c.onSurface,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.clip(CircleShape).background(c.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 6.dp)
  )
}

/** The notes' photo through TDLib (downloaded and decoded as XDrive's thumbnails), null until it is there or when it fails. */
@Composable
private fun rememberHeroPhoto(model: UpdateModel?): Bitmap? {
  val tdlib = model?.tdlib
  val file = remember(model) { model?.photo?.let { heroFile(it) } }
  var bitmap by remember(file?.id) { mutableStateOf(XDriveThumbs.cached(file?.id)) }
  LaunchedEffect(file?.id) {
    if (tdlib != null && file != null && bitmap == null) {
      XDriveThumbs.load(tdlib, file, HERO_PHOTO_SIZE)?.let { bitmap = it }
    }
  }
  return bitmap
}

/** The largest size of the photo up to 1280px (the hero is at most 560dp wide), else the smallest. */
private fun heroFile(photo: TdApi.Photo): TdApi.File? {
  val sizes = photo.sizes ?: return null
  val pick = sizes.filter { it.width <= HERO_PHOTO_SIZE && it.height <= HERO_PHOTO_SIZE }.maxByOrNull { it.width }
    ?: sizes.minByOrNull { it.width }
  return pick?.photo
}

/**
 * The generated hero: the theme's primaryContainer to tertiaryContainer, four large Material shapes at low alpha
 * turning and drifting slowly, and the app's icon in the middle. Still with Reduce motion. As the hero folds ([fold])
 * the shapes fade and the icon shrinks up into the band, level with the chips ([chips]' row, px), fading out instead
 * where they reach the band's middle.
 */
@Composable
private fun HeroArt(still: Boolean, fold: HeroFold, chips: () -> IntSize, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  val transition = if (still) null else rememberInfiniteTransition(label = "xgramUpdateHero")
  val drift = if (transition == null) null else transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(DRIFT_MS, easing = LinearEasing), RepeatMode.Restart),
    label = "xgramUpdateDrift"
  )
  val breathe = if (transition == null) null else transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(BREATHE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "xgramUpdateBreathe"
  )
  BoxWithConstraints(
    modifier
      .background(Brush.linearGradient(listOf(c.primaryContainer, c.tertiaryContainer)))
      .clearAndSetSemantics { }
  ) {
    val w = maxWidth
    val h = maxHeight
    val widthPx = constraints.maxWidth
    val shapesShown = { (1f - fold.fraction * DECOR_FADE).coerceIn(0f, 1f) }
    // Whole turns per cycle, so the restart never jumps
    Decoration(MaterialShapes.Cookie9Sided, h * .8f, -(h * .22f), -(h * .26f), c.primary.copy(alpha = .14f), 1f, 0f, drift, shapesShown)
    Decoration(MaterialShapes.Sunny, h * .64f, w - h * .44f, h * .5f, c.tertiary.copy(alpha = .18f), -1f, .25f, drift, shapesShown)
    Decoration(MaterialShapes.Clover4Leaf, h * .3f, w * .62f, h * .12f, c.secondary.copy(alpha = .2f), 2f, .5f, drift, shapesShown)
    Decoration(MaterialShapes.Pill, h * .36f, w * .16f, h * .62f, c.onPrimaryContainer.copy(alpha = .08f), -1f, .75f, drift, shapesShown)
    // Centered in the space under the chips' row, so the chips don't cover it on short heroes or at large font sizes
    val logo = minOf(h * .5f, h - HERO_CHIPS - 24.dp).coerceIn(56.dp, 104.dp)
    LogoMorph(
      logo,
      breathe,
      drift,
      Modifier
        .align(Alignment.Center)
        .padding(top = HERO_CHIPS)
        .graphicsLayer {
          // Folding: down to the band's size, its middle from (h + chips) / 2 up to the chips' middle, the line Open
          // post is centered on too (the band's middle without chips), never so low the band cuts it
          val f = fold.fraction
          val scale = 1f + (BAND_LOGO.toPx() / logo.toPx() - 1f) * f
          scaleX = scale
          scaleY = scale
          val row = chips()
          val middle = (if (row.height > 0) CHIPS_INSET.toPx() + row.height / 2f else HERO_BAND.toPx() / 2f)
            .coerceAtMost(HERO_BAND.toPx() - BAND_LOGO.toPx() / 2f)
          translationY = (2f * middle - h.toPx() - HERO_CHIPS.toPx()) / 2f * f
          // The chips reach the band's middle (two chips, large fonts, the cover screen): out by half the fold
          val clear = CHIPS_INSET.toPx() + row.width + 8.dp.toPx() <= (widthPx - BAND_LOGO.toPx()) / 2f
          alpha = if (clear) 1f else (1f - 2f * f).coerceIn(0f, 1f)
        }
    )
  }
}

/**
 * A shape of the hero at ([x], [y]) from the start corner, turning [turns] times and circling a few dp per cycle, at
 * [shown] alpha (it fades as the hero folds).
 */
@Composable
private fun Decoration(shape: RoundedPolygon, diameter: Dp, x: Dp, y: Dp, color: Color, turns: Float, seed: Float, drift: State<Float>?, shown: () -> Float) {
  Box(
    Modifier
      .offset(x, y)
      .size(diameter)
      .graphicsLayer {
        val p = drift?.value ?: 0f
        val angle = 2f * PI.toFloat() * (p + seed)
        rotationZ = 360f * turns * p
        translationX = sin(angle) * 8.dp.toPx()
        translationY = cos(angle) * 6.dp.toPx()
        alpha = shown()
      }
      .clip(shape.toShape())
      .background(color)
  )
}

/**
 * The app's icon (its themed layer, in onPrimary) on a primary shape morphing between a soft burst and a 12-sided
 * cookie, breathing a little and turning slowly. The icon's vector is 108dp with the logo in its middle, so it is drawn
 * larger than the shape.
 */
@Composable
private fun LogoMorph(diameter: Dp, breathe: State<Float>?, drift: State<Float>?, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  val morph = remember { Morph(MaterialShapes.SoftBurst, MaterialShapes.Cookie12Sided) }
  val fill = c.primary
  Box(
    modifier
      .size(diameter)
      .graphicsLayer {
        val pulse = .97f + .06f * (breathe?.value ?: 0f)
        scaleX = pulse
        scaleY = pulse
      },
    contentAlignment = Alignment.Center
  ) {
    Box(
      Modifier
        .matchParentSize()
        .graphicsLayer { rotationZ = 360f * (drift?.value ?: 0f) }
        .drawWithCache {
          val path = Path()
          val matrix = Matrix()
          onDrawBehind {
            // The morph's path is in unit coordinates: scaled to the box
            path.rewind()
            morph.toPath(breathe?.value ?: 0f, path)
            matrix.reset()
            matrix.scale(size.width, size.height)
            path.transform(matrix)
            drawPath(path, fill)
          }
        }
    )
    Icon(
      painterResource(R.drawable.xgram_ic_monochrome),
      contentDescription = null,
      tint = c.onPrimary,
      modifier = Modifier.requiredSize(diameter * 1.12f)
    )
  }
}
