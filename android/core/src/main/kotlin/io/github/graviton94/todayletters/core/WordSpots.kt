package io.github.graviton94.todayletters.core

import kotlin.math.roundToInt

/** 낱말 하나를 말한 때: [s] ~ [e] 밀리초 (소리의 시작에서). [t] 는 낱말 글자 (문장부호 붙은 그대로). */
data class WordAt(val t: String, val s: Int, val e: Int)

/**
 * 낱말 자리 (시안 하루 흐름 ③): 말하기 판의 낱말 줄 · 읽는 자리 밑줄 · 재생 자리. 낭독마다 미리 뽑은 낱말 시간 파일이 있으면 그것
 * (정렬 작업, `<소리>.words.json`), 없으면 기기에서 어림한 자리 ([estimate]): 글자로 센 음절을 소리 크기의 봉우리 (음절 핵) 에
 * 나눠 놓고, 쉼 · 문장부호 · 낭독 덩어리 시각에 맞춘다. 근거 · 시제품: 시안 audit/tools/words.py.
 *
 * 영어 · 한국어 편지는 문장 하나가 10~20초 한 덩어리라, 낱말 시간이 있으면 따라 읽기를 4초 안쪽 · 6~10 낱말 마디로 나눈다 ([split]).
 */
object WordSpots {
    /** 따라 읽기 한 마디의 길이 상한 (밀리초). */
    const val SPAN_MS = 4000
    private const val PAUSE_MIN_MS = 150
    private const val QUIET_DB = 32f
    private const val NUCLEUS_DB = 25f
    private const val NUCLEUS_GAP = 9
    private const val NUCLEUS_DIP = 2.5f
    private const val VOWELS = "aeiouyàâäéèêëîïôöùûüœæáíóúý"
    private val BREAK = Regex("[,.;:!?—–)’”]$")

    /** 글자로 센 음절 수 (0 이면 소리가 없는 낱말: 문장부호만 등). */
    fun syllables(word: String, lang: Lang): Int {
        val w = word.lowercase()
        if (lang == Lang.KO || lang == Lang.JA) {
            val h = w.count { it in '가'..'힣' || it in '぀'..'ヿ' || it in '一'..'鿿' }
            val lat = w.filter { it in 'a'..'z' }
            if (h == 0 && lat.isEmpty()) return 0
            return maxOf(1, h + if (lat.isNotEmpty()) syllables(lat, Lang.EN) else 0)
        }
        if (w.isNotEmpty() && w.all { it.isDigit() }) return 2 * w.length
        val clean = w.filter { it in 'a'..'z' || it in VOWELS || it == '\'' || it == 'ñ' || it == 'ß' }
        if (clean.isEmpty()) return 0
        val groups = Regex("[$VOWELS]+")
        return when (lang) {
            Lang.FR -> {
                val w2 = if (groups.findAll(clean).count() > 1) clean.replace(Regex("(es|ent|e)$"), "") else clean
                maxOf(1, groups.findAll(w2).count())
            }
            Lang.EN -> {
                val w2 = if (!clean.endsWith("le") && groups.findAll(clean).count() > 1) clean.removeSuffix("e") else clean
                maxOf(1, groups.findAll(w2).count())
            }
            else -> maxOf(1, groups.findAll(clean).count())
        }
    }

    /**
     * 어림한 낱말 자리. [db] 는 10ms 프레임의 크기 (소리 시작부터, [PitchLine.analyze] · [Prosody.analyze] 의 db).
     * [chunks] 는 낭독 덩어리 (밀리초 시각과 글, 소리 시작 기준) 가 둘 이상 있을 때: 덩어리마다 그 안에 낱말을 놓는다.
     * 없으면 [text] 를 쉼 · 문장부호로 나눠 맞춘다. 낱말이 없거나 소리가 없으면 빈 목록.
     */
    fun estimate(text: String, db: FloatArray, lang: Lang, chunks: List<Triple<Int, Int, String>> = emptyList()): List<WordAt> {
        if (db.isEmpty()) return emptyList()
        return if (chunks.size > 1) byChunks(chunks, db, lang) else byPauses(text, db, lang)
    }

    private fun envelope(db: FloatArray): FloatArray {
        val n = db.size
        return FloatArray(n) { i -> var s = 0f; for (k in -2..2) s += db[(i + k).coerceIn(0, n - 1)]; s / 5 }
    }

    private fun nuclei(db: FloatArray, a: Int, b: Int, top: Float): List<Int> {
        val s = envelope(db)
        val kept = ArrayList<Int>()
        for (i in maxOf(a, 1) until minOf(b, s.size - 1)) {
            if (!(s[i] > top - NUCLEUS_DB && s[i] >= s[i - 1] && s[i] > s[i + 1])) continue
            val q = kept.lastOrNull()
            if (q != null) {
                var dip = Float.MAX_VALUE
                for (k in q..i) dip = minOf(dip, s[k])
                if (i - q < NUCLEUS_GAP || minOf(s[q], s[i]) - dip < NUCLEUS_DIP) { if (s[i] > s[q]) kept[kept.lastIndex] = i; continue }
            }
            kept += i
        }
        return kept
    }

