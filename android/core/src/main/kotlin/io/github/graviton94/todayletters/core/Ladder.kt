package io.github.graviton94.todayletters.core

/**
 * 답장 사다리: 받는 사람(테오 · 누나 · 아버지)이 되어 쓰는 답장. 언어 등급([Reading.Grade])이 오를수록 도움이 줄어든다.
 *
 *  A1 · 1단     잇기       (별자리 잇기: 낱말 별을 순서대로)
 *  A1 · 2단 ~ A2  빈칸 하나  (낱말 셋 가운데 고르기: 떠올릴 수 있는 낱말 20개쯤, 대략 4~5일째부터)
 *  B1          빈칸 둘    (같은 낱말의 다른 꼴이 미끼로 섞인다)
 *  B2          직접 쓰기  (빈칸 둘을 쳐서 쓰기, 틀리면 한 글자씩 힌트)
 *
 * 답장은 빈센트(모차르트) 편지의 한 문장에 대답한다 ([Letter.replyQuote]). 쓴 답장은 복습 카드가 된다 ([CardRef.reply]).
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
}
