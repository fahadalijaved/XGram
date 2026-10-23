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

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.fahadali.xgram.XgramComposeBridge
import com.fahadali.xgram.XgramReorder
import com.fahadali.xgram.XgramSettingDeps
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.chats.XgramInlineVideo
import com.fahadali.xgram.chats.XgramInputButtonsController
import com.fahadali.xgram.chats.XgramSwipeActions
import com.fahadali.xgram.chats.XgramUndoSend
import com.fahadali.xgram.checkin.XgramCheckInSheet
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.GroupScope
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.layout.XgramKeyboard
import org.thunderdog.challegram.R

/** Messages: the message menu, swipes and taps, defaults, links, videos, the message box and Telegram's AI tools. */
@Composable
fun MessagesPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramMessages)) {
    val settings = XgramSettings.instance()
    // Worked out once per build of the page, not while rows are drawn.
    val hidden = XgramComposeBridge.messageMenuHiddenCount()

    // No header over this group: its only row is called Message menu already.
    group {
      value(
        R.id.btn_xgramMessageMenu, s(R.string.XgramMessageMenu),
        if (hidden > 0) {
          s(R.string.XgramHiddenCount, hidden)
        } else {
          s(if (XgramReorder.hasOrder(XgramReorder.MESSAGE_MENU)) R.string.XgramOrderCustom else R.string.XgramOrderDefault)
        }
      ) {
        host.openPage(XgramPage.MESSAGE_MENU)
      }
    }

    header(R.string.XgramR7ChReactHeader)
    group {
      switch(
        R.id.btn_xgramR7ChannelReactions, s(R.string.XgramR7ChReactTitle), s(R.string.XgramR7ChReactSub),
        checked = settings.channelReactions()
      ) {
        // Open chats follow at once: MessagesController listens for the key.
        settings.setChannelReactions(it)
      }
    }
    description(R.string.XgramR7ChReactInfo)

    header(R.string.XgramHeaderGestures)
    group {
      swipe(host, settings, R.id.btn_xgramSwipeLeft, R.string.XgramSwipeLeft, toRight = false)
      swipe(host, settings, R.id.btn_xgramSwipeRight, R.string.XgramSwipeRight, toRight = true)
    }
    description(R.string.XgramSwipeInfo)
    group {
      switch(R.id.btn_xgramTapTimeDetails, s(R.string.XgramTapTimeDetails), s(R.string.XgramTapTimeDetailsSub), checked = settings.tapTimeForDetails()) {
        settings.setTapTimeForDetails(it)
      }
      switch(R.id.btn_xgramPullToNext, s(R.string.XgramPullToNext), s(R.string.XgramPullToNextSub), checked = settings.pullToNext()) {
        settings.setPullToNext(it)
      }
    }

    header(R.string.XgramHeaderDefaults)
    group {
      switch(
        R.id.btn_xgramDeleteForEveryone, s(R.string.XgramDeleteForEveryone), s(R.string.XgramCheckedByDefault),
        checked = settings.deleteForEveryoneByDefault()
      ) {
        settings.setDeleteForEveryoneByDefault(it)
      }
      switch(
        R.id.btn_xgramForwardWithoutAuthor, s(R.string.XgramForwardWithoutAuthor), s(R.string.XgramCheckedByDefault),
        checked = settings.forwardWithoutAuthor()
      ) {
        settings.setForwardWithoutAuthor(it)
      }
    }

    header(R.string.XgramHeaderLinks)
    group {
      switch(R.id.btn_xgramCleanLinks, s(R.string.XgramCleanLinks), s(R.string.XgramCleanLinksSub), checked = settings.cleanLinks()) {
        settings.setCleanLinks(it)
      }
      switch(R.id.btn_xgramFixLinkPreviews, s(R.string.XgramFixLinkPreviews), s(R.string.XgramFixLinkPreviewsSub), checked = settings.fixLinkPreviews()) {
        settings.setFixLinkPreviews(it)
      }
    }
    description(R.string.XgramFixLinkPreviewsInfo)

    header(R.string.XgramHeaderVideos)
    group {
      switch(R.id.btn_xgramInlineVideo, s(R.string.XgramInlineVideo), s(R.string.XgramInlineVideoSub), checked = settings.inlineVideo()) {
        settings.setInlineVideo(it)
        // Open chats are rebuilt too: whether a video message gets the player is decided when the message is made.
        XgramInlineVideo.postSettingChanged()
      }
      buttons(
        R.id.btn_xgramInlineVideoStart, s(R.string.XgramInlineVideoStart),
        listOf(
          s(R.string.XgramComposeInlineVideoAuto) to XgramSettings.INLINE_VIDEO_START_AUTO,
          s(R.string.XgramInlineVideoStartTap) to XgramSettings.INLINE_VIDEO_START_TAP
        ),
        selected = settings.inlineVideoStart(),
        enabled = XgramSettingDeps.inlineVideoStartEnabled() // only read for videos played in the message
      ) {
        settings.setInlineVideoStart(it)
      }
    }
    // What the rows do, or that Telegram X's Autoplay GIFs keeps videos from starting by themselves.
    description(XgramSettingDeps.inlineVideoInfo())
    group {
      switch(
        R.id.btn_xgramVideoAutoHide, s(R.string.XgramVideoAutoHide), s(R.string.XgramVideoAutoHideSub),
        checked = settings.videoAutoHide()
      ) {
        settings.setVideoAutoHide(it)
      }
    }
    description(R.string.XgramVideoAutoHideInfo)

    header(R.string.XgramMessageBox)
    group {
      val hidden = XgramInputButtonsController.hiddenCount()
      value(
        R.id.btn_xgramInputButtons, s(R.string.XgramInputButtons),
        if (hidden > 0) s(R.string.XgramHiddenCount, hidden) else s(R.string.XgramAllShown)
      ) {
        host.openPage(XgramPage.INPUT_BUTTONS)
      }
      // Changes the voice and video button, which the buttons above can hide.
      val recordReason = XgramSettingDeps.tapToRecordReason()
      switch(
        R.id.btn_xgramTapToRecord, s(R.string.XgramTapToRecord),
        s(if (recordReason != 0) recordReason else R.string.XgramTapToRecordSub),
        checked = settings.tapToRecord(), enabled = recordReason == 0
      ) {
        settings.setTapToRecord(it)
      }
    }
    description(R.string.XgramTapToRecordInfo)
    group {
      // Enter to send: Auto keeps Telegram X's switch and sends when a hardware keyboard is attached (XgramKeyboard).
      value(R.id.btn_xgramEnterToSend, s(R.string.XgramEnterToSend), XgramKeyboard.enterToSendName(settings.enterToSend())) {
        host.choose(
          ChoiceSpec(
            title = s(R.string.XgramEnterToSend),
            options = listOf(XgramSettings.ENTER_TO_SEND_AUTO, XgramSettings.ENTER_TO_SEND_ON, XgramSettings.ENTER_TO_SEND_OFF)
              .map { XgramKeyboard.enterToSendName(it) to it },
            selected = settings.enterToSend()
          ) { picked ->
            settings.setEnterToSend(picked)
          }
        )
      }
    }
    description(R.string.XgramEnterToSendInfo)
    group {
      // Undo send: how long a message waits; voice and video messages only wait while it is on.
      val delay = settings.undoSendSeconds()
      value(R.id.btn_xgramUndoSend, s(R.string.XgramUndoSend), XgramUndoSend.delayName(delay)) {
        host.choose(
          ChoiceSpec(
            title = s(R.string.XgramUndoSend),
            info = s(R.string.XgramUndoSendChoiceInfo),
            options = XgramSettings.UNDO_SEND_SECONDS.map { XgramUndoSend.delayName(it) to it },
            selected = delay
          ) { picked ->
            settings.setUndoSendSeconds(picked)
          }
        )
      }
      // Faded bubbles in the chat, and only the newest waiting: only read while Undo send is on.
      val optionsReason = XgramSettingDeps.undoSendOptionsReason()
      switch(
        R.id.btn_xgramUndoSendBubbles, s(R.string.XgramUndoSendBubbles),
        s(if (optionsReason != 0) optionsReason else R.string.XgramUndoSendBubblesSub),
        checked = settings.undoSendBubbles(), enabled = optionsReason == 0
      ) {
        settings.setUndoSendBubbles(it)
      }
      switch(
        R.id.btn_xgramUndoSendNewest, s(R.string.XgramUndoSendNewest),
        s(if (optionsReason != 0) optionsReason else R.string.XgramUndoSendNewestSub),
        checked = settings.undoSendNewestOnly(), enabled = optionsReason == 0
      ) {
        settings.setUndoSendNewestOnly(it)
      }
      val voiceReason = XgramSettingDeps.undoSendVoiceReason()
      switch(
        R.id.btn_xgramUndoSendVoice, s(R.string.XgramUndoSendVoice),
        s(if (voiceReason != 0) voiceReason else R.string.XgramUndoSendVoiceSub),
        checked = settings.undoSendVoice(), enabled = voiceReason == 0
      ) {
        settings.setUndoSendVoice(it)
      }
    }
    description(R.string.XgramUndoSendInfo)

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

    header(R.string.XgramHeaderTelegramAi)
    group {
      switch(R.id.btn_xgramAiTools, s(R.string.XgramAiTools), s(R.string.XgramAiToolsSub), checked = settings.aiTools()) {
        settings.setAiTools(it)
      }
    }
    description(R.string.XgramAiToolsInfo)
  }
}

/** Swipe left or right: the action a swipe on a message takes, picked in a sheet. */
private fun GroupScope.swipe(host: XgramComposeHost, settings: XgramSettings, id: Int, @StringRes title: Int, toRight: Boolean) {
  val current = settings.swipeAction(toRight)
  value(id, s(title), XgramSwipeActions.name(current)) {
    host.choose(
      ChoiceSpec(
        title = s(title),
        options = XgramSwipeActions.ACTIONS.map { XgramSwipeActions.name(it) to it },
        selected = current
      ) { action ->
        settings.setSwipeAction(toRight, action)
      }
    )
  }
}
