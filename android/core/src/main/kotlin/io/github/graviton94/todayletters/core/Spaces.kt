package io.github.graviton94.todayletters.core

/**
 * 배우는 말마다 따로 쌓는 진도 (v22): 같은 작품이라도 프랑스어로 읽은 편지와 영어로 읽은 편지는 다른 진도다.
 * 진도 열쇠의 작품 이름 뒤에 배우는 말을 붙인다: 원어는 그대로 ("vincent"), 영어 · 한국어는 "vincent@en" · "vincent@ko".
 * 그래서 편지 진도 "p:vincent@en:I:c1-l2", 오늘의 할 일 "quest:vincent@en:" 처럼 작품 열쇠를 쓰던 곳이 그대로 언어마다 갈린다.
 * 원어 진도는 예전 열쇠 그대로라 옮길 것이 없고, 예전에 영어 · 한국어로 쌓은 진도는 처음 한 번 그 언어 열쇠로 옮긴다.
 */
object Spaces {
    /** 진도 꼬리: 원어면 "", 아니면 "@en" · "@ko". */
    fun suffix(learn: Lang, original: Lang): String = if (learn == original) "" else "@${learn.code}"

    /** 진도 열쇠의 작품 이름 ("vincent" · "vincent@en"). */
    fun of(series: String, learn: Lang, original: Lang): String = series + suffix(learn, original)

    /** 진도 열쇠 이름 → (작품, 언어 코드 · 원어면 null). */
    fun parse(space: String): Pair<String, String?> =
        if ('@' in space) space.substringBefore('@') to space.substringAfter('@') else space to null

    /** 예전(언어 구분 없던) 열쇠 [key] 를 [space] 로: "vincent:I:c1" → "vincent@en:I:c1". [series] 로 시작하지 않으면 null. */
    fun move(key: String, series: String, space: String): String? =
        if (key == series || key.startsWith("$series:")) space + key.removePrefix(series) else null
}

/**
 * 낱말 · 표현 소리 파일 이름 (data/lexicon/README.md, scripts/narrate.py 의 slug):
 * 편지 낭독 폴더의 v_<언어>_<slug>.m4a (오늘의 낱말) · ph<번호>_<언어>.m4a (오늘의 표현, 1부터).
 */
object VocabAudio {
    /** 사전 키 → 파일 이름 조각: a-z · 0-9 · '-' 는 그대로, 그 밖은 _<코드포인트 16진 소문자>_. */
    fun slug(key: String): String = key.codePoints().toArray().joinToString("") { c ->
        if (c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c == '-'.code) c.toChar().toString() else "_" + Integer.toHexString(c) + "_"
    }

    /** 오늘의 낱말 소리 (확장자 없이): v_de_<slug>. */
    fun word(lex: String, lang: Lang) = "v_${lang.code}_${slug(lex)}"

    /** 오늘의 표현 소리 (확장자 없이): ph1_en. [index] 는 0부터. */
    fun phrase(index: Int, lang: Lang) = "ph${index + 1}_${lang.code}"
}
