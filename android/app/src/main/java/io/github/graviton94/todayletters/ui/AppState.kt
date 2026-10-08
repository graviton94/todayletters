package io.github.graviton94.todayletters.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.graviton94.todayletters.core.AppSettings
import io.github.graviton94.todayletters.core.Arrivals
import io.github.graviton94.todayletters.core.Coach
import io.github.graviton94.todayletters.core.Langs
import io.github.graviton94.todayletters.core.Launch
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.LetterProgress
import io.github.graviton94.todayletters.core.Nav
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.RoomView
import io.github.graviton94.todayletters.core.SeriesSettings
import io.github.graviton94.todayletters.core.Stage
import io.github.graviton94.todayletters.data.Library
import io.github.graviton94.todayletters.data.Narrator
import io.github.graviton94.todayletters.data.Recorder
import io.github.graviton94.todayletters.data.Store
import io.github.graviton94.todayletters.data.Work
import java.time.LocalDate

/**
 * 앱 한 벌의 상태. 화면 이동은 [Route] 스택 하나 (core/Routes.kt 의 계층을 따름).
 * 저장은 [Store] (기기 안), 콘텐츠는 [Library] (assets).
 */
class AppState(val ctx: Context, deepLink: Boolean = false) {
    val store = Store(ctx)
    val works: List<Work> = Library.works(ctx)
    val narrator = Narrator(ctx)
    val recorder = Recorder(ctx)

    val reducedMotion: Boolean =
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    var app by mutableStateOf(if (store.hasApp()) store.app() else Langs.firstRun(ctx.resources.configuration.locales[0].language))
        private set

    /** 앱을 켰을 때 남은 단계 (오프닝 → 처음 소개 또는 오늘의 봉투 → 메인). */
    var stages by mutableStateOf(
        Launch.plan(
            firstRun = !store.onboarded,
            firstOfDay = store.openedDay != LocalDate.now().toEpochDay(),
            deepLink = deepLink,
        )
    )
        private set

    val stage get() = stages.first()
    fun nextStage() {
        if (stages.size > 1) stages = stages.drop(1)
        if (stage == Stage.MAIN) store.openedDay = LocalDate.now().toEpochDay()
    }

    /** 화면 스택. 맨 끝이 지금 화면. */
    val stack = mutableStateListOf<Route>(Route.Inbox)
    val route get() = stack.last()

    fun go(r: Route) {
        if (r is Route.Tab) { stack.clear(); stack.addAll(Nav.trail(r)) } else stack.add(r)
    }

    /** 뒤로: 스택을 하나 내리고, 비면 계층의 부모로. 맨 위(편지함)면 false (앱을 나감). */
    fun back(): Boolean {
        narrator.stop()
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        val up = Nav.up(route) ?: return false
        stack.clear(); stack.addAll(Nav.trail(up)); return true
    }

    fun update(a: AppSettings) {
        val moved = Langs.afterReadChange(a, works.associate { w -> w.series to seriesSettings(w.series.id) })
        moved.first.forEach { (s, v) -> store.save(s.id, v) }
        app = a; store.save(a); version++
    }

    /** 작품 설정이 바뀌었을 때 화면을 다시 그리게 하는 표시. */
    var version by mutableStateOf(0)
        private set

    fun seriesSettings(id: String): SeriesSettings =
        store.series(id) ?: Langs.start(works.first { it.series.id == id }.series, app).also { store.save(id, it) }

    fun update(id: String, s: SeriesSettings) { store.save(id, s); version++ }

    fun room(id: String) = RoomView.of(app, seriesSettings(id), reducedMotion)

    fun work(id: String) = works.first { it.series.id == id }

    fun key(series: String, chapter: String, letter: String) = "$series:$chapter:$letter"
    fun progress(series: String, chapter: String, letter: String): LetterProgress {
        @Suppress("UNUSED_EXPRESSION") version
        return store.progress(key(series, chapter, letter))
    }

    fun save(series: String, chapter: String, letter: String, p: LetterProgress) {
        store.save(key(series, chapter, letter), p); version++
    }

    /** 이 작품에서 다 읽은 편지 수와 지금 열 수 있는 편지 (순서대로). */
    fun doneCount(id: String) = work(id).chapters.flatMap { c -> c.letters.map { c.id to it } }
        .count { (c, l) -> progress(id, c, l.id).done }

    fun openable(id: String): List<Pair<String, Letter>> {
        val all = work(id).chapters.flatMap { c -> c.letters.map { c.id to it } }
        val done = doneCount(id)
        val n = Arrivals.openable(all.size, done, store.openedToday(id), seriesSettings(id).lettersPerDay)
        return all.take(n)
    }

    fun waiting(id: String): Int {
        val all = work(id).chapters.sumOf { it.letters.size }
        return Arrivals.waiting(all, doneCount(id), store.openedToday(id), seriesSettings(id).lettersPerDay)
    }

    /** 새 편지를 처음 열 때 한 번 센다 (하루 편지 수). */
    fun open(id: String, chapter: String, letter: Letter, from: Route.Tab) {
        val p = progress(id, chapter, letter.id)
        if (p.shown == 0 && !p.done) store.markOpened(id)
        val idx = work(id).chapters.indexOfFirst { it.id == chapter } + 1
        go(Route.Letter(id, idx, work(id).chapters[idx - 1].letters.indexOf(letter) + 1, from))
    }

    fun letterOf(r: Route.Letter): Pair<String, Letter> {
        val ch = work(r.series).chapters[r.chapter - 1]
        return ch.id to ch.letters[r.letter - 1]
    }

    fun replied(r: Route.Letter, mode: ReplyMode) {
        val (ch, l) = letterOf(r)
        save(r.series, ch, l.id, progress(r.series, ch, l.id).reply(mode).complete(seriesSettings(r.series).modes))
    }

    // 도움말 (?)
    val coach = Coach(store.coachSeen)
    var coachTick by mutableStateOf(0)
        private set
    fun coachDone(screen: String) { coach.done(screen); store.coachSeen = coach.seen; coachTick++ }
    fun coachAgain(screen: String) { coach.again(screen); store.coachSeen = coach.seen; coachTick++ }
    fun coachReset() { coach.reset(); store.coachSeen = coach.seen; coachTick++ }

    fun finishOnboarding(a: AppSettings, seriesId: String, s: SeriesSettings) {
        update(a); update(seriesId, s); store.onboarded = true; nextStage()
    }

    var settingsOpen by mutableStateOf(false)
}
