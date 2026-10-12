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
 * 첫 화면: 언어 미술관 입구 (앱을 켤 때마다). 내가 모은 그림들이 화면 가득 천천히 바뀌며 지나가고 (모을수록 입구가 풍성해지는 보상),
 * 아직 모은 것이 적으면 날마다 바뀌는 ‘오늘의 작품’ 한 점. 그 위로
 * 이름 · 앱 이름 · 한 줄 소개 · 작품 설명 · 입장 단추가 차례로 떠오른다. 그동안 뒤에서 편지와 낱말 카드를 불러 둔다.
 * 레벨이 5 를 넘으면 한 줄 소개 아래에 그동안 연 입구 장식 (가장 새 것) 의 이름이 작은 문양과 함께 조용히.
 * 누르면 책장 넘기는 소리와 함께 시간의 서재가 서서히 나타난다.
 */
@Composable
fun Opening(s: AppState) {
    val ink = Color(0xFFECE7DE); val soft = Color(0xFFA29B90); val gilt = Color(0xFFC9B48C); val ground = Color(0xFF121110)
    // 모은 그림 (편지 그림 · 소장품, 기기에 있는 것) 이 셋 넘으면 그것들을 차례로, 아니면 오늘의 작품 (유화 · 명작을 날마다 돌아가며)
    val pieces = remember {
        val owned = s.works.flatMap { w -> s.gallery(w.series.id).filter { s.owns(w.series.id, it) && s.available(it) } }
        if (owned.size >= 3) owned.shuffled(java.util.Random(s.today)).take(8)
        else {
            val pool = s.works.flatMap { w -> w.kit.collection.filter { (it.tier == Tier.PAINTING || it.tier == Tier.MASTER) && s.available(it) } }
            listOfNotNull(pool.getOrNull(Math.floorMod(s.today, pool.size.coerceAtLeast(1).toLong()).toInt()))
        }
    }
    // 레벨로 열린 입구 장식 (가장 새 것 하나)
    val ui = uiLang()
    val deco = remember(ui) { io.github.graviton94.todayletters.core.Levels.decoration(s.level.level)?.let { io.github.graviton94.todayletters.core.Levels.decorationName(s.medalBook.levels, it, ui) } }
    var shownAt by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val piece = pieces.getOrNull(shownAt % pieces.size.coerceAtLeast(1))
    val still = s.reducedMotion
    // 천천히 바뀌는 전시: 한 점씩 4초 남짓 (움직임 줄이기면 한 점에 머문다)
    LaunchedEffect(pieces) { if (!still && pieces.size > 1) while (true) { delay(4200); shownAt++ } }
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
        androidx.compose.animation.Crossfade(piece, animationSpec = tween(1600), label = "gallery") { pc ->
            if (pc != null) AssetImage(pc.image, Modifier.fillMaxSize().graphicsLayer {
                alpha = steps[0].value; val k = 1.06f - 0.06f * steps[0].value; scaleX = k; scaleY = k
            }, sample = 1)
        }
        // 위는 이름과 한 줄 소개가 그림 위에서도 읽히게 더 어둡게 (F29 대비)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to ground.copy(alpha = 0.78f), 0.32f to ground.copy(alpha = 0.3f), 0.55f to ground.copy(alpha = 0.15f), 0.88f to ground,
        )))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("LINGUA MUSEUM", style = Type.caps, color = gilt, modifier = Modifier.rise(steps[1].value))
            Text(stringResource(R.string.app_name), style = Type.display.ui(), color = ink, modifier = Modifier.rise(steps[2].value))
            Text(stringResource(R.string.opening_tagline), style = Type.small.ui(), color = ink, textAlign = TextAlign.Center,
                modifier = Modifier.rise(steps[3].value))
            // 입구 장식 (5 레벨마다 하나): 이름과 작은 문양만, 조용히
            deco?.let { name ->
                // 밝은 그림 위에서도 읽히게 옅은 어둠 한 겹 (글자 둘레만)
                Column(Modifier.padding(top = 6.dp).rise(steps[3].value * 0.85f).background(ground.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Ornament(gilt.copy(alpha = 0.8f))
                    Text(stringResource(R.string.opening_deco, name), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = gilt)
                }
            }
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
