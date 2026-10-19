package io.github.graviton94.todayletters.core

import kotlin.math.abs
import kotlin.math.max

// ── 말하기 · 소리 (2026-10-19, 시안 vox): 또박또박 검사 · 어제 말 떠올리기 · 내 이야기로 · 이번 장의 소리 ──────────────

/**
 * 말하기 검사 (말 알아듣기 없이, 길이도 보지 않음): 목소리가 들어갔고 ([Prosody.spoke]) 음절 수가 원래 소리와 비슷한가.
 * 원래 소리가 있으면 그 소리에서 센 음절 ([Prosody.syllables]), 없으면 (내 이야기로) 글자로 어림한 음절 ([estimate]).
 * 어긋나면 ‘조금 더 또박또박 · 끝까지 말해 봐요’ 한 줄로 다시. 같은 줄에서 두 번째로 안 되면 목소리만 들어갔어도 통과 (막지 않는다, 사용자 결정 가).
 */
object SpeakCheck {
    /** 내 음절 수가 원래의 이만큼 안에 들면 통과 (40%). */
    const val TOLERANCE = 0.4
    /** 짧은 말 (1~2음절) 은 세기 오차가 한 음절이라 적어도 이만큼은 봐준다. */
    const val MIN_SLACK = 1

    /** |내 것 − 원래| ≤ 원래의 40% (적어도 한 음절). 원래를 모르면 (0 이하) 따지지 않는다. */
    fun close(mine: Int, ref: Int): Boolean = ref <= 0 || abs(mine - ref) <= max(TOLERANCE * ref, MIN_SLACK.toDouble())

    /** 판정: [pass] 면 통과, [hint] 면 목소리는 들어갔지만 음절이 어긋남 (‘또박또박’ 한 줄). */
    data class Verdict(val pass: Boolean, val hint: Boolean)

    /**
     * [spoke] 목소리가 들어갔나, [mine] 내 음절, [ref] 원래 음절 (null 이면 음절은 따지지 않음), [misses] 이 줄에서 앞서 안 된 횟수.
     * 침묵 · 잡음은 언제나 아니다 (‘다시 해 봐요’). 음절이 어긋나도 두 번째 실패 (앞서 한 번 이상 안 됨) 면 통과.
     */
    fun judge(spoke: Boolean, mine: Int, ref: Int?, misses: Int): Verdict = when {
        !spoke -> Verdict(pass = false, hint = false)
        ref == null || close(mine, ref) -> Verdict(pass = true, hint = false)
        misses >= 1 -> Verdict(pass = true, hint = false)
        else -> Verdict(pass = false, hint = true)
    }

    private val VOWELS = mapOf(
        Lang.FR to "aeiouyàâäéèêëîïôöùûüÿœæ",
        Lang.DE to "aeiouyäöü",
        Lang.EN to "aeiouy",
        Lang.NL to "aeiouyëïéèáóú",
    )

    /**
     * 글자로 어림한 음절 수: 한국어는 한글 글자 수, 프랑스어 · 독일어 · 영어 (그 밖의 말) 는 낱말마다 모음 덩어리 수 (j' · l' 처럼 모음 없는 조각은 0).
     * 프랑스어 · 영어는 낱말 끝의 소리 나지 않는 e (fr -e · -es, en -e, 영어 -le 은 셈) 를 뺀다 (모음 덩어리가 둘 이상일 때만).
     */
    fun estimate(text: String, lang: Lang): Int {
        val t = Breaks.plain(text)
        if (lang == Lang.KO) return t.count { it in '\uAC00'..'\uD7A3' }
        val v = VOWELS[lang] ?: VOWELS.getValue(Lang.EN)
        return Regex("[\\p{L}]+").findAll(t.lowercase()).sumOf { m ->
            val w = m.value
            var groups = 0; var inV = false
            for (c in w) { val isV = c in v; if (isV && !inV) groups++; inV = isV }
            val silent = groups > 1 && when (lang) {
                Lang.FR -> (w.endsWith("e") || w.endsWith("es")) && w.length > 2 && w[w.length - (if (w.endsWith("es")) 3 else 2)] !in v
                Lang.EN -> w.endsWith("e") && !w.endsWith("le") && w.length > 2 && w[w.length - 2] !in v
                else -> false
            }
            // 모음 없는 조각 (j' · l' · qu') 은 셈하지 않는다
            if (groups == 0) 0 else groups - if (silent) 1 else 0
        }
    }
}

