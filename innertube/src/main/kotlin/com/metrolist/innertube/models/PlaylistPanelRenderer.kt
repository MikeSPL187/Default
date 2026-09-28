package com.metrolist.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class PlaylistPanelRenderer(
    val title: String?,
    val titleText: Runs?,
    val shortBylineText: Runs?,
    val contents: List<Content>,
    val isInfinite: Boolean?,
    val numItemsToShow: Int?,
    val playlistId: String?,
    val continuations: List<Continuation>?,
) {
    @Serializable
    data class Content(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer?,
        val automixPreviewVideoRenderer: AutomixPreviewVideoRenderer?,
        val playlistPanelVideoWrapperRenderer: PlaylistPanelVideoWrapperRenderer? = null,
    ) {
        /** The queue entry itself, also when YouTube wraps a song together with its music video. */
        val videoRenderer: PlaylistPanelVideoRenderer?
            get() = playlistPanelVideoRenderer ?: playlistPanelVideoWrapperRenderer?.primaryRenderer?.playlistPanelVideoRenderer
    }
}

/**
 * A song paired with its music video, as YouTube Music lists it to offer "Song / Video". The
 * counterpart is read as loosely as possible, so an unexpected shape never loses the queue.
 */
@Serializable
data class PlaylistPanelVideoWrapperRenderer(
    val primaryRenderer: PrimaryRenderer? = null,
    val counterpart: List<Counterpart>? = null,
) {
    @Serializable
    data class PrimaryRenderer(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null,
    )

    @Serializable
    data class Counterpart(
        val counterpartRenderer: CounterpartRenderer? = null,
        val segmentMap: SegmentMap? = null,
    )

    @Serializable
    data class CounterpartRenderer(
        val playlistPanelVideoRenderer: CounterpartVideo? = null,
    )

    @Serializable
    data class CounterpartVideo(
        val videoId: String? = null,
    )

    @Serializable
    data class SegmentMap(
        val segment: List<Segment>? = null,
    )

    /** Times arrive as strings or numbers depending on the client, so both are accepted. */
    @Serializable
    data class Segment(
        val primaryVideoStartTimeMilliseconds: JsonPrimitive? = null,
        val counterpartVideoStartTimeMilliseconds: JsonPrimitive? = null,
        val durationMilliseconds: JsonPrimitive? = null,
    )
}
