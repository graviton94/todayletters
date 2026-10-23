package io.github.graviton94.todayletters.data

import android.content.Context
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Chapter
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.Message
import io.github.graviton94.todayletters.core.Moment
import io.github.graviton94.todayletters.core.Place
import io.github.graviton94.todayletters.core.Spot
import io.github.graviton94.todayletters.core.Plate
import io.github.graviton94.todayletters.core.Series
import io.github.graviton94.todayletters.core.Tri
import io.github.graviton94.todayletters.core.Word
import io.github.graviton94.todayletters.core.Kit
import io.github.graviton94.todayletters.core.Tone
import io.github.graviton94.todayletters.core.Piece
import io.github.graviton94.todayletters.core.Tier
import io.github.graviton94.todayletters.core.Exhibition
import io.github.graviton94.todayletters.core.Daily
import io.github.graviton94.todayletters.core.DailySpot
import io.github.graviton94.todayletters.core.Example
import io.github.graviton94.todayletters.core.LexEntry
import io.github.graviton94.todayletters.core.Lexicon
import io.github.graviton94.todayletters.core.Phrase
import io.github.graviton94.todayletters.core.Token
import io.github.graviton94.todayletters.core.Vocab
import io.github.graviton94.todayletters.core.grammar
import org.json.JSONArray
import org.json.JSONObject

/** 작품 하나: 표지 정보 + 챕터들. assets/letters/<작품>/series.json 과 챕터 파일들. */
data class Work(
    val series: Series,
    val sender: String,
    val title: Tri,
    val years: String,
    val seal: String,
    val chapters: List<Chapter>,
    /** 동그란 초상: assets/portraits/<파일>. 저작권이 끝난 원화. */
    val portrait: String = "",
    val credit: String = "",
    val fullName: String = sender,
    val recipient: Tri = Tri(mapOf(Lang.EN to "")),
    /** 앱 글자에 쓰는 보내는 사람 이름 (빈센트 / Vincent). */
    val name: Tri = Tri(mapOf(Lang.EN to sender)),
    /** 널리 알려진 이름 (모차르트 / Mozart): 받기 시트 “모차르트의 시대로 떠날까요?”. 없으면 [name]. */
    val known: Tri = name,
    /** 손으로 그린 지도의 장소들 (위치 공유 · 지도). */
    val places: List<Place> = emptyList(),
    /** 시리즈 키트 (빛깔 · 대표 그림 · 화폐 이름 · 갤러리 소장품 · 이달의 전시). */
    val kit: Kit = Kit(),
    /** 프롤로그 (첫 편지 앞의 도슨트). 낱말 카드 열쇠는 장 "P". */
    val prologue: List<Docent> = emptyList(),
    val prologueChapter: Chapter? = null,
    /** 프롤로그 끝의 도착 화면 (파리 → 아를). */
    val arrival: Arrival? = null,
    /** 맺음말 · 아웃트로. */
    val outro: Outro? = null,
    /** 어휘 사전 (lexicon.json, 원어 낱말 → 뜻 · 등급 · 발음 · 예문). 없으면 null. 영어 · 한국어 자료의 작품이면 그 사전 (lexicon_en · _ko). */
    val lexicon: Lexicon? = null,
    /** 편지 낱말 · 사전이 어느 배우는 말의 것인가 (null = 원어). */
    val vocabLang: Lang? = null,
    /** 어휘 자료 꼬리 ("" = 원어, "en" · "ko"): gloss 파일 · 카드 열쇠에 쓴다. */
    val deck: String = "",
    /** 영어 · 한국어를 배울 때의 같은 작품 (그 언어 자료 *_en · *_ko.json 이 있을 때만). 없으면 원어 자료 그대로 (지금처럼). */
    val decks: Map<Lang, Work> = emptyMap(),
    /** 시리즈 소장 값 (series.json 의 "tier", docs/billing.md). 상품 id 는 core.Products.series(id). */
    val tier: io.github.graviton94.todayletters.core.PriceTier = io.github.graviton94.todayletters.core.PriceTier.T2,
    /** 말투 노트의 ‘실제 사이’ 한 줄 (series.json "register"): 편지 "to" 의 원어 글자 → ko · en, "default" 는 받는 사람이 없을 때. */
    val register: Map<String, Tri> = emptyMap(),
)

/** 이 편지에 답하는 사람과 보낸 사람이 실제로 어떤 사이인지 (말투 노트 한 줄). 없으면 null. */
fun Work.registerOf(letter: Letter): Tri? =
    letter.to?.lines?.get(series.original)?.let { register[Breaks.plain(it)] } ?: register["default"]

