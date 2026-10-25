package io.github.graviton94.todayletters.core

/**
 * 오늘의 회화 · 내 차례 한 줄의 상태 (화면 없이 시험할 수 있는 순수 상태 기계). 도움은 [Support.Talk] 만큼:
 *
 *  - SHADOW (A1): 말풍선에 원문 + 번역. 아래 칸에 듣기 · 녹음 (녹음은 바로 된다).
 *  - BLANK  (A2): 말풍선에 빈칸 (그 줄의 덩어리) + 번역. 아래 칸에 칩 (정답 · 틀린 보기). 맞는 칩을 고르기 전에는 녹음이 막힌다.
 *                 틀린 칩은 흔들리고 놓이지 않는다 (다시 고를 수 있음). 맞는 칩이면 말풍선이 채워지고 녹음이 열린다.
 *  - CHIPS  (B1): 말풍선에 뜻 + 첫 낱말. 아래 칸에 남은 낱말 조각 (문장 차례가 아니게 섞음). 차례대로 눌러 문장을 쌓는다.
 *                 다음 낱말이 아닌 조각은 흔들리고 놓이지 않는다. 같은 글자의 조각은 어느 것이든 된다. 놓인 낱말은 틀릴 수 없어 되돌리기가 없다.
 *                 힌트는 다음 조각을 밝힌다. 문장을 다 쌓기 전에는 녹음이 막힌다.
 *  - MEANING (B2): 말풍선에 뜻만. 힌트 두 단 (첫 낱말 → 원문), 녹음은 바로 된다.
 *
 * 먼저 듣기 (시안 하루 흐름 ②, 2026-10-24): 문장이 다 보이고 원래 소리가 있으면 ([start] 의 listen) 먼저 [Phase.LISTEN] —
 * 내 문장을 한 번 들려주고, 끝까지 들은 뒤 ([Event.Listened]) 에야 녹음이 열린다. 소리가 없는 줄은 막지 않는다.
 * BLANK · CHIPS 는 답을 다 맞춘 뒤에 듣는다 (먼저 들으면 답이 들린다). MEANING 은 뜻만 보고 말하는 단계라 듣기 없이.
 *
 * 녹음 → 채점 ([Event.Heard]): 따라 읽기와 같은 부품 (원래 소리 · 기기 안 녹음 · [Prosody] 억양 · 리듬, 편지 걸음처럼 목소리가 들어가면 통과).
 * 맞으면 [Phase.PASSED] (원문 + 금빛 체크, ‘다음’ 으로 다음 상대 줄). 틀리면 [Phase.RETRY] (다시 해 봐요),
 * [MAX_FAILS] 번 틀리면 [Phase.REVEALED] (원문을 보여 주고 다음으로 갈 수 있다, 막히지 않음). 폰의 음성 인식 · 스스로 판정은 없다.
 * 맞은 뒤 · 원문을 보여 준 뒤에도 다시 말하기는 늘 열려 있다 ([State.canRecord]): 맞음은 거두지 않고, 원문을 본 뒤 맞히면 맞음.
 * 줄 하나만 건너뛰기는 없다 (걸음 전체의 건너뛰기만). 고르기만 하고 녹음하지 않으면 넘어가지 않는다.
 */
object TalkTurn {
    /** 이만큼 틀리면 원문을 보여 주고 넘어갈 수 있게 한다. */
    const val MAX_FAILS = 2
    /** 힌트 단 수: B1 은 다음 조각 밝히기 (몇 번이든), B2 는 첫 낱말 → 원문. */
    const val MEANING_HINTS = 2

    enum class Phase {
        /** 칩 고르기 · 조각 쌓기가 남음 (녹음 막힘). */
        BUILD,
        /** 내 문장의 원래 소리를 먼저 한 번 듣는 중 (녹음 막힘, 끝까지 들으면 [READY]). */
        LISTEN,
        /** 녹음할 수 있음. */
        READY,
        /** 한 번 틀림: 다시 녹음. */
        RETRY,
        /** 맞음 (원문 + 금빛 체크). */
        PASSED,
        /** [MAX_FAILS] 번 틀림: 원문을 보여 주고 ‘다음으로’. */
        REVEALED,
        /** 이 줄을 마침 (다음 상대 줄로). */
        DONE,
    }

