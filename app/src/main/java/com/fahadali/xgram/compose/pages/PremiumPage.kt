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

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.popups.ModernActionedLayout
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.tool.Screen
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.ui.EmojiStatusSelectorEmojiPage
import org.thunderdog.challegram.ui.SettingsArchiveChatListController
import org.thunderdog.challegram.ui.SettingsFoldersController
import org.thunderdog.challegram.ui.SettingsPrivacyKeyController
import java.util.concurrent.TimeUnit

/**
 * XGram: Telegram Premium in one place. The account's state and a gift code, then every Premium setting XGram has
 * (each opens the screen it lives on, or is a switch here), the limits Premium raises, and what Premium includes with
 * where each part is in XGram. Rows that need Premium are greyed out without it. Buying Premium stays in the official
 * app, as buying Stars does.
 */
@Composable
fun PremiumPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  var premiumState by remember { mutableStateOf<TdApi.PremiumState?>(null) }
  var features by remember { mutableStateOf<TdApi.PremiumFeatures?>(null) }
  var newChat by remember { mutableStateOf<TdApi.NewChatPrivacySettings?>(null) }
  var readDate by remember { mutableStateOf<TdApi.ReadDatePrivacySettings?>(null) }
  var archive by remember { mutableStateOf<TdApi.ArchiveChatListSettings?>(null) }
  var noAds by remember { mutableStateOf<Boolean?>(null) }
  var adsBusy by remember { mutableStateOf(false) }
  var tagsOn by remember { mutableStateOf<Boolean?>(null) }
  var tagsBusy by remember { mutableStateOf(false) }

  // Read again whenever the page gets focus: a screen opened from here (or another device) may have changed them
  DisposableEffect(tdlib, host.version) {
    var active = true
    tdlib.send(TdApi.GetPremiumState()) { result, _ -> if (result != null) UI.post { if (active) premiumState = result } }
    tdlib.send(TdApi.GetPremiumFeatures(null)) { result, _ -> if (result != null) UI.post { if (active) features = result } }
    tdlib.send(TdApi.GetNewChatPrivacySettings()) { result, _ -> if (result != null) UI.post { if (active) newChat = result } }
    tdlib.send(TdApi.GetReadDatePrivacySettings()) { result, _ -> if (result != null) UI.post { if (active) readDate = result } }
    tdlib.send(TdApi.GetArchiveChatListSettings()) { result, _ -> if (result != null) UI.post { if (active) archive = result } }
    tdlib.send(TdApi.GetUserFullInfo(tdlib.myUserId())) { result, _ ->
      if (result != null) UI.post { if (active) noAds = !result.hasSponsoredMessagesEnabled }
    }
    tagsOn = null // Telegram's value again (tdlib.areChatFolderTagsEnabled), now that the update has had time to come
    onDispose { active = false }
  }

  SettingsPage(title = str(R.string.XgramPremium)) {
    // Read at use, never kept: the page unlocks as soon as Telegram says the account has Premium
    val premium = tdlib.hasPremium()
    val needs = s(R.string.XgramPremiumNeeds)

    group {
      val state = premiumState?.state?.text?.trim()
      value(1, s(R.string.XgramPremium), if (!premium) s(R.string.XgramPremiumInactive) else if (!state.isNullOrEmpty()) state else s(R.string.XgramPremiumActive), icon = R.drawable.baseline_premium_star_24) { }
      action(2, s(R.string.XgramPremiumRedeem), s(R.string.XgramPremiumRedeemInfo), icon = R.drawable.baseline_gift_outline_24) {
        host.prompt(PromptSpec(title = s(R.string.XgramPremiumRedeem), hint = s(R.string.XgramPremiumRedeemHint), confirm = s(R.string.XgramPremiumRedeemCheck)) { text ->
          redeemGiftCode(host, text)
        })
      }
    }
    if (!premium) {
      description(R.string.XgramPremiumInactiveInfo)
    }

    header(R.string.XgramPremiumPrivacy)
    group {
      val canSetMessagePrivacy = premium || tdlib.canSetNewChatPrivacySettings()
      value(10, s(R.string.XgramPremiumWhoCanMessage), if (canSetMessagePrivacy) newChatText(newChat) else needs, icon = R.drawable.baseline_chat_bubble_24, enabled = canSetMessagePrivacy) {
        host.open(SettingsPrivacyKeyController(host.activity, tdlib).apply { setArguments(SettingsPrivacyKeyController.Args.newChatsPrivacy()) })
      }
      val read = readDate
      value(11, s(R.string.XgramPremiumReadTime), if (read == null) "…" else s(if (read.showReadDate) R.string.XgramPremiumReadTimeShown else R.string.XgramPremiumReadTimeHidden), icon = R.drawable.baseline_visibility_24) {
        host.open(SettingsPrivacyKeyController(host.activity, tdlib).apply { setArguments(SettingsPrivacyKeyController.Args(TdApi.UserPrivacySettingShowStatus())) })
      }
      link(12, s(R.string.XgramPremiumVoice), if (premium) s(R.string.XgramPremiumVoiceInfo) else needs, icon = R.drawable.baseline_mic_24, enabled = premium) {
        host.open(SettingsPrivacyKeyController(host.activity, tdlib).apply { setArguments(SettingsPrivacyKeyController.Args(TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages())) })
      }
      val canArchive = premium || tdlib.autoArchiveAvailable()
      val archived = archive
      value(
        13, s(R.string.XgramPremiumArchive),
        if (!canArchive) needs else if (archived == null) "…" else s(if (archived.archiveAndMuteNewChatsFromUnknownUsers) R.string.XgramPremiumArchiveOn else R.string.XgramPremiumArchiveOff),
        icon = R.drawable.baseline_archive_24, enabled = canArchive
      ) {
        host.open(SettingsArchiveChatListController(host.activity, tdlib))
      }
      val adsOff = noAds
      switch(
        14, s(R.string.XgramPremiumNoAds), if (premium) s(R.string.XgramPremiumNoAdsInfo) else needs, icon = R.drawable.baseline_block_24,
        checked = premium && adsOff == true, enabled = premium && adsOff != null && !adsBusy, busy = adsBusy
      ) { on ->
        adsBusy = true
        tdlib.send(TdApi.ToggleHasSponsoredMessagesEnabled(!on)) { _, error ->
          UI.post {
            adsBusy = false
            if (error != null) {
              UI.showError(error)
            } else {
              noAds = on
            }
          }
        }
      }
    }

    header(R.string.XgramPremiumProfile)
    group {
      val status = tdlib.myUser()?.emojiStatus
      val statusText = when {
        !premium -> needs
        status == null -> s(R.string.XgramPremiumEmojiStatusNone)
        status.expirationDate > 0 -> s(R.string.XgramPremiumEmojiStatusUntil, Lang.dateYearShortTime(status.expirationDate.toLong(), TimeUnit.SECONDS))
        else -> s(R.string.XgramPremiumEmojiStatusSet)
      }
      value(20, s(R.string.XgramPremiumEmojiStatus), statusText, icon = R.drawable.baseline_emoticon_outline_24, enabled = premium) {
        EmojiStatusSelectorEmojiPage.Wrapper(host.activity, tdlib, host.controller, object : EmojiStatusSelectorEmojiPage.AnimationsEmojiStatusSetDelegate {
          override fun onAnimationStart() = Unit
          override fun onAnimationEnd() = host.changed() // the row's value
          override fun getDestX(): Int = Screen.currentWidth() / 2 // the picked emoji flies to the page title
          override fun getDestY(): Int = Screen.dp(56f)
        }).show()
      }
    }

    header(R.string.XgramPremiumChats)
    group {
      val tags = tagsOn ?: tdlib.areChatFolderTagsEnabled()
      switch(
        30, s(R.string.XgramPremiumFolderTags), if (premium) s(R.string.XgramPremiumFolderTagsInfo) else needs, icon = R.drawable.baseline_label_24,
        checked = premium && tags, enabled = premium && !tagsBusy, busy = tagsBusy
      ) { on ->
        tagsBusy = true
        tdlib.send(TdApi.ToggleChatFolderTags(on)) { _, error ->
          UI.post {
            tagsBusy = false
            if (error != null) {
              UI.showError(error)
            } else {
              tagsOn = on
            }
          }
        }
      }
      link(31, s(R.string.XgramPremiumFolders), s(R.string.XgramPremiumFoldersInfo), icon = R.drawable.baseline_folder_24) {
        host.open(SettingsFoldersController(host.activity, tdlib))
      }
      link(32, s(R.string.XgramPremiumSavedTags), if (premium) s(R.string.XgramPremiumSavedTagsInfo) else needs, icon = R.drawable.baseline_bookmark_24) {
        tdlib.ui().openChat(host.controller, tdlib.selfChatId(), null)
      }
      link(33, s(R.string.XgramPremiumTranslation), s(R.string.XgramPremiumTranslationInfo), icon = R.drawable.baseline_translate_24) {
        host.openPage(XgramPage.TRANSLATION)
      }
    }

    val data = features
    val limits = data?.limits
    if (!limits.isNullOrEmpty()) {
      header(R.string.XgramPremiumLimits)
      group {
        limits.forEachIndexed { index, limit ->
          val yours = if (premium) limit.premiumValue else limit.defaultValue
          val other = if (premium) limit.defaultValue else limit.premiumValue
          value(
            100 + index, limitName(limit.type),
            s(if (premium) R.string.XgramPremiumLimitYours else R.string.XgramPremiumLimitFree, Lang.formatNumber(yours.toLong()), Lang.formatNumber(other.toLong())),
            key = "limit_" + limit.type.constructor
          ) { }
        }
      }
      description(R.string.XgramPremiumLimitsInfo)
    }

    val list = data?.features
    if (!list.isNullOrEmpty()) {
      header(R.string.XgramPremiumIncluded)
      group {
        list.forEachIndexed { index, feature ->
          val (name, where) = describeFeature(feature)
          value(200 + index, name, where, key = "feature_" + feature.constructor) { }
        }
      }
      description(R.string.XgramPremiumIncludedInfo)
    }
  }
}

