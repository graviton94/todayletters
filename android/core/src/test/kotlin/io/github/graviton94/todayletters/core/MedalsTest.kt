package io.github.graviton94.todayletters.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 메달 진열장과 끝없는 레벨 (data/medals.json). 데이터 파일을 그대로 읽어서 앱과 같은 값으로 시험한다. */
class MedalsTest {
    private val json: Map<String, Any?> by lazy {
        @Suppress("UNCHECKED_CAST")
        Json.parse(dataFile().readText()) as Map<String, Any?>
    }
    private val book by lazy { Medals.parse(dataFile().readText()) }

    private fun dataFile(): File {
        var d: File? = File("").absoluteFile
        while (d != null) { File(d, "data/medals.json").takeIf { it.exists() }?.let { return it }; d = d.parentFile }
        error("data/medals.json 을 찾지 못함")
    }

    @Test fun parsesAllMedals() {
        assertEquals(7, book.families.size)
        assertEquals(31, book.medals.size)
        assertEquals(listOf(3, 7, 14, 30, 60, 100, 365), book.inFamily("streak").map { it.threshold })
        val m = book.medal("streak_14")!!
        assertEquals(MedalTier.SILVER, m.tier)
        assertEquals("헤라클레스의 끈기", Breaks.plain(m.title[Lang.KO]))
        assertEquals("medals/streak_14.webp", m.image)
        assertEquals("The Metropolitan Museum of Art", m.source.museum)
        assertTrue(m.source.license.startsWith("CC0"))
        assertEquals("vincent", book.medal("series_vincent")!!.series)
        assertEquals("medals/series_spare.webp", book.spareImage)
        // 모든 메달 그림은 출처를 갖는다
        book.medals.forEach { assertTrue(it.source.objectId > 0 && it.source.url.isNotEmpty(), it.id) }
    }

    @Test fun jsonParserHandlesEscapes() {
        assertEquals(mapOf("a" to listOf(1.0, true, null), "b" to "x\"yé"), Json.parse("""{"a":[1,true,null],"b":"x\"yé"}"""))
    }

    @Test fun medalsUnlockAtThresholds() {
        val s = Stats(bestStreak = 7, lettersDone = 1, wordsKnown = 24, readAlongStars = 10, walksDone = 5, seriesCompleted = setOf("mozart"))
        val ids = Medals.newly(book, s, setOf("streak_3")).map { it.id }
        assertEquals(listOf("streak_7", "letters_1", "stars_10", "walks_5", "series_mozart"), ids)
        // 앱에 있는 작품의 시리즈 메달만
        assertFalse("series_mozart" in Medals.newly(book, s, emptySet(), series = setOf("vincent")).map { it.id })
        assertEquals("streak_14", Medals.next(book, "streak", setOf("streak_3", "streak_7"))!!.id)
        assertEquals(0.5f, Medals.progress(book.medal("streak_14")!!, s))
        assertNull(Medals.next(book, "walks", book.inFamily("walks").map { it.id }.toSet()))
    }

    @Test fun oldMilestonesStayEarned() {
        val old = setOf("vincent:alone_1", "vincent:streak_7", "mozart:words_50", "all_letters_30", "all_streak_100", "vincent:pieces_12", "all_langs_2")
        assertEquals(setOf("letters_1", "streak_7", "words_25", "letters_28", "streak_100"), Medals.migrate(old))
        Medals.migrate(old).forEach { assertTrue(book.medal(it) != null, it) }
        assertTrue(Medals.grantsPainting(3)); assertFalse(Medals.grantsPainting(4)); assertFalse(Medals.grantsPainting(0))
    }

    @Test fun xpCurveMatchesData() {
        assertEquals(100, Levels.xpToNext(1))
        assertEquals(264, Levels.xpToNext(2))
        assertEquals(2512, Levels.xpToNext(10))
        @Suppress("UNCHECKED_CAST")
        val table = (json["levels"] as Map<String, Any?>)["table"] as List<Map<String, Any?>>
        assertEquals(30, table.size)
        table.forEach { row ->
            val lv = (row["level"] as Double).toInt()
            assertEquals((row["xpTotal"] as Double).toLong(), Levels.totalFor(lv), "xpTotal $lv")
            assertEquals((row["xpToNext"] as Double).toLong(), Levels.xpToNext(lv), "xpToNext $lv")
            @Suppress("UNCHECKED_CAST") val title = row["title"] as Map<String, String>
            assertEquals(title["ko"], Levels.title(book.levels, lv, Lang.KO))
            assertEquals(title["en"], Levels.title(book.levels, lv, Lang.EN))
        }
    }

    @Test fun levelFromXp() {
        assertEquals(Level(1, 0, 100, 0), Levels.of(0))
        assertEquals(Level(1, 99, 100, 99), Levels.of(99))
        assertEquals(Level(2, 0, 264, 100), Levels.of(100))
        val l14 = Levels.of(Levels.totalFor(14) + 10)
        assertEquals(14, l14.level); assertEquals(10, l14.into)
        assertEquals("해설사 II", Levels.title(book.levels, 14, Lang.KO))
        assertEquals("학예 보조 II", Levels.title(book.levels, 17, Lang.KO))
        // 31 레벨부터 관장 IV, V …
        assertEquals("관장 IV", Levels.title(book.levels, 31, Lang.KO))
        assertEquals("Director XIII", Levels.title(book.levels, 40, Lang.EN))
        assertEquals(Rank(9, 3), Levels.rank(30))
    }

    @Test fun entranceDecorations() {
        assertNull(Levels.decoration(4))
        assertEquals(Decoration(0, 1, 5), Levels.decoration(5))
        assertEquals(Decoration(1, 1, 10), Levels.decoration(14))
        assertEquals("월계관 문패", Levels.decorationName(book.levels, Levels.decoration(5)!!, Lang.KO))
        assertEquals("개선 아치", Levels.decorationName(book.levels, Levels.decoration(60)!!, Lang.KO))
        assertEquals("월계관 문패 II", Levels.decorationName(book.levels, Levels.decoration(65)!!, Lang.KO))
        assertEquals(listOf(10, 15), Levels.decorationsBetween(9, 16).map { it.level })
        assertNull(Levels.decorationAt(14))
    }

    @Test fun xpLedgerCapsRepeatsOnly() {
        // medals.json 의 XP 표와 같은 값
        XpAction.entries.forEach { assertEquals(book.levels.xp[it.key], it.amount, it.key) }
        var l = XpLedger()
        repeat(6) { l = Xp.gain(l, XpAction.LETTER_READ, 10).first }
        assertEquals(150, l.total)                                   // 하루 상한 150
        assertEquals(50, Xp.gain(l, XpAction.MEDAL, 10).second)      // 메달은 상한 없음
        val next = Xp.gain(l, XpAction.LETTER_READ, 11)               // 다음 날은 새로
        assertEquals(30, next.second); assertEquals(180, next.first.total)
        assertEquals(0, Xp.gain(l, XpAction.WORD_LEARNED, 10, times = 0).second)
        assertEquals(3 * 30 + 2 * 40 + 10 * 3 + 50 + 300, Xp.seed(Stats(lettersDone = 3, wordsKnown = 10, seriesCompleted = setOf("vincent")), sealed = 2, medals = 1, days = 0))
    }

    @Test fun romanNumerals() {
        assertEquals(listOf("I", "IV", "IX", "XIV", "XL", "XCIX"), listOf(1, 4, 9, 14, 40, 99).map(Roman::of))
    }
}
