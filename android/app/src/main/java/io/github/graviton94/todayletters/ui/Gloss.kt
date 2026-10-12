package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import io.github.graviton94.todayletters.core.Breaks
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.data.learnVocab
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.Vocab
import io.github.graviton94.todayletters.core.Word
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 등급 알약 (A1…C2): 금빛 테의 둥근 작은 글자. */
@Composable
fun LevelPill(level: String) {
    val p = Ink.palette
    Box(Modifier.border(1.dp, p.giltText, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
        Text(level, style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.giltText)
    }
}

/**
 * 눌러서 뜻 보기: 편지 원문의 낱말 하나를 누르면 아래에 작은 카드.
 * 표제어 · 등급 · 발음기호 · 뜻(앱 글자 언어) · Tatoeba 예문(작성자 표기) · 카드에 담기 (기능어는 뜻만).
 * 낱말 낭독은 그 낱말의 녹음(w<i>_<언어> · 오늘의 낱말 v_<언어>_<slug>)이 이 기기에 있을 때만 (기기 TTS 는 쓰지 않는다).
 */
@Composable
fun GlossSheet(s: AppState, series: String, chapter: String, letter: Letter, key: String, top: Boolean = false, onClose: () -> Unit) {
    val p = Ink.palette
    val work = s.work(series)
    // 사전의 언어: 원어 자료면 원어, 영어 · 한국어 자료(*_en · *_ko)면 그 언어
    val original = work.learnVocab
    val e = work.lexicon?.get(key) ?: return
    // 뜻은 앱 글자 언어로. 배우는 말이 바로 그 언어면 다른 하나로
    val ui = Vocab.glossLang(uiLang(), original)
    val word = remember(series, letter.id, key, original) { Vocab.match(e, letter.source, original, work.series.original) }
    // 챕터 낱말의 녹음이 없으면 오늘의 낱말 소리 (v_<언어>_<slug>) 로
    val audio = remember(series, letter.id, key, original) { s.wordAudio(series, chapter, letter, word ?: Vocab.word(e, original), original, work) }
    val has = remember(series, key, s.version) { s.hasLex(series, e.key) }
    val ownKey = s.ownKey(series, chapter, letter, e.key)
    var later by remember(key) { mutableStateOf(false) }
    val waiting = later || s.isLater(ownKey)
    LaunchedEffect(key) { if (audio.isNotEmpty()) s.narrator.play(audio) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    // 누른 낱말이 화면 아래 절반이면 카드는 위에 (그 낱말을 가리지 않게 화면의 절반 안쪽으로)
    val half = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.45f).dp
    Box(Modifier.fillMaxSize().background(p.scrim).clickable(onClick = onClose), contentAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().then(if (top) Modifier.heightIn(max = half) else Modifier).background(p.paper).clickable(enabled = false) {}
                .then(if (top) Modifier.statusBarsSafe() else Modifier.navigationBarsPadding())
                .verticalScroll(rememberScrollState()).padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Text(Vocab.display(e, original), style = Type.title.copy(fontFamily = Faces.display).of(original), color = p.ink)
                        if (e.level.isNotEmpty()) LevelPill(e.level)
                    }
                    val line = listOfNotNull(e.ipa.takeIf { it.isNotEmpty() }?.let { "[$it]" }, Vocab.posLabel(e).takeIf { it.isNotEmpty() }).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, style = Type.small, color = p.inkSoft)
                }
                if (audio.isNotEmpty() && s.narrator.has(audio)) SpeakerButton(false, stringResource(R.string.a11y_listen_word), onDark = true) { s.narrator.play(audio) }
            }
            Text(Vocab.meaning(e, ui), style = Type.heading.of(ui), color = p.ink)
            e.example?.let { ex ->
                Hair()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ex.text, style = Type.base.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic).of(original), color = p.ink)
                    val trLang = if (ui == Lang.KO && !ex.ko.isNullOrEmpty()) Lang.KO else Lang.EN
                    val tr = if (trLang == Lang.KO) ex.ko.orEmpty() else ex.en
                    if (tr.isNotEmpty() && tr != ex.text) Text(tr, style = Type.small.of(trLang), color = p.inkSoft)
                    Text("Tatoeba · ${ex.author}", style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                }
            }
            if (!e.function) Box(Modifier.padding(top = Tokens.Space.s1)) {
                when {
                    has -> Capsule(stringResource(R.string.gloss_in_cards), CapsuleKind.DONE)
                    waiting -> Text(stringResource(R.string.gloss_later), style = Type.small.ui(), color = p.inkSoft)
                    else -> Box(Modifier.heightIn(min = 44.dp).border(1.dp, p.giltText).pressable {
                        if (!s.addOwn(series, chapter, letter, e.key)) later = true
                    }.padding(horizontal = Tokens.Space.s3), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.gloss_add), style = Type.small.ui(), color = p.giltText)
                    }
                }
            }
        }
    }
}

