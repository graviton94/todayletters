package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Tier
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 요소 하나가 아래에서 스르륵 떠오르며 나타난다. */
private fun Modifier.rise(a: Float) = graphicsLayer { alpha = a; translationY = (1f - a) * 18.dp.toPx() }

/**
 * 첫 화면: 언어 미술관 입구. 날마다 바뀌는 ‘오늘의 작품’ 한 점이 화면 가득 천천히 밝아지고,
 * 이름 · 앱 이름 · 한 줄 소개 · 작품 설명 · 입장 단추가 차례로 떠오른다. 그동안 뒤에서 편지와 낱말 카드를 불러 둔다.
 * 누르면 책장 넘기는 소리와 함께 시간의 서재가 서서히 나타난다.
 */
@Composable
fun Opening(s: AppState) {
    val ink = Color(0xFFECE7DE); val soft = Color(0xFFA29B90); val gilt = Color(0xFFC9B48C); val ground = Color(0xFF121110)
    // 오늘의 작품: 모든 시리즈의 유화 · 명작을 날마다 돌아가며
    val piece = remember {
        val pool = s.works.flatMap { w -> w.kit.collection.filter { it.tier == Tier.PAINTING || it.tier == Tier.MASTER } }
        pool.getOrNull(Math.floorMod(s.today, pool.size.coerceAtLeast(1).toLong()).toInt())
    }
    val still = s.reducedMotion
    val steps = remember { List(6) { Animatable(if (still) 1f else 0f) } }
    var ready by remember { mutableStateOf(still) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 뒤에서 미리 불러 두기 (편지 목록 · 오늘 복습할 낱말)
        s.works.forEach { s.openable(it.series.id) }; s.dueCards()
        if (still) return@LaunchedEffect
        // 그림 → LINGUA MUSEUM → 언어 미술관 → 한 줄 소개 → 작품 설명 → 단추
        steps.forEachIndexed { i, a ->
            launch {
                delay(if (i == 0) 0L else 500L + i * 260L)
                a.animateTo(1f, tween(if (i == 0) 1400 else 700, easing = FastOutSlowInEasing))
            }
        }
        delay(500L + 6 * 260L + 500L)
        ready = true
    }
    fun enter() {
        if (leaving) return
        leaving = true
        if (s.app.sound) s.narrator.play("sounds/page.m4a")
        s.nextStage()
    }
    Box(Modifier.fillMaxSize().background(ground)) {
        if (piece != null) AssetImage(piece.image, Modifier.fillMaxSize().graphicsLayer {
            alpha = steps[0].value; val k = 1.06f - 0.06f * steps[0].value; scaleX = k; scaleY = k
        }, sample = 1)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to ground.copy(alpha = 0.6f), 0.3f to ground.copy(alpha = 0.1f), 0.55f to ground.copy(alpha = 0.15f), 0.88f to ground,
        )))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("LINGUA MUSEUM", style = Type.caps, color = gilt, modifier = Modifier.rise(steps[1].value))
            Text(stringResource(R.string.app_name), style = Type.display.ui(), color = ink, modifier = Modifier.rise(steps[2].value))
            Text(stringResource(R.string.opening_tagline), style = Type.small.ui(), color = ink.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                modifier = Modifier.rise(steps[3].value))
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (piece != null) Text(
                stringResource(R.string.opening_piece, piece.title[uiLang()], piece.date) + "\n" + piece.collection,
                style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = soft, textAlign = TextAlign.Center,
                modifier = Modifier.rise(steps[4].value),
            )
            Box(
                Modifier.fillMaxWidth().heightIn(min = Tokens.Size.button).rise(if (ready) steps[5].value else 0f)
                    .border(1.5.dp, gilt).pressable(enabled = ready) { enter() },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.opening_enter), style = Type.body.ui(), color = ink) }
        }
    }
}
