package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.CardRef
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.Mine
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.SpeakCheck
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

// ── 내 이야기로 바꿔 말하기 (core [Mine], 회화의 "mine") ──────────────────
// 회화 걸음이 끝난 뒤 ([TalkStep] 의 ‘마치기’) 한 장: 내 줄 하나의 빈칸에 넣을 보기 셋 → 골라서 말해 보기 (원래 소리가 없으니 글자로 어림한 음절과 견준다).
// 고른 문장은 복습 카드 ‘내가 만든 말’ ([CardRef.mine], 꼬리 "n<보기>", 소리 없음).

/** 이 회화 뒤에 ‘내 이야기로’ 를 하는가: 데이터가 있고 (배우는 말 문장이 만들어지는 보기 둘 이상), 다시 하기 · 튜토리얼 다시 하기가 아님. */
fun AppState.mineDue(r: Route.Letter): Boolean {
    val (chapter, letter) = letterOf(r)
    return !r.epilogue && !replaying(r.series, chapter, letter.id) && Mine.available(letter.talk, room(r.series).learn)
}

/** 고른 보기를 카드함에 (내가 만든 말). 다시 하기면 들이지 않는다. */
fun AppState.mineChosen(series: String, chapter: String, letter: Letter, opt: Int) {
    if (replaying(series, chapter, letter.id)) return
    admit(listOf(CardRef.mine(series, chapter, letter.id, opt, deckOf(series))))
    bump()
}

/** 내 이야기로 녹음 (편지마다 하나, 기기 안). */
private fun AppState.mineTake(series: String, chapter: String, letter: String) = java.io.File(ctx.filesDir, "talk/${space(series)}_${chapter}_$letter/n.wav")

@Composable
fun MineStep(s: AppState, r: Route.Play, onDone: () -> Unit) {
    val (chapter, letter) = s.letterOf(r.room)
    MinePane(s, r.room.series, chapter, letter, onDone)
}

/**
 * ‘이번엔 내 이야기로’: 빈칸 문장 (배우는 말 + 앱 글자 언어 뜻), 보기 셋 (배우는 말 · 뜻), 고르면 빈칸이 채워지고 ‘말해 보기’ → 녹음 칸.
 * 목소리가 들리고 음절이 비슷하면 (두 번째는 목소리만으로) ‘마치기’. ‘지금은 소리 내기 어려워요 · 건너뛰기’ 는 언제나 (고른 문장은 카드로).
 */
@Composable
internal fun MinePane(s: AppState, series: String, chapter: String, letter: Letter, onDone: () -> Unit) {
    val p = Ink.palette
    val learn = s.room(series).learn
    val ui = uiLang()
    val talk = letter.talk
    val opts = remember(letter.id, learn) { Mine.options(talk, learn) }
    var pick by rememberSaveable(letter.id) { mutableIntStateOf(-1) }
    var speaking by rememberSaveable(letter.id) { mutableStateOf(false) }
    var spoken by remember(letter.id, pick) { mutableStateOf(false) }
    val sentence = (if (pick >= 0) Mine.sentence(talk, pick, learn) else Mine.blanked(talk, learn)).orEmpty()
    val meaning = if (ui == learn) "" else (if (pick >= 0) Mine.sentence(talk, pick, ui) else Mine.blanked(talk, ui)).orEmpty()
    val file = remember(letter.id) { s.mineTake(series, chapter, letter.id).also { it.delete() } }
    DisposableEffect(Unit) { onDispose { s.pcm.onFrame = null; s.pcm.stop(); s.recorder.release() } }
    fun finish() {
        s.narrator.stop()
        if (pick >= 0) s.mineChosen(series, chapter, letter, pick)
        onDone()
    }
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(if (spoken) 1 else 0, 1, onClose = { finish() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            PracticeTitle("IN MY WORDS", stringResource(R.string.mine_title))
            Text(stringResource(R.string.mine_d), style = Type.body.ui(), color = p.inkSoft)
            Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(sentence, style = Type.title.copy(fontFamily = Faces.display).of(learn), color = p.ink)
                if (meaning.isNotBlank()) Text(meaning, style = Type.base.of(ui), color = p.inkSoft)
            }
            // 보기 셋: 배우는 말 · 뜻 (고르면 금빛 테)
            opts.forEach { i ->
                val on = i == pick
                val target = talk?.mine?.opts?.getOrNull(i)?.get(learn).orEmpty()
                val tr = if (ui == learn) "" else talk?.mine?.opts?.getOrNull(i)?.get(ui).orEmpty()
                Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).border(if (on) 2.dp else 1.dp, if (on) p.giltText else p.line)
                    .pressable(role = Role.Button) { if (pick != i) { pick = i; speaking = false; s.recorder.stopPlaying(); file.delete() } }
                    .padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2), verticalArrangement = Arrangement.Center) {
                    Text(target, style = Type.body.of(learn), color = p.ink)
                    if (tr.isNotBlank()) Text(tr, style = Type.small.of(ui), color = p.inkSoft)
                }
            }
            if (speaking && pick >= 0) {
                if (spoken) Text(stringResource(R.string.speak_line_ok), style = Type.small.ui(), color = p.correct, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                // 원래 소리가 없다: 글자로 어림한 음절과 견준다 (core 의 SpeakCheck.estimate)
                SpeakLine(s, null, file, key = Triple(letter.id, pick, learn), estimate = SpeakCheck.estimate(sentence, learn),
                    sayLike = s.sayLike(series, sentence)) { spoken = true }
            }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            when {
                spoken -> Primary(stringResource(R.string.mine_finish), shine = true) { finish() }
                !speaking -> Primary(stringResource(R.string.mine_say), enabled = pick >= 0, shine = pick >= 0) { speaking = true }
                else -> Unit
            }
            if (!spoken) Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable(role = Role.Button) { finish() }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.mine_skip), style = Type.small.ui().copy(textDecoration = TextDecoration.Underline), color = p.inkSoft)
            }
        }
    }
}
