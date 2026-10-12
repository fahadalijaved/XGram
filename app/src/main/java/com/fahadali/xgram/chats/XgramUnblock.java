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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.lambda.RunnableData;

/** Blocked users screen: unblock everyone, or only deleted accounts (NagramX). */
public final class XgramUnblock {
  private XgramUnblock () { }

  private static final int PAGE = 100;

  public static void showMenu (ViewController<?> context, @Nullable TdApi.BlockList blockList) {
    context.showOptions(null,
      new int[] {R.id.btn_xgramUnblockDeleted, R.id.btn_xgramUnblockAll, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramUnblockDeleted), Lang.getString(R.string.XgramUnblockAll), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_sweep_24, R.drawable.baseline_block_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_xgramUnblockDeleted) {
          unblock(context.tdlib(), blockList, true);
        } else if (id == R.id.btn_xgramUnblockAll) {
          context.showOptions(Lang.getString(R.string.XgramUnblockAllConfirm),
            new int[] {R.id.btn_xgramUnblockAll, R.id.btn_cancel},
            new String[] {Lang.getString(R.string.XgramUnblockAll), Lang.getString(R.string.Cancel)},
            new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
            new int[] {R.drawable.baseline_block_24, R.drawable.baseline_cancel_24},
            (v, confirmId) -> {
              if (confirmId == R.id.btn_xgramUnblockAll) {
                unblock(context.tdlib(), blockList, false);
              }
              return true;
            });
        }
        return true;
      });
  }

  private static void unblock (Tdlib tdlib, @Nullable TdApi.BlockList blockList, boolean deletedOnly) {
    collect(tdlib, blockList, 0, new ArrayList<>(), senders -> {
      int count = 0;
      for (TdApi.MessageSender sender : senders) {
        if (!deletedOnly || isDeletedAccount(tdlib, sender)) {
          tdlib.send(new TdApi.SetMessageSenderBlockList(sender, null), tdlib.typedOkHandler());
          count++;
        }
      }
      final int total = count;
      tdlib.ui().post(() -> UI.showToast(Lang.getString(R.string.XgramUnblockedCount, total), Toast.LENGTH_SHORT));
    });
  }

  // Reads the whole list first: unblocking while paging would shift it.
  private static void collect (Tdlib tdlib, @Nullable TdApi.BlockList blockList, int offset, List<TdApi.MessageSender> out, RunnableData<List<TdApi.MessageSender>> after) {
    tdlib.send(new TdApi.GetBlockedMessageSenders(blockList, offset, PAGE), (senders, error) -> {
      if (error != null) {
        tdlib.ui().post(() -> UI.showError(error));
        return;
      }
      Collections.addAll(out, senders.senders);
      if (senders.senders.length == PAGE && out.size() < senders.totalCount) {
        collect(tdlib, blockList, offset + senders.senders.length, out, after);
      } else {
        after.runWithData(out);
      }
    });
  }

  private static boolean isDeletedAccount (Tdlib tdlib, TdApi.MessageSender sender) {
    if (!(sender instanceof TdApi.MessageSenderUser)) {
      return false;
    }
    TdApi.User user = tdlib.cache().user(((TdApi.MessageSenderUser) sender).userId);
    return user != null && user.type.getConstructor() == TdApi.UserTypeDeleted.CONSTRUCTOR;
  }
}
