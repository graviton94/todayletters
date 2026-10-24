package io.github.graviton94.todayletters.core

import io.github.graviton94.todayletters.core.TalkTurn.Event
import io.github.graviton94.todayletters.core.TalkTurn.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 오늘의 회화 · 내 차례 상태 기계 ([TalkTurn]). */
class TalkTurnTest {
    private val fr = "Vraiment ? Tu es fatigué du voyage ?"
    private fun run(st: TalkTurn.State, vararg es: Event) = es.fold(st) { s, e -> TalkTurn.reduce(s, e) }

    @Test fun shadowRecordsAtOnceAndPasses() {
        val st = TalkTurn.start(Support.Talk.SHADOW, fr)
        assertTrue(st.canRecord); assertTrue(st.fullLine); assertEquals(fr, st.built)
        val ok = run(st, Event.Heard(true))
        assertEquals(Phase.PASSED, ok.phase); assertTrue(ok.passed)
        assertEquals(Phase.DONE, run(ok, Event.Continue).phase)
        // 칩이 없으니 고르기는 아무 일도 하지 않는다
        assertEquals(st, run(st, Event.Pick(0)))
    }

    @Test fun blankGatesRecordingUntilTheRightChip() {
        val b = Ladder.find(fr, "Tu es fatigué")!!
        val st = TalkTurn.start(Support.Talk.BLANK, fr, b, listOf("Tu as fatigué", "Tu es fatigué", "Tu es fatiguée"))
        assertEquals(Phase.BUILD, st.phase); assertFalse(st.canRecord); assertTrue(st.showsRecord); assertFalse(st.fullLine)
        // 녹음이 막힌 동안의 채점 결과는 무시
        assertEquals(st, run(st, Event.Heard(true)))
        // 틀린 칩: 흔들고 놓이지 않음, 다시 고를 수 있음
        val wrong = run(st, Event.Pick(0))
        assertEquals(0, wrong.rejected); assertEquals(1, wrong.shake); assertFalse(wrong.filled); assertEquals(Phase.BUILD, wrong.phase)
        val wrong2 = run(wrong, Event.Pick(2))
        assertEquals(2, wrong2.rejected); assertFalse(wrong2.canRecord)
        // 맞는 칩: 채워지고 녹음이 열린다. 고르기만으로는 넘어가지 않는다
        val right = run(wrong2, Event.Pick(1))
        assertTrue(right.filled); assertTrue(right.canRecord); assertTrue(right.fullLine); assertNull(right.rejected)
        assertEquals(Phase.READY, right.phase)
        assertEquals(Phase.READY, run(right, Event.Continue).phase)
        assertEquals(Phase.PASSED, run(right, Event.Heard(true)).phase)
    }

    @Test fun blankWithoutAHoleRecordsAtOnce() {
        val st = TalkTurn.start(Support.Talk.BLANK, fr, null, emptyList())
        assertTrue(st.canRecord); assertTrue(st.fullLine)
    }

    @Test fun chipsMustBePlacedInOrder() {
        val st = TalkTurn.start(Support.Talk.CHIPS, fr, seed = 7)
        // 첫 낱말은 놓여 있고, 남은 조각은 문장 차례가 아니다
        assertEquals(1, st.placed); assertEquals("Vraiment ?", st.built)
        assertNotEquals(listOf("Tu", "es", "fatigué", "du", "voyage"), st.chips)
        assertEquals(listOf("du", "es", "fatigué", "Tu", "voyage").map(TalkTurn::key).sorted(), st.chips.map(TalkTurn::key).sorted())
        assertFalse(st.canRecord); assertEquals(Phase.BUILD, st.phase)
        // 다음이 아닌 조각: 튕김
        val bad = st.chips.indexOf("voyage")
        val r1 = run(st, Event.Pick(bad))
        assertEquals(bad, r1.rejected); assertEquals(1, r1.placed); assertEquals(1, r1.shake)
        // 힌트: 다음 조각 (Tu) 을 밝힌다
        val h = run(r1, Event.Hint)
        assertEquals("Tu", h.chips[h.hinted!!])
        // 차례대로 쌓기
        var s = h
        listOf("Tu", "es", "fatigué", "du").forEach { w -> s = run(s, Event.Pick(s.chips.indexOf(w))) }
        assertEquals("Vraiment ? Tu es fatigué du", s.built); assertFalse(s.canRecord)
        // 이미 쓴 조각은 다시 못 쓴다
        assertEquals(s, run(s, Event.Pick(s.chips.indexOf("Tu"))))
        s = run(s, Event.Pick(s.chips.indexOf("voyage")))
        assertTrue(s.canRecord); assertTrue(s.fullLine); assertEquals(fr, s.built); assertNull(s.hinted)
    }

    @Test fun duplicateWordsAnyChipCounts() {
        val line = "Tu as froid ? Tu as besoin de quelque chose ?"
        var s = TalkTurn.start(Support.Talk.CHIPS, line, seed = 3)
        // 다음은 "as": 두 "as" 조각 가운데 뒤의 것을 눌러도 된다
        val asChips = s.chips.indices.filter { s.chips[it] == "as" }
        assertEquals(2, asChips.size)
        s = run(s, Event.Pick(asChips.last()))
        assertEquals(2, s.placed)
        // 다음은 "froid", 그 뒤 "Tu" (대문자) — 소문자 꼴 조각은 없지만 같은 알맹이
        s = run(s, Event.Pick(s.chips.indexOf("froid")))
        s = run(s, Event.Pick(s.chips.indexOf("Tu")))
        s = run(s, Event.Pick(s.chips.indices.first { s.chips[it] == "as" && it !in s.used }))
        assertEquals("Tu as froid ? Tu as", s.built)
    }

