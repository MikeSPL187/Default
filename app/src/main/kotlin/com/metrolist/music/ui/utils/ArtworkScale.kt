package com.metrolist.music.ui.utils

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import kotlin.math.max

/**
 * Fills a square with a video thumbnail: crops like [ContentScale.Crop], and for 4:3 thumbnails
 * also cuts off the black bars YouTube draws above and below the 16:9 picture.
 */
object VideoThumbnailCrop : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        val scale = videoThumbnailScale(srcSize.width, srcSize.height, dstSize.width, dstSize.height)
        return ScaleFactor(scale, scale)
    }
}

internal fun videoThumbnailScale(srcWidth: Float, srcHeight: Float, dstWidth: Float, dstHeight: Float): Float {
    if (srcWidth <= 0f || srcHeight <= 0f) return 1f
    val aspect = srcWidth / srcHeight
    // A 4:3 thumbnail holds a 16:9 picture between bars.
    val pictureHeight = if (aspect in 1.30f..1.37f) srcWidth * 9f / 16f else srcHeight
    return max(dstWidth / srcWidth, dstHeight / pictureHeight)
}

/** Video thumbnails always fill the frame; album art follows the "crop album art" setting. */
fun artworkContentScale(url: String?, cropAlbumArt: Boolean): ContentScale =
    when {
        url?.isYouTubeVideoThumbnail() == true -> VideoThumbnailCrop
        cropAlbumArt -> ContentScale.Crop
        else -> ContentScale.Fit
    }
