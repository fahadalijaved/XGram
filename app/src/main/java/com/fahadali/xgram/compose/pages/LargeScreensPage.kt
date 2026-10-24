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
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.layout.XgramWindow
import org.thunderdog.challegram.R

/**
 * Appearance > Large screens and foldables: two panes and their widths, the fold's postures, drag and drop. Mostly for
 * tablets and unfolded phones, so off Appearance's own page. The same rows as the classic page.
 */
@Composable
fun LargeScreensPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramLargeScreens)) {
    val settings = XgramSettings.instance()

    // How wide the panes are: half each, or the width set with the handle on the line between them. Both only with
    // two panes; Split evenly leaves nothing to resize.
    val twoPaneReason = XgramSettingDeps.twoPaneOptionReason()
    val resizeReason = XgramSettingDeps.paneResizableReason()
    header(R.string.XgramHeaderTwoPanes)
    group {
      switch(R.id.btn_xgramTwoPane, s(R.string.XgramTwoPane), s(R.string.XgramTwoPaneSub), checked = settings.twoPaneLayout()) {
        settings.setTwoPaneLayout(it)
        host.activity.xgramTwoPane()?.update()
      }
      switch(
        R.id.btn_xgramPaneSplitEvenly, s(R.string.XgramPaneSplitEvenly),
        s(if (twoPaneReason != 0) twoPaneReason else R.string.XgramPaneSplitEvenlySub),
        checked = settings.paneSplitEvenly(), enabled = XgramSettingDeps.twoPaneOptionsEnabled()
      ) {
        settings.setPaneSplitEvenly(it)
        host.activity.xgramTwoPane()?.update()
      }
      switch(
        R.id.btn_xgramPaneResizable, s(R.string.XgramPaneResizable),
        s(if (resizeReason != 0) resizeReason else R.string.XgramPaneResizableSub),
        checked = settings.paneResizable(), enabled = XgramSettingDeps.paneResizableEnabled()
      ) {
        settings.setPaneResizable(it)
        host.activity.xgramTwoPane()?.update()
      }
    }
    description(XgramSettingDeps.paneInfo())
    group {
      switch(R.id.btn_xgramLimitMessageWidth, s(R.string.XgramLimitMessageWidth), s(R.string.XgramLimitMessageWidthSub), checked = settings.limitMessageWidth()) {
        settings.setLimitMessageWidth(it)
      }
    }

    // Foldables: only where a fold can be reported (as the classic page).
    if (XgramWindow.isPostureSupported()) {
      header(R.string.XgramHeaderFoldables)
      group {
        switch(
          R.id.btn_xgramFoldHingeSplit, s(R.string.XgramFoldHingeSplit),
          s(if (twoPaneReason != 0) twoPaneReason else R.string.XgramFoldHingeSplitSub),
          checked = settings.foldHingeSplit(), enabled = XgramSettingDeps.twoPaneOptionsEnabled()
        ) {
          settings.setFoldHingeSplit(it)
          host.activity.xgramTwoPane()?.update()
        }
        switch(R.id.btn_xgramFoldTabletop, s(R.string.XgramFoldTabletop), s(R.string.XgramFoldTabletopSub), checked = settings.foldTabletop()) {
          settings.setFoldTabletop(it)
        }
      }
      description(R.string.XgramFoldInfo)
    }

    header(R.string.XgramHeaderDragDrop)
    group {
      switch(R.id.btn_xgramDragDrop, s(R.string.XgramDragDrop), s(R.string.XgramDragDropSub), checked = settings.dragDrop()) {
        settings.setDragDrop(it)
      }
      switch(R.id.btn_xgramOpenBeside, s(R.string.XgramOpenBeside), s(R.string.XgramOpenBesideSub), checked = settings.openBeside()) {
        settings.setOpenBeside(it)
      }
    }
    description(R.string.XgramDragDropInfo)
  }
}
