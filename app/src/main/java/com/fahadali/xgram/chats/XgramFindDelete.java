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
package com.fahadali.xgram.chats;

import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fahadali.xgram.export.ExportOptions;
import com.fahadali.xgram.export.ExportOptionsSheet;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import tgx.td.ChatId;
import tgx.td.MessageId;

/**
 * Find and delete (chat menu): finds the chat's messages from some people and/or containing some words (export's Sent
 * by and Containing, * for any letters), says how many, and deletes them all for everyone. Telegram's search finds
 * them (per sender; a word that starts with * needs the whole history read); each one is checked against the words
 * again on the phone, since Telegram's search matches word starts. In groups, other people's messages need the right
 * to delete messages: those that can't be deleted are counted and left.
 */
public final class XgramFindDelete {
  private XgramFindDelete () { }

  private static final int LIMIT = 100;

  /** Groups and private chats (in channels, Telegram's own admin tools). */
  public static boolean canUse (Tdlib tdlib, long chatId) {
    return chatId != 0 && !ChatId.isSecret(chatId) && !tdlib.isChannel(chatId);
  }

  private static final class Filters {
    final List<TdApi.MessageSender> senders = new ArrayList<>();
    String containing = "";
  }

  public static void show (ViewController<?> c, long chatId) {
    final Filters filters = new Filters();
    final List<ListItem> rows = new ArrayList<>();
    rows.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramExportSentBy, 0, R.string.XgramExportSentBy, false));
    rows.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramExportContaining, 0, R.string.XgramExportContaining, false));
    c.showSettings(new SettingsWrapBuilder(R.id.btn_xgramFindDelete)
      .xgramSetHeadline(R.id.btn_xgramFindDelete, Lang.getString(R.string.XgramFindDelete))
      .addHeaderItem(Lang.getString(R.string.XgramFindDeleteInfo))
      .setRawItems(rows)
      .setSettingProcessor((item, view, isUpdate) -> {
        final int id = item.getId();
        if (id == R.id.btn_xgramExportSentBy) {
          view.setData(ExportOptionsSheet.sendersLabel(c, filters.senders));
        } else if (id == R.id.btn_xgramExportContaining) {
          view.setData(filters.containing.isEmpty() ? Lang.getString(R.string.XgramExportAnyText) : filters.containing);
        }
      })
      .setOnSettingItemClick((view, settingsId, item, doneButton, adapter, window) -> {
        final int id = item.getId();
        if (id == R.id.btn_xgramExportSentBy) {
          ExportOptionsSheet.pickSenders(c, chatId, filters.senders, () -> adapter.updateValuedSettingById(R.id.btn_xgramExportSentBy));
        } else if (id == R.id.btn_xgramExportContaining) {
          ExportOptionsSheet.pickContaining(c, filters.containing, typed -> {
            filters.containing = typed;
            adapter.updateValuedSettingById(R.id.btn_xgramExportContaining);
          });
        }
      })
      .setSaveStr(R.string.XgramFindDeleteFind)
      .setAllowResize(false)
      .setIntDelegate((id, result) -> {
        if (filters.senders.isEmpty() && filters.containing.isEmpty()) {
          UI.showToast(R.string.XgramFindDeleteNeedFilter, Toast.LENGTH_SHORT);
          return;
        }
        new Search(c, chatId, filters.senders.toArray(new TdApi.MessageSender[0]), filters.containing).start();
      }));
  }

  /** One search: the senders one after another (or the whole history), every match's id, then the question. */
  private static final class Search {
    final ViewController<?> c;
    final Tdlib tdlib;
    final long chatId;
    final TdApi.MessageSender[] senders;
    final String containing, query;
    @Nullable final Pattern pattern;
    final TreeSet<Long> found = new TreeSet<>(Collections.reverseOrder());
    int senderIndex;

    Search (ViewController<?> c, long chatId, TdApi.MessageSender[] senders, String containing) {
      this.c = c;
      this.tdlib = c.tdlib();
      this.chatId = chatId;
      this.senders = senders;
      this.containing = containing;
      this.query = ExportOptions.serverQuery(containing);
      this.pattern = ExportOptions.wordPattern(containing);
    }

    void start () {
      UI.showToast(senders.length == 0 && query.isEmpty() ? R.string.XgramFindDeleteSearchingAll : R.string.XgramFindDeleteSearching, Toast.LENGTH_SHORT);
      next(0);
    }

    private void next (long from) {
      if (senders.length == 0 && query.isEmpty()) {
        // A word that starts with *: Telegram's search can't take it, the whole history is read
        tdlib.send(new TdApi.GetChatHistory(chatId, from, 0, LIMIT, false), (page, error) -> {
          if (error != null || page == null || page.messages == null || page.messages.length == 0) {
            done();
            return;
          }
          long oldest = from;
          for (TdApi.Message message : page.messages) {
            take(message);
            oldest = oldest == 0 ? message.id : Math.min(oldest, message.id);
          }
          if (oldest == from) {
            done();
          } else {
            next(oldest);
          }
        });
        return;
      }
      final TdApi.MessageSender sender = senders.length > 0 ? senders[senderIndex] : null;
      tdlib.send(new TdApi.SearchChatMessages(chatId, null, query, sender, from, 0, LIMIT, null), (result, error) -> {
        final boolean end = error != null || result == null || result.messages == null || result.messages.length == 0 || result.nextFromMessageId == 0 || result.nextFromMessageId == from;
        if (result != null && result.messages != null) {
          for (TdApi.Message message : result.messages) {
            take(message);
          }
        }
        if (!end) {
          next(result.nextFromMessageId);
        } else if (++senderIndex < senders.length) {
          next(0);
        } else {
          done();
        }
      });
    }

    private void take (TdApi.Message message) {
      if (message != null && message.sendingState == null && MessageId.toServerMessageId(message.id) != 0 && ExportOptions.matchesText(pattern, message)) {
        found.add(message.id);
      }
    }

    private void done () {
      c.runOnUiThreadOptional(() -> {
        if (found.isEmpty()) {
          UI.showToast(R.string.XgramFindDeleteNone, Toast.LENGTH_SHORT);
          return;
        }
        final int count = found.size();
        final long newest = found.first();
        c.showOptions(Lang.getString(R.string.XgramFindDeleteConfirm, Lang.plural(R.string.xMessages, count)),
          new int[] {R.id.btn_xgramFindDelete, R.id.btn_xgramFindDeleteShow, R.id.btn_cancel},
          new String[] {Lang.getString(R.string.XgramFindDeleteDelete, Lang.plural(R.string.xMessages, count)), Lang.getString(R.string.XgramFindDeleteShow), Lang.getString(R.string.Cancel)},
          new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
          new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_search_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_xgramFindDelete) {
              delete();
            } else if (id == R.id.btn_xgramFindDeleteShow && c instanceof MessagesController) {
              ((MessagesController) c).highlightMessage(new tgx.td.MessageId(chatId, newest));
            }
            return true;
          });
      });
    }

    private void delete () {
      final long[] ids = new long[found.size()];
      int i = 0;
      for (long id : found) {
        ids[i++] = id;
      }
      UI.showToast(R.string.XgramFindDeleteDeleting, Toast.LENGTH_SHORT);
      deleteBatch(ids, 0, 0, 0);
    }

    /** 100 at a time; a batch Telegram refuses is tried one by one, so the ones that can be deleted still are. */
    private void deleteBatch (long[] ids, int start, int deleted, int failed) {
      if (start >= ids.length) {
        tdlib.ui().post(() -> UI.showToast(failed == 0 ?
          Lang.getString(R.string.XgramFindDeleteDone, Lang.plural(R.string.xMessages, deleted)) :
          Lang.getString(R.string.XgramFindDeleteDonePartly, Lang.plural(R.string.xMessages, deleted), Lang.plural(R.string.xMessages, failed)), Toast.LENGTH_LONG));
        return;
      }
      final int end = Math.min(ids.length, start + LIMIT);
      final long[] batch = java.util.Arrays.copyOfRange(ids, start, end);
      tdlib.send(new TdApi.DeleteMessages(chatId, batch, true), (ok, error) -> {
        if (error == null) {
          deleteBatch(ids, end, deleted + batch.length, failed);
        } else if (batch.length > 1) {
          deleteOneByOne(ids, start, end, start, deleted, failed);
        } else {
          deleteBatch(ids, end, deleted, failed + 1);
        }
      });
    }

    private void deleteOneByOne (long[] ids, int start, int end, int index, int deleted, int failed) {
      if (index >= end) {
        deleteBatch(ids, end, deleted, failed);
        return;
      }
      tdlib.send(new TdApi.DeleteMessages(chatId, new long[] {ids[index]}, true), (ok, error) ->
        deleteOneByOne(ids, start, end, index + 1, deleted + (error == null ? 1 : 0), failed + (error == null ? 0 : 1)));
    }
  }
}
