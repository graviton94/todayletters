package io.github.graviton94.todayletters.core

/**
 * 미술관 벽 라벨 (시안 rw2 · 보상 · 새 편지): 보상 화면 · 서재 편지지의 작은 머리말은 그 작품의 말 (원어) 로 쓴다
 * (빈센트 · 나폴레옹 "Lettre I", 모차르트 "Brief I", 고야 "Carta I"). 낱말 몇 개뿐이라 데이터가 아니라 여기에 둔다.
 * 라벨의 작가 · 해 · 곳은 편지 그림의 데이터에서만 ([meta]): 없는 칸은 뺀다 (지어내지 않는다).
 */
object Wall {
    /** ‘편지’ 한 낱말. 모르는 말은 영어. */
    fun letter(lang: Lang): String = when (lang) {
        Lang.FR -> "Lettre"; Lang.DE -> "Brief"; Lang.ES -> "Carta"; Lang.NL -> "Brief"
        Lang.KO -> "편지"; Lang.JA -> "手紙"; Lang.EN -> "Letter"
    }

    /** ‘새 메달’. */
    fun medal(lang: Lang): String = when (lang) {
        Lang.FR -> "Nouvelle médaille"; Lang.DE -> "Neue Medaille"; Lang.ES -> "Nueva medalla"; Lang.NL -> "Nieuwe medaille"
        Lang.KO -> "새 메달"; Lang.JA -> "新しいメダル"; Lang.EN -> "New medal"
    }

    /** ‘레벨’ (레벨 업 머리말 "Niveau 15"). */
    fun level(lang: Lang): String = when (lang) {
        Lang.FR -> "Niveau"; Lang.DE -> "Stufe"; Lang.ES -> "Nivel"; Lang.NL -> "Niveau"
        Lang.KO -> "레벨"; Lang.JA -> "レベル"; Lang.EN -> "Level"
    }

    /** ‘이정표’ (혼자 읽기 이정표 머리말 "Jalon"). */
    fun milestone(lang: Lang): String = when (lang) {
        Lang.FR -> "Jalon"; Lang.DE -> "Meilenstein"; Lang.ES -> "Hito"; Lang.NL -> "Mijlpaal"
        Lang.KO -> "이정표"; Lang.JA -> "道標"; Lang.EN -> "Milestone"
    }

    /** "Lettre II" (편지 번호는 작품 처음부터 센 것, 로마 숫자). */
    fun letterNo(lang: Lang, n: Int): String = "${letter(lang)} ${roman(n)}"

    /** 로마 숫자 (1 이상, 그 밖은 아라비아 숫자 그대로). */
    fun roman(n: Int): String {
        if (n <= 0 || n >= 4000) return n.toString()
        val v = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val s = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var k = n; val b = StringBuilder()
        v.forEachIndexed { i, x -> while (k >= x) { b.append(s[i]); k -= x } }
        return b.toString()
    }

    /** 그림의 해: "1887 (Paris)" → "1887". */
    fun year(date: String): String = date.substringBefore(" (").trim()

    /** 그림의 곳: "1887 (Paris)" → "Paris", 괄호가 없으면 "" (지어내지 않는다). */
    fun place(date: String): String = date.substringAfter(" (", "").substringBefore(")").trim()

    /** 벽 라벨의 작은 줄: 작가 · 해 · 곳 (빈 칸은 뺀다). */
    fun meta(artist: String, date: String): String =
        listOf(artist.replace("⁠", "").trim(), year(date), place(date)).filter { it.isNotEmpty() }.joinToString(" · ")

    /**
     * 칭호를 이름과 끝의 로마 숫자로: "해설사 III" → ("해설사", "III"), "Docent II" → ("Docent", "II"). 숫자가 없으면 (이름, "").
     * 화면은 이름을 세운 글자로, 숫자를 Cormorant 로 쓴다.
     */
    fun rank(title: String): Pair<String, String> {
        val t = title.trim()
        val m = Regex("^(.*\\S)\\s+([IVXLCDM]+)$").find(t) ?: return t to ""
        return m.groupValues[1] to m.groupValues[2]
    }
}

/**
 * 화면과 화면 사이 (시안 rw2 Between screens): 보상 · 큰 단추는 ‘좋아요 · 닫기’ 대신 어디로 가는지 + 거기서 기다리는 것.
 * 무엇을 적을지는 여기서 정하고, 글은 화면이 고른다 (core 테스트 NextTest).
 */
object Next {
    /** 기초 낱말 카드 열쇠인가 ([CardRef.basic] 꼴). */
    fun basic(key: String): Boolean = key.contains(":${Basics.CHAPTER}:${Basics.LETTER}:")

    /** 오늘 볼 카드 열쇠 [due] 를 (낱말 · 표현 · 답장 카드 수, 새 기초 낱말 수) 로. */
    fun review(due: List<String>): Pair<Int, Int> = due.count { !basic(it) } to due.count { basic(it) }

    /** 걸음을 마친 한 장의 단추. */
    sealed interface AfterStep {
        /** 다음 걸음 [mode] · 이 편지에서 마친 걸음 [done] / [total]. */
        data class Step(val mode: ReplyMode, val done: Int, val total: Int) : AfterStep
        /** 다음 걸음이 없다 (마친 편지의 다시 하기 · 복습 탭에서 들어옴): 들어온 곳으로. [letter] 면 편지 방. */
        data class Back(val letter: Boolean) : AfterStep
    }

