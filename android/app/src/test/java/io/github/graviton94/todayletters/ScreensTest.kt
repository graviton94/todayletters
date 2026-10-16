package io.github.graviton94.todayletters

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import io.github.graviton94.todayletters.ui.SeriesSettingsScreen
import io.github.graviton94.todayletters.ui.Session
import io.github.graviton94.todayletters.ui.QuotesScreen
import io.github.graviton94.todayletters.ui.Artwork
import io.github.graviton94.todayletters.ui.postcard
import io.github.graviton94.todayletters.data.DownloadJob
import org.junit.Rule
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
        if (onboarded) Store(ctx).onboarded = true
        if (onboarded && !tutorial) Store(ctx).tutorialDone = true
        val s = AppState(ctx, deepLink = false)
        s.update(s.app.copy(pace = TypingPace.INSTANT))
        // 편지 도착 연출(2단계 D1)은 따로 찍는다: 다른 편지 방 사진에서는 이미 본 것으로
        s.works.forEach { w -> w.chapters.forEach { c -> c.letters.forEach { l -> s.store.markArrived(s.key(w.series.id, c.id, l.id)) } } }
        return s
    }

    /** [series] 가 있으면 앱처럼 그 시리즈의 빛깔로 칠한다 (시리즈 안 화면). */
    private fun shot(name: String, dark: Boolean, ms: Long = 2500, act: () -> Unit = {}, series: AppState? = null, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            TodayLettersTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                if (series != null) io.github.graviton94.todayletters.ui.InSeries(series.currentWork) { Box(Modifier.fillMaxSize().background(Ink.palette.paper)) { content() } }
                else Box(Modifier.fillMaxSize().background(Ink.palette.paper)) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(ms)
        act()
        rule.mainClock.advanceTimeBy(900)
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
        s.reward = null; s.levelUp = null; s.gradeUp = null
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
    @Test fun updateReady() { val s = state(true); s.go(io.github.graviton94.todayletters.core.Route.Library); s.whatsNew = false; s.updateReady = true
        shot("u02_update_ready_ko_dark", true) { LibraryTab(s); io.github.graviton94.todayletters.ui.UpdateLayer(s) } }
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
        shot("38_blank_hint_ko_light", false, act = { rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll("💡 번역에서 찾기")).performClick() }, series = s) {
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
        s.reward = AppState.RewardMoment("vincent", 18, pc, listOf("streak_7"), null,
            listOf(io.github.graviton94.todayletters.core.Earn.REVIEW_RIGHT to 8, io.github.graviton94.todayletters.core.Earn.SHADOW_PASS to 6, io.github.graviton94.todayletters.core.Earn.STREAK to 4))
        shot("45_reward_dark", true, 2000) { io.github.graviton94.todayletters.ui.RewardOverlay(s) }
    }
    @Test fun recital() { val s = engineState(); s.reward = null; shot("46_recital_light", false, series = s) { io.github.graviton94.todayletters.ui.RecitalScreen(s) } }
    @Test fun enter() { val s = engineState(); s.reward = null; shot("47_enter", true, 3000) { io.github.graviton94.todayletters.ui.EnterScreen(s, "vincent") } }
    @Test fun todayEngine() { val s = engineState(); s.reward = null; shot("48_today_engine_light", false, 3000, series = s) { Inbox(s) } }
    @Test fun libraryEngine() { val s = engineState(); s.reward = null; shot("49b_library_engine_light", false, 3000) { LibraryTab(s) } }
    @Test fun speak() {
        val s = engineState()
        shot("49_speak_light", false, series = s) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.SPEAK)) }
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
        s.reward = AppState.RewardMoment("vincent", 28, pc, listOf("words_25"), null,
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
        s.reward = AppState.RewardMoment("vincent", 28, pc, listOf("words_25"), null, listOf(io.github.graviton94.todayletters.core.Earn.LETTER to 6))
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
    private fun shownAll(s: AppState, ch: Int, n: Int) {
        val c = s.work("mozart").chapters[ch - 1]; val l = c.letters[n - 1]
        s.save("mozart", c.id, l.id, s.progress("mozart", c.id, l.id).copy(shown = l.messages.size))
    }
    private fun mozartDone(s: AppState) = s.work("mozart").chapters.forEach { c ->
        c.letters.forEach { l -> s.save("mozart", c.id, l.id, s.progress("mozart", c.id, l.id).copy(shown = l.messages.size, done = true)) }
    }
    @Test fun mozartLibrary() { val s = state(true); shot("m01_library", true) { LibraryTab(s) } }
    @Test fun mozartRoom() { val s = state(true); shownAll(s, 1, 4); shot("m02_room_c1l4", false, 4000, series = s) { Room(s, Route.Letter("mozart", 1, 4)) } }
    @Test fun mozartMusicSheet() {
        val s = state(true); val w = s.work("mozart")
        val m = w.chapters[6].letters[2].moments.filterIsInstance<io.github.graviton94.todayletters.core.Moment.Music>().first()
        shot("m03_music_sheet", false, 3000, series = s) { io.github.graviton94.todayletters.ui.MusicSheet(s, w, m) {} }
    }
    @Test fun mozartWalkEra() { val s = state(true); shot("m04_walk_era", true, series = s) { Artwork(s, Route.Artwork("mozart", 1, Route.Letter("mozart", 1, 3))) } }
    @Test fun mozartWalkScore() {
        val s = state(true)
        // 앱 밖에 둔 장의 그림: 받아 둔 것으로 (받기 전에는 산책 점을 띄우지 않는다, F12)
        java.io.File("../../remote/plates/m-c6-l5.webp").takeIf { it.exists() }?.copyTo(java.io.File(ctx.filesDir, "plates/m-c6-l5.webp").apply { parentFile?.mkdirs() }, overwrite = true)
        io.github.graviton94.todayletters.data.AssetFiles.changed()
        shot("m05_walk_score", false, act = { rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll("산책 시작")).performClick() }, series = s) { Artwork(s, Route.Artwork("mozart", 6, Route.Letter("mozart", 6, 5))) }
    }
    @Test fun mozartPrologue() { val s = state(true); shot("m06_prologue_p1", true, 3000) { io.github.graviton94.todayletters.ui.PrologueScreen(s, Route.Prologue("mozart", 0)) } }
    @Test fun mozartArrival() { val s = state(true); shot("m07_prologue_arrival", true, 3000) { io.github.graviton94.todayletters.ui.PrologueScreen(s, Route.Prologue("mozart", 3)) } }
    @Test fun mozartOutro() { val s = state(true); mozartDone(s); shot("m08_outro", true, 3000) { io.github.graviton94.todayletters.ui.OutroScreen(s, "mozart") } }
    @Test fun mozartCover() { val s = state(true); shot("m09_cover", false, series = s) { io.github.graviton94.todayletters.ui.SeriesCover(s, "mozart") } }
    /** 모차르트에 처음 들어갈 때 (튜토리얼 뒤): 배울 말만 고르는 시트 → 바로 편지 I. */
    @Test fun mozartWelcome() { val s = state(true); shot("m10_welcome", true, series = s.also { it.enter("mozart") }) { io.github.graviton94.todayletters.ui.WelcomeSheet(s, "mozart") } }
    /** 오늘: 편지 카드의 단추 하나 · 장마다 읽은 수 줄의 글자 링크 · 번역 줄. */
    /** 상점 (v5 통합 그림): 소장품이 없어 ‘갤러리 칸 열기’ 줄이 없다. */
    @Test fun mozartShop() { val s = state(true); s.enter("mozart"); s.reward = null; shot("m19_shop", true, series = s) { io.github.graviton94.todayletters.ui.ShopTab(s) } }
    @Test fun mozartToday() { val s = state(true); s.enter("mozart"); shot("m11_today", true, series = s) { io.github.graviton94.todayletters.ui.Inbox(s) } }
    /** 편지 도착: 첫 줄이 다 쓰인 뒤 (아래 ‘편지 읽기’ 단추). */
    @Test fun mozartArrivalLine() {
        val s = state(true); val l = s.work("mozart").chapters.first().letters.first()
        shot("m12_arrival_line", true, series = s) { io.github.graviton94.todayletters.ui.ArrivalBody(s, "mozart", "I", l, 1, { }, startAt = 2) }
    }
    /** 낮은 화면의 편지 도착 그림 (글 묶음이 넘치지 않고 굴러야 한다). */
    @Config(sdk = [34], qualifiers = "ko-rKR-w393dp-h560dp-xxhdpi")
    @Test fun mozartArrivalSmall() {
        val s = state(true); val l = s.work("mozart").chapters.first().letters.first()
        shot("m13_arrival_painting_small", true, series = s) { io.github.graviton94.todayletters.ui.ArrivalBody(s, "mozart", "I", l, 1, { }, startAt = 0) }
    }
    /** 프롤로그 둘째 장: ‘이전’ · ‘건너뛰기’ · ‘다음’. */
    @Test fun mozartProloguePrev() { val s = state(true); shot("m14_prologue_p2", true, 3000) { io.github.graviton94.todayletters.ui.PrologueScreen(s, Route.Prologue("mozart", 1)) } }
    /** 눌러서 뜻 보기: 편지 방에서 낱말 하나의 작은 카드 (표제어 · 등급 · 발음기호 · 뜻 · Tatoeba 예문 · 카드에 담기). */
    @Test fun mozartGloss() {
        val s = state(true); shownAll(s, 1, 4)
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
        val s = state(true)
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
        val s = state(true)
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
        val s = state(true)
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
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(0)
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
            rule.onNodeWithText(ctx.getString(R.string.plan_series, s.work("mozart").name[io.github.graviton94.todayletters.core.Lang.KO])).performClick()
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
            scrolled { rule.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction()).onFirst().performScrollToIndex(0) }
        }, series = s) { Room(s, room(1)) }
    }
    /** 번역 줄 물러나기: B1 에서 다 아는 문장(첫 문장 빼고 모두)은 번역을 접고, 첫 문장은 번역 그대로. 다 읽은 편지라 끝 쪽이 보인다. */
    @Test fun foldedTranslation() {
        val s = graded(engineState().also { it.reward = null }, 7)
        val l = s.work("vincent").chapters.first().letters.first()
        knowWords(s, l, (1 until l.messages.size).toList())
        shot("g03_room_folded_b1_light", false, 3000, series = s) { Room(s, room(1)) }
        org.junit.Assert.assertTrue("last sentence folds", s.folds("vincent", l.id, l.messages.size - 1))
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
    // 오늘의 회화 (메신저, talkchat.html): 아래 칸은 도움 사다리대로 (core 의 TalkTurn). 기기 음성 인식이 없는 Robolectric 에서는 녹음 단추가 ‘말해 봤어요’ (스스로 판정)
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
    /** 다 마친 대화 (A1): 말해 봤어요 → 원래 소리 → 맞게 말했어요 를 두 번, 내 말풍선에 금빛 체크 · 아래 칸은 마치기. */
    @Test fun depthTalkFinished() = withDepth {
        val s = graded(state(true), 1)
        shot("depth12_talk_finished_light", false, ms = 4000, act = {
            repeat(2) {
                tap(ctx.getString(R.string.talk_said))
                tap(ctx.getString(R.string.speak_ok))
                rule.mainClock.advanceTimeBy(2500)
            }
            org.junit.Assert.assertTrue(rule.onAllNodes(hasText(ctx.getString(R.string.talk_finish)) and hasClickAction()).fetchSemanticsNodes().isNotEmpty())
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
        shot("slow01_talk_bubble_light", false, ms = 4000, act = { org.junit.Assert.assertTrue(slowButtons() >= 2) }, series = s) { Play(s, talkRoute) }
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
            rule.onNode(hasText(ctx.getString(R.string.set_listen_auto).uppercase())).performScrollTo()
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
        val on = androidx.compose.runtime.mutableStateOf(false)
        shot("finish04_next_glow_light", false, ms = 500, act = { on.value = true }) {
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)) {
                io.github.graviton94.todayletters.ui.Primary("다음: 낱말 맞추기", enabled = false) {}
                io.github.graviton94.todayletters.ui.Primary("다음: 낱말 맞추기 ›", enabled = on.value) {}
                androidx.compose.runtime.CompositionLocalProvider(io.github.graviton94.todayletters.ui.LocalStill provides true) {
                    io.github.graviton94.todayletters.ui.Primary("다음: 낱말 맞추기 ›", enabled = on.value) {}
                }
            }
        }
    }
}
