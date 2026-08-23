package com.polentita.music

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.polentita.music.data.playlistimport.PlaylistImportCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PolentitaApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var playlistImportCoordinator: PlaylistImportCoordinator

    override fun onCreate() {
        super.onCreate()
        playlistImportCoordinator.restoreIfNeeded()
    }

    override fun onTerminate() {
        playlistImportCoordinator.shutdownForProcessTermination()
        super.onTerminate()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
