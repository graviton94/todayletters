package io.github.graviton94.todayletters.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.graviton94.todayletters.core.AppSettings
import io.github.graviton94.todayletters.core.Arrivals
import io.github.graviton94.todayletters.core.Coach
import io.github.graviton94.todayletters.core.Langs
import io.github.graviton94.todayletters.core.Launch
import io.github.graviton94.todayletters.core.Memory
import io.github.graviton94.todayletters.core.Achievements
import io.github.graviton94.todayletters.core.Achievement
import io.github.graviton94.todayletters.core.Draws
import io.github.graviton94.todayletters.core.Piece
import io.github.graviton94.todayletters.core.Tier
import io.github.graviton94.todayletters.core.Quest
import io.github.graviton94.todayletters.core.Quests
import io.github.graviton94.todayletters.core.Spend
import io.github.graviton94.todayletters.core.Streak
import io.github.graviton94.todayletters.core.Wallet
import io.github.graviton94.todayletters.core.ReviewKind
import io.github.graviton94.todayletters.core.Exhibition
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
class AppState(val ctx: Context, deepLink: Boolean = false, recreated: Boolean = false) {
    val store = Store(ctx)
    val works: List<Work> = Library.works(ctx)
    val narrator = Narrator(ctx)
    private val effects = io.github.graviton94.todayletters.data.Effects(ctx)
    /** 맞음 · 틀림 효과음 (설정 › 효과음이 켜져 있을 때만). */
    fun cue(ok: Boolean) { if (app.effects) runCatching { effects.play(ok) } }
    val recorder = Recorder(ctx)
    val downloads = io.github.graviton94.todayletters.data.Downloads(ctx)

    /** 앱 밖에 둔 장의 낭독을 받을지 묻는 중: 받고 나면 (또는 소리 없이 읽기를 고르면) 이 편지를 연다. */
    /** 낭독 받기 묻기. [letter] 가 있으면 받은 뒤 그 편지를 열고, 없으면 미리 받기 (받고 끝). */
    data class DownloadAsk(val pack: io.github.graviton94.todayletters.data.Downloads.Pack, val series: String, val chapter: String, val letter: Letter?, val from: Route.Tab?)
    var downloadAsk by mutableStateOf<DownloadAsk?>(null)
    /** 이번 실행에서 ‘소리 없이 읽기’를 고른 장 (다시 묻지 않음). */
    private val silentChapters = mutableSetOf<String>()

    val reducedMotion: Boolean =
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    var app by mutableStateOf(if (store.hasApp()) store.app() else Langs.firstRun(ctx.resources.configuration.locales[0].language))
        private set

    /** 앱을 켰을 때 남은 단계 (첫 화면 → 처음 소개 → 메인). */
    // 첫 화면(미술관 입구)은 앱을 켤 때마다 늘 (알림으로 열어도). 언어를 바꿔 화면을 다시 그릴 때만 건너뛴다.
    var stages by mutableStateOf(Launch.plan(
        firstRun = !store.onboarded,
        firstOfDay = true,
        deepLink = recreated,
    ))
        private set

    val stage get() = stages.first()

    private var seenDay = LocalDate.now().toEpochDay()
    /** 앱으로 돌아올 때: 날짜가 바뀌었으면 화면을 오늘 기준으로 새로 그린다. */
    fun resumed() {
        val today = LocalDate.now().toEpochDay()
        if (today == seenDay) return
        seenDay = today
        version++
    }
    fun nextStage() {
        if (stages.size > 1) stages = stages.drop(1)
        if (stage == Stage.MAIN) store.openedDay = LocalDate.now().toEpochDay()
    }

    /** 지금 들어가 있는 시리즈 (시리즈 안 화면은 이 시리즈를 본다). */
    var current by mutableStateOf(store.lastSeries.takeIf { id -> works.any { it.series.id == id } } ?: works.firstOrNull()?.series?.id.orEmpty())
        private set
    val currentWork: Work get() = work(current)

    /** 서재에서 고른 시리즈로 들어간다 (전환 화면의 ‘눌러서 들어가기’). */
    fun enter(id: String) {
        current = id; store.lastSeries = id
        forward = true
        stack.clear(); stack.addAll(Nav.trail(Route.Inbox))
    }

