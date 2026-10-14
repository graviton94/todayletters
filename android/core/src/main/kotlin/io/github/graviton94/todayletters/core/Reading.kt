package io.github.graviton94.todayletters.core

/**
 * 읽을 수 있는 비율과 언어 등급 (배우는 말마다). 모두 기기 안의 카드와 편지 낱말로만 센다.
 *
 * ‘아는 낱말’ = 기능어 (관사 · 전치사 · 대명사 …) · 이름 · 숫자, 그리고 내 카드에서 떠올리기([Growth.known]) 이상인 낱말.
 * 편지 한 통의 비율은 그 편지의 모든 낱말 (gloss.json 의 토큰, 같은 낱말이 여러 번이면 여러 번) 가운데 아는 낱말의 몫이다.
 * 실제 편지로 재 보면 처음엔 60% 안팎에서 시작해 한 작품을 다 익히면 80% 안팎에 닿는다 (그래서 등급 문턱도 이 범위에 둔다).
 */
object Reading {
    /** 카드가 없어도 아는 것으로 치는 낱말: 기능어 · 이름 · 숫자 · 약어 · 외국어. */
    fun free(e: LexEntry?): Boolean = e != null &&
        (e.function || e.pos == "name" || e.pos == "num" || e.pos == "abbr" || e.pos.startsWith("foreign"))

    /** [keys] (낱말 사전 열쇠, 나온 차례 그대로) 가운데 [known] 의 비율 (%, 버림). 낱말이 없으면 0. */
    fun pct(keys: List<String>, known: (String) -> Boolean): Int =
        if (keys.isEmpty()) 0 else keys.count(known) * 100 / keys.size

    /** 최근 [n] 통의 평균 (%). 없으면 0. */
    fun average(pcts: List<Int>, n: Int = RECENT): Int {
        val last = pcts.takeLast(n)
        return if (last.isEmpty()) 0 else last.sum() / last.size
    }
    const val RECENT = 5

    // ── 언어 등급 ──────────────────────────────────────────
    enum class Band { A1, A2, B1, B2 }

    /** 등급: [band] 와 그 안의 단 [step] (1~3). [index] 는 1 (A1 · 1단) … 12 (B2 · 3단). */
    data class Grade(val band: Band, val step: Int) {
        val index: Int get() = band.ordinal * 3 + step
        companion object {
            fun of(index: Int): Grade = (index.coerceIn(1, MAX) - 1).let { Grade(Band.entries[it / 3], it % 3 + 1) }
        }
    }
    const val MAX = 12

    /** 단마다 필요한 ‘내 것이 된 낱말’ 수 (떠올리기 이상, 이 언어의 모든 작품): A1 · 1단 0 … B2 · 3단 460. */
    val words = intArrayOf(0, 20, 40, 60, 90, 120, 160, 200, 250, 300, 380, 460)
    /** 등급 (A1 · A2 · B1 · B2) 에 들어가려면 최근 편지의 읽을 수 있는 비율 평균이 이만큼. */
    val coverage = intArrayOf(0, 60, 68, 75)

    /**
     * 지금 숫자로 닿는 등급. 낱말 수와 읽을 수 있는 비율을 함께 본다: 낱말이 많아도 비율이 등급 문턱에 못 미치면
     * 그 아래 등급의 3단에 머문다. [floor] 는 이미 닿았던 등급 (내려가지 않는다).
     */
    fun grade(known: Int, recent: Int, floor: Int = 1): Grade {
        var best = 1
        for (i in 1..MAX) {
            val band = (i - 1) / 3
            if (known >= words[i - 1] && recent >= coverage[band]) best = i
        }
        return Grade.of(maxOf(best, floor))
    }

    /** 다음 단까지 남은 낱말 수 (낱말만으로 닿는 경우). 맨 위면 null. */
    fun wordsToNext(g: Grade, known: Int): Int? = if (g.index >= MAX) null else (words[g.index] - known).coerceAtLeast(0)

    /** 다음 단이 새 등급이고 그 문턱이 비율이라면, 넘어야 할 비율. 아니면 null. */
    fun coverageToNext(g: Grade): Int? = if (g.index >= MAX || g.step != 3) null else coverage[g.band.ordinal + 1]

    // ── 번역 줄 물러나기 ────────────────────────────────────
    /**
     * 이 등급에서 번역 줄을 접는 문장의 아는 낱말 비율 문턱. A1 은 접지 않는다 (null), A2 는 100% (모두 아는 문장만), B1 95%, B2 90%.
     * 한 번에 다 빼지 않고 등급이 오를수록 조금씩 물러난다.
     */
    fun foldAt(band: Band): Int? = when (band) {
        Band.A1 -> null
        Band.A2 -> 100
        Band.B1 -> 95
        Band.B2 -> 90
    }

    /** 이 문장의 번역 줄을 접는가 (문장 낱말이 셋 이상일 때만: 한두 낱말 인사말로 들쭉날쭉하지 않게). */
    fun folds(band: Band, sentence: List<String>, known: (String) -> Boolean): Boolean {
        val at = foldAt(band) ?: return false
        return sentence.size >= 3 && pct(sentence, known) >= at
    }
}
