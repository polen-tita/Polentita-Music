package com.polentita.music.playback.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControllerConnectionGateTest {
    @Test
    fun `first reopen can reconnect after disconnected service`() {
        val gate = MediaControllerConnectionGate()

        assertTrue(gate.tryStart(alreadyConnected = false))
        gate.complete()

        assertTrue(gate.tryStart(alreadyConnected = false))
    }

    @Test
    fun `connected controller and active attempt do not create duplicate connections`() {
        val gate = MediaControllerConnectionGate()

        assertFalse(gate.tryStart(alreadyConnected = true))
        assertTrue(gate.tryStart(alreadyConnected = false))
        assertFalse(gate.tryStart(alreadyConnected = false))
    }
}
