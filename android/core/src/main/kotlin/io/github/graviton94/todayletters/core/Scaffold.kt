package io.github.graviton94.todayletters.core

/**
 * 도움 사다리 (모든 화면이 같이 따르는 한 곳): 언어 등급 ([Reading.Grade], A1~B2 × 3단) 과 낱말마다 익숙한 정도 (복습 칸, [Card.box]) 로
 * 화면마다 얼마나 도와줄지 정한다. 실력이 오르면 도움을 조금씩 뗀다.
 *
 *   |            | A1 (낱말을 줍는 사람) | A2 (문장을 읽는 사람) | B1 (편지를 읽는 사람)      | B2 (답장을 쓰는 사람)      |
 *   | 번역 줄    | 또렷이 (접지 않음)  | 조금 옅게              | 더 옅게                    | 가장 옅게 (그래도 늘 보임)  |
 *   | 낱말 아래 뜻 | 내 것이 아닌 낱말 | 내 것이 아닌 낱말       | 내 것이 아닌 낱말          | 없음 (눌러서 뜻 사전)       |
 *   | 예열       | 기본 켜짐           | 기본 켜짐              | 켜짐 · 끌 수 있음           | 없음                       |
 *   | 느린 듣기  | 기본                | 기본                   | -                          | -                          |
 *   | 이해 확인  | 질문 · 보기 읽는 말 | 질문 · 보기 읽는 말    | 질문 배우는 말 · 보기 읽는 말 | 둘 다 배우는 말            |
 *   | (지문 번역) | 눌러서 보기        | 눌러서 보기            | 눌러서 보기                 | 눌러서 보기                |
 *   | 회화 내 차례 | 원문 + 번역 → 따라 말하기 | 빈칸 1 + 칩 → 채워 말하기 | 번역 + 첫 낱말 + 낱말 칩 · 힌트(원문) | 번역만 · 힌트 2단 (첫 낱말 → 원문) |
 *   | 답장       | 별자리 → 빈칸 1     | 빈칸 1                | 빈칸 2                     | 직접 쓰기                  |
 *   | 답장 말해 보기 | 문장 보며         | 문장 보며             | 문장 흐리게                 | 문장 없이 (힌트)            |
 *   | 복습 문장 힌트 | 바로 보임         | 누르면 보임           | 누르면 보임                 | 두 번 틀린 뒤               |
 *
 * 번역은 어느 단계에서도 접지 않는다 (예전 ‘번역 접기’ 는 없앴다): 단계가 오를수록 옅어질 뿐 ([readAlpha]).
 * B2 는 힌트가 가장 적지만 번역 줄 · 뜻 사전(낱말을 눌러 보기)은 언제나 쓸 수 있다.
 * 낱말마다: 잘 아는 낱말 (칸이 [KNOWN_BOX] 이상) 부터 빈칸으로, 아직 모르는 낱말은 등급이 올라도 보여 준다.
 * 작품 설정의 ‘도움 한 단계 더’ ([SeriesSettings.easier]) 를 켜면 한 등급 아래의 도움 ([grade]).
 */
object Support {
    /** 잘 아는 낱말의 복습 칸 문턱 (떠올리기 이상, [Growth.known] 과 같은 문턱). */
    val KNOWN_BOX = Growth.Stage.RECALL.ordinal
    /** ‘도움 한 단계 더’ 가 낮추는 단 수 (한 등급 = 3단). */
    const val EASIER_STEPS = 3
    /** B2 의 복습 문장 힌트가 보이는 틀린 횟수. */
    const val REVIEW_HINT_AFTER_WRONGS = 2
    /** 빈칸 고르기에서 복습 칸 하나가 낱말 점수에 더하는 값 (잘 아는 낱말일수록 먼저 빈칸). */
    const val BOX_WEIGHT = 2

    /** 도움을 정하는 등급: [easier] 면 [EASIER_STEPS] 단 아래 (A1 · 1단 아래로는 내려가지 않는다). */
    fun grade(g: Reading.Grade, easier: Boolean): Reading.Grade = if (easier) Reading.Grade.of(g.index - EASIER_STEPS) else g

    // ── 번역 줄 (편지 방) ──────────────────────────────────
    /**
     * 번역 줄의 진하기 (1 = 또렷이). 접지 않고 옅어지기만 한다: A1 1 · A2 0.85 · B1 0.7 · B2 0.55.
     * 작품 설정의 ‘실력만큼 번역 옅게’ ([SeriesSettings.fadeRead]) 를 끄면 [on] = false 로 늘 1.
     */
    fun readAlpha(band: Reading.Band, on: Boolean = true): Float = if (!on) 1f else when (band) {
        Reading.Band.A1 -> 1f
        Reading.Band.A2 -> 0.85f
        Reading.Band.B1 -> 0.7f
        Reading.Band.B2 -> 0.55f
    }

