package io.github.graviton94.todayletters.core

/**
 * 낭독 소리의 낱말 시각: 소리 파일 옆 `<소리 이름>.words.json` (scripts/align_words.py · align.yml, 강제 정렬로 잰 값).
 *
 *     {"v": 1, "ok": true, "words": [{"t": "Mon", "s": 812, "e": 990, "q": 0.83}, …]}
 *
 * [words] 는 앱이 그 소리와 함께 보여 주는 글을 띄어쓰기로 나눈 낱말 차례 그대로 ([tokens] 와 같은 나누기, 화면 글자 그대로).
 * s · e 는 그 소리 처음부터 밀리초 (마디 시각 m*.json 과 달리 앞 쉼이 없다: 목소리가 실제로 나오는 때).
 * q 는 정렬 점수 0..1, 없으면 소리가 없는 낱말 (— · 같은 기호뿐, s = e = 앞 낱말의 끝).
 * [ok] 가 false 면 정렬이 약하다: 화면은 마디 시각 (Spoken) 으로 돌아가는 것이 낫다.
 * 없는 파일 · 모르는 판 (v) · 깨진 글은 null ([parse]).
 */
data class WordTimes(val words: List<Word>, val ok: Boolean) {
    data class Word(val text: String, val start: Int, val end: Int, val score: Float?) {
        /** 소리가 있는 낱말인가 (기호뿐이면 false). */
        val voiced get() = score != null
    }

    /** 재생 위치 [ms] 에 읽고 있는 낱말 번호: 시작이 지난 마지막 소리 있는 낱말. 첫 낱말 전이면 -1. */
    fun at(ms: Int): Int = words.indexOfLast { it.voiced && it.start <= ms }

    /** 낱말 [i]..[j] (포함) 의 시간 범위 (밀리초). 소리 있는 낱말이 없으면 null. */
    fun span(i: Int, j: Int): IntRange? {
        val v = words.subList(i.coerceAtLeast(0), (j + 1).coerceAtMost(words.size)).filter { it.voiced }
        if (v.isEmpty()) return null
        return v.first().start..v.maxOf { it.end }
    }

    /**
     * 긴 마디를 [max] 낱말 이하 조각 (낱말 번호 범위) 으로: 가장 긴 숨 (앞 낱말 끝 → 다음 낱말 시작) 에서 먼저 자르고,
     * 숨이 비슷하면 구두점으로 끝나는 낱말 뒤를 고른다. 조각은 [min] 낱말 아래로 작아지지 않게 (되도록).
     * 따라 읽기 칸을 en · ko 에서 짧게 나눌 때 쓴다.
     */
    fun pieces(max: Int, min: Int = 2): List<IntRange> {
        if (words.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        fun split(r: IntRange) {
            if (r.last - r.first + 1 <= max) { out += r; return }
            val lo = (r.first + min - 1).coerceAtMost(r.last - 1)
            val hi = (r.last - min).coerceAtLeast(r.first)
            val cuts = if (lo <= hi) lo..hi else r.first until r.last
            val best = cuts.maxBy { k ->
                val gap = (nextStart(k + 1) - words[k].end).coerceAtLeast(0)
                gap + (if (PUNCT.containsMatchIn(words[k].text)) 120 else 0)
            }
            split(r.first..best); split(best + 1..r.last)
        }
        split(words.indices)
        return out
    }

    private fun nextStart(k: Int): Int = words.drop(k).firstOrNull { it.voiced }?.start ?: words.last().end

    companion object {
        const val VERSION = 1
        private val PUNCT = Regex("[,.;:!?…—–)\\]»”’。、]$")

        /** 소리 경로 (…/m1_fr.m4a) → 낱말 시각 경로 (…/m1_fr.words.json). */
        fun pathFor(audio: String): String = audio.removeSuffix(".m4a") + ".words.json"

        /** 받기에서 빠져도 되는 파일 (이미 받은 장을 이것 때문에 다시 받게 하지 않는다, data/Downloads.kt ready). */
        fun optional(path: String): Boolean = path.endsWith(".words.json")

        /** 앱과 같은 낱말 나누기 (이음표를 빼고 띄어쓰기로). 화면 글의 낱말과 [words] 를 맞출 때. */
        fun tokens(text: String): List<String> = Breaks.plain(text).split(Regex("\\s+")).filter { it.isNotEmpty() }

        fun parse(text: String): WordTimes? = runCatching {
            @Suppress("UNCHECKED_CAST")
            val o = Json.parse(text) as Map<String, Any?>
            if ((o["v"] as? Double)?.toInt() != VERSION) return null
            val ws = (o["words"] as List<*>).map { w ->
                val m = w as Map<*, *>
                Word(m["t"] as String, (m["s"] as Double).toInt(), (m["e"] as Double).toInt(), (m["q"] as? Double)?.toFloat())
            }
            WordTimes(ws, o["ok"] as? Boolean ?: true)
        }.getOrNull()

        /** [parse] 하고, 화면 글 [shown] 의 낱말과 차례 · 글자가 다르면 null (글을 고친 뒤 옛 시각을 쓰지 않게). */
        fun parseFor(text: String, shown: String): WordTimes? = parse(text)?.takeIf { wt -> wt.words.map { it.text } == tokens(shown) }
    }
}
