package com.quransunah.app.di

import com.quransunah.app.data.repository.BookmarkRepositoryImpl
import com.quransunah.app.data.repository.MushafRepositoryImpl
import com.quransunah.app.data.repository.SearchRepositoryImpl
import com.quransunah.app.data.repository.TafsirRepositoryImpl
import com.quransunah.app.domain.repository.AudioPlayerRepository
import com.quransunah.app.domain.repository.BookmarkRepository
import com.quransunah.app.domain.repository.MushafRepository
import com.quransunah.app.domain.repository.SearchRepository
import com.quransunah.app.domain.repository.TafsirRepository
import com.quransunah.app.media.AudioController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMushafRepository(impl: MushafRepositoryImpl): MushafRepository

    @Binds
    @Singleton
    abstract fun bindTafsirRepository(impl: TafsirRepositoryImpl): TafsirRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindAudioPlayerRepository(impl: AudioController): AudioPlayerRepository
}
