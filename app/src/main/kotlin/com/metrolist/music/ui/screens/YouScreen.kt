package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.AccountSettingsDialog
import com.metrolist.music.ui.screens.wrapped.WrappedPeriod
import com.metrolist.music.ui.screens.wrapped.wrappedRoute
import com.metrolist.music.viewmodels.WeekSummary
import com.metrolist.music.viewmodels.YouViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The «Вы» tab: the profile, this week in numbers, the recap and the tools that used to hide
 * behind icons on the home top bar.
 */
@Composable
fun YouScreen(
    navController: NavController,
    latestVersionName: String,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val week by viewModel.week.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    var showAccount by remember { mutableStateOf(false) }
    val go: (String) -> Unit = { route ->
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        navController.navigate(route)
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        item(key = "profile") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            if (account == null) navController.navigate("login") else showAccount = true
                        }.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                val info = account
                if (info?.thumbnailUrl != null) {
                    AsyncImage(
                        model = info.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(60.dp)
                                .clip(CircleShape),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Icon(painterResource(R.drawable.you_filled), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text(
                        info?.name ?: stringResource(R.string.you_sign_in),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        info?.channelHandle ?: info?.email ?: stringResource(if (info == null) R.string.you_sign_in_sub else R.string.you_account_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(painterResource(R.drawable.navigate_next), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item(key = "week") { WeekCard(week, onClick = { go("stats") }) }

        item(key = "recap") {
            val month = YearMonth.now()
            RecapCard(
                month = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
                onClick = { go(wrappedRoute(WrappedPeriod.InMonth(month))) },
            )
        }

        item(key = "tools") {
            val tools =
                listOf(
                    Tool(R.drawable.history, R.string.history, Color(0xFFFFB780), "history"),
                    Tool(R.drawable.graphic_eq, R.string.you_recognized, Color(0xFF9CC7FF), "recognition_history"),
                    Tool(R.drawable.group_outlined, R.string.together, Color(0xFFC9A2F5), "listen_together_from_topbar"),
                    Tool(R.drawable.alarm, R.string.you_alarm, Color(0xFFE6D35A), "alarm"),
                    Tool(R.drawable.equalizer, R.string.equalizer, Color(0xFF9DD3A8), "equalizer"),
                    Tool(R.drawable.watch, R.string.you_watch, Color(0xFFF59A9A), "settings/storage"),
                )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                tools.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tool -> ToolTile(tool, Modifier.weight(1f)) { go(tool.route) } }
                    }
                }
            }
        }

        item(key = "settings") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { go("settings") }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) { Icon(painterResource(R.drawable.settings), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp)) }
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 14.dp))
                Icon(painterResource(R.drawable.navigate_next), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showAccount) {
        AccountSettingsDialog(onDismiss = { showAccount = false }, latestVersionName = latestVersionName)
    }
}

private data class Tool(
    val icon: Int,
    val label: Int,
    val tint: Color,
    val route: String,
)

@Composable
private fun ToolTile(
    tool: Tool,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .height(84.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainer)
                .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(lerp(colors.surfaceContainerHigh, tool.tint, 0.26f)),
        ) { Icon(painterResource(tool.icon), null, tint = tool.tint, modifier = Modifier.size(21.dp)) }
        Text(
            stringResource(tool.label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp, start = 6.dp, end = 6.dp),
        )
    }
}

@Composable
private fun WeekCard(
    week: WeekSummary?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.you_this_week).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(
                    week?.totalMs?.let { formatListening(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                val delta = week?.takeIf { it.previousWeekMs > 0 }?.let { ((it.totalMs - it.previousWeekMs) * 100.0 / it.previousWeekMs).roundToInt() }
                if (delta != null) {
                    Surface(shape = CircleShape, color = colors.tertiaryContainer, modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            stringResource(R.string.you_week_delta, (if (delta >= 0) "+" else "−") + kotlin.math.abs(delta) + "%"),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else if (week != null && week.totalMs == 0L) {
                    Text(stringResource(R.string.you_week_empty), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
            week?.topArtist?.let { artist ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = artist.artist.thumbnailUrl,
                        contentDescription = artist.artist.name,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape),
                    )
                    Text(stringResource(R.string.you_most_played), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        val days = week?.perDayMs ?: List(7) { 0L }
        val max = days.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val today = LocalDate.now().dayOfWeek.value - 1
        val labels = stringResource(R.string.you_weekdays).split(",")
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
            modifier =
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(70.dp),
        ) {
            days.forEachIndexed { i, ms ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((4 + 44 * ms.toFloat() / max).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (i == today) colors.primary else lerp(colors.surfaceContainerHighest, colors.primary, 0.28f)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        labels.getOrElse(i) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == today) colors.onSurface else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapCard(
    month: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.you_recap_title, month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
            Text(stringResource(R.string.you_recap_sub), style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
        }
        Surface(shape = CircleShape, color = colors.onPrimaryContainer) {
            Text(
                stringResource(R.string.you_recap_open),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun formatListening(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    return when {
        hours > 0 && minutes % 60 > 0 -> stringResource(R.string.duration_hours_minutes, hours.toInt(), (minutes % 60).toInt())
        hours > 0 -> stringResource(R.string.duration_hours, hours.toInt())
        else -> stringResource(R.string.duration_minutes, minutes.toInt())
    }
}
