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
import io.github.graviton94.todayletters.core.Word
import io.github.graviton94.todayletters.core.Visitor
import io.github.graviton94.todayletters.core.StampArt
import io.github.graviton94.todayletters.core.ParcelReturn
import io.github.graviton94.todayletters.core.Parcel
import io.github.graviton94.todayletters.core.Memory
import io.github.graviton94.todayletters.core.Achievements
import io.github.graviton94.todayletters.core.Stats
import io.github.graviton94.todayletters.core.Growth
import io.github.graviton94.todayletters.core.Rewards
import io.github.graviton94.todayletters.core.Earn
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

    private var seenDay = LocalDate.now().toEpochDay()
    /** 앱으로 돌아올 때: 날짜가 바뀌었으면 화면을 오늘 기준으로 새로 그리고, 오늘의 봉투부터. */
    fun resumed() {
        val today = LocalDate.now().toEpochDay()
        if (today == seenDay) return
        seenDay = today
        version++
        if (stage == Stage.MAIN && store.onboarded && store.openedDay != today) {
            stages = listOf(Stage.TODAY, Stage.MAIN)
            stack.clear(); stack.add(Route.Inbox)
        }
    }
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

    /** 도착했지만 한 번도 열지 않은 편지 수 (서재의 붉은 숫자). */
    fun unread(id: String): Int = openable(id).count { (c, l) -> !store.started(key(id, c, l.id)) && progress(id, c, l.id).shown == 0 }

    /** 열지 않은 다음 편지가 도착한 날 (epoch day). */
    fun arrivedOn(id: String): Long? = store.lastOpen(id).let { (d, n) -> Arrivals.arrivedOn(d, n, seriesSettings(id).lettersPerDay) }

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
        store.save(io.github.graviton94.todayletters.core.Memory.after(c, correct, today))
        version++
    }
    fun reviewDone() { store.reviewedDay = today; version++; settle() }
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
        if (n > store.bestStreak) store.bestStreak = n
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

    /** 연습을 마치고 대화방으로. 이 연습으로 편지가 끝났으면 (처음 한 번) 이어 읽은 날을 세고 편지 완료 화면으로. */
    fun replyAndContinue(r: Route.Letter, mode: ReplyMode) {
        val (ch, l) = letterOf(r)
        val was = progress(r.series, ch, l.id).done
        replied(r, mode)
        back()
        if (!was && progress(r.series, ch, l.id).done) { finished(); earn(Earn.LETTER); go(Route.Done(r)) }
        settle()
    }

    // ── 보상 엔진 (모든 시리즈 공통: 우표 · 혼자 읽기 · 업적 · 기념 우표 · 물건) ───────────
    /** 한 번에 보여 줄 보상 순간. */
    data class RewardMoment(
        val stamps: Int,
        val items: List<Word>,
        val achievements: List<String>,
        val gifts: List<StampArt>,
        val milestone: Int?,
    )
    var reward by mutableStateOf<RewardMoment?>(null)
    private var pendingStamps = 0
    var wallet by mutableStateOf(store.wallet)
        private set

    fun earn(e: Earn, times: Int = 1): Int {
        val (w, g) = Rewards.earn(store.wallet, e, today, times)
        store.wallet = w; wallet = w; pendingStamps += g
        return g
    }
    fun spend(cost: Int): Boolean {
        val w = Rewards.spend(store.wallet, cost) ?: return false
        store.wallet = w; wallet = w; version++; return true
    }

    /** 받은(연) 편지들의 낱말 카드 키 — 모든 작품. */
    fun arrivedWordKeys(): List<String> = works.flatMap { w ->
        val id = w.series.id
        w.chapters.flatMap { c -> c.letters.filter { l -> progress(id, c.id, l.id).let { it.done || it.shown > 0 } }
            .flatMap { l -> l.words.indices.map { cardKey(id, c.id, l.id, it) } } }
    }
    fun cardMap() = cards().associateBy { it.key }
    val readAlone: Int get() = run { @Suppress("UNUSED_EXPRESSION") version; Growth.readAlone(arrivedWordKeys(), cardMap()) }

    /** 편지 한 통을 혼자 읽을 수 있는가. */
    fun letterAlone(id: String, chapter: String, l: Letter): Boolean =
        Growth.letterAlone(l.words.indices.map { cardKey(id, chapter, l.id, it) }, cardMap())

    /** 편지 한 통의 혼자 읽기: (혼자 읽는 낱말 수, 낱말 수, 그 편지 낱말이 다음에 복습으로 오는 날까지 며칠 · 없으면 null). */
    data class LetterGrowth(val known: Int, val total: Int, val nextDue: Int?) {
        val pct get() = if (total == 0) 0 else known * 100 / total
    }
    fun letterGrowth(id: String, chapter: String, l: Letter): LetterGrowth {
        val cm = cardMap()
        val cs = l.words.indices.mapNotNull { cm[cardKey(id, chapter, l.id, it)] }
        val next = cs.filter { !Growth.known(it) }.minOfOrNull { it.due }?.let { (it - today).toInt().coerceAtLeast(0) }
        return LetterGrowth(cs.count { Growth.known(it) }, l.words.size, next)
    }

    /**
     * 오늘 화면의 주인공 편지: 읽는 중인 편지가 없을 때, 다 읽었지만 아직 혼자 다 읽지 못하는 가장 최근 편지.
     * (북극성 = ‘이 편지를 얼마나 혼자 읽나’)
     */
    fun focusLetter(id: String): Pair<String, Letter>? =
        openable(id).lastOrNull { (c, l) -> progress(id, c, l.id).done && !letterAlone(id, c, l) }

    fun stats(): Stats {
        val cm = cardMap()
        var done = 0; var alone = 0; var chapters = 0
        works.forEach { w -> w.chapters.forEach { c ->
            val ds = c.letters.count { progress(w.series.id, c.id, it.id).done }
            done += ds; if (ds == c.letters.size && ds > 0) chapters++
            alone += c.letters.count { l -> progress(w.series.id, c.id, l.id).done && Growth.letterAlone(l.words.indices.map { cardKey(w.series.id, c.id, l.id, it) }, cm) }
        } }
        return Stats(
            lettersDone = done, streak = streak, bestStreak = maxOf(store.bestStreak, streak),
            wordsKnown = cm.values.count { Growth.known(it) }, wordsOwned = cm.values.count { it.box >= Memory.TOP },
            readAlone = readAlone, lettersAlone = alone, visitors = store.visitors,
            parcels = works.sumOf { store.parcels(it.series.id) }, chapters = chapters,
        )
    }

    /** 오늘의 일: 편지 · 복습 · 손님. 다 하면 보너스. */
    data class DayTask(val kind: String, val done: Boolean)
    fun dayTasks(): List<DayTask> {
        val w = works.firstOrNull() ?: return emptyList()
        val id = w.series.id
        return listOfNotNull(
            DayTask("letter", today in store.doneDays() || waiting(id) == 0),
            DayTask("review", reviewedToday || dueCards().isEmpty()),
            if (w.kit.visitors.isNotEmpty()) DayTask("visitor", store.visitorDay == today) else null,
        )
    }

    /** 오늘의 손님 (키트의 손님을 날마다 돌아가며). */
    fun visitorToday(): Pair<Work, Visitor>? {
        val w = works.firstOrNull { it.kit.visitors.isNotEmpty() } ?: return null
        return w to w.kit.visitors[(today % w.kit.visitors.size).toInt()]
    }
    fun visitorDone() { store.visitorDay = today; earn(Earn.VISITOR); version++; settle() }

    /** 정기 소포: 이번 달 (달력) 에 보냈는지 · 보낼 수 있는지. */
    val thisMonth: Int get() = LocalDate.now().let { Parcel.month(it.year, it.monthValue) }
    fun parcelSent(id: String) = store.parcelMonth(id) == thisMonth
    fun sendParcel(w: Work): ParcelReturn? {
        val kit = w.kit.parcel ?: return null
        if (parcelSent(w.series.id) || !spend(kit.cost)) return null
        val n = store.parcels(w.series.id)
        store.sendParcel(w.series.id, thisMonth)
        version++; settle()
        return kit.returns.getOrNull(n % kit.returns.size.coerceAtLeast(1))
    }

    /**
     * 정산: 혼자 읽기가 오른 만큼 · 새로 금빛이 된 물건 · 오늘의 일 보너스 · 새 업적(+기념 우표)을 모아
     * 한 번의 보상 순간으로 보여 준다. 아무 것도 없으면 조용히.
     */
    fun settle() {
        val cm = cardMap()
        // 편지 속 물건: 낱말이 떠올리기 단계에 오르면 금빛
        val items = mutableListOf<Word>()
        works.forEach { w -> w.chapters.forEach { c -> c.letters.forEach { l -> l.words.forEachIndexed { i, word ->
            if (word.icon.isEmpty()) return@forEachIndexed
            val k = cardKey(w.series.id, c.id, l.id, i)
            if (Growth.known(cm[k]) && k !in store.goldItems) { store.gild(k); items += word }
        } } } }
        // 혼자 읽기
        val now = readAlone
        val seen = store.readAloneSeen
        var milestone: Int? = null
        if (now > seen) {
            earn(Earn.READ_ALONE, now - seen)
            milestone = Growth.crossed(seen, now).lastOrNull()
            store.readAloneSeen = now
        }
        // 업적 → 기념 우표
        val got = Achievements.newly(stats(), store.achievements)
        val gifts = mutableListOf<StampArt>()
        got.forEach { a ->
            store.unlock(a.id); earn(Earn.ACHIEVEMENT)
            works.firstNotNullOfOrNull { w -> w.kit.stamps.firstOrNull { "${w.series.id}:${it.id}" !in store.stamps }?.let { w to it } }?.let { (w, st) ->
                store.addStamp("${w.series.id}:${st.id}"); gifts += st
            }
        }
        if (pendingStamps > 0 || items.isNotEmpty() || got.isNotEmpty()) {
            reward = RewardMoment(pendingStamps, items, got.map { it.id }, gifts, milestone)
        }
        pendingStamps = 0
        version++
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
