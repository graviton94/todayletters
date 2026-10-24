package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.core.FlightMath
import kotlin.math.roundToInt

/*
 * 받은 것이 다음 화면의 제자리로 옮겨 가기 (시안 rw2 Between screens ①~④): 걸음 그림 → 다음 걸음 머리의 작은 그림,
 * 편지 완료의 그림 → 오늘 맨 위 그림 칸, 메달 → 진열장 칸, 서재 카드 → 편지 도착 맨 위.
 * 보상은 화면 위에 덮인 층이고 다음 화면은 길 (Route) 이라, 한 SharedTransitionLayout 안에 둘 수 없다 → 잰 자리로 그리는 층 하나.
 * 처음 자리 ([Flight.from], 창 좌표) 는 누른 쪽이 재서 넘기고, 닿을 자리는 받는 쪽이 [flightSlot] 으로 알린다.
 * 날아가는 동안 받는 자리는 비워 두고 (같은 그림이 둘 보이지 않게), 다 닿으면 그 자리의 그림이 그대로 이어진다.
 * 움직임 줄이기면 날지 않는다 (바로 제자리, 마지막 모습).
 */

/** 이 층이 그리는 창 (대화창 안의 자리는 그 대화창의 층이 그린다: 편지 도착). */
val LocalFlightHost = staticCompositionLocalOf { "main" }

@Stable
class Flights {
    /** 옮겨 가는 것 하나: 그림 [image] (assets 경로), 처음 자리 [from], 닿을 자리 이름 [to], 모양. [n] 은 날릴 때마다 하나씩. */
    data class Flight(val image: String, val from: Rect, val to: String, val n: Int,
                      val round: Boolean = false, val cornerFrom: Float = 0f, val cornerTo: Float = 0f,
                      /** 가는 테두리 + 그림자 (걸음 그림 · 머리 그림). */ val framed: Boolean = false,
                      /** 그림 자르기 자리 (위에서 몇 할). */ val alignY: Float = 0f,
                      /** 누른 쪽의 자리 이름 (날아가는 동안 그 자리를 비운다). */ val source: String = "",
                      /** 닿을 자리의 그림이 다른 그림이면 (서재 표지 → 편지 그림): 자리를 비우지 않고, 끝 3할 동안 옅어지며 그 그림에 넘겨준다. */ val handOff: Boolean = false)
    data class Slot(val rect: Rect, val host: String)

    var now by mutableStateOf<Flight?>(null)
    val slots = mutableStateMapOf<String, Slot>()
    private var count = 0

    /** 날리기. 움직임 줄이기면 날리지 않는다 (받는 자리가 처음부터 그 그림). */
    fun launch(image: String, from: Rect?, to: String, still: Boolean, round: Boolean = false, cornerFrom: Float = 0f, cornerTo: Float = 0f,
               framed: Boolean = false, alignY: Float = 0f, source: String = "", handOff: Boolean = false) {
        if (still || from == null || image.isBlank()) { now = null; return }
        now = Flight(image, from, to, ++count, round, cornerFrom, cornerTo, framed, alignY, source, handOff)
    }

    /** [key] 자리로 날아오는 중 (그 자리는 비워 둔다). */
    fun incoming(key: String) = now?.let { it.to == key && !it.handOff } == true
    /** [key] 자리에서 떠나는 중 (누른 쪽의 그림을 비운다). */
    fun leaving(key: String) = now?.source == key && key.isNotEmpty()
}

/** 닿을 자리: 그 자리의 창 좌표를 알리고, 날아오는 동안은 비워 둔다. */
fun Modifier.flightSlot(s: AppState, key: String): Modifier = composed {
    val host = LocalFlightHost.current
    androidx.compose.runtime.DisposableEffect(key) { onDispose { if (s.flights.slots[key]?.host == host) s.flights.slots.remove(key) } }
    this.onGloballyPositioned { s.flights.slots[key] = Flights.Slot(it.boundsInWindow(), host) }
        .graphicsLayer { alpha = if (s.flights.incoming(key)) 0f else 1f }
}

/** 처음 자리를 재 두는 그릇: [modifier] 를 그림에 달고, 누를 때 [rect] 를 넘긴다. 떠나는 동안은 [source] 자리를 비운다. */
@Stable
class Bounds { var rect by mutableStateOf<Rect?>(null) }

@Composable
fun rememberBounds() = remember { Bounds() }

fun Modifier.measured(b: Bounds): Modifier = onGloballyPositioned { b.rect = it.boundsInWindow() }

/** 떠나는 자리 (날아가는 동안 비움). */
fun Modifier.flightSource(s: AppState, key: String): Modifier = graphicsLayer { alpha = if (s.flights.leaving(key)) 0f else 1f }

/**
 * 날아가는 그림을 그리는 층 (화면 맨 위). [LocalFlightHost] 가 같은 창의 자리로 가는 것만 그리고, 닿을 자리가 아직 없으면
 * 바깥 창 ("main") 이 처음 자리에 그대로 둔다 (다음 화면이 그려지기를 기다린다). 다 닿으면 [Flights.now] 를 비운다.
 */
@Composable
fun FlightLayer(s: AppState) {
    val f = s.flights.now ?: return
    val host = LocalFlightHost.current
    val slot = s.flights.slots[f.to]
    // 이 창의 몫이 아니면 그리지 않는다 (자리가 다른 창에 있거나, 자리가 아직 없는데 이 층이 바깥 창이 아님)
    if (slot != null && slot.host != host) return
    if (slot == null && host != "main") return
    var origin by remember { mutableStateOf(Offset.Zero) }
    var t by remember(f.n) { mutableFloatStateOf(0f) }
    LaunchedEffect(f.n) {
        var start = -1L; var landed: Long? = null
        while (true) {
            val now = androidx.compose.runtime.withFrameMillis { it }
            if (start < 0) start = now
            val el = now - start
            if (landed == null && s.flights.slots[f.to] != null) landed = el
            t = landed?.let { FlightMath.progress(el - it, s.reducedMotion) } ?: 0f
            if (FlightMath.over(el, landed, s.reducedMotion)) break
        }
        if (s.flights.now?.n == f.n) s.flights.now = null
    }
    val d = LocalDensity.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val to = slot?.rect ?: f.from
        val b = FlightMath.lerp(box(f.from), box(to), t)
        val w = with(d) { b.w.toDp() }; val h = with(d) { b.h.toDp() }
        val corner = with(d) { (f.cornerFrom + (f.cornerTo - f.cornerFrom) * t).toDp() }
        val shape = if (f.round) CircleShape else RoundedCornerShape(corner)
        val fade = if (f.handOff) 1f - ((t - 0.7f) / 0.3f).coerceIn(0f, 1f) else 1f
        Box(Modifier.offset { IntOffset((b.x - origin.x).roundToInt(), (b.y - origin.y).roundToInt()) }.size(w, h).graphicsLayer { alpha = fade }
            .then(if (f.framed) Modifier.shadow(10.dp, shape) else Modifier)
            .clip(shape)
            .then(if (f.framed) Modifier.border(1.dp, Color(0x2EEEE7DA), shape) else Modifier)) {
            AssetImage(f.image, Modifier.fillMaxSize(), sample = 2, alignment = androidx.compose.ui.BiasAlignment(0f, f.alignY * 2 - 1))
        }
    }
}

private fun box(r: Rect) = FlightMath.Box(r.left, r.top, r.width, r.height)
