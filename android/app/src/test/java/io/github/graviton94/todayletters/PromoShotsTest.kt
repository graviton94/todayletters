package io.github.graviton94.todayletters

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.ThemeMode
import io.github.graviton94.todayletters.core.TypingPace
import io.github.graviton94.todayletters.data.Store
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.TodayLettersTheme
import io.github.graviton94.todayletters.ui.AppState
import io.github.graviton94.todayletters.ui.LibraryTab
import io.github.graviton94.todayletters.ui.Play
import io.github.graviton94.todayletters.ui.Room
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 홍보 영상 (promo-video.yml · scripts/promo/make.py) 에 쓰는 화면 사진. 앱에는 들어가지 않는다.
 * 한국어 화면 (ko) 과 영어 화면 (en) 을 같은 장면으로 찍는다: build/screens/promo_<장면>_<ko|en>.png
 * 진짜 데이터 그대로 (빈센트 I장 첫 편지) — 시험용으로 끼운 글은 없다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w393dp-h852dp-xxhdpi")
class PromoShotsTest {
    @get:Rule val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun state(read: Lang): AppState {
        ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().clear().commit()
        Store(ctx).startLegacy(true)
        Store(ctx).onboarded = true
        Store(ctx).tutorialDone = true
        Store(ctx).notifyAsked = true
        listOf("vincent", "mozart", "napoleon").forEach { ctx.getSharedPreferences("letters", Context.MODE_PRIVATE).edit().putInt("s:$it:warm", 0).commit() }
        val s = AppState(ctx, deepLink = false)
        s.update(s.app.copy(pace = TypingPace.INSTANT, read = read))
        s.works.forEach { w -> w.chapters.forEach { c -> c.letters.forEach { l -> s.store.markArrived(s.key(w.series.id, c.id, l.id)) } } }
        return s
    }

    private fun shot(name: String, dark: Boolean, ms: Long = 3000, series: AppState? = null, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            TodayLettersTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                if (series != null) io.github.graviton94.todayletters.ui.InSeries(series.currentWork) { Box(Modifier.fillMaxSize().background(Ink.palette.paper)) { content() } }
                else Box(Modifier.fillMaxSize().background(Ink.palette.paper)) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(ms)
        rule.mainClock.advanceTimeBy(900)
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    /** 나폴레옹은 처음 묶음을 받아 둔 것으로 (저장소의 remote/ 에서 files/ 로): 서재에 세 작품. */
    private fun fetchedLocally(series: String, chapters: List<String>) {
        val remote = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.map { java.io.File(it, "remote") }.firstOrNull { it.isDirectory } ?: return
        val d = io.github.graviton94.todayletters.data.Downloads(ctx)
        chapters.mapNotNull { d.pack(series, it) }.flatMap { it.parts }.forEach { p ->
            val src = java.io.File(remote, p.path); val out = java.io.File(ctx.filesDir, p.path)
            if (src.isFile && !out.isFile) { out.parentFile?.mkdirs(); src.copyTo(out) }
        }
        io.github.graviton94.todayletters.data.AssetFiles.changed()
    }

    private fun lang(en: Boolean) = if (en) "en" else "ko"

    // 1 · 편지가 도착하는 화면 (그림 → 첫 줄이 쓰인 뒤)
    private fun arrival(en: Boolean) {
        val s = state(if (en) Lang.EN else Lang.KO)
        val l = s.work("vincent").chapters.first().letters.first()
        shot("promo_arrival_${lang(en)}", false, series = s) { io.github.graviton94.todayletters.ui.ArrivalBody(s, "vincent", "I", l, 1, { }, startAt = 2) }
    }
    @Test fun arrivalKo() = arrival(false)
    @Test @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") fun arrivalEn() = arrival(true)

    // 2 · 편지 방: 낱말 아래 뜻 (편지 I-1 의 첫 두 문장까지 도착한 것으로: 끝의 그림 · 노트가 아니라 문장이 화면에)
    private fun gloss(en: Boolean) {
        val s = state(if (en) Lang.EN else Lang.KO)
        val r = Route.Letter("vincent", 1, 1)
        val (c, l) = s.letterOf(r)
        s.save("vincent", c, l.id, s.progress("vincent", c, l.id).copy(shown = 2))
        shot("promo_gloss_${lang(en)}", false, 4000, series = s) { Room(s, r) }
    }
    @Test fun glossKo() = gloss(false)
    @Test @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") fun glossEn() = gloss(true)

    // 3 · 오늘의 회화 (A1, 메신저 화면)
    private fun talk(en: Boolean) {
        val s = state(if (en) Lang.EN else Lang.KO)
        s.store.setGrade(s.seriesSettings("vincent").learn.code, 1); s.bump()
        shot("promo_talk_${lang(en)}", false, 4000, series = s) { Play(s, Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.TALK)) }
    }
    @Test fun talkKo() = talk(false)
    @Test @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") fun talkEn() = talk(true)

    // 4 · 서재: 세 작품
    private fun library(en: Boolean) {
        val s = state(if (en) Lang.EN else Lang.KO).also { fetchedLocally("napoleon", listOf("P", "I", "II")) }
        shot("promo_library_${lang(en)}", true, 3000) { LibraryTab(s) }
    }
    @Test fun libraryKo() = library(false)
    @Test @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi") fun libraryEn() = library(true)
}
