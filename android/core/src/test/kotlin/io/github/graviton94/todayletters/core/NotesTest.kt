package io.github.graviton94.todayletters.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 노트 (core/Notes.kt): 실제 data/lexicon/notes_<말>.json 규칙으로 작은 문장을 맞춰 본다. */
class NotesTest {
    private fun root(): File {
        var d: File? = File("").absoluteFile
        while (d != null) { if (File(d, "data/medals.json").exists()) return d; d = d.parentFile }
        error("저장소 뿌리를 찾지 못함")
    }

    private fun book(lang: String, lex: List<LexEntry>, idioms: String? = null): NoteBook {
        val byKey = lex.associateBy { it.key }
        return NoteBook.parse(File(root(), "data/lexicon/notes_$lang.json").readText(), idioms) { byKey[it] }
    }

    private fun t(surface: String, key: String = surface, fn: String = "") = Token(surface, key, fn)

    // 빈센트 c1-l2: “je ne te dis que cela” — ne … que 는 부정이 아니라 ‘~만’ (닻 “je ne” = 나는 ~하지 않는다 를 덮어쓴다)
    private val frLex = listOf(
        LexEntry("je", pos = "pron", ko = "나는", en = "I", function = true), LexEntry("ne", pos = "adv", ko = "~않다 (부정)", en = "not", function = true),
        LexEntry("te", pos = "pron", ko = "너를, 너에게", en = "you", function = true), LexEntry("dire", pos = "v", ko = "말하다", en = "to say"),
        LexEntry("que", pos = "conj", ko = "~라는 것", en = "that", function = true), LexEntry("cela", pos = "pron", ko = "그것", en = "that", function = true),
        LexEntry("avoir", pos = "v", ko = "가지다", en = "to have", function = true), LexEntry("rapporter", pos = "v", ko = "가져오다", en = "to bring back"),
        LexEntry("une", pos = "art"), LexEntry("toile", pos = "n", ko = "캔버스", en = "canvas"),
    )

    @Test fun neQueMeansOnly() {
        val b = book("fr", frLex)
        val ts = listOf(t("je"), t("ne"), t("te"), t("dis", "dire"), t("que"), t("cela"))
        val h = b.match(ts).single { it.kind == Hit.Kind.PATTERN }
        assertEquals("fr.ne_que", h.id)
        assertEquals(listOf(1, 4), h.parts)
        assertEquals("~만", h.short["ko"])
        assertTrue(h.always)
        assertTrue(h.note!!.note(Lang.KO).isNotEmpty())
        // 낱말 아래 뜻: que 아래 ‘~만’, ne 는 뜻 없이 같은 묶음, 닻 “je ne” 는 잡히지 않는다 (기능어 뜻을 끈 등급에도)
        val anchors = listOf(Anchor("je ne", listOf("je", "ne"), "나는 ~하지 않는다", "I don't", id = "x"))
        val u = Interlinear.units(ts, { k -> frLex.firstOrNull { it.key == k } }, Lang.KO, { false }, false, anchors = anchors, notes = b)
        assertEquals("~만", u.single { it.from == 4 }.gloss)
        assertEquals(Interlinear.Kind.PATTERN, u.single { it.from == 1 }.kind)
        assertNull(u.single { it.from == 1 }.gloss)
        assertTrue(u.none { it.kind == Interlinear.Kind.ANCHOR })
        assertEquals("말하다", u.single { it.from == 3 }.gloss)
    }

    @Test fun passeCompose() {
        val b = book("fr", frLex)
        val ts = listOf(t("j'", "je"), t("ai", "avoir"), t("rapporté", "rapporter"), t("une"), t("toile"))
        val h = b.match(ts).single { it.kind == Hit.Kind.PATTERN }
        assertEquals("fr.pc", h.id)
        assertEquals(listOf(1, 2), h.parts)
        assertEquals("~했다", h.short["ko"])
        val u = Interlinear.units(ts, { k -> frLex.firstOrNull { it.key == k } }, Lang.KO, { false }, true, notes = b)
        assertEquals("~했다", u.single { it.from == 1 }.gloss)
        // 과거분사는 그 낱말 뜻 그대로, 같은 문형 칸
        val pp = u.single { it.from == 2 }
        assertEquals("가져오다", pp.gloss)
        assertEquals("fr.pc", pp.hit?.id)
        // 기능어 노트: je 는 노트의 짧은 뜻
        assertEquals("나는", u.single { it.from == 0 }.gloss)
        assertNotNull(b.forToken("je"))
    }

    // 모차르트 c4: “fängt das Gebet an” — 분리동사 anfangen (뜻은 사전 줄)
    @Test fun germanSeparableVerb() {
        val lex = listOf(LexEntry("fangen", pos = "v", ko = "잡다", en = "to catch"), LexEntry("anfangen", pos = "v", ko = "시작하다", en = "to begin"),
            LexEntry("der", pos = "art", function = true), LexEntry("Gebet", pos = "n", ko = "기도", en = "prayer"), LexEntry("an", pos = "prep", ko = "~에", en = "at", function = true))
        val b = book("de", lex)
        val ts = listOf(t("fängt", "fangen"), t("das", "der"), t("Gebet"), t("an"))
        val h = b.match(ts).single { it.kind == Hit.Kind.PATTERN }
        assertEquals("de.sep", h.id)
        assertEquals(listOf(0, 3), h.parts)
        assertEquals("시작하다", h.short["ko"])
        val u = Interlinear.units(ts, { k -> lex.firstOrNull { it.key == k } }, Lang.KO, { false }, false, notes = b)
        assertEquals("시작하다", u.single { it.from == 0 }.gloss)
        assertNull(u.single { it.from == 3 }.gloss)
        // 뒤에 명사가 오면 전치사 (분리동사 아님)
        assertTrue(b.match(listOf(t("fängt", "fangen"), t("an"), t("Gebet"))).none { it.id == "de.sep" })
    }

