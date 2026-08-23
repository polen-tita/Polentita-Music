package com.polentita.music.core.di

import android.content.Context
import androidx.room.Room
import com.polentita.music.core.database.AlbumDao
import com.polentita.music.core.database.BackupDao
import com.polentita.music.core.database.DownloadDao
import com.polentita.music.core.database.DatabaseMigrations
import com.polentita.music.core.database.PlaybackHistoryDao
import com.polentita.music.core.database.PlaylistDao
import com.polentita.music.core.database.PolentitaDatabase
import com.polentita.music.core.database.SongDao
import com.polentita.music.core.database.RemoteReferenceDao
import com.polentita.music.core.database.PlaylistImportDao
import com.polentita.music.data.repository.DefaultMusicRepository
import com.polentita.music.data.artwork.DefaultArtworkEditingRepository
import com.polentita.music.data.artwork.DefaultArtworkSearchRepository
import com.polentita.music.data.artwork.DefaultManagedArtworkStore
import com.polentita.music.data.artwork.ManagedArtworkStore
import com.polentita.music.core.network.AndroidNetworkAccessPolicy
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.domain.repository.MusicRepository
import com.polentita.music.domain.artwork.ArtworkEditingRepository
import com.polentita.music.domain.artwork.ArtworkSearchRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PolentitaDatabase =
        Room.databaseBuilder(context, PolentitaDatabase::class.java, "polentita.db")
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.MIGRATION_2_3,
                DatabaseMigrations.MIGRATION_3_4,
            )
            .build()

    @Provides fun songDao(db: PolentitaDatabase): SongDao = db.songDao()
    @Provides fun albumDao(db: PolentitaDatabase): AlbumDao = db.albumDao()
    @Provides fun playlistDao(db: PolentitaDatabase): PlaylistDao = db.playlistDao()
    @Provides fun downloadDao(db: PolentitaDatabase): DownloadDao = db.downloadDao()
    @Provides fun historyDao(db: PolentitaDatabase): PlaybackHistoryDao = db.historyDao()
    @Provides fun backupDao(db: PolentitaDatabase): BackupDao = db.backupDao()
    @Provides fun remoteReferenceDao(db: PolentitaDatabase): RemoteReferenceDao = db.remoteReferenceDao()
    @Provides fun playlistImportDao(db: PolentitaDatabase): PlaylistImportDao = db.playlistImportDao()

    @Provides
    @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun musicRepository(implementation: DefaultMusicRepository): MusicRepository

    @Binds
    @Singleton
    abstract fun artworkEditingRepository(
        implementation: DefaultArtworkEditingRepository,
    ): ArtworkEditingRepository

    @Binds
    @Singleton
    abstract fun artworkSearchRepository(
        implementation: DefaultArtworkSearchRepository,
    ): ArtworkSearchRepository

    @Binds
    @Singleton
    abstract fun managedArtworkStore(implementation: DefaultManagedArtworkStore): ManagedArtworkStore

    @Binds
    @Singleton
    abstract fun networkAccessPolicy(implementation: AndroidNetworkAccessPolicy): NetworkAccessPolicy
}
