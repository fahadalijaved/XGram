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
package com.fahadali.xgram.compose

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import org.thunderdog.challegram.tool.UI

/**
 * The window of an XGram Compose dialog (a platform Dialog holding a ComposeView: the update card, the M3 pickers),
 * edge to edge over the app. A dimming window is the one the system takes the navigation bar from (its dim reaches the
 * bar), and one that doesn't draw the system bar backgrounds itself gets an opaque black bar, which hid the app's
 * bottom row while the update card was open (the owner's Fold, 2026-10-24). So the window draws them: transparent,
 * without the contrast scrim, the 32% dim over the app shows evenly under both bars, and their icons stay as the app
 * has them (the dim keeps a light bar light enough for dark icons). The content keeps to the safe area itself
 * (WindowInsets.safeDrawing).
 */
object XgramDialogWindow {
  /** After setContentView. [dark]: the dialog's scheme, for the icons when the activity can't be found. */
  @JvmStatic
  @Suppress("DEPRECATION")
  fun edgeToEdge(window: Window, context: Context, dark: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.statusBarColor = Color.TRANSPARENT
      window.navigationBarColor = Color.TRANSPARENT
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isStatusBarContrastEnforced = false
      window.isNavigationBarContrastEnforced = false
    }
    // The activity's own flags: Telegram X sets the legacy ones in every mode (UI.setLightSystemBars), the
    // insets controller's only with edge to edge
    val appVisibility = UI.getContext(context)?.window?.decorView?.systemUiVisibility
    val lightStatusBar = if (appVisibility != null) (appVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0 else !dark
    val lightNavigationBar = if (appVisibility != null) (appVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0 else !dark
    WindowCompat.getInsetsController(window, window.decorView).apply {
      isAppearanceLightStatusBars = lightStatusBar
      isAppearanceLightNavigationBars = lightNavigationBar
    }
  }
}
