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
package com.fahadali.xgram.compose.pages

import androidx.compose.runtime.Composable
import com.fahadali.xgram.XgramSettingDeps
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.activity.XgramActivityHiddenController
import com.fahadali.xgram.activity.XgramActivityStore
import com.fahadali.xgram.checkin.XgramCheckInSheet
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.layout.XgramNavToolbar
import com.fahadali.xgram.later.XgramLater
import com.fahadali.xgram.later.XgramLaterRecord
import org.thunderdog.challegram.R

/**
 * Activity and reminders: Activity (com.fahadali.xgram.activity, every mention, reply and reaction in one place),
 * Later's snoozed chats (com.fahadali.xgram.later) and Check In's timer (com.fahadali.xgram.checkin, started from a
 * chat's ⋮ menu). The same rows as the classic page.
 */
@Composable
fun ActivityPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramActivitySection)) {
    val settings = XgramSettings.instance()
    val accountId = host.tdlib.accountId()

    // No header: the switch says what it is.
    group {
      switch(R.id.btn_xgramActivityShow, s(R.string.XgramActivityShow), s(R.string.XgramActivityShowInfo), checked = settings.activityOn()) {
        settings.setActivityOn(it)
      }
    }

    // The rest only does something while Activity is on: the reason instead of the line under the title.
    val activityReason = XgramSettingDeps.activityOptionsReason()
    val activityEnabled = activityReason == 0
    fun sub(info: Int): String = s(if (activityEnabled) info else activityReason)

    header(R.string.XgramHeaderInclude)
    group {
      switch(R.id.btn_xgramActivityReactions, s(R.string.XgramActivityIncludeReactions), sub(R.string.XgramActivityIncludeReactionsInfo),
        checked = settings.activityReactions(), enabled = activityEnabled) {
        settings.setActivityReactions(it)
      }
      switch(R.id.btn_xgramActivityMuted, s(R.string.XgramActivityIncludeMuted), sub(R.string.XgramActivityIncludeMutedInfo),
        checked = settings.activityMuted(), enabled = activityEnabled) {
        settings.setActivityMuted(it)
      }
      switch(R.id.btn_xgramActivityDiscussion, s(R.string.XgramActivityIncludeDiscussion), sub(R.string.XgramActivityIncludeDiscussionInfo),
        checked = settings.activityDiscussion(), enabled = activityEnabled) {
        settings.setActivityDiscussion(it)
      }
      switch(R.id.btn_xgramActivityLookBack, s(R.string.XgramActivityLookBack), sub(R.string.XgramActivityLookBackInfo),
        checked = settings.activityLookBack(), enabled = activityEnabled) {
        settings.setActivityLookBack(it)
      }
    }

    header(R.string.XgramHeaderWhereItShows)
    group {
      switch(R.id.btn_xgramActivityBadge, s(R.string.XgramActivityBadge), sub(R.string.XgramActivityBadgeInfo),
        checked = settings.activityBadge(), enabled = activityEnabled) {
        settings.setActivityBadge(it)
      }
      // The floating toolbar's Activity tab is chosen with the other tabs, on the Navigation bar page
      val activityTab = (settings.navTabs() and (1 shl XgramNavToolbar.DEST_ACTIVITY)) != 0
      value(R.id.btn_xgramActivityNavBar, s(R.string.XgramActivityNavBar),
        s(if (activityTab) R.string.XgramActivityNavBarTab else R.string.XgramActivityBarOff), enabled = activityEnabled) {
        host.openPage(XgramPage.NAV_BAR)
      }
    }
    // The bell above the chat list: its only switch is on Chat list > Header buttons
    description(R.string.XgramActivityNavBarInfo)
    group {
      val hidden = XgramActivityStore.instance().summary(accountId).hiddenCount()
      value(
        R.id.btn_xgramActivityHidden, s(R.string.XgramActivityHiddenChats),
        when (hidden) {
          0 -> s(R.string.XgramNone)
          1 -> s(R.string.XgramActivityHiddenCountOne)
          else -> s(R.string.XgramActivityHiddenCountMany, hidden)
        }
      ) {
        host.open(XgramActivityHiddenController(host.activity, host.tdlib))
      }
      action(R.id.btn_xgramActivityClearAll, s(R.string.XgramActivityClearAll), s(R.string.XgramActivityClearAllInfo), destructive = true) {
        host.confirm(ConfirmSpec(
          title = s(R.string.XgramActivityClearAllConfirm),
          text = s(R.string.XgramActivityClearAllInfo),
          confirm = s(R.string.XgramActivityClearAllButton),
          destructive = true
        ) {
          XgramActivityStore.instance().clearAll(accountId)
        })
      }
    }
    description(R.string.XgramActivityLocalNote)

    // Later: snoozed chats and reminders
    header(R.string.XgramLaterHeader)
    group {
      switch(R.id.btn_xgramLaterRow, s(R.string.XgramLaterRowSetting), s(R.string.XgramLaterRowSettingSub), checked = XgramLater.showRow()) {
        XgramLater.setShowRow(it)
      }
      switch(R.id.btn_xgramLaterRaise, s(R.string.XgramLaterRaise), s(R.string.XgramLaterRaiseSub), checked = XgramLater.raiseWoken()) {
        XgramLater.setRaiseWoken(it)
      }
      value(R.id.btn_xgramLaterWakeEarly, s(R.string.XgramLaterWakeEarly), XgramLater.wakeEarlyName(XgramLater.wakeEarlyMode())) {
        host.choose(
          ChoiceSpec(
            title = s(R.string.XgramLaterWakeEarly),
            options = listOf(
              s(R.string.XgramLaterWakeToMe) to XgramLaterRecord.WAKE_TO_ME,
              s(R.string.XgramLaterWakeAny) to XgramLaterRecord.WAKE_ANY,
              s(R.string.XgramLaterWakeTimeOnly) to XgramLaterRecord.WAKE_TIME_ONLY
            ),
            selected = XgramLater.wakeEarlyMode(),
            // XGram Later: the sub-line belongs to "On messages to me" only
            subtitles = listOf(s(R.string.XgramLaterWakeToMeSub), null, null)
          ) { mode ->
            XgramLater.setWakeEarlyMode(mode)
          }
        )
      }
      value(
        R.id.btn_xgramLaterExact, s(R.string.XgramLaterExact),
        XgramLater.exactRowValue(host.activity)
      ) {
        XgramLater.openExactRow(host.controller)
      }
      link(R.id.btn_xgramLaterChannel, s(R.string.XgramLaterChannelLink), s(R.string.XgramLaterChannelLinkSub)) {
        XgramLater.openReminderChannelSettings(host.activity)
      }
    }

    // Check In: a safety timer, started from a chat's ⋮ menu
    header(R.string.XgramCheckInHeader)
    group {
      val timer = settings.checkInTimer()
      value(R.id.btn_xgramCheckInTimer, s(R.string.XgramCheckInTimer), XgramCheckInSheet.durationName(timer)) {
        host.choose(
          ChoiceSpec(
            title = s(R.string.XgramCheckInTimer),
            options = XgramSettings.CHECK_IN_TIMERS.map { XgramCheckInSheet.durationName(it) to it },
            selected = timer
          ) { minutes ->
            settings.setCheckInTimer(minutes)
          }
        )
      }
      buttons(
        R.id.btn_xgramCheckInGrace, s(R.string.XgramCheckInGrace),
        // Short labels: four full plurals don't fit the connected row on the folded screen.
        XgramSettings.CHECK_IN_GRACES.map { s(R.string.XgramCheckInMinShort, it) to it },
        selected = settings.checkInGrace(),
        subtitle = s(R.string.XgramCheckInGraceSub)
      ) {
        settings.setCheckInGrace(it)
      }
      // How close counts as arrived, for "End when I arrive at a place…".
      buttons(
        R.id.btn_xgramCheckInRadius, s(R.string.XgramCheckInRadius),
        XgramSettings.CHECK_IN_RADII.map { XgramCheckInSheet.radiusName(it) to it },
        selected = settings.checkInRadius()
      ) {
        settings.setCheckInRadius(it)
      }
      val location = settings.checkInLocation()
      switch(R.id.btn_xgramCheckInLocation, s(R.string.XgramCheckInIncludeLocation), s(R.string.XgramCheckInLocationSub), checked = location) {
        settings.setCheckInLocation(it)
      }
      switch(R.id.btn_xgramCheckInBattery, s(R.string.XgramCheckInIncludeBattery), checked = settings.checkInBattery()) {
        settings.setCheckInBattery(it)
      }
      // Only applies while the alert carries a location.
      switch(
        R.id.btn_xgramCheckInFollowUp, s(R.string.XgramCheckInFollowUp),
        s(if (location) R.string.XgramCheckInFollowUpSub else R.string.XgramCheckInFollowUpNeeds),
        checked = settings.checkInFollowUp(), enabled = location
      ) {
        settings.setCheckInFollowUp(it)
      }
    }
    description(R.string.XgramCheckInInfo)
  }
}
