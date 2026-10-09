package io.github.graviton94.todayletters.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import io.github.graviton94.todayletters.design.uiHangul
import kotlinx.coroutines.delay

private val envInk = Color(0xFF2A2118)
private val envSoft = Color(0xFF6E6150)
private val postBlue = Color(0xFF3D4C6E)

/** 밀랍 색: 밝은 곳 · 가운데 · 그늘. */
private class Wax(val hi: Color, val mid: Color, val lo: Color, val ink: Color)
private val waxGreen = Wax(Color(0xFF4E7A5E), Color(0xFF2F5A40), Color(0xFF1C3A28), Color(0xFFDCE3D2))
private val waxGold = Wax(Color(0xFFE2C277), Color(0xFFB88E3A), Color(0xFF7D5C1E), envInk)

/**
 * 오프닝: 앱을 켤 때마다. 앱 이름과 성격, 앞으로 올 발신인들의 봉투. 글자는 시스템 언어(한국어 · 영어)로.
 * 불러오기는 뒤에서 끝나고, 준비되면 "눌러서 열기"가 나타난다. 어디를 눌러도 들어간다.
 */
@Composable
fun Opening(s: AppState) {
    val p = Ink.palette
    val haptic = LocalHapticFeedback.current
    var ready by remember { mutableStateOf(false) }
    // 서서히 나타나기: 램프가 켜지듯 바탕 → 이름 → 소개 → 봉투가 한 장씩 → "눌러서 열기"
    val t = remember { androidx.compose.animation.core.Animatable(if (s.reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(3200, easing = androidx.compose.animation.core.LinearEasing))
    }
    LaunchedEffect(Unit) { delay(Tokens.Motion.loadingMinMs.toLong()); ready = true }
    fun win(a: Float, b: Float) = androidx.compose.animation.core.FastOutSlowInEasing.transform(((t.value - a) / (b - a)).coerceIn(0f, 1f))
    val ko = uiHangul()
    Box(
        Modifier.fillMaxSize().background(p.paper).graphicsLayer { alpha = win(0f, 0.22f) }.desk(p.paper, p.lamp, 0.55f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = ready && t.value > 0.6f) {
                if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                s.nextStage()
            },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 56.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.open_caps), style = Type.caps, color = p.giltText, modifier = Modifier.rise(win(0.1f, 0.3f)))
            Spacer(Modifier.height(Tokens.Space.s3))
            Text(
                stringResource(R.string.app_name),
                style = if (ko) Type.display.ui().copy(fontSize = Tokens.Text.brand, lineHeight = Tokens.Text.brand * 1.15f)
                else Type.display.copy(fontSize = Tokens.Text.brand * 1.15f, lineHeight = Tokens.Text.brand * 1.25f),
                color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.rise(win(0.16f, 0.4f)),
            )
            Spacer(Modifier.height(Tokens.Space.s3))
            Text(
                stringResource(R.string.open_tagline), style = Type.body.ui(), color = p.inkSoft,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = Tokens.Space.s5).rise(win(0.26f, 0.5f)),
            )
            Spacer(Modifier.height(Tokens.Space.s5))
            Pile(s.reducedMotion) { i -> win(0.42f + i * 0.08f, 0.6f + i * 0.08f) }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(ready && t.value > 0.86f, enter = fadeIn(tween(Tokens.Motion.introFadeMs))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PulseRing(s.reducedMotion)
                    Spacer(Modifier.height(Tokens.Space.s2))
                    Text(stringResource(R.string.open_cta), style = Type.small.ui(), color = p.inkSoft)
                }
            }
        }
    }
}

/** 아래에서 살짝 떠오르며 나타나기. */
private fun Modifier.rise(a: Float) = graphicsLayer { alpha = a; translationY = (1f - a) * 14.dp.toPx() }

