package io.github.graviton94.todayletters.core

/**
 * 답장 사다리: 받는 사람(테오 · 누나 · 아버지)이 되어 쓰는 답장. 언어 등급([Reading.Grade])이 오를수록 도움이 줄어든다.
 *
 *  A1 · 1단     잇기       (별자리 잇기: 낱말 별을 순서대로)
 *  A1 · 2단 ~ A2  빈칸 하나  (낱말 셋 가운데 고르기: 떠올릴 수 있는 낱말 20개쯤, 대략 4~5일째부터)
 *  B1          빈칸 둘    (같은 낱말의 다른 꼴이 미끼로 섞인다)
 *  B2          직접 쓰기  (빈칸 둘을 쳐서 쓰기, 틀리면 한 글자씩 힌트)
 *
 * 답장은 턴 여럿 ([Letter.replies], I~II장 1턴 · III~V 2턴 · VI~ 3턴): 턴마다 편지의 한 문장에 대답하고 ([ReplyTurn.quote]),
 * 그 턴의 오늘의 문법 ([ReplyTurn.grammar]) 을 쓴다. 빈칸은 그 문법 덩어리 ([ReplyTurn.focus]) 먼저, 없거나 모자라면 낱말 점수로 ([holes]).
 * 쓴 답장은 턴마다 복습 카드가 된다 ([CardRef.reply]). 한국어를 배우면 말투 바꿔 보기 카드도 ([CardRef.style], [styleOptions]).
 */
object Ladder {
    enum class Help { LINK, CHOOSE, CHOOSE_TWO, WRITE }

    fun help(g: Reading.Grade): Help = when (g.index) {
        1 -> Help.LINK
        in 2..6 -> Help.CHOOSE
        in 7..9 -> Help.CHOOSE_TWO
        else -> Help.WRITE
    }

    /** 이 도움에서 비우는 빈칸 수. */
    fun blanks(h: Help): Int = when (h) {
        Help.LINK -> 0
        Help.CHOOSE -> 1
        Help.CHOOSE_TWO, Help.WRITE -> 2
    }

    /** 답장 문장을 낱말로 (띄어쓰기 단위). */
    fun tokens(sentence: String): List<String> = sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** 낱말의 알맹이: 앞뒤 문장부호를 뗀다 ("toi." → "toi", "«Oui»" → "Oui"). 글자가 없으면 "". */
    fun core(token: String): String = token.trim { !it.isLetterOrDigit() }

    /** 낱말 앞뒤에 붙은 문장부호 (빈칸 둘레에 그대로 둔다). */
    fun around(token: String): Pair<String, String> {
        val c = core(token)
        if (c.isEmpty()) return token to ""
        val at = token.indexOf(c)
        return token.substring(0, at) to token.substring(at + c.length)
    }

    /**
     * 비울 낱말 자리: [score] 가 0 보다 큰 낱말 가운데 점수가 높은 [n] 개 (같으면 뒤쪽 낱말), 문장 차례로.
     * 문장의 첫 낱말은 대문자 꼴이 정답을 알려 주지 않도록 다른 후보가 있으면 피한다.
     */
    fun pick(tokens: List<String>, n: Int, score: (String) -> Int): List<Int> {
        if (n <= 0) return emptyList()
        val cand = tokens.indices.map { it to score(core(tokens[it])) }.filter { it.second > 0 && core(tokens[it.first]).isNotEmpty() }
        val later = cand.filter { it.first > 0 }.ifEmpty { cand }
        return later.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
            .map { it.first }.distinctBy { core(tokens[it]).lowercase() }.take(n).sorted()
    }

