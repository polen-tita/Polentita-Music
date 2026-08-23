package com.polentita.music.data.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.polentita.music.core.network.RemoteCoverDownloader
import com.polentita.music.core.storage.LibraryStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadedCoverResolverTest {
    @Test
    fun `cover failure returns null and cleans temporary file so audio can continue`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloader = mockk<RemoteCoverDownloader>()
        val storage = mockk<LibraryStorage>(relaxed = true)
        var temporary: File? = null
        coEvery { downloader.download(any(), any()) } coAnswers {
            temporary = secondArg()
            throw IllegalArgumentException("Imagen rechazada")
        }
        val resolver = DownloadedCoverResolver(context, downloader, storage)

        val result = resolver.resolveOrNull(
            "https://images.example.test/cover.jpg",
            "abcdef123456",
        )

        assertNull(result)
        assertFalse(temporary?.exists() == true)
        coVerify(exactly = 0) { storage.storeDownloadedCover(any(), any(), any()) }
    }
}
