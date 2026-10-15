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
package com.fahadali.xgram.compose

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import org.thunderdog.challegram.BaseActivity
import org.thunderdog.challegram.R
import org.thunderdog.challegram.navigation.BackHeaderButton
import org.thunderdog.challegram.navigation.NavigationController
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.theme.ColorState
import org.thunderdog.challegram.theme.Theme
import org.thunderdog.challegram.theme.ThemeDelegate
import org.thunderdog.challegram.tool.UI

/**
 * A Telegram X page whose content is a Jetpack Compose Material 3 Expressive settings page ([XgramPage]).
 *
 * It stays a regular ViewController, so swipe-back, predictive back (the XgramNavMotion card), the shared-axis
 * transition and navigateTo to Telegram X's own pages all keep working. Telegram X's header stays in the stack but is
 * see-through, empty and lets touches through ([xgramDrawsOwnAppBar]); the page draws its own top app bar under it.
 */
class XgramComposeController(context: Context, tdlib: Tdlib) : ViewController<XgramComposeController.Args>(context, tdlib) {
  class Args(val page: XgramPage, val highlightId: Int = 0, val highlightKey: String? = null)

  companion object {
    @JvmStatic
    @JvmOverloads
    fun create(context: Context, tdlib: Tdlib, page: XgramPage, highlightId: Int = 0, highlightKey: String? = null): XgramComposeController {
      val c = XgramComposeController(context, tdlib)
      c.setArguments(Args(page, highlightId, highlightKey))
      return c
    }
  }

  private val owner = XgramComposeOwner()

  private val versionState = mutableIntStateOf(0)
  private val langVersionState = mutableIntStateOf(0)
  private val themeVersionState = mutableIntStateOf(0)
  private val isDarkState = mutableStateOf(Theme.isDark())
  private val topInsetState = mutableIntStateOf(0)
  private val bottomInsetState = mutableIntStateOf(0)
  private val canGoBackState = mutableStateOf(true)
  private val dialogState = mutableStateOf<Any?>(null)

  private var highlightConsumed = false
  private var focusedOnce = false
  private var backConsumer: (() -> Unit)? = null
  private val noSwipeAreas = HashMap<Any, () -> Rect?>()

  private val host: XgramComposeHost = Host()

  override fun getId(): Int = R.id.controller_xgramCompose

  // Telegram X's header shows no title: the page draws it.
  override fun getName(): CharSequence = ""

  override fun xgramDrawsOwnAppBar(): Boolean = true

  override fun getBackButton(): Int = BackHeaderButton.TYPE_NONE

  override fun getFloatingButtonId(): Int = 0

  override fun useDropShadow(): Boolean = false

  // No ongoing audio or call bar over the page's own app bar.
  override fun useDropPlayer(): Boolean = false

  // A ComposeView composes once attached, and Telegram X attaches when the animation starts.
  override fun needAsynchronousAnimation(): Boolean = false

  override fun supportsBottomInset(): Boolean = true

