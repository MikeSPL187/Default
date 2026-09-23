/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Wraps [action] so that on Android 8-9, where writing to shared Music/ still needs
 * WRITE_EXTERNAL_STORAGE, the permission is requested first. Newer versions run it directly.
 */
@Composable
fun rememberSharedStorageAction(action: () -> Unit): () -> Unit {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return action
    val context = LocalContext.current
    val currentAction by rememberUpdatedState(action)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Without the permission the export itself reports a readable error.
            currentAction()
        }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            currentAction()
        } else {
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
