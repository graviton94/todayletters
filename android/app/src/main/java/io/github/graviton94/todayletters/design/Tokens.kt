// 자동 생성: scripts/generate.py (design/tokens.json). 손으로 고치지 말 것.
package io.github.graviton94.todayletters.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 한 테마의 색. 라이트와 다크는 이름이 같고 값만 다르다. */
data class Palette(val paper: Color, val leaf: Color, val sky: Color, val ink: Color, val inkSoft: Color, val hair: Color, val gilt: Color, val giltText: Color, val fill: Color, val onFill: Color, val onFillSoft: Color, val post: Color, val hide: Color, val hideInk: Color, val correct: Color, val wrong: Color, val scrim: Color, val mapPaper: Color, val mapRoad: Color, val mapRiver: Color, val slip: Color, val slipInk: Color, val slipSoft: Color, val slipLaid: Color, val slipAge: Color, val shadow: Color, val lamp: Color, val line: Color)

/** 보내는 사람의 봉랍: 바탕 · 안쪽 테 · 글자. 두 테마 공통. */
data class Seal(val wax: Color, val ring: Color, val ink: Color)

object Tokens {
    val light = Palette(paper = Color(0xFFEFE6D2), leaf = Color(0xFFFBF6EA), sky = Color(0xFFF6EEDC), ink = Color(0xFF2A2118), inkSoft = Color(0xFF6E6150), hair = Color(0xFFD9CCAE), gilt = Color(0xFFB8913E), giltText = Color(0xFF7A5A20), fill = Color(0xFF2A2118), onFill = Color(0xFFF7F0E1), onFillSoft = Color(0xFFCFC2A8), post = Color(0xFF3D4C6E), hide = Color(0xFFE4D9C2), hideInk = Color(0xFF968871), correct = Color(0xFF3E6B57), wrong = Color(0xFFA23B2C), scrim = Color(0x522A2118), mapPaper = Color(0xFFEEE5D0), mapRoad = Color(0xFFFBF6EA), mapRiver = Color(0xFFB9C7DA), slip = Color(0xFFFBF6EA), slipInk = Color(0xFF2A2118), slipSoft = Color(0xFF6E6150), slipLaid = Color(0x0B5A3C14), slipAge = Color(0x33966E32), shadow = Color(0x383C280A), lamp = Color(0x99FFFAEC), line = Color(0xFFB9A985))
    val dark = Palette(paper = Color(0xFF17110C), leaf = Color(0xFF211912), sky = Color(0xFF1C150F), ink = Color(0xFFEADFC8), inkSoft = Color(0xFFA8977C), hair = Color(0xFF33281D), gilt = Color(0xFFE6BF68), giltText = Color(0xFFD2A955), fill = Color(0xFFD2A955), onFill = Color(0xFF17110C), onFillSoft = Color(0xFF5A4320), post = Color(0xFFB49A78), hide = Color(0xFF261D15), hideInk = Color(0xFF6E5E4A), correct = Color(0xFF8FBF9F), wrong = Color(0xFFE39A86), scrim = Color(0x8C000000), mapPaper = Color(0xFF211912), mapRoad = Color(0xFF33281D), mapRiver = Color(0xFF4A5C6E), slip = Color(0xFFE9DCC0), slipInk = Color(0xFF2A2118), slipSoft = Color(0xFF6A5B47), slipLaid = Color(0x0D5A3C14), slipAge = Color(0x4D785523), shadow = Color(0x99000000), lamp = Color(0x1AFFBA6E), line = Color(0xFF4A3B2B))
    object Seals {
        val vincent = Seal(Color(0xFF8E2A22), Color(0xFF7A231C), Color(0xFFEAD0C2))
        val emile = Seal(Color(0xFF2F4A3A), Color(0xFF263D30), Color(0xFFD6E2D2))
        val theo = Seal(Color(0xFF3C4A6B), Color(0xFF323E5A), Color(0xFFDCE2EE))
    }
    object Space {
        val s1 = 4.dp
        val s2 = 8.dp
        val s3 = 12.dp
        val s4 = 16.dp
        val s5 = 20.dp
        val s6 = 24.dp
        val s7 = 32.dp
        val s8 = 40.dp
    }
    object Radius {
        val frame = 0.dp
        val bubble = 2.dp
        val badge = 11.dp
        val round = 999.dp
    }
    object Stroke {
        val hair = 1.dp
        val rule = 1.dp
        val progress = 3.dp
        val underline = 2.dp
        val sealRing = 4.dp
    }
    object Size {
        val touch = 56.dp
        val row = 64.dp
        val button = 54.dp
        val buttonSm = 46.dp
        val chip = 46.dp
        val avatar = 40.dp
        val avatarLg = 96.dp
        val seal = 46.dp
        val postmark = 58.dp
        val postmarkSm = 40.dp
        val icon = 20.dp
        val iconLg = 26.dp
        val mic = 62.dp
        val star = 8.dp
        val starNow = 14.dp
        val handleW = 36.dp
        val handleH = 3.dp
        val plate = 230.dp
        val plateHero = 290.dp
        val bubbleMax = 304.dp
        val replyMax = 270.dp
        val portraitRow = 52.dp
        val speaker = 44.dp
    }
    object Text {
        val display = 30.sp
        val title = 24.sp
        val heading = 19.sp
        val numeral = 24.sp
        val target = 18.sp
        val base = 14.sp
        val body = 17.sp
        val label = 17.sp
        val small = 14.sp
        val caps = 11.sp
        val capsSm = 11.sp
        val word = 44.sp
        val chipWord = 18.sp
        val brand = 40.sp
    }
    object Font {
        const val display = "Cormorant Garamond"
        const val text = "Crimson Pro"
        const val caps = "Cinzel"
        const val hangul = "Noto Serif KR"
        const val kana = "Noto Serif JP"
    }
    object Motion {
        const val introHoldMs = 1600
        const val introFadeMs = 900
        const val loadingMinMs = 600
        const val tapPulseMs = 1800
        const val typingDotsMs = 1000
        const val typeCharMs = 28
        const val typeChunk = 2
        const val betweenMessagesMs = 1200
        const val twinkleMs = 1400
        const val fadeMs = 220
        const val sheetMs = 280
        const val pageMs = 320
        const val coachDelayMs = 700
        const val toastMs = 2400
        const val sealBreakMs = 600
        const val starLinkMs = 520
        const val inkLineMs = 900
        const val inkPauseMs = 500
    }
    object Leading {
        const val target = 1.4f
        const val base = 1.5f
        const val body = 1.55f
        const val title = 1.2f
        const val display = 1.02f
    }
    object Tracking {
        const val caps = 0.22f
        const val capsTight = 0.15f
    }
    object Alpha {
        const val future = 0.6f
        const val faint = 0.5f
        const val glow = 0.18f
        const val coachScrim = 0.85f
    }
    object Ratio {
        const val scaleLarge = 1.15f
        const val systemFontMax = 1.15f
    }
}