/** 봉투 더미: 390×360 기준으로 그리고, 좁은 화면에서는 같은 비율로 줄인다. */
@Composable
private fun Pile(still: Boolean, appear: (Int) -> Float) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val k = (maxWidth / 390.dp).coerceAtMost(1f)
        Box(Modifier.size(390.dp * k, 360.dp * k), contentAlignment = Alignment.TopStart) {
            Box(Modifier.requiredSize(390.dp, 360.dp).scale(k)) {
                // 바쇼: 접은 화지, 세로 글씨
                Envelope(18.dp, 16.dp, 200.dp, 128.dp, -10f, Color(0xFFEAE0C9), appear = appear(0)) {
                    Text("1689", style = Type.capsSm, color = envSoft, modifier = Modifier.offset(18.dp, 14.dp))
                    Column(Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 22.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        "芭蕉 深川".forEach { c -> Text(if (c == ' ') "" else c.toString(), style = Type.body.copy(fontSize = 15.sp), color = envInk) }
                    }
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 14.dp).size(26.dp).border(1.5.dp, Color(0xFFA2382A)), contentAlignment = Alignment.Center) {
                        Text("印", style = Type.small.copy(fontSize = 12.sp), color = Color(0xFFA2382A))
                    }
                }
                // 모차르트: 뒷면, 초록 밀랍
                Envelope(168.dp, 4.dp, 206.dp, 132.dp, 8f, back = true, appear = appear(1)) { Box(Modifier.offset(83.dp, 62.dp)) { WaxSeal(waxGreen, "", 40.dp) } }
                // 오스틴
                Envelope(2.dp, 132.dp, 214.dp, 132.dp, 5f, appear = appear(2)) {
                    Text("Miss Austen\nSteventon", style = Type.label.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 19.sp), color = envInk, modifier = Modifier.offset(18.dp, 52.dp))
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 18.dp)) { Mark("OVERTON", "JA 96", null, 46.dp) }
                }
                // 나폴레옹: 뒷면, 금빛 밀랍
                Envelope(196.dp, 140.dp, 190.dp, 122.dp, -7f, back = true, appear = appear(3)) {
                    Box(Modifier.offset(76.dp, 57.dp)) { WaxSeal(waxGold, "N", 38.dp) }
                    Text("ARMÉE D'ITALIE", style = Type.capsSm.copy(fontSize = 9.sp), color = envSoft, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 10.dp))
                }
                // 빈센트: 맨 위, 앞면 (주소 · 우표 · 소인)
                val lift = if (still) 0f else rememberInfiniteTransition(label = "lift").animateFloat(0f, -5f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "y").value
                Envelope(46.dp, 168.dp, 296.dp, 178.dp, -2f, lift = lift, appear = appear(4)) {
                    Text("V.", style = Type.capsSm.copy(fontSize = 9.sp), color = Color(0xFF8E2A22), modifier = Modifier.offset(16.dp, 16.dp))
                    Stamp(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp))
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 70.dp)) { Mark("ARLES", "21 FÉVR", "1888", 58.dp) }
                    Text(
                        "Monsieur Théo van Gogh\n      54, rue Lepic\n            Paris",
                        style = Type.label.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp),
                        color = envInk, modifier = Modifier.offset(38.dp, 84.dp).clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

/** 봉투 한 장. [back] 이면 뒷면 덮개의 V 접합선을 그린다. 밀랍은 부르는 쪽이 그 꼭짓점(가로 50%, 세로 62%)에 놓는다. */
@Composable
private fun Envelope(
    x: Dp, y: Dp, w: Dp, h: Dp, tilt: Float, paper: Color = Color(0xFFF7F0E1), back: Boolean = false, lift: Float = 0f, appear: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = Ink.palette
    Box(
        Modifier.offset(x, y).size(w, h)
            .graphicsLayer {
                // 봉투가 위에서 살짝 기울어진 채 내려와 놓인다
                alpha = appear
                rotationZ = tilt + (1f - appear) * tilt.coerceIn(-1f, 1f) * 6f
                translationY = lift * density - (1f - appear) * 36.dp.toPx()
                shadowElevation = 10.dp.toPx(); ambientShadowColor = p.shadow; spotShadowColor = p.shadow
            }
            .paperGrain(paper, Color(0x0B5A3C14), Color(0x38785523))
            .drawBehind {
                if (back) {
                    val seam = Color(0x385A3C14)
                    val v = Offset(size.width / 2, size.height * 0.62f)
                    drawLine(seam, Offset(0f, 0f), v, 1.2f)
                    drawLine(seam, Offset(size.width, 0f), v, 1.2f)
                }
            },
        content = content,
    )
}

