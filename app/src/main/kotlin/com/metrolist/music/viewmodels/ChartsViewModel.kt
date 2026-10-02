/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.ChartsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChartsViewModel @Inject constructor() : ViewModel() {
    private val _chartsPage = MutableStateFlow<ChartsPage?>(null)
    val chartsPage = _chartsPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadCharts() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            val page = YouTube.getChartsPage().onFailure { Timber.w(it, "Could not load the charts") }.getOrNull()
            // Charts are not published in every country; the global chart stands in for them, as on Home.
            _chartsPage.value =
                if (page?.sections?.any { section -> section.items.any { it is SongItem } } == true) {
                    page
                } else {
                    YouTube.playlist(GLOBAL_CHART_PLAYLIST).getOrNull()?.let { global ->
                        ChartsPage(
                            sections = listOf(ChartsPage.ChartSection(global.playlist.title, global.songs, ChartsPage.ChartType.TOP)),
                            continuation = null,
                        )
                    } ?: page
                }
            if (_chartsPage.value == null) _error.value = "Failed to load charts"

            _isLoading.value = false
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            _chartsPage.value?.continuation?.let { continuation ->
                _isLoading.value = true
                YouTube.getChartsPage(continuation)
                    .onSuccess { newPage ->
                        _chartsPage.value = _chartsPage.value?.copy(
                            sections = _chartsPage.value?.sections.orEmpty() + newPage.sections,
                            continuation = newPage.continuation
                        )
                    }
                    .onFailure { e ->
                        _error.value = "Failed to load more: ${e.message}"
                    }
                _isLoading.value = false
            }
        }
    }
}
