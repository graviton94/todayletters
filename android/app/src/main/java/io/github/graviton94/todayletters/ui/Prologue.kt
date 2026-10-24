package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/*
 * 반 고흐 시리즈를 여닫는 세 화면 (v21 5단계).
 *   프롤로그: 아를 이전 35년을 도슨트 네 장 + 도착 한 장. 장마다 그 시절을 말하는 원문 한 줄 · 듣기 · 낱말 셋 (누르면 카드함에).
 *   맺음말: 마지막 편지 뒤, 그가 지니고 있던 편지(652) 세 줄과 생애를 맺는 글.
 *   나의 도록: 편지마다 받은 그림을 날짜 순으로. 다 읽었으면 표지 · 메달.
 * 색은 테마와 상관없이 밤의 전시실 (편지 도착 · 크게 보기와 같은 밤색).
 */
private val Night = Color(0xFF120E0A)
private val Light = Color(0xFFF0E3C4)
private val Soft = Color(0xFFB3A07A)
private val Faint = Color(0xFF8A7C62)
private val Gilt = Color(0xFFD9AE55)
private val Wall = Color(0xFF1B150F)

/** 연표 (series.json 의 prologue.timeline): [at] 번째 마디가 지금. 연표가 없으면 그리지 않는다. */
@Composable
private fun Timeline(years: List<String>, at: Int) {
    if (years.size < 2) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            years.indices.forEach { k ->
                Box(Modifier.size(if (k == at) 9.dp else 7.dp).background(if (k == at) Gilt else if (k < at) Color(0xFF8A7550) else Color(0xFF3A3127), CircleShape))
                if (k < years.lastIndex) Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF3A3127)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            years.forEach { Text(it, style = Type.caps, color = Soft) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrologueScreen(s: AppState, r: Route.Prologue) {
    val w = s.work(r.series)
    val view = s.room(r.series)
    val ui = uiLang()
    val pages = w.prologue
    var page by remember(r) { mutableStateOf(r.page.coerceIn(0, pages.size)) }
    var detail by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { val me = s.narrator.scene; onDispose { s.narrator.stop(me) } }
    BackHandler { if (page > 0) { s.narrator.stop(); page-- } else s.back() }
    // 다른 장으로 넘기면 맨 위부터
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().background(Night).statusBarsSafe().navigationBarsSafe()) {
        val last = page >= pages.size
        w.arrival?.let { a -> Timeline(a.years, a.at.getOrElse(page) { a.years.lastIndex }) }
        if (!last) {
            val d = pages[page]
            val l = d.letter
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                // 소리 없이 읽기 (받지 않음): 그림이 이 기기에 없으면 그림 칸째 뺀다
                l.plate?.takeIf { io.github.graviton94.todayletters.data.AssetFiles.has(s.ctx, "plates/${it.image}") }?.let { pl ->
                    Box(Modifier.padding(horizontal = 24.dp).padding(top = 18.dp).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF2B2117), Wall))).border(1.dp, Color(0xFF4A3B28)).padding(10.dp), contentAlignment = Alignment.Center) {
                        AssetImage("plates/${pl.image}", Modifier.fillMaxWidth().heightIn(max = 250.dp).aspectRatio(1.3f, matchHeightConstraintsFirst = false), sample = 2)
                    }
                }
                Column(Modifier.padding(horizontal = 26.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(d.caps, style = Type.caps, color = Gilt)
                    Text(d.heading[ui], style = Type.title.ui(), color = Light)
                    Text(d.sub, style = Type.small.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic), color = Soft)
                    Text(d.body[ui], style = Type.body.ui(), color = Color(0xFFD9CCB0), modifier = Modifier.padding(top = 4.dp))
                }
                l.messages.firstOrNull()?.let { m ->
                    val audio = s.narrator.path(r.series, "P", l.id, "m1_${view.learn.code}")
                    val listen = stringResource(R.string.a11y_listen_word)
                    Row(Modifier.padding(horizontal = 26.dp).padding(top = 14.dp).fillMaxWidth()) {
                        // 금빛 인용 줄: 글 높이 전체를 따라 (글 쪽에서 그려서, 줄 수가 늘어도 끝까지)
                        Column(Modifier.weight(1f).heightIn(min = 60.dp).drawBehind { drawRect(Gilt, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }
                            .padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val line = Breaks.plain(m.text[view.learn])
                            GlossText(s, r.series, "P", l, 0, spokenText(line, spokenRange(s.narrator, audio, line), Gilt), style = Type.body.of(view.learn).copy(fontStyle = FontStyle.Italic), color = Light)
                            if (view.read != view.learn) Text(Breaks.plain(m.text[view.read]), style = Type.small.of(view.read), color = Soft)
                            Text(d.source[ui], style = Type.small.ui(), color = Faint)
                        }
                        if (s.narrator.has(audio)) Box(Modifier.padding(start = 8.dp).size(48.dp).semantics { contentDescription = listen }.pressable { s.narrator.play(audio) }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size(38.dp).border(1.dp, Gilt, CircleShape), contentAlignment = Alignment.Center) { SpeakerGlyph(Gilt, 16.dp) }
                        }
                    }
                    FlowRow(Modifier.padding(horizontal = 26.dp).padding(top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        l.words.forEachIndexed { i, wd ->
                            val key = s.cardKey(r.series, "P", l.id, i)
                            val mine = s.store.hasCard(key)
                            Row(Modifier.heightIn(min = 40.dp).border(1.dp, if (mine) Gilt else Color(0xFF4A3F31)).pressable {
                                if (!s.store.hasCard(key)) { s.admit(listOf(key)); s.bump() }
                                detail = key
                            }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(wd.text[view.learn], style = Type.small.of(view.learn), color = Light)
                                Text(wd.text[view.read], style = Type.small.of(view.read), color = Soft)
                            }
                        }
                    }
                }
                l.plate?.takeIf { io.github.graviton94.todayletters.data.AssetFiles.has(s.ctx, "plates/${it.image}") }?.let { pl -> Text("${pl.title[ui]} · ${pl.date} · ${pl.collection}", style = Type.small.ui(), color = Faint, modifier = Modifier.padding(horizontal = 26.dp).padding(bottom = 12.dp)) }
            }
        } else {
            val a = w.arrival
            Column(Modifier.weight(1f).padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center) {
                Text(a?.date.orEmpty(), style = Type.display.copy(fontFamily = Faces.display), color = Gilt)
                Text(a?.route.orEmpty(), style = Type.caps, color = Soft, modifier = Modifier.padding(top = 8.dp))
                a?.let {
                    Text(it.heading[ui], style = Type.title.ui(), color = Light, modifier = Modifier.padding(top = 30.dp))
                    Text(it.body[ui], style = Type.body.ui(), color = Color(0xFFD9CCB0), modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // 이전 (첫 장 뒤로) · 건너뛰기 (마지막 장 앞까지)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > 0) Box(Modifier.heightIn(min = 48.dp).border(1.dp, Gilt).pressable { s.narrator.stop(); page-- }.padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.prologue_prev), style = Type.body.ui(), color = Gilt)
                }
                if (!last) Text(stringResource(R.string.prologue_skip), style = Type.small.ui(), color = Soft, maxLines = 1, modifier = Modifier.heightIn(min = 48.dp).pressable { s.narrator.stop(); s.finishPrologue(r.series) }.padding(vertical = 14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
                (0..pages.size).forEach { k -> Box(Modifier.size(6.dp).background(if (k == page) Gilt else Color(0xFF4A3F31), CircleShape)) }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                val label = stringResource(if (last) R.string.prologue_go else R.string.prologue_next)
                Box(Modifier.heightIn(min = 48.dp).then(if (last) Modifier.background(Gilt) else Modifier.border(1.dp, Gilt)).pressable {
                    s.narrator.stop()
                    if (last) s.finishPrologue(r.series) else page++
                }.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = Type.body.ui(), color = if (last) Night else Gilt)
                }
            }
        }
    }
    detail?.let { k -> WordDetail(s, k) { detail = null } }
}

