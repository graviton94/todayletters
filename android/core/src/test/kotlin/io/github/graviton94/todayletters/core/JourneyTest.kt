package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.github.graviton94.todayletters.core.Journey.Step

/** 오늘의 여정 · 건너뛰기 · 튜토리얼 · 자유 연습 기록 (흐름 개편). */
class JourneyTest {
    private val all = listOf(ReplyMode.MATCH, ReplyMode.ALOUD, ReplyMode.CONSTELLATION)

    /** “12개 맞혔는데 3/5” 이 다시 없도록: 복습은 이 판에서 실제로 답한 수 / 이 판의 문제 수로만 센다. */
    @Test fun reviewCountsOnlyThisSession() {
        assertEquals(3 to 12, Journey.reviewCount(total = 12, answered = 3, finished = false))
        assertEquals(12 to 12, Journey.reviewCount(total = 12, answered = 3, finished = true))
        // 답한 수가 판의 문제 수를 넘지 않는다
        assertEquals(5 to 5, Journey.reviewCount(total = 5, answered = 12, finished = false))
        assertEquals(0 to 0, Journey.reviewCount(total = 0, answered = 0, finished = false))
        assertEquals(Step.NONE, Journey.review(total = 0, answered = 0, finished = false))
        assertEquals(Step.TODO, Journey.review(total = 4, answered = 0, finished = false))
        assertEquals(Step.NOW, Journey.review(total = 4, answered = 2, finished = false))
        assertEquals(Step.DONE, Journey.review(total = 4, answered = 4, finished = true))
    }

    @Test fun journeyCompletesOnlyWithRealWork() {
        // 할 것이 없는 날: 완료가 아니다 (아무것도 안 했으니 보너스도 없다)
        assertFalse(Journey.complete(Step.NONE, Step.NONE))
        // 첫날: 편지만 마쳤고 복습할 카드가 아직 없음 → 완료
        assertTrue(Journey.complete(Step.DONE, Step.NONE))
        // 편지는 끝났는데 복습이 남음 → 아직
        assertFalse(Journey.complete(Step.DONE, Step.TODO))
        assertFalse(Journey.complete(Step.DONE, Step.NOW))
        assertTrue(Journey.complete(Step.DONE, Step.DONE))
        // 오늘 올 편지가 없고 복습만 했다 → 완료
        assertTrue(Journey.complete(Step.NONE, Step.DONE))
        assertFalse(Journey.complete(Step.NOW, Step.DONE))
        // 보너스는 하루 한 번
        assertTrue(Journey.payable(Step.DONE, Step.DONE, paidDay = 9, today = 10))
        assertFalse(Journey.payable(Step.DONE, Step.DONE, paidDay = 10, today = 10))
        assertEquals(3, Journey.doneCount(Step.DONE, Step.DONE, paid = true))
        assertEquals(3, Journey.doneCount(Step.DONE, Step.NONE, paid = true))
        assertEquals(1, Journey.doneCount(Step.NOW, Step.NONE, paid = false))
        assertEquals(0, Journey.doneCount(Step.NONE, Step.NONE, paid = false))
    }

    @Test fun letterStep() {
        assertEquals(Step.DONE, Journey.letter(doneToday = true, available = false, started = false))
        assertEquals(Step.TODO, Journey.letter(doneToday = false, available = true, started = false))
        assertEquals(Step.NOW, Journey.letter(doneToday = false, available = true, started = true))
        assertEquals(Step.NONE, Journey.letter(doneToday = false, available = false, started = false))
    }

    /** 건너뛴 걸음은 편지를 마치는 데 한 것으로 치지만 따로 남고, 다시 하면 지워진다. */
    @Test fun skippedStepsCompleteTheLetterAndCanBeRedone() {
        var p = LetterProgress(shown = 4)
        assertEquals(listOf(Step.DONE, Step.NOW, Step.TODO, Step.TODO, Step.TODO), Journey.letterDots(4, 4, all, p))
        p = p.reply(ReplyMode.MATCH).skip(ReplyMode.ALOUD).complete(all)
        assertFalse(p.done)
        assertEquals(listOf(Step.DONE, Step.DONE, Step.SKIPPED, Step.NOW, Step.TODO), Journey.letterDots(4, 4, all, p))
        p = p.skip(ReplyMode.CONSTELLATION).complete(all)
        assertTrue(p.done)
        assertEquals(listOf(ReplyMode.ALOUD, ReplyMode.CONSTELLATION), p.toRedo)
        assertEquals(listOf(Step.DONE, Step.DONE, Step.SKIPPED, Step.SKIPPED, Step.DONE), Journey.letterDots(4, 4, all, p))
        p = p.reply(ReplyMode.ALOUD)
        assertEquals(listOf(ReplyMode.CONSTELLATION), p.toRedo)
        assertTrue(p.complete(all).done)
        // 이미 한 걸음은 건너뛰어도 그대로
        assertEquals(p, p.skip(ReplyMode.MATCH))
        // 읽기 전에는 읽기가 지금
        assertEquals(Step.NOW, Journey.letterDots(1, 4, all, LetterProgress(shown = 1)).first())
    }

    /** 낭독이 없는 편지는 따라 읽기를 하지 않는다 (F11). */
    @Test fun noNarrationNoReadAlong() {
        assertEquals(all, Plays.forLetter(all.toSet(), narrated = true))
        assertEquals(listOf(ReplyMode.MATCH, ReplyMode.CONSTELLATION), Plays.forLetter(all.toSet(), narrated = false))
        assertEquals(listOf(ReplyMode.MATCH), Plays.forLetter(setOf(ReplyMode.MATCH), narrated = true))
    }

    @Test fun reviewOrderIsMeaningBlankDictation() {
        val kinds = listOf(ReviewKind.DICTATION, ReviewKind.SPEAK, ReviewKind.MEANING, ReviewKind.BLANK)
        assertEquals(listOf(ReviewKind.MEANING, ReviewKind.BLANK, ReviewKind.DICTATION, ReviewKind.SPEAK), kinds.sortedBy(Journey::order))
    }

    @Test fun tutorialOnceThenOnlyHelp() {
        assertFalse(Tutorial.done(flag = false, lettersDone = 0))
        assertTrue(Tutorial.done(flag = true, lettersDone = 0))
        // 예전 사용자: 편지를 한 통이라도 마쳤으면 튜토리얼은 끝난 것
        assertTrue(Tutorial.done(flag = false, lettersDone = 1))
        assertTrue(Tutorial.guide("match", done = false, seen = setOf("read")))
        assertFalse(Tutorial.guide("read", done = false, seen = setOf("read")))
        assertFalse(Tutorial.guide("match", done = true, seen = emptySet()))
    }

    /** 자유 연습도 기록된다 (F09): 볼 날이 된 카드는 복습과 같이, 아직이면 맞혀도 일정은 그대로 · 틀리면 내일 다시. */
    @Test fun freePracticeRecords() {
        val due = Card("a", box = 2, due = 10, seen = 3)
        assertEquals(Memory.after(due, true, 10), Memory.practiced(due, true, 10))
        val early = Card("b", box = 2, due = 15, seen = 3)
        val ok = Memory.practiced(early, true, 10)
        assertEquals(2, ok.box); assertEquals(15, ok.due); assertEquals(4, ok.seen); assertEquals(10, ok.last)
        val miss = Memory.practiced(early, false, 10)
        assertEquals(0, miss.box); assertEquals(11, miss.due)
    }
}
