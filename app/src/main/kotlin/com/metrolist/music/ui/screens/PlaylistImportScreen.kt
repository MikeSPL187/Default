package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.playlistimport.ImportError
import com.metrolist.music.playlistimport.ImportSource
import com.metrolist.music.playlistimport.ImportState
import com.metrolist.music.playlistimport.ImportedPlaylist
import com.metrolist.music.playlistimport.PlaylistImporter
import com.metrolist.music.playlistimport.TrackStatus
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistImportViewModel
    @Inject
    constructor(
        val importer: PlaylistImporter,
    ) : ViewModel()

/** Transfers a playlist from Yandex Music or Spotify, or a pasted list, into the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportScreen(
    navController: NavController,
    viewModel: PlaylistImportViewModel = hiltViewModel(),
) {
    val importer = viewModel.importer
    val state by importer.state.collectAsStateWithLifecycle()
    val playlist = state.playlist

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
            modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
        ) {
            if (playlist == null) {
                item(key = "form") {
                    ImportForm(
                        state = state,
                        onLoadLink = importer::load,
                        onLoadList = importer::loadList,
                    )
                }
            } else {
                item(key = "header") {
                    ImportHeader(
                        state = state,
                        playlist = playlist,
                        onStart = importer::start,
                        onBack = importer::reset,
                        onCancel = importer::cancel,
                        onOpen = { id -> navController.navigate("local_playlist/$id") },
                        onAgain = importer::reset,
                    )
                }
                itemsIndexed(playlist.tracks, key = { index, _ -> "track_$index" }) { index, track ->
                    TrackRow(
                        number = index + 1,
                        title = track.title,
                        artists = track.artists.joinToString(", "),
                        status = state.statuses.getOrNull(index) ?: TrackStatus.PENDING,
                        importing = state.phase == ImportState.Phase.IMPORTING,
                    )
                }
                item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
            }
        }

        TopAppBar(
            title = { Text(stringResource(R.string.playlist_import_title)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )
    }
}

@Composable
private fun ImportForm(
    state: ImportState,
    onLoadLink: (String) -> Unit,
    onLoadList: (String, String) -> Unit,
) {
    var byList by rememberSaveable { mutableStateOf(false) }
    var link by rememberSaveable { mutableStateOf("") }
    var listTitle by rememberSaveable { mutableStateOf("") }
    var listText by rememberSaveable { mutableStateOf("") }
    val loading = state.phase == ImportState.Phase.LOADING
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val defaultName = stringResource(R.string.playlist_import_default_name)

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.playlist_import_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !byList,
                onClick = { byList = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text(stringResource(R.string.playlist_import_by_link)) },
            )
            SegmentedButton(
                selected = byList,
                onClick = { byList = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text(stringResource(R.string.playlist_import_by_list)) },
            )
        }

        val error = state.error?.let { errorText(it) }
        if (!byList) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text(stringResource(R.string.playlist_import_link_label)) },
                supportingText = { Text(error ?: stringResource(R.string.playlist_import_link_hint)) },
                isError = error != null,
                singleLine = true,
                enabled = !loading,
                leadingIcon = { Icon(painterResource(R.drawable.link), contentDescription = null) },
                trailingIcon = {
                    TextButton(
                        enabled = !loading,
                        onClick = {
                            scope.launch {
                                clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { link = it.toString().trim() }
                            }
                        },
                    ) { Text(stringResource(R.string.playlist_import_paste)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onLoadLink(link) },
                enabled = link.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.playlist_import_find))
                }
            }
        } else {
            OutlinedTextField(
                value = listTitle,
                onValueChange = { listTitle = it },
                label = { Text(stringResource(R.string.playlist_import_name_label)) },
                placeholder = { Text(defaultName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = listText,
                onValueChange = { listText = it },
                label = { Text(stringResource(R.string.playlist_import_list_label)) },
                placeholder = { Text(stringResource(R.string.playlist_import_list_hint)) },
                supportingText = if (error != null) { { Text(error) } } else null,
                isError = error != null,
                minLines = 6,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onLoadList(listTitle.ifBlank { defaultName }, listText) },
                enabled = listText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.playlist_import_continue))
            }
        }
    }
}

@Composable
private fun errorText(error: ImportError): String =
    stringResource(
        when (error) {
            ImportError.UNSUPPORTED_LINK -> R.string.playlist_import_error_link
            ImportError.NOT_FOUND -> R.string.playlist_import_error_not_found
            ImportError.NETWORK -> R.string.playlist_import_error_network
            ImportError.EMPTY -> R.string.playlist_import_error_empty
            ImportError.UNREADABLE -> R.string.playlist_import_error_unreadable
        },
    )

@Composable
private fun ImportHeader(
    state: ImportState,
    playlist: ImportedPlaylist,
    onStart: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (String) -> Unit,
    onAgain: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val total = playlist.tracks.size
    var name by rememberSaveable(playlist.title) { mutableStateOf(playlist.title) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerHighest),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text =
                        stringResource(
                            when (playlist.source) {
                                ImportSource.YANDEX_MUSIC -> R.string.playlist_import_source_yandex
                                ImportSource.SPOTIFY -> R.string.playlist_import_source_spotify
                                ImportSource.TEXT -> R.string.playlist_import_source_text
                            },
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.playlist_import_track_count, total, total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        when (state.phase) {
            ImportState.Phase.PREVIEW -> {
                if (playlist.truncated) NoteCard(stringResource(R.string.playlist_import_truncated))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_import_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.playlist_import_back))
                    }
                    Button(onClick = { onStart(name) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.playlist_import_start))
                    }
                }
            }

            ImportState.Phase.IMPORTING -> {
                Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.playlist_import_searching), style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else state.processed.toFloat() / total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.playlist_import_progress, state.found, total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.playlist_import_background_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                }
            }

            ImportState.Phase.DONE -> {
                Card(colors = CardDefaults.cardColors(containerColor = colors.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.playlist_import_done, state.found, total),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onPrimaryContainer,
                        )
                        if (state.found < total) {
                            Text(
                                text = stringResource(R.string.playlist_import_not_found_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onPrimaryContainer,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onAgain, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.playlist_import_again))
                    }
                    Button(
                        onClick = { state.playlistId?.let(onOpen) },
                        enabled = state.playlistId != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.playlist_import_open))
                    }
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun NoteCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(16.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun TrackRow(
    number: Int,
    title: String,
    artists: String,
    status: TrackStatus,
    importing: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artists.isNotEmpty()) {
                Text(artists, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (status) {
                TrackStatus.FOUND -> Icon(painterResource(R.drawable.check), contentDescription = null, tint = colors.primary)
                TrackStatus.NOT_FOUND -> Icon(painterResource(R.drawable.close), contentDescription = null, tint = colors.error)
                TrackStatus.PENDING -> if (importing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}
