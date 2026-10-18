package io.github.graviton94.todayletters.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Anchor
import io.github.graviton94.todayletters.core.Anchors
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.CardRef
import io.github.graviton94.todayletters.core.Growth
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.Tri
import io.github.graviton94.todayletters.core.UiLang
import io.github.graviton94.todayletters.core.Vocab
import io.github.graviton94.todayletters.core.Word
import io.github.graviton94.todayletters.data.Library
import io.github.graviton94.todayletters.data.Work
import io.github.graviton94.todayletters.data.learnVocab
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

// ── 입문자 길 (시안 beginner.html ① ② ⑤): 낱말 아래 뜻 · 편지 전 예열 · 닻 표현 ──────────────────
// 저장은 SharedPreferences "letters" (기록 보관 · 안드로이드 백업과 같은 범위):
//   s:<작품>:il      낱말 아래 뜻 켜짐 (기본 true)
//   s:<작품>:warm    편지 전 예열 -1 등급에 맞춰 (기본) · 1 켜기 · 0 끄기
//   wu:<편지 열쇠>   예열에서 본 것 ("w:<사전 열쇠>" · "a<닻 번호>" · "p<표현 번호>" 을 줄바꿈으로)
//   anc:<작품>/<자료> 예열에서 배운 닻 표현 (맞추는 꼴 "j ai" 을 줄바꿈으로)

private fun AppState.prefs() = ctx.getSharedPreferences("letters", Context.MODE_PRIVATE)

/** 낱말 아래 뜻 (편지 방 위에서 켜고 끈다, 작품마다, 기본 켜짐). */
fun AppState.interlinearOn(series: String): Boolean { @Suppress("UNUSED_EXPRESSION") version; return prefs().getBoolean("s:$series:il", true) }
fun AppState.setInterlinear(series: String, on: Boolean) { prefs().edit().putBoolean("s:$series:il", on).apply(); bump() }

/** 편지 전 예열 고름: null = 등급에 맞춰 (A1 · A2 켜기, B1 부터 끄기), true · false = 직접 고름. */
fun AppState.warmChoice(series: String): Boolean? {
    @Suppress("UNUSED_EXPRESSION") version
    return when (prefs().getInt("s:$series:warm", -1)) { 1 -> true; 0 -> false; else -> null }
}
fun AppState.setWarmChoice(series: String, v: Boolean?) { prefs().edit().putInt("s:$series:warm", when (v) { true -> 1; false -> 0; null -> -1 }).apply(); bump() }

/** 이 작품에서 편지 전 예열을 하는가 (도움 등급 · ‘도움 한 단계 더’ 를 따른다). */
fun AppState.warmOn(series: String): Boolean = io.github.graviton94.todayletters.core.WarmUp.on(warmChoice(series), support(series).band)

/** 뜻을 보일 말: 읽는 말이 영어 · 한국어이고 배우는 말과 다르면 그것, 아니면 앱 글자 언어 ([Vocab.glossLang]). */
fun AppState.glossLangFor(series: String): Lang {
    val v = room(series)
    if (v.read != v.learn && (v.read == Lang.KO || v.read == Lang.EN)) return v.read
    return Vocab.glossLang(if (app.ui == UiLang.KO) Lang.KO else Lang.EN, v.learn)
}

// ── 닻 표현 ───────────────────────────────────────────
private val anchorFiles = ConcurrentHashMap<String, List<Anchor>>()

/** 닻 표현 파일 (assets letters/<작품>/anchors[_en|_ko].json, scripts/anchors.py). 없으면 빈 것. */
fun anchorsOf(ctx: Context, series: String, deck: String): List<Anchor> = anchorFiles.getOrPut("$series/$deck") {
    runCatching {
        val o = JSONObject(ctx.assets.open("letters/$series/${io.github.graviton94.todayletters.core.VocabSet.file("anchors", deck)}").bufferedReader().readText())
        val a = o.getJSONArray("anchors")
        (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            val first = x.optJSONArray("first")
            Anchor(x.getString("t"), x.getString("k").split(" ").filter { it.isNotEmpty() }, x.optString("ko"), x.optString("en"),
                x.optInt("n"), first?.optString(0).orEmpty(), first?.optInt(1) ?: 0, x.optString("letters").split(" ").filter { it.isNotEmpty() })
        }
    }.getOrDefault(emptyList())
}

