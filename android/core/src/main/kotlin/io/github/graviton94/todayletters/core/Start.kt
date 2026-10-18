package io.github.graviton94.todayletters.core

/**
 * 처음 시작 (시안 onboard.html): 작품을 처음 열 때 세 화면 — 실력 · 왜 · 하루 분량. 새로 쓰는 사람만 묻는다 (이미 쓰던 사람 · 그 말로 읽던 사람은 묻지 않음).
 * 모두 작품 설정에서 바꿀 수 있다.
 *
 *   실력 → 시작 단계 · 천천히 듣는 기간: 처음이에요 = 낱말을 줍는 사람 1단 + 14일 · 인사 정도는 알아요 = 1단 + 7일 · 천천히 읽을 수 있어요 = 문장을 읽는 사람 1단, 없음.
 *   왜   → 응원 한 줄 · 다음 작품 추천에만 (학습량은 바꾸지 않음).
 *   하루 분량 → 편지 한 통 (복습 [Memory.DAILY] 장) · 편지 한 통 + 복습 더 ([MORE_REVIEW] 장). 편지 반 통은 없다.
 */
object Start {
    enum class Level { NEW, GREETINGS, SLOW_READ }
    enum class Why { TRAVEL, ARTS, WORK, CURIOUS }
    enum class Amount { ONE, MORE }

    /** ‘복습 더’ 의 하루 복습 카드 수. */
    const val MORE_REVIEW = 20

    /** 시작 단계 ([Reading.Grade.index]): 이 아래로는 내려가지 않는 바닥. */
    fun grade(l: Level): Int = when (l) { Level.NEW, Level.GREETINGS -> 1; Level.SLOW_READ -> 4 }

    /** 천천히 듣는 기간 (일, 0 = 없음). */
    fun slowDays(l: Level): Int = when (l) { Level.NEW -> 14; Level.GREETINGS -> 7; Level.SLOW_READ -> 0 }

    /** 시작 단계에서 거꾸로 실력 칸 (작품 설정에서 지금 고른 것을 보일 때). 기간이 7일이면 인사 정도. */
    fun level(start: Int, slowDays: Int): Level = when {
        start >= 4 -> Level.SLOW_READ
        slowDays in 1..7 -> Level.GREETINGS
        else -> Level.NEW
    }

    /** 하루 복습 카드 수. */
    fun daily(a: Amount): Int = if (a == Amount.MORE) MORE_REVIEW else Memory.DAILY

    /**
     * 처음 시작을 물을까: 이 작품에서 아직 답하지 않았고 ([answered]), 이 버전 전부터 쓰던 사람이 아니고 ([legacy]),
     * 이 말로 읽은 편지가 하나도 없을 때 ([startedInLang], 다른 작품 포함).
     */
    fun asks(answered: Boolean, legacy: Boolean, startedInLang: Boolean): Boolean = !answered && !legacy && !startedInLang

    /** 왜에 맞춘 다음 작품 차례: [ids] (서재 차례) 를 좋아할 만한 것부터. 모르는 작품은 뒤에 서재 차례 그대로. */
    fun suggest(why: Why?, ids: List<String>): List<String> {
        val pref = when (why) {
            Why.TRAVEL -> listOf("vincent", "napoleon", "mozart")
            Why.ARTS -> listOf("vincent", "mozart", "napoleon")
            Why.WORK -> listOf("napoleon", "mozart", "vincent")
            Why.CURIOUS, null -> emptyList()
        }
        return ids.sortedBy { pref.indexOf(it).let { i -> if (i < 0) pref.size else i } }
    }
}

/**
 * 천천히 듣는 2주 (시안 onboard.html, 글자 A): 처음 며칠 동안 오늘 화면 맨 위에 ‘어제 편지 다시 듣기’,
 * 오늘 편지는 처음 한 번 [Listen.SLOW] 로 저절로 읽은 뒤 평소 빠르기. 기간이 지나면 저절로 끝, 작품 설정에서 다시 켠다.
 */
