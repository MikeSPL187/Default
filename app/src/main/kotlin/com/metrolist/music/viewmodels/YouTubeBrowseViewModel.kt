/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.pages.BrowseResult
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.read
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")

    val result = MutableStateFlow<BrowseResult?>(null)

    /** The last fetch failed; the screen trades its placeholders for a retry card. */
    val loadFailed = MutableStateFlow(false)

    init {
        load()
    }

    fun retry() {
        loadFailed.value = false
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val hideExplicit = context.dataStore.read(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.read(HideYoutubeShortsKey, false)
            YouTube
                .browse(browseId, params)
                .onSuccess {
                    result.value = it
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
                }.onFailure {
                    reportException(it)
                    loadFailed.value = true
                }
        }
    }
}
