package io.github.graviton94.todayletters

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.graviton94.todayletters.core.Exercises
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.ThemeMode
import io.github.graviton94.todayletters.core.TypingPace
import io.github.graviton94.todayletters.data.Store
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.TodayLettersTheme
import io.github.graviton94.todayletters.ui.AppState
import io.github.graviton94.todayletters.ui.ChapterScreen
import io.github.graviton94.todayletters.ui.Done
import io.github.graviton94.todayletters.ui.Inbox
import io.github.graviton94.todayletters.ui.WordsTab
import io.github.graviton94.todayletters.ui.LibraryTab
import io.github.graviton94.todayletters.ui.Onboarding
import io.github.graviton94.todayletters.ui.Play
import io.github.graviton94.todayletters.ui.Room
import io.github.graviton94.todayletters.ui.finishWarm
import io.github.graviton94.todayletters.ui.warmCards
import io.github.graviton94.todayletters.ui.SeriesSettingsScreen
import io.github.graviton94.todayletters.ui.Session
import io.github.graviton94.todayletters.ui.QuotesScreen
import io.github.graviton94.todayletters.ui.Artwork
import io.github.graviton94.todayletters.ui.postcard
import io.github.graviton94.todayletters.data.DownloadJob
import org.junit.Rule
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RuntimeEnvironment
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * 화면 사진: 기기 없이 화면을 PNG 로 그려 검토한다 (screens 가지에 올라감). 앱에는 들어가지 않는다.
 * 움직임은 멈춘 시계로 일정 시간만큼만 흘려서 찍는다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w393dp-h852dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** [tutorial] 이면 처음 설치의 튜토리얼 중 (서재 강조 · 걸음마다 안내 한 줄). 아니면 마친 것으로 (예전 화면 사진 그대로). */
    private fun state(onboarded: Boolean, tutorial: Boolean = false): AppState {
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().clear().commit()
        // 처음 시작 세 가지는 따로 찍는다 (start*): 다른 사진에서는 이미 쓰던 사람으로
        Store(ctx).startLegacy(true)
        if (onboarded) Store(ctx).onboarded = true
        if (onboarded && !tutorial) Store(ctx).tutorialDone = true
        // 알림 권한 앞 한 줄 (편지 완료 뒤 대화창) 은 따로 찍는다: 다른 사진에서는 이미 물은 것으로 (대화창은 화면 뿌리가 둘이 된다)
        Store(ctx).notifyAsked = true
        // 편지 전 예열 (ui/WarmUp.kt) 은 따로 찍는다 (il3 · il4): 다른 사진에서는 편지를 열면 바로 편지 방
        listOf("vincent", "mozart", "napoleon", "goya").forEach { ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().putInt("s:$it:warm", 0).commit() }
        val s = AppState(ctx, deepLink = false)
        s.update(s.app.copy(pace = TypingPace.INSTANT))
        // 편지 도착 연출(2단계 D1)은 따로 찍는다: 다른 편지 방 사진에서는 이미 본 것으로
        s.works.forEach { w -> w.chapters.forEach { c -> c.letters.forEach { l -> s.store.markArrived(s.key(w.series.id, c.id, l.id)) } } }
        return s
    }

    /** [series] 가 있으면 앱처럼 그 시리즈의 빛깔로 칠한다 (시리즈 안 화면). */
    private fun shot(name: String, dark: Boolean, ms: Long = 2500, act: () -> Unit = {}, series: AppState? = null, after: Long = 900, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            TodayLettersTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                if (series != null) io.github.graviton94.todayletters.ui.InSeries(series.currentWork) { Box(Modifier.fillMaxSize().background(Ink.palette.paper)) { content() } }
                else Box(Modifier.fillMaxSize().background(Ink.palette.paper)) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(ms)
        act()
        rule.mainClock.advanceTimeBy(after)
        rule.onRoot().captureRoboImage("build/screens/$name.png")
        audit(name)
    }

    /**
     * 접근성 점검 (v21 4단계 R2): 누를 수 있는데 이름이 없는 것, 48dp 보다 작은 것을 적어 둔다.
     * 실패시키지 않고 build/screens/a11y.txt 에 모은다 (사람이 보고 고친다).
     */
    private fun audit(name: String) {
        val d = ctx.resources.displayMetrics.density
        val found = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { n ->
            val c = n.config
            val label = (c.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + c.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
                .joinToString(" ").trim()
            val w = (n.size.width / d).toInt(); val h = (n.size.height / d).toInt()
            val issues = buildList { if (label.isEmpty()) add("이름 없음"); if (w < 48 || h < 48) add("작음 ${w}×${h}dp") }
            if (issues.isEmpty()) null else "  ${issues.joinToString(" · ")}  [${label.take(40)}]"
        }
        if (found.isEmpty()) return
        val f = java.io.File("build/screens/a11y.txt"); f.parentFile?.mkdirs()
        synchronized(ScreensTest::class.java) { f.appendText("$name\n" + found.joinToString("\n") + "\n") }
    }

    /** 큰 글씨 (시스템 글자 크기 2배): 넘치거나 잘리는 데가 없는지 찍는다. */
    private fun large(name: String, ms: Long = 2500, series: AppState? = null, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(2f)
        try { shot("${name}_large", false, ms, series = series, content = content) } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    /** 편지를 앱처럼 끝낸다: 열기 → 다 읽기 → 걸음마다 마치기 (replyAndContinue → finished · 편지 완료). 보상 순간은 닫는다. */
    private fun finishReal(s: AppState, series: String, ch: Int, n: Int) {
        val r = Route.Letter(series, ch, n)
        val (c, l) = s.letterOf(r)
        s.open(series, c, l, null, skipAudio = true)
        s.save(series, c, l.id, s.progress(series, c, l.id).copy(shown = l.messages.size))
        s.letterModes(series, c, l).forEach { m -> if (!s.progress(series, c, l.id).done) s.replyAndContinue(r, m) }
        s.reward = null; s.levelUp = null; s.gradeUp = null; s.stepNote = null
    }

    /**
     * 키보드가 올라온 화면: [act] 뒤에 IME 인셋 ([kbDp] 높이)을 Compose 뷰에 직접 보내고, 그 자리에 회색 ‘키보드’ 판을 그린다.
     * imePadding 이 제대로면 입력 칸 · 확인 단추가 판 위에 보인다.
     */
    private fun shotIme(name: String, dark: Boolean, kbDp: Int = 300, act: () -> Unit = {}, series: AppState? = null, content: @Composable () -> Unit) {
        var host: android.view.View? = null
        val kb = androidx.compose.runtime.mutableStateOf(false)
        shot(name, dark, act = {
            act()
            rule.mainClock.advanceTimeBy(300)
            rule.runOnIdle {
                val px = (kbDp * ctx.resources.displayMetrics.density).toInt()
                val ins = androidx.core.view.WindowInsetsCompat.Builder()
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, px))
                    .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), true).build()
                androidx.core.view.ViewCompat.dispatchApplyWindowInsets(host!!, ins)
                kb.value = true
            }
            rule.mainClock.advanceTimeBy(600)
        }, series = series) {
            host = androidx.compose.ui.platform.LocalView.current
            Box(Modifier.fillMaxSize()) {
                content()
                if (kb.value) Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxSize().padding(top = (852 - kbDp).dp)
                    .background(androidx.compose.ui.graphics.Color(0xFF3C3C3F)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    androidx.compose.material3.Text("KEYBOARD", color = androidx.compose.ui.graphics.Color(0xFFBBBBBB))
                }
            }
        }
    }

    @Test fun opening() { val s = state(true); shot("01_opening_ko_dark", true, 4500) { io.github.graviton94.todayletters.ui.Opening(s) } }
    @Test fun onboarding0() { val s = state(false); shot("03a_onboarding0_ko_light", false) { Onboarding(s) } }
    @Test fun onboarding2() { val s = state(false); shot("04_onboarding2_ko_dark", true) { Onboarding(s) } }
    @Test fun inbox() { val s = state(true); shot("06_inbox_ko_light", false, series = s) { Inbox(s) } }
    // 0.9.5: 다음 단추의 금빛 결 (4초 주기 가운데쯤, 3.5초에 찍음) · 큐레이터 노트 · 표현 카드 원문 크기
    @Test fun v095RoomShine() {
        val s = state(true)
        val (c, l) = s.letterOf(Route.Letter("vincent", 1, 1))
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = l.messages.size))
        shot("v095_room_next_shine_light", false, 2600, series = s) { Room(s, Route.Letter("vincent", 1, 1)) }
    }
    @Test fun v095PhraseCard() { val s = state(true); shot("v095_phrase_card_light", false, series = s) { io.github.graviton94.todayletters.ui.PhraseCards(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.MATCH)) { } } }
    @Test fun roomLight() { val s = state(true); shot("07_room_ko_light", false, 6000, series = s) { Room(s, Route.Letter("vincent", 1, 1)) } }
    @Test fun roomDark() { val s = state(true); shot("08_room_ko_dark", true, 6000, series = s) { Room(s, Route.Letter("vincent", 1, 1)) } }
    @Test fun constellation() {
        val s = state(true)
        val letter = s.work("vincent").chapters.first().letters.first()
        val answer = Exercises.constellation(Exercises.replyFor(letter, s.seriesSettings("vincent").learn).first, letter.id.hashCode()).first
        shot("09_constellation_ko_light", false, act = {
            answer.take(2).forEach { rule.onAllNodes(hasText(it) and hasClickAction() and isEnabled()).onFirst().performClick(); rule.mainClock.advanceTimeBy(700) }
        }, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.CONSTELLATION)) }
    }
    @Test fun constellationDark() {
        val s = state(true)
        val letter = s.work("vincent").chapters.first().letters.first()
        val answer = Exercises.constellation(Exercises.replyFor(letter, s.seriesSettings("vincent").learn).first, letter.id.hashCode()).first
        shot("10_constellation_ko_dark", true, act = {
            answer.take(3).forEach { rule.onAllNodes(hasText(it) and hasClickAction() and isEnabled()).onFirst().performClick(); rule.mainClock.advanceTimeBy(700) }
        }, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.CONSTELLATION)) }
    }
    /** 작은 폰 (360×640): 별자리 잇기가 굴리지 않고 한 화면에 — 모든 별이 아래 단추 위에 보인다. */
    @Config(sdk = [34], qualifiers = "ko-rKR-w360dp-h640dp-xhdpi")
    @Test fun fixFitConstellationSmall() {
        val s = state(true)
        val letter = s.work("vincent").chapters.first().letters.first()
        val (answer, pieces) = Exercises.constellation(Exercises.replyFor(letter, s.seriesSettings("vincent").learn).first, letter.id.hashCode())
        shot("fixfit_constellation_360x640", false, act = {
            answer.take(2).forEach { rule.onAllNodes(hasText(it) and hasClickAction() and isEnabled()).onFirst().performClick(); rule.mainClock.advanceTimeBy(700) }
            val button = rule.onNodeWithText(ctx.getString(R.string.play_show)).fetchSemanticsNode().boundsInRoot
            pieces.distinct().forEach { w ->
                rule.onAllNodes(hasText(w)).fetchSemanticsNodes().forEach { n ->
                    org.junit.Assert.assertTrue("star '$w' (${n.boundsInRoot}) is below the buttons ($button)", n.boundsInRoot.bottom <= button.top + 1f)
                }
            }
        }, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.CONSTELLATION)) }
    }
    @Test fun match() { val s = state(true); shot("13_match_ko_light", false, act = {
        // 보기 B 를 하나 고른 모습 (맞든 틀리든)
        rule.onAllNodes(hasText("B", substring = false) and hasClickAction()).onFirst().performClick()
        rule.mainClock.advanceTimeBy(500)
    }, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.MATCH)) } }
    @Test fun aloud() { val s = state(true); shot("14_aloud_ko_dark", true, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.ALOUD)) } }
    @Test fun done() { val s = state(true); finishReal(s, "vincent", 1, 1); shot("15_done_ko_light", false, 4000, series = s) { Done(s, Route.Letter("vincent", 1, 1)) } }
    @Test fun chapter() {
        val s = state(true)
        val (c, l) = s.openable("vincent").first()
        s.open("vincent", c, l, null)
        shot("16_chapter_ko_dark", true, series = s) { ChapterScreen(s, Route.Chapter("vincent", 1)) }
    }
    @Test fun words() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = 4))
        shot("17_words_ko_light", false, series = s) { WordsTab(s) }
    }
    @Test fun roomGuide() {
        val s = state(true)
        finishReal(s, "vincent", 1, 1)   // 다 읽고 걸음까지 마친 편지 (다시 읽기 띠 · 오늘로 돌아가기)
        shot("18_room_finished_ko_light", false, 4000, series = s) { Room(s, Route.Letter("vincent", 1, 1)) }
    }
    @Test fun seriesSettings() { val s = state(true); shot("11_series_settings_ko_light", false, series = s) { SeriesSettingsScreen(s, "vincent") } }
    /** 업데이트 뒤 첫 실행의 ‘새로 바뀐 것’ · 새 버전을 다 받은 뒤의 ‘다시 열기’ 띠 (서재). */
    @Test fun updateWhatsNew() { val s = state(true); s.go(io.github.graviton94.todayletters.core.Route.Library); s.whatsNew = true
        shot("u01_whatsnew_ko_light", false) { LibraryTab(s); io.github.graviton94.todayletters.ui.UpdateLayer(s) } }
    // ── 업데이트 띠 · 기록 보관 시트 (시안 update_backup.html): 띠는 Play 없이 상태만 넣어 찍는다 ──
    private fun updateShot(name: String, dark: Boolean, band: io.github.graviton94.todayletters.core.UpdateBand) {
        val s = state(true); s.go(Route.Library); s.whatsNew = false; s.updateBand = band
        shot(name, dark) { LibraryTab(s); io.github.graviton94.todayletters.ui.UpdateLayer(s) }
    }
    @Test fun ubUpdateOffer() = updateShot("ub1_update_offer_ko_light", false, io.github.graviton94.todayletters.core.UpdateBand.Offer(18_400_000))
    @Test fun ubUpdateProgress() = updateShot("ub2_update_progress_ko_light", false, io.github.graviton94.todayletters.core.UpdateBand.Fetching(7_728_000, 18_400_000))
    @Test fun ubUpdateFailed() = updateShot("ub2b_update_failed_ko_light", false, io.github.graviton94.todayletters.core.UpdateBand.Failed)
    @Test fun ubUpdateReady() = updateShot("ub3_update_ready_ko_dark", true, io.github.graviton94.todayletters.core.UpdateBand.Ready)
    // ── 결제 뒤 고마워요 한 장 ─────────────
    @Test fun thanksSub() { val s = state(true); s.thanks = io.github.graviton94.todayletters.data.Billing.Thanks(null); shot("thanks1_sub_light", false, series = s) { Inbox(s); io.github.graviton94.todayletters.ui.ThanksSheet(s) } }
    @Test fun thanksOwn() { val s = state(true); s.thanks = io.github.graviton94.todayletters.data.Billing.Thanks("vincent"); shot("thanks2_own_light", false, series = s) { Inbox(s); io.github.graviton94.todayletters.ui.ThanksSheet(s) } }

    // ── 바로 오늘로 (nav 시안 A) ─────────────
    /** 빈센트: 편지 I 마침 · II 읽는 중 · III 새로 (와 있는 편지 2통), 모차르트도 읽기 시작 (다른 작품 소식 한 줄). */
    private fun navState(): AppState {
        val s = state(true)
        val v = s.work("vincent").chapters.first().letters
        s.save("vincent", "I", v[0].id, s.progress("vincent", "I", v[0].id).copy(shown = v[0].messages.size, done = true))
        s.store.markStarted(s.key("vincent", "I", v[0].id))
        s.save("vincent", "I", v[1].id, s.progress("vincent", "I", v[1].id).copy(shown = 1))
        s.store.markStarted(s.key("vincent", "I", v[1].id))
        if (s.works.any { it.series.id == "mozart" }) {
            s.store.markLearnChosen("mozart")
            val m = s.work("mozart").chapters.first()
            s.store.markStarted(s.key("mozart", m.id, m.letters[0].id))
            s.save("mozart", m.id, m.letters[0].id, s.progress("mozart", m.id, m.letters[0].id).copy(shown = 1))
        }
        s.store.markLearnChosen("vincent"); s.store.markFirstAsked("vincent")
        s.enter("vincent")
        return s
    }
    @Test fun navToday() { val s = navState(); shot("nav1_today_light", false, series = s) { Inbox(s) } }
    @Test fun navTodayDark() { val s = navState(); shot("nav1_today_dark", true, series = s) { Inbox(s) } }
    @Test fun navLibrary() { val s = navState(); shot("nav2_library_dark", true) { LibraryTab(s) } }

    @Test fun library() { val s = state(true); shot("12_library_ko_dark", true) { LibraryTab(s) } }

    /** 편지 두 통을 끝내고 낱말을 모은 상태 (복습 화면들). 몇 장은 오늘 복습할 차례로. */
    private fun withCards(): AppState {
        val s = state(true)
        s.work("vincent").chapters.first().letters.take(2).forEach { l -> s.collectWords("vincent", "I", l) }
        s.cards().forEachIndexed { i, c -> s.store.save(c.copy(box = i % 4, due = if (i % 3 == 0) s.today + 2 else s.today)) }
        return s
    }
    @Test fun review() { val s = withCards(); shot("20_review_ko_light", false, series = s) { WordsTab(s) } }
    @Test fun reviewDark() { val s = withCards(); shot("21_review_ko_dark", true, series = s) { WordsTab(s) } }
    @Test fun dictation() { val s = withCards(); shot("22_dictation_ko_light", false, series = s) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.DICTATION)) } }
    @Test fun dictationPad() {
        val s = withCards()
        shot("22b_dictation_pad_ko_light", false, act = { rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll("프랑스어 글자판")).performClick() }, series = s) {
            Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.DICTATION))
        }
    }
    @Test fun blank() { val s = withCards(); shot("23_blank_ko_dark", true, series = s) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.BLANK)) } }
    @Test fun meaning() { val s = withCards(); shot("24_meaning_ko_light", false, series = s) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.MEANING)) } }
    @Test fun quotes() {
        val s = withCards()
        s.toggleQuote(s.quoteKey("vincent", "I", s.work("vincent").chapters.first().letters.first().id, 2))
        s.toggleQuote(s.quoteKey("vincent", "I", s.work("vincent").chapters.first().letters[1].id, 0))
        shot("25_quotes_ko_light", false, series = s) { QuotesScreen(s) }
    }
    @Test fun walk() { val s = state(true); shot("26_walk_intro", false, series = s) { Artwork(s, Route.Artwork("vincent", 1, Route.Letter("vincent", 1, 1))) } }
    @Test fun walkSpot() {
        val s = state(true)
        shot("27_walk_spot", false, act = { rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll("산책 시작")).performClick() }, series = s) { Artwork(s, Route.Artwork("vincent", 1, Route.Letter("vincent", 1, 3))) }
    }
    @Test fun todayHome() { val s = withCards(); shot("28_today_home_ko_light", false, series = s) { Inbox(s) } }
    @Test fun moments() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters[2]
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size))
        shot("29_room_moments_ko_dark", true, 4000, series = s) { Room(s, Route.Letter("vincent", 1, 3)) }
    }
    @Test fun postcard() {
        val s = state(true)
        val w = s.work("vincent"); val l = w.chapters.first().letters[1]
        val bmp = postcard(ctx, w, l, l.messages[2], io.github.graviton94.todayletters.core.Lang.FR, io.github.graviton94.todayletters.core.Lang.KO)
        java.io.File("build/screens").mkdirs()
        java.io.File("build/screens/30_postcard.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun libraryMissed() {
        val s = state(true)
        val (c, l) = s.openable("vincent").first()
        s.open("vincent", c, l, null)
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = l.messages.size, done = true))
        // 이틀 전에 첫 편지를 열었다 → 다음 편지는 어제 도착, 아직 안 열었으니 부재중
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().putLong("o:vincent:day", s.today - 2).putInt("o:vincent:n", 1).commit()
        shot("31_library_missed_ko_dark", true) { LibraryTab(s) }
    }
    @Test fun roomLocation() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size))
        shot("32_room_location_ko_dark", true, 4000, series = s) { Room(s, Route.Letter("vincent", 1, 1)) }
    }
    @Test fun askDialog() {
        val s = state(true)
        shot("33_ask_ko_dark", true) { io.github.graviton94.todayletters.ui.Ask("복습을 그만할까요?", "그만하기", "계속하기", {}, {}, body = "지금까지 푼 것은 저장돼요.") }
    }
    @Test fun koreanPad() {
        // 한국어를 배우는 공간 (영어로 읽기) 에 카드를 모은다: 그래야 받아쓰기가 한국어 글자판을 연다
        val s = state(true)
        s.update(s.app.copy(read = io.github.graviton94.todayletters.core.Lang.EN))
        s.update("vincent", s.seriesSettings("vincent").copy(learn = io.github.graviton94.todayletters.core.Lang.KO))
        s.work("vincent").chapters.first().letters.take(2).forEach { l -> s.collectWords("vincent", "I", l) }
        s.cards().forEach { c -> s.store.save(c.copy(box = 1, due = s.today)) }
        s.bump()
        org.junit.Assert.assertTrue("korean cards", s.cards().isNotEmpty())
        shot("34_korean_pad_light", false, series = s) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.DICTATION)) }
    }
    @Test fun roomTransfer() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters[1]
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size))
        shot("35_room_transfer_ko_light", false, 4000, series = s) { Room(s, Route.Letter("vincent", 1, 2)) }
    }
    @Test fun roomRent() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters[4]
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size))
        shot("36_room_rent_ko_dark", true, 4000, series = s) { Room(s, Route.Letter("vincent", 1, 5)) }
    }
    @Test fun chapterEnd() {
        val s = withCards()
        s.work("vincent").chapters.first().letters.forEach { l ->
            s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size, done = true))
            s.store.markStarted(s.key("vincent", "I", l.id))
        }
        shot("37_chapter_end_ko_dark", true, series = s) { Inbox(s) }
    }
    @Test fun blankHint() {
        val s = withCards()
        shot("38_blank_hint_ko_light", false, act = { rule.onNodeWithText(ctx.getString(R.string.blank_hint)).performClick() /* 💡 는 그린 전구 (BulbGlyph) 로 바뀜 */ }, series = s) {
            Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.BLANK))
        }
    }

    /** 엔진 화면용: 편지 세 통을 끝내고, 낱말 몇 개는 위 단계로, 우표 146장, 갤러리 몇 점, 이정표 둘. */
    private fun engineState(): AppState {
        val s = withCards()
        val ls = s.work("vincent").chapters.first().letters
        ls.take(3).forEach { l ->
            s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size, done = true))
            s.store.markStarted(s.key("vincent", "I", l.id))
            s.collectWords("vincent", "I", l)
        }
        s.cards().forEachIndexed { k, c -> s.store.save(c.copy(box = k % 6)) }
        s.store.saveWallet("vincent", io.github.graviton94.todayletters.core.Wallet(balance = 146))
        listOf("met-436529", "met-459123", "brieven-238", "brieven-108", "met-335537", "brieven-070").forEach { s.store.addPiece("vincent", it) }
        // 메달 진열장: 받은 메달 넷 · 레벨 17 (학예 보조 II)
        listOf("streak_3" to 20L, "streak_7" to 8L, "letters_1" to 9L, "walks_5" to 2L).forEach { (m, d) -> s.store.awardMedal(m, s.today - d) }
        s.store.saveXp(io.github.graviton94.todayletters.core.XpLedger(total = io.github.graviton94.todayletters.core.Levels.totalFor(17) + 820))
        s.store.levelSeen = 17
        s.store.markDoneDay(s.today); s.store.markDoneDay(s.today - 1); s.store.markDoneDay(s.today - 3)
        s.store.saveStreak(s.today, 12); s.store.rests = 1
        return s
    }
    @Test fun gallery() { val s = engineState(); s.reward = null; shot("40_gallery_dark", true, series = s) { io.github.graviton94.todayletters.ui.GalleryTab(s) } }
    @Test fun shop() { val s = engineState(); s.reward = null; shot("41_shop_light", false, series = s) { io.github.graviton94.todayletters.ui.ShopTab(s) } }
    @Test fun record() { val s = engineState(); s.reward = null; shot("42_record_light", false) { io.github.graviton94.todayletters.ui.RecordScreen(s) } }
    @Test fun exhibition() { val s = engineState(); s.reward = null; shot("44_exhibition_dark", true, series = s) { io.github.graviton94.todayletters.ui.ExhibitionScreen(s) } }
    @Test fun reward() {
        val s = engineState()
        val pc = s.work("vincent").kit.collection.first { it.id == "met-436529" }
        s.reward = AppState.RewardMoment("vincent", 18, pc, emptyList(), null,
            listOf(io.github.graviton94.todayletters.core.Earn.REVIEW_RIGHT to 8, io.github.graviton94.todayletters.core.Earn.SHADOW_PASS to 6, io.github.graviton94.todayletters.core.Earn.STREAK to 4))
        shot("45_reward_dark", true, 2000) { io.github.graviton94.todayletters.ui.RewardOverlay(s) }
    }
    @Test fun recital() { val s = engineState(); s.reward = null; shot("46_recital_light", false, series = s) { io.github.graviton94.todayletters.ui.RecitalScreen(s) } }
    @Test fun enter() { val s = engineState(); s.reward = null; shot("47_enter", true, 3000) { io.github.graviton94.todayletters.ui.EnterScreen(s, "vincent") } }
    @Test fun todayEngine() { val s = engineState(); s.reward = null; shot("48_today_engine_light", false, 3000, series = s) { Inbox(s) } }
    @Test fun libraryEngine() { val s = engineState(); s.reward = null; shot("49b_library_engine_light", false, 3000) { LibraryTab(s) } }
    /** 복습 말하기 = 따라 읽기와 같은 부품 (녹음 · 내 소리, 목소리가 들어가면 맞음). 음성 인식 · 스스로 판정은 없다. */
    @Test fun speak() {
        val s = engineState()
        shot("49_speak_light", false, act = {
            org.junit.Assert.assertTrue(rule.onAllNodes(hasText(ctx.getString(R.string.shadow_talk))).fetchSemanticsNodes().isNotEmpty())
            org.junit.Assert.assertTrue(rule.onAllNodes(hasText(ctx.getString(R.string.speak_tap))).fetchSemanticsNodes().isNotEmpty())
        }, series = s) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.SPEAK)) }
    }
    @Test fun roomFade() {
        // B1 · 거의 다 아는 편지: 번역이 접히고, 아직인 오늘의 낱말만 작은 뜻풀이로 (53 다시 읽기와 다른 장면)
        val s = graded(engineState().also { it.reward = null }, 7)
        val l = s.work("vincent").chapters.first().letters.first()
        knowWords(s, l, l.messages.indices.toList())
        l.words.indices.forEach { i -> s.store.cards().firstOrNull { it.key == s.cardKey("vincent", "I", l.id, i) }?.let { c -> s.store.save(c.copy(box = 1)) } }
        s.bump()
        shot("50_room_fade_ko_light", false, 4000, series = s) { Room(s, Route.Letter("vincent", 1, 1)) }
    }

    // ── v11 하나의 고리: 오늘의 북극성 · 다시 읽기 · 따라 읽기 · 봉인 ──
    private fun loopState(): AppState {
        val s = engineState()
        s.reward = null
        s.store.markOpened("vincent")   // 오늘 몫을 이미 열었다 → 새 편지 없이 다 읽은 편지가 주인공
        return s
    }
    private fun room(n: Int) = Route.Letter("vincent", 1, n, Route.Inbox)
    @Test fun todayFocus() { val s = loopState(); shot("51_today_focus_light", false, 2000, series = s) { Inbox(s) } }
    @Test fun todaySealed() {
        val s = loopState()
        val l = s.work("vincent").chapters.first().letters[2]
        l.words.indices.forEach { i -> s.store.save(io.github.graviton94.todayletters.core.Card(s.cardKey("vincent", "I", l.id, i), box = 3, due = s.today + 5)) }
        s.store.markShadowed(s.key("vincent", "I", l.id)); s.settle(); s.reward = null
        shot("52_today_sealed_dark", true, 2000, series = s) { Inbox(s) }
    }
    /** 다 읽은 편지 아래 큐레이터 노트: 노트가 말하는 그림이 작게 (‘그림’ 걸음 전에도). 낱말 뜻(옅게) · 문장 번역(또렷하게). */
    @Test fun noteFigure() {
        val s = loopState()
        shot("nf1_room_note_dark", true, 4000, act = {
            rule.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst()
                .performScrollToNode(hasText(ctx.getString(R.string.note_title)))
        }, series = s) { Room(s, room(1)) }
    }
    /** 대화 속 사진 (공공 영역 그림으로 넓힘): 빈센트 I-1 의 히로시게 눈 판화 · 눈 덮인 아를. */
    @Test fun momentPhoto1() = momentPhoto("mp1_moment_photo_dark", 0)
    @Test fun momentPhoto2() = momentPhoto("mp2_moment_photo_dark", 1)
    /** 대화 속 사진 (공공 영역 그림으로 넓힘): 빈센트 I-1 의 새 사진 말풍선만. */
    private fun momentPhoto(name: String, k: Int) {
        val s = loopState()
        val w = s.work("vincent")
        val m = w.chapters.first { it.id == "I" }.letters.first().moments.filterIsInstance<io.github.graviton94.todayletters.core.Moment.Photo>()[k]
        shot(name, true, 3000, series = s) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(16.dp)) {
                io.github.graviton94.todayletters.ui.MomentCard(s, w, m, onMap = {}, onPhoto = {})
            }
        }
    }
    @Test fun reread() { val s = loopState(); shot("53_room_reread_light", false, 3000, series = s) { Room(s, room(1)) } }
    @Test fun shadow() { val s = loopState(); shot("54_shadow_dark", true, 2000, series = s) { io.github.graviton94.todayletters.ui.ShadowScreen(s, Route.Shadow(room(1))) } }
    @Test fun seal() {
        val s = loopState()
        val l = s.work("vincent").chapters.first().letters[0]
        s.shadowChunks("vincent", "I", l).forEachIndexed { i, cs -> cs.indices.forEach { j -> s.take("vincent", "I", l.id, i, j).apply { parentFile?.mkdirs(); writeBytes(ByteArray(16)) } } }
        shot("55_seal_dark", true, 2000, series = s) { io.github.graviton94.todayletters.ui.SealScreen(s, Route.Seal(room(1))) }
    }
    @Test fun download() {
        val s = state(true)
        val ch = s.work("vincent").chapters[1]
        s.downloadAsk = AppState.DownloadAsk(s.downloads.pack("vincent", ch.id)!!, "vincent", ch.id, ch.letters.first(), Route.Inbox)
        shot("56_download_light", false, series = s) { io.github.graviton94.todayletters.ui.DownloadSheet(s, s.downloadAsk!!) }
    }
    @Test fun roomResume() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = 2))
        shot("57_room_resume_light", false, 3000, series = s) { Room(s, Route.Letter("vincent", 1, 1)) }
    }
    @Test fun viewer() {
        val s = state(true)
        val pc = s.work("vincent").kit.collection.first { it.tier == io.github.graviton94.todayletters.core.Tier.PAINTING }
        shot("58_viewer_dark", true, series = s) { io.github.graviton94.todayletters.ui.PieceView(pc) { } }
    }
    @Test fun contents() { val s = state(true); shot("59_contents_light", false, series = s) { io.github.graviton94.todayletters.ui.SeriesCover(s, "vincent") } }
    @Test fun roomChapter3() {
        val s = state(true)
        val l = s.work("vincent").chapters[2].letters[3]
        s.save("vincent", "III", l.id, s.progress("vincent", "III", l.id).copy(shown = l.messages.size))
        shot("60_room_c3_light", false, 3000, series = s) { Room(s, Route.Letter("vincent", 3, 4)) }
    }
    /** 긴 제목 지킴이 (v20): 새 시리즈를 넣기 전에 아주 긴 원제 · 번역에서 줄바꿈 · 캡슐이 깨지지 않는지. */
    @Test fun longTitles() {
        val s = state(true)
        shot("61_long_titles_light", false, series = s) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(horizontal = 24.dp)) {
                io.github.graviton94.todayletters.ui.ChapterRow("III", "La maison jaune", "노란 집", io.github.graviton94.todayletters.core.Lang.KO, io.github.graviton94.todayletters.core.Lang.FR, "편지 5통 · 0통 읽음", {}) {
                    io.github.graviton94.todayletters.ui.Capsule("무료", io.github.graviton94.todayletters.ui.CapsuleKind.FILLED)
                    io.github.graviton94.todayletters.ui.Capsule("✓ 낭독 있음", io.github.graviton94.todayletters.ui.CapsuleKind.DONE)
                }
                io.github.graviton94.todayletters.ui.ChapterRow("IV", "Lettres de Wolfgang Amadeus Mozart à son père, de Salzbourg à Paris", "잘츠부르크에서 파리까지, 아버지에게 보내는 볼프강 아마데우스 모차르트의 편지", io.github.graviton94.todayletters.core.Lang.KO, io.github.graviton94.todayletters.core.Lang.FR, "편지 12통 · 0통 읽음", {}) {
                    io.github.graviton94.todayletters.ui.Capsule("미리 받기 · 12.4MB", io.github.graviton94.todayletters.ui.CapsuleKind.FILLED)
                    io.github.graviton94.todayletters.ui.Capsule("받는 중…")
                }
                io.github.graviton94.todayletters.ui.ChapterRow("V", "Briefe an den Vater aus der Zeit der großen Reise nach Mannheim und Paris", "Letters to his father from the time of the great journey to Mannheim and Paris", io.github.graviton94.todayletters.core.Lang.EN, io.github.graviton94.todayletters.core.Lang.DE, "12 letters", {}) {
                    io.github.graviton94.todayletters.ui.Capsule("Free", io.github.graviton94.todayletters.ui.CapsuleKind.FILLED)
                }
            }
        }
    }
    /** 1단계 F2: 낱말 카드 상세 (내 것 · 잊을 뻔 표시 · 나온 문장). */
    @Test fun wordDetail() {
        val s = loopState()
        val l = s.work("vincent").chapters.first().letters.first()
        val key = s.cardKey("vincent", "I", l.id, 0)
        s.store.save(io.github.graviton94.todayletters.core.Card(key, box = 5, due = s.today + 41, seen = 9, keep = 1, last = s.today - 20, lapsed = true))
        shot("62_word_detail_light", false, series = s) { io.github.graviton94.todayletters.ui.WordDetailBody(s, key) { } }
    }
    /** 1단계 F7: 오늘 화면의 ‘기억 확인’ (오래전 내 것 낱말). */
    /** 기억 확인은 복습 탭으로 옮겼다 (오늘은 여정 하나). */
    @Test fun todaySurprise() {
        val s = loopState()
        val l = s.work("vincent").chapters.first().letters.first()
        s.store.save(io.github.graviton94.todayletters.core.Card(s.cardKey("vincent", "I", l.id, 1), box = 5, due = s.today + 30, seen = 8, last = s.today - 40))
        shot("63_review_surprise_light", false, 2000, series = s) { WordsTab(s) }
    }
    // ── 2단계: 진행 줄 · 편지 도착 · 문장 받아쓰기 · 내 구절 ──
    /** U1: 펼친 걸음 목록 (읽기 끝 · 지금 낱말 맞추기). */
    @Test fun roomSteps() {
        val s = state(true)
        shot("64_room_steps_light", false, series = s) {
            androidx.compose.foundation.layout.Column {
                io.github.graviton94.todayletters.ui.StepSheet(listOf(
                    io.github.graviton94.todayletters.ui.Step("읽기", "4문장 모두 읽음", 2),
                    io.github.graviton94.todayletters.ui.Step("낱말 맞추기", "낱말 6개 · 뜻과 소리를 짝지어요", 1),
                    io.github.graviton94.todayletters.ui.Step("따라 읽기", "4문장을 소리 내어", 0),
                    io.github.graviton94.todayletters.ui.Step("답장", "테오의 답장을 써요", 0),
                    io.github.graviton94.todayletters.ui.Step("그림", "이 편지의 그림을 받아요", 0),
                ), "I") { }
            }
        }
    }
    /** D1: 그림이 먼저 · 첫 줄이 다 쓰인 뒤. */
    @Test fun arrivalPainting() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        shot("65_arrival_painting", false, series = s) { io.github.graviton94.todayletters.ui.ArrivalBody(s, "vincent", "I", l, 1, { }, startAt = 0) }
    }
    @Test fun arrivalLine() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        shot("66_arrival_line", false, series = s) { io.github.graviton94.todayletters.ui.ArrivalBody(s, "vincent", "I", l, 1, { }, startAt = 2) }
    }
    /** F4: ‘말하기’ 칸 위 낱말(pays)의 듣고 쓰기 → 그 낭독 마디를 통째로. */
    @Test fun phraseDictation() {
        val s = withCards()
        val l = s.work("vincent").chapters.first().letters.first()
        val key = s.cardKey("vincent", "I", l.id, 1)
        s.store.save(io.github.graviton94.todayletters.core.Card(key, box = 4, due = s.today, seen = 5))
        shot("67_phrase_dictation_light", false, series = s) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.DICTATION, only = listOf(key))) }
    }
    /** F5: 내 구절로 빈칸 연습. */
    @Test fun quoteBlank() {
        val s = withCards()
        val l = s.work("vincent").chapters.first().letters.first()
        val qk = s.quoteKey("vincent", "I", l.id, 2)
        s.toggleQuote(qk)
        val key = s.cardKey("vincent", "I", l.id, 3)
        shot("68_quote_blank_light", false, series = s) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.BLANK, only = listOf(key), quote = qk)) }
    }
    // ── 3단계: 액자 · 메달 · 이번 주 · 우표 · 도록 · 작품 카드 ──
    /** D2: 명작이 실제 장식 금박 액자에. */
    @Test fun rewardMaster() {
        val s = engineState()
        val pc = s.work("vincent").kit.collection.first { it.id == "met-436528" }
        s.reward = AppState.RewardMoment("vincent", 28, pc, emptyList(), null,
            listOf(io.github.graviton94.todayletters.core.Earn.ACHIEVEMENT to 10, io.github.graviton94.todayletters.core.Earn.LETTER to 6, io.github.graviton94.todayletters.core.Earn.STREAK to 4, io.github.graviton94.todayletters.core.Earn.REVIEW_RIGHT to 8))
        shot("69_reward_master", true, 2000) { io.github.graviton94.todayletters.ui.RewardOverlay(s) }
    }
    /** E4: 이달의 전시를 다 모은 순간 (도록 표지). */
    @Test fun rewardCatalogue() {
        val s = engineState()
        val e = s.exhibition("vincent")!!
        s.reward = AppState.RewardMoment("vincent", 50, null, emptyList(), null, listOf(io.github.graviton94.todayletters.core.Earn.EXHIBITION to 50), e)
        shot("70_reward_catalogue", true, 2000) { io.github.graviton94.todayletters.ui.RewardOverlay(s) }
    }
    /** E1: 이번 주. */
    @Test fun week() {
        val s = engineState(); s.reward = null
        val start = io.github.graviton94.todayletters.core.Recital.weekStart(s.today)
        (0..4).forEach { d -> s.store.addScore(start + d, 64 + d * 4, 58 + d * 3) }
        s.store.logDay("pieces", s.today, "vincent|met-436529"); s.store.logDay("pieces", s.today, "vincent|brieven-238")
        s.store.logDay("rest", start + 2, "rest")
        shot("71_week_light", false, series = s) { io.github.graviton94.todayletters.ui.WeekScreen(s) }
    }
    /** E5: 작품 카드 (테두리 소인). */
    @Test fun artCard() {
        val s = engineState(); s.reward = null
        s.store.addBorder("vincent", "POSTMARK"); s.store.setBorder("vincent", "POSTMARK")
        val w = s.work("vincent"); val l = w.chapters[2].letters[0]
        shot("72_art_card_light", false, series = s) { io.github.graviton94.todayletters.ui.ArtCardSheet(s, w, l, l.messages[1]) { } }
    }
    /** E3: 카드 테두리 고르기. */
    @Test fun borders() {
        val s = engineState(); s.reward = null
        shot("73_borders_light", false, series = s) { io.github.graviton94.todayletters.ui.BorderSheet(s, s.work("vincent")) { } }
    }
    // ── 4단계: 설정 · 첫 실행 · 위젯 · 어두운 화면 · 큰 글씨 ──
    @Test fun settings() { val s = state(true); shot("74_settings_light", false) { io.github.graviton94.todayletters.ui.SettingsScreen(s) } }
    @Test fun settingsDark() { val s = state(true); shot("75_settings_dark", true) { io.github.graviton94.todayletters.ui.SettingsScreen(s) } }
    @Test fun weekDark() {
        val s = engineState(); s.reward = null
        val start = io.github.graviton94.todayletters.core.Recital.weekStart(s.today)
        (0..3).forEach { d -> s.store.addScore(start + d, 60 + d * 5, 55 + d * 4) }
        shot("76_week_dark", true, series = s) { io.github.graviton94.todayletters.ui.WeekScreen(s) }
    }
    @Test fun shopDark() { val s = engineState(); s.reward = null; shot("77_shop_dark", true, series = s) { io.github.graviton94.todayletters.ui.ShopTab(s) } }
    @Test fun artCardDark() {
        val s = engineState(); s.reward = null
        val w = s.work("vincent"); val l = w.chapters[2].letters[0]
        shot("78_art_card_dark", true, series = s) { io.github.graviton94.todayletters.ui.ArtCardSheet(s, w, l, l.messages[1]) { } }
    }
    @Test fun stepsDark() {
        val s = state(true)
        shot("79_room_steps_dark", true, series = s) {
            io.github.graviton94.todayletters.ui.StepSheet(listOf(
                io.github.graviton94.todayletters.ui.Step("읽기", "4문장 모두 읽음", 2),
                io.github.graviton94.todayletters.ui.Step("낱말 맞추기", "낱말 6개 · 뜻과 소리를 짝지어요", 1),
                io.github.graviton94.todayletters.ui.Step("따라 읽기", "4문장을 소리 내어", 0),
            ), "I") { }
        }
    }
    @Test fun arrivalDark() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        shot("80_arrival_dark", true, series = s) { io.github.graviton94.todayletters.ui.ArrivalBody(s, "vincent", "I", l, 1, { }, startAt = 2) }
    }
    @Test fun askDark() {
        val s = state(true)
        shot("81_ask_dark", true, series = s) {
            Room(s, Route.Letter("vincent", 1, 1))
            io.github.graviton94.todayletters.ui.Ask("답장은 나중에 쓸까요?", "나중에", "계속 쓰기", { }, { })
        }
    }
    /** D3: 위젯 4×2 · 2×2 (RemoteViews 를 그대로 펼쳐 그린다). */
    @Test fun widgets() {
        state(true)
        val sum = io.github.graviton94.todayletters.data.TodaySummary.of(ctx)
        val art = sum?.plate?.let { io.github.graviton94.todayletters.data.TodayWidget.plate(ctx, it) }
        val d = ctx.resources.displayMetrics.density
        listOf(Triple("82_widget_4x2", false, 340 to 170), Triple("83_widget_2x2", true, 170 to 170)).forEach { (name, small, size) ->
            val shaped = art?.let { io.github.graviton94.todayletters.data.TodayWidget.rounded(it, if (small) size.first.toFloat() else size.first * 0.38f, size.second.toFloat(), 22f, allCorners = small) }
            val rv = io.github.graviton94.todayletters.data.TodayWidget.views(ctx, sum, shaped, small)
            val v = rv.apply(ctx, android.widget.FrameLayout(ctx))
            val w = (size.first * d).toInt(); val h = (size.second * d).toInt()
            v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            val f = java.io.File("build/screens/$name.png"); f.parentFile?.mkdirs()
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun largeToday() { val s = withCards(); large("84_today", 2000, series = s) { Inbox(s) } }
    @Test fun largeRoom() { val s = state(true); large("85_room", 6000, series = s) { Room(s, Route.Letter("vincent", 1, 1)) } }
    @Test fun largeDictation() { val s = withCards(); large("86_dictation", series = s) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.DICTATION)) } }
    @Test fun largeWordCard() {
        val s = loopState()
        val l = s.work("vincent").chapters.first().letters.first()
        val key = s.cardKey("vincent", "I", l.id, 0)
        s.store.save(io.github.graviton94.todayletters.core.Card(key, box = 3, due = s.today + 5, seen = 4))
        large("87_word_card", series = s) { io.github.graviton94.todayletters.ui.WordDetailBody(s, key) { } }
    }
    @Test fun largeSettings() { val s = state(true); large("88_settings") { io.github.graviton94.todayletters.ui.SettingsScreen(s) } }
    @Test fun largeReward() {
        val s = engineState()
        val pc = s.work("vincent").kit.collection.first { it.id == "met-436528" }
        s.reward = AppState.RewardMoment("vincent", 28, pc, emptyList(), null, listOf(io.github.graviton94.todayletters.core.Earn.LETTER to 6))
        large("89_reward", 2000) { io.github.graviton94.todayletters.ui.RewardOverlay(s) }
    }

    // ── 릴리즈 전 점검 (반 고흐 완결): 새 화면과 확인이 필요한 편지만. screens 워크플로에서 '*ScreensTest.release*' 로 따로 돌린다.
    private fun allDone(s: AppState, upTo: Int = 8) = s.work("vincent").chapters.take(upTo).forEach { c ->
        c.letters.forEach { l -> s.save("vincent", c.id, l.id, s.progress("vincent", c.id, l.id).copy(shown = l.messages.size, done = true)) }
    }
    @Test fun releasePrologueFirst() { val s = state(true); shot("r01_prologue_p1", true, 3000) { io.github.graviton94.todayletters.ui.PrologueScreen(s, Route.Prologue("vincent", 0)) } }
    @Test fun releasePrologueLast() { val s = state(true); shot("r02_prologue_p4", true, 3000) { io.github.graviton94.todayletters.ui.PrologueScreen(s, Route.Prologue("vincent", 3)) } }
    @Test fun releaseArrival() { val s = state(true); shot("r03_prologue_arrival", true, 3000) { io.github.graviton94.todayletters.ui.PrologueScreen(s, Route.Prologue("vincent", 4)) } }
    @Config(qualifiers = "+h1700dp")
    @Test fun releaseCover() { val s = state(true); allDone(s, 2); shot("r04_cover_rows", false, series = s) { io.github.graviton94.todayletters.ui.SeriesCover(s, "vincent") } }
    @Test fun releaseDoneLast() { val s = state(true); allDone(s); s.finished(); s.reward = null; shot("r05_done_last", false, 4000, series = s) { Done(s, Route.Letter("vincent", 8, 7)) } }
    @Config(qualifiers = "+h1900dp")
    @Test fun releaseOutro() { val s = state(true); allDone(s); shot("r06_outro_full", true, 3000) { io.github.graviton94.todayletters.ui.OutroScreen(s, "vincent") } }
    @Config(qualifiers = "+h1600dp")
    @Test fun releaseCatalogue() { val s = state(true); allDone(s, 3); shot("r07_catalogue_partial", true, 2000) { io.github.graviton94.todayletters.ui.CatalogueScreen(s, "vincent") } }
    @Config(qualifiers = "+h1600dp")
    @Test fun releaseCatalogueDone() { val s = state(true); allDone(s); shot("r08_catalogue_complete", true, 2000) { io.github.graviton94.todayletters.ui.CatalogueScreen(s, "vincent") } }
    @Test fun releasePrologueWord() { val s = state(true); shot("r09_prologue_word", false, series = s) { io.github.graviton94.todayletters.ui.WordDetailBody(s, "vincent:P:p1:0") { } } }
    /** c1-l1 밀짚모자: 편지가 그림을 묘사하지 않아 색 두 자리로 이은 것. */
    @Test fun releaseWalkStrawHat() {
        val s = state(true)
        shot("r10_walk_c1l1", false, act = { rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll("산책 시작")).performClick() }, series = s) { Artwork(s, Route.Artwork("vincent", 1, Route.Letter("vincent", 1, 1))) }
    }
    /** c2-l6 Patience Escalier: 걸린 그림(파란 배경)과 편지(주황)의 차이를 해설이 말하는지. */
    @Test fun releasePatience() { val s = state(true); shot("r11_artwork_c2l6", false, series = s) { Artwork(s, Route.Artwork("vincent", 6, Route.Letter("vincent", 2, 6))) } }
    /** c5-l3 (573): 룰랭의 목소리 · ‘pour mon oreille’ 문장. */
    @Config(qualifiers = "+h1400dp")
    @Test fun releaseRoom573() {
        val s = state(true)
        val l = s.work("vincent").chapters[4].letters[2]
        s.save("vincent", "V", l.id, s.progress("vincent", "V", l.id).copy(shown = 2))
        shot("r12_room_c5l3", false, 3000, series = s) { Room(s, Route.Letter("vincent", 5, 3)) }
    }

    // ── 모차르트 (릴리즈 전 점검): '*ScreensTest.mozart*' 로 따로 돌린다.
    /** 모차르트 장면은 서문 · I장 · 맺음을 받아 둔 것으로 (앱 밖 파일을 저장소의 remote/ 에서 files/ 로). */
    private fun mstate(): AppState = state(true).also { fetchedLocally("mozart", listOf("P", "I", "II", "E")) }
    private fun fetchedLocally(series: String, chapters: List<String>) {
        val remote = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.map { java.io.File(it, "remote") }.firstOrNull { it.isDirectory } ?: return
        val d = io.github.graviton94.todayletters.data.Downloads(ctx)
        chapters.mapNotNull { d.pack(series, it) }.flatMap { it.parts }.forEach { p ->
            val src = java.io.File(remote, p.path); val out = java.io.File(ctx.filesDir, p.path)
            if (src.isFile && !out.isFile) { out.parentFile?.mkdirs(); src.copyTo(out) }
        }
        io.github.graviton94.todayletters.data.AssetFiles.changed()
    }
    private fun shownAll(s: AppState, ch: Int, n: Int) {
        val c = s.work("mozart").chapters[ch - 1]; val l = c.letters[n - 1]
        s.save("mozart", c.id, l.id, s.progress("mozart", c.id, l.id).copy(shown = l.messages.size))
    }
    private fun mozartDone(s: AppState) = s.work("mozart").chapters.forEach { c ->
        c.letters.forEach { l -> s.save("mozart", c.id, l.id, s.progress("mozart", c.id, l.id).copy(shown = l.messages.size, done = true)) }
    }
    /** 나폴레옹 공개 (hidden 해제): 서재에 셋째 작품 카드 · 그 작품의 표지 (처음 묶음 P+I+II 를 받은 것으로). */
    @Test fun napoleonLibrary() { val s = state(true).also { fetchedLocally("napoleon", listOf("P", "I", "II")) }; shot("n01_library_napoleon", false, 3000) { LibraryTab(s) } }
    @Test fun napoleonCover() { val s = state(true).also { fetchedLocally("napoleon", listOf("P", "I", "II")) }; shot("n02_cover_napoleon", true, 3000, series = s) { io.github.graviton94.todayletters.ui.SeriesCover(s, "napoleon") } }
    // ── 고야 (넷째 작품, 서문 + I장): '*ScreensTest.goya*'. 앱 안에는 대표 그림 · 초상만, 나머지는 저장소 remote/ 에서 files/ 로 (받은 것으로).
    private fun gstate(): AppState = state(true).also { fetchedLocally("goya", listOf("P", "I")) }
    /** I장 [n] 번째 편지의 메시지 [shown] 개가 와 있는 것으로 (편지 방 맨 위가 보이게 앞 몇 개만). */
    private fun goyaShown(s: AppState, n: Int, shown: Int = 2) {
        val c = s.work("goya").chapters.first(); val l = c.letters[n - 1]
        s.save("goya", c.id, l.id, s.progress("goya", c.id, l.id).copy(shown = shown))
    }
    /** 서재: 넷째 작품 고야 카드 (〈양산〉 · 붉은 황토 빛깔). */
    @Test fun goyaLibrary() { val s = gstate(); shot("g01_library_goya", false, 3000, act = { rule.onRoot().performTouchInput { swipeUp() }; rule.mainClock.advanceTimeBy(600); rule.onRoot().performTouchInput { swipeUp() } }) { LibraryTab(s) } }
    /** 편지 방 I-1 (스페인어로 읽기): 문장마다 ‘원래 철자 ›’ 접힌 줄. */
    @Test fun goyaRoom() { val s = gstate(); s.enter("goya"); goyaShown(s, 1); shot("g02_room_c1l1", false, 4000, series = s) { Room(s, Route.Letter("goya", 1, 1)) } }
    /** 편지 방 I-1: 첫 문장의 ‘원래 철자’ 를 펼친 모습 (판본에 찍힌 dibiertas · bisites · á, 어두운 화면). */
    @Test fun goyaRoomOrig() {
        val s = gstate(); s.enter("goya"); goyaShown(s, 1)
        shot("g03_room_c1l1_orig_dark", true, 4000, act = { tap(io.github.graviton94.todayletters.core.Breaks.keepAll("원래 철자"), substring = true) }, series = s) { Room(s, Route.Letter("goya", 1, 1)) }
    }
    /** IV장 첫 편지 (소장한 것으로, IV장 · 맺음까지 받은 것으로): “이제 왕의 화가가 됐어”. */
    @Test fun goyaRoomIV() {
        val s = state(true).also { fetchedLocally("goya", listOf("P", "I", "IV", "E")) }
        s.store.entitlement = io.github.graviton94.todayletters.core.Entitlement.of(listOf("series_goya")); s.bump()
        s.enter("goya")
        val c = s.work("goya").chapters[3]; val l = c.letters[0]
        s.save("goya", c.id, l.id, s.progress("goya", c.id, l.id).copy(shown = 2))
        shot("g05_room_c4l1", false, 4000, series = s) { Room(s, Route.Letter("goya", 4, 1)) }
    }
    /** 맺음 (보르도 세 통 + 〈아직 배우고 있다〉, 모든 편지를 마친 것으로, 어두운 화면). */
    @Test fun goyaOutro() {
        val s = state(true).also { fetchedLocally("goya", listOf("P", "I", "II", "III", "IV", "V", "VI", "E")) }
        s.store.entitlement = io.github.graviton94.todayletters.core.Entitlement.of(listOf("series_goya")); s.bump()
        s.work("goya").chapters.forEach { c -> c.letters.forEach { l -> s.save("goya", c.id, l.id, s.progress("goya", c.id, l.id).copy(shown = l.messages.size, done = true)) } }
        shot("g06_outro", true, 3000) { io.github.graviton94.todayletters.ui.OutroScreen(s, "goya") }
    }
    /** 오늘의 회화 I-1 (A1, 스페인어): 마드리드의 프란시스코와 통화 · 나는 마르틴. */
    @Test fun goyaTalk() {
        val s = gstate(); s.enter("goya"); s.store.setGrade(s.seriesSettings("goya").learn.code, 1); s.bump()
        shot("g04_talk_c1l1", false, ms = 4000, series = s) { Play(s, Route.Play(Route.Letter("goya", 1, 1), ReplyMode.TALK)) }
    }
    @Test fun mozartLibrary() { val s = mstate(); shot("m01_library", true) { LibraryTab(s) } }
    @Test fun mozartRoom() { val s = mstate(); shownAll(s, 1, 4); shot("m02_room_c1l4", false, 4000, series = s) { Room(s, Route.Letter("mozart", 1, 4)) } }
    @Test fun mozartMusicSheet() {
        val s = mstate(); val w = s.work("mozart")
        val m = w.chapters[6].letters[2].moments.filterIsInstance<io.github.graviton94.todayletters.core.Moment.Music>().first()
        shot("m03_music_sheet", false, 3000, series = s) { io.github.graviton94.todayletters.ui.MusicSheet(s, w, m) {} }
    }
    @Test fun mozartWalkEra() { val s = mstate(); shot("m04_walk_era", true, series = s) { Artwork(s, Route.Artwork("mozart", 1, Route.Letter("mozart", 1, 3))) } }
    @Test fun mozartWalkScore() {
        val s = mstate()
        // 앱 밖에 둔 장의 그림: 받아 둔 것으로 (받기 전에는 산책 점을 띄우지 않는다, F12)
        java.io.File("../../remote/plates/m-c6-l5.webp").takeIf { it.exists() }?.copyTo(java.io.File(ctx.filesDir, "plates/m-c6-l5.webp").apply { parentFile?.mkdirs() }, overwrite = true)
        io.github.graviton94.todayletters.data.AssetFiles.changed()
        shot("m05_walk_score", false, act = { rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll("산책 시작")).performClick() }, series = s) { Artwork(s, Route.Artwork("mozart", 6, Route.Letter("mozart", 6, 5))) }
    }
    @Test fun mozartPrologue() { val s = mstate(); shot("m06_prologue_p1", true, 3000) { io.github.graviton94.todayletters.ui.PrologueScreen(s, Route.Prologue("mozart", 0)) } }
    @Test fun mozartArrival() { val s = mstate(); shot("m07_prologue_arrival", true, 3000) { io.github.graviton94.todayletters.ui.PrologueScreen(s, Route.Prologue("mozart", 3)) } }
    @Test fun mozartOutro() { val s = mstate(); mozartDone(s); shot("m08_outro", true, 3000) { io.github.graviton94.todayletters.ui.OutroScreen(s, "mozart") } }
    @Test fun mozartCover() { val s = mstate(); shot("m09_cover", false, series = s) { io.github.graviton94.todayletters.ui.SeriesCover(s, "mozart") } }
    /** 모차르트에 처음 들어갈 때 (튜토리얼 뒤): 배울 말만 고르는 시트 → 바로 편지 I. */
    @Test fun mozartWelcome() { val s = mstate(); shot("m10_welcome", true, series = s.also { it.enter("mozart") }) { io.github.graviton94.todayletters.ui.WelcomeSheet(s, "mozart") } }
    /** 오늘: 편지 카드의 단추 하나 · 장마다 읽은 수 줄의 글자 링크 · 번역 줄. */
    /** 상점 (v5 통합 그림): 소장품이 없어 ‘갤러리 칸 열기’ 줄이 없다. */
    @Test fun mozartShop() { val s = mstate(); s.enter("mozart"); s.reward = null; shot("m19_shop", true, series = s) { io.github.graviton94.todayletters.ui.ShopTab(s) } }
    @Test fun mozartToday() { val s = mstate(); s.enter("mozart"); shot("m11_today", true, series = s) { io.github.graviton94.todayletters.ui.Inbox(s) } }
    /** 편지 도착: 첫 줄이 다 쓰인 뒤 (아래 ‘편지 읽기’ 단추). */
    @Test fun mozartArrivalLine() {
        val s = mstate(); val l = s.work("mozart").chapters.first().letters.first()
        shot("m12_arrival_line", true, series = s) { io.github.graviton94.todayletters.ui.ArrivalBody(s, "mozart", "I", l, 1, { }, startAt = 2) }
    }
    /** 낮은 화면의 편지 도착 그림 (글 묶음이 넘치지 않고 굴러야 한다). */
    @Config(sdk = [34], qualifiers = "ko-rKR-w393dp-h560dp-xxhdpi")
    @Test fun mozartArrivalSmall() {
        val s = mstate(); val l = s.work("mozart").chapters.first().letters.first()
        shot("m13_arrival_painting_small", true, series = s) { io.github.graviton94.todayletters.ui.ArrivalBody(s, "mozart", "I", l, 1, { }, startAt = 0) }
    }
    /** 프롤로그 둘째 장: ‘이전’ · ‘건너뛰기’ · ‘다음’. */
    @Test fun mozartProloguePrev() { val s = mstate(); shot("m14_prologue_p2", true, 3000) { io.github.graviton94.todayletters.ui.PrologueScreen(s, Route.Prologue("mozart", 1)) } }
    /** 눌러서 뜻 보기: 편지 방에서 낱말 하나의 작은 카드 (표제어 · 등급 · 발음기호 · 뜻 · Tatoeba 예문 · 카드에 담기). */
    @Test fun mozartGloss() {
        val s = mstate(); shownAll(s, 1, 4)
        val w = s.work("mozart"); val (ch, l) = s.letterOf(Route.Letter("mozart", 1, 4))
        val key = io.github.graviton94.todayletters.data.Library.gloss(ctx, "mozart").getValue(l.id).flatten().first { t ->
            w.lexicon!![t.key]?.let { e -> !e.function && e.example != null && l.words.none { it.lex == e.key } } == true
        }.key
        shot("m15_gloss_card", false, 4000, series = s) {
            Box(Modifier.fillMaxSize()) {
                Room(s, Route.Letter("mozart", 1, 4))
                io.github.graviton94.todayletters.ui.GlossSheet(s, "mozart", ch, l, key) { }
            }
        }
    }
    /** 낱말 맞추기 다음의 표현 카드 (편지 속 표현 · 번역 · 쓰임). */
    @Test fun mozartPhrases() {
        val s = mstate()
        shot("m16_phrase_cards", true, series = s) { io.github.graviton94.todayletters.ui.PhraseCards(s, Route.Play(Route.Letter("mozart", 1, 1), ReplyMode.MATCH)) { } }
    }
    /** 눌러서 뜻 보기 공통 (GlossText): 이 메시지에서 뜻 카드를 펼칠 낱말 하나 (기능어가 아니고 사전에 있는 것). */
    private fun pick(s: AppState, series: String, chapter: String, l: io.github.graviton94.todayletters.core.Letter, msg: Int): AppState.GlossPick {
        val toks = s.glossTokens(series, l.id, msg)!!
        val lex = s.work(series).lexicon!!
        val j = toks.indices.first { lex[toks[it].key]?.function == false }
        return AppState.GlossPick(series, chapter, l, msg, j, toks[j].key)
    }
    /** 프롤로그의 원문 한 줄에서 낱말을 눌렀을 때 (금빛 바탕 + 아래 뜻 카드). */
    @Test fun mozartPrologueGloss() {
        val s = mstate()
        val l = s.work("mozart").prologue.first().letter
        s.gloss = pick(s, "mozart", "P", l, 0)
        shot("m17_prologue_gloss", true, 3000) {
            Box(Modifier.fillMaxSize()) {
                io.github.graviton94.todayletters.ui.PrologueScreen(s, Route.Prologue("mozart", 0))
                io.github.graviton94.todayletters.ui.GlossHost(s)
            }
        }
    }
    /** 따라 읽기 화면의 문장에서 낱말을 눌렀을 때 (읽는 자리 칠하기는 그대로). */
    @Test fun mozartReadAlongGloss() {
        val s = mstate()
        val room = Route.Letter("mozart", 1, 1)
        val (ch, l) = s.letterOf(room)
        s.gloss = pick(s, "mozart", ch, l, 0)
        shot("m18_read_along_gloss", false, 2000, series = s) {
            Box(Modifier.fillMaxSize()) {
                io.github.graviton94.todayletters.ui.ShadowScreen(s, Route.Shadow(room, only = 0))
                io.github.graviton94.todayletters.ui.GlossHost(s)
            }
        }
    }

    // ── 폰 시험 뒤 고친 것: 문장 아래 뜻 카드 · 튜토리얼 ‘다 읽었어요’ · 낱말 칸 맞추기 ──
    /** 편지 방에서 낱말을 누르면 그 문장 바로 아래에 작은 뜻 카드 (머리를 가리지 않는다). */
    @Test fun fixGlossRoom() {
        val s = state(true)
        val room = Route.Letter("vincent", 1, 1)
        val (ch, l) = s.letterOf(room)
        s.save("vincent", ch, l.id, s.progress("vincent", ch, l.id).copy(shown = l.messages.size))
        s.gloss = pick(s, "vincent", ch, l, 0)
        // 편지 끝까지 내려간 목록을 맨 위로 되돌려, 누른 첫 문장이 보이게
        shot("f01_room_gloss_below_light", false, 4000, act = {
            rule.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performTouchInput { swipeDown(); swipeDown(); swipeDown() }
            rule.mainClock.advanceTimeBy(600)
        }, series = s) {
            Box(Modifier.fillMaxSize()) { Room(s, room); io.github.graviton94.todayletters.ui.GlossHost(s) }
        }
    }
    /** 아래 자리가 모자라는 문장 (마지막 조각): 카드는 문장 위에. */
    @Test fun fixGlossRoomAbove() {
        val s = state(true)
        val room = Route.Letter("vincent", 1, 1)
        val (ch, l) = s.letterOf(room)
        s.save("vincent", ch, l.id, s.progress("vincent", ch, l.id).copy(shown = l.messages.size))
        s.gloss = pick(s, "vincent", ch, l, l.messages.lastIndex)
        shot("f02_room_gloss_last_dark", true, 4000, series = s) {
            Box(Modifier.fillMaxSize()) { Room(s, room); io.github.graviton94.todayletters.ui.GlossHost(s) }
        }
    }
    /** 튜토리얼: 다 읽은 뒤 ‘다음 · 낱말 맞추기’ 바로 위의 안내와 금빛 테 (어두운 테마). */
    @Test fun fixTutorialDoneDark() {
        val s = state(true, tutorial = true)
        s.startTutorial(s.seriesSettings("vincent").learn)
        shot("f03_room_guide_done_dark", true, 6000, series = s) { Room(s, Route.Letter("vincent", 1, 1, Route.Inbox)) }
    }
    /** 낱말 맞추기 (어두운 테마): 첫 낱말의 뜻을 맞힌 모습 (번역 문장에 뜻이 금빛). */
    @Test fun fixMatchDark() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        val v = s.room("vincent")
        val (pairs, _) = Exercises.matchBoard(l, v.learn, v.read)
        shot("f04_match8_dark", true, act = {
            rule.onAllNodes(hasText(pairs[0].second) and hasClickAction()).onFirst().performClick()
            rule.mainClock.advanceTimeBy(500)
        }, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.MATCH)) }
    }
    /** 낱말 맞추기, 긴 독일어 낱말 (모차르트 · 독일어로 배우기): 한 단계 작은 글자 · 두 줄, 너비는 42% 까지. */
    @Test fun fixMatchGerman() {
        val s = state(true)
        s.update("mozart", s.seriesSettings("mozart").copy(learn = io.github.graviton94.todayletters.core.Lang.DE))
        val v = s.room("mozart")
        val w = s.work("mozart")
        val (ci, li) = w.chapters.withIndex().flatMap { (c, ch) -> ch.letters.withIndex().map { (n, lt) -> Triple(c, n, lt) } }
            .filter { Exercises.matchBoard(it.third, v.learn, v.read).first.size >= 6 }
            .maxBy { t -> Exercises.matchBoard(t.third, v.learn, v.read).first.maxOf { it.first.length } }
            .let { it.first to it.second }
        shot("f05_match_german_long_light", false, series = s) { Play(s, Route.Play(Route.Letter("mozart", ci + 1, li + 1), ReplyMode.MATCH)) }
    }
    @Test fun fixMatchLarge() {
        val s = state(true)
        large("f06_match8", series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.MATCH)) }
    }

    // ── 흐름 개편: 튜토리얼 · 오늘의 여정 · 건너뛰기 ──
    /** 튜토리얼 1: 환영 한 장. */
    @Test fun tutorialWelcome() { val s = state(false); shot("t01_welcome_ko_light", false) { Onboarding(s) } }
    /** 튜토리얼 2: 서재, 빈센트 카드 강조 + 카드 안에서 배울 말 고르기. */
    @Test fun tutorialLibrary() { val s = state(true, tutorial = true); shot("t02_library_tutorial_ko_light", false) { LibraryTab(s) } }
    @Test fun tutorialLibraryDark() { val s = state(true, tutorial = true); shot("t02b_library_tutorial_ko_dark", true) { LibraryTab(s) } }
    /** 튜토리얼 3: 편지 I 의 읽기 걸음, 안내 한 줄. */
    @Test fun tutorialRoom() {
        val s = state(true, tutorial = true)
        s.startTutorial(s.seriesSettings("vincent").learn)
        shot("t03_room_guide_ko_light", false, 6000, series = s) { Room(s, Route.Letter("vincent", 1, 1, Route.Inbox)) }
    }
    /** 도착 한 장: 위에 그림, 아래에 첫 줄이 쓰이는 중. */
    @Test fun arrivalOne() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        shot("t04_arrival_one", false, 1800, series = s) { io.github.graviton94.todayletters.ui.ArrivalBody(s, "vincent", "I", l, 1, { }) }
    }
    /** 튜토리얼 4: 낱말 맞추기의 안내 한 줄과 ‘건너뛰기’. */
    @Test fun tutorialMatchSkip() {
        val s = state(true, tutorial = true)
        s.store.markGuideSeen("read")
        shot("t05_match_skip_ko_light", false, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.MATCH)) }
    }
    /** 따라 읽기 걸음: 안내 한 줄 (녹음만 해도 통과) · 건너뛰기. */
    @Test fun tutorialAloudSkip() {
        val s = state(true, tutorial = true)
        shot("t06_aloud_skip_ko_dark", true, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.ALOUD)) }
    }
    /** 오늘의 여정: 편지는 마침 (따라 읽기 · 답장은 건너뜀), 복습은 다섯 중 둘, 완료는 아직. */
    private fun journeyState(): AppState {
        val s = withCards()
        val l = s.work("vincent").chapters.first().letters.first()
        s.store.markStarted(s.key("vincent", "I", l.id)); s.store.markOpened(s.space("vincent"))
        s.save("vincent", "I", l.id, io.github.graviton94.todayletters.core.LetterProgress(shown = l.messages.size, replied = setOf(ReplyMode.MATCH),
            skipped = setOf(ReplyMode.ALOUD, ReplyMode.CONSTELLATION), done = true))
        val keys = s.dueCards("vincent").map { it.key }
        s.store.saveSession(s.reviewSession("vincent"), Store.SavedSession(s.today, keys, 2.coerceAtMost(keys.size), List(2.coerceAtMost(keys.size)) { true }))
        s.reward = null
        return s
    }
    @Test fun todayJourney() { val s = journeyState(); shot("t07_today_journey_ko_light", false, 2000, series = s) { Inbox(s) } }
    @Test fun todayJourneyDark() { val s = journeyState(); shot("t07b_today_journey_ko_dark", true, 2000, series = s) { Inbox(s) } }
    /** 첫날: 편지를 마쳤고 복습할 카드가 아직 없음 → 여정 완료 (보너스 · 편지 그림). */
    @Test fun todayJourneyDone() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        s.store.markStarted(s.key("vincent", "I", l.id)); s.store.markOpened(s.space("vincent"))
        s.save("vincent", "I", l.id, io.github.graviton94.todayletters.core.LetterProgress(shown = l.messages.size, replied = setOf(ReplyMode.MATCH, ReplyMode.ALOUD, ReplyMode.CONSTELLATION), done = true))
        s.reward = null
        shot("t08_today_journey_done_ko_light", false, 2000, series = s) { Inbox(s) }
    }
    /** 복습 탭: 오늘의 복습 (이 작품만) · 자유 연습 · 다시 하기 (건너뛴 걸음). */
    @Test fun reviewRedo() { val s = journeyState(); shot("t09_review_redo_ko_light", false, 2000, series = s) { WordsTab(s) } }
    /** 편지 방: 다 읽은 편지의 걸음 목록에 ‘다시 하기’ (건너뛴 걸음). */
    @Test fun roomRedo() { val s = journeyState(); shot("t10_room_redo_ko_light", false, 4000, series = s) { Room(s, Route.Letter("vincent", 1, 1, Route.Inbox)) } }
    /** 튜토리얼 끝: 편지 I 완료 위에 “이제 혼자서도 괜찮아요”. */
    @Test fun graduate() {
        val s = state(true, tutorial = true)
        val l = s.work("vincent").chapters.first().letters.first()
        s.save("vincent", "I", l.id, io.github.graviton94.todayletters.core.LetterProgress(shown = l.messages.size, replied = setOf(ReplyMode.MATCH), skipped = setOf(ReplyMode.ALOUD, ReplyMode.CONSTELLATION), done = true))
        s.reward = null
        shot("t11_graduate_ko_light", false, 4000, series = s) { Done(s, Route.Letter("vincent", 1, 1)) }
    }

    // ── 메달 진열장 · 끝없는 레벨 ──
    /** 메달 진열장: 받은 메달은 금속과 등급 테, 다음 메달은 석고 본과 진행 ("12/14일"). */
    @Test fun cabinet() { val s = engineState(); s.reward = null; shot("c01_cabinet_ko_light", false) { io.github.graviton94.todayletters.ui.MedalsScreen(s) } }
    @Test fun cabinetDark() { val s = engineState(); s.reward = null; shot("c02_cabinet_ko_dark", true) { io.github.graviton94.todayletters.ui.MedalsScreen(s) } }
    /** 메달 자세히: 받은 메달 (유물 이름 · 시기 · 소장처 · 받은 까닭). */
    @Test fun medalDetail() {
        val s = engineState(); s.reward = null
        val m = s.medalBook.medal("streak_7")!!
        shot("c03_medal_detail_ko_light", false, series = s) { io.github.graviton94.todayletters.ui.MedalDetail(s, m) { } }
    }
    /** 메달 자세히: 다음 메달 (석고 본 · 진행 · 받는 법). */
    @Test fun medalDetailNext() {
        val s = engineState(); s.reward = null
        val m = s.medalBook.medal("streak_14")!!
        shot("c04_medal_detail_next_ko_dark", true, series = s) { io.github.graviton94.todayletters.ui.MedalDetail(s, m) { } }
    }
    /** 칭호 줄: 서재 머리 (“학예 보조 II · Lv 17” 과 얇은 막대). */
    @Test fun levelLine() { val s = engineState(); s.reward = null; shot("c05_level_line_library_ko_light", false, 3000) { LibraryTab(s) } }
    /** 칭호 줄: 작품 머리 (오늘). */
    @Test fun levelLineSeries() { val s = engineState(); s.reward = null; shot("c06_level_line_series_ko_light", false, 3000, series = s) { Inbox(s) } }
    /** 레벨 업 순간: 보상 순간과 같은 전시실 벽, 새 칭호와 새 입구 장식 (15 레벨). */
    @Test fun levelUp() {
        val s = engineState(); s.reward = null
        s.store.saveXp(io.github.graviton94.todayletters.core.XpLedger(total = io.github.graviton94.todayletters.core.Levels.totalFor(15) + 40))
        s.levelUp = AppState.LevelUp(14, 15)
        shot("c07_level_up_ko_dark", true, 2000) { io.github.graviton94.todayletters.ui.LevelUpOverlay(s) }
    }
    /** 보상 순간의 새 메달 (금속 메달과 이름). */
    @Test fun rewardMedal() {
        val s = engineState()
        s.reward = AppState.RewardMoment("vincent", 10, null, listOf("streak_7"), null, listOf(io.github.graviton94.todayletters.core.Earn.ACHIEVEMENT to 10))
        shot("c08_reward_medal_ko_dark", true, 2000) { io.github.graviton94.todayletters.ui.RewardOverlay(s) }
    }
    /** 입구 장식: 레벨 17 이면 ‘대리석 기둥 한 쌍’ (15 레벨) 이 입구에 조용히. */
    @Test fun openingDecoration() { val s = engineState(); s.reward = null; shot("c09_opening_decoration_ko_dark", true, 4500) { io.github.graviton94.todayletters.ui.Opening(s) } }

    /** 굴려서 찾기는 굴리는 움직임이 끝나야 돌아오므로, 그동안만 시계를 흘린다 (멈춘 시계면 끝나지 않는다). */
    private fun scrolled(block: () -> Unit) {
        rule.mainClock.autoAdvance = true
        try { block() } finally { rule.mainClock.autoAdvance = false }
    }

    // ── APK 20 피드백: 상점 · 구독 · 나의 기록 · 갤러리 세 묶음 · 알림 시계 바퀴 · 확인 창 ──
    @Test fun purchase() { val s = engineState(); s.reward = null; shot("p01_purchase_light", false, series = s) { io.github.graviton94.todayletters.ui.PurchaseScreen(s) } }
    @Test fun purchaseDark() { val s = engineState(); s.reward = null; shot("p02_purchase_dark", true, series = s) { io.github.graviton94.todayletters.ui.PurchaseScreen(s) } }
    // ── 기록 보관 (시안 backup.html): 설정 묶음 · 내보내기 시트 · 가져오기 확인 · 구매 화면의 작품별 소장 칸 ──
    @Test fun backupSettings() {
        val s = engineState(); s.reward = null; s.store.backupDay = s.today
        shot("bk1_settings_backup_light", false, act = {
            scrolled { rule.onNodeWithText(ctx.getString(R.string.backup_note)).performScrollTo() }
        }) { io.github.graviton94.todayletters.ui.SettingsScreen(s) }
    }
    @Test fun ubBackupExport() {
        val s = engineState(); s.reward = null
        shot("ub4_backup_export_sheet_light", false, act = {
            scrolled { rule.onNodeWithText(ctx.getString(R.string.backup_export)).performScrollTo().performClick() }
        }) { io.github.graviton94.todayletters.ui.SettingsScreen(s) }
    }
    @Test fun ubBackupImport() {
        val s = engineState(); s.reward = null
        shot("ub5_backup_import_sheet_light", false, act = {
            scrolled { rule.onNodeWithText(ctx.getString(R.string.backup_import)).performScrollTo().performClick() }
        }) { io.github.graviton94.todayletters.ui.SettingsScreen(s) }
    }
    /** 가져오기 확인: 파일 = 지금 기록을 내보낸 것, 지금 폰 = 새 폰처럼 작은 수 (나란히 다르게 보이게). */
    @Test fun ubBackupConfirm() {
        val s = engineState(); s.reward = null
        val r = io.github.graviton94.todayletters.core.Backup.decode(io.github.graviton94.todayletters.data.Records.export(s.store, s.today))!!
        shot("ub6_backup_confirm_light", false) {
            io.github.graviton94.todayletters.ui.SettingsScreen(s)
            io.github.graviton94.todayletters.ui.BackupImportSheet(r.summary, io.github.graviton94.todayletters.core.Backup.Summary(2, 9, r.summary.made), onImport = { }, onClose = { })
        }
    }
    /** 오늘 화면의 보관 한 줄: 내보낸 적이 없고 어제 처음 한 장을 다 마침. */
    @Test fun backupToday() {
        val s = engineState(); s.reward = null; s.store.backupChapterDay = s.today - 1
        shot("bk5_today_backup_line_light", false, 3000, series = s) { Inbox(s) }
    }
    /** 구매 화면: 연 · 월 구독 + 작품마다 소장 칸 (지금 작품 먼저, 모차르트는 소장함). */
    @Test fun backupPurchaseAll() {
        val s = engineState(); s.reward = null
        s.store.entitlement = io.github.graviton94.todayletters.core.Entitlement.of(listOf("series_mozart"))
        val t = AppState(ctx)
        shot("bk4_purchase_all_light", false, series = t) { io.github.graviton94.todayletters.ui.PurchaseScreen(t) }
    }
    // ── 판매 정책 (v4 시안): 상점 금테 배너 · 구매 화면 셋 칸 · 나의 기록의 시리즈 진도와 이용권 ──
    @Test fun shopBanner() { val s = engineState(); s.reward = null; shot("p15_shop_banner_light", false, act = {
        scrolled { rule.onNodeWithText(ctx.getString(R.string.unlock_all)).performScrollTo() }
    }, series = s) { io.github.graviton94.todayletters.ui.ShopTab(s) } }
    @Test fun shopBannerDark() { val s = engineState(); s.reward = null; shot("p16_shop_banner_dark", true, act = {
        scrolled { rule.onNodeWithText(ctx.getString(R.string.unlock_all)).performScrollTo() }
    }, series = s) { io.github.graviton94.todayletters.ui.ShopTab(s) } }
    /** 구매 화면에서 시리즈 소장 칸을 고른 모습 (모차르트에서 들어옴). */
    @Test fun purchaseSeries() {
        val s = engineState(); s.reward = null; s.enter("mozart")
        shot("p17_purchase_series_light", false, act = {
            rule.onNodeWithText(ctx.getString(R.string.plan_series, s.work("mozart").known[io.github.graviton94.todayletters.core.Lang.KO])).performClick()
        }, series = s) { io.github.graviton94.todayletters.ui.PurchaseScreen(s) }
    }
    /** 빈센트를 소장한 뒤: 소장함 표시, 배너에는 모차르트만. */
    @Test fun purchaseOwned() {
        val s = engineState(); s.reward = null
        s.store.entitlement = io.github.graviton94.todayletters.core.Entitlement.of(listOf("series_vincent"))
        val t = AppState(ctx)
        shot("p18_purchase_owned_dark", true, series = t) { io.github.graviton94.todayletters.ui.PurchaseScreen(t) }
    }
    /** 연 구독 중: 연 구독 칸에만 이용 중 표시 · Play 스토어에서 구독 관리, 나의 기록의 이용권 줄. */
    @Test fun purchaseSubscribed() {
        val s = engineState(); s.reward = null
        s.store.entitlement = io.github.graviton94.todayletters.core.Entitlement.of(listOf("sub_all")).copy(basePlan = io.github.graviton94.todayletters.core.Products.YEARLY)
        val t = AppState(ctx)
        shot("p19_purchase_subscribed_light", false, series = t) { io.github.graviton94.todayletters.ui.PurchaseScreen(t) }
    }
    @Test fun recordPass() { val s = engineState(); s.reward = null; shot("p20_record_pass_light", false, act = {
        scrolled { rule.onNodeWithText(ctx.getString(R.string.unlock_all), substring = true).performScrollTo() }
    }) { io.github.graviton94.todayletters.ui.RecordScreen(s) } }
    @Test fun seriesCoverLocked() { val s = engineState(); s.reward = null; shot("p21_cover_locked_light", false, act = {
        scrolled { rule.onAllNodes(hasText(ctx.getString(R.string.chapter_locked))).onFirst().performScrollTo() }
    }, series = s) { io.github.graviton94.todayletters.ui.SeriesCover(s, "vincent") } }
    @Test fun recordDark() { val s = engineState(); s.reward = null; shot("p03_record_dark", true) { io.github.graviton94.todayletters.ui.RecordScreen(s) } }
    @Test fun galleryGroups() { val s = engineState(); s.reward = null; shot("p04_gallery_groups_light", false, series = s) { io.github.graviton94.todayletters.ui.GalleryTab(s) } }
    @Test fun galleryEmpty() { val s = state(true); shot("p05_gallery_empty_light", false, series = s) { io.github.graviton94.todayletters.ui.GalleryTab(s) } }
    @Test fun collection() { val s = engineState(); s.reward = null; shot("p06_collection_light", false, series = s) { io.github.graviton94.todayletters.ui.CollectionScreen(s, "vincent") } }
    @Test fun alarmWheel() {
        val s = state(true)
        shot("p07_alarm_wheel_light", false, act = {
            scrolled { rule.onNodeWithText(ctx.getString(R.string.set_arrival)).performScrollTo().performClick() }
        }) { io.github.graviton94.todayletters.ui.SettingsScreen(s) }
    }
    @Test fun alarmWheelDark() {
        val s = state(true)
        s.store.noticeHour = 19; s.store.noticeMinute = 35
        shot("p08_alarm_wheel_dark", true) {
            io.github.graviton94.todayletters.ui.SettingsScreen(s)
            io.github.graviton94.todayletters.ui.NoticeTimeSheet(s) { }
        }
    }
    @Test fun tutorialConfirm() {
        val s = state(true)
        shot("p09_tutorial_confirm_light", false, act = {
            scrolled { rule.onNodeWithText(ctx.getString(R.string.set_help_again)).performScrollTo().performClick() }
        }) { io.github.graviton94.todayletters.ui.SettingsScreen(s) }
    }
    @Test fun clearConfirm() {
        val s = state(true)
        shot("p10_clear_confirm_dark", true) {
            io.github.graviton94.todayletters.ui.SettingsScreen(s)
            io.github.graviton94.todayletters.ui.Ask(ctx.getString(R.string.clear_t), ctx.getString(R.string.clear_yes), ctx.getString(R.string.cancel), { }, { },
                body = ctx.getString(R.string.clear_b, "64.0MB"))
        }
    }
    @Test fun predownloadAsk() {
        val s = state(true)
        val ch = s.work("vincent").chapters[1]
        s.downloadAsk = AppState.DownloadAsk(s.downloads.pack("vincent", ch.id)!!, "vincent", ch.id, null, null)
        shot("p11_predownload_ask_light", false, series = s) { io.github.graviton94.todayletters.ui.DownloadSheet(s, s.downloadAsk!!) }
    }
    @Test fun learnConfirm() {
        val s = state(true)
        shot("p12_learn_confirm_light", false, series = s) {
            SeriesSettingsScreen(s, "vincent")
            io.github.graviton94.todayletters.ui.Ask(ctx.getString(R.string.learn_confirm_t), ctx.getString(R.string.learn_confirm_yes), ctx.getString(R.string.cancel), { }, { },
                body = ctx.getString(R.string.learn_confirm_b, ctx.getString(R.string.lang_en), ctx.getString(R.string.lang_fr)))
        }
    }
    @Test fun todayFooter() { val s = withCards(); shot("p13_today_footer_light", false, 2000, act = {
        repeat(3) { rule.onRoot().performTouchInput { swipeUp() }; rule.mainClock.advanceTimeBy(1200) }
    }, series = s) { Inbox(s) } }
    @Test fun openingScrim() {
        val s = engineState(); s.reward = null
        // 밝은 그림 셋 이상을 가진 상태 (입구 장식 줄이 밝은 그림 위에)
        shot("p14_opening_scrim_dark", true, 4500) { io.github.graviton94.todayletters.ui.Opening(s) }
    }
    @Test fun feedbackSheet() {
        val s = state(true)
        s.feedback = AppState.FeedbackAsk()
        shot("p15_feedback_light", false) {
            io.github.graviton94.todayletters.ui.SettingsScreen(s)
            io.github.graviton94.todayletters.ui.FeedbackSheet(s, s.feedback!!)
        }
    }
    @Test fun feedbackSheetDark() {
        val s = state(true)
        s.feedback = AppState.FeedbackAsk(io.github.graviton94.todayletters.core.Feedback.Kind.IDEA)
        shot("p16_feedback_dark", true) {
            io.github.graviton94.todayletters.ui.SettingsScreen(s)
            io.github.graviton94.todayletters.ui.FeedbackSheet(s, s.feedback!!)
        }
    }
    @Test fun crashAsk() {
        val s = state(true)
        s.crashAsk = true
        shot("p17_crash_ask_light", false, series = s) {
            Inbox(s)
            io.github.graviton94.todayletters.ui.CrashAsk(s)
        }
    }
    @Test fun predownloadRunning() {
        val s = state(true)
        val ch = s.work("vincent").chapters[1]
        val pack = s.downloads.pack("vincent", ch.id)!!
        // 진행 숫자는 실제 꾸러미 크기에 맞춘다 (시트의 ‘장 · 크기’ 줄과 같은 수)
        DownloadJob.show(DownloadJob.State("vincent", ch.id, s.work("vincent").name[io.github.graviton94.todayletters.core.Lang.KO], pack.bytes * 36 / 100, pack.bytes, DownloadJob.Phase.RUNNING))
        s.downloadAsk = AppState.DownloadAsk(pack, "vincent", ch.id, null, null)
        try { shot("p18_predownload_running_light", false, series = s) { io.github.graviton94.todayletters.ui.DownloadSheet(s, s.downloadAsk!!) } }
        finally { DownloadJob.show(null) }
    }

    // ── 작품 받기 (시안 assets.html): 모차르트를 처음 열 때 · 받는 중 · 닿음 · 끊김. '*ScreensTest.dl*' 로 따로 돌린다.
    private fun dlShot(name: String, phase: DownloadJob.Phase?) {
        val s = state(true)
        s.enter("mozart")
        val pack = s.downloads.first("mozart")!!
        if (phase != null) DownloadJob.show(DownloadJob.State("mozart", pack.chapter, s.work("mozart").name[io.github.graviton94.todayletters.core.Lang.KO], pack.bytes * 42 / 100, pack.bytes, phase))
        s.downloadAsk = AppState.DownloadAsk(pack, "mozart", pack.chapter, null, null)
        try {
            shot(name, false, series = s) {
                io.github.graviton94.todayletters.ui.SeriesCover(s, "mozart")
                s.downloadAsk?.let { io.github.graviton94.todayletters.ui.DownloadSheet(s, it) }
            }
        } finally { DownloadJob.show(null) }
    }
    @Test fun dlAsk() = dlShot("dl1_ask_light", null)
    @Test fun dlLoading() = dlShot("dl2_loading_light", DownloadJob.Phase.RUNNING)
    @Test fun dlDone() = dlShot("dl3_done_light", DownloadJob.Phase.DONE)
    @Test fun dlFailed() = dlShot("dl4_failed_light", DownloadJob.Phase.FAILED)
    /** 편지를 열려다 연 시트: ‘다음에’ · ‘소리 없이 읽기’ 가 한 줄에. */
    @Test fun dlSilentAsk() {
        val s = state(true)
        val c = s.work("mozart").chapters[0]
        s.open("mozart", c.id, c.letters[0], Route.Inbox)
        shot("dl5_ask_silent_light", false, series = s) {
            io.github.graviton94.todayletters.ui.SeriesCover(s, "mozart")
            s.downloadAsk?.let { io.github.graviton94.todayletters.ui.DownloadSheet(s, it) }
        }
    }
    /** 아무것도 받지 않고 ‘소리 없이 읽기’ 로 연 모차르트 I-1: 그림 · 곡 · 낭독 단추 없이 글만. */
    @Test fun dlSilentRoom() {
        val s = state(true)
        val c = s.work("mozart").chapters[0]
        s.open("mozart", c.id, c.letters[0], Route.Inbox)
        s.downloadFinished(silent = true)
        val r = s.route as Route.Letter
        shownAll(s, 1, 1)
        shot("dl6_room_silent_c1l1_ko_light", false, 4000, series = s) { Room(s, r) }
    }

    // ── 언어 등급 · 답장 사다리 (docs/next.md 다음 할 일 1): 사다리 · 아는 낱말 % · 처음 → 지금 · 번역 접기 · 등급 블록 · 축하 · 메달 가족 · 작품 설정 ──
    /** 지금 배우는 말(빈센트)의 언어 등급을 [index] (1 = A1 · 1단 … 12 = B2 · 3단) 로. 닿았던 등급 아래로는 내려가지 않으므로 바닥으로 넣는다. */
    private fun graded(s: AppState, index: Int): AppState {
        s.store.setGrade(s.seriesSettings("vincent").learn.code, index); s.bump()
        return s
    }
    /** 편지 [l] (I장) 의 문장 [msgs] 에 나오는 낱말을 모두 내 것으로 (떠올리기 이상): 아는 낱말 % 를 올리고 그 문장의 번역을 접게 한다. */
    private fun knowWords(s: AppState, l: io.github.graviton94.todayletters.core.Letter, msgs: List<Int>) {
        val lex = s.work("vincent").lexicon
        msgs.flatMap { s.letterKeys("vincent", l.id, it).orEmpty() }.distinct().filter { lex?.get(it) != null }.forEach { k ->
            s.store.save(io.github.graviton94.todayletters.core.Card(s.ownKey("vincent", "I", l, k), box = 5, due = s.today + 30, seen = 6))
        }
        s.bump()
    }
    private val replyRoute get() = Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.CONSTELLATION)
    /** 답장 사다리 A1 · 2단: 빈칸 하나, 낱말 셋 가운데 고르기 (A1 · 1단 잇기는 09 · 10 별자리 사진). */
    @Test fun ladderChoose() { val s = graded(state(true), 2); shot("l01_ladder_choose_a1_light", false, series = s) { Play(s, replyRoute) } }
    /** A2 · 2단: 여전히 빈칸 하나 (어두운 화면). */
    @Test fun ladderChooseDark() { val s = graded(state(true), 5); shot("l02_ladder_choose_a2_dark", true, series = s) { Play(s, replyRoute) } }
    /** B1 · 2단: 빈칸 둘, 같은 낱말의 다른 꼴이 미끼로. */
    @Test fun ladderChooseTwo() { val s = graded(state(true), 8); shot("l03_ladder_choose_two_b1_light", false, series = s) { Play(s, replyRoute) } }
    /** B1: ‘정답 보기’ 로 다 채운 뒤 (밑줄 친 답장 · 보내기 단추). */
    @Test fun ladderFilled() {
        val s = graded(state(true), 8)
        shot("l04_ladder_filled_b1_dark", true, act = {
            rule.onAllNodes(hasText(ctx.getString(R.string.play_show)) and hasClickAction()).onFirst().performClick()
        }, series = s) { Play(s, replyRoute) }
    }
    /** B2 · 2단: 직접 쓰기 (빈칸에 쳐서). */
    @Test fun ladderWrite() { val s = graded(state(true), 11); shot("l05_ladder_write_b2_light", false, series = s) { Play(s, replyRoute) } }
    /** B2: 틀리게 쓰고 확인 → 한 글자 힌트. */
    @Test fun ladderWriteHint() {
        val s = graded(state(true), 11)
        shot("l06_ladder_write_hint_b2_dark", true, act = {
            rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("zzz")
            rule.mainClock.advanceTimeBy(300)
            rule.onAllNodes(hasText(ctx.getString(R.string.ladder_check)) and hasClickAction()).onFirst().performClick()
        }, series = s) { Play(s, replyRoute) }
    }
    /** 편지 머리의 ‘아는 낱말 %’ (날짜 옆): 세 통을 읽고 낱말을 모은 뒤 넷째 편지. */
    @Test fun knownPct() {
        val s = engineState(); s.reward = null
        val l = s.work("vincent").chapters.first().letters[3]
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = 2))
        shot("g01_room_known_pct_light", false, 4000, series = s) { Room(s, Route.Letter("vincent", 1, 4)) }
        // 머리 줄은 한 줄로 잘리므로, 사진에 % 가 안 보이면 값이 없는 것인지 잘린 것인지 여기서 가린다
        org.junit.Assert.assertNotNull("coverage", s.coverage("vincent", l.id))
    }
    /** 편지 머리의 더 보기 메뉴 (대화방 정보): 메뉴는 따로 뜬 창이라 화면 전체를 찍는다. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun roomMenu() {
        val s = state(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            TodayLettersTheme(ThemeMode.LIGHT) {
                io.github.graviton94.todayletters.ui.InSeries(s.currentWork) { Box(Modifier.fillMaxSize().background(Ink.palette.paper)) { Room(s, Route.Letter("vincent", 1, 1)) } }
            }
        }
        rule.mainClock.advanceTimeBy(4000)
        rule.onNode(androidx.compose.ui.test.hasContentDescription(ctx.getString(R.string.room_more))).performClick()
        rule.mainClock.advanceTimeBy(300)
        // 빠르기는 메뉴에서 빠졌다 (문장마다의 0.7× · 작품 설정의 기본 듣기 속도)
        rule.onNode(hasText(ctx.getString(R.string.room_info))).assertExists()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/g02_room_menu_light.png")
    }
    /** 다 읽은 편지의 ‘처음 읽을 때 → 지금’ (다시 읽기 띠). */
    @Test fun rereadCoverage() {
        val s = engineState(); s.reward = null
        val l = s.work("vincent").chapters.first().letters.first()
        s.store.setFirstCoverage(s.key("vincent", "I", l.id), 58)
        knowWords(s, l, listOf(0, 1))
        // 다 읽은 편지는 끝으로 내려가 있으므로 맨 위의 다시 읽기 띠로 올린다
        shot("g02_room_reread_cov_light", false, 3000, act = {
            // 편지 목록은 LazyColumn 이라 화면 밖 띠는 아직 그려지지 않았다: 목록을 맨 위로 굴린다
            scrolled { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performTouchInput { swipeDown(); swipeDown(); swipeDown() } }
        }, series = s) { Room(s, room(1)) }
    }
    /** 번역 줄은 접지 않는다: B1 에서는 옅게만 (다 아는 문장도 번역이 보인다). 다 읽은 편지라 끝 쪽이 보인다. */
    @Test fun foldedTranslation() {
        val s = graded(engineState().also { it.reward = null }, 7)
        val l = s.work("vincent").chapters.first().letters.first()
        knowWords(s, l, (1 until l.messages.size).toList())
        shot("g03_room_folded_b1_light", false, 3000, series = s) { Room(s, room(1)) }
        org.junit.Assert.assertTrue("translation dims at B1", s.readAlpha("vincent") < 1f)
    }
    @Test fun foldedTranslationDark() {
        val s = graded(engineState().also { it.reward = null }, 10)
        val l = s.work("vincent").chapters.first().letters.first()
        knowWords(s, l, l.messages.indices.toList())
        shot("g04_room_folded_b2_dark", true, 3000, series = s) { Room(s, room(1)) }
    }
    /** 나의 기록의 언어 등급 블록 (등급 · 단, 막대, 떠올릴 수 있는 낱말 · 읽을 수 있는 비율, 다음에 필요한 것). */
    @Test fun recordGrade() {
        val s = graded(engineState().also { it.reward = null }, 5)
        shot("g05_record_grade_light", false, act = {
            scrolled { rule.onAllNodes(hasText(ctx.getString(R.string.grade_title))).onFirst().performScrollTo() }
        }) { io.github.graviton94.todayletters.ui.RecordScreen(s) }
    }
    @Test fun recordGradeDark() {
        val s = graded(engineState().also { it.reward = null }, 8)
        shot("g06_record_grade_dark", true, act = {
            scrolled { rule.onAllNodes(hasText(ctx.getString(R.string.grade_title))).onFirst().performScrollTo() }
        }) { io.github.graviton94.todayletters.ui.RecordScreen(s) }
    }
    /** 등급 축하: 새 등급에 들어섬 (A1 · 3단 → A2 · 1단, 번역 줄이 물러나는 한 줄). */
    @Test fun gradeUpBand() {
        val s = engineState(); s.reward = null
        val lang = s.seriesSettings("vincent").learn
        s.gradeUp = AppState.GradeUp(lang, io.github.graviton94.todayletters.core.Reading.Grade.of(3), io.github.graviton94.todayletters.core.Reading.Grade.of(4))
        shot("g07_grade_up_band_dark", true, 2000) { io.github.graviton94.todayletters.ui.GradeUpOverlay(s) }
    }
    /** 등급 축하: 같은 등급 안에서 한 단 (A2 · 1단 → 2단). */
    @Test fun gradeUpStep() {
        val s = engineState(); s.reward = null
        val lang = s.seriesSettings("vincent").learn
        s.gradeUp = AppState.GradeUp(lang, io.github.graviton94.todayletters.core.Reading.Grade.of(4), io.github.graviton94.todayletters.core.Reading.Grade.of(5))
        shot("g08_grade_up_step_light", false, 2000) { io.github.graviton94.todayletters.ui.GradeUpOverlay(s) }
    }
    // ── 처음 시작 · 천천히 듣는 2주 · 들은 시간 (시안 onboard.html · grade.html) ──
    private fun freshStart(): AppState {
        val s = state(onboarded = true)
        s.store.markLearnChosen("vincent")
        return s
    }
    @Test fun start1Level() { val s = freshStart(); shot("st1_level_light", false, 1500, series = s) { io.github.graviton94.todayletters.ui.StartSheet(s, "vincent") } }
    @Test fun start2Why() { val s = freshStart(); shot("st2_why_light", false, 1500, series = s) { io.github.graviton94.todayletters.ui.StartSheet(s, "vincent", startAt = 1) } }
    @Test fun start3Amount() { val s = freshStart(); shot("st3_amount_dark", true, 1500, series = s) { io.github.graviton94.todayletters.ui.StartSheet(s, "vincent", startAt = 2) } }
    /** 처음이에요 → 낱말을 줍는 사람 1단 + 천천히 듣는 2주 (14일), 첫 편지로. 이미 쓰던 사람은 묻지 않는다. */
    @Test fun startFlow() {
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().clear().commit()
        Store(ctx).onboarded = true
        val s = AppState(ctx, deepLink = false)
        s.startTutorial(s.seriesSettings("vincent").learn)
        org.junit.Assert.assertEquals("vincent", s.startAsk)
        s.finishStart("vincent", io.github.graviton94.todayletters.core.Start.Level.NEW, io.github.graviton94.todayletters.core.Start.Why.ARTS, io.github.graviton94.todayletters.core.Start.Amount.MORE)
        org.junit.Assert.assertNull(s.startAsk)
        org.junit.Assert.assertEquals(1, s.slowDay("vincent"))
        org.junit.Assert.assertEquals(io.github.graviton94.todayletters.core.Start.Amount.MORE, s.seriesSettings("vincent").amount)
        // 첫 편지로 (새 편지는 예열부터)
        org.junit.Assert.assertTrue(s.route is Route.Letter || s.route is Route.WarmUp)
        org.junit.Assert.assertFalse(s.needsStart("vincent"))
    }
    /** 오늘 · 천천히 듣는 2주 6일째: 맨 위 ‘어제 편지 다시 듣기 · 1분’, 오늘 편지 ‘먼저 0.7×로 한 번’, 이어 읽기 줄 옆 ‘오늘 N분’. */
    @Test fun startSlowToday() {
        val s = engineState(); s.reward = null
        val cur = s.seriesSettings("vincent")
        s.update("vincent", cur.copy(slowFrom = s.today - 5, slowDays = 14))
        s.store.addListen(cur.learn.code, s.today, 12 * 60_000L)
        // 마친 세 통은 어제 마친 것으로 (어제 편지 다시 듣기 · 오늘 편지는 IV)
        val log = ctx.getSharedPreferences("letters", Context.MODE_PRIVATE)
        log.edit().putStringSet("log:letters", log.getStringSet("log:letters", emptySet())!!.map { "${s.today - 1}:" + it.substringAfter(":") }.toSet()).commit()
        s.bump()
        shot("st4_today_slow_light", false, 3000, series = s) { Inbox(s) }
    }
    /** 나의 기록: 새 등급 이름 (문장을 읽는 사람 2단) · 편지를 들은 시간 · 이번 주 · 아는 낱말이 들리는 비율. */
    @Test fun startRecordListen() {
        val s = graded(engineState().also { it.reward = null }, 5)
        val lang = s.seriesSettings("vincent").learn.code
        s.store.addListen(lang, s.today - 40, (2 * 60 + 32) * 60_000L)
        s.store.addListen(lang, s.today - 1, 30 * 60_000L)
        s.store.addListen(lang, s.today, 18 * 60_000L)
        val l = s.work("vincent").chapters.first().letters.first()
        val now = s.coverage("vincent", l.id) ?: 60
        s.store.setFirstCoverage(s.key("vincent", "I", l.id), (now - 14).coerceAtLeast(0))
        s.bump()
        shot("st5_record_listen_light", false, act = {
            scrolled { rule.onAllNodes(hasText(ctx.getString(R.string.listen_title))).onFirst().performScrollTo() }
        }) { io.github.graviton94.todayletters.ui.RecordScreen(s) }
    }
    /** 단이 올라 새 등급: “편지를 읽는 사람이 됐어요” · 번역은 조금 더 옅게 (접지 않음). */
    @Test fun startGradeUp() {
        val s = engineState(); s.reward = null
        val lang = s.seriesSettings("vincent").learn
        s.gradeUp = AppState.GradeUp(lang, io.github.graviton94.todayletters.core.Reading.Grade.of(6), io.github.graviton94.todayletters.core.Reading.Grade.of(7))
        shot("st6_grade_up_dark", true, 2000) { io.github.graviton94.todayletters.ui.GradeUpOverlay(s) }
    }
    /** 메달 진열장의 언어 등급 가족: A2 · B1 메달을 받았고 다음은 B2 (석고 본 · 진행). */
    private fun gradeMedals(): AppState {
        val s = graded(engineState().also { it.reward = null }, 8)
        s.store.awardMedal("grade_4", s.today - 9); s.store.awardMedal("grade_7", s.today - 2); s.bump()
        return s
    }
    @Test fun cabinetGrade() {
        val s = gradeMedals()
        val title = s.medalBook.family("grade")!!.title[io.github.graviton94.todayletters.core.Lang.KO]
        shot("g09_cabinet_grade_light", false, act = {
            // 가족 이름이 화면 아래 끝에 걸리므로 반 화면쯤 더 올린다
            scrolled {
                rule.onAllNodes(hasText(title)).onFirst().performScrollTo()
                rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.85f, endY = bottom * 0.35f) }
            }
        }) { io.github.graviton94.todayletters.ui.MedalsScreen(s) }
    }
    @Test fun medalDetailGrade() {
        val s = gradeMedals()
        shot("g10_medal_detail_grade_dark", true, series = s) { io.github.graviton94.todayletters.ui.MedalDetail(s, s.medalBook.medal("grade_7")!!) { } }
    }
    /** 작품 설정: 하루 편지 수 줄 없이, 번역 보기 아래 ‘실력만큼 번역 물러나기’. */
    @Test fun seriesSettingsFade() {
        val s = state(true)
        shot("g11_series_settings_fade_light", false, act = {
            scrolled { rule.onNodeWithText(ctx.getString(R.string.set_fade_read_d)).performScrollTo() }
        }, series = s) { SeriesSettingsScreen(s, "vincent") }
    }
    @Test fun seriesSettingsFadeDark() {
        val s = state(true)
        shot("g12_series_settings_fade_dark", true, act = {
            scrolled { rule.onNodeWithText(ctx.getString(R.string.set_fade_read_d)).performScrollTo() }
        }, series = s) { SeriesSettingsScreen(s, "vincent") }
    }

    // ── 키보드 (IME): 쓰는 칸이 열리면 화면이 키보드 위로 줄고, 문제 · 칸 · 확인이 보이는지 ──
    @Test fun imeLadderWrite() {
        val s = graded(state(true), 11)
        shotIme("k01_ime_ladder_write_b2_light", false, act = { rule.onAllNodes(hasSetTextAction()).onFirst().performClick(); rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("je") }, series = s) { Play(s, replyRoute) }
    }
    @Test fun imeDictation() {
        val s = withCards()
        shotIme("k02_ime_dictation_light", false, series = s) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.DICTATION)) }
    }
    @Test fun imeFeedback() {
        val s = state(true)
        s.feedback = AppState.FeedbackAsk(io.github.graviton94.todayletters.core.Feedback.Kind.BUG)
        shotIme("k03_ime_feedback_light", false, act = { rule.onAllNodes(hasSetTextAction()).onFirst().performClick() }) {
            io.github.graviton94.todayletters.ui.SettingsScreen(s)
            io.github.graviton94.todayletters.ui.FeedbackSheet(s, s.feedback!!)
        }
    }

    // ── 문법 사다리 (턴 · 오늘의 문법 · 말투 노트 · 말투 바꿔 보기). 실제 III장~ 데이터가 아직 없어 I장 첫 편지에 시험용 턴을 끼운다 ──
    private object G {
        fun tri(fr: String, en: String, ko: String) = io.github.graviton94.todayletters.core.Tri(mapOf(
            io.github.graviton94.todayletters.core.Lang.FR to fr, io.github.graviton94.todayletters.core.Lang.EN to en, io.github.graviton94.todayletters.core.Lang.KO to ko))
        fun ui(ko: String, en: String) = io.github.graviton94.todayletters.core.Tri(mapOf(io.github.graviton94.todayletters.core.Lang.KO to ko, io.github.graviton94.todayletters.core.Lang.EN to en))
        val grammar = listOf(
            io.github.graviton94.todayletters.core.Grammar("I.0", mapOf(
                io.github.graviton94.todayletters.core.Lang.FR to io.github.graviton94.todayletters.core.GrammarLine(ui("복합과거 (avoir)", "Passé composé with avoir"), ui("지난 일을 말해요 · avoir + 과거분사", "Talk about what happened · avoir + past participle")),
                io.github.graviton94.todayletters.core.Lang.EN to io.github.graviton94.todayletters.core.GrammarLine(ui("과거형", "Past simple"), ui("지난 일을 말해요", "Talk about what happened")),
                io.github.graviton94.todayletters.core.Lang.KO to io.github.graviton94.todayletters.core.GrammarLine(ui("-아서/어서", "-아서/어서 (so · and then)"), ui("이유 · 순서를 이어요", "Link a reason or a sequence")),
            )),
            io.github.graviton94.todayletters.core.Grammar("I.1", mapOf(
                io.github.graviton94.todayletters.core.Lang.FR to io.github.graviton94.todayletters.core.GrammarLine(ui("복합과거 복습", "Passé composé review"), ui("앞에서 배운 문법을 다시", "Practise it again")),
                io.github.graviton94.todayletters.core.Lang.KO to io.github.graviton94.todayletters.core.GrammarLine(ui("-아서/어서 복습", "-아서/어서 review"), ui("앞에서 배운 문법을 다시", "Practise it again")),
            )),
        )
        val turns = listOf(
            io.github.graviton94.todayletters.core.ReplyTurn(0, "I.0",
                tri("J'ai lu ta lettre deux fois, et j'ai pensé à tes nuits de travail.", "I read your letter twice, and I thought about your nights of work.",
                    io.github.graviton94.todayletters.core.Breaks.keepAll("형 편지를 읽어서 형 생각을 많이 했어요.")),
                koLow = io.github.graviton94.todayletters.core.Breaks.keepAll("형 편지를 읽어서 형 생각을 많이 했어."),
                koHigh = io.github.graviton94.todayletters.core.Breaks.keepAll("형님 편지를 읽어서 형님 생각을 많이 했습니다."),
                focus = mapOf(io.github.graviton94.todayletters.core.Lang.FR to listOf("ai lu", "pensé"), io.github.graviton94.todayletters.core.Lang.EN to listOf("read", "thought"),
                    io.github.graviton94.todayletters.core.Lang.KO to listOf("읽어서")),
                wrong = mapOf(io.github.graviton94.todayletters.core.Lang.FR to listOf(listOf("lis", "lirai"), listOf("pense", "penser")), io.github.graviton94.todayletters.core.Lang.EN to listOf(listOf("reads", "reading"), listOf("think", "thinks")),
                    io.github.graviton94.todayletters.core.Lang.KO to listOf(listOf("읽고", "읽으면")))),
            io.github.graviton94.todayletters.core.ReplyTurn(1, "I.1",
                tri("Tu as choisi des couleurs très fortes, et j'ai tout de suite voulu voir le tableau.", "You chose very strong colours, and I wanted to see the painting right away.",
                    io.github.graviton94.todayletters.core.Breaks.keepAll("형이 아주 강한 색을 골라서 그 그림이 바로 보고 싶어졌어요.")),
                koLow = io.github.graviton94.todayletters.core.Breaks.keepAll("형이 아주 강한 색을 골라서 그 그림이 바로 보고 싶어졌어."),
                koHigh = io.github.graviton94.todayletters.core.Breaks.keepAll("형님께서 아주 강한 색을 고르셔서 그 그림이 바로 보고 싶어졌습니다."),
                focus = mapOf(io.github.graviton94.todayletters.core.Lang.FR to listOf("as choisi", "voulu"), io.github.graviton94.todayletters.core.Lang.EN to listOf("chose", "wanted"),
                    io.github.graviton94.todayletters.core.Lang.KO to listOf("골라서")),
                wrong = mapOf(io.github.graviton94.todayletters.core.Lang.FR to listOf(listOf("choisis", "choisir"), listOf("veux", "voulais")), io.github.graviton94.todayletters.core.Lang.EN to listOf(listOf("choose", "chosen"), listOf("want", "wanting")),
                    io.github.graviton94.todayletters.core.Lang.KO to listOf(listOf("고르고", "고르면")))),
        )
        fun patch(w: io.github.graviton94.todayletters.data.Work): io.github.graviton94.todayletters.data.Work = w.copy(
            chapters = w.chapters.mapIndexed { i, c -> if (i > 0) c else c.copy(grammar = grammar, letters = c.letters.mapIndexed { j, l -> if (j == 0) l.copy(replies = turns) else l }) },
            decks = w.decks.mapValues { patch(it.value) },
            outro = w.outro?.let { o -> o.copy(epilogue = o.epilogue?.copy(replies = turns + turns.first().copy(quote = 0))) },
        )
    }

    /** 빈센트 I장 첫 편지에 시험용 두 턴 · 오늘의 문법을 끼운 채로 [block] 을 찍고 되돌린다. */
    private fun withGrammar(block: () -> Unit) {
        val original = io.github.graviton94.todayletters.data.Library.works(ctx)
        io.github.graviton94.todayletters.data.Library.swapForTest(ctx) { ws -> ws.map { if (it.series.id == "vincent") G.patch(it) else it } }
        try { block() } finally { io.github.graviton94.todayletters.data.Library.swapForTest(ctx) { original } }
    }

    /** 한국어를 배우는 공간 (영어로 읽기). */
    private fun koreanLearner(s: AppState): AppState {
        s.update(s.app.copy(read = io.github.graviton94.todayletters.core.Lang.EN))
        s.update("vincent", s.seriesSettings("vincent").copy(learn = io.github.graviton94.todayletters.core.Lang.KO))
        s.bump()
        return s
    }

    /** B1: 턴 1 / 2 · 오늘의 문법 줄 (점선) · 인용 · 문법 덩어리 빈칸 둘 (같은 낱말의 다른 꼴이 미끼). */
    @Test fun grammarLadderTurn1() = withGrammar {
        val s = graded(state(true), 8)
        shot("grammar01_ladder_turn1_b1_light", false, series = s) { Play(s, replyRoute) }
    }
    /** B1: 턴 1 을 채워 ‘다음 턴으로’ → 턴 2 / 2 (다른 인용 · 복습 문법). */
    @Test fun grammarLadderTurn2() = withGrammar {
        val s = graded(state(true), 8)
        shot("grammar02_ladder_turn2_b1_dark", true, act = {
            rule.onAllNodes(hasText(ctx.getString(R.string.play_show)) and hasClickAction()).onFirst().performClick()
            rule.mainClock.advanceTimeBy(300)
            rule.onAllNodes(hasText(ctx.getString(R.string.ladder_next_turn)) and hasClickAction()).onFirst().performClick()
            // 턴을 보내면 ‘소리 내어 말해 보기’ 한 번: 건너뛰고 턴 2 로
            rule.mainClock.advanceTimeBy(300)
            rule.onAllNodes(hasText(ctx.getString(R.string.reply_speak_skip)) and hasClickAction()).onFirst().performClick()
        }, series = s) { Play(s, replyRoute) }
    }
    /** 한국어를 배울 때 (A2 빈칸 하나): 해요체로 채운 뒤 펼쳐진 말투 노트 (반말 · 높임 · 실제 사이). */
    @Test fun grammarKoreanStyleNote() = withGrammar {
        val s = graded(koreanLearner(state(true)), 5)
        shot("grammar03_korean_style_note_light", false, act = {
            rule.onAllNodes(hasText(ctx.getString(R.string.play_show)) and hasClickAction()).onFirst().performClick()
        }, series = s) { Play(s, replyRoute) }
    }
    /** 복습의 말투 바꿔 보기 카드: 해요체 문장 → 반말 · 높임 고르기 (보기 셋: 정답 · 다른 말투 · 섞인 것). */
    @Test fun grammarStyleCard() = withGrammar {
        val s = koreanLearner(state(true))
        val l = s.work("vincent").chapters.first().letters.first()
        val key = io.github.graviton94.todayletters.core.CardRef.style("vincent", "I", l.id, s.deckOf("vincent"), 1)
        s.store.save(io.github.graviton94.todayletters.core.Card(key, box = 1, due = s.today)); s.bump()
        shot("grammar04_style_card_light", false, series = s) { Session(s, Route.Session(only = listOf(key))) }
    }
    /** 말투 바꿔 보기: 맞힌 뒤 (어두운 화면). */
    @Test fun grammarStyleCardRight() = withGrammar {
        val s = koreanLearner(state(true))
        val l = s.work("vincent").chapters.first().letters.first()
        val key = io.github.graviton94.todayletters.core.CardRef.style("vincent", "I", l.id, s.deckOf("vincent"), 1)
        s.store.save(io.github.graviton94.todayletters.core.Card(key, box = 1, due = s.today)); s.bump()
        val t = G.turns[1]
        val target = io.github.graviton94.todayletters.core.Breaks.plain(if (io.github.graviton94.todayletters.core.Ladder.askLow(key, s.today)) t.koLow else t.koHigh)
        shot("grammar05_style_card_right_dark", true, act = { rule.onNodeWithText(target).performClick() }, series = s) { Session(s, Route.Session(only = listOf(key))) }
    }
    /** 턴마다 복습 카드: 두 턴을 마치면 답장 카드 둘 (r · r1), 한국어면 말투 카드도. 마지막 턴에서 답장 걸음이 끝난다. */
    @Test fun grammarTurnsMakeCards() = withGrammar {
        val s = koreanLearner(state(true))
        val r = Route.Letter("vincent", 1, 1)
        val (c, l) = s.letterOf(r)
        s.replyTurnDone(r, 0); s.replyTurnDone(r, 1)
        val deck = s.deckOf("vincent")
        val keys = s.store.cards().map { it.key }.toSet() + s.store.later(deck)
        listOf(io.github.graviton94.todayletters.core.CardRef.reply("vincent", c, l.id, deck, 0), io.github.graviton94.todayletters.core.CardRef.reply("vincent", c, l.id, deck, 1),
            io.github.graviton94.todayletters.core.CardRef.style("vincent", c, l.id, deck, 0)).forEach { k -> org.junit.Assert.assertTrue(k, k in keys) }
        org.junit.Assert.assertNotNull(s.cardWord(io.github.graviton94.todayletters.core.CardRef.reply("vincent", c, l.id, deck, 1)))
    }

    private val epilogueRoute get() = Route.Play(Route.Letter("vincent", Route.Letter.EPILOGUE, 1), ReplyMode.CONSTELLATION)
    /** 맺음 편지에도 답장 (턴 셋): 아웃트로 화면의 답장 단추. */
    @Test fun grammarOutroReply() = withGrammar {
        val s = graded(state(true), 5)
        shot("grammar06_outro_reply_dark", true, act = {
            rule.onAllNodes(hasText(ctx.getString(R.string.outro_reply))).onFirst().performScrollTo()
        }) { io.github.graviton94.todayletters.ui.OutroScreen(s, "vincent") }
    }
    /** 맺음 편지의 답장 사다리 (턴 1 / 3). */
    @Test fun grammarEpilogueLadder() = withGrammar {
        val s = graded(state(true), 8)
        shot("grammar07_epilogue_ladder_b1_light", false, series = s) { Play(s, epilogueRoute) }
    }
    /** 맺음 편지 답장을 보내면 진도에 남고 (장 "E"), 턴마다 복습 카드. */
    @Test fun grammarEpilogueProgress() = withGrammar {
        val s = state(true)
        val r = Route.Letter("vincent", Route.Letter.EPILOGUE, 1)
        val (c, l) = s.letterOf(r)
        org.junit.Assert.assertEquals("E", c)
        s.go(Route.Outro("vincent")); s.go(Route.Play(r, ReplyMode.CONSTELLATION))
        (0..2).forEach { s.replyTurnDone(r, it) }
        s.replyAndContinue(r, ReplyMode.CONSTELLATION)
        org.junit.Assert.assertTrue(ReplyMode.CONSTELLATION in s.progress("vincent", "E", l.id).replied)
        org.junit.Assert.assertEquals(Route.Outro("vincent"), s.route)
        org.junit.Assert.assertNotNull(s.cardWord(io.github.graviton94.todayletters.core.CardRef.reply("vincent", "E", l.id, s.deckOf("vincent"), 2)))
    }

    // ── 문법 사다리, 실제 데이터 (data/*/chapter*.json · epilogue.json 의 replies · grammar · wrong). '*ScreensTest.grammarReal*' 로 따로 돌린다 ──
    /** [series] 에서 배우는 말을 [learn] 으로 (읽는 말이 같으면 읽기를 다른 말로), 그 말의 언어 등급을 [index] 로. */
    private fun learning(s: AppState, series: String, learn: io.github.graviton94.todayletters.core.Lang, index: Int): AppState {
        if (learn == s.app.read) s.update(s.app.copy(read = if (learn == io.github.graviton94.todayletters.core.Lang.KO) io.github.graviton94.todayletters.core.Lang.EN else io.github.graviton94.todayletters.core.Lang.KO))
        s.update(series, s.seriesSettings(series).copy(learn = learn))
        s.store.setGrade(learn.code, index)
        if (s.current != series) s.enter(series)
        s.bump()
        return s
    }
    private fun realRoute(series: String, ch: Int, n: Int) = Route.Play(Route.Letter(series, ch, n), ReplyMode.CONSTELLATION)
    /** 빈센트 III장 첫 편지 · 턴 1 / 2 · B1 (프랑스어): 오늘의 문법 · 인용 · 빈칸 둘 (답장에 적힌 틀린 보기). */
    @Test fun grammarReal01() {
        val s = learning(state(true), "vincent", io.github.graviton94.todayletters.core.Lang.FR, 8)
        shot("grammarReal01_vincent3_l1_turn1_b1_fr_light", false, series = s) { Play(s, realRoute("vincent", 3, 1)) }
    }
    /** 같은 편지 턴 2 / 2 (복습 문법), 어두운 화면. */
    @Test fun grammarReal02() {
        val s = learning(state(true), "vincent", io.github.graviton94.todayletters.core.Lang.FR, 8)
        shot("grammarReal02_vincent3_l1_turn2_b1_fr_dark", true, act = {
            rule.onAllNodes(hasText(ctx.getString(R.string.play_show)) and hasClickAction()).onFirst().performClick()
            rule.mainClock.advanceTimeBy(300)
            rule.onAllNodes(hasText(ctx.getString(R.string.ladder_next_turn)) and hasClickAction()).onFirst().performClick()
            rule.mainClock.advanceTimeBy(300)
            rule.onAllNodes(hasText(ctx.getString(R.string.reply_speak_skip)) and hasClickAction()).onFirst().performClick()
        }, series = s) { Play(s, realRoute("vincent", 3, 1)) }
    }
    /** 모차르트 VI장 첫 편지 · 턴 1 / 3 · B1 (독일어). */
    @Test fun grammarReal03() {
        val s = learning(state(true), "mozart", io.github.graviton94.todayletters.core.Lang.DE, 8)
        shot("grammarReal03_mozart6_l1_turn1_b1_de_light", false, series = s) { Play(s, realRoute("mozart", 6, 1)) }
    }
    /** 빈센트 II장 첫 편지 · 한국어를 배울 때 (A2 빈칸 하나): 맞는 보기를 눌러 맞힌 뒤 펼쳐진 말투 노트. */
    @Test fun grammarReal04() {
        val s = learning(state(true), "vincent", io.github.graviton94.todayletters.core.Lang.KO, 5)
        val r = realRoute("vincent", 2, 1)
        val answer = io.github.graviton94.todayletters.core.Breaks.plain(s.letterOf(r.room).second.replies.first().focus[io.github.graviton94.todayletters.core.Lang.KO]!!.first())
        shot("grammarReal04_vincent2_l1_ko_style_note_light", false, act = {
            rule.onAllNodes(hasText(answer) and hasClickAction()).onFirst().performClick()
            rule.mainClock.advanceTimeBy(300)
            rule.onAllNodes(hasText(ctx.getString(R.string.style_note))).onFirst().performScrollTo()
        }, series = s) { Play(s, r) }
    }
    /** 영어를 배울 때 · 모차르트 IV장 둘째 편지 · 턴 1 · A2 (빈칸 하나), 어두운 화면. */
    @Test fun grammarReal05() {
        val s = learning(state(true), "mozart", io.github.graviton94.todayletters.core.Lang.EN, 5)
        shot("grammarReal05_mozart4_l2_turn1_a2_en_dark", true, series = s) { Play(s, realRoute("mozart", 4, 2)) }
    }
    /** 빈센트 맺음 편지 답장 · 턴 1 / 3 · B2 직접 쓰기 (프랑스어). */
    @Test fun grammarReal06() {
        val s = learning(state(true), "vincent", io.github.graviton94.todayletters.core.Lang.FR, 11)
        shot("grammarReal06_vincent_epilogue_turn1_b2_fr_light", false, series = s) { Play(s, realRoute("vincent", Route.Letter.EPILOGUE, 1)) }
    }

    // ── 학습 깊이 (depth.html 2): 이해 확인 · 오늘의 회화 · 답장 말해 보기 · 발음 줄. 실제 데이터가 아직 없어 빈센트 I장 첫 편지에 시험용 문제 · 대화를 끼운다 ──
    private object D {
        fun tri(fr: String, en: String, ko: String) = io.github.graviton94.todayletters.core.Tri(mapOf(
            io.github.graviton94.todayletters.core.Lang.FR to fr, io.github.graviton94.todayletters.core.Lang.EN to en,
            io.github.graviton94.todayletters.core.Lang.KO to io.github.graviton94.todayletters.core.Breaks.keepAll(ko)))
        fun ui(ko: String, en: String) = io.github.graviton94.todayletters.core.Tri(mapOf(
            io.github.graviton94.todayletters.core.Lang.KO to io.github.graviton94.todayletters.core.Breaks.keepAll(ko), io.github.graviton94.todayletters.core.Lang.EN to en))
        private val FR = io.github.graviton94.todayletters.core.Lang.FR
        private val EN = io.github.graviton94.todayletters.core.Lang.EN
        private val KO = io.github.graviton94.todayletters.core.Lang.KO
        val check = io.github.graviton94.todayletters.core.Check(
            tri("À quoi Vincent a-t-il le plus pensé pendant le voyage ?", "What did Vincent think about most on the way?", "빈센트는 오는 길에 무엇을 가장 많이 생각했나요?"),
            listOf(tri("Au nouveau pays", "The new country", "새로 볼 고장"), tri("À Théo", "Theo", "테오"),
                tri("À la neige", "The snow", "쌓인 눈"), tri("Aux vignes", "The vineyards", "포도밭")),
            answer = 1, evidence = listOf(0), mark = tri("pensé à toi", "thought about you", "너를 많이 생각했어"))
        val talk = io.github.graviton94.todayletters.core.Talk(
            ui("아를에 막 도착한 빈센트와 통화 · 나는 테오", "A call with Vincent, just arrived in Arles · you are Theo"),
            listOf(
                io.github.graviton94.todayletters.core.TalkLine(false, tri("Allô, Théo ! Il neige partout ici !", "Hello, Theo! It's snowing everywhere here!", "여보세요, 테오! 여기는 온통 눈이에요!")),
                io.github.graviton94.todayletters.core.TalkLine(true, tri("Vraiment ? Le voyage était long ?", "Really? Was the journey long?", "정말요? 오는 길은 길었어요?"),
                    focus = mapOf(FR to listOf("était"), EN to listOf("Was"), KO to listOf("길었어요")),
                    wrong = mapOf(FR to listOf(listOf("est", "sera")), EN to listOf(listOf("Is", "Were")), KO to listOf(listOf("길어요", "길겠어요")))),
                io.github.graviton94.todayletters.core.TalkLine(false, tri("Oui, mais j'ai pensé à toi.", "Yes, but I thought of you.", "네, 그래도 네 생각을 했어요.")),
                io.github.graviton94.todayletters.core.TalkLine(true, tri("Il fait froid ? Tu as besoin de quelque chose ?", "Is it cold? Do you need anything?", "추워요? 필요한 거 있어요?"),
                    focus = mapOf(FR to listOf("as"), EN to listOf("need"), KO to listOf("있어요")),
                    wrong = mapOf(FR to listOf(listOf("es", "a")), EN to listOf(listOf("needs", "needing")), KO to listOf(listOf("있어", "있습니다")))),
            ),
            grammar = "I.0",
            wordsBy = mapOf(FR to listOf("voyage", "penser"), EN to listOf("journey", "think"), KO to listOf("여행", "생각하다")),
        )
        fun patch(w: io.github.graviton94.todayletters.data.Work): io.github.graviton94.todayletters.data.Work = w.copy(
            chapters = w.chapters.mapIndexed { i, c -> if (i > 0) c else c.copy(letters = c.letters.mapIndexed { j, l -> if (j == 0) l.copy(checks = listOf(check), talk = talk) else l }) },
            decks = w.decks.mapValues { patch(it.value) },
        )
    }

    /** 빈센트 I장 첫 편지에 시험용 이해 확인 · 회화를 끼운 채로 [block] 을 찍고 되돌린다. */
    private fun withDepth(block: () -> Unit) {
        val original = io.github.graviton94.todayletters.data.Library.works(ctx)
        io.github.graviton94.todayletters.data.Library.swapForTest(ctx) { ws -> ws.map { if (it.series.id == "vincent") D.patch(it) else it } }
        try { block() } finally { io.github.graviton94.todayletters.data.Library.swapForTest(ctx) { original } }
    }
    private val checkRoute get() = Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.CHECK)
    private val talkRoute get() = Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.TALK)

    /** 이해 확인 (프랑스어 · 한국어로 읽기 · A1): 안내 · 지문 (전문 · 듣기 · A1 은 번역 보기 링크) · 질문 · 보기 A–D. 근거 표시 없음. */
    @Test fun depthCheckQuestion() = withDepth {
        val s = graded(state(true), 1)
        shot("depth01_check_question_fr_a1_light", false, series = s) { Play(s, checkRoute) }
    }
    /** 맞힌 뒤: 지문 속 그 대목 (pensé à toi) 옅은 금빛 + 번역 줄, 마침 단추 (어두운 화면). */
    @Test fun depthCheckAnswered() = withDepth {
        val s = graded(state(true), 1)
        shot("depth02_check_answered_fr_a1_dark", true, act = {
            rule.onAllNodes(hasText(io.github.graviton94.todayletters.core.Breaks.plain(D.check.options[1][io.github.graviton94.todayletters.core.Lang.KO])) and hasClickAction()).onFirst().performClick()
        }, series = s) { Play(s, checkRoute) }
    }
    /** 한국어를 배울 때 (영어로 읽기 · A1): 지문은 한국어 전문, 질문 · 보기는 영어. */
    @Test fun depthCheckKorean() = withDepth {
        val s = learning(state(true), "vincent", io.github.graviton94.todayletters.core.Lang.KO, 2)
        shot("depth03_check_ko_learner_a1_light", false, series = s) { Play(s, checkRoute) }
    }
    /** 영어를 배울 때 (한국어로 읽기 · B1): 지문 영어 전문, 질문은 배우는 말 (영어) · 보기는 읽는 말 (한국어). 맞힌 뒤. */
    @Test fun depthCheckEnglishB1() = withDepth {
        val s = learning(state(true), "vincent", io.github.graviton94.todayletters.core.Lang.EN, 8)
        shot("depth04_check_en_learner_b1_answered_light", false, act = {
            rule.onAllNodes(hasText(io.github.graviton94.todayletters.core.Breaks.plain(D.check.options[1][io.github.graviton94.todayletters.core.Lang.KO])) and hasClickAction()).onFirst().performClick()
        }, series = s) { Play(s, checkRoute) }
    }
    // 오늘의 회화 (메신저, talkchat.html): 아래 칸은 도움 사다리대로 (core 의 TalkTurn). 말하기는 따라 읽기와 같은 단추 (원래 소리 · 녹음 · 내 소리)
    private fun tap(text: String, substring: Boolean = false) {
        rule.onAllNodes(hasText(text, substring = substring) and hasClickAction()).onFirst().performClick()
        rule.mainClock.advanceTimeBy(400)
    }
    /** A1 (프랑스어): 내 말풍선에 원문 + 번역 (점선), 아래 칸은 듣기 · 따라 말하기 (바로 된다). */
    @Test fun depthTalkA1() = withDepth {
        val s = graded(state(true), 1)
        shot("depth05_talk_fr_a1_tray_light", false, ms = 4000, series = s) { Play(s, talkRoute) }
    }
    /** A2 (프랑스어): 말풍선에 빈칸 + 번역, 아래 칸은 보기 칩 · 녹음은 회색 (빈칸을 먼저 채워요). */
    @Test fun depthTalkA2Before() = withDepth {
        val s = graded(state(true), 4)
        shot("depth06_talk_fr_a2_blank_locked_light", false, ms = 4000, series = s) { Play(s, talkRoute) }
    }
    /** A2: 맞는 칩 (était) 을 고른 뒤 — 말풍선이 채워지고 듣기 · 말하기가 열린다 (어두운 화면). */
    @Test fun depthTalkA2After() = withDepth {
        val s = graded(state(true), 4)
        shot("depth07_talk_fr_a2_blank_filled_dark", true, ms = 4000, act = { tap("était") }, series = s) { Play(s, talkRoute) }
    }
    /** B1 (프랑스어): 뜻 + 첫 낱말, 섞은 조각을 차례로. Le 를 놓고, 차례가 아닌 long 을 눌러 튕긴 모습 (녹음은 아직 회색). */
    @Test fun depthTalkB1Mid() = withDepth {
        val s = graded(state(true), 7)
        shot("depth08_talk_fr_b1_chips_rejected_light", false, ms = 4000, act = { tap("Le"); tap("long") }, series = s) { Play(s, talkRoute) }
    }
    /** B2 (프랑스어): 뜻만, 힌트 한 번 (첫 낱말), 녹음은 바로 된다 (어두운 화면). */
    @Test fun depthTalkB2Hint() = withDepth {
        val s = graded(state(true), 11)
        shot("depth09_talk_fr_b2_hint1_dark", true, ms = 4000, act = { tap(ctx.getString(R.string.talk_hint), substring = true) }, series = s) { Play(s, talkRoute) }
    }
    /** 한국어를 배울 때 (영어로 읽기 · A2): 해요체 덩어리 빈칸 + 칩. */
    @Test fun depthTalkKorean() = withDepth {
        val s = learning(state(true), "vincent", io.github.graviton94.todayletters.core.Lang.KO, 4)
        shot("depth10_talk_ko_learner_a2_light", false, ms = 4000, series = s) { Play(s, talkRoute) }
    }
    /** 영어를 배울 때 (한국어로 읽기 · B1): 조각 쌓기, 차례가 아닌 조각 하나 튕김. */
    @Test fun depthTalkEnglish() = withDepth {
        val s = learning(state(true), "vincent", io.github.graviton94.todayletters.core.Lang.EN, 7)
        shot("depth11_talk_en_learner_b1_dark", true, ms = 4000, act = { tap("Was"); tap("long") }, series = s) { Play(s, talkRoute) }
    }
    /** 회화 말하기 = 따라 읽기와 같은 부품 (원래 소리 · 녹음 · 내 소리, 억양 · 리듬 채점). 스스로 판정 (말해 봤어요 · 맞게 말했어요) 은 없다. */
    @Test fun depthTalkShadowPad() = withDepth {
        val s = graded(state(true), 1)
        shot("depth12_talk_a1_shadow_pad_dark", true, ms = 4000, act = {
            org.junit.Assert.assertTrue(rule.onAllNodes(hasText(ctx.getString(R.string.shadow_talk))).fetchSemanticsNodes().isNotEmpty())
        }, series = s) { Play(s, talkRoute) }
    }
    /** 이해 확인 · 회화가 있는 편지의 걸음: 읽기 → 이해 확인 → 낱말 → (따라 읽기) → 회화 → 답장. */
    @Test fun depthStepsInLetter() = withDepth {
        val s = state(true)
        val r = Route.Letter("vincent", 1, 1)
        val (c, l) = s.letterOf(r)
        val modes = s.letterModes("vincent", c, l)
        org.junit.Assert.assertEquals(ReplyMode.CHECK, modes.first())
        org.junit.Assert.assertTrue(ReplyMode.TALK in modes)
        org.junit.Assert.assertTrue(modes.indexOf(ReplyMode.TALK) < modes.indexOf(ReplyMode.CONSTELLATION))
    }
    /** 답장 턴을 보낸 뒤 ‘소리 내어 말해 보기’ (A2: 문장 보며). */
    @Test fun depthReplySpeak() = withGrammar {
        val s = graded(state(true), 5)
        shot("depth09_reply_speak_a2_light", false, act = {
            rule.onAllNodes(hasText(ctx.getString(R.string.play_show)) and hasClickAction()).onFirst().performClick()
            rule.mainClock.advanceTimeBy(300)
            rule.onAllNodes(hasText(ctx.getString(R.string.ladder_next_turn)) and hasClickAction()).onFirst().performClick()
            rule.mainClock.advanceTimeBy(300)
            // 답장 말해 보기도 따라 읽기와 같은 녹음 단추 (인식 · 스스로 판정 없음)
            org.junit.Assert.assertTrue(rule.onAllNodes(hasText(ctx.getString(R.string.shadow_talk))).fetchSemanticsNodes().isNotEmpty())
            // 원래 소리: 답장 낭독 r1_<언어> (편지 폴더) 가 있으니 듣기 단추가 있다
            org.junit.Assert.assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(ctx.getString(R.string.shadow_orig))).fetchSemanticsNodes().isNotEmpty())
        }, series = s) { Play(s, replyRoute) }
    }
    /** 답장 말해 보기 · B1: 문장 흐리게, 어두운 화면 (B2 는 직접 쓰기라 이 장면에서 바로 닿지 않는다). */
    @Test fun depthReplySpeakHidden() = withGrammar {
        val s = graded(state(true), 8)
        shot("depth10_reply_speak_b1_dark", true, act = {
            rule.onAllNodes(hasText(ctx.getString(R.string.play_show)) and hasClickAction()).onFirst().performClick()
            rule.mainClock.advanceTimeBy(300)
            rule.onAllNodes(hasText(ctx.getString(R.string.ladder_next_turn)) and hasClickAction()).onFirst().performClick()
        }, series = s) { Play(s, replyRoute) }
    }
    /** 낱말 이름표의 발음 줄: 영어 (IPA · 강세) · 한국어 ([소리] · 로마자 + 소리 규칙 한 줄). */
    @Test fun depthPronCards() {
        val en = io.github.graviton94.todayletters.core.Word(D.tri("voyage", "journey", "여행"), ipaEn = "ˈdʒɜːr.ni")
        val ko1 = io.github.graviton94.todayletters.core.Word(D.tri("lire", "read", "읽어요"), romKo = "ilgeoyo", sayKo = "일거요",
            pronNote = D.ui("받침이 넘어가요 (연음)", "The final consonant moves over (linking)"))
        val ko2 = io.github.graviton94.todayletters.core.Word(D.tri("ensemble", "together", "같이"), romKo = "gachi", sayKo = "가치",
            pronNote = D.ui("같이는 [가치]로 소리 나요 (구개음화)", "같이 sounds [가치] (palatalization)"))
        val E = io.github.graviton94.todayletters.core.Lang.EN; val K = io.github.graviton94.todayletters.core.Lang.KO
        shot("depth11_pron_cards_light", false) {
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                io.github.graviton94.todayletters.ui.WordLabel("journey", io.github.graviton94.todayletters.core.Pron.line(en, E, false), E, "") {}
                io.github.graviton94.todayletters.ui.WordLabel("읽어요", io.github.graviton94.todayletters.core.Pron.line(ko1, K, false), K, "",
                    note = io.github.graviton94.todayletters.core.Pron.note(ko1, K, K)) {}
                io.github.graviton94.todayletters.ui.WordLabel("같이", io.github.graviton94.todayletters.core.Pron.line(ko2, K, false), K, "",
                    note = io.github.graviton94.todayletters.core.Pron.note(ko2, K, K)) {}
            }
        }
    }

    // ── 천천히 듣기 (slow.html): 소리가 나는 곳마다 스피커 · 0.7× 두 단추, 작품 설정의 기본 듣기 속도 ──
    /** 시험용 회화에 소리 (I장 첫 편지의 t1~t4) 를 단 채로 [block]. */
    private fun withSlow(block: () -> Unit) {
        val original = io.github.graviton94.todayletters.data.Library.works(ctx)
        fun patch(w: io.github.graviton94.todayletters.data.Work): io.github.graviton94.todayletters.data.Work = w.copy(
            chapters = w.chapters.mapIndexed { i, c -> if (i > 0) c else c.copy(letters = c.letters.mapIndexed { j, l -> if (j == 0) l.copy(checks = listOf(D.check), talk = D.talk.copy(audio = "t")) else l }) },
            decks = w.decks.mapValues { patch(it.value) },
        )
        io.github.graviton94.todayletters.data.Library.swapForTest(ctx) { ws -> ws.map { if (it.series.id == "vincent") patch(it) else it } }
        try { block() } finally { io.github.graviton94.todayletters.data.Library.swapForTest(ctx) { original } }
    }
    private fun slowButtons() = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(ctx.getString(R.string.a11y_listen_slow)) and hasClickAction()).fetchSemanticsNodes().size

    /** 회화 말풍선 (상대 줄) 안의 스피커 · 0.7× (A1, 내 차례 아래 칸에도 둘). */
    @Test fun slowTalkBubble() = withSlow {
        val s = graded(state(true), 1)
        // 말풍선 안 스피커 · 0.7× (ListenButtons), 내 차례 아래 칸은 말하기 판 공통 단추 (rec_controls ABCD: 원래 소리 아래 칩 1× · 0.7×).
        // 예전에는 아래 칸에도 ListenButtons 가 있어 ‘둘 이상’ 을 셌다: 이제 아래 칸의 0.7× 는 빠르기 칩이라 그것을 따로 센다
        shot("slow01_talk_bubble_light", false, ms = 4000, act = {
            org.junit.Assert.assertTrue(slowButtons() >= 1)
            val chip = ctx.getString(R.string.shadow_speed, io.github.graviton94.todayletters.ui.SLOW_TEXT)
            org.junit.Assert.assertEquals(1, rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(chip) and hasClickAction()).fetchSemanticsNodes().size)
        }, series = s) { Play(s, talkRoute) }
    }
    /** 이해 확인 지문 아래 스피커 · 0.7× · 번역 보기. */
    @Test fun slowCheckPassage() = withSlow {
        val s = graded(state(true), 1)
        shot("slow02_check_passage_light", false, act = { org.junit.Assert.assertEquals(1, slowButtons()) }, series = s) { Play(s, checkRoute) }
    }
    /** 낱말 이름표 (낱말 맞추기 · 복습 뜻 고르기) 와 낱말 카드: 금빛 고리 스피커 · 0.7×. */
    @Test fun slowWordCard() {
        val s = loopState()
        val l = s.work("vincent").chapters.first().letters.first()
        val key = s.cardKey("vincent", "I", l.id, 0)
        shot("slow03_word_card_light", false, act = { org.junit.Assert.assertTrue(slowButtons() >= 1) }, series = s) {
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                io.github.graviton94.todayletters.ui.WordLabel("voyage", "[vwajaʒ]", io.github.graviton94.todayletters.core.Lang.FR, "") {}
                Box(Modifier.weight(1f)) { io.github.graviton94.todayletters.ui.WordDetailBody(s, key) { } }
            }
        }
    }
    /** 작품 설정의 기본 듣기 속도 (0.7 · 0.85 · 1.0) + 등급에 맞춰 자동. 0.7 을 고르면 자동이 꺼지고 그 값이 남는다. */
    @Test fun slowSettings() {
        val s = state(true)
        shot("slow04_settings_speed_light", false, act = {
            // 같은 글자가 예열 고름 칸에도 있다: 누를 수 없는 이름표 (듣기 빠르기의 ‘등급에 맞춰 자동’) 로
            // 이름표가 화면 밑으로 내려가 이제는 실제로 굴린다: 굴리는 동안만 시계를 흘린다 (scrolled)
            scrolled { rule.onNode(hasText(ctx.getString(R.string.set_listen_auto).uppercase()) and androidx.compose.ui.test.SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick)).performScrollTo() }
            org.junit.Assert.assertTrue(s.seriesSettings("vincent").listenAuto)
            org.junit.Assert.assertEquals(0.85f, s.listenSpeed("vincent"), 0.001f)
            rule.onNode(hasText("0.7") and hasClickAction()).performClick()
            rule.mainClock.advanceTimeBy(300)
            org.junit.Assert.assertFalse(s.seriesSettings("vincent").listenAuto)
            org.junit.Assert.assertEquals(0.7f, s.listenSpeed("vincent"), 0.001f)
        }, series = s) { SeriesSettingsScreen(s, "vincent") }
    }

    // ── 오늘 마침 · 처음부터 다시 보기 · 다음 단추 (finish 시안) ─────────────
    /** 마지막 걸음을 마치면 한 장: 오늘의 편지를 마쳤어요 · 편지 번호와 날짜 · 다음 편지 시각 (알림 시각 그대로). */
    @Test fun finishSheet() {
        val s = state(true)
        finishReal(s, "vincent", 1, 1)
        org.junit.Assert.assertEquals(s.key("vincent", s.letterOf(Route.Letter("vincent", 1, 1)).first, s.letterOf(Route.Letter("vincent", 1, 1)).second.id), s.finishSheet)
        shot("finish01_sheet_light", false, 4000, series = s) { Done(s, Route.Letter("vincent", 1, 1)) }
    }

    /** 같은 마침 한 장을 어두운 테마로, 금빛 결이 단추 한가운데를 지날 때 (밝은 단추 위 짙은 금). */
    @Test fun finishSheetDark() {
        val s = state(true)
        finishReal(s, "vincent", 1, 1)
        shot("finish01_sheet_dark", true, 2400, series = s) { Done(s, Route.Letter("vincent", 1, 1)) }
    }

    /** 금빛 결 (shine) 을 밝은 · 어두운 테마의 같은 단추에서: 지나가는 중 한 장씩. */
    @Test fun shineLight() { shot("shine_light", false, 2400) { ShineSample() } }
    @Test fun shineDark() { shot("shine_dark", true, 2400) { ShineSample() } }
    @Composable private fun ShineSample() {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            io.github.graviton94.todayletters.ui.Primary("다음 걸음 · 낱말 맞추기", shine = true) {}
            io.github.graviton94.todayletters.ui.Primary("복습 시작", small = true, shine = true) {}
            io.github.graviton94.todayletters.ui.Primary("결 없음 (비교)") {}
        }
    }

    /** 걸음을 건너뛰어 마쳐도 한 장: 건너뛴 걸음 N개는 복습에서. */
    @Test fun finishSheetSkipped() {
        val s = state(true)
        val r = Route.Letter("vincent", 1, 1)
        val (c, l) = s.letterOf(r)
        s.open("vincent", c, l, null, skipAudio = true)
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = l.messages.size))
        val modes = s.letterModes("vincent", c, l)
        modes.forEachIndexed { i, m -> if (!s.progress("vincent", c, l.id).done) { if (i == 0) s.skipStep(r, m) else s.replyAndContinue(r, m) } }
        s.reward = null; s.levelUp = null; s.gradeUp = null
        org.junit.Assert.assertNotNull(s.finishSheet)
        org.junit.Assert.assertEquals(1, s.progress("vincent", c, l.id).toRedo.size)
        shot("finish02_sheet_skipped_dark", true, 4000, series = s) { Done(s, r) }
    }

    /** 편지 목록: 마친 편지마다 ‘처음부터 다시 보기’. 눌러 다시 보고 마쳐도 보상은 다시 주지 않는다. */
    @Test fun finishListReplay() {
        val s = state(true)
        finishReal(s, "vincent", 1, 1)
        s.finishSheet = null
        val r = Route.Letter("vincent", 1, 1)
        val (c, l) = s.letterOf(r)
        val coins = s.wallet("vincent").total
        val xp = s.store.xp().total
        shot("finish03_list_replay_light", false, series = s) { ChapterScreen(s, Route.Chapter("vincent", 1)) }
        rule.onNode(hasText(ctx.getString(R.string.letter_again)) and hasClickAction()).performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(300)
        org.junit.Assert.assertTrue(s.again("vincent", c, l.id))
        org.junit.Assert.assertEquals(0, s.progress("vincent", c, l.id).shown)
        org.junit.Assert.assertFalse(s.progress("vincent", c, l.id).done)
        // 다시 보기를 끝까지: 보상 · XP 는 그대로, 오늘 마침 한 장도 다시 뜨지 않는다
        finishReal(s, "vincent", 1, 1)
        org.junit.Assert.assertFalse(s.again("vincent", c, l.id))
        org.junit.Assert.assertTrue(s.progress("vincent", c, l.id).done)
        org.junit.Assert.assertEquals(coins, s.wallet("vincent").total)
        org.junit.Assert.assertEquals(xp, s.store.xp().total)
        org.junit.Assert.assertNull(s.finishSheet)
    }

    /** 다음 단추: 누를 수 없다가 누를 수 있게 되면 금빛으로 채워지고 숨 쉬듯 빛난다 (위: 아직, 가운데: 켜진 뒤, 아래: 움직임 줄이기). */
    @Test fun finishGlowNext() {
        shot("finish04_next_glow_light", false, ms = 1200) {
            // 화면 안에서 0.3초 뒤에 켠다 (테스트 쪽에서 바꾼 상태는 멈춘 시계에 잘 안 전해짐)
            val on = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); on.value = true }
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)) {
                io.github.graviton94.todayletters.ui.Primary("다음: 낱말 맞추기", enabled = false) {}
                io.github.graviton94.todayletters.ui.Primary("다음: 낱말 맞추기 ›", enabled = on.value) {}
                androidx.compose.runtime.CompositionLocalProvider(io.github.graviton94.todayletters.ui.LocalStill provides true) {
                    io.github.graviton94.todayletters.ui.Primary("다음: 낱말 맞추기 ›", enabled = on.value) {}
                }
            }
        }
    }

    // 처음 쓰는 사람 (firstuser): 편지 I 을 마친 첫날 — 오늘 맨 위 한 줄 · 다음 편지 시각 / 알림 켜기 · 남은 걸음, 빈 복습 탭 (첫 복습은 내일부터), 빈 갤러리 칸
    @Test fun firstDayToday() {
        val s = state(true); finishReal(s, "vincent", 1, 1)
        shot("first01_today_done_light", false, 3000, series = s) { Inbox(s) }
    }
    @Test fun firstDayReview() {
        val s = state(true); finishReal(s, "vincent", 1, 1)
        shot("first02_review_first_day_light", false, series = s) { WordsTab(s) }
    }
    @Test fun firstNoticeAsk() {
        shot("first04_notice_ask_light", false) {
            io.github.graviton94.todayletters.ui.Ask(ctx.getString(R.string.notice_pre_t), ctx.getString(R.string.notice_pre_yes), ctx.getString(R.string.notice_pre_no), {}, {})
        }
    }
    @Test fun firstDayGallery() {
        val s = state(true)
        shot("first03_gallery_empty_light", false, series = s) { io.github.graviton94.todayletters.ui.GalleryTab(s) }
    }

    // ── 입문자 길 (시안 beginner.html ① ② ⑤): 낱말 아래 뜻 · 편지 전 예열 · 닻 표현 ──
    private fun ilRoom(s: AppState, n: Int): Route.Letter {
        val r = Route.Letter("vincent", 1, n)
        val (c, l) = s.letterOf(r)
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = l.messages.size))
        return r
    }
    /** 편지 I-1, 낱말 아래 뜻 (기본 켜짐): 아직 내 것이 아닌 낱말마다 짧은 뜻, pour le moins · j'ai 는 한 칸. */
    @Test fun ilRoomLight() { val s = state(true); val r = ilRoom(s, 1); shot("il1_room_fr_light", false, 4000, series = s) { Room(s, r) } }
    @Test fun ilRoomDark() { val s = state(true); val r = ilRoom(s, 1); shot("il2_room_fr_dark", true, 4000, series = s) { Room(s, r) } }
    /** 편지 전 예열: 카드 낱말 셋 + 덩어리 하나, 첫 장이 지금 장. */
    @Test fun ilWarmUp() {
        val s = state(true)
        shot("il3_warmup_light", false, series = s) { io.github.graviton94.todayletters.ui.WarmUpScreen(s, Route.WarmUp(Route.Letter("vincent", 1, 2))) }
    }
    /** 예열을 다 한 모습 (점 넷 · ‘편지 열기 ›’). */
    @Test fun ilWarmUpDone() {
        val s = state(true)
        shot("il3b_warmup_done_dark", true, act = {
            repeat(4) {
                val said = rule.onAllNodes(hasText(ctx.getString(R.string.warm_said)) and hasClickAction())
                if (said.fetchSemanticsNodes().isNotEmpty()) { said.onFirst().performClick(); rule.mainClock.advanceTimeBy(300) }
            }
        }, series = s) { io.github.graviton94.todayletters.ui.WarmUpScreen(s, Route.WarmUp(Route.Letter("vincent", 1, 2))) }
    }
    /** 예열 뒤 편지 I-2: 아까 본 말 (금빛 점선), 익힌 닻 (j'ai · c'est · mon cher, 금빛 바탕). */
    @Test fun ilAnchors() {
        val s = state(true)
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().putString("anc:vincent/",
            io.github.graviton94.todayletters.ui.anchorsOf(ctx, "vincent", "").filter { it.text in setOf("j'ai", "c'est", "mon cher") }.joinToString("\n") { it.id }).commit()
        val r = Route.Letter("vincent", 1, 2)
        val (c, l) = s.letterOf(r)
        s.finishWarm(Route.WarmUp(r), s.warmCards("vincent", c, l))
        ilRoom(s, 2)
        s.bump()
        shot("il4_room_anchors_light", false, 4000, series = s) { Room(s, r) }
    }
    /** 한국어를 배우는 사람 (읽는 말 영어): 어절마다 영어 뜻. */
    @Test fun ilRoomKo() {
        val s = state(true)
        s.update(s.app.copy(read = io.github.graviton94.todayletters.core.Lang.EN))
        s.update("vincent", s.seriesSettings("vincent").copy(learn = io.github.graviton94.todayletters.core.Lang.KO))
        val r = ilRoom(s, 1)
        shot("il5_room_ko_learner_light", false, 4000, series = s) { Room(s, r) }
    }

    // ── 단계성 보강 (2026-10-19): 기초 낱말 카드 · 예열의 ‘오늘 말해 볼 말’ · 따라 읽기 걸음의 회화 줄 ──
    // 기초 낱말 덱은 assets letters/vincent/basics.json (data/ 에서 generate.py 가 넣는다). 없으면 복습 장면은 빈 판으로 찍힌다.
    private fun basicKey(s: AppState, box: Int = 0): String? {
        val b = io.github.graviton94.todayletters.ui.basicsOf(ctx, "vincent", "").items.firstOrNull { it.ex != null } ?: return null
        val key = io.github.graviton94.todayletters.core.CardRef.basic("vincent", b.id)
        s.store.save(io.github.graviton94.todayletters.core.Card(key, box = box, due = s.today)); s.bump()
        return key
    }
    /** 기초 낱말 카드 (새 카드, 뜻 고르기): 이름표 ‘기초 낱말’ · 큰 낱말 · 듣기 · 품사, 아래 예문 칸 (편지에서 · 예문). */
    @Test fun basicCard() {
        val s = state(true)
        val key = basicKey(s)
        shot("basic1_card_light", false, series = s) { Session(s, Route.Session(only = listOfNotNull(key))) }
    }
    /** 기초 낱말 카드: 맞힌 뒤 (뜻 · 예문 번역), 어두운 화면. */
    @Test fun basicCardAnswered() {
        val s = state(true)
        val key = basicKey(s)
        val meaning = key?.let { s.cardWord(it)?.word?.text?.get(s.room("vincent").read) }
        shot("basic2_card_answered_dark", true, act = {
            if (meaning != null) rule.onAllNodes(hasText(meaning) and hasClickAction()).onFirst().performClick()
        }, series = s) { Session(s, Route.Session(only = listOfNotNull(key))) }
    }
    /** 나의 기록: 언어 등급 아래 ‘기초 낱말 N / 300 · 많이 쓰는 300개 중’. */
    @Test fun basicRecord() {
        val s = engineState(); s.reward = null
        basicKey(s, box = 3)
        shot("basic3_record_light", false) { io.github.graviton94.todayletters.ui.RecordScreen(s) }
    }
    /** 예열: 낱말 · 덩어리를 다 하고 ‘오늘 말해 볼 말’ (오늘의 회화 내 줄) 이 지금 장. */
    @Test fun warmUpTalk() {
        val s = state(true)
        shot("il6_warmup_talk_light", false, act = {
            repeat(4) {
                val said = rule.onAllNodes(hasText(ctx.getString(R.string.warm_said)) and hasClickAction())
                if (said.fetchSemanticsNodes().isNotEmpty()) { said.onFirst().performClick(); rule.mainClock.advanceTimeBy(300) }
            }
        }, series = s) { io.github.graviton94.todayletters.ui.WarmUpScreen(s, Route.WarmUp(Route.Letter("vincent", 1, 2))) }
    }
    /** 따라 읽기 걸음: 회화 내 줄이 먼저 (‘오늘 말해 볼 말 1 / 2’, 원래 소리 = 회화 소리). */
    @Test fun shadowTalkLine() {
        val s = state(true)
        shot("sh1_shadow_talk_light", false, series = s) {
            io.github.graviton94.todayletters.ui.ReadAlong(s, Route.Letter("vincent", 1, 2), only = listOf(0), onClose = {}, onFinish = {})
        }
    }

    // ── 말하기 · 소리 (2026-10-19, 시안 vox): 한 번에 찍기 `*ScreensTest.vox*` ──
    /** 녹음한 것처럼 둔 말하기 칸 (기기 녹음 없이): [unclear] 면 ‘또박또박’ 한 줄, [rough] 가 2 면 ‘비슷하게 읽으면’. */
    private fun voxTake(unclear: Boolean, rough: Int, sayLike: String?): io.github.graviton94.todayletters.ui.ShadowTake {
        val f = java.io.File(ctx.cacheDir, "vox/take.wav").apply { parentFile?.mkdirs(); writeBytes(ByteArray(64)) }
        return io.github.graviton94.todayletters.ui.ShadowTake(null, -1, 0, f, step = true, sayLike = sayLike).apply {
            tick = 1; voice = !unclear && rough < 2; this.unclear = unclear; misses = if (unclear) 1 else 0; this.rough = rough
        }
    }
    @Composable private fun VoxLine(s: AppState, t: io.github.graviton94.todayletters.ui.ShadowTake) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom) {
            androidx.compose.material3.Text("Oui, ça va. Et toi ?", style = io.github.graviton94.todayletters.design.Type.target, color = Ink.palette.ink,
                modifier = Modifier.padding(24.dp))
            io.github.graviton94.todayletters.ui.ShadowControls(s, t)
        }
    }
    /** 말하기 검사: 목소리는 들렸지만 음절이 어긋남 → ‘조금 더 또박또박 · 끝까지 말해 봐요’. */
    @Test fun voxHint() { val s = state(true); val t = voxTake(true, 1, null); shot("vox1_hint_light", false, series = s) { VoxLine(s, t) } }
    /** 두 번째로 안 된 뒤: 원래 소리 단추 위에 ‘비슷하게 읽으면 · 위, 싸 바. 에 뚜아?’ (scripts/pron.py 자동 힌트). */
    @Test fun voxPron() { val s = state(true); val t = voxTake(false, 2, "위, 싸 바. 에 뚜아?"); shot("vox2_pron_light", false, series = s) { VoxLine(s, t) } }

    // ── 말하기 판 고침 (2026-10-24 사용자 보고): 한 번에 찍기 `*ScreensTest.fix*` ──
    /** 가짜 목소리 (높이 [hz] 시간 → Hz), [spikes] 의 시각 (초) 근처는 YIN 이 옥타브로 잘못 잡은 것처럼 두 배. */
    private fun fakeVoice(seconds: Double, spikes: List<Double> = emptyList(), hz: (Double) -> Double): FloatArray {
        val rate = io.github.graviton94.todayletters.data.Wav.RATE
        val n = (seconds * rate).toInt(); var ph = 0.0
        return FloatArray(n) { k ->
            val t = k.toDouble() / rate
            val f = hz(t) * (if (spikes.any { kotlin.math.abs(it - t) < 0.02 }) 2 else 1)
            ph += 2 * Math.PI * f / rate
            val gap = (t % 0.45) > 0.38   // 낱말 사이 쉼
            if (gap) 0f else (0.5 * (0.6 * Math.sin(ph) + 0.3 * Math.sin(2 * ph) + 0.1 * Math.sin(3 * ph))).toFloat()
        }
    }
    /** 녹음 · 채점을 마친 것처럼 둔 말하기 칸: 판정은 앱과 같은 한 곳 (core 의 SpeakCheck.judge, 점수 포함). */
    private fun scoredTake(score: io.github.graviton94.todayletters.core.Prosody.Score, mineSyl: Int, refSyl: Int): io.github.graviton94.todayletters.ui.ShadowTake {
        val P = io.github.graviton94.todayletters.core.Prosody
        val rate = io.github.graviton94.todayletters.data.Wav.RATE
        val f = java.io.File(ctx.cacheDir, "fix/take.wav").apply { parentFile?.mkdirs(); writeBytes(ByteArray(64)) }
        val L = io.github.graviton94.todayletters.core.PitchLine
        val ref = L.analyze(fakeVoice(1.8) { t -> 190 + 60 * kotlin.math.sin(t * 3.2) }, rate)
        // 내 소리: 낮은 목소리 · 조금 느리게 · 옥타브로 튄 곳 셋 (예전에는 큰 세로 가시가 되었다). 판의 선은 한 줄 (core 의 PitchLine)
        val mine = L.analyze(fakeVoice(2.3, spikes = listOf(0.5, 1.1, 1.7)) { t -> 120 + 38 * kotlin.math.sin(t / 2.3 * 1.8 * 3.2) }, rate)
        val c = L.curves(ref, mine)
        val v = io.github.graviton94.todayletters.core.SpeakCheck.judge(true, mineSyl, refSyl, 0, score.total)
        return io.github.graviton94.todayletters.ui.ShadowTake("audio/none.m4a", 0, Int.MAX_VALUE, f, step = true).apply {
            tick = 1; heard = true; result = Triple(score, c?.ref, c?.mine); voice = v.pass; unclear = v.hint; misses = if (v.pass) 0 else 1
        }
    }
    @Composable private fun ScoredPane(s: AppState, t: io.github.graviton94.todayletters.ui.ShadowTake) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 40.dp)) {
            androidx.compose.material3.Text("Hi! Are you tired from travelling?", style = io.github.graviton94.todayletters.design.Type.target, color = Ink.palette.ink)
        }
        // 판 · 아래 칸 (말하기 일곱 곳 공통): 아래에 붙는다
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            io.github.graviton94.todayletters.ui.SpeakPanel(s, t) { io.github.graviton94.todayletters.ui.MainButton("다음", enabled = t.passed == true, reason = "말해 보면 다음으로 갈 수 있어요") {} }
        }
    }
    /** 점수 91 (억양 100 · 리듬 77) 인데 음절은 어긋남: 이제 통과 (‘훌륭해요!’ 만, ‘또박또박’ 없음), 두 선은 다듬고 시간축을 맞춤. */
    @Test fun fixShadowHighScorePasses() {
        val s = state(true)
        val t = scoredTake(io.github.graviton94.todayletters.core.Prosody.Score(100, 77), mineSyl = 3, refSyl = 10)
        org.junit.Assert.assertEquals(true, t.passed); org.junit.Assert.assertFalse(t.unclear)
        shot("fix1_shadow_high_score_pass_dark", true, series = s) { ScoredPane(s, t) }
    }
    /** 점수가 문턱 아래 · 음절도 어긋남: ‘다시’ 한 줄과 ‘또박또박’ 이 함께 (칭찬 없음). */
    @Test fun fixShadowLowScoreRetry() {
        val s = state(true)
        val t = scoredTake(io.github.graviton94.todayletters.core.Prosody.Score(30, 35), mineSyl = 3, refSyl = 10)
        org.junit.Assert.assertEquals(false, t.passed); org.junit.Assert.assertTrue(t.unclear)
        shot("fix2_shadow_low_score_retry_light", false, series = s) { ScoredPane(s, t) }
    }
    /** 녹음이 남지 않은 칸 (너무 짧음): 0점 판 없이 안내만. */
    @Test fun fixShadowNoTake() {
        val s = state(true)
        val f = java.io.File(ctx.cacheDir, "fix/none.wav").apply { delete() }
        val t = io.github.graviton94.todayletters.ui.ShadowTake("audio/none.m4a", 0, Int.MAX_VALUE, f, step = true).apply { tick = 1; voice = false }
        shot("fix3_shadow_no_take_light", false, act = {
            org.junit.Assert.assertTrue(rule.onAllNodes(hasText("0")).fetchSemanticsNodes().isEmpty())
        }, series = s) { ScoredPane(s, t) }
    }

    // ── 말하기 단추 셋 · 녹음 고리 · 비교 듣기 · 미리 보는 원래 선 (2026-10-24, 시안 rec_controls 2 ABCD): `*ScreensTest.abcd*` ──
    /** 원래 소리의 선 (core 의 Prosody.preview) 을 이미 읽어 둔 칸: 원래 소리 있음, 녹음 [taken] 이면 파일을 둔다. */
    private fun abcdTake(taken: Boolean): io.github.graviton94.todayletters.ui.ShadowTake {
        val P = io.github.graviton94.todayletters.core.Prosody
        val rate = io.github.graviton94.todayletters.data.Wav.RATE
        val f = java.io.File(ctx.cacheDir, "abcd/take.wav").apply { parentFile?.mkdirs(); delete(); if (taken) writeBytes(ByteArray(64)) }
        val tr = io.github.graviton94.todayletters.core.PitchLine.analyze(fakeVoice(1.8) { t -> 190 + 60 * kotlin.math.sin(t * 3.2) }, rate)
        val pv = io.github.graviton94.todayletters.core.PitchLine.line(tr)!!
        @Suppress("UNUSED_VARIABLE") val unusedP = P
        // 원래 소리는 이미 한 번 들은 것으로 (먼저 듣기를 마침): 녹음 단추가 켜진 모습
        return io.github.graviton94.todayletters.ui.ShadowTake("audio/none.m4a", 0, Int.MAX_VALUE, f, step = true,
            text = "Hi! Are you tired from travelling?", lang = io.github.graviton94.todayletters.core.Lang.EN).apply {
            refCurve = pv.values; refRange = pv.range; refMs = pv.ms; line = pv; heard = true
            spots = io.github.graviton94.todayletters.core.WordSpots.estimate(text!!, tr.db, lang!!)
        }
    }
    /** 녹음 전: 원래 소리 점선 · ‘선을 따라 오르내리듯 읽어 보세요.’ · 단추 셋 (원래 소리 아래 칩 1× · 0.7×, 금테 말하기, 흐린 내 소리). */
    @Test fun abcdBefore() {
        val s = state(true)
        val t = abcdTake(false)
        shot("abcd1_before_light", false, series = s) { ScoredPane(s, t) }
    }
    /** 녹음 중: 원래 길이의 60% 쯤 말한 자리. 점선 위에 내 선 (같은 시간축 · 같은 폭), 말하기 둘레 고리, ‘듣고 있어요 · 다 말하면 저절로 멈춰요’. */
    @Test fun abcdRecording() {
        val s = state(true)
        val t = abcdTake(false)
        val ref = t.refMs!!
        val onset = 300L
        var at = 0L
        while (at < onset + ref * 0.6) {
            val sec = (at - onset) / 1000.0
            val voiced = at >= onset && (sec % 0.45) < 0.38
            t.times.add(at); t.levels.add(if (voiced) -18f else -60f)
            t.pitches.add(if (voiced) (120 + 34 * kotlin.math.sin(sec / 2.0 * 1.8 * 3.2)).toFloat() else 0f)
            at += 40
        }
        t.onset = onset; t.recording = true
        t.ring = ((at - onset).toFloat() / ref).coerceIn(0f, 1f)
        shot("abcd2_recording_dark", true, series = s) { ScoredPane(s, t) }
        t.recording = false
    }
    /** 채점 뒤 비교 듣기: 원래 소리가 나오는 중 (위 한 줄 ‘비교 듣기: 원래 소리 → 내 소리’ 의 원래 소리가 금빛), 내 소리 자리는 ‘비교 듣기’ (멈춤 모양). */
    @Test fun abcdCompare() {
        val s = state(true)
        val t = scoredTake(io.github.graviton94.todayletters.core.Prosody.Score(92, 84), mineSyl = 9, refSyl = 10)
        t.refCurve = abcdTake(false).refCurve
        java.io.File(ctx.cacheDir, "fix/take.wav").writeBytes(ByteArray(64))
        t.compare = io.github.graviton94.todayletters.ui.ShadowTake.Side.ORIG; t.playing = true
        shot("abcd3_compare_dark", true, series = s) { ScoredPane(s, t) }
    }
    /** 채점 뒤 (비교 듣기 전, 밝은 화면): 칩 0.7× 를 고른 상태. */
    @Test fun abcdScored() {
        val s = state(true)
        val t = scoredTake(io.github.graviton94.todayletters.core.Prosody.Score(92, 84), mineSyl = 9, refSyl = 10)
        t.slow = true
        shot("abcd4_scored_light", false, series = s) { ScoredPane(s, t) }
    }

    /** 어제 회화를 한 것으로 (빈센트 I-2): 오늘 화면 카드 · 떠올리기 퀴즈. */
    private fun voxYesterday(s: AppState): io.github.graviton94.todayletters.core.Letter {
        val (c, l) = s.letterOf(Route.Letter("vincent", 1, 2))
        s.store.logDay("talk", s.today - 1, s.key("vincent", c, l.id)); s.bump()
        return l
    }
    /** 오늘 화면 맨 위: ‘어제 말 떠올리기 · 어제 대화에서 내가 한 말 2개’. */
    @Test fun voxRecallCard() { val s = state(true); voxYesterday(s); shot("vox3_recall_card_light", false, series = s) { Inbox(s) } }
    /** 떠올리기 퀴즈: 뜻 · 조각 (틀린 조각 하나 섞임). */
    @Test fun voxRecall() {
        val s = state(true); voxYesterday(s)
        shot("vox4_recall_light", false, series = s) { io.github.graviton94.todayletters.ui.RecallScreen(s, Route.RecallQuiz("vincent")) }
    }
    /** 떠올리기: 조각을 차례대로 눌러 맞힌 뒤 (줄 · 듣기 · ‘소리 내어 말해 보기’). */
    @Test fun voxRecallRight() {
        val s = state(true); val l = voxYesterday(s)
        val learn = s.room("vincent").learn
        val me = io.github.graviton94.todayletters.core.ShadowLines.talkMe(l.talk, learn).first()
        val chunks = io.github.graviton94.todayletters.core.Recall.chunks(io.github.graviton94.todayletters.core.Breaks.plain(l.talk!!.lines[me].text[learn]).trim())
        shot("vox5_recall_right_dark", true, act = {
            chunks.forEach { c -> rule.onAllNodes(hasText(c) and hasClickAction()).onFirst().performClick(); rule.mainClock.advanceTimeBy(300) }
        }, series = s) { io.github.graviton94.todayletters.ui.RecallScreen(s, Route.RecallQuiz("vincent")) }
    }
    /** 이번엔 내 이야기로: 빈칸 문장 · 보기 셋, 첫 보기를 고른 모습. 앱 assets 에 아직 "mine" 이 없으면 시험용 보기를 끼운다. */
    @Test fun voxMine() {
        val s = state(true)
        val (c, l0) = s.letterOf(Route.Letter("vincent", 1, 2))
        val F = io.github.graviton94.todayletters.core.Lang.FR; val E = io.github.graviton94.todayletters.core.Lang.EN; val K = io.github.graviton94.todayletters.core.Lang.KO
        val l = if (l0.talk?.mine != null) l0 else l0.copy(talk = l0.talk?.copy(mine = io.github.graviton94.todayletters.core.TalkMine(3,
            mapOf(F to "beaucoup", E to "lots", K to "많이"),
            listOf(mapOf(F to "le français", E to "French", K to "프랑스어를"), mapOf(F to "à peindre", E to "to paint", K to "그림 그리기를"),
                mapOf(F to "à cuisiner", E to "to cook", K to "요리를")))))
        val first = l.talk!!.mine!!.opts[0][s.room("vincent").learn].orEmpty()
        shot("vox6_mine_light", false, act = {
            rule.onAllNodes(hasText(first) and hasClickAction()).onFirst().performClick()
        }, series = s) { io.github.graviton94.todayletters.ui.MinePane(s, "vincent", c, l) {} }
    }
    /** 이번 장의 소리: 요령 · 첫 쌍 (뜻 · 듣기). 앱 assets 에 sounds.json 이 아직 없으면 시험용 장. */
    private fun voxSounds(s: AppState) = s.soundDeckOrNull() ?: io.github.graviton94.todayletters.core.SoundChapter(
        mapOf(io.github.graviton94.todayletters.core.Lang.KO to "u 와 ou", io.github.graviton94.todayletters.core.Lang.EN to "u and ou"),
        mapOf(io.github.graviton94.todayletters.core.Lang.KO to "u 는 ‘이’ 입 모양에서 입술만 둥글게, ou 는 ‘우’", io.github.graviton94.todayletters.core.Lang.EN to "Round your lips"),
        listOf("tu" to "tout", "vu" to "vous", "rue" to "roue", "dessus" to "dessous"),
        mapOf("tu" to mapOf(io.github.graviton94.todayletters.core.Lang.KO to "너"), "tout" to mapOf(io.github.graviton94.todayletters.core.Lang.KO to "모두")))
    private fun AppState.soundDeckOrNull() = io.github.graviton94.todayletters.ui.soundsOf(ctx, "vincent", "").chapters["I"]
    @Test fun voxSound() {
        val s = state(true)
        val c = voxSounds(s)
        shot("vox7_sound_light", false, series = s) { io.github.graviton94.todayletters.ui.SoundsPane(s, "vincent", c, io.github.graviton94.todayletters.core.Lang.FR, seed = 1) {} }
    }
    /** 이번 장의 소리: 한 판 ‘들린 말은?’ 에서 하나를 고른 뒤. */
    @Test fun voxSoundRound() {
        val s = state(true)
        val c = voxSounds(s)
        val r = io.github.graviton94.todayletters.core.Sounds.rounds(c.pairs.size, 1L * 31 + 0 + s.today).first()
        val word = c.pairs[r.pair].first
        shot("vox8_sound_round_dark", true, act = {
            rule.onAllNodes(hasText(word) and hasClickAction()).onFirst().performClick()
        }, series = s) { io.github.graviton94.todayletters.ui.SoundsPane(s, "vincent", c, io.github.graviton94.todayletters.core.Lang.FR, seed = 1, stage0 = 0) {} }
    }

    // ── 편지 카드 공유 (시안 Gbowc2Rqd6VJJpfmMPP6jC 2): 카드 그림만 바로 그린다 (편지 방을 스크롤하지 않음) ──
    /** 그림 + 가장 긴 메시지 전체 (말줄임 없음, 높이는 내용만큼) · 번역 · 꼬리 줄 · 그림 줄. 밝은 종이. */
    @Test fun cardShare1() {
        val s = state(true)
        val w = s.work("vincent"); val l = w.chapters[0].letters[0]
        val m = l.messages.maxBy { it.text[s.room("vincent").learn].length }
        shot("cardShare1_light", false, series = s) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                io.github.graviton94.todayletters.ui.LetterCardImage(io.github.graviton94.todayletters.ui.rememberLetterCard(s, w, l, m), Modifier.fillMaxSize())
            }
        }
    }
    /** 어두운 카드: 첫 메시지 (봉인한 편지의 ‘이 편지 카드’ 와 같은 문장). */
    @Test fun cardShare2() {
        val s = state(true)
        val w = s.work("vincent"); val l = w.chapters[0].letters[0]
        shot("cardShare2_dark", true, series = s) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                io.github.graviton94.todayletters.ui.LetterCardImage(io.github.graviton94.todayletters.ui.rememberLetterCard(s, w, l, l.messages.first()), Modifier.fillMaxSize())
            }
        }
    }

    // ── 하루 흐름 정리 (시안 ux/src.html, 2026-10-24): 말하기 판 하나 (일곱 곳 공통) · 먼저 듣기 · 편지 끝 흐름. 한 번에 `*ScreensTest.rules*` ──
    private enum class Phase { LISTEN, RECORD, SCORED }
    /** 실제 화면의 말하기 칸을 [ph] 로 꾸민다 ([AppState.takeHook]): 원래 소리 한 줄 선 · 낱말 자리 (가짜 목소리) · 듣는 중 / 녹음 중 / 채점 뒤. */
    private fun staged(s: AppState, ph: Phase): AppState {
        val L = io.github.graviton94.todayletters.core.PitchLine
        val rate = io.github.graviton94.todayletters.data.Wav.RATE
        val tr = L.analyze(fakeVoice(2.2) { t -> 180 + 55 * kotlin.math.sin(t * 2.9) }, rate)
        val ln = L.line(tr)!!
        hookDone = false
        s.takeHook = { t, voiced ->
            if (t.hasAudio) {
                t.line = ln; t.refCurve = ln.values; t.refRange = ln.range; t.refMs = ln.ms
                t.spots = io.github.graviton94.todayletters.core.WordSpots.estimate(t.text ?: "Oui, ça va. Et toi ?", tr.db, t.lang ?: io.github.graviton94.todayletters.core.Lang.FR)
            }
            when (ph) {
                Phase.LISTEN -> { t.heard = false; t.playing = true; t.playAt = (ln.ms * 0.45).toInt(); t.listenRing = 0.45f }
                Phase.RECORD -> {
                    t.heard = true
                    val ref = t.targetMs ?: 2000
                    val onset = 300L
                    var at = 0L
                    while (at < onset + ref * 0.6) {
                        val sec = (at - onset) / 1000.0
                        val voiced2 = at >= onset && (sec % 0.45) < 0.38
                        t.times.add(at); t.levels.add(if (voiced2) -18f else -60f)
                        t.pitches.add(if (voiced2) (125 + 34 * kotlin.math.sin(sec * 2.6)).toFloat() else 0f)
                        at += 40
                    }
                    t.onset = onset; t.recording = true; t.ring = 0.6f
                }
                Phase.SCORED -> {
                    // 앱과 같은 길 (스크린샷 점검 2026-10-24 (d)): 목소리가 든 녹음을 녹음 중 파일에 쓰고 녹음을 끝낸다 → 화면의 채점 · 판정 ([ShadowTake.tick])
                    // 이 그대로 돈다 (통과 수 · 다음 단추가 실제 상태). 그 뒤 판의 두 선 · 점수만 정해 둔 값으로 (Robolectric 은 원래 낭독을 풀지 못한다)
                    t.heard = true
                    kotlinx.coroutines.delay(300)
                    val voice = fakeVoice(2.5) { x -> 130 + 40 * kotlin.math.sin(x * 2.6) }
                    val pcm = java.nio.ByteBuffer.allocate(voice.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    voice.forEach { pcm.putShort((it * 32767).toInt().coerceIn(-32768, 32767).toShort()) }
                    io.github.graviton94.todayletters.data.Wav.write(java.io.File(t.file.path + ".rec"), pcm.array())
                    t.recording = true
                    t.stopRecording(s)
                    androidx.compose.runtime.snapshotFlow { t.scoring to t.voice }.first { !it.first && it.second != null }
                    if (t.hasAudio) {
                        val c = L.curves(tr, L.analyze(voice, rate))
                        t.result = Triple(io.github.graviton94.todayletters.core.Prosody.Score(96, 84), c?.ref, c?.mine)
                    }
                    hookDone = true
                }
            }
        }
        return s
    }
    private fun phaseName(ph: Phase) = ph.name.lowercase()
    /** 채점 뒤 장면: 화면의 채점은 백그라운드 스레드에서 도니, 끝날 때까지 시계를 조금씩 흘린다. */
    @Volatile private var hookDone = false
    private fun waitHook() { repeat(80) { if (hookDone) return; rule.mainClock.advanceTimeBy(100); Thread.sleep(40) } }
    private fun phaseDark(ph: Phase) = ph == Phase.RECORD

    /** 따라 읽기 (회화 내 줄 먼저): 먼저 듣기 (고리 · 금빛 세로줄 · 지금 낱말) → 녹음 중 → 채점 뒤 (점수 한 줄 · 칭찬). */
    private fun readAlongShot(ph: Phase) {
        val dark = phaseDark(ph)
        val s = staged(state(true), ph)
        shot("rules1_readalong_${phaseName(ph)}_${if (dark) "dark" else "light"}", dark, ms = 1500, act = { if (ph == Phase.SCORED) waitHook() }, series = s) {
            io.github.graviton94.todayletters.ui.ReadAlong(s, Route.Letter("vincent", 1, 2), only = listOf(0), onClose = {}, onFinish = {}, onSkip = {})
        }
    }
    @Test fun rulesReadAlongListen() = readAlongShot(Phase.LISTEN)
    @Test fun rulesReadAlongRecord() = readAlongShot(Phase.RECORD)
    @Test fun rulesReadAlongScored() = readAlongShot(Phase.SCORED)
    /** 회화 내 차례 (A1): 내 문장을 먼저 한 번 (녹음 단추는 다 들은 뒤), 판은 아래 칸 바로 위 · 높이 고정. */
    private fun talkShot(ph: Phase) {
        val dark = phaseDark(ph)
        withSlow {
            val s = staged(graded(state(true), 1), ph)
            shot("rules2_talk_${phaseName(ph)}_${if (dark) "dark" else "light"}", dark, ms = 4000, act = { if (ph == Phase.SCORED) waitHook() }, series = s) { Play(s, talkRoute) }
        }
    }
    @Test fun rulesTalkListen() = talkShot(Phase.LISTEN)
    @Test fun rulesTalkRecord() = talkShot(Phase.RECORD)
    @Test fun rulesTalkScored() = talkShot(Phase.SCORED)
    /** 답장 말해 보기 (A1, 문장이 보임): 같은 판 · 같은 아래 칸, × 는 나가기. */
    private fun replyShot(ph: Phase) {
        val dark = phaseDark(ph)
        val s = staged(graded(state(true), 1), ph)
        shot("rules3_reply_${phaseName(ph)}_${if (dark) "dark" else "light"}", dark, ms = 1500, act = { if (ph == Phase.SCORED) waitHook() }, series = s) {
            io.github.graviton94.todayletters.ui.ReplySpeak(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.CONSTELLATION), 0, 1, onNext = {})
        }
    }
    @Test fun rulesReplyListen() = replyShot(Phase.LISTEN)
    @Test fun rulesReplyRecord() = replyShot(Phase.RECORD)
    @Test fun rulesReplyScored() = replyShot(Phase.SCORED)
    /** 내 이야기로 (원래 소리 없음): 보기를 고르고 ‘말해 보기’ 뒤의 판 (선 대신 크기 막대 · 칭찬 한 줄). */
    private fun mineShot(ph: Phase) {
        val dark = phaseDark(ph)
        val s = staged(state(true), ph)
        val (c, l0) = s.letterOf(Route.Letter("vincent", 1, 2))
        val F = io.github.graviton94.todayletters.core.Lang.FR; val E = io.github.graviton94.todayletters.core.Lang.EN; val K = io.github.graviton94.todayletters.core.Lang.KO
        val l = if (l0.talk?.mine != null) l0 else l0.copy(talk = l0.talk?.copy(mine = io.github.graviton94.todayletters.core.TalkMine(3,
            mapOf(F to "beaucoup", E to "lots", K to "많이"),
            listOf(mapOf(F to "le français", E to "French", K to "프랑스어를"), mapOf(F to "à peindre", E to "to paint", K to "그림 그리기를"),
                mapOf(F to "à cuisiner", E to "to cook", K to "요리를")))))
        val first = l.talk!!.mine!!.opts[0][s.room("vincent").learn].orEmpty()
        shot("rules4_mine_${phaseName(ph)}_${if (dark) "dark" else "light"}", dark, act = {
            rule.onAllNodes(hasText(first) and hasClickAction()).onFirst().performClick(); rule.mainClock.advanceTimeBy(300)
            rule.onAllNodes(hasText(ctx.getString(R.string.mine_say)) and hasClickAction()).onFirst().performClick(); rule.mainClock.advanceTimeBy(1500)
            if (ph == Phase.SCORED) waitHook()
        }, series = s) { io.github.graviton94.todayletters.ui.MinePane(s, "vincent", c, l) {} }
    }
    @Test fun rulesMineListen() = mineShot(Phase.LISTEN)
    @Test fun rulesMineRecord() = mineShot(Phase.RECORD)
    @Test fun rulesMineScored() = mineShot(Phase.SCORED)
    /** 어제 말 떠올리기: 맞힌 뒤 ‘소리 내어 말해 보기’ 의 판. */
    private fun recallShot(ph: Phase) {
        val dark = phaseDark(ph)
        val s = staged(state(true), ph); val l = voxYesterday(s)
        val learn = s.room("vincent").learn
        val me = io.github.graviton94.todayletters.core.ShadowLines.talkMe(l.talk, learn).first()
        val chunks = io.github.graviton94.todayletters.core.Recall.chunks(io.github.graviton94.todayletters.core.Breaks.plain(l.talk!!.lines[me].text[learn]).trim())
        shot("rules5_recall_${phaseName(ph)}_${if (dark) "dark" else "light"}", dark, act = {
            chunks.forEach { ch -> rule.onAllNodes(hasText(ch) and hasClickAction()).onFirst().performClick(); rule.mainClock.advanceTimeBy(300) }
            rule.onAllNodes(hasText(ctx.getString(R.string.recall_say)) and hasClickAction()).onFirst().performClick(); rule.mainClock.advanceTimeBy(1500)
            if (ph == Phase.SCORED) waitHook()
        }, series = s) { io.github.graviton94.todayletters.ui.RecallScreen(s, Route.RecallQuiz("vincent")) }
    }
    @Test fun rulesRecallListen() = recallShot(Phase.LISTEN)
    @Test fun rulesRecallRecord() = recallShot(Phase.RECORD)
    @Test fun rulesRecallScored() = recallShot(Phase.SCORED)
    /** 복습 말하기: 답을 보기 전에는 원래 소리 · 점선 없이 (먼저 들으면 답을 듣는 셈), 맞힌 뒤 같은 판. */
    private fun reviewSpeakShot(ph: Phase) {
        val dark = phaseDark(ph)
        val s = staged(engineState(), ph); s.reward = null
        shot("rules6_review_speak_${phaseName(ph)}_${if (dark) "dark" else "light"}", dark, ms = 1500, act = { if (ph == Phase.SCORED) waitHook() }, series = s) {
            Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.SPEAK))
        }
    }
    @Test fun rulesReviewSpeakListen() = reviewSpeakShot(Phase.LISTEN)
    @Test fun rulesReviewSpeakRecord() = reviewSpeakShot(Phase.RECORD)
    @Test fun rulesReviewSpeakScored() = reviewSpeakShot(Phase.SCORED)

    // 편지 끝 흐름 ①: 걸음 안 보상은 걸음 마침 한 장 (예전 “+2” 칩, rules7) → `rw2*`
    /** 편지 끝 흐름 ②: 편지 완료 보상 한 번, 오른 등급 · 레벨은 그 안에 한 줄씩 (따로 한 장씩 뜨지 않는다). */
    @Test fun rulesEndReward() {
        val s = state(true)
        val G = io.github.graviton94.todayletters.core.Reading.Grade
        s.reward = AppState.RewardMoment("vincent", 12, null, emptyList(), null,
            listOf(io.github.graviton94.todayletters.core.Earn.LETTER to 10, io.github.graviton94.todayletters.core.Earn.PRACTICE to 2),
            grade = AppState.GradeUp(s.room("vincent").learn, G.of(3), G.of(4)), level = AppState.LevelUp(4, 5))
        shot("rules8_end_reward_dark", true, 2000) { io.github.graviton94.todayletters.ui.RewardOverlay(s) }
    }
    /** 편지 끝 흐름 ③: 완료 화면 하나 (큰 단추 ‘오늘로’ · 작은 ‘그림 속 산책’), 튜토리얼 끝 한 줄은 이 화면 안에. */
    @Test fun rulesEndDone() {
        val s = state(true, tutorial = true)
        finishReal(s, "vincent", 1, 1)
        shot("rules9_end_done_tutorial_light", false, 4000, series = s) { Done(s, Route.Letter("vincent", 1, 1)) }
        org.junit.Assert.assertTrue(s.tutorialDone)
    }
    /** 편지 끝 흐름 ④: 알림 허락은 다음에 오늘에 왔을 때 한 번 (완료 화면 위에 겹치지 않음). */
    @Test fun rulesEndNotice() {
        val s = state(true)
        finishReal(s, "vincent", 1, 1)
        s.store.notifyAsked = false
        // 오늘에 들어올 때의 정산이 보상을 띄우면 그것이 걷힌 뒤에 묻는다 (덮개)
        shot("rules10_end_today_notice_light", false, 2000, act = { s.reward = null; s.levelUp = null; s.gradeUp = null }, series = s) { Inbox(s) }
        org.junit.Assert.assertTrue(s.store.notifyAsked)
    }

    // ── 보상 · 새 편지 (시안 rw2, 2026-10-24): 화면마다 시작 · 끝, 옮겨 가는 것은 가운데 한 장 (`*ScreensTest.rw*`) ──
    private fun px(dp: Float) = dp * ctx.resources.displayMetrics.density
    /** 창 좌표의 칸 (dp). */
    private fun rectDp(x: Float, y: Float, w: Float, h: Float) = androidx.compose.ui.geometry.Rect(px(x), px(y), px(x + w), px(y + h))

    /** ① 정답 (이해 확인): 고른 보기 옆 금선 원 · 체크, 지문의 근거 “pensé à toi” 에 불이 들고 금선 밑줄. */
    private fun checkRight(name: String, dark: Boolean, after: Long) = withDepth {
        val s = graded(state(true), 1)
        shot(name, dark, act = {
            rule.onAllNodes(hasText(io.github.graviton94.todayletters.core.Breaks.plain(D.check.options[1][s.room("vincent").read])) and hasClickAction()).onFirst().performClick()
        }, after = after, series = s) { Play(s, checkRoute) }
    }
    @Test fun rwCheckRightMid() = checkRight("rw1a_check_right_mid_light", false, 700)
    @Test fun rwCheckRight() = checkRight("rw1b_check_right_light", false, 2200)
    @Test fun rwCheckRightDark() = checkRight("rw1c_check_right_dark", true, 2200)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi")
    @Test fun rwCheckRightEn() = checkRight("rw1d_check_right_en_light", false, 2200)
    /** 뜻 고르기 (복습 · 낱말 맞추기 같은 부품) + 맞음 띠: 화폐 수가 제자리에서 한 칸 (가운데 · 끝). */
    private fun meaningRight(name: String, after: Long) {
        val s = state(true)
        shot(name, false, ms = 0, after = after, series = s) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(16.dp)) {
                io.github.graviton94.todayletters.ui.MeaningOptions(listOf("인간의", "올리브색의", "필요한", "새로운"), "새로운", io.github.graviton94.todayletters.core.Lang.KO,
                    "새로운", true, emptySet(), false, 0) { }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                io.github.graviton94.todayletters.ui.FeedbackBar(true, ctx.getString(R.string.review_right), "nouveau  ·  [nuvo]  ·  새로운", ctx.getString(R.string.ob_next), {},
                    ticker = { io.github.graviton94.todayletters.ui.CurrencyTicker("우표", 12, 13, Ink.palette.ink, Ink.palette.inkSoft, false) })
            }
        }
    }
    @Test fun rwMeaningRightMid() = meaningRight("rw1e_meaning_right_mid_light", 1250)
    @Test fun rwMeaningRight() = meaningRight("rw1f_meaning_right_light", 2400)

    /** ② 걸음 마침 한 장 → 다음 걸음 머리의 작은 그림. [stage] 0 마침 한 장 · 1 옮겨 가는 중 · 2 다음 걸음 (머리에 그림). */
    private fun stepNote(name: String, dark: Boolean, stage: Int) = withDepth {
        val s = graded(state(true), 1)
        val room = Route.Letter("vincent", 1, 1)
        val (c, l) = s.letterOf(room)
        val modes = s.letterModes("vincent", c, l)
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = l.messages.size).reply(modes[0]))
        val img = "plates/${l.plate!!.image}"
        val next = Route.Play(room, modes[1])
        when (stage) {
            0 -> s.stepNote = AppState.StepNote(room, 2, modes[0], img, io.github.graviton94.todayletters.core.Next.AfterStep.Step(modes[1], 1, modes.size), 1)
            else -> {
                s.stepPic = AppState.StepPic(next, img)
                if (stage == 1) s.flights.launch(img, rectDp(140.5f, 236f, 112f, 140f), "step-thumb", false, framed = true, alignY = 0.3f)
            }
        }
        shot(name, dark, ms = if (stage == 1) 120 else 2600, after = if (stage == 1) 0 else 900, series = s) {
            Box(Modifier.fillMaxSize()) { Play(s, next); io.github.graviton94.todayletters.ui.StepNoteOverlay(s); io.github.graviton94.todayletters.ui.FlightLayer(s) }
        }
    }
    @Test fun rwStepNote() = stepNote("rw2a_step_note_light", false, 0)
    @Test fun rwStepNoteDark() = stepNote("rw2b_step_note_dark", true, 0)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi")
    @Test fun rwStepNoteEn() = stepNote("rw2c_step_note_en_light", false, 0)
    @Test fun rwStepThumbMid() = stepNote("rw2d_step_to_thumb_mid_light", false, 1)
    @Test fun rwStepThumb() = stepNote("rw2e_step_thumb_end_light", false, 2)

    /** ③ 편지 완료 (벽 라벨 · 봉인 · 셈) → 오늘 (맨 위 그림 칸 · 체크 · 다음 할 일). */
    private fun letterDone(name: String, ms: Long) {
        val s = withCards()
        finishReal(s, "vincent", 1, 1)
        shot(name, true, ms = ms, after = 0, series = s) { Done(s, Route.Letter("vincent", 1, 1)) }
    }
    @Test fun rwDoneStart() = letterDone("rw3a_done_start_dark", 900)
    @Test fun rwDone() = letterDone("rw3b_done_dark", 4000)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi")
    @Test fun rwDoneEn() = letterDone("rw3c_done_en_dark", 4000)
    private fun doneToToday(name: String, dark: Boolean, ms: Long, fly: Boolean) {
        val s = withCards()
        finishReal(s, "vincent", 1, 1)
        val (c, l) = s.letterOf(Route.Letter("vincent", 1, 1))
        s.landing = s.key("vincent", c, l.id)
        if (fly) s.flights.launch("plates/${l.plate!!.image}", rectDp(0f, 0f, 393f, 630f), "today-pic", false, alignY = 0.3f)
        shot(name, dark, ms = ms, after = 0, series = s) { Box(Modifier.fillMaxSize()) { Inbox(s); io.github.graviton94.todayletters.ui.FlightLayer(s) } }
    }
    @Test fun rwDoneToTodayMid() = doneToToday("rw3d_done_to_today_mid_light", false, 140, true)
    @Test fun rwToday() = doneToToday("rw3e_today_landed_light", false, 3000, false)
    @Test fun rwTodayDark() = doneToToday("rw3f_today_landed_dark", true, 3000, false)

    /** ④ 레벨 업: 옛 칭호가 흐려지고 새 칭호 (한글 세운 글자 · 로마 숫자 Cormorant), 금선, 새로 열린 것, XP 줄. */
    private fun levelUp(name: String, from: Int, to: Int, ms: Long) {
        val s = engineState(); s.reward = null
        s.store.saveXp(io.github.graviton94.todayletters.core.XpLedger(total = io.github.graviton94.todayletters.core.Levels.totalFor(to) + 40))
        s.levelUp = AppState.LevelUp(from, to)
        shot(name, true, ms = ms, after = 0) { io.github.graviton94.todayletters.ui.LevelUpOverlay(s) }
    }
    @Test fun rwLevelMid() = levelUp("rw4a_level_up_mid", 14, 15, 1150)
    @Test fun rwLevel() = levelUp("rw4b_level_up_deco", 14, 15, 3600)
    @Test fun rwLevelPlain() = levelUp("rw4c_level_up_plain", 15, 16, 3600)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi")
    @Test fun rwLevelEn() = levelUp("rw4d_level_up_en", 14, 15, 3600)

    /** ⑤ 새 메달 → 진열장 칸 (가족 아래 ‘다음 메달’ 한 줄). */
    private fun medal(name: String, ms: Long) {
        val s = engineState()
        s.reward = AppState.RewardMoment("vincent", 10, null, listOf("streak_7"), null, listOf(io.github.graviton94.todayletters.core.Earn.ACHIEVEMENT to 10))
        shot(name, true, ms = ms, after = 0) { io.github.graviton94.todayletters.ui.MedalMoment(s) }
    }
    @Test fun rwMedalMid() = medal("rw5a_medal_mid", 1000)
    @Test fun rwMedal() = medal("rw5b_medal", 3600)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi")
    @Test fun rwMedalEn() = medal("rw5c_medal_en", 3600)
    private fun cabinet(name: String, dark: Boolean, ms: Long, fly: Boolean) {
        val s = engineState(); s.reward = null
        s.medalLanding = "streak_7"
        if (fly) s.flights.launch("medals/streak_7.webp", rectDp(121.5f, 300f, 150f, 150f), "medal:streak_7", false, round = true)
        shot(name, dark, ms = ms, after = 0) { Box(Modifier.fillMaxSize()) { io.github.graviton94.todayletters.ui.MedalsScreen(s); io.github.graviton94.todayletters.ui.FlightLayer(s) } }
    }
    @Test fun rwCabinetMid() = cabinet("rw5d_cabinet_mid_light", false, 160, true)
    @Test fun rwCabinet() = cabinet("rw5e_cabinet_landed_light", false, 2000, false)

    /** ⑥ 서재의 새 편지 (맨 위 큰 카드 · 편지지 · 빛 한 번 · 편지 열기) → 편지 도착 (카드 그림이 맨 위로, 첫 줄). */
    private fun library(name: String, dark: Boolean, ms: Long) {
        val s = engineState(); s.reward = null
        org.junit.Assert.assertNotNull(s.freshLetter("vincent"))
        shot(name, dark, ms = ms, after = 0) { LibraryTab(s) }
    }
    @Test fun rwLibraryMid() = library("rw6a_library_slip_mid_dark", true, 1100)
    @Test fun rwLibrary() = library("rw6b_library_hero_dark", true, 3600)
    @Test fun rwLibraryLight() = library("rw6c_library_hero_light", false, 3600)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi")
    @Test fun rwLibraryEn() = library("rw6d_library_hero_en_dark", true, 3600)
    private fun arrivalFromCard(name: String, ms: Long, fly: Boolean) {
        val s = engineState(); s.reward = null
        val (c, l, n) = s.freshLetter("vincent")!!
        if (fly) s.flights.launch(s.work("vincent").kit.hero, rectDp(20f, 236f, 353f, 300f), "arrival-pic", false, cornerFrom = px(16f), alignY = 0.25f, handOff = true)
        shot(name, false, ms = ms, after = 0, series = s) {
            Box(Modifier.fillMaxSize()) { io.github.graviton94.todayletters.ui.ArrivalBody(s, "vincent", c, l, n, { }); io.github.graviton94.todayletters.ui.FlightLayer(s) }
        }
    }
    @Test fun rwArrivalMid() = arrivalFromCard("rw6e_card_to_arrival_mid", 160, true)
    @Test fun rwArrival() = arrivalFromCard("rw6f_card_to_arrival_end", 6000, false)

    // ── 시스템 막대 (ui/Insets.kt): 앱의 실제 뿌리 (Root) 를 그리고, Compose 뷰에 상태 바 24dp · 내비게이션 바 48dp 인셋을 보낸다 ──
    // Robolectric 은 막대 두께가 0 이라 그대로 찍으면 겹쳐도 보이지 않는다. 막대 자리에는 반투명 빨간 띠를 그어
    // 그 띠 밑에 누를 것 · 글자가 깔리지 않는지 본다. '*ScreensTest.insets*' 로 따로 돌린다.
    private fun simulateBars(v: android.view.View) {
        val d = ctx.resources.displayMetrics.density
        val status = androidx.core.view.WindowInsetsCompat.Type.statusBars()
        val nav = androidx.core.view.WindowInsetsCompat.Type.navigationBars()
        val ins = androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(status, androidx.core.graphics.Insets.of(0, (BAR_TOP * d).toInt(), 0, 0))
            .setInsets(nav, androidx.core.graphics.Insets.of(0, 0, 0, (BAR_BOTTOM * d).toInt()))
            .setVisible(status, true).setVisible(nav, true).build()
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(v, ins)
    }
    private val BAR_TOP = 24
    private val BAR_BOTTOM = 48

    /** 막대가 있는 화면: [s] 를 메인 단계로 넘기고 (첫 화면 없이) 도움말은 본 것으로, Root 를 그린다. */
    private fun barsShot(name: String, dark: Boolean, s: AppState, ms: Long = 2500, after: Long = 900, act: () -> Unit = {}, late: (() -> Unit)? = null) {
        // 혼자 읽기 이정표는 이미 본 것으로 (서재가 뜨며 정산한 보상 순간이 찍을 장면을 덮지 않게)
        s.store.readAloneSeen = s.readAlone
        repeat(3) { if (s.stage != io.github.graviton94.todayletters.core.Stage.MAIN) s.nextStage() }
        listOf("inbox", "words", "gallery", "shop", "library", "series", "room", "play_aloud", "play_constellation").forEach { s.coachDone(it) }
        s.whatsNew = false
        var host: android.view.View? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            host = androidx.compose.ui.platform.LocalView.current
            TodayLettersTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    io.github.graviton94.todayletters.ui.Root(s, onExit = { })
                    val band = androidx.compose.ui.graphics.Color(0x66FF1E1E)
                    Box(Modifier.align(androidx.compose.ui.Alignment.TopCenter).fillMaxWidth().height(BAR_TOP.dp).background(band))
                    Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().height(BAR_BOTTOM.dp).background(band))
                }
            }
        }
        rule.runOnIdle { simulateBars(host!!) }
        // [late]: 화면이 뜬 뒤에 넣는 보상 순간 (뜨며 하는 정산이 덮어쓰지 않게)
        if (late != null) { rule.mainClock.advanceTimeBy(300); rule.runOnIdle { late() } }
        rule.mainClock.advanceTimeBy(ms)
        act()
        rule.runOnIdle { simulateBars(host!!) }
        rule.mainClock.advanceTimeBy(after)
        rule.onRoot().captureRoboImage("build/screens/$name.png")
        audit(name)
    }

    @Test fun insetsLibraryUpdate() {
        val s = engineState(); s.reward = null; s.go(Route.Library)
        barsShot("insets01_library_update_light", false, s, 3600, late = {
            s.reward = null; s.levelUp = null; s.updateBand = io.github.graviton94.todayletters.core.UpdateBand.Offer(18_400_000)
        })
    }
    @Test fun insetsRoom() {
        val s = state(true)
        val (c, l) = s.letterOf(Route.Letter("vincent", 1, 1))
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = l.messages.size))
        s.go(Route.Letter("vincent", 1, 1))
        barsShot("insets02_room_light", false, s, 4000)
    }
    @Test fun insetsReadAlong() {
        val s = state(true); s.go(Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.ALOUD))
        barsShot("insets03_read_along_dark", true, s)
    }
    @Test fun insetsTalk() = withDepth {
        val s = graded(state(true), 1); s.go(talkRoute)
        barsShot("insets04_talk_light", false, s)
    }
    @Test fun insetsStepNote() = withDepth {
        val s = graded(state(true), 1)
        val room = Route.Letter("vincent", 1, 1)
        val (c, l) = s.letterOf(room)
        val modes = s.letterModes("vincent", c, l)
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = l.messages.size).reply(modes[0]))
        s.go(Route.Play(room, modes[1]))
        s.stepNote = AppState.StepNote(room, 2, modes[0], "plates/${l.plate!!.image}", io.github.graviton94.todayletters.core.Next.AfterStep.Step(modes[1], 1, modes.size), 1)
        barsShot("insets05_step_note_light", false, s, 2600)
    }
    @Test fun insetsDone() {
        val s = withCards()
        finishReal(s, "vincent", 1, 1)
        s.go(Route.Done(Route.Letter("vincent", 1, 1)))
        barsShot("insets06_letter_done_dark", true, s, 4000, after = 0)
    }
    @Test fun insetsLevelUp() {
        val s = engineState(); s.reward = null; s.go(Route.Library)
        s.store.saveXp(io.github.graviton94.todayletters.core.XpLedger(total = io.github.graviton94.todayletters.core.Levels.totalFor(15) + 40))
        barsShot("insets07_level_up_dark", true, s, 3600, after = 0, late = { s.reward = null; s.levelUp = AppState.LevelUp(14, 15) })
    }
    /** 메달과 함께 온 그림 (셋째 메달): 메달 한 장 + “함께 온 그림” 한 줄. */
    @Test fun insetsMedalPainting() {
        val s = engineState(); s.reward = null; s.go(Route.Library)
        val m = medalWithPainting(s)
        barsShot("insets08_medal_painting_dark", true, s, 3600, after = 0, late = { s.levelUp = null; s.reward = m })
    }
    @Test fun insetsDownloadSheet() {
        val s = state(true); s.go(Route.Inbox)
        val ch = s.work("vincent").chapters[1]
        s.downloadAsk = AppState.DownloadAsk(s.downloads.pack("vincent", ch.id)!!, "vincent", ch.id, ch.letters.first(), Route.Inbox)
        barsShot("insets09_download_sheet_light", false, s)
    }
    @Test fun insetsBackupSheet() {
        val s = engineState(); s.reward = null; s.go(Route.Library); s.settingsOpen = true
        barsShot("insets10_backup_sheet_light", false, s, late = { s.reward = null; s.levelUp = null }, act = {
            scrolled { rule.onNodeWithText(ctx.getString(R.string.backup_export)).performScrollTo().performClick() }
        })
    }
    @Test fun insetsDialog() {
        val s = state(true); s.go(Route.Library); s.askingExit = true
        barsShot("insets11_dialog_light", false, s)
    }
    @Test fun insetsToday() {
        val s = withCards(); s.go(Route.Inbox)
        barsShot("insets12_today_light", false, s, 3000)
    }

    private fun medalWithPainting(s: AppState): AppState.RewardMoment {
        val pc = s.work("vincent").kit.collection.first { !s.owns("vincent", it) }
        s.store.addPiece("vincent", pc.id)
        return AppState.RewardMoment("vincent", 10, pc, listOf("streak_7"), null, listOf(io.github.graviton94.todayletters.core.Earn.ACHIEVEMENT to 10))
    }
    /** ⑤ 셋째 메달: 메달 한 장 아래 “함께 온 그림” (예전 보상 화면 대신, 시안 medal 1안). */
    @Test fun rwMedalPainting() {
        val s = engineState()
        s.reward = medalWithPainting(s)
        shot("rw5f_medal_painting", true, ms = 3600, after = 0) { io.github.graviton94.todayletters.ui.MedalMoment(s); io.github.graviton94.todayletters.ui.RewardOverlay(s) }
    }

    // ── 남은 보상 · 알림 (2026-10-25, 시안 rw2 의 말씨로): 이정표 · 화폐만 · 오늘의 자료 → 갤러리 칸 · 명작 · 전시 · 등급 · 봉인 · 맺음 · 새로 바뀐 것 ──
    // 모두 ko · en × 밝은 · 어두운 (보상 벽은 테마와 상관없이 밤의 전시실이라 단추 · 링크 글이 맞는지를 본다). '*ScreensTest.ow*' 로 따로 돌린다.
    /** 덮인 화면 [under] 위에 보상 [m] (정산이 덮어쓰지 않게 길을 먼저 옮긴 뒤). */
    private fun owReward(name: String, dark: Boolean, under: Route, ms: Long = 3600, m: (AppState) -> AppState.RewardMoment) {
        val s = engineState(); s.reward = null; s.levelUp = null; s.gradeUp = null
        s.go(under)
        s.reward = m(s)
        shot(name, dark, ms = ms, after = 0) { io.github.graviton94.todayletters.ui.RewardOverlay(s); io.github.graviton94.todayletters.ui.MedalMoment(s) }
    }
    /** ① 혼자 읽기 이정표 (예전 “잘 읽었어요 · 이제 25%를 혼자 읽어요 · 좋아요”) → 오늘. */
    private fun owMilestone(name: String, dark: Boolean, ms: Long = 3600) = owReward(name, dark, Route.Inbox, ms) {
        AppState.RewardMoment("vincent", 50, null, emptyList(), 25, listOf(io.github.graviton94.todayletters.core.Earn.READ_ALONE to 40, io.github.graviton94.todayletters.core.Earn.LETTER to 10))
    }
    @Test fun owMilestoneMid() = owMilestone("ow1a_milestone_mid_dark", true, 900)
    @Test fun owMilestoneLight() = owMilestone("ow1b_milestone_ko_light", false)
    @Test fun owMilestoneDark() = owMilestone("ow1c_milestone_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owMilestoneEnLight() = owMilestone("ow1d_milestone_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owMilestoneEnDark() = owMilestone("ow1e_milestone_en_dark", true)
    /** ② 받은 화폐만 + 편지 끝에 접힌 등급 · 레벨 한 줄 (예전 “잘 읽었어요 · 좋아요”) → 오늘의 복습 결과. */
    private fun owGain(name: String, dark: Boolean) = owReward(name, dark, Route.Session()) { s ->
        val G = io.github.graviton94.todayletters.core.Reading.Grade
        AppState.RewardMoment("vincent", 12, null, emptyList(), null, listOf(io.github.graviton94.todayletters.core.Earn.REVIEW_RIGHT to 8, io.github.graviton94.todayletters.core.Earn.JOURNEY to 4),
            grade = AppState.GradeUp(s.room("vincent").learn, G.of(3), G.of(4)), level = AppState.LevelUp(17, 18))
    }
    @Test fun owGainLight() = owGain("ow2a_gain_ko_light", false)
    @Test fun owGainDark() = owGain("ow2b_gain_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owGainEnLight() = owGain("ow2c_gain_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owGainEnDark() = owGain("ow2d_gain_en_dark", true)
    /** ③ 오늘의 자료 (예전 “오늘의 자료가 왔어요 · 실제 액자 · 갤러리에 걸기 · 닫기”): 그림 가득 · 벽 라벨 · 갤러리에 걸기 n / N. */
    private fun owPiece(name: String, dark: Boolean, id: String = "met-436529", ms: Long = 4000, milestone: Int? = null) = owReward(name, dark, Route.Inbox, ms) { s ->
        val pc = s.work("vincent").kit.collection.first { it.id == id }
        AppState.RewardMoment("vincent", 18, pc, emptyList(), milestone, listOf(io.github.graviton94.todayletters.core.Earn.REVIEW_RIGHT to 8, io.github.graviton94.todayletters.core.Earn.SHADOW_PASS to 6, io.github.graviton94.todayletters.core.Earn.STREAK to 4))
    }
    @Test fun owPieceStart() = owPiece("ow3a_piece_start_dark", true, ms = 900)
    @Test fun owPieceLight() = owPiece("ow3b_piece_ko_light", false)
    @Test fun owPieceDark() = owPiece("ow3c_piece_ko_dark", true, milestone = 50)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owPieceEnLight() = owPiece("ow3d_piece_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owPieceEnDark() = owPiece("ow3e_piece_en_dark", true)
    /** 명작 (예전 금빛 조명 · 장식 액자 · 금빛 명판): 같은 벽 라벨, 머리말만 “명작”. */
    @Test fun owMaster() = owPiece("ow3f_master_ko_dark", true, id = "met-436528")
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owMasterEn() = owPiece("ow3g_master_en_light", false, id = "met-436528")
    /** 오늘의 자료 → 갤러리: 그림이 ‘모은 작품’ 맨 앞 칸으로 (가운데 · 닿은 뒤). */
    private fun owGallery(name: String, dark: Boolean, fly: Boolean) {
        val s = engineState(); s.reward = null; s.levelUp = null
        val pc = s.work("vincent").kit.collection.first { it.id == "met-436529" }
        s.galleryLanding = pc.id
        if (fly) s.flights.launch(pc.image, rectDp(0f, 0f, 393f, 852f), "gallery:${pc.id}", false, alignY = 0.3f)
        shot(name, dark, ms = if (fly) 160 else 2000, after = 0, series = s) {
            Box(Modifier.fillMaxSize()) { io.github.graviton94.todayletters.ui.GalleryTab(s); io.github.graviton94.todayletters.ui.FlightLayer(s) }
        }
    }
    @Test fun owGalleryMid() = owGallery("ow3h_piece_to_gallery_mid_light", false, true)
    @Test fun owGalleryLanded() = owGallery("ow3i_gallery_landed_light", false, false)
    @Test fun owGalleryLandedDark() = owGallery("ow3j_gallery_landed_dark", true, false)
    /** ④ 이달의 전시를 다 모음 (예전 “도록 넘겨 보기 · 닫기”) → 이달의 전시로. */
    private fun owCatalogue(name: String, dark: Boolean) = owReward(name, dark, Route.Inbox) { s ->
        // 전시 자료를 모두 가진 때만 뜨는 순간 (데이터를 그 상태로)
        s.exhibitionPieces("vincent").forEach { s.store.addPiece("vincent", it.id) }
        AppState.RewardMoment("vincent", 50, null, emptyList(), null, listOf(io.github.graviton94.todayletters.core.Earn.EXHIBITION to 50), s.exhibition("vincent")!!)
    }
    @Test fun owCatalogueLight() = owCatalogue("ow4a_catalogue_ko_light", false)
    @Test fun owCatalogueDark() = owCatalogue("ow4b_catalogue_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owCatalogueEnLight() = owCatalogue("ow4c_catalogue_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owCatalogueEnDark() = owCatalogue("ow4d_catalogue_en_dark", true)
    /** ⑤ 언어 등급 (예전 조명 · 금빛 큰 글자 · ‘좋아요’): 새 등급 (A1 3단 → A2 1단, 오늘 위) · 같은 등급 안 한 단 (걸음 위). */
    private fun owGrade(name: String, dark: Boolean, from: Int, to: Int, ms: Long = 3600, under: Route = Route.Inbox) {
        val s = engineState(); s.reward = null; s.levelUp = null
        s.go(under)
        val G = io.github.graviton94.todayletters.core.Reading.Grade
        s.gradeUp = AppState.GradeUp(s.seriesSettings("vincent").learn, G.of(from), G.of(to))
        shot(name, dark, ms = ms, after = 0) { io.github.graviton94.todayletters.ui.GradeUpOverlay(s) }
    }
    private val stepUnder get() = Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.ALOUD)
    @Test fun owGradeMid() = owGrade("ow5a_grade_band_mid_dark", true, 3, 4, ms = 1100)
    @Test fun owGradeLight() = owGrade("ow5b_grade_band_ko_light", false, 3, 4)
    @Test fun owGradeDark() = owGrade("ow5c_grade_band_ko_dark", true, 3, 4)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owGradeEnLight() = owGrade("ow5d_grade_band_en_light", false, 3, 4)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owGradeEnDark() = owGrade("ow5e_grade_band_en_dark", true, 3, 4)
    @Test fun owGradeStep() = owGrade("ow5f_grade_step_ko_dark", true, 4, 5, under = stepUnder)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owGradeStepEn() = owGrade("ow5g_grade_step_en_light", false, 4, 5, under = stepUnder)
    /** ⑥ 완독 봉인 (예전 맨 아래 ‘닫기’ → ‘편지로 돌아가기’). */
    private fun owSeal(name: String, dark: Boolean) {
        val s = loopState()
        val l = s.work("vincent").chapters.first().letters[0]
        s.shadowChunks("vincent", "I", l).forEachIndexed { i, cs -> cs.indices.forEach { j -> s.take("vincent", "I", l.id, i, j).apply { parentFile?.mkdirs(); writeBytes(ByteArray(16)) } } }
        shot(name, dark, 2000, series = s) { io.github.graviton94.todayletters.ui.SealScreen(s, Route.Seal(room(1))) }
    }
    @Config(qualifiers = "+h1300dp") @Test fun owSealLight() = owSeal("ow6a_seal_ko_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h1300dp-xxhdpi") @Test fun owSealEnDark() = owSeal("ow6b_seal_en_dark", true)
    /** ⑦ 맺음 (예전 ‘닫기’ → ‘오늘로’). */
    @Config(qualifiers = "+h1900dp") @Test fun owOutroLight() { val s = state(true); allDone(s); shot("ow7a_outro_ko_light", false, 3000) { io.github.graviton94.todayletters.ui.OutroScreen(s, "vincent") } }
    @Config(qualifiers = "en-rUS-w393dp-h1900dp-xxhdpi") @Test fun owOutroEnDark() { val s = state(true); allDone(s); shot("ow7b_outro_en_dark", true, 3000) { io.github.graviton94.todayletters.ui.OutroScreen(s, "vincent") } }
    /** ⑧ 새로 바뀐 것 (예전 ‘좋아요’ → 덮인 화면 이름 ‘서재로’). */
    private fun owWhatsNew(name: String, dark: Boolean) { val s = state(true); s.go(Route.Library); s.whatsNew = true
        shot(name, dark) { LibraryTab(s); io.github.graviton94.todayletters.ui.UpdateLayer(s) } }
    @Test fun owWhatsNewLight() = owWhatsNew("ow8a_whatsnew_ko_light", false)
    @Test fun owWhatsNewDark() = owWhatsNew("ow8b_whatsnew_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun owWhatsNewEn() = owWhatsNew("ow8c_whatsnew_en_light", false)

    // ── 편지 방 읽기 흐름 (시안 room src4, 2026-10-25 허락): rr* ──
    // 가짜 낭독 (Narrator.fake) 으로 흐름을 몰아서 찍는다: 끝 = end(), 재생 자리 = at(밀리초).
    private fun rrState(series: String = "vincent"): AppState {
        val s = when (series) {
            "mozart" -> mstate()
            "goya" -> gstate()
            "napoleon" -> state(true).also { fetchedLocally("napoleon", listOf("P", "I")) }
            else -> state(true)
        }
        s.enter(series)
        return s
    }
    private inner class RoomDriver(val s: AppState, val room: Route.Letter) {
        fun end(ms: Long = 0) {
            rule.runOnUiThread { s.narrator.fakeEnd(); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            if (ms > 0) rule.mainClock.advanceTimeBy(ms)
        }
        fun wait(ms: Long) = rule.mainClock.advanceTimeBy(ms)
        /** 문장 [msg] 의 [k] 번째 낱말 가운데쯤으로 (낱말 시각 파일). */
        fun mid(msg: Int, k: Int) {
            val (c, l) = s.letterOf(room)
            val w = s.narrator.words(s.narrator.path(room.series, c, l.id, "m${msg + 1}_${s.room(room.series).learn.code}")) ?: return
            val x = w[k.coerceAtMost(w.lastIndex)]
            rule.runOnUiThread { s.narrator.fakeAt = x.s + (x.e - x.s) * 6 / 10 }
            rule.mainClock.advanceTimeBy(150)
        }
        fun tapText(t: String, substring: Boolean = false, unmerged: Boolean = false) {
            rule.onAllNodesWithText(t, substring = substring, useUnmergedTree = unmerged).onFirst().performClick()
            rule.mainClock.advanceTimeBy(400)
        }
        fun firstLine(msg: Int) = io.github.graviton94.todayletters.core.Breaks.plain(s.letterOf(room).second.messages[msg].text[s.room(room.series).learn]).take(14)
    }
    private fun rrShot(name: String, dark: Boolean, s: AppState, room: Route.Letter, after: Long = 400, drive: RoomDriver.() -> Unit) {
        s.narrator.fake = mutableListOf()
        try { shot(name, dark, ms = 400, act = { RoomDriver(s, room).drive() }, series = s, after = after) { Room(s, room) } }
        finally { s.narrator.fake = null }
    }
    private val vin2 = Route.Letter("vincent", 1, 2, Route.Inbox)

    /** ① 읽는 중: 말한 낱말 먹색 · 지금 낱말 금빛 밑줄 · 아직은 옅게, 뜻은 지금 문장에만, 번역 80%. */
    private fun rrKaraoke(name: String, dark: Boolean) { val s = rrState(); rrShot(name, dark, s, vin2) { mid(0, 5) } }
    @Test fun rr1KaraokeKoLight() = rrKaraoke("rr1a_karaoke_ko_light", false)
    @Test fun rr1KaraokeKoDark() = rrKaraoke("rr1b_karaoke_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr1KaraokeEnLight() = rrKaraoke("rr1c_karaoke_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr1KaraokeEnDark() = rrKaraoke("rr1d_karaoke_en_dark", true)

    /** ② 읽은 첫 문장을 눌러 다시 듣는 중: 띠 “1번째 문장을 다시 듣는 중 · 이어서 듣기 · 2번째부터”. */
    private fun rrReplay(name: String, dark: Boolean) {
        val s = rrState()
        rrShot(name, dark, s, vin2) { end(11_500); tapText(firstLine(0), substring = true); mid(0, 3) }
    }
    @Test fun rr2ReplayKoLight() = rrReplay("rr2a_replay_ko_light", false)
    @Test fun rr2ReplayKoDark() = rrReplay("rr2b_replay_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr2ReplayEnLight() = rrReplay("rr2c_replay_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr2ReplayEnDark() = rrReplay("rr2d_replay_en_dark", true)

    /** ③ 지금 문장의 기능어 (une) 를 누른 카드: 멈춤 · ‘“une” 부터 이어 듣기’ · 닫기. */
    private fun rrWord(name: String, dark: Boolean) { val s = rrState(); rrShot(name, dark, s, vin2) { mid(0, 9); tapText("une", unmerged = true) } }
    @Test fun rr3WordKoLight() = rrWord("rr3a_word_card_ko_light", false)
    @Test fun rr3WordKoDark() = rrWord("rr3b_word_card_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr3WordEnLight() = rrWord("rr3c_word_card_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr3WordEnDark() = rrWord("rr3d_word_card_en_dark", true)

    /** ④ 문장 사이 쉼: 카드 아래 금선이 차는 중, 가운데 단추는 ‘다음 문장으로’. */
    private fun rrHold(name: String, dark: Boolean) { val s = rrState(); rrShot(name, dark, s, vin2) { end(1800) } }
    @Test fun rr4HoldKoLight() = rrHold("rr4a_hold_ko_light", false)
    @Test fun rr4HoldKoDark() = rrHold("rr4b_hold_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr4HoldEnLight() = rrHold("rr4c_hold_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr4HoldEnDark() = rrHold("rr4d_hold_en_dark", true)

    /** ⑤ 빈센트가 보낸 사진 (왼쪽): 보는 동안 쉼 (사진 아래 금선). */
    private fun rrPhoto(name: String, dark: Boolean) { val s = rrState(); rrShot(name, dark, s, vin2) { end(6400) } }
    @Test fun rr5PhotoKoLight() = rrPhoto("rr5a_photo_ko_light", false)
    @Test fun rr5PhotoKoDark() = rrPhoto("rr5b_photo_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr5PhotoEnLight() = rrPhoto("rr5c_photo_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr5PhotoEnDark() = rrPhoto("rr5d_photo_en_dark", true)

    /** ⑥ 빈센트가 위치를 공유했어요 (왼쪽, 지도에서 보기). I장 첫 편지: 알림 줄 → 첫 문장 → 위치. */
    private fun rrPlace(name: String, dark: Boolean) {
        val s = rrState()
        rrShot(name, dark, s, Route.Letter("vincent", 1, 1, Route.Inbox)) { wait(2300); end(6100) }
    }
    @Test fun rr6PlaceKoLight() = rrPlace("rr6a_location_ko_light", false)
    @Test fun rr6PlaceKoDark() = rrPlace("rr6b_location_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr6PlaceEnLight() = rrPlace("rr6c_location_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr6PlaceEnDark() = rrPlace("rr6d_location_en_dark", true)

    /** ⑦ 내 차례 (테오의 송금, 편지 앞): 아래 자리가 ‘테오로서 빈센트에게 50 francs 보내기 →’. */
    private val vin3 = Route.Letter("vincent", 1, 3, Route.Inbox)
    private fun rrAsk(name: String, dark: Boolean) { val s = rrState(); rrShot(name, dark, s, vin3) { wait(300) } }
    @Test fun rr7AskKoLight() = rrAsk("rr7a_theo_ask_ko_light", false)
    @Test fun rr7AskKoDark() = rrAsk("rr7b_theo_ask_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr7AskEnLight() = rrAsk("rr7c_theo_ask_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr7AskEnDark() = rrAsk("rr7d_theo_ask_en_dark", true)

    /** ⑧ 보낸 뒤: 오른쪽 어두운 말풍선 ‘내가 보냄 · 테오 → 빈센트’, 이어서 첫 문장. */
    private fun rrSent(name: String, dark: Boolean) {
        val s = rrState()
        rrShot(name, dark, s, vin3) {
            val t = s.letterOf(vin3).second.moments.filterIsInstance<io.github.graviton94.todayletters.core.Moment.Transfer>().first()
            val ui = if (ctx.resources.configuration.locales[0].language == "ko") io.github.graviton94.todayletters.core.Lang.KO else io.github.graviton94.todayletters.core.Lang.EN
            val to = s.work("vincent").recipient[ui]; val from = s.work("vincent").name[ui]
            val label = ctx.getString(R.string.room_send_as, if (ui == io.github.graviton94.todayletters.core.Lang.KO) to + "로서" else to, from, t.amount)
            rule.onNode(androidx.compose.ui.test.hasContentDescription(label)).performClick()
            wait(900); mid(0, 2)
        }
    }
    @Test fun rr8SentKoLight() = rrSent("rr8a_theo_sent_ko_light", false)
    @Test fun rr8SentKoDark() = rrSent("rr8b_theo_sent_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr8SentEnLight() = rrSent("rr8c_theo_sent_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr8SentEnDark() = rrSent("rr8d_theo_sent_en_dark", true)

    /** ⑨ 다 들은 편지: 마지막 순간 뒤 큐레이터 노트 (미술관 카드) · 아래 자리 ‘다음 걸음 · 낱말 맞추기  2 / N →’. */
    private fun rrNote(name: String, dark: Boolean) {
        val s = rrState()
        val (c, l) = s.letterOf(vin2)
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = l.messages.size))
        rrShot(name, dark, s, vin2) { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performTouchInput { swipeUp(); swipeUp() }; wait(600) }
    }
    @Test fun rr9NoteKoLight() = rrNote("rr9a_note_cta_ko_light", false)
    @Test fun rr9NoteKoDark() = rrNote("rr9b_note_cta_ko_dark", true)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr9NoteEnLight() = rrNote("rr9c_note_cta_en_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr9NoteEnDark() = rrNote("rr9d_note_cta_en_dark", true)

    /** ⑩ 다른 작품의 순간: 모차르트 (볼프강이 사진을 보냄) · 나폴레옹 (위치 공유) · 고야 (사진). */
    private fun rrMozart(name: String, dark: Boolean) { val s = rrState("mozart"); rrShot(name, dark, s, Route.Letter("mozart", 1, 1, Route.Inbox)) { end(5400) } }
    private fun rrNapoleon(name: String, dark: Boolean) { val s = rrState("napoleon"); rrShot(name, dark, s, Route.Letter("napoleon", 1, 1, Route.Inbox)) { wait(2300); end(5500) } }
    private fun rrGoya(name: String, dark: Boolean) {
        val s = rrState("goya")
        rrShot(name, dark, s, Route.Letter("goya", 1, 1, Route.Inbox)) { wait(2300); end(8200); end(5300); end(5600) }
    }
    @Test fun rr10MozartKoLight() = rrMozart("rr10a_mozart_photo_ko_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr10MozartEnDark() = rrMozart("rr10b_mozart_photo_en_dark", true)
    @Test fun rr11NapoleonKoLight() = rrNapoleon("rr11a_napoleon_location_ko_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr11NapoleonEnDark() = rrNapoleon("rr11b_napoleon_location_en_dark", true)
    @Test fun rr12GoyaKoLight() = rrGoya("rr12a_goya_photo_ko_light", false)
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") @Test fun rr12GoyaEnDark() = rrGoya("rr12b_goya_photo_en_dark", true)
}
