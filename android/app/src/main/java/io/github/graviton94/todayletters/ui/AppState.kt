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

    /** 마지막 이동이 앞으로였나 (화면 전환 방향). */
    var forward by mutableStateOf(true)
        private set

    fun go(r: Route) {
        forward = r !is Route.Tab || stack.size > 1 || (stack.lastOrNull() as? Route.Tab)?.let { tabIndex(it) < tabIndex(r) } ?: true
        if (r is Route.Tab) { stack.clear(); stack.addAll(Nav.trail(r)) } else stack.add(r)
    }

    private fun tabIndex(r: Route) = listOf(Route.Inbox, Route.Library, Route.Words, Route.Gallery).indexOf(r)

    /** 앱을 닫을지 묻는 중. */
    var askingExit by mutableStateOf(false)

    /** 뒤로: 스택을 하나 내리고, 비면 계층의 부모로. 맨 위(편지함)면 false (앱을 나감). */
    fun back(): Boolean {
        narrator.stop()
        forward = false
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

    private fun all(id: String) = work(id).chapters.flatMap { c -> c.letters.map { c.id to it } }

    /** 한 번이라도 연 편지 수 (다 읽은 것 포함). */
    fun startedCount(id: String) = all(id).count { (c, l) -> store.started(key(id, c, l.id)) || progress(id, c, l.id).let { it.shown > 0 || it.done } }

    fun openable(id: String): List<Pair<String, Letter>> {
        val all = all(id)
        val n = Arrivals.openable(all.size, startedCount(id), store.openedToday(id), seriesSettings(id).lettersPerDay)
        return all.take(n)
    }

    fun waiting(id: String): Int =
        Arrivals.waiting(all(id).size, doneCount(id), startedCount(id), store.openedToday(id), seriesSettings(id).lettersPerDay)

    /** 이 편지가 며칠 뒤에 오는가 (0 이면 이미 왔다). */
    fun daysUntil(id: String, letterId: String): Int {
        val idx = all(id).indexOfFirst { it.second.id == letterId }
        return Arrivals.daysUntil(idx, openable(id).size, seriesSettings(id).lettersPerDay)
    }

    // ── 복습 (낱말 카드) ─────────────────────────────────────
    val today: Long get() = LocalDate.now().toEpochDay()

    /** 낱말 카드 키 "작품:챕터:편지:낱말번호". */
    fun cardKey(series: String, chapter: String, letter: String, word: Int) = "$series:$chapter:$letter:$word"

    /** 카드 키 → (작품, 챕터, 편지, 낱말). 없어진 낱말이면 null. */
    fun cardWord(key: String): Triple<Work, Pair<String, Letter>, Int>? {
        val (sid, ch, lid, wi) = key.split(":").takeIf { it.size == 4 } ?: return null
        val w = works.firstOrNull { it.series.id == sid } ?: return null
        val c = w.chapters.firstOrNull { it.id == ch } ?: return null
        val l = c.letters.firstOrNull { it.id == lid } ?: return null
        val i = wi.toIntOrNull()?.takeIf { it in l.words.indices } ?: return null
        return Triple(w, ch to l, i)
    }

    /** 편지를 다 읽으면 그 편지의 낱말이 카드함에 들어간다 (내일부터 복습). */
    fun collectWords(series: String, chapter: String, letter: Letter) {
        letter.words.indices.forEach { i ->
            val k = cardKey(series, chapter, letter.id, i)
            if (!store.hasCard(k)) store.save(io.github.graviton94.todayletters.core.Memory.added(k, today))
        }
        version++
    }

    fun cards() = run { @Suppress("UNUSED_EXPRESSION") version; store.cards().filter { cardWord(it.key) != null } }
    fun dueCards() = io.github.graviton94.todayletters.core.Memory.dueToday(cards(), today)
    fun answer(c: io.github.graviton94.todayletters.core.Card, correct: Boolean) {
        store.save(io.github.graviton94.todayletters.core.Memory.after(c, correct, today)); version++
    }
    fun reviewDone() { store.reviewedDay = today; version++ }
    val reviewedToday: Boolean get() = run { @Suppress("UNUSED_EXPRESSION") version; store.reviewedDay == today }

    // ── 내 구절 ──────────────────────────────────────────────
    fun quoteKey(series: String, chapter: String, letter: String, msg: Int) = "$series:$chapter:$letter:$msg"
    fun isSaved(k: String) = run { @Suppress("UNUSED_EXPRESSION") version; k in store.quotes }
    fun toggleQuote(k: String) { store.quotes = if (k in store.quotes) store.quotes - k else listOf(k) + store.quotes; version++ }

    /** 이번 주(월~일) 중 편지를 끝낸 요일 (0=월). */
    fun weekDone(): Set<Int> {
        val now = LocalDate.now(); val monday = now.minusDays((now.dayOfWeek.value - 1).toLong()).toEpochDay()
        return store.doneDays().filter { it in monday..(monday + 6) }.map { (it - monday).toInt() }.toSet()
    }

    /** 편지를 끝냈을 때: 이어 읽은 날을 센다. */
    fun finished(): Int {
        val today = LocalDate.now().toEpochDay()
        store.markDoneDay(today)
        val n = io.github.graviton94.todayletters.core.Streak.after(store.streakDay, store.streakCount, today)
        store.saveStreak(today, n)
        runCatching { io.github.graviton94.todayletters.data.TodayWidget.refresh(ctx) }
        return n
    }
    val streak: Int get() = if (LocalDate.now().toEpochDay() - store.streakDay <= 1) store.streakCount else 0

    /** 새 편지를 처음 열 때 한 번 센다 (하루 편지 수). */
    fun open(id: String, chapter: String, letter: Letter, from: Route.Tab) {
        val p = progress(id, chapter, letter.id)
        if (!store.started(key(id, chapter, letter.id)) && p.shown == 0 && !p.done) store.markOpened(id)
        store.markStarted(key(id, chapter, letter.id))
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
