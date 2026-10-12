package io.github.graviton94.todayletters.core

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 끝없는 레벨 (data/medals.json 의 levels). 레벨 n → n+1 에 round(100 · n^1.4) XP, 레벨 1 은 0 XP 에서.
 * 칭호는 3 레벨씩 한 등급 (I · II · III): 견습 관람객 → … → 관장 III (30). 31 레벨부터는 관장 IV, V … 로 끝없이.
 * 5 레벨마다 입구 장식 하나 (열두 가지, 60 레벨 뒤로는 이름 뒤에 II, III …).
 */
data class LevelBook(
    val ranks: List<Tri> = emptyList(),
    val decorations: List<Tri> = emptyList(),
    /** 행동마다 XP (medals.json 의 levels.xp). */
    val xp: Map<String, Int> = emptyMap(),
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun parse(m: Map<String, Any?>): LevelBook {
            fun tri(v: Any?) = (v as? Map<String, Any?>).orEmpty().let { o -> Tri(listOf(Lang.KO, Lang.EN).associateWith { o[it.code] as? String ?: "" }) }
            val ranks = (m["ranks"] as? List<Any?>).orEmpty().map(::tri)
            val decos = (m["decorations"] as? List<Any?>).orEmpty().map(::tri)
            val xp = (m["xp"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> (v as? Double)?.let { k to it.toInt() } }.toMap()
            return LevelBook(ranks, decos, xp)
        }
    }
}

/** 지금 레벨: [level] · 이 레벨에서 모은 XP [into] · 다음 레벨까지 필요한 XP [need] · 누적 [total]. */
data class Level(val level: Int, val into: Long, val need: Long, val total: Long) {
    val frac: Float get() = if (need <= 0) 0f else (into.toFloat() / need).coerceIn(0f, 1f)
    val left: Long get() = need - into
}

/** 칭호: [rank] 는 등급 번호 (0 = 견습 관람객 … 9 = 관장), [step] 은 로마 숫자 (I, II, III, 관장은 IV 부터 끝없이). */
data class Rank(val rank: Int, val step: Int)

/** 입구 장식: 장식 번호 (0~11) 와 몇 바퀴째인지 (1 이면 숫자 없이, 2 부터 II, III …). */
data class Decoration(val index: Int, val cycle: Int, val level: Int)

object Levels {
    const val BASE = 100.0
    const val EXP = 1.4
    const val RANKS = 10
    const val DECORATIONS = 12

    fun xpToNext(n: Int): Long = (BASE * n.toDouble().pow(EXP)).roundToLong()

    /** 레벨 [level] 에 닿는 누적 XP (레벨 1 = 0). */
    fun totalFor(level: Int): Long = (1 until level.coerceAtLeast(1)).sumOf { xpToNext(it) }

    fun of(xp: Long): Level {
        var lv = 1; var rest = xp.coerceAtLeast(0)
        while (rest >= xpToNext(lv)) { rest -= xpToNext(lv); lv++ }
        return Level(lv, rest, xpToNext(lv), xp.coerceAtLeast(0))
    }

    fun rank(level: Int): Rank {
        val lv = level.coerceAtLeast(1)
        return if (lv <= RANKS * 3) Rank((lv - 1) / 3, (lv - 1) % 3 + 1) else Rank(RANKS - 1, lv - 27)
    }

    /** 칭호 글: "학예 보조 II". */
    fun title(book: LevelBook, level: Int, lang: Lang): String {
        val r = rank(level)
        val name = book.ranks.getOrNull(r.rank)?.get(lang).orEmpty()
        return "$name ${Roman.of(r.step)}".trim()
    }

    /** 이 레벨까지 열린 가장 새 입구 장식. 5 레벨 전이면 null. */
    fun decoration(level: Int): Decoration? {
        if (level < 5) return null
        val k = level / 5 - 1
        return Decoration(k % DECORATIONS, k / DECORATIONS + 1, (k + 1) * 5)
    }

