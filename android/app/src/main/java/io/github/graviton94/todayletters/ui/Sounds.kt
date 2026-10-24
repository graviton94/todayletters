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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.SoundChapter
import io.github.graviton94.todayletters.core.SoundDeck
import io.github.graviton94.todayletters.core.Sounds
import io.github.graviton94.todayletters.core.VocabSet
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import java.util.concurrent.ConcurrentHashMap

// ── 이번 장의 소리 (core [Sounds], data/<작품>/sounds[_en|_ko].json) ──────────────────
// I~VIII 장의 첫 편지를 처음 열 때 (장마다 · 배우는 말마다 한 번) 예열 · 편지 앞에 1분. 예열을 끈 사람에게도 (소리 파일을 받은 뒤에만 · 테스트에는 파일이 없어 안 나온다).
// 저장은 SharedPreferences "letters": snd:<진도 이름 (작품 · 작품@en …)> = 본 장 id 를 줄바꿈으로.
// 소리 s_<말>_<slug>.m4a 는 audio/<작품>/S/sounds/ (R2 묶음 "S", 다른 장을 받을 때 함께). 낱말 소리가 이 기기에 다 있을 때만 띄운다.

private val soundFiles = ConcurrentHashMap<String, SoundDeck>()

/** 이번 장의 소리 파일 (assets letters/<작품>/sounds[_en|_ko].json). 없으면 빈 덱. */
fun soundsOf(ctx: Context, series: String, deck: String): SoundDeck = soundFiles.getOrPut("$series/$deck") {
    Sounds.parse(runCatching { ctx.assets.open("letters/$series/${VocabSet.file("sounds", deck)}").bufferedReader().use { it.readText() } }.getOrNull())
}

/** 지금 배우는 말의 이 작품 소리 덱 (원어 sounds.json · 영어 sounds_en · 한국어 sounds_ko). */
fun AppState.soundDeck(series: String): SoundDeck {
    val w = works.firstOrNull { it.series.id == series } ?: return SoundDeck.EMPTY
    return soundsOf(ctx, series, VocabSet.deckFor(room(series).learn, w.series.original))
}

private fun AppState.soundPrefs() = ctx.getSharedPreferences("letters", Context.MODE_PRIVATE)

/** 이번 장의 소리를 본 장 (지금 배우는 말). */
fun AppState.soundsSeen(series: String): Set<String> =
    soundPrefs().getString("snd:${space(series)}", "").orEmpty().split("\n").filter { it.isNotBlank() }.toSet()

private fun AppState.markSoundsSeen(series: String, chapter: String) =
    soundPrefs().edit().putString("snd:${space(series)}", (soundsSeen(series) + chapter).joinToString("\n")).apply()

/** 낱말 소리 (이 기기에 있으면 경로, 없으면 null). */
fun AppState.soundClip(series: String, learn: Lang, word: String): String? =
    narrator.path(series, Sounds.CHAPTER, Sounds.LETTER, Sounds.audio(learn, word)).takeIf { narrator.has(it) }

/** 새 편지를 처음 열 때 이번 장의 소리를 먼저 하는가 ([AppState.open]): 예열을 하는 사람, 장의 첫 편지, 안 본 장, 낱말 소리가 다 있음. */
fun AppState.soundsDue(series: String, chapter: String, letter: Letter): Boolean {
    val index = work(series).chapters.firstOrNull { it.id == chapter }?.letters?.indexOf(letter) ?: return false
    val deck = soundDeck(series)
    if (!Sounds.due(deck, chapter, index, soundsSeen(series))) return false
    val learn = room(series).learn
    return deck.chapters[chapter]!!.words.all { soundClip(series, learn, it) != null }
}

/** 마쳤거나 건너뛰었다: 본 장으로 적고, 예열 (할 것이 있으면) 또는 편지 방으로 (이 화면은 뒤로 가기에 남기지 않는다). */
fun AppState.finishSounds(r: Route.SoundCheck) {
    val (chapter, letter) = letterOf(r.room)
    markSoundsSeen(r.room.series, chapter)
    narrator.stop()
    if (route == r) stack.removeAt(stack.lastIndex)
    go(if (warmUpDue(r.room.series, chapter, letter)) Route.WarmUp(r.room) else r.room)
}

@Composable
fun SoundCheckScreen(s: AppState, r: Route.SoundCheck) {
    val (chapter, _) = s.letterOf(r.room)
    val c = s.soundDeck(r.room.series).chapters[chapter]
    if (c == null) { LaunchedEffect(Unit) { s.finishSounds(r) }; return }
    SoundsPane(s, r.room.series, c, s.room(r.room.series).learn, seed = chapter.hashCode().toLong()) { s.finishSounds(r) }
}

/**
 * 이번 장의 소리 한 장: 요령 한 줄 · 첫 쌍 (뜻 · 듣기) → ‘들린 말은?’ 네 판 (한 낱말을 들려주고 둘 가운데 고르기) → 넷 중 셋이면 끝.
 * 아니면 ‘한 번 더’ 또는 건너뛰기. [stage0] 은 처음 자리 (-1 소개, 0~ 판: 화면 사진용).
 */
