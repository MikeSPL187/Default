/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import com.metrolist.music.utils.LanguageSwitcher
import androidx.activity.compose.LocalActivity
import android.widget.Toast
import com.metrolist.music.ui.component.TITLE_SCROLL_PX
import com.metrolist.music.ui.component.LargeScreenTitle
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.metrolist.music.utils.clearNotRecommended
import com.metrolist.music.ui.menu.rememberNotRecommended
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AddToPlaylistPosition
import com.metrolist.music.constants.AddToPlaylistPositionKey
import com.metrolist.music.constants.ContentCountryKey
import com.metrolist.music.constants.ContentLanguageKey
import com.metrolist.music.constants.CountryCodeToName
import com.metrolist.music.constants.EnableBetterLyricsKey
import com.metrolist.music.constants.EnableKugouKey
import com.metrolist.music.constants.EnableLrcLibKey
import com.metrolist.music.constants.EnablePaxsenixKey
import com.metrolist.music.constants.EnableLyricsPlus
import com.metrolist.music.constants.EnableZemerKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.LanguageCodeToName
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.constants.PreferSyncedLyricsKey
import com.metrolist.music.constants.ShareYouTubeLinksKey
import com.metrolist.music.constants.ProxyEnabledKey
import com.metrolist.music.constants.ProxyPasswordKey
import com.metrolist.music.constants.ProxyTypeKey
import com.metrolist.music.constants.ProxyUrlKey
import com.metrolist.music.constants.ProxyUsernameKey
import com.metrolist.music.constants.QuickPicks
import com.metrolist.music.constants.QuickPicksKey
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.constants.ShowArtistDescriptionKey
import com.metrolist.music.constants.ShowMostStatsPlaylistsKey
import com.metrolist.music.constants.ShowArtistSubscriberCountKey
import com.metrolist.music.constants.ShowMonthlyListenersKey
import com.metrolist.music.constants.TopSize
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.DraggableLyricsProviderItem
import com.metrolist.music.ui.component.DraggableLyricsProviderList
import com.metrolist.music.lyrics.LyricsProviderRegistry
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import java.net.Proxy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    // Used only before Android 13
    val activity = LocalActivity.current
    val coroutineScope = rememberCoroutineScope()
    val currentAppLanguage = remember { LanguageSwitcher.current(context) }

    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (hideVideoSongs, onHideVideoSongsChange) = rememberPreference(key = HideVideoSongsKey, defaultValue = false)
    val (hideYoutubeShorts, onHideYoutubeShortsChange) = rememberPreference(key = HideYoutubeShortsKey, defaultValue = false)
    val notRecommended by rememberNotRecommended()
    var showResetNotRecommendedDialog by rememberSaveable { mutableStateOf(false) }
    val notRecommendedScope = rememberCoroutineScope()

    if (showResetNotRecommendedDialog) {
        AlertDialog(
            onDismissRequest = { showResetNotRecommendedDialog = false },
            title = { Text(stringResource(R.string.not_recommended_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.not_recommended_summary,
                        notRecommended.songIds.size,
                        notRecommended.artistIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetNotRecommendedDialog = false
                        notRecommendedScope.launch {
                            context.clearNotRecommended()
                            Toast.makeText(context, R.string.not_recommended_cleared, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetNotRecommendedDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    val (showArtistDescription, onShowArtistDescriptionChange) = rememberPreference(key = ShowArtistDescriptionKey, defaultValue = true)
    val (showArtistSubscriberCount, onShowArtistSubscriberCountChange) = rememberPreference(key = ShowArtistSubscriberCountKey, defaultValue = true)
    val (showMonthlyListeners, onShowMonthlyListenersChange) = rememberPreference(key = ShowMonthlyListenersKey, defaultValue = true)
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "")
    val (proxyUsername, onProxyUsernameChange) = rememberPreference(key = ProxyUsernameKey, defaultValue = "")
    val (proxyPassword, onProxyPasswordChange) = rememberPreference(key = ProxyPasswordKey, defaultValue = "")
    val (enableZemer, onEnableZemerChange) = rememberPreference(key = EnableZemerKey, defaultValue = true)
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(key = EnableBetterLyricsKey, defaultValue = true)
    val (enablePaxsenix, onEnablePaxsenixChange) = rememberPreference(key = EnablePaxsenixKey, defaultValue = true)
    val (enableLyricsPlus, onEnableLyricsPlusChange) = rememberPreference(key = EnableLyricsPlus, defaultValue = true)
    val (lyricsProviderOrder, onLyricsProviderOrderChange) = rememberPreference(
        key = LyricsProviderOrderKey,
        defaultValue = LyricsProviderRegistry.serializeProviderOrder(LyricsProviderRegistry.getDefaultProviderOrder())
    )
    val (preferSyncedLyrics, onPreferSyncedLyricsChange) = rememberPreference(key = PreferSyncedLyricsKey, defaultValue = true)
    val (shareYouTubeLinks, onShareYouTubeLinksChange) = rememberPreference(key = ShareYouTubeLinksKey, defaultValue = false)
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)
    val (showMostStatsPlaylists, onShowMostStatsPlaylistsChange) =
        rememberPreference(key = ShowMostStatsPlaylistsKey, defaultValue = true)
    val (addToPlaylistPosition, onAddToPlaylistPositionChange) = rememberEnumPreference(
        AddToPlaylistPositionKey,
        AddToPlaylistPosition.BEGINNING,
    )

    LaunchedEffect(showMostStatsPlaylists) {
        if (!showMostStatsPlaylists) {
            database.withTransaction {
                clearPlaylist(com.metrolist.music.db.entities.PlaylistEntity.WEEKLY_MOST_PLAYLIST_ID)
                clearPlaylist(com.metrolist.music.db.entities.PlaylistEntity.MONTHLY_MOST_PLAYLIST_ID)
                delete(
                    com.metrolist.music.db.entities.PlaylistEntity(
                        id = com.metrolist.music.db.entities.PlaylistEntity.WEEKLY_MOST_PLAYLIST_ID,
                        name = "",
                    ),
                )
                delete(
                    com.metrolist.music.db.entities.PlaylistEntity(
                        id = com.metrolist.music.db.entities.PlaylistEntity.MONTHLY_MOST_PLAYLIST_ID,
                        name = "",
                    ),
                )
            }
        }
    }

    val providerDisplayNames =
        mapOf(
            "BetterLyrics" to "Better Lyrics",
            "Paxsenix" to "Paxsenix",
            "LrcLib" to "LrcLib",
            "KuGou" to "KuGou",
            "LyricsPlus" to "LyricsPlus",
            "Zemer" to "Zemer",
            "YouTubeSubtitle" to "YouTube Subtitles",
            "YouTube" to "YouTube",
        )

    var showProxyConfigurationDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showProxyConfigurationDialog) {
        var expandedDropdown by remember { mutableStateOf(false) }

        var tempProxyUrl by rememberSaveable { mutableStateOf(proxyUrl) }
        var tempProxyUsername by rememberSaveable { mutableStateOf(proxyUsername) }
        var tempProxyPassword by rememberSaveable { mutableStateOf(proxyPassword) }
        var authEnabled by rememberSaveable { mutableStateOf(proxyUsername.isNotBlank() || proxyPassword.isNotBlank()) }

        AlertDialog(
            onDismissRequest = { showProxyConfigurationDialog = false },
            title = {
                Text(stringResource(R.string.config_proxy))
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expandedDropdown,
                        onExpandedChange = { expandedDropdown = !expandedDropdown },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = proxyType.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.proxy_type)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS).forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        onProxyTypeChange(type)
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = tempProxyUrl,
                        onValueChange = { tempProxyUrl = it },
                        label = { Text(stringResource(R.string.proxy_url)) },
                        placeholder = { Text("host:port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.enable_authentication))
                        Switch(
                            checked = authEnabled,
                            onCheckedChange = {
                                authEnabled = it
                                if (!it) {
                                    tempProxyUsername = ""
                                    tempProxyPassword = ""
                                }
                            }
                        )
                    }

                    AnimatedVisibility(visible = authEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempProxyUsername,
                                onValueChange = { tempProxyUsername = it },
                                label = { Text(stringResource(R.string.proxy_username)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tempProxyPassword,
                                onValueChange = { tempProxyPassword = it },
                                label = { Text(stringResource(R.string.proxy_password)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onProxyUrlChange(tempProxyUrl)
                        onProxyUsernameChange(if (authEnabled) tempProxyUsername else "")
                        onProxyPasswordChange(if (authEnabled) tempProxyPassword else "")
                        showProxyConfigurationDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProxyConfigurationDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    var showContentLanguageDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showContentLanguageDialog) {
        EnumDialog(
            onDismiss = { showContentLanguageDialog = false },
            onSelect = {
                onContentLanguageChange(it)
                showContentLanguageDialog = false
            },
            title = stringResource(R.string.content_language),
            current = contentLanguage,
            values = (listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList()),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showContentCountryDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showContentCountryDialog) {
        EnumDialog(
            onDismiss = { showContentCountryDialog = false },
            onSelect = {
                onContentCountryChange(it)
                showContentCountryDialog = false
            },
            title = stringResource(R.string.content_country),
            current = contentCountry,
            values = (listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList()),
            valueText = {
                CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showAppLanguageDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showAppLanguageDialog) {
        EnumDialog(
            onDismiss = { showAppLanguageDialog = false },
            onSelect = { tag ->
                showAppLanguageDialog = false
                // Switched in place, with the screen fading out and back in the new language.
                activity?.let { coroutineScope.launch { LanguageSwitcher.switch(it, tag) } }
            },
            title = stringResource(R.string.app_language),
            current = currentAppLanguage,
            values = (listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList()),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showProviderSelectionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showProviderSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showProviderSelectionDialog = false },
            title = { Text(stringResource(R.string.lyrics_provider_selection)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProviderSwitchRow(
                        title = stringResource(R.string.enable_lrclib),
                        description = stringResource(R.string.enable_lrclib_desc),
                        checked = enableLrclib,
                        onCheckedChange = onEnableLrclibChange,
                    )
                    ProviderSwitchRow(
                        title = stringResource(R.string.enable_kugou),
                        description = stringResource(R.string.enable_kugou_desc),
                        checked = enableKugou,
                        onCheckedChange = onEnableKugouChange,
                    )
                    ProviderSwitchRow(
                        title = stringResource(R.string.enable_better_lyrics),
                        description = stringResource(R.string.enable_better_lyrics_desc),
                        checked = enableBetterLyrics,
                        onCheckedChange = onEnableBetterLyricsChange,
                    )
                    ProviderSwitchRow(
                        title = stringResource(R.string.enable_paxsenix),
                        description = stringResource(R.string.enable_paxsenix_desc),
                        checked = enablePaxsenix,
                        onCheckedChange = onEnablePaxsenixChange,
                    )
                    ProviderSwitchRow(
                        title = stringResource(R.string.enable_lyricsplus),
                        description = stringResource(R.string.enable_lyricsplus_desc),
                        checked = enableLyricsPlus,
                        onCheckedChange = onEnableLyricsPlusChange,
                    )
                    ProviderSwitchRow(
                        title = stringResource(R.string.enable_zemer),
                        description = stringResource(R.string.enable_zemer_desc),
                        checked = enableZemer,
                        onCheckedChange = onEnableZemerChange,
                    )
                    Column(modifier = Modifier.padding(2.dp)) {
                        Text(
                            text = stringResource(R.string.youtube_music_lyrics_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showProviderSelectionDialog = false }
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    var showQuickPicksDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showQuickPicksDialog) {
        EnumDialog(
            onDismiss = { showQuickPicksDialog = false },
            onSelect = {
                onQuickPicksChange(it)
                showQuickPicksDialog = false
            },
            title = stringResource(R.string.set_quick_picks),
            current = quickPicks,
            values = QuickPicks.values().toList(),
            valueText = {
                when (it) {
                    QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                    QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                }
            }
        )
    }

    var showAddToPlaylistPositionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showAddToPlaylistPositionDialog) {
        EnumDialog(
            onDismiss = { showAddToPlaylistPositionDialog = false },
            onSelect = {
                onAddToPlaylistPositionChange(it)
                showAddToPlaylistPositionDialog = false
            },
            title = stringResource(R.string.add_to_playlist_position),
            current = addToPlaylistPosition,
            values = AddToPlaylistPosition.entries,
            valueText = {
                when (it) {
                    AddToPlaylistPosition.BEGINNING -> stringResource(R.string.playlist_position_beginning)
                    AddToPlaylistPosition.END -> stringResource(R.string.playlist_position_end)
                }
            },
            valueDescription = {
                when (it) {
                    AddToPlaylistPosition.BEGINNING -> stringResource(R.string.playlist_position_beginning_desc)
                    AddToPlaylistPosition.END -> stringResource(R.string.playlist_position_end_desc)
                }
            },
        )
    }

    var showTopLengthDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showTopLengthDialog) {
        var tempLength by rememberSaveable { mutableFloatStateOf(lengthTop.toFloat()) }

        AlertDialog(
            onDismissRequest = { showTopLengthDialog = false },
            title = { Text(stringResource(R.string.top_length)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(tempLength.toInt().toString())
                    Slider(
                        value = tempLength,
                        onValueChange = { tempLength = it },
                        valueRange = 1f..100f,
                        steps = 98
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLengthTopChange(tempLength.toInt().toString())
                        showTopLengthDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        )
    }

    var showProviderPriorityDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showProviderPriorityDialog) {
        val currentOrder = LyricsProviderRegistry.deserializeProviderOrder(lyricsProviderOrder)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val normalizedOrder = currentOrder.filter { it in defaultOrder } +
            defaultOrder.filter { it !in currentOrder }

        val enabledProviders = setOf(
            "LrcLib".takeIf { enableLrclib },
            "KuGou".takeIf { enableKugou },
            "BetterLyrics".takeIf { enableBetterLyrics },
            "Paxsenix".takeIf { enablePaxsenix },
            "LyricsPlus".takeIf { enableLyricsPlus },
            "Zemer".takeIf { enableZemer },
        ).filterNotNull().toSet()
        val lyricsIcon = painterResource(R.drawable.lyrics)
        val draggableItems = remember { mutableStateListOf<DraggableLyricsProviderItem>() }

        LaunchedEffect(normalizedOrder, enableLrclib, enableKugou, enableBetterLyrics, enablePaxsenix, enableLyricsPlus, enableZemer) {
            val orderedEnabledProviders = normalizedOrder.filter { it in enabledProviders }
            draggableItems.clear()
            draggableItems.addAll(
                orderedEnabledProviders.mapNotNull { providerName ->
                    LyricsProviderRegistry.getProviderByName(providerName) ?: return@mapNotNull null
                    DraggableLyricsProviderItem(
                        id = providerName,
                        name = providerDisplayNames[providerName] ?: providerName,
                        icon = lyricsIcon,
                    )
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showProviderPriorityDialog = false },
            title = { Text(stringResource(R.string.lyrics_provider_priority)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    Text(
                        stringResource(R.string.lyrics_provider_priority_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DraggableLyricsProviderList(
                        items = draggableItems,
                        onItemsReordered = { reorderedItems ->
                            val enabledOrder = reorderedItems.map { it.id }
                            val disabledOrder = normalizedOrder.filter { it !in enabledProviders }
                            onLyricsProviderOrderChange(
                                LyricsProviderRegistry.serializeProviderOrder(enabledOrder + disabledOrder)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showProviderPriorityDialog = false }
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    val scrollState = rememberScrollState()
    val titleInBar by remember { derivedStateOf { scrollState.value > TITLE_SCROLL_PX } }
    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        LargeScreenTitle(stringResource(R.string.content), horizontalPadding = 4.dp)
        Material3SettingsGroup(
            title = stringResource(R.string.general),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.content_language)) },
                    description = {
                        Text(
                            LanguageCodeToName.getOrElse(contentLanguage) { stringResource(R.string.system_default) }
                        )
                    },
                    onClick = { showContentLanguageDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.location_on),
                    title = { Text(stringResource(R.string.content_country)) },
                    description = {
                        Text(
                            CountryCodeToName.getOrElse(contentCountry) { stringResource(R.string.system_default) }
                        )
                    },
                    onClick = { showContentCountryDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.explicit),
                    title = { Text(stringResource(R.string.hide_explicit)) },
                    trailingContent = {
                        Switch(
                            checked = hideExplicit,
                            onCheckedChange = onHideExplicitChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (hideExplicit) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onHideExplicitChange(!hideExplicit) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.slow_motion_video),
                    title = { Text(stringResource(R.string.hide_video_songs)) },
                    trailingContent = {
                        Switch(
                            checked = hideVideoSongs,
                            onCheckedChange = onHideVideoSongsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (hideVideoSongs) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onHideVideoSongsChange(!hideVideoSongs) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.hide_image),
                    title = { Text(stringResource(R.string.hide_youtube_shorts)) },
                    trailingContent = {
                        Switch(
                            checked = hideYoutubeShorts,
                            onCheckedChange = onHideYoutubeShortsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (hideYoutubeShorts) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onHideYoutubeShortsChange(!hideYoutubeShorts) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.not_recommended),
                    title = { Text(stringResource(R.string.not_recommended_title)) },
                    description = {
                        Text(
                            if (notRecommended.isEmpty) {
                                stringResource(R.string.not_recommended_empty)
                            } else {
                                stringResource(
                                    R.string.not_recommended_summary,
                                    notRecommended.songIds.size,
                                    notRecommended.artistIds.size,
                                )
                            },
                        )
                    },
                    enabled = !notRecommended.isEmpty,
                    onClick = { showResetNotRecommendedDialog = true },
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.playlists),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.playlist_add),
                    title = { Text(stringResource(R.string.add_to_playlist_position)) },
                    description = {
                        Text(
                            when (addToPlaylistPosition) {
                                AddToPlaylistPosition.BEGINNING -> stringResource(R.string.playlist_position_beginning)
                                AddToPlaylistPosition.END -> stringResource(R.string.playlist_position_end)
                            }
                        )
                    },
                    onClick = { showAddToPlaylistPositionDialog = true },
                )
            ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.artist_page_settings),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.show_artist_description)) },
                    trailingContent = {
                        Switch(
                            checked = showArtistDescription,
                            onCheckedChange = onShowArtistDescriptionChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showArtistDescription) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowArtistDescriptionChange(!showArtistDescription) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.group),
                    title = { Text(stringResource(R.string.show_artist_subscriber_count)) },
                    trailingContent = {
                        Switch(
                            checked = showArtistSubscriberCount,
                            onCheckedChange = onShowArtistSubscriberCountChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showArtistSubscriberCount) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowArtistSubscriberCountChange(!showArtistSubscriberCount) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.headphones),
                    title = { Text(stringResource(R.string.show_artist_monthly_listeners)) },
                    trailingContent = {
                        Switch(
                            checked = showMonthlyListeners,
                            onCheckedChange = onShowMonthlyListenersChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showMonthlyListeners) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowMonthlyListenersChange(!showMonthlyListeners) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.app_language),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text(stringResource(R.string.app_language)) },
                    description = {
                        Text(
                            LanguageCodeToName.getOrElse(currentAppLanguage) { stringResource(R.string.system_default) }
                        )
                    },
                    onClick = { showAppLanguageDialog = true }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.proxy),
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.wifi_proxy),
                        title = { Text(stringResource(R.string.enable_proxy)) },
                        trailingContent = {
                            Switch(
                                checked = proxyEnabled,
                                onCheckedChange = onProxyEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (proxyEnabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onProxyEnabledChange(!proxyEnabled) }
                    )
                )
                if (proxyEnabled) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.settings),
                            title = { Text(stringResource(R.string.config_proxy)) },
                            onClick = { showProxyConfigurationDialog = true }
                        )
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.lyrics),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_provider_selection)) },
                    description = { Text(stringResource(R.string.lyrics_provider_selection_desc)) },
                    onClick = { showProviderSelectionDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.sort),
                    title = { Text(stringResource(R.string.lyrics_provider_priority)) },
                    description = { Text(stringResource(R.string.lyrics_provider_priority_desc)) },
                    onClick = { showProviderPriorityDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.timer),
                    title = { Text(stringResource(R.string.prefer_synced_lyrics)) },
                    description = { Text(stringResource(R.string.prefer_synced_lyrics_desc)) },
                    trailingContent = {
                        Switch(
                            checked = preferSyncedLyrics,
                            onCheckedChange = onPreferSyncedLyricsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (preferSyncedLyrics) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPreferSyncedLyricsChange(!preferSyncedLyrics) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language_korean_latin),
                    title = { Text(stringResource(R.string.lyrics_romanization)) },
                    onClick = { navController.navigate("settings/content/romanization") }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_recap),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.stats),
                    title = { Text(stringResource(R.string.show_most_stats_playlists)) },
                    description = { Text(stringResource(R.string.show_most_stats_playlists_desc)) },
                    trailingContent = {
                        Switch(
                            checked = showMostStatsPlaylists,
                            onCheckedChange = onShowMostStatsPlaylistsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showMostStatsPlaylists) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowMostStatsPlaylistsChange(!showMostStatsPlaylists) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.misc),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.share),
                    title = { Text(stringResource(R.string.share_youtube_links)) },
                    description = { Text(stringResource(R.string.share_youtube_links_desc)) },
                    trailingContent = {
                        Switch(
                            checked = shareYouTubeLinks,
                            onCheckedChange = onShareYouTubeLinksChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (shareYouTubeLinks) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShareYouTubeLinksChange(!shareYouTubeLinks) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.trending_up),
                    title = { Text(stringResource(R.string.top_length)) },
                    description = { Text(lengthTop) },
                    onClick = { showTopLengthDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.home_outlined),
                    title = { Text(stringResource(R.string.set_quick_picks)) },
                    description = {
                        Text(
                            when (quickPicks) {
                                QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                                QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                            }
                        )
                    },
                    onClick = { showQuickPicksDialog = true }
                )
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { if (titleInBar) Text(stringResource(R.string.content)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back),
                )
            }
        }
    )
}

/** A provider on/off row; the whole row toggles, and reads as one switch to TalkBack. */
@Composable
private fun ProviderSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = {
                Icon(
                    painter = painterResource(if (checked) R.drawable.check else R.drawable.close),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }
}
