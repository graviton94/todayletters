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

/** 오늘의 할 일: 날마다 세 가지. 셋 다 하면 오늘의 자료를 한 번 더 뽑는다. */
enum class Quest(val goal: Int) {
    SHADOW_PASS(3),     // 따라 읽기 3문장 통과 (v18: 문장 통째)
    REREAD(1),          // 어제 (또는 지난) 편지 다시 읽기
    DICTATION_RUN(3),   // 듣고 쓰기 3개 연속 정답
    REVIEW_DONE(1),     // 오늘의 복습 끝내기
    MEANING_RUN(5),     // 뜻 고르기 5개 맞히기
}

object Quests {
    /** [day] 의 세 가지. 날마다 바뀌고, 같은 날은 늘 같다. 따라 읽기는 사흘에 이틀. */
    fun today(day: Long): List<Quest> {
        val rnd = java.util.Random(day * 7919L)
        val rest = Quest.entries.filter { it != Quest.SHADOW_PASS }.shuffled(rnd)
        return if (Math.floorMod(day, 3L) != 2L) listOf(Quest.SHADOW_PASS) + rest.take(2) else rest.take(3)
    }

    fun done(q: Quest, count: Int) = count >= q.goal
}

/**
 * 한 시리즈의 오늘의 할 일 진행. 시리즈마다 따로 센다 (보상 화폐가 시리즈마다 달라서, 그 시리즈에서 한 일만).
 * [day] 가 오늘이 아니면 모두 0 부터. [paid] 는 셋 다 해서 보너스를 받은 날.
 */
data class QuestLog(val day: Long = -1, val counts: Map<Quest, Int> = emptyMap(), val paid: Long = -1) {
    fun count(today: Long, q: Quest) = if (day == today) counts[q] ?: 0 else 0
    fun set(today: Long, q: Quest, n: Int) = QuestLog(today, (if (day == today) counts else emptyMap()) + (q to n), paid)
    fun add(today: Long, q: Quest, n: Int = 1) = set(today, q, count(today, q) + n)
    fun atLeast(today: Long, q: Quest, n: Int) = if (count(today, q) >= n) this else set(today, q, n)
    /** 오늘의 셋을 다 했고 오늘 보너스를 아직 안 받았다. */
    fun payable(today: Long) = paid != today && Quests.today(today).all { Quests.done(it, count(today, it)) }
}

/** 일요일 낭독회: 한 주 (월~일) 의 첫날과, 오늘이 낭독회 날인가. */
object Recital {
    /** epoch day 의 요일: 0=월 … 6=일 (1970-01-01 은 목요일). */
    fun weekday(day: Long) = Math.floorMod(day + 3, 7L).toInt()
    fun weekStart(day: Long) = day - weekday(day)
    fun isSunday(day: Long) = weekday(day) == 6
}
