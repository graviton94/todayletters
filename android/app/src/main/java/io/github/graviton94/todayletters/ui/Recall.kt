package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.Recall
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.ShadowLines
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

// ── 어제 말 떠올리기 (core [Recall]) ──────────────────
// 이번 주 기록 ([io.github.graviton94.todayletters.data.Store.logDay]): "talk" = 회화를 마친 날 · 편지 열쇠 (AppState.replyAndContinue),
// "recall" = 오늘 떠올리기를 마친 진도 이름 (작품 · 작품@en …). 마치면 오늘 화면의 카드가 사라진다.

/** 떠올릴 줄 하나: 그 편지의 회화 줄 번호 [line]. */
data class RecallItem(val chapter: String, val letter: Letter, val line: Int)

/**
 * 오늘 떠올릴 줄: 지난 공부한 날 (이 작품에서 편지를 마쳤거나 회화를 한 날, 오늘 앞) 에 회화를 했으면 그 편지 (그날 여럿이면 가장 뒤 편지) 의
 * 내 줄 앞 [Recall.MAX] 개. 오늘 이미 했거나, 그날 회화가 없었으면 빈 목록.
 */
fun AppState.recallItems(series: String): List<RecallItem> {
    @Suppress("UNUSED_EXPRESSION") version
    val sp = space(series)
    if (store.dayLog("recall").any { it.first == today && it.second == sp }) return emptyList()
    val talks = store.dayLog("talk").filter { it.second.startsWith("$sp:") }
    if (talks.isEmpty()) return emptyList()
    val days = talks.map { it.first } + store.dayLog("letters").filter { it.second.startsWith("$sp:") }.map { it.first }
    val prev = Recall.previousDay(days, today) ?: return emptyList()
    val keys = talks.filter { it.first == prev }.map { it.second }.toSet()
    val (c, l) = work(series).chapters.flatMap { ch -> ch.letters.map { ch.id to it } }.lastOrNull { (c, l) -> key(series, c, l.id) in keys } ?: return emptyList()
    return ShadowLines.talkMe(l.talk, room(series).learn).take(Recall.MAX).map { RecallItem(c, l, it) }
}

/** 오늘 떠올리기를 마쳤다 (카드가 사라진다). */
fun AppState.recallDone(series: String) { store.logDay("recall", today, space(series)); bump() }

/** 오늘 화면 맨 위 카드: ‘어제 말 떠올리기 · 어제 대화에서 내가 한 말 N개’ (누르면 퀴즈). */
@Composable
fun RecallCard(s: AppState, id: String) {
    val p = Ink.palette
    val items = remember(id, s.version, s.today) { s.recallItems(id) }
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(p.leaf).border(1.dp, p.gilt).pressable { s.go(Route.RecallQuiz(id)) }
        .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Caps(stringResource(R.string.recall_title), p.giltText, small = true)
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recall_card_d, items.size), style = Type.body.ui(), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.recall_go), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.giltText, maxLines = 1)
        }
    }
}

@Composable
fun RecallScreen(s: AppState, r: Route.RecallQuiz) {
    val items = remember(r.series) { s.recallItems(r.series) }
    if (items.isEmpty()) { LaunchedEffect(Unit) { s.back() }; return }
    RecallPane(s, r.series, items) { s.recallDone(r.series); s.back() }
}

