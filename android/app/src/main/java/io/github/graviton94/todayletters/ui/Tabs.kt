package io.github.graviton94.todayletters.ui

import io.github.graviton94.todayletters.data.learnVocab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Langs
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

private val romans = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV",
    "XVI", "XVII", "XVIII", "XIX", "XX", "XXI", "XXII", "XXIII", "XXIV", "XXV")
fun roman(n: Int) = romans.getOrElse(n - 1) { n.toString() }

@Composable
private fun Page(content: @Composable () -> Unit) =
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
    ) { content() }

/**
 * 단어장: 받은 편지의 낱말. 한 줄에 낱말 · 뜻 (누르면 발음). 위에 찾기, 아래로 20개씩 더 보기.
 * 같은 낱말은 한 번만 (처음 나온 편지 기준).
 */
@Composable
fun WordListScreen(s: AppState) {
    val p = Ink.palette
    var query by remember { mutableStateOf("") }
    var limit by remember { mutableIntStateOf(20) }
    var detail by remember { mutableStateOf<String?>(null) }
    data class Entry(val learn: String, val read: String, val ipa: String, val date: String, val audio: String, val lang: io.github.graviton94.todayletters.core.Lang, val readLang: io.github.graviton94.todayletters.core.Lang, val key: String)
    val all = remember(s.version) {
        s.works.flatMap { w ->
            val id = w.series.id
            val room = s.room(id)
            w.chapters.flatMap { c ->
                c.letters.filter { s.progress(id, c.id, it.id).shown > 0 }.flatMap { l ->
                    l.words.mapIndexed { i, word ->
                        Entry(word.text[room.learn], word.text[room.read], io.github.graviton94.todayletters.core.Pron.line(word, room.learn, room.learn == w.learnVocab), l.date,
                            s.wordAudio(id, c.id, l, word, room.learn), room.learn, room.read, s.cardKey(id, c.id, l.id, i))
                    }
                }
            }
        }.plus(
            // 편지에서 눌러 내 카드로 담은 낱말 · 표현 카드
            s.cards().filter { c -> io.github.graviton94.todayletters.core.CardRef.parse(c.key)?.let { it.own.isNotEmpty() || it.phrase >= 0 } == true }.mapNotNull { c ->
                s.cardWord(c.key)?.let { cw ->
                    val room = s.room(cw.work.series.id)
                    Entry(cw.word.text[room.learn], cw.word.text[room.read], io.github.graviton94.todayletters.core.Pron.line(cw.word, room.learn, room.learn == cw.work.learnVocab), cw.at.second.date,
                        s.wordAudio(cw, room.learn), room.learn, room.read, c.key)
                }
            }
        ).distinctBy { it.learn.lowercase() }
    }
    fun norm(x: String) = java.text.Normalizer.normalize(io.github.graviton94.todayletters.core.Breaks.plain(x).lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    val shown = if (query.isBlank()) all else all.filter { norm(it.learn).contains(norm(query)) || norm(it.read).contains(norm(query)) }
    Column(Modifier.fillMaxSize().imePadding()) {   // 키보드가 올라오면 목록이 그 위에서 끝난다
        TopBar(stringResource(R.string.words_search), s, help = null, showBack = true, showSettings = false)
        Column(Modifier.padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(horizontal = Tokens.Space.s3),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                    drawCircle(p.inkSoft, size.width * 0.32f, androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.width * 0.42f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                    drawLine(p.inkSoft, androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.width * 0.66f), androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.width * 0.95f), 1.5.dp.toPx())
                }
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.words_search), style = Type.body.ui(), color = p.hideInk)
                    androidx.compose.foundation.text.BasicTextField(
                        query, { query = it; limit = 20 }, singleLine = true,
                        textStyle = Type.body.ui().copy(color = p.ink), cursorBrush = androidx.compose.ui.graphics.SolidColor(p.giltText),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(stringResource(R.string.words_count, all.size), style = Type.small.ui(), color = p.inkSoft)
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tokens.Space.s5)) {
            items(shown.take(limit).size) { k ->
                val e = shown[k]
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable { detail = e.key },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Text(e.learn, style = Type.body.of(e.lang), color = p.ink, maxLines = 1)
                    if (e.ipa.isNotEmpty()) Text(e.ipa, style = Type.small, color = p.hideInk, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(e.read, style = Type.small.of(e.readLang), color = p.inkSoft, maxLines = 1)
                }
                Hair()
            }
            item {
                when {
                    all.isEmpty() -> EmptyState(stringResource(R.string.empty_words_t), stringResource(R.string.empty_words_b), stringResource(R.string.today_open)) { s.go(Route.Inbox) }
                    shown.isEmpty() -> Text(stringResource(R.string.words_none), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(vertical = Tokens.Space.s4))
                    shown.size > limit -> Box(Modifier.padding(vertical = Tokens.Space.s4)) { Secondary(stringResource(R.string.words_more, shown.size - limit)) { limit += 20 } }
                }
            }
        }
    }
    detail?.let { k -> WordDetail(s, k) { detail = null } }
}

