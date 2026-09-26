package com.metrolist.music.ui.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import timber.log.Timber

/** Where the sound goes: this phone, wired headphones or a Bluetooth device by its name. */
data class AudioOutput(
    val kind: Kind,
    val name: String? = null,
) {
    enum class Kind { PHONE, WIRED, BLUETOOTH }
}

private val bluetoothTypes =
    buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }

private val wiredTypes =
    setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET)

/** Media follows the last connected headset, so a connected one is where it plays. */
private fun currentOutput(audioManager: AudioManager): AudioOutput {
    val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    outputs.firstOrNull { it.type in bluetoothTypes }?.let { return AudioOutput(AudioOutput.Kind.BLUETOOTH, it.productName?.toString()?.takeIf(String::isNotBlank)) }
    if (outputs.any { it.type in wiredTypes }) return AudioOutput(AudioOutput.Kind.WIRED)
    return AudioOutput(AudioOutput.Kind.PHONE)
}

/** The output of the moment, kept up to date as headphones come and go. */
@Composable
fun rememberAudioOutput(): AudioOutput {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    var output by remember { mutableStateOf(currentOutput(audioManager)) }
    DisposableEffect(audioManager) {
        val callback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    output = currentOutput(audioManager)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    output = currentOutput(audioManager)
                }
            }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    return output
}

/**
 * Opens the system's output switcher, where the user picks the phone, headphones or a speaker.
 * Before Android 14 SystemUI takes it as a broadcast; without either, the Bluetooth settings.
 */
fun openOutputSwitcher(context: Context) {
    val shown =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                runCatching {
                    context.sendBroadcast(
                        Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                            .setPackage("com.android.systemui")
                            .putExtra("package_name", context.packageName),
                    )
                    true
                }.getOrDefault(false)
            else -> false
        }
    if (!shown) {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No Bluetooth settings")
        }
    }
}

/** "Where it plays": a chip with the current output that opens the switcher. */
@Composable
fun AudioOutputChip(
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val output = rememberAudioOutput()
    val label =
        output.name ?: stringResource(
            when (output.kind) {
                AudioOutput.Kind.PHONE -> R.string.output_this_phone
                AudioOutput.Kind.WIRED -> R.string.output_headphones
                AudioOutput.Kind.BLUETOOTH -> R.string.output_bluetooth
            },
        )
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            openOutputSwitcher(context)
        },
        shape = CircleShape,
        color = contentColor.copy(alpha = 0.12f),
        contentColor = contentColor,
        modifier = modifier.widthIn(max = 240.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp)) {
            Icon(
                painterResource(if (output.kind == AudioOutput.Kind.PHONE) R.drawable.speaker else R.drawable.headphones),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
