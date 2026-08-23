package com.polentita.music.core.di

import com.polentita.music.data.extractor.ChaquopyYtDlpExtractor
import com.polentita.music.data.extractor.YtDlpExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {
    @Binds
    @Singleton
    abstract fun ytDlpExtractor(implementation: ChaquopyYtDlpExtractor): YtDlpExtractor
}