/**
 * 편지 속 표현 (낱말 맞추기 다음): 표현 · 번역 · 쓰임 설명 카드 1–2장. 카드는 편지를 마치면 복습 카드함에 같이 들어간다.
 */
@Composable
fun PhraseCards(s: AppState, r: Route.Play, onFinish: () -> Unit) {
    val p = Ink.palette
    val (_, letter) = s.letterOf(r.room)
    val work = s.work(r.room.series)
    val original = work.learnVocab
    val view = s.room(r.room.series)
    val tr = if (view.read == Lang.EN || view.read == Lang.KO) view.read else uiLang()
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            PracticeTitle(deco(work.series.original, "EXPRESSIONS", "AUSDRÜCKE"), stringResource(R.string.phrase_title))
            val (chapter, _) = s.letterOf(r.room)
            letter.phrases.forEach { ph -> PhraseCard(s, r.room.series, chapter, letter, ph, original, tr) }
            Text(stringResource(R.string.phrase_note), style = Type.small.ui(), color = p.inkSoft)
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            Primary(stringResource(R.string.play_finish), onClick = onFinish)
        }
    }
}

/**
 * 표현 카드 한 장 (v22): 표현 (배우는 말의 따옴표로) · 번역 · 쓰임 설명 · 듣기, 그리고 그 표현이 나온 편지 문장(번역과 함께)과
 * 자리 (편지 번호 · 날짜 · 몇 번째 줄). 소리는 ph<n>_<언어>, 없으면 낭독에서 그 표현이 든 마디만.
 */
@Composable
fun PhraseCard(s: AppState, series: String, chapter: String, letter: Letter, ph: Word, original: Lang, tr: Lang) {
    val p = Ink.palette
    val w = s.work(series)
    val sound = remember(series, letter.id, ph, original) { s.phraseAudio(series, chapter, letter, ph, original) }
    val listen = stringResource(R.string.phrase_listen)
    Slip(seed = ph.hashCode(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Caps(stringResource(R.string.phrase_card), p.giltText, small = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(io.github.graviton94.todayletters.core.Quote.wrap(ph.text[original], original),
                    style = Type.title.copy(fontFamily = Faces.display).of(original), color = p.slipInk, modifier = Modifier.weight(1f))
                if (sound != null) SpeakerButton(s.narrator.now == sound.first, listen) {
                    if (s.narrator.now == sound.first) s.narrator.stop()
                    else sound.second?.let { rg -> s.narrator.playRange(sound.first, rg.first, rg.last) } ?: s.narrator.play(sound.first)
                }
            }
            Text(ph.text[tr], style = Type.heading.of(tr), color = p.slipInk)
            ph.note?.get(uiLang())?.takeIf { it.isNotEmpty() }?.let { Text(it, style = Type.small.ui(), color = p.slipSoft) }
            // 나온 문장 · 자리
            letter.messages.getOrNull(ph.msg)?.let { m ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.slipSoft.copy(alpha = 0.3f)))
                Text(stringResource(R.string.phrase_example), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.slipSoft)
                val line = m.text[original]
                val at = Breaks.plain(line).lowercase().indexOf(Breaks.plain(ph.text[original]).lowercase())
                val marked = androidx.compose.ui.text.buildAnnotatedString {
                    val plain = Breaks.plain(line)
                    append(plain)
                    if (at >= 0) addStyle(androidx.compose.ui.text.SpanStyle(background = Color(0x55D2A955)), at, (at + Breaks.plain(ph.text[original]).length).coerceAtMost(plain.length))
                }
                Text(marked, style = Type.base.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic).of(original), color = p.slipInk)
                if (tr != original) Text(m.text[tr], style = Type.small.of(tr), color = p.slipSoft)
                val n = w.chapters.flatMap { it.letters }.indexOf(letter) + 1
                Text(stringResource(R.string.phrase_where, if (n > 0) roman(n) else "P", dateLine(letter.date, letter.place), ph.msg + 1),
                    style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.slipSoft)
            }
        }
    }
}

/** 화면 맨 위: 어디서든 펼친 낱말([AppState.gloss])의 작은 카드. 전체 화면 창 안이면 [layer] 는 그 창의 층 (1부터). */
@Composable
fun GlossHost(s: AppState, layer: Int = 0) {
    // 전체 화면 창(낱말 카드 상세) 안에서 열었으면 그 창 안의 것만 띄운다
    if (layer != s.glossLayers) return
    val g = s.gloss ?: return
    GlossSheet(s, g.series, g.chapter, g.letter, g.key, top = s.glossLow) { s.gloss = null }
}