    /**
     * 고르기 낱말: 정답 하나와 미끼 [decoys] 개를 섞는다. 미끼는 [near] (같은 낱말의 다른 꼴) 먼저, 모자라면 [pool] 에서.
     * 정답과 글자가 같은 것 (대소문자 무시) 은 미끼가 되지 않는다. 같은 [seed] 면 늘 같은 판.
     */
    fun chips(answer: String, near: List<String>, pool: List<String>, seed: Int, decoys: Int = 2): List<String> {
        val a = answer.lowercase()
        val picks = (near.shuffled(java.util.Random(seed.toLong())) + pool.shuffled(java.util.Random(seed * 31L + 7)))
            .map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() != a }.distinctBy { it.lowercase() }.take(decoys)
        return (picks + answer).shuffled(java.util.Random(seed * 17L + 3))
    }

    /** 쓰기 칸의 힌트: 앞에서 [n] 글자 (정답보다 길게 주지 않는다). */
    fun hint(answer: String, n: Int): String = answer.take(n.coerceIn(0, (answer.length - 1).coerceAtLeast(0)))

    /** 빈칸에 쓴 답이 맞나: 받아쓰기처럼 너그럽게 ([Memory.grade]: 대소문자 · 앞뒤 문장부호 무시, 악상만 틀리면 맞음). */
    fun right(answer: String, typed: String): Boolean = Memory.grade(answer, typed) != Memory.Mark.WRONG

    /** 낱말이 문장의 어디에 있나: 낱말마다 알맹이([core])의 글자 범위 (알맹이가 없으면 빈 범위). [tokens] 와 같은 차례. */
    fun spans(sentence: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var at = 0
        for (t in tokens(sentence)) {
            val i = sentence.indexOf(t, at).coerceAtLeast(0)
            at = i + t.length
            val c = core(t)
            val j = if (c.isEmpty()) -1 else t.indexOf(c)
            out += if (j < 0) IntRange.EMPTY else (i + j) until (i + j + c.length)
        }
        return out
    }

    /** 덩어리 [chunk] 가 문장의 어디에 있나: 낱말 경계에 맞는 첫 자리, 없으면 그냥 첫 자리, 그것도 없으면 null (대소문자 무시). */
    fun find(sentence: String, chunk: String, from: Int = 0): IntRange? {
        val c = chunk.trim()
        if (c.isEmpty()) return null
        fun edge(k: Int) = k < 0 || k >= sentence.length || !sentence[k].isLetterOrDigit()
        var first: IntRange? = null
        var i = sentence.indexOf(c, from, ignoreCase = true)
        while (i >= 0) {
            val r = i until i + c.length
            if (edge(i - 1) && edge(i + c.length)) return r
            if (first == null) first = r
            i = sentence.indexOf(c, i + 1, ignoreCase = true)
        }
        return first
    }

    /**
     * 비울 자리 [n] 개 (문장 안 글자 범위, 문장 차례로): 그 턴의 문법 덩어리 [focus] 를 먼저 (겹치지 않게, 앞에서부터),
     * 모자라면 [pick] (낱말 점수 [score]) 으로 채운다. 덩어리가 문장에 없으면 건너뛴다.
     */
    fun holes(sentence: String, focus: List<String>, n: Int, score: (String) -> Int): List<IntRange> {
        if (n <= 0) return emptyList()
        val out = mutableListOf<IntRange>()
        fun free(r: IntRange) = out.none { it.first <= r.last && r.first <= it.last }
        for (f in focus) {
            if (out.size >= n) break
            find(sentence, f)?.takeIf(::free)?.let { out += it }
        }
        if (out.size < n) {
            val sp = spans(sentence)
            val toks = tokens(sentence)
            // 덩어리와 겹치는 낱말은 빈 글자로 가려서 고르지 않게
            val masked = toks.mapIndexed { i, t -> if (!sp[i].isEmpty() && free(sp[i])) t else "" }
            pick(masked, n - out.size, score).forEach { out += sp[it] }
        }
        return out.take(n).sortedBy { it.first }
    }

    /** 덩어리 [focus] 가운데 비울 수 있는 것 (문장에 있는 것) 만. */
    fun focusIn(sentence: String, focus: List<String>): List<String> = focus.filter { find(sentence, it) != null }

    /** 말풍선의 한 조각: 글자, 또는 빈칸 ([hole] 번째, 0부터). */
    data class Piece(val text: String, val hole: Int = -1)

    /**
     * 말풍선 낱말 묶음: 띄어쓰기로 나눈 묶음마다 조각들. 빈칸이 낱말 가운데 있어도 (J'＿＿) 앞뒤 글자와 한 묶음이라 붙어서 보인다.
     * 빈칸 덩어리 안의 띄어쓰기 (ai lu) 는 나누지 않는다.
     */
    fun layout(sentence: String, holes: List<IntRange>): List<List<Piece>> {
        val units = mutableListOf<MutableList<Piece>>(mutableListOf())
        val buf = StringBuilder()
        fun flush() { if (buf.isNotEmpty()) { units.last() += Piece(buf.toString()); buf.clear() } }
        var i = 0
        val sorted = holes.withIndex().sortedBy { it.value.first }
        var h = 0
        while (i < sentence.length) {
            val next = sorted.getOrNull(h)
            if (next != null && i == next.value.first) {
                flush(); units.last() += Piece(sentence.substring(next.value), next.index)
                i = next.value.last + 1; h++
                continue
            }
            val ch = sentence[i]
            if (ch.isWhitespace()) { flush(); if (units.last().isNotEmpty()) units += mutableListOf<Piece>() }
            else buf.append(ch)
            i++
        }
        flush()
        return units.filter { it.isNotEmpty() }
    }

    /**
     * 덩어리 빈칸의 미끼 재료: 덩어리의 낱말마다 같은 낱말의 다른 꼴 ([formsOf], 사전의 꼴들). 여러 낱말 덩어리 (ai lu) 면
     * 그 낱말 하나만 (lu) 도 미끼가 된다 (조동사를 빠뜨린 꼴). 정답과 같은 것은 [chips] 가 걸러 낸다.
     */
    fun nearForms(chunk: String, formsOf: (String) -> List<String>): List<String> {
        val words = tokens(chunk).map(::core).filter { it.isNotEmpty() }
        val forms = words.reversed().flatMap { w -> formsOf(w).filter { !it.equals(w, ignoreCase = true) } }
        return (forms + if (words.size > 1) words.filter { it.length > 2 } else emptyList()).distinctBy { it.lowercase() }
    }

    // ── 말투 바꿔 보기 (한국어를 배울 때만) ─────────────────────
    /** 이 카드가 오늘 묻는 말투: 반말이면 true, 높임이면 false (카드 열쇠 · 날로 번갈아). */
    fun askLow(key: String, day: Long): Boolean = ((key.hashCode().toLong() + day) and 1L) == 0L

    /** 문장 나누기 (. ! ? 뒤 띄어쓰기). */
    private fun sentences(t: String): List<String> = t.trim().split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }

    /**
     * 말투 바꿔 보기의 보기 2~3개: 정답 [target] (반말 또는 높임), 다른 말투 [other], 그리고 문장이 둘 이상이면 앞은 정답 말투 ·
     * 뒤는 다른 말투로 섞인 것 (말투가 오락가락하는 흔한 실수). 같은 [seed] 면 늘 같은 차례.
     */
    fun styleOptions(target: String, other: String, seed: Int): List<String> {
        val a = sentences(target); val b = sentences(other)
        val mixed = if (a.size >= 2 && a.size == b.size) (listOf(a.first()) + b.drop(1)).joinToString(" ") else null
        return (listOfNotNull(target, other, mixed).filter { it.isNotBlank() }.distinct()).shuffled(java.util.Random(seed.toLong()))
    }
}
