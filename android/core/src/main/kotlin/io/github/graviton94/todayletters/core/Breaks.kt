package io.github.graviton94.todayletters.core

/**
 * 줄바꿈: 모든 언어에서 낱말(한국어는 어절) 가운데서 줄이 바뀌지 않게 한다.
 * 한국어는 기본으로 글자마다 줄을 바꿀 수 있어서 "도착해요"가 "도 / 착해요"로 갈라지곤 한다.
 * 안드로이드 13 이상은 화면 쪽에서 어절 단위 줄바꿈(LineBreak.WordBreak.Phrase)을 켜고,
 * 그 아래 버전까지 같게 보이도록 한글이 낀 낱말 안의 글자 사이에 보이지 않는 이음표(U+2060)를 넣는다.
 * 띄어쓰기에서만 줄이 바뀐다. 아주 긴 낱말이 한 줄을 넘으면 그때만 화면이 알아서 자른다.
 */
object Breaks {
    const val JOIN = '\u2060'

    private fun hangul(c: Char) = c in '\uAC00'..'\uD7A3' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F'

    fun keepAll(text: String): String {
        if (text.none(::hangul)) return text
        val out = StringBuilder(text.length + text.length / 2)
        for (i in text.indices) {
            val c = text[i]
            out.append(c)
            val n = text.getOrNull(i + 1) ?: continue
            if (c == JOIN || n == JOIN || c.isWhitespace() || n.isWhitespace()) continue
            if (hangul(c) || hangul(n)) out.append(JOIN)
        }
        return out.toString()
    }

    /** 이음표를 뺀 글자 (비교 · 검색용). */
    fun plain(text: String) = text.replace(JOIN.toString(), "")
}

/**
 * 낭독 중인 자리 칠하기: 낭독 마디(narrate.py 의 m<i>_<언어>.json 덩어리)를 화면 문장의 글자 범위로 옮긴다.
 * 마디 글은 낭독용 철자(끊음 표시 · 구두점)가 화면과 조금 달라서, 글자(문자 · 숫자) 수를 차례로 세어 나눈다.
 */
object Spoken {
    /** [chunks] 의 글들을 [text] 안의 범위로 (마디마다 하나, 앞 띄어쓰기는 뺀다). 마디가 없으면 빈 목록. */
    fun ranges(text: String, chunks: List<String>): List<IntRange> {
        if (chunks.isEmpty() || text.isEmpty()) return emptyList()
        val counts = chunks.map { c -> c.count { it.isLetterOrDigit() } }
        val total = counts.sum()
        val letters = text.count { it.isLetterOrDigit() }
        if (total == 0 || letters == 0) return listOf(text.indices)
        val out = mutableListOf<IntRange>()
        var start = 0; var seen = 0; var cum = 0
        counts.forEachIndexed { k, n ->
            cum += n
            val goal = (cum.toLong() * letters / total).toInt()
            var j = start
            while (j < text.length && seen < goal) { if (text[j].isLetterOrDigit()) seen++; j++ }
            // 마디 끝의 구두점까지 함께 (다음 띄어쓰기 · 글자 앞에서 멈춤)
            while (j < text.length && !text[j].isLetterOrDigit() && !text[j].isWhitespace()) j++
            if (k == counts.lastIndex) j = text.length
            var a = start
            while (a < j && text[a].isWhitespace()) a++
            out += a until j
            start = j
        }
        return out
    }

    /**
     * 마디 시각(s)은 TTS 조각의 시작이라 앞에 쉼이 붙어 있다: 실제 목소리는 s 보다 0.4–0.8초 (평균 0.57초) 늦게 나온다
     * (낭독 40개를 무음 검출로 잰 값). 칠하기는 이만큼 늦춰야 목소리와 맞는다.
     */
    const val LEAD_MS = 500

    /** 재생 위치 [at] (밀리초) 에 읽고 있는 마디 번호: 시작([lead] 만큼 늦춘)이 지난 마지막 마디 (마디 사이 숨에서는 앞 마디). */
    fun at(starts: List<Int>, at: Int, lead: Int = 0): Int = starts.indexOfLast { it + lead <= at }.coerceAtLeast(0)

    /** 마디 끝의 숨 (TTS 조각의 뒤 쉼), 밀리초. 마디 안 문장의 시각을 나눌 때 목소리가 나는 몫만 쓴다. */
    const val TAIL_MS = 450
    /** 마디 안 문장 사이 숨을 글자 몇 개 몫으로 칠지. */
    private const val STOP_LETTERS = 3

