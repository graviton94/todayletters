package io.github.graviton94.todayletters.core

/**
 * 닻 표현 한 줄 (data/<작품>/anchors[_en|_ko].json, scripts/anchors.py): 작품마다 여러 편지에 되풀이되는 2~3낱말 덩어리.
 * [key] 는 맞추는 꼴 (토큰마다 [Anchors.norm], 띄어 씀), [text] 는 보이는 꼴. 뜻은 앱이 쓴 짧은 풀이.
 * [id] 는 덩어리 글자에서 나온 고정 이름 ([Anchors.id]): 복습 카드 · 배운 닻은 목록 번호가 아니라 이것으로 적는다 (목록을 고쳐도 그대로).
 */
data class Anchor(
    val text: String, val key: List<String>, val ko: String, val en: String,
    val count: Int = 0, val first: String = "", val firstMsg: Int = 0, val letters: List<String> = emptyList(),
    val id: String = "",
) {
    fun meaning(lang: Lang): String = if (lang == Lang.KO) ko.ifEmpty { en } else en.ifEmpty { ko }
}

object Anchors {
    /** 맞추는 꼴: 소문자, 글자와 숫자만 (j' → j, Théo, → théo). scripts/anchors.py 의 norm 과 같다. */
    fun norm(surface: String): String = surface.lowercase().filter { it.isLetterOrDigit() }

    /** 고정 이름: sha1("<작품>[_말]/<맞추는 꼴>") 앞 8자 (scripts/anchors.py 의 anchor_id 와 같다). [name] 은 "vincent" · "vincent_en". */
    fun id(name: String, key: List<String>): String =
        java.security.MessageDigest.getInstance("SHA-1").digest("$name/${key.joinToString(" ")}".toByteArray(Charsets.UTF_8))
            .take(4).joinToString("") { "%02x".format(it) }

    /**
     * 토큰 [norms] (맞추는 꼴) 에서 덩어리 [key] 가 나오는 자리 (첫 토큰 번호들). 토큰 꼴을 이어 붙인 글자가 덩어리의 글자와 같으면
     * 같은 것으로 본다 (j' + ai = jai, 덩어리 "j ai"). 토큰 수가 달라도 (표현 "j'ai" 를 j' · ai 두 토큰으로) 맞는다.
     */
    fun find(norms: List<String>, key: List<String>): List<IntRange> {
        val want = key.joinToString("")
        if (want.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        for (i in norms.indices) {
            if (norms[i].isEmpty() || !want.startsWith(norms[i])) continue
            var acc = ""
            var j = i
            while (j < norms.size && acc.length < want.length) {
                acc += norms[j]
                if (!want.startsWith(acc)) break
                if (acc == want) { out += i..j; break }
                j++
            }
        }
        return out
    }

    /** 표현 글자 → 맞추는 꼴 (띄어쓰기 · 문장부호에서 끊고, 끊긴 조각마다 [norm]). */
    fun keyOf(text: String): List<String> = Breaks.plain(text).split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }
}

/**
 * 낱말 아래 뜻 (시안 beginner.html ①): 편지 원문 낱말마다 그 아래 작은 뜻. 아직 내 것이 아닌 낱말에만 달아서, 익힐수록 저절로 준다.
 *
 *  - 뜻은 사전 (lexicon) 의 표제어 뜻을 짧게 ([short]: 첫 뜻, 길면 줄임). 사전에 없으면 뜻을 지어내지 않는다 (빈 뜻 = 점선만).
 *  - 덩어리는 하나로: 짧은 표현 카드 (4낱말까지) 와 닻 표현은 낱말을 따로 풀지 않고 한 칸 (pour le moins = 적어도).
 *  - 내 것 ([Growth.known]) 이 된 낱말 · 이름 · 숫자 · 외국어는 뜻 없음. 기능어 (관사 · 전치사 …) 는 [function] 일 때만 (입문 A1 · A2).
 *  - 닻 표현은 익혔으면 ([Unit.known]) 뜻 없이 금빛 바탕, 아니면 금빛 밑줄과 뜻.
 */
object Interlinear {
    enum class Kind { WORD, PHRASE, ANCHOR }

