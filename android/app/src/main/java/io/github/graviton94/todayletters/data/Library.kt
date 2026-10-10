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
)

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
            val chapters = meta.getJSONArray("chapters").let { arr ->
                (0 until arr.length()).map { i ->
                    val c = arr.getJSONObject(i)
                    val data = JSONObject(am.open("letters/$id/${c.getString("file")}").bufferedReader().readText())
                    Chapter(id, c.getString("id"), tri(c.getJSONObject("title")), c.optBoolean("free", false), letters(data))
                }
            }
            Work(
                Series(id, original), meta.getString("sender"), tri(meta.getJSONObject("title")), meta.optString("years"), meta.optString("seal", id), chapters,
                portrait = meta.optString("portrait"), credit = meta.optString("credit"),
                fullName = meta.optString("fullName", meta.getString("sender")), recipient = meta.optJSONObject("recipient")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to meta.optString("recipient"))),
                name = meta.optJSONObject("name")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to meta.getString("sender"))),
                places = meta.optJSONObject("places")?.let { o ->
                    o.keys().asSequence().map { k -> o.getJSONObject(k).let { p -> Place(k, p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), tri(p.getJSONObject("name")), tri(p.getJSONObject("note")), p.optDouble("lat", 0.0), p.optDouble("lng", 0.0)) } }.toList()
                } ?: emptyList(),
                kit = meta.optJSONObject("kit")?.let { kit(it) } ?: Kit(),
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

    /** 세 언어 글자. 한국어가 낱말 가운데서 줄이 바뀌지 않게 [Breaks.keepAll] 을 거친다. */
    private fun tri(o: JSONObject, prefix: String = ""): Tri =
        Tri(Lang.entries.mapNotNull { l -> o.optString(prefix + l.code, "").takeIf { it.isNotEmpty() }?.let { l to Breaks.keepAll(it) } }.toMap())

    private fun letters(data: JSONObject): List<Letter> {
        val arr = data.getJSONArray("letters")
        return (0 until arr.length()).map { i ->
            val l = arr.getJSONObject(i)
            val msgs = l.getJSONArray("messages").let { m -> (0 until m.length()).map { Message(tri(m.getJSONObject(it))) } }
            val words = l.optJSONArray("words")?.let { w ->
                (0 until w.length()).map { j -> w.getJSONObject(j).let { Word(tri(it), it.optString("pos"), it.optString("ipa"), it.optString("icon")) } }
            } ?: emptyList()
            val plate = l.optJSONObject("plate")?.let { pl ->
                val spots = pl.optJSONArray("spots")?.let { a ->
                    (0 until a.length()).map { k -> a.getJSONObject(k).let { sp ->
                        Spot(sp.getDouble("x").toFloat(), sp.getDouble("y").toFloat(), tri(sp.getJSONObject("title")), tri(sp.getJSONObject("note")),
                            sp.optJSONObject("word")?.let { wo -> Word(tri(wo), "", wo.optString("ipa")) },
                            if (sp.has("quote")) sp.getInt("quote") else null)
                    } }
                } ?: emptyList()
                Plate(tri(pl, "title_"), pl.optString("date"), pl.optString("collection"), pl.optString("image"), spots, pl.optString("imageSource"))
            }
            val moments = l.optJSONArray("moments")?.let { a ->
                (0 until a.length()).mapNotNull { k -> a.getJSONObject(k).let { m ->
                    when (m.optString("type")) {
                        "location" -> Moment.Location(m.getInt("after"), m.getString("place"), tri(m.getJSONObject("title")), m.optString("address"))
                        "photo" -> Moment.Photo(m.getInt("after"), m.getString("image"), tri(m.getJSONObject("caption")))
                        "sketch" -> Moment.Photo(m.getInt("after"), m.getString("image"), tri(m.getJSONObject("caption")), sketch = true, credit = m.optString("credit"))
                        "transfer" -> Moment.Transfer(m.getInt("after"), m.getString("amount"), tri(m.getJSONObject("label")), tri(m.getJSONObject("memo")), m.optBoolean("outgoing"))
                        "notice" -> Moment.Notice(m.getInt("after"), tri(m.getJSONObject("text")))
                        "weather" -> Moment.Weather(m.getInt("after"), tri(m.getJSONObject("title")), tri(m.getJSONObject("value")), m.optString("quote"), m.optString("icon", "snow"))
                        else -> null
                    }
                } }
            } ?: emptyList()
            Letter(
                id = l.getString("id"), date = l.optString("date"), place = l.optString("place"), mood = l.optString("mood", "calm"),
                messages = msgs, words = words, note = l.optJSONObject("note")?.let { tri(it) }, plate = plate,
                reply = l.optJSONObject("reply")?.let { tri(it) }, moments = moments,
                status = l.optJSONObject("status")?.let { tri(it) },
            )
        }
    }
}