/**
 * 어제 말 떠올리기 (소리 없이 푸는 퀴즈): 지난 공부한 날 이 작품에서 한 회화의 “me” 줄 (많아야 [MAX]).
 * 줄의 뜻 (앱 글자 언어) 을 보고, 그 줄을 2~4 조각 ([chunks]) + 다른 내 줄의 조각 하나 ([distractor]) 가운데서 차례로 누른다.
 * 맞으면 줄 + 회화 소리, 틀리면 답을 보여 주고 그 줄을 오늘 퀴즈 끝에 한 번 더 ([after]).
 */
object Recall {
    const val MAX = 2

    /** 지난 공부한 날: [days] (이 작품에서 편지를 마쳤거나 회화를 한 날) 가운데 [today] 앞의 가장 늦은 날. 없으면 null. */
    fun previousDay(days: Collection<Long>, today: Long): Long? = days.filter { it < today }.maxOrNull()

    /** 줄을 조각으로: 문장부호 (, ; : · 등) 뒤에서 먼저 나누고, 그래도 2~4 가 아니면 낱말 묶음으로 고르게. 낱말이 하나면 그 하나. */
    fun chunks(line: String): List<String> {
        // 문장부호만인 토막 (프랑스어 “beaucoup !” 의 “!”) 은 앞 낱말에 붙인다
        val words = ArrayList<String>()
        Breaks.plain(line).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { w ->
            if (words.isNotEmpty() && w.none { it.isLetterOrDigit() }) words[words.lastIndex] = words.last() + " " + w else words += w
        }
        if (words.size <= 1) return words
        val byPunct = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        words.forEach { w -> cur += w; if (w.last() in ",;:—–.!?…") { byPunct += cur; cur = ArrayList() } }
        if (cur.isNotEmpty()) byPunct += cur
        val groups = if (byPunct.size in 2..4) byPunct else {
            val k = ((words.size + 2) / 3).coerceIn(2, 4).coerceAtMost(words.size)
            val base = words.size / k; val extra = words.size % k
            var at = 0
            (0 until k).map { g -> val n = base + if (g < extra) 1 else 0; words.subList(at, at + n).also { at += n } }
        }
        return groups.map { it.joinToString(" ") }
    }

    /** 틀린 조각 하나: 다른 내 줄들 [others] 의 조각 가운데 이 줄의 조각과 같지 않은 것 ([seed] 로 고름). 없으면 null. */
    fun distractor(target: List<String>, others: List<String>, seed: Int): String? {
        val own = target.map { norm(it) }.toSet()
        val pool = others.flatMap { chunks(it) }.filter { norm(it).isNotEmpty() && norm(it) !in own }.distinct()
        if (pool.isEmpty()) return null
        return pool[Math.floorMod(seed, pool.size)]
    }

    private fun norm(x: String) = x.lowercase().filter { it.isLetterOrDigit() }

    /**
     * 한 문제의 상태: [chunks] 는 맞는 차례, [tiles] 는 섞은 조각 (틀린 조각 포함), [used] 는 놓은 조각, [wrong] 이면 틀림 (답을 보여 줌).
     * 같은 글자의 조각은 어느 것이든 된다.
     */
    data class State(val chunks: List<String>, val tiles: List<String>, val used: List<Int> = emptyList(), val wrong: Boolean = false) {
        val placed: Int get() = used.size
        val right: Boolean get() = !wrong && placed >= chunks.size
        val over: Boolean get() = wrong || right
        /** 놓은 조각을 이은 글. */
        val built: String get() = used.joinToString(" ") { tiles[it] }

