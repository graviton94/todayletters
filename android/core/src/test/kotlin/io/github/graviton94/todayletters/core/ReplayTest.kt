package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 처음부터 다시 보기: 걸음은 다시 할 수 있지만 보상은 처음 마칠 때 한 번만. */
class ReplayTest {
    private val modes = listOf(ReplyMode.MATCH, ReplyMode.ALOUD, ReplyMode.CONSTELLATION)

    @Test fun rewardsOnceOnReplay() {
        // 처음: 낱말 맞추기 · 답장은 하고 따라 읽기는 건너뛰어 편지를 마친다 → 편지 보상
        var saved = LetterProgress(shown = 5).reply(ReplyMode.MATCH).skip(ReplyMode.ALOUD).reply(ReplyMode.CONSTELLATION).complete(modes)
        assertTrue(saved.done)
        assertTrue(Replay.paysLetter(savedDoneBefore = false, replaying = false))
        assertTrue(Replay.can(saved))
        assertFalse(Replay.can(LetterProgress(shown = 2)))

        // 다시 보기: 첫 메시지부터, 걸음도 처음부터
        var again = Replay.start()
        assertEquals(0, again.shown); assertFalse(again.done); assertTrue(again.replied.isEmpty())
        var paid = 0
        modes.forEach { m ->
            val (next, pay) = Replay.stepDone(saved, m)
            saved = next; if (pay) paid++
            again = again.reply(m).complete(modes)
        }
        // 전에 건너뛴 따라 읽기만 처음 한 걸음 → 그 걸음 보상 한 번, 저장된 진도에서 건너뜀 표시가 지워진다
        assertEquals(1, paid)
        assertTrue(ReplyMode.ALOUD in saved.replied)
        assertTrue(saved.toRedo.isEmpty())
        assertTrue(again.done)
        // 다시 보기로 마친 편지: 편지 보상 없음
        assertFalse(Replay.paysLetter(savedDoneBefore = true, replaying = true))
        assertFalse(Replay.paysLetter(savedDoneBefore = false, replaying = true))

        // 두 번째 다시 보기: 보상 없음
        assertEquals(0, modes.count { Replay.stepDone(saved, it).second })
    }
}