/** 이 작품의 모든 장 (프롤로그 · 챕터 · 맺음) 의 오늘의 문법에서 [id] 찾기. */
fun Work.grammarOf(id: String): io.github.graviton94.todayletters.core.Grammar? {
    if (id.isEmpty()) return null
    val all = listOfNotNull(prologueChapter) + chapters + listOfNotNull(outro?.let { Chapter(series.id, "E", Tri(mapOf(Lang.EN to "")), false, emptyList(), it.grammar) })
    return all.grammar(id)
}

/** 이 시리즈를 소장하는 상품 (구매 화면의 셋째 칸). */
val Work.seriesPlan: io.github.graviton94.todayletters.core.Plan.Series get() = io.github.graviton94.todayletters.core.Plan.Series(series.id, tier)

/** 편지 낱말 · 사전 · 눌러서 뜻 보기의 언어 (원어 자료면 원어). */
val Work.learnVocab: Lang get() = vocabLang ?: series.original

/** 배우는 말 [learn] 에 맞는 작품: 그 언어 자료가 있으면 그것, 없으면 원어 자료 그대로. */
fun Work.forLearn(learn: Lang): Work = if (learn == series.original) this else decks[learn] ?: this

/** 모든 배우는 말의 사전 (출처 화면). */
val Work.lexicons: List<Lexicon> get() = listOfNotNull(lexicon) + decks.values.mapNotNull { it.lexicon }

/** 편지마다 어휘 자료를 입힌다 (프롤로그 · 챕터 · 맺음말 편지). [set] 이 null 이면 챕터 낱말 그대로. */
fun Work.withVocab(set: io.github.graviton94.todayletters.core.VocabSet?): Work {
    val lang = set?.lang ?: series.original
    fun ap(l: Letter) = Vocab.apply(l, set?.daily?.get(l.id), set?.lexicon, lang, series.original)
    val pro = prologue.map { it.copy(letter = ap(it.letter)) }
    return copy(
        chapters = chapters.map { c -> c.copy(letters = c.letters.map(::ap)) },
        prologue = pro,
        prologueChapter = prologueChapter?.copy(letters = pro.map { it.letter }),
        outro = outro?.copy(epilogue = outro.epilogue?.let(::ap), epilogues = outro.epilogues.map(::ap)),
        lexicon = set?.lexicon,
        vocabLang = set?.lang?.takeIf { it != series.original },
        deck = set?.deck.orEmpty(),
    )
}

/** 이 편지를 받는 사람: 편지에 적힌 사람(아버지 · 누나 · 콘스탄체…)이 있으면 그 사람, 없으면 작품의 받는 사람(테오). */
fun Work.recipientOf(letter: Letter): Tri = letter.to ?: recipient

/** 이 작품에 ‘편지 속 그 곡’ 녹음이 하나라도 있는가. */
val Work.hasMusic: Boolean get() = chapters.any { c -> c.letters.any { it.music != null } }

/** 프롤로그 한 장: 그 시절 · 제목 · 설명 + 그 시절을 말하는 원문 한 줄([letter] 의 첫 문장 · 낱말 · 그림). */
data class Docent(val period: String, val caps: String, val heading: Tri, val sub: String, val body: Tri, val source: Tri, val letter: Letter)
/** [years] 는 프롤로그 위 연표, [at] 은 도슨트 쪽마다 (그리고 마지막 도착 화면의) 연표 자리. [label] 은 표지의 프롤로그 줄 이름 (아를 이전). */
data class Arrival(val date: String, val route: String, val heading: Tri, val body: Tri, val years: List<String> = emptyList(), val at: List<Int> = emptyList(), val label: Tri? = null)
/** 아웃트로: 맺음말 편지([epilogue], 장 "E") 와 생애를 맺는 글. */
data class Outro(val image: String, val credit: String, val caps: String, val name: String, val years: String, val body: List<Tri>, val head: Tri?, val epilogue: Letter?,
                 /** 맺음 편지 모두 (epilogue.json 의 letters, 차례대로). 보통 하나 ([epilogue] 와 같음), 고야는 보르도 셋. */
                 val epilogues: List<Letter> = listOfNotNull(epilogue),
                 /** 맺음 편지의 오늘의 문법 (epilogue.json 의 "grammar", 없으면 VIII 장 것을 id 로 가리킨다). */
                 val grammar: List<io.github.graviton94.todayletters.core.Grammar> = emptyList())

/** 앱 안에 넣은 편지 데이터 읽기 (scripts/generate.py 가 data/ 에서 assets/letters/ 로 옮김). */
object Library {
    @Volatile private var cache: List<Work>? = null

    fun works(ctx: Context): List<Work> = cache ?: load(ctx).also { if (it.isNotEmpty()) cache = it }

    /**
     * 시험 · 화면 사진용: 읽어 둔 작품을 [f] 로 바꿔 끼운다 (실제 데이터가 아직 없는 화면을 찍을 때, 끝나면 되돌린다). 앱에서는 쓰지 않는다.
     */
    fun swapForTest(ctx: Context, f: (List<Work>) -> List<Work>) { cache = f(works(ctx)) }

