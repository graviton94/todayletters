package io.github.graviton94.todayletters.core

/** Tatoeba 예문 (CC BY 2.0 FR): 문장마다 작성자를 밝힌다 (https://tatoeba.org/sentences/show/<id>). */
data class Example(val id: Long, val text: String, val en: String, val ko: String?, val author: String)

/**
 * 어휘 사전 한 줄 (data/<작품>/lexicon.json). [key] = 동형어 id ("ton#2") 또는 표제어.
 * [function] 은 기능어 (관사 · 전치사 · 대명사 …): 뜻은 보이되 카드에 담지 않는다.
 */
data class LexEntry(
    val lemma: String, val id: String? = null, val forms: List<String> = emptyList(), val pos: String = "", val gender: String = "",
    val ko: String = "", val en: String = "", val ipa: String = "", val level: String = "",
    val first: String = "", val letters: List<String> = emptyList(), val example: Example? = null, val function: Boolean = false,
) {
    val key: String get() = id ?: lemma
}

class Lexicon(val lang: Lang, val entries: List<LexEntry>) {
    private val byKey = entries.associateBy { it.key }
    operator fun get(key: String): LexEntry? = byKey[key]
    val size get() = entries.size
}

/** 편지 속 표현 하나 (daily.json phrases): [msg] 는 그 표현이 나온 메시지 순번. */
data class Phrase(val text: String, val msg: Int, val ko: String, val en: String, val noteKo: String, val noteEn: String)

/** 그림 점 낱말 바꾸기: [word] 가 null 이면 그 점에는 낱말을 두지 않는다. */
data class DailySpot(val index: Int, val word: String?, val was: String = "")

/** 편지 한 통의 오늘의 낱말 · 그림 점 바꾸기 · 표현 (daily.json). */
data class Daily(val words: List<String> = emptyList(), val spots: List<DailySpot> = emptyList(), val phrases: List<Phrase> = emptyList())

/** gloss.json 의 토큰 하나: 편지에 찍힌 꼴과 사전 열쇠. */
data class Token(val surface: String, val key: String)

/**
 * 배우는 언어 한 벌의 어휘 자료: 원어는 lexicon · gloss · daily.json, 영어 · 한국어는 *_en · *_ko.json.
 * [deck] 은 파일 꼬리 · 카드 열쇠 머리 ("" = 원어, "en", "ko").
 */
class VocabSet(val lang: Lang, val deck: String, val lexicon: Lexicon?, val daily: Map<String, Daily>) {
    /** gloss 파일 이름 (gloss.json · gloss_en.json …). */
    fun file(base: String) = VocabSet.file(base, deck)
    companion object {
        fun file(base: String, deck: String) = if (deck.isEmpty()) "$base.json" else "${base}_$deck.json"
        /** 배우는 언어 [learn] 의 자료 꼬리: 원어면 "", 아니면 언어 코드. */
        fun deckFor(learn: Lang, original: Lang) = if (learn == original) "" else learn.code
    }
}

/**
 * 어휘 자료를 편지에 잇기: 오늘의 낱말 · 표현 카드 · 그림 점 낱말 · 눌러서 뜻 보기.
 * 모두 순수 함수 (앱은 Library 에서 JSON 만 읽어 넘긴다).
 */
object Vocab {
    private val ARTICLE = Regex("^(der|die|das|le|la|les|l'|l’)\\s*", RegexOption.IGNORE_CASE)

    private fun norm(x: String) = Breaks.plain(x).trim().lowercase()

    /** "der Brief" → "brief", "l'eau" → "eau". */
    fun bare(x: String) = norm(x).replace(ARTICLE, "").trim()

    /** 카드에 보일 꼴: 독일어 명사는 관사를 붙인다 (챕터 낱말과 같은 모양: der Brief), 프랑스어는 표제어 그대로. */
    fun display(e: LexEntry, original: Lang): String =
        if (original == Lang.DE && e.pos == "n") when (e.gender) { "m" -> "der "; "f" -> "die "; "n" -> "das "; "pl" -> "die "; else -> "" } + e.lemma
        else e.lemma

    /** 품사 줄 (챕터 낱말과 같은 꼴: n.m. · v. · adj.). */
    fun posLabel(e: LexEntry): String = when {
        e.pos == "n" && e.gender.isNotEmpty() -> "n.${e.gender}."
        e.pos.startsWith("foreign:") -> e.pos.removePrefix("foreign:") + "."
        e.pos.isEmpty() -> ""
        else -> "${e.pos}."
    }

    /** 사전 한 줄 → 낱말 (배우는 말 · 영어 · 한국어). [lang] 은 사전의 언어 (원어 · 영어 · 한국어 사전 모두). [audio] 는 그 편지에 있는 낱말 낭독 (없으면 ""). */
    fun word(e: LexEntry, lang: Lang, audio: String = ""): Word = Word(
        Tri(glosses(e.en, Breaks.keepAll(e.ko), lang, display(e, lang))),
        pos = posLabel(e), ipa = e.ipa, audio = audio, lex = e.key, level = e.level, forms = e.forms,
    )