/** 지금 배우는 말의 이 작품 닻 표현 (어휘 자료가 그 말의 것일 때만, 눌러서 뜻 보기와 같은 조건). */
fun AppState.anchors(series: String): List<Anchor> {
    val w = works.firstOrNull { it.series.id == series } ?: return emptyList()
    if (w.lexicon == null || room(series).learn != w.learnVocab) return emptyList()
    return anchorsOf(ctx, series, w.deck)
}

private fun AppState.taughtKey(series: String) = "anc:$series/${deckOf(series)}"
/** 예열에서 배운 닻 (맞추는 꼴). */
fun AppState.anchorsTaught(series: String): Set<String> {
    @Suppress("UNUSED_EXPRESSION") version
    return prefs().getString(taughtKey(series), "").orEmpty().split("\n").filter { it.isNotBlank() }.toSet()
}

/** 닻 카드 열쇠 (닻 번호 → 카드함 · 나중 줄에 이미 있는 열쇠), 지금 배우는 말의 것. */
private fun AppState.anchorCards(series: String): Map<Int, String> {
    val deck = deckOf(series)
    return (store.cardKeys() + store.later(deck)).mapNotNull { k ->
        CardRef.parse(k)?.takeIf { it.series == series && it.deck == deck && it.anchor >= 0 }?.let { it.anchor to k }
    }.toMap()
}

/** 익힌 닻인가: 예열에서 배웠거나, 그 닻 카드가 떠올리기 이상 ([Growth.known]). */
fun AppState.anchorKnownFn(series: String): (Int) -> Boolean {
    val list = anchors(series)
    if (list.isEmpty()) return { false }
    val taught = anchorsTaught(series)
    val cards = anchorCards(series)
    val cm = if (cards.isEmpty()) emptyMap() else cardMap()
    return { i -> list.getOrNull(i)?.let { a -> a.key.joinToString(" ") in taught || cards[i]?.let { Growth.known(cm[it]) } == true } == true }
}

/** 닻 카드 들이기: 아직 카드가 없는 닻만, 이 편지 열쇠로. */
fun AppState.admitAnchors(series: String, chapter: String, letter: Letter, idx: Collection<Int>) {
    if (idx.isEmpty()) return
    val have = anchorCards(series)
    val keys = idx.filter { it !in have }.map { CardRef.anchor(series, chapter, letter.id, it, deckOf(series)) }
    if (keys.isNotEmpty()) { admit(keys); bump() }
}

/** 이 편지에 나오는 닻 (번호 → 메시지 번호 · 그 메시지 안 첫 토큰). */
private fun anchorsIn(tokens: List<List<io.github.graviton94.todayletters.core.Token>>, list: List<Anchor>): Map<Int, Pair<Int, Int>> {
    val out = linkedMapOf<Int, Pair<Int, Int>>()
    tokens.forEachIndexed { m, ts ->
        val ns = ts.map { Anchors.norm(it.surface) }
        list.forEachIndexed { i, a -> if (i !in out) Anchors.find(ns, a.key).firstOrNull()?.let { out[i] = m to it.first } }
    }
    return out
}

/** 다 읽은 편지의 닻 표현도 카드함으로 (낱말 카드와 같은 간격, 예열을 끈 사람도). */
fun AppState.collectAnchors(series: String, chapter: String, letter: Letter) {
    val list = anchors(series)
    if (list.isEmpty()) return
    val toks = Library.gloss(ctx, series, deckOf(series))[letter.id] ?: return
    admitAnchors(series, chapter, letter, anchorsIn(toks, list).keys)
}

/** 닻 카드 → 낱말 (복습에서 표현 카드처럼). [AppState.cardWord] 가 부른다. */
fun AppState.anchorCard(w: Work, chapter: String, l: Letter, i: Int): AppState.CardWord? {
    val a = anchorsOf(ctx, w.series.id, w.deck).getOrNull(i) ?: return null
    val learn = w.learnVocab
    val msg = Library.gloss(ctx, w.series.id, w.deck)[l.id]?.let { anchorsIn(it, listOf(a))[0]?.first } ?: -1
    val text = linkedMapOf(learn to if (learn == Lang.KO) Breaks.keepAll(a.text) else a.text)
    if (learn != Lang.EN) text[Lang.EN] = a.en
    if (learn != Lang.KO) text[Lang.KO] = Breaks.keepAll(a.ko)
    val note = Tri(mapOf(Lang.KO to ctx.getString(R.string.anchor_note_ko), Lang.EN to ctx.getString(R.string.anchor_note_en)))
    return AppState.CardWord(w, chapter to l, Word(Tri(text), note = note, msg = msg))
}

