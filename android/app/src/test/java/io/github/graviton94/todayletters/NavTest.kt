package io.github.graviton94.todayletters

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    /** [seen] 이면 두 작품 모두 배울 말을 골랐고 프롤로그도 본 것으로, 튜토리얼도 마친 것으로 (오늘부터 걷는 길). */
    private fun state(onboarded: Boolean = true, deepLink: Boolean = false, seen: Boolean = onboarded): AppState {
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().clear().commit()
        if (onboarded) Store(ctx).onboarded = true
        if (seen) { listOf("vincent", "mozart").forEach { Store(ctx).markLearnChosen(it); Store(ctx).markPrologueSeen(it) }; Store(ctx).tutorialDone = true }
        return AppState(ctx, deepLink = deepLink)
    }

    /** 편지 방의 걸음을 차례로: [skip] 에 든 걸음은 건너뛰고 나머지는 한다. 마지막 걸음 뒤에는 편지 완료 화면. */
    private fun AppState.walkLetter(room: Route.Letter, skip: Set<io.github.graviton94.todayletters.core.ReplyMode> = emptySet()) {
        val (c, l) = letterOf(room)
        save(room.series, c, l.id, progress(room.series, c, l.id).copy(shown = l.messages.size))
        letterModes(room.series, c, l).forEach { m ->
            go(Route.Play(room, m))
            if (m in skip) skipStep(room, m) else replyAndContinue(room, m)
        }
    }

    private fun AppState.backTo(r: Route) { assertTrue(back()); assertEquals(r, route) }

    /**
     * 튜토리얼 뒤 다른 작품 (모차르트) 에 처음 들어갈 때: 배울 말만 고르는 시트 → 바로 편지 I (프롤로그는 저절로 띄우지 않고 목차에서).
     * 도움말 · 안내 한 줄은 저절로 뜨지 않는다. 다음부터는 오늘로.
     */
    @Test fun secondSeriesEntryAsksOnlyLanguage() {
        val s = state(seen = false)
        s.store.markLearnChosen("vincent"); s.store.tutorialDone = true
        if (s.works.none { it.series.id == "mozart" }) return
        s.enter("mozart")
        assertEquals(Route.Welcome("mozart"), s.route)
        s.backTo(Route.Library)
        s.enter("mozart")
        s.chooseLearn("mozart", io.github.graviton94.todayletters.core.Lang.DE)
        assertEquals(io.github.graviton94.todayletters.core.Lang.DE, s.seriesSettings("mozart").learn)
        assertEquals(Route.Letter("mozart", 1, 1, Route.Inbox), s.route)
        assertTrue(s.stack.none { it is Route.Prologue })
        assertFalse(s.guideDue("read")); assertFalse(s.guideDue("match"))
        assertFalse(s.coach.due("room", calm = true))
        s.backTo(Route.Inbox)
        // 프롤로그는 목차에서 언제든, 다 보면 목차로
        if (s.work("mozart").prologue.isNotEmpty()) {
            s.go(Route.Series("mozart")); s.go(Route.Prologue("mozart"))
            s.finishPrologue("mozart")
            assertEquals(Route.Series("mozart"), s.route)
        }
        s.enter("mozart")
        assertEquals(Route.Inbox, s.route)
    }

    /**
     * 처음 설치 (튜토리얼): 입구 → 환영 → 서재 (빈센트 카드에서 배울 말) → 편지 I (걸음마다 처음 한 번 안내 한 줄, 건너뛰기 가능)
     * → 편지 완료 + “이제 혼자서도 괜찮아요” → 끝. 그 뒤로는 어느 화면에서도 도움말이 저절로 뜨지 않는다.
     */
    @Test fun firstInstallTutorial() {
        val s = state(onboarded = false)
        assertEquals(io.github.graviton94.todayletters.core.Stage.OPENING, s.stage)
        s.nextStage()
        assertEquals(io.github.graviton94.todayletters.core.Stage.ONBOARDING, s.stage)
        s.finishWelcome()
        assertEquals(io.github.graviton94.todayletters.core.Stage.MAIN, s.stage)
        assertTrue(s.store.onboarded)
        assertEquals(Route.Library, s.route)
        assertFalse(s.tutorialDone)
        assertEquals("vincent", s.tutorialSeries)
        assertTrue(s.needsWelcome("vincent"))   // 서재가 빈센트 카드를 강조하고 그 카드에서 고른다
        val learn = io.github.graviton94.todayletters.core.Langs.defaultLearn(s.work("vincent").series, s.app.read)
        s.startTutorial(learn)
        val room = Route.Letter("vincent", 1, 1, Route.Inbox)
        assertEquals(room, s.route)
        assertEquals(learn, s.seriesSettings("vincent").learn)
        assertTrue(s.stack.none { it is Route.Prologue })
        // 안내 한 줄: 그 걸음에 처음 들어갈 때만
        assertTrue(s.guideDue("read")); s.guideShown("read"); assertFalse(s.guideDue("read"))
        assertTrue(s.guideDue("match"))
        // 따라 읽기는 건너뛰고 나머지는 한다 → 편지 완료
        s.walkLetter(room, skip = setOf(io.github.graviton94.todayletters.core.ReplyMode.ALOUD))
        assertEquals(Route.Done(room), s.route)
        val (c, l) = s.letterOf(room)
        assertTrue(s.progress("vincent", c, l.id).done)
        val modes = s.letterModes("vincent", c, l)
        if (io.github.graviton94.todayletters.core.ReplyMode.ALOUD in modes) {
            assertEquals(listOf(io.github.graviton94.todayletters.core.ReplyMode.ALOUD), s.progress("vincent", c, l.id).toRedo)
            assertEquals(1, s.redos("vincent").size)
        }
        // 오늘의 여정: 편지는 마침, 복습할 카드는 아직 (내일부터) → 여정 완료 · 보너스
        val j = s.journey("vincent")
        assertEquals(io.github.graviton94.todayletters.core.Journey.Step.DONE, j.letter)
        assertEquals(io.github.graviton94.todayletters.core.Journey.Step.NONE, j.review)
        assertTrue(j.paid)
        assertTrue(s.wallet("vincent").balance > 0)
        // 편지 완료 화면에서 “이제 혼자서도 괜찮아요” → 튜토리얼 끝
        assertFalse(s.tutorialDone)
        s.graduate()
        assertTrue(s.tutorialDone)
        assertFalse(s.guideDue("match")); assertFalse(s.guideDue("reply"))
        assertFalse(s.coach.due("room", calm = true)); assertFalse(s.coach.due("library", calm = true))
        // (?) 로는 언제든
        s.coachAgain("room"); assertTrue(s.coach.due("room", calm = true))
        s.reward = null
        s.backTo(room)
        s.backTo(Route.Inbox)
        s.backTo(Route.Library)
        assertFalse(s.back())
    }

    /** 예전 사용자: 편지를 한 통이라도 마쳤으면 튜토리얼은 끝난 것 (안내 · 서재 강조 없이). */
    @Test fun existingUsersSkipTutorial() {
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().clear().commit()
        val st = Store(ctx)
        st.onboarded = true
        val probe = AppState(ctx)
        val c = probe.work("vincent").chapters.first(); val l = c.letters.first()
        st.save(probe.key("vincent", c.id, l.id), io.github.graviton94.todayletters.core.LetterProgress(shown = l.messages.size, done = true))
        val s = AppState(ctx)
        assertTrue(s.tutorialDone)
        assertFalse(s.guideDue("read"))
    }

    @Test fun libraryBranches() {
        val s = state()
        s.go(Route.Record); s.go(Route.Medals); s.backTo(Route.Record); s.backTo(Route.Library)
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
        // 상점 › 구독 · 시리즈
        s.go(Route.Shop)
        s.go(Route.Purchase); s.backTo(Route.Shop)
        s.go(Route.Gallery); s.go(Route.Catalogue("vincent")); s.backTo(Route.Gallery)
        s.go(Route.Collection("vincent")); s.backTo(Route.Gallery)
        s.go(Route.Words); s.go(Route.Session(free = true)); s.backTo(Route.Words)
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
        s.startTutorial(io.github.graviton94.todayletters.core.Langs.defaultLearn(s.work("vincent").series, s.app.read))
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

    /** 배우는 말 바꾸기 (v22): 진도는 언어마다 따로, 바꾸면 그 언어의 진도로 건너가고 처음 한 번 알림. 화면은 그대로. */
    @Test fun languageSwitchKeepsProgressPerLanguage() {
        val s = state()
        s.enter("vincent")
        val c = s.work("vincent").chapters.first(); val l = c.letters.first()
        val original = s.seriesSettings("vincent").learn
        s.save("vincent", c.id, l.id, s.progress("vincent", c.id, l.id).copy(shown = l.messages.size, done = true))
        assertEquals(1, s.doneCount("vincent"))
        val other = io.github.graviton94.todayletters.core.Langs.learnChoices(s.work("vincent").series, s.app.read).first { it != original }
        s.switchLearn("vincent", other)
        assertEquals(Route.Inbox, s.route)
        assertTrue(s.langNotice)
        assertEquals(0, s.doneCount("vincent"))
        assertFalse(s.progress("vincent", c.id, l.id).done)
        s.langNotice = false
        s.switchLearn("vincent", original)
        assertFalse(s.langNotice)   // 알림은 한 번만
        assertTrue(s.progress("vincent", c.id, l.id).done)
        assertEquals(1, s.doneIn("vincent", original)); assertEquals(0, s.doneIn("vincent", other))
    }

    /** 예전 (언어 구분 없던) 진도는 그때 배우던 말의 것으로 옮겨진다. */
    @Test fun oldProgressMovesToItsLanguage() {
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().clear().commit()
        val st = Store(ctx)
        st.onboarded = true
        listOf("vincent", "mozart").forEach { st.markLearnChosen(it); st.markPrologueSeen(it) }
        st.save("vincent", io.github.graviton94.todayletters.core.SeriesSettings(learn = io.github.graviton94.todayletters.core.Lang.EN))
        val probe = AppState(ctx)
        val c = probe.work("vincent").chapters.first(); val l = c.letters.first()
        // 앱이 처음 켜지기 전의 열쇠로 다시 써 두고 (옮긴 표시도 지우고) 새로 켠다
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().remove("spaces:vincent").commit()
        st.save("vincent:${c.id}:${l.id}", io.github.graviton94.todayletters.core.LetterProgress(shown = l.messages.size, done = true))
        st.markStarted("vincent:${c.id}:${l.id}")
        val s = AppState(ctx)
        assertEquals("vincent@en:${c.id}:${l.id}", s.key("vincent", c.id, l.id))
        assertTrue(s.progress("vincent", c.id, l.id).done)
        assertTrue(s.store.started(s.key("vincent", c.id, l.id)))
        s.switchLearn("vincent", io.github.graviton94.todayletters.core.Lang.FR)
        assertFalse(s.progress("vincent", c.id, l.id).done)
    }

    /** 상점 (넷째 이름표) 맨 아래 ‘구독 · 시리즈’ → 구매 화면, 뒤로 가면 상점. 결제는 아직 열리지 않았다. */
    @Test fun shopLeadsToPurchase() {
        val s = state()
        s.enter("vincent")
        s.go(Route.Shop)
        assertEquals(listOf(Route.Library, Route.Shop), s.stack.toList())
        rule.setContent { TodayLettersTheme(ThemeMode.LIGHT) { io.github.graviton94.todayletters.ui.ShopTab(s) } }
        rule.onNodeWithText(ctx.getString(R.string.shop_plans)).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(Route.Purchase, s.route)
        assertFalse(io.github.graviton94.todayletters.data.Billing.ready)
        s.backTo(Route.Shop)
        s.backTo(Route.Library)
    }

    /** 나의 기록은 서재 머리에서 (하나뿐, 모든 작품 합산). 작품 머리의 칭호 줄로 들어가면 뒤로 가기는 그 작품으로. */
    @Test fun libraryLeadsToMyRecord() {
        val s = state()
        rule.setContent { TodayLettersTheme(ThemeMode.LIGHT) { io.github.graviton94.todayletters.ui.LibraryTab(s) } }
        rule.onNodeWithText(ctx.getString(R.string.lib_overall)).performClick()
        rule.waitForIdle()
        assertEquals(Route.Record, s.route)
        s.go(Route.Medals); s.backTo(Route.Record)
        s.go(Route.Week); s.backTo(Route.Record)
        s.backTo(Route.Library)
        // 작품 안 (오늘) 의 칭호 줄 → 나의 기록 → 뒤로 오늘
        s.enter("vincent")
        s.go(Route.Record)
        s.backTo(Route.Inbox)
    }

    /**
     * 설정 › 튜토리얼 다시 하기: 확인 뒤 빈센트의 편지 I 로 바로, 걸음마다 한 줄 안내가 다시. 그 편지는 연습 진도로 처음부터 하고
     * 저장된 진도 · 화폐는 그대로. 마치면 (“이제 혼자서도 괜찮아요”) 원래 진도로 돌아온다.
     */
    @Test fun tutorialReplayKeepsProgress() {
        val s = state()
        s.enter("vincent")
        val c = s.work("vincent").chapters.first(); val l = c.letters.first()
        val k = s.key("vincent", c.id, l.id)
        s.save("vincent", c.id, l.id, s.progress("vincent", c.id, l.id).copy(shown = l.messages.size, done = true))
        s.store.markStarted(k)
        s.settle(); s.reward = null; s.levelUp = null
        val balance = s.wallet("vincent").balance
        val done = s.doneCount("vincent")
        s.settingsOpen = true
        s.replayTutorial()
        assertFalse(s.settingsOpen)
        val room = Route.Letter("vincent", 1, 1, Route.Inbox)
        assertEquals(room, s.route)
        assertEquals(listOf(Route.Library, Route.Inbox, room), s.stack.toList())
        assertFalse(s.tutorialDone)
        assertTrue(s.guideDue("read"))
        // 연습 진도는 처음부터, 저장된 진도는 그대로
        assertEquals(0, s.progress("vincent", c.id, l.id).shown)
        assertFalse(s.progress("vincent", c.id, l.id).done)
        assertTrue(s.store.progress(k).done)
        s.walkLetter(room)
        assertEquals(Route.Done(room), s.route)
        assertTrue(s.store.progress(k).done)
        assertEquals(l.messages.size, s.store.progress(k).shown)
        assertEquals(balance, s.wallet("vincent").balance)   // 다시 하는 편지로는 화폐를 세지 않는다
        s.graduate()
        assertTrue(s.tutorialDone)
        assertTrue(s.progress("vincent", c.id, l.id).done)
        assertEquals(done, s.doneCount("vincent"))
        assertFalse(s.guideDue("match"))
    }
}
