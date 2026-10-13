package io.github.graviton94.todayletters.data

import android.content.Context
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Spend
import org.json.JSONObject

/**
 * 상점 줄의 작은 그림 (모든 시리즈가 같은 그림): 내일 편지 미리 열기 · 쉼표 · 카드 테두리.
 * data/shop.json → assets/shop/shop.json (scripts/generate.py). 그림은 assets/shop/<image> (132×132).
 * 출처 화면과 사이트(scripts/site_sources.py)가 같은 데이터를 쓴다.
 */
class ShopPicture(
    val spend: Spend?,
    /** assets 안 경로 ("shop/vermeer-letter.webp"). */
    val image: String,
    private val title: Map<String, String>,
    val artist: String,
    val date: String,
    val museum: String,
    val objectId: String,
    val license: String,
    val url: String,
    private val detail: Map<String, String>,
) {
    fun title(l: Lang) = title[l.code] ?: title["en"].orEmpty()
    fun detail(l: Lang) = detail[l.code] ?: detail["en"].orEmpty()
}

object ShopArt {
    @Volatile private var cache: List<ShopPicture>? = null

    fun all(ctx: Context): List<ShopPicture> = cache ?: runCatching {
        val doc = JSONObject(ctx.assets.open("shop/shop.json").bufferedReader().readText())
        val arr = doc.optJSONArray("pictures")
        (0 until (arr?.length() ?: 0)).map { i ->
            val o = arr!!.getJSONObject(i)
            fun map(k: String): Map<String, String> = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.optString(it) } }.orEmpty()
            ShopPicture(
                spend = runCatching { Spend.valueOf(o.optString("spend")) }.getOrNull(),
                image = "shop/" + o.optString("image"),
                title = map("title"),
                artist = o.optString("artist"),
                date = o.optString("date"),
                museum = o.optString("museum"),
                objectId = o.optString("objectID"),
                license = o.optString("license"),
                url = o.optString("url"),
                detail = map("detail"),
            )
        }
    }.getOrDefault(emptyList()).also { cache = it }

    fun of(ctx: Context, sp: Spend): ShopPicture? = all(ctx).firstOrNull { it.spend == sp }
}
