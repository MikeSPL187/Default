package com.metrolist.music.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInHorizontally
import androidx.compose.material3.Surface
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playlistimport.ImportError
import com.metrolist.music.playlistimport.ImportLink
import com.metrolist.music.playlistimport.ImportSource
import com.metrolist.music.playlistimport.ImportState
import com.metrolist.music.playlistimport.ImportedPlaylist
import com.metrolist.music.playlistimport.ImportedTrack
import com.metrolist.music.playlistimport.PlaylistImportService
import com.metrolist.music.playlistimport.PlaylistImporter
import com.metrolist.music.playlistimport.TrackStatus
import com.metrolist.music.playlistimport.parseImportLink
import com.metrolist.music.playlistimport.parseTrackList
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.TITLE_SCROLL_PX
import com.metrolist.music.ui.screens.wrapped.components.rememberArtworkAccent
import com.metrolist.music.ui.utils.backToMain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistImportViewModel
    @Inject
    constructor(
        val importer: PlaylistImporter,
    ) : ViewModel()

private enum class TrackFilter { ALL, FOUND, MISSING }

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
    val colors = MaterialTheme.colorScheme
    val playerConnection = LocalPlayerConnection.current
    val haptic = LocalHapticFeedback.current
    val insets = LocalPlayerAwareWindowInsets.current

    var filter by rememberSaveable { mutableStateOf(TrackFilter.ALL) }
    var pickIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    // Where the music comes from; none yet while the sources are being chosen from.
    var origin by rememberSaveable { mutableStateOf<Origin?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val file = readPlaylistFile(context, uri)
                    if (file != null) importer.loadFile(file.first, file.second) else importer.fileFailed()
                }
            }
        }
    fun choose(next: Origin?) {
        importer.clearError()
        if (next == Origin.FILE) filePicker.launch(PLAYLIST_FILE_TYPES) else origin = next
    }
    // Back from a source's form returns to the sources, not out of the screen.
    BackHandler(enabled = playlist == null && origin != null) { choose(null) }
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > TITLE_SCROLL_PX } }
    // The bar lies over the cover's glow at the top and takes a background once the list runs under it.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // A little buzz when a transfer finishes while the screen is open, not on coming back to it.
    var lastPhase by remember { mutableStateOf(state.phase) }
    LaunchedEffect(state.phase) {
        val previous = lastPhase
        lastPhase = state.phase
        if (previous == ImportState.Phase.IMPORTING && state.phase == ImportState.Phase.DONE) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        if (state.phase != ImportState.Phase.DONE) filter = TrackFilter.ALL
        // The ring and the result sit at the top: a list read down to its end comes back up to them.
        if (previous != state.phase && (state.phase == ImportState.Phase.IMPORTING || state.phase == ImportState.Phase.DONE)) {
            listState.animateScrollToItem(0)
        }
    }

    val accent by animateColorAsState(
        rememberArtworkAccent(playlist?.coverUrl) ?: colors.primaryContainer,
        tween(700),
        label = "import accent",
    )
    val bottomAction = state.phase == ImportState.Phase.PREVIEW || state.phase == ImportState.Phase.IMPORTING

    Box(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        // The cover's colour glows behind the playlist; before there is one, the theme's own.
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = if (playlist != null) 0.5f else 0.35f), Color.Transparent))),
        )

        val indices =
            remember(playlist, state.statuses, filter) {
                playlist?.tracks?.indices?.filter { index ->
                    val status = state.statuses.getOrNull(index)
                    when (filter) {
                        TrackFilter.ALL -> true
                        TrackFilter.FOUND -> status == TrackStatus.FOUND
                        TrackFilter.MISSING -> status == TrackStatus.NOT_FOUND
                    }
                }.orEmpty()
            }

        LazyColumn(
            state = listState,
            contentPadding =
                PaddingValues(
                    top = insets.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding(),
                    bottom = insets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding() + if (bottomAction) 96.dp else 16.dp,
                ),
            modifier = Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal)),
        ) {
            if (playlist == null) {
                item(key = "form") {
                    AnimatedContent(
                        targetState = origin,
                        transitionSpec = {
                            // Forward into a source slides in from the end; back to the sources, from the start.
                            val forward = targetState != null
                            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { width -> if (forward) width / 6 else -width / 6 })
                                .togetherWith(fadeOut(tween(160)))
                        },
                        label = "import origin",
                    ) { current ->
                        when (current) {
                            null -> SourcePicker(error = state.error, onPick = { choose(it) })
                            Origin.LIST -> ListStep(state = state, onLoadList = importer::loadList)
                            else -> LinkStep(origin = current, state = state, onLoadLink = importer::load)
                        }
                    }
                }
            } else {
                item(key = "header") {
                    when (state.phase) {
                        ImportState.Phase.PREVIEW -> PreviewHeader(playlist, onRename = importer::rename)
                        else ->
                            ProgressHeader(
                                state = state,
                                playlist = playlist,
                                onPlay = {
                                    val songs = state.matches.filterNotNull().distinctBy { it.id }
                                    if (songs.isNotEmpty()) {
                                        playerConnection?.playQueue(ListQueue(title = playlist.title, items = songs.map { it.toMediaItem() }))
                                    }
                                },
                                onOpen = { id -> navController.navigate("local_playlist/$id") },
                                onAgain = {
                                    importer.reset()
                                    origin = null
                                },
                            )
                    }
                }
                if (state.phase == ImportState.Phase.DONE) {
                    item(key = "filters") {
                        FilterRow(state = state, total = playlist.tracks.size, filter = filter, onFilter = { filter = it })
                    }
                }
                items(indices, key = { "track_$it" }) { index ->
                    val status = state.statuses.getOrNull(index) ?: TrackStatus.PENDING
                    val canPick = status != TrackStatus.PENDING && state.phase != ImportState.Phase.PREVIEW
                    TrackRow(
                        number = index + 1,
                        track = playlist.tracks[index],
                        match = state.matches.getOrNull(index),
                        status = status,
                        importing = state.phase == ImportState.Phase.IMPORTING,
                        asCard = status == TrackStatus.NOT_FOUND && state.phase == ImportState.Phase.DONE,
                        onClick = if (canPick) ({ pickIndex = index }) else null,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        if (bottomAction && playlist != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, colors.surface, colors.surface)))
                    .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.phase == ImportState.Phase.PREVIEW) {
                    val total = playlist.tracks.size
                    Button(onClick = importer::start, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(pluralStringResource(R.plurals.playlist_import_start_tracks, total, total), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    OutlinedButton(onClick = importer::cancel, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(android.R.string.cancel), modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }

        TopAppBar(
            // A loaded playlist is named in the bar; the forms carry their own large titles, which
            // move up into the bar once scrolled away.
            title = {
                when {
                    state.playlist != null -> Text(stringResource(R.string.playlist_import_title_single))
                    scrolled -> Text(stringResource(R.string.playlist_import_heading))
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = { if (playlist == null && origin != null) choose(null) else navController.navigateUp() },
                    onLongClick = navController::backToMain,
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = colors.surfaceContainer),
            scrollBehavior = scrollBehavior,
        )
    }

    pickIndex?.let { index ->
        val track = playlist?.tracks?.getOrNull(index)
        if (track == null) {
            LaunchedEffect(Unit) { pickIndex = null }
        } else {
            PickSongSheet(
                track = track,
                onPick = { song ->
                    importer.replace(index, song)
                    pickIndex = null
                },
                onDismiss = { pickIndex = null },
            )
        }
    }
}

/** The ways music comes in: from a service by link, as a typed list, or from a playlist file. */
private enum class Origin { YANDEX, SPOTIFY, LIST, FILE }

private val PLAYLIST_FILE_TYPES =
    arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl", "text/csv", "text/comma-separated-values", "application/csv", "text/plain")

private const val MAX_FILE_BYTES = 4L * 1024 * 1024

/** A playlist file's name and text, or null when it cannot be read (or is far too big to be a playlist). */
private suspend fun readPlaylistFile(
    context: Context,
    uri: Uri,
): Pair<String, String>? =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val (name, size) =
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) to (if (cursor.isNull(1)) null else cursor.getLong(1)) else null
                } ?: (null to null)
            if (size != null && size > MAX_FILE_BYTES) return@runCatching null
            val text = resolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return@runCatching null
            (name ?: uri.lastPathSegment ?: "Playlist") to text
        }.onFailure { Timber.tag("PlaylistImport").w(it, "Could not read $uri") }
            .getOrNull()
    }

