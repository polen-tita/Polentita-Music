package com.polentita.music.data.repository

import com.polentita.music.core.database.RemoteReferenceDao
import com.polentita.music.core.database.RemoteReferenceEntity
import com.polentita.music.domain.provider.RemoteTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RemoteReferenceRepository @Inject constructor(
    private val dao: RemoteReferenceDao,
) {
    fun observeAll(): Flow<List<RemoteReferenceEntity>> = dao.observeAll()

    fun observeSavedKeys(): Flow<Set<String>> = observeAll().map { references ->
        references.mapTo(mutableSetOf()) { key(it.providerId, it.remoteTrackId) }
    }

    suspend fun save(track: RemoteTrack) {
        val externalUrl = requireNotNull(track.externalUrl) {
            "Este resultado no tiene un enlace externo para guardar"
        }
        dao.upsert(
            RemoteReferenceEntity(
                providerId = track.providerId,
                remoteTrackId = track.id,
                title = track.title,
                artist = track.artist.name,
                album = track.album.name,
                durationMs = track.durationMs,
                thumbnailUrl = track.coverUri,
                externalUrl = externalUrl,
                license = track.license.name,
                attribution = track.attribution?.text,
            ),
        )
    }

    suspend fun remove(track: RemoteTrack) {
        dao.delete(track.providerId, track.id)
    }

    suspend fun remove(providerId: String, remoteTrackId: String) {
        dao.delete(providerId, remoteTrackId)
    }

    companion object {
        fun key(providerId: String, remoteTrackId: String) = "$providerId:$remoteTrackId"
    }
}