@Composable
fun OutroScreen(s: AppState, series: String) {
    val w = s.work(series)
    val o = w.outro ?: return
    val view = s.room(series)
    val ui = uiLang()
    DisposableEffect(Unit) { val me = s.narrator.scene; onDispose { s.narrator.stop(me) } }
    BackHandler { s.back() }
    val done = w.chapters.flatMap { c -> c.letters.filter { s.progress(series, c.id, it.id).done } }
    Box(Modifier.fillMaxSize().background(Night)) {
        // 맺음 그림: 이 기기에 없으면 대표 그림 · 받아 둔 읽은 그림으로
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val outroImg = remember(o.image, io.github.graviton94.todayletters.data.AssetFiles.generation) {
            firstAvailable(ctx, listOf("plates/${o.image}", w.kit.hero) + done.mapNotNull { it.plate?.image?.let { i -> "plates/$i" } }.reversed())
        }
        AssetImage(outroImg, Modifier.fillMaxWidth().height(300.dp), sample = 2, alignment = Alignment.TopCenter)
        Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(0f to Night.copy(alpha = 0.25f), 0.7f to Night.copy(alpha = 0.85f), 1f to Night)))
        Column(Modifier.fillMaxSize().statusBarsSafe().navigationBarsSafe()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.height(170.dp))
                // 맺음말: 그가 지니고 있던 편지
                // 맺음 편지가 여럿이면 (고야: 보르도 셋) 차례대로, 둘째부터는 날짜 · 곳 한 줄을 머리로
                o.epilogues.forEachIndexed { k, e ->
                    if (k == 0) o.head?.let { Text(it[ui], style = Type.small.ui(), color = Gilt) }
                    else Text(shortDateLine(e.date, e.place), style = Type.small.ui(), color = Gilt, modifier = Modifier.padding(top = 18.dp))
                    e.messages.forEachIndexed { i, m ->
                        val audio = s.narrator.path(series, "E", e.id, "m${i + 1}_${view.learn.code}")
                        Row(Modifier.fillMaxWidth().pressable { s.narrator.play(audio) }.padding(vertical = 4.dp)) {
                            Column(Modifier.heightIn(min = 40.dp).drawBehind { drawRect(Gilt, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }
                                .padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                val line = Breaks.plain(m.text[view.learn])
                                GlossText(s, series, "E", e, i, spokenText(line, spokenRange(s.narrator, audio, line), Gilt), style = Type.body.of(view.learn).copy(fontStyle = FontStyle.Italic), color = Light)
                                if (view.read != view.learn) Text(Breaks.plain(m.text[view.read]), style = Type.small.of(view.read), color = Soft)
                            }
                        }
                    }
                    // ‘눌러서 듣기’ 는 맺음 낭독이 이 기기에 있을 때만 (소리 없이 읽기면 없음)
                    if (s.narrator.has(s.narrator.path(series, "E", e.id, "m1_${view.learn.code}"))) Text(stringResource(R.string.outro_listen), style = Type.small.ui(), color = Faint)
                    // 맺음 편지에도 답장 (턴 셋 · VIII 장 문법): 답장 사다리로, 보내면 이 화면으로 돌아온다
                    if (e.replies.isNotEmpty()) {
                        val sent = ReplyMode.CONSTELLATION in s.progress(series, "E", e.id).replied
                        val help = s.replyHelp(series)
                        val how = when (help) {
                            io.github.graviton94.todayletters.core.Ladder.Help.LINK -> R.string.mode_constellation
                            io.github.graviton94.todayletters.core.Ladder.Help.WRITE -> R.string.ladder_write_mode
                            else -> R.string.ladder_choose_mode
                        }
                        Text(stringResource(if (sent) R.string.outro_replied else R.string.outro_reply), style = Type.small.ui(), color = if (sent) Gilt else Soft, modifier = Modifier.padding(top = 8.dp))
                        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, Gilt).pressable {
                            s.narrator.stop(); s.go(Route.Play(Route.Letter(series, Route.Letter.EPILOGUE, k + 1), ReplyMode.CONSTELLATION))
                        }, contentAlignment = Alignment.Center) { Text(stringResource(R.string.room_reply, stringResource(how)), style = Type.body.ui(), color = Gilt) }
                    }
                }
                Text(o.caps, style = Type.caps, color = Gilt, modifier = Modifier.padding(top = 18.dp))
                Text(o.name, style = Type.display.copy(fontFamily = Faces.display), color = Light)
                Text(o.years, style = Type.small.copy(fontFamily = Faces.display), color = Soft)
                o.body.forEach { Text(it[ui], style = Type.body.ui(), color = Color(0xFFDCCFB3)) }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    done.mapNotNull { it.plate?.image }.take(10).forEach { AssetImage("plates/$it", Modifier.size(26.dp), sample = 8) }
                }
                Text(o.credit, style = Type.small.ui(), color = Faint, modifier = Modifier.padding(bottom = 12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).heightIn(min = 48.dp).border(1.dp, Gilt).pressable { s.back() }, contentAlignment = Alignment.Center) { Text(stringResource(R.string.outro_close), style = Type.body.ui(), color = Gilt) }
                Box(Modifier.weight(1f).heightIn(min = 48.dp).background(Gilt).pressable { s.go(Route.Catalogue(series)) }, contentAlignment = Alignment.Center) { Text(stringResource(R.string.outro_catalogue), style = Type.body.ui(), color = Night) }
            }
        }
    }
}