/** 밀랍 봉인: 고르지 않은 둥근 가장자리, 위쪽에서 비치는 빛. */
@Composable
private fun WaxSeal(w: Wax, letter: String, d: Dp) {
    Box(
        Modifier.size(d).graphicsLayer { shadowElevation = 3.dp.toPx(); shape = CircleShape; clip = true }
            .drawBehind {
                val r = size.minDimension
                drawCircle(Brush.radialGradient(listOf(w.hi, w.mid, w.lo), center = Offset(r * 0.34f, r * 0.3f), radius = r * 0.75f))
                drawCircle(Color(0x29000000), radius = r / 2 - 2.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (letter.isNotEmpty()) Text(letter, style = Type.signature.copy(fontSize = (d.value * 0.45f).sp), color = w.ink)
    }
}

/** 우체국 소인: 두 겹 원. */
@Composable
private fun Mark(place: String, day: String, year: String?, size: Dp) {
    Box(
        Modifier.size(size).rotate(-12f).alpha(0.82f)
            .border(1.4.dp, postBlue, CircleShape).padding(3.dp).border(1.dp, postBlue, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(place, style = Type.capsSm.copy(fontSize = (size.value * 0.13f).sp, letterSpacing = 0.5.sp), color = postBlue)
            Text(day, style = Type.capsSm.copy(fontSize = (size.value * 0.155f).sp, letterSpacing = 0.5.sp), color = postBlue)
            if (year != null) Text(year, style = Type.capsSm.copy(fontSize = (size.value * 0.13f).sp, letterSpacing = 0.5.sp), color = postBlue)
        }
    }
}

/** 우표: 프랑스 15상팀 (구멍 난 가장자리는 점선으로). */
@Composable
private fun Stamp(modifier: Modifier) {
    Box(modifier.size(40.dp, 48.dp).background(Color(0xFF6E89A8)).drawBehind {
        drawRect(Color(0xFFF7F0E1), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))))
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("POSTES", style = Type.capsSm.copy(fontSize = 7.sp, letterSpacing = 0.3.sp), color = Color(0xFFEAF0F5))
            Text("15", style = Type.heading.copy(fontSize = 15.sp), color = Color(0xFFEAF0F5))
            Text("RF", style = Type.capsSm.copy(fontSize = 7.sp), color = Color(0xFFEAF0F5))
        }
    }
}

/** 눌러서 열기: 번지는 고리 (움직임 줄이기면 멈춤). */
@Composable
private fun PulseRing(still: Boolean) {
    val p = Ink.palette
    val t = rememberInfiniteTransition(label = "ring")
    val k by t.animateFloat(0.7f, 1.6f, infiniteRepeatable(tween(Tokens.Motion.tapPulseMs)), label = "k")
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        if (!still) Box(Modifier.size(64.dp).scale(k).alpha(((1.6f - k) / 0.9f).coerceIn(0f, 0.7f)).border(1.dp, p.giltText, CircleShape))
        Box(Modifier.size(44.dp).border(1.dp, p.giltText, CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(18.dp)) {
                val w = size.width
                val st = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
                drawRect(p.giltText, Offset(w * 0.08f, w * 0.2f), androidx.compose.ui.geometry.Size(w * 0.84f, w * 0.6f), style = st)
                drawLine(p.giltText, Offset(w * 0.08f, w * 0.24f), Offset(w * 0.5f, w * 0.55f), 1.5.dp.toPx())
                drawLine(p.giltText, Offset(w * 0.92f, w * 0.24f), Offset(w * 0.5f, w * 0.55f), 1.5.dp.toPx())
            }
        }
    }
}

/**
 * 오늘의 봉투 (그날 처음 열 때): 고른 작품의 편지가 도착했다는 한 장면. 누르면 편지함으로.
 * 기다리는 편지가 없으면 바로 넘어간다.
 */
@Composable
fun Today(s: AppState) {
    val p = Ink.palette
    val work = s.works.firstOrNull()
    val waiting = work?.let { s.waiting(it.series.id) } ?: 0
    LaunchedEffect(waiting) { if (waiting == 0) s.nextStage() }
    if (work == null || waiting == 0) return
    val next = s.openable(work.series.id).firstOrNull { (c, l) -> !s.progress(work.series.id, c, l.id).done }?.second
    Box(
        Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.45f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.nextStage() },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s7),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Portrait(work.portrait, work.fullName, 72.dp)
            Spacer(Modifier.height(Tokens.Space.s4))
            Text(stringResource(R.string.today_title, work.name[uiLang()]), style = Type.title.ui(), color = p.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Tokens.Space.s2))
            Text(stringResource(R.string.intro_new, waiting), style = Type.small.ui(), color = p.inkSoft)
            Spacer(Modifier.height(Tokens.Space.s6))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val k = (maxWidth / 300.dp).coerceAtMost(1f)
                Box(Modifier.size(300.dp * k, 186.dp * k)) {
                    Box(Modifier.requiredSize(300.dp, 186.dp).scale(k)) {
                        Envelope(2.dp, 4.dp, 296.dp, 178.dp, -1.5f) {
                            Stamp(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp))
                            Box(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 70.dp)) {
                                Mark(next?.place?.uppercase() ?: "ARLES", next?.date?.let { dayOf(it).uppercase() } ?: "", next?.date?.take(4), 58.dp)
                            }
                            Text(
                                "Monsieur Théo van Gogh\n      54, rue Lepic\n            Paris",
                                style = Type.label.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp),
                                color = envInk, modifier = Modifier.offset(38.dp, 84.dp).clearAndSetSemantics { },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            PulseRing(s.reducedMotion)
            Spacer(Modifier.height(Tokens.Space.s2))
            Text(stringResource(R.string.open_cta), style = Type.small.ui(), color = p.inkSoft)
        }
    }
}