    /** 칠하는 한 토막: 시작 시각 (밀리초, 마디 시각과 같은 기준 · [LEAD_MS] 를 더하기 전) 과 화면 글의 범위. */
    data class Seg(val start: Int, val range: IntRange)

    /**
     * 낭독 마디 ([starts] · [ends] · [texts], narrate.py 의 json) 를 화면 [text] 의 토막으로.
     * 마디는 “//” 자리에서만 나뉘어 있어서 한 마디에 문장이 여럿 들 수 있다 (번역 줄 · 말 없는 언어는 메시지 전체가 한 마디).
     * 그대로 칠하면 한 마디(10초 넘게) 동안 둘째 · 셋째 문장(문장 짝 화면의 둘째 문단)까지 함께 밑줄이 그어져 소리와 어긋난다.
     * 그래서 마디 안을 문장 ([Pairs.sentences]) 으로 다시 나누고, 시각은 그 마디의 목소리 몫 안에서 글자 수 (+ 문장 사이 숨) 비율로 나눈다.
     * 토막은 시각 · 글자 자리 모두 차례대로 (겹치지 않고 [text] 안).
     */
    fun timed(text: String, starts: List<Int>, ends: List<Int>, texts: List<String>): List<Seg> {
        val rs = ranges(text, texts)
        if (rs.isEmpty()) return emptyList()
        val out = mutableListOf<Seg>()
        rs.forEachIndexed { k, r ->
            val s = starts.getOrElse(k) { out.lastOrNull()?.start ?: 0 }
            val e = ends.getOrElse(k) { s }.coerceAtLeast(s)
            if (r.isEmpty()) { out += Seg(s, r); return@forEachIndexed }
            val sub = text.substring(r.first, r.last + 1)
            val parts = Pairs.sentences(sub).map { (it.first + r.first)..(it.last + r.first) }.filter { !it.isEmpty() }
            if (parts.size < 2) { out += Seg(s, r); return@forEachIndexed }
            // 마디 첫 글자에서 끝까지 빈틈 없이 (문장 사이 띄어쓰기는 앞 토막에, 앞의 [r] 시작부터)
            val spans = parts.mapIndexed { j, p -> (if (j == 0) r.first else p.first)..(if (j == parts.lastIndex) r.last else p.last) }
            val w = parts.mapIndexed { j, p -> text.substring(p.first, p.last + 1).count { it.isLetterOrDigit() } + if (j > 0) STOP_LETTERS else 0 }
            val total = w.sum().coerceAtLeast(1)
            val voice = (e - s - LEAD_MS - TAIL_MS).coerceAtLeast(0)
            var cum = 0
            spans.forEachIndexed { j, sp ->
                // 첫 문장은 마디 시작 그대로 (칠하는 쪽이 LEAD_MS 를 더한다), 다음 문장은 목소리 몫의 비율 자리
                val t = if (j == 0) s else s + (voice.toLong() * cum / total).toInt()
                out += Seg(t, sp)
                cum += w[j]
            }
        }
        return out
    }
}

/**
 * 따옴표: 배우는 말의 꼴로. 영어 · 한국어 “…”, 독일어 „…“, 프랑스어 « … », 스페인어 «…».
 */
object Quote {
    fun wrap(text: String, lang: Lang): String = when (lang) {
        Lang.FR -> "« $text »"
        Lang.DE -> "„$text“"
        Lang.ES -> "«$text»"
        else -> "“$text”"
    }
}

/**
 * 문장 끝이 아닌 마침표: 줄임말 (St. · Mr. · Mrs. · Dr. · M. · Mme · Hr. · Sig. · No. · heil. …), 머리글자 (J. · A.),
 * 날짜 · 차례의 숫자 (den 3. Juni, 1. Theo). 네 언어 (영어 · 프랑스어 · 독일어 · 스페인어) 와 편지에 실제로 나온 꼴을 한 목록에 둔다.
 * 언어마다 겹치지 않는 꼴이라 (영어 ‘I.’ 만 빼고) 한 목록으로 충분하다.
 */
