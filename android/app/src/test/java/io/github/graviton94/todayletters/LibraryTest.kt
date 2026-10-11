package io.github.graviton94.todayletters

import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Moment
import io.github.graviton94.todayletters.data.Library
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
}
