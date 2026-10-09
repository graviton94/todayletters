package io.github.graviton94.todayletters.core

/** 복습 문제의 꼴. SPEAK 는 소리 내어 말하기 (말하기 칸). */
enum class ReviewKind { DICTATION, BLANK, MEANING, SPEAK }

/**
 * 낱말 카드 하나의 기억 상태. [box] 는 성장 단계 (0 만남 … 5 내 것, [Growth.Stage]).
 * [due] 는 다시 볼 날 (epoch day).
 */
data class Card(val key: String, val box: Int = 0, val due: Long = 0, val seen: Int = 0)

/**
 * 기억 강화 (늘어나는 간격 반복). 맞히면 한 단계 위로 · 다음 볼 날을 그 단계 간격만큼 뒤로.
 * 틀리면 두 단계 내려가 내일 다시 (처음부터가 아니라: 오래 쌓은 기억을 한 번에 잃지 않게). 하루 최대 [DAILY] 장.
 */
object Memory {
    const val DAILY = 12
    const val TOP = 5
    /** 단계마다 다음 볼 때까지의 날 수: 만남 1 · 알아보기 1 · 떠올리기 3 · 쓰기 7 · 말하기 16 · 내 것 35. */
    val gaps = intArrayOf(1, 1, 3, 7, 16, 35)

    fun added(key: String, today: Long) = Card(key, box = 0, due = today + 1)

    fun after(c: Card, correct: Boolean, today: Long): Card =
        if (correct) {
            val box = (c.box + 1).coerceAtMost(TOP)
            c.copy(box = box, due = today + gaps[box], seen = c.seen + 1)
        } else c.copy(box = (c.box - 2).coerceAtLeast(0), due = today + 1, seen = c.seen + 1)

    /** 오늘 볼 카드: 볼 날이 지난 것부터, 낮은 칸 먼저, 최대 [DAILY] 장. */
    fun dueToday(cards: Collection<Card>, today: Long): List<Card> =
        cards.filter { it.due <= today }.sortedWith(compareBy({ it.box }, { it.due }, { it.key })).take(DAILY)

    /**
     * 문제 꼴 = 성장 곡선: 알아보기(뜻 고르기) → 떠올리기(빈칸) → 쓰기(듣고 쓰기) → 말하기.
     * 위 단계는 쓰기와 말하기를 번갈아 (오래 안 쓰면 잊는 쪽부터).
     */
    fun kindFor(c: Card): ReviewKind = when (c.box) {
        0 -> ReviewKind.MEANING
        1 -> ReviewKind.BLANK
        2 -> ReviewKind.DICTATION
        3 -> ReviewKind.SPEAK
        else -> if (c.seen % 2 == 0) ReviewKind.SPEAK else ReviewKind.DICTATION
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

    /** 단계별 장 수 (0 만남 … 5 내 것). */
    fun counts(cards: Collection<Card>) = IntArray(TOP + 1).also { a -> cards.forEach { a[it.box.coerceIn(0, TOP)]++ } }
}