object Abbrev {
    /** 늘 뒤에 이름 · 낱말이 이어지는 줄임말 (호칭 · 성인 · 칭호). 대소문자 그대로. */
    val TITLES = setOf(
        // 영어
        "Mr", "Mrs", "Ms", "Messrs", "Dr", "St", "Sts", "Mt", "Rev", "Prof", "Capt", "Col", "Gen", "Lt", "Sgt", "Jr", "Sr", "Esq", "Hon",
        // 프랑스어
        "M", "MM", "Mme", "Mmes", "Mlle", "Mlles", "Mgr", "Ste", "Vve",
        // 독일어 · 이탈리아어 (모차르트의 편지)
        "Hr", "Hrn", "Herrn", "Fr", "Frl", "Mad", "Mons", "Sig", "Sigr", "Sgra", "Ew", "Se", "Ihro", "heil", "hl", "Hl",
        // 스페인어 (고야의 편지: D. Martín · Sra. · Vm. · Excmo.)
        "Sra", "Sras", "Srta", "Sres", "Srs", "Dn", "Dña", "Ud", "Uds", "Vd", "Vds", "Vm", "Vmd", "Excmo", "Excma", "Ilmo", "Ilma", "Fco", "Ntra", "Ntro",
    )
    /** 뒤에 숫자가 올 때만 줄임말 (No. 5, Nr. 3, p. 12, 15 Oct. 1888). */
    val BEFORE_NUMBER = setOf("No", "no", "Nos", "Nr", "nr", "n", "p", "pp", "S", "vol", "Vol", "op", "Op", "fig", "Fig", "art", "Art",
        "Jan", "Febr", "Feb", "Mar", "Apr", "Aug", "Sept", "Sep", "Oct", "Okt", "Nov", "Dec", "Dez", "núm", "pág", "Ene", "Abr", "Ago", "Dic")
    /** 뒤에 소문자 · 숫자가 이어질 때만 줄임말 (etc. and, z. B. wenn, fr. 50). 문장 끝에도 자주 오는 꼴. */
    val SOFT = setOf("etc", "viz", "cf", "vs", "ca", "approx", "fr", "fl", "Fl", "usw", "bzw", "vgl", "resp", "sc", "z", "B", "d", "h", "u", "a", "i", "e", "av", "bd", "chap", "env")

    /** [text] 의 [dot] 자리 마침표가 문장 끝이 아니면 true. */
    fun keeps(text: String, dot: Int): Boolean {
        if (dot !in text.indices || text[dot] != '.') return false
        var a = dot
        while (a > 0 && text[a - 1].isLetterOrDigit()) a--
        val word = text.substring(a, dot)
        if (word.isEmpty()) return false
        // 앞이 마침표면 (z.B. · d.h. · J.-C. 의 가운데) 이어지는 줄임말
        val joined = a > 0 && text[a - 1] == '.'
        var b = dot + 1
        while (b < text.length && (text[b].isWhitespace() || text[b] in "\"“„«‘(")) b++
        if (b >= text.length) return false
        val next = text[b]
        if (word in TITLES) return true
        if (word.length == 1 && word[0].isUpperCase() && word != "I") return next.isLetter() && (next.isUpperCase() || joined)  // 머리글자 J. Roulin · P. S.
        if (word in BEFORE_NUMBER && next.isDigit()) return true
        if ((word in SOFT || joined) && (next.isLowerCase() || next.isDigit())) return true
        // 숫자: 날짜 · 차례 (den 3. Juni · 1. Theo) — 두 자리까지, 뒤가 소문자거나 문장 첫머리의 차례 번호
        if (word.all { it.isDigit() } && word.length <= 2) {
            if (next.isLowerCase()) return true
            val before = text.substring(0, a).trimEnd()
            if (before.isEmpty() || before.last() in ".!?…:") return true
            // 독일어 날짜: “den 3. Juni”, “am 16. May” — 앞 낱말이 관사 · 전치사
            val prev = before.takeLastWhile { it.isLetter() }
            if (prev in setOf("den", "dem", "der", "des", "am", "vom", "zum", "bis")) return true
        }
        return false
    }
}

/**
 * 문장 짝 (배우는 말 한 문장 + 바로 아래 번역 한 줄): 메시지 하나를 문장으로 나누고, 번역 문장을 길이 비율로 차례대로 붙인다.
 * 두 언어의 문장 수가 같으면 하나씩, 다르면 누적 길이가 가장 가깝게 (순서는 늘 지킨다). 번역이 없으면 배우는 말만.
 */
object Pairs {
    private val END = Regex("""(?<=[.!?…;])[\"”»’)\]]*\s+""")