/** 펼친 낱말([picked], 글 배치 [layout])이 화면 아래 절반에 있는지 [onLow] 로 알린다 ([AppState.glossLow]). */
fun Modifier.glossSpot(picked: IntRange?, layout: androidx.compose.ui.text.TextLayoutResult?, onLow: (Boolean) -> Unit): Modifier = composed {
    var at by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    LaunchedEffect(picked, layout, at) {
        val l = layout ?: return@LaunchedEffect
        val c = at?.takeIf { it.isAttached } ?: return@LaunchedEffect
        if (picked == null || picked.isEmpty()) return@LaunchedEffect
        val line = l.getLineForOffset(picked.first.coerceIn(0, l.layoutInput.text.length))
        val mid = c.localToRoot(androidx.compose.ui.geometry.Offset(0f, (l.getLineTop(line) + l.getLineBottom(line)) / 2f)).y
        onLow(mid > c.findRootCoordinates().size.height / 2f)
    }
    onGloballyPositioned { at = it }
}

/**
 * 눌러서 뜻 보기 글자: 편지 원문(배우는 말)의 한 메시지가 보이는 곳이면 어디서든 (프롤로그 · 맺음말 · 따라 읽기 · 복습 문장 · 낱말 카드 · 내 구절).
 * [text] 는 그 메시지를 보이는 그대로 (따옴표 · 빈칸 · 칠하기 포함, AnnotatedString 이면 그 꾸밈 그대로). 토큰은 보이는 글자에 맞춰 찾는다.
 * 토큰이 없으면 (그 언어의 자료가 없을 때) 그냥 글자. 낱말이 아닌 곳을 누르면 부모의 누르기(낭독 등)가 그대로 받는다.
 */
@Composable
fun GlossText(
    s: AppState, series: String, chapter: String, letter: Letter, msg: Int,
    text: androidx.compose.ui.text.AnnotatedString, style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE, textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    /** [text] 가 메시지의 한 토막이면: 메시지 전체 글 [full] 과 그 안에서 [text] 가 시작하는 자리 [offset] (토큰은 전체 글에 맞춘다). */
    full: String? = null, offset: Int = 0,
) {
    val tokens = remember(series, letter.id, msg, s.room(series).learn, s.work(series).deck) { s.glossTokens(series, letter.id, msg) }
    if (tokens.isNullOrEmpty()) {
        Text(text, style = style, color = color, modifier = modifier, maxLines = maxLines, textAlign = textAlign,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        return
    }
    val ranges = remember(text.text, tokens, full, offset) {
        if (full == null) Vocab.align(text.text, tokens)
        else Vocab.align(full, tokens).map { r -> if (r.isEmpty() || r.first < offset || r.last >= offset + text.length) IntRange.EMPTY else (r.first - offset)..(r.last - offset) }
    }
    val picked = s.gloss?.takeIf { it.series == series && it.letter.id == letter.id && it.msg == msg }?.let { ranges.getOrNull(it.token) }
    val shown = remember(text, picked) {
        if (picked == null || picked.isEmpty()) text else androidx.compose.ui.text.AnnotatedString.Builder(text).apply {
            addStyle(androidx.compose.ui.text.SpanStyle(background = androidx.compose.ui.graphics.Color(0x55D2A955)), picked.first, (picked.last + 1).coerceAtMost(text.length))
        }.toAnnotatedString()
    }
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(
        shown, style = style, color = color, maxLines = maxLines, textAlign = textAlign,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, onTextLayout = { layout = it },
        modifier = modifier.glossSpot(picked, layout) { s.glossLow = it }.pointerInput(ranges) {
            // 낱말 위를 누를 때만 받는다 (그 밖은 부모에게)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                val l = layout ?: return@awaitEachGesture
                if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                val off = l.getOffsetForPosition(up.position)
                val j = ranges.indexOfFirst { !it.isEmpty() && off in it.first..(it.last + 1) }
                if (j < 0) return@awaitEachGesture
                up.consume()
                s.gloss = AppState.GlossPick(series, chapter, letter, msg, j, tokens[j].key)
            }
        },
    )
}

/** [GlossText] 의 글자 꼴 (꾸밈 없는 글). */
@Composable
fun GlossText(
    s: AppState, series: String, chapter: String, letter: Letter, msg: Int, text: String, style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE, textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) = GlossText(s, series, chapter, letter, msg, androidx.compose.ui.text.AnnotatedString(text), style, modifier, color, maxLines, textAlign)
