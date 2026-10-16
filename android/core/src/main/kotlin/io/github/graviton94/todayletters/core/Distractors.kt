package io.github.graviton94.todayletters.core

/**
 * 뜻 고르기의 틀린 보기 (복습 · 편지 걸음의 낱말 맞추기).
 * 보기는 정답과 같은 갈래여야 한다: 낱말 뜻 ↔ 낱말 뜻, 표현 ↔ 표현, 답장 문장 ↔ 문장. 같은 작품(같은 덱 · 같은 읽는 말)에서만.
 * 정답과 같은 뜻 · 같은 낱말(다른 편지의 같은 낱말) · 빈 글 · 겹치는 글은 넣지 않는다.
 * 고르는 차례: 같은 품사이고 길이가 비슷한 것 → 같은 품사 → 길이가 비슷한 것 → 나머지 (같은 차례 안에서는 [seed] 로 섞는다).
 */
object Distractors {
    enum class Kind { WORD, PHRASE, SENTENCE }

    /** 보기 후보 하나. [text] 는 보일 뜻, [key] 는 같은 낱말을 알아보는 열쇠 (배우는 말 꼴), [group] 은 작품. */
    data class Item(val text: String, val kind: Kind, val pos: String = "", val key: String = "", val group: String = "")

    /** 뜻 글을 뜻 낱개로 (“study (sketch)”, “country, land”, “작은; 조금”). 비교용으로 소문자 · 띄어쓰기 정리. */
    fun senses(x: String): Set<String> =
        norm(x).split(Regex("[,;/()·]")).map { it.trim().removePrefix("to ").trim() }.filter { it.isNotEmpty() }.toSet()

    private fun norm(x: String) = Breaks.plain(x).lowercase().replace(Regex("[.!?…:\"“”«»„]"), "").replace(Regex("\\s+"), " ").trim()

    /** 길이 비슷함: 짧은 쪽 / 긴 쪽 이 이만큼 이상이면 비슷하다. */
    const val NEAR = 0.5

    fun pick(answer: Item, pool: List<Item>, n: Int = 3, seed: Long = 0, exclude: Collection<String> = emptyList()): List<String> {
        val aNorm = norm(answer.text)
        val aSenses = senses(answer.text)
        val banned = (exclude.map(::norm) + aNorm).toSet()
        val bannedSenses = aSenses + exclude.flatMap(::senses)
        val aKey = norm(answer.key)
        val seen = HashSet<String>()
        val ok = pool.filter { c ->
            val t = norm(c.text)
            t.isNotEmpty() && c.kind == answer.kind &&
                (answer.group.isEmpty() || c.group == answer.group) &&
                t !in banned && (aKey.isEmpty() || norm(c.key) != aKey) &&
                senses(c.text).none { it in bannedSenses } && seen.add(t)
        }
        val rnd = java.util.Random(seed)
        fun near(c: Item): Boolean {
            val a = Breaks.plain(answer.text).length.toDouble(); val b = Breaks.plain(c.text).length.toDouble()
            return minOf(a, b) / maxOf(a, b).coerceAtLeast(1.0) >= NEAR
        }
        fun tier(c: Item): Int {
            val samePos = answer.pos.isNotEmpty() && c.pos == answer.pos
            return when { samePos && near(c) -> 0; samePos -> 1; near(c) -> 2; else -> 3 }
        }
        // 고른 보기끼리도 뜻이 겹치지 않게
        val chosen = mutableListOf<Item>()
        val used = HashSet<String>()
        for (c in ok.shuffled(rnd).sortedBy(::tier)) {
            if (chosen.size >= n) break
            val s = senses(c.text)
            if (s.any { it in used }) continue
            chosen += c; used += s
        }
        return chosen.map { it.text }
    }

    /** 표현 카드 · 답장 카드가 아닌 낱말 카드의 갈래: 뜻이 길어도 낱말이면 낱말. */
    fun kindOf(phrase: Boolean, reply: Boolean): Kind = when { reply -> Kind.SENTENCE; phrase -> Kind.PHRASE; else -> Kind.WORD }
}
