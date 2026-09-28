package com.metrolist.innertube.models

/**
 * The music video of a song. [segments] say which stretch of the song matches which stretch of the
 * video, as a video often opens with a scene the song does not have; empty when they line up.
 */
data class VideoCounterpart(
    val videoId: String,
    val segments: List<Segment> = emptyList(),
) {
    data class Segment(
        val songStartMs: Long,
        val videoStartMs: Long,
        val durationMs: Long,
    )
}
