package io.github.graviton94.todayletters

import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Moment
import io.github.graviton94.todayletters.data.Library
import io.github.graviton94.todayletters.data.forLearn
import io.github.graviton94.todayletters.data.learnVocab
import io.github.graviton94.todayletters.data.lexicons
import io.github.graviton94.todayletters.data.withVocab
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 편지 데이터 읽기: 모차르트의 ‘편지 속 그 곡’ · 오늘의 철자 · 한 줄 날씨. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryTest {
    @Test fun musicMoment() {
        val m = Library.moment(JSONObject("""{"after": 2, "type": "music", "piece": {"de": "Le nozze di Figaro — Ouvertüre", "en": "Le nozze di Figaro — Overture", "ko": "피가로의 결혼 — 서곡"},
            "commons": "File:x.flac", "audio": "m-c7-l3.m4a", "performer": "Musopen Symphony", "audioLicense": "Public domain", "audioSource": "https://commons.wikimedia.org/wiki/File:x.flac"}"""))
        assertTrue(m is Moment.Music)
        m as Moment.Music
        assertEquals(2, m.after)
        assertEquals("m-c7-l3.m4a", m.audio)
        assertEquals("Musopen Symphony", m.performer)
        assertEquals("Public domain", m.license)
        assertEquals("Le nozze di Figaro — Overture", m.piece[Lang.EN])
        assertTrue(m.playable)
    }

    @Test fun musicWithoutRecordingIsSilent() {
        val m = Library.moment(JSONObject("""{"after": 1, "type": "music", "piece": {"de": "Arie", "en": "Aria", "ko": "아리아"}, "commons": null}""")) as Moment.Music
        assertEquals("", m.audio)   // JSON null 이 "null" 글자가 되지 않게
        assertFalse(m.playable)
    }

    @Test fun oneLineWeatherAndUnknownMoment() {
        val w = Library.moment(JSONObject("""{"after": 1, "type": "weather", "quote": "jetzt scheint die Sonne", "text": {"de": "Sonne", "en": "Sun", "ko": "해"}}""")) as Moment.Weather
        assertEquals("Sun", w.title[Lang.EN])
        assertEquals("", w.value[Lang.EN])
        assertNull(Library.moment(JSONObject("""{"after": 0, "type": "hologram"}""")))
        assertNull(Library.moment(JSONObject("""{"after": 0, "type": "notice"}""")))   // 칸이 모자라면 건너뜀
    }

    @Test fun modernSpellingIsParsed() {
        val w = Library.word(JSONObject("""{"de": "daß", "de_modern": "dass", "pos": "conj.", "ipa": "das", "en": "that", "ipa_en": "ðæt", "ko": "~라는 것", "rom_ko": "ra-neun geot"}"""))
        assertEquals("daß", w.text[Lang.DE])
        assertEquals("dass", w.modern)
        assertEquals("dass", w.answer(Lang.DE, Lang.DE))
        assertEquals("ðæt", w.ipaEn)
        assertEquals("ra-neun geot", w.romKo)
        assertEquals("", w.icon)
    }

    @Test fun lexiconRowsFollowFields() {
        val lex = Library.lexicon("""{"_doc": "x", "lang": "fr", "fields": ["lemma","id","forms","pos","gender","ko","en","ipa","level","first","letters","example","function"],
            "entries": [["pendant", null, ["Pendant","pendant"], "prep", null, "~ 동안", "during", "pɑ̃dɑ̃", "A1", "p1", "p1 c3-l1", null, 1],
                        ["maladie", null, ["maladie"], "n", "f", "병", "illness", "maladi", "B1", "p1", "p1 c5-l3", [350877, "Il a soigné ma maladie.", "He cured my illness.", null, "Barbiche0"]],
                        ["ton", "ton#2", ["ton"], "n", "m", "색조", "tone"]]}""")
        assertEquals(Lang.FR, lex.lang)
        assertEquals(3, lex.size)
        val p = lex["pendant"]!!
        assertTrue(p.function)
        assertEquals(listOf("p1", "c3-l1"), p.letters)
        assertEquals("", p.gender)
        val m = lex["maladie"]!!
        assertFalse(m.function)
        assertEquals(350877L, m.example!!.id)
        assertEquals("Barbiche0", m.example!!.author)
        assertNull(m.example!!.ko)
        assertEquals("tone", lex["ton#2"]!!.en)   // 동형어는 id 로
        assertEquals("", lex["ton#2"]!!.level)    // 뒤쪽 null 생략
    }

    @Test fun glossTokensAreStringsOrPairs() {
        val g = Library.gloss("""{"_doc": "x", "lang": "de", "letters": {"c1-l1": [[["Mich", "ich"], "es", ["daß", "dass"]], []]}}""")
        val msgs = g.getValue("c1-l1")
        assertEquals(2, msgs.size)
        assertEquals(listOf("ich", "es", "dass"), msgs[0].map { it.key })
        assertEquals(listOf("Mich", "es", "daß"), msgs[0].map { it.surface })
        assertTrue(msgs[1].isEmpty())
    }

    @Test fun dailyWordsSpotsAndPhrases() {
        val d = Library.daily("""{"_doc": "x", "lang": "fr", "letters": {
            "c1-l3": {"words": ["tableau", "jour"], "spots": [{"i": 2, "w": "tableau", "was": "Souvenir de Mauve"}, {"i": 0, "w": [], "was": "Vincent"}],
                      "phrases": [{"t": "tout de même", "m": 1, "ko": "그래도", "en": "all the same", "note_ko": "양보", "note_en": "Conceding"}]},
            "p1": {"words": [], "spots": [], "phrases": []}}}""")
        val l = d.getValue("c1-l3")
        assertEquals(listOf("tableau", "jour"), l.words)
        assertEquals(2, l.spots[0].index)
        assertEquals("tableau", l.spots[0].word)
        assertNull(l.spots[1].word)   // [] → 그 점에는 낱말 없음
        assertEquals("Conceding", l.phrases.single().noteEn)
        assertEquals(1, l.phrases.single().msg)
        assertTrue(d.getValue("p1").words.isEmpty())
    }

    /** 앱에 넣은 실제 자료: 두 작품 모두 사전 · 토큰 · 오늘의 낱말을 읽고, 편지 낱말이 daily.json 으로 바뀐다. */
    @Test fun bundledVocabularyLoads() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val works = Library.works(ctx)
        for (id in listOf("vincent", "mozart")) {
            val w = works.first { it.series.id == id }
            assertTrue(w.lexicon!!.size > 1000)
            val l = w.chapters.first().letters.first()
            assertTrue(l.words.isNotEmpty() && l.words.all { it.lex.isNotEmpty() })
            assertTrue(l.source.isNotEmpty())
            assertEquals(l.words.size, l.words.distinctBy { it.lex }.size)
            val g = Library.gloss(ctx, id)
            assertEquals(l.messages.size, g.getValue(l.id).size)
        }
    }

    /** 고야 ("origLine": true): 메시지마다 판본에 찍힌 글 (orig.es) 을 읽고, 지금 철자와 문장부호 · 대소문자만 다르면 비운다. 다른 작품은 읽지 않는다. */
    @Test fun goyaOriginalSpelling() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val works = Library.works(ctx)
        val g = works.first { it.series.id == "goya" }
        val m = g.chapters.first().letters.first().messages.first()
        assertTrue(m.orig.contains("dibiertas"))
        assertTrue(m.text[Lang.ES].contains("diviertas"))
        assertEquals("", g.prologue[3].letter.messages.first().orig)   // p4: 찍힌 글과 지금 철자가 마침표만 다르다
        assertTrue(works.first { it.series.id == "napoleon" }.chapters.first().letters.first().messages.all { it.orig.isEmpty() })
    }

    private val lexFields = """"fields": ["lemma","id","forms","pos","gender","ko","en","ipa","level","first","letters","example","function"]"""

    /** 배우는 말마다 자료 한 벌: 원어 lexicon.json, 영어 lexicon_en.json, 한국어 lexicon_ko.json. 사전이 없는 언어는 빠진다 (= 원어 자료 그대로). */
    @Test fun vocabSetsFollowTheLearnLanguage() {
        val files = mapOf(
            "lexicon.json" to """{"lang": "fr", $lexFields, "entries": [["jardin", null, ["jardin"], "n", "m", "정원", "garden"]]}""",
            "daily.json" to """{"lang": "fr", "letters": {"c1-l1": {"words": ["jardin"], "spots": [], "phrases": []}}}""",
            "lexicon_en.json" to """{"lang": "en", $lexFields, "entries": [["garden", null, ["garden", "gardens"], "n", null, "정원", "", "ˈɡɑːdən", "A2"]]}""",
            "daily_en.json" to """{"lang": "en", "letters": {"c1-l1": {"words": ["garden"], "spots": [], "phrases": []}}}""",
            // 한국어: 오늘의 낱말만 있고 사전이 없으면 그 언어 자료는 없는 것
            "daily_ko.json" to """{"lang": "ko", "letters": {}}""",
        )
        val sets = Library.vocabSets(Lang.FR) { files[it] }
        assertEquals(setOf(Lang.FR, Lang.EN), sets.keys)
        assertEquals("", sets.getValue(Lang.FR).deck)
        assertEquals("en", sets.getValue(Lang.EN).deck)
        assertEquals("garden", sets.getValue(Lang.EN).lexicon!!["garden"]!!.lemma)
        assertEquals(listOf("garden"), sets.getValue(Lang.EN).daily.getValue("c1-l1").words)
        assertEquals(listOf("jardin"), sets.getValue(Lang.FR).daily.getValue("c1-l1").words)
        // 영어 오늘의 낱말이 없어도 사전만 있으면 그 자료 (낱말은 챕터 낱말 그대로)
        val noDaily = Library.vocabSets(Lang.FR) { if (it == "daily_en.json") null else files[it] }
        assertTrue(noDaily.getValue(Lang.EN).daily.isEmpty())
        // 아무것도 없으면 빈 것
        assertTrue(Library.vocabSets(Lang.DE) { null }.isEmpty())
        assertEquals("gloss_ko.json", io.github.graviton94.todayletters.core.VocabSet.file("gloss", "ko"))
        assertEquals("gloss.json", io.github.graviton94.todayletters.core.VocabSet.file("gloss", ""))
    }

    /** 작품을 배우는 말에 맞추기: 그 언어 자료가 있으면 그 낱말 · 사전, 없으면 원어 자료 그대로 (지금처럼). */
    @Test fun workFollowsTheLearnLanguageWithFallback() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val base = Library.works(ctx).first { it.series.id == "vincent" }
        // 지금 앱에 영어 · 한국어 자료가 없으면: 원어 자료 그대로
        for (lang in listOf(Lang.EN, Lang.KO)) if (lang !in base.decks) assertTrue(base.forLearn(lang) === base)
        assertTrue(base.forLearn(Lang.FR) === base)
        assertEquals(Lang.FR, base.learnVocab)
        assertEquals("", base.deck)
        // 영어 자료를 입히면: 편지 낱말 · 사전 · 자료 꼬리가 영어
        val l0 = base.chapters.first().letters.first()
        val en = l0.source.first().text[Lang.EN]
        val set = io.github.graviton94.todayletters.core.VocabSet(Lang.EN, "en",
            io.github.graviton94.todayletters.core.Lexicon(Lang.EN, listOf(io.github.graviton94.todayletters.core.LexEntry(en, forms = listOf(en), ko = "뜻", ipa = "x", level = "A1"))),
            mapOf(l0.id to io.github.graviton94.todayletters.core.Daily(words = listOf(en))))
        val enWork = base.copy(decks = mapOf(Lang.EN to base.withVocab(set)))
        val w = enWork.forLearn(Lang.EN)
        assertEquals(Lang.EN, w.learnVocab)
        assertEquals("en", w.deck)
        val word = w.chapters.first().letters.first().words.single()
        assertEquals(en, word.text[Lang.EN])
        assertEquals(l0.source.first().audio, word.audio)       // 챕터 낱말과 같으면 그 낭독 (w1_en)
        assertEquals("x", word.ipa)                              // 발음기호는 영어 사전 것
        assertTrue(enWork.forLearn(Lang.KO) === enWork)          // 한국어 자료는 없음 → 원어 그대로
        assertEquals(2, enWork.lexicons.size)
    }

    // ── 답장 턴 · 오늘의 문법 (문법 사다리) ──────────────────
    @Test fun repliesParseTurnsInOrder() {
        val l = JSONObject("""{"replies": [
            {"quote": 0, "grammar": "III.0", "fr": "J'ai lu ta lettre deux fois.", "en": "I read your letter twice.", "ko": "형 편지를 두 번 읽었어요.",
             "koLow": "형 편지를 두 번 읽었어.", "koHigh": "형님 편지를 두 번 읽었습니다.",
             "focus": {"fr": ["ai lu"], "en": ["read"], "ko": ["읽었어요"]},
             "wrong": {"fr": [["lis", "lirai"]], "en": [["reads", "reading"]], "ko": [["읽어요", "읽을게요"]]}, "_note": "이 앱이 쓴 답장"},
            {"quote": 2, "grammar": "II.1", "fr": "Tu as choisi.", "en": "You chose.", "ko": "형이 골랐네요."}]}""")
        val t = Library.replies(l)
        assertEquals(2, t.size)
        assertEquals(0, t[0].quote); assertEquals("III.0", t[0].grammar)
        assertEquals("J'ai lu ta lettre deux fois.", t[0].text[Lang.FR])
        assertEquals("형 편지를 두 번 읽었어요.", io.github.graviton94.todayletters.core.Breaks.plain(t[0].text[Lang.KO]))
        assertEquals("형 편지를 두 번 읽었어.", io.github.graviton94.todayletters.core.Breaks.plain(t[0].koLow))
        assertEquals(listOf("ai lu"), t[0].focus[Lang.FR]); assertEquals(listOf("읽었어요"), t[0].focus[Lang.KO])
        assertTrue(t[0].styled); assertFalse(t[1].styled)
        assertEquals(listOf(listOf("lis", "lirai")), t[0].wrong[Lang.FR]); assertEquals(listOf(listOf("읽어요", "읽을게요")), t[0].wrong[Lang.KO])
        assertEquals(2, t[1].quote); assertTrue(t[1].focus.isEmpty()); assertTrue(t[1].wrong.isEmpty())
    }

    @Test fun oldReplyIsOneTurnWithoutGrammar() {
        val t = Library.replies(JSONObject("""{"reply": {"fr": "Moi aussi, j'ai pensé à toi.", "en": "I thought of you too.", "ko": "나도 형 생각 많이 했어.", "quote": 1}}"""))
        assertEquals(1, t.size)
        assertEquals(1, t[0].quote); assertEquals("", t[0].grammar)
        assertEquals("I thought of you too.", t[0].text[Lang.EN])
        assertTrue(Library.replies(JSONObject("{}")).isEmpty())
    }

    @Test fun chapterGrammarParses() {
        val g = Library.grammar(JSONObject("""{"grammar": [
            {"id": "III.0", "fr": {"name": {"ko": "복합과거 (avoir)", "en": "Passé composé with avoir"}, "hint": {"ko": "지난 일을 말해요", "en": "Talk about what happened"}},
             "ko": {"name": {"ko": "-아서/어서", "en": "-아서/어서"}}},
            {"fr": {"name": {"en": "no id"}}}], "letters": []}"""))
        assertEquals(1, g.size)
        assertEquals("Passé composé with avoir", g[0].line(Lang.FR)!!.name[Lang.EN])
        assertEquals("지난 일을 말해요", io.github.graviton94.todayletters.core.Breaks.plain(g[0].line(Lang.FR)!!.hint[Lang.KO]))
        assertEquals(null, g[0].line(Lang.EN))
        assertTrue(Library.grammar(JSONObject("""{"letters": []}""")).isEmpty())
    }

    /** 실제 데이터: 예전 "reply" 를 쓰는 장도 그대로 한 턴으로 읽힌다 (I장 첫 편지는 인용이 있다). */
    @Test fun realDataStillHasOneTurn() {
        val w = Library.works(androidx.test.core.app.ApplicationProvider.getApplicationContext()).first { it.series.id == "vincent" }
        val l = w.chapters.first().letters.first()
        assertTrue(l.replies.isNotEmpty())
        assertTrue(l.replies.first().quote >= 0)
    }
}