/** The first screen: where the music comes from, each source a large card, and how a transfer goes. */
@Composable
private fun SourcePicker(
    error: ImportError?,
    onPick: (Origin) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.playlist_import_heading),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            stringResource(R.string.playlist_import_pick_source),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
        )
        error?.let { ErrorNote(errorText(it)) }
        Origin.entries.forEach { origin ->
            SourceCard(origin = origin, onClick = { onPick(origin) })
        }
        Spacer(Modifier.height(14.dp))
        HowItWorks()
    }
}

@Composable
private fun SourceCard(
    origin: Origin,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Cards stand out from the page: lighter than it in a light theme, a step up in a dark one.
    val dark = colors.surface.luminance() < 0.5f
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceBadge(origin, 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(originTitle(origin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        when (origin) {
                            Origin.YANDEX, Origin.SPOTIFY -> R.string.playlist_import_by_link_desc
                            Origin.LIST -> R.string.playlist_import_source_list_desc
                            Origin.FILE -> R.string.playlist_import_source_file_desc
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.navigate_next), contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

/**
 * A source's mark: the services get a letter on a tint of their own colour (not their logos), the
 * list and the file an icon on the theme's tones.
 */
@Composable
private fun SourceBadge(
    origin: Origin,
    size: Dp,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val (background, content) =
        when (origin) {
            Origin.YANDEX -> if (dark) Color(0xFF4A3A00) to Color(0xFFFFE08A) else Color(0xFFFFE9A8) to Color(0xFF5C4300)
            Origin.SPOTIFY -> if (dark) Color(0xFF0F3D22) to Color(0xFF9BE6B4) else Color(0xFFCDEFD8) to Color(0xFF0F4D27)
            Origin.LIST -> colors.primaryContainer to colors.onPrimaryContainer
            Origin.FILE -> colors.tertiaryContainer to colors.onTertiaryContainer
        }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        when (origin) {
            Origin.YANDEX, Origin.SPOTIFY ->
                Text(
                    if (origin == Origin.YANDEX) "Я" else "S",
                    color = content,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
            Origin.LIST -> Icon(painterResource(R.drawable.list), contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
            Origin.FILE -> Icon(painterResource(R.drawable.description), contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
private fun originTitle(origin: Origin): String =
    stringResource(
        when (origin) {
            Origin.YANDEX -> R.string.playlist_import_source_yandex
            Origin.SPOTIFY -> R.string.playlist_import_source_spotify
            Origin.LIST -> R.string.playlist_import_source_list
            Origin.FILE -> R.string.playlist_import_source_file
        },
    )

/** Three steps side by side: the list is read, each track found, a playlist made. */
@Composable
private fun HowItWorks() {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.playlist_import_how_it_works), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.playlist_import_how_1, R.string.playlist_import_how_2, R.string.playlist_import_how_3).forEachIndexed { index, step ->
                    val last = index == 2
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(if (last) colors.primary else colors.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (index + 1).toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (last) colors.onPrimary else colors.primary,
                            )
                        }
                        Text(
                            stringResource(step),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** The large title of a source's form, under its mark. */
@Composable
private fun StepHeader(
    origin: Origin,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        SourceBadge(origin, 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A Yandex Music or Spotify link: the field, the search, and where to find the link in that app. */
@Composable
private fun LinkStep(
    origin: Origin,
    state: ImportState,
    onLoadLink: (String) -> Unit,
) {
    var link by rememberSaveable { mutableStateOf("") }
    val loading = state.phase == ImportState.Phase.LOADING
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val error = state.error?.let { errorText(it) }
    // What the link points to, as soon as it is pasted: the service and whether it is a playlist or an album.
    val recognised = remember(link) { parseImportLink(link) }
    val yandex = origin == Origin.YANDEX

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepHeader(
            origin = origin,
            title = stringResource(if (yandex) R.string.playlist_import_link_title_yandex else R.string.playlist_import_link_title_spotify),
            subtitle = stringResource(R.string.playlist_import_link_subtitle),
        )
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text(stringResource(R.string.playlist_import_link_label)) },
            supportingText =
                when {
                    error != null -> { { Text(error) } }
                    recognised != null -> { { Text(linkKind(recognised), color = colors.primary, fontWeight = FontWeight.Medium) } }
                    else -> null
                },
            isError = error != null,
            singleLine = true,
            enabled = !loading,
            shape = RoundedCornerShape(18.dp),
            leadingIcon = {
                Icon(
                    painterResource(if (recognised != null) R.drawable.check else R.drawable.link),
                    contentDescription = null,
                    tint = if (recognised != null) colors.primary else colors.onSurfaceVariant,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (link.isNotBlank() && !loading) onLoadLink(link) }),
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
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.search), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.playlist_import_find), style = MaterialTheme.typography.titleMedium)
            }
        }
        HintCard(title = stringResource(R.string.playlist_import_how_title)) {
            listOf(
                if (yandex) R.string.playlist_import_step_open_yandex else R.string.playlist_import_step_open_spotify,
                R.string.playlist_import_step_share,
                R.string.playlist_import_step_paste,
            ).forEachIndexed { index, step -> HintStep(number = index + 1, text = stringResource(step)) }
        }
        if (!yandex) {
            Text(
                stringResource(R.string.playlist_import_spotify_limit),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** A list typed or pasted by hand: the playlist's name, its tracks with their count as they go in. */
@Composable
private fun ListStep(
    state: ImportState,
    onLoadList: (String, String) -> Unit,
) {
    var listTitle by rememberSaveable { mutableStateOf("") }
    var listText by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val defaultName = stringResource(R.string.playlist_import_default_name)
    val error = state.error?.let { errorText(it) }
    val count = remember(listText) { parseTrackList(listText).size }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepHeader(
            origin = Origin.LIST,
            title = stringResource(R.string.playlist_import_source_list),
            subtitle = stringResource(R.string.playlist_import_list_subtitle),
        )
        OutlinedTextField(
            value = listTitle,
            onValueChange = { listTitle = it },
            label = { Text(stringResource(R.string.playlist_import_name_label)) },
            placeholder = { Text(defaultName) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = listText,
            onValueChange = { listText = it },
            label = { Text(stringResource(R.string.playlist_import_list_label)) },
            placeholder = { Text(stringResource(R.string.playlist_import_list_hint)) },
            supportingText =
                when {
                    error != null -> { { Text(error) } }
                    count > 0 -> { { Text(pluralStringResource(R.plurals.playlist_import_track_count, count, count), color = colors.primary, fontWeight = FontWeight.Medium) } }
                    else -> null
                },
            isError = error != null,
            minLines = 6,
            maxLines = 14,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onLoadList(listTitle.ifBlank { defaultName }, listText) },
            enabled = count > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(R.string.playlist_import_continue), style = MaterialTheme.typography.titleMedium)
        }
        HintCard(title = stringResource(R.string.playlist_import_list_tip_title)) {
            Text(stringResource(R.string.playlist_import_list_tip), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

/** Why the last attempt failed, above the sources so another one can be picked right away. */
@Composable
private fun ErrorNote(text: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.error), contentDescription = null, tint = colors.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onErrorContainer)
        }
    }
}

@Composable
private fun HintCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun HintStep(
    number: Int,
    text: String,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun linkKind(link: ImportLink): String =
    when (link) {
        is ImportLink.SpotifyPlaylist -> stringResource(R.string.playlist_import_link_playlist, stringResource(R.string.playlist_import_source_spotify))
        is ImportLink.SpotifyAlbum -> stringResource(R.string.playlist_import_link_album, stringResource(R.string.playlist_import_source_spotify))
        is ImportLink.YandexAlbum -> stringResource(R.string.playlist_import_link_album, stringResource(R.string.playlist_import_source_yandex))
        is ImportLink.YandexPlaylist, is ImportLink.YandexUserPlaylist ->
            stringResource(R.string.playlist_import_link_playlist, stringResource(R.string.playlist_import_source_yandex))
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
            ImportError.FILE_UNREADABLE -> R.string.playlist_import_error_file
        },
    )

@Composable
private fun sourceName(source: ImportSource): String =
    stringResource(
        when (source) {
            ImportSource.YANDEX_MUSIC -> R.string.playlist_import_source_yandex
            ImportSource.SPOTIFY -> R.string.playlist_import_source_spotify
            ImportSource.TEXT -> R.string.playlist_import_source_list
            ImportSource.FILE -> R.string.playlist_import_source_file
        },
    )

@Composable
private fun Cover(
    url: String?,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(
                painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2.5f),
            )
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** The playlist as read: cover, source, a name that can be edited in place, and the count. */
@Composable
private fun PreviewHeader(
    playlist: ImportedPlaylist,
    onRename: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var name by remember(playlist.source, playlist.coverUrl) { mutableStateOf(playlist.title) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(playlist.coverUrl, 128.dp, 22.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(sourceName(playlist.source), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Spacer(Modifier.height(2.dp))
                BasicTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onRename(it)
                    },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                val total = playlist.tracks.size
                Text(pluralStringResource(R.plurals.playlist_import_track_count, total, total), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Text(stringResource(R.string.playlist_import_rename_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }
        if (playlist.truncated) NoteCard(stringResource(R.string.playlist_import_truncated))
    }
}

/**
 * The cover inside a ring that fills as tracks are looked up; when the transfer is done the ring
 * closes and the cover gives way to a check mark.
 */
@Composable
private fun CoverRing(
    coverUrl: String?,
    progress: Float,
    done: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val sweep by animateFloatAsState(progress, tween(450), label = "import ring")
    Box(Modifier.size(184.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(colors.surfaceContainerHighest, 0f, 360f, false, topLeft, arcSize, style = stroke)
            drawArc(colors.primary, -90f, 360f * sweep, false, topLeft, arcSize, style = stroke)
        }
        AnimatedContent(
            targetState = done,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), initialScale = 0.6f))
                    .togetherWith(fadeOut(tween(200)))
            },
            label = "import done",
        ) { isDone ->
            if (isDone) {
                Box(Modifier.size(132.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(64.dp))
                }
            } else {
                Cover(coverUrl, 132.dp, 24.dp)
            }
        }
    }
}

@Composable
private fun ProgressHeader(
    state: ImportState,
    playlist: ImportedPlaylist,
    onPlay: () -> Unit,
    onOpen: (String) -> Unit,
    onAgain: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val total = playlist.tracks.size
    val done = state.phase == ImportState.Phase.DONE
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverRing(playlist.coverUrl, if (total == 0) 0f else state.processed.toFloat() / total, done)
        Spacer(Modifier.height(20.dp))
        AnimatedContent(targetState = done, label = "import status") { isDone ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (isDone) {
                    Text(
                        stringResource(R.string.playlist_import_done, state.found, total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.playlist_import_done_where, playlist.title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onPlay, enabled = state.found > 0, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.playlist_import_play), style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(
                            onClick = { state.playlistId?.let(onOpen) },
                            enabled = state.playlistId != null,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text(stringResource(R.string.playlist_import_open_short), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    TextButton(onClick = onAgain) { Text(stringResource(R.string.playlist_import_again)) }
                } else {
                    Text(
                        stringResource(R.string.playlist_import_progress_short, state.processed, total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.playlist_import_searching), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Text(
                        stringResource(R.string.playlist_import_background_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FilterRow(
    state: ImportState,
    total: Int,
    filter: TrackFilter,
    onFilter: (TrackFilter) -> Unit,
) {
    Column {
        // One line that scrolls, so a long count in another language never wraps a chip below.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            FilterChip(
                selected = filter == TrackFilter.ALL,
                onClick = { onFilter(TrackFilter.ALL) },
                label = { Text(stringResource(R.string.playlist_import_filter_all, total)) },
            )
            FilterChip(
                selected = filter == TrackFilter.FOUND,
                onClick = { onFilter(TrackFilter.FOUND) },
                label = { Text(stringResource(R.string.playlist_import_filter_found, state.found)) },
            )
            if (state.found < total) {
                FilterChip(
                    selected = filter == TrackFilter.MISSING,
                    onClick = { onFilter(TrackFilter.MISSING) },
                    label = { Text(stringResource(R.string.playlist_import_filter_missing, total - state.found)) },
                )
            }
        }
        Text(
            stringResource(R.string.playlist_import_replace_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StatusMark(
    status: TrackStatus,
    importing: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.4f)).togetherWith(fadeOut(tween(100)))
        },
        label = "track status",
    ) { current ->
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (current) {
                TrackStatus.FOUND ->
                    Box(Modifier.size(24.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                    }
                TrackStatus.NOT_FOUND ->
                    Icon(painterResource(R.drawable.close), contentDescription = null, tint = colors.error, modifier = Modifier.size(22.dp))
                TrackStatus.PENDING ->
                    if (importing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * One track: its place in the original, cover, title and artists, what YouTube Music gave for it,
 * and whether it was found. A track not found at the end becomes a card offering a manual search.
 */
@Composable
private fun TrackRow(
    number: Int,
    track: ImportedTrack,
    match: SongItem?,
    status: TrackStatus,
    importing: Boolean,
    asCard: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dimmed = status == TrackStatus.NOT_FOUND
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (asCard) 12.dp else 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp),
            )
            Spacer(Modifier.width(8.dp))
            Cover(track.coverUrl ?: match?.thumbnail, 50.dp, 12.dp, Modifier.alpha(if (dimmed) 0.6f else 1f))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (dimmed) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.artists.isNotEmpty()) {
                    Text(
                        track.artists.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (match != null && status == TrackStatus.FOUND) {
                    Text(
                        "→ " + (listOf(match.title) + match.artists.take(2).map { it.name }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (asCard && onClick != null) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.heightIn(min = 36.dp)) {
                        Icon(painterResource(R.drawable.search), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.playlist_import_find_manually))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusMark(status, importing)
        }
    }
    if (asCard) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
            shape = RoundedCornerShape(20.dp),
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) { content() }
    } else {
        Box(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) { content() }
    }
}

/** A search on YouTube Music, started from the track's own name, to pick its song by hand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickSongSheet(
    track: ImportedTrack,
    onPick: (SongItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf((track.artists.take(2) + track.title).joinToString(" ")) }
    var results by remember { mutableStateOf<List<SongItem>?>(null) }
    LaunchedEffect(query) {
        results = null
        if (query.isBlank()) return@LaunchedEffect
        delay(400)
        val found = PlaylistImportService.searchSongs(query)
        // The search swallows cancellation, so a query typed over must not show its stale result.
        ensureActive()
        results = found
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.playlist_import_pick_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.playlist_import_search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val found = results
            when {
                found == null ->
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                found.isEmpty() ->
                    Text(
                        stringResource(R.string.playlist_import_no_results),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                    )
                else ->
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(found, key = { it.id }) { song ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(song) }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Cover(song.thumbnail, 52.dp, 12.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        song.artists.joinToString(", ") { it.name },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
            }
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