    /**
     * 한 칸: 토큰 [from]..[to] (포함). [gloss] 가 null 이면 뜻을 달지 않는 낱말 (안다 · 이름 …), "" 면 뜻이 필요한데 사전에 없음.
     * [anchor] 는 닻 표현 번호 (이 목록 안, 닻이 아니면 -1), [anchorId] 는 그 닻의 고정 이름 ([Anchor.id]), [known] 은 익힌 닻.
     */
    data class Unit(val from: Int, val to: Int, val gloss: String?, val kind: Kind = Kind.WORD, val anchor: Int = -1, val known: Boolean = false,
                    val anchorId: String = "")

    /** 짧은 덩어리 (표현 카드) 로 칠 낱말 수 상한: 그보다 긴 표현은 낱말마다 푼다 (문장 하나를 통째로 덮지 않게). */
    const val PHRASE_MAX = 4
    /** 뜻 글자 상한 (넘치면 …). */
    const val GLOSS_MAX = 14

    /** 뜻을 짧게: 첫 뜻 (쉼표 · 쌍반점 · 빗금 · 괄호 앞), 길면 줄임. */
    fun short(x: String): String {
        val first = Breaks.plain(x).split(Regex("[,;/(（]")).first().trim().ifEmpty { Breaks.plain(x).trim() }
        return if (first.length <= GLOSS_MAX) first else first.take(GLOSS_MAX - 1).trimEnd() + "…"
    }

    /** 뜻을 달지 않는 사전 줄: 이름 · 숫자 · 약어 · 외국어. */
    private fun silent(e: LexEntry) = e.pos == "name" || e.pos == "num" || e.pos == "abbr" || e.pos.startsWith("foreign")

    /**
     * 메시지 한 개의 칸들 ([tokens] 차례). [phrases] 는 이 메시지의 표현 (글자, 뜻), [anchors] 는 작품의 닻 표현.
     * [known] 은 내 것이 된 사전 열쇠, [function] 이면 기능어에도 뜻, [anchorKnown] 은 익힌 닻 (번호).
     */
    fun units(
        tokens: List<Token>, lexicon: (String) -> LexEntry?, lang: Lang, known: (String) -> Boolean, function: Boolean,
        phrases: List<Pair<String, String>> = emptyList(), anchors: List<Anchor> = emptyList(), anchorKnown: (Int) -> Boolean = { false },
    ): List<Unit> {
        val norms = tokens.map { Anchors.norm(it.surface) }
        val taken = BooleanArray(tokens.size)
        val chunks = mutableListOf<Unit>()
        fun claim(r: IntRange): Boolean {
            if (r.first < 0 || r.last >= tokens.size || r.any { taken[it] }) return false
            r.forEach { taken[it] = true }; return true
        }
        // 표현 카드 (짧은 것) 먼저, 그다음 닻 (긴 것부터): 겹치면 먼저 잡은 쪽
        phrases.map { (t, m) -> Anchors.keyOf(t) to m }.filter { it.first.size in 2..PHRASE_MAX }.sortedByDescending { it.first.size }.forEach { (key, m) ->
            Anchors.find(norms, key).firstOrNull()?.let { r -> if (claim(r)) chunks += Unit(r.first, r.last, short(m), Kind.PHRASE) }
        }
        anchors.withIndex().sortedByDescending { it.value.key.size }.forEach { (i, a) ->
            Anchors.find(norms, a.key).forEach { r ->
                if (claim(r)) {
                    val k = anchorKnown(i)
                    chunks += Unit(r.first, r.last, if (k) null else short(a.meaning(lang)), Kind.ANCHOR, anchor = i, known = k, anchorId = a.id)
                }
            }
        }
        val out = chunks.toMutableList()
        tokens.forEachIndexed { j, t ->
            if (taken[j]) return@forEachIndexed
            val e = lexicon(t.key)
            val g = when {
                e == null -> ""
                silent(e) -> null
                e.function -> if (function) short(Vocab.meaning(e, lang)) else null
                known(e.key) -> null
                else -> short(Vocab.meaning(e, lang))
            }
            out += Unit(j, j, g)
        }
        return out.sortedBy { it.from }
    }

    /** 화면 한 칸 (낱말 묶음): 글자 범위 [range] (뒤따르는 문장부호까지) 와 그 안의 칸 번호들. */
    data class Cell(val range: IntRange, val units: List<Int>)

