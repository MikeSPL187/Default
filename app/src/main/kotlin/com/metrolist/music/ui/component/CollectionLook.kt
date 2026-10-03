package com.metrolist.music.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import com.metrolist.music.R

/** The collections the app keeps by itself. */
enum class CollectionKind { LIKED, DOWNLOADED, TOP, CACHED, UPLOADED }

/** How a collection is drawn: its icon on its own tile, the same wherever it shows. */
data class CollectionLook(
    val icon: Int,
    val background: Brush,
    val tint: Color,
)

private val LikedTileBrush = Brush.linearGradient(listOf(Color(0xFFFF8FA3), Color(0xFFC2185B)))

@Composable
fun collectionLook(kind: CollectionKind): CollectionLook {
    val colors = MaterialTheme.colorScheme
    // Each tile is the surface tinted with its accent, so all of them sit at the same depth in
    // either theme; system colours can make one container light in the dark theme.
    fun tile(accent: Color) = SolidColor(lerp(colors.surfaceContainerHighest, accent, 0.32f))
    return when (kind) {
        CollectionKind.LIKED -> CollectionLook(R.drawable.favorite, LikedTileBrush, Color.White)
        CollectionKind.DOWNLOADED -> CollectionLook(R.drawable.offline, tile(colors.primary), colors.primary)
        CollectionKind.TOP -> CollectionLook(R.drawable.trending_up, tile(colors.secondary), colors.secondary)
        CollectionKind.CACHED -> CollectionLook(R.drawable.cached, tile(colors.tertiary), colors.tertiary)
        CollectionKind.UPLOADED -> CollectionLook(R.drawable.cloud, SolidColor(colors.surfaceContainerHighest), colors.onSurfaceVariant)
    }
}
