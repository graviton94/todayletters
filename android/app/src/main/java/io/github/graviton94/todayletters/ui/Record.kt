package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Levels
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/**
 * 서재 › 나의 기록 (모든 작품 합산, 하나뿐): 칭호 · 레벨 막대, 이어 읽기 · 편지 · 익힌 낱말, 지금 이용권 (→ 구매 화면),
 * 시리즈마다 진도 (배우는 말), 메달 여섯 (→ 전체 보기), 이번 주 · 일요일 낭독회. 작품 머리의 칭호 줄을 눌러도 여기로 온다.
 */
@Composable
fun RecordScreen(s: AppState) {
    val p = Ink.palette
    s.version
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    val st = s.medalStats()
    val have = s.medalsOwned
    val all = cabinetMedals(s)
    val lv = s.level
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.record_title), s, help = null, showBack = true, showSettings = false)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            // 칭호 · 레벨: 맨 위에서 (굴리지 않아도) 칭호 · Lv, 막대, 남은 XP 가 모두 보인다
            Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(
                    buildAnnotatedString {
                        append(Levels.title(s.medalBook.levels, lv.level, uiLang()))
                        withStyle(SpanStyle(color = p.giltText)) { append(" · Lv ${lv.level}") }
                    },
                    style = Type.title.ui(), color = p.ink,
                )
                Bar(lv.frac)
                Text(stringResource(R.string.level_left, lv.left, lv.level + 1), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.align(Alignment.End))
            }
            // 이어 읽기 · 편지 · 익힌 낱말
            Row(Modifier.fillMaxWidth()) {
                listOf("${s.streak}" to R.string.lib_stat_streak, "${st.lettersDone}" to R.string.record_letters, "${s.learning.sumOf { s.mine(it).size }}" to R.string.record_words).forEach { (v, l) ->
                    Column(Modifier.weight(1f)) {
                        Text(v, style = Type.title.copy(fontFamily = Faces.display), color = p.ink)
                        Text(stringResource(l), style = Type.small.ui(), color = p.inkSoft)
                    }
                }
            }
            // 언어 등급 (배우는 말마다): 관람 레벨이 ‘얼마나 했나’라면 이것은 ‘얼마나 읽을 수 있나’
            GradeBlock(s)
            // 지금 이용권: 무료 · 소장한 시리즈 · 구독 (숫자 셋 바로 아래, 누르면 구매 화면)
            val ent = s.entitlement
            val pass = when {
                ent.subscribed -> stringResource(R.string.pass_sub)
                ent.owned.isNotEmpty() -> s.baseWorks.filter { it.series.id in ent.owned }.map { stringResource(R.string.pass_owned, it.name[uiLang()]) }.joinToString(" · ")
                else -> stringResource(R.string.pass_free)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).background(p.leaf).border(1.dp, if (ent.subscribed) p.line else p.gilt)
                    .pressable { s.go(Route.Purchase) }.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                Text(stringResource(R.string.pass_line, pass), style = Type.body.ui(), color = p.ink, modifier = Modifier.weight(1f))
                if (!ent.subscribed) Text(stringResource(R.string.unlock_all), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.giltText)
                Text("›", style = Type.heading, color = p.giltText)
            }
            // 시리즈마다 진도 (배우는 말과 함께)
            Column {
                s.baseWorks.forEach { w ->
                    val id = w.series.id
                    val learn = s.store.series(id)?.learn ?: w.series.original
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${w.name[uiLang()]} · ${stringResource(langLabel(learn))}", style = Type.body.ui(), color = p.ink, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.lib_letters, s.doneCount(id), w.chapters.sumOf { it.letters.size }), style = Type.small.ui(), color = p.inkSoft)
                    }
                    Hair()
                }
            }
            // 메달: 여섯 칸 (받은 것 → 다음 것), 나머지는 전체 보기
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.record_medals), style = Type.heading.ui(), color = p.ink, modifier = Modifier.weight(1f))
                Text("${all.count { it.id in have }} / ${all.size}", style = Type.small.copy(fontFamily = Faces.display), color = p.giltText)
            }
            val next = all.filter { it.id !in have && it.series == null }.groupBy { it.family }.values.mapNotNull { ms -> ms.minByOrNull { it.threshold } }.toSet()
            featuredMedals(s, 6).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { m ->
                        MedalCell(s, m, st, earned = m.id in have, isNext = m in next || (m.series != null && m.id !in have), size = 72.dp,
                            modifier = Modifier.weight(1f)) { detail = m.id }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, p.line).pressable { s.go(Route.Medals) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.record_all) + " ›", style = Type.body.ui(), color = p.ink) }
            Hair()
            // 이번 주 · 일요일 낭독회 (지금 들어가 있는 작품의 것)
            run {
                val wk = s.week(s.current)
                RecordRow(stringResource(R.string.week_title), stringResource(R.string.week_line, wk.done.size, wk.letters, wk.owned)) { s.go(Route.Week) }
            }
            val sunday = io.github.graviton94.todayletters.core.Recital.isSunday(s.today)
            RecordRow(stringResource(R.string.recital_title), stringResource(if (s.recitalDone) R.string.recital_done else if (sunday) R.string.recital_today else R.string.recital_sunday)) { s.go(Route.Recital) }
            Spacer(Modifier.height(Tokens.Space.s5))
        }
    }
    detail?.let { mid -> s.medalBook.medal(mid)?.let { m -> MedalDetail(s, m) { detail = null } } }
}

