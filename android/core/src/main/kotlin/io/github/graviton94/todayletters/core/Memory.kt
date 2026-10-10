package io.github.graviton94.todayletters.core

/** 복습 문제의 꼴. SPEAK 는 소리 내어 말하기 (말하기 칸). */
enum class ReviewKind { DICTATION, BLANK, MEANING, SPEAK }

/**
 * 낱말 카드 하나의 기억 상태. [box] 는 성장 단계 (0 만남 … 5 내 것, [Growth.Stage]).
 * [due] 는 다시 볼 날 (epoch day). [keep] 은 ‘내 것’에서 연달아 맞힌 횟수 (간격이 그만큼 늘어난다),
 * [last] 는 마지막으로 답한 날, [lapsed] 는 ‘내 것’까지 갔다가 잊었던 적이 있는가.
 */
data class Card(val key: String, val box: Int = 0, val due: Long = 0, val seen: Int = 0,
                val keep: Int = 0, val last: Long = 0, val lapsed: Boolean = false)

/**
 * 기억 강화 (늘어나는 간격 반복). 맞히면 한 단계 위로 · 다음 볼 날을 그 단계 간격만큼 뒤로.
 * 틀리면 두 단계 내려가 내일 다시 (처음부터가 아니라: 오래 쌓은 기억을 한 번에 잃지 않게). 하루 최대 [DAILY] 장.
 */
object Memory {
    const val DAILY = 12
    const val TOP = 5
    /** 하루 몫 가운데 ‘내 것’ 확인에 남겨 두는 자리 (새 낱말이 많아도 밀리지 않게). */
    const val KEEP_SLOTS = 2
    /** 단계마다 다음 볼 때까지의 날 수: 만남 1 · 알아보기 1 · 떠올리기 3 · 쓰기 7 · 말하기 16 · 내 것 35. */
    val gaps = intArrayOf(1, 1, 3, 7, 16, 35)
    /** ‘내 것’이 된 뒤에도 계속: 맞힐 때마다 35 → 60 → 120 → 240일, 그 뒤로는 240일마다 (다시는 안 나오는 낱말은 없다). */
    val keepGaps = intArrayOf(35, 60, 120, 240)

    fun added(key: String, today: Long) = Card(key, box = 0, due = today + 1)

    /** 긴 간격(2주 넘게)은 ±20% 흔든다: 언제 다시 물을지 미리 알 수 없게 (같은 낱말 · 같은 날이면 늘 같은 값). */
    fun jitter(days: Int, key: String, today: Long): Int {
        if (days < 14) return days
        val r = java.util.Random(key.hashCode() * 31L + today).nextDouble()   // 0..1
        return (days * (0.8 + 0.4 * r)).toInt().coerceAtLeast(1)
    }

    fun after(c: Card, correct: Boolean, today: Long): Card =
        if (correct) {
            val atTop = c.box == TOP
            val box = (c.box + 1).coerceAtMost(TOP)
            val keep = if (atTop) c.keep + 1 else 0
            val gap = if (box == TOP) keepGaps[keep.coerceAtMost(keepGaps.lastIndex)] else gaps[box]
            c.copy(box = box, due = today + jitter(gap, c.key, today), seen = c.seen + 1, keep = keep, last = today)
        } else c.copy(box = (c.box - 2).coerceAtLeast(0), due = today + 1, seen = c.seen + 1, keep = 0, last = today,
            lapsed = c.lapsed || c.box == TOP)

    /**
     * 오늘 볼 카드: 볼 날이 지난 것 가운데, ‘내 것’ 확인 [KEEP_SLOTS] 장을 먼저 자리 잡고, 나머지는 낮은 칸부터. 최대 [DAILY] 장.
     * 문제 순서는 낮은 칸부터 (확인 낱말은 섞여서 나온다).
     */
    fun dueToday(cards: Collection<Card>, today: Long): List<Card> {
        val due = cards.filter { it.due <= today }
        val keepers = due.filter { it.box == TOP }.sortedWith(compareBy({ it.due }, { it.key })).take(KEEP_SLOTS)
        val rest = (due - keepers.toSet()).sortedWith(compareBy({ it.box }, { it.due }, { it.key })).take(DAILY - keepers.size)
        return (rest + keepers).sortedWith(compareBy({ it.box }, { it.due }, { it.key }))
    }

    /**
     * 갑자기 한 문제: 오래전(2주 넘게 안 본) ‘내 것’ 낱말 하나. 날마다 다른 것 (같은 날이면 같은 것). 없으면 null.
     * 맞히면 일정은 그대로 (마지막 본 날만), 틀리면 [after] 처럼 내려간다.
     */
    fun surprise(cards: Collection<Card>, today: Long): Card? {
        val pool = cards.filter { it.box == TOP && it.last in 1..(today - 14) && it.due > today }.sortedBy { it.key }
        if (pool.isEmpty()) return null
        return pool[java.util.Random(today * 7919).nextInt(pool.size)]
    }
    fun surpriseAnswered(c: Card, correct: Boolean, today: Long): Card = if (correct) c.copy(last = today, seen = c.seen + 1) else after(c, false, today)

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
