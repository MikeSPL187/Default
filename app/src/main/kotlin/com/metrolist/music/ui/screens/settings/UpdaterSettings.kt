/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.CheckForUpdatesKey
import com.metrolist.music.constants.UpdateNotificationsEnabledKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.update.UpdateCheckWorker
import com.metrolist.music.update.UpdateProgress
import com.metrolist.music.update.UpdateState
import com.metrolist.music.update.WatchUpdater
import com.metrolist.music.update.WhatsNew
import com.metrolist.music.update.rememberUpdateActions
import com.metrolist.music.update.release
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** This build, whether a newer one exists, the update switches and the latest builds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterScreen(navController: NavController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val (checkForUpdates, onCheckForUpdatesChange) = rememberPreference(CheckForUpdatesKey, true)
    val (updateNotifications, onUpdateNotificationsChange) = rememberPreference(UpdateNotificationsEnabledKey, true)
    val state by WatchUpdater.state.collectAsStateWithLifecycle()
    val releases by WatchUpdater.releases.collectAsStateWithLifecycle()
    val checking by WatchUpdater.checking.collectAsStateWithLifecycle()
    val lastChecked by WatchUpdater.lastChecked.collectAsStateWithLifecycle()
    val actions = rememberUpdateActions(state)
    val current = WatchUpdater.currentBuild

    LaunchedEffect(Unit) { WatchUpdater.checkForUpdate() }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(8.dp))

        val colors = MaterialTheme.colorScheme
        val update = state.release?.takeIf { state !is UpdateState.UpToDate }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (update != null) colors.primaryContainer else colors.surfaceContainerHigh)
                .animateContentSize()
                .padding(20.dp),
        ) {
            val onCard = if (update != null) colors.onPrimaryContainer else colors.onSurface
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyMedium, color = onCard.copy(alpha = 0.8f))
            Text(
                if (current != null) stringResource(R.string.update_build, current) else stringResource(R.string.updates_local_build_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = onCard,
            )
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = update != null,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "update status",
            ) { available ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(if (available) R.drawable.arrow_downward else R.drawable.check),
                        contentDescription = null,
                        tint = if (available) colors.primary else colors.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            current == null -> stringResource(R.string.updates_local_build)
                            available -> stringResource(R.string.updates_available, update?.build ?: 0)
                            else -> stringResource(R.string.updates_latest)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = onCard,
                    )
                }
            }
            Text(
                if (lastChecked == 0L) {
                    stringResource(R.string.updates_never_checked)
                } else {
                    stringResource(R.string.updates_checked, DateUtils.getRelativeTimeSpanString(lastChecked, now, DateUtils.MINUTE_IN_MILLIS).toString())
                },
                style = MaterialTheme.typography.bodySmall,
                color = onCard.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
            if (update != null) {
                UpdateProgress(state, Modifier.padding(top = 16.dp))
                if (update.notes.isNotEmpty()) WhatsNew(update.notes, Modifier.padding(top = 16.dp))
                Spacer(Modifier.height(16.dp))
                when (state) {
                    is UpdateState.Downloading ->
                        OutlinedButton(onClick = WatchUpdater::cancelDownload, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.update_cancel))
                        }
                    is UpdateState.Ready ->
                        Button(onClick = actions.install, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_install)) }
                    else ->
                        Button(onClick = actions.update, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (state is UpdateState.Failed) R.string.update_retry else R.string.update_now))
                        }
                }
                if (state is UpdateState.Failed) {
                    Text(stringResource(R.string.update_failed), style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.update_settings),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.update),
                        title = { Text(stringResource(R.string.updates_auto)) },
                        description = { Text(stringResource(R.string.updates_auto_desc)) },
                        trailingContent = {
                            Switch(checked = checkForUpdates, onCheckedChange = {
                                haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                onCheckForUpdatesChange(it)
                            })
                        },
                        onClick = { onCheckForUpdatesChange(!checkForUpdates) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.notification),
                        title = { Text(stringResource(R.string.updates_notify)) },
                        description = { Text(stringResource(R.string.updates_notify_desc)) },
                        trailingContent = {
                            Switch(checked = updateNotifications, onCheckedChange = {
                                haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                onUpdateNotificationsChange(it)
                                UpdateCheckWorker.schedule(context, it)
                            })
                        },
                        onClick = {
                            onUpdateNotificationsChange(!updateNotifications)
                            UpdateCheckWorker.schedule(context, !updateNotifications)
                        },
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                scope.launch { WatchUpdater.checkForUpdate(force = true) }
            },
            enabled = !checking && current != null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
        ) {
            if (checking) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.updates_check_now))
            }
        }

        if (releases.isNotEmpty()) {
            Text(
                stringResource(R.string.updates_history).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                releases.take(HISTORY).forEach { release ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceContainer)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            release.build.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (release.build == current) colors.primary else colors.onSurface,
                            modifier = Modifier.width(44.dp),
                        )
                        Text(
                            release.notes.firstOrNull().orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.updater)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

private const val HISTORY = 5