    sealed interface Event {
        /** 아래 칸의 칩 [index] 를 눌렀다. */
        data class Pick(val index: Int) : Event
        /** 힌트 단추. */
        data object Hint : Event
        /** 녹음 채점 결과: [ok] 면 맞음. */
        data class Heard(val ok: Boolean) : Event
        /** 다음으로 (맞힌 뒤 · 원문을 보여 준 뒤). */
        data object Continue : Event
        /** 먼저 듣기를 끝까지 들었다 (또는 들을 소리가 없다). */
        data object Listened : Event
    }

    /**
     * 한 줄의 상태. [tokens] 는 띄어쓰기 낱말 (한국어는 어절, 문장부호 붙은 그대로), [keys] 는 맞춰 볼 알맹이 (문장부호 뗌 · 소문자, 문장부호만이면 "").
     * BLANK: [blank] 는 문장 안 글자 범위, [answer] 는 그 글자. CHIPS: [placed] 는 쌓은 알맹이 낱말 수 (첫 낱말 포함), [used] 는 쓴 칩.
     * [chips] 는 아래 칸의 칩 글자 (BLANK 은 보기, CHIPS 는 섞은 낱말 조각). [rejected] 는 방금 튕긴 칩, [shake] 는 흔들기 번호.
     */
    data class State(
        val help: Support.Talk,
        val sentence: String,
        val tokens: List<String>,
        val keys: List<String>,
        val blank: IntRange? = null,
        val answer: String? = null,
        val chips: List<String> = emptyList(),
        val filled: Boolean = true,
        val placed: Int = 0,
        val used: Set<Int> = emptySet(),
        val rejected: Int? = null,
        val shake: Int = 0,
        val hints: Int = 0,
        val fails: Int = 0,
        val phase: Phase = Phase.READY,
        /** 원래 소리가 있어 먼저 듣는가 ([Phase.LISTEN] 을 거친다). */
        val listen: Boolean = false,
    ) {
        /** 알맹이가 있는 낱말의 자리 (쌓을 차례). */
        val order: List<Int> get() = keys.indices.filter { keys[it].isNotEmpty() }

        /**
         * 녹음할 수 있나 (칩 · 조각 · 먼저 듣기를 마침). 채점된 뒤 (맞음 [Phase.PASSED] · 원문을 보여 줌 [Phase.REVEALED]) 에도 늘 다시 말할 수 있다
         * (2026-10-25 폰 시험: 맞힌 뒤 ‘다시 말하기’ 가 흐린 단추로 막혔다).
         */
        val canRecord: Boolean get() = phase == Phase.READY || phase == Phase.RETRY || phase == Phase.PASSED || phase == Phase.REVEALED

        /** 녹음 단추를 보여 주나 (막혀 있어도 보인다: 회색 + 까닭 한 줄). */
        val showsRecord: Boolean get() = phase == Phase.BUILD || phase == Phase.LISTEN || canRecord

        /** 말풍선에 원문 전체가 보이나. */
        val fullLine: Boolean get() = phase == Phase.PASSED || phase == Phase.REVEALED || phase == Phase.DONE ||
            when (help) {
                Support.Talk.SHADOW -> true
                Support.Talk.BLANK -> filled
                Support.Talk.CHIPS -> placed >= order.size
                Support.Talk.MEANING -> hints >= MEANING_HINTS
            }

        /** 쌓인 글 (CHIPS: 쌓은 낱말까지 문장부호 포함, MEANING 힌트 1: 첫 낱말). 원문 전체가 보이면 문장 그대로. */
        val built: String get() = when {
            fullLine -> sentence
            help == Support.Talk.CHIPS -> through(placed)
            help == Support.Talk.MEANING && hints >= 1 -> through(1)
            else -> ""
        }

        /** 알맹이 낱말 [n] 개까지의 글 (뒤따르는 문장부호만의 낱말도 붙인다). */
        private fun through(n: Int): String {
            if (n <= 0) return ""
            val o = order
            if (n > o.size) return sentence
            var end = o[n - 1]
            while (end + 1 < tokens.size && keys[end + 1].isEmpty()) end++
            return tokens.subList(0, end + 1).joinToString(" ")
        }

        /** 다음에 놓을 낱말의 알맹이 (CHIPS). 다 쌓았으면 null. */
        val nextKey: String? get() = order.getOrNull(placed)?.let { keys[it] }

        /** 힌트로 밝힐 칩: CHIPS 에서 힌트를 받았으면 다음 낱말과 같은 아직 안 쓴 칩. */
        val hinted: Int? get() = if (help != Support.Talk.CHIPS || hints <= 0 || phase != Phase.BUILD) null else nextChip

        private val nextChip: Int? get() = nextKey?.let { k -> chips.indices.firstOrNull { it !in used && key(chips[it]) == k } }

        /** 힌트 단추를 보여 주나. */
        val hintable: Boolean get() = when (help) {
            Support.Talk.CHIPS -> phase == Phase.BUILD
            Support.Talk.MEANING -> hints < MEANING_HINTS && (phase == Phase.READY || phase == Phase.RETRY)
            else -> false
        }

        /** 맞게 끝났나 (금빛 체크). */
        val passed: Boolean get() = phase == Phase.PASSED || (phase == Phase.DONE && fails < MAX_FAILS)
    }

