package com.justgba.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameTest {
    @Test fun fitKeepsGbaAspectOn1080p() {
        val (w, h) = VideoFrame.size(1920, 1080, 240, 160, 0)
        assertEquals(1620, w)
        assertEquals(1080, h)
    }

    @Test fun fitKeepsGbaAspectOnANarrowScreen() {
        val (w, h) = VideoFrame.size(800, 480, 240, 160, 0)
        assertEquals(720, w)
        assertEquals(480, h)
    }

    @Test fun integerScaleUsesTheLargestWholeFactor() {
        val (w, h) = VideoFrame.size(1920, 1080, 240, 160, 1)
        assertEquals(1440, w)
        assertEquals(960, h)
    }

    @Test fun fixedScaleStaysInsideTheView() {
        val (w, h) = VideoFrame.size(800, 480, 240, 160, 5)
        assertEquals(720, w)
        assertEquals(480, h)
        assertTrue(w <= 800 && h <= 480)
    }

    @Test fun twoTimesDoublesTheGbaFrame() {
        val (w, h) = VideoFrame.size(1920, 1080, 240, 160, 2)
        assertEquals(480, w)
        assertEquals(320, h)
    }

    @Test fun sixAndSevenMatchIntegerOn1080p() {
        val integer = VideoFrame.size(1920, 1080, 240, 160, 1)
        assertEquals(1440 to 960, integer)
        assertEquals(integer, VideoFrame.size(1920, 1080, 240, 160, 6))
        assertEquals(integer, VideoFrame.size(1920, 1080, 240, 160, 7))
    }
}
