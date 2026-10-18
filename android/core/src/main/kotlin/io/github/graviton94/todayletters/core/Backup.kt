package io.github.graviton94.todayletters.core

/**
 * 기록 보관 (앱 설정 › 기록 보관): 진도 · 설정 (SharedPreferences "letters") 을 JSON 파일 하나로 내보내고 다시 들인다.
 * 안드로이드 백업 (res/xml/data_extraction_rules.xml) 이 담는 것과 같다: 진도 · 메달 · 레벨 · 우표 · 카드 복습 · 설정.
 * 받은 낭독 · 그림 · 녹음 · 오류 기록은 처음부터 이 저장소에 없다.
 *
 * 넣지 않는 것 ([keep] 이 false):
 * - 이용권 캐시 "ent:*" — 산 것은 Google Play 에서만 온다. 가져올 때도 파일의 것은 버리고 이 폰의 것을 그대로 둔다.
 * - 개발용 "debug:*", 이 폰에서 마지막으로 내보낸 날 "backup:*".
 *
 * 파일 꼴 (format 1):
 * {"app":"io.github.graviton94.todayletters","kind":"records","format":1,"made":"2026-10-18","letters":23,"cards":184,
 *  "prefs":{"열쇠":["b"|"i"|"l"|"f"|"s", "값"] | ["ss", ["글", …]]}}
 * 값은 모두 글로 적는다 (Long · Float 가 JSON 수(Double)를 지나며 바뀌지 않게).
 * 꼴이 바뀌면 [FORMAT] 을 올리고 [decode] 에서 옛 꼴을 옮긴다. 더 새 꼴 (이 앱이 모르는 것) 은 읽지 않는다.
 */
object Backup {
    const val APP = "io.github.graviton94.todayletters"
    const val KIND = "records"
    const val FORMAT = 1

    /** 파일에 넣는 열쇠인가. */
    fun keep(key: String): Boolean = !key.startsWith("ent:") && !key.startsWith("debug:") && !key.startsWith("backup:")

    /** 한 줄 요약: 마친 편지 수 · 카드 수 · 만든 날 (YYYY-MM-DD). */
    data class Summary(val letters: Int, val cards: Int, val made: String)

    /** 읽은 파일: 들일 값 (이미 [keep] 으로 거른 것) 과 요약. */
    data class Records(val values: Map<String, Any>, val summary: Summary, val format: Int)

    /** 진도 "p:<열쇠>" = "보인 수|한 걸음|완료|…" 의 완료가 1 인 것 · 카드 "c:<열쇠>" 수. */
    fun summary(values: Map<String, *>, made: String): Summary = Summary(
        letters = values.count { (k, v) -> k.startsWith("p:") && (v as? String)?.split("|")?.getOrNull(2) == "1" },
        cards = values.keys.count { it.startsWith("c:") },
        made = made,
    )

    /** 저장소의 값 전부 ([values], SharedPreferences.getAll) → 파일 글. 모르는 꼴의 값은 건너뛴다. */
    fun encode(values: Map<String, *>, made: String): String {
        val kept = values.filterKeys(::keep).toSortedMap()
        val s = summary(kept, made)
        val b = StringBuilder()
        b.append("{\"app\":").str(APP).append(",\"kind\":").str(KIND).append(",\"format\":").append(FORMAT)
            .append(",\"made\":").str(made).append(",\"letters\":").append(s.letters).append(",\"cards\":").append(s.cards)
            .append(",\"prefs\":{")
        var first = true
        for ((k, v) in kept) {
            val tag = when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is String -> "s"; is Set<*> -> "ss"; else -> null } ?: continue
            if (!first) b.append(',')
            first = false
            b.append('\n').str(k).append(":[").str(tag).append(',')
            if (v is Set<*>) {
                b.append('[')
                v.filterIsInstance<String>().sorted().forEachIndexed { i, e -> if (i > 0) b.append(','); b.str(e) }
                b.append(']')
            } else b.str(v.toString())
            b.append(']')
        }
        b.append("\n}}\n")
        return b.toString()
    }

    /** 파일 글 → 기록. 이 앱의 기록 파일이 아니거나 망가졌거나 더 새 꼴이면 null (“이 파일은 읽을 수 없어요”). */
    fun decode(text: String): Records? = runCatching {
        val root = Json.parse(text.trimStart('﻿')) as? Map<*, *> ?: return null
        if (root["app"] != APP || root["kind"] != KIND) return null
        val format = (root["format"] as? Double)?.takeIf { it % 1.0 == 0.0 }?.toInt() ?: return null
        if (format < 1 || format > FORMAT) return null
        val made = (root["made"] as? String)?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) } ?: return null
        val prefs = root["prefs"] as? Map<*, *> ?: return null
        val out = LinkedHashMap<String, Any>()
        for ((k, raw) in prefs) {
            val key = k as? String ?: return null
            val pair = raw as? List<*> ?: return null
            if (pair.size != 2) return null
            val v = pair[1]
            val value: Any = when (pair[0]) {
                "b" -> when (v) { "true" -> true; "false" -> false; else -> return null }
                "i" -> (v as? String)?.toIntOrNull() ?: return null
                "l" -> (v as? String)?.toLongOrNull() ?: return null
                "f" -> (v as? String)?.toFloatOrNull() ?: return null
                "s" -> v as? String ?: return null
                "ss" -> (v as? List<*>)?.map { it as? String ?: return null }?.toSet() ?: return null
                else -> return null
            }
            if (keep(key)) out[key] = value
        }
        Records(out, summary(out, made), format)
    }.getOrNull()

    /** 기본 파일 이름: 언어미술관-기록-2026-10-18.json (앱 이름은 [prefix]). */
    fun fileName(prefix: String, made: String) = "$prefix-$made.json"

    private fun StringBuilder.str(s: String): StringBuilder {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
        return append('"')
    }
}