/** 작품 표지: 소개 · 진도 · 목차 · ⚙ 작품 설정. */
@Composable
fun SeriesCover(s: AppState, id: String) {
    val p = Ink.palette
    val w = s.work(id)
    s.version
    Column(Modifier.fillMaxSize()) {
        TopBar(w.sender, s, help = "series", showBack = true, showSettings = false) {
            IconButton(stringResource(R.string.series_settings), onClick = { s.go(Route.SeriesSettings(id)) }) { Gear(p.ink) }
        }
        Page {
            Caps(deco(w.series.original, "Saison I · Table des matières", "Band I · Inhalt"), decorative = true)
            Text(w.title[w.series.original], style = Type.display, color = p.ink)
            Text(w.title[s.app.read], style = Type.body.of(s.app.read), color = p.inkSoft)
            Rule()
            // 처음 묶음 (서문 + 무료 장) 을 아직 받지 않았으면: 받기 단추가 남는다
            FirstPackButton(s, id)
            // 무료 장 끝이 가까우면 미리 한 줄 (남은 무료 편지 1~3통)
            FreeLeftLine(s, id)
            // 프롤로그: 아를 이전 (언제든 다시 보기)
            if (w.prologue.isNotEmpty()) {
                ChapterRow(
                    numeral = "P", original = deco(w.series.original, "Prologue", "Prolog"), read = w.arrival?.label?.get(uiLang()) ?: stringResource(R.string.cover_prologue),
                    readLang = s.app.read, originalLang = w.series.original,
                    meta = stringResource(R.string.cover_prologue_meta, w.prologue.size),
                    onClick = { s.openPrologue(id) },
                ) {}
                Hair()
            }
            w.chapters.forEachIndexed { i, c ->
                val done = c.letters.count { s.progress(id, c.id, it.id).done }
                // 잠긴 장 (III장부터, 소장 · 구독 전): 누르면 구매 화면
                val open = s.chapterOpen(c)
                ChapterRow(
                    numeral = roman(i + 1), original = c.title[w.series.original], read = c.title[s.app.read].takeIf { it != c.title[w.series.original] },
                    readLang = s.app.read, originalLang = w.series.original,
                    meta = stringResource(R.string.chapter_meta, c.letters.size, done),
                    onClick = { s.go(if (open) Route.Chapter(id, i + 1) else Route.Purchase) },
                ) {
                    if (c.free && !s.entitlement.owns(id)) Capsule(stringResource(R.string.free), CapsuleKind.FILLED)
                    if (open) AudioChip(s, id, c.id) else Capsule(stringResource(R.string.chapter_locked))
                }
                Hair()
            }
            // 맺음말과 도록: 맺음말은 마지막 편지를 읽은 뒤에, 도록은 언제든
            if (w.outro != null) {
                val done = s.seriesDone(id)
                ChapterRow(
                    numeral = deco(w.series.original, "É", "E"), original = deco(w.series.original, "Épilogue", "Epilog"), read = stringResource(R.string.cover_epilogue),
                    readLang = s.app.read, originalLang = w.series.original,
                    meta = stringResource(if (done) R.string.cover_epilogue_open else R.string.cover_epilogue_locked),
                    onClick = { if (done) s.openOutro(id) },
                ) {}
                Hair()
            }
            ChapterRow(
                numeral = "✦", original = deco(w.series.original, "Catalogue", "Katalog"), read = stringResource(R.string.plates_title),
                readLang = s.app.read, originalLang = w.series.original,
                meta = stringResource(R.string.catalogue_progress, w.chapters.sumOf { c -> c.letters.count { s.progress(id, c.id, it.id).done } }, w.chapters.sumOf { it.letters.size }),
                onClick = { s.go(Route.Catalogue(id)) },
            ) {}
            Hair()
        }
    }
}

