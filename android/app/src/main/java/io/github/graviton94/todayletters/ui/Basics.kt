package io.github.graviton94.todayletters.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.BasicDeck
import io.github.graviton94.todayletters.core.BasicItem
import io.github.graviton94.todayletters.core.Basics
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.CardRef
import io.github.graviton94.todayletters.core.Growth
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.Message
import io.github.graviton94.todayletters.core.Tri
import io.github.graviton94.todayletters.core.VocabSet
import io.github.graviton94.todayletters.core.Word
import io.github.graviton94.todayletters.data.Work
import io.github.graviton94.todayletters.data.learnVocab
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import java.util.concurrent.ConcurrentHashMap

// ── 기초 낱말 덱 (core [Basics], data/<작품>/basics[_en|_ko].json) ──────────────────
// 카드 열쇠 "<작품>:B:basics:[<자료>/]b<id>" ([CardRef.basic]). 편지에 매이지 않아, 복습에서는 예문 한 줄짜리 편지 ([basicLetter]) 로 다룬다.
// 오늘 처음 들인 기초 낱말은 이번 주 기록 "basic" ([io.github.graviton94.todayletters.data.Store.logDay]) 에 적는다: 하루 한 번만 들이게.

private val basicFiles = ConcurrentHashMap<String, BasicDeck>()

/** 기초 낱말 덱 (assets letters/<작품>/basics[_en|_ko].json). 파일이 아직 없으면 빈 덱. */
fun basicsOf(ctx: Context, series: String, deck: String): BasicDeck = basicFiles.getOrPut("$series/$deck") {
    Basics.parse(runCatching { ctx.assets.open("letters/$series/${VocabSet.file("basics", deck)}").bufferedReader().use { it.readText() } }.getOrNull())
}

/** 지금 배우는 말의 이 작품 기초 낱말 덱 (그 말의 어휘 자료일 때만, 닻과 같은 조건). */
fun AppState.basicDeck(series: String): BasicDeck {
    val w = works.firstOrNull { it.series.id == series } ?: return BasicDeck.EMPTY
    if (room(series).learn != w.learnVocab) return BasicDeck.EMPTY
    return basicsOf(ctx, series, w.deck)
}

/** 덱 한 줄 → 카드 낱말 · 예문 편지 (같은 작품 · 자료면 늘 같은 것이라 한 번만 만든다). */
private val basicWords = ConcurrentHashMap<String, Pair<Word, Letter>>()
private val lemmaKeys = ConcurrentHashMap<String, Map<String, io.github.graviton94.todayletters.core.LexEntry>>()

/** 세 언어 줄: 배우는 말 [learn] 은 [own], 다른 말은 [other] (없으면 영어 · 한국어 가운데 있는 것). */
private fun triOf(learn: Lang, own: String, original: Lang, other: Map<Lang, String>): Tri {
    val m = linkedMapOf(learn to if (learn == Lang.KO) Breaks.keepAll(own) else own)
    listOf(original, Lang.EN, Lang.KO).distinct().filter { it != learn }.forEach { l ->
        (other[l] ?: other[Lang.EN] ?: other[Lang.KO])?.let { m[l] = if (l == Lang.KO) Breaks.keepAll(it) else it }
    }
    return Tri(m)
}

/** 예문 한 줄짜리 편지 (복습의 빈칸 · 문장 힌트가 그 문장을 쓴다). 예문이 없으면 문장 없는 편지. */
private fun basicPair(w: Work, b: BasicItem): Pair<Word, Letter> = basicWords.getOrPut("${w.series.id}/${w.deck}/${b.id}") {
    val learn = w.learnVocab
    val byLemma = lemmaKeys.getOrPut("${w.series.id}/${w.deck}") { w.lexicon?.entries?.associateBy { it.lemma.lowercase() }.orEmpty() }
    // 사전에 같은 표제어가 있으면 그 열쇠 (내 것 · 등급 · 낱말 아래 뜻이 같은 낱말로 센다) · 편지에 찍힌 꼴
    val e = byLemma[b.w.lowercase()] ?: byLemma[b.k.lowercase()]
    val word = Word(triOf(learn, b.w, w.series.original, b.mean), pos = if (b.pos.isEmpty()) "" else "${b.pos}.",
        lex = e?.key.orEmpty(), level = e?.level.orEmpty(), forms = e?.forms.orEmpty(), ipa = e?.ipa.orEmpty())
    val msgs = listOfNotNull(b.ex?.let { x -> Message(triOf(learn, x.text, w.series.original, x.tr)) })
    word to Letter(Basics.LETTER, "", "", "", msgs, emptyList(), null, null)
}

/** 기초 낱말 카드 → 낱말 ([AppState.cardWord] 가 부른다). 덱에 없는 이름이면 null. */
fun AppState.basicCard(w: Work, id: String): AppState.CardWord? {
    val b = basicsOf(ctx, w.series.id, w.deck).items.firstOrNull { it.id == id } ?: return null
    val (word, letter) = basicPair(w, b)
    return AppState.CardWord(w, Basics.CHAPTER to letter, word, basic = b)
}

/** 기초 낱말 소리 (낱말 b_ · 예문 bx_, audio/<작품>/B/basics/): 이 기기에 있으면 그 경로, 없으면 "" (듣기 단추를 숨긴다). */
fun AppState.basicAudio(series: String, name: String): String =
    narrator.path(series, Basics.CHAPTER, Basics.LETTER, name).takeIf { io.github.graviton94.todayletters.data.AssetFiles.has(ctx, it) }.orEmpty()