        fun tap(i: Int): State {
            if (over || i in used || i !in tiles.indices) return this
            return if (norm(tiles[i]) == norm(chunks[placed])) copy(used = used + i) else copy(wrong = true)
        }
    }

    /** 문제 하나 시작: 조각 + 틀린 조각을 [seed] 로 섞는다 (맞는 차례 그대로가 아니게). */
    fun start(line: String, others: List<String>, seed: Int): State {
        val c = chunks(line)
        val d = distractor(c, others, seed)
        val all = c + listOfNotNull(d)
        var tiles = all.shuffled(java.util.Random(seed.toLong()))
        if (tiles.size > 1 && tiles.take(c.size) == c) tiles = tiles.drop(1) + tiles.first()
        return State(c, tiles)
    }

    /**
     * 한 문제를 마친 뒤의 차례: 틀렸고 아직 한 번 더 넣지 않은 줄이면 끝에 한 번 더. [queue] 는 남은 차례 (지금 문제 포함, 맨 앞),
     * [again] 은 이미 한 번 더 넣은 줄. 돌려주는 것은 (다음 차례, again).
     */
    fun after(queue: List<Int>, again: Set<Int>, right: Boolean): Pair<List<Int>, Set<Int>> {
        val cur = queue.firstOrNull() ?: return queue to again
        val rest = queue.drop(1)
        return if (!right && cur !in again) (rest + cur) to (again + cur) else rest to again
    }
}

/**
 * 내 이야기로 바꿔 말하기 ([TalkMine]): 내 줄 [TalkMine.line] 의 [TalkMine.slot] 자리를 보기로 바꾼 문장. 데이터가 모자라면 null (그 화면을 건너뛴다).
 */
object Mine {
    /** 빈칸 표시. */
    const val BLANK = "＿＿＿"

    /** 보기 [i] 를 넣은 [lang] 문장. 줄 · 자리 · 보기가 그 말에 없거나 자리가 줄에 없으면 null. */
    fun sentence(talk: Talk?, i: Int, lang: Lang): String? {
        val m = talk?.mine ?: return null
        val line = talk.lines.getOrNull(m.line)?.takeIf { it.me }?.text?.lines?.get(lang)?.let { Breaks.plain(it) } ?: return null
        val slot = m.slot[lang]?.let { Breaks.plain(it) }?.takeIf { it.isNotEmpty() } ?: return null
        val opt = m.opts.getOrNull(i)?.get(lang)?.let { Breaks.plain(it) }?.takeIf { it.isNotEmpty() } ?: return null
        val at = line.indexOf(slot).takeIf { it >= 0 } ?: return null
        return line.substring(0, at) + opt + line.substring(at + slot.length)
    }

    /** 빈칸 문장 ([lang] 줄의 자리를 [BLANK] 로). */
    fun blanked(talk: Talk?, lang: Lang): String? {
        val m = talk?.mine ?: return null
        val line = talk.lines.getOrNull(m.line)?.takeIf { it.me }?.text?.lines?.get(lang)?.let { Breaks.plain(it) } ?: return null
        val slot = m.slot[lang]?.let { Breaks.plain(it) }?.takeIf { it.isNotEmpty() } ?: return null
        val at = line.indexOf(slot).takeIf { it >= 0 } ?: return null
        return line.substring(0, at) + BLANK + line.substring(at + slot.length)
    }

    /** 배우는 말 [learn] 으로 쓸 수 있는 보기 번호들 (그 말 문장이 만들어지는 것만). */
    fun options(talk: Talk?, learn: Lang): List<Int> = talk?.mine?.opts?.indices?.filter { sentence(talk, it, learn) != null }.orEmpty()

    /** 이 회화에서 ‘내 이야기로’ 를 할 수 있나 (빈칸 문장과 보기 둘 이상). */
    fun available(talk: Talk?, learn: Lang): Boolean = blanked(talk, learn) != null && options(talk, learn).size >= 2

