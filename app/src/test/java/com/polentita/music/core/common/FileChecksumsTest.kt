package com.polentita.music.core.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileChecksumsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `sha256 streams file deterministically`() {
        val file = File(temporaryFolder.root, "audio.bin")
        file.writeBytes("polentita".encodeToByteArray())

        assertEquals(
            "ad67b92f3c4cd11f06824e9356e8b122dce4d2b94ad07b63f5f3b914849942e7",
            file.sha256(),
        )
    }
}