@Composable
fun CatalogueScreen(s: AppState, series: String) {
    val w = s.work(series)
    val ui = uiLang()
    s.version
    BackHandler { s.back() }
    val complete = s.seriesDone(series)
    val all = w.chapters.flatMapIndexed { ci, c -> c.letters.mapIndexed { li, l -> Triple(ci, li, c to l) } }
    val got = all.count { (_, _, cl) -> s.progress(series, cl.first.id, cl.second.id).done }
    Column(Modifier.fillMaxSize().background(Night).statusBarsSafe().navigationBarsSafe().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).pressable { s.back() }, contentAlignment = Alignment.Center) { Text("‹", style = Type.title, color = Light) }
            Text(stringResource(R.string.plates_title), style = Type.small.ui(), color = Soft)
        }
        // 표지: 책 한 권 + (완결이면) 메달
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.width(250.dp).background(Color(0xFF2A2017)).border(1.dp, Color(0xFF5A4630)).padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // 표지 그림: 마지막으로 읽은 편지의 그림, 이 기기에 없으면 받아 둔 다른 읽은 그림 · 대표 그림 순으로
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val cover = remember(got, io.github.graviton94.todayletters.data.AssetFiles.generation) {
                    val read = all.filter { (_, _, cl) -> s.progress(series, cl.first.id, cl.second.id).done }.mapNotNull { it.third.second.plate?.image?.let { i -> "plates/$i" } }.reversed()
                    firstAvailable(ctx, read + w.kit.hero)
                }
                Box(Modifier.size(width = 186.dp, height = 140.dp).background(Color(0xFFE9DCC0)).padding(6.dp)) { AssetImage(cover, Modifier.fillMaxSize(), gray = !complete, sample = 4) }
                Text("CATALOGUE", style = Type.caps, color = Gilt, modifier = Modifier.padding(top = 22.dp))
                Text(stringResource(R.string.catalogue_name, w.fullName.substringAfter(' ')), style = Type.title.copy(fontFamily = Faces.display), color = Light, textAlign = TextAlign.Center)
                Text(stringResource(R.string.catalogue_sub, w.years), style = Type.small.ui(), color = Soft)
            }
            // 완결이면 그 작품의 완주 메달 (메달 진열장의 것)
            if (complete) s.medalBook.medals.firstOrNull { it.series == series }?.let { MedalImage(it, true, 72.dp) }
            Text(stringResource(if (complete) R.string.catalogue_complete else R.string.catalogue_progress, got, all.size), style = Type.body.ui(), color = Light, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, start = 30.dp, end = 30.dp))
        }
        // 펼친 면: 장마다, 날짜 순
        w.chapters.forEachIndexed { ci, c ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 20.dp, bottom = 8.dp)) {
                Text("${roman(ci + 1)} · ${c.title[w.series.original].uppercase()}", style = Type.caps, color = Gilt, modifier = Modifier.weight(1f))
                Text(c.title[ui], style = Type.small.ui(), color = Soft)
            }
            c.letters.chunked(4).forEachIndexed { row, chunk ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEachIndexed { k, l ->
                        val li = row * 4 + k
                        val done = s.progress(series, c.id, l.id).done
                        Column(Modifier.weight(1f).background(if (done) Color(0xFF241C14) else Color.Transparent).border(1.dp, Color(0xFF3A3127))
                            .then(if (done && l.plate != null) Modifier.pressable { s.go(Route.Artwork(series, li + 1, Route.Letter(series, ci + 1, li + 1))) } else Modifier).padding(5.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (done && l.plate != null) AssetImage("plates/${l.plate!!.image}", Modifier.fillMaxSize(), sample = 8)
                                else Text(roman(li + 1), style = Type.heading.copy(fontFamily = Faces.display), color = Color(0xFF4A3F31))
                            }
                            Text(if (done) l.plate?.title?.get(ui).orEmpty() else "", style = Type.small.ui().copy(fontSize = Tokens.Text.caps, lineHeight = Tokens.Text.caps * 1.3f), color = Soft, maxLines = 2, minLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    repeat(4 - chunk.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        Box(Modifier.height(24.dp))
    }
}
