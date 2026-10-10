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

    private fun state(onboarded: Boolean): AppState {
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().clear().commit()
        if (onboarded) Store(ctx).onboarded = true
        val s = AppState(ctx, deepLink = false)
        s.update(s.app.copy(pace = TypingPace.INSTANT))
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
    }

    @Test fun opening() { val s = state(true); shot("01_opening_ko_dark", true, 4500) { io.github.graviton94.todayletters.ui.Opening(s) } }
    @Test fun onboarding0() { val s = state(false); shot("03a_onboarding0_ko_light", false) { Onboarding(s) } }
    @Test fun onboarding1() { val s = state(false); shot("03_onboarding1_ko_light", false) { Onboarding(s, startStep = 1) } }
    @Test fun onboarding2() { val s = state(false); shot("04_onboarding2_ko_dark", true) { Onboarding(s, startStep = 2) } }
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
        s.store.unlock("vincent:alone_1"); s.store.setAchievedDay("vincent:alone_1", s.today - 8)
        s.store.unlock("vincent:streak_7"); s.store.setAchievedDay("vincent:streak_7", s.today - 3)
        s.store.markDoneDay(s.today); s.store.markDoneDay(s.today - 1); s.store.markDoneDay(s.today - 3)
        s.store.saveStreak(s.today, 12); s.store.rests = 1
        return s
    }
    @Test fun gallery() { val s = engineState(); s.reward = null; shot("40_gallery_dark", true, series = s) { io.github.graviton94.todayletters.ui.GalleryTab(s) } }
    @Test fun milestones() { val s = engineState(); s.reward = null; shot("41_milestones_light", false, series = s) { io.github.graviton94.todayletters.ui.MilestonesTab(s) } }
    @Test fun overall() { val s = engineState(); s.reward = null; shot("42_overall_light", false) { io.github.graviton94.todayletters.ui.OverallScreen(s) } }
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
    @Test fun todaySurprise() {
        val s = loopState()
        val l = s.work("vincent").chapters.first().letters.first()
        s.store.save(io.github.graviton94.todayletters.core.Card(s.cardKey("vincent", "I", l.id, 1), box = 5, due = s.today + 30, seen = 8, last = s.today - 40))
        shot("63_today_surprise_light", false, 2000, series = s) { Inbox(s) }
    }
}
