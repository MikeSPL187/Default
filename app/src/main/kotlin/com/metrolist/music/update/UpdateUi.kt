package com.metrolist.music.update

import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.datastore.preferences.core.Preferences
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.constants.CheckForUpdatesKey
import com.metrolist.music.constants.DismissedStandaloneUpdateKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.launch

val UpdateState.release: WatchRelease?
    get() =
        when (this) {
            UpdateState.UpToDate -> null
            is UpdateState.Available -> release
            is UpdateState.Downloading -> release
            is UpdateState.Ready -> release
            is UpdateState.Failed -> release
        }

/**
 * Starts the download of [UpdateState.release] and, once it is ready, the system installer; asks
 * for the permission to install on the way when Android requires it.
 */
class UpdateActions internal constructor(
    val update: () -> Unit,
    val install: () -> Unit,
)

@Composable
fun rememberUpdateActions(state: UpdateState): UpdateActions {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var installWhenReady by remember { mutableStateOf(false) }
    var awaitingPermission by remember { mutableStateOf(false) }

    fun installNow(ready: UpdateState.Ready) {
        if (WatchUpdater.canInstall(context)) {
            WatchUpdater.install(context, ready.file)
        } else {
            awaitingPermission = true
            WatchUpdater.requestInstallPermission(context)
        }
    }

    LaunchedEffect(state, installWhenReady) {
        if (installWhenReady && state is UpdateState.Ready) {
            installWhenReady = false
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            installNow(state)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitingPermission && WatchUpdater.canInstall(context)) {
            awaitingPermission = false
            (WatchUpdater.state.value as? UpdateState.Ready)?.let { WatchUpdater.install(context, it.file) }
        }
    }
    return UpdateActions(
        update = {
            state.release?.let {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                installWhenReady = true
                WatchUpdater.download(context, it)
            }
        },
        install = {
            (state as? UpdateState.Ready)?.let {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                installNow(it)
            }
        },
    )
}

/**
 * Checks for a new build when the app opens and offers it in a bottom sheet. "Later" hides that
 * build for good; a tap on the update notification brings the sheet back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchUpdatePrompt() {
    if (WatchUpdater.currentBuild == null) return
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val scope = rememberCoroutineScope()
    // Read before deciding anything, so a build the user put off never flashes up on launch.
    val prefs by produceState<Preferences?>(null) { context.dataStore.data.collect { value = it } }
    val checkEnabled = prefs?.get(CheckForUpdatesKey) ?: true
    val state by WatchUpdater.state.collectAsStateWithLifecycle()
    var hidden by rememberSaveable { mutableStateOf(false) }
    var engaged by rememberSaveable { mutableStateOf(false) }
    var forced by rememberSaveable { mutableStateOf(activity?.intent?.getBooleanExtra(UpdateCheckWorker.EXTRA_SHOW_UPDATE, false) == true) }

    DisposableEffect(activity) {
        val listener =
            Consumer<android.content.Intent> { intent ->
                if (intent.getBooleanExtra(UpdateCheckWorker.EXTRA_SHOW_UPDATE, false)) {
                    forced = true
                    hidden = false
                }
            }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
    LaunchedEffect(Unit) { WatchUpdater.cleanUp(context) }
    LaunchedEffect(prefs == null, checkEnabled, forced) {
        if (prefs != null && (checkEnabled || forced)) WatchUpdater.checkForUpdate(force = forced)
    }

    val loaded = prefs ?: return
    val release = state.release ?: return
    val offered = checkEnabled && state is UpdateState.Available && release.tag != loaded[DismissedStandaloneUpdateKey]
    if (hidden || !(engaged || forced || offered)) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val actions = rememberUpdateActions(state)
    ModalBottomSheet(
        onDismissRequest = { hidden = true },
        sheetState = sheetState,
    ) {
        UpdateSheetContent(
            state = state,
            onLater = {
                hidden = true
                scope.launch { context.safeDataStoreEdit { it[DismissedStandaloneUpdateKey] = release.tag } }
            },
            onUpdate = {
                engaged = true
                actions.update()
            },
            onInstall = actions.install,
            onCancel = WatchUpdater::cancelDownload,
        )
    }
}

@Composable
private fun UpdateSheetContent(
    state: UpdateState,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val release = state.release ?: return
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
            ) {
                Icon(painterResource(R.drawable.arrow_downward), contentDescription = null, tint = colors.onPrimaryContainer)
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val subtitle =
                    when (state) {
                        is UpdateState.Downloading -> stringResource(R.string.update_downloading, release.build)
                        is UpdateState.Ready -> stringResource(R.string.update_ready, release.build)
                        else ->
                            stringResource(
                                R.string.update_subtitle,
                                release.build,
                                WatchUpdater.currentBuild ?: 0,
                                Formatter.formatShortFileSize(context, release.size),
                            )
                    }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }

        UpdateProgress(state, Modifier.padding(top = 20.dp))

        if (release.notes.isNotEmpty()) {
            WhatsNew(release.notes, Modifier.padding(top = 20.dp))
        }

        Spacer(Modifier.height(24.dp))
        AnimatedContent(
            targetState = state::class,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "update actions",
        ) { kind ->
            when (kind) {
                UpdateState.Downloading::class ->
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onCancel()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.update_cancel)) }

                UpdateState.Ready::class ->
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_install)) }

                else ->
                    Column {
                        if (state is UpdateState.Failed) {
                            Text(
                                stringResource(R.string.update_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.error,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onLater()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.update_later)) }
                            Button(onClick = onUpdate, modifier = Modifier.weight(1f)) {
                                Text(stringResource(if (state is UpdateState.Failed) R.string.update_retry else R.string.update_now))
                            }
                        }
                        Text(
                            stringResource(R.string.update_music_keeps_playing),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                        )
                    }
            }
        }
    }
}

/** A wavy bar with how much is downloaded, the speed and the time left; nothing when idle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateProgress(
    state: UpdateState,
    modifier: Modifier = Modifier,
) {
    val downloading = state as? UpdateState.Downloading
    val done = state is UpdateState.Ready
    if (downloading == null && !done) return
    val context = LocalContext.current
    val shown by animateFloatAsState(if (done) 1f else downloading?.fraction ?: 0f, tween(300), label = "update progress")
    Column(modifier) {
        LinearWavyProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
        if (downloading != null) {
            Row(Modifier.padding(top = 10.dp)) {
                Text(
                    stringResource(
                        R.string.update_progress,
                        Formatter.formatShortFileSize(context, downloading.bytes),
                        Formatter.formatShortFileSize(context, downloading.total),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (downloading.bytesPerSecond > 0) {
                    val left = ((downloading.total - downloading.bytes) / downloading.bytesPerSecond).coerceAtLeast(1)
                    val eta =
                        if (left < 60) stringResource(R.string.update_eta_seconds, left) else stringResource(R.string.update_eta_minutes, (left + 59) / 60)
                    Text(
                        stringResource(R.string.download_speed, Formatter.formatShortFileSize(context, downloading.bytesPerSecond)) + " · " + eta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun WhatsNew(
    notes: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.update_whats_new).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
        )
        notes.take(MAX_NOTES).forEach { note ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(painterResource(R.drawable.check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Text(note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

private const val MAX_NOTES = 6
