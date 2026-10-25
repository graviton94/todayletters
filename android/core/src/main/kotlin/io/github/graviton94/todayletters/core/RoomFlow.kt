package io.github.graviton94.todayletters.core

/**
 * 편지 방 읽기 흐름 (시안 room src4, 2026-10-25 허락 + 같은 날 ‘순간도 쉼’ 더함).
 *
 * 편지 한 통은 차례 ([timeline]): 편지 앞 순간 (after -1) → 문장 하나 → 그 문장 뒤 순간들 → … → 큐레이터 노트.
 * 이어 듣기는 이 차례를 따라간다: 문장은 낭독이 끝나면 [holdMs] 만큼 쉬고 (아래 금선이 찬다), 볼 것이 있는 순간
 * (사진 · 위치 · 날씨 · 알림 · 보낸 사람의 송금) 도 [momentHoldMs] 만큼 쉰다. 곡은 듣거나 넘길 때까지 (null).
 * 내가 (받는 사람이) 하는 일 ([Moment.Transfer.byReader]) 은 내가 눌러야 이어진다.
 * 사람이 누른 것이 이긴다: 읽은 문장 다시 듣기 · 낱말 · 사진 · 지도를 누르면 이어 듣기는 멈춘다 (화면 쪽).
 *
 * 낱말 시각 ([WordTimes], 없으면 마디 시각에서 [chunkWords]) 으로 지금 낱말을 따라가고 ([wordAt]),
 * 낱말 사이 쉼이 길면 건너뛴다 ([skipSilence]). 낱말에서 멈췄으면 그 낱말 바로 앞부터 ([resumeSeek]).
 */
object RoomFlow {
    /** 문장 사이 쉼 (메뉴 ‘문장 사이 쉼’: 짧게 · 보통 · 길게). 문장과 순간 모두에 곱한다. */
    enum class Pause(val factor: Float) { SHORT(0.5f), NORMAL(1f), LONG(1.6f) }

    const val HOLD_BASE_MS = 1800
    const val HOLD_PER_CHAR_MS = 40
    const val HOLD_CAP_MS = 5000

    /** 문장이 끝난 뒤 쉼: 번역 길이 [translation] (글자) 만큼 min(5000, 1800 + 40 × 길이), 쉼 설정을 곱한다. */
    fun holdMs(translation: Int, pause: Pause = Pause.NORMAL): Long =
        (minOf(HOLD_CAP_MS, HOLD_BASE_MS + HOLD_PER_CHAR_MS * translation.coerceAtLeast(0)) * pause.factor).toLong()

    const val PHOTO_BASE_MS = 3500
    const val PHOTO_PER_CHAR_MS = 30
    const val PHOTO_CAP_MS = 6000
    const val PLACE_MS = 2500
    const val GLANCE_MS = 2000

    /**
     * 순간을 볼 시간 (밀리초): 사진 · 스케치 3500 + 30 × 설명 길이 (6000 까지), 위치 2500, 날씨 · 알림 · 보낸 사람 쪽 송금 2000.
     * 곡은 null (듣거나 넘길 때까지 기다림). 내가 하는 송금은 쉼이 아니라 내 차례라 0 ([blocks]).
     */
    fun momentHoldMs(m: Moment, caption: Int = 0, pause: Pause = Pause.NORMAL): Long? {
        val base = when (m) {
            is Moment.Photo -> minOf(PHOTO_CAP_MS, PHOTO_BASE_MS + PHOTO_PER_CHAR_MS * caption.coerceAtLeast(0))
            is Moment.Location -> PLACE_MS
            is Moment.Weather, is Moment.Notice -> GLANCE_MS
            is Moment.Transfer -> if (m.byReader) return 0 else GLANCE_MS
            is Moment.Music -> return null
        }
        return (base * pause.factor).toLong()
    }

    /** 내가 해야 이어지는 순간인가 (받는 사람이 하는 송금). */
    fun blocks(m: Moment): Boolean = m is Moment.Transfer && m.byReader

