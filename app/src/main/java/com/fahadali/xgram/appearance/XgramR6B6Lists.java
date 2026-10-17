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
package com.fahadali.xgram.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawModifier;
import org.thunderdog.challegram.widget.NoScrollTextView;

/**
 * M3E round 6, batch 6: Telegram X's settings lists, chat folders and the group and channel pages. The sentence-case
 * table the shared list labels read ({@link XgramR5Lists#label}), and the row pieces those pages share: a selected
 * (secondaryContainer) tile, a status dot with a trailing tabular meta label, a trailing drag handle, a small filled
 * tonal button with a leading icon, the modal sheet surface and the hero row that holds the 96dp avatar picker.
 * Everything here only draws with {@link XgramExpressive#isEnabled()}; colors are roles read at draw time, so light,
 * dark and Pure black apply to views already built. Main thread only.
 */
public final class XgramR6B6Lists {
  private XgramR6B6Lists () { }

  // Sentence case

  /** Telegram X's title case strings used in settings rows, headers and page titles, each with its XGram sentence case. */
  private static final int[] SENTENCE = {
    R.string.AbortPassword, R.string.XgramR6B6ScAbortPassword,
    R.string.AddContact, R.string.XgramR6B6ScAddContact,
    R.string.AdditionalInviteLinks, R.string.XgramR6B6ScAdditionalInviteLinks,
    R.string.AggressiveAntiSpam, R.string.XgramR6B6ScAggressiveAntiSpam,
    R.string.AllowBotsAndMiniApps, R.string.XgramR6B6ScAllowBotsAndMiniApps,
    R.string.AllowNotifications, R.string.XgramR6B6ScAllowNotifications,
    R.string.AnimatedEmoji, R.string.XgramR6B6ScAnimatedEmoji,
    R.string.AnimatedStickers, R.string.XgramR6B6ScAnimatedStickers,
    R.string.AppLogs, R.string.XgramR6B6ScAppLogs,
    R.string.ApproveNewMembers, R.string.XgramR6B6ScApproveNewMembers,
    R.string.InviteLinkAdminApproval, R.string.XgramR6B6ScApproveNewMembers,
    R.string.ArchiveAsFolder, R.string.XgramR6B6ScArchiveAsFolder,
    R.string.ArchiveNonContacts, R.string.XgramR6B6ScArchiveNonContacts,
    R.string.ArchiveSettings, R.string.XgramR6B6ScArchiveSettings,
    R.string.AskAQuestion, R.string.XgramR6B6ScAskAQuestion,
    R.string.AutoLock, R.string.XgramR6B6ScAutoLock,
    R.string.AutoNightMode, R.string.XgramR6B6ScAutoNightMode,
    R.string.AutoNightSystem, R.string.XgramR6B6ScAutoNightSystem,
    R.string.AvatarsInReactions, R.string.XgramR6B6ScAvatarsInReactions,
    R.string.BadgeCounter, R.string.XgramR6B6ScBadgeCounter,
    R.string.BadgeCounterArchive, R.string.XgramR6B6ScBadgeCounterArchive,
    R.string.BadgeCounterMessages, R.string.XgramR6B6ScBadgeCounterMessages,
    R.string.BadgeCounterMuted, R.string.XgramR6B6ScBadgeCounterMuted,
    R.string.BatmanTransitions, R.string.XgramR6B6ScBatmanTransitions,
    R.string.BigEmoji, R.string.XgramR6B6ScBigEmoji,
    R.string.BigReactions, R.string.XgramR6B6ScBigReactions,
    R.string.BlockedSenders, R.string.XgramR6B6ScBlockedSenders,
    R.string.Camera, R.string.XgramR6B6ScCamera,
    R.string.CameraGrid, R.string.XgramR6B6ScCameraGrid,
    R.string.CameraKeepMedia, R.string.XgramR6B6ScCameraKeepMedia,
    R.string.CameraRatio, R.string.XgramR6B6ScCameraRatio,
    R.string.CameraRatioFull, R.string.XgramR6B6ScCameraRatioFull,
    R.string.CameraSettings, R.string.XgramR6B6ScCameraSettings,
    R.string.CameraType, R.string.XgramR6B6ScCameraType,
    R.string.CategoryMain, R.string.XgramR6B6ScCategoryMain,
    R.string.ChangePasscode, R.string.XgramR6B6ScChangePasscode,
    R.string.ChangePassword, R.string.XgramR6B6ScChangePassword,
    R.string.ChangeRecoveryEmail, R.string.XgramR6B6ScChangeRecoveryEmail,
    R.string.ChannelLink, R.string.XgramR6B6ScChannelLink,
    R.string.ChannelName, R.string.XgramR6B6ScChannelName,
    R.string.ChannelPrivate, R.string.XgramR6B6ScChannelPrivate,
    R.string.ChannelPublic, R.string.XgramR6B6ScChannelPublic,
    R.string.ChannelShowAuthors, R.string.XgramR6B6ScChannelShowAuthors,
    R.string.ChannelSignMessages, R.string.XgramR6B6ScChannelSignMessages,
    R.string.ChargeForMessages, R.string.XgramR6B6ScChargeForMessages,
    R.string.ChatFolders, R.string.XgramR6B6ScChatFolders,
    R.string.ChatHistory, R.string.XgramR6B6ScChatHistory,
    R.string.ChatListStyle, R.string.XgramR6B6ScChatListStyle,
    R.string.ChatMode, R.string.XgramR6B6ScChatMode,
    R.string.ChatPermissions, R.string.XgramR6B6ScChatPermissions,
    R.string.ChatStyleBubbles, R.string.XgramR6B6ScChatStyleBubbles,
    R.string.ChatStyleBubblesChannel, R.string.XgramR6B6ScChatStyleBubblesChannel,
    R.string.ColorTheme, R.string.XgramR6B6ScColorTheme,
    R.string.ConvertToBroadcastGroup, R.string.XgramR6B6ScConvertToBroadcastGroup,
    R.string.CopyReportData, R.string.XgramR6B6ScCopyReportData,
    R.string.CopyVersion, R.string.XgramR6B6ScCopyVersion,
    R.string.CreateANewLink, R.string.XgramR6B6ScCreateANewLink,
    R.string.CreateAnInviteLink, R.string.XgramR6B6ScCreateAnInviteLink,
    R.string.CreateLink, R.string.XgramR6B6ScCreateLink,
    R.string.CreateNewFolder, R.string.XgramR6B6ScCreateNewFolder,
    R.string.CustomVibrations, R.string.XgramR6B6ScCustomVibrations,
    R.string.DataSaver, R.string.XgramR6B6ScDataSaver,
    R.string.DataSettings, R.string.XgramR6B6ScDataSettings,
    R.string.DebugLogFiles, R.string.XgramR6B6ScDebugLogFiles,
    R.string.LogFiles, R.string.XgramR6B6ScDebugLogFiles,
    R.string.DebugLogSize, R.string.XgramR6B6ScDebugLogSize,
    R.string.DebugLogTags, R.string.XgramR6B6ScDebugLogTags,
    R.string.DebugLogcat, R.string.XgramR6B6ScDebugLogcat,
    R.string.DebugVerbosity, R.string.XgramR6B6ScDebugVerbosity,
    R.string.DeleteAllRevokedLinks, R.string.XgramR6B6ScDeleteAllRevokedLinks,
    R.string.DeleteFolder, R.string.XgramR6B6ScDeleteFolder,
    R.string.DisablePassword, R.string.XgramR6B6ScDisablePassword,
    R.string.DoubleShort, R.string.XgramR6B6ScDoubleShort,
    R.string.DynamicEmojiPackOrder, R.string.XgramR6B6ScDynamicEmojiPackOrder,
    R.string.DynamicStickerPackOrder, R.string.XgramR6B6ScDynamicStickerPackOrder,
    R.string.EditBot, R.string.XgramR6B6ScEditBot,
    R.string.EditBotUsername, R.string.XgramR6B6ScEditBotUsername,
    R.string.EditGroup, R.string.XgramR6B6ScEditGroup,
    R.string.EditPrivacyGlobal, R.string.XgramR6B6ScEditPrivacyGlobal,
    R.string.EditPrivacyReadDate, R.string.XgramR6B6ScEditPrivacyReadDate,
    R.string.Emoji, R.string.XgramR6B6ScEmoji,
    R.string.EmojiPacks, R.string.XgramR6B6ScEmojiPacks,
    R.string.EmojiSets, R.string.XgramR6B6ScEmojiSets,
    R.string.EncryptionKey, R.string.XgramR6B6ScEncryptionKey,
    R.string.EraseDatabase, R.string.XgramR6B6ScEraseDatabase,
    R.string.EventLog, R.string.XgramR6B6ScEventLog,
    R.string.EventNewContact2, R.string.XgramR6B6ScEventNewContact2,
    R.string.EventScheduledMessage, R.string.XgramR6B6ScEventScheduledMessage,
    R.string.ExcludeChats, R.string.XgramR6B6ScExcludeChats,
    R.string.Experiment_NoEdgeToEdge, R.string.XgramR6B6ScExperimentNoEdgeToEdge,
    R.string.Experiment_PeerIds, R.string.XgramR6B6ScExperimentPeerIds,
    R.string.FolderActionExcludeChats, R.string.XgramR6B6ScFolderActionChats,
    R.string.FolderActionIncludeChats, R.string.XgramR6B6ScFolderActionChats,
    R.string.FolderExcludedChats, R.string.XgramR6B6ScFolderExcludedChats,
    R.string.FolderIncludedChats, R.string.XgramR6B6ScFolderIncludedChats,
    R.string.FolderLink, R.string.XgramR6B6ScFolderLink,
    R.string.GiftFromUnknown, R.string.XgramR6B6ScGiftFromUnknown,
    R.string.GiftLinkActivated, R.string.XgramR6B6ScGiftLinkActivated,
    R.string.GiftLinkRedeem, R.string.XgramR6B6ScGiftLinkRedeem,
    R.string.GroupInfo, R.string.XgramR6B6ScGroupInfo,
    R.string.GroupLink, R.string.XgramR6B6ScGroupLink,
    R.string.GroupPrivate, R.string.XgramR6B6ScGroupPrivate,
    R.string.GroupPublic, R.string.XgramR6B6ScGroupPublic,
    R.string.HideMembers, R.string.XgramR6B6ScHideMembers,
    R.string.HideReadTime, R.string.XgramR6B6ScHideReadTime,
    R.string.HideSecret, R.string.XgramR6B6ScHideSecret,
    R.string.HoldToPreview, R.string.XgramR6B6ScHoldToPreview,
    R.string.Icons, R.string.XgramR6B6ScIcons,
    R.string.InChannels, R.string.XgramR6B6ScInChannels,
    R.string.InChatSound, R.string.XgramR6B6ScInChatSound,
    R.string.InGroups, R.string.XgramR6B6ScInGroups,
    R.string.InPrivateChats, R.string.XgramR6B6ScInPrivateChats,
    R.string.IncludeChats, R.string.XgramR6B6ScIncludeChats,
    R.string.IncludeDismissed, R.string.XgramR6B6ScIncludeDismissed,
    R.string.IncognitoKeyboard, R.string.XgramR6B6ScIncognitoKeyboard,
    R.string.InstallGooglePlayServices, R.string.XgramR6B6ScInstallGooglePlayServices,
    R.string.InviteLink, R.string.XgramR6B6ScInviteLink,
    R.string.InviteLinkCopy, R.string.XgramR6B6ScInviteLinkCopy,
    R.string.InviteLinkDelete, R.string.XgramR6B6ScInviteLinkDelete,
    R.string.InviteLinkManage, R.string.XgramR6B6ScInviteLinkManage,
    R.string.InviteLinkRequests, R.string.XgramR6B6ScInviteLinkRequests,
    R.string.InviteLinks, R.string.XgramR6B6ScInviteLinks,
    R.string.JunkFiles, R.string.XgramR6B6ScJunkFiles,
    R.string.KeepMedia, R.string.XgramR6B6ScKeepMedia,
    R.string.LaunchApp, R.string.XgramR6B6ScLaunchApp,
    R.string.LaunchAppCheckUpdate, R.string.XgramR6B6ScLaunchAppCheckUpdate,
    R.string.LinkedChannel, R.string.XgramR6B6ScLinkedChannel,
    R.string.LinkedGroup, R.string.XgramR6B6ScLinkedGroup,
    R.string.LocalDatabase, R.string.XgramR6B6ScLocalDatabase,
    R.string.LocalFolder, R.string.XgramR6B6ScLocalFolder,
    R.string.LocalizationEditConfirmSave, R.string.XgramR6B6ScLocalizationEditConfirmSave,
    R.string.LoginEmail, R.string.XgramR6B6ScLoginEmail,
    R.string.LoopAnimatedStickers, R.string.XgramR6B6ScLoopAnimatedStickers,
    R.string.ManageChannel, R.string.XgramR6B6ScManageChannel,
    R.string.ManageGroup, R.string.XgramR6B6ScManageGroup,
    R.string.MapPreviewProvider, R.string.XgramR6B6ScMapPreviewProvider,
    R.string.MapPreviewProviderNone, R.string.XgramR6B6ScMapPreviewProviderNone,
    R.string.MediaAndFiles, R.string.XgramR6B6ScMediaAndFiles,
    R.string.Mentions, R.string.XgramR6B6ScMentions,
    R.string.MessageContentPreview, R.string.XgramR6B6ScMessageContentPreview,
    R.string.MessagePreview, R.string.XgramR6B6ScMessagePreview,
    R.string.MobileUsage, R.string.XgramR6B6ScMobileUsage,
    R.string.MuteNonContacts, R.string.XgramR6B6ScMuteNonContacts,
    R.string.MyContacts, R.string.XgramR6B6ScMyContacts,
    R.string.NetworkUsage, R.string.XgramR6B6ScNetworkUsage,
    R.string.NeverAllow, R.string.XgramR6B6ScNeverAllow,
    R.string.NeverShareWith, R.string.XgramR6B6ScNeverShareWith,
    R.string.NewChannel, R.string.XgramR6B6ScNewChannel,
    R.string.NewFolder, R.string.XgramR6B6ScNewFolder,
    R.string.NewGroup, R.string.XgramR6B6ScNewGroup,
    R.string.NoFiles, R.string.XgramR6B6ScNoFiles,
    R.string.NoMusic, R.string.XgramR6B6ScNoMusic,
    R.string.NoPhotos, R.string.XgramR6B6ScNoPhotos,
    R.string.NoVideos, R.string.XgramR6B6ScNoVideos,
    R.string.NotificationImportance, R.string.XgramR6B6ScNotificationImportance,
    R.string.OnMobileNetwork, R.string.XgramR6B6ScOnMobileNetwork,
    R.string.OnRoaming, R.string.XgramR6B6ScOnRoaming,
    R.string.OtherChats, R.string.XgramR6B6ScOtherChats,
    R.string.PasscodeItem, R.string.XgramR6B6ScPasscodeLock,
    R.string.PasscodeTitle, R.string.XgramR6B6ScPasscodeLock,
    R.string.passcode_passcodeInvisibility, R.string.XgramR6B6ScPasscodeInvisibility,
    R.string.passcode_fingerprint, R.string.XgramR6B6ScPasscodeFingerprint,
    R.string.passcode_biometrics, R.string.XgramR6B6ScPasscodeBiometrics,
    R.string.PinnedMessages, R.string.XgramR6B6ScPinnedMessages,
    R.string.PollSettingAdding, R.string.XgramR6B6ScPollSettingAdding,
    R.string.PollSettingCountries, R.string.XgramR6B6ScPollSettingCountries,
    R.string.PollSettingDuration, R.string.XgramR6B6ScPollSettingDuration,
    R.string.PollSettingHideResults, R.string.XgramR6B6ScPollSettingHideResults,
    R.string.PollSettingMembers, R.string.XgramR6B6ScPollSettingMembers,
    R.string.PollSettingMultiple, R.string.XgramR6B6ScPollSettingMultiple,
    R.string.PollSettingQuiz, R.string.XgramR6B6ScPollSettingQuiz,
    R.string.PollSettingRevoting, R.string.XgramR6B6ScPollSettingRevoting,
    R.string.PollSettingShowVoters, R.string.XgramR6B6ScPollSettingShowVoters,
    R.string.PollSettingShuffle, R.string.XgramR6B6ScPollSettingShuffle,
    R.string.PrimaryInviteLink, R.string.XgramR6B6ScPrimaryInviteLink,
    R.string.PrimaryInviteLinkMenu, R.string.XgramR6B6ScPrimaryInviteLinkMenu,
    R.string.Prioritize, R.string.XgramR6B6ScPrioritize,
    R.string.Privacy, R.string.XgramR6B6ScPrivacy,
    R.string.PrivacyDeleteCloudDrafts, R.string.XgramR6B6ScPrivacyDeleteCloudDrafts,
    R.string.PrivacyMessagePreview, R.string.XgramR6B6ScPrivacyMessagePreview,
    R.string.PrivacyPaymentsClear, R.string.XgramR6B6ScPrivacyPaymentsClear,
    R.string.PrivacyPolicy, R.string.XgramR6B6ScPrivacyPolicy,
    R.string.PrivacySettings, R.string.XgramR6B6ScPrivacySettings,
    R.string.PrivateChats, R.string.XgramR6B6ScPrivateChats,
    R.string.ProxyNone, R.string.XgramR6B6ScProxyNone,
    R.string.PublicPhoto, R.string.XgramR6B6ScPublicPhoto,
    R.string.PushServices, R.string.XgramR6B6ScPushServices,
    R.string.QuickReaction, R.string.XgramR6B6ScQuickReaction,
    R.string.QuickReactionEnable, R.string.XgramR6B6ScQuickReactionEnable,
    R.string.RecommendedFolders, R.string.XgramR6B6ScRecommendedFolders,
    R.string.ReduceMotion, R.string.XgramR6B6ScReduceMotion,
    R.string.RemoveFolder, R.string.XgramR6B6ScRemoveFolder,
    R.string.RepeatNotifications, R.string.XgramR6B6ScRepeatNotifications,
    R.string.ResetNotifications, R.string.XgramR6B6ScResetNotifications,
    R.string.RestrictBotsAndMiniApps, R.string.XgramR6B6ScRestrictBotsAndMiniApps,
    R.string.RestrictSaving, R.string.XgramR6B6ScRestrictSaving,
    R.string.RevokeLink, R.string.XgramR6B6ScRevokeLink,
    R.string.RevokedInviteLinks, R.string.XgramR6B6ScRevokedInviteLinks,
    R.string.RightSwipeEdgeAnimation, R.string.XgramR6B6ScRightSwipeEdgeAnimation,
    R.string.RoamingUsage, R.string.XgramR6B6ScRoamingUsage,
    R.string.RtlLayout, R.string.XgramR6B6ScRtlLayout,
    R.string.ScreenCapture, R.string.XgramR6B6ScScreenCapture,
    R.string.SecretChats, R.string.XgramR6B6ScSecretChats,
    R.string.SecretWebPage, R.string.XgramR6B6ScSecretWebPage,
    R.string.SelectChats, R.string.XgramR6B6ScSelectChats,
    R.string.SessionSnapshots, R.string.XgramR6B6ScSessionSnapshots,
    R.string.SessionsTitle, R.string.XgramR6B6ScSessionsTitle,
    R.string.SetRecoveryEmail, R.string.XgramR6B6ScSetRecoveryEmail,
    R.string.SetUpUsername, R.string.XgramR6B6ScSetUpUsername,
    R.string.SettingsAndThemes, R.string.XgramR6B6ScSettingsAndThemes,
    R.string.ShareFolder, R.string.XgramR6B6ScShareFolder,
    R.string.ShareLink, R.string.XgramR6B6ScShareLink,
    R.string.ShareNotificationError, R.string.XgramR6B6ScShareNotificationError,
    R.string.ShowOtherChats, R.string.XgramR6B6ScShowOtherChats,
    R.string.ShowSecret, R.string.XgramR6B6ScShowSecret,
    R.string.SignOutAltChangeNumber, R.string.XgramR6B6ScSignOutAltChangeNumber,
    R.string.ReminderActionChangePhoneNumber, R.string.XgramR6B6ScSignOutAltChangeNumber,
    R.string.SignOutAltClearCache, R.string.XgramR6B6ScSignOutAltClearCache,
    R.string.SignOutAltHelp, R.string.XgramR6B6ScSignOutAltHelp,
    R.string.SignOutAltPasscode, R.string.XgramR6B6ScSignOutAltPasscode,
    R.string.SizeUnit, R.string.XgramR6B6ScSizeUnit,
    R.string.SlowMode, R.string.XgramR6B6ScSlowMode,
    R.string.StatsMessagePublishDate, R.string.XgramR6B6ScStatsMessagePublishDate,
    R.string.StatsMessageSharesPrivate, R.string.XgramR6B6ScStatsMessageSharesPrivate,
    R.string.StatsMessageSharesPublic, R.string.XgramR6B6ScStatsMessageSharesPublic,
    R.string.StatsMessageSignature, R.string.XgramR6B6ScStatsMessageSignature,
    R.string.StatsMessageViewCount, R.string.XgramR6B6ScStatsMessageViewCount,
    R.string.StatsNotifications, R.string.XgramR6B6ScStatsNotifications,
    R.string.StatsShares, R.string.XgramR6B6ScStatsShares,
    R.string.StatsViews, R.string.XgramR6B6ScStatsViews,
    R.string.StickerPacks, R.string.XgramR6B6ScStickerPacks,
    R.string.StickersAndEmoji, R.string.XgramR6B6ScStickersAndEmoji,
    R.string.StorageUsage, R.string.XgramR6B6ScStorageUsage,
    R.string.SuggestAnimatedEmoji, R.string.XgramR6B6ScSuggestAnimatedEmoji,
    R.string.SuggestContacts, R.string.XgramR6B6ScSuggestContacts,
    R.string.SuggestContactsDone, R.string.XgramR6B6ScSuggestContactsDone,
    R.string.SyncContacts, R.string.XgramR6B6ScSyncContacts,
    R.string.SyncContactsDelete, R.string.XgramR6B6ScSyncContactsDelete,
    R.string.SyncContactsDeleteButton, R.string.XgramR6B6ScSyncContactsDelete,
    R.string.SystemNotificationSettings, R.string.XgramR6B6ScSystemNotificationSettings,
    R.string.TdlibLogs, R.string.XgramR6B6ScTdlibLogs,
    R.string.TestMode, R.string.XgramR6B6ScTestMode,
    R.string.TextSize, R.string.XgramR6B6ScTextSize,
    R.string.ThemeCopy, R.string.XgramR6B6ScThemeCopy,
    R.string.ThemeCreate, R.string.XgramR6B6ScThemeCreate,
    R.string.ThemeCreateTitle, R.string.XgramR6B6ScThemeCreateTitle,
    R.string.ThemeSettings, R.string.XgramR6B6ScThemeSettings,
    R.string.TransferOwnershipChannel, R.string.XgramR6B6ScTransferOwnershipChannel,
    R.string.TransferOwnershipGroup, R.string.XgramR6B6ScTransferOwnershipGroup,
    R.string.Tweaks, R.string.XgramR6B6ScTweaks,
    R.string.TwoStepVerification, R.string.XgramR6B6ScTwoStepVerification,
    R.string.UseHqRoundVideos, R.string.XgramR6B6ScUseHqRoundVideos,
    R.string.UseInAppBrowser, R.string.XgramR6B6ScUseInAppBrowser,
    R.string.UseSystemFonts, R.string.XgramR6B6ScUseSystemFonts,
    R.string.ViewPackPreview, R.string.XgramR6B6ScViewPackPreview,
    R.string.ViewSourceCode, R.string.XgramR6B6ScViewSourceCode,
    R.string.ViewSourceCodeChanges, R.string.XgramR6B6ScViewSourceCodeChanges,
    R.string.ViewStats, R.string.XgramR6B6ScViewStats,
    R.string.VoiceCalls, R.string.XgramR6B6ScVoiceCalls,
    R.string.VoipUseLessData, R.string.XgramR6B6ScVoipUseLessData,
    R.string.WiFiUsage, R.string.XgramR6B6ScWiFiUsage,
    R.string.ReminderActionLearnMore, R.string.XgramR6B6ScReminderActionLearnMore,
    R.string.ReminderActionVerifyPassword, R.string.XgramR6B6ScReminderActionVerifyPassword,
    R.string.ReminderCheckTfaPasswordHide, R.string.XgramR6B6ScReminderCheckTfaPasswordHide
  };

