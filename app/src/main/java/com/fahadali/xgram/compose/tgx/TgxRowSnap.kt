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
package com.fahadali.xgram.compose.tgx

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.VectorDrawable
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.fahadali.xgram.appearance.XgramIconPack
import org.thunderdog.challegram.component.base.SettingView
import org.thunderdog.challegram.theme.ColorId
import org.thunderdog.challegram.ui.ListItem
import org.thunderdog.challegram.ui.SettingHolder
import org.thunderdog.challegram.util.CustomTypefaceSpan
import org.thunderdog.challegram.widget.CheckBoxView
import org.thunderdog.challegram.widget.RadioView
import org.thunderdog.challegram.widget.SliderWrapView

/**
 * One item of the adapter, kept by identity while it stays in the list. Its view is created and bound the first time
 * the row is composed, like RecyclerView does, and kept until the item leaves the list.
 */
internal class TgxRow(val item: ListItem, serial: Long) {
  /** The row's key in the Compose list: stable across inserts, and rows with the same id stay apart. */
  val key: String = "t$serial"
  var index = -1
  var viewType: Int = item.viewType
  var holder: SettingHolder? = null
  var holderType = Int.MIN_VALUE
  var kind = TgxRowKind.HIDDEN
  var dirty = false
  /** How many compositions show the row now (0: off screen). */
  var composed = 0
  /** What the row shows; created with its first bind, so the first composition writes no state. */
  var state: MutableState<TgxRowSnap>? = null
  /** The fallback frame the real view sits in while the row is on screen. */
  var frame: TgxFallbackFrame? = null
  /** Where the row is drawn: tooltips and menus anchor to the view at that place. */
  var coordinates: LayoutCoordinates? = null
}

/** What a row shows, read back from its bound view. Equal snaps write no state, so nothing recomposes. */
internal data class TgxRowSnap(
  val kind: TgxRowKind,
  val title: CharSequence? = null,
  val subtitle: CharSequence? = null,
  val icon: Int = 0,
  val checked: Boolean = false,
  /** View.isEnabled: false takes no clicks. */
  val enabled: Boolean = true,
  /** Telegram X's "visually disabled": dimmed but still tappable (it explains why in a tooltip). */
  val dimmed: Boolean = false,
  val busy: Boolean = false,
  val toggleDisabled: Boolean = false,
  val locked: Boolean = false,
  val destructive: Boolean = false,
  val iconRotated: Boolean = false,
  val longClickable: Boolean = false,
  val centered: Boolean = false,
  val sliderLabels: List<String> = emptyList(),
  val sliderIndex: Int = 0
) {
  companion object {
    val HIDDEN = TgxRowSnap(TgxRowKind.HIDDEN)
  }
}

/** The generic mapping of SettingsAdapter rows to Compose rows. */
internal object TgxRead {
  /** Shadows, separators, offsets and paddings: groups and gaps take their place. */
  fun isHiddenType(type: Int): Boolean = when (type) {
    ListItem.TYPE_SHADOW_TOP, ListItem.TYPE_SHADOW_BOTTOM, ListItem.TYPE_SEPARATOR, ListItem.TYPE_SEPARATOR_FULL,
    ListItem.TYPE_EMPTY_OFFSET, ListItem.TYPE_EMPTY_OFFSET_SMALL, ListItem.TYPE_EMPTY_OFFSET_NO_HEAD,
    ListItem.TYPE_PADDING, ListItem.TYPE_ZERO_VIEW, ListItem.TYPE_SHADOWED_OFFSET, ListItem.TYPE_DRAWER_OFFSET,
    ListItem.TYPE_DRAWER_EMPTY -> true
    else -> false
  }

  fun isSeparatorType(type: Int): Boolean = type == ListItem.TYPE_SEPARATOR || type == ListItem.TYPE_SEPARATOR_FULL

  /** Plain headers become M3 headers; headers with a button or a checkbox stay real views for now. */
  fun isHeaderType(type: Int): Boolean =
    type == ListItem.TYPE_HEADER || type == ListItem.TYPE_HEADER_PADDED || type == ListItem.TYPE_HEADER_MULTILINE

  fun isDescriptionType(type: Int): Boolean =
    type == ListItem.TYPE_DESCRIPTION || type == ListItem.TYPE_DESCRIPTION_SMALL || type == ListItem.TYPE_DESCRIPTION_CENTERED

  /** Rows that sit on the page, never in a card: empty states, progress, the build number. */
  fun isStandaloneType(type: Int): Boolean = when (type) {
    ListItem.TYPE_EMPTY, ListItem.TYPE_ICONIZED_EMPTY, ListItem.TYPE_SESSIONS_EMPTY, ListItem.TYPE_WEBSITES_EMPTY,
    ListItem.TYPE_SMART_EMPTY, ListItem.TYPE_PROGRESS, ListItem.TYPE_SMART_PROGRESS, ListItem.TYPE_BUILD_NO -> true
    else -> false
  }