    /** 보기 [i] 의 세 언어 글 ([langs] 가운데 만들어지는 말만): 복습 카드 ‘내가 만든 말’ 의 낱말. */
    fun tri(talk: Talk?, i: Int, langs: List<Lang>): Tri? {
        val m = langs.mapNotNull { l -> sentence(talk, i, l)?.let { l to (if (l == Lang.KO) Breaks.keepAll(it) else it) } }.toMap()
        return if (m.isEmpty()) null else Tri(m)
    }
}

/** 이번 장의 소리 한 장: [name] · [tip] 은 앱 글자 언어 (덱의 말은 빠짐), [pairs] 는 헷갈리는 두 낱말, [mean] 은 낱말마다 뜻. */
data class SoundChapter(val name: Map<Lang, String>, val tip: Map<Lang, String>, val pairs: List<Pair<String, String>>, val mean: Map<String, Map<Lang, String>>) {
    fun nameIn(lang: Lang) = name[lang] ?: name[Lang.EN] ?: name[Lang.KO] ?: name.values.firstOrNull().orEmpty()
    fun tipIn(lang: Lang) = tip[lang] ?: tip[Lang.EN] ?: tip[Lang.KO] ?: tip.values.firstOrNull().orEmpty()
    fun meaning(word: String, lang: Lang): String = mean[word]?.let { it[lang] ?: it[Lang.EN] ?: it[Lang.KO] }.orEmpty()
    /** 이 장의 낱말 (두 낱말 모두, 겹치지 않게). */
    val words: List<String> get() = pairs.flatMap { listOf(it.first, it.second) }.distinct()
}

/** sounds[_en|_ko].json 하나: [lang] 덱의 말, [checked] 사람이 봤나, [chapters] 장 id (I…VIII) 마다. */
data class SoundDeck(val lang: Lang?, val checked: Boolean, val chapters: Map<String, SoundChapter>) {
    companion object { val EMPTY = SoundDeck(null, false, emptyMap()) }
}

/**
 * 이번 장의 소리 (data/<작품>/sounds[_en|_ko].json): I~VIII 장의 첫 편지를 처음 열 때 (장마다 · 배우는 말마다 한 번) 편지 앞에 1분.
 * 요령 한 줄 · 첫 쌍 (뜻 · 듣기) → ‘들린 말은?’ 네 판 ([rounds]), 넷 중 셋 ([PASS]) 이면 끝. 건너뛸 수 있다.
 * 소리: s_<말>_<slug>.m4a (카드 낱말과 같은 slug, [VocabAudio.slug]), audio/<작품>/S/sounds/ (R2 묶음 “S”).
 */
object Sounds {
    const val CHAPTER = "S"
    const val LETTER = "sounds"
    const val ROUNDS = 4
    const val PASS = 3
    /** 이 화면을 여는 장. */
    val CHAPTERS = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII")

    /** 낱말 소리 파일 이름 (확장자 없이). */
    fun audio(lang: Lang, word: String) = "s_${lang.code}_${VocabAudio.slug(word)}"

