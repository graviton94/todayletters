package io.github.graviton94.todayletters.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 판매 정책 (docs/billing.md): 무료 범위 · 이용권 · 값. 작품 자료(data/<작품>/series.json)도 그대로 읽어 정책과 맞는지 본다. */
class SalesTest {
    private fun ch(n: Int, free: Boolean, series: String = "vincent") = Chapter(series, "$n", Tri(mapOf(Lang.EN to "")), free, emptyList())
    private val eight = (1..8).map { ch(it, it <= 2) }

    @Test fun freeIsPrologueAndFirstTwoChapters() {
        val none = Entitlement()
        assertTrue(none.free)
        assertEquals(listOf(true, true, false, false, false, false, false, false), eight.map { Access.open(it, none) })
        assertEquals(3..8, Access.lockedRange(eight))
        assertNull(Access.lockedRange(eight.map { it.copy(free = true) }))
    }

    @Test fun ownedSeriesOpensOnlyThatSeries() {
        val e = Entitlement.of(listOf("series_vincent"))
        assertFalse(e.subscribed); assertFalse(e.free)
        assertTrue(eight.all { Access.open(it, e) })
        assertFalse(Access.open(ch(3, false, "mozart"), e))
        assertTrue(Access.open(ch(2, true, "mozart"), e))
    }

    @Test fun subscriptionOpensEverySeriesAndEndingItKeepsOwned() {
        val sub = Entitlement.of(listOf(Products.SUB, "series_mozart"))
        assertTrue(sub.subscribed)
        assertTrue(Access.open(ch(8, false, "vincent"), sub))
        assertTrue(Access.open(ch(8, false, "future"), sub))
        // 구독이 끝나면: 소장한 모차르트만 계속 열린다
        val ended = Entitlement.of(listOf("series_mozart"))
        assertTrue(Access.open(ch(8, false, "mozart"), ended))
        assertFalse(Access.open(ch(3, false, "vincent"), ended))
    }

    @Test fun productIds() {
        assertEquals("sub_all", Products.SUB)
        assertEquals("series_vincent", Products.series("vincent"))
        assertEquals("vincent", Products.seriesOf("series_vincent"))
        assertNull(Products.seriesOf("sub_all"))
        assertNull(Products.seriesOf("series_"))
        assertEquals("sub_all:yearly", Plan.Yearly.key)
        assertEquals("sub_all:monthly", Plan.Monthly.key)
        assertEquals("series_mozart", Plan.Series("mozart").key)
    }

    @Test fun prices() {
        assertEquals(43, Access.yearlyDiscount(1_900_000_000L, 12_900_000_000L))
        assertEquals(1_075_000_000L, Access.perMonth(12_900_000_000L))
        assertEquals(0, Access.yearlyDiscount(0, 12_900_000_000L))
        assertEquals("₩12,900", Price.planned(12_900).formatted)
        assertEquals("₩1,075", Price.won(1_075))
        assertEquals(PriceTier.T1, PriceTier.forVolume(39))
        assertEquals(PriceTier.T2, PriceTier.forVolume(40))
        assertEquals(PriceTier.T2, PriceTier.forVolume(70))
        assertEquals(PriceTier.T3, PriceTier.forVolume(71))
        assertEquals(PriceTier.T3, PriceTier.forVolume(20, extra = true))
        assertEquals(2_900, Plan.Series("vincent", PriceTier.of(2)).fallbackWon)
    }

    // ── 작품 자료가 정책과 맞는가 ─────────────────────────────
    private fun dataDir(): File {
        var d: File? = File("").absoluteFile
        while (d != null) { File(d, "data/medals.json").takeIf { it.exists() }?.let { return it.parentFile }; d = d.parentFile }
        error("data/ 를 찾지 못함")
    }

    @Suppress("UNCHECKED_CAST")
    private fun obj(f: File) = Json.parse(f.readText()) as Map<String, Any?>

    @Test fun seriesDataFollowsPolicy() {
        val metas = dataDir().listFiles()!!.mapNotNull { File(it, "series.json").takeIf(File::exists) }
        assertTrue(metas.size >= 2)
        val tiers = mutableMapOf<String, PriceTier>()
        metas.forEach { f ->
            val m = obj(f)
            val id = m["id"] as String
            @Suppress("UNCHECKED_CAST") val chapters = m["chapters"] as List<Map<String, Any?>>
            // 무료는 I · II장뿐
            assertEquals(chapters.indices.map { it < 2 }, chapters.map { it["free"] == true }, id)
            assertEquals(Products.series(id), m["product"], id)
            fun letters(name: String) = File(f.parentFile, name).takeIf(File::exists)?.let { (obj(it)["letters"] as? List<*>)?.size } ?: 0
            val n = letters("prologue.json") + chapters.sumOf { letters(it["file"] as String) } + (if (File(f.parentFile, "epilogue.json").exists()) 1 else 0)
            val tier = PriceTier.of((m["tier"] as Number).toInt())
            assertEquals(PriceTier.forVolume(n, m["extra"] == true), tier, "$id: 편지 $n 통")
            tiers[id] = tier
        }
        assertEquals(PriceTier.T2, tiers["vincent"])
        assertEquals(PriceTier.T2, tiers["mozart"])
    }
}