  fun findText(view: View): TextView? {
    if (view is TextView) {
      return view
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        val found = findText(view.getChildAt(i))
        if (found != null) {
          return found
        }
      }
    }
    return null
  }

  /** The view a tap goes to: the row itself, or the first child with a click listener (a button in the row). */
  fun clickTarget(view: View): View? {
    if (view.hasOnClickListeners()) {
      return view
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        val found = clickTarget(view.getChildAt(i))
        if (found != null) {
          return found
        }
      }
    }
    return null
  }

  private fun isInfoType(type: Int): Boolean = type == SettingView.TYPE_INFO || type == SettingView.TYPE_INFO_COMPACT ||
    type == SettingView.TYPE_INFO_SUPERCOMPACT || type == SettingView.TYPE_INFO_MULTILINE

  /** How [item], bound to [view], is drawn: the page's hint first, then the generic mapping. */
  fun kindOf(context: Context, item: ListItem, type: Int, view: View, hints: TgxPageHints): TgxRowKind {
    hints.kind(item, view)?.let { return it }
    if (isHiddenType(type)) {
      return TgxRowKind.HIDDEN
    }
    when (type) {
      ListItem.TYPE_HEADER, ListItem.TYPE_HEADER_PADDED, ListItem.TYPE_HEADER_MULTILINE ->
        return if (view is TextView) TgxRowKind.HEADER else TgxRowKind.FALLBACK
      ListItem.TYPE_DESCRIPTION, ListItem.TYPE_DESCRIPTION_SMALL, ListItem.TYPE_DESCRIPTION_CENTERED ->
        return if (view is TextView) TgxRowKind.DESCRIPTION else TgxRowKind.FALLBACK
      ListItem.TYPE_BUILD_NO -> return if (view is TextView) TgxRowKind.BUILD_NO else TgxRowKind.FALLBACK
      ListItem.TYPE_EMPTY -> return if (view is TextView) TgxRowKind.EMPTY else TgxRowKind.FALLBACK
      ListItem.TYPE_PROGRESS, ListItem.TYPE_SMART_PROGRESS -> return TgxRowKind.LOADING
      ListItem.TYPE_SLIDER -> {
        val values = item.sliderValues
        return if (view is SliderWrapView && values != null && values.size >= 2) TgxRowKind.SLIDER else TgxRowKind.FALLBACK
      }
      ListItem.TYPE_BUTTON -> return if (findText(view) != null && clickTarget(view) != null) TgxRowKind.BUTTON else TgxRowKind.FALLBACK
      // A three-state switch, avatars and color previews: the real row until a hint draws them.
      ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE, ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR,
      ListItem.TYPE_RADIO_OPTION_WITH_AVATAR, ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR -> return TgxRowKind.FALLBACK
    }
    if (view !is SettingView) {
      return TgxRowKind.FALLBACK
    }
    // Previews drawn into the row (emoji, reactions, icons), counters, bitmap icons and color dots keep the real row.
    if (!view.drawModifiers.isNullOrEmpty() || view.hasUnreadCounter() || (view.colorDataId != ColorId.NONE)) {
      return TgxRowKind.FALLBACK
    }
    val icon = view.iconResource
    if ((icon == 0 && view.hasIcon()) || (icon != 0 && !TgxIcons.supported(context, icon))) {
      return TgxRowKind.FALLBACK
    }
    if (view.type == SettingView.TYPE_INFO_MULTILINE) {
      val entities = view.textWrapper?.entities
      if (entities != null && entities.isNotEmpty()) {
        return TgxRowKind.FALLBACK
      }
    }
    if (view.toggler != null) {
      return TgxRowKind.SWITCH
    }
    if (view.childCount > 0) {
      return when (view.getChildAt(0)) {
        is RadioView -> TgxRowKind.RADIO
        is CheckBoxView -> TgxRowKind.CHECKBOX
        else -> TgxRowKind.FALLBACK
      }
    }
    return TgxRowKind.VALUE
  }

  /** Reads what the row shows now. Getters only: cheap enough to run on every change the view reports. */
  fun read(kind: TgxRowKind, view: View, item: ListItem, type: Int): TgxRowSnap = when (kind) {
    TgxRowKind.SWITCH, TgxRowKind.RADIO, TgxRowKind.CHECKBOX, TgxRowKind.VALUE ->
      if (view is SettingView) readSetting(kind, view) else TgxRowSnap.HIDDEN
    TgxRowKind.HEADER -> TgxRowSnap(kind, title = (view as? TextView)?.text)
    TgxRowKind.DESCRIPTION -> TgxRowSnap(kind, title = (view as? TextView)?.text, centered = type == ListItem.TYPE_DESCRIPTION_CENTERED)
    TgxRowKind.BUILD_NO, TgxRowKind.EMPTY -> TgxRowSnap(
      kind, title = (view as? TextView)?.text, enabled = view.isEnabled, longClickable = view.isLongClickable
    )
    TgxRowKind.BUTTON -> TgxRowSnap(
      kind, title = item.string ?: findText(view)?.text, enabled = view.isEnabled, longClickable = view.isLongClickable
    )
    TgxRowKind.SLIDER -> TgxRowSnap(
      kind, title = item.string, enabled = view.isEnabled,
      sliderLabels = item.sliderValues?.toList() ?: emptyList(), sliderIndex = item.sliderValue
    )
    TgxRowKind.LOADING, TgxRowKind.FALLBACK, TgxRowKind.HIDDEN -> TgxRowSnap(kind)
  }

  private fun readSetting(kind: TgxRowKind, view: SettingView): TgxRowSnap {
    val name = view.name
    val data = view.data
    val title: CharSequence?
    val subtitle: CharSequence?
    if (isInfoType(view.type)) {
      // Info rows draw the data large and the name under it, unless swapped (valued settings: name, then value).
      val swapped = view.isDataSwapped
      val wrapper = view.textWrapper
      title = if (view.type == SettingView.TYPE_INFO_MULTILINE && wrapper != null) wrapper.text else if (swapped) name else data
      subtitle = if (swapped) data else name
    } else {
      title = name
      subtitle = data
    }
    val toggler = view.toggler
    val checked = when (kind) {
      TgxRowKind.SWITCH -> toggler?.isEnabled == true
      TgxRowKind.RADIO -> (view.getChildAt(0) as? RadioView)?.isChecked == true
      TgxRowKind.CHECKBOX -> (view.getChildAt(0) as? CheckBoxView)?.isChecked == true
      else -> false
    }
    return TgxRowSnap(
      kind = kind,
      title = title,
      subtitle = if (subtitle.isNullOrEmpty()) null else subtitle,
      icon = view.iconResource,
      checked = checked,
      enabled = view.isEnabled,
      dimmed = !view.isVisuallyEnabled,
      busy = view.isInProgress,
      toggleDisabled = toggler?.isDisabled == true,
      locked = toggler?.isLockShown == true,
      destructive = view.textColorId == ColorId.textNegative,
      iconRotated = view.isIconRotated,
      longClickable = view.isLongClickable
    )
  }
}