    /** 맞춰 볼 알맹이: 앞뒤 문장부호를 떼고 소문자. */
    fun key(token: String): String = Ladder.core(Breaks.plain(token)).lowercase()

    /**
     * 내 차례 줄 하나를 시작한다. [sentence] 는 배우는 말 줄. BLANK 이면 [blank] (빈칸 자리) · [options] (보기 칩, 정답 포함) 를 화면이 고른 대로 받는다
     * (빈칸이 없으면 바로 녹음). CHIPS 면 첫 낱말을 두고 남은 낱말을 [seed] 로 섞는다 (문장 차례 그대로면 한 칸 돌린다).
     */
    fun start(help: Support.Talk, sentence: String, blank: IntRange? = null, options: List<String> = emptyList(), seed: Int = 0,
              /** 원래 소리가 있으면 true: 문장이 다 보일 때 먼저 듣기. */ listen: Boolean = false): State =
        ready(begin(help, sentence, blank, options, seed).copy(listen = listen && help != Support.Talk.MEANING))

    /** 녹음할 차례가 됐을 때: 먼저 들을 것이 있으면 [Phase.LISTEN]. */
    private fun ready(st: State): State = if (st.phase == Phase.READY && st.listen) st.copy(phase = Phase.LISTEN) else st

    private fun begin(help: Support.Talk, sentence: String, blank: IntRange?, options: List<String>, seed: Int): State {
        val s = Breaks.plain(sentence).trim()
        val tokens = Ladder.tokens(s)
        val keys = tokens.map(::key)
        val base = State(help, s, tokens, keys)
        return when (help) {
            Support.Talk.SHADOW, Support.Talk.MEANING -> base
            Support.Talk.BLANK -> {
                val b = blank?.takeIf { it.first >= 0 && it.last < s.length && !it.isEmpty() }
                if (b == null || options.isEmpty()) base
                else base.copy(blank = b, answer = s.substring(b), chips = options, filled = false, phase = Phase.BUILD)
            }
            Support.Talk.CHIPS -> {
                val order = keys.indices.filter { keys[it].isNotEmpty() }
                val rest = order.drop(1).map { Ladder.core(tokens[it]) }
                val mixed = shuffle(rest, seed)
                val st = base.copy(chips = mixed, placed = order.size.coerceAtMost(1))
                if (st.placed >= order.size) st else st.copy(phase = Phase.BUILD)
            }
        }
    }

    /** 섞기: 문장 차례와 같으면 한 칸 돌린다 (조각이 둘 이상이고 모두 같은 글자가 아니면). */
    fun shuffle(words: List<String>, seed: Int): List<String> {
        if (words.size < 2) return words
        val out = words.shuffled(java.util.Random(seed.toLong()))
        if (out.map(::key) != words.map(::key)) return out
        val rotated = words.drop(1) + words.first()
        return if (rotated.map(::key) != words.map(::key)) rotated else out
    }

