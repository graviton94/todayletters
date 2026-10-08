package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.ThemeMode
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 토큰 견본: 같은 컴포넌트가 라이트 · 다크에서 색만 바뀌는지 폰에서 확인하는 화면. */
@Composable
fun Specimen(mode: ThemeMode, large: Boolean, onMode: (ThemeMode) -> Unit, onLarge: (Boolean) -> Unit) {
    val p = Ink.palette
    Column(
        Modifier.fillMaxSize().background(p.paper).safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.s7, vertical = Tokens.Space.s6),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5),
    ) {
        Text("Lettres de Vincent".uppercase(), style = Type.caps, color = p.inkSoft)
        Box(Modifier.fillMaxWidth().height(Tokens.Stroke.rule).background(p.ink))
        Text(stringResource(R.string.app_name), style = Type.display, color = p.ink)

        Segmented(
            listOf(ThemeMode.SYSTEM to R.string.set_theme_system, ThemeMode.LIGHT to R.string.set_theme_light, ThemeMode.DARK to R.string.set_theme_dark),
            selected = mode, onPick = onMode,
        )
        Segmented(listOf(false to R.string.set_text_normal, true to R.string.set_text_large), selected = large, onPick = onLarge)

        // 말풍선: 배우는 언어 한 줄 + 작게 내 언어 한 줄
        Column(
            Modifier.widthIn(max = Tokens.Size.bubbleMax).background(p.leaf).border(Tokens.Stroke.hair, p.hair)
                .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1),
        ) {
            Text("Mais la vue des étoiles me fait toujours rêver,", style = Type.target, color = p.ink)
            Text("그래도 별을 보면 나는 늘 꿈을 꾸게 돼.", style = Type.base, color = p.inkSoft)
            Text("t. à t. Vincent", style = Type.signature, color = p.inkSoft, modifier = Modifier.align(Alignment.End))
        }
        Column(
            Modifier.align(Alignment.End).widthIn(max = Tokens.Size.replyMax).background(p.fill)
                .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
        ) {
            Text("La vue des étoiles te fait rêver ?", style = Type.target, color = p.onFill)
            Text("별을 보면 형은 꿈을 꾸는구나?", style = Type.base, color = p.onFillSoft)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
            val seal = Tokens.Seals.vincent
            Box(
                Modifier.size(Tokens.Size.seal).background(seal.wax, CircleShape).border(Tokens.Stroke.sealRing, seal.ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("V", style = Type.heading, color = seal.ink) }
            Swatch(p.gilt); Swatch(p.giltText); Swatch(p.post); Swatch(p.correct); Swatch(p.wrong)
        }
    }
}

@Composable
private fun Swatch(c: Color) = Box(Modifier.size(Tokens.Size.icon).background(c))

@Composable
private fun <T> Segmented(options: List<Pair<T, Int>>, selected: T, onPick: (T) -> Unit) {
    val p = Ink.palette
    Row(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, p.hair)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).heightIn(min = Tokens.Size.touch)
                    .background(if (on) p.fill else Color.Transparent)
                    .semantics { this.selected = on }
                    .clickable(role = Role.RadioButton) { onPick(value) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(label), style = Type.label, color = if (on) p.onFill else p.ink) }
        }
    }
}