    @Test fun spanishSerEstar() {
        val lex = listOf(LexEntry("estar", pos = "v", ko = "있다", en = "to be", function = true), LexEntry("bueno", pos = "adj", ko = "좋은", en = "good"),
            LexEntry("pintar", pos = "v", ko = "그리다", en = "to paint"), LexEntry("ser", pos = "v", ko = "~이다", en = "to be", function = true))
        val b = book("es", lex)
        assertEquals("es.estar_adj", b.match(listOf(t("estoy", "estar"), t("bueno"))).single().id)
        assertEquals("es.ser_adj", b.match(listOf(t("es", "ser"), t("bueno"))).single().id)
        val ger = b.match(listOf(t("estoy", "estar"), t("pintando", "pintar"))).single()
        assertEquals("es.estar_ger", ger.id)
        assertEquals("~하고 있다", ger.short["ko"])
    }

    @Test fun englishPresentPerfect() {
        val lex = listOf(LexEntry("I", pos = "pron", function = true), LexEntry("have", pos = "v", ko = "가지다", en = "to have", function = true),
            LexEntry("see", pos = "v", ko = "보다", en = "to see"), LexEntry("go", pos = "v", ko = "가다", en = "to go"), LexEntry("to", pos = "prep", function = true))
        val b = book("en", lex)
        val h = b.match(listOf(t("I"), t("have"), t("seen", "see"))).single { it.kind == Hit.Kind.PATTERN }
        assertEquals("en.pres_perf", h.id)
        assertEquals(listOf(1, 2), h.parts)
        // have to + 동사 원형은 현재완료가 아니라 ‘~해야 한다’
        assertEquals("en.have_to", b.match(listOf(t("I"), t("have"), t("to"), t("go"))).first { it.kind == Hit.Kind.PATTERN }.id)
    }

    @Test fun koreanParticleAndEnding() {
        val lex = listOf(LexEntry("편지", pos = "n", ko = "편지", en = "letter"), LexEntry("생각하다", pos = "v", ko = "생각하다", en = "to think"),
            LexEntry("그리다", pos = "v", ko = "그리다", en = "to paint"), LexEntry("있다", pos = "v", ko = "있다", en = "to be, to have", function = true))
        val b = book("ko", lex)
        val obj = b.match(listOf(t("편지를", "편지", "를/JKO"))).single()
        assertEquals(Hit.Kind.WORD, obj.kind)
        assertEquals("를/JKO", obj.note?.id)
        assertNull(obj.suffix)
        assertNotNull(b.forEnding("를/JKO"))
        // 과거 -었- 은 낱말 뜻 뒤에 (영어 뜻)
        val past = Interlinear.units(listOf(t("생각했어", "생각하다", "었/EP+어/EF")), { k -> lex.firstOrNull { it.key == k } }, Lang.EN, { false }, true, notes = b)
        assertEquals("to think (past)", past.single().gloss)
        // -고 있다: 앞 어절은 그 뜻, 있다 는 ‘-ing (now)’
        val ts = listOf(t("그리고", "그리다", "고/EC"), t("있어", "있다", "어/EF"))
        val h = b.match(ts).single { it.kind == Hit.Kind.PATTERN }
        assertEquals("ko.go_itda", h.id)
        val u = Interlinear.units(ts, { k -> lex.firstOrNull { it.key == k } }, Lang.EN, { false }, true, notes = b)
        assertEquals("to paint", u.single { it.from == 0 }.gloss)
        assertEquals("-ing (now)", u.single { it.from == 1 }.gloss)
    }

    @Test fun idiomsWinOverAnchorsAndKeepTheirMeaning() {
        val lex = listOf(LexEntry("peu", pos = "adv", ko = "조금", en = "little", function = true), LexEntry("à", pos = "prep", function = true))
        val idioms = """{"idioms":[{"lem":"peu à peu","t":"peu à peu","ko":"조금씩","en":"little by little","note_ko":"점점","n":2,"first":["c1-l1",0]}]}"""
        val b = book("fr", lex, idioms)
        val ts = listOf(t("peu"), t("à"), t("peu"))
        val h = b.match(ts).single()
        assertEquals(Hit.Kind.IDIOM, h.kind)
        assertEquals(0 to 2, h.start to h.end)
        assertEquals("관용구", h.note!!.tag(Lang.KO))
        val u = Interlinear.units(ts, { k -> lex.firstOrNull { it.key == k } }, Lang.KO, { false }, false,
            anchors = listOf(Anchor("peu à", listOf("peu", "à"), "x", "x", id = "y")), notes = b)
        assertEquals(listOf(Interlinear.Kind.IDIOM), u.map { it.kind })
        assertEquals("조금씩", u.single().gloss)
    }

    @Test fun emptyBookChangesNothing() {
        val ts = listOf(t("je"), t("ne"), t("te"))
        val lex: (String) -> LexEntry? = { k -> frLex.firstOrNull { it.key == k } }
        assertEquals(Interlinear.units(ts, lex, Lang.KO, { false }, true), Interlinear.units(ts, lex, Lang.KO, { false }, true, notes = NoteBook.EMPTY))
        assertTrue(NoteBook.parse(null).match(ts).isEmpty())
    }
}