/** Whether Compose can draw a Telegram X icon (painterResource: vector XML or a bitmap), cached per resource. */
internal object TgxIcons {
  private val cache = SparseIntArray()

  fun supported(context: Context, icon: Int): Boolean {
    val res = XgramIconPack.map(icon)
    val cached = cache.get(res)
    if (cached != 0) {
      return cached == 1
    }
    val ok = try {
      val value = TypedValue()
      context.resources.getValue(res, value, true)
      val path = value.string?.toString() ?: ""
      !path.endsWith(".xml") || context.getDrawable(res) is VectorDrawable
    } catch (t: Throwable) {
      false
    }
    cache.put(res, if (ok) 1 else 2)
    return ok
  }
}

/** Telegram X's spanned texts (descriptions) as AnnotatedString: bold, italic and links that tap the real span. */
internal object TgxSpans {
  fun annotated(text: CharSequence?, view: View?, linkColor: Color): AnnotatedString {
    if (text == null) {
      return AnnotatedString("")
    }
    if (text !is Spanned) {
      return AnnotatedString(text.toString())
    }
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor))
    return buildAnnotatedString {
      append(text.toString())
      for (span in text.getSpans(0, text.length, Any::class.java)) {
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        if (start < 0 || end <= start || end > text.length) {
          continue
        }
        when (span) {
          is ClickableSpan -> addLink(
            LinkAnnotation.Clickable("tgx", linkStyles, object : LinkInteractionListener {
              override fun onClick(link: LinkAnnotation) {
                if (view != null) {
                  span.onClick(view)
                }
              }
            }),
            start, end
          )
          is CustomTypefaceSpan -> {
            if (span.isClickable) {
              val clicked = text.subSequence(start, end).toString()
              addLink(
                LinkAnnotation.Clickable("tgx", linkStyles, object : LinkInteractionListener {
                  override fun onClick(link: LinkAnnotation) {
                    if (view != null) {
                      span.onClick(view, clicked)
                    }
                  }
                }),
                start, end
              )
            } else if (span.typeface != null) {
              addStyle(SpanStyle(fontWeight = FontWeight.Medium), start, end)
            }
          }
          is StyleSpan -> when (span.style) {
            Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
          }
        }
      }
    }
  }
}