// ── 편지 전 예열 ───────────────────────────────────────
/** 예열 한 장: 보일 글 · 뜻 · 소리 (파일, 범위) · 무엇인지. */
data class WarmCard(val item: io.github.graviton94.todayletters.core.WarmUp.Item, val text: String, val meaning: String, val audio: Pair<String, IntRange?>?, val lex: String = "")

private fun firstMeaning(x: String) = Breaks.plain(x).split(Regex("[;/(]")).first().trim().trimEnd(',').ifEmpty { Breaks.plain(x) }

/** 이 편지의 예열 (core [io.github.graviton94.todayletters.core.WarmUp.pick]). 어휘 자료가 없거나 두 장이 안 되면 빈 목록. */
fun AppState.warmCards(series: String, chapter: String, letter: Letter): List<WarmCard> {
    val w = work(series)
    if (w.lexicon == null || room(series).learn != w.learnVocab) return emptyList()
    val toks = Library.gloss(ctx, series, w.deck)[letter.id] ?: return emptyList()
    val flat = toks.flatten()
    val starts = toks.runningFold(0) { a, m -> a + m.size }
    val learn = w.learnVocab
    val gl = glossLangFor(series)
    val mine = mine(room(series).learn)
    val words = letter.words.map { wd -> (flat.indexOfFirst { it.key == wd.lex }.takeIf { it >= 0 && wd.lex.isNotEmpty() } ?: Int.MAX_VALUE) to (wd.lex in mine) }
    val phrases = letter.phrases.map { ph ->
        val key = Anchors.keyOf(ph.text[learn])
        val at = toks.getOrNull(ph.msg)?.let { ts -> Anchors.find(ts.map { Anchors.norm(it.surface) }, key).firstOrNull()?.first?.plus(starts[ph.msg]) } ?: Int.MAX_VALUE
        key.size to at
    }
    val list = anchors(series)
    val known = anchorKnownFn(series)
    val here = anchorsIn(toks, list).filterKeys { !known(it) }.map { (i, at) -> i to starts[at.first] + at.second }
    return io.github.graviton94.todayletters.core.WarmUp.pick(words, phrases, here).mapNotNull { it ->
        when (it.kind) {
            io.github.graviton94.todayletters.core.WarmUp.Kind.WORD -> letter.words.getOrNull(it.index)?.let { wd ->
                val path = wordAudio(series, chapter, letter, wd, learn)
                WarmCard(it, wd.text[learn], firstMeaning(wd.text[gl]), path.takeIf { p -> p.isNotEmpty() }?.let { p -> p to null }, wd.lex)
            }
            io.github.graviton94.todayletters.core.WarmUp.Kind.PHRASE -> letter.phrases.getOrNull(it.index)?.let { ph ->
                WarmCard(it, ph.text[learn], ph.text[gl], phraseAudio(series, chapter, letter, ph, learn))
            }
            // 닻 표현은 따로 녹음이 없다: 듣기 단추 없이
            io.github.graviton94.todayletters.core.WarmUp.Kind.ANCHOR -> list.getOrNull(it.index)?.let { a -> WarmCard(it, a.text, a.meaning(gl), null) }
        }
    }
}

/** 새 편지를 처음 열 때 예열을 먼저 하는가 ([AppState.open]). */
fun AppState.warmUpDue(series: String, chapter: String, letter: Letter): Boolean =
    warmOn(series) && warmCards(series, chapter, letter).isNotEmpty()

/** 예열에서 본 것 (편지 방의 ‘아까 본 말’): 사전 열쇠 "w:<열쇠>" · 닻 "a<번호>" · 표현 "p<번호>". */
fun AppState.warmSeen(series: String, chapter: String, letterId: String): Set<String> {
    @Suppress("UNUSED_EXPRESSION") version
    return prefs().getString("wu:${key(series, chapter, letterId)}", "").orEmpty().split("\n").filter { it.isNotBlank() }.toSet()
}

