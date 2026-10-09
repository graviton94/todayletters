package io.github.graviton94.todayletters

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
import io.github.graviton94.todayletters.ui.Opening
import io.github.graviton94.todayletters.ui.Play
import io.github.graviton94.todayletters.ui.Room
import io.github.graviton94.todayletters.ui.SeriesSettingsScreen
import io.github.graviton94.todayletters.ui.Today
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

    private fun shot(name: String, dark: Boolean, ms: Long = 2500, act: () -> Unit = {}, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            TodayLettersTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { Box(Modifier.fillMaxSize().background(Ink.palette.paper)) { content() } }
        }
        rule.mainClock.advanceTimeBy(ms)
        act()
        rule.mainClock.advanceTimeBy(900)
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun openingKoLight() { val s = state(false); shot("01_opening_ko_light", false) { Opening(s) } }
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi")
    @Test fun openingEnDark() { val s = state(false); shot("02_opening_en_dark", true) { Opening(s) } }
    @Test fun onboarding0() { val s = state(false); shot("03a_onboarding0_ko_light", false) { Onboarding(s) } }
    @Test fun onboarding1() { val s = state(false); shot("03_onboarding1_ko_light", false) { Onboarding(s, startStep = 1) } }
    @Test fun onboarding2() { val s = state(false); shot("04_onboarding2_ko_dark", true) { Onboarding(s, startStep = 2) } }
    @Test fun today() { val s = state(true); shot("05_today_ko_dark", true) { Today(s) } }
    @Test fun inbox() { val s = state(true); shot("06_inbox_ko_light", false) { Inbox(s) } }
    @Test fun roomLight() { val s = state(true); shot("07_room_ko_light", false, 6000) { Room(s, Route.Letter("vincent", 1, 1)) } }
    @Test fun roomDark() { val s = state(true); shot("08_room_ko_dark", true, 6000) { Room(s, Route.Letter("vincent", 1, 1)) } }
    @Test fun constellation() {
        val s = state(true)
        val letter = s.work("vincent").chapters.first().letters.first()
        val answer = Exercises.constellation(Exercises.replyFor(letter, s.seriesSettings("vincent").learn).first, letter.id.hashCode()).first
        shot("09_constellation_ko_light", false, act = {
            answer.take(2).forEach { rule.onAllNodes(hasText(it) and hasClickAction() and isEnabled()).onFirst().performClick(); rule.mainClock.advanceTimeBy(700) }
        }) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.CONSTELLATION)) }
    }
    @Test fun constellationDark() {
        val s = state(true)
        val letter = s.work("vincent").chapters.first().letters.first()
        val answer = Exercises.constellation(Exercises.replyFor(letter, s.seriesSettings("vincent").learn).first, letter.id.hashCode()).first
        shot("10_constellation_ko_dark", true, act = {
            answer.take(3).forEach { rule.onAllNodes(hasText(it) and hasClickAction() and isEnabled()).onFirst().performClick(); rule.mainClock.advanceTimeBy(700) }
        }) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.CONSTELLATION)) }
    }
    @Test fun match() { val s = state(true); shot("13_match_ko_light", false, act = {
        val l = s.work("vincent").chapters.first().letters.first()
        val w = l.words.first().text[s.seriesSettings("vincent").learn]
        rule.onAllNodes(hasText(w) and hasClickAction()).onFirst().performClick()
    }) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.MATCH)) } }
    @Test fun aloud() { val s = state(true); shot("14_aloud_ko_dark", true) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.ALOUD)) } }
    @Test fun done() { val s = state(true); shot("15_done_ko_light", false, 4000) { Done(s, Route.Letter("vincent", 1, 1)) } }
    @Test fun chapter() {
        val s = state(true)
        val (c, l) = s.openable("vincent").first()
        s.open("vincent", c, l, Route.Library)
        shot("16_chapter_ko_dark", true) { ChapterScreen(s, Route.Chapter("vincent", 1)) }
    }
    @Test fun words() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = 4))
        shot("17_words_ko_light", false) { WordsTab(s) }
    }
    @Test fun roomGuide() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = 4))
        shot("18_room_finished_ko_light", false, 4000) { Room(s, Route.Letter("vincent", 1, 1)) }
    }
    @Test fun seriesSettings() { val s = state(true); shot("11_series_settings_ko_light", false) { SeriesSettingsScreen(s, "vincent") } }
    @Test fun library() { val s = state(true); shot("12_library_ko_dark", true) { LibraryTab(s) } }

    /** 편지 두 통을 끝내고 낱말을 모은 상태 (복습 화면들). 몇 장은 오늘 복습할 차례로. */
    private fun withCards(): AppState {
        val s = state(true)
        s.work("vincent").chapters.first().letters.take(2).forEach { l -> s.collectWords("vincent", "I", l) }
        s.cards().forEachIndexed { i, c -> s.store.save(c.copy(box = i % 4, due = if (i % 3 == 0) s.today + 2 else s.today)) }
        return s
    }
    @Test fun review() { val s = withCards(); shot("20_review_ko_light", false) { WordsTab(s) } }
    @Test fun reviewDark() { val s = withCards(); shot("21_review_ko_dark", true) { WordsTab(s) } }
    @Test fun dictation() { val s = withCards(); shot("22_dictation_ko_light", false) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.DICTATION)) } }
    @Test fun dictationPad() {
        val s = withCards()
        shot("22b_dictation_pad_ko_light", false, act = { rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll("프랑스어 글자판")).performClick() }) {
            Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.DICTATION))
        }
    }
    @Test fun blank() { val s = withCards(); shot("23_blank_ko_dark", true) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.BLANK)) } }
    @Test fun meaning() { val s = withCards(); shot("24_meaning_ko_light", false) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.MEANING)) } }
    @Test fun quotes() {
        val s = withCards()
        s.toggleQuote(s.quoteKey("vincent", "I", s.work("vincent").chapters.first().letters.first().id, 2))
        s.toggleQuote(s.quoteKey("vincent", "I", s.work("vincent").chapters.first().letters[1].id, 0))
        shot("25_quotes_ko_light", false) { QuotesScreen(s) }
    }
    @Test fun walk() { val s = state(true); shot("26_walk_intro", false) { Artwork(s, Route.Artwork("vincent", 1, Route.Letter("vincent", 1, 1))) } }
    @Test fun walkSpot() {
        val s = state(true)
        shot("27_walk_spot", false, act = { rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll("산책 시작")).performClick() }) { Artwork(s, Route.Artwork("vincent", 1, Route.Letter("vincent", 1, 3))) }
    }
    @Test fun todayHome() { val s = withCards(); shot("28_today_home_ko_light", false) { Inbox(s) } }
    @Test fun moments() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters[2]
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size))
        shot("29_room_moments_ko_dark", true, 4000) { Room(s, Route.Letter("vincent", 1, 3)) }
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
        s.open("vincent", c, l, Route.Library)
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = l.messages.size, done = true))
        // 이틀 전에 첫 편지를 열었다 → 다음 편지는 어제 도착, 아직 안 열었으니 부재중
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().putLong("o:vincent:day", s.today - 2).putInt("o:vincent:n", 1).commit()
        shot("31_library_missed_ko_dark", true) { LibraryTab(s) }
    }
    @Test fun roomLocation() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters.first()
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size))
        shot("32_room_location_ko_dark", true, 4000) { Room(s, Route.Letter("vincent", 1, 1)) }
    }
    @Test fun askDialog() {
        val s = state(true)
        shot("33_ask_ko_dark", true) { io.github.graviton94.todayletters.ui.Ask("복습을 그만할까요?", "그만하기", "계속하기", {}, {}, body = "지금까지 푼 것은 저장돼요.") }
    }
    @Test fun koreanPad() {
        val s = withCards()
        s.update(s.app.copy(read = io.github.graviton94.todayletters.core.Lang.EN))
        s.update("vincent", s.seriesSettings("vincent").copy(learn = io.github.graviton94.todayletters.core.Lang.KO))
        shot("34_korean_pad_light", false) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.DICTATION)) }
    }
    @Test fun roomTransfer() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters[1]
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size))
        shot("35_room_transfer_ko_light", false, 4000) { Room(s, Route.Letter("vincent", 1, 2)) }
    }
    @Test fun roomRent() {
        val s = state(true)
        val l = s.work("vincent").chapters.first().letters[4]
        s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size))
        shot("36_room_rent_ko_dark", true, 4000) { Room(s, Route.Letter("vincent", 1, 5)) }
    }
    @Test fun chapterEnd() {
        val s = withCards()
        s.work("vincent").chapters.first().letters.forEach { l ->
            s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size, done = true))
            s.store.markStarted(s.key("vincent", "I", l.id))
        }
        shot("37_chapter_end_ko_dark", true) { Inbox(s) }
    }
    @Test fun blankHint() {
        val s = withCards()
        shot("38_blank_hint_ko_light", false, act = { rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll("💡 번역에서 찾기")).performClick() }) {
            Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.BLANK))
        }
    }

    /** 엔진 화면용: 편지 세 통을 끝내고, 낱말 몇 개는 위 단계로, 우표 80장. */
    private fun engineState(): AppState {
        val s = withCards()
        val ls = s.work("vincent").chapters.first().letters
        ls.take(3).forEach { l ->
            s.save("vincent", "I", l.id, s.progress("vincent", "I", l.id).copy(shown = l.messages.size, done = true))
            s.store.markStarted(s.key("vincent", "I", l.id))
            s.collectWords("vincent", "I", l)
        }
        s.cards().forEachIndexed { k, c -> s.store.save(c.copy(box = k % 6)) }
        s.store.wallet = io.github.graviton94.todayletters.core.Wallet(balance = 80)
        s.store.unlock("first_letter"); s.store.unlock("streak_3"); s.store.addStamp("vincent:neige"); s.store.addStamp("vincent:pont")
        s.store.markDoneDay(s.today); s.store.markDoneDay(s.today - 1); s.store.markDoneDay(s.today - 3)
        return s
    }
    private fun albumTab(name: String, label: String, dark: Boolean) {
        val s = engineState()
        shot(name, dark, act = { if (label.isNotEmpty()) rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll(label)).performClick() }) {
            io.github.graviton94.todayletters.ui.CollectionTab(s)
        }
    }
    @Test fun albumCabinet() = albumTab("40_album_cabinet_dark", "", true)
    @Test fun albumItems() = albumTab("41_album_items_light", "물건", false)
    @Test fun albumJournal() = albumTab("42_album_journal_light", "여행기", false)
    @Test fun albumStamps() = albumTab("43_album_stamps_light", "기념 우표", false)
    @Test fun albumProgress() = albumTab("44_album_progress_dark", "진도 · 업적", true)
    @Test fun reward() {
        val s = engineState()
        val w = s.work("vincent")
        s.reward = AppState.RewardMoment(24, w.chapters.first().letters[0].words.filter { it.icon.isNotEmpty() }.take(1), listOf("streak_7"), listOf(w.kit.stamps[2]), 25)
        shot("45_reward_dark", true, 2000) { io.github.graviton94.todayletters.ui.RewardOverlay(s) }
    }
    @Test fun parcel() { val s = engineState(); shot("46_parcel_light", false) { io.github.graviton94.todayletters.ui.ParcelScreen(s, "vincent") } }
    @Test fun visitor() { val s = engineState(); shot("47_visitor_light", false) { io.github.graviton94.todayletters.ui.VisitorScreen(s) } }
    @Test fun todayEngine() { val s = engineState(); s.reward = null; shot("48_today_engine_light", false, 3000) { Inbox(s) } }
    @Test fun speak() {
        val s = engineState()
        shot("49_speak_light", false) { Session(s, Route.Session(io.github.graviton94.todayletters.core.ReviewKind.SPEAK)) }
    }
    @Test fun roomFade() {
        val s = engineState()
        s.cards().forEach { c -> s.store.save(c.copy(box = 3)) }
        shot("50_room_fade_ko_light", false, 4000) { Room(s, Route.Letter("vincent", 1, 1)) }
    }
}
