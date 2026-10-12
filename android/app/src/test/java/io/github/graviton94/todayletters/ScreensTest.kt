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
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    @Test fun match() { val s = state(true); shot("13_match_ko_light", false, act = {
        val l = s.work("vincent").chapters.first().letters.first()
        val w = l.words.first().text[s.seriesSettings("vincent").learn]
        rule.onAllNodes(hasText(w) and hasClickAction()).onFirst().performClick()
    }, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.MATCH)) } }
    @Test fun aloud() { val s = state(true); shot("14_aloud_ko_dark", true, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.ALOUD)) } }
    @Test fun done() { val s = state(true); shot("15_done_ko_light", false, 4000, series = s) { Done(s, Route.Letter("vincent", 1, 1)) } }
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
        val l = s.work("vincent").chapters.first().letters.first()
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = 4))
        shot("18_room_finished_ko_light", false, 4000, series = s) { Room(s, Route.Letter("vincent", 1, 1)) }
    }
    @Test fun seriesSettings() { val s = state(true); shot("11_series_settings_ko_light", false, series = s) { SeriesSettingsScreen(s, "vincent") } }
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
        val s = withCards()
        s.update(s.app.copy(read = io.github.graviton94.todayletters.core.Lang.EN))
        s.update("vincent", s.seriesSettings("vincent").copy(learn = io.github.graviton94.todayletters.core.Lang.KO))
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
        val s = engineState()
        s.cards().forEach { c -> s.store.save(c.copy(box = 3)) }
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
            val rv = io.github.graviton94.todayletters.data.TodayWidget.views(ctx, sum, art, small)
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
    @Test fun releaseCover() { val s = state(true); allDone(s, 3); shot("r04_cover_rows", false, series = s) { io.github.graviton94.todayletters.ui.SeriesCover(s, "vincent") } }
    @Test fun releaseDoneLast() { val s = state(true); allDone(s); shot("r05_done_last", false, 4000, series = s) { Done(s, Route.Letter("vincent", 8, 7)) } }
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

    // ── APK 20 피드백: 상점 · 구독 · 나의 기록 · 갤러리 세 묶음 · 알림 시계 바퀴 · 확인 창 ──
    @Test fun purchase() { val s = engineState(); s.reward = null; shot("p01_purchase_light", false, series = s) { io.github.graviton94.todayletters.ui.PurchaseScreen(s) } }
    @Test fun purchaseDark() { val s = engineState(); s.reward = null; shot("p02_purchase_dark", true, series = s) { io.github.graviton94.todayletters.ui.PurchaseScreen(s) } }
    @Test fun recordDark() { val s = engineState(); s.reward = null; shot("p03_record_dark", true) { io.github.graviton94.todayletters.ui.RecordScreen(s) } }
    @Test fun galleryGroups() { val s = engineState(); s.reward = null; shot("p04_gallery_groups_light", false, series = s) { io.github.graviton94.todayletters.ui.GalleryTab(s) } }
    @Test fun galleryEmpty() { val s = state(true); shot("p05_gallery_empty_light", false, series = s) { io.github.graviton94.todayletters.ui.GalleryTab(s) } }
    @Test fun collection() { val s = engineState(); s.reward = null; shot("p06_collection_light", false, series = s) { io.github.graviton94.todayletters.ui.CollectionScreen(s, "vincent") } }
    @Test fun alarmWheel() {
        val s = state(true)
        shot("p07_alarm_wheel_light", false, act = {
            rule.onNodeWithText(ctx.getString(R.string.set_arrival)).performScrollTo().performClick()
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
            rule.onNodeWithText(ctx.getString(R.string.set_help_again)).performScrollTo().performClick()
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
        rule.onNodeWithText(ctx.getString(R.string.today_all_letters)).performScrollTo()
    }, series = s) { Inbox(s) } }
    @Test fun openingScrim() {
        val s = engineState(); s.reward = null
        // 밝은 그림 셋 이상을 가진 상태 (입구 장식 줄이 밝은 그림 위에)
        shot("p14_opening_scrim_dark", true, 4500) { io.github.graviton94.todayletters.ui.Opening(s) }
    }
}
