package io.github.graviton94.todayletters.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실제 자료로 core [NoteBook] 과 scripts/notes.py 가 같은 규칙인지: 작품 · 말마다 잡힌 수 (문형 · 관용구 · 기능어) 가
 * scripts/notes/expect.json (notes.py 가 센 것) 과 같아야 한다. 규칙 · 자료를 고치면 `python3 scripts/notes.py` 를 다시 돌린다.
 */
class NotesDataTest {
    private val root: File = run {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "data/medals.json").exists()) d = d.parentFile
        d ?: error("저장소 뿌리를 찾지 못함")
    }

    @Suppress("UNCHECKED_CAST")
    private fun obj(f: File) = Json.parse(f.readText()) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun lexicon(f: File): Map<String, LexEntry> {
        val o = obj(f)
        val fields = (o["fields"] as List<*>).map { it.toString() }
        return (o["entries"] as List<List<Any?>>).map { row ->
            fun at(n: String) = fields.indexOf(n).let { if (it in row.indices) row[it] else null }
            LexEntry(at("lemma") as String, at("id") as? String, pos = at("pos") as? String ?: "", ko = at("ko") as? String ?: "",
                en = at("en") as? String ?: "", function = (at("function") as? Double) == 1.0)
        }.associateBy { it.key }
    }

    @Suppress("UNCHECKED_CAST")
    private fun gloss(f: File): List<List<Token>> = (obj(f)["letters"] as Map<String, List<List<Any?>>>).values.flatMap { msgs ->
        msgs.map { toks -> toks.map { t -> if (t is List<*>) Token(t[0] as String, t[1] as String, (t.getOrNull(2) as? String).orEmpty()) else Token(t as String, t) } }
    }

    @Test fun kotlinMatchesTheScript() {
        @Suppress("UNCHECKED_CAST") val expect = obj(File(root, "scripts/notes/expect.json")).filterKeys { !it.startsWith("_") } as Map<String, Map<String, Double>>
        assertTrue(expect.size >= 12)
        val orig = mapOf("vincent" to "fr", "mozart" to "de", "napoleon" to "fr", "goya" to "es")
        expect.forEach { (name, want) ->
            val series = name.substringBefore('_')
            val deck = name.substringAfter('_', "")
            val tail = if (deck.isEmpty()) "" else "_$deck"
            val dir = File(root, "data/$series")
            val lex = lexicon(File(dir, "lexicon$tail.json"))
            val idioms = File(dir, "idioms$tail.json").takeIf { it.exists() }?.readText()
            val book = NoteBook.parse(File(root, "data/lexicon/notes_${deck.ifEmpty { orig.getValue(series) }}.json").readText(), idioms) { lex[it] }
            val got = mutableMapOf<String, Int>()
            gloss(File(dir, "gloss$tail.json")).forEach { ts -> book.match(ts).forEach { h -> got.merge(h.kind.name, 1, Int::plus) } }
            assertEquals(want.mapValues { it.value.toInt() }, Hit.Kind.entries.associate { it.name to (got[it.name] ?: 0) }, name)
        }
    }

    /** 앱에 실린 노트 (assets/notes) 가 data/lexicon 의 것과 같은가는 generate.py --check 가 본다. 여기서는 다섯 말이 다 있는지만. */
    @Test fun everyLearningLanguageHasNotes() {
        listOf("fr", "de", "es", "en", "ko").forEach { l ->
            val b = NoteBook.parse(File(root, "data/lexicon/notes_$l.json").readText())
            assertEquals(l, b.lang)
            assertTrue(b.words.size in 60..130, "$l: 기능어 ${b.words.size}")
            assertTrue(b.patterns.size in 15..40, "$l: 문형 ${b.patterns.size}")
        }
    }
}