/** 나의 기록 › 메달 전체: 가족마다 진열장. */
@Composable
fun MedalsScreen(s: AppState) {
    s.version
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.cabinet_title), s, help = null, showBack = true, showSettings = false)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            MedalCabinet(s) { detail = it.id }
            Spacer(Modifier.height(Tokens.Space.s5))
        }
    }
    detail?.let { mid -> s.medalBook.medal(mid)?.let { m -> MedalDetail(s, m) { detail = null } } }
}

/** 나의 기록의 한 줄 (이번 주 · 낭독회): 이름 · 한 줄 · ›. */
@Composable
private fun RecordRow(title: String, line: String, onClick: () -> Unit) {
    val p = Ink.palette
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink)
            Text(line, style = Type.small.ui(), color = p.inkSoft)
        }
        Text("›", style = Type.heading, color = p.inkSoft)
    }
    Hair()
}

/** 나의 기록 › 언어 등급: 배우는 말마다 등급 · 단, 다음 단까지의 막대, 떠올릴 수 있는 낱말 · 읽을 수 있는 비율 (지난주와 견줘), 다음에 필요한 것. */
@Composable
private fun GradeBlock(s: AppState) {
    val p = Ink.palette
    val langs = s.learning
    if (langs.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(stringResource(R.string.grade_title), style = Type.heading.ui(), color = p.ink)
        langs.forEach { lang ->
            val g = s.grade(lang)
            val known = s.mine(lang).size
            val recent = s.recentCoverage(lang)
            val words = io.github.graviton94.todayletters.core.Reading.words
            val from = words[g.index - 1]
            val to = words.getOrNull(g.index)
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.grade_line, stringResource(langLabel(lang)), g.band.name, g.step))
                },
                style = Type.title.ui(), color = p.ink,
            )
            Bar(if (to == null) 1f else ((known - from).toFloat() / (to - from)).coerceIn(0f, 1f))
            val delta = s.lastWeekCoverage[lang]?.let { recent - it }
            Text(stringResource(R.string.grade_cov, recent) + (delta?.let { " · " + stringResource(R.string.grade_delta, it) } ?: ""),
                style = Type.small.ui(), color = p.inkSoft)
            val needCov = io.github.graviton94.todayletters.core.Reading.coverageToNext(g)?.takeIf { recent < it }
            val needWords = io.github.graviton94.todayletters.core.Reading.wordsToNext(g, known)
            Text(
                when {
                    needWords == null -> stringResource(R.string.grade_top)
                    needWords == 0 && needCov != null -> stringResource(R.string.grade_next_cov, needCov)
                    else -> stringResource(R.string.grade_next_words, needWords)
                },
                style = Type.small.ui(), color = p.giltText,
            )
        }
        Text(stringResource(R.string.grade_note), style = Type.small.ui(), color = p.inkSoft)
        Hair()
    }
}