    /** 이 레벨에서 새로 열린 장식 (5 의 배수일 때만). */
    fun decorationAt(level: Int): Decoration? = if (level >= 5 && level % 5 == 0) decoration(level) else null

    fun decorationName(book: LevelBook, d: Decoration, lang: Lang): String {
        val name = book.decorations.getOrNull(d.index)?.get(lang).orEmpty()
        return if (d.cycle > 1) "$name ${Roman.of(d.cycle)}" else name
    }

    /** [from] 레벨에서 [to] 레벨로 오르며 새로 열린 장식들. */
    fun decorationsBetween(from: Int, to: Int): List<Decoration> = ((from + 1)..to).mapNotNull(::decorationAt)
}

/**
 * XP 를 받는 일 (medals.json 의 levels.xp 와 같은 값). [dailyCap] 은 하루에 받는 XP 상한 (0 이면 없음):
 * 되풀이할 수 있는 것만 막는다 (화폐의 [Earn] 과 같은 방식).
 */
enum class XpAction(val key: String, val amount: Int, val dailyCap: Int = 0) {
    LETTER_READ("letter_read", 30, 150),
    LETTER_SEALED("letter_sealed", 40),
    PRACTICE("practice", 10, 150),
    REVIEW_RIGHT("review_right", 2, 120),
    WORD_LEARNED("word_learned", 3),
    READALONG_STAR("readalong_star", 5, 150),
    WALK_DONE("walk_done", 15, 75),
    QUESTS_DONE("quests_done", 25),
    STREAK_DAY("streak_day", 10, 10),
    MEDAL("medal", 50),
    SERIES_COMPLETED("series_completed", 300),
}

/** XP 장부: 누적 · 오늘 날짜 · 오늘 일마다 받은 XP. */
data class XpLedger(val total: Long = 0, val day: Long = -1, val today: Map<XpAction, Int> = emptyMap())

object Xp {
    /** [a] 를 [times] 번 했을 때 받는 XP (하루 상한 안에서). 날이 바뀌면 오늘 몫을 새로. */
    fun gain(l: XpLedger, a: XpAction, day: Long, times: Int = 1): Pair<XpLedger, Int> {
        if (times <= 0) return l to 0
        val base = if (l.day == day) l else l.copy(day = day, today = emptyMap())
        val already = base.today[a] ?: 0
        val want = a.amount * times
        val gain = if (a.dailyCap <= 0) want else want.coerceAtMost((a.dailyCap - already).coerceAtLeast(0))
        if (gain <= 0) return base to 0
        return base.copy(total = base.total + gain, today = base.today + (a to already + gain)) to gain
    }

    /**
     * 처음 레벨이 생길 때 (예전 사용자): 그동안 한 일로 XP 를 한 번 채운다 (상한 없이, 조용히).
     * 읽은 편지 · 봉인 · 익힌 낱말 · 따라 읽기 별 · 그림 산책 · 메달 · 완주한 시리즈 · 이어 읽은 날.
     */
    fun seed(s: Stats, sealed: Int, medals: Int, days: Int): Long =
        s.lettersDone.toLong() * XpAction.LETTER_READ.amount + sealed.toLong() * XpAction.LETTER_SEALED.amount +
            s.wordsKnown.toLong() * XpAction.WORD_LEARNED.amount + s.readAlongStars.toLong() * XpAction.READALONG_STAR.amount +
            s.walksDone.toLong() * XpAction.WALK_DONE.amount + medals.toLong() * XpAction.MEDAL.amount +
            s.seriesCompleted.size.toLong() * XpAction.SERIES_COMPLETED.amount + days.toLong() * XpAction.STREAK_DAY.amount
}

/** 로마 숫자 (1 → I, 4 → IV, 14 → XIV …). 0 이하면 빈 글. */
object Roman {
    private val table = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    fun of(n: Int): String {
        var r = n; val b = StringBuilder()
        for ((v, s) in table) while (r >= v) { b.append(s); r -= v }
        return b.toString()
    }
}