  private static final SparseIntArray SENTENCE_MAP = buildSentenceMap();

  private static SparseIntArray buildSentenceMap () {
    SparseIntArray map = new SparseIntArray(SENTENCE.length / 2);
    for (int i = 0; i + 1 < SENTENCE.length; i += 2) {
      map.put(SENTENCE[i], SENTENCE[i + 1]);
    }
    return map;
  }

  /** The XGram sentence case string for Telegram X's title case {@code res}, or 0 when this table does not know it. */
  @StringRes
  public static int sentenceFor (@StringRes int res) {
    return res != 0 ? SENTENCE_MAP.get(res, 0) : 0;
  }

  /** A SettingView row's name: {@link XgramR5Lists#label} with Expressive design, Telegram X's string otherwise. */
  @Nullable
  public static CharSequence name (@NonNull ListItem item) {
    return XgramExpressive.isEnabled() ? XgramR5Lists.label(item) : item.getString();
  }

  /** A page title ({@code getName}): sentence case with Expressive design over the bundled English, as is otherwise. */
  @NonNull
  public static String title (@StringRes int res) {
    return XgramR5Lists.label(res);
  }

  /** {@code res} with Expressive design (a leading icon Telegram X's row does without), 0 otherwise. */
  @DrawableRes
  public static int icon (@DrawableRes int res) {
    return XgramExpressive.isEnabled() ? res : 0;
  }

