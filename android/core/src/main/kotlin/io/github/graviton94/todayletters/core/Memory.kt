package io.github.graviton94.todayletters.core

/** 복습 문제의 꼴. */
enum class ReviewKind { DICTATION, BLANK, MEANING }

/**
 * 낱말 카드 하나의 기억 상태. [box] 0 새 낱말 · 1 익히는 중 · 2 거의 · 3 기억.
 * [due] 는 다시 볼 날 (epoch day).
 */
data class Card(val key: String, val box: Int = 0, val due: Long = 0, val seen: Int = 0)

/**
 * 기억 강화 (라이트너 상자). 맞히면 한 칸 위로 · 다음 볼 날을 칸 간격만큼 뒤로.
 * 틀리면 새 낱말 칸으로 돌아가 내일 다시. 하루 최대 [DAILY] 장.
 */
object Memory {
    const val DAILY = 12
    /** 칸마다 다음 볼 때까지의 날 수: 새 낱말 1 · 익히는 중 1 · 거의 4 · 기억 14. */
    val gaps = intArrayOf(1, 1, 4, 14)

    fun added(key: String, today: Long) = Card(key, box = 0, due = today + 1)

    fun after(c: Card, correct: Boolean, today: Long): Card =
        if (correct) {
            val box = (c.box + 1).coerceAtMost(3)
            c.copy(box = box, due = today + gaps[box], seen = c.seen + 1)
        } else c.copy(box = 0, due = today + 1, seen = c.seen + 1)

    /** 오늘 볼 카드: 볼 날이 지난 것부터, 낮은 칸 먼저, 최대 [DAILY] 장. */
    fun dueToday(cards: Collection<Card>, today: Long): List<Card> =
        cards.filter { it.due <= today }.sortedWith(compareBy({ it.box }, { it.due }, { it.key })).take(DAILY)

    /** 문제 꼴: 새 낱말은 뜻 고르기, 그다음은 듣고 쓰기 · 빈칸 채우기를 번갈아 (듣고 쓰기가 기본). */
    fun kindFor(c: Card): ReviewKind = when (c.box) {
        0 -> ReviewKind.MEANING
        2 -> ReviewKind.BLANK
        else -> ReviewKind.DICTATION
    }

    /**
     * 받아쓰기 채점 (너그럽게): 대소문자 · 앞뒤 문장부호는 보지 않는다. 악상(é 등)만 틀리면 맞은 것으로 치되 [Mark.ACCENT] 로 알려 준다.
     */
    enum class Mark { RIGHT, ACCENT, WRONG }
    fun grade(answer: String, typed: String): Mark {
        fun clean(x: String) = Breaks.plain(x).trim().trim('.', ',', ';', ':', '!', '?', '…', '«', '»', '"').lowercase()
        fun bare(x: String) = java.text.Normalizer.normalize(x, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        val a = clean(answer); val t = clean(typed)
        return when {
            a == t -> Mark.RIGHT
            bare(a) == bare(t) -> Mark.ACCENT
            else -> Mark.WRONG
        }
    }

    /** 칸별 장 수 (새 · 익히는 중 · 거의 · 기억). */
    fun counts(cards: Collection<Card>) = IntArray(4).also { a -> cards.forEach { a[it.box.coerceIn(0, 3)]++ } }
}
