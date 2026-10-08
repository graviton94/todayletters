package io.github.graviton94.todayletters.core

/**
 * 줄바꿈: 모든 언어에서 낱말(한국어는 어절) 가운데서 줄이 바뀌지 않게 한다.
 * 한국어는 기본으로 글자마다 줄을 바꿀 수 있어서 "도착해요"가 "도 / 착해요"로 갈라지곤 한다.
 * 안드로이드 13 이상은 화면 쪽에서 어절 단위 줄바꿈(LineBreak.WordBreak.Phrase)을 켜고,
 * 그 아래 버전까지 같게 보이도록 한글이 낀 낱말 안의 글자 사이에 보이지 않는 이음표(U+2060)를 넣는다.
 * 띄어쓰기에서만 줄이 바뀐다. 아주 긴 낱말이 한 줄을 넘으면 그때만 화면이 알아서 자른다.
 */
object Breaks {
    const val JOIN = '\u2060'

    private fun hangul(c: Char) = c in '\uAC00'..'\uD7A3' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F'

    fun keepAll(text: String): String {
        if (text.none(::hangul)) return text
        val out = StringBuilder(text.length + text.length / 2)
        for (i in text.indices) {
            val c = text[i]
            out.append(c)
            val n = text.getOrNull(i + 1) ?: continue
            if (c == JOIN || n == JOIN || c.isWhitespace() || n.isWhitespace()) continue
            if (hangul(c) || hangul(n)) out.append(JOIN)
        }
        return out.toString()
    }

    /** 이음표를 뺀 글자 (비교 · 검색용). */
    fun plain(text: String) = text.replace(JOIN.toString(), "")
}
