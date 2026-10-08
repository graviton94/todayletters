package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Nav
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.Stage
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 앱의 뿌리: 시작 단계 (오프닝 → 처음 소개 또는 오늘의 봉투) 다음에 이름표 화면들. */
@Composable
fun Root(s: AppState, onExit: () -> Unit) {
    val p = Ink.palette
    androidx.compose.runtime.CompositionLocalProvider(LocalHaptics provides s.app.haptics) {
    Box(Modifier.fillMaxSize().background(p.paper)) {
        // 오프닝 · 처음 소개 · 오늘의 봉투에서 뒤로: 닫을지 묻는다 (메인은 Main 이 따로 묻는다)
        BackHandler(enabled = s.stage != Stage.MAIN && !s.askingExit) { s.askingExit = true }
        AnimatedContent(s.stage, transitionSpec = { fadeIn(tween(Tokens.Motion.pageMs * 2)) togetherWith fadeOut(tween(Tokens.Motion.pageMs)) }, label = "stage") { stage ->
        when (stage) {
            Stage.OPENING -> Opening(s)
            Stage.ONBOARDING -> Onboarding(s)
            Stage.TODAY -> Today(s)
            Stage.MAIN -> Main(s, onExit)
        }
        }
        if (s.askingExit) Ask(
            stringResource(R.string.exit_title), stringResource(R.string.exit_yes), stringResource(R.string.exit_no),
            onYes = { s.askingExit = false; onExit() }, onNo = { s.askingExit = false },
        )
    }
    }
}

@Composable
private fun Main(s: AppState, onExit: () -> Unit) {
    val p = Ink.palette
    // 맨 위 화면에서 뒤로: 바로 나가지 않고 닫을지 묻는다 (홈 단추로 내리는 건 묻지 않음)
    BackHandler(enabled = !s.settingsOpen && !s.askingExit) { if (!s.back()) s.askingExit = true }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                s.route,
                transitionSpec = {
                    val d = if (s.forward) 1 else -1
                    if (s.reducedMotion) fadeIn(tween(Tokens.Motion.fadeMs)) togetherWith fadeOut(tween(Tokens.Motion.fadeMs))
                    else (fadeIn(tween(Tokens.Motion.pageMs)) + androidx.compose.animation.slideInHorizontally(tween(Tokens.Motion.pageMs)) { it / 5 * d }) togetherWith
                        (fadeOut(tween(Tokens.Motion.fadeMs)) + androidx.compose.animation.slideOutHorizontally(tween(Tokens.Motion.pageMs)) { -it / 8 * d })
                },
                label = "route",
            ) { r ->
                when (r) {
                    Route.Inbox -> Inbox(s)
                    Route.Library -> LibraryTab(s)
                    Route.Words -> WordsTab(s)
                    Route.Gallery -> GalleryTab(s)
                    is Route.Series -> SeriesCover(s, r.series)
                    is Route.SeriesSettings -> SeriesSettingsScreen(s, r.series)
                    is Route.Chapter -> ChapterScreen(s, r)
                    is Route.Letter -> Room(s, r)
                    is Route.RoomInfo -> RoomInfo(s, r.room)
                    is Route.Play -> Play(s, r)
                    is Route.Done -> Done(s, r.room)
                    is Route.Artwork -> Artwork(s, r)
                    is Route.Review -> WordsTab(s)
                    is Route.Purchase -> SeriesCover(s, r.series)
                    Route.Settings -> Inbox(s)
                }
            }
        }
        if (s.route is Route.Tab) Tabs(s)
    }
    if (s.settingsOpen) SettingsScreen(s)
    CoachLayer(s)
}

@Composable
private fun Tabs(s: AppState) {
    val p = Ink.palette
    Column {
        Rule()
        Row(Modifier.fillMaxWidth().background(p.paper)) {
            listOf(Route.Inbox to R.string.tab_inbox, Route.Library to R.string.tab_library, Route.Words to R.string.tab_words, Route.Gallery to R.string.tab_gallery)
                .forEach { (r, label) ->
                    val on = s.route == r
                    Box(
                        Modifier.weight(1f).heightIn(min = Tokens.Size.row).semantics { selected = on }
                            .clickable(role = Role.Tab) { s.go(r) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                            Text(stringResource(label), style = Type.small.ui(), color = if (on) p.ink else p.inkSoft)
                            Box(Modifier.heightIn(min = Tokens.Stroke.progress).fillMaxWidth(0.3f).background(if (on) p.giltText else p.paper))
                        }
                    }
                }
        }
    }
}

/** 지금 화면의 도움말 id (core 의 Coach 와 strings 의 coach_<id>_*). */
fun helpId(r: Route): String? = when (r) {
    Route.Inbox -> "inbox"
    Route.Library -> "library"
    is Route.Series -> "series"
    is Route.Letter -> "room"
    is Route.Play -> if (r.mode.name == "ALOUD") "play_aloud" else "play_constellation"
    else -> null
}

fun showsSettings(r: Route) = Nav.showsAppSettings(r)