object Slow {
    /** 다시 켤 때의 기간. */
    const val DAYS = 14

    /** 오늘이 기간의 며칠째인가 (1부터). 꺼졌거나 지났으면 null. */
    fun day(from: Long, days: Int, today: Long): Int? {
        if (from < 0 || days <= 0) return null
        val d = (today - from + 1).toInt()
        return d.takeIf { it in 1..days }
    }

    /** 편지를 마친 뒤 한 번 띄우는 안내 (“처음 2주는 천천히 들어봐요 · 오늘 N분 들었어요”). */
    enum class Note { FIRST, DAY3 }

    /**
     * 이번에 띄울 안내: 기간 중 ([day] != null) 처음 마친 편지면 FIRST, 아니면 3일째에 한 번 DAY3. 이미 띄웠으면 null.
     * [firstDone] = 방금 마친 것이 이 작품의 첫 편지.
     */
    fun note(day: Int?, firstDone: Boolean, shownFirst: Boolean, shownDay3: Boolean): Note? = when {
        day == null -> null
        firstDone && !shownFirst -> Note.FIRST
        day == 3 && !shownDay3 -> Note.DAY3
        else -> null
    }

    /** 다시 듣기 길이 (밀리초) → 화면의 분 (올림, 적어도 1분). */
    fun minutes(ms: Long): Int = ((ms + 59_999) / 60_000).toInt().coerceAtLeast(1)
}

/**
 * 들은 시간 (시안 onboard.html 3): 배우는 말의 소리를 실제로 재생한 시간만 센다 — 편지 낭독 · 다시 듣기 · 카드 · 표현 소리 ·
 * 따라 말하기의 원래 소리. 효과음 · 곡 · 내 녹음은 세지 않는다. 언어는 파일 이름의 언어 꼬리 (m3_fr.m4a → fr).
 */
object Listening {
    private val langs = Lang.entries.map { it.code }.toSet()

    /** 이 소리 파일의 언어 코드 (세지 않는 소리면 null). */
    fun langOf(asset: String): String? {
        if (!asset.startsWith("audio/") || asset.contains("/music/") || !asset.endsWith(".m4a")) return null
        val name = asset.substringAfterLast('/').removeSuffix(".m4a")
        return name.split('_').drop(1).firstOrNull { it in langs }
    }

    /** epoch day 의 주 (월요일) 첫날. */
    fun weekStart(day: Long): Long = day - Math.floorMod(day + 3, 7L)

    /** 시간 · 분 (버림). */
    fun hm(ms: Long): Pair<Int, Int> = (ms / 3_600_000).toInt() to ((ms / 60_000) % 60).toInt()

    /** 분 (버림). */
    fun min(ms: Long): Int = (ms / 60_000).toInt()

    /**
     * 재생 시계 하나 (재생기 하나에 하나): 시작 · 멈춤 · 이어서 · 끝. 멈춘 동안은 세지 않는다.
     * [stop] 은 지금까지 잰 (언어, 밀리초) 를 돌려주고 비운다 (세지 않는 소리면 null).
     */
    class Clock {
        private var lang: String? = null
        private var since = -1L
        private var acc = 0L

        fun start(asset: String, now: Long): Pair<String, Long>? {
            val out = stop(now)
            lang = langOf(asset)
            since = if (lang != null) now else -1
            acc = 0
            return out
        }

        fun pause(now: Long) { if (since >= 0) { acc += (now - since).coerceAtLeast(0); since = -1 } }
        fun resume(now: Long) { if (lang != null && since < 0) since = now }

        fun stop(now: Long): Pair<String, Long>? {
            pause(now)
            val l = lang; val ms = acc
            lang = null; acc = 0; since = -1
            return if (l != null && ms > 0) l to ms else null
        }
    }
}