@Composable
internal fun SoundsPane(s: AppState, series: String, c: SoundChapter, learn: Lang, seed: Long, stage0: Int = -1, onDone: () -> Unit) {
    val p = Ink.palette
    val ui = uiLang()
    fun clip(w: String) = s.soundClip(series, learn, w)
    fun play(w: String, speed: Float? = null) { clip(w)?.let { s.narrator.play(it, speed) } }
    // -1 소개 · 0..판 수-1 판 · 판 수 결과
    var stage by remember { mutableIntStateOf(stage0) }
    var again by remember { mutableIntStateOf(0) }
    val rounds = remember(again) { Sounds.rounds(c.pairs.size, seed * 31 + again + s.today) }
    var right by remember(again) { mutableIntStateOf(0) }
    var picked by remember(again, stage) { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) { val me = s.narrator.scene; onDispose { s.narrator.stop(me) } }
    // 판이 바뀌면 그 판의 낱말을 한 번
    // (판마다 화면 안의 단계: 앞 판의 늦은 소리는 받지 않고, 덮개가 걷힌 뒤에)
    LaunchedEffect(stage, again) {
        s.narrator.step("sounds" to (stage to again))
        s.uncovered()
        rounds.getOrNull(stage)?.let { rd -> c.pairs[rd.pair].let { (a, b) -> play(if (rd.second) b else a) } }
    }
    var leaving by remember { mutableStateOf(false) }
    val over = stage >= rounds.size
    val passed = over && Sounds.passed(right, rounds.size)

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        // × 는 나가기 (한 번 묻기), 건너뛰기는 위 오른쪽 글자 하나 (편지는 그대로 열린다)
        PracticeHeader(stage.coerceIn(0, rounds.size), rounds.size, onClose = { leaving = true }, onSkip = if (passed) null else ({ s.narrator.stop(); onDone() }))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Caps(stringResource(R.string.sounds_minute), p.giltText, small = true)
            Text(stringResource(R.string.sounds_title, c.nameIn(ui)), style = Type.title.ui(), color = p.ink)
            c.tipIn(ui).takeIf { it.isNotBlank() }?.let { Text(it, style = Type.body.ui(), color = p.inkSoft) }
            when {
                // 소개: 첫 쌍을 뜻 · 듣기와 함께
                stage < 0 -> {
                    Text(stringResource(R.string.sounds_pair), style = Type.small.ui(), color = p.giltText)
                    val (a, b) = c.pairs.first()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        listOf(a, b).forEach { w ->
                            Column(Modifier.weight(1f).background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s3),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                                Text(w, style = Type.title.copy(fontFamily = Faces.display).of(learn), color = p.ink, textAlign = TextAlign.Center)
                                c.meaning(w, ui).takeIf { it.isNotBlank() }?.let { Text(it, style = Type.small.ui(), color = p.inkSoft, textAlign = TextAlign.Center) }
                                if (clip(w) != null) ListenButtons(play = { sp -> play(w, sp) })
                            }
                        }
                    }
                }
                // 한 판: 들린 말은?
                !over -> {
                    val rd = rounds[stage]
                    val (a, b) = c.pairs[rd.pair]
                    val heard = if (rd.second) 1 else 0
                    val words = listOf(a, b)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        Text(stringResource(R.string.sounds_which), style = Type.heading.ui(), color = p.ink, modifier = Modifier.weight(1f))
                        if (clip(words[heard]) != null) ListenButtons(play = { sp -> play(words[heard], sp) }, label = stringResource(R.string.sounds_listen))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        words.forEachIndexed { i, w ->
                            val pick = picked
                            val edge = when {
                                pick == null -> p.line
                                i == heard -> p.correct
                                i == pick -> p.wrong
                                else -> p.hair
                            }
                            Box(Modifier.weight(1f).heightIn(min = 72.dp).border(if (pick != null && (i == heard || i == pick)) 2.dp else 1.dp, edge)
                                .pressable(enabled = pick == null, role = Role.Button) {
                                    picked = i
                                    if (i == heard) right++
                                    s.cue(i == heard)
                                }.padding(Tokens.Space.s3), contentAlignment = Alignment.Center) {
                                Text(w, style = Type.title.copy(fontFamily = Faces.display).of(learn), color = p.ink, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    picked?.let { pk ->
                        Text(if (pk == heard) stringResource(R.string.sounds_right) else stringResource(R.string.sounds_wrong, words[heard]),
                            style = Type.body.ui(), color = if (pk == heard) p.correct else p.inkSoft, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
                // 결과
                else -> {
                    if (passed) Text(stringResource(R.string.sounds_done), style = Type.heading.ui(), color = p.giltText)
                    Text(stringResource(R.string.sounds_score, right, rounds.size), style = Type.body.ui(), color = p.ink)
                }
            }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            when {
                stage < 0 -> Primary(stringResource(R.string.sounds_start), shine = true) { s.narrator.stop(); stage = 0 }
                !over -> MainButton(stringResource(R.string.recall_next), enabled = picked != null, reason = stringResource(R.string.need_heard)) { s.narrator.stop(); stage++ }
                passed -> Primary(stringResource(R.string.sounds_open), shine = true) { onDone() }
                else -> Primary(stringResource(R.string.sounds_retry), shine = true) { again++; stage = 0 }
            }
        }
    }
    if (leaving) Ask(stringResource(R.string.leave_title), stringResource(R.string.leave_yes), stringResource(R.string.leave_no),
        onYes = { leaving = false; s.narrator.stop(); s.back() }, onNo = { leaving = false })
}