    /** 뜻 줄 (en · ko) 위에 배우는 말 [lang] 의 글자를 얹는다: 영어 · 한국어 사전이면 그 언어 줄은 표제어가 된다. */
    private fun glosses(en: String, ko: String, lang: Lang, own: String): Map<Lang, String> =
        linkedMapOf<Lang, String>().apply {
            put(lang, if (lang == Lang.KO) Breaks.keepAll(own) else own)
            if (lang != Lang.EN) put(Lang.EN, en)
            if (lang != Lang.KO) put(Lang.KO, ko)
        }

    /**
     * 뜻풀이 언어: 앱 글자 언어 [ui] (영어 · 한국어). 배우는 말이 바로 그 언어면 (한국어로 한국어를 배우는 경우) 다른 하나.
     */
    fun glossLang(ui: Lang, learn: Lang): Lang = if (ui != learn) ui else if (ui == Lang.KO) Lang.EN else Lang.KO

    /** 사전 한 줄의 뜻 ([glossLang] 언어, 비면 다른 하나). */
    fun meaning(e: LexEntry, lang: Lang): String = if (lang == Lang.KO) e.ko.ifEmpty { e.en } else e.en.ifEmpty { e.ko }

    /** 챕터 낱말 [w] 가 사전 줄 [e] 와 같은 낱말인가 (관사 · 대소문자 무시, 표제어나 편지 속 꼴). */
    fun same(w: Word, e: LexEntry, original: Lang, modern: Boolean = true): Boolean {
        val names = (listOf(e.lemma) + e.forms).map { bare(it) }.toSet()
        return bare(w.text[original]) in names || (modern && w.modern.isNotEmpty() && bare(w.modern) in names)
    }

    /** 이 편지의 낱말 가운데 [e] 와 같은 것 (낭독 · 손으로 쓴 번역을 그대로 쓰려고). */
    fun match(e: LexEntry, source: List<Word>, original: Lang, series: Lang = original): Word? = source.firstOrNull { same(it, e, original, modern = original == series) }

    /** 눌러서 담은 낱말 · 오늘의 낱말: 편지의 챕터 낱말과 같으면 그 낱말(낭독 포함)에 사전 정보를 더하고, 아니면 사전에서 만든다. */
    fun wordFor(e: LexEntry, source: List<Word>, original: Lang, series: Lang = original): Word =
        match(e, source, original, series)?.let {
            // 영어 · 한국어를 배울 때: 챕터 낱말의 발음기호는 원어 것이라 사전 것으로 (영어는 챕터의 영어 발음기호가 다음)
            val ipa = if (original == series) it.ipa.ifEmpty { e.ipa } else e.ipa.ifEmpty { if (original == Lang.EN) it.ipaEn else "" }
            it.copy(lex = e.key, level = e.level, forms = e.forms, ipa = ipa)
        } ?: word(e, original)

    /**
     * 편지의 카드 낱말: daily.json 의 낱말(등급별 8개, 앞 편지와 겹치지 않음)이 있으면 그것, 없으면 챕터 파일의 낱말.
     * 사전에 없는 열쇠는 건너뛰고, 같은 낱말은 한 번만.
     */
    fun deckWords(daily: Daily?, source: List<Word>, lexicon: Lexicon?, original: Lang, series: Lang = original): List<Word> {
        if (daily == null || lexicon == null || daily.words.isEmpty()) return source
        val out = daily.words.distinct().mapNotNull { k -> lexicon[k]?.let { wordFor(it, source, original, series) } }.distinctBy { it.lex }
        return out.ifEmpty { source }
    }

    /**
     * 표현 → 표현 카드 (원어 표현 · 영어 · 한국어, 쓰임 설명). [index] 는 그 언어 phrases 의 번호 (0부터):
     * 소리는 편지 폴더의 ph<index+1>_<언어>.m4a ([Word.audio] = "ph<n>"), 나온 문장은 [Phrase.msg].
     */
    fun phraseWord(p: Phrase, original: Lang, index: Int = -1): Word = Word(
        Tri(glosses(p.en, Breaks.keepAll(p.ko), original, p.text)),
        audio = if (index >= 0) "ph${index + 1}" else "",
        note = Tri(mapOf(Lang.EN to p.noteEn, Lang.KO to Breaks.keepAll(p.noteKo))),
        msg = p.msg,
    )

    /** 그림 점 낱말 바꾸기: 바꾼 점은 사전의 표제어를 낱말로 (그 점의 옛 낭독은 쓰지 않는다), 빈 낱말이면 낱말을 뺀다. */
    fun spots(spots: List<Spot>, daily: Daily?, lexicon: Lexicon?, original: Lang): List<Spot> {
        if (daily == null || daily.spots.isEmpty()) return spots
        return spots.mapIndexed { i, sp ->
            val d = daily.spots.firstOrNull { it.index == i } ?: return@mapIndexed sp
            val e = d.word?.takeIf { it.isNotBlank() }?.let { lexicon?.get(it) }
            if (d.word.isNullOrBlank()) sp.copy(word = null, voiced = false)
            else if (e == null) sp
            else sp.copy(word = word(e, original), voiced = false)
        }
    }

