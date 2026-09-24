package com.metrolist.music.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoThumbnailTest {
    @Test
    fun `recognizes video thumbnails and not album art`() {
        assertTrue("https://i.ytimg.com/vi/abc123/hqdefault.jpg".isYouTubeVideoThumbnail())
        assertTrue("https://i1.ytimg.com/vi_webp/abc123/sddefault.webp".isYouTubeVideoThumbnail())
        assertFalse("https://lh3.googleusercontent.com/xyz=w544-h544-l90-rj".isYouTubeVideoThumbnail())
    }

    @Test
    fun `widescreen frame replaces letterboxed thumbnails only`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc123/hq720.jpg",
            "https://i.ytimg.com/vi/abc123/hqdefault.jpg?sqp=x&rs=y".widescreenVideoThumbnail(),
        )
        assertNull("https://i.ytimg.com/vi/abc123/hq720.jpg".widescreenVideoThumbnail())
        assertNull("https://i.ytimg.com/vi/abc123/maxresdefault.jpg".widescreenVideoThumbnail())
        assertNull("https://lh3.googleusercontent.com/xyz=w544-h544".widescreenVideoThumbnail())
    }

    @Test
    fun `letterboxed 4 by 3 thumbnail is scaled until the bars leave a square`() {
        // 480x360 with a 480x270 picture: the square must be filled by the 270px tall picture.
        assertEquals(1000f / 270f, videoThumbnailScale(480f, 360f, 1000f, 1000f), 0.001f)
    }

    @Test
    fun `16 by 9 thumbnail is cropped like ContentScale Crop`() {
        assertEquals(1000f / 720f, videoThumbnailScale(1280f, 720f, 1000f, 1000f), 0.001f)
    }

    @Test
    fun `square image keeps a plain crop`() {
        assertEquals(2f, videoThumbnailScale(500f, 500f, 1000f, 1000f), 0.001f)
    }
}