    /** 화면 스택. 맨 끝이 지금 화면. 알림으로 들어오면 그 시리즈의 오늘부터. */
    val stack = mutableStateListOf<Route>().apply { addAll(if (deepLink && store.onboarded) Nav.trail(Route.Inbox) else listOf(Route.Library)) }
    val route get() = stack.last()

    /** 마지막 이동이 앞으로였나 (화면 전환 방향). */
    var forward by mutableStateOf(true)
        private set

    fun go(r: Route) {
        forward = r != Route.Library && (r !is Route.Tab || stack.size > 2 || (stack.lastOrNull() as? Route.Tab)?.let { tabIndex(it) < tabIndex(r) } ?: true)
        when {
            r == Route.Library -> { narrator.stop(); stack.clear(); stack.add(r) }
            r is Route.Tab -> { stack.clear(); stack.addAll(Nav.trail(r)) }
            else -> stack.add(r)
        }
    }

    private fun tabIndex(r: Route) = listOf(Route.Inbox, Route.Words, Route.Gallery, Route.Milestones).indexOf(r)

    /** 앱을 닫을지 묻는 중. */
    var askingExit by mutableStateOf(false)

    /** 앱을 벗어난 횟수. 녹음 · 내 녹음 듣기 화면은 이 값이 바뀌면 그 자리에서 멈춘 것으로 그린다. */
    var away by mutableIntStateOf(0)
        private set
    /** 앱을 벗어날 때: 이어 듣는 편지 낭독(알림이 있는 것)만 두고, 짧은 소리 · 녹음 · 내 녹음 듣기는 멈춘다 (마이크를 뒤에서 켜 두지 않는다). */
    fun leftApp() {
        if (io.github.graviton94.todayletters.data.Playback.now == null) narrator.stop()
        pcm.stop(); recorder.stop(); recorder.stopPlaying()
        away++
    }