  // Large title

  /**
   * XgramM3Bars' large title on a page whose list sits one level deeper than the page view (an EditBaseController page:
   * its list is inside the content frame, which the shared attach skips as not the page's own content). The same
   * checks run once the list is in its window; nothing with Expressive design off.
   */
  public static void attachLargeTitle (@NonNull org.thunderdog.challegram.navigation.ViewController<?> controller, @NonNull RecyclerView recyclerView) {
    if (!XgramExpressive.isEnabled()) {
      return;
    }
    if (recyclerView.isAttachedToWindow()) {
      attachLargeTitleNow(controller, recyclerView);
      return;
    }
    recyclerView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (@NonNull View v) {
        recyclerView.removeOnAttachStateChangeListener(this);
        attachLargeTitleNow(controller, recyclerView);
      }

      @Override
      public void onViewDetachedFromWindow (@NonNull View v) { }
    });
  }

  private static void attachLargeTitleNow (@NonNull org.thunderdog.challegram.navigation.ViewController<?> controller, @NonNull RecyclerView recyclerView) {
    if (recyclerView.getTag(R.id.xgram_m3e_large_title) instanceof XgramM3Bars.LargeTitleDecoration || !controller.xgramAllowsLargeTitle()) {
      return;
    }
    XgramM3Bars.LargeTitleDecoration decoration = new XgramM3Bars.LargeTitleDecoration(controller);
    recyclerView.addItemDecoration(decoration);
    recyclerView.addOnScrollListener(decoration.snapListener); // the idle snap of a half-collapsed title, as XgramM3Bars.attachNow
    recyclerView.setTag(R.id.xgram_m3e_large_title, decoration);
  }

  // Selected tile

  /**
   * The tile of a SettingsAdapter row in secondaryContainer while {@code selected} (the current proxy, the current
   * choice), surfaceBright otherwise. The tile is made here when the row is bound before the list made it.
   */
  public static void setRowSelected (@NonNull View row, boolean selected) {
    if (!XgramExpressive.isEnabled()) {
      return;
    }
    XgramExpressive.bindListRow(row);
    XgramExpressive.MorphDrawable morph = XgramExpressive.findMorph(row.getBackground());
    if (morph != null) {
      morph.setColorRole(selected ? XgramExpressive.SECONDARY_CONTAINER : XgramExpressive.SURFACE_BRIGHT);
    }
  }

  // Status dot and trailing meta label (proxy rows)

  /** The space a SettingView leaves before its supporting text for the 8dp status dot (dp). */
  public static final float STATUS_DOT_SPACE = 14f;

  private static @Nullable TextPaint metaPaint;
  private static int metaReservedWidth;

  private static TextPaint metaPaint () {
    TextPaint paint = metaPaint;
    if (paint == null) {
      paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      paint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
      paint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        paint.setFontFeatureSettings("tnum");
        paint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_M) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M));
      }
      metaPaint = paint;
    }
    return paint;
  }

  /**
   * An 8dp status dot in a color role before a SettingView's supporting text (the row leaves room for it with
   * SettingView.xgramSetSubtitleStart) and a trailing meta label at the row's end: labelMedium with tabular figures,
   * onSurfaceVariant, or primary when emphasized (the best ping). Its width is fixed (four digits and a unit), so the
   * row's text never jumps while the label changes.
   */
  public static final class StatusModifier implements DrawModifier {
    private int dotRole = -1;
    private @Nullable String meta;
    private boolean emphasized;

    /** {@code dotRole} -1 draws no dot, a null {@code meta} no label. */
    public void set (int dotRole, @Nullable String meta, boolean emphasized) {
      this.dotRole = dotRole;
      this.meta = meta;
      this.emphasized = emphasized;
    }

    @Override
    public int getWidth () {
      if (!XgramExpressive.isEnabled()) {
        return 0;
      }
      if (metaReservedWidth == 0) {
        metaReservedWidth = (int) Math.ceil(metaPaint().measureText(Lang.getString(R.string.XgramR6B6PingMs, "0000"))) + Screen.dp(12f);
      }
      return metaReservedWidth;
    }

    @Override
    public void afterDraw (View view, Canvas c) {
      if (!XgramExpressive.isEnabled()) {
        return;
      }
      final boolean rtl = Lang.rtl();
      final int width = view.getWidth();
      if (dotRole >= 0 && view instanceof SettingView) {
        SettingView settingView = (SettingView) view;
        final float radius = Screen.dp(4f);
        final float start = settingView.getMeasuredNameStart();
        final float cx = rtl ? width - start - radius : start + radius;
        // bodyMedium's x-height centre sits about 5dp above its baseline
        final float cy = settingView.getMeasuredNameTop() - Screen.dp(5f);
        c.drawCircle(cx, cy, radius, Paints.fillingPaint(XgramExpressive.color(dotRole)));
      }
      if (!TextUtils.isEmpty(meta)) {
        TextPaint paint = metaPaint();
        paint.setColor(XgramExpressive.color(emphasized ? XgramExpressive.PRIMARY : XgramExpressive.ON_SURFACE_VARIANT));
        final float textWidth = paint.measureText(meta);
        final float x = rtl ? Screen.dp(16f) : width - Screen.dp(16f) - textWidth;
        final Paint.FontMetrics metrics = paint.getFontMetrics();
        final float y = view.getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
        c.drawText(meta, x, y, paint);
      }
    }
  }

  // Drag handle

  /** The trailing drag handle of a reorderable row: baseline_drag_handle_24 in onSurfaceVariant, 24dp in from the end. */
  public static final float DRAG_HANDLE_CENTER = 28f;

  private static @Nullable Drawable dragHandle;
  private static int dragHandleRes;

  /** The drag handle glyph of the current icon pack, loaded once per pack. */
  @Nullable
  private static Drawable dragHandle (@NonNull View view) {
    final int res = XgramIconPack.map(R.drawable.baseline_drag_handle_24);
    if (dragHandle == null || dragHandleRes != res) {
      dragHandle = Drawables.get(view.getResources(), R.drawable.baseline_drag_handle_24);
      dragHandleRes = res;
    }
    return dragHandle;
  }

  /** Draws the trailing drag handle (icon pack glyph) centered {@code centerFromEndDp} in from the row's end. */
  public static void drawDragHandle (@NonNull View view, @NonNull Canvas c, float centerFromEndDp, float alpha) {
    if (!XgramExpressive.isEnabled()) {
      return;
    }
    Drawable icon = dragHandle(view);
    if (icon == null) {
      return;
    }
    final float cx = Lang.rtl() ? Screen.dp(centerFromEndDp) : view.getWidth() - Screen.dp(centerFromEndDp);
    final float cy = view.getHeight() / 2f;
    Drawables.drawCentered(c, icon, cx, cy, Paints.getPorterDuffPaint(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), alpha)));
  }

  /** Whether {@code x} (view coordinates) is on the 48dp touch target of the handle {@link #drawDragHandle} draws. */
  public static boolean isOnDragHandle (@NonNull View view, float x, float centerFromEndDp) {
    final float cx = Lang.rtl() ? Screen.dp(centerFromEndDp) : view.getWidth() - Screen.dp(centerFromEndDp);
    return Math.abs(x - cx) <= Screen.dp(24f);
  }

  /** A trailing drag handle as a SettingView draw modifier. It takes no text width: the row reserves it with forcePadding. */
  public static final class DragHandleModifier implements DrawModifier {
    private final float centerFromEndDp;

    public DragHandleModifier (float centerFromEndDp) {
      this.centerFromEndDp = centerFromEndDp;
    }

    @Override
    public void afterDraw (View view, Canvas c) {
      drawDragHandle(view, c, centerFromEndDp, 1f);
    }

    public boolean isOnHandle (@NonNull View view, float x) {
      return isOnDragHandle(view, x, centerFromEndDp);
    }
  }

  // A small filled tonal button with a leading icon ("Add" of a recommended folder)

  /**
   * ButtonSmallTokens + FilledTonalButtonTokens with a leading icon: 40dp, full corners that morph to 8dp while pressed,
   * secondaryContainer under onSecondaryContainer (labelLarge and a 20dp icon-pack glyph), 12dp before the icon, 8dp
   * between icon and label, 16dp after it. Disabled: onSurface at .12 under onSurface at .38. Roles are re-read when it
   * draws. Give it 48dp of height: the container is inset 4dp above and below (the touch target).
   */
  public static class TonalIconButton extends NoScrollTextView {
    private final @Nullable Drawable icon;
    private final XgramExpressive.MorphDrawable container;

    public TonalIconButton (Context context, @DrawableRes int iconRes) {
      super(context);
      XgramExpressive.applyText(this, XgramExpressive.TS_LABEL_L, false);
      setAllCaps(false);
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);
      setGravity(Gravity.CENTER);
      setIncludeFontPadding(false);
      this.icon = iconRes != 0 ? Drawables.get(context.getResources(), iconRes) : null;
      final boolean rtl = Lang.rtl();
      final int start = Screen.dp(icon != null ? 12f + XgramM3Sheets.BUTTON_ICON_SIZE + XgramM3Sheets.BUTTON_ICON_LABEL_SPACE : XgramM3Sheets.BUTTON_SPACE);
      final int end = Screen.dp(XgramM3Sheets.BUTTON_SPACE);
      setPadding(rtl ? end : start, 0, rtl ? start : end, 0);
      setMinWidth(Screen.dp(48f));
      Views.setClickable(this);
      container = XgramM3Sheets.attachFilledButton(this, XgramExpressive.SECONDARY_CONTAINER);
      // a 40dp container in a 48dp touch target: give the view 48dp of height
      setBackground(new InsetDrawable(getBackground(), 0, Screen.dp(4f), 0, Screen.dp(4f)));
      syncColors();
    }

    @Override
    public void setEnabled (boolean enabled) {
      final boolean changed = enabled != isEnabled();
      super.setEnabled(enabled);
      if (changed) {
        syncColors();
        invalidate();
      }
    }

    private int syncColors () {
      if (container == null) {
        return getCurrentTextColor(); // a call from the TextView constructor
      }
      final int content;
      if (isEnabled()) {
        container.setColorRole(XgramExpressive.SECONDARY_CONTAINER);
        content = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
      } else {
        final int onSurface = XgramExpressive.color(XgramExpressive.ON_SURFACE);
        container.setColor(XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTAINER));
        content = XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTENT);
      }
      if (getCurrentTextColor() != content) {
        setTextColor(content);
      }
      return content;
    }

    @Override
    protected void onDraw (Canvas c) {
      final int content = syncColors();
      super.onDraw(c);
      if (icon != null && icon.getMinimumWidth() > 0) {
        final float size = Screen.dp(XgramM3Sheets.BUTTON_ICON_SIZE);
        final float cx = Lang.rtl() ? getWidth() - Screen.dp(12f) - size / 2f : Screen.dp(12f) + size / 2f;
        final float cy = getHeight() / 2f;
        final float scale = size / icon.getMinimumWidth();
        final int saveCount = c.save();
        c.scale(scale, scale, cx, cy);
        Drawables.drawCentered(c, icon, cx, cy, Paints.getPorterDuffPaint(content));
        c.restoreToCount(saveCount);
      }
    }
  }

  // Modal sheet surface

  /**
   * The surface of a modal bottom sheet (SheetBottomTokens): surfaceContainerLow with 28dp top corners and the 32x4dp
   * drag handle in onSurfaceVariant at .4, 8dp from the top. The color is read when it draws.
   */
  public static final class SheetDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];

    @Override
    public void draw (@NonNull Canvas c) {
      final Rect bounds = getBounds();
      if (bounds.isEmpty()) {
        return;
      }
      rect.set(bounds);
      final float radius = Math.min(Screen.dpf(XgramExpressive.CORNER_XL), Math.min(rect.width() / 2f, rect.height()));
      radii[0] = radii[1] = radii[2] = radii[3] = radius;
      radii[4] = radii[5] = radii[6] = radii[7] = 0f;
      path.rewind();
      path.addRoundRect(rect, radii, Path.Direction.CW);
      paint.setColor(XgramM3Sheets.sheetColor());
      c.drawPath(path, paint);
      final float handleWidth = Screen.dpf(32f), handleHeight = Screen.dpf(4f), handleTop = bounds.top + Screen.dpf(8f);
      rect.set(bounds.exactCenterX() - handleWidth / 2f, handleTop, bounds.exactCenterX() + handleWidth / 2f, handleTop + handleHeight);
      paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), .4f));
      c.drawRoundRect(rect, handleHeight / 2f, handleHeight / 2f, paint);
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  /** The space the actions row of a sheet leaves under its 64dp band (dp), above the IME and navigation insets. */
  public static final float ACTIONS_BOTTOM = XgramR5Sheets.ACTIONS_BOTTOM; // the official 8dp (XgramM3Wheel.ACTIONS_BOTTOM)

  /** The height of a sheet's bottom actions: the 64dp band of {@link XgramR5Sheets#actionsRow} plus {@link #ACTIONS_BOTTOM}. */
  public static int actionsHeight () {
    return Screen.dp(XgramR5Sheets.ACTIONS_HEIGHT + ACTIONS_BOTTOM);
  }

  // Hero row with the 96dp avatar picker (group and channel pages)

  /** The size of the avatar picker of a hero row (dp). */
  public static final float AVATAR_HERO = 96f;

  /**
   * A list row that centres {@code avatar} (made 96dp here) with 16dp above and {@code bottomDp} below it, on the page
   * color (no tile). The avatar keeps its own click listener.
   */
  @NonNull
  public static FrameLayout newAvatarHero (@NonNull Context context, @NonNull View avatar, float bottomDp) {
    FrameLayout frame = new FrameLayout(context);
    frame.setId(R.id.xgram_r6b6_avatarHero);
    frame.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Screen.dp(AVATAR_HERO), Screen.dp(AVATAR_HERO), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
    params.topMargin = Screen.dp(16f);
    params.bottomMargin = Screen.dp(bottomDp);
    if (avatar.getParent() instanceof ViewGroup) {
      ((ViewGroup) avatar.getParent()).removeView(avatar);
    }
    frame.addView(avatar, params);
    return frame;
  }

  /** The height of {@link #newAvatarHero} (px). */
  public static int avatarHeroHeight (float bottomDp) {
    return Screen.dp(16f + AVATAR_HERO + bottomDp);
  }
}
