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
package com.fahadali.xgram.xdrive

import org.thunderdog.challegram.R

/**
 * Folder icons: Material icons by a short key, which the folders file keeps (other devices map it back). A folder the
 * AI names gets one by its words ("Recipes" → restaurant).
 */
object XDriveIcons {
  /** Key → drawable, in the icon picker's order. */
  val ALL: List<Pair<String, Int>> = listOf(
    "folder" to R.drawable.baseline_folder_24,
    "key" to R.drawable.baseline_vpn_key_24,
    "receipt" to R.drawable.baseline_receipt_long_24,
    "payments" to R.drawable.baseline_payments_24,
    "shopping" to R.drawable.baseline_shopping_bag_24,
    "flight" to R.drawable.baseline_flight_24,
    "work" to R.drawable.baseline_work_24,
    "school" to R.drawable.baseline_school_24,
    "code" to R.drawable.baseline_code_24,
    "todo" to R.drawable.baseline_playlist_add_check_24,
    "idea" to R.drawable.baseline_lightbulb_24,
    "article" to R.drawable.baseline_article_24,
    "book" to R.drawable.baseline_book_24,
    "document" to R.drawable.baseline_description_24,
    "movie" to R.drawable.baseline_movie_24,
    "music" to R.drawable.baseline_music_note_24,
    "photo" to R.drawable.baseline_image_24,
    "food" to R.drawable.baseline_restaurant_24,
    "fitness" to R.drawable.baseline_fitness_center_24,
    "place" to R.drawable.baseline_location_on_24,
    "contact" to R.drawable.baseline_contact_phone_24,
    "people" to R.drawable.baseline_group_24,
    "chat" to R.drawable.baseline_forum_24,
    "link" to R.drawable.baseline_link_24,
    "web" to R.drawable.baseline_public_24,
    "apps" to R.drawable.baseline_apps_24,
    "archive" to R.drawable.baseline_archive_24,
    "home" to R.drawable.baseline_home_24,
    "car" to R.drawable.baseline_directions_car_24,
    "game" to R.drawable.baseline_sports_esports_24,
    "star" to R.drawable.baseline_star_24,
    "heart" to R.drawable.baseline_favorite_24,
    "security" to R.drawable.baseline_security_24
  )

  private val BY_KEY = ALL.toMap()

  fun drawableOf(key: String?): Int = (if (key != null) BY_KEY[key] else null) ?: R.drawable.baseline_folder_24

  private val WORDS: List<Pair<Regex, String>> = listOf(
    Regex("(?i)pass|login|account|credential|key|secret|كلمة") to "key",
    Regex("(?i)receipt|invoice|bill|فاتور") to "receipt",
    Regex("(?i)money|financ|bank|budget|payment|salary|expense|tax|crypto|invest") to "payments",
    Regex("(?i)shop|buy|order|wishlist|deal|product") to "shopping",
    Regex("(?i)travel|trip|flight|hotel|vacation|holiday|visa|سفر|رحلة") to "flight",
    Regex("(?i)work|job|office|meeting|project|business|client|career") to "work",
    Regex("(?i)stud|school|class|lecture|exam|course|universit|college|learn|lesson") to "school",
    Regex("(?i)code|dev|program|tech|software|github|script") to "code",
    Regex("(?i)to-?do|task|list|reminder|plan") to "todo",
    Regex("(?i)idea|inspir|thought|brainstorm") to "idea",
    Regex("(?i)news|article|read|blog") to "article",
    Regex("(?i)book|novel|ebook") to "book",
    Regex("(?i)doc|file|pdf|paper|form") to "document",
    Regex("(?i)movie|film|video|watch|show|series|youtube") to "movie",
    Regex("(?i)music|song|playlist|audio|podcast") to "music",
    Regex("(?i)photo|picture|image|wallpaper|meme|screenshot") to "photo",
    Regex("(?i)recipe|food|cook|meal|restaurant|kitchen") to "food",
    Regex("(?i)fitness|gym|workout|health|diet|sport|medic") to "fitness",
    Regex("(?i)place|location|address|map") to "place",
    Regex("(?i)contact|phone|number") to "contact",
    Regex("(?i)family|friend|people|personal") to "people",
    Regex("(?i)chat|post|social|forward|channel") to "chat",
    Regex("(?i)link|url|site|web") to "link",
    Regex("(?i)app|apk") to "apps",
    Regex("(?i)home|house") to "home",
    Regex("(?i)car|vehicle") to "car",
    Regex("(?i)game") to "game",
    Regex("(?i)quote|favou?rite|love") to "heart"
  )

  /** An icon for a folder's name, or null: none fits. */
  fun keyForName(name: String): String? = WORDS.firstOrNull { it.first.containsMatchIn(name) }?.second
}