/**
 * 떠올리기 퀴즈 (소리 없이): 줄의 뜻 (앱 글자 언어) → 2~4 조각 + 틀린 조각 하나를 차례대로 누르기.
 * 맞으면 줄 + 회화 소리 (기기에 있으면) + ‘소리 내어 말해 보기’ (따라 말하기 칸, 또박또박 검사), 틀리면 답을 보여 주고 그 줄을 끝에 한 번 더.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecallPane(s: AppState, series: String, items: List<RecallItem>, onFinish: () -> Unit) {
    val p = Ink.palette
    val learn = s.room(series).learn
    val ui = uiLang()
    var queue by remember { mutableStateOf(items.indices.toList()) }
    var again by remember { mutableStateOf(emptySet<Int>()) }
    var done by remember { mutableIntStateOf(0) }
    // 틀린 조각을 뽑을 다른 내 줄 (같은 작품의 회화 내 줄, 배우는 말)
    val pool = remember(series, learn) {
        s.work(series).chapters.flatMap { it.letters }.flatMap { l -> ShadowLines.talkMe(l.talk, learn).map { Breaks.plain(l.talk!!.lines[it].text[learn]) } }.distinct()
    }
    DisposableEffect(Unit) { val me = s.narrator.scene; onDispose { s.narrator.stop(me); s.pcm.onFrame = null; s.pcm.stop(); s.recorder.release() } }
    val item = queue.firstOrNull()?.let { items[it] } ?: return
    val line = item.letter.talk!!.lines[item.line]
    val text = Breaks.plain(line.text[learn]).trim()
    var st by remember(done) { mutableStateOf(Recall.start(text, pool.filter { it.trim() != text }, seed = text.hashCode() + done)) }
    var speak by remember(done) { mutableStateOf(false) }
    val sayFile = remember(done) { java.io.File(s.ctx.cacheDir, "speak/recall.wav").also { it.delete() } }
    val audio = remember(item, learn) {
        item.letter.talk?.audioName(item.line, learn)?.let { s.narrator.path(series, item.chapter, item.letter.id, it) }?.takeIf { s.narrator.has(it) }
    }
    var leaving by remember { mutableStateOf(false) }
    // 말하기 칸 (맞힌 뒤 ‘소리 내어 말해 보기’): 말하기 일곱 곳 공통의 판 · 아래 칸 ([SpeakPanel]). 맞으면 그 줄의 소리를 한 번 들은 셈이라 먼저 듣기는 그 소리로
    val take = if (st.right && speak) rememberShadowTake(s, audio, 0, Int.MAX_VALUE, sayFile, step = true, key = Triple(series, done, item.line),
        sayLike = s.sayLike(series, text), text = text, lang = learn) else null
    // 말하기 칸이 열리면 먼저 듣기 (끝까지 들으면 녹음이 열린다)
    LaunchedEffect(take) { if (take != null && !take.have) { s.uncovered(); take.original(s) } }
    // 맞으면 그 줄의 회화 소리를 한 번 (이 줄의 소리로, 덮개가 걷힌 뒤)
    LaunchedEffect(st.right, done) {
        val me = s.narrator.step("recall" to done)
        if (st.right) { s.uncovered(); audio?.let { s.narrator.play(it, owner = me) } }
    }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(done, done + queue.size, onClose = { leaving = true })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Caps(stringResource(R.string.recall_title), p.giltText, small = true)
            Text(stringResource(R.string.recall_tap), style = Type.body.ui(), color = p.inkSoft)
            Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                // 뜻 (앱 글자 언어): 배우는 말이 바로 그 말이면 다른 하나
                val shown = if (ui == learn) (if (ui == Lang.KO) Lang.EN else Lang.KO) else ui
                Text(Breaks.plain(line.text[shown]), style = Type.heading.of(shown), color = p.ink)
                val built = if (st.over) text else st.built
                Text(built.ifEmpty { "…" }, style = Type.title.copy(fontFamily = Faces.display).of(learn),
                    color = when { st.wrong -> p.inkSoft; st.right -> p.ink; else -> p.ink.copy(alpha = 0.8f) })
            }
            // 조각 (놓은 것은 사라진다)
            if (!st.over) FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                st.tiles.forEachIndexed { i, t ->
                    if (i in st.used) return@forEachIndexed
                    Box(Modifier.heightIn(min = 48.dp).border(1.dp, p.line).pressable(role = Role.Button) {
                        val next = st.tap(i); s.cue(!next.wrong); st = next
                    }.padding(horizontal = Tokens.Space.s3), contentAlignment = Alignment.Center) {
                        Text(t, style = Type.body.of(learn), color = p.ink)
                    }
                }
            }
            if (st.right) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Text(stringResource(R.string.recall_right), style = Type.body.ui(), color = p.correct, modifier = Modifier.weight(1f))
                    audio?.let { a -> ListenButtons(play = { sp -> s.narrator.play(a, sp) }) }
                }
                if (!speak) Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable(role = Role.Button) { s.narrator.stop(); speak = true }, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.recall_say), style = Type.small.ui().copy(textDecoration = TextDecoration.Underline), color = p.giltText)
                }
            }
            if (st.wrong) Text(stringResource(R.string.recall_wrong), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        val next: () -> Unit = {
            val (nq, na) = Recall.after(queue, again, st.right)
            s.narrator.stop(); s.recorder.stopPlaying()
            if (nq.isEmpty()) onFinish() else { queue = nq; again = na; done++ }
        }
        val label = stringResource(if (Recall.after(queue, again, st.right).first.isEmpty()) R.string.recall_finish else R.string.recall_next)
        if (take != null) SpeakPanel(s, take) { MainButton(label) { next() } }
        else Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            MainButton(label, enabled = st.over, reason = stringResource(R.string.need_pick)) { next() }
        }
    }
    if (leaving) Ask(stringResource(R.string.leave_title), stringResource(R.string.leave_yes), stringResource(R.string.leave_no),
        onYes = { leaving = false; s.narrator.stop(); s.back() }, onNo = { leaving = false })
}