/** A gift code typed or pasted (a t.me/giftcode/ link too): its details with Apply, Telegram X's gift code sheet. */
private fun redeemGiftCode(host: XgramComposeHost, input: String) {
  val code = input.trim().trimEnd('/').substringAfterLast('/')
  if (code.isEmpty()) {
    return
  }
  host.tdlib.send(TdApi.CheckPremiumGiftCode(code)) { info, _ ->
    UI.post {
      if (info != null) {
        ModernActionedLayout.showGiftCode(host.controller, code, null, info)
      } else {
        UI.showToast(R.string.XgramPremiumRedeemInvalid, Toast.LENGTH_SHORT)
      }
    }
  }
}

private fun newChatText(settings: TdApi.NewChatPrivacySettings?): String = when {
  settings == null -> "…"
  settings.incomingPaidMessageStarCount > 0 -> s(
    R.string.XgramPremiumMessagePaid,
    if (settings.incomingPaidMessageStarCount == 1L) s(R.string.XgramPaidStarsOne) else s(R.string.XgramPaidStarsMany, Lang.formatNumber(settings.incomingPaidMessageStarCount))
  )
  settings.allowNewChatsFromUnknownUsers -> s(R.string.XgramPremiumMessageEverybody)
  else -> s(R.string.XgramPremiumMessageContacts)
}

