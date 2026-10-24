package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 말하기 판의 한 줄 억양 선 ([PitchLine]) · 낱말 자리 ([WordSpots]) (시안 하루 흐름 ③, 2026-10-24). */
class PitchLineTest {
    private val rate = 16000

    /** 가짜 목소리: 음높이 [hz] (시각 → Hz), 크기 [amp] (0 이면 침묵), 배음 셋. [noise] 구간은 쉿 소리 (마찰음). */
    private fun voice(seconds: Double, hz: (Double) -> Double, amp: (Double) -> Double, noise: ClosedRange<Double>? = null): FloatArray {
        val n = (seconds * rate).toInt(); var ph = 0.0
        val rnd = java.util.Random(7)
        return FloatArray(n) { k ->
            val t = k.toDouble() / rate
            ph += 2 * Math.PI * hz(t) / rate
            val v = amp(t) * (0.6 * Math.sin(ph) + 0.3 * Math.sin(2 * ph) + 0.1 * Math.sin(3 * ph))
            (if (noise != null && t in noise) 0.25 * (rnd.nextDouble() * 2 - 1) else v).toFloat()
        }
    }

    /** 높낮이가 있는 말 세 마디 (사이에 60ms 끊김 하나 · 600ms 쉼 하나 · 마찰음 하나). */
    private val phrase by lazy {
        voice(2.6, hz = { t -> 170 + 40 * Math.sin(t * 3) }, amp = { t ->
            when {
                t < 0.15 -> 0.0
                t in 0.70..0.76 -> 0.0          // 짧은 끊김 (예전 선은 여기서 끊겼다)
                t in 1.20..1.80 -> 0.0          // 긴 쉼 (120ms 로 줄여 그린다)
                t > 2.45 -> 0.0
                else -> 0.5
            }
        }, noise = 1.0..1.08)
    }

    @Test fun realVoiceIsOneUnbrokenLine() {
        val line = assertNotNull(PitchLine.line(PitchLine.analyze(phrase, rate)))
        assertEquals(PitchLine.FRAMES, line.values.size)
        assertTrue(line.values.none { it.isNaN() }, "no gaps")
        assertEquals(1, PitchLine.pieces(line.values))
        assertTrue(line.values.all { it in -1f..1f })
        assertTrue(line.range in 3f..12f)
        // 실제 길이 (앞뒤 침묵만 뺌) 는 녹음 고리의 기준: 0.15 ~ 2.45초
        assertTrue(line.ms in 2200..2400, "ms ${line.ms}")
    }

