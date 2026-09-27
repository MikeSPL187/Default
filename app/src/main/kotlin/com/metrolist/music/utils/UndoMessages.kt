package com.metrolist.music.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Confirmations from menus that close before they could show one; the main screen shows them with "Undo". */
object UndoMessages {
    data class Message(
        val text: String,
        val undo: (() -> Unit)?,
    )

    private val messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)
    val events: SharedFlow<Message> = messages.asSharedFlow()

    fun post(
        text: String,
        undo: (() -> Unit)? = null,
    ) {
        messages.tryEmit(Message(text, undo))
    }
}
