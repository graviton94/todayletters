package io.github.graviton94.todayletters.core

/**
 * 기능어 · 문형 · 관용구 노트 (편지 방 낱말 아래 뜻 + 누르면 짧은 문법 카드).
 *
 *  - 자료: 배우는 말마다 `notes_<말>.json` (data/lexicon, 앱 assets `notes/`) + 작품 · 말마다 `idioms[_en|_ko].json` (assets `letters/<작품>/`).
 *    둘 다 `scripts/notes.py` 가 손으로 쓴 원본 (scripts/notes/<말>.txt · scripts/idioms/<작품>[_말].txt) 에서 만든다.
 *  - 맞추기 차례: 문형 (파일 차례, 떨어진 부분도 [Pattern.gap] 안에서) > 관용구 (긴 것부터, 표제어가 잇달아) > 기능어 · 어미 (한 낱말).
 *    [Interlinear.units] 에서는 표현 카드 > 문형 > 관용구 > 닻 > 낱말.
 *  - 규칙은 scripts/notes.py 의 Book 과 같다 (NotesDataTest 가 실제 자료로 잡힌 수를 맞춰 본다).
 */
data class Note(val id: String, val tag: Map<String, String>, val short: Map<String, String>, val note: Map<String, String>) {
    fun tag(lang: Lang) = Notes.pick(tag, lang)
    fun short(lang: Lang) = Notes.pick(short, lang)
    fun note(lang: Lang) = Notes.pick(note, lang)
}

object Notes {
    /** 노트의 짧은 뜻 글자 상한 (손으로 쓴 그대로, 넘치면 …). */
    const val NOTE_MAX = 16
    /** 낱말 뜻 + 어미 뜻 (to think (past)) 상한: 넘으면 어미 뜻을 붙이지 않는다. */
    const val SUFFIX_MAX = 22

    /** 뜻 글: 한국어면 "ko", 아니면 "en" (비면 다른 하나). */
    fun pick(m: Map<String, String>, lang: Lang): String =
        if (lang == Lang.KO) m["ko"].orEmpty().ifEmpty { m["en"].orEmpty() } else m["en"].orEmpty().ifEmpty { m["ko"].orEmpty() }

    fun clip(x: String): String {
        val t = x.trim()
        return if (t.length <= NOTE_MAX) t else t.take(NOTE_MAX - 1).trimEnd() + "…"
    }

