/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import java.time.Instant

@Composable
fun PlaybackError(
    error: PlaybackException,
    retry: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val streamClient by playerConnection.currentStreamClient.collectAsStateWithLifecycle()
    val isOnline by playerConnection.service.connectivityObserver.networkStatus.collectAsStateWithLifecycle()
    val causes = remember(error) { error.causeChain() }
    val rawErrorMessages =
        remember(causes) {
            causes.mapNotNull { it.message?.takeIf(String::isNotBlank) }.distinct()
        }
    val isExplicitRestricted =
        rawErrorMessages.any {
            it.contains("confirm your age", ignoreCase = true) ||
                it.contains("age-restricted", ignoreCase = true) ||
                it.contains("LOGIN_REQUIRED", ignoreCase = true) ||
                it.contains("403", ignoreCase = true)
        }
    val isJobCancelled =
        rawErrorMessages.any {
            it.contains("job", ignoreCase = true) &&
                (it.contains("cancelled", ignoreCase = true) ||
                    it.contains("canceled", ignoreCase = true) ||
                    it.contains("cancellat", ignoreCase = true))
        }
    val guidance =
        when {
            isExplicitRestricted -> stringResource(R.string.error_explicit_login_recommended)
            isJobCancelled -> stringResource(R.string.error_job_cancelled)
            else -> null
        }
    val errorMessage =
        playbackErrorMessages(
            isOnline = isOnline,
            offlineMessage = stringResource(R.string.error_offline_playback),
            guidance = guidance,
            rawErrorMessages = rawErrorMessages,
        ).joinToString("\n").ifBlank { stringResource(R.string.error_unknown) }
    val causeSummary =
        remember(causes) {
            causes
                .drop(1)
                .joinToString(" → ") { cause ->
                    cause.javaClass.simpleName.ifBlank { cause.javaClass.name }
                }
        }
    val errorCodeName = remember(error) { error.errorCodeName.removePrefix("ERROR_CODE_") }
    val reportedAt = remember(error) { Instant.now() }
    val errorReport =
        remember(error, mediaMetadata, streamClient) {
            buildPlaybackErrorReport(error, mediaMetadata, streamClient, reportedAt)
        }

    var details by remember(error) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // Takt: what happened and what to do in one sentence; the technical part stays folded away
    // for a bug report.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(colors.surfaceContainerHigh.copy(alpha = 0.92f))
                .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.errorContainer),
        ) {
            Icon(
                painter = painterResource(if (isOnline) R.drawable.error else R.drawable.cloud_off),
                contentDescription = null,
                tint = colors.onErrorContainer,
                modifier = Modifier.size(30.dp),
            )
        }

        Text(
            text = stringResource(if (isOnline) R.string.player_error_title else R.string.error_no_internet_connection),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text =
                when {
                    !isOnline -> stringResource(R.string.error_offline_playback)
                    guidance != null -> guidance
                    else -> stringResource(R.string.player_error_hint)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Button(onClick = retry) {
                Icon(painterResource(R.drawable.replay), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.retry))
            }
            OutlinedButton(onClick = { playerConnection.player.seekToNext() }) {
                Icon(painterResource(R.drawable.skip_next), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.player_error_next))
            }
        }

        TextButton(onClick = { details = !details }, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.player_error_details), style = MaterialTheme.typography.labelMedium)
            Icon(
                painterResource(if (details) R.drawable.expand_less else R.drawable.expand_more),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(visible = details) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf("$errorCodeName (${error.errorCode})", causeSummary).filter { it.isNotEmpty() }.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Metrolist Playback Error", errorReport))
                        // Android 13 and later confirm a copy on their own.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Icon(painterResource(R.drawable.content_copy), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.player_error_copy))
                }
            }
        }
    }
}

internal fun Throwable.causeChain(): List<Throwable> =
    generateSequence(this) { it.cause }.take(8).toList()

internal fun playbackErrorMessages(
    isOnline: Boolean,
    offlineMessage: String,
    guidance: String?,
    rawErrorMessages: List<String>,
): List<String> =
    if (isOnline) (listOfNotNull(guidance) + rawErrorMessages).distinct() else listOf(offlineMessage)

private fun buildPlaybackErrorReport(
    error: PlaybackException,
    mediaMetadata: MediaMetadata?,
    streamClient: String?,
    reportedAt: Instant,
): String =
    buildString {
        appendLine("Metrolist Playback Error Report")
        appendLine("================================")
        appendLine("Time: $reportedAt")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Architecture: ${BuildConfig.ARCHITECTURE}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()
        appendLine("Media")
        appendLine("-----")
        appendLine("Title: ${mediaMetadata?.title ?: "Unknown"}")
        appendLine("Artists: ${mediaMetadata?.artists?.joinToString(", ") { it.name } ?: "Unknown"}")
        appendLine("ID: ${mediaMetadata?.id ?: "Unknown"}")
        appendLine("Link: ${mediaMetadata?.id?.let { "https://music.youtube.com/watch?v=$it" } ?: "Unknown"}")
        appendLine("Stream client: ${streamClient ?: "Not resolved"}")
        appendLine()
        appendLine("Error")
        appendLine("-----")
        appendLine("Code: ${error.errorCodeName.removePrefix("ERROR_CODE_")} (${error.errorCode})")
        error.causeChain().forEachIndexed { index, cause ->
            appendLine("[$index] ${cause.javaClass.name}: ${cause.message ?: "(no message)"}")
        }
        appendLine()
        appendLine("Stack trace")
        appendLine("-----------")
        append(error.stackTraceToString())
    }