    private fun load(ctx: Context): List<Work> {
        val am = ctx.assets
        // 작품 목록: generate.py 가 쓴 index.json (없으면 폴더 목록)
        val ids = runCatching {
            org.json.JSONArray(am.open("letters/index.json").bufferedReader().readText()).let { a -> (0 until a.length()).map { a.getString(it) } }
        }.getOrElse { (am.list("letters") ?: emptyArray()).filter { !it.endsWith(".json") }.sorted() }
        return ids.mapNotNull { id ->
            val meta = runCatching { JSONObject(am.open("letters/$id/series.json").bufferedReader().readText()) }.getOrNull() ?: return@mapNotNull null
            val original = Lang.valueOf(meta.getString("original").uppercase())
            // "origLine": true (고야) 면 메시지의 찍힌 글 (orig.<원어>) 도 읽는다: 편지 방의 ‘원래 철자’ 줄
            val origLang = original.takeIf { meta.optBoolean("origLine", false) }
            fun letters(data: JSONObject) = parseLetters(data, origLang)
            val prologue = meta.optJSONObject("prologue")?.optString("file")?.takeIf { it.isNotEmpty() }?.let { f ->
                runCatching {
                    val data = JSONObject(am.open("letters/$id/$f").bufferedReader().readText())
                    val ls = letters(data)
                    val arr = data.getJSONArray("letters")
                    ls.mapIndexed { i, l ->
                        val d = arr.getJSONObject(i).getJSONObject("docent")
                        Docent(d.optString("period"), d.optString("caps"), tri(d.getJSONObject("heading")), d.optString("sub"), tri(d.getJSONObject("body")), tri(d.getJSONObject("source")), l)
                    }
                }.getOrNull()
            } ?: emptyList()
            // 챕터 파일이 아직 없으면(작업 중인 작품) 그 장만 건너뛴다: 한 작품의 빈 장 때문에 서재 전체가 비지 않게
            val chapters = meta.getJSONArray("chapters").let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val c = arr.getJSONObject(i)
                    val data = runCatching { JSONObject(am.open("letters/$id/${c.getString("file")}").bufferedReader().readText()) }.getOrNull() ?: return@mapNotNull null
                    Chapter(id, c.getString("id"), tri(c.getJSONObject("title")), c.optBoolean("free", false), letters(data), grammar(data))
                }
            }
            if (chapters.isEmpty()) return@mapNotNull null
            val raw = Work(
                Series(id, original), meta.getString("sender"), tri(meta.getJSONObject("title")), meta.optString("years"), meta.optString("seal", id), chapters,
                portrait = meta.optString("portrait"), credit = meta.optString("credit"),
                fullName = meta.optString("fullName", meta.getString("sender")), recipient = meta.optJSONObject("recipient")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to meta.optString("recipient"))),
                name = meta.optJSONObject("name")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to meta.getString("sender"))),
                known = (meta.optJSONObject("known") ?: meta.optJSONObject("name"))?.let { tri(it) } ?: Tri(mapOf(Lang.EN to meta.getString("sender"))),
                places = meta.optJSONObject("places")?.let { o ->
                    o.keys().asSequence().map { k -> o.getJSONObject(k).let { p -> Place(k, p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), tri(p.getJSONObject("name")), tri(p.getJSONObject("note")), p.optDouble("lat", 0.0), p.optDouble("lng", 0.0)) } }.toList()
                } ?: emptyList(),
                kit = meta.optJSONObject("kit")?.let { kit(it) } ?: Kit(),
                tier = io.github.graviton94.todayletters.core.PriceTier.of(meta.optInt("tier", 2)),
                register = meta.optJSONObject("register")?.let { o -> o.keys().asSequence().filter { !it.startsWith("_") }.mapNotNull { k -> o.optJSONObject(k)?.let { k to tri(it) } }.toMap() } ?: emptyMap(),
                prologue = prologue,
                prologueChapter = prologue.takeIf { it.isNotEmpty() }?.let { d -> Chapter(id, "P", Tri(mapOf(Lang.EN to "Prologue")), true, d.map { it.letter }) },
                arrival = meta.optJSONObject("prologue")?.let { pr -> pr.optJSONObject("arrival")?.let { a ->
                    val tl = pr.optJSONObject("timeline")
                    Arrival(a.optString("date"), a.optString("route"), tri(a.getJSONObject("heading")), tri(a.getJSONObject("body")),
                        years = tl?.optJSONArray("years")?.let { y -> (0 until y.length()).map { y.getString(it) } } ?: emptyList(),
                        at = tl?.optJSONArray("at")?.let { y -> (0 until y.length()).map { y.getInt(it) } } ?: emptyList(),
                        label = pr.optJSONObject("label")?.let { tri(it) })
                } },
                outro = meta.optJSONObject("outro")?.let { o ->
                    val ep = runCatching { JSONObject(am.open("letters/$id/${o.getString("file")}").bufferedReader().readText()) }.getOrNull()
                    val first = ep?.getJSONArray("letters")?.optJSONObject(0)
                    Outro(o.optString("image"), o.optString("credit"), o.optString("caps"), o.optString("name"), o.optString("years"),
                        o.optJSONArray("body")?.let { b -> (0 until b.length()).map { tri(b.getJSONObject(it)) } } ?: emptyList(),
                        first?.optJSONObject("head")?.let { tri(it) }, ep?.let { letters(it).firstOrNull() },
                        epilogues = ep?.let { letters(it) } ?: emptyList(), grammar = ep?.let { grammar(it) } ?: emptyList())
                },
            )
            // 어휘 자료: 배우는 말마다 한 벌 (원어 lexicon · daily.json, 영어 *_en, 한국어 *_ko). 없으면 챕터 파일의 낱말 그대로
            val sets = vocabSets(original) { name -> runCatching { am.open("letters/$id/$name").bufferedReader().readText() }.getOrNull() }
            raw.withVocab(sets[original]).copy(decks = sets.filterKeys { it != original }.mapValues { (_, set) -> raw.withVocab(set) })
        }
    }

    /**
     * 작품의 어휘 자료들: 배우는 말 → 자료 한 벌. 원어는 lexicon.json · daily.json, 영어 · 한국어는 lexicon_en.json · daily_en.json …
     * 사전 파일이 없는 언어는 빠진다 (그 언어를 배울 때는 원어 자료 그대로 = 지금처럼). [read] 는 파일 이름 → 내용 (없으면 null).
     */
    fun vocabSets(original: Lang, read: (String) -> String?): Map<Lang, io.github.graviton94.todayletters.core.VocabSet> =
        listOf(original, Lang.EN, Lang.KO).distinct().mapNotNull { lang ->
            val deck = io.github.graviton94.todayletters.core.VocabSet.deckFor(lang, original)
            fun f(base: String) = io.github.graviton94.todayletters.core.VocabSet.file(base, deck)
            val lex = read(f("lexicon"))?.let { runCatching { lexicon(it) }.getOrNull() } ?: return@mapNotNull null
            val daily = read(f("daily"))?.let { runCatching { daily(it) }.getOrNull() } ?: emptyMap()
            lang to io.github.graviton94.todayletters.core.VocabSet(lang, deck, lex, daily)
        }.toMap()

    private fun color(s: String?, fallback: Long): Long =
        s?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it } ?: fallback

    private fun tone(o: JSONObject?): Tone? = o?.let {
        Tone(color(it.optString("paper"), 0xFFF4EFE6), color(it.optString("ink"), 0xFF1F1A15), color(it.optString("soft"), 0xFF6E655A),
            color(it.optString("faint"), 0xFFA39886), color(it.optString("accent"), 0xFF8A7350))
    }

    private fun kit(o: JSONObject): Kit {
        val theme = o.optJSONObject("theme")
        return Kit(
            light = tone(theme?.optJSONObject("light")),
            dark = tone(theme?.optJSONObject("dark")),
            hero = o.optString("hero"),
            place = o.optString("place"),
            world = o.optJSONObject("world")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to "")),
            currency = o.optJSONObject("currency")?.optJSONObject("name")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to "")),
            weekdays = o.optString("weekdays", "MTWTFSS"),
            tag = o.optJSONObject("tag")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to "")),
            about = o.optJSONObject("about")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to "")),
            fullName = o.optJSONObject("fullName")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to "")),
            collection = o.optJSONArray("collection")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.getJSONObject(i).let { c ->
                    val tier = runCatching { Tier.valueOf(c.getString("tier").uppercase()) }.getOrNull() ?: return@mapNotNull null
                    Piece(c.getString("id"), c.getString("image"), tri(c.getJSONObject("title")), c.optString("date"), c.optString("collection"), tier,
                        c.optJSONArray("themes")?.let { t -> (0 until t.length()).map { t.getString(it) }.toSet() } ?: emptySet())
                } }
            } ?: emptyList(),
            exhibitions = o.optJSONArray("exhibitions")?.let { a ->
                (0 until a.length()).map { i -> a.getJSONObject(i).let { e -> Exhibition(e.getString("id"), tri(e.getJSONObject("title")), e.getString("theme")) } }
            } ?: emptyList(),
        )
    }

    /** 글자 하나 (없거나 JSON null 이면 빈 글자: optString 은 null 을 "null" 로 돌려준다). */
    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

    /** 오늘의 철자: 원어 코드에 _modern 을 붙인 칸 (de_modern · fr_modern). */
    private fun modern(o: JSONObject): String = Lang.entries.firstNotNullOfOrNull { l -> o.str(l.code + "_modern").takeIf { it.isNotEmpty() } }.orEmpty()

    /** 낱말 한 줄: 세 언어 · 품사 · 발음 · 물건 그림 · 오늘의 철자 · 영어 발음 · 한국어 로마자 · 한국어 소리 나는 대로 (say_ko) · 소리 규칙 한 줄 (pronNote {ko, en}). */
    internal fun word(o: JSONObject): Word = Word(tri(o), o.str("pos"), o.str("ipa"), o.str("icon"), modern(o), o.str("ipa_en"), o.str("rom_ko"),
        sayKo = o.str("say_ko"), pronNote = o.optJSONObject("pronNote")?.let { tri(it) }?.takeIf { it.lines.isNotEmpty() })

    /** 순간 하나. 모르는 꼴이거나 칸이 모자라면 null (편지 한 통의 순간 하나 때문에 작품 전체를 못 읽는 일이 없게). */
    internal fun moment(m: JSONObject): Moment? = runCatching {
        when (m.optString("type")) {
            "location" -> Moment.Location(m.getInt("after"), m.getString("place"), tri(m.getJSONObject("title")), m.optString("address"))
            "photo" -> Moment.Photo(m.getInt("after"), m.getString("image"), tri(m.getJSONObject("caption")))
            "sketch" -> Moment.Photo(m.getInt("after"), m.getString("image"), tri(m.getJSONObject("caption")), sketch = true, credit = m.optString("credit"))
            "transfer" -> Moment.Transfer(m.getInt("after"), m.getString("amount"), tri(m.getJSONObject("label")), tri(m.getJSONObject("memo")), m.optBoolean("outgoing"))
            "notice" -> Moment.Notice(m.getInt("after"), tri(m.getJSONObject("text")))
            // 날씨: 제목 · 큰 값 둘로 나뉜 것(빈센트)과 한 줄 글(text)만 있는 것(모차르트) 둘 다
            "weather" -> {
                val text = m.optJSONObject("text")?.let { tri(it) }
                Moment.Weather(m.getInt("after"), m.optJSONObject("title")?.let { tri(it) } ?: text!!,
                    m.optJSONObject("value")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to "")), m.str("quote"), m.optString("icon", "snow"))
            }
            "music" -> Moment.Music(m.getInt("after"), tri(m.getJSONObject("piece")), m.str("audio"), m.str("performer"), m.str("audioLicense"), m.str("audioSource"))
            else -> null
        }
    }.getOrNull()

    /** 세 언어 글자. 한국어가 낱말 가운데서 줄이 바뀌지 않게 [Breaks.keepAll] 을 거친다. */
    private fun tri(o: JSONObject, prefix: String = ""): Tri =
        Tri(Lang.entries.mapNotNull { l -> o.optString(prefix + l.code, "").takeIf { it.isNotEmpty() }?.let { l to Breaks.keepAll(it) } }.toMap())

    /**
     * 장의 오늘의 문법 셋 ("grammar": [{id, fr|de|en|ko: {name: {ko, en}, hint: {ko, en}}}]). 칸이 모자란 것은 건너뛴다.
     */
    internal fun grammar(data: JSONObject): List<io.github.graviton94.todayletters.core.Grammar> {
        val a = data.optJSONArray("grammar") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val g = a.optJSONObject(i) ?: return@mapNotNull null
            val id = g.str("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val lines = Lang.entries.mapNotNull { lang ->
                g.optJSONObject(lang.code)?.let { o ->
                    val name = o.optJSONObject("name")?.let { tri(it) } ?: return@let null
                    lang to io.github.graviton94.todayletters.core.GrammarLine(name, o.optJSONObject("hint")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to "")))
                }
            }.toMap()
            io.github.graviton94.todayletters.core.Grammar(id, lines)
        }
    }

    /**
     * 답장 턴들: "replies" (턴마다 quote · grammar · 언어 줄 · koLow · koHigh · focus · wrong), 없으면 예전 "reply" (+ "quote") 하나를 문법 없는 한 턴으로.
     */
    internal fun replies(l: JSONObject): List<io.github.graviton94.todayletters.core.ReplyTurn> {
        l.optJSONArray("replies")?.let { a ->
            return (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val text = tri(o).takeIf { it.lines.isNotEmpty() } ?: return@mapNotNull null
                val focus = focus(o)
                val wrong = wrong(o)
                io.github.graviton94.todayletters.core.ReplyTurn(o.optInt("quote", -1), o.str("grammar"), text,
                    Breaks.keepAll(o.str("koLow")), Breaks.keepAll(o.str("koHigh")), focus, wrong)
            }
        }
        return l.optJSONObject("reply")?.let { r -> tri(r).takeIf { it.lines.isNotEmpty() }?.let { listOf(io.github.graviton94.todayletters.core.ReplyTurn(r.optInt("quote", -1), "", it)) } } ?: emptyList()
    }

    /** 빈칸 덩어리 (언어마다): {"fr": ["ai lu", ...]}. 답장 턴 · 회화 줄이 같이 쓴다. */
    private fun focus(o: JSONObject): Map<Lang, List<String>> = o.optJSONObject("focus")?.let { f ->
        Lang.entries.mapNotNull { lang -> f.optJSONArray(lang.code)?.let { arr -> lang to (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() } } }.toMap()
    } ?: emptyMap()

    /** 덩어리마다 틀린 보기 2개 (focus 와 같은 차례): {"fr": [["lis", "lirai"], ...]}. */
    private fun wrong(o: JSONObject): Map<Lang, List<List<String>>> = o.optJSONObject("wrong")?.let { w ->
        Lang.entries.mapNotNull { lang -> w.optJSONArray(lang.code)?.let { arr ->
            lang to (0 until arr.length()).map { i -> arr.optJSONArray(i)?.let { p -> (0 until p.length()).map { p.optString(it) }.filter { it.isNotBlank() } }.orEmpty() }
        } }.toMap()
    } ?: emptyMap()

    /**
     * 이해 확인 ("check": [{q: {fr|de, en, ko}, options: [{…} × 4], answer: 0–3, evidence: 3 | [3, 4], mark?: {fr|de, en, ko}}]).
     * 질문 · 보기가 비었거나 정답 번호가 보기 밖이면 그 문제는 건너뛴다. 근거 번호는 편지 메시지 안의 것만.
     */
    internal fun checks(l: JSONObject, messages: Int): List<io.github.graviton94.todayletters.core.Check> {
        val a = l.optJSONArray("check") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val q = o.optJSONObject("q")?.let { tri(it) }?.takeIf { it.lines.isNotEmpty() } ?: return@mapNotNull null
            val opts = o.optJSONArray("options")?.let { op -> (0 until op.length()).mapNotNull { k -> op.optJSONObject(k)?.let { tri(it) }?.takeIf { it.lines.isNotEmpty() } } }.orEmpty()
            val answer = o.optInt("answer", -1)
            if (opts.size < 2 || answer !in opts.indices) return@mapNotNull null
            val ev = when (val e = o.opt("evidence")) {
                is JSONArray -> (0 until e.length()).map { e.optInt(it, -1) }
                is Number -> listOf(e.toInt())
                else -> emptyList()
            }.filter { it in 0 until messages }.distinct().sorted()
            io.github.graviton94.todayletters.core.Check(q, opts, answer, ev, o.optJSONObject("mark")?.let { tri(it) }?.takeIf { it.lines.isNotEmpty() })
        }
    }

    /**
     * 오늘의 회화 ("talk": {scene: {ko, en}, lines: [{who: "them"|"me", fr|de, en, ko, focus?, wrong?}], words: [원어 표제어…] 또는 {fr|de: […], en: […], ko: […]}, grammar: "I.0", audio?: "t" 또는 {"*": "t", "ko": "tk"} (언어마다 다른 소리 앞부분, 없는 언어는 "*")}).
     * 줄이 없으면 null (걸음을 건너뛴다).
     */
    internal fun talk(l: JSONObject): io.github.graviton94.todayletters.core.Talk? {
        val t = l.optJSONObject("talk") ?: return null
        val lines = t.optJSONArray("lines")?.let { a -> (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val text = tri(o).takeIf { it.lines.isNotEmpty() } ?: return@mapNotNull null
            io.github.graviton94.todayletters.core.TalkLine(o.optString("who") == "me", text, focus(o), wrong(o))
        } }.orEmpty()
        if (lines.isEmpty()) return null
        val au = t.optJSONObject("audio")
        return io.github.graviton94.todayletters.core.Talk(
            t.optJSONObject("scene")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to "")), lines,
            t.optJSONArray("words")?.let { w -> (0 until w.length()).map { w.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
            t.str("grammar"), au?.let { it.optString("*").ifEmpty { it.optString("default") } } ?: t.str("audio"),
            wordsBy = t.optJSONObject("words")?.let { o -> Lang.entries.mapNotNull { lang -> o.optJSONArray(lang.code)?.let { a -> lang to (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } }.toMap() }.orEmpty(),
            audioBy = au?.let { o -> Lang.entries.mapNotNull { lang -> o.optString(lang.code).takeIf { it.isNotEmpty() }?.let { lang to it } }.toMap() }.orEmpty(),
            mine = mine(t),
        )
    }

    /**
     * 내 이야기로 ("mine": {line: 내 줄 번호, slot: {fr|de, en, ko}, opts: [{…} × 3]}). 없거나 모자라면 null (그 화면을 건너뛴다).
     * 글은 이음표 없이 그대로 둔다 (자리 찾기는 core [io.github.graviton94.todayletters.core.Mine] 가 이음표를 뺀 글로).
     */
    private fun mine(t: JSONObject): io.github.graviton94.todayletters.core.TalkMine? {
        val m = t.optJSONObject("mine") ?: return null
        fun langs(o: JSONObject?): Map<Lang, String> = o?.let { x -> Lang.entries.mapNotNull { l -> x.optString(l.code, "").takeIf { it.isNotEmpty() }?.let { l to it } }.toMap() }.orEmpty()
        val line = m.optInt("line", -1).takeIf { it >= 0 } ?: return null
        val slot = langs(m.optJSONObject("slot")).takeIf { it.isNotEmpty() } ?: return null
        val opts = m.optJSONArray("opts")?.let { a -> (0 until a.length()).map { langs(a.optJSONObject(it)) } }.orEmpty().filter { it.isNotEmpty() }
        return if (opts.isEmpty()) null else io.github.graviton94.todayletters.core.TalkMine(line, slot, opts)
    }

    internal fun parseLetters(data: JSONObject, origLang: Lang? = null): List<Letter> {
        val arr = data.getJSONArray("letters")
        return (0 until arr.length()).map { i ->
            val l = arr.getJSONObject(i)
            val msgs = l.getJSONArray("messages").let { m -> (0 until m.length()).map { k ->
                val o = m.getJSONObject(k)
                val text = tri(o)
                // 원래 철자: 지금 철자와 낱말이 다를 때만 (문장부호 · 대소문자만 다르면 줄을 보이지 않는다)
                val orig = origLang?.let { lang -> o.optJSONObject("orig")?.str(lang.code) }.orEmpty().trim()
                fun bare(t: String) = t.lowercase().filter { it.isLetterOrDigit() }
                Message(text, orig.takeIf { it.isNotEmpty() && bare(it) != bare(o.str(origLang?.code ?: "")) }.orEmpty())
            } }
            val words = l.optJSONArray("words")?.let { w ->
                (0 until w.length()).map { j -> word(w.getJSONObject(j)).copy(audio = "w${j + 1}") }
            } ?: emptyList()
            val plate = l.optJSONObject("plate")?.let { pl ->
                val spots = pl.optJSONArray("spots")?.let { a ->
                    (0 until a.length()).map { k -> a.getJSONObject(k).let { sp ->
                        Spot(sp.getDouble("x").toFloat(), sp.getDouble("y").toFloat(), tri(sp.getJSONObject("title")), tri(sp.getJSONObject("note")),
                            sp.optJSONObject("word")?.let { wo -> Word(tri(wo), "", wo.optString("ipa")) },
                            if (sp.has("quote")) sp.getInt("quote") else null)
                    } }
                } ?: emptyList()
                Plate(tri(pl, "title_"), pl.optString("date"), pl.optString("collection"), pl.optString("image"), spots, pl.str("imageSource"),
                    kind = pl.str("kind"), credit = pl.str("credit"), license = pl.str("imageLicense"), why = pl.optJSONObject("why")?.let { tri(it) })
            }
            val moments = l.optJSONArray("moments")?.let { a ->
                (0 until a.length()).mapNotNull { k -> moment(a.getJSONObject(k)) }
            } ?: emptyList()
            Letter(
                id = l.getString("id"), date = l.optString("date"), place = l.optString("place"), mood = l.optString("mood", "calm"),
                messages = msgs, words = words, note = l.optJSONObject("note")?.let { tri(it) }, plate = plate,
                replies = replies(l), moments = moments,
                status = l.optJSONObject("status")?.let { tri(it) },
                to = l.optJSONObject("to")?.let { tri(it) },
                checks = checks(l, msgs.size),
                talk = talk(l),
            )
        }
    }

    // ── 어휘 자료 (data/lexicon/README.md) ─────────────────────
    private fun JSONArray.strAt(i: Int): String = if (i >= length() || isNull(i)) "" else optString(i)

    /** lexicon.json: "fields" 순서의 배열 한 줄 = 표제어 하나 (뒤쪽 null 은 생략). */
    fun lexicon(json: String): Lexicon {
        val o = JSONObject(json)
        val lang = runCatching { Lang.valueOf(o.getString("lang").uppercase()) }.getOrDefault(Lang.FR)
        val fields = o.getJSONArray("fields").let { f -> (0 until f.length()).map { f.getString(it) } }
        fun at(name: String) = fields.indexOf(name)
        val iLemma = at("lemma"); val iId = at("id"); val iForms = at("forms"); val iPos = at("pos"); val iGender = at("gender")
        val iKo = at("ko"); val iEn = at("en"); val iIpa = at("ipa"); val iLevel = at("level"); val iFirst = at("first")
        val iLetters = at("letters"); val iEx = at("example"); val iFn = at("function")
        // 발음 칸 (있으면): 영어 IPA (영어 사전은 ipa 칸 그대로) · 한국어 소리 나는 대로 "pron" ("[가치]") · 로마자 "rom" · 소리 규칙 "pron_note" {ko, en}
        // (같은 뜻의 다른 이름 ipa_en · say_ko · rom_ko · pronNote 도 읽는다)
        fun first(vararg names: String) = names.map(::at).firstOrNull { it >= 0 } ?: -1
        val iIpaEn = at("ipa_en"); val iRom = first("rom", "rom_ko"); val iSay = first("pron", "say_ko"); val iNote = first("pron_note", "pronNote")
        val rows = o.getJSONArray("entries")
        val entries = (0 until rows.length()).mapNotNull { r ->
            val a = rows.optJSONArray(r) ?: return@mapNotNull null
            fun s(i: Int) = if (i < 0) "" else a.strAt(i)
            val ex = if (iEx >= 0 && iEx < a.length()) a.optJSONArray(iEx)?.let { e ->
                runCatching { Example(e.getLong(0), e.strAt(1), e.strAt(2), e.strAt(3).takeIf { it.isNotEmpty() }, e.strAt(4)) }.getOrNull()
            } else null
            LexEntry(
                lemma = s(iLemma).takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                id = s(iId).takeIf { it.isNotEmpty() },
                forms = if (iForms >= 0 && iForms < a.length()) a.optJSONArray(iForms)?.let { f -> (0 until f.length()).map { f.getString(it) } } ?: emptyList() else emptyList(),
                pos = s(iPos), gender = s(iGender), ko = s(iKo), en = s(iEn), ipa = s(iIpa), level = s(iLevel), first = s(iFirst),
                letters = s(iLetters).split(" ").filter { it.isNotEmpty() },
                example = ex,
                function = iFn >= 0 && iFn < a.length() && !a.isNull(iFn) && a.optInt(iFn, 0) == 1,
                ipaEn = s(iIpaEn), romKo = s(iRom), sayKo = s(iSay).trim().removePrefix("[").removeSuffix("]").trim(),
                pronNote = if (iNote >= 0 && iNote < a.length()) a.optJSONObject(iNote)?.let { tri(it) }?.takeIf { it.lines.isNotEmpty() } else null,
            )
        }
        return Lexicon(lang, entries)
    }

    /** daily.json: 편지 id → 오늘의 낱말 · 그림 점 바꾸기 · 표현. 그림 점의 w 가 [] · null · "" 이면 그 점에 낱말을 두지 않는다. */
    fun daily(json: String): Map<String, Daily> {
        val ls = JSONObject(json).getJSONObject("letters")
        return ls.keys().asSequence().associateWith { k ->
            val d = ls.getJSONObject(k)
            Daily(
                words = d.optJSONArray("words")?.let { w -> (0 until w.length()).map { w.getString(it) } } ?: emptyList(),
                spots = d.optJSONArray("spots")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { sp ->
                    val w = if (sp.isNull("w")) null else (sp.opt("w") as? String)?.takeIf { it.isNotBlank() }
                    DailySpot(sp.optInt("i", -1), w, sp.str("was"))
                } } } ?: emptyList(),
                phrases = d.optJSONArray("phrases")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { ph ->
                    Phrase(ph.str("t"), ph.optInt("m", -1), ph.str("ko"), ph.str("en"), ph.str("note_ko"), ph.str("note_en"))
                } } } ?: emptyList(),
            )
        }
    }

    /** gloss.json: 편지 id → 메시지마다 토큰 (편지에 찍힌 꼴, 사전 열쇠). */
    fun gloss(json: String): Map<String, List<List<Token>>> {
        val ls = JSONObject(json).getJSONObject("letters")
        return ls.keys().asSequence().associateWith { k ->
            val msgs = ls.getJSONArray(k)
            (0 until msgs.length()).map { m ->
                val toks = msgs.getJSONArray(m)
                (0 until toks.length()).map { t ->
                    when (val x = toks.get(t)) {
                        is JSONArray -> Token(x.getString(0), x.getString(1))
                        else -> x.toString().let { Token(it, it) }
                    }
                }
            }
        }
    }

    private val glosses = java.util.concurrent.ConcurrentHashMap<String, Map<String, List<List<Token>>>>()

    /** 눌러서 뜻 보기 토큰 (작품 · 자료마다 처음 쓸 때 읽는다). [deck] 은 "" (원어) · "en" · "ko". 없으면 빈 것. */
    fun gloss(ctx: Context, series: String, deck: String = ""): Map<String, List<List<Token>>> = glosses.getOrPut("$series/$deck") {
        runCatching { gloss(ctx.assets.open("letters/$series/${io.github.graviton94.todayletters.core.VocabSet.file("gloss", deck)}").bufferedReader().readText()) }.getOrDefault(emptyMap())
    }
}
