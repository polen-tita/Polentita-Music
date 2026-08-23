package com.polentita.music.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.polentita.music.core.network.CoverImageValidator
import com.polentita.music.core.network.DownloadedCover
import com.polentita.music.core.network.NetworkAccessBlockedException
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkAccessState
import com.polentita.music.core.network.NetworkBlockReason
import com.polentita.music.core.network.RemoteCoverDownloader
import com.polentita.music.core.storage.LibraryStorage
import com.polentita.music.domain.artwork.ArtworkCandidate
import com.polentita.music.domain.artwork.ArtworkChoice
import com.polentita.music.domain.artwork.ArtworkSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManagedArtworkStoreTest {
    @Test
    fun `validator detects image contents and rejects a mismatched declared type`() {
        val file = File.createTempFile("cover-validator-", ".part")
        try {
            file.writeBytes(validJpegBytes())

            assertEquals("image/jpeg", CoverImageValidator.validate(file, "image/jpeg"))
            val mismatch = runCatching { CoverImageValidator.validate(file, "image/png") }.exceptionOrNull()
            assertTrue(mismatch?.message.orEmpty().contains("no coincide"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `validator accepts complete png and webp images`() {
        val cases = listOf(
            "image/png" to validImageBytes(Bitmap.CompressFormat.PNG),
            "image/webp" to validWebpBytes(),
        )

        cases.forEach { (mimeType, bytes) ->
            val file = File.createTempFile("cover-supported-", ".part")
            try {
                file.writeBytes(bytes)
                assertEquals(mimeType, CoverImageValidator.validate(file, mimeType))
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun `validator rejects files larger than eight megabytes`() {
        val file = File.createTempFile("cover-too-large-", ".part")
        try {
            RandomAccessFile(file, "rw").use { it.setLength(CoverImageValidator.MAX_COVER_BYTES + 1) }

            val failure = runCatching { CoverImageValidator.validate(file) }.exceptionOrNull()

            assertTrue(failure?.message.orEmpty().contains("8 MB"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `validator rejects a truncated file even when its signature looks like jpeg`() {
        val file = File.createTempFile("cover-truncated-", ".part")
        try {
            file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))

            val failure = runCatching { CoverImageValidator.validate(file, "image/jpeg") }.exceptionOrNull()

            assertTrue(failure?.message.orEmpty().contains("dañada"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `remote covers are content deduplicated before entering Covers`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloader = mockk<RemoteCoverDownloader>()
        val storage = mockk<LibraryStorage>()
        val policy = allowedPolicy()
        val checksums = mutableListOf<String>()
        coEvery { downloader.download(any(), any()) } coAnswers {
            val destination = secondArg<File>()
            destination.writeBytes(validJpegBytes())
            DownloadedCover(destination, "image/jpeg")
        }
        coEvery { storage.storeDownloadedCover(any(), capture(checksums), "image/jpeg") } coAnswers {
            "content://library/Covers/${secondArg<String>()}.jpg"
        }
        val store = DefaultManagedArtworkStore(context, downloader, storage, policy)

        val first = store.prepare(remoteChoice("one"))
        val second = store.prepare(remoteChoice("two"))

        assertEquals(first, second)
        assertEquals(2, checksums.size)
        assertEquals(checksums.first(), checksums.last())
    }

    @Test
    fun `selected content uri is validated and copied into managed Covers`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Uri.parse("content://documents/image/cover")
        val bytes = validJpegBytes()
        Shadows.shadowOf(context.contentResolver).registerInputStream(source, ByteArrayInputStream(bytes))
        val storage = mockk<LibraryStorage>()
        coEvery { storage.storeDownloadedCover(any(), any(), "image/jpeg") } returns
            "content://library/Covers/selected.jpg"
        val store = DefaultManagedArtworkStore(
            context,
            mockk<RemoteCoverDownloader>(),
            storage,
            allowedPolicy(),
        )

        val result = store.prepare(ArtworkChoice.LocalFile(source.toString()))

        assertEquals("content://library/Covers/selected.jpg", result)
        coVerify(exactly = 1) { storage.storeDownloadedCover(any(), any(), "image/jpeg") }
    }

    @Test
    fun `remote save rechecks download policy before network access`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloader = mockk<RemoteCoverDownloader>()
        val storage = mockk<LibraryStorage>(relaxed = true)
        val policy = mockk<NetworkAccessPolicy>()
        coEvery { policy.current() } returns NetworkAccessState(
            connected = true,
            wifiOnlyDownloads = true,
            wifi = false,
            metered = true,
            remoteSearchAllowed = true,
            downloadAllowed = false,
            remoteBlockReason = null,
            downloadBlockReason = NetworkBlockReason.WIFI_REQUIRED,
        )
        val store = DefaultManagedArtworkStore(context, downloader, storage, policy)

        val failure = runCatching { store.prepare(remoteChoice("blocked")) }.exceptionOrNull()

        assertTrue(failure is NetworkAccessBlockedException)
        coVerify(exactly = 0) { downloader.download(any(), any()) }
    }

    @Test
    fun `remote downloader accepts generic binary declaration only when image bytes are valid`() = runTest {
        val client = responseClient("application/octet-stream")
        val destination = File.createTempFile("remote-cover-", ".part")
        destination.delete()

        val result = RemoteCoverDownloader(client).download(
            "https://images.example.test/cover",
            destination,
        )

        assertEquals("image/jpeg", result.mimeType)
        destination.delete()
    }

    @Test
    fun `remote downloader removes a file whose declared image type mismatches its bytes`() = runTest {
        val client = responseClient("image/png")
        val destination = File.createTempFile("remote-cover-mismatch-", ".part")
        destination.delete()

        val failure = runCatching {
            RemoteCoverDownloader(client).download(
                "https://images.example.test/cover",
                destination,
            )
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("no coincide"))
        assertTrue(!destination.exists())
    }

    private fun allowedPolicy() = mockk<NetworkAccessPolicy> {
        coEvery { current() } returns NetworkAccessState(
            connected = true,
            wifi = true,
            metered = false,
            remoteSearchAllowed = true,
            downloadAllowed = true,
            remoteBlockReason = null,
            downloadBlockReason = null,
        )
    }

    private fun remoteChoice(id: String) = ArtworkChoice.Remote(
        ArtworkCandidate(
            id = id,
            source = ArtworkSource.TIDAL,
            title = "Album",
            artist = "Artist",
            imageUrl = "https://images.example.test/$id.jpg",
        ),
    )

    private fun responseClient(contentType: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", contentType)
                .body(
                    validJpegBytes()
                        .toResponseBody(contentType.toMediaType()),
                )
                .build()
        }
        .build()

    private fun validJpegBytes(): ByteArray = validImageBytes(Bitmap.CompressFormat.JPEG)

    private fun validWebpBytes(): ByteArray = Base64.getDecoder().decode(
        "UklGRkoAAABXRUJQVlA4WAoAAAAQAAAAAQAAAQAAQUxQSAUAAAAAnKOjrABWUDggHgAAADABAJ0BKgIAAgACADQlAACGcAD+7Y/H90sIbrFAAA==",
    )

    private fun validImageBytes(format: Bitmap.CompressFormat): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            check(bitmap.compress(format, 90, output))
            output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