/** 오늘 처음 들인 기초 낱말 카드 (이 작품 · 지금 자료). */
private fun AppState.basicsToday(series: String): Set<String> {
    val deck = deckOf(series)
    return store.dayLog("basic").filter { it.first == today }.map { it.second }
        .filter { k -> CardRef.parse(k)?.let { it.series == series && it.deck == deck } == true }.toSet()
}

/**
 * 이 작품의 오늘의 복습 한 판 (core [Basics.session]): 볼 날이 된 카드 + 새 기초 낱말 (12장이면 2~3, 20장이면 4).
 * [cards] 는 이 작품 · 지금 말의 카드. 덱이 없으면 예전 그대로 ([io.github.graviton94.todayletters.core.Memory.dueToday]).
 */
fun AppState.basicDay(series: String, cards: List<io.github.graviton94.todayletters.core.Card>, daily: Int): Basics.Day {
    val deck = basicDeck(series)
    // 편지를 하나도 안 연 작품은 새 기초 낱말도 아직 (서재 숫자 · 모든 작품 합계가 부풀지 않게)
    if (deck.isEmpty || openedChapters(series).isEmpty()) return Basics.Day(io.github.graviton94.todayletters.core.Memory.dueToday(cards, today, daily), emptyList())
    val d = deckOf(series)
    val have = (store.cardKeys() + store.later(d)).toSet()
    val pending = deck.items.map { CardRef.basic(series, it.id, d) }.filter { it !in have }
    return Basics.session(cards, today, daily, pending, basicsToday(series))
}

/** 오늘의 복습을 새로 시작할 때: 새 기초 낱말을 카드함에 (상자 0 · 오늘), 하루 한 번. */
fun AppState.introduceBasics(keys: List<String>) {
    if (keys.isEmpty()) return
    keys.forEach { k ->
        if (!store.hasCard(k)) store.save(io.github.graviton94.todayletters.core.Card(k, box = 0, due = today))
        store.logDay("basic", today, k)
    }
    bump()
}

/**
 * 나의 기록 ‘기초 낱말 N / 300’: 배우는 말 [lang] 에서 많이 쓰는 표제어 ([BasicDeck.top]) 가운데 써 보기 이상
 * ([Growth.knownForGrade], 등급과 같은 기준) 인 것. 낱말 카드 · 기초 낱말 카드 둘 다 센다. 그 말의 덱이 없으면 null.
 */
fun AppState.basicsKnown(lang: Lang): Pair<Int, Int>? {
    val top = baseWorks.asSequence().map { w -> basicsOf(ctx, w.series.id, VocabSet.deckFor(lang, w.series.original)).top }.firstOrNull { it.isNotEmpty() } ?: return null
    val known = store.cards().filter { Growth.knownForGrade(it) }.mapNotNull { c ->
        val ref = CardRef.parse(c.key) ?: return@mapNotNull null
        val base = baseWorks.firstOrNull { it.series.id == ref.series } ?: return@mapNotNull null
        if (VocabSet.deckFor(lang, base.series.original) != ref.deck) return@mapNotNull null
        when {
            ref.basic.isNotEmpty() -> basicsOf(ctx, ref.series, ref.deck).items.firstOrNull { it.id == ref.basic }?.k
            ref.own.isNotEmpty() -> ref.own
            else -> cardWord(c.key)?.word?.takeIf { !it.phrase }?.lex?.takeIf { it.isNotEmpty() }
        }
    }.toSet()
    return Basics.knownTop(top, known) to top.size
}

/**
 * 복습의 기초 낱말 카드 (뜻 고르기): 이름표 ‘기초 낱말’, 큰 낱말 · 듣기 · 품사, 답이 나면 뜻.
 * 아래 예문 칸: ‘편지에서’ (편지 문장) 또는 ‘예문’ (앱이 쓴 문장, 작은 안내), 듣기, 답이 나면 번역 (앱 글자 언어).
 */
@Composable
internal fun BasicCardPanel(s: AppState, q: Q, revealed: Boolean) {
    val p = Ink.palette
    val b = q.basic ?: return
    Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Caps(stringResource(R.string.basic_tag), p.giltText, small = true, decorative = true, modifier = Modifier.align(Alignment.End))
        Text(q.word, style = Type.display.copy(fontFamily = Faces.display).of(q.learn), color = p.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            if (q.audio.isNotEmpty()) ListenButtons(play = { sp -> s.narrator.play(q.audio, sp) }, ring = true)
            val pron = listOf(q.ipa, q.pos).filter { it.isNotEmpty() }.joinToString(" · ")
            if (pron.isNotEmpty()) Text(pron, style = Type.small, color = p.inkSoft)
        }
        if (revealed) Text(q.meaning, style = Type.title.of(q.read), color = p.ink)
    }
    val ex = b.ex ?: return
    Column(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(stringResource(if (ex.written) R.string.basic_example else R.string.basic_from_letter), style = Type.small.ui(), color = p.giltText, modifier = Modifier.weight(1f))
            if (q.exAudio.isNotEmpty()) ListenButtons(play = { sp -> s.narrator.play(q.exAudio, sp) }, label = stringResource(R.string.a11y_listen_sentence))
        }
        Text(ex.text, style = Type.target.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic).of(q.learn), color = p.ink)
        if (revealed) q.exTranslation.takeIf { it.isNotEmpty() }?.let { Text(it, style = Type.small.of(q.exLang), color = p.inkSoft) }
        if (ex.written) Text(stringResource(R.string.basic_example_note), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
    }
}
