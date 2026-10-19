package io.github.graviton94.todayletters.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.SayLike
import io.github.graviton94.todayletters.core.VocabSet
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import java.util.concurrent.ConcurrentHashMap

// ── 비슷하게 읽으면 (core [SayLike], data/<작품>/pron[_en|_ko].json — scripts/pron.py 가 espeak-ng 로 만든 자동 · 어림 힌트) ──────────────────

private val sayFiles = ConcurrentHashMap<String, Map<String, String>>()
private val sayLoading: MutableSet<String> = ConcurrentHashMap.newKeySet()

/** 소리 힌트 파일 (assets letters/<작품>/pron[_en|_ko].json). 없으면 빈 것. */
fun sayLikeOf(ctx: Context, series: String, deck: String): Map<String, String> = sayFiles.getOrPut("$series/$deck") {
    SayLike.parse(runCatching { ctx.assets.open("letters/$series/${VocabSet.file("pron", deck)}").bufferedReader().use { it.readText() } }.getOrNull())
}

/**
 * [text] (지금 배우는 말) 의 소리 힌트: 한국어 앱 글자 + 프랑스어 · 독일어 · 영어를 배우면 한글, 영어 앱 글자 + 한국어를 배우면 로마자.
 * 그 밖이면 · 힌트가 없으면 null.
 */
fun AppState.sayLike(series: String, text: String): String? {
    val w = works.firstOrNull { it.series.id == series } ?: return null
    val learn = room(series).learn
    if (!SayLike.shows(app.ui, learn)) return null
    val deck = VocabSet.deckFor(learn, w.series.original)
    // 파일(작품 · 말마다 수백 KB)은 처음 한 번 뒤에서 읽고, 다 읽으면 화면을 다시 그린다 (그리는 중에 읽으면 버벅임)
    val m = sayFiles["$series/$deck"] ?: run {
        if (sayLoading.add("$series/$deck")) Thread {
            sayLikeOf(ctx, series, deck)
            android.os.Handler(android.os.Looper.getMainLooper()).post { bump() }
        }.start()
        return null
    }
    return SayLike.lookup(m, text)
}

/** ‘비슷하게 읽으면 · 위, 싸 바’ 한 줄 (두 번째로 안 된 뒤에만, 그 뒤로 늘). */
@Composable
fun SayLikeLine(t: ShadowTake) {
    if (!t.showsSayLike) return
    val p = Ink.palette
    val hint = t.sayLike.orEmpty()
    val hangul = hint.any { it in '가'..'힣' }
    Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2, Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.say_like), style = Type.small.ui(), color = p.giltText)
        Text(hint, style = Type.body.of(if (hangul) Lang.KO else Lang.EN), color = p.ink)
    }
}
