package io.github.graviton94.todayletters

import android.content.Context
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
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.graviton94.todayletters.core.Exercises
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.ThemeMode
import io.github.graviton94.todayletters.core.TypingPace
import io.github.graviton94.todayletters.data.Store
import io.github.graviton94.todayletters.design.TodayLettersTheme
import io.github.graviton94.todayletters.ui.AppState
import io.github.graviton94.todayletters.ui.Inbox
import io.github.graviton94.todayletters.ui.LibraryTab
import io.github.graviton94.todayletters.ui.Onboarding
import io.github.graviton94.todayletters.ui.Opening
import io.github.graviton94.todayletters.ui.Play
import io.github.graviton94.todayletters.ui.Room
import io.github.graviton94.todayletters.ui.SeriesSettingsScreen
import io.github.graviton94.todayletters.ui.Today
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
            TodayLettersTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { Box(Modifier.fillMaxSize()) { content() } }
        }
        rule.mainClock.advanceTimeBy(ms)
        act()
        rule.mainClock.advanceTimeBy(900)
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun openingKoLight() { val s = state(false); shot("01_opening_ko_light", false) { Opening(s) } }
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi")
    @Test fun openingEnDark() { val s = state(false); shot("02_opening_en_dark", true) { Opening(s) } }
    @Test fun onboarding1() { val s = state(false); shot("03_onboarding1_ko_light", false) { Onboarding(s) } }
    @Test fun onboarding2() { val s = state(false); shot("04_onboarding2_ko_dark", true) { Onboarding(s, startStep = 1) } }
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
    @Test fun seriesSettings() { val s = state(true); shot("11_series_settings_ko_light", false) { SeriesSettingsScreen(s, "vincent") } }
    @Test fun library() { val s = state(true); shot("12_library_ko_dark", true) { LibraryTab(s) } }
}
