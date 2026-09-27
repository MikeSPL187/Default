package com.metrolist.music.ui.screens.home

import kotlinx.coroutines.flow.MutableSharedFlow

/** Requests to Home from outside its screen, such as "Edit Home" from the top bar. */
object HomeEvents {
    val edit = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