/** 챕터: 그림 표지 + 편지의 길 (세로 선 위에 편지마다 마디). 다 읽은 편지는 그림이 붙고, 앞으로 올 편지는 흐리게. */
@Composable
fun ChapterScreen(s: AppState, r: Route.Chapter) {
    val p = Ink.palette
    val w = s.work(r.series)
    val c = w.chapters[r.chapter - 1]
    val openable = s.openable(r.series).map { it.second.id }.toSet()
    s.version
    val readN = c.letters.count { s.progress(r.series, c.id, it.id).done }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            PlateImage(c.letters.firstOrNull { it.plate != null }?.let { l -> if (s.progress(r.series, c.id, l.id).done) l.plate?.image else c.letters.getOrNull(2)?.plate?.image }.orEmpty(), Modifier.fillMaxSize())
            // 바닥 그늘을 진하게 (글자 자리부터): 위 40% 는 그림 그대로, 그 아래로 바탕색이 55% → 100% 로 덮어 금빛 머리글 · 제목이 밝은 그림 위에서도 읽힌다
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to androidx.compose.ui.graphics.Color(0x73170F0A), 0.4f to androidx.compose.ui.graphics.Color(0x0D170F0A),
                0.62f to p.paper.copy(alpha = 0.55f), 0.85f to p.paper.copy(alpha = 0.85f), 1f to p.paper)))
            IconButton(stringResource(R.string.back), onClick = { s.back() }) { Chevron(androidx.compose.ui.graphics.Color(0xFFF7F0E1)) }
            Column(Modifier.align(Alignment.BottomStart).padding(start = Tokens.Space.s5, bottom = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Caps("${deco(w.series.original, "Chapitre", "Kapitel")} ${roman(r.chapter)} · ${c.letters.firstOrNull()?.place ?: ""} ${c.letters.firstOrNull()?.date?.take(4) ?: ""}", p.giltText, small = true, decorative = true)
                Text(c.title[w.series.original], style = Type.display, color = p.ink)
                Text(stringResource(R.string.chapter_meta, c.letters.size, readN) + " · " + c.title[uiLang()], style = Type.small.ui(), color = p.inkSoft)
            }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3)) {
            if (c.free) FreeLeftLine(s, r.series, Modifier.padding(bottom = Tokens.Space.s2))
            c.letters.forEachIndexed { i, l ->
                val pr = s.progress(r.series, c.id, l.id)
                val can = l.id in openable
                val days = s.daysUntil(r.series, l.id)
                val reading = can && !pr.done
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    // 길: 마디와 이어지는 선
                    Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(top = 26.dp).size(16.dp).then(
                            when { pr.done -> Modifier.background(p.giltText, androidx.compose.foundation.shape.CircleShape)
                                   reading -> Modifier.background(p.leaf, androidx.compose.foundation.shape.CircleShape).border(2.dp, p.ink, androidx.compose.foundation.shape.CircleShape)
                                   else -> Modifier.dashed(p.line) }))
                        if (i < c.letters.lastIndex) Box(Modifier.width(1.5.dp).weight(1f).background(p.line))
                    }
                    Row(
                        Modifier.weight(1f).padding(bottom = Tokens.Space.s2)
                            .then(if (reading) Modifier.background(p.leaf).border(1.dp, p.giltText.copy(alpha = 0.7f)) else Modifier)
                            .pressable(enabled = can) { s.open(r.series, c.id, l, null) }.padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s3),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Caps(stringResource(R.string.letter_n, roman(i + 1)), p.giltText, small = true)
                            Text(dateLine(l.date, l.place), style = Type.body.ui(), color = if (can) p.ink else p.hideInk)
                            Text(
                                when {
                                    pr.done -> stringResource(R.string.letter_done) + (l.plate?.let { " · " + it.title[uiLang()] } ?: "")
                                    pr.shown > 0 -> stringResource(R.string.letter_reading)
                                    can -> stringResource(R.string.letter_new)
                                    days == 1 -> stringResource(R.string.letter_eta)
                                    else -> stringResource(R.string.letter_eta_n, days)
                                },
                                style = Type.small.ui(), color = when { pr.done -> p.giltText; can -> p.ink; else -> p.hideInk }, maxLines = 1,
                            )
                            // 건너뛴 걸음: 편지 목록에서도 다시 하기 (편지를 열면 걸음 목록에 ‘다시 하기’)
                            val redo = if (pr.done) pr.toRedo.filter { it in s.letterModes(r.series, c.id, l) } else emptyList()
                            if (redo.isNotEmpty()) Text(stringResource(R.string.journey_redo, redo.size), style = Type.small.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = p.inkSoft)
                            // 처음부터 다시 보기: 첫 메시지부터 새 편지처럼 (걸음도 다시, 보상은 처음 한 번만). 줄을 누르면 지금처럼 전문 다시 읽기
                            if (pr.done) TextLink(stringResource(R.string.letter_again)) { s.replayLetter(r.series, c.id, l, null) }
                        }
                        if (pr.done) PlateThumb(l.plate?.image.orEmpty(), true, Modifier.size(52.dp))
                        else if (!can) Lock(p.hideInk)
                    }
                }
            }
        }
    }
}

