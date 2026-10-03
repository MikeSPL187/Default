package com.metrolist.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
    /** The device itself, to send the sound to it; null for an output only described. */
    val device: AudioDeviceInfo? = null,
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

/**
 * The outputs music can play on now: the phone's speaker, then wired headphones, then each
 * Bluetooth device once (a headset is listed under several types).
 */
private fun availableOutputs(audioManager: AudioManager): List<AudioOutput> {
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    val wired = devices.firstOrNull { it.type in wiredTypes }
    val bluetooth =
        devices
            .filter { it.type in bluetoothTypes }
            .distinctBy { it.productName?.toString() ?: it.address }
    return buildList {
        add(AudioOutput(AudioOutput.Kind.PHONE, device = speaker))
        wired?.let { add(AudioOutput(AudioOutput.Kind.WIRED, it.productName?.toString()?.takeIf(String::isNotBlank)?.takeUnless { name -> name == Build.MODEL }, it)) }
        bluetooth.forEach { add(AudioOutput(AudioOutput.Kind.BLUETOOTH, it.productName?.toString()?.takeIf(String::isNotBlank), it)) }
    }
}

/** Where media goes without a choice made in the app: the last connected headset, else the phone. */
private fun systemOutput(outputs: List<AudioOutput>): AudioOutput =
    outputs.firstOrNull { it.kind == AudioOutput.Kind.BLUETOOTH }
        ?: outputs.firstOrNull { it.kind == AudioOutput.Kind.WIRED }
        ?: outputs.first()

/** The outputs of the moment, kept up to date as headphones come and go. */
@Composable
private fun rememberAudioOutputs(): List<AudioOutput> {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    var outputs by remember { mutableStateOf(availableOutputs(audioManager)) }
    DisposableEffect(audioManager) {
        val callback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    outputs = availableOutputs(audioManager)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    outputs = availableOutputs(audioManager)
                }
            }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    return outputs
}

private fun openBluetoothSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No Bluetooth settings")
    }
}

@Composable
private fun outputLabel(output: AudioOutput): String =
    output.name ?: stringResource(
        when (output.kind) {
            AudioOutput.Kind.PHONE -> R.string.output_this_phone
            AudioOutput.Kind.WIRED -> R.string.output_headphones
            AudioOutput.Kind.BLUETOOTH -> R.string.output_bluetooth
        },
    )

private fun outputIcon(output: AudioOutput) = if (output.kind == AudioOutput.Kind.PHONE) R.drawable.speaker else R.drawable.headphones

/**
 * "Where it plays": a chip with the current output that opens a sheet of the outputs at hand.
 * The sheet is the app's own, as the system's output switcher is missing on many phones.
 */
@Composable
fun AudioOutputChip(
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val service = LocalPlayerConnection.current?.service
    val outputs = rememberAudioOutputs()
    val noPlayer = remember { MutableStateFlow<AudioDeviceInfo?>(null) }
    val preferred by (service?.preferredOutput ?: noPlayer).collectAsStateWithLifecycle()
    // A device picked here plays while it is connected; once it is gone, the system decides again.
    val current = preferred?.let { device -> outputs.firstOrNull { it.device?.id == device.id } } ?: systemOutput(outputs)
    var picking by rememberSaveable { mutableStateOf(false) }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            picking = true
        },
        shape = CircleShape,
        color = contentColor.copy(alpha = 0.12f),
        contentColor = contentColor,
        modifier = modifier.widthIn(max = 240.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp)) {
            Icon(painterResource(outputIcon(current)), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                outputLabel(current),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    if (picking) {
        OutputSheet(
            outputs = outputs,
            current = current,
            onPick = { output ->
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                // Picking where the system plays anyway is no choice to remember.
                service?.setPreferredOutput(output.device.takeUnless { output == systemOutput(outputs) })
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutputSheet(
    outputs: List<AudioOutput>,
    current: AudioOutput,
    onPick: (AudioOutput) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.output_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
            )
            outputs.forEach { output ->
                val selected = output == current
                Surface(
                    onClick = { onPick(output) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) colors.secondaryContainer else Color.Transparent,
                    contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Icon(painterResource(outputIcon(output)), contentDescription = null)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(outputLabel(output), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(
                                    when (output.kind) {
                                        AudioOutput.Kind.PHONE -> R.string.output_kind_phone
                                        AudioOutput.Kind.WIRED -> R.string.output_kind_wired
                                        AudioOutput.Kind.BLUETOOTH -> R.string.output_bluetooth
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) colors.onSecondaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                            )
                        }
                        if (selected) Icon(painterResource(R.drawable.check), contentDescription = stringResource(R.string.output_playing_here))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Surface(
                onClick = {
                    onDismiss()
                    openBluetoothSettings(context)
                },
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Icon(painterResource(R.drawable.bluetooth), contentDescription = null, tint = colors.primary)
                    Text(
                        stringResource(R.string.output_connect_device),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.primary,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}
