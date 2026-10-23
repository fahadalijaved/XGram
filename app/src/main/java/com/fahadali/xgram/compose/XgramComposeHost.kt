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

import android.graphics.Rect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import org.thunderdog.challegram.BaseActivity
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.telegram.Tdlib

/**
 * What a Compose settings page gets from its Telegram X controller ([XgramComposeController]). Read it with
 * `LocalXgramHost.current`. The Int and Boolean values below are snapshot state: reading them in a composable
 * recomposes it when they change.
 */
interface XgramComposeHost {
  val controller: ViewController<*>
  val activity: BaseActivity
  val tdlib: Tdlib
  val page: XgramPage

  /**
   * Bumped by [changed] and whenever the page gets focus again (a Telegram X page such as Themes or another screen may
   * have changed a value). Settings singletons are not snapshot state: pages read them after reading this.
   */
  val version: Int

  /** Call after any setter. The toolkit rows (switch, buttons, slider, radio, dialogs) call it themselves. */
  fun changed()

  val langVersion: Int
  val themeVersion: Int
  val isDark: Boolean

  /** Status bar height in px (Telegram X's RootFrameLayout.getTopInset). */
  val topInset: Int

  /** Navigation bar or keyboard height in px. */
  val bottomInset: Int

  /** The settings search page: where it was opened from (com.fahadali.xgram.search.SettingsSearch.ORIGIN_*). */
  val origin: Int
    get() = 0

  /** A row to scroll to and pulse once shown (settings search, Telegram X shortcuts); 0 when none. */
  val highlightId: Int
  /** Tells apart rows that share [highlightId]. */
  val highlightKey: String?
  fun consumeHighlight()

  /** Opens a Telegram X page (Themes, emoji sets, ...). */
  fun open(c: ViewController<*>)
  /** Opens another Compose page. */
  fun openPage(p: XgramPage, highlightId: Int = 0, key: String? = null)
  fun back()
  /** Whether there is a page to go back to: the app bar shows its back arrow only then. */
  val canGoBack: Boolean

  /** A bottom sheet with one choice. */
  fun choose(spec: ChoiceSpec<*>)
  /** An alert dialog asking to confirm. */
  fun confirm(spec: ConfirmSpec)
  /** An alert dialog with a text field. */
  fun prompt(spec: PromptSpec)
  /** The sheet or dialog on screen ([ChoiceSpec], [ConfirmSpec] or [PromptSpec]), or null. */
  val dialog: Any?
  fun dismissDialog()

  /**
   * An in-page state that back closes first (a selection, an edit mode). Do not use Compose BackHandler: it registers
   * on the activity ahead of Telegram X and breaks the predictive back card. Sheets and dialogs handle back themselves.
   */
  fun setBackConsumer(f: (() -> Unit)?)

  /**
   * The predictive back gesture's progress over the in-page state the back consumer closes ([setBackConsumer]): the
   * page shows the state shrinking away as the finger moves, and the consumer runs when the gesture is let go.
   * Null: back closes it at once.
   */
  fun setBackProgress(listener: XgramBackProgress?) {}

  /**
   * Keeps Telegram X's swipe-back from starting inside the rectangle [bounds] returns, in window pixels (null from it:
   * nowhere right now). [bounds] runs only when a swipe could start; null removes the area. See Modifier.xgramNoSwipeBack.
   */
  fun noSwipeBack(key: Any, bounds: (() -> Rect?)?)

  /** Reads the dynamic colors again (after a wallpaper or Material You change). */
  fun themeChanged()

  fun restartActivity()
}

val LocalXgramHost = staticCompositionLocalOf<XgramComposeHost> { error("XgramComposeHost is not provided") }

/**
 * One choice in a bottom sheet: tapping an option closes the sheet, then calls [onSelect]. [subtitles] are the options'
 * own sub-lines by index (null or missing: none); [info] is about the whole choice. [fonts]: an option's title and
 * sub-line drawn in its own font family by index (the Font picker's previews; null or missing: the theme's).
 */
class ChoiceSpec<T>(
  val title: String,
  val info: String? = null,
  val options: List<Pair<String, T>>,
  val selected: T,
  val subtitles: List<String?> = emptyList(),
  val fonts: List<FontFamily?> = emptyList(),
  val onSelect: (T) -> Unit
) {
  val labels: List<String>
    get() = options.map { it.first }

  val selectedIndex: Int
    get() = options.indexOfFirst { it.second == selected }

  fun pick(index: Int) {
    if (index in options.indices) {
      onSelect(options[index].second)
    }
  }
}

/** Confirmation: [confirm] is the button text; [destructive] colors it as an error. */
class ConfirmSpec(
  val title: String? = null,
  val text: String,
  val confirm: String,
  val destructive: Boolean = false,
  val onConfirm: () -> Unit
)

/** A text field in a dialog; [onDone] gets the text when [confirm] is pressed. */
class PromptSpec(
  val title: String,
  val hint: String? = null,
  val confirm: String = Lang.getString(R.string.Save),
  val initial: String = "",
  val onDone: (String) -> Unit
)

/** A predictive back gesture over a page's in-page state: [onProgress] from 0 to 1, then the back consumer or [onCancel]. */
interface XgramBackProgress {
  /** The gesture started at the right edge (the state slides left) or the left one. */
  fun onStart(fromRightEdge: Boolean)
  fun onProgress(progress: Float)
  fun onCancel()
}
