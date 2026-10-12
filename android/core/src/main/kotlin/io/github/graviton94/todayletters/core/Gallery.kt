package io.github.graviton94.todayletters.core

/**
 * 갤러리 (모든 시리즈 공통 틀). 시리즈 키트가 공공 영역 소장품 목록을 주고, 학습을 마칠 때마다 한 점이 걸린다.
 * 편지 자료(편지에 붙은 그림)는 편지를 다 읽으면 걸리고, 나머지는 ‘오늘의 자료’ 뽑기로.
 */
enum class Tier(val weight: Int) {
    /** 편지에 붙은 자료: 뽑기에는 나오지 않는다. */
    LETTER(0),
    SKETCH(60),
    DRAWING(25),
    PAINTING(12),
    MASTER(3),
}

/** 소장품 한 점. [image] 는 assets 안 경로. [theme] 은 이달의 전시 주제와 맞춰 보는 낱말. */
data class Piece(
    val id: String,
    val image: String,
    val title: Tri,
    val date: String,
    val collection: String,
    val tier: Tier,
    val themes: Set<String> = emptySet(),
)

/** 이달의 전시: 달마다 돌아가는 주제. 그 주제의 소장품이 두 배로 잘 나오고, 다 모으면 도록 표지가 남는다. */
data class Exhibition(val id: String, val title: Tri, val theme: String)

object Draws {
    /**
     * 오늘의 자료 한 점. 이미 가진 것은 빼고, 희귀도 무게에 전시 주제 ×2. [minTier] 이상만 (이정표의 확정 명작).
     * 남은 게 없으면 null. [seed] 가 같으면 결과도 같다 (같은 날 다시 열어도 같은 그림).
     */
    fun pick(pool: List<Piece>, owned: Set<String>, seed: Long, theme: String? = null, minTier: Tier = Tier.SKETCH): Piece? {
        val left = pool.filter { it.tier != Tier.LETTER && it.id !in owned }
        val eligible = left.filter { it.tier >= minTier }.ifEmpty { left }
        if (eligible.isEmpty()) return null
        val weights = eligible.map { it.tier.weight * (if (theme != null && theme in it.themes) 2 else 1) }
        val total = weights.sum()
        if (total <= 0) return eligible.first()
        var r = java.util.Random(seed).nextInt(total)
        eligible.forEachIndexed { i, p -> r -= weights[i]; if (r < 0) return p }
        return eligible.last()
    }

    /** 이달의 전시: 달(0~11)에 따라 목록을 돌아가며. */
    fun exhibition(list: List<Exhibition>, month: Int): Exhibition? = if (list.isEmpty()) null else list[Math.floorMod(month, list.size)]

    /** 전시 자료: 이 전시 주제의 소장품. */
    fun exhibitionPieces(pool: List<Piece>, e: Exhibition): List<Piece> = pool.filter { e.theme in it.themes }
}

/** 일요일 낭독회: 한 주 (월~일) 의 첫날과, 오늘이 낭독회 날인가. */
object Recital {
    /** epoch day 의 요일: 0=월 … 6=일 (1970-01-01 은 목요일). */
    fun weekday(day: Long) = Math.floorMod(day + 3, 7L).toInt()
    fun weekStart(day: Long) = day - weekday(day)
    fun isSunday(day: Long) = weekday(day) == 6
}
