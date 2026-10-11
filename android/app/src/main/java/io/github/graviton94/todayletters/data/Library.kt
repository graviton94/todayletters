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
)

/** 이 편지를 받는 사람: 편지에 적힌 사람(아버지 · 누나 · 콘스탄체…)이 있으면 그 사람, 없으면 작품의 받는 사람(테오). */
fun Work.recipientOf(letter: Letter): Tri = letter.to ?: recipient

/** 이 작품에 ‘편지 속 그 곡’ 녹음이 하나라도 있는가 (서재 카드의 ‘듣기 있음’). */
val Work.hasMusic: Boolean get() = chapters.any { c -> c.letters.any { it.music != null } }

/** 프롤로그 한 장: 그 시절 · 제목 · 설명 + 그 시절을 말하는 원문 한 줄([letter] 의 첫 문장 · 낱말 · 그림). */
data class Docent(val period: String, val caps: String, val heading: Tri, val sub: String, val body: Tri, val source: Tri, val letter: Letter)
data class Arrival(val date: String, val route: String, val heading: Tri, val body: Tri)
/** 아웃트로: 맺음말 편지([epilogue], 장 "E") 와 생애를 맺는 글. */
data class Outro(val image: String, val credit: String, val caps: String, val name: String, val years: String, val body: List<Tri>, val head: Tri?, val epilogue: Letter?)

/** 앱 안에 넣은 편지 데이터 읽기 (scripts/generate.py 가 data/ 에서 assets/letters/ 로 옮김). */
object Library {
    @Volatile private var cache: List<Work>? = null

    fun works(ctx: Context): List<Work> = cache ?: load(ctx).also { if (it.isNotEmpty()) cache = it }

    private fun load(ctx: Context): List<Work> {
        val am = ctx.assets
        // 작품 목록: generate.py 가 쓴 index.json (없으면 폴더 목록)
        val ids = runCatching {
            org.json.JSONArray(am.open("letters/index.json").bufferedReader().readText()).let { a -> (0 until a.length()).map { a.getString(it) } }
        }.getOrElse { (am.list("letters") ?: emptyArray()).filter { !it.endsWith(".json") }.sorted() }
        return ids.mapNotNull { id ->
            val meta = runCatching { JSONObject(am.open("letters/$id/series.json").bufferedReader().readText()) }.getOrNull() ?: return@mapNotNull null
            val original = Lang.valueOf(meta.getString("original").uppercase())
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
                    Chapter(id, c.getString("id"), tri(c.getJSONObject("title")), c.optBoolean("free", false), letters(data))
                }
            }
            if (chapters.isEmpty()) return@mapNotNull null
            Work(
                Series(id, original), meta.getString("sender"), tri(meta.getJSONObject("title")), meta.optString("years"), meta.optString("seal", id), chapters,
                portrait = meta.optString("portrait"), credit = meta.optString("credit"),
                fullName = meta.optString("fullName", meta.getString("sender")), recipient = meta.optJSONObject("recipient")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to meta.optString("recipient"))),
                name = meta.optJSONObject("name")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to meta.getString("sender"))),
                places = meta.optJSONObject("places")?.let { o ->
                    o.keys().asSequence().map { k -> o.getJSONObject(k).let { p -> Place(k, p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), tri(p.getJSONObject("name")), tri(p.getJSONObject("note")), p.optDouble("lat", 0.0), p.optDouble("lng", 0.0)) } }.toList()
                } ?: emptyList(),
                kit = meta.optJSONObject("kit")?.let { kit(it) } ?: Kit(),
                prologue = prologue,
                prologueChapter = prologue.takeIf { it.isNotEmpty() }?.let { d -> Chapter(id, "P", Tri(mapOf(Lang.EN to "Prologue")), true, d.map { it.letter }) },
                arrival = meta.optJSONObject("prologue")?.optJSONObject("arrival")?.let { a -> Arrival(a.optString("date"), a.optString("route"), tri(a.getJSONObject("heading")), tri(a.getJSONObject("body"))) },
                outro = meta.optJSONObject("outro")?.let { o ->
                    val ep = runCatching { JSONObject(am.open("letters/$id/${o.getString("file")}").bufferedReader().readText()) }.getOrNull()
                    val first = ep?.getJSONArray("letters")?.optJSONObject(0)
                    Outro(o.optString("image"), o.optString("credit"), o.optString("caps"), o.optString("name"), o.optString("years"),
                        o.optJSONArray("body")?.let { b -> (0 until b.length()).map { tri(b.getJSONObject(it)) } } ?: emptyList(),
                        first?.optJSONObject("head")?.let { tri(it) }, ep?.let { letters(it).firstOrNull() })
                },
            )
        }
    }

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

    /** 낱말 한 줄: 세 언어 · 품사 · 발음 · 물건 그림 · 오늘의 철자 · 영어 발음 · 한국어 로마자. */
    internal fun word(o: JSONObject): Word = Word(tri(o), o.str("pos"), o.str("ipa"), o.str("icon"), modern(o), o.str("ipa_en"), o.str("rom_ko"))

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

    private fun letters(data: JSONObject): List<Letter> {
        val arr = data.getJSONArray("letters")
        return (0 until arr.length()).map { i ->
            val l = arr.getJSONObject(i)
            val msgs = l.getJSONArray("messages").let { m -> (0 until m.length()).map { Message(tri(m.getJSONObject(it))) } }
            val words = l.optJSONArray("words")?.let { w ->
                (0 until w.length()).map { j -> word(w.getJSONObject(j)) }
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
                reply = l.optJSONObject("reply")?.let { tri(it) }, moments = moments,
                status = l.optJSONObject("status")?.let { tri(it) },
                to = l.optJSONObject("to")?.let { tri(it) },
            )
        }
    }
}