/** A TDLib type's name in words, for one this page doesn't know yet: "PremiumFeatureNewThing" to "New thing". */
private fun humanize(value: Any, prefix: String): String {
  val words = value.javaClass.simpleName.removePrefix(prefix).replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
  return words.replaceFirstChar { it.uppercase() }
}

private fun limitName(type: TdApi.PremiumLimitType): String = when (type) {
  is TdApi.PremiumLimitTypeSupergroupCount -> s(R.string.XgramPremiumLimitSupergroupCount)
  is TdApi.PremiumLimitTypePinnedChatCount -> s(R.string.XgramPremiumLimitPinnedChatCount)
  is TdApi.PremiumLimitTypeCreatedPublicChatCount -> s(R.string.XgramPremiumLimitCreatedPublicChatCount)
  is TdApi.PremiumLimitTypeSavedAnimationCount -> s(R.string.XgramPremiumLimitSavedAnimationCount)
  is TdApi.PremiumLimitTypeFavoriteStickerCount -> s(R.string.XgramPremiumLimitFavoriteStickerCount)
  is TdApi.PremiumLimitTypeChatFolderCount -> s(R.string.XgramPremiumLimitChatFolderCount)
  is TdApi.PremiumLimitTypeChatFolderChosenChatCount -> s(R.string.XgramPremiumLimitChatFolderChosenChatCount)
  is TdApi.PremiumLimitTypePinnedArchivedChatCount -> s(R.string.XgramPremiumLimitPinnedArchivedChatCount)
  is TdApi.PremiumLimitTypePinnedSavedMessagesTopicCount -> s(R.string.XgramPremiumLimitPinnedSavedMessagesTopicCount)
  is TdApi.PremiumLimitTypeMessageTextLength -> s(R.string.XgramPremiumLimitMessageTextLength)
  is TdApi.PremiumLimitTypeCaptionLength -> s(R.string.XgramPremiumLimitCaptionLength)
  is TdApi.PremiumLimitTypeBioLength -> s(R.string.XgramPremiumLimitBioLength)
  is TdApi.PremiumLimitTypeChatFolderInviteLinkCount -> s(R.string.XgramPremiumLimitChatFolderInviteLinkCount)
  is TdApi.PremiumLimitTypeShareableChatFolderCount -> s(R.string.XgramPremiumLimitShareableChatFolderCount)
  is TdApi.PremiumLimitTypeActiveStoryCount -> s(R.string.XgramPremiumLimitActiveStoryCount)
  is TdApi.PremiumLimitTypeWeeklyPostedStoryCount -> s(R.string.XgramPremiumLimitWeeklyPostedStoryCount)
  is TdApi.PremiumLimitTypeMonthlyPostedStoryCount -> s(R.string.XgramPremiumLimitMonthlyPostedStoryCount)
  is TdApi.PremiumLimitTypeStoryCaptionLength -> s(R.string.XgramPremiumLimitStoryCaptionLength)
  is TdApi.PremiumLimitTypeStorySuggestedReactionAreaCount -> s(R.string.XgramPremiumLimitStorySuggestedReactionAreaCount)
  is TdApi.PremiumLimitTypeSimilarChatCount -> s(R.string.XgramPremiumLimitSimilarChatCount)
  is TdApi.PremiumLimitTypeOwnedBotCount -> s(R.string.XgramPremiumLimitOwnedBotCount)
  is TdApi.PremiumLimitTypeCustomTextCompositionStyleCount -> s(R.string.XgramPremiumLimitCustomTextCompositionStyleCount)
  else -> humanize(type, "PremiumLimitType")
}

