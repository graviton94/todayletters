package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 소리 차례 (Voice): Narrator 의 play · stop 이 쓰는 순서를 그대로 흉내 낸 작은 재생기로,
 * 화면을 옮길 때 앞 화면의 소리가 이어지거나 새 화면 소리를 끄지 않는지 본다.
 */
class VoiceTest {
    /** Narrator 와 같은 순서: play = (받는가) → 앞 소리 끝(false) → 시작. stop = 끝(false). 끝까지 = 끝(true). */
    private class Player {
        val v = Voice()
        var now: String? = null
        val log = mutableListOf<String>()
        fun play(a: String, by: Any? = v.scene, onEnd: (Boolean) -> Unit = {}): Boolean {
            if (!v.admits(by)) { onEnd(false); return false }
            stop()
            v.begin(by, onEnd); now = a; log += a
            return true
        }
        fun stop() { now = null; v.end(false) }
        fun stop(by: Any?) { if (now != null && !v.owns(by)) return; stop() }
        fun complete() { now = null; v.end(true) }
        fun enter(scene: Any?) { v.enter(scene); stop() }
    }

    @Test fun chainContinuesOnlyWhenHeardToTheEnd() {
        val p = Player(); p.enter("check")
        val clips = listOf("a", "b", "c")
        fun listen(k: Int) { p.play(clips[k]) { ok -> if (ok && k + 1 < clips.size) listen(k + 1) } }
        listen(0)
        p.complete()                 // a 끝까지 → b
        assertEquals("b", p.now)
        // 다른 화면으로: b 의 ‘끝’(false) 이 c 를 부르지 않는다
        p.enter("review")
        assertNull(p.now)
        assertEquals(listOf("a", "b"), p.log)
    }

    @Test fun oldChainThatIgnoresTheFlagIsRefusedWhileStopping() {
        val p = Player(); p.enter("talk")
        // 예전 부르는 쪽처럼 끝(false) 에도 곧바로 다음 소리를 부르는 경우
        p.play("old1") { p.play("old2") }
        p.play("new")               // 새 소리가 앞 소리를 멈춘다 → old2 는 받지 않음
        assertEquals("new", p.now)
        assertEquals(listOf("old1", "new"), p.log)
    }

    @Test fun lateAutoPlayFromTheLeavingScreenIsRefused() {
        val p = Player(); p.enter("room")
        val room = p.v.scene
        p.enter("play")
        p.play("next-screen")
        var told: Boolean? = null
        // 나가는 화면의 늦은 자동 재생 (delay 뒤 · 다음 줄 차례)
        assertFalse(p.play("room-line", by = room) { told = it })
        assertEquals(false, told)
        assertEquals("next-screen", p.now)
    }

    @Test fun leavingScreenDisposeDoesNotStopTheNewScreensVoice() {
        val p = Player(); p.enter("room")
        val room = p.v.scene
        p.play("room-line")
        p.enter("check")
        p.play("check-passage")      // 새 화면이 들어오며 저절로
        p.stop(room)                 // 전환이 끝나 앞 화면의 onDispose
        assertEquals("check-passage", p.now)
        p.stop("check")
        assertNull(p.now)
    }

    @Test fun ownerlessVoiceCanBeStoppedByAnyone() {
        val p = Player()
        p.play("x", by = null)
        p.stop("anyone")
        assertNull(p.now)
    }

    @Test fun readingInOrderStopsWhenInterruptedButFollowsButtons() {
        assertEquals(3, Voice.next(2, completed = true, jump = 0))
        assertNull(Voice.next(2, completed = false, jump = 0))       // 다른 소리 · 화면을 떠남
        assertEquals(3, Voice.next(2, completed = false, jump = 1))   // ‘다음’ 단추
        assertEquals(1, Voice.next(2, completed = false, jump = -1))
        assertEquals(0, Voice.next(0, completed = false, jump = -1))
        assertEquals(2, Voice.next(2, completed = false, jump = 2))
    }

    @Test fun endIsCalledOnce() {
        val v = Voice(); var n = 0
        v.begin(null) { n++ }
        v.end(false); v.end(true)
        assertEquals(1, n)
        assertTrue(v.admits(null))
    }
}
