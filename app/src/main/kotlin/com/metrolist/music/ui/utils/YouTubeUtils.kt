/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("LocalVariableName")

package com.metrolist.music.ui.utils

import kotlin.math.roundToInt

private val GOOGLEUSERCONTENT_SIZE_PATTERN =
    Regex("^(https://(?:lh3|yt3)\\.googleusercontent\\.com/[^?]*?)=w(\\d+)-h(\\d+)[^?]*(\\?.*)?$")
private val GGPHT_SIZE_PATTERN =
    Regex("^(https://yt3\\.ggpht\\.com/[^?=]+)=(?:s\\d+|w\\d+-h\\d+)[^?]*(\\?.*)?$")
fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    GOOGLEUSERCONTENT_SIZE_PATTERN
        .matchEntire(this)
        ?.groupValues
        ?.let { group ->
            val originalWidth = group[2].toInt()
            val originalHeight = group[3].toInt()
            val query = group[4]
            val targetWidth = width ?: ((height!!.toDouble() * originalWidth) / originalHeight).roundToInt()
            val targetHeight = height ?: ((width!!.toDouble() * originalHeight) / originalWidth).roundToInt()
            return "${group[1]}=w${targetWidth.coerceAtLeast(1)}-h${targetHeight.coerceAtLeast(1)}-p-l90-rj$query"
        }

    GGPHT_SIZE_PATTERN.matchEntire(this)?.groupValues?.let { group ->
        val query = group[2]
        return if (width != null && height != null) {
            "${group[1]}=w$width-h$height-p-l90-rj$query"
        } else {
            "${group[1]}=s${width ?: height}$query"
        }
    }

    return this
}

private val YOUTUBE_VIDEO_THUMBNAIL_PATTERN =
    Regex("^https?://i\\d?\\.ytimg\\.com/vi(?:_webp)?/([^/]+)/([^/?]+)")

/** Thumbnails of YouTube videos (i.ytimg.com), which are 16:9 or letterboxed 4:3 instead of square. */
fun String.isYouTubeVideoThumbnail(): Boolean = YOUTUBE_VIDEO_THUMBNAIL_PATTERN.containsMatchIn(this)

/**
 * The 1280×720 frame of a video thumbnail. hqdefault/sddefault are 4:3 with the black bars
 * drawn into the image, and too small to fill the player. Null when this is not a video
 * thumbnail or already the widescreen one.
 */
fun String.widescreenVideoThumbnail(): String? {
    val match = YOUTUBE_VIDEO_THUMBNAIL_PATTERN.find(this) ?: return null
    val (videoId, file) = match.destructured
    if (file.startsWith("hq720") || file.startsWith("maxresdefault")) return null
    return "https://i.ytimg.com/vi/$videoId/hq720.jpg"
}
