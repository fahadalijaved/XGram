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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.tool.Strings
import org.thunderdog.challegram.tool.UI
import java.util.concurrent.TimeUnit

/**
 * XGram: Telegram Stars, like official Telegram's page: the balance and the transactions (what each was, when, and
 * how many Stars came in or went out). Buying Stars stays in the official app.
 */
@Composable
fun StarsPage() {
  val host = LocalXgramHost.current
  var transactions by remember { mutableStateOf<TdApi.StarTransactions?>(null) }
  var failed by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    host.tdlib.send(TdApi.GetStarTransactions(TdApi.MessageSenderUser(host.tdlib.myUserId()), "", null, "", 100)) { result, error ->
      UI.post {
        if (result != null) {
          transactions = result
        } else {
          failed = error != null
        }
      }
    }
  }
  SettingsPage(title = str(R.string.XgramStars)) {
    val data = transactions
    group {
      value(R.id.btn_xgramStarsBalance, s(R.string.XgramStarsBalance), if (data != null) "⭐ " + Strings.buildCounter(data.starAmount.starCount) else if (failed) "—" else "…", icon = R.drawable.baseline_star_24) { }
    }
    val list = data?.transactions
    if (list != null && list.isNotEmpty()) {
      header(R.string.XgramStarsHistory)
      group {
        list.forEachIndexed { index, transaction ->
          val amount = transaction.starAmount.starCount
          val sign = if (amount > 0) "+" else ""
          val title = describe(transaction)
          val date = Lang.dateYearShortTime(transaction.date.toLong(), TimeUnit.SECONDS)
          value(index + 1, "$title · $date", "$sign${Strings.buildCounter(amount)} ⭐", key = "star_" + transaction.id) { }
        }
      }
    } else if (data != null) {
      description(R.string.XgramStarsNone)
    }
    description(R.string.XgramStarsInfo)
  }
}

/** A transaction's kind in words: its TDLib type, from "StarTransactionTypeChannelPaidReactionSend" to "Channel paid reaction send". */
private fun describe(transaction: TdApi.StarTransaction): String {
  val name = transaction.type?.javaClass?.simpleName?.removePrefix("StarTransactionType") ?: return s(R.string.XgramStars)
  val words = name.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
  val text = words.replaceFirstChar { it.uppercase() }
  return if (transaction.isRefund) s(R.string.XgramStarsRefund, text) else text
}