    // ── 낱말 아래 뜻 · 예열 · 느린 듣기 (A1 · A2 를 넓게 돕는다) ─────────
    /** 편지 문장의 낱말 아래 뜻 (행간 뜻): B2 아래에서 기본으로, 낱말마다 아직 내 것이 아닌 낱말에만 ([interlinearWord]). */
    fun interlinear(band: Reading.Band): Boolean = band != Reading.Band.B2

    /** 이 낱말 아래에 뜻을 다나 (복습 칸 [box]): 잘 아는 낱말 ([knows]) 이 아니면. */
    fun interlinearWord(band: Reading.Band, box: Int): Boolean = interlinear(band) && !knows(box)

    /** 편지 앞 예열: ON = 기본으로 함 (A1 · A2), OPTIONAL = 기본으로 하되 끌 수 있음 (B1), OFF = 없음 (B2). */
    enum class WarmUp { ON, OPTIONAL, OFF }

    fun warmUp(band: Reading.Band): WarmUp = when (band) {
        Reading.Band.A1, Reading.Band.A2 -> WarmUp.ON
        Reading.Band.B1 -> WarmUp.OPTIONAL
        Reading.Band.B2 -> WarmUp.OFF
    }

    /** 느린 듣기가 기본인 단계 (A1 · A2): 기본 빠르기 ([Listen.auto]) 가 1 보다 느리다. */
    fun slowByDefault(band: Reading.Band): Boolean = band == Reading.Band.A1 || band == Reading.Band.A2

    // ── 이해 확인 ──────────────────────────────────────────
    /** 이해 확인의 질문 · 보기 언어. */
    data class CheckLangs(val question: Lang, val options: Lang)

    fun check(band: Reading.Band, learn: Lang, read: Lang): CheckLangs = when (band) {
        Reading.Band.A1, Reading.Band.A2 -> CheckLangs(read, read)
        Reading.Band.B1 -> CheckLangs(learn, read)
        Reading.Band.B2 -> CheckLangs(learn, learn)
    }

    /** 이해 확인의 지문 번역: 어느 단계에서도 눌러서 볼 수 있다 (B2 도 번역은 눌러서, 맞힌 뒤에는 누구나 한 줄). */
    @Suppress("UNUSED_PARAMETER")
    fun checkTranslationTappable(band: Reading.Band): Boolean = true

    // ── 오늘의 회화 · 내 차례 ──────────────────────────────
    enum class Talk {
        /** 원문 전체 + 번역 보임 → 듣고 따라 말하기. */
        SHADOW,
        /** 원문에 빈칸 하나 (그 줄의 덩어리, 잘 아는 것 먼저) + 칩 → 채워 말하기. */
        BLANK,
        /** 번역 + 첫 낱말 + 낱말 칩 → 말하기, 힌트 한 번에 원문. */
        CHIPS,
        /** 번역만 → 말하기, 힌트 두 단 (첫 낱말 → 원문). 모르는 낱말은 칩으로 남긴다. */
        MEANING,
    }

    fun talk(band: Reading.Band): Talk = when (band) {
        Reading.Band.A1 -> Talk.SHADOW
        Reading.Band.A2 -> Talk.BLANK
        Reading.Band.B1 -> Talk.CHIPS
        Reading.Band.B2 -> Talk.MEANING
    }

    /** 힌트 단추의 단 수 (CHIPS 1: 원문, MEANING 2: 첫 낱말 → 원문). */
    fun talkHints(t: Talk): Int = when (t) { Talk.CHIPS -> 1; Talk.MEANING -> 2; else -> 0 }

    /** 내 차례 줄을 처음부터 보여 주나 (힌트 [hints] 번 누른 뒤 포함). */
    fun talkLineShown(t: Talk, hints: Int): Boolean = when (t) {
        Talk.SHADOW -> true
        Talk.BLANK -> false
        else -> hints >= talkHints(t)
    }

    /** 첫 낱말을 보여 주나. */
    fun talkFirstWord(t: Talk, hints: Int): Boolean = t == Talk.CHIPS || (t == Talk.MEANING && hints >= 1)

    // ── 답장 ──────────────────────────────────────────────
    /** 답장 사다리의 도움 ([Ladder.help]). */
    fun reply(g: Reading.Grade): Ladder.Help = Ladder.help(g)