/** A feature's name and where it is in XGram ("Official apps for now" when XGram doesn't have it yet). */
private fun describeFeature(feature: TdApi.PremiumFeature): Pair<String, String> {
  val official = R.string.XgramPremiumOfficialOnly
  val (name, where) = when (feature) {
    is TdApi.PremiumFeatureIncreasedLimits -> R.string.XgramPremiumFeatureIncreasedLimits to R.string.XgramPremiumFeatureIncreasedLimitsWhere
    is TdApi.PremiumFeatureIncreasedUploadFileSize -> R.string.XgramPremiumFeatureIncreasedUploadFileSize to R.string.XgramPremiumFeatureIncreasedUploadFileSizeWhere
    is TdApi.PremiumFeatureImprovedDownloadSpeed -> R.string.XgramPremiumFeatureImprovedDownloadSpeed to R.string.XgramPremiumFeatureImprovedDownloadSpeedWhere
    is TdApi.PremiumFeatureVoiceRecognition -> R.string.XgramPremiumFeatureVoiceRecognition to R.string.XgramPremiumFeatureVoiceRecognitionWhere
    is TdApi.PremiumFeatureDisabledAds -> R.string.XgramPremiumFeatureDisabledAds to R.string.XgramPremiumFeatureDisabledAdsWhere
    is TdApi.PremiumFeatureUniqueReactions -> R.string.XgramPremiumFeatureUniqueReactions to R.string.XgramPremiumFeatureUniqueReactionsWhere
    is TdApi.PremiumFeatureUniqueStickers -> R.string.XgramPremiumFeatureUniqueStickers to R.string.XgramPremiumFeatureUniqueStickersWhere
    is TdApi.PremiumFeatureCustomEmoji -> R.string.XgramPremiumFeatureCustomEmoji to R.string.XgramPremiumFeatureCustomEmojiWhere
    is TdApi.PremiumFeatureAdvancedChatManagement -> R.string.XgramPremiumFeatureAdvancedChatManagement to R.string.XgramPremiumFeatureAdvancedChatManagementWhere
    is TdApi.PremiumFeatureProfileBadge -> R.string.XgramPremiumFeatureProfileBadge to R.string.XgramPremiumFeatureProfileBadgeWhere
    is TdApi.PremiumFeatureEmojiStatus -> R.string.XgramPremiumFeatureEmojiStatus to R.string.XgramPremiumFeatureEmojiStatusWhere
    is TdApi.PremiumFeatureAnimatedProfilePhoto -> R.string.XgramPremiumFeatureAnimatedProfilePhoto to R.string.XgramPremiumFeatureAnimatedProfilePhotoWhere
    is TdApi.PremiumFeatureForumTopicIcon -> R.string.XgramPremiumFeatureForumTopicIcon to R.string.XgramPremiumFeatureForumTopicIconWhere
    is TdApi.PremiumFeatureAppIcons -> R.string.XgramPremiumFeatureAppIcons to R.string.XgramPremiumFeatureAppIconsWhere
    is TdApi.PremiumFeatureRealTimeChatTranslation -> R.string.XgramPremiumFeatureRealTimeChatTranslation to R.string.XgramPremiumFeatureRealTimeChatTranslationWhere
    is TdApi.PremiumFeatureUpgradedStories -> R.string.XgramPremiumFeatureUpgradedStories to official
    is TdApi.PremiumFeatureChatBoost -> R.string.XgramPremiumFeatureChatBoost to R.string.XgramPremiumFeatureChatBoostWhere
    is TdApi.PremiumFeatureAccentColor -> R.string.XgramPremiumFeatureAccentColor to R.string.XgramPremiumFeatureAccentColorWhere
    is TdApi.PremiumFeatureBackgroundForBoth -> R.string.XgramPremiumFeatureBackgroundForBoth to official
    is TdApi.PremiumFeatureSavedMessagesTags -> R.string.XgramPremiumFeatureSavedMessagesTags to R.string.XgramPremiumFeatureSavedMessagesTagsWhere
    is TdApi.PremiumFeatureMessagePrivacy -> R.string.XgramPremiumFeatureMessagePrivacy to R.string.XgramPremiumFeatureMessagePrivacyWhere
    is TdApi.PremiumFeatureLastSeenTimes -> R.string.XgramPremiumFeatureLastSeenTimes to R.string.XgramPremiumFeatureLastSeenTimesWhere
    is TdApi.PremiumFeatureBusiness -> R.string.XgramPremiumFeatureBusiness to official
    is TdApi.PremiumFeatureMessageEffects -> R.string.XgramPremiumFeatureMessageEffects to R.string.XgramPremiumFeatureMessageEffectsWhere
    is TdApi.PremiumFeatureChecklists -> R.string.XgramPremiumFeatureChecklists to R.string.XgramPremiumFeatureChecklistsWhere
    is TdApi.PremiumFeaturePaidMessages -> R.string.XgramPremiumFeaturePaidMessages to R.string.XgramPremiumFeaturePaidMessagesWhere
    is TdApi.PremiumFeatureProtectPrivateChatContent -> R.string.XgramPremiumFeatureProtectPrivateChatContent to official
    is TdApi.PremiumFeatureTextComposition -> R.string.XgramPremiumFeatureTextComposition to R.string.XgramPremiumFeatureTextCompositionWhere
    is TdApi.PremiumFeatureRichMessages -> R.string.XgramPremiumFeatureRichMessages to official
    else -> return humanize(feature, "PremiumFeature") to s(official)
  }
  return s(name) to s(where)
}