    /** 글 → 덱. 없거나 깨졌으면 빈 덱 (파일이 아직 없을 때와 같다). 쌍이 아닌 줄은 뺀다. */
    fun parse(text: String?): SoundDeck {
        if (text.isNullOrBlank()) return SoundDeck.EMPTY
        val o = runCatching { Json.parse(text) as? Map<*, *> }.getOrNull() ?: return SoundDeck.EMPTY
        fun langs(x: Any?): Map<Lang, String> = (x as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val l = Lang.entries.firstOrNull { it.code == k } ?: return@mapNotNull null
            (v as? String)?.takeIf { it.isNotBlank() }?.let { l to it }
        }.toMap()
        val chapters = (o["chapters"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val id = k as? String ?: return@mapNotNull null
            val c = v as? Map<*, *> ?: return@mapNotNull null
            val pairs = (c["pairs"] as? List<*>).orEmpty().mapNotNull { p ->
                val l = (p as? List<*>)?.filterIsInstance<String>()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return@mapNotNull null
                if (l.size == 2 && l[0] != l[1]) l[0] to l[1] else null
            }
            if (pairs.isEmpty()) return@mapNotNull null
            val mean = (c["mean"] as? Map<*, *>).orEmpty().mapNotNull { (w, m) -> (w as? String)?.let { it to langs(m) } }.toMap()
            id to SoundChapter(langs(c["name"]), langs(c["tip"]), pairs, mean)
        }.toMap()
        return SoundDeck(Lang.entries.firstOrNull { it.code == o["lang"] }, o["checked"] == true, chapters)
    }

    /** 편지를 열기 전에 이 화면을 띄우나: I~VIII 장의 첫 편지 ([letterIndex] 0) 이고, 덱에 그 장이 있고, 아직 안 본 장 ([seen]). */
    fun due(deck: SoundDeck, chapter: String, letterIndex: Int, seen: Set<String>): Boolean =
        letterIndex == 0 && chapter in CHAPTERS && chapter !in seen && deck.chapters[chapter] != null

    /** 한 판: [pair] 번 쌍에서 [second] 면 둘째 낱말을 들려준다. */
    data class Round(val pair: Int, val second: Boolean)

    /** 네 판: 쌍마다 한 번 (쌍이 넷보다 적으면 되풀이), 차례 · 들려줄 낱말은 [seed] 로. */
    fun rounds(pairs: Int, seed: Long, n: Int = ROUNDS): List<Round> {
        if (pairs <= 0) return emptyList()
        val r = java.util.Random(seed)
        val order = (0 until n).map { it % pairs }.shuffled(r)
        return order.map { Round(it, r.nextBoolean()) }
    }

    /** 넷 중 셋이면 끝. */
    fun passed(right: Int, n: Int = ROUNDS): Boolean = right >= minOf(PASS, n)
}

/**
 * 비슷하게 읽으면 (data/<작품>/pron[_en|_ko].json, scripts/pron.py 가 espeak-ng 로 만든 자동 · 어림 힌트): 말하기가 두 번째로 안 되면
 * 그 줄 아래에 내 글자로 소리를 보여 준다. 한국어 앱 글자 + 프랑스어 · 독일어 · 영어를 배우면 한글, 영어 앱 글자 + 한국어를 배우면 로마자.
 * 글 그대로 찾고, 없으면 (따라 읽기 마디) 낱말마다 찾아 이어 붙인다. 하나라도 없으면 null (줄을 숨긴다).
 */
object SayLike {
    /** 이 사람에게 힌트를 보여 주나. */
    fun shows(ui: UiLang, learn: Lang): Boolean = if (learn == Lang.KO) ui == UiLang.EN else ui == UiLang.KO

    fun parse(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        val o = runCatching { Json.parse(text) as? Map<*, *> }.getOrNull() ?: return emptyMap()
        return (o["items"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.takeIf { it.isNotBlank() }?.let { key to it } } }.toMap()
    }

    private val CORE = Regex("^([^\\p{L}\\p{N}_]*)(.*?)([^\\p{L}\\p{N}_]*)$")

    fun lookup(items: Map<String, String>, text: String): String? {
        if (items.isEmpty()) return null
        val t = Breaks.plain(text).trim()
        if (t.isEmpty()) return null
        items[t]?.let { return it }
        val out = ArrayList<String>()
        for (tok in t.split(Regex("\\s+"))) {
            val m = CORE.find(tok) ?: return null
            val (pre, core, post) = m.destructured
            if (core.isEmpty()) { out += tok; continue }
            val h = items[core] ?: items[core.lowercase()] ?: return null
            out += pre + h + post
        }
        return out.joinToString(" ").replace(Regex("\\s+([?!:;»])"), "$1").trim().takeIf { it.isNotEmpty() }
    }
}