    /** 답장 말해 보기에서 문장을 어떻게 보이나. */
    enum class Speak { SHOWN, FADED, HIDDEN }

    fun replySpeak(band: Reading.Band): Speak = when (band) {
        Reading.Band.A1, Reading.Band.A2 -> Speak.SHOWN
        Reading.Band.B1 -> Speak.FADED
        Reading.Band.B2 -> Speak.HIDDEN
    }

    // ── 복습 문장 힌트 ─────────────────────────────────────
    enum class ReviewHint { NOW, TAP, AFTER_WRONGS }

    fun reviewHint(band: Reading.Band): ReviewHint = when (band) {
        Reading.Band.A1 -> ReviewHint.NOW
        Reading.Band.A2, Reading.Band.B1 -> ReviewHint.TAP
        Reading.Band.B2 -> ReviewHint.AFTER_WRONGS
    }

    /** 복습 문장 힌트를 지금 보이나: 답이 났으면 늘, 아니면 규칙대로 ([tapped] 힌트를 눌렀나, [wrongs] 틀린 횟수). */
    fun reviewHintShown(h: ReviewHint, tapped: Boolean, wrongs: Int, answered: Boolean): Boolean = answered || when (h) {
        ReviewHint.NOW -> true
        ReviewHint.TAP -> tapped
        ReviewHint.AFTER_WRONGS -> wrongs >= REVIEW_HINT_AFTER_WRONGS
    }

    /** 힌트 단추를 보여 주나 (누르면 보이는 규칙일 때만). */
    fun reviewHintTappable(h: ReviewHint): Boolean = h == ReviewHint.TAP

    // ── 낱말마다 ──────────────────────────────────────────
    /** 잘 아는 낱말인가. */
    fun knows(box: Int): Boolean = box >= KNOWN_BOX

    /** 빈칸 점수: 낱말 점수 [base] (0 이면 비우지 않는 낱말) 에 익숙함을 더한다. */
    fun blankScore(base: Int, box: Int): Int = if (base <= 0) 0 else base + box.coerceAtLeast(0) * BOX_WEIGHT

    /** 덩어리들 가운데 비울 것: 덩어리 낱말들의 칸 평균이 가장 높은 것 (같으면 앞의 것). 없으면 null. */
    fun pickBlank(chunks: List<String>, box: (String) -> Int): String? =
        chunks.withIndex().maxWithOrNull(compareBy<IndexedValue<String>> { (_, c) ->
            val ws = c.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (ws.isEmpty()) 0.0 else ws.sumOf { box(it).toDouble() } / ws.size
        }.thenByDescending { it.index })?.value

    /** 등급이 올라도 보여 줄 낱말: 아직 잘 모르는 낱말 (차례 그대로). */
    fun keepVisible(words: List<String>, box: (String) -> Int): List<String> = words.filter { !knows(box(it)) }

    /** 등급이 [from] → [to] 로 오르며 도움이 줄었나 (등급이 바뀌거나 답장 도움이 바뀌면). */
    fun lessened(from: Reading.Grade, to: Reading.Grade): Boolean =
        to.index > from.index && (from.band != to.band || Ladder.help(from) != Ladder.help(to))
}

/**
 * 낱말 카드의 발음 줄: 배우는 말마다 다르게.
 * 영어는 IPA (강세 표시 그대로), 한국어는 소리 나는 대로 [ ] + 로마자 (+ 규칙 한 줄 [note]), 프랑스어 · 독일어는 지금처럼 IPA.
 */
object Pron {
    /**
     * 발음 줄 (괄호까지, 없으면 빈 글자). [ownIpa] 는 이 낱말의 [Word.ipa] 가 배우는 말의 것인가
     * (원어를 배우거나, 배우는 말의 사전에서 온 낱말일 때).
     */
    fun line(w: Word, learn: Lang, ownIpa: Boolean): String = when (learn) {
        Lang.EN -> w.ipaEn.ifEmpty { if (ownIpa) w.ipa else "" }.let { if (it.isEmpty()) "" else "[$it]" }
        Lang.KO -> listOfNotNull(w.sayKo.takeIf { it.isNotEmpty() }?.let { "[$it]" }, w.romKo.takeIf { it.isNotEmpty() }).joinToString(" · ")
        else -> if (ownIpa && w.ipa.isNotEmpty()) "[${w.ipa}]" else ""
    }

    /** 한국어 소리 규칙 한 줄 (앱 글자 언어 [ui]). 한국어를 배울 때만. */
    fun note(w: Word, learn: Lang, ui: Lang): String = if (learn == Lang.KO) w.pronNote?.get(ui).orEmpty() else ""
}