/** 자물쇠 (아직 오지 않은 편지). */
@Composable
private fun Lock(c: androidx.compose.ui.graphics.Color) = androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
    val w = size.width
    val st = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx())
    drawRect(c, androidx.compose.ui.geometry.Offset(w * 0.18f, w * 0.45f), androidx.compose.ui.geometry.Size(w * 0.64f, w * 0.45f), style = st)
    drawArc(c, 180f, 180f, false, androidx.compose.ui.geometry.Offset(w * 0.3f, w * 0.12f), androidx.compose.ui.geometry.Size(w * 0.4f, w * 0.66f), style = st)
}

/** 작품 설정: 배울 언어 · 번역 줄 · 답장 방식 · 하루 편지 수. */
@Composable
fun SeriesSettingsScreen(s: AppState, id: String) {
    val p = Ink.palette
    val w = s.work(id)
    s.version
    val cur = s.seriesSettings(id)
    // 배우는 말 바꾸기는 한 번 묻고 (진도가 언어마다 따로라서)
    var learnAsk by remember { mutableStateOf<io.github.graviton94.todayletters.core.Lang?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.series_settings), s, help = null, showBack = true, showSettings = false)
        Page {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Portrait(w.portrait, w.fullName, Tokens.Size.avatar)
                Text(w.title[uiLang()], style = Type.heading.ui(), color = p.ink)
            }
            Caps(stringResource(R.string.set_learn), p.giltText, small = true)
            Choices(Langs.learnChoices(w.series, s.app.read).map { it to stringResource(langLabel(it)) }, cur.learn) { if (it != cur.learn) learnAsk = it }
            Text(stringResource(R.string.lang_notice_title), style = Type.small.ui(), color = p.inkSoft)

            Caps(stringResource(R.string.ob_modes), p.giltText, small = true)
            Column {
                listOf(
                    Triple(ReplyMode.MATCH, R.string.mode_match, R.string.mode_match_d),
                    Triple(ReplyMode.ALOUD, R.string.mode_aloud, R.string.mode_aloud_d),
                    Triple(ReplyMode.CONSTELLATION, R.string.mode_constellation, R.string.mode_constellation_d),
                ).forEach { (m, label, desc) ->
                    val on = m in cur.modes
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Checkbox) {
                            val next = if (on) cur.modes - m else cur.modes + m
                            if (next.any { it in io.github.graviton94.todayletters.core.Plays.inLetter }) s.update(id, cur.copy(modes = next))
                        },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
                    ) {
                        CheckMark(on)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(label), style = Type.body.ui(), color = p.ink)
                            Text(stringResource(desc, w.name[uiLang()]), style = Type.small.ui(), color = p.inkSoft)
                        }
                        ModePreview(m)
                    }
                    Hair()
                }
                Text(stringResource(R.string.modes_note), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(top = Tokens.Space.s2))
            }

            Caps(stringResource(R.string.set_show_read), p.giltText, small = true)
            Choices(listOf(true to stringResource(R.string.on), false to stringResource(R.string.off)), cur.showRead) { s.update(id, cur.copy(showRead = it)) }
            // 번역 줄 물러나기 (언어 등급에 맞춰, 기본 켜짐: A1 에서는 접지 않는다)
            if (cur.showRead) {
                Caps(stringResource(R.string.set_fade_read), p.giltText, small = true)
                Choices(listOf(true to stringResource(R.string.on), false to stringResource(R.string.off)), cur.autoFold) { s.update(id, cur.copy(autoFold = it)) }
                Text(stringResource(R.string.set_fade_read_d), style = Type.small.ui(), color = p.inkSoft)
            }

            // 도움 한 단계 더 (도움 사다리, core 의 Support): 등급은 그대로, 한 등급 아래의 힌트 · 번역 · 빈칸
            Caps(stringResource(R.string.set_easier), p.giltText, small = true)
            Choices(listOf(true to stringResource(R.string.on), false to stringResource(R.string.off)), cur.easier) { s.update(id, cur.copy(easier = it)) }
            Text(stringResource(R.string.set_easier_d), style = Type.small.ui(), color = p.inkSoft)

            // 기본 듣기 빠르기 (core 의 Listen, 시안 slow.html): 세 칸 + 등급에 맞춰 자동. 직접 고르면 자동이 꺼진다. 설명 줄 없이 이름표만
            Caps(stringResource(R.string.set_listen_speed), p.giltText, small = true)
            Choices(io.github.graviton94.todayletters.core.Listen.CHOICES.map { it to io.github.graviton94.todayletters.core.Listen.label(it) },
                io.github.graviton94.todayletters.core.Listen.speed(cur, s.support(id).band)) { s.update(id, cur.copy(listenAuto = false, listenSpeed = it)) }
            Caps(stringResource(R.string.set_listen_auto), p.giltText, small = true)
            Choices(listOf(true to stringResource(R.string.on), false to stringResource(R.string.off)), cur.listenAuto) { on ->
                // 끌 때는 지금 쓰던 빠르기를 그대로 고른 값으로
                s.update(id, if (on) cur.copy(listenAuto = true) else cur.copy(listenAuto = false, listenSpeed = io.github.graviton94.todayletters.core.Listen.speed(cur, s.support(id).band)))
            }

            // 알림(도착 · 저녁)은 앱 설정으로 옮김 (v21 4단계 U3)
            Text(stringResource(R.string.set_notice_moved), style = Type.small.ui(), color = p.inkSoft)
        }
    }
    learnAsk?.let { l ->
        Ask(stringResource(R.string.learn_confirm_t), stringResource(R.string.learn_confirm_yes), stringResource(R.string.cancel),
            onYes = { learnAsk = null; s.switchLearn(id, l) }, onNo = { learnAsk = null },
            body = stringResource(R.string.learn_confirm_b, stringResource(langLabel(cur.learn)), stringResource(langLabel(l))))
    }
}

