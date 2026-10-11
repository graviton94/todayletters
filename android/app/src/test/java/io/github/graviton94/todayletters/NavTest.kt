package io.github.graviton94.todayletters

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.ThemeMode
import io.github.graviton94.todayletters.data.Store
import io.github.graviton94.todayletters.design.TodayLettersTheme
import io.github.graviton94.todayletters.ui.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 길 찾기 (v21 4단계 U5): 앞으로 간 길을 뒤로 되짚으면 들어온 곳으로 돌아오는지.
 * 화면 표의 go · back 길을 앱 상태로 걸어 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w393dp-h852dp-xxhdpi")
class NavTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** [seen] 이면 두 작품 모두 배울 말을 골랐고 프롤로그도 본 것으로 (오늘부터 걷는 길). */
    private fun state(onboarded: Boolean = true, deepLink: Boolean = false, seen: Boolean = onboarded): AppState {
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().clear().commit()
        if (onboarded) Store(ctx).onboarded = true
        if (seen) listOf("vincent", "mozart").forEach { Store(ctx).markLearnChosen(it); Store(ctx).markPrologueSeen(it) }
        return AppState(ctx, deepLink = deepLink)
    }

    private fun AppState.backTo(r: Route) { assertTrue(back()); assertEquals(r, route) }

    /** 처음 들어가는 작품 (모차르트): 무엇을 배울지 고르고 → 그 작품의 프롤로그 (한 번) → 편지 I. 다음부터는 오늘로. */
    @Test fun firstEntryAsksLanguageThenPrologue() {
        val s = state(seen = false)
        if (s.works.none { it.series.id == "mozart" } || s.work("mozart").prologue.isEmpty()) return
        s.enter("mozart")
        assertEquals(Route.Welcome("mozart"), s.route)
        s.backTo(Route.Library)
        s.enter("mozart")
        s.chooseLearn("mozart", io.github.graviton94.todayletters.core.Lang.DE)
        assertEquals(io.github.graviton94.todayletters.core.Lang.DE, s.seriesSettings("mozart").learn)
        assertEquals(Route.Prologue("mozart"), s.route)
        s.finishPrologue("mozart")
        assertEquals(Route.Letter("mozart", 1, 1, Route.Inbox), s.route)
        s.enter("mozart")
        assertEquals(Route.Inbox, s.route)
    }

    @Test fun firstRunGoesStraightToLetterOne() {
        val s = state(onboarded = false)
        assertEquals(Route.Library, s.route)
        s.startFirstLetter("vincent")
        // 프롤로그(아를 이전)를 지나 첫 편지로
        assertEquals(Route.Prologue("vincent"), s.route)
        s.finishPrologue("vincent")
        assertEquals(Route.Letter("vincent", 1, 1, Route.Inbox), s.route)
        s.backTo(Route.Inbox)
        s.backTo(Route.Library)
        assertFalse(s.back())
    }

    @Test fun libraryBranches() {
        val s = state()
        s.go(Route.Overall); s.backTo(Route.Library)
        s.go(Route.Settings); s.backTo(Route.Library)
        s.go(Route.Enter("vincent")); s.backTo(Route.Library)
    }

    @Test fun contentsToLetterAndBack() {
        val s = state()
        s.enter("vincent")
        s.go(Route.Series("vincent")); s.go(Route.Chapter("vincent", 1))
        val room = Route.Letter("vincent", 1, 1)
        s.go(room)
        s.go(Route.RoomInfo(room)); s.backTo(room)
        s.go(Route.Shadow(room)); s.backTo(room)
        s.go(Route.Done(room)); s.go(Route.Artwork("vincent", 1, room)); s.backTo(Route.Done(room)); s.backTo(room)
        s.backTo(Route.Chapter("vincent", 1))
        s.backTo(Route.Series("vincent"))
        s.backTo(Route.Inbox)
        s.backTo(Route.Library)
    }

    @Test fun todayShortcuts() {
        val s = state()
        s.enter("vincent")
        s.go(Route.Week); s.backTo(Route.Inbox)
        s.go(Route.Recital); s.backTo(Route.Inbox)
        s.go(Route.Words); s.go(Route.Quotes); s.backTo(Route.Words)
        s.go(Route.Session()); s.backTo(Route.Words)
        s.backTo(Route.Library)
    }

    /** 알림으로 들어오면 그 작품의 오늘부터, 뒤로 가면 서재. */
    @Test fun deepLinkStartsAtToday() {
        val s = state(deepLink = true)
        assertEquals(Route.Inbox, s.route)
        s.backTo(Route.Library)
    }

    /** 편지 도착 장면에서 뒤로: 들어온 곳(오늘)으로, 도착은 본 것으로 치지 않는다 (다음에 다시 온다). */
    @Test fun arrivalBackReturnsToEntry() {
        val s = state(seen = false)
        s.startFirstLetter("vincent")
        val c = s.work("vincent").chapters.first(); val l = c.letters.first()
        val key = s.key("vincent", c.id, l.id)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            TodayLettersTheme(ThemeMode.LIGHT) {
                io.github.graviton94.todayletters.ui.ArrivalBody(s, "vincent", c.id, l, 1, { _ -> s.store.markArrived(key) }, startAt = 0,
                    onBack = { s.narrator.stop(); s.back() })
            }
        }
        rule.mainClock.advanceTimeBy(600)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(Route.Inbox, s.route)
        assertFalse(s.store.arrived(key))
    }
}
