package io.github.graviton94.todayletters.core

/**
 * 아주 작은 JSON 읽개 (core 는 안드로이드 · 외부 라이브러리 없이 돌아서). 데이터 파일(medals.json)을 core 에서 바로 읽고 테스트하려고.
 * 객체 → Map<String, Any?>, 배열 → List<Any?>, 수 → Double, 글 → String, true/false → Boolean, null → null.
 */
object Json {
    fun parse(text: String): Any? = Reader(text).run { val v = value(); ws(); require(i == s.length) { "JSON: 끝에 남은 글자 ($i)" }; v }

    private class Reader(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            require(i < s.length) { "JSON: 뜻밖의 끝" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("JSON: 뜻밖의 글자 '$c' ($i)")
            }
        }
        fun lit(word: String, v: Any?): Any? { require(s.startsWith(word, i)) { "JSON: $word ($i)" }; i += word.length; return v }
        fun num(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(st, i).toDouble()
        }
        fun str(): String {
            i++ // "
            val b = StringBuilder()
            while (true) {
                require(i < s.length) { "JSON: 닫히지 않은 글" }
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r'); 'b' -> b.append('\b'); 'f' -> b.append('\u000C')
                            'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> b.append(e)
                        }
                    }
                    else -> b.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++
            val out = mutableListOf<Any?>()
            ws(); if (s[i] == ']') { i++; return out }
            while (true) {
                out += value(); ws()
                when (s[i++]) { ',' -> continue; ']' -> return out; else -> error("JSON: 배열 ($i)") }
            }
        }
        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws(); if (s[i] == '}') { i++; return out }
            while (true) {
                ws(); val k = str(); ws()
                require(s[i++] == ':') { "JSON: ':' ($i)" }
                out[k] = value(); ws()
                when (s[i++]) { ',' -> continue; '}' -> return out; else -> error("JSON: 객체 ($i)") }
            }
        }
    }
}