/** 답장 방식 옆의 작은 그림: 별자리는 이어진 별, 따라 읽기는 소리 물결. */
@Composable
private fun ModePreview(m: ReplyMode) {
    val p = Ink.palette
    androidx.compose.foundation.Canvas(Modifier.size(62.dp, 38.dp).border(Tokens.Stroke.hair, p.hair)) {
        val w = size.width; val h = size.height
        if (m == ReplyMode.MATCH) {
            drawRect(p.line, androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.24f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawRect(p.line, androidx.compose.ui.geometry.Offset(w * 0.6f, h * 0.56f), androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.24f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawLine(p.gilt, androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.32f), androidx.compose.ui.geometry.Offset(w * 0.6f, h * 0.68f), 1.dp.toPx())
        } else if (m == ReplyMode.CONSTELLATION) {
            val a = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.68f)
            val b = androidx.compose.ui.geometry.Offset(w * 0.46f, h * 0.3f)
            val c = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.58f)
            drawLine(p.gilt, a, b, 1.dp.toPx())
            drawCircle(p.gilt, 3.dp.toPx(), a); drawCircle(p.gilt, 3.dp.toPx(), b)
            drawCircle(p.gilt, 3.dp.toPx(), c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        } else {
            val bars = listOf(0.25f, 0.5f, 0.75f, 0.4f, 0.62f, 0.3f, 0.18f)
            bars.forEachIndexed { i, k ->
                val x = w * (0.2f + i * 0.1f)
                drawLine(p.inkSoft, androidx.compose.ui.geometry.Offset(x, h * (0.5f - k / 2)), androidx.compose.ui.geometry.Offset(x, h * (0.5f + k / 2)), 2.5.dp.toPx())
            }
        }
    }
}