    /** 차례 한 칸. */
    sealed interface Beat {
        /** 문장 [i] (메시지 번호). */
        data class Msg(val i: Int) : Beat
        /** 순간 [k] (넘겨준 순간 목록의 번호). */
        data class Mo(val k: Int) : Beat
        /** 큐레이터 노트 (편지 끝, 말풍선이 아닌 미술관 카드). */
        data object Note : Beat
    }

    /**
     * 차례: [afters] 는 순간마다 ‘몇 번째 문장 뒤’ (-1 이하 = 첫 문장 앞, 문장 수 이상 = 마지막 문장 뒤). 같은 자리의 순간은 넘겨준 차례대로.
     * [note] 면 맨 끝에 노트.
     */
    fun timeline(messages: Int, afters: List<Int>, note: Boolean): List<Beat> = buildList {
        afters.withIndex().filter { it.value < 0 }.forEach { add(Beat.Mo(it.index)) }
        for (i in 0 until messages) {
            add(Beat.Msg(i))
            afters.withIndex().filter { it.value == i || (i == messages - 1 && it.value >= messages) }.forEach { add(Beat.Mo(it.index)) }
        }
        if (messages == 0) afters.withIndex().filter { it.value >= 0 }.forEach { add(Beat.Mo(it.index)) }
        if (note) add(Beat.Note)
    }

    /**
     * 다시 열었을 때 이미 놓인 칸 수: 문장 [shown] 개가 놓였으면 [shown] 번째 문장 칸 앞까지 (그 사이 순간은 놓인 것, 내가 할 일은 한 것).
     * 다 놓였으면 노트까지 모두. 하나도 없으면 0 (편지 앞 순간부터 다시).
     */
    fun visibleFor(beats: List<Beat>, shown: Int): Int {
        if (shown <= 0) return 0
        val k = beats.indexOf(Beat.Msg(shown))
        return if (k < 0) beats.size else k
    }

    /** 문장 [i] 의 칸 번호 (없으면 -1). */
    fun beatOf(beats: List<Beat>, i: Int): Int = beats.indexOf(Beat.Msg(i))

    /** [from] 칸부터 처음 나오는 문장 번호 (없으면 null): 멈춘 띠의 “M번째부터”. */
    fun firstMsgFrom(beats: List<Beat>, from: Int): Int? =
        beats.drop(from.coerceAtLeast(0)).firstNotNullOfOrNull { (it as? Beat.Msg)?.i }

    /** 낱말을 조금 일찍 밝힌다 (눈이 소리보다 먼저 가게). */
    const val EARLY_MS = 40
    /** 지금 낱말이 끝난 뒤에도 이만큼은 ‘지금’ 으로 둔다 (다음 낱말이 오기 전 깜빡이지 않게). */
    const val LINGER_MS = 220

    /** 재생 위치 [pos] 에 읽는 낱말: 시작 ([EARLY_MS] 일찍) 이 지난 마지막 낱말 번호. 첫 낱말 전이면 -1. [words] 는 낱말마다 시작..끝 (밀리초). */
    fun wordAt(pos: Int, words: List<IntRange>): Int {
        var k = -1
        for (j in words.indices) if (pos >= words[j].first - EARLY_MS) k = j
        return k
    }

    /** [k] 번째 낱말이 아직 ‘지금’ 인가 (끝 + [LINGER_MS] 안), 아니면 말한 것. */
    fun lingering(pos: Int, words: List<IntRange>, k: Int): Boolean = k in words.indices && pos <= words[k].last + LINGER_MS

    /** 낱말 사이 이보다 긴 쉼은 줄인다. */
    const val GAP_MS = 600
    /** 앞 낱말이 끝난 뒤 이만큼은 그대로 쉰다. */
    const val REST_MS = 350
    /** 다음 낱말 이만큼 앞으로 건너뛴다. */
    const val LAND_MS = 250
    /** 다음 낱말 이만큼 앞보다 늦었으면 건너뛰지 않는다 (건너뛴 자리 [LAND_MS] 에서 다시 건너뛰지 않게, 시안의 되풀이 버그). */
    const val GUARD_MS = 300

