// 자동 생성: scripts/generate.py (design/tokens.json). 손으로 고치지 말 것.
package io.github.graviton94.todayletters.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 한 테마의 색. 라이트와 다크는 이름이 같고 값만 다르다. */
data class Palette(val paper: Color, val leaf: Color, val sky: Color, val ink: Color, val inkSoft: Color, val hair: Color, val gilt: Color, val giltText: Color, val fill: Color, val onFill: Color, val onFillSoft: Color, val post: Color, val hide: Color, val hideInk: Color, val correct: Color, val wrong: Color, val scrim: Color, val mapPaper: Color, val mapRoad: Color, val mapRiver: Color)

/** 보내는 사람의 봉랍: 바탕 · 안쪽 테 · 글자. 두 테마 공통. */
data class Seal(val wax: Color, val ring: Color, val ink: Color)

object Tokens {
    val light = Palette(paper = Color(0xFFF6F3EC), leaf = Color(0xFFFFFFFF), sky = Color(0xFFFFFFFF), ink = Color(0xFF16130F), inkSoft = Color(0xFF5D564C), hair = Color(0xFFD8D0C2), gilt = Color(0xFFB8913E), giltText = Color(0xFF7F6127), fill = Color(0xFF16130F), onFill = Color(0xFFF6F3EC), onFillSoft = Color(0xFFCFC6B6), post = Color(0xFF3C4A6B), hide = Color(0xFFECE6DA), hideInk = Color(0xFF9C9488), correct = Color(0xFF3E6B57), wrong = Color(0xFFA23B2C), scrim = Color(0x4716130F), mapPaper = Color(0xFFEEE7D8), mapRoad = Color(0xFFFFFFFF), mapRiver = Color(0xFFB9C7DA))
    val dark = Palette(paper = Color(0xFF0E1630), leaf = Color(0xFF18234A), sky = Color(0xFF121C3D), ink = Color(0xFFEDE6D3), inkSoft = Color(0xFFA9A38F), hair = Color(0xFF2C3760), gilt = Color(0xFFF0CD6A), giltText = Color(0xFFD9B45A), fill = Color(0xFFD9B45A), onFill = Color(0xFF0E1630), onFillSoft = Color(0xFF4A3D1C), post = Color(0xFF8C9AC0), hide = Color(0xFF1A2550), hideInk = Color(0xFF5E6788), correct = Color(0xFF7FB39A), wrong = Color(0xFFE08A7C), scrim = Color(0x8C050814), mapPaper = Color(0xFF121C3D), mapRoad = Color(0xFF24305A), mapRiver = Color(0xFF2F4E8E))
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
        val buttonSm = 50.dp
        val chip = 46.dp
        val avatar = 40.dp
        val avatarLg = 62.dp
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
    }
    object Text {
        val display = 42.sp
        val title = 30.sp
        val heading = 23.sp
        val numeral = 24.sp
        val target = 18.sp
        val base = 13.sp
        val body = 16.sp
        val label = 15.sp
        val small = 13.sp
        val caps = 11.sp
        val capsSm = 10.sp
        val word = 44.sp
        val chipWord = 19.sp
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
        const val starLinkMs = 260
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