    /** [text] 의 문장 범위 (끝 구두점 · 닫는 따옴표 포함, 뒤 띄어쓰기 뺌). [notEnd] 의 글자 (예 ";") 뒤에서는 나누지 않는다. */
    fun sentences(text: String, notEnd: String = ""): List<IntRange> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<IntRange>()
        var start = 0
        END.findAll(text).forEach { m ->
            // 줄임말 · 머리글자 · 서수 뒤 마침표는 문장 끝이 아니다 (St. Rémy, Mr. Tersteeg, J. Roulin, den 3. Juni)
            val mark = text.substring(0, m.range.first).trimEnd('"', '”', '»', '’', ')', ']').lastOrNull()
            if (mark != null && mark in notEnd) return@forEach
            if (m.value.isBlank() && text[m.range.first - 1] == '.' && Abbrev.keeps(text, m.range.first - 1)) return@forEach
            val end = m.range.first + m.value.trimEnd().length
            if (end > start && text.substring(start, end).any { it.isLetterOrDigit() }) { out += start until end; start = m.range.last + 1 }
        }
        if (start < text.length && text.substring(start).any { it.isLetterOrDigit() }) out += start until text.trimEnd().length
        else if (out.isNotEmpty() && start < text.length) out[out.lastIndex] = out.last().first until text.trimEnd().length
        return out.ifEmpty { listOf(text.indices) }
    }

    private val CLAUSE = Regex("""(?<=[,;:—–])\s+""")

    /** [r] 안을 쉼표 · 쌍점 · 줄표 뒤에서 나눈 마디 범위들. */
    fun clauses(text: String, r: IntRange): List<IntRange> {
        val sub = text.substring(r.first, r.last + 1)
        val out = mutableListOf<IntRange>()
        var start = 0
        CLAUSE.findAll(sub).forEach { m ->
            if (sub.substring(start, m.range.first).any { it.isLetterOrDigit() }) { out += (r.first + start) until (r.first + m.range.first); start = m.range.last + 1 }
        }
        if (start < sub.length) {
            if (sub.substring(start).any { it.isLetterOrDigit() } || out.isEmpty()) out += (r.first + start)..r.last
            else out[out.lastIndex] = out.last().first..r.last
        }
        return out
    }

    /**
     * (배우는 말 범위, 그 아래 둘 번역 글) 짝들. [read] 가 null 이면 번역은 "".
     * 문장 수가 같으면 문장마다. 번역 문장이 더 많으면 배우는 말을 마디(쉼표)로 나눠 번역 문장마다 묶고, 더 적으면 배우는 말 문장을 묶는다.
     * [clauses] 면 (도착 화면처럼 한 문장을 크게 보일 때) 둘 다 한 문장이어도 마디끼리 짝을 짓는다.
     */
    fun of(learn: String, read: String?, clauses: Boolean = false): List<Pair<IntRange, String>> {
        val ls = sentences(learn)
        if (read.isNullOrBlank()) return ls.map { it to "" }
        val rsR = sentences(read)
        val rs = rsR.map { read.substring(it) }
        if (rs.size == ls.size && (ls.size > 1 || !clauses)) return ls.zip(rs)
        fun len(r: IntRange) = r.last - r.first + 1
        // 배우는 말 단위: 번역 문장이 더 많거나 마디로 짝지을 때는 마디까지 나눈다
        val lu = if (rs.size > ls.size || clauses) ls.flatMap { clauses(learn, it) } else ls
        val ru = if (clauses && rs.size <= 1) rsR.flatMap { clauses(read, it) }.map { read.substring(it) } else rs
        if (ru.size <= 1 || lu.size <= 1) return listOf((ls.first().first..ls.last().last) to rs.joinToString(" "))
        return if (ru.size <= lu.size) {
            group(ru.map { it.length }, lu.map(::len)).mapIndexed { i, g -> (lu[g.first].first..lu[g.last].last) to ru[i] }
        } else {
            group(lu.map(::len), ru.map { it.length }).mapIndexed { i, g -> lu[i] to ru.subList(g.first, g.last + 1).joinToString(" ") }
        }
    }

    /**
     * 긴 쪽([many], n 개)을 짧은 쪽([few], k 개, k ≤ n) 수만큼 차례로 묶는다: 묶음마다 하나 이상, 누적 비율이 가장 가깝게.
     * 반환은 [few] 의 i 번째에 붙는 [many] 의 번호 범위.
     */
    internal fun group(few: List<Int>, many: List<Int>): List<IntRange> {
        val k = few.size; val n = many.size
        val fTot = few.sum().coerceAtLeast(1).toDouble(); val mTot = many.sum().coerceAtLeast(1).toDouble()
        val out = mutableListOf<IntRange>()
        var j = 0; var fCum = 0.0; var mCum = 0.0
        for (i in 0 until k) {
            fCum += few[i] / fTot
            val start = j
            mCum += many[j] / mTot; j++
            // 남은 것이 남은 묶음 수보다 많을 때만 더 붙인다
            while (j < n && n - j > k - 1 - i && (i == k - 1 || kotlin.math.abs(mCum + many[j] / mTot - fCum) < kotlin.math.abs(mCum - fCum))) {
                mCum += many[j] / mTot; j++
            }
            out += start until j
        }
        return out
    }
}