    /** 뒤로: 스택을 하나 내리고, 비면 계층의 부모로. 맨 위(서재)면 false (앱을 나감). */
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
        val n = Arrivals.openable(all.size, startedCount(id), store.openedToday(id), seriesSettings(id).lettersPerDay) +
            (if (store.earlyDay(id) == today) 1 else 0)
        return all.take(n.coerceAtMost(all.size))
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
        if (correct) earn(Earn.REVIEW_RIGHT)
        version++
    }
    /** 복습 문제 하나의 결과 (오늘의 할 일: 듣고 쓰기 연속 · 뜻 고르기). */
    private var answerRun = ReviewKind.MEANING to 0
    fun judged(kind: ReviewKind, ok: Boolean) {
        answerRun = if (ok) kind to (if (answerRun.first == kind) answerRun.second + 1 else 1) else kind to 0
        if (ok && kind == ReviewKind.DICTATION) questAtLeast(Quest.DICTATION_RUN, answerRun.second)
        if (ok && kind == ReviewKind.MEANING) questAdd(Quest.MEANING_RUN)
    }
    fun reviewDone() {
        store.reviewedDay = today; questAdd(Quest.REVIEW_DONE); finished()
        draw(current, "review")
        version++; settle()
    }
    val reviewedToday: Boolean get() = run { @Suppress("UNUSED_EXPRESSION") version; store.reviewedDay == today }

    // ── 내 구절 ──────────────────────────────────────────────
    fun quoteKey(series: String, chapter: String, letter: String, msg: Int) = "$series:$chapter:$letter:$msg"
    fun isSaved(k: String) = run { @Suppress("UNUSED_EXPRESSION") version; k in store.quotes }
    fun toggleQuote(k: String) { store.quotes = if (k in store.quotes) store.quotes - k else listOf(k) + store.quotes; version++ }

    /** 이번 주(월~일) 중 배운 날 (편지 · 복습 · 따라 읽기, 0=월). */
    fun weekDone(): Set<Int> {
        val monday = io.github.graviton94.todayletters.core.Recital.weekStart(today)
        return store.doneDays().filter { it in monday..(monday + 6) }.map { (it - monday).toInt() }.toSet()
    }

    /** 오늘 배웠다 (편지를 끝냈거나 복습 · 따라 읽기를 했다): 이어 읽은 날을 센다. 빈 날은 쉼표로 메운다. */
    fun finished(): Int {
        val first = today !in store.doneDays()
        store.markDoneDay(today)
        val (n, used) = Streak.afterWithRest(store.streakDay, store.streakCount, today, store.rests)
        if (used > 0) store.rests = (store.rests - used).coerceAtLeast(0)
        store.saveStreak(today, n)
        if (n > store.bestStreak) store.bestStreak = n
        if (first) earn(Earn.STREAK)
        runCatching { io.github.graviton94.todayletters.data.TodayWidget.refresh(ctx) }
        return n
    }
    val streak: Int get() = run { @Suppress("UNUSED_EXPRESSION") version; Streak.shown(store.streakDay, store.streakCount, today, store.rests) }
    val rests: Int get() = run { @Suppress("UNUSED_EXPRESSION") version; store.rests }

    /** 새 편지를 처음 열 때 한 번 센다 (하루 편지 수). */
    fun open(id: String, chapter: String, letter: Letter, from: Route.Tab?, skipAudio: Boolean = false) {
        // 앱 밖(R2)에 둔 장이고 아직 받지 않았으면: 먼저 크기를 알려 주고 받을지 묻는다
        val pack = downloads.pack(id, chapter)
        if (!skipAudio && pack != null && "$id:$chapter" !in silentChapters && !downloads.ready(pack)) {
            downloadAsk = DownloadAsk(pack, id, chapter, letter, from); return
        }
        val p = progress(id, chapter, letter.id)
        if (p.done) questAdd(Quest.REREAD)
        current = id
        if (!store.started(key(id, chapter, letter.id)) && p.shown == 0 && !p.done) store.markOpened(id)
        store.markStarted(key(id, chapter, letter.id))
        val idx = work(id).chapters.indexOfFirst { it.id == chapter } + 1
        go(Route.Letter(id, idx, work(id).chapters[idx - 1].letters.indexOf(letter) + 1, from))
    }

    /** 받기를 마쳤거나 소리 없이 읽기를 골랐을 때: 기다리던 편지를 연다. */
    fun downloadFinished(silent: Boolean) {
        val a = downloadAsk ?: return
        downloadAsk = null
        version++
        val l = a.letter ?: return
        if (silent) silentChapters += "${a.series}:${a.chapter}"
        open(a.series, a.chapter, l, a.from, skipAudio = true)
    }
    /**
     * 소장품이 연 편지의 그림이기도 하면 (영어 제목이 같으면) 그 그림 산책: (자리 수, 가기). 아니면 null.
     * 아직 안 연 편지의 그림은 잇지 않는다 (미리 보이지 않게).
     */
    fun plateWalk(series: String, pc: io.github.graviton94.todayletters.core.Piece): Pair<Int, () -> Unit>? {
        val key = pc.title[io.github.graviton94.todayletters.core.Lang.EN].trim().lowercase().takeIf { it.isNotEmpty() } ?: return null
        work(series).chapters.forEachIndexed { ci, c ->
            c.letters.forEachIndexed { li, l ->
                val pl = l.plate ?: return@forEachIndexed
                if (pl.title[io.github.graviton94.todayletters.core.Lang.EN].trim().lowercase() != key || pl.spots.isEmpty()) return@forEachIndexed
                val pr = progress(series, c.id, l.id)
                if (pr.shown == 0 && !pr.done) return@forEachIndexed
                return pl.spots.size to { go(Route.Artwork(series, li + 1, Route.Letter(series, ci + 1, li + 1, Route.Gallery))) }
            }
        }
        return null
    }
    /** 갑자기 한 문제 (F7): 오늘 아직 안 냈으면, 오래전 ‘내 것’ 낱말 하나. */
    fun surprise(): io.github.graviton94.todayletters.core.Card? =
        if (store.surpriseDay == today) null else io.github.graviton94.todayletters.core.Memory.surprise(cards(), today)
    /** 기억났는가: 맞으면 일정은 그대로 + 우표 하나, 아니면 내려가서 내일 다시. 하루 한 번. */
    fun surpriseDone(c: io.github.graviton94.todayletters.core.Card, ok: Boolean) {
        store.save(io.github.graviton94.todayletters.core.Memory.surpriseAnswered(c, ok, today))
        store.surpriseDay = today
        if (ok) earn(Earn.REVIEW_RIGHT, id = c.key.substringBefore(":"))
        cue(ok)
        version++
    }
    /** 받아 둔 낭독을 모두 지운다 (설정 › 저장 공간). */
    fun clearDownloads() { downloads.clear(); version++ }
    /** 미리 받기: 아직 도착하지 않은 장도 (지금은 유료로 막힌 장이 없음). [chapter] 가 null 이면 모든 장. */
    fun predownload(series: String, chapter: String?) {
        val pack = if (chapter == null) downloads.all(series) else downloads.pack(series, chapter)
        if (pack == null || downloads.ready(pack)) return
        downloadAsk = DownloadAsk(pack, series, pack.chapter, null, null)
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

    // ── 보상 엔진 (모든 시리즈 공통: 시리즈 화폐 · 오늘의 자료 · 이정표) ───────────
    /** 한 번에 보여 줄 보상 순간: 받은 화폐 · 걸린 자료 · 새 이정표 · 혼자 읽기 이정표. */
    data class RewardMoment(
        val series: String,
        val gain: Int,
        val piece: Piece?,
        val achievements: List<String>,
        val milestone: Int?,
        val lines: List<Pair<Earn, Int>> = emptyList(),
    )
    var reward by mutableStateOf<RewardMoment?>(null)
    private var pending = mutableMapOf<Earn, Int>()
    private var pendingPiece: Piece? = null

    /** 시리즈 지갑. */
    fun wallet(id: String = current): Wallet = run { @Suppress("UNUSED_EXPRESSION") version; store.wallet(id) }
    val wallet: Wallet get() = wallet(current)

    fun earn(e: Earn, times: Int = 1, id: String = current): Int {
        val (w, g) = Rewards.earn(store.wallet(id), e, today, times)
        store.saveWallet(id, w); if (g > 0) pending[e] = (pending[e] ?: 0) + g
        return g
    }
    fun spend(cost: Int, id: String = current): Boolean {
        val w = Rewards.spend(store.wallet(id), cost) ?: return false
        store.saveWallet(id, w); version++; return true
    }

    /** 화폐로 사기. 쉼표는 [Streak.MAX_RESTS] 개까지. */
    fun buy(what: Spend, id: String = current, piece: Piece? = null): Boolean {
        when (what) {
            Spend.REST -> if (store.rests >= Streak.MAX_RESTS) return false
            Spend.EARLY_LETTER -> if (store.earlyDay(id) == today || all(id).size <= openable(id).size) return false
            Spend.PICK_PIECE -> if (piece == null || owns(id, piece)) return false
        }
        if (!spend(what.cost, id)) return false
        when (what) {
            Spend.REST -> store.rests = store.rests + 1
            Spend.EARLY_LETTER -> store.setEarlyDay(id, today)
            Spend.PICK_PIECE -> store.addPiece(id, piece!!.id)
        }
        version++; return true
    }

    // ── 갤러리 ───────────────────────────────────────────────
    /** 편지 자료: 다 읽은 편지의 그림 (편지 순서대로). */
    fun letterPieces(id: String): List<Piece> = work(id).chapters.flatMap { c -> c.letters.mapNotNull { l ->
        l.plate?.let { pl -> Piece("letter:${l.id}", "plates/${pl.image}", pl.title, pl.date, pl.collection, Tier.LETTER) }
    } }
    fun ownedLetterPieces(id: String) = work(id).chapters.flatMap { c -> c.letters.filter { progress(id, c.id, it.id).done && it.plate != null }.map { "letter:${it.id}" } }.toSet()
    fun owns(id: String, p: Piece) = run { @Suppress("UNUSED_EXPRESSION") version; p.id in store.pieces(id) || p.id in ownedLetterPieces(id) }
    /** 이 시리즈 갤러리 전체 (편지 자료 + 소장품) 와 가진 수. */
    fun gallery(id: String): List<Piece> = letterPieces(id) + work(id).kit.collection
    fun ownedCount(id: String) = gallery(id).count { owns(id, it) }

    /**
     * 오늘의 자료 한 점을 건다. 같은 날 같은 [reason] 으로는 한 번만 (다시 열어도 같은 그림).
     * 이달의 전시 주제가 두 배로 잘 나온다. [minTier] 는 이정표의 확정 명작.
     */
    fun draw(id: String, reason: String, minTier: Tier = Tier.SKETCH): Piece? {
        val tag = "$today:$reason"
        if (tag in store.drawTags(id)) return null
        val pool = work(id).kit.collection
        val p = Draws.pick(pool, store.pieces(id), (today * 131 + reason.hashCode()).toLong() xor id.hashCode().toLong(), exhibition(id)?.theme, minTier) ?: return null
        store.addPiece(id, p.id); store.addDrawTag(id, tag)
        pendingPiece = p
        version++
        return p
    }

    // ── 오늘의 할 일 ─────────────────────────────────────────
    val quests: List<Quest> get() = Quests.today(today)
    fun questCount(q: Quest) = run { @Suppress("UNUSED_EXPRESSION") version; store.quest(today, q) }
    fun questDone(q: Quest) = Quests.done(q, questCount(q))
    private fun questAdd(q: Quest, n: Int = 1) { if (q in quests) { store.setQuest(today, q, store.quest(today, q) + n); questsCheck() } }
    private fun questAtLeast(q: Quest, n: Int) { if (q in quests && store.quest(today, q) < n) { store.setQuest(today, q, n); questsCheck() } }
    private fun questsCheck() {
        version++
        if (quests.all { questDone(it) } && store.questsPaidDay != today) {
            store.questsPaidDay = today
            earn(Earn.QUESTS); draw(current, "quests")
            settle()
        }
    }

    // ── 이달의 전시 ──────────────────────────────────────────
    fun exhibition(id: String = current): Exhibition? = Draws.exhibition(work(id).kit.exhibitions, LocalDate.now().monthValue - 1)
    fun exhibitionPieces(id: String = current): List<Piece> = exhibition(id)?.let { Draws.exhibitionPieces(work(id).kit.collection, it) } ?: emptyList()
    val daysLeftInMonth: Int get() = LocalDate.now().let { it.lengthOfMonth() - it.dayOfMonth + 1 }

    // ── 일요일 낭독회 ────────────────────────────────────────
    /** 이번 주 (월~) 에 녹음한 마디들, 녹음한 차례대로. */
    fun weekTakes(): List<java.io.File> {
        val start = java.time.LocalDate.ofEpochDay(io.github.graviton94.todayletters.core.Recital.weekStart(today)).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val root = java.io.File(ctx.filesDir, "readings")
        return (root.listFiles() ?: emptyArray()).filter { it.isDirectory && it.name != "first" }
            .flatMap { d -> (d.listFiles() ?: emptyArray()).filter { it.name.endsWith(".wav") && it.lastModified() >= start } }
            .sortedBy { it.lastModified() }
    }
    /** 억양 · 리듬 기록: (첫 주 평균, 이번 주 평균). 기록이 없으면 null. */
    fun scoreTrend(): Pair<Pair<Int, Int>?, Pair<Int, Int>?> {
        val all = store.scores()
        if (all.isEmpty()) return null to null
        val firstWeek = io.github.graviton94.todayletters.core.Recital.weekStart(all.keys.min())
        val thisWeek = io.github.graviton94.todayletters.core.Recital.weekStart(today)
        fun avg(range: LongRange) = all.filterKeys { it in range }.values.let { v ->
            val n = v.sumOf { it.third }; if (n == 0) null else (v.sumOf { it.first } / n) to (v.sumOf { it.second } / n)
        }
        return avg(firstWeek..firstWeek + 6) to avg(thisWeek..thisWeek + 6)
    }
    fun recordScore(sc: io.github.graviton94.todayletters.core.Prosody.Score) { store.addScore(today, sc.intonation, sc.rhythm); version++ }
    val recitalDone: Boolean get() = run { @Suppress("UNUSED_EXPRESSION") version; store.recitalWeek == io.github.graviton94.todayletters.core.Recital.weekStart(today) }
    fun recitalFinish() {
        if (recitalDone) return
        store.recitalWeek = io.github.graviton94.todayletters.core.Recital.weekStart(today)
        earn(Earn.RECITAL); settle()
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
     * 오늘 화면의 주인공 편지 (북극성 = ‘이 편지를 얼마나 혼자 읽나’). 읽는 중인 편지가 없을 때:
     * 봉인했지만 봉인 화면을 아직 못 본 편지 → 다 읽었지만 아직 봉인 못 한 가장 최근 편지.
     */
    fun focusLetter(id: String): Pair<String, Letter>? {
        val done = openable(id).filter { (c, l) -> progress(id, c, l.id).done }
        return done.firstOrNull { (c, l) -> isSealed(id, c, l) && key(id, c, l.id) !in store.sealSeen }
            ?: done.lastOrNull { (c, l) -> !isSealed(id, c, l) }
    }

    // ── 따라 읽기 · 완독 봉인 (모든 시리즈 공통) ─────────────────
    /** 따라 읽기의 칸: 문장(메시지)마다 하나, 문장 통째 (v18). 낭독 시각이 있으면 첫 마디 시작 ~ 끝 마디 끝. */
    fun shadowChunks(id: String, chapter: String, l: Letter): List<List<io.github.graviton94.todayletters.data.Chunk>> {
        val learn = seriesSettings(id).learn
        return l.messages.mapIndexed { i, m ->
            val text = io.github.graviton94.todayletters.core.Breaks.plain(m.text[learn])
            val cs = narrator.chunks(narrator.path(id, chapter, l.id, "m${i + 1}_${learn.code}"))
            listOf(if (cs.isEmpty()) io.github.graviton94.todayletters.data.Chunk(-1, -1, text) else io.github.graviton94.todayletters.data.Chunk(cs.first().start, cs.last().end, text))
        }
    }

    /** 마디 녹음 파일: files/readings/<편지>/m<문장>_c<마디>.wav (원음이라 억양 · 리듬을 잴 수 있다) */
    fun readingDir(id: String, chapter: String, letter: String) = java.io.File(ctx.filesDir, "readings/${id}_${chapter}_$letter")
    fun take(id: String, chapter: String, letter: String, msg: Int, chunk: Int) = java.io.File(readingDir(id, chapter, letter), "m${msg}_c$chunk.wav")
    val pcm = io.github.graviton94.todayletters.data.PcmRecorder()

    /** 억양 · 리듬을 통과한 마디인가. */
    fun passed(id: String, chapter: String, letter: String, msg: Int, chunk: Int) = run { @Suppress("UNUSED_EXPRESSION") version; "${key(id, chapter, letter)}:$msg:$chunk" in store.passed }
    fun markPassed(id: String, chapter: String, letter: String, msg: Int, chunk: Int) {
        val k = "${key(id, chapter, letter)}:$msg:$chunk"
        if (k !in store.passed) { store.markPassed(k); earn(Earn.SHADOW_PASS, id = id) }
        questAdd(Quest.SHADOW_PASS); finished()
        version++
    }

    /** 내 낭독: 마디 녹음을 차례로 이어 붙인 한 편. 하나도 없으면 null. */
    fun myReading(id: String, chapter: String, l: Letter): java.io.File? {
        val parts = shadowChunks(id, chapter, l).flatMapIndexed { i, cs -> cs.indices.map { j -> take(id, chapter, l.id, i, j) } }.filter { it.exists() }
        if (parts.isEmpty()) return null
        val out = java.io.File(ctx.filesDir, "readings/${id}_${chapter}_${l.id}.wav")
        runCatching { io.github.graviton94.todayletters.data.Wav.join(parts, out) }.onFailure { return null }
        return out
    }

    fun isShadowed(id: String, chapter: String, l: Letter) = run { @Suppress("UNUSED_EXPRESSION") version; key(id, chapter, l.id) in store.shadowed }
    fun isSealed(id: String, chapter: String, l: Letter) = run { @Suppress("UNUSED_EXPRESSION") version; key(id, chapter, l.id) in store.sealed }

    /** 따라 읽기를 끝까지 했을 때. 이미 혼자 다 읽는 편지라면 봉인까지. 봉인됐으면 true. */
    fun shadowDone(id: String, chapter: String, l: Letter): Boolean {
        store.markShadowed(key(id, chapter, l.id)); version++
        settle()
        return isSealed(id, chapter, l)
    }
    fun sealSeen(id: String, chapter: String, l: Letter) { store.markSealSeen(key(id, chapter, l.id)); version++ }

    /** 이정표 숫자: [id] 가 있으면 그 시리즈만, 없으면 모든 시리즈를 합쳐서. */
    fun stats(id: String? = null): Stats {
        val cm = cardMap()
        var done = 0; var alone = 0; var chapters = 0; var known = 0; var owned = 0
        val ws = if (id == null) works else listOf(work(id))
        ws.forEach { w -> w.chapters.forEach { c ->
            val ds = c.letters.count { progress(w.series.id, c.id, it.id).done }
            done += ds; if (ds == c.letters.size && ds > 0) chapters++
            alone += c.letters.count { l -> progress(w.series.id, c.id, l.id).done && Growth.letterAlone(l.words.indices.map { cardKey(w.series.id, c.id, l.id, it) }, cm) }
        } }
        val prefixes = ws.map { it.series.id + ":" }
        cm.values.filter { c -> prefixes.any { c.key.startsWith(it) } }.forEach { if (Growth.known(it)) known++; if (it.box >= Memory.TOP) owned++ }
        return Stats(
            lettersDone = done, streak = streak, bestStreak = maxOf(store.bestStreak, streak),
            wordsKnown = known, wordsOwned = owned,
            readAlone = if (id == null) readAlone else Growth.readAlone(arrivedWordKeys().filter { it.startsWith("$id:") }, cm),
            lettersAlone = alone, lettersSealed = store.sealed.count { k -> prefixes.any { k.startsWith(it) } }, chapters = chapters,
            shadowPassed = store.passed.count { k -> prefixes.any { k.startsWith(it) } },
            pieces = ws.sumOf { ownedCount(it.series.id) },
            seriesStarted = works.count { startedCount(it.series.id) > 0 },
            langsSpoken = works.filter { w -> store.passed.any { it.startsWith(w.series.id + ":") } }.map { seriesSettings(it.series.id).learn }.distinct().size,
        )
    }

    /** 이정표 받은 날 (epoch day), 못 받았으면 null. */
    fun achievedOn(key: String): Long? = run { @Suppress("UNUSED_EXPRESSION") version; store.achievedDay(key) }

    /**
     * 정산: 혼자 읽기가 오른 만큼 · 봉인 · 새 이정표 (시리즈 · 전체) 를 모아 한 번의 보상 순간으로 보여 준다.
     * 이정표 세 개마다 명작 한 점이 확정. 아무 것도 없으면 조용히.
     */
    fun settle() {
        val cm = cardMap()
        val id = current
        // 완독 봉인: 혼자 다 읽고 (낱말 모두 떠올리기 이상) 다 따라 읽은 편지
        works.forEach { w -> w.chapters.forEach { c -> c.letters.forEach { l ->
            val k = key(w.series.id, c.id, l.id)
            if (k !in store.sealed && k in store.shadowed && progress(w.series.id, c.id, l.id).done &&
                Growth.letterAlone(l.words.indices.map { cardKey(w.series.id, c.id, l.id, it) }, cm)) { store.seal(k); earn(Earn.SEAL, id = w.series.id) }
        } } }
        // 혼자 읽기
        val now = readAlone
        val seen = store.readAloneSeen
        var milestone: Int? = null
        if (now > seen) {
            earn(Earn.READ_ALONE, now - seen)
            milestone = Growth.crossed(seen, now).lastOrNull()
            store.readAloneSeen = now
        }
        // 이정표: 이 시리즈 + 전체
        val got = mutableListOf<String>()
        if (id.isNotEmpty()) Achievements.newly(Achievements.series, stats(id), store.achievements, id).forEach { a ->
            val k = Achievements.key(a, id)
            store.unlock(k); store.setAchievedDay(k, today); earn(Earn.ACHIEVEMENT); got += a.id
            val n = store.achievements.count { it.startsWith("$id:") }
            if (Achievements.grantsMasterpiece(n - 1)) draw(id, "ach:${a.id}", Tier.PAINTING)
        }
        Achievements.newly(Achievements.overall, stats(), store.achievements).forEach { a ->
            store.unlock(a.id); store.setAchievedDay(a.id, today); earn(Earn.ACHIEVEMENT); got += a.id
        }
        val gain = pending.values.sum()
        if (gain > 0 || pendingPiece != null || got.isNotEmpty()) {
            reward = RewardMoment(id, gain, pendingPiece, got, milestone, pending.toList())
        }
        pending = mutableMapOf(); pendingPiece = null
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