    /** 한국어 어미 · 조사의 짧은 뜻을 낱말 뜻 뒤에 괄호로 붙인다 (to think (past)). 길면 붙이지 않는다. */
    fun withEnding(gloss: String, suffix: String): String {
        val s = suffix.trim()
        if (s.isEmpty()) return gloss
        val out = "$gloss ${if (s.startsWith("(")) s else "($s)"}"
        return if (out.length <= SUFFIX_MAX) out else gloss
    }
}

/** 토큰 하나의 조건 (scripts/notes.py 머리 주석): 모두 맞아야 하고 [not] 가운데 하나라도 맞으면 안 된다. */
data class TokenSpec(
    val lemma: Set<String>? = null, val form: Set<String>? = null, val pos: Set<String>? = null,
    val re: Regex? = null, val fn: Regex? = null, val cls: List<String> = emptyList(), val infl: Boolean = false, val upper: Boolean = false,
    val not: List<TokenSpec> = emptyList(), val role: Role = Role.MARK,
) {
    /** 문형 속 자리: [MARK] 는 문형의 짧은 뜻, [HEAD] 는 그 낱말 뜻 그대로, [PART] 는 뜻 없이 한 묶음. */
    enum class Role { MARK, HEAD, PART }
}

data class Pattern(
    val id: String, val seq: List<TokenSpec>, val gap: Int = 0, val stop: List<TokenSpec> = emptyList(),
    val nextNot: List<TokenSpec> = emptyList(), val prevNot: List<TokenSpec> = emptyList(),
    val always: Boolean = false, val compound: Boolean = false, val note: Note,
)

/** 관용구: 표제어 열쇠 [lem] (소문자) 가 잇달아 나오면. [text] 는 보이는 꼴. */
data class Idiom(val lem: List<String>, val text: String, val note: Note)

/**
 * 맞은 자리: 토큰 [start]..[end] (포함), 실제로 잡은 토큰 [parts] 와 그 자리 [roles]. [short] 는 낱말 아래에 보일 짧은 뜻 (ko · en),
 * [note] 는 누르면 보일 카드. [always] 면 기능어 뜻을 끈 등급에도 뜻을 단다. [suffix] 는 한국어 어미 뜻 (낱말 뜻 뒤에 붙임).
 */
data class Hit(
    val start: Int, val end: Int, val kind: Kind, val short: Map<String, String>, val note: Note?,
    val parts: List<Int> = listOf(start), val roles: List<TokenSpec.Role> = listOf(TokenSpec.Role.MARK), val id: String = "",
    val always: Boolean = false, val suffix: Map<String, String>? = null,
) {
    enum class Kind { PATTERN, IDIOM, WORD }
}

class NoteBook(
    val lang: String,
    val words: Map<String, Note>,
    val endings: Map<String, Pair<Note, Boolean>>,
    val classes: Map<String, List<TokenSpec>>,
    val patterns: List<Pattern>,
    val idioms: List<Idiom>,
    val lexicon: (String) -> LexEntry? = { null },
) {
    /** 기능어 노트 (사전 열쇠). */
    fun forToken(lexKey: String): Note? = words[lexKey] ?: words[lexKey.lowercase()]

    /** 한국어 어미 · 조사 노트 (fn 조각 "를/JKO"). */
    fun forEnding(piece: String): Note? = endings[piece]?.first

    private fun ok(s: TokenSpec, t: Token): Boolean {
        val k = t.key.lowercase()
        if (s.lemma != null && k !in s.lemma) return false
        if (s.form != null && Anchors.norm(t.surface) !in s.form) return false
        if (s.pos != null && lexicon(t.key)?.pos !in s.pos) return false
        if (s.re != null && !s.re.matches(t.surface.lowercase())) return false
        if (s.fn != null && !s.fn.containsMatchIn(t.fn)) return false
        if (s.infl && t.surface.lowercase() == k.substringBefore('#')) return false
        if (s.upper && t.surface.firstOrNull()?.isUpperCase() != true) return false
        for (c in s.cls) if (classes[c].orEmpty().none { ok(it, t) }) return false
        if (s.not.any { ok(it, t) }) return false
        return true
    }

    private fun tryAt(p: Pattern, ts: List<Token>, i: Int, taken: BooleanArray): Pair<List<Int>, Map<String, String>>? {
        if (taken[i] || !ok(p.seq[0], ts[i])) return null
        if (i > 0 && p.prevNot.any { ok(it, ts[i - 1]) }) return null
        val parts = mutableListOf(i)
        for (s in p.seq.drop(1)) {
            val prev = parts.last()
            var found = -1
            var j = prev + 1
            while (j < minOf(ts.size, prev + 2 + p.gap)) {
                if (!taken[j] && ok(s, ts[j])) { found = j; break }
                if (p.stop.any { ok(it, ts[j]) }) break
                j++
            }
            if (found < 0) return null
            parts += found
        }
        val last = parts.last()
        if (last + 1 < ts.size && p.nextNot.any { ok(it, ts[last + 1]) }) return null
        if (p.compound) {
            val e = lexicon(Anchors.norm(ts[parts[1]].surface) + ts[parts[0]].key.lowercase()) ?: return null
            return parts to mapOf("ko" to Interlinear.short(e.ko), "en" to Interlinear.short(e.en))
        }
        return parts to p.note.short
    }

    /**
     * 메시지 한 개의 토큰에서 맞은 자리들 (시작 순). [blocked] 토큰은 잡지 않는다 (먼저 잡힌 표현 카드 등).
     * 문형 · 관용구는 서로 겹치지 않고, 남은 토큰마다 기능어 · 어미 노트가 있으면 [Hit.Kind.WORD].
     */
    fun match(tokens: List<Token>, blocked: (Int) -> Boolean = { false }): List<Hit> {
        val n = tokens.size
        val taken = BooleanArray(n) { blocked(it) }
        val hits = mutableListOf<Hit>()
        for (p in patterns) for (i in 0 until n) {
            val (parts, short) = tryAt(p, tokens, i, taken) ?: continue
            parts.forEach { taken[it] = true }
            hits += Hit(parts.first(), parts.last(), Hit.Kind.PATTERN, short, p.note, parts, p.seq.map { it.role }, p.id, p.always)
        }
        val keys = tokens.map { it.key.lowercase() }
        for (d in idioms.withIndex().sortedWith(compareBy({ -it.value.lem.size }, { it.index })).map { it.value }) {
            val m = d.lem.size
            for (i in 0..n - m) {
                if (keys.subList(i, i + m) == d.lem && (i until i + m).none { taken[it] }) {
                    (i until i + m).forEach { taken[it] = true }
                    hits += Hit(i, i + m - 1, Hit.Kind.IDIOM, d.note.short, d.note, (i until i + m).toList(), List(m) { TokenSpec.Role.MARK },
                        d.lem.joinToString(" "), always = true)
                }
            }
        }
        tokens.forEachIndexed { i, t ->
            if (taken[i]) return@forEachIndexed
            val w = forToken(t.key)
            val end = if (t.fn.isEmpty() || endings.isEmpty()) null else t.fn.split('+').let { ps -> endings.keys.firstOrNull { it in ps } }
            if (w == null && end == null) return@forEachIndexed
            val e = end?.let { endings[it] }
            val note = w ?: e!!.first
            hits += Hit(i, i, Hit.Kind.WORD, note.short, note, id = note.id, suffix = e?.takeIf { it.second }?.first?.short)
        }
        return hits.sortedBy { it.start }
    }

    companion object {
        val EMPTY = NoteBook("", emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyList())
        private val IDIOM_TAG = mapOf("ko" to "관용구", "en" to "Idiom")

        @Suppress("UNCHECKED_CAST")
        private fun strs(x: Any?): Map<String, String> = (x as? Map<String, Any?>).orEmpty().mapValues { it.value as? String ?: "" }

        @Suppress("UNCHECKED_CAST")
        private fun spec(x: Any?): TokenSpec {
            val m = x as Map<String, Any?>
            fun set(k: String) = (m[k] as? List<*>)?.map { it.toString() }?.toSet()
            return TokenSpec(
                lemma = set("lemma"), form = set("form"), pos = set("pos"),
                re = (m["re"] as? String)?.let(::Regex), fn = (m["fn"] as? String)?.let(::Regex),
                cls = (m["cls"] as? List<*>)?.map { it.toString() }.orEmpty(), infl = m["infl"] == true, upper = m["upper"] == true,
                not = (m["not"] as? List<*>)?.map(::spec).orEmpty(),
                role = when (m["role"]) { "head" -> TokenSpec.Role.HEAD; "part" -> TokenSpec.Role.PART; else -> TokenSpec.Role.MARK },
            )
        }

        private fun specs(x: Any?): List<TokenSpec> = (x as? List<*>)?.map(::spec).orEmpty()

        /**
         * [notes] = notes_<말>.json 글 (없으면 null), [idioms] = 그 작품 · 말의 idioms*.json 글 (없으면 null),
         * [lexicon] = 그 작품 · 말의 사전 (품사 · 분리동사 뜻). 읽을 수 없는 파일은 빈 것으로 친다.
         */
        @Suppress("UNCHECKED_CAST")
        fun parse(notes: String?, idioms: String? = null, lexicon: (String) -> LexEntry? = { null }): NoteBook {
            val o = notes?.let { runCatching { Json.parse(it) as Map<String, Any?> }.getOrNull() }.orEmpty()
            fun note(id: String, x: Any?): Note {
                val m = x as? Map<String, Any?> ?: emptyMap()
                return Note(id, strs(m["tag"]), strs(m["short"]), strs(m["note"]))
            }
            val words = (o["words"] as? Map<String, Any?>).orEmpty().mapValues { (k, v) -> note(k, v) }
            val endings = (o["endings"] as? Map<String, Any?>).orEmpty().mapValues { (k, v) -> note(k, v) to ((v as? Map<*, *>)?.get("show") == true) }
            val classes = (o["classes"] as? Map<String, Any?>).orEmpty().mapValues { specs(it.value) }
            val patterns = (o["patterns"] as? List<*>).orEmpty().map { x ->
                val m = x as Map<String, Any?>
                val id = m["id"] as String
                Pattern(id, specs(m["seq"]), (m["gap"] as? Double)?.toInt() ?: 0, specs(m["stop"]), specs(m["next_not"]), specs(m["prev_not"]),
                    m["always"] == true, m["compound"] == true, note(id, m))
            }
            val ids = idioms?.let { runCatching { Json.parse(it) as Map<String, Any?> }.getOrNull() }.orEmpty()
            val list = (ids["idioms"] as? List<*>).orEmpty().map { x ->
                val m = x as Map<String, Any?>
                val lem = (m["lem"] as String).split(' ').filter { it.isNotEmpty() }
                val short = mapOf("ko" to (m["ko"] as? String).orEmpty(), "en" to (m["en"] as? String).orEmpty())
                Idiom(lem, (m["t"] as? String).orEmpty(),
                    Note(lem.joinToString(" "), IDIOM_TAG, short, mapOf("ko" to (m["note_ko"] as? String).orEmpty(), "en" to (m["note_en"] as? String).orEmpty())))
            }
            return NoteBook((o["lang"] as? String).orEmpty(), words, endings, classes, patterns, list, lexicon)
        }
    }
}
