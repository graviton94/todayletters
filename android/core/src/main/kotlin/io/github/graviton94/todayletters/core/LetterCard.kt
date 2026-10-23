package io.github.graviton94.todayletters.core

/**
 * 편지 카드 (공유용 그림 한 장, 시안 Gbowc2Rqd6VJJpfmMPP6jC 2): 위는 그 편지의 그림 (4:3 으로 자름),
 * 그 아래 문장 전체 (말줄임 없음, 카드 높이는 글만큼) · 앱 글자 언어의 번역 · 꼬리 줄 “보낸 사람 → 받는 사람 · 날짜” / 앱 이름,
 * 맨 아래 작은 줄에 그림의 작가 · 제목 · 연도 (docs/image-policy.md 7).
 * 그리기는 앱 (ui/LetterCard.kt), 글 · 자르기 · 숫자는 여기서.
 */
object LetterCard {
    /** 카드 너비 (px). 높이는 내용만큼. */
    const val WIDTH = 1080
    /** 그림 칸의 가로 : 세로. */
    const val RATIO = 4f / 3f

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** 보이지 않는 줄바꿈 표시 (U+2060) 를 빼고 앞뒤 빈칸을 다듬는다 (공유 글 · 견주기용). */
    fun clean(t: String): String = t.replace("⁠", "").trim()

    /**
     * 카드의 날짜: "1888-02-21" → "1888. 2. 21." · "21 Feb 1888", 달까지만 ("1888-02") → "1888. 2." · "Feb 1888".
     * 그 밖의 꼴 ("1811" 등) 은 그대로.
     */
    fun date(date: String, ui: Lang): String {
        val ko = ui == Lang.KO
        val full = Regex("""(\d{4})-(\d{2})-(\d{2}).*""").matchEntire(date.trim())
        if (full != null) {
            val (y, m, d) = full.destructured
            val mi = m.toInt(); val di = d.toInt()
            if (mi in 1..12) return if (ko) "$y. $mi. $di." else "$di ${months[mi - 1]} $y"
        }
        val month = Regex("""(\d{4})-(\d{2})""").matchEntire(date.trim())
        if (month != null) {
            val (y, m) = month.destructured
            val mi = m.toInt()
            if (mi in 1..12) return if (ko) "$y. $mi." else "${months[mi - 1]} $y"
        }
        return date.trim()
    }

    /** 꼬리 줄 왼쪽: “빈센트 → 테오 · 1888. 2. 21.” (받는 사람 · 날짜가 비면 그 칸을 뺀다). */
    fun footer(sender: String, recipient: String, date: String, ui: Lang): String {
        val who = listOf(clean(sender), clean(recipient)).filter { it.isNotEmpty() }.joinToString(" → ")
        return listOf(who, date(date, ui)).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    /** 그림의 연도: 날짜 칸의 덧말 (“1887 (Paris)”) 을 뺀다. */
    fun year(date: String): String = date.substringBefore(" (").trim()

    /** 맨 아래 작은 줄: 작가 · 제목 · 연도 (빈 칸은 뺀다). */
    fun credit(artist: String, title: String, date: String): String =
        listOf(clean(artist), clean(title), year(date)).filter { it.isNotEmpty() }.joinToString(" · ")

    /** 번역 줄: 배우는 말이 앱 글자 언어와 같거나, 번역이 비었거나 원문과 같으면 없음. */
    fun translation(original: String, translated: String, learn: Lang, ui: Lang): String? {
        if (learn == ui) return null
        val t = translated.trim()
        return t.takeIf { it.isNotEmpty() && clean(it) != clean(original) }
    }

    /** 원문 글자 크기 (px, 너비 [WIDTH] 기준): 긴 문장일수록 조금 작게. 말줄임 없이 다 싣는다. */
    fun originalPx(chars: Int): Float = when {
        chars <= 90 -> 56f
        chars <= 180 -> 50f
        chars <= 300 -> 44f
        else -> 40f
    }

    /** 플레이 스토어 주소. */
    fun storeUrl(appId: String): String = "https://play.google.com/store/apps/details?id=$appId"

    /** 그림에서 잘라 낼 자리 (왼쪽, 위, 오른쪽, 아래). 넓은 그림은 가운데, 긴 그림은 위쪽 [bias] 만큼 (얼굴이 잘리지 않게). */
    data class Crop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    fun crop(w: Int, h: Int, ratio: Float = RATIO, bias: Float = 0.35f): Crop {
        if (w <= 0 || h <= 0) return Crop(0, 0, w.coerceAtLeast(0), h.coerceAtLeast(0))
        return if (w.toFloat() / h > ratio) {
            val cw = (h * ratio).toInt().coerceIn(1, w)
            val x = (w - cw) / 2
            Crop(x, 0, x + cw, h)
        } else {
            val ch = (w / ratio).toInt().coerceIn(1, h)
            val y = ((h - ch) * bias.coerceIn(0f, 1f)).toInt()
            Crop(0, y, w, y + ch)
        }
    }
}
