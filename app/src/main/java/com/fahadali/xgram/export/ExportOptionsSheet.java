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
package com.fahadali.xgram.export;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.fahadali.xgram.compose.XgramR5ChatPickers;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Telegram Desktop's export options, as a Telegram X settings sheet (the toolkit of its automatic download sheet): the
 * media types (Photos, Videos, Voice messages, Video messages, Stickers, GIFs, Files), the size limit right under
 * Files as in Desktop, the date range (From / To with XGram's date picker) and, opened in a topic, "Only this topic".
 * The types and the size limit are remembered; Export hands the options on.
 */
final class ExportOptionsSheet {
  private ExportOptionsSheet () { }

  interface Callback {
    @UiThread
    void onStart (ExportOptions options);
  }

  /** Telegram's first day (14 August 2013): nothing is older. */
  private static final int TELEGRAM_START_YEAR = 2013, TELEGRAM_START_MONTH = Calendar.AUGUST, TELEGRAM_START_DAY = 14;

  /** The checkboxes in Desktop's order, with the type each one stands for. */
  private static final int[] TYPE_IDS = {
    R.id.btn_photos, R.id.btn_video, R.id.btn_voice, R.id.btn_videoNote, R.id.btn_stickers, R.id.btn_gif, R.id.btn_file
  };
  private static final int[] TYPE_NAMES = {
    R.string.Photos, R.string.Videos, R.string.VoiceMessages, R.string.VideoMessages, R.string.Stickers, R.string.GIFs, R.string.Files
  };
  private static final int[] TYPES = {
    ExportOptions.TYPE_PHOTOS, ExportOptions.TYPE_VIDEOS, ExportOptions.TYPE_VOICE, ExportOptions.TYPE_VIDEO_NOTES,
    ExportOptions.TYPE_STICKERS, ExportOptions.TYPE_GIFS, ExportOptions.TYPE_FILES
  };

  /** The picked days, as the local midnight in ms (0: Beginning / Today). Main thread. */
  private static final class Range {
    long fromDayMs, tillDayMs;
  }

  @UiThread
  static void show (ViewController<?> c, long chatId, @Nullable TdApi.MessageTopic topic, Callback callback) {
    final int topicScope = scopeOf(topic);
    final Range range = new Range();
    final int types = ExportOptions.lastTypes();

    final List<ListItem> rows = new ArrayList<>(TYPE_IDS.length + 4);
    for (int i = 0; i < TYPE_IDS.length; i++) {
      rows.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, TYPE_IDS[i], 0, TYPE_NAMES[i], (types & TYPES[i]) != 0));
    }
    // Desktop puts the size limit right under Files; the builder's own slider would sit above every row
    final String[] sizes = new String[ExportOptions.SIZE_STEPS_MB.length];
    for (int i = 0; i < sizes.length; i++) {
      sizes[i] = Lang.getString(R.string.fileSize_MB, String.valueOf(ExportOptions.SIZE_STEPS_MB[i]));
    }
    rows.add(new ListItem(ListItem.TYPE_SLIDER, R.id.btn_size, 0, R.string.XgramExportSizeLimit, false).setSliderInfo(sizes, ExportOptions.lastSizeIndex()));
    rows.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramExportFrom, 0, R.string.XgramExportFrom, false));
    rows.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramExportTill, 0, R.string.XgramExportTill, false));
    if (topicScope != ExportOptions.SCOPE_WHOLE) {
      rows.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_xgramExportOnlyTopic, 0, R.string.XgramExportOnlyTopic, true));
    }

    final String info = Lang.getString(R.string.XgramExportOptionsInfo) + "\n\n" + Lang.getString(R.string.XgramExportMediaInfo);
    c.showSettings(new SettingsWrapBuilder(R.id.btn_xgramExportOptions)
      .xgramSetHeadline(R.id.btn_xgramExportOptions, Lang.getString(R.string.XgramExportChat))
      .addHeaderItem(info)
      .setRawItems(rows)
      .setSettingProcessor((item, view, isUpdate) -> {
        final int id = item.getId();
        if (id == R.id.btn_xgramExportFrom) {
          view.setData(range.fromDayMs != 0 ? dayLabel(range.fromDayMs) : Lang.getString(R.string.Beginning));
        } else if (id == R.id.btn_xgramExportTill) {
          view.setData(range.tillDayMs != 0 ? dayLabel(range.tillDayMs) : Lang.getString(R.string.Today));
        }
      })
      .setOnSettingItemClick((view, settingsId, item, doneButton, adapter, window) -> {
        final int id = item.getId();
        if (id == R.id.btn_xgramExportFrom || id == R.id.btn_xgramExportTill) {
          pickDay(c, range, id == R.id.btn_xgramExportFrom, adapter);
        }
      })
      .setSaveStr(R.string.XgramExportStart)
      .setAllowResize(false)
      .setIntDelegate((id, result) -> {
        final int mask = typesOf(result);
        final int sizeIndex = result.get(R.id.btn_size, ExportOptions.lastSizeIndex());
        ExportOptions.saveLast(mask, sizeIndex);
        final boolean onlyTopic = topicScope != ExportOptions.SCOPE_WHOLE && result.get(R.id.btn_xgramExportOnlyTopic) != 0;
        final int fromDate = range.fromDayMs != 0 ? (int) (range.fromDayMs / 1000L) : 0;
        // "To" is the whole picked day: the range ends at the next local midnight; Today is no end at all
        final int tillDate = range.tillDayMs != 0 ? (int) (nextDayMs(range.tillDayMs) / 1000L) : 0;
        callback.onStart(new ExportOptions(c.tdlib().id(), chatId,
          onlyTopic ? topicScope : ExportOptions.SCOPE_WHOLE, onlyTopic ? topicIdOf(topic) : 0,
          mask, ExportOptions.sizeBytesAt(sizeIndex), fromDate, tillDate));
      }));
  }

  private static int typesOf (SparseIntArray result) {
    int mask = 0;
    for (int i = 0; i < TYPE_IDS.length; i++) {
      if (result.get(TYPE_IDS[i]) != 0) {
        mask |= TYPES[i];
      }
    }
    return mask;
  }

  /** The scope "Only this topic" stands for, {@link ExportOptions#SCOPE_WHOLE} outside a topic (or in a thread). */
  private static int scopeOf (@Nullable TdApi.MessageTopic topic) {
    if (topic instanceof TdApi.MessageTopicForum) {
      return ExportOptions.SCOPE_FORUM_TOPIC;
    }
    if (topic instanceof TdApi.MessageTopicSavedMessages) {
      return ExportOptions.SCOPE_SAVED_TOPIC;
    }
    if (topic instanceof TdApi.MessageTopicDirectMessages) {
      return ExportOptions.SCOPE_DM_TOPIC;
    }
    return ExportOptions.SCOPE_WHOLE;
  }

  private static long topicIdOf (@Nullable TdApi.MessageTopic topic) {
    if (topic instanceof TdApi.MessageTopicForum) {
      return ((TdApi.MessageTopicForum) topic).forumTopicId;
    }
    if (topic instanceof TdApi.MessageTopicSavedMessages) {
      return ((TdApi.MessageTopicSavedMessages) topic).savedMessagesTopicId;
    }
    if (topic instanceof TdApi.MessageTopicDirectMessages) {
      return ((TdApi.MessageTopicDirectMessages) topic).directMessagesChatTopicId;
    }
    return 0;
  }

  // The date rows

  private static String dayLabel (long dayMs) {
    return Lang.dateYearFull(dayMs, TimeUnit.MILLISECONDS);
  }

  /**
   * XGram's date picker for From or To. Each limits the other instead of swapping them (From can't be after To, To
   * can't be before From); its start button is Beginning for From and Today for To. Without Expressive design,
   * Telegram X's platform date dialog, as for Jump to date.
   */
  private static void pickDay (ViewController<?> c, Range range, boolean from, SettingsAdapter adapter) {
    final long telegramStart = dayStart(TELEGRAM_START_YEAR, TELEGRAM_START_MONTH, TELEGRAM_START_DAY);
    final long today = startOfDay(System.currentTimeMillis());
    final long min = from ? telegramStart : (range.fromDayMs != 0 ? range.fromDayMs : telegramStart);
    final long max = from ? (range.tillDayMs != 0 ? range.tillDayMs : today) : today;
    final long current = from ? range.fromDayMs : range.tillDayMs;
    final long initial = current != 0 ? current : (from ? min : max);
    final int rowId = from ? R.id.btn_xgramExportFrom : R.id.btn_xgramExportTill;
    final String neutral = Lang.getString(from ? R.string.Beginning : R.string.Today);
    final Runnable onNeutral = () -> {
      if (from) {
        range.fromDayMs = 0;
      } else {
        range.tillDayMs = 0;
      }
      adapter.updateValuedSettingById(rowId);
    };
    final XgramR5ChatPickers.DateCallback onPick = (year, month, dayOfMonth) -> {
      final long day = dayStart(year, month, dayOfMonth);
      if (from) {
        range.fromDayMs = day;
      } else {
        // Today's date is the open end: messages sent after the sheet was opened are in too
        range.tillDayMs = day >= today ? 0 : day;
      }
      adapter.updateValuedSettingById(rowId);
    };
    if (XgramR5ChatPickers.showDatePicker(c.context(), initial, min, max, onPick, neutral, onNeutral, Lang.getString(R.string.OK))) {
      return;
    }
    final Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(initial);
    final DatePickerDialog dialog = new DatePickerDialog(c.context(), Theme.dialogTheme(),
      (view, year, month, dayOfMonth) -> onPick.onDatePicked(year, month, dayOfMonth),
      calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
    dialog.setButton(DialogInterface.BUTTON_NEUTRAL, neutral, (d, which) -> onNeutral.run());
    try {
      dialog.getDatePicker().setMaxDate(Math.max(max, min) + TimeUnit.DAYS.toMillis(1) - 1);
      dialog.getDatePicker().setMinDate(min);
    } catch (Throwable ignored) { }
    ViewSupport.showDatePicker(dialog);
  }

  private static long dayStart (int year, int month, int dayOfMonth) {
    final Calendar c = Calendar.getInstance();
    c.clear();
    c.set(year, month, dayOfMonth, 0, 0, 0);
    return c.getTimeInMillis();
  }

  private static long startOfDay (long timeMs) {
    final Calendar c = Calendar.getInstance();
    c.setTimeInMillis(timeMs);
    return dayStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
  }

  /** The local midnight after {@code dayMs} (a day is not always 24 hours: summer time). */
  private static long nextDayMs (long dayMs) {
    final Calendar c = Calendar.getInstance();
    c.setTimeInMillis(dayMs);
    c.add(Calendar.DAY_OF_MONTH, 1);
    return dayStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
  }
}
