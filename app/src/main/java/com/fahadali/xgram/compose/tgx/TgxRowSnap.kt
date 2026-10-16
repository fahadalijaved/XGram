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
import android.widget.ImageView
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
import com.fahadali.xgram.appearance.XgramComposeSettingsIcons
import org.thunderdog.challegram.component.base.SettingView
import org.thunderdog.challegram.theme.ColorId
import org.thunderdog.challegram.theme.Theme
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

/** The color role Telegram X gives a row's value text (SettingView.setDataColorId), as an M3 role. */
internal object TgxTone {
  const val NONE = 0
  /** textNegative: "Notifications blocked", importance None. */
  const val ERROR = 1
  /** textNeutral, textSecure, textLink: an exception, the pack in use, a working connection. */
  const val PRIMARY = 2

  fun of(colorId: Int): Int = when (colorId) {
    ColorId.textNegative -> ERROR
    ColorId.textNeutral, ColorId.textSecure, ColorId.textLink -> PRIMARY
    else -> NONE
  }
}

/** What sits at the end of a HEADER_ACTION row. */
internal object TgxHeaderAction {
  const val NONE = 0
  const val ICON = 1
  const val TEXT = 2
  const val CHECKBOX = 3
}

/** What a row shows, read back from its bound view. Equal snaps write no state, so nothing recomposes. */
internal data class TgxRowSnap(
  val kind: TgxRowKind,
  val title: CharSequence? = null,
  /** The second line (the value), or a header's text button label. */
  val subtitle: CharSequence? = null,
  val icon: Int = 0,
  val checked: Boolean = false,
  /** Checked only in part: a switch's or a checkbox's third state (some of a group on). */
  val partial: Boolean = false,
  /** View.isEnabled: false takes no taps. */
  val enabled: Boolean = true,
  /**
   * Telegram X's look: true when the row is drawn disabled (SettingView.isVisuallyEnabled false). Independent of
   * [enabled]: a row may look enabled and take no taps (counters), or look disabled and still explain itself on tap.
   */
  val dimmed: Boolean = false,
  val busy: Boolean = false,
  /** The control itself is disabled (a toggler or a checkbox), whatever the row does. */
  val toggleDisabled: Boolean = false,
  val locked: Boolean = false,
  val destructive: Boolean = false,
  /** [TgxTone] of the value text. */
  val tone: Int = TgxTone.NONE,
  /** The radio's own color (a theme's color), ARGB; 0: the M3 colors. */
  val radioColor: Int = 0,
  /** The radio is drawn in its own color when selected too (RadioView.setApplyColor), not only when unselected. */
  val radioApplied: Boolean = false,
  /** [TgxHeaderAction] of a HEADER_ACTION row. */
  val action: Int = TgxHeaderAction.NONE,
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

  /** Headers become M3 headers on the page; those with an icon, a text button or a checkbox keep it at their end. */
  fun isHeaderType(type: Int): Boolean =
    type == ListItem.TYPE_HEADER || type == ListItem.TYPE_HEADER_PADDED || type == ListItem.TYPE_HEADER_MULTILINE ||
      isActionHeaderType(type)

  private fun isActionHeaderType(type: Int): Boolean = type == ListItem.TYPE_HEADER_WITH_ACTION ||
    type == ListItem.TYPE_HEADER_WITH_TEXT_BUTTON || type == ListItem.TYPE_HEADER_WITH_CHECKBOX

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

  /** The title of a HEADER_WITH_* row (SettingHolder: text1 next to a text button, else the first child). */
  private fun headerTitleOf(view: ViewGroup, type: Int): TextView? =
    if (type == ListItem.TYPE_HEADER_WITH_TEXT_BUTTON) {
      view.findViewById<TextView>(android.R.id.text1)
    } else {
      view.getChildAt(0) as? TextView
    }

  /** The control at the end of a HEADER_WITH_* row: the child with a click of its own (icon, text button, checkbox). */
  private fun headerTargetOf(view: ViewGroup, title: View?): View? {
    for (i in 0 until view.childCount) {
      val child = view.getChildAt(i)
      if (child !== title && child.hasOnClickListeners()) {
        return child
      }
    }
    return null
  }

  /** What a HEADER_WITH_* row has at its end ([TgxHeaderAction]); NONE when its views are not the usual ones. */
  private fun headerActionOf(context: Context, view: View, type: Int, item: ListItem): Int {
    if (view !is ViewGroup) {
      return TgxHeaderAction.NONE
    }
    val title = headerTitleOf(view, type) ?: return TgxHeaderAction.NONE
    val target = headerTargetOf(view, title) ?: return TgxHeaderAction.NONE
    return when (type) {
      ListItem.TYPE_HEADER_WITH_ACTION -> {
        val icon = item.iconResource
        if (target is ImageView && icon != 0 && TgxIcons.supported(context, icon)) TgxHeaderAction.ICON else TgxHeaderAction.NONE
      }
      ListItem.TYPE_HEADER_WITH_TEXT_BUTTON -> if (target is TextView) TgxHeaderAction.TEXT else TgxHeaderAction.NONE
      ListItem.TYPE_HEADER_WITH_CHECKBOX -> if (target is CheckBoxView) TgxHeaderAction.CHECKBOX else TgxHeaderAction.NONE
      else -> TgxHeaderAction.NONE
    }
  }

  /** Telegram X writes button labels in capitals; M3 buttons use the label as written, when it is the same text. */
  private fun buttonLabel(shown: CharSequence?, written: CharSequence?): CharSequence? =
    if (shown != null && written != null && shown.toString().equals(written.toString(), ignoreCase = true)) written else shown

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
      ListItem.TYPE_HEADER_WITH_ACTION, ListItem.TYPE_HEADER_WITH_TEXT_BUTTON, ListItem.TYPE_HEADER_WITH_CHECKBOX ->
        return if (headerActionOf(context, view, type, item) != TgxHeaderAction.NONE) TgxRowKind.HEADER_ACTION else TgxRowKind.FALLBACK
      ListItem.TYPE_DESCRIPTION, ListItem.TYPE_DESCRIPTION_SMALL, ListItem.TYPE_DESCRIPTION_CENTERED ->
        return if (view is TextView) TgxRowKind.DESCRIPTION else TgxRowKind.FALLBACK
      ListItem.TYPE_BUILD_NO -> return if (view is TextView) TgxRowKind.BUILD_NO else TgxRowKind.FALLBACK
      ListItem.TYPE_EMPTY -> return if (view is TextView) TgxRowKind.EMPTY else TgxRowKind.FALLBACK
      ListItem.TYPE_PROGRESS, ListItem.TYPE_SMART_PROGRESS -> return TgxRowKind.LOADING
      ListItem.TYPE_SLIDER -> {
        // The values the slider shows are on its view: setSliderValues puts the item's there, and pages such as
        // EditChatLinkController set their own without the item.
        val values = (view as? SliderWrapView)?.values
        return if (values != null && values.size >= 2) TgxRowKind.SLIDER else TgxRowKind.FALLBACK
      }
      ListItem.TYPE_BUTTON -> return if (findText(view) != null && clickTarget(view) != null) TgxRowKind.BUTTON else TgxRowKind.FALLBACK
      // Telegram X's red and green toggler (EditRights maps it in its hints), avatars and color previews: the real
      // row until a hint draws them.
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
    val toggler = view.toggler
    if (toggler != null) {
      // A toggler with a click of its own toggles, while the row does something else (it expands a group).
      return if (toggler.isClickable && toggler.hasOnClickListeners()) TgxRowKind.SPLIT_SWITCH else TgxRowKind.SWITCH
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

  /**
   * Reads what the row shows now. Getters only: cheap enough to run on every change the view reports. [partial]: the
   * page's hint that a switch row is only partly on.
   */
  fun read(kind: TgxRowKind, view: View, item: ListItem, type: Int, partial: Boolean): TgxRowSnap = when (kind) {
    TgxRowKind.SWITCH, TgxRowKind.SPLIT_SWITCH, TgxRowKind.RADIO, TgxRowKind.CHECKBOX, TgxRowKind.VALUE ->
      if (view is SettingView) readSetting(kind, view, partial) else TgxRowSnap.HIDDEN
    TgxRowKind.HEADER -> TgxRowSnap(kind, title = (view as? TextView)?.text)
    TgxRowKind.HEADER_ACTION -> readHeader(view, item, type)
    TgxRowKind.DESCRIPTION -> TgxRowSnap(kind, title = (view as? TextView)?.text, centered = type == ListItem.TYPE_DESCRIPTION_CENTERED)
    TgxRowKind.BUILD_NO, TgxRowKind.EMPTY -> TgxRowSnap(
      kind, title = (view as? TextView)?.text, enabled = view.isEnabled, longClickable = view.isLongClickable
    )
    TgxRowKind.BUTTON -> TgxRowSnap(
      kind, title = item.string ?: findText(view)?.text, enabled = view.isEnabled, longClickable = view.isLongClickable
    )
    TgxRowKind.SLIDER -> {
      val slider = view as? SliderWrapView
      val values = slider?.values
      TgxRowSnap(
        kind, title = item.string, enabled = view.isEnabled,
        sliderLabels = values?.asList() ?: emptyList(),
        sliderIndex = if (slider != null && values != null) slider.valueIndex else item.sliderValue
      )
    }
    TgxRowKind.LOADING, TgxRowKind.FALLBACK, TgxRowKind.HIDDEN -> TgxRowSnap(kind)
  }

  private fun readHeader(view: View, item: ListItem, type: Int): TgxRowSnap {
    if (view !is ViewGroup) {
      return TgxRowSnap(TgxRowKind.HEADER_ACTION)
    }
    val title = headerTitleOf(view, type)
    val target = headerTargetOf(view, title)
    val enabled = view.isEnabled && target != null && target.isEnabled
    return when (type) {
      ListItem.TYPE_HEADER_WITH_ACTION -> TgxRowSnap(
        TgxRowKind.HEADER_ACTION, title = title?.text, icon = item.iconResource, action = TgxHeaderAction.ICON, enabled = enabled
      )
      ListItem.TYPE_HEADER_WITH_TEXT_BUTTON -> TgxRowSnap(
        TgxRowKind.HEADER_ACTION, title = title?.text, subtitle = buttonLabel((target as? TextView)?.text, item.string),
        action = TgxHeaderAction.TEXT, enabled = enabled
      )
      else -> {
        val box = target as? CheckBoxView
        val checked = box?.isChecked == true
        TgxRowSnap(
          TgxRowKind.HEADER_ACTION, title = title?.text, action = TgxHeaderAction.CHECKBOX, enabled = enabled,
          checked = checked, partial = checked && box?.isPartiallyChecked == true, toggleDisabled = box?.isDisabled == true
        )
      }
    }
  }

  private fun readSetting(kind: TgxRowKind, view: SettingView, partialHint: Boolean): TgxRowSnap {
    val name = view.name
    val data = view.data
    val title: CharSequence?
    val subtitle: CharSequence?
    // Info rows draw the data large and the name under it, unless swapped (valued settings: name, then value).
    val dataIsTitle = isInfoType(view.type) && !view.isDataSwapped
    if (isInfoType(view.type)) {
      val wrapper = view.textWrapper
      title = if (view.type == SettingView.TYPE_INFO_MULTILINE && wrapper != null) wrapper.text else if (dataIsTitle) data else name
      subtitle = if (dataIsTitle) name else data
    } else {
      title = name
      subtitle = data
    }
    val toggler = view.toggler
    val first = if (view.childCount > 0) view.getChildAt(0) else null
    val radio = if (kind == TgxRowKind.RADIO) first as? RadioView else null
    val box = if (kind == TgxRowKind.CHECKBOX) first as? CheckBoxView else null
    val checked = when (kind) {
      TgxRowKind.SWITCH, TgxRowKind.SPLIT_SWITCH -> toggler?.isEnabled == true
      TgxRowKind.RADIO -> radio?.isChecked == true
      TgxRowKind.CHECKBOX -> box?.isChecked == true
      else -> false
    }
    val partial = when (kind) {
      TgxRowKind.SWITCH, TgxRowKind.SPLIT_SWITCH -> checked && partialHint
      TgxRowKind.CHECKBOX -> checked && box?.isPartiallyChecked == true
      else -> false
    }
    val radioColorId = radio?.colorId ?: 0
    return TgxRowSnap(
      kind = kind,
      title = title,
      subtitle = if (subtitle.isNullOrEmpty()) null else subtitle,
      icon = view.iconResource,
      checked = checked,
      partial = partial,
      enabled = view.isEnabled,
      dimmed = !view.isVisuallyEnabled,
      busy = view.isInProgress,
      toggleDisabled = toggler?.isDisabled == true || box?.isDisabled == true,
      locked = toggler?.isLockShown == true,
      destructive = view.textColorId == ColorId.textNegative,
      // The value's color, when the second line is the value.
      tone = if (dataIsTitle) TgxTone.NONE else TgxTone.of(view.dataColorId),
      radioColor = if (radioColorId != 0) Theme.getColor(radioColorId) else 0,
      radioApplied = radioColorId != 0 && radio?.isApplyColor == true,
      iconRotated = view.isIconRotated,
      longClickable = view.isLongClickable
    )
  }
}

/** Whether Compose can draw a Telegram X icon (painterResource: vector XML or a bitmap), cached per resource. */
internal object TgxIcons {
  private val cache = SparseIntArray()

  fun supported(context: Context, icon: Int): Boolean {
    // The drawable tgxIcon draws (the pack's, or the Line family's with the Classic pack).
    val res = XgramComposeSettingsIcons.map(icon)
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
          is StyleSpan -> addTgxStyle(span, start, end)
        }
      }
    }
  }

  /**
   * A row's title or value with Telegram X's bold and italic ("Share my number", "Allowed 3/9"), no links: the whole
   * row is the tap target.
   */
  fun styled(text: Spanned): AnnotatedString = buildAnnotatedString {
    append(text.toString())
    for (span in text.getSpans(0, text.length, Any::class.java)) {
      val start = text.getSpanStart(span)
      val end = text.getSpanEnd(span)
      if (start < 0 || end <= start || end > text.length) {
        continue
      }
      when (span) {
        is CustomTypefaceSpan -> if (span.typeface != null) {
          addStyle(SpanStyle(fontWeight = FontWeight.Medium), start, end)
        }
        is StyleSpan -> addTgxStyle(span, start, end)
      }
    }
  }

  private fun AnnotatedString.Builder.addTgxStyle(span: StyleSpan, start: Int, end: Int) {
    when (span.style) {
      Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
      Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
      Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
    }
  }
}
