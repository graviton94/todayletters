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
import io.github.graviton94.todayletters.data.DownloadJob
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
        // 처음 시작 세 가지는 ScreensTest.startFlow 에서: 여기서는 이미 쓰던 사람으로
        Store(ctx).startLegacy(true)
        if (onboarded) Store(ctx).onboarded = true
        if (seen) { listOf("vincent", "mozart").forEach { Store(ctx).markLearnChosen(it); Store(ctx).markPrologueSeen(it) }; Store(ctx).tutorialDone = true }
        // 편지 전 예열 (ui/WarmUp.kt) 없이: 편지를 열면 바로 편지 방
        listOf("vincent", "mozart", "napoleon").forEach { ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().putInt("s:$it:warm", 0).commit() }
        return AppState(ctx, deepLink = deepLink)
    }

    /** 편지 방의 걸음을 차례로: [skip] 에 든 걸음은 건너뛰고 나머지는 한다. 마지막 걸음 뒤에는 편지 완료 화면. */
    private fun AppState.walkLetter(room: Route.Letter, skip: Set<io.github.graviton94.todayletters.core.ReplyMode> = emptySet()) {
        val (c, l) = letterOf(room)
        save(room.series, c, l.id, progress(room.series, c, l.id).copy(shown = l.messages.size))
        letterModes(room.series, c, l).forEach { m ->
            // 걸음을 마치면 바로 다음 걸음 화면이 열린다 (stepEnded): 이미 그 걸음이면 다시 열지 않는다
            if (route != Route.Play(room, m)) go(Route.Play(room, m))
            if (m in skip) skipStep(room, m) else replyAndContinue(room, m)
        }
    }

    /**
     * 걸음을 마치면 편지 방으로 돌아가지 않고 바로 다음 걸음 (사용자 요청 2026-10-24). 방은 뒤에 남아 걸음 화면을 닫으면 편지로,
     * 마지막 걸음 뒤에는 편지 완료. 마친 편지에서 다시 한 걸음은 들어온 곳 (방) 으로.
     */
    @Test fun stepGoesStraightToNextStep() {
        val s = state()
        s.enter("vincent")
        val room = Route.Letter("vincent", 1, 1, Route.Inbox)
        val (c, l) = s.letterOf(room)
        s.open("vincent", c, l, Route.Inbox, skipAudio = true)
        assertEquals(room, s.route)
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = l.messages.size))
        val modes = s.letterModes("vincent", c, l)
        assertTrue(modes.size >= 2)
        s.go(Route.Play(room, modes[0]))
        s.replyAndContinue(room, modes[0])
        assertEquals(Route.Play(room, modes[1]), s.route)
        // 걸음 화면을 닫으면 편지 방 (편지로 돌아갈 수 있다)
        s.backTo(room)
        s.go(Route.Play(room, modes[1]))
        modes.drop(1).forEach { m -> if (s.route != Route.Play(room, m)) s.go(Route.Play(room, m)); s.skipStep(room, m) }
        assertEquals(Route.Done(room), s.route)
        s.reward = null; s.levelUp = null
        s.backTo(room)
        // 마친 편지에서 건너뛴 걸음을 다시 하면: 그 걸음만 하고 방으로
        s.go(Route.Play(room, modes[1]))
        s.replyAndContinue(room, modes[1])
        assertEquals(room, s.route)
    }

    /**
     * ‘다음 편지 열기’ (사용자 보고 2026-10-24): 앞서 읽다 둔 편지 (끝까지 읽고 걸음은 남김) 가 있어도 방금 마친 편지 뒤의 새 편지를 연다
     * (예전에는 앞쪽 첫 안 마친 편지라 그 편지가 끝 부분부터 열렸다). 새 편지는 첫 메시지부터 (도착 장면).
     */
    @Test fun nextLetterOpensAtStart() {
        val s = state()
        s.enter("vincent")
        val rooms = (1..3).map { Route.Letter("vincent", 1, it, Route.Inbox) }
        val (c, l1) = s.letterOf(rooms[0]); val l2 = s.letterOf(rooms[1]).second; val l3 = s.letterOf(rooms[2]).second
        // 편지 I: 끝까지 읽고 걸음은 남김
        s.open("vincent", c, l1, Route.Inbox, skipAudio = true)
        s.save("vincent", c, l1.id, s.progress("vincent", c, l1.id).copy(shown = l1.messages.size))
        s.backTo(Route.Inbox)
        // 편지 II: 끝까지 마침
        s.open("vincent", c, l2, Route.Inbox, skipAudio = true)
        s.walkLetter(rooms[1])
        assertEquals(Route.Done(rooms[1]), s.route)
        s.reward = null; s.levelUp = null
        // 편지 III 이 와 있게 (오늘 받을 몫이 끝났으면 하나 더)
        if (s.openable("vincent").size < 3) s.store.markStarted(s.key("vincent", c, l3.id))
        assertEquals(c to l3, s.nextLetter(rooms[1]))
        val (nc, nl) = s.nextLetter(rooms[1])!!
        s.open("vincent", nc, nl, Route.Inbox)
        assertEquals(rooms[2], s.route)
        assertEquals(0, s.progress("vincent", c, l3.id).shown)
        assertFalse(s.progress("vincent", c, l3.id).done)
        // 뒤에 안 마친 편지가 없으면 앞서 읽다 둔 편지 (이어 읽기)
        s.save("vincent", c, l3.id, s.progress("vincent", c, l3.id).copy(shown = l3.messages.size, done = true))
        assertEquals(c to l1, s.nextLetter(rooms[1]))
    }

    private fun AppState.backTo(r: Route) { assertTrue(back()); assertEquals(r, route) }

    /** 앱을 켜면 마지막으로 읽던 작품의 오늘 (뒤로 가면 서재). 작품에 들어가기만 하고 한 통도 안 열었으면 서재부터. */
    @Test fun startOpensLastSeriesToday() {
        val s = state()
        s.enter("vincent")
        assertEquals(Route.Library, AppState(ctx).route)
        val room = Route.Letter("vincent", 1, 1)
        val (c, l) = s.letterOf(room)
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = 1))
        val again = AppState(ctx)
        assertEquals("vincent", again.current)
        assertEquals(listOf(Route.Library, Route.Inbox), again.stack.toList())
        again.backTo(Route.Library)
    }

    /** 처음 쓰는 사람은 서재부터 (읽던 기록이 있어도 소개를 마치기 전에는). */
    @Test fun firstRunStartsAtLibrary() {
        val s = state(onboarded = false)
        assertEquals(Route.Library, s.route)
    }

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
        // 모차르트는 앱 안 장이 없다: 첫 편지 앞에서 처음 묶음 (서문 + 무료 장) 받기를 묻고, 다 받으면 그 편지로
        assertEquals(io.github.graviton94.todayletters.data.Downloads.FIRST, s.downloadAsk?.chapter)
        assertEquals(listOf("P", "I", "II"), s.downloads.firstChapters("mozart"))
        assertEquals(Route.Inbox, s.route)
        s.downloadFinished(silent = false)
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
        // 오늘의 여정: 편지는 마침. 편지 낱말 카드는 내일부터지만, 편지를 연 날부터 새 기초 낱말 (2~3개) 이 오늘의 복습에 온다
        // (기초 낱말 덱, 2026-10). 그래서 첫날도 복습 걸음이 있고, 그것까지 하면 여정 완료 · 보너스
        val j = s.journey("vincent")
        assertEquals(io.github.graviton94.todayletters.core.Journey.Step.DONE, j.letter)
        assertEquals(io.github.graviton94.todayletters.core.Journey.Step.TODO, j.review)
        assertFalse(j.paid)
        val first = s.startReview("vincent")
        assertTrue(first.isNotEmpty() && first.all { io.github.graviton94.todayletters.core.CardRef.parse(it.key)?.chapter == io.github.graviton94.todayletters.core.Basics.CHAPTER })
        s.reviewDone()
        assertEquals(io.github.graviton94.todayletters.core.Journey.Step.DONE, s.journey("vincent").review)
        assertTrue(s.journey("vincent").paid)
        assertEquals(Route.Done(room), s.route)
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

    /** 상점 (넷째 이름표) 맨 아래 금테 배너 ‘모든 편지 열기’ → 구매 화면, 뒤로 가면 상점. Play 상품이 없으면 살 수 없다 (곧 열려요). */
    @Test fun shopLeadsToPurchase() {
        val s = state()
        s.enter("vincent")
        s.go(Route.Shop)
        assertEquals(listOf(Route.Library, Route.Shop), s.stack.toList())
        rule.setContent { TodayLettersTheme(ThemeMode.LIGHT) { io.github.graviton94.todayletters.ui.ShopTab(s) } }
        rule.onNodeWithText(ctx.getString(R.string.unlock_all)).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(Route.Purchase, s.route)
        assertFalse(s.billing.available(io.github.graviton94.todayletters.core.Plan.Yearly))
        s.backTo(Route.Shop)
        s.backTo(Route.Library)
    }

    /** 상점 그림 (v5): 소장품이 없는 시리즈 (모차르트) 에서는 ‘갤러리 칸 열기’ 줄이 없고, 고흐에는 있다. */
    @Test fun shopHidesPickWithoutCollection() {
        val s = state()
        s.enter("mozart")
        assertTrue(s.currentWork.kit.collection.isEmpty())
        rule.setContent { TodayLettersTheme(ThemeMode.LIGHT) { io.github.graviton94.todayletters.ui.ShopTab(s) } }
        rule.onNodeWithText(ctx.getString(R.string.spend_pick)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.spend_rest)).assertExists()
    }

    /** 무료는 프롤로그 + I · II장. III장의 편지를 열면 구매 화면, 뒤로 가면 들어온 곳. 소장하면 (저장된 이용권) 열린다. */
    @Test fun lockedChapterLeadsToPurchase() {
        val s = state()
        s.enter("vincent")
        val w = s.work("vincent")
        assertTrue(s.chapterOpen(w.chapters[0])); assertTrue(s.chapterOpen(w.chapters[1])); assertFalse(s.chapterOpen(w.chapters[2]))
        s.open("vincent", w.chapters[2].id, w.chapters[2].letters[0], Route.Inbox, skipAudio = true)
        assertEquals(Route.Purchase, s.route)
        s.backTo(Route.Inbox)
        // II장은 무료: 편지가 열린다
        s.open("vincent", w.chapters[1].id, w.chapters[1].letters[0], Route.Inbox, skipAudio = true)
        assertEquals(Route.Letter("vincent", 2, 1, Route.Inbox), s.route)
        // 소장 (기기에 저장된 이용권): 다시 켜도 그대로, 모차르트 III장은 여전히 잠김
        s.store.entitlement = io.github.graviton94.todayletters.core.Entitlement.of(listOf("series_vincent"))
        val t = AppState(ctx)
        assertTrue(t.chapterOpen(t.work("vincent").chapters[2]))
        assertFalse(t.chapterOpen(t.work("mozart").chapters[2]))
        t.enter("vincent")
        t.open("vincent", w.chapters[2].id, w.chapters[2].letters[0], Route.Inbox, skipAudio = true)
        assertEquals(Route.Letter("vincent", 3, 1, Route.Inbox), t.route)
    }

    /** 목차의 잠긴 장 (III장부터) 을 누르면 구매 화면. */
    @Test fun lockedChapterRowLeadsToPurchase() {
        val s = state()
        s.enter("vincent")
        s.go(Route.Series("vincent"))
        rule.setContent { TodayLettersTheme(ThemeMode.LIGHT) { io.github.graviton94.todayletters.ui.SeriesCover(s, "vincent") } }
        rule.onNodeWithText(io.github.graviton94.todayletters.core.Breaks.keepAll(s.work("vincent").chapters[2].title[io.github.graviton94.todayletters.core.Lang.FR])).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(Route.Purchase, s.route)
        s.backTo(Route.Series("vincent"))
    }

    /** 나의 기록의 이용권 줄 → 구매 화면, 뒤로 가면 나의 기록. */
    @Test fun recordLeadsToPurchase() {
        val s = state()
        s.go(Route.Record)
        rule.setContent { TodayLettersTheme(ThemeMode.LIGHT) { io.github.graviton94.todayletters.ui.RecordScreen(s) } }
        rule.onNodeWithText(ctx.getString(R.string.unlock_all), substring = true).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(Route.Purchase, s.route)
        s.backTo(Route.Record)
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

    /** 설정 › 의견 보내기: 시트가 열리고 (오류 · 제안 · 기타, 함께 보내기 미리 보기), 뒤로 가면 닫히고 설정은 그대로. */
    @Test fun feedbackSheetFromSettings() {
        val s = state()
        s.settingsOpen = true
        rule.setContent {
            TodayLettersTheme(ThemeMode.LIGHT) {
                io.github.graviton94.todayletters.ui.SettingsScreen(s)
                s.feedback?.let { io.github.graviton94.todayletters.ui.FeedbackSheet(s, it) }
            }
        }
        rule.onNodeWithText(ctx.getString(R.string.set_feedback)).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(io.github.graviton94.todayletters.core.Feedback.Kind.BUG, s.feedback?.kind)
        rule.onNodeWithText(ctx.getString(R.string.fb_idea)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.fb_attach)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.fb_send)).assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(null, s.feedback)
        assertTrue(s.settingsOpen)
    }

    /** 앱이 멈춘 기록이 있으면 다음에 열 때 한 번만 묻는다. ‘보내기’ 는 의견 보내기 (오류) 로, 다시 열면 묻지 않는다. */
    @Test fun crashAskOnceThenFeedback() {
        ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(state().crashAsk)
        io.github.graviton94.todayletters.data.CrashLog.record(ctx, Thread.currentThread(), IllegalStateException("boom"))
        assertEquals(1, io.github.graviton94.todayletters.data.CrashLog.count(ctx))
        assertTrue(io.github.graviton94.todayletters.data.CrashLog.last(ctx)!!.contains("IllegalStateException: boom"))
        val s = state()
        assertTrue(s.crashAsk)
        rule.setContent { TodayLettersTheme(ThemeMode.LIGHT) { io.github.graviton94.todayletters.ui.CrashAsk(s) } }
        rule.onNodeWithText(ctx.getString(R.string.crash_t)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.crash_send)).performClick()
        rule.waitForIdle()
        assertFalse(s.crashAsk)
        assertEquals(io.github.graviton94.todayletters.core.Feedback.Kind.BUG, s.feedback?.kind)
        assertFalse(state().crashAsk)
        // 괜찮아요도 한 번으로 끝
        io.github.graviton94.todayletters.data.CrashLog.record(ctx, Thread.currentThread(), RuntimeException("again"))
        val t = state()
        assertTrue(t.crashAsk)
        t.answerCrash(send = false)
        assertEquals(null, t.feedback)
        assertFalse(state().crashAsk)
        io.github.graviton94.todayletters.data.CrashLog.clear(ctx)
        assertEquals(0, io.github.graviton94.todayletters.data.CrashLog.count(ctx))
    }

    /**
     * 미리 받기: 받는 중이면 시트는 진행 (23 / 64MB) 과 ‘멈추기’. 멈추면 받은 데부터 다시 받기. 시트를 닫아도 받기 상태는 남는다 (알림 · 서비스가 이어서).
     */
    /** 앱 안 장이 없는 작품의 서문 · 맺음은 받기 시트를 먼저 (다 받으면 그 화면으로). 빈센트 서문은 앱 안이라 바로 열린다. */
    @Test fun prologueAndOutroAskBeforeOpening() {
        val s = state()
        s.openPrologue("vincent")
        assertEquals(Route.Prologue("vincent"), s.route)
        assertEquals(null, s.downloadAsk)
        if (s.works.none { it.series.id == "mozart" }) return
        s.openPrologue("mozart")
        assertEquals(io.github.graviton94.todayletters.data.Downloads.FIRST, s.downloadAsk?.chapter)
        assertEquals(Route.Prologue("mozart"), s.downloadAsk?.then)
        s.downloadLater()
        s.openOutro("vincent")
        assertEquals("E", s.downloadAsk?.chapter)
        assertEquals(Route.Outro("vincent"), s.downloadAsk?.then)
        s.downloadFinished(silent = false)
        assertEquals(Route.Outro("vincent"), s.route)
    }

    @Test fun downloadSheetStopAndResume() {
        val s = state()
        val ch = s.work("vincent").chapters[1]
        val pack = s.downloads.pack("vincent", ch.id) ?: return
        DownloadJob.show(DownloadJob.State("vincent", ch.id, "Vincent", 23_000_000, 64_000_000, DownloadJob.Phase.RUNNING))
        s.downloadAsk = AppState.DownloadAsk(pack, "vincent", ch.id, null, null)
        rule.setContent { TodayLettersTheme(ThemeMode.LIGHT) { s.downloadAsk?.let { io.github.graviton94.todayletters.ui.DownloadSheet(s, it) } } }
        rule.onNodeWithText("23.0 / 64MB", substring = true).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.dl_pause)).performClick()
        rule.waitForIdle()
        assertEquals(DownloadJob.Phase.STOPPED, DownloadJob.state?.phase)
        rule.onNodeWithText(ctx.getString(R.string.dl_go, io.github.graviton94.todayletters.ui.sizeLabel(s.downloads.remaining(pack)))).assertExists()
        // 뒤로: 시트만 닫힌다
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(null, s.downloadAsk)
        assertEquals(DownloadJob.Phase.STOPPED, DownloadJob.state?.phase)
        DownloadJob.show(null)
    }
}
