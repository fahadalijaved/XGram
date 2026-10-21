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
package com.fahadali.xgram.compose.tgx

import android.view.View
import androidx.compose.runtime.Composable
import com.fahadali.xgram.appearance.XgramM3Bars
import com.fahadali.xgram.appearance.XgramR6B5Pages
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.base.SettingView
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.data.DoubleTextWrapper
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.ui.ListItem
import org.thunderdog.challegram.ui.SettingsDataController
import org.thunderdog.challegram.ui.SettingsLanguageTranslateController
import org.thunderdog.challegram.ui.SettingsLogOutController
import org.thunderdog.challegram.ui.SettingsNotificationController
import org.thunderdog.challegram.ui.SettingsPrivacyController
import org.thunderdog.challegram.ui.SettingsThemeController

/** How one row of a Telegram X page is drawn in Compose. */
enum class TgxRowKind {
  /** Shadows, separators and offsets: not drawn (groups and gaps replace them). */
  HIDDEN,
  SWITCH, RADIO, CHECKBOX,
  /**
   * A switch row with two targets (its toggler has a click of its own): the row opens or expands something, the
   * switch after a divider toggles (EditRights' Send media group).
   */
  SPLIT_SWITCH,
  /** A row that opens something or shows a value (also destructive actions, by text color). */
  VALUE,
  HEADER, DESCRIPTION,
  /** A header with an icon button, a text button or a checkbox at its end (Telegram X's HEADER_WITH_* rows). */
  HEADER_ACTION,
  /** Telegram X's full-width text button: an action row in the primary color. */
  BUTTON,
  SLIDER, BUILD_NO, LOADING, EMPTY,
  /**
   * An empty state with an icon (TYPE_ICONIZED_EMPTY, TYPE_WEBSITES_EMPTY, TYPE_SESSIONS_EMPTY, M3E TYPE_EMPTY): the
   * icon on a soft burst in primaryContainer, the headline in titleLarge, the text in bodyMedium.
   */
  EMPTY_ICON,
  /**
   * A chat, or a message of one, drawn natively (TYPE_CHAT_SMALL with a DoubleTextWrapper, or a row a page maps here):
   * the page's leading content (a 40dp avatar), the headline in bodyLarge, the supporting text in bodyMedium and the
   * page's trailing content (a date in labelMedium). The headline is the wrapper's title, the supporting text the
   * item's string value (ListItem.setStringValue), unless the page's [TgxPageHints.text] says otherwise.
   */
  CHAT,
  /** The real Telegram X view in the row's place (AndroidView): users, chats, edit fields, previews, custom rows. */
  FALLBACK
}

/**
 * How a page wants a row's text drawn ([TgxPageHints.text]): its headline, its supporting line (null: none) and their
 * look. Equal values recompose nothing.
 */
data class TgxRowText(
  val title: CharSequence?,
  val subtitle: CharSequence?,
  /** Tabular figures in the supporting line (IDs, times, sizes). */
  val tabular: Boolean = false,
  /** Lines of the supporting text before it is cut with an ellipsis. */
  val subtitleLines: Int = Int.MAX_VALUE,
  /** The supporting line is still loading: a shimmer bar takes its place. */
  val loading: Boolean = false,
  /**
   * A download or an install of the row in progress: a 24dp wavy ring at the row's end in place of its trailing content
   * (0..1, NaN while its progress is unknown); -1: none.
   */
  val progress: Float = -1f
)

/**
 * What a page may change about its Compose rows. Every method has a default: [TgxPageHints.DEFAULT] opts a page in as
 * it is. Hints are Kotlin only; the controller itself stays untouched.
 */
interface TgxPageHints {
  /**
   * Whether this instance of the page is drawn in Compose (false: the classic list). For pages that are also used
   * where they must not depend on Compose, such as the crash recovery screen.
   */
  fun allow(c: ViewController<*>): Boolean = true

  /** The page title as the first list item, fading Telegram X's small title in as it scrolls away. */
  fun largeTitle(c: ViewController<*>): Boolean = c.xgramAllowsLargeTitle()

  /**
   * The large title's style: XgramM3Bars.TITLE_LARGE (displaySmall, every page by default, as the Compose pages and the
   * classic list) or XgramM3Bars.TITLE_MEDIUM (headlineMedium) for a page that needs the smaller one.
   */
  fun titleStyle(c: ViewController<*>): Int = XgramM3Bars.TITLE_LARGE

  /** A bodyMedium line under the large title (Log out: "Alternative options"); null: none. */
  fun largeSubtitle(c: ViewController<*>): CharSequence? = null

  /** A form (a name, a sign-up step): the page's column at most 480dp wide, centred, as XGram's forms. */
  fun formWidth(c: ViewController<*>): Boolean = false

  /** How to draw [item] (bound to [view]); null: the generic mapping. FALLBACK keeps the real view. */
  fun kind(item: ListItem, view: View): TgxRowKind? = null