    /**
     * 칸들을 화면의 낱말 묶음으로: 칸 사이에 띄어쓰기가 없으면 (l'art · j'ai) 한 묶음. 묶음은 다음 묶음 앞까지의 글자를 갖고
     * (문장부호 포함, 끝 공백 뺌), 첫 묶음은 맨 앞 글자 (따옴표 등) 도 갖는다. [ranges] 는 토큰마다 글자 범위 ([Vocab.align]).
     * 자리를 못 찾은 토큰의 칸은 빠지고, 칸이 하나도 없으면 빈 목록.
     */
    fun cells(text: String, ranges: List<IntRange>, units: List<Unit>): List<Cell> {
        val placed = units.withIndex().mapNotNull { (i, u) ->
            val a = ranges.getOrNull(u.from) ?: return@mapNotNull null
            val b = ranges.getOrNull(u.to) ?: return@mapNotNull null
            if (a.isEmpty() || b.isEmpty()) null else Triple(i, a.first, b.last)
        }.sortedBy { it.second }
        if (placed.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<Triple<Int, Int, Int>>>()
        placed.forEach { p ->
            val last = groups.lastOrNull()?.last()
            if (last != null && p.second > last.third && text.substring(last.third + 1, p.second).none { it.isWhitespace() }) groups.last() += p
            else groups += mutableListOf(p)
        }
        return groups.mapIndexed { k, g ->
            val start = if (k == 0) 0 else g.first().second
            val next = groups.getOrNull(k + 1)?.first()?.second ?: text.length
            var end = next
            while (end > start && text[end - 1].isWhitespace()) end--
            Cell(start until end, g.map { it.first })
        }
    }
}

/**
 * 편지 전 예열 (시안 beginner.html ②): 편지를 열기 전에 그 편지의 말 3~4개 (카드 낱말 앞쪽 것 + 덩어리 하나) 를 듣고 따라 말한다.
 * 기본은 도움 등급 A1 · A2 (‘도움 한 단계 더’ 포함) 에서 켜짐, B1 부터 꺼짐. 작품 설정에서 켜고 끈다 ([on]).
 */
object WarmUp {
    enum class Kind { WORD, PHRASE, ANCHOR }
    /** 예열 한 장: [kind] 와 그 번호 (카드 낱말 번호 · 표현 번호 · 닻 번호). */
    data class Item(val kind: Kind, val index: Int)

    const val WORDS = 3
    const val MAX = 4

    /** 켜졌나: [choice] 가 null 이면 (등급에 맞춰) 도움 등급이 A2 이하일 때. */
    fun on(choice: Boolean?, band: Reading.Band): Boolean = choice ?: (band <= Reading.Band.A2)

    /**
     * 고르기. [words] = 카드 낱말마다 (편지에 처음 나오는 토큰 자리, 이미 아는가) — 자리가 없으면 Int.MAX_VALUE.
     * [phrases] = 표현마다 (낱말 수, 처음 자리), [anchors] = 이 편지에 나오는 아직 안 익힌 닻 (번호, 처음 자리).
     * 모르는 카드 낱말을 나온 차례로 [WORDS] 개 + 덩어리 하나 (닻 → 짧은 표현), 덩어리가 없으면 낱말 하나 더.
     * 두 장이 안 되면 빈 목록 (예열 없이 바로 편지).
     */
    fun pick(words: List<Pair<Int, Boolean>>, phrases: List<Pair<Int, Int>>, anchors: List<Pair<Int, Int>>): List<Item> {
        val ws = words.withIndex().filter { !it.value.second && it.value.first != Int.MAX_VALUE }.sortedBy { it.value.first }.map { it.index }
        val chunk = anchors.minByOrNull { it.second }?.let { Item(Kind.ANCHOR, it.first) }
            ?: phrases.withIndex().filter { it.value.first in 2..Interlinear.PHRASE_MAX }.minByOrNull { it.value.second }?.let { Item(Kind.PHRASE, it.index) }
        val out = ws.take(if (chunk == null) MAX else WORDS).map { Item(Kind.WORD, it) } + listOfNotNull(chunk)
        return if (out.size < 2) emptyList() else out
    }
}