@Composable
fun Bordered(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, Ink.palette.hair).padding(Tokens.Space.s4)) { content() }

/** 표지의 ‘다운로드 · 27MB’: 처음 묶음을 아직 받지 않았을 때만. 받는 중이면 ‘받는 중…’. */
@Composable
private fun FirstPackButton(s: AppState, id: String) {
    s.version
    io.github.graviton94.todayletters.data.AssetFiles.generation
    val pack = s.downloads.first(id)?.takeIf { !s.downloads.ready(it) } ?: return
    val job = io.github.graviton94.todayletters.data.DownloadJob.state?.takeIf { it.series == id && it.chapter == pack.chapter && it.active }
    if (job != null) Secondary(stringResource(R.string.audio_getting)) { s.predownload(id, pack.chapter) }
    else Secondary(stringResource(R.string.dl_go, sizeLabel(s.downloads.remaining(pack)))) { s.predownload(id, pack.chapter) }
}

/** 장의 낭독 상태 (v19): 앱 안 · 받음 · 받는 중 · 미리 받기 · 크기. 도착 전 장도 미리 받을 수 있다. */
@Composable
private fun AudioChip(s: AppState, id: String, chapter: String) {
    s.version
    val pack = s.downloads.pack(id, chapter)
    // ‘받는 중’ 은 실제로 받고 있을 때만 (받을지 묻는 시트가 떠 있을 때는 아직 크기)
    val asking = io.github.graviton94.todayletters.data.DownloadJob.state?.let { it.series == id && it.active && (it.chapter == chapter || it.chapter == io.github.graviton94.todayletters.data.Downloads.ALL || (it.chapter == io.github.graviton94.todayletters.data.Downloads.FIRST && chapter in s.downloads.firstChapters(id))) } == true
    when {
        pack == null || s.downloads.ready(pack) -> Capsule(stringResource(R.string.audio_ready), CapsuleKind.DONE)
        asking -> Capsule(stringResource(R.string.audio_getting))
        else -> Capsule(stringResource(R.string.audio_ahead, sizeLabel(s.downloads.remaining(pack))), CapsuleKind.FILLED) { s.predownload(id, chapter) }
    }
}

/**
 * 장 한 줄 (v20 규칙): 제목은 혼자 한 줄 폭 전체 · 원제와 번역은 두 줄 · 캡슐은 아래 정보 줄에.
 * 긴 제목은 균형 줄바꿈으로 고르게 (Type.label 은 제목이 아니라서 여기서 Heading 으로).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChapterRow(
    numeral: String, original: String, read: String?, readLang: io.github.graviton94.todayletters.core.Lang, originalLang: io.github.graviton94.todayletters.core.Lang,
    meta: String, onClick: () -> Unit, capsules: @Composable () -> Unit,
) {
    val p = Ink.palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clickable(role = Role.Button, onClick = onClick).padding(vertical = Tokens.Space.s3),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
    ) {
        // 번호 칸은 폭을 고정 (I · III · VIII 이어도 제목이 같은 자리에서 시작)
        Text(numeral, style = Type.numeral, color = p.giltText, maxLines = 1, softWrap = false, modifier = Modifier.width(52.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val balanced = androidx.compose.ui.text.style.LineBreak.Heading
            val keep = io.github.graviton94.todayletters.core.Breaks::keepAll   // 어절 가운데서 끊기지 않게 (몇 번 거쳐도 같음)
            Text(keep(original), style = Type.label.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display, lineBreak = balanced).of(originalLang), color = p.ink)
            if (read != null) Text(keep(read), style = Type.small.copy(lineBreak = balanced).of(readLang), color = p.ink)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(keep(meta), style = Type.small.ui(), color = p.inkSoft)
                capsules()
            }
        }
    }
}