    /** 한 구간 ([sa], [sb] 프레임) 안에 낱말 [words] 를 음절 수만큼 핵에 나눠 놓는다. */
    private fun place(words: List<String>, sa: Int, sb: Int, db: FloatArray, top: Float, lang: Lang, out: MutableList<WordAt>) {
        if (words.isEmpty()) return
        val sy = words.map { maxOf(1, syllables(it, lang)) }
        val total = sy.sum()
        val nu = nuclei(db, sa, sb, top).ifEmpty { (1..total).map { sa + ((sb - sa).toLong() * it / (total + 1)).toInt() } }
        val n = nu.size
        var cum = 0
        val starts = ArrayList<Int>()
        words.indices.forEach { k ->
            val st = if (k == 0) sa else {
                val j = minOf(n - 1, (cum.toDouble() * n / total).roundToInt())
                val prev = if (j > 0) nu[j - 1] else sa
                (prev + nu[j]) / 2
            }
            cum += sy[k]
            starts += st
        }
        words.indices.forEach { k ->
            val e = if (k == words.lastIndex) sb else starts[k + 1]
            out += WordAt(words[k], starts[k] * Prosody.HOP_MS, maxOf(e, starts[k] + 1) * Prosody.HOP_MS)
        }
    }

    private fun byPauses(text: String, db: FloatArray, lang: Lang): List<WordAt> {
        val words = Regex("\\S+").findAll(text).map { it.value }.toList()
        if (words.isEmpty()) return emptyList()
        val top = db.max(); val n = db.size
        val act = (0 until n).filter { db[it] > top - QUIET_DB }
        val a0 = act.firstOrNull() ?: 0; val a1 = (act.lastOrNull() ?: (n - 1)) + 1
        // 쉼 (150ms 넘게 조용함, 안쪽만)
        val ps = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < a1) {
            if (db[i] >= top - QUIET_DB) { i++; continue }
            var j = i
            while (j < a1 && db[j] < top - QUIET_DB) j++
            if ((j - i) * Prosody.HOP_MS >= PAUSE_MIN_MS && i > 0 && j < a1 && i > a0) ps += i to j
            i = j
        }
        val segs = ArrayList<Pair<Int, Int>>()
        val bounds = listOf(a0) + ps.map { it.second }; val ends = ps.map { it.first } + a1
        bounds.indices.forEach { segs += bounds[it] to ends[it] }
        val phr = ArrayList<MutableList<Int>>(); phr += mutableListOf<Int>()
        words.indices.forEach { k -> phr.last() += k; if (BREAK.containsMatchIn(words[k]) && k < words.lastIndex) phr += mutableListOf<Int>() }
        // 소리 쪽 · 글 쪽 마디 수 맞추기: 가장 짧은 쉼부터 합치고, 음절이 가장 적은 글 마디끼리 합친다
        while (segs.size > phr.size) {
            val k = (0 until segs.size - 1).minByOrNull { segs[it + 1].first - segs[it].second }!!
            segs[k] = segs[k].first to segs[k + 1].second; segs.removeAt(k + 1)
        }
        while (phr.size > segs.size) {
            val k = (0 until phr.size - 1).minByOrNull { (phr[it] + phr[it + 1]).sumOf { w -> syllables(words[w], lang) } }!!
            phr[k] = (phr[k] + phr[k + 1]).toMutableList(); phr.removeAt(k + 1)
        }
        val out = ArrayList<WordAt>()
        segs.indices.forEach { k -> place(phr[k].map { words[it] }, segs[k].first, segs[k].second, db, top, lang, out) }
        return out
    }

    private fun byChunks(chunks: List<Triple<Int, Int, String>>, db: FloatArray, lang: Lang): List<WordAt> {
        val top = db.max(); val out = ArrayList<WordAt>()
        chunks.forEach { (s, e, text) ->
            val a = (s / Prosody.HOP_MS).coerceAtLeast(0); val b = (e / Prosody.HOP_MS).coerceAtMost(db.size)
            if (b <= a) return@forEach
            val act = (a until b).filter { db[it] > top - QUIET_DB }
            if (act.isEmpty()) return@forEach
            place(Regex("\\S+").findAll(text).map { it.value }.toList(), act.first(), act.last() + 1, db, top, lang, out)
        }
        return out
    }

    /** 지금 [ms] 에 말하는 낱말의 번호 (말하기 전이면 -1, 다 말한 뒤면 마지막). */
    fun at(words: List<WordAt>, ms: Int): Int = words.indexOfLast { it.s <= ms }

    /**
     * 긴 문장 나누기 (영어 · 한국어, 낱말 시간이 있을 때): 한 마디가 [maxMs] 안쪽 · [max] 낱말 이하가 되게, 낱말 사이 쉼이 가장 긴 곳
     * (문장부호면 더 우선) 에서 자른다. 되도록 [min] 낱말 이상, 끝에 3 낱말 안 되게 남기지 않는다. 낱말 번호 범위들.
     */
    fun split(words: List<WordAt>, maxMs: Int = SPAN_MS, min: Int = Prosody.SPAN_MIN, max: Int = Prosody.SPAN_MAX): List<IntRange> {
        val n = words.size
        if (n == 0) return emptyList()
        val out = ArrayList<IntRange>()
        var from = 0
        while (from < n) {
            if (words[n - 1].e - words[from].s <= maxMs && n - from <= max) { out += from until n; break }
            val fits = (from until n - 1).filter { k -> k == from || (k - from + 1 <= max && words[k].e - words[from].s <= maxMs) }
            val tail = n - 1 - from
            val pool = fits.filter { it - from + 1 >= min && n - 1 - it >= 3 }.ifEmpty { fits.filter { n - 1 - it >= 3 || tail < 3 }.ifEmpty { fits } }
            val cut = pool.maxByOrNull { k ->
                val gap = (words[k + 1].s - words[k].e).coerceAtLeast(0)
                gap + (if (BREAK.containsMatchIn(words[k].t)) 300 else 0) + k   // 같으면 뒤쪽 (마디를 길게)
            } ?: from
            out += from..cut
            from = cut + 1
        }
        return out
    }
}