    /**
     * 긴 쉼 건너뛰기: 재생 위치 [pos] 가 낱말 k 와 k+1 사이 (k 끝 + [REST_MS] 뒤 · k+1 시작 − [GUARD_MS] 앞) 이고 그 쉼이 [GAP_MS] 보다 길면
     * 옮길 자리 (k+1 시작 − [LAND_MS]), 아니면 null. 옮긴 자리는 k+1 시작 − [GUARD_MS] 보다 뒤라 다시 옮기지 않는다.
     */
    fun skipSilence(pos: Int, words: List<IntRange>): Int? {
        val k = wordAt(pos, words)
        if (k < 0 || k + 1 >= words.size) return null
        val end = words[k].last; val next = words[k + 1].first
        if (next - end <= GAP_MS) return null
        if (pos <= end + REST_MS || pos >= next - GUARD_MS) return null
        return next - LAND_MS
    }

    /** 낱말에서 이어 듣기: 그 낱말 시작 150ms 앞부터. */
    const val RESUME_LEAD_MS = 150

    fun resumeSeek(wordStart: Int): Int = (wordStart - RESUME_LEAD_MS).coerceAtLeast(0)

    /**
     * 화면 글 [text] 의 낱말 (띄어쓰기로 나눈 것, [WordTimes.tokens] 와 같은 차례) 마다 글자 범위.
     * 한국어 글 안의 이음표 ([Breaks.JOIN]) 는 낱말에 든다.
     */
    fun wordRanges(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = -1
        text.forEachIndexed { i, c ->
            if (c.isWhitespace()) { if (start >= 0) { out += start until i; start = -1 } }
            else if (start < 0) start = i
        }
        if (start >= 0) out += start until text.length
        return out.filter { r -> text.substring(r.first, r.last + 1).any { it != Breaks.JOIN } }
    }

    /**
     * 낱말 시각이 없을 때: 낭독 마디 시각 ([starts] · [ends] · [chunks] 글, 밀리초) 에서 낱말마다 어림 (마디 앞 쉼 [lead] 와 뒤 숨 [Spoken.TAIL_MS] 를 뺀
     * 목소리 몫을 글자 수대로 나눔). 낱말은 [text] 의 [wordRanges] 차례. 마디가 없으면 빈 목록.
     */
    fun chunkWords(text: String, starts: List<Int>, ends: List<Int>, chunks: List<String>, lead: Int = Spoken.LEAD_MS): List<IntRange> {
        val words = wordRanges(text)
        if (chunks.isEmpty() || words.isEmpty() || starts.size != chunks.size || ends.size != chunks.size) return emptyList()
        val spans = Spoken.ranges(text, chunks)
        fun letters(r: IntRange) = (r.first..minOf(r.last, text.lastIndex)).count { text[it].isLetterOrDigit() }.coerceAtLeast(1)
        return words.map { w ->
            val c = spans.indexOfFirst { w.first in it }.let { if (it < 0) spans.indexOfLast { s -> s.first <= w.first }.coerceAtLeast(0) else it }
            val a = starts[c] + lead
            val b = (ends[c] - Spoken.TAIL_MS).coerceAtLeast(a + 1)
            val inChunk = words.filter { it.first in spans[c] }.ifEmpty { listOf(w) }
            val total = inChunk.sumOf { letters(it) }
            val before = inChunk.takeWhile { it != w }.sumOf { letters(it) }
            val s0 = a + ((b - a).toLong() * before / total).toInt()
            val e0 = a + ((b - a).toLong() * (before + letters(w)) / total).toInt()
            s0..maxOf(s0, e0 - 1)
        }
    }
}
