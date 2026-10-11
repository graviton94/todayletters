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
}
