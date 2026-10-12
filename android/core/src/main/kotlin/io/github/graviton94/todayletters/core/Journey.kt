package io.github.graviton94.todayletters.core

/**
 * 오늘의 여정 (작품마다 하루 하나): 차례가 있는 세 걸음.
 *
 *   I   오늘의 편지 — 읽기 · 낱말 · 따라 읽기 · 답장 · 그림 (걸음마다 점, 건너뛰기 가능)
 *   II  오늘의 복습 — 이 작품 카드만, 한 판 (뜻 → 빈칸 → 듣고 쓰기)
 *   III 여정 완료   — 보너스 화폐 + 편지 그림 한 점이 갤러리에
 *
 * 세는 것은 실제로 한 일뿐이다: 복습은 이 판에서 답한 문제 수 / 이 판의 문제 수 (따로 연습한 것은 섞이지 않는다).
 * 할 것이 없는 걸음(오늘 올 편지가 없음 · 복습할 카드가 없음)은 [Step.NONE] 으로, 여정을 막지 않지만 한 것으로도 치지 않는다.
 */
object Journey {
    /** 걸음 하나의 상태. */
    enum class Step { NONE, TODO, NOW, DONE, SKIPPED }

    /**
     * 오늘의 편지 걸음 점: 읽기 · [modes] (편지에서 하는 걸음) · 그림.
     * 읽기 전에는 읽기가 지금, 다 읽으면 다음 걸음이 지금. 건너뛴 걸음은 [Step.SKIPPED].
     */
    fun letterDots(shown: Int, total: Int, modes: List<ReplyMode>, p: LetterProgress): List<Step> {
        val read = p.done || shown >= total
        val next = if (!read) null else modes.firstOrNull { it !in p.replied && it !in p.skipped }
        return buildList {
            add(if (read) Step.DONE else Step.NOW)
            modes.forEach { m ->
                add(when {
                    m in p.replied -> Step.DONE
                    m in p.skipped -> Step.SKIPPED
                    m == next -> Step.NOW
                    else -> Step.TODO
                })
            }
            add(if (p.done) Step.DONE else if (read && next == null) Step.NOW else Step.TODO)
        }
    }

    /** 오늘의 편지 걸음: 오늘 편지 한 통을 마쳤으면 DONE, 열 편지가 있으면 (시작했으면 NOW) TODO, 없으면 NONE. */
    fun letter(doneToday: Boolean, available: Boolean, started: Boolean): Step = when {
        doneToday -> Step.DONE
        available -> if (started) Step.NOW else Step.TODO
        else -> Step.NONE
    }

    /**
     * 오늘의 복습 걸음: 이 판을 다 풀었으면 DONE, 이 판의 문제가 없으면 NONE, 몇 개 풀었으면 NOW.
     * [total] 은 이 판의 문제 수 (시작 전이면 오늘 볼 이 작품 카드 수), [answered] 는 이 판에서 답한 수.
     */
    fun review(total: Int, answered: Int, finished: Boolean): Step = when {
        finished -> Step.DONE
        total <= 0 -> Step.NONE
        answered > 0 -> Step.NOW
        else -> Step.TODO
    }

    /** 이 판의 진행 글자 ("3 / 12"): 실제로 답한 수, 이 판의 문제 수를 넘지 않는다. */
    fun reviewCount(total: Int, answered: Int, finished: Boolean): Pair<Int, Int> =
        (if (finished) total else answered.coerceIn(0, total.coerceAtLeast(0))) to total.coerceAtLeast(0)

    /** 여정 완료: 두 걸음이 끝났거나 할 것이 없고, 적어도 하나는 실제로 했다. */
    fun complete(letter: Step, review: Step): Boolean =
        letter in setOf(Step.DONE, Step.NONE) && review in setOf(Step.DONE, Step.NONE) && (letter == Step.DONE || review == Step.DONE)

    /** 여정 보너스를 줄 차례인가: 완료했고 오늘 아직 안 받았다. */
    fun payable(letter: Step, review: Step, paidDay: Long, today: Long) = complete(letter, review) && paidDay != today

    /** 세 걸음 가운데 끝난 수 (머리의 "2 / 3"). 할 것이 없는 걸음은 끝난 것으로 보지 않는다. */
    fun doneCount(letter: Step, review: Step, paid: Boolean): Int =
        listOf(letter == Step.DONE, review == Step.DONE, paid).count { it }

    /** 오늘의 복습 차례: 뜻 고르기 → 빈칸 → 듣고 쓰기 → 말하기 (같은 꼴 안에서는 원래 차례). */
    fun order(kind: ReviewKind): Int = when (kind) {
        ReviewKind.MEANING -> 0
        ReviewKind.BLANK -> 1
        ReviewKind.DICTATION -> 2
        ReviewKind.SPEAK -> 3
    }
}
