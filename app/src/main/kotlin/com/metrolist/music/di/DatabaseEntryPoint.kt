package com.metrolist.music.di

import com.metrolist.music.db.MusicDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The database for code Hilt doesn't construct, such as WorkManager workers. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun database(): MusicDatabase
}