    @Test fun koreanSplitsByEojeol() {
        val s = TalkTurn.start(Support.Talk.CHIPS, "추워요? 필요한 거 있어요?", seed = 1)
        assertEquals(listOf("추워요", "필요한", "거", "있어요"), s.keys)
        assertEquals(setOf("필요한", "거", "있어요"), s.chips.toSet())
        assertEquals("추워요?", s.built)
    }

    @Test fun meaningHintsTwoStepsAndRecordsAtOnce() {
        val st = TalkTurn.start(Support.Talk.MEANING, fr)
        assertTrue(st.canRecord); assertFalse(st.fullLine); assertEquals("", st.built); assertTrue(st.hintable)
        val h1 = run(st, Event.Hint)
        assertEquals("Vraiment ?", h1.built); assertFalse(h1.fullLine)
        val h2 = run(h1, Event.Hint)
        assertTrue(h2.fullLine); assertFalse(h2.hintable)
        assertEquals(h2, run(h2, Event.Hint))
    }

    @Test fun failTwiceRevealsAndLetsYouContinue() {
        val st = TalkTurn.start(Support.Talk.MEANING, fr)
        val f1 = run(st, Event.Heard(false))
        assertEquals(Phase.RETRY, f1.phase); assertTrue(f1.canRecord); assertFalse(f1.fullLine)
        val f2 = run(f1, Event.Heard(false))
        assertEquals(Phase.REVEALED, f2.phase); assertFalse(f2.canRecord); assertTrue(f2.fullLine); assertFalse(f2.passed)
        val done = run(f2, Event.Continue)
        assertEquals(Phase.DONE, done.phase); assertFalse(done.passed)
        // 한 번 틀리고 맞히면 금빛 체크
        assertTrue(run(f1, Event.Heard(true)).passed)
    }

    /** 말하기는 따라 읽기와 같은 채점 하나: 스스로 판정 단계는 없다. 맞힌 뒤 · 원문을 보여 준 뒤의 채점은 무시. */
    @Test fun scoringOnlyNoSelfJudge() {
        assertTrue(Phase.entries.none { it.name == "SELF" })
        val st = TalkTurn.start(Support.Talk.SHADOW, fr)
        assertTrue(st.canRecord); assertTrue(st.fullLine)
        val passed = run(st, Event.Heard(true))
        assertEquals(Phase.PASSED, passed.phase); assertFalse(passed.canRecord)
        assertEquals(passed, run(passed, Event.Heard(false)))
        assertEquals(Phase.DONE, run(passed, Event.Continue).phase)
        val revealed = run(st, Event.Heard(false), Event.Heard(false))
        assertEquals(Phase.REVEALED, revealed.phase)
        assertEquals(revealed, run(revealed, Event.Heard(true)))
    }

    @Test fun shuffleNeverKeepsSentenceOrder() {
        val w = listOf("Le", "voyage", "était", "long")
        (0..40).forEach { seed -> assertNotEquals(w, TalkTurn.shuffle(w, seed)) }
        assertEquals(listOf("x"), TalkTurn.shuffle(listOf("x"), 1))
    }

    // ── 먼저 듣기 (시안 하루 흐름 ②) ──
    @Test fun shadowListensFirstWhenThereIsSound() {
        val st = TalkTurn.start(Support.Talk.SHADOW, fr, listen = true)
        assertEquals(Phase.LISTEN, st.phase); assertFalse(st.canRecord); assertTrue(st.showsRecord)
        // 듣기 전 녹음 결과는 무시
        assertEquals(st, run(st, Event.Heard(true)))
        val ready = run(st, Event.Listened)
        assertEquals(Phase.READY, ready.phase); assertTrue(ready.canRecord)
        // 다시 듣기 신호는 아무 일도 하지 않는다
        assertEquals(ready, run(ready, Event.Listened))
        assertEquals(Phase.PASSED, run(ready, Event.Heard(true)).phase)
    }

    @Test fun noSoundIsNotLocked() {
        assertEquals(Phase.READY, TalkTurn.start(Support.Talk.SHADOW, fr, listen = false).phase)
    }

    @Test fun blankListensAfterTheRightChip() {
        val b = Ladder.find(fr, "Tu es fatigué")!!
        val st = TalkTurn.start(Support.Talk.BLANK, fr, b, listOf("Tu as fatigué", "Tu es fatigué", "Tu es fatiguée"), listen = true)
        assertEquals(Phase.BUILD, st.phase)
        val picked = run(st, Event.Pick(1))
        assertEquals(Phase.LISTEN, picked.phase); assertTrue(picked.fullLine)
        assertEquals(Phase.READY, run(picked, Event.Listened).phase)
    }

    @Test fun meaningNeverListensFirst() {
        // 뜻만 보고 말하는 단계: 먼저 들으면 답이 들린다
        assertEquals(Phase.READY, TalkTurn.start(Support.Talk.MEANING, fr, listen = true).phase)
    }
}