/** 예열을 마쳤다 (또는 건너뜀): 본 것을 적고, 배운 닻은 카드함에, 그리고 편지 방으로 (예열 화면은 뒤로 가기에 남기지 않는다). */
fun AppState.finishWarm(r: Route.WarmUp, seen: List<WarmCard>) {
    val (chapter, letter) = letterOf(r.room)
    val series = r.room.series
    if (seen.isNotEmpty()) {
        val tags = seen.map { c ->
            when (c.item.kind) {
                io.github.graviton94.todayletters.core.WarmUp.Kind.WORD -> "w:${c.lex}"
                io.github.graviton94.todayletters.core.WarmUp.Kind.PHRASE -> "p${c.item.index}"
                io.github.graviton94.todayletters.core.WarmUp.Kind.ANCHOR -> "a${c.item.index}"
            }
        }
        val e = prefs().edit().putString("wu:${key(series, chapter, letter.id)}", tags.joinToString("\n"))
        val taught = seen.filter { it.item.kind == io.github.graviton94.todayletters.core.WarmUp.Kind.ANCHOR }.mapNotNull { anchors(series).getOrNull(it.item.index) }
        if (taught.isNotEmpty()) e.putString(taughtKey(series), (anchorsTaught(series) + taught.map { it.key.joinToString(" ") }).joinToString("\n"))
        e.apply()
        admitAnchors(series, chapter, letter, seen.filter { it.item.kind == io.github.graviton94.todayletters.core.WarmUp.Kind.ANCHOR }.map { it.item.index })
    }
    narrator.stop()
    if (route == r) stack.removeAt(stack.lastIndex)
    go(r.room)
}

/**
 * 편지 전 예열 화면: “편지를 열기 전에 · 오늘 편지에 나오는 말”, 낱말 · 덩어리 3~4장. 지금 장의 소리를 한 번 저절로 (자동 낭독 설정),
 * ‘따라 말했어요’ 로 다음 장, 다 하면 ‘편지 열기 ›’. 말하기는 믿고 맡긴다 (녹음 · 채점 없음). 점은 진행.
 */
@Composable
fun WarmUpScreen(s: AppState, r: Route.WarmUp) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(r.room)
    val series = r.room.series
    val learn = s.work(series).learnVocab
    val gl = s.glossLangFor(series)
    val cards = remember(letter.id) { s.warmCards(series, chapter, letter) }
    if (cards.isEmpty()) { LaunchedEffect(Unit) { s.finishWarm(r, emptyList()) }; return }
    var at by remember(letter.id) { mutableIntStateOf(0) }
    fun play(i: Int, speed: Float? = null) {
        val (path, range) = cards.getOrNull(i)?.audio ?: return
        if (range != null) s.narrator.playRange(path, range.first, range.last, speed) else s.narrator.play(path, speed)
    }
    LaunchedEffect(at) { if (at < cards.size && s.app.sound) play(at) }
    androidx.activity.compose.BackHandler { s.back() }
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(stringResource(R.string.back), onClick = { s.back() }) { Chevron(p.ink) }
            Box(Modifier.weight(1f))
            Box(Modifier.heightIn(min = 48.dp).pressable { s.finishWarm(r, cards.take(at)) }.padding(horizontal = Tokens.Space.s4), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.warm_skip), style = Type.small.ui(), color = p.inkSoft)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            PracticeTitle(stringResource(R.string.warm_caps), stringResource(R.string.warm_title))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                cards.indices.forEach { i -> Box(Modifier.size(7.dp).background(if (i < at || at >= cards.size) p.giltText else if (i == at) p.giltText.copy(alpha = 0.55f) else p.hair, CircleShape)) }
            }
            cards.forEachIndexed { i, c ->
                val now = i == at
                Row(
                    Modifier.fillMaxWidth().alpha(if (now || at >= cards.size) 1f else if (i < at) 0.7f else 0.45f)
                        .background(p.leaf).border(if (now) 1.5.dp else Tokens.Stroke.hair, if (now) p.giltText else p.hair)
                        .pressable(haptic = false) { if (i <= at) play(i) }
                        .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        when (c.item.kind) {
                            io.github.graviton94.todayletters.core.WarmUp.Kind.ANCHOR -> Caps(stringResource(R.string.warm_anchor), p.giltText, small = true)
                            io.github.graviton94.todayletters.core.WarmUp.Kind.PHRASE -> Caps(stringResource(R.string.warm_phrase), p.giltText, small = true)
                            else -> {}
                        }
                        Text(c.text, style = Type.title.copy(fontFamily = Faces.display).of(learn), color = p.ink)
                        if (c.meaning.isNotEmpty()) Text(c.meaning, style = Type.small.of(gl), color = p.inkSoft)
                    }
                    if (c.audio != null && i <= at) ListenButtons(play = { sp -> play(i, sp) })
                }
            }
            Text(stringResource(R.string.warm_hint), style = Type.small.ui(), color = p.inkSoft,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            if (at < cards.size) Primary(stringResource(R.string.warm_said), shine = true) { s.narrator.stop(); at++ }
            else Primary(stringResource(R.string.warm_open), shine = true) { s.finishWarm(r, cards) }
        }
    }
}
