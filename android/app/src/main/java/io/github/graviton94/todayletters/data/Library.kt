package io.github.graviton94.todayletters.data

import android.content.Context
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Chapter
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.Message
import io.github.graviton94.todayletters.core.Plate
import io.github.graviton94.todayletters.core.Series
import io.github.graviton94.todayletters.core.Tri
import io.github.graviton94.todayletters.core.Word
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
    val recipient: String = "",
    /** 앱 글자에 쓰는 보내는 사람 이름 (빈센트 / Vincent). */
    val name: Tri = Tri(mapOf(Lang.EN to sender)),
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
                fullName = meta.optString("fullName", meta.getString("sender")), recipient = meta.optString("recipient"),
                name = meta.optJSONObject("name")?.let { tri(it) } ?: Tri(mapOf(Lang.EN to meta.getString("sender"))),
            )
        }
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
                (0 until w.length()).map { j -> w.getJSONObject(j).let { Word(tri(it), it.optString("pos"), it.optString("ipa")) } }
            } ?: emptyList()
            val plate = l.optJSONObject("plate")?.let { pl ->
                Plate(tri(pl, "title_"), pl.optString("date"), pl.optString("collection"), pl.optString("image"))
            }
            Letter(
                id = l.getString("id"), date = l.optString("date"), place = l.optString("place"), mood = l.optString("mood", "calm"),
                messages = msgs, words = words, note = l.optJSONObject("note")?.let { tri(it) }, plate = plate,
                reply = l.optJSONObject("reply")?.let { tri(it) },
            )
        }
    }
}