  /** Extra trailing content of a VALUE row (an avatar stack, a preview); null: none. */
  fun trailing(item: ListItem, view: View): (@Composable () -> Unit)? = null

  /**
   * [trailing] with the page at hand, for content that reads the page's state (a download's progress, a message's
   * date). VALUE, RADIO and CHAT rows ask this one; by default it is [trailing].
   */
  fun trailing(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? = trailing(item, view)

  /**
   * The leading content of a VALUE, RADIO or CHAT row in place of the row's own icon (a chat's 40dp avatar, see
   * TgxChatAvatar); null: the row's icon as a tonal circle, or nothing. A RADIO row keeps its radio button.
   */
  fun leading(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? = null

  /** The row's text as the page wants it drawn (VALUE, RADIO and CHAT rows); null: as read from the view. */
  fun text(c: ViewController<*>, item: ListItem, view: View): TgxRowText? = null

  /**
   * Whether a switch row is only partly on (a group with some of its options on): drawn as the switch's third state.
   * Read with the row, after every change the page makes to it.
   */
  fun isPartial(c: ViewController<*>, item: ListItem, view: View): Boolean = false

  companion object {
    @JvmField
    val DEFAULT: TgxPageHints = object : TgxPageHints {}
  }
}

/**
 * XGram's lists of chats (Saved by chat, a community's chats): TYPE_CHAT_SMALL rows drawn natively, with the chat's 40dp
 * avatar, its title, the item's string value as the supporting text and the item's int value (a unix time, 0: none)
 * as a trailing labelMedium date.
 */
internal open class TgxChatRowsHints : TgxPageHints {
  override fun kind(item: ListItem, view: View): TgxRowKind? =
    if (item.viewType == ListItem.TYPE_CHAT_SMALL && item.data is DoubleTextWrapper) TgxRowKind.CHAT else null

  override fun leading(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
    val wrapper = item.data as? DoubleTextWrapper ?: return null
    return { TgxChatAvatar { avatar -> avatar.setWrapper(wrapper) } }
  }

  override fun trailing(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
    if (item.viewType != ListItem.TYPE_CHAT_SMALL || item.intValue <= 0) {
      return null
    }
    val date = XgramR6B5Pages.shortDate(item.intValue.toLong())
    return { TgxTrailingLabel(date) }
  }

  companion object {
    @JvmField
    val INSTANCE: TgxPageHints = TgxChatRowsHints()
  }
}

/**
 * The Telegram X pages drawn in Compose with Expressive design on. A page is listed by its exact class (a subclass is
 * a page of its own). Each batch adds one line per controller to its own file (TgxPagesBatch1..3):
 *
 *     SettingsPhoneController::class.java to TgxPageHints.DEFAULT,
 */
object TgxComposePages {
  // The core's pages: the pilots and the most visible Settings pages.
  private val core: Map<Class<*>, TgxPageHints> = mapOf(
    SettingsPrivacyController::class.java to TgxPageHints.DEFAULT,
    SettingsDataController::class.java to TgxPageHints.DEFAULT,
    SettingsNotificationController::class.java to TgxPageHints.DEFAULT,
    SettingsThemeController::class.java to TgxPageHints.DEFAULT,
    SettingsLanguageTranslateController::class.java to TgxPageHints.DEFAULT,
    SettingsLogOutController::class.java to LogOutHints,
  )

  /**
   * Log out and Delete account: Telegram X's two-line header becomes the large title with "Alternative options" under
   * it in bodyMedium (the page drops its custom header with Expressive design). Log out and Delete account are error
   * rows (textNegative: an errorContainer icon circle and an error label).
   */
  private object LogOutHints : TgxPageHints {
    override fun largeSubtitle(c: ViewController<*>): CharSequence? = Lang.getString(R.string.SignOutAlt)

    /** The classic error circle is a draw modifier on these rows: they stay native rows here all the same. */
    override fun kind(item: ListItem, view: View): TgxRowKind? {
      val id = item.id
      if ((id == R.id.btn_logout || id == R.id.btn_deleteAccount) && view is SettingView && view.toggler == null && view.childCount == 0) {
        return TgxRowKind.VALUE
      }
      return null
    }
  }

  private val pages: Map<Class<*>, TgxPageHints> by lazy {
    HashMap<Class<*>, TgxPageHints>().apply {
      putAll(core)
      putAll(TgxPagesBatch1.pages)
      putAll(TgxPagesBatch2.pages)
      putAll(TgxPagesBatch3.pages)
      putAll(TgxPagesLater.pages) // XGram Later
      putAll(TgxPagesDrive.pages) // XGram Drive
    }
  }

  /** The hints of [c]'s page, or null when the page keeps its classic list. */
  @JvmStatic
  fun hints(c: ViewController<*>): TgxPageHints? = pages[c.javaClass]
}
