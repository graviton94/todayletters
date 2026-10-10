package io.github.graviton94.todayletters.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/*
 * 연습 화면 공통 틀 (v18): 편지 안 연습과 복습이 같은 머리 · 같은 확인 띠를 쓴다.
 *   머리: ✕ · 진행 막대 · n/N · (?)
 *   확인 띠: 맞으면 초록 띠 (뜻 · 발음 + 다음), 틀리면 붉은 띠 (다시 고르기 · 정답 보기), 두 번째도 틀리면 정답
 */

/** 연습 머리. [onHelp] 가 있으면 오른쪽에 (?). */
@Composable
fun PracticeHeader(at: Int, total: Int, onClose: () -> Unit, onHelp: (() -> Unit)? = null, count: String? = null) {
    val p = Ink.palette
    Row(Modifier.fillMaxWidth().padding(end = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
        IconButton(stringResource(R.string.close), onClick = onClose) { Text("×", style = Type.title, color = p.ink) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (0 until total.coerceAtLeast(1)).forEach { k ->
                Box(Modifier.weight(1f).height(4.dp).background(when { k < at -> p.giltText; k == at -> p.ink; else -> p.hair }))
            }
        }
        Text("  " + (count ?: "${(at + 1).coerceAtMost(total)} / $total"), style = Type.small, color = p.inkSoft)
        if (onHelp != null) {
            Box(Modifier.width(Tokens.Space.s2))
            Box(Modifier.size(44.dp).pressable { onHelp() }, contentAlignment = Alignment.Center) { HelpGlyph(p.inkSoft) }
        }
    }
}

/** 연습의 종류 이름 (작은 금빛 대문자) + 무엇을 하는지 한 줄. */
@Composable
fun PracticeTitle(kind: String, prompt: String?) {
    val p = Ink.palette
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Caps(kind, p.giltText, small = true, decorative = true)
        if (prompt != null) Text(prompt, style = Type.heading.ui(), color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/**
 * 확인 띠 (화면 아래). [ok] true 맞음 · false 틀림.
 * [primary] 는 큰 단추 (다음 · 다시 고르기), [secondary] 는 밑줄 글자 (정답 보기).
 */
@Composable
fun FeedbackBar(
    ok: Boolean, title: String, detail: String? = null,
    primary: String, onPrimary: () -> Unit,
    secondary: String? = null, onSecondary: () -> Unit = {},
) {
    val p = Ink.palette
    val c = if (ok) p.correct else p.wrong
    AnimatedVisibility(true, enter = fadeIn(tween(Tokens.Motion.fadeMs)) + slideInVertically(tween(Tokens.Motion.fadeMs)) { it / 3 }) {
        Column(
            Modifier.fillMaxWidth().background(c.copy(alpha = 0.12f)),
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(c))
            Column(Modifier.padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Box(Modifier.size(22.dp).background(c, CircleShape), contentAlignment = Alignment.Center) {
                        Text(if (ok) "✓" else "!", style = Type.small, color = p.paper)
                    }
                    Text(title, style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c)
                }
                if (detail != null) Text(detail, style = Type.small.ui(), color = p.ink)
                if (secondary != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Box(Modifier.heightIn(min = 44.dp).pressable { onSecondary() }, contentAlignment = Alignment.CenterStart) {
                        Text(secondary, style = Type.small.ui().copy(textDecoration = TextDecoration.Underline), color = p.inkSoft)
                    }
                    Box(Modifier.weight(1f)) { Primary(primary, onClick = onPrimary) }
                }
                else Primary(primary, onClick = onPrimary)
            }
        }
    }
}

/** [key] 가 바뀔 때마다 좌우로 짧게 흔들린다 (틀린 칸). 처음 그릴 때는 가만히. */
@Composable
fun Modifier.shake(key: Int): Modifier {
    val x = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (key == 0) return@LaunchedEffect
        for (d in listOf(10f, -9f, 7f, -5f, 3f, 0f)) x.animateTo(d, tween(45))
    }
    return this.graphicsLayer { translationX = x.value * density }
}

/** 틀렸을 때 손떨림 두 번 (설정에서 끄면 없음). */
fun buzzWrong(view: android.view.View, on: Boolean) {
    if (!on) return
    view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT else android.view.HapticFeedbackConstants.LONG_PRESS)
}