    @Test fun longPauseIsDrawnShort() {
        val line = assertNotNull(PitchLine.line(PitchLine.analyze(phrase, rate)))
        // 쉼 앞 (1.15초) 과 뒤 (1.85초) 의 가로 거리: 실제 0.7초지만 120ms 남짓으로 줄어든다
        val gap = line.x(1850) - line.x(1150)
        val linear = 0.7f / 2.3f
        assertTrue(gap < linear / 2, "gap $gap vs linear $linear")
        // 시간이 가면 자리는 물러나지 않는다
        val xs = (0..2600 step 20).map { line.x(it) }
        assertTrue(xs.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(0f, line.x(0)); assertEquals(1f, line.x(2600))
    }

    @Test fun mineIsFilledToo() {
        val ref = PitchLine.analyze(phrase, rate)
        // 내 소리: 더 느리게 · 낮게 · 중간에 끊김 많이 (목소리가 자주 끊겨도 한 줄)
        val mine = PitchLine.analyze(voice(3.0, hz = { t -> 120 + 25 * Math.sin(t * 2.6) }, amp = { t ->
            if (t < 0.2 || t > 2.8 || (t * 10).toInt() % 4 == 0) 0.0 else 0.4
        }), rate)
        val c = assertNotNull(PitchLine.curves(ref, mine))
        assertEquals(c.ref.size, c.mine!!.size)
        assertEquals(1, PitchLine.pieces(c.ref)); assertEquals(1, PitchLine.pieces(c.mine))
        assertNull(PitchLine.curves(PitchLine.analyze(FloatArray(rate), rate), mine))
        assertNull(PitchLine.curves(ref, PitchLine.analyze(FloatArray(rate), rate))!!.mine)
    }

    @Test fun fricativeHooksAreDropped() {
        // 쉿 소리 (마찰음) 를 높은 음으로 읽어 생기던 갈고리: 선이 그 자리에서 ±1 로 치솟지 않는다
        val line = assertNotNull(PitchLine.line(PitchLine.analyze(phrase, rate)))
        val at = (line.x(1040) * (line.values.size - 1)).toInt()
        assertTrue(kotlin.math.abs(line.values[at]) < 0.95f, "value ${line.values[at]}")
    }

    /** 앱에 든 실제 낭독 한 토막 (빈센트 I장 2번째 편지 둘째 덩어리 “des berges orangées avec verdure,”, 16kHz s16). */
    @Test fun bundledNarrationIsOneLine() {
        val bytes = javaClass.getResourceAsStream("/pitch/fr_c1l2_m2_chunk2.s16")!!.readBytes()
        val sb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val x = FloatArray(sb.remaining()) { sb.get(it) / 32768f }
        val t = PitchLine.analyze(x, rate)
        val line = assertNotNull(PitchLine.line(t))
        assertEquals(1, PitchLine.pieces(line.values))
        // 예전 선은 같은 소리에서 여러 조각이었다 (근거: 시안 audit/pitch)
        val old = Prosody.preview(Prosody.analyze(x, rate))
        assertTrue(PitchLine.pieces(old?.ref) > 1, "old line pieces ${PitchLine.pieces(old?.ref)}")
        // 낱말 다섯이 순서대로, 소리 안에
        val ws = WordSpots.estimate("des berges orangées avec verdure,", t.db, Lang.FR)
        assertEquals(listOf("des", "berges", "orangées", "avec", "verdure,"), ws.map { it.t })
        assertTrue(ws.zipWithNext().all { (a, b) -> b.s > a.s && a.e <= b.s + 1 })
        assertTrue(ws.first().s >= 0 && ws.last().e <= t.size * 10)
    }

    /**
     * 근거 파일 (CI 에서는 하지 않음): PITCH_CLIPS 폴더의 16kHz s16 소리 (<이름>.s16, 같은 이름 .json 은 낭독 덩어리) 마다
     * 예전 선 · 새 선 · 낱말 자리를 <이름>.out.json 으로 쓴다. 파이썬 시제품과 숫자로 견주고 PNG 로 그리는 데 쓴다.
     */
    @Test fun dumpForEvidence() {
        val dir = System.getenv("PITCH_CLIPS")?.let { java.io.File(it) }?.takeIf { it.isDirectory } ?: return
        dir.listFiles { f -> f.name.endsWith(".s16") }!!.sorted().forEach { f ->
            val sb = java.nio.ByteBuffer.wrap(f.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val x = FloatArray(sb.remaining()) { sb.get(it) / 32768f }
            val t = PitchLine.analyze(x, rate)
            val line = PitchLine.line(t)
            val old = Prosody.preview(Prosody.analyze(x, rate))
            val lang = Lang.values().first { f.name.startsWith(it.code + "_") }
            val meta = java.io.File(f.path.removeSuffix(".s16") + ".json").takeIf { it.isFile }?.readText()
            val chunks = meta?.let { Regex("\"s\":\\s*([0-9.]+),\\s*\"e\":\\s*([0-9.]+),\\s*\"text\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(it).map { m ->
                Triple((m.groupValues[1].toDouble() * 1000).toInt(), (m.groupValues[2].toDouble() * 1000).toInt(), m.groupValues[3])
            }.toList() }.orEmpty()
            val text = chunks.joinToString(" ") { it.third }
            val ws = if (text.isBlank()) emptyList() else WordSpots.estimate(text, t.db, lang, chunks)
            fun arr(a: FloatArray?) = a?.joinToString(",", "[", "]") { if (it.isNaN()) "null" else "%.4f".format(java.util.Locale.ROOT, it) } ?: "null"
            val words = ws.joinToString(",", "[", "]") { w -> "{\"t\":\"${w.t.replace("\"", "\\\"")}\",\"s\":${w.s},\"e\":${w.e},\"x\":${"%.4f".format(java.util.Locale.ROOT, line?.x(w.s) ?: 0f)}}" }
            java.io.File(f.path.removeSuffix(".s16") + ".out.json").writeText(
                "{\"old\":${arr(old?.ref)},\"new\":${arr(line?.values)},\"semis\":${arr(line?.semis)},\"range\":${line?.range},\"ms\":${line?.ms},\"words\":$words}")
        }
    }
}

class WordSpotsTest {
    private fun w(t: String, s: Int, e: Int) = WordAt(t, s, e)

    @Test fun syllableCounts() {
        assertEquals(2, WordSpots.syllables("verger", Lang.FR))
        assertEquals(1, WordSpots.syllables("monstre.", Lang.FR))
        assertEquals(1, WordSpots.syllables("make", Lang.EN))
        assertEquals(2, WordSpots.syllables("little", Lang.EN))
        assertEquals(3, WordSpots.syllables("안녕히", Lang.KO))
        assertEquals(0, WordSpots.syllables("—", Lang.FR))
        assertEquals(4, WordSpots.syllables("12", Lang.EN))
    }

    @Test fun shortSentenceStaysWhole() {
        val ws = (0 until 8).map { w("w$it", it * 400, it * 400 + 350) }
        assertEquals(listOf(0..7), WordSpots.split(ws))
    }

    @Test fun longSentenceSplitsAtPausesUnderFourSeconds() {
        // 영어 한 문장 20 낱말 · 약 9초, 쉼은 6번째 · 13번째 낱말 뒤 (쉼표)
        var at = 0
        val ws = (0 until 20).map { k ->
            val t = if (k == 5 || k == 12) "w$k," else "w$k"
            val x = w(t, at, at + 380); at += 420 + if (k == 5 || k == 12) 400 else 0; x
        }
        val parts = WordSpots.split(ws)
        assertEquals(listOf(0..5, 6..12, 13..19), parts)
        parts.forEach { r -> assertTrue(ws[r.last].e - ws[r.first].s <= WordSpots.SPAN_MS); assertTrue(r.count() <= Prosody.SPAN_MAX) }
    }

    @Test fun noTinyTail() {
        val ws = (0 until 12).map { w("w$it", it * 300, it * 300 + 280) }
        val parts = WordSpots.split(ws)
        assertTrue(parts.all { it.count() >= 3 }, "$parts")
        assertEquals(12, parts.sumOf { it.count() })
    }

    @Test fun currentWord() {
        val ws = listOf(w("a", 100, 300), w("b", 300, 700))
        assertEquals(-1, WordSpots.at(ws, 50)); assertEquals(0, WordSpots.at(ws, 200)); assertEquals(1, WordSpots.at(ws, 900))
    }
}
