package com.mcc.app.ui

/**
 * 프레임 그림 재생 순서. Android 와 분리해 두어 테스트할 수 있습니다.
 *
 * 그림 4장을 그리면 1→2→3→4→3→2→(1로 반복) 왕복으로 재생해 6장처럼 보입니다.
 */
object FrameSequence {

    /** n장 프레임의 왕복 순서 (0부터). 1장이면 [0], 2장이면 [0,1]. */
    fun order(n: Int): IntArray = when {
        n <= 1 -> intArrayOf(0)
        n == 2 -> intArrayOf(0, 1)
        else -> IntArray(2 * n - 2) { i -> if (i < n) i else 2 * n - 2 - i }
    }

    /** 시작 후 elapsedMs 가 지났을 때 보여줄 프레임 번호 (0부터). */
    fun frameAt(elapsedMs: Long, n: Int, frameMs: Long): Int {
        val seq = order(n)
        val step = (elapsedMs.coerceAtLeast(0) / frameMs.coerceAtLeast(1)) % seq.size
        return seq[step.toInt()]
    }
}
