package io.github.graviton94.todayletters.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.github.graviton94.todayletters.R
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.github.graviton94.todayletters.core.ThemeMode

/**
 * 하루의 편지 테마.
 * 컴포넌트는 하나: 화면 코드는 [Ink.palette] 의 색 이름과 [Type] 의 글자 역할만 쓴다.
 * 라이트와 다크는 같은 이름에 다른 값이 들어갈 뿐, 모양 · 간격 · 움직임은 같다.
 * 테마는 사용자 설정(기본: 시스템)을 따르고, 편지의 시간대로 바뀌지 않는다.
 */
private val LocalPalette = staticCompositionLocalOf { Tokens.light }
private val LocalDark = staticCompositionLocalOf { false }

object Ink {
    val palette: Palette @Composable get() = LocalPalette.current
    val isDark: Boolean @Composable get() = LocalDark.current
}

/** 글꼴 역할 (scripts/build_fonts.py 가 쓰는 글자만 남겨 res/font 에 넣음). */
object Faces {
    /** Cormorant Garamond: 제목 · 숫자 · 이름 · 서명 */
    val display = FontFamily(
        Font(R.font.display_medium, FontWeight.Medium),
        Font(R.font.display_semibold, FontWeight.SemiBold),
        Font(R.font.display_italic, FontWeight.Medium, FontStyle.Italic),
    )
    /** Crimson Pro: 편지 · 본문 (라틴) */
    val text = FontFamily(
        Font(R.font.text_regular, FontWeight.Normal),
        Font(R.font.text_medium, FontWeight.Medium),
        Font(R.font.text_italic, FontWeight.Normal, FontStyle.Italic),
    )
    /** Cinzel: 작은 대문자 라벨 (라틴만) */
    val caps = FontFamily(Font(R.font.caps, FontWeight.Medium))
    /** Noto Serif KR: 한글이 들어가는 모든 글자 */
    val hangul = FontFamily(
        Font(R.font.kr_regular, FontWeight.Normal),
        Font(R.font.kr_semibold, FontWeight.SemiBold),
        Font(R.font.kr_semibold, FontWeight.Medium),
    )
}

/** 앱 글자가 한국어인가 (앱 글자 언어는 영어 · 한국어 둘). */
@Composable
fun uiHangul(): Boolean = LocalConfiguration.current.locales[0].language == "ko"

/**
 * 한글이 들어가는 글자는 한글 글꼴로. 라틴 글꼴에는 한글이 없어서, 글자의 언어를 아는 곳(말풍선 줄,
 * 앱 글자)에서 이걸로 고른다. 이탤릭 한글은 바로 세운다.
 */
fun TextStyle.hangul(on: Boolean = true): TextStyle =
    if (!on) this else copy(fontFamily = Faces.hangul, fontStyle = FontStyle.Normal,
        // 한글에는 대문자 라벨의 자간을 주지 않는다 ("배 울 언 어"처럼 벌어지지 않게). 라벨은 한 단계 키워 굵게.
        letterSpacing = 0.em,
        fontSize = if (fontFamily == Faces.caps) Tokens.Text.small else fontSize,
        fontWeight = if (fontFamily == Faces.caps || (fontWeight ?: FontWeight.Normal) >= FontWeight.Medium) FontWeight.SemiBold else FontWeight.Normal,
        // 한국어는 어절 단위로만 줄을 바꾼다 (안드로이드 13+; 그 아래는 글자에 넣은 이음표가 같은 일을 한다. core/Breaks.kt)
        lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase))

private fun style(face: FontFamily, size: TextUnit, leading: Float? = null, weight: FontWeight = FontWeight.Normal,
                  italic: Boolean = false, tracking: Float = 0f) = TextStyle(
    fontFamily = face, fontSize = size, fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    lineHeight = leading?.em ?: TextUnit.Unspecified,
    letterSpacing = tracking.em,
)

/** 글자 역할. 화면 코드에서 크기 숫자를 쓰지 않는다. */
object Type {
    val display get() = style(Faces.display, Tokens.Text.display, Tokens.Leading.display, FontWeight.Medium)
    val title get() = style(Faces.display, Tokens.Text.title, Tokens.Leading.title, FontWeight.Medium)
    val heading get() = style(Faces.display, Tokens.Text.heading, Tokens.Leading.title, FontWeight.SemiBold)
    val numeral get() = style(Faces.display, Tokens.Text.numeral, 1.0f)
    val word get() = style(Faces.display, Tokens.Text.word, 1.0f, FontWeight.Medium)
    /** 말풍선의 배우는 언어 줄 (크게). */
    val target get() = style(Faces.text, Tokens.Text.target, Tokens.Leading.target)
    /** 말풍선의 내 언어 줄 (작게, inkSoft 로). */
    val base get() = style(Faces.text, Tokens.Text.base, Tokens.Leading.base)
    val body get() = style(Faces.text, Tokens.Text.body, Tokens.Leading.body)
    val label get() = style(Faces.text, Tokens.Text.label, Tokens.Leading.body)
    val small get() = style(Faces.text, Tokens.Text.small, Tokens.Leading.base)
    val chipWord get() = style(Faces.text, Tokens.Text.chipWord, 1.2f)
    val signature get() = style(Faces.display, Tokens.Text.label, 1.2f, italic = true)
    /** 작은 대문자 라벨 (Chapitre II, PL. XII). 라틴 글자에만. */
    val caps get() = style(Faces.caps, Tokens.Text.caps, 1.3f, FontWeight.Medium, tracking = Tokens.Tracking.caps)
    val capsSm get() = style(Faces.caps, Tokens.Text.capsSm, 1.3f, FontWeight.Medium, tracking = Tokens.Tracking.caps)
}

/**
 * @param mode 사용자 설정의 테마 (기본: 시스템).
 * @param large 글자 크기 두 단계 중 "크게". 폰의 글꼴 배율은 [Tokens.Ratio.systemFontMax] 까지만 따라가서 화면이 깨지지 않게 한다.
 */
@Composable
fun TodayLettersTheme(mode: ThemeMode = ThemeMode.SYSTEM, large: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val density = LocalDensity.current
    val scale = density.fontScale.coerceAtMost(Tokens.Ratio.systemFontMax) * (if (large) Tokens.Ratio.scaleLarge else 1f)
    CompositionLocalProvider(
        LocalPalette provides if (dark) Tokens.dark else Tokens.light,
        LocalDark provides dark,
        LocalDensity provides Density(density.density, scale),
        content = content,
    )
}