  override fun onBottomInsetChanged(extraBottomInset: Int, extraBottomInsetWithoutIme: Int, isImeInset: Boolean) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset)
    // With the keyboard: fields and the end of the list stay above it.
    bottomInsetState.intValue = extraBottomInset
  }

  override fun onCreateView(context: Context): View {
    return XgramComposeRoot(context, owner, { top -> topInsetState.intValue = top }) {
      XgramComposeApp(host)
    }
  }

  override fun onFocus() {
    super.onFocus()
    owner.moveTo(Lifecycle.State.RESUMED)
    updateCanGoBack()
    // Values may have changed on a Telegram X page (Themes, emoji sets, ...) or on another screen. Not on the first
    // focus: the page was just built, and building it again would land right when the user starts to scroll.
    if (focusedOnce) {
      versionState.intValue++
    } else {
      focusedOnce = true
    }
    // Opened as the first page (Quick Settings tile), the main screen goes in below it right after.
    UI.post { if (!isDestroyed) updateCanGoBack() }
  }

  override fun onBlur() {
    super.onBlur()
    owner.moveTo(Lifecycle.State.STARTED)
  }

  override fun onActivityPause() {
    super.onActivityPause()
    owner.moveTo(Lifecycle.State.STARTED)
  }

  override fun onActivityResume() {
    super.onActivityResume()
    // The wallpaper (so the dynamic colors) may have changed while XGram was away.
    checkDark()
    themeVersionState.intValue++
    if (isFocused) {
      owner.moveTo(Lifecycle.State.RESUMED)
      // Values may have changed while XGram was away (Quick Settings tile).
      versionState.intValue++
    }
  }

  override fun destroy() {
    super.destroy()
    backConsumer = null
    noSwipeAreas.clear()
    owner.destroy()
  }

  private fun updateCanGoBack() {
    canGoBackState.value = navigationController() != null && stackSize() > 1
  }

  // Theme and language

  private fun checkDark() {
    val dark = Theme.isDark()
    if (isDarkState.value != dark) {
      isDarkState.value = dark
    }
  }

  override fun onThemeChanged(fromTheme: ThemeDelegate?, toTheme: ThemeDelegate?) {
    super.onThemeChanged(fromTheme, toTheme)
    checkDark()
    themeVersionState.intValue++
  }

  override fun onThemeColorsChanged(areTemp: Boolean, state: ColorState?) {
    super.onThemeColorsChanged(areTemp, state)
    checkDark()
    if (!areTemp) {
      themeVersionState.intValue++
    }
  }

  override fun handleLanguagePackEvent(event: Int, arg1: Int) {
    super.handleLanguagePackEvent(event, arg1)
    langVersionState.intValue++
  }

  override fun handleLanguageDirectionChange() {
    super.handleLanguageDirectionChange()
    langVersionState.intValue++
  }

  // Back and swipe-back

  override fun performOnBackPressed(fromTop: Boolean, commit: Boolean): Boolean {
    val consumer = backConsumer
    if (consumer != null) {
      if (commit) {
        consumer()
      }
      return true
    }
    return super.performOnBackPressed(fromTop, commit)
  }

  override fun canSlideBackFrom(nav: NavigationController?, x: Float, y: Float): Boolean {
    if (nav != null && noSwipeAreas.isNotEmpty()) {
      val location = IntArray(2)
      nav.getValue().getLocationInWindow(location)
      val wx = (location[0] + x).toInt()
      val wy = (location[1] + y).toInt()
      // The bounds are worked out only now, when a swipe could start.
      for (bounds in noSwipeAreas.values) {
        val rect = bounds()
        if (rect != null && rect.contains(wx, wy)) {
          return false
        }
      }
    }
    return super.canSlideBackFrom(nav, x, y)
  }

  private inner class Host : XgramComposeHost {
    override val controller: ViewController<*>
      get() = this@XgramComposeController

    override val activity: BaseActivity
      get() = this@XgramComposeController.context()

    override val tdlib: Tdlib
      get() = this@XgramComposeController.tdlib()

    override val page: XgramPage
      get() = this@XgramComposeController.getArgumentsStrict().page

    override val version: Int
      get() = versionState.intValue

    override fun changed() {
      versionState.intValue++
    }

    override val langVersion: Int
      get() = langVersionState.intValue

    override val themeVersion: Int
      get() = themeVersionState.intValue

    override val isDark: Boolean
      get() = isDarkState.value

    override val topInset: Int
      get() = topInsetState.intValue

    override val bottomInset: Int
      get() = bottomInsetState.intValue

    override val highlightId: Int
      get() = if (highlightConsumed) 0 else this@XgramComposeController.getArgumentsStrict().highlightId

    override val highlightKey: String?
      get() = if (highlightConsumed) null else this@XgramComposeController.getArgumentsStrict().highlightKey

    override fun consumeHighlight() {
      highlightConsumed = true
    }

    override fun open(c: ViewController<*>) {
      navigateTo(c)
    }

    override fun openPage(p: XgramPage, highlightId: Int, key: String?) {
      navigateTo(create(activity, tdlib, p, highlightId, key))
    }

    override fun back() {
      navigateBack()
    }

    override val canGoBack: Boolean
      get() = canGoBackState.value

    override fun choose(spec: ChoiceSpec<*>) {
      dialogState.value = spec
    }

    override fun confirm(spec: ConfirmSpec) {
      dialogState.value = spec
    }

    override fun prompt(spec: PromptSpec) {
      dialogState.value = spec
    }

    override val dialog: Any?
      get() = dialogState.value

    override fun dismissDialog() {
      dialogState.value = null
    }

    override fun setBackConsumer(f: (() -> Unit)?) {
      val had = backConsumer != null
      backConsumer = f
      if (had != (f != null)) {
        activity.notifyBackPressAvailabilityChanged()
      }
    }

    override fun noSwipeBack(key: Any, bounds: (() -> Rect?)?) {
      if (bounds == null) {
        noSwipeAreas.remove(key)
      } else {
        noSwipeAreas[key] = bounds
      }
    }

    override fun themeChanged() {
      checkDark()
      themeVersionState.intValue++
    }

    override fun restartActivity() {
      scheduleActivityRestart()
    }
  }
}