    /** 사건 하나로 다음 상태. 지금 단계에 맞지 않는 사건은 무시한다 (녹음이 막힌 동안의 Heard 등). */
    fun reduce(st: State, e: Event): State = when (e) {
        is Event.Pick -> ready(pick(st, e.index))
        Event.Hint -> if (!st.hintable) st else st.copy(hints = st.hints + 1, rejected = null)
        // 맞힌 뒤 다시 말한 것은 맞음을 거두지 않는다 (안 되면 판의 ‘다시’ 한 줄만), 원문을 보여 준 뒤 맞히면 맞음
        is Event.Heard -> when {
            !st.canRecord -> st
            st.phase == Phase.PASSED -> st
            st.phase == Phase.REVEALED -> if (e.ok) st.copy(phase = Phase.PASSED) else st
            e.ok -> st.copy(phase = Phase.PASSED)
            else -> fail(st, Phase.RETRY)
        }
        Event.Continue -> if (st.phase == Phase.PASSED || st.phase == Phase.REVEALED) st.copy(phase = Phase.DONE) else st
        Event.Listened -> if (st.phase == Phase.LISTEN) st.copy(phase = Phase.READY) else st
    }

    private fun fail(st: State, again: Phase): State {
        val f = st.fails + 1
        return st.copy(fails = f, phase = if (f >= MAX_FAILS) Phase.REVEALED else again)
    }

    private fun pick(st: State, i: Int): State {
        if (st.phase != Phase.BUILD || i !in st.chips.indices || i in st.used) return st
        return when (st.help) {
            Support.Talk.BLANK -> {
                val a = st.answer ?: return st
                if (key(st.chips[i]) == key(a) && Breaks.plain(st.chips[i]).trim().equals(a.trim(), ignoreCase = true))
                    st.copy(filled = true, used = st.used + i, rejected = null, phase = Phase.READY)
                else st.copy(rejected = i, shake = st.shake + 1)
            }
            Support.Talk.CHIPS -> {
                val next = st.nextKey ?: return st
                if (key(st.chips[i]) != next) st.copy(rejected = i, shake = st.shake + 1)
                else {
                    val placed = st.placed + 1
                    st.copy(placed = placed, used = st.used + i, rejected = null, phase = if (placed >= st.order.size) Phase.READY else Phase.BUILD)
                }
            }
            else -> st
        }
    }
}

/**
 * 따라 읽기의 칸 하나: 오늘의 회화 내 줄 [talk] (0부터, 회화 줄이 아니면 -1) 또는 편지 문장 [msg] 의 마디 [chunk].
 * 회화 줄은 장이 갈수록 길어지는 말하기 계단이라 (I장 짧게 → 뒤 장 길게), 편지 문장보다 먼저 따라 말한다.
 */
data class ShadowSpot(val talk: Int = -1, val msg: Int = -1, val chunk: Int = 0) {
    val isTalk: Boolean get() = talk >= 0
    /** 통과 · 별 기록의 꼬리: 편지 마디는 예전 그대로 "<문장>:<마디>", 회화 줄은 "t<줄>:0". */
    val tag: String get() = if (isTalk) "t$talk:0" else "$msg:$chunk"
}

object ShadowLines {
    /** 회화의 내 줄 (번호, 차례대로): 배우는 말 [learn] 의 글이 있는 "me" 줄만. 회화가 없으면 빈 목록. */
    fun talkMe(talk: Talk?, learn: Lang): List<Int> =
        talk?.lines.orEmpty().withIndex().filter { (_, l) -> l.me && l.text.lines[learn]?.isNotBlank() == true }.map { it.index }

    /**
     * 따라 읽기 차례: 회화 내 줄 먼저 ([withTalk] 일 때, 편지 걸음), 그다음 편지 문장의 마디 (예전 그대로).
     * [chunks] 는 문장마다 마디 수, [only] 는 고른 문장만 (null 이면 모든 문장).
     */
    fun order(talk: Talk?, learn: Lang, chunks: List<Int>, only: List<Int>?, withTalk: Boolean = true): List<ShadowSpot> {
        val lines = if (withTalk) talkMe(talk, learn).map { ShadowSpot(talk = it) } else emptyList()
        val letter = chunks.indices.filter { only == null || it in only }.flatMap { i -> (0 until chunks[i]).map { ShadowSpot(msg = i, chunk = it) } }
        return lines + letter
    }
}
