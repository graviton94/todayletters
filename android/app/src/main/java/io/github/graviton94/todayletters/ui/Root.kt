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
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Nav
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.Stage
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 앱의 뿌리: 첫 화면 (켤 때마다) → 환영 한 장 (처음 설치 한 번) → 서재와 시리즈 안 화면들. */
@Composable
fun Root(s: AppState, onExit: () -> Unit) {
    val p = Ink.palette
    // 앱을 벗어나면 (홈 · 다른 앱 · 화면 끔) 짧은 소리와 녹음을 멈춘다. 편지 낭독은 알림과 함께 이어진다.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) s.leftApp() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalHaptics provides s.app.haptics, LocalStill provides s.reducedMotion) {
    Box(Modifier.fillMaxSize().background(p.paper)) {
        // 처음 소개에서 뒤로: 닫을지 묻는다 (메인은 Main 이 따로 묻는다)
        BackHandler(enabled = s.stage != Stage.MAIN && !s.askingExit) { s.askingExit = true }
        // 첫 화면에서 들어갈 때: 책장 소리와 함께 다음 화면이 천천히 밝아진다
        AnimatedContent(s.stage, transitionSpec = {
            if (s.reducedMotion) fadeIn(tween(Tokens.Motion.fadeMs)) togetherWith fadeOut(tween(Tokens.Motion.fadeMs))
            else fadeIn(tween(1200, delayMillis = 250)) togetherWith fadeOut(tween(700))
        }, label = "stage") { stage ->
        when (stage) {
            Stage.OPENING -> Opening(s)
            Stage.ONBOARDING -> Onboarding(s)
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
    // 맨 위 화면(서재)에서 뒤로: 바로 나가지 않고 닫을지 묻는다 (홈 단추로 내리는 건 묻지 않음)
    BackHandler(enabled = !s.settingsOpen && !s.askingExit && s.reward == null && s.levelUp == null && s.gradeUp == null && s.downloadAsk == null && s.startAsk == null && s.feedback == null && !s.crashAsk) { if (!s.back()) s.askingExit = true }
    val inSeries = Nav.inSeries(s.route) && s.route !is Route.Enter && s.current.isNotEmpty()
    // 시리즈 안 화면은 그 시리즈의 빛깔로 (서재 · 내 기록 · 전환은 제 색)
    val body: @Composable () -> Unit = {
        val p = Ink.palette
        Column(Modifier.fillMaxSize().background(p.paper).statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    s.route,
                    transitionSpec = {
                        val d = if (s.forward) 1 else -1
                        when {
                            s.reducedMotion -> fadeIn(tween(Tokens.Motion.fadeMs)) togetherWith fadeOut(tween(Tokens.Motion.fadeMs))
                            // 서재 → 전환: 카드가 화면 가득 커지듯 (전환 화면이 스스로 커진다)
                            targetState is Route.Enter || initialState is Route.Enter -> fadeIn(tween(Tokens.Motion.pageMs)) togetherWith fadeOut(tween(Tokens.Motion.pageMs))
                            else -> (fadeIn(tween(Tokens.Motion.pageMs)) + androidx.compose.animation.slideInHorizontally(tween(Tokens.Motion.pageMs)) { it / 5 * d }) togetherWith
                                (fadeOut(tween(Tokens.Motion.fadeMs)) + androidx.compose.animation.slideOutHorizontally(tween(Tokens.Motion.pageMs)) { -it / 8 * d })
                        }
                    },
                    label = "route",
                ) { r ->
                    when (r) {
                        Route.Library -> LibraryTab(s)
                        Route.Record -> RecordScreen(s)
                        Route.Medals -> MedalsScreen(s)
                        Route.Purchase -> PurchaseScreen(s)
                        is Route.Enter -> EnterScreen(s, r.series)
                        Route.Inbox -> Inbox(s)
                        Route.Words -> WordsTab(s)
                        Route.Gallery -> GalleryTab(s)
                        Route.Shop -> ShopTab(s)
                        Route.Exhibition -> ExhibitionScreen(s)
                        Route.Recital -> RecitalScreen(s)
                        Route.Week -> WeekScreen(s)
                        is Route.Series -> SeriesCover(s, r.series)
                        is Route.SeriesSettings -> SeriesSettingsScreen(s, r.series)
                        is Route.Chapter -> ChapterScreen(s, r)
                        is Route.Letter -> Room(s, r)
                        is Route.RoomInfo -> RoomInfo(s, r.room)
                        is Route.WarmUp -> WarmUpScreen(s, r)
                        is Route.SoundCheck -> SoundCheckScreen(s, r)
                        is Route.RecallQuiz -> RecallScreen(s, r)
                        is Route.Play -> Play(s, r)
                        is Route.Done -> Done(s, r.room)
                        is Route.Prologue -> PrologueScreen(s, r)
                        is Route.Welcome -> WelcomeSheet(s, r.series)
                        is Route.Outro -> OutroScreen(s, r.series)
                        is Route.Catalogue -> CatalogueScreen(s, r.series)
                        is Route.Collection -> CollectionScreen(s, r.series)
                        is Route.Shadow -> ShadowScreen(s, r)
                        is Route.Seal -> SealScreen(s, r)
                        is Route.Artwork -> Artwork(s, r)
                        is Route.Session -> Session(s, r)
                        Route.WordList -> WordListScreen(s)
                        Route.Quotes -> QuotesScreen(s)
                    }
                }
            }
            if (s.route is Route.Tab) Tabs(s)
        }
        // 눌러서 뜻 보기: 어느 화면에서 눌렀든 맨 위에 작은 카드
        GlossHost(s)
        if (s.settingsOpen) SettingsScreen(s)
        // 처음 시작 세 가지 (작품을 처음 열 때, 새로 쓰는 사람만): 받기 시트가 있으면 그 아래
        s.startAsk?.let { StartSheet(s, it) }
        // 앱 밖에 둔 장의 낭독 받기 (크기 안내 · 진행률 · 뒤로)
        s.downloadAsk?.let { DownloadSheet(s, it) }
        CoachLayer(s)
        // 배우는 말을 처음 바꿨을 때 한 번: 언어마다 진도가 따로
        LangNoticeHost(s)
        // 결제가 끝난 뒤 고마워요 한 장
        ThanksSheet(s)
        // 걸음 안 보상: 작은 “+2” (화면을 덮지 않음)
        RewardChip(s)
        // 보상 순간: 어느 화면에서든 맨 위에
        RewardOverlay(s)
        // 레벨 업: 보상 순간이 닫힌 뒤 한 장
        GradeUpOverlay(s)
        LevelUpOverlay(s)
        // 의견 보내기 (설정 › 의견 보내기) · 지난번에 앱이 멈췄으면 한 번 묻기
        s.feedback?.let { FeedbackSheet(s, it) }
        CrashAsk(s)
        // 새 버전: 업데이트 뒤 ‘새로 바뀐 것’ · 다 받으면 ‘다시 열기’ 띠 · 하루 한 번 묻기 (서재에서만)
        UpdateLayer(s)
    }
    if (inSeries) InSeries(s.currentWork) { body() } else body()
}

@Composable
private fun Tabs(s: AppState) {
    val p = Ink.palette
    Column {
        Hair()
        Row(Modifier.fillMaxWidth().background(p.paper)) {
            listOf(Route.Inbox to R.string.tab_inbox, Route.Words to R.string.tab_words, Route.Gallery to R.string.tab_gallery, Route.Shop to R.string.tab_shop)
                .forEach { (r, label) ->
                    val on = s.route == r
                    Box(
                        Modifier.weight(1f).heightIn(min = Tokens.Size.row).semantics { selected = on }
                            .clickable(role = Role.Tab) { s.go(r) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                            Text(stringResource(label), style = Type.small.ui(), color = if (on) p.ink else p.hideInk)
                            Box(Modifier.heightIn(min = 2.dp).fillMaxWidth(0.18f).background(if (on) p.giltText else p.paper))
                        }
                    }
                }
        }
    }
}

/** 지금 화면의 도움말 id (core 의 Coach 와 strings 의 coach_<id>_*). */
fun helpId(r: Route): String? = when (r) {
    Route.Inbox -> "inbox"
    Route.Words -> "words"
    Route.Gallery -> "gallery"
    Route.Shop -> "shop"
    Route.Library -> "library"
    is Route.Series -> "series"
    is Route.Letter -> "room"
    is Route.Play -> if (r.mode.name == "ALOUD") "play_aloud" else "play_constellation"
    else -> null
}

fun showsSettings(r: Route) = Nav.showsAppSettings(r)
