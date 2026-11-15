package com.mcc.app

import com.mcc.app.ui.FrameSequence
import org.junit.Assert.assertEquals
import org.junit.Test

/** 프레임 그림 왕복 재생 순서 */
class FrameSequenceTest {

    @Test
    fun four_frames_play_back_and_forth() {
        assertEquals(listOf(0, 1, 2, 3, 2, 1), FrameSequence.order(4).toList())
    }

    @Test
    fun one_or_two_frames() {
        assertEquals(listOf(0), FrameSequence.order(1).toList())
        assertEquals(listOf(0), FrameSequence.order(0).toList())
        assertEquals(listOf(0, 1), FrameSequence.order(2).toList())
    }

    @Test
    fun frame_changes_every_125ms_and_loops() {
        val seen = (0 until 12).map { FrameSequence.frameAt(it * 125L, 4, 125L) }
        assertEquals(listOf(0, 1, 2, 3, 2, 1, 0, 1, 2, 3, 2, 1), seen)
        assertEquals("같은 125ms 안에서는 같은 프레임", 1, FrameSequence.frameAt(249L, 4, 125L))
        assertEquals("음수 시간도 안전", 0, FrameSequence.frameAt(-500L, 4, 125L))
    }
}
