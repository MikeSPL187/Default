package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.PlayerBackgroundStyle
import com.metrolist.music.ui.screens.wrapped.components.rememberArtworkAccent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

/**
 * The three looks of the player side by side, drawn with the cover of what plays now, so the
 * choice is seen rather than described. Tapping one picks it.
 */
@Composable
fun PlayerStylePreview(
    selected: PlayerBackgroundStyle,
    onSelect: (PlayerBackgroundStyle) -> Unit,
    available: List<PlayerBackgroundStyle>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val metadata = LocalPlayerConnection.current?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    val cover = metadata?.thumbnailUrl
    val accent = rememberArtworkAccent(cover) ?: colors.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surfaceContainer)
                .padding(14.dp),
    ) {
        listOf(
            PlayerBackgroundStyle.GRADIENT to R.string.gradient,
            PlayerBackgroundStyle.BLUR to R.string.player_background_blur,
            PlayerBackgroundStyle.DEFAULT to R.string.follow_theme,
        ).filter { it.first in available }.forEach { (style, label) ->
            val on = style == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(style)
                        },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (on) Modifier.border(3.dp, colors.primary, RoundedCornerShape(16.dp)) else Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
                        ),
                ) {
                    when (style) {
                        PlayerBackgroundStyle.GRADIENT ->
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent, Color.Black.copy(alpha = 0.85f)))))
                        PlayerBackgroundStyle.BLUR ->
                            AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(14.dp).background(accent.copy(alpha = 0.6f)))
                        PlayerBackgroundStyle.DEFAULT ->
                            Box(Modifier.fillMaxSize().background(colors.surfaceContainerHighest))
                    }
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 14.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(accent),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 14.dp, end = 14.dp, bottom = 16.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (style == PlayerBackgroundStyle.DEFAULT) colors.onSurfaceVariant else Color.White.copy(alpha = 0.75f)),
                    )
                }
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
        }
    }
}