    /**
     * 걸음 [modes] (이 편지에서 하는 차례) 가운데 한 것 [replied] · 건너뛴 것 [skipped] 을 빼고 남은 첫 걸음.
     * [straight] 는 편지 방에서 이어 하던 걸음이라 바로 다음 걸음으로 가는가 (AppState.stepEnded 와 같은 조건).
     */
    fun afterStep(modes: List<ReplyMode>, replied: Set<ReplyMode>, skipped: Set<ReplyMode>, straight: Boolean, backToLetter: Boolean): AfterStep {
        val next = if (straight) modes.firstOrNull { it !in replied && it !in skipped } else null
        val done = modes.count { it in replied || it in skipped }
        return if (next != null) AfterStep.Step(next, done, modes.size) else AfterStep.Back(backToLetter)
    }

    /** 편지 완료 화면의 큰 단추: 맺음이 있으면 맺음, 아니면 오늘 (복습 [review] 장 · 기초 낱말 [basics] 개, 0 이면 그 칸 없음). */
    data class AfterLetter(val outro: Boolean, val review: Int, val basics: Int)

    fun afterLetter(outro: Boolean, due: List<String>, reviewDone: Boolean): AfterLetter {
        if (outro || reviewDone) return AfterLetter(outro, 0, 0)
        val (r, b) = review(due)
        return AfterLetter(false, r, b)
    }

    /**
     * 보상 · 알림 한 장 (혼자 읽기 이정표 · 오늘의 자료 · 등급 · 새로 바뀐 것) 이 덮고 있는 화면: 단추가 그 화면 이름을 적고 (‘좋아요’ 대신),
     * 누르면 한 장만 걷혀 그 화면이 그대로 이어진다 (길은 바꾸지 않는다).
     */
    sealed interface Under {
        /** 걸음 중 (걸음을 마치면 다음 걸음이 이미 열려 있다). */
        data class Step(val mode: ReplyMode) : Under
        data object Today : Under
        data object Library : Under
        /** 편지 완료 화면 (벽 라벨). */
        data object Finished : Under
        /** 오늘의 복습 결과. */
        data object Review : Under
        /** 복습 탭 (낱말 · 문장 목록). */
        data object Words : Under
        /** 편지 방 · 따라 읽기 · 봉인. */
        data object Letter : Under
        data object Gallery : Under
        data object Record : Under
        data object Shop : Under
        data object Other : Under
    }

    fun under(r: Route): Under = when (r) {
        is Route.Play -> Under.Step(r.mode)
        Route.Inbox, is Route.Series, is Route.Chapter, is Route.RecallQuiz, is Route.Outro -> Under.Today
        Route.Library, is Route.Enter, is Route.Welcome -> Under.Library
        is Route.Done -> Under.Finished
        is Route.Session -> Under.Review
        is Route.Letter, is Route.Shadow, is Route.Seal, is Route.RoomInfo, is Route.WarmUp, is Route.SoundCheck -> Under.Letter
        Route.Gallery, Route.Exhibition, is Route.Catalogue, is Route.Collection, is Route.Artwork -> Under.Gallery
        Route.Record, Route.Medals, Route.Week, Route.Recital -> Under.Record
        Route.Shop, Route.Purchase -> Under.Shop
        Route.Words, Route.WordList, Route.Quotes -> Under.Words
        else -> Under.Other
    }

    /** 레벨 업의 단추: 새 입구 장식이 열렸으면 입구 (앱을 켤 때의 첫 화면) 로, 아니면 나의 기록. */
    fun afterLevel(from: Int, to: Int): Boolean = Levels.decorationsBetween(from, to).isNotEmpty()
}

/**
 * 받은 것이 다음 화면의 제자리로 옮겨 가는 움직임 (시안 rw2 ①~④): 처음 자리 → 그 화면의 자리, 시간에 따라 사이를 잇는다.
 * 화면 쪽 (ui/Flight.kt) 이 자리를 재고, 셈은 여기 (core 테스트 FlightMathTest).
 */
object FlightMath {
    /** 날아가는 시간 (밀리초): 시안 5.2초 중 44% → 62% 남짓. */
    const val MS = 900
    /** 자리가 아직 없으면 (다음 화면이 그려지기 전) 이만큼 기다리다 그만둔다. */
    const val WAIT_MS = 1600

    data class Box(val x: Float, val y: Float, val w: Float, val h: Float)

    /** [a] 에서 [b] 로 [t] (0..1) 만큼. */
    fun lerp(a: Box, b: Box, t: Float): Box {
        val k = t.coerceIn(0f, 1f)
        return Box(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.w + (b.w - a.w) * k, a.h + (b.h - a.h) * k)
    }

    /** 시안의 느린 끝 (cubic-bezier .22 .7 .18 1) 을 닮은 감속: 0..1 → 0..1, 끝에서 부드럽게 멈춘다. */
    fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        // 3차 베지어 (0,0) (.22,.7) (.18,1) (1,1) 의 x → y 를 이분법으로
        var lo = 0f; var hi = 1f
        repeat(24) {
            val m = (lo + hi) / 2
            val bx = 3 * (1 - m) * (1 - m) * m * 0.22f + 3 * (1 - m) * m * m * 0.18f + m * m * m
            if (bx < x) lo = m else hi = m
        }
        val u = (lo + hi) / 2
        return 3 * (1 - u) * (1 - u) * u * 0.7f + 3 * (1 - u) * u * u * 1f + u * u * u
    }

    /** 걸린 시간 [elapsed] 의 자리 (0..1). 움직임 줄이기면 늘 1 (바로 제자리). */
    fun progress(elapsed: Long, still: Boolean, ms: Int = MS): Float = if (still || ms <= 0) 1f else ease(elapsed.toFloat() / ms)

    /** 다 옮겨 갔나: 다 날았거나, 자리를 기다리다 [WAIT_MS] 를 넘었다. */
    fun over(elapsed: Long, landedAt: Long?, still: Boolean): Boolean =
        still || (landedAt != null && elapsed - landedAt >= MS) || (landedAt == null && elapsed >= WAIT_MS)
}