    /** 편지 한 통에 daily.json 을 입힌다. [original] 은 그 자료의 언어 (영어 · 한국어 자료면 EN · KO), [series] 는 작품의 원어: 카드 낱말 · 표현 · 그림 점. 챕터 낱말은 [Letter.source] 에 남는다. */
    fun apply(letter: Letter, daily: Daily?, lexicon: Lexicon?, original: Lang, series: Lang = original): Letter {
        val source = letter.source.ifEmpty { letter.words }
        if (daily == null) return letter.copy(source = source)
        return letter.copy(
            source = source,
            words = deckWords(daily, source, lexicon, original, series),
            phrases = daily.phrases.mapIndexed { i, ph -> phraseWord(ph, original, i) },
            plate = letter.plate?.let { pl -> pl.copy(spots = spots(pl.spots, daily, lexicon, original)) },
        )
    }

    /**
     * 토큰마다 글 안의 자리: 토큰은 편지 글의 낱말 순서 그대로라, 앞에서부터 낱말 경계에 맞는 첫 자리를 찾는다.
     * 못 찾은 토큰은 빈 범위 (그 뒤 토큰은 계속 찾는다).
     */
    fun align(text: String, tokens: List<Token>): List<IntRange> {
        var cur = 0
        return tokens.map { t ->
            val at = find(text, t.surface, cur)
            if (at < 0) IntRange.EMPTY else { cur = at + t.surface.length; at until at + t.surface.length }
        }
    }

    private fun find(text: String, surface: String, from: Int): Int {
        if (surface.isEmpty()) return -1
        var start = from
        while (true) {
            val i = text.indexOf(surface, start)
            if (i < 0) return -1
            val j = i + surface.length
            val okL = !surface.first().isLetter() || i == 0 || !text[i - 1].isLetter()
            val okR = !surface.last().isLetter() || j >= text.length || !text[j].isLetter()
            if (okL && okR) return i
            start = i + 1
        }
    }
}

/**
 * 낱말 카드 열쇠: "작품:장:편지:<꼬리>". 꼬리가 숫자면 그 편지의 카드 낱말, "p<n>" 이면 표현 카드, "+<사전 열쇠>" 면 내가 눌러 담은 낱말.
 */
data class CardRef(val series: String, val chapter: String, val letter: String, val index: Int = -1, val phrase: Int = -1, val own: String = "",
                   /** 어휘 자료 꼬리 ([VocabSet.deck]): "" 는 원어, "en" · "ko" 는 영어 · 한국어를 배울 때의 낱말. 꼬리 앞에 "en/" 처럼 붙는다. */
                   val deck: String = "") {
    companion object {
        private val DECK = Regex("^([a-z]{2})/(.+)$")
        fun parse(key: String): CardRef? {
            val parts = key.split(":", limit = 4).takeIf { it.size == 4 } ?: return null
            val (s, c, l, tail) = parts
            val m = DECK.find(tail)
            val deck = m?.groupValues?.get(1).orEmpty()
            val t = m?.groupValues?.get(2) ?: tail
            return when {
                t.startsWith("+") -> t.drop(1).takeIf { it.isNotEmpty() }?.let { CardRef(s, c, l, own = it, deck = deck) }
                t.startsWith("p") -> t.drop(1).toIntOrNull()?.let { CardRef(s, c, l, phrase = it, deck = deck) }
                else -> t.toIntOrNull()?.let { CardRef(s, c, l, index = it, deck = deck) }
            }
        }
        private fun pre(deck: String) = if (deck.isEmpty()) "" else "$deck/"
        fun word(series: String, chapter: String, letter: String, n: Int, deck: String = "") = "$series:$chapter:$letter:${pre(deck)}$n"
        fun phrase(series: String, chapter: String, letter: String, n: Int, deck: String = "") = "$series:$chapter:$letter:${pre(deck)}p$n"
        fun own(series: String, chapter: String, letter: String, lex: String, deck: String = "") = "$series:$chapter:$letter:${pre(deck)}+$lex"
        /** 열쇠의 자료 꼬리 (원어면 ""). */
        fun deckOf(key: String): String = parse(key)?.deck.orEmpty()
    }
}

/**
 * 하루에 새로 들이는 카드 수: 최대 [DAILY_NEW] 장. 넘치는 것은 ‘나중’ 줄에 두었다가 다음 날부터 앞에서부터 들인다.
 */
object Intake {
    const val DAILY_NEW = 15

    /** [queue] (나중 줄 + 새 카드, 앞이 먼저) 가운데 오늘 들일 것과 나중으로 미룰 것. [addedToday] 는 오늘 이미 들인 수. */
    fun split(queue: List<String>, addedToday: Int, cap: Int = DAILY_NEW): Pair<List<String>, List<String>> {
        val q = queue.distinct()
        val room = (cap - addedToday).coerceAtLeast(0)
        return q.take(room) to q.drop(room)
    }
}
