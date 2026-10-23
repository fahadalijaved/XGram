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
package com.fahadali.xgram.update

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fahadali.xgram.XgramSettingsController
import com.fahadali.xgram.activity.XgramActivity
import com.fahadali.xgram.compose.XgramSettingsPages
import com.fahadali.xgram.later.XgramLaterController
import org.thunderdog.challegram.BuildConfig
import org.thunderdog.challegram.R
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.unsorted.Settings

/**
 * XGram's own big features, as the update screen's highlights show them ([XgramUpdateScreen]): an icon, a line on
 * what each does and where Try it takes you. A line of the notes' Highlights section that names one ("Undo send: …")
 * gets its icon and, in the welcome, its Try it. Settings → XGram → What's new opens them the same way.
 */
enum class XgramFeature(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val info: Int) {
  ACTIVITY(R.drawable.baseline_notifications_24, R.string.XgramActivity, R.string.XgramWelcomeActivityInfo),
  UNDO_SEND(R.drawable.baseline_undo_24, R.string.XgramUndoSend, R.string.XgramWelcomeUndoSendInfo),
  CHECK_IN(R.drawable.baseline_location_on_24, R.string.XgramCheckIn, R.string.XgramWelcomeCheckInInfo),
  LATER(R.drawable.baseline_watch_later_24, R.string.XgramLater, R.string.XgramWelcomeLaterInfo);

  /**
   * Try it, from [from]: Activity (its tab when the navigation bar has one; its switch in Chat list while it is off),
   * Undo send's delay in Messages (off until one is picked), Check In's part of Messages (it starts from a chat's ⋮
   * menu), the Later page.
   */
  fun open(from: ViewController<*>) {
    val tdlib = from.tdlib()
    if (from.isDestroyed || tdlib == null) {
      return
    }
    val context = from.context()
    when (this) {
      ACTIVITY -> if (XgramActivity.isOn()) {
        XgramActivity.open(from)
      } else {
        from.navigateTo(XgramSettingsPages.open(context, tdlib, XgramSettingsController.SECTION_CHAT_LIST, R.id.btn_xgramActivityShow))
      }
      UNDO_SEND -> from.navigateTo(XgramSettingsPages.open(context, tdlib, XgramSettingsController.SECTION_MESSAGES, R.id.btn_xgramUndoSend))
      CHECK_IN -> from.navigateTo(XgramSettingsPages.open(context, tdlib, XgramSettingsController.SECTION_MESSAGES, R.id.btn_xgramCheckInTimer))
      LATER -> from.navigateTo(XgramLaterController(context, tdlib))
    }
  }

  companion object {
    private val CHECK_IN_NAME = Regex("\\bcheck[ -]?in\\b")

    /** The feature a highlight's name is about ("Undo Send", "Check-in", "Later (snooze and reminders)"), or null. */
    fun of(name: String): XgramFeature? {
      val words = name.lowercase()
      return when {
        words.contains("activity") -> ACTIVITY
        words.contains("undo") || words.contains("unsend") -> UNDO_SEND
        CHECK_IN_NAME.containsMatchIn(words) -> CHECK_IN
        words == "later" || words.startsWith("later ") -> LATER
        else -> null
      }
    }
  }
}

/**
 * When the welcome after an update shows ([XgramUpdateScreen.welcomeIfNeeded]): once per version, never after a fresh
 * install. The highlights built into the app ([BUILT_IN]) show in the first welcome after they changed; later welcomes
 * show the installed build's post instead (its own highlights and notes), and only when it has notes. Local state, not
 * in backups.
 */
internal object XgramWelcome {
  /** The version whose welcome was shown (or skipped: a fresh install, a build without a post). */
  private const val KEY_CODE = "xgram_welcome_code"

  /** The [BUILT_IN] set the user has seen. */
  private const val KEY_HIGHLIGHTS = "xgram_welcome_highlights"

  /** Raise when [BUILT_IN] changes, so the next update shows the new set once. */
  private const val HIGHLIGHTS_VERSION = 1

  /** Stable 0.29.1.1818's four big features, the welcome's carousel. */
  val BUILT_IN = listOf(XgramFeature.ACTIVITY, XgramFeature.UNDO_SEND, XgramFeature.CHECK_IN, XgramFeature.LATER)

  private val pmc
    get() = Settings.instance().pmc()

  /**
   * Whether this version's welcome is still to come. The first time on a fresh install it records the version and the
   * highlights instead: there is nothing new to someone who just installed XGram.
   */
  fun isDue(): Boolean {
    val seen = pmc.getInt(KEY_CODE, 0)
    if (seen >= BuildConfig.ORIGINAL_VERSION_CODE) {
      return false
    }
    if (seen == 0 && isFreshInstall()) {
      pmc.putInt(KEY_CODE, BuildConfig.ORIGINAL_VERSION_CODE)
      pmc.putInt(KEY_HIGHLIGHTS, HIGHLIGHTS_VERSION)
      return false
    }
    return true
  }

  /** Whether the welcome shows [BUILT_IN] (with Try it) rather than the post's highlights. */
  fun builtInDue(): Boolean = pmc.getInt(KEY_HIGHLIGHTS, 0) < HIGHLIGHTS_VERSION

  /** This version's welcome was shown ([builtIn]: with [BUILT_IN]), or there is none to show. */
  fun done(builtIn: Boolean) {
    pmc.putInt(KEY_CODE, BuildConfig.ORIGINAL_VERSION_CODE)
    if (builtIn) {
      pmc.putInt(KEY_HIGHLIGHTS, HIGHLIGHTS_VERSION)
    }
  }

  /** Installed and never updated since: no version before this one ran here (builds before the welcome kept none). */
  private fun isFreshInstall(): Boolean = try {
    val context = UI.getContext()
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.firstInstallTime == info.lastUpdateTime
  } catch (t: Throwable) {
    false
  }
}
