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
import io.github.graviton94.todayletters.core.Medals
import io.github.graviton94.todayletters.core.MedalBook
import io.github.graviton94.todayletters.core.Levels
import io.github.graviton94.todayletters.core.Level
import io.github.graviton94.todayletters.core.Xp
import io.github.graviton94.todayletters.core.XpAction
import io.github.graviton94.todayletters.core.Draws
import io.github.graviton94.todayletters.core.Piece
import io.github.graviton94.todayletters.core.Tier
import io.github.graviton94.todayletters.core.Journey
import io.github.graviton94.todayletters.core.Plays
import io.github.graviton94.todayletters.core.Tutorial
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
import io.github.graviton94.todayletters.data.forLearn
import io.github.graviton94.todayletters.data.learnVocab
import io.github.graviton94.todayletters.core.CardRef
import java.time.LocalDate

/**
 * 앱 한 벌의 상태. 화면 이동은 [Route] 스택 하나 (core/Routes.kt 의 계층을 따름).
 * 저장은 [Store] (기기 안), 콘텐츠는 [Library] (assets).
 */
class AppState(val ctx: Context, deepLink: Boolean = false, recreated: Boolean = false) {
    val store = Store(ctx)
    /** 앱에 넣은 작품들 (원어 자료). 영어 · 한국어 자료는 [Work.decks]. */
    val baseWorks: List<Work> = Library.works(ctx)
    /**
     * 지금 배우는 말에 맞춘 작품들: 영어 · 한국어를 배우고 그 언어 자료(*_en · *_ko.json)가 있으면 그 낱말 · 사전 · 표현으로.
     * 없으면 원어 자료 그대로. 같은 언어면 늘 같은 객체 (화면의 remember · indexOf 가 그대로 맞게).
     */
    val works: List<Work> get() = baseWorks.map { w -> if (w.decks.isEmpty()) w else w.forLearn(store.series(w.series.id)?.learn ?: w.series.original) }
    val narrator = Narrator.shared(ctx)
    /** 편지 속 그 곡 (60초 발췌): 낭독과 따로 울리는 재생기. 방을 나가거나 뒤로 가면 멈춘다. */
    val music = io.github.graviton94.todayletters.data.Excerpt(ctx)
    // 효과음 재생기는 처음 울릴 때 만든다 (알림 · 위젯이 이 상태를 만들 때마다 SoundPool 을 새로 열지 않게)
    private val effects by lazy { io.github.graviton94.todayletters.data.Effects(ctx) }
    /** 맞음 · 틀림 효과음 (설정 › 효과음이 켜져 있을 때만). */
    fun cue(ok: Boolean) { if (app.effects) runCatching { effects.play(ok) } }
    /** 자료가 걸리는 소리 (등급마다). */
    fun cueTier(t: io.github.graviton94.todayletters.core.Tier) { if (app.effects) runCatching { effects.tier(io.github.graviton94.todayletters.data.Frames.kindOf(t).sound) } }
    val recorder = Recorder(ctx)
    val downloads = io.github.graviton94.todayletters.data.Downloads(ctx)
    /** 결제 (구독 · 시리즈 소장). Play 에 붙는 것은 MainActivity 가 ([io.github.graviton94.todayletters.data.Billing.connect]). */
    val billing = io.github.graviton94.todayletters.data.Billing(ctx, store).also { it.onChange = { version++ } }
    /** 지금 이용권 (구독 · 소장한 시리즈). 바뀌면 화면을 다시 그린다. */
    val entitlement: io.github.graviton94.todayletters.core.Entitlement get() = run { @Suppress("UNUSED_EXPRESSION") version; billing.effective }
    /** 이 장을 읽을 수 있나 (프롤로그 · I · II장은 늘, 그 뒤는 소장 · 구독). */
    fun chapterOpen(c: io.github.graviton94.todayletters.core.Chapter) = io.github.graviton94.todayletters.core.Access.open(c, entitlement)
    /** 장 id ("I" · "P" · "E") 로: 이 작품의 장 목록에 없는 것 (프롤로그 · 맺음말) 은 늘 열림. */
    fun chapterIdOpen(series: String): (String) -> Boolean = { ch -> work(series).chapters.firstOrNull { it.id == ch }?.let(::chapterOpen) ?: true }

    /** 앱 밖에 둔 장의 낭독을 받을지 묻는 중: 받고 나면 (또는 소리 없이 읽기를 고르면) 이 편지를 연다. */
    /** 낭독 받기 묻기. [letter] 가 있으면 받은 뒤 그 편지를 열고, 없으면 미리 받기 (받고 끝). */
    /** 받기 시트: [letter] 가 있으면 다 받은 뒤 그 편지를, [then] 이 있으면 그 화면 (서문 · 맺음) 을 연다. */
    data class DownloadAsk(val pack: io.github.graviton94.todayletters.data.Downloads.Pack, val series: String, val chapter: String, val letter: Letter?, val from: Route.Tab?, val then: Route? = null)
    var downloadAsk by mutableStateOf<DownloadAsk?>(null)

    /** 의견 보내기 시트 (설정 › 의견 보내기, 멈춘 뒤 ‘보내기’). [kind] 는 처음 고른 칸. */
    data class FeedbackAsk(val kind: io.github.graviton94.todayletters.core.Feedback.Kind = io.github.graviton94.todayletters.core.Feedback.Kind.BUG)
    var feedback by mutableStateOf<FeedbackAsk?>(null)
    /** 새 버전을 다 받아 둠: 서재 아래 띠 ‘다시 열기’ ([restartForUpdate] 는 MainActivity 가 채운다). */
    var updateReady by mutableStateOf(false)
    var restartForUpdate: () -> Unit = {}
    /** 서재에 들어왔을 때 새 버전을 물어본다 (MainActivity 가 채운다). */
    var askUpdate: () -> Unit = {}
    /**
     * ‘새로 바뀐 것’: 버전이 바뀐 뒤 첫 실행에 한 번. 처음 설치(아직 소개 전)는 보여 주지 않고 지금 버전만 적어 둔다.
     */
    var whatsNew by mutableStateOf(run {
        val now = io.github.graviton94.todayletters.data.Updates.versionCode(ctx)
        when {
            now <= 0L || store.seenVersion == now -> false
            !store.onboarded -> { store.seenVersion = now; false }
            else -> true
        }
    })
    fun closeWhatsNew() { whatsNew = false; store.seenVersion = io.github.graviton94.todayletters.data.Updates.versionCode(ctx) }

    /** 지난번에 앱이 멈췄으면 이번에 한 번만 묻는다 (data/CrashLog). */
    var crashAsk by mutableStateOf(runCatching { io.github.graviton94.todayletters.data.CrashLog.shouldAsk(ctx) }.getOrDefault(false))
    fun answerCrash(send: Boolean) {
        crashAsk = false
        io.github.graviton94.todayletters.data.CrashLog.asked(ctx)
        if (send) feedback = FeedbackAsk(io.github.graviton94.todayletters.core.Feedback.Kind.BUG)
    }

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
        drainLater()
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

    init {
        // 언어마다 진도 (v22): 예전 진도는 그때 배우던 말의 것으로 옮긴다 (작품마다 한 번)
        baseWorks.forEach { w -> runCatching { store.migrateSpace(w.series.id, space(w.series.id)) } }
        // 처음 연 날 (그날만 튜토리얼 작품 2통): 처음 설치면 오늘, 이미 쓰던 사람이면 -1 (덤 없음)
        if (!store.hasFirstDay()) store.firstDay = if (store.onboarded || store.progressKeys().isNotEmpty()) -1 else LocalDate.now().toEpochDay()
        // 예전 사용자: 편지를 한 통이라도 마쳤으면 튜토리얼은 끝난 것으로 (안내 한 줄 · 서재 강조 없이)
        if (!store.tutorialDone && !store.tutorialReplay && Tutorial.done(false, store.progressKeys().count { store.progress(it).done })) store.tutorialDone = true
    }

    // ── 처음 설치의 튜토리얼 (core 의 Tutorial) ─────────────────
    /** 튜토리얼을 마쳤나. 마치기 전에는 서재가 첫 작품 카드를 강조하고, 편지 I 의 걸음마다 한 줄 안내가 처음 한 번 나온다. */
    val tutorialDone: Boolean get() = run { @Suppress("UNUSED_EXPRESSION") version; store.tutorialDone }
    /** 튜토리얼의 작품 (서재의 첫 작품, 빈센트). */
    val tutorialSeries: String get() = works.firstOrNull()?.series?.id.orEmpty()
    /** 이 걸음의 안내 한 줄을 보여 줄 차례인가 (튜토리얼 중, 처음 들어왔을 때). */
    fun guideDue(step: String) = Tutorial.guide(step, store.tutorialDone, store.guideSeen)
    fun guideShown(step: String) { store.markGuideSeen(step) }
    /** 튜토리얼 끝: 그 뒤로는 어느 작품에서도 도움말이 저절로 뜨지 않는다 ((?) 로만). */
    fun graduate() { store.tutorialDone = true; store.tutorialReplay = false; store.endReplay(); version++ }

    /**
     * 설정 › 튜토리얼 다시 하기: (?) 도움말과 편지 걸음의 한 줄 안내를 처음 상태로 돌리고 빈센트의 편지 I 로 바로 들어간다.
     * 그 편지의 진도는 따로 둔 연습 진도로 처음부터 (저장된 진도 · 화폐 · 이어 읽기는 그대로, [replaying]).
     * 아직 빈센트를 시작하지 않았으면 처음 설치처럼 서재의 튜토리얼 카드부터.
     */
    fun replayTutorial() {
        coach.reset(); store.coachSeen = coach.seen; coachTick++
        store.clearGuideSeen(); store.tutorialDone = false; store.tutorialReplay = true; store.endReplay()
        settingsOpen = false
        val id = tutorialSeries
        val c = works.firstOrNull()?.chapters?.firstOrNull()
        val l = c?.letters?.firstOrNull()
        if (c == null || l == null || needsWelcome(id)) { version++; go(Route.Library); return }
        store.replayKey = key(id, c.id, l.id)
        version++
        enter(id)
        open(id, c.id, l, Route.Inbox)
    }

    /** 튜토리얼을 다시 하는 중인 그 편지인가 (진도는 따로, 화폐 · 이어 읽기 · 하루 편지 수는 세지 않는다). */
    fun replaying(series: String, chapter: String, letter: String): Boolean = tutorialReplaying(series, chapter, letter) || again(series, chapter, letter)
    private fun tutorialReplaying(series: String, chapter: String, letter: String): Boolean =
        store.tutorialReplay && store.replayKey.isNotEmpty() && store.replayKey == key(series, chapter, letter)

    /** 처음부터 다시 보는 중인 편지인가 ([replayLetter]): 진도는 따로, 보상은 처음 마칠 때 한 번만 (core 의 Replay). */
    fun again(series: String, chapter: String, letter: String): Boolean = run {
        @Suppress("UNUSED_EXPRESSION") version
        store.againKey.isNotEmpty() && store.againKey == key(series, chapter, letter)
    }
    private fun again(r: Route.Letter): Boolean = !r.epilogue && letterOf(r).let { (ch, l) -> again(r.series, ch, l.id) }

    /**
     * 처음부터 다시 보기 (편지 목록 · 오늘의 편지 마침 한 장): 마친 편지를 첫 메시지부터 새 편지처럼 다시 받는다.
     * ‘다시 읽기’(마친 편지를 열어 전문을 한 번에 보기) 와는 따로. 걸음도 다시 하지만 화폐 · XP · 메달 · 도장은 다시 주지 않는다.
     */
    fun replayLetter(id: String, chapter: String, letter: Letter, from: Route.Tab?) {
        if (!io.github.graviton94.todayletters.core.Replay.can(store.progress(key(id, chapter, letter.id)))) return
        finishSheet = null
        store.againKey = key(id, chapter, letter.id)
        store.againProgress = io.github.graviton94.todayletters.core.Replay.start()
        version++
        open(id, chapter, letter, from)
    }

    /** 오늘의 편지 마침 한 장 (편지 완료 화면 위에): 방금 처음 마친 편지의 열쇠. 한 번 닫으면 다시 뜨지 않는다. */
    var finishSheet by mutableStateOf<String?>(null)
    private fun replaying(r: Route.Letter): Boolean = letterOf(r).let { (ch, l) -> replaying(r.series, ch, l.id) }

    /**
     * 튜토리얼: 서재의 첫 작품 카드에서 배울 말을 골랐다 → 전환 · 오늘을 건너 그 작품의 편지 I 로 (프롤로그는 목차에서 언제든).
     * 뒤로 가면 오늘.
     */
    fun startTutorial(learn: io.github.graviton94.todayletters.core.Lang) = chooseLearn(tutorialSeries, learn)

    /** ‘언어마다 진도가 따로 저장돼요’ 알림 (처음 배우는 말을 바꿀 때 한 번). */
    var langNotice by mutableStateOf(false)

    /**
     * 배우는 말 바꾸기 (서재 카드 · 시리즈 머리 · 작품 설정 어디서든). 진도는 언어마다 따로라 바꾸면 그 언어의 진도로 건너간다.
     * 처음 바꿀 때 한 번 알림. 낭독 · 곡은 멈춘다 (다른 언어의 소리라서).
     */
    fun switchLearn(id: String, learn: io.github.graviton94.todayletters.core.Lang) {
        val cur = seriesSettings(id)
        if (cur.learn == learn) return
        narrator.stop(); music.stop(); gloss = null
        update(id, cur.copy(learn = learn))
        store.markLearnChosen(id)
        // 새 언어의 진도 공간은 옮길 것이 없다 (이미 옮겼거나 새로 시작)
        store.migrateSpace(id, space(id))
        if (!store.langNoticeSeen) { store.langNoticeSeen = true; langNotice = true }
    }

    /**
     * 서재에서 고른 시리즈로 들어간다 (짧은 전환 뒤 저절로). 처음 들어가는 작품이면 무엇을 배울지만 고르고 ([Route.Welcome])
     * 고르면 바로 편지 I. 그 밖에는 오늘. 프롤로그는 목차에서 언제든 (저절로 띄우지 않는다).
     */
    fun enter(id: String) {
        current = id; store.lastSeries = id
        forward = true
        stack.clear()
        if (needsWelcome(id)) { stack.addAll(listOf(Route.Library, Route.Welcome(id))); return }
        stack.addAll(Nav.trail(Route.Inbox))
        // 앱 안 장이 없는 작품을 처음 열 때: 서문 + 무료 장을 받을지 한 번 묻는다 (‘다음에’ 면 표지에 받기 단추가 남는다)
        if (!store.firstAsked(id)) downloads.first(id)?.takeIf { !downloads.ready(it) }?.let { store.markFirstAsked(id); downloadAsk = DownloadAsk(it, id, it.chapter, null, null) }
    }

    /** 서문 열기: 아직 받지 않은 작품이면 먼저 받기 시트 (다 받으면 서문으로). */
    fun openPrologue(id: String, page: Int = 0) {
        val r = Route.Prologue(id, page)
        downloads.needed(id, "P")?.takeIf { !store.silent("$id:P") }?.let { downloadAsk = DownloadAsk(it, id, it.chapter, null, null, r); return }
        go(r)
    }

    /** 맺음 열기: 맺음 낭독 · 그림을 받지 않았으면 먼저 받기 시트. */
    fun openOutro(id: String) {
        downloads.needed(id, "E")?.takeIf { !store.silent("$id:E") }?.let { downloadAsk = DownloadAsk(it, id, it.chapter, null, null, Route.Outro(id)); return }
        go(Route.Outro(id))
    }

    /** 아직 무엇을 배울지 고르지 않았고, 한 통도 열지 않은 작품 (이미 읽던 작품에는 묻지 않는다). */
    fun needsWelcome(id: String) = !store.learnChosen(id) && startedCount(id) == 0

    /** 처음 들어간 작품에서 배울 말을 골랐다: 그 작품 설정에 담고 바로 첫 편지로 (뒤로 가면 오늘). */
    fun chooseLearn(id: String, learn: io.github.graviton94.todayletters.core.Lang) {
        update(id, seriesSettings(id).copy(learn = learn))
        store.markLearnChosen(id)
        store.migrateSpace(id, space(id))
        enter(id)
        if (route == Route.Inbox) openFirst(id)
    }

    private fun openFirst(id: String) {
        val c = work(id).chapters.firstOrNull() ?: return
        c.letters.firstOrNull()?.let { open(id, c.id, it, Route.Inbox) }
    }

    /** 프롤로그를 다 봤거나 건너뛰었다: 들어온 곳(목차)으로. */
    fun finishPrologue(id: String) {
        store.markPrologueSeen(id)
        back()
    }

    /** 이 작품의 모든 편지를 다 읽었나 (맺음말 · 완결 도록). */
    fun seriesDone(id: String) = work(id).chapters.all { c -> c.letters.all { progress(id, c.id, it.id).done } }

    /** 화면 스택. 맨 끝이 지금 화면. 알림으로 들어오면 그 시리즈의 오늘부터. */
    val stack = mutableStateListOf<Route>().apply { addAll(if (deepLink && store.onboarded) Nav.trail(Route.Inbox) else listOf(Route.Library)) }
    val route get() = stack.last()

    /** 마지막 이동이 앞으로였나 (화면 전환 방향). */
    var forward by mutableStateOf(true)
        private set

    fun go(r: Route) {
        gloss = null
        forward = r != Route.Library && (r !is Route.Tab || stack.size > 2 || (stack.lastOrNull() as? Route.Tab)?.let { tabIndex(it) < tabIndex(r) } ?: true)
        when {
            r == Route.Library -> { narrator.stop(); music.stop(); stack.clear(); stack.add(r) }
            r is Route.Tab -> { stack.clear(); stack.addAll(Nav.trail(r)) }
            else -> stack.add(r)
        }
    }

    private fun tabIndex(r: Route) = listOf(Route.Inbox, Route.Words, Route.Gallery, Route.Shop).indexOf(r)

    /** 앱을 닫을지 묻는 중. */
    var askingExit by mutableStateOf(false)

    /** 앱을 벗어난 횟수. 녹음 · 내 녹음 듣기 화면은 이 값이 바뀌면 그 자리에서 멈춘 것으로 그린다. */
    var away by mutableIntStateOf(0)
        private set
    /** 앱을 벗어날 때: 이어 듣는 편지 낭독(알림이 있는 것)만 두고, 짧은 소리 · 녹음 · 내 녹음 듣기는 멈춘다 (마이크를 뒤에서 켜 두지 않는다). */
    fun leftApp() {
        if (io.github.graviton94.todayletters.data.Playback.now == null) narrator.stop()
        music.pause()
        pcm.stop(); recorder.stop(); recorder.stopPlaying()
        away++
    }

    /** 뒤로: 스택을 하나 내리고, 비면 계층의 부모로. 맨 위(서재)면 false (앱을 나감). */
    fun back(): Boolean {
        narrator.stop(); music.stop()
        gloss = null
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
    /** 저장한 값이 바뀌어 화면을 다시 그려야 할 때. */
    fun bump() { version++ }

    fun seriesSettings(id: String): SeriesSettings =
        store.series(id) ?: Langs.start(works.first { it.series.id == id }.series, app).also { store.save(id, it) }

    fun update(id: String, s: SeriesSettings) { store.save(id, s); version++ }

    fun room(id: String) = RoomView.of(app, seriesSettings(id), reducedMotion)

    fun work(id: String) = works.first { it.series.id == id }

    /**
     * 진도 열쇠의 작품 이름: 배우는 말마다 따로 ("vincent" 원어 · "vincent@en" · "vincent@ko", [io.github.graviton94.todayletters.core.Spaces]).
     * 편지 진도 · 연 편지 · 오늘의 할 일 · 하루 편지 수 · 따라 읽기 · 봉인이 모두 이 이름으로 갈린다.
     */
    fun space(series: String): String {
        val w = baseWorks.firstOrNull { it.series.id == series } ?: return series
        val learn = store.series(series)?.learn ?: return series
        return io.github.graviton94.todayletters.core.Spaces.of(series, learn, w.series.original)
    }

    fun key(series: String, chapter: String, letter: String) = "${space(series)}:$chapter:$letter"
    fun progress(series: String, chapter: String, letter: String): LetterProgress {
        @Suppress("UNUSED_EXPRESSION") version
        if (tutorialReplaying(series, chapter, letter)) return store.replayProgress
        if (again(series, chapter, letter)) return store.againProgress
        return store.progress(key(series, chapter, letter))
    }

    fun save(series: String, chapter: String, letter: String, p: LetterProgress) {
        // 튜토리얼 다시 하기: 연습 진도에만 (저장된 진도는 그대로)
        if (tutorialReplaying(series, chapter, letter)) { store.replayProgress = p; version++; return }
        if (again(series, chapter, letter)) { store.againProgress = p; version++; return }
        val k = key(series, chapter, letter)
        val newlyDone = p.done && !store.progress(k).done
        if (newlyDone) store.logDay("letters", today, k)   // 이번 주 돌아보기 (E1)
        store.save(k, p); version++
        // 오늘의 여정 I: 편지를 마쳤다 (여정을 다 했으면 보너스 · 그림)
        if (newlyDone) journeyCheck(series)
    }

    /** 배우는 말 [learn] 으로 다 읽은 편지 수 (언어 고르기 시트에서 언어마다 진도를 보여 줄 때). */
    fun doneIn(id: String, learn: io.github.graviton94.todayletters.core.Lang): Int = run {
        @Suppress("UNUSED_EXPRESSION") version
        val w = baseWorks.firstOrNull { it.series.id == id } ?: return@run 0
        val sp = io.github.graviton94.todayletters.core.Spaces.of(id, learn, w.series.original)
        w.chapters.sumOf { c -> c.letters.count { store.progress("$sp:${c.id}:${it.id}").done } }
    }

    /** 이 작품에서 다 읽은 편지 수와 지금 열 수 있는 편지 (순서대로). */
    fun doneCount(id: String) = work(id).chapters.flatMap { c -> c.letters.map { c.id to it } }
        .count { (c, l) -> progress(id, c, l.id).done }

    private fun all(id: String) = work(id).chapters.flatMap { c -> c.letters.map { c.id to it } }

    /** 연 장 ("작품:장"): 한 통이라도 연 편지가 있는 장. 자유 연습은 이 장들의 카드에서 무작위로 고른다. */
    fun openedChapters(id: String = current): Set<String> = work(id).chapters
        .filter { c -> c.letters.any { l -> store.started(key(id, c.id, l.id)) || progress(id, c.id, l.id).let { it.shown > 0 || it.done } } }
        .map { "$id:${it.id}" }.toSet()

    /** 한 번이라도 연 편지 수 (다 읽은 것 포함). */
    fun startedCount(id: String) = all(id).count { (c, l) -> store.started(key(id, c, l.id)) || progress(id, c, l.id).let { it.shown > 0 || it.done } }

    /** 그날 새로 오는 편지 수 (작품마다 1통, 처음 연 날의 튜토리얼 작품만 2통). */
    fun perDay(id: String, day: Long = today): Int = Arrivals.perDay(id, tutorialSeries, store.firstDay, day)

    fun openable(id: String): List<Pair<String, Letter>> {
        val all = all(id)
        val n = Arrivals.openable(all.size, startedCount(id), store.openedToday(space(id)), perDay(id)) +
            (if (store.earlyDay(space(id)) == today) 1 else 0)
        return all.take(n.coerceAtMost(all.size))
    }

    /** 도착했지만 한 번도 열지 않은 편지 수 (서재의 붉은 숫자). */
    fun unread(id: String): Int = openable(id).count { (c, l) -> !store.started(key(id, c, l.id)) && progress(id, c, l.id).shown == 0 }

    /** 열지 않은 다음 편지가 도착한 날 (epoch day). */
    fun arrivedOn(id: String): Long? = store.lastOpen(space(id)).let { (d, n) -> Arrivals.arrivedOn(d, n, perDay(id, d)) }

    fun waiting(id: String): Int =
        Arrivals.waiting(all(id).size, doneCount(id), startedCount(id), store.openedToday(space(id)), perDay(id))

    /** 이 편지가 며칠 뒤에 오는가 (0 이면 이미 왔다). */
    fun daysUntil(id: String, letterId: String): Int {
        val idx = all(id).indexOfFirst { it.second.id == letterId }
        return Arrivals.daysUntil(idx, openable(id).size, 1)
    }

    // ── 복습 (낱말 카드) ─────────────────────────────────────
    val today: Long get() = LocalDate.now().toEpochDay()

    /** 낱말 카드 키 "작품:챕터:편지:낱말번호". */
    fun cardKey(series: String, chapter: String, letter: String, word: Int) = CardRef.word(series, chapter, letter, word, deckOf(series))

    /** 이 작품에서 지금 쓰는 어휘 자료 꼬리 ("" 원어 · "en" · "ko"): 카드 열쇠 · 하루 몫 · 나중 줄이 배우는 말마다 따로. */
    fun deckOf(series: String): String = works.firstOrNull { it.series.id == series }?.deck.orEmpty()

    /** 카드 열쇠의 자료에 맞는 작품 (원어 자료 카드는 배우는 말을 바꿔도 원어 자료에서 찾는다). */
    private fun workFor(series: String, deck: String): Work? {
        val base = baseWorks.firstOrNull { it.series.id == series } ?: return null
        if (deck.isEmpty()) return base
        return base.decks.values.firstOrNull { it.deck == deck }
    }

    /** 카드 한 장이 가리키는 낱말: 작품 · (장, 편지) · 낱말 · 편지 안 번호(카드 낱말이면 0부터, 아니면 -1). [own] 은 내가 눌러 담은 낱말. */
    data class CardWord(val work: Work, val at: Pair<String, Letter>, val word: io.github.graviton94.todayletters.core.Word, val index: Int = -1, val own: Boolean = false)

    private fun letterFor(w: Work, ch: String, lid: String): Letter? =
        when (ch) {
            "P" -> w.prologueChapter?.letters
            "E" -> listOfNotNull(w.outro?.epilogue)
            else -> w.chapters.firstOrNull { it.id == ch }?.letters
        }?.firstOrNull { it.id == lid }

    /** 카드 키 → 낱말. 없어진 낱말이면 null. 키 꼴은 [io.github.graviton94.todayletters.core.CardRef]. */
    fun cardWord(key: String): CardWord? {
        val ref = io.github.graviton94.todayletters.core.CardRef.parse(key) ?: return null
        val w = workFor(ref.series, ref.deck) ?: return null
        val l = letterFor(w, ref.chapter, ref.letter) ?: return null
        return when {
            ref.index >= 0 -> l.words.getOrNull(ref.index)?.let { CardWord(w, ref.chapter to l, it, ref.index) }
            ref.phrase >= 0 -> l.phrases.getOrNull(ref.phrase)?.let { CardWord(w, ref.chapter to l, it) }
            // 내가 쓴 답장: 표현 카드처럼 (문장 하나, 소리 없음 · 쓰임 설명은 ‘내가 쓴 답장’)
            ref.reply -> l.replies.getOrNull(ref.turn)?.let { CardWord(w, ref.chapter to l, io.github.graviton94.todayletters.core.Word(it.text, note = replyNote)) }
            // 말투 바꿔 보기: 그 턴의 해요체 문장 (반말 · 높임이 있을 때만)
            ref.style -> l.replies.getOrNull(ref.turn)?.takeIf { it.styled }?.let { CardWord(w, ref.chapter to l, io.github.graviton94.todayletters.core.Word(it.text, note = styleNote)) }
            else -> w.lexicon?.get(ref.own)?.let { e -> CardWord(w, ref.chapter to l, io.github.graviton94.todayletters.core.Vocab.wordFor(e, l.source, w.learnVocab, w.series.original), own = true) }
        }
    }

    /**
     * 낱말 · 표현 소리. 차례로 찾아 이 기기에 있는 첫 파일 (없으면 "": 기기 TTS 는 쓰지 않는다, 듣기 단추를 숨긴다):
     *  1. 그 편지 폴더의 챕터 낱말 w<i>_<언어> · 표현 ph<n>_<언어> ([io.github.graviton94.todayletters.core.Word.audio])
     *  2. 오늘의 낱말 소리 v_<언어>_<slug> (data/lexicon/README.md): 그 편지 폴더, 없으면 그 낱말이 오늘의 낱말인 다른 편지 폴더
     * 앱 밖(R2)에 둔 장은 받아 둔 뒤에만 찾힌다.
     */
    fun wordAudio(series: String, chapter: String, letter: Letter, word: io.github.graviton94.todayletters.core.Word, lang: io.github.graviton94.todayletters.core.Lang,
                  work: io.github.graviton94.todayletters.data.Work? = null): String {
        fun has(path: String) = io.github.graviton94.todayletters.data.AssetFiles.has(ctx, path)
        if (word.audio.isNotEmpty()) narrator.path(series, chapter, letter.id, "${word.audio}_${lang.code}").let { if (has(it)) return it }
        if (word.lex.isEmpty() || word.phrase) return ""
        val name = io.github.graviton94.todayletters.core.VocabAudio.word(word.lex, lang)
        narrator.path(series, chapter, letter.id, name).let { if (has(it)) return it }
        val w = work ?: works.firstOrNull { it.series.id == series } ?: return ""
        fun scan(ch: String, ls: List<Letter>): String? = ls.firstOrNull { l -> l.id != letter.id && l.words.any { it.lex == word.lex } }
            ?.let { l -> narrator.path(series, ch, l.id, name).takeIf(::has) }
        return w.chapters.firstNotNullOfOrNull { c -> scan(c.id, c.letters) }
            ?: w.prologueChapter?.let { scan("P", it.letters) }
            ?: w.outro?.epilogue?.let { scan("E", listOf(it)) }
            ?: ""
    }
    fun wordAudio(c: CardWord, lang: io.github.graviton94.todayletters.core.Lang) = wordAudio(c.work.series.id, c.at.first, c.at.second, c.word, lang, c.work)

    /**
     * 표현 카드의 소리 (v22): ph<n>_<언어> 가 있으면 그것 (범위 없음). 없으면 그 표현이 나온 문장의 낭독에서
     * 표현이 든 마디만 (마디 시각이 있을 때), 그것도 없으면 문장 전체. 이 기기에 소리가 없으면 null.
     */
    fun phraseAudio(series: String, chapter: String, letter: Letter, ph: io.github.graviton94.todayletters.core.Word,
                    lang: io.github.graviton94.todayletters.core.Lang): Pair<String, IntRange?>? {
        wordAudio(series, chapter, letter, ph, lang).takeIf { it.isNotEmpty() }?.let { return it to null }
        if (ph.msg < 0) return null
        val m = narrator.path(series, chapter, letter.id, "m${ph.msg + 1}_${lang.code}")
        if (!io.github.graviton94.todayletters.data.AssetFiles.has(ctx, m)) return null
        fun norm(x: String) = io.github.graviton94.todayletters.core.Breaks.plain(x).lowercase().filter { it.isLetterOrDigit() || it == ' ' }.replace(Regex("\\s+"), " ").trim()
        val words = norm(ph.text[lang]).split(" ").filter { it.isNotEmpty() }
        val cs = narrator.chunks(m)
        if (words.isEmpty() || cs.isEmpty()) return m to null
        val first = cs.indexOfFirst { norm(it.text).split(" ").contains(words.first()) }
        val last = cs.indexOfLast { norm(it.text).split(" ").contains(words.last()) }
        if (first < 0 || last < first) return m to null
        return m to (cs[first].start..cs[last].end)
    }

    /** 편지를 다 읽으면 그 편지의 낱말 · 표현이 카드함에 들어간다 (내일부터 복습). 하루 새 카드는 [io.github.graviton94.todayletters.core.Intake.DAILY_NEW] 장까지. */
    fun collectWords(series: String, chapter: String, letter: Letter) {
        admit(letter.words.indices.map { cardKey(series, chapter, letter.id, it) } +
            letter.phrases.indices.map { CardRef.phrase(series, chapter, letter.id, it, deckOf(series)) })
        version++
    }

    /**
     * 새 카드 들이기: 나중 줄(어제 넘친 것)부터 오늘 몫까지 카드함에 넣고 나머지는 나중 줄로. 이미 있는 카드는 건너뛴다.
     * 반환: 이번에 넣은 열쇠.
     */
    fun admit(keys: List<String>): List<String> =
        keys.groupBy { CardRef.deckOf(it) }.ifEmpty { mapOf("" to emptyList<String>()) }.flatMap { (deck, ks) -> admit(ks, deck) }

    /** 배우는 말 한 벌([deck])의 하루 몫 · 나중 줄로 들인다. */
    private fun admit(keys: List<String>, deck: String): List<String> {
        val day = today
        val fresh = keys.filter { !store.hasCard(it) }
        val queue = (store.later(deck) + fresh).filter { !store.hasCard(it) }.distinct()
        val (now, later) = io.github.graviton94.todayletters.core.Intake.split(queue, store.addedOn(day, deck))
        now.forEach { store.save(io.github.graviton94.todayletters.core.Memory.added(it, day)) }
        store.setLater(deck, later)
        if (now.isNotEmpty()) store.setAdded(day, store.addedOn(day, deck) + now.size, deck)
        return now
    }

    private val DECKS = listOf("", "en", "ko")

    /** 날이 바뀌면 나중 줄을 오늘 몫만큼 카드함으로 (배우는 말마다). */
    fun drainLater() {
        val waiting = DECKS.filter { store.later(it).isNotEmpty() }
        if (waiting.isEmpty()) return
        waiting.forEach { admit(emptyList(), it) }
        version++
    }

    /** 나중 줄에 있는가 (하루 몫이 차서 내일 들어갈 카드). */
    fun isLater(key: String) = run { @Suppress("UNUSED_EXPRESSION") version; key in store.later(CardRef.deckOf(key)) }

    /** 편지에서 눌러 본 낱말을 내 카드로 담는 열쇠 (지금 배우는 말의 자료). */
    fun ownKey(series: String, chapter: String, letter: Letter, lex: String) = CardRef.own(series, chapter, letter.id, lex, deckOf(series))

    /** 편지에서 눌러 본 낱말을 내 카드로 담는다. 오늘 몫이 찼으면 나중 줄로 (false). */
    fun addOwn(series: String, chapter: String, letter: Letter, lex: String): Boolean {
        val key = ownKey(series, chapter, letter, lex)
        val now = admit(listOf(key)).contains(key)
        version++
        return now
    }

    /** 이 사전 열쇠가 이미 카드함(또는 나중 줄)에 있나: 이 편지의 카드 낱말이거나, 어느 편지에서든 내가 담은 낱말 (지금 배우는 말의 자료에서). */
    fun hasLex(series: String, lex: String): Boolean = run {
        @Suppress("UNUSED_EXPRESSION") version
        val deck = deckOf(series)
        val keys = store.cardKeys() + store.later(deck)
        keys.any { k -> CardRef.parse(k)?.takeIf { it.series == series && it.deck == deck }?.let { ref -> ref.own == lex || cardWord(k)?.word?.lex == lex } == true }
    }

    // ── 눌러서 뜻 보기 (편지 · 프롤로그 · 맺음말 · 따라 읽기 · 복습 문장 · 내 구절 어디서든) ──
    /** 펼친 낱말: 작품 · 장 · 편지 · 메시지 번호 · 토큰 번호 · 사전 열쇠. 화면 맨 위의 [GlossHost] 가 작은 카드를 띄운다. */
    data class GlossPick(val series: String, val chapter: String, val letter: Letter, val msg: Int, val token: Int, val key: String)
    var gloss by mutableStateOf<GlossPick?>(null)
    /** 펼친 낱말이 든 문장의 자리 (뜻 카드는 그 바로 아래에, 자리가 없으면 위에). */
    var glossAnchor by mutableStateOf<GlossAnchor?>(null)
    /** 지금 열린 전체 화면 창(낱말 카드 상세)의 층 수: 뜻 카드는 맨 위 층에만 뜬다. */
    var glossLayers by mutableStateOf(0)

    /**
     * 이 편지 메시지의 눌러서 뜻 보기 토큰: 지금 배우는 말이 이 작품 어휘 자료의 언어일 때만 (원어 · 그리고 *_en · *_ko 자료가 있는 영어 · 한국어).
     * 없으면 null (그 자리는 그냥 글자).
     */
    fun glossTokens(series: String, letterId: String, msg: Int): List<io.github.graviton94.todayletters.core.Token>? {
        val w = works.firstOrNull { it.series.id == series } ?: return null
        if (w.lexicon == null || room(series).learn != w.learnVocab) return null
        return Library.gloss(ctx, series, w.deck)[letterId]?.getOrNull(msg)
    }

    /** 지금 배우는 말의 카드만 (언어마다 진도가 따로, v22): 영어로 배우는 동안에는 프랑스어 카드가 복습에 나오지 않는다. */
    fun cards() = run {
        @Suppress("UNUSED_EXPRESSION") version
        val decks = works.associate { it.series.id to it.deck }
        store.cards().filter { c -> CardRef.parse(c.key)?.let { decks[it.series] == it.deck } == true && cardWord(c.key) != null }
    }
    /** 오늘 볼 카드: [id] 가 있으면 그 작품의 카드만 (오늘의 여정 II · 서재 카드의 숫자), 없으면 모든 작품. */
    fun dueCards(id: String? = null) = io.github.graviton94.todayletters.core.Memory.dueToday(
        cards().filter { c -> id == null || CardRef.parse(c.key)?.series == id }, today)
    /** 지금 배우는 말의 이 작품 카드 (복습 탭 · 자유 연습은 지금 작품의 것만, F08). */
    fun seriesCards(id: String = current) = cards().filter { c -> CardRef.parse(c.key)?.series == id }

    /** 오늘의 복습 (여정 II) 의 답: 기억 칸이 움직이고, 맞히면 그 작품 화폐 하나. */
    fun answer(c: io.github.graviton94.todayletters.core.Card, correct: Boolean) = record(io.github.graviton94.todayletters.core.Memory.after(c, correct, today), c, if (correct) Earn.REVIEW_RIGHT else null)
    /** 자유 연습의 답 (F09): 기록은 되지만 (core 의 Memory.practiced) 화폐는 여정에서만. */
    fun practiced(c: io.github.graviton94.todayletters.core.Card, correct: Boolean) = record(io.github.graviton94.todayletters.core.Memory.practiced(c, correct, today), c, null)
    private fun record(next: io.github.graviton94.todayletters.core.Card, c: io.github.graviton94.todayletters.core.Card, e: Earn?) {
        if (next.box == io.github.graviton94.todayletters.core.Memory.TOP && c.box < io.github.graviton94.todayletters.core.Memory.TOP) store.logDay("own", today, c.key)
        store.save(next)
        if (e != null) earn(e, id = c.key.substringBefore(":"))
        version++
    }

    /** 오늘의 복습 한 판의 저장 이름: 작품의 진도 이름마다 (빈센트를 프랑스어 · 영어로 따로). */
    fun reviewSession(id: String = current) = "today:${space(id)}"

    /** 오늘의 복습 (여정 II) 을 끝까지 풀었다. */
    fun reviewDone() {
        store.setReviewedOn(space(current), today); finished()
        draw(current, "review")
        journeyCheck(current)
        version++; settle()
    }

    // ── 오늘의 여정 (core 의 Journey): 오늘의 편지 → 오늘의 복습 → 여정 완료 ─────────────
    /** 이 작품에서 오늘 마친 편지 열쇠 (이번 주 기록에서). */
    private fun doneToday(id: String): List<String> = store.dayLog("letters").filter { it.first == today && it.second.startsWith("${space(id)}:") }.map { it.second }

    /**
     * 오늘의 편지: 오늘 이미 마친 편지가 있으면 그 편지 (여정 I 의 ✓ 와 아래 카드가 같은 편지를 가리키게 — 다른 안 읽은 편지를
     * 오늘 것처럼 보여 주지 않는다. 더 받은 편지는 목차에서). 없으면 열 수 있는 편지 가운데 아직 안 마친 첫 편지. 둘 다 없으면 null (내일 온다).
     */
    fun todayLetter(id: String): Pair<String, Letter>? {
        val open = openable(id)
        val keys = doneToday(id).toSet()
        if (keys.isNotEmpty()) open.lastOrNull { (c, l) -> key(id, c, l.id) in keys }?.let { return it }
        return open.firstOrNull { (c, l) -> !progress(id, c, l.id).done }
    }

    /** 오늘의 여정 한 장의 상태. [reviewCount] 는 (이 판에서 답한 수, 이 판의 문제 수). */
    data class JourneyView(val letter: Journey.Step, val review: Journey.Step, val reviewCount: Pair<Int, Int>, val paid: Boolean) {
        val complete get() = Journey.complete(letter, review)
        val done get() = Journey.doneCount(letter, review, paid)
    }

    fun journey(id: String = current): JourneyView = run {
        @Suppress("UNUSED_EXPRESSION") version
        val sp = space(id)
        val tl = todayLetter(id)
        val letterStep = Journey.letter(
            doneToday = doneToday(id).isNotEmpty(),
            available = tl != null && !progress(id, tl.first, tl.second.id).done,
            started = tl != null && progress(id, tl.first, tl.second.id).shown > 0,
        )
        val saved = store.session(reviewSession(id))?.takeIf { it.day == today }
        val finished = store.reviewedOn(sp) == today
        val total = if (saved != null) saved.keys.size else if (finished) 0 else dueCards(id).size
        val answered = saved?.at ?: 0
        val reviewStep = Journey.review(total, answered, finished)
        JourneyView(letterStep, reviewStep, Journey.reviewCount(total, answered, finished), store.journeyPaid(sp) == today)
    }

    /** 여정을 다 했으면 (하루 한 번): 보너스 화폐 + 오늘 편지의 그림이 갤러리에 (보상 순간은 [settle] 이 띄운다). */
    fun journeyCheck(id: String) {
        val j = journey(id)
        if (!Journey.payable(j.letter, j.review, store.journeyPaid(space(id)), today)) return
        store.setJourneyPaid(space(id), today)
        earn(Earn.JOURNEY, id = id)
        todayLetter(id)?.second?.let { l -> letterPieces(id).firstOrNull { it.id == "letter:${l.id}" } }?.let { pendingPiece = it }
        version++
    }

    /** 무료 장 (잠긴 장 바로 앞까지) 에서 아직 안 마친 편지 수와 마지막 무료 장 번호. 잠긴 장이 없거나 소장 · 구독했으면 null. */
    fun freeLeft(id: String = current): Pair<Int, Int>? {
        val w = work(id)
        if (entitlement.owns(id)) return null
        val lastFree = (io.github.graviton94.todayletters.core.Access.lockedRange(w.chapters) ?: return null).first - 1
        if (lastFree < 1) return null
        return w.chapters.take(lastFree).sumOf { c -> c.letters.count { !progress(id, c.id, it.id).done } } to lastFree
    }

    /** 내일 새 편지가 오는가: 아직 오지 않은 편지가 있고, 그 편지의 장이 열려 있다 (잠긴 장이면 ‘내일 온다’고 말하지 않는다). */
    fun moreComing(id: String): Boolean {
        val next = all(id).getOrNull(openable(id).size) ?: return false
        return chapterIdOpen(id)(next.first)
    }

    /** 도착 알림 켜기 (오늘 화면의 ‘알림 켜기’): 모든 작품을 저장된 시각으로. */
    fun noticeOn() {
        works.forEach { wk -> update(wk.series.id, seriesSettings(wk.series.id).copy(arrivalNotice = true)) }
        io.github.graviton94.todayletters.data.Notices.schedule(ctx)
    }

    /** 건너뛴 걸음 (복습 탭의 ‘다시 하기’): 이 작품에서 마친 편지 가운데 아직 다시 하지 않은 걸음, 편지 차례대로. */
    data class Redo(val room: Route.Letter, val letter: Letter, val mode: ReplyMode)
    fun redos(id: String = current): List<Redo> = run {
        @Suppress("UNUSED_EXPRESSION") version
        work(id).chapters.flatMapIndexed { ci, c -> c.letters.flatMapIndexed { li, l ->
            progress(id, c.id, l.id).toRedo.filter { it in letterModes(id, c.id, l) }.map { Redo(Route.Letter(id, ci + 1, li + 1), l, it) }
        } }
    }

    // ── 내 구절 ──────────────────────────────────────────────
    /** 구절 열쇠: 진도처럼 배우는 말마다 따로 ("vincent@en:I:c1-l2:0", [space]). */
    fun quoteKey(series: String, chapter: String, letter: String, msg: Int) = "${space(series)}:$chapter:$letter:$msg"
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
        (1..used).forEach { store.logDay("rest", today - it, "rest") }
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
        // 잠긴 장 (III장부터, 소장 · 구독 전): 편지 대신 구매 화면 (이 시리즈 소장이 셋째 칸)
        work(id).chapters.firstOrNull { it.id == chapter }?.let { c -> if (!chapterOpen(c)) { current = id; go(Route.Purchase); return } }
        // 앱 밖(R2)에 둔 장이고 아직 받지 않았으면: 먼저 크기를 알려 주고 받을지 묻는다
        // 서문 · 무료 장이면 처음 묶음으로 한 번에
        val pack = downloads.needed(id, chapter)
        if (!skipAudio && pack != null && !store.silent("$id:$chapter")) {
            downloadAsk = DownloadAsk(pack, id, pack.chapter, letter, from); return
        }
        val p = progress(id, chapter, letter.id)
        current = id
        if (!replaying(id, chapter, letter.id) && !store.started(key(id, chapter, letter.id)) && p.shown == 0 && !p.done) store.markOpened(space(id))
        store.markStarted(key(id, chapter, letter.id))
        // 처음 열 때의 읽을 수 있는 비율 (다시 읽을 때 견준다)
        if (store.firstCoverage(key(id, chapter, letter.id)) < 0) coverage(id, letter.id)?.let { store.setFirstCoverage(key(id, chapter, letter.id), it) }
        val idx = work(id).chapters.indexOfFirst { it.id == chapter } + 1
        go(Route.Letter(id, idx, work(id).chapters[idx - 1].letters.indexOf(letter) + 1, from))
    }

    /**
     * 받기를 마쳤거나 소리 없이 읽기를 골랐을 때: 기다리던 편지 · 화면을 연다.
     * 소리 없이 읽기는 그 장 (서문 "작품:P" · 맺음 "작품:E" · 편지의 장) 을 다시 묻지 않게만 적는다. 받은 것으로 치지 않으므로 표지 · 설정의 받기 단추는 그대로.
     */
    fun downloadFinished(silent: Boolean) {
        val a = downloadAsk ?: return
        downloadAsk = null
        version++
        a.then?.let {
            if (silent) when (it) { is Route.Prologue -> store.markSilent("${a.series}:P"); is Route.Outro -> store.markSilent("${a.series}:E"); else -> {} }
            go(it); return
        }
        val l = a.letter
        if (l == null) {
            // 처음 묶음을 표지 · 들어올 때 받았다: ‘첫 장 열기’ 는 I장으로
            if (a.chapter == io.github.graviton94.todayletters.data.Downloads.FIRST) { current = a.series; go(Route.Chapter(a.series, 1)) }
            return
        }
        val ch = work(a.series).chapters.firstOrNull { c -> l in c.letters }?.id ?: return
        if (silent) store.markSilent("${a.series}:$ch")
        open(a.series, ch, l, a.from, skipAudio = true)
    }

    /** 받기 시트를 ‘다음에’ · 뒤로 닫는다 (받는 중이면 받기는 이어진다). 처음 묶음은 다시 저절로 묻지 않는다. */
    fun downloadLater() {
        downloadAsk?.let { if (it.chapter == io.github.graviton94.todayletters.data.Downloads.FIRST) store.markFirstAsked(it.series) }
        downloadAsk = null
        version++
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
                // 그 장을 받지 않았으면 (소리 없이 읽기) 걸을 그림이 없다
                if (!io.github.graviton94.todayletters.data.AssetFiles.has(ctx, "plates/${pl.image}")) return@forEachIndexed
                val pr = progress(series, c.id, l.id)
                if (pr.shown == 0 && !pr.done) return@forEachIndexed
                return pl.spots.size to { go(Route.Artwork(series, li + 1, Route.Letter(series, ci + 1, li + 1, Route.Gallery))) }
            }
        }
        return null
    }
    /** 기억 확인 (F7, 복습 탭): 오늘 아직 안 냈으면, 오래전 ‘내 것’ 낱말 하나 (이 작품의 것). */
    fun surprise(): io.github.graviton94.todayletters.core.Card? =
        if (store.surpriseDay == today) null else io.github.graviton94.todayletters.core.Memory.surprise(seriesCards(), today)
    /** 기억났는가: 맞으면 일정은 그대로, 아니면 내려가서 내일 다시. 하루 한 번 (화폐는 여정에서만). */
    fun surpriseDone(c: io.github.graviton94.todayletters.core.Card, ok: Boolean) {
        store.save(io.github.graviton94.todayletters.core.Memory.surpriseAnswered(c, ok, today))
        store.surpriseDay = today
        cue(ok)
        version++
    }
    /** 받아 둔 낭독을 모두 지운다 (설정 › 저장 공간). */
    fun clearDownloads() { io.github.graviton94.todayletters.data.DownloadJob.stop(); downloads.clear(); version++ }
    /** 미리 받기: 아직 도착하지 않은 장도 (무료 장 · 소장 · 구독한 장). [chapter] 가 null 이면 모든 장. */
    fun predownload(series: String, chapter: String?) {
        val pack = when (chapter) {
            null -> downloads.all(series, chapterIdOpen(series))
            io.github.graviton94.todayletters.data.Downloads.FIRST -> downloads.first(series)
            in downloads.firstChapters(series) -> downloads.first(series)
            else -> downloads.pack(series, chapter)?.takeIf { chapterIdOpen(series)(chapter) }
        }
        if (pack == null || downloads.ready(pack)) return
        downloadAsk = DownloadAsk(pack, series, pack.chapter, null, null)
    }

    fun letterOf(r: Route.Letter): Pair<String, Letter> {
        // 맺음 편지: 아웃트로 화면에서 답장 걸음만 (장 "E")
        if (r.epilogue) return "E" to work(r.series).outro!!.epilogue!!
        val ch = work(r.series).chapters[r.chapter - 1]
        return ch.id to ch.letters[r.letter - 1]
    }

    /** 이 편지 낭독이 이 기기에 있나 (첫 문장, 지금 배우는 말). */
    fun narrated(id: String, chapter: String, l: Letter) = l.messages.isNotEmpty() && narrator.has(narrator.path(id, chapter, l.id, "m1_${seriesSettings(id).learn.code}"))

    /** 이 편지에서 하는 걸음: 작품 설정에서 켠 것, 낭독이 없는 편지는 따라 읽기 없이 (F11). 이해 확인 · 오늘의 회화는 편지에 데이터가 있을 때만. */
    fun letterModes(id: String, chapter: String, l: Letter): List<ReplyMode> = Plays.forLetter(seriesSettings(id).modes, narrated(id, chapter, l), l)

    fun replied(r: Route.Letter, mode: ReplyMode) {
        val (ch, l) = letterOf(r)
        save(r.series, ch, l.id, progress(r.series, ch, l.id).reply(mode).complete(stepsOf(r)))
    }

    /** 걸음을 마치고 대화방으로 (건너뛴 걸음을 다시 한 것이면 들어온 곳으로). 이 걸음으로 편지가 끝났으면 이어 읽은 날을 세고 편지 완료 화면으로. */
    fun replyAndContinue(r: Route.Letter, mode: ReplyMode) {
        val (ch, l) = letterOf(r)
        val before = progress(r.series, ch, l.id)
        if (again(r)) {
            // 다시 보기: 전에 건너뛴 걸음을 이번에 처음 했으면 저장된 진도에도 한 것으로 · 그 걸음 보상 한 번 (core 의 Replay)
            val k = key(r.series, ch, l.id)
            val (saved, pay) = io.github.graviton94.todayletters.core.Replay.stepDone(store.progress(k), mode)
            store.save(k, saved)
            if (pay) earn(Earn.PRACTICE, id = r.series)
        } else if (mode !in before.replied && !replaying(r)) earn(Earn.PRACTICE, id = r.series)
        replied(r, mode)
        stepEnded(r, before.done)
    }

    /**
     * 답장 한 턴을 마쳤다: 쓴 답장은 턴마다 오늘의 루틴(복습 · 말하기)에서 다시 본다 ([CardRef.reply] 턴 번호까지).
     * 한국어를 배우면 그 턴의 말투 바꿔 보기 카드도 ([CardRef.style], 반말 · 높임이 있을 때). 다시 하기 · 저자 답장이 없는 편지는 들이지 않는다.
     */
    fun replyTurnDone(r: Route.Letter, turn: Int) {
        if (replaying(r)) return
        val (ch, l) = letterOf(r)
        val t = l.replies.getOrNull(turn) ?: return
        val deck = deckOf(r.series)
        admit(listOfNotNull(CardRef.reply(r.series, ch, l.id, deck, turn),
            if (room(r.series).learn == io.github.graviton94.todayletters.core.Lang.KO && t.styled) CardRef.style(r.series, ch, l.id, deck, turn) else null))
    }

    /** 걸음 건너뛰기: 편지를 마치는 데는 한 것으로, 건너뛴 표시는 남겨 ‘다시 하기’로 (편지 목록 · 복습 탭). */
    fun skipStep(r: Route.Letter, mode: ReplyMode) {
        val (ch, l) = letterOf(r)
        val before = progress(r.series, ch, l.id)
        save(r.series, ch, l.id, before.skip(mode).complete(stepsOf(r)))
        // 처음 한 번만: 건너뛴 걸음이 어디 있는지 (복습 › 다시 하기) 짧게
        if (!store.skipNoted) {
            store.skipNoted = true
            android.widget.Toast.makeText(ctx, ctx.getString(io.github.graviton94.todayletters.R.string.skip_note), android.widget.Toast.LENGTH_LONG).show()
        }
        stepEnded(r, before.done)
    }

    /** 이 편지에서 하는 걸음 (맺음 편지는 답장 하나). */
    private fun stepsOf(r: Route.Letter): List<ReplyMode> =
        if (r.epilogue) listOf(ReplyMode.CONSTELLATION) else letterOf(r).let { (ch, l) -> letterModes(r.series, ch, l) }

    private fun stepEnded(r: Route.Letter, was: Boolean) {
        val (ch, l) = letterOf(r)
        recorder.stop()
        back()
        // 맺음 편지의 답장: 아웃트로로 돌아갈 뿐 (편지 완료 화면 · 편지 보상 없음)
        if (r.epilogue) { settle(); return }
        if (!was && progress(r.series, ch, l.id).done) letterDone(r)
        settle()
    }

    /** 편지를 막 마쳤다 (마지막 걸음을 했거나 건너뛰었다): 처음이면 보상 · 오늘의 편지 마침 한 장, 다시 보기면 보상 없이 끝. 그리고 편지 완료 화면. */
    private fun letterDone(r: Route.Letter) {
        val (ch, l) = letterOf(r)
        val replay = replaying(r)
        if (io.github.graviton94.todayletters.core.Replay.paysLetter(savedDoneBefore = again(r), replaying = replay)) {
            finished(); earn(Earn.LETTER, id = r.series)
            finishSheet = key(r.series, ch, l.id)
        }
        if (again(r)) store.endAgain()
        go(Route.Done(r))
    }

    /** 걸음이 없는 편지 (읽기만): 대화방의 ‘오늘은 여기까지’ 로 마친다. */
    fun finishReading(r: Route.Letter) {
        val (ch, l) = letterOf(r)
        save(r.series, ch, l.id, progress(r.series, ch, l.id).complete(stepsOf(r)).copy(done = true))
        letterDone(r)
        settle()
    }

    // ── 보상 엔진 (모든 시리즈 공통: 시리즈 화폐 · 오늘의 자료 · 이정표) ───────────
    /** 한 번에 보여 줄 보상 순간: 받은 화폐 · 걸린 자료 · 새 이정표 · 혼자 읽기 이정표. */
    data class RewardMoment(
        val series: String,
        val gain: Int,
        val piece: Piece?,
        /** 새로 받은 메달 id (core 의 Medals). */
        val medals: List<String>,
        val milestone: Int?,
        val lines: List<Pair<Earn, Int>> = emptyList(),
        /** 이달의 전시를 방금 다 모았으면 그 전시 (도록 표지, E4). */
        val catalogue: Exhibition? = null,
    )
    var reward by mutableStateOf<RewardMoment?>(null)
    /** 레벨 업 순간: [from] → [to] (한 번에 여러 레벨을 올라도 하나만). 보상 순간이 닫힌 뒤에 뜬다. */
    data class LevelUp(val from: Int, val to: Int)
    var levelUp by mutableStateOf<LevelUp?>(null)
    private var pending = mutableMapOf<Earn, Int>()
    private var pendingPiece: Piece? = null

    /** 시리즈 지갑. */
    fun wallet(id: String = current): Wallet = run { @Suppress("UNUSED_EXPRESSION") version; store.wallet(id) }
    val wallet: Wallet get() = wallet(current)

    fun earn(e: Earn, times: Int = 1, id: String = current): Int {
        xpFor(e)?.let { xp(it, times) }
        val (w, g) = Rewards.earn(store.wallet(id), e, today, times)
        store.saveWallet(id, w); if (g > 0) pending[e] = (pending[e] ?: 0) + g
        return g
    }
    /** 화폐를 받는 일 가운데 XP 도 받는 것 (medals.json 의 levels.xp). */
    private fun xpFor(e: Earn): XpAction? = when (e) {
        Earn.LETTER -> XpAction.LETTER_READ
        Earn.PRACTICE -> XpAction.PRACTICE
        Earn.REVIEW_RIGHT -> XpAction.REVIEW_RIGHT
        Earn.JOURNEY -> XpAction.QUESTS_DONE
        Earn.STREAK -> XpAction.STREAK_DAY
        Earn.ACHIEVEMENT -> XpAction.MEDAL
        Earn.SEAL -> XpAction.LETTER_SEALED
        else -> null
    }

    // ── 메달 진열장 · 끝없는 레벨 (core 의 Medals · Levels, assets/medals/medals.json) ─────────
    val medalBook: MedalBook by lazy {
        runCatching { Medals.parse(ctx.assets.open("medals/medals.json").bufferedReader().readText()) }.getOrDefault(MedalBook(emptyList(), emptyList()))
    }

    /** XP 받기 (하루 상한 안에서). 레벨 업 순간은 [settle] 이 한 번에 띄운다. */
    fun xp(a: XpAction, times: Int = 1): Int {
        val (l, g) = Xp.gain(store.xp(), a, today, times)
        if (g > 0) store.saveXp(l)
        return g
    }
    val level: Level get() = run { @Suppress("UNUSED_EXPRESSION") version; Levels.of(store.xp().total) }

    /** 메달 숫자: 모든 작품 · 모든 배우는 말을 합쳐서 (한 번 한 일은 언어를 바꿔도 남는다). */
    fun medalStats(): Stats {
        @Suppress("UNUSED_EXPRESSION") version
        return Stats(
            lettersDone = store.progressKeys().count { store.progress(it).done },
            streak = streak, bestStreak = maxOf(store.bestStreak, streak),
            wordsKnown = store.cards().count { Growth.known(it) },
            lettersSealed = store.sealed.size,
            readAlongStars = maxOf(store.stars().values.sum(), store.starred.size),
            walksDone = store.walks.size,
            bestGrade = io.github.graviton94.todayletters.core.Lang.entries.maxOf { store.grade(it.code) },
            seriesCompleted = store.seriesDone + baseWorks.map { it.series.id }.filter { id -> work(id).chapters.isNotEmpty() && seriesDone(id) },
        )
    }
    /** 받은 메달 (id) · 받은 날. */
    val medalsOwned: Set<String> get() = run { @Suppress("UNUSED_EXPRESSION") version; store.medals }
    fun medalDay(id: String): Long? = run { @Suppress("UNUSED_EXPRESSION") version; store.medalDay(id) }

    /** 따라 읽기 별: 마디마다 가장 많이 받은 별만 센다 (같은 마디를 되풀이해 별을 불리지 않게). 새로 늘어난 별만큼 XP. */
    fun recordStars(id: String, chapter: String, letter: String, msg: Int, chunk: Int, n: Int) {
        if (n <= 0) return
        val k = "${key(id, chapter, letter)}:$msg:$chunk"
        val old = store.stars()[k] ?: 0
        if (n > old) { store.setStars(k, n); xp(XpAction.READALONG_STAR, n - old); version++ }
    }

    /** 그림 산책을 마지막 자리까지 걸었다 (편지마다 한 번 센다). */
    fun walkDone(series: String, letterId: String) {
        val k = "$series:$letterId"
        if (k in store.walks) return
        store.addWalk(k); xp(XpAction.WALK_DONE); version++
    }

    /**
     * 예전 이정표 → 메달 (한 번): 겹치는 이정표는 그 메달로 (받은 날 그대로). 그리고 처음 레벨이 생길 때 그동안의 기록으로 XP 를 채운다
     * (레벨 업 순간 없이, 조용히).
     */
    private fun migrateMedals() {
        if (!store.medalsMigrated) {
            val old = store.achievements
            Medals.migrate(old).forEach { mid ->
                val day = old.filter { mid in Medals.migrate(setOf(it)) }.mapNotNull { store.achievedDay(it)?.takeIf { d -> d >= 0 } }.minOrNull() ?: today
                store.awardMedal(mid, day)
            }
            store.medalsMigrated = true
        }
        if (!store.xpSeeded) {
            val ms = medalStats()
            val seed = Xp.seed(ms, ms.lettersSealed, store.medals.size, store.doneDays().size)
            if (seed > 0) store.saveXp(store.xp().copy(total = store.xp().total + seed))
            store.wordsSeen = ms.wordsKnown
            store.seriesDone.let { done -> ms.seriesCompleted.filter { it !in done }.forEach(store::addSeriesDone) }
            store.levelSeen = Levels.of(store.xp().total).level
            store.xpSeeded = true
        }
    }

    fun spend(cost: Int, id: String = current): Boolean {
        val w = Rewards.spend(store.wallet(id), cost) ?: return false
        store.saveWallet(id, w); version++; return true
    }

    /** 상점에서 지금 살 수 있나 (화폐와 조건). 고르는 것 (갤러리 칸 · 액자) 은 고를 것이 있는지만 본다. */
    fun canBuy(what: Spend, id: String = current): Boolean {
        if (wallet(id).balance < what.cost) return false
        return when (what) {
            Spend.REST -> store.rests < Streak.MAX_RESTS
            Spend.EARLY_LETTER -> store.earlyDay(space(id)) != today && all(id).size > openable(id).size
            Spend.PICK_PIECE -> work(id).kit.collection.any { !owns(id, it) && available(it) }
            Spend.FRAME -> gallery(id).any { owns(id, it) && available(it) }
            Spend.BORDER -> true
        }
    }

    /** 화폐로 사기. 쉼표는 [Streak.MAX_RESTS] 개까지. */
    fun buy(what: Spend, id: String = current, piece: Piece? = null, option: String? = null): Boolean {
        when (what) {
            Spend.REST -> if (store.rests >= Streak.MAX_RESTS) return false
            Spend.EARLY_LETTER -> if (store.earlyDay(space(id)) == today || all(id).size <= openable(id).size) return false
            Spend.PICK_PIECE -> if (piece == null || owns(id, piece) || !available(piece)) return false
            Spend.FRAME -> if (piece == null || option == null || !owns(id, piece) || frameKind(piece).name == option) return false
            Spend.BORDER -> if (option == null || option in store.borders(id)) return false
        }
        if (!spend(what.cost, id)) return false
        when (what) {
            Spend.REST -> store.rests = store.rests + 1
            Spend.EARLY_LETTER -> store.setEarlyDay(space(id), today)
            Spend.PICK_PIECE -> { store.addPiece(id, piece!!.id); store.logDay("pieces", today, "$id|${piece.id}") }
            Spend.FRAME -> store.setFrame(piece!!.id, option!!)
            Spend.BORDER -> { store.addBorder(id, option!!); store.setBorder(id, option) }
        }
        version++; settle(); return true
    }

    /** 이 작품의 액자: 고른 것, 없으면 등급의 기본 (E3). */
    fun frameKind(p: Piece): io.github.graviton94.todayletters.data.Frames.Kind = run {
        @Suppress("UNUSED_EXPRESSION") version
        store.frameOf(p.id)?.let { n -> io.github.graviton94.todayletters.data.Frames.Kind.entries.firstOrNull { it.name == n } } ?: io.github.graviton94.todayletters.data.Frames.kindOf(p.tier)
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
    /** 이 작품의 그림이 지금 기기에 있는가 (앱 안 맛보기이거나 받아 둔 장의 것). 없는 소장품은 뽑기 · 고르기에 넣지 않는다. */
    fun available(p: Piece) = io.github.graviton94.todayletters.data.AssetFiles.has(ctx, p.image)
    fun ownedCount(id: String) = gallery(id).count { owns(id, it) }

    /**
     * 오늘의 자료 한 점을 건다. 같은 날 같은 [reason] 으로는 한 번만 (다시 열어도 같은 그림).
     * 이달의 전시 주제가 두 배로 잘 나온다. [minTier] 는 이정표의 확정 명작.
     */
    fun draw(id: String, reason: String, minTier: Tier = Tier.SKETCH): Piece? {
        val tag = "$today:$reason"
        if (tag in store.drawTags(id)) return null
        val pool = work(id).kit.collection.filter { available(it) }
        val p = Draws.pick(pool, store.pieces(id), (today * 131 + reason.hashCode()).toLong() xor id.hashCode().toLong(), exhibition(id)?.theme, minTier) ?: return null
        store.addPiece(id, p.id); store.addDrawTag(id, tag); store.logDay("pieces", today, "$id|${p.id}")
        pendingPiece = p
        version++
        return p
    }

    // ── 이달의 전시 ──────────────────────────────────────────
    fun exhibition(id: String = current): Exhibition? = Draws.exhibition(work(id).kit.exhibitions, LocalDate.now().monthValue - 1)
    fun exhibitionPieces(id: String = current): List<Piece> = exhibition(id)?.let { Draws.exhibitionPieces(work(id).kit.collection, it) } ?: emptyList()
    /** 도록 열쇠: 전시 id · 연월 (같은 전시도 해가 바뀌면 다시). */
    fun catalogueKey(e: Exhibition) = "${e.id}:${LocalDate.now().year}-${LocalDate.now().monthValue}"
    /** 다 모은 전시들 (전시 · 연 · 월). */
    fun catalogues(id: String = current): List<Triple<Exhibition, Int, Int>> = run {
        @Suppress("UNUSED_EXPRESSION") version
        store.catalogues().mapNotNull { k ->
            val e = work(id).kit.exhibitions.firstOrNull { it.id == k.substringBefore(":") } ?: return@mapNotNull null
            val (y, m) = k.substringAfter(":").split("-").map { it.toIntOrNull() ?: 0 }
            Triple(e, y, m)
        }.sortedWith(compareBy({ it.second }, { it.third }))
    }

    // ── 이번 주 (E1) ─────────────────────────────────────────
    data class Week(
        val start: Long, val done: Set<Int>, val rests: Set<Int>, val letters: Int, val owned: Int, val sentences: Int,
        /** 요일(0=월)마다 (억양, 리듬) 평균. 따라 읽은 날만. */
        val scores: Map<Int, Pair<Int, Int>>,
        val lastWeek: Pair<Int, Int>?, val pieces: List<Piece>,
    )
    fun week(id: String = current): Week = run {
        @Suppress("UNUSED_EXPRESSION") version
        val start = io.github.graviton94.todayletters.core.Recital.weekStart(today)
        val range = start..start + 6
        fun inWeek(kind: String) = store.dayLog(kind).filter { it.first in range }
        val sc = store.scores()
        val days = sc.filterKeys { it in range }.filterValues { it.third > 0 }.map { (d, v) -> (d - start).toInt() to (v.first / v.third to v.second / v.third) }.toMap()
        val last = sc.filterKeys { it in (start - 7) until start }.values.let { v -> v.sumOf { it.third }.takeIf { it > 0 }?.let { n -> v.sumOf { it.first } / n to v.sumOf { it.second } / n } }
        val pieceIds = inWeek("pieces").map { it.second }.filter { it.startsWith("$id|") }.map { it.substringAfter("|") }.toSet()
        Week(start, weekDone(), inWeek("rest").map { (it.first - start).toInt() }.toSet(),
            inWeek("letters").count { it.second.startsWith("${space(id)}:") }, inWeek("own").count { k -> CardRef.parse(k.second)?.let { it.series == id && it.deck == deckOf(id) } == true },
            sc.filterKeys { it in range }.values.sumOf { it.third }, days, last,
            gallery(id).filter { it.id in pieceIds })
    }

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

    // ── 읽을 수 있는 비율 · 언어 등급 (core 의 Reading) ─────────────
    /** 이 작품의 어휘 자료가 지금 배우는 말의 것이면 그 작품 (아니면 null: 셀 낱말이 없다). */
    private fun vocabWork(series: String): Work? =
        works.firstOrNull { it.series.id == series }?.takeIf { it.lexicon != null && room(series).learn == it.learnVocab }

    private var mineMemo: Pair<Int, Map<io.github.graviton94.todayletters.core.Lang, Set<String>>> = -1 to emptyMap()
    /** 배우는 말 [lang] 에서 내 것이 된 낱말 (사전 열쇠): 그 말을 배우는 모든 작품의 카드 가운데 떠올리기 이상. */
    fun mine(lang: io.github.graviton94.todayletters.core.Lang): Set<String> {
        if (mineMemo.first != version) mineMemo = version to emptyMap()
        mineMemo.second[lang]?.let { return it }
        val got = cards().filter { Growth.known(it) }.mapNotNull { c ->
            val ref = CardRef.parse(c.key) ?: return@mapNotNull null
            if (seriesSettings(ref.series).learn != lang) return@mapNotNull null
            if (ref.own.isNotEmpty()) ref.own else cardWord(c.key)?.word?.takeIf { !it.phrase }?.lex?.takeIf { it.isNotEmpty() }
        }.toSet()
        mineMemo = version to (mineMemo.second + (lang to got))
        return got
    }

    /** 이 작품에서 아는 낱말인가 (기능어 · 이름 · 숫자, 또는 내 것이 된 낱말). */
    fun knows(series: String): (String) -> Boolean {
        val w = vocabWork(series) ?: return { false }
        val m = mine(room(series).learn)
        return { k -> k in m || io.github.graviton94.todayletters.core.Reading.free(w.lexicon?.get(k)) }
    }

    /** 편지 한 통 (또는 그 문장 [msg]) 의 낱말 사전 열쇠, 나온 차례대로. 어휘 자료가 없으면 null. */
    fun letterKeys(series: String, letterId: String, msg: Int? = null): List<String>? {
        val w = vocabWork(series) ?: return null
        val ms = Library.gloss(ctx, series, w.deck)[letterId] ?: return null
        return (if (msg == null) ms.flatten() else ms.getOrNull(msg).orEmpty()).map { it.key }
    }

    /** 이 편지의 읽을 수 있는 비율 (%). 셀 낱말이 없으면 null. */
    fun coverage(series: String, letterId: String): Int? =
        letterKeys(series, letterId)?.takeIf { it.isNotEmpty() }?.let { io.github.graviton94.todayletters.core.Reading.pct(it, knows(series)) }

    /** 이 편지를 처음 열 때의 비율 (없으면 null). */
    fun firstCoverage(series: String, chapter: String, letterId: String): Int? = store.firstCoverage(key(series, chapter, letterId)).takeIf { it >= 0 }

    /** 지금 배우는 말들 (편지를 연 작품의 것, 서재 순서로 겹치지 않게). 아무 데도 시작하지 않았으면 지금 작품의 말. */
    val learning: List<io.github.graviton94.todayletters.core.Lang>
        get() = works.filter { startedCount(it.series.id) > 0 }.map { room(it.series.id).learn }.distinct()
            .ifEmpty { listOfNotNull(current.takeIf { id -> works.any { it.series.id == id } }?.let { room(it).learn }) }

    /** 배우는 말 [lang] 의 최근 [io.github.graviton94.todayletters.core.Reading.RECENT] 통 (다 읽은 편지) 의 읽을 수 있는 비율 평균. */
    fun recentCoverage(lang: io.github.graviton94.todayletters.core.Lang): Int =
        io.github.graviton94.todayletters.core.Reading.average(works.filter { room(it.series.id).learn == lang }.flatMap { w ->
            val id = w.series.id
            w.chapters.flatMap { c -> c.letters.filter { progress(id, c.id, it.id).done }.mapNotNull { coverage(id, it.id) } }
        })

    /** 배우는 말 [lang] 의 언어 등급 (닿았던 등급 아래로는 내려가지 않는다). */
    fun grade(lang: io.github.graviton94.todayletters.core.Lang): io.github.graviton94.todayletters.core.Reading.Grade {
        if (gradeMemo.first != version) gradeMemo = version to emptyMap()
        gradeMemo.second[lang]?.let { return it }
        val g = io.github.graviton94.todayletters.core.Reading.grade(mine(lang).size, recentCoverage(lang), store.grade(lang.code).coerceAtLeast(1))
        gradeMemo = version to (gradeMemo.second + (lang to g))
        return g
    }
    private var gradeMemo: Pair<Int, Map<io.github.graviton94.todayletters.core.Lang, io.github.graviton94.todayletters.core.Reading.Grade>> = -1 to emptyMap()

    /** 이 작품에서 번역 줄을 접을 문장인가 ([msg] 번째 메시지). 작품 설정에서 껐거나 셀 낱말이 없으면 false. */
    fun folds(series: String, letterId: String, msg: Int): Boolean {
        if (!seriesSettings(series).autoFold) return false
        val keys = letterKeys(series, letterId, msg) ?: return false
        return io.github.graviton94.todayletters.core.Reading.folds(grade(room(series).learn).band, keys, knows(series))
    }

    /** 언어 등급이 오른 순간 (레벨 업과 같은 자리, 레벨 업보다 먼저). */
    data class GradeUp(val lang: io.github.graviton94.todayletters.core.Lang, val from: io.github.graviton94.todayletters.core.Reading.Grade, val to: io.github.graviton94.todayletters.core.Reading.Grade)
    var gradeUp by mutableStateOf<GradeUp?>(null)
    /** 배우는 말마다 지난주의 읽을 수 있는 비율 (정산 때 잰다, 없으면 빠짐). */
    var lastWeekCoverage by mutableStateOf<Map<io.github.graviton94.todayletters.core.Lang, Int>>(emptyMap())
        private set

    /** 정산의 한 부분: 언어 등급이 오른 만큼 XP, 가장 많이 오른 말 하나를 축하. 처음 재는 날은 조용히 적어만 둔다 (예전 사용자). */
    private fun settleGrades() {
        var best: GradeUp? = null
        val week = today / 7
        val prev = mutableMapOf<io.github.graviton94.todayletters.core.Lang, Int>()
        learning.forEach { lang ->
            val g = grade(lang)
            val was = store.grade(lang.code)
            if (was == 0) store.setGrade(lang.code, g.index)
            else if (g.index > was) {
                xp(XpAction.GRADE_UP, g.index - was)
                store.setGrade(lang.code, g.index)
                if (best == null || g.index - was > best!!.to.index - best!!.from.index) best = GradeUp(lang, io.github.graviton94.todayletters.core.Reading.Grade.of(was), g)
            }
            store.weekCoverage(lang.code, week, recentCoverage(lang))?.let { prev[lang] = it }
        }
        lastWeekCoverage = prev
        best?.let { gradeUp = it }
    }

    // ── 답장 사다리 (core 의 Ladder) ─────────────────────────
    /** 답장 카드의 쓰임 설명. */
    private val replyNote = io.github.graviton94.todayletters.core.Tri(mapOf(
        io.github.graviton94.todayletters.core.Lang.KO to ctx.getString(io.github.graviton94.todayletters.R.string.ladder_card_note_ko),
        io.github.graviton94.todayletters.core.Lang.EN to ctx.getString(io.github.graviton94.todayletters.R.string.ladder_card_note_en),
    ))

    /** 말투 바꿔 보기 카드의 쓰임 설명. */
    val styleNote = io.github.graviton94.todayletters.core.Tri(mapOf(
        io.github.graviton94.todayletters.core.Lang.KO to ctx.getString(io.github.graviton94.todayletters.R.string.style_card_note_ko),
        io.github.graviton94.todayletters.core.Lang.EN to ctx.getString(io.github.graviton94.todayletters.R.string.style_card_note_en),
    ))

    /** 이 작품의 답장 도움 (지금 배우는 말의 언어 등급만큼, [support]). */
    fun replyHelp(series: String): io.github.graviton94.todayletters.core.Ladder.Help =
        io.github.graviton94.todayletters.core.Support.reply(support(series))

    /**
     * 도움 사다리의 등급 (core 의 Support): 이 작품에서 배우는 말의 언어 등급, 작품 설정의 ‘도움 한 단계 더’ 면 한 등급 아래.
     * 이해 확인 · 회화 · 답장 · 답장 말해 보기 · 복습 문장 힌트가 모두 이것을 따른다 (번역 줄 물러나기는 등급 그대로).
     */
    fun support(series: String): io.github.graviton94.todayletters.core.Reading.Grade =
        io.github.graviton94.todayletters.core.Support.grade(grade(room(series).learn), seriesSettings(series).easier)

    /**
     * 이 작품의 기본 듣기 빠르기 (core 의 [io.github.graviton94.todayletters.core.Listen]): 작품 설정의 ‘등급에 맞춰 자동’ 이면 도움 사다리 등급대로,
     * 아니면 고른 값. 빠르기를 따로 주지 않은 모든 소리 ([Narrator.base]) 가 이것을 따른다. 0.7× 단추만 예외.
     */
    fun listenSpeed(series: String = current): Float =
        if (works.none { it.series.id == series }) 1f
        else io.github.graviton94.todayletters.core.Listen.speed(seriesSettings(series), support(series).band)

    init { narrator.base = { listenSpeed() } }

    /**
     * 낱말마다 익숙한 정도: 이 작품에서 지금 배우는 말의 낱말 꼴 → 그 낱말 카드의 복습 칸 (없으면 0).
     * 기능어 · 이름 · 숫자는 늘 아는 것으로 (가장 높은 칸): 빈칸 · 남겨 보일 낱말을 고를 때 ([io.github.graviton94.todayletters.core.Support]).
     */
    fun familiarity(series: String): (String) -> Int {
        val boxes = HashMap<String, Int>()
        cards().forEach { c ->
            val ref = CardRef.parse(c.key) ?: return@forEach
            if (ref.series != series) return@forEach
            val lex = if (ref.own.isNotEmpty()) ref.own else cardWord(c.key)?.word?.takeIf { !it.phrase }?.lex.orEmpty()
            if (lex.isNotEmpty()) boxes[lex] = maxOf(boxes[lex] ?: 0, c.box)
        }
        return { form ->
            val e = lexOf(series, io.github.graviton94.todayletters.core.Ladder.core(form))
            when {
                e == null -> 0
                io.github.graviton94.todayletters.core.Reading.free(e) -> io.github.graviton94.todayletters.core.Memory.TOP
                else -> boxes[e.key] ?: 0
            }
        }
    }

    private val formIndex = HashMap<io.github.graviton94.todayletters.core.Lexicon, Map<String, io.github.graviton94.todayletters.core.LexEntry>>()
    /** 사전에서 이 꼴의 표제어 (꼴 · 표제어, 대소문자 무시). */
    fun lexOf(series: String, form: String): io.github.graviton94.todayletters.core.LexEntry? {
        val lex = vocabWork(series)?.lexicon ?: return null
        val idx = synchronized(formIndex) {
            formIndex.getOrPut(lex) { buildMap { lex.entries.forEach { e -> (e.forms + e.lemma).forEach { f -> putIfAbsent(f.lowercase(), e) } } } }
        }
        return idx[form.lowercase()]
    }

    /**
     * 답장 낱말의 빈칸 점수: 기능어 · 이름은 0, 사전에 있는 낱말은 3 + (오늘의 낱말이면 2) + 길이,
     * 사전에 없으면 두 글자 이상이고 대문자로 시작하지 않을 때만 1 + 길이.
     */
    fun replyScore(series: String, letter: Letter): (String) -> Int {
        val learn = room(series).learn
        val today = letter.words.flatMap { w -> listOf(w.text[learn]) + w.forms }.map { io.github.graviton94.todayletters.core.Ladder.core(it).lowercase() }.toSet()
        return { c ->
            val e = lexOf(series, c)
            when {
                c.length < 2 -> 0
                e != null && io.github.graviton94.todayletters.core.Reading.free(e) -> 0
                e != null -> 3 + (if (c.lowercase() in today) 2 else 0) + c.length / 4
                c.first().isUpperCase() -> 0
                else -> 1 + c.length / 4
            }
        }
    }

    /**
     * 고르기의 미끼: (같은 낱말의 다른 꼴, 그 밖의 후보 = 이 편지의 오늘의 낱말 · 답장의 다른 낱말).
     * 문법 덩어리 빈칸 ([focus]) 이면 덩어리의 낱말마다 다른 꼴 ([io.github.graviton94.todayletters.core.Ladder.nearForms]: ai lu → lis · lire · lu).
     */
    fun replyDecoys(series: String, letter: Letter, answer: String, others: List<String>, focus: Boolean = false): Pair<List<String>, List<String>> {
        val learn = room(series).learn
        fun forms(w: String) = lexOf(series, w)?.let { it.forms + it.lemma }.orEmpty().filter { it.isNotEmpty() && !it.first().isUpperCase() }
        val near = (if (focus) io.github.graviton94.todayletters.core.Ladder.nearForms(answer, ::forms) else forms(answer))
            .filter { !it.equals(answer, ignoreCase = true) }
        val pool = letter.words.map { io.github.graviton94.todayletters.core.Ladder.core(it.text[learn].substringAfterLast(' ')) } + others
        return near to pool.filter { it.isNotEmpty() }
    }

    // ── 따라 읽기 · 완독 봉인 (모든 시리즈 공통) ─────────────────
    /**
     * 따라 읽기의 칸: 문장(메시지)마다, 긴 문장은 낭독 덩어리를 묶은 6~10 낱말 마디로 (v23, [io.github.graviton94.todayletters.core.Prosody.spans]).
     * 낭독 시각이 없으면 문장 통째 하나 (시각 −1).
     */
    fun shadowChunks(id: String, chapter: String, l: Letter): List<List<io.github.graviton94.todayletters.data.Chunk>> {
        val learn = seriesSettings(id).learn
        // 낱말 수: 띄어 쓰지 않는 일본어는 세 글자를 한 낱말로 어림
        fun words(t: String) = if (learn == io.github.graviton94.todayletters.core.Lang.JA) (t.count { !it.isWhitespace() } + 2) / 3 else t.split(Regex("\\s+")).count { it.isNotBlank() }
        return l.messages.mapIndexed { i, m ->
            val text = io.github.graviton94.todayletters.core.Breaks.plain(m.text[learn])
            val cs = narrator.chunks(narrator.path(id, chapter, l.id, "m${i + 1}_${learn.code}"))
            if (cs.isEmpty()) listOf(io.github.graviton94.todayletters.data.Chunk(-1, -1, text))
            else io.github.graviton94.todayletters.core.Prosody.spans(cs.map { words(it.text) }).map { r ->
                io.github.graviton94.todayletters.data.Chunk(cs[r.first].start, cs[r.last].end, r.joinToString(" ") { cs[it].text })
            }
        }
    }

    /** 마디 녹음 파일: files/readings/<편지>/m<문장>_c<마디>.wav (원음이라 억양 · 리듬을 잴 수 있다) */
    fun readingDir(id: String, chapter: String, letter: String) = java.io.File(ctx.filesDir, "readings/${space(id)}_${chapter}_$letter")
    fun take(id: String, chapter: String, letter: String, msg: Int, chunk: Int) = java.io.File(readingDir(id, chapter, letter), "m${msg}_c$chunk.wav")
    val pcm = io.github.graviton94.todayletters.data.PcmRecorder()
    /** 회화 내 차례 녹음: files/talk/<편지>/t<줄>.wav (따라 읽기와 같은 채점, 봉인 낭독에는 들지 않게 따로). */
    fun talkTake(id: String, chapter: String, letter: String, line: Int) = java.io.File(ctx.filesDir, "talk/${space(id)}_${chapter}_$letter/t$line.wav")
    /** 답장 말해 보기 녹음: files/talk/<편지>/r<턴>.wav. */
    fun replyTake(id: String, chapter: String, letter: String, turn: Int) = java.io.File(ctx.filesDir, "talk/${space(id)}_${chapter}_$letter/r$turn.wav")
    /** 복습 말하기 녹음 (한 칸, 문제마다 새로): cache/speak.wav. */
    val speakTake: java.io.File get() = java.io.File(ctx.cacheDir, "speak/take.wav")

    /** 억양 · 리듬을 통과한 마디인가. */
    fun passed(id: String, chapter: String, letter: String, msg: Int, chunk: Int) = run { @Suppress("UNUSED_EXPRESSION") version; "${key(id, chapter, letter)}:$msg:$chunk" in store.passed }
    fun markPassed(id: String, chapter: String, letter: String, msg: Int, chunk: Int) {
        val k = "${key(id, chapter, letter)}:$msg:$chunk"
        if (k !in store.passed) { store.markPassed(k); earn(Earn.SHADOW_PASS, id = id) }
        finished()
        version++
    }

    /** 내 낭독: 마디 녹음을 차례로 이어 붙인 한 편. 하나도 없으면 null. */
    fun myReading(id: String, chapter: String, l: Letter): java.io.File? {
        val parts = shadowChunks(id, chapter, l).flatMapIndexed { i, cs -> cs.indices.map { j -> take(id, chapter, l.id, i, j) } }.filter { it.exists() }
        if (parts.isEmpty()) return null
        val out = java.io.File(ctx.filesDir, "readings/${space(id)}_${chapter}_${l.id}.wav")
        runCatching { io.github.graviton94.todayletters.data.Wav.join(parts, out) }.onFailure { return null }
        return out
    }

    fun isShadowed(id: String, chapter: String, l: Letter) = run { @Suppress("UNUSED_EXPRESSION") version; key(id, chapter, l.id) in store.shadowed }
    fun isSealed(id: String, chapter: String, l: Letter) = run { @Suppress("UNUSED_EXPRESSION") version; key(id, chapter, l.id) in store.sealed }

    /** 이 편지를 따라 읽어 별을 하나 이상 받았나 (봉인 조건, F13: core 의 Prosody.stars). 끝까지 다 따라 읽은 편지도. */
    fun readAloud(id: String, chapter: String, l: Letter): Boolean = run {
        @Suppress("UNUSED_EXPRESSION") version
        val k = key(id, chapter, l.id)
        k in store.starred || k in store.shadowed
    }
    fun markStar(id: String, chapter: String, letter: String) { store.markStar(key(id, chapter, letter)); version++ }

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
        val prefixes = ws.map { space(it.series.id) + ":" }
        val ids = ws.map { it.series.id }.toSet()
        cm.values.filter { c -> CardRef.parse(c.key)?.series in ids }.forEach { if (Growth.known(it)) known++; if (it.box >= Memory.TOP) owned++ }
        return Stats(
            lettersDone = done, streak = streak, bestStreak = maxOf(store.bestStreak, streak),
            wordsKnown = known, wordsOwned = owned,
            readAlone = if (id == null) readAlone else Growth.readAlone(arrivedWordKeys().filter { it.startsWith("$id:") }, cm),
            lettersAlone = alone, lettersSealed = store.sealed.count { k -> prefixes.any { k.startsWith(it) } }, chapters = chapters,
            shadowPassed = store.passed.count { k -> prefixes.any { k.startsWith(it) } },
            pieces = ws.sumOf { ownedCount(it.series.id) },
        )
    }


    /**
     * 정산: 혼자 읽기가 오른 만큼 · 봉인 · 새 메달 · 완주한 작품 · 새로 익힌 낱말을 모아 한 번의 보상 순간으로 보여 준다.
     * 메달 셋째마다 유화 한 점이 확정. 레벨이 올랐으면 그 뒤에 레벨 업 순간 하나. 아무 것도 없으면 조용히.
     */
    fun settle() {
        val cm = cardMap()
        val id = current
        // 완독 봉인 (F13): 낱말을 모두 떠올리고 (떠올리기 이상) 소리 내어 읽은 (따라 읽기 별 하나 이상 · 녹음만 해도) 편지
        works.forEach { w -> w.chapters.forEach { c -> c.letters.forEach { l ->
            val k = key(w.series.id, c.id, l.id)
            if (k !in store.sealed && progress(w.series.id, c.id, l.id).done && readAloud(w.series.id, c.id, l) &&
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
        // 다 읽은 작품 (시리즈 메달 · XP 300)
        baseWorks.forEach { w ->
            val sid = w.series.id
            if (sid !in store.seriesDone && work(sid).chapters.isNotEmpty() && seriesDone(sid)) { store.addSeriesDone(sid); xp(XpAction.SERIES_COMPLETED) }
        }
        // 새로 익힌 낱말 (가장 높은 기록을 넘은 만큼만)
        val ms = medalStats()
        if (ms.wordsKnown > store.wordsSeen) { xp(XpAction.WORD_LEARNED, ms.wordsKnown - store.wordsSeen); store.wordsSeen = ms.wordsKnown }
        // 메달 (모든 작품 합쳐서): 화폐 · XP 50, 셋째마다 유화 한 점
        val got = mutableListOf<String>()
        Medals.newly(medalBook, ms, store.medals, baseWorks.map { it.series.id }.toSet()).forEach { m ->
            store.awardMedal(m.id, today); earn(Earn.ACHIEVEMENT); got += m.id
            if (id.isNotEmpty() && Medals.grantsPainting(store.medals.size)) draw(id, "medal:${m.id}", Tier.PAINTING)
        }
        // 이달의 전시를 다 모았으면 (전시마다 한 번): 도록 표지 + 화폐 (E4)
        var catalogue: Exhibition? = null
        if (id.isNotEmpty()) exhibition(id)?.let { e ->
            val ep = exhibitionPieces(id)
            val k = catalogueKey(e)
            if (ep.isNotEmpty() && ep.all { owns(id, it) } && k !in store.catalogues()) {
                store.addCatalogue(k); earn(Earn.EXHIBITION, id = id); catalogue = e
            }
        }
        val gain = pending.values.sum()
        if (gain > 0 || pendingPiece != null || got.isNotEmpty() || catalogue != null) {
            reward = RewardMoment(id, gain, pendingPiece, got, milestone, pending.toList(), catalogue)
        }
        pending = mutableMapOf(); pendingPiece = null
        // 언어 등급 (오르면 XP 가 붙으니 레벨 업보다 먼저)
        settleGrades()
        // 레벨 업: 이번 일로 오른 만큼 한 번 (보상 순간이 있으면 그 뒤에)
        val lv = Levels.of(store.xp().total).level
        if (lv > store.levelSeen) { levelUp = LevelUp(store.levelSeen, lv); store.levelSeen = lv }
        version++
    }

    // 도움말 (?)
    val coach = Coach(store.coachSeen)
    var coachTick by mutableStateOf(0)
        private set
    fun coachDone(screen: String) { coach.done(screen); store.coachSeen = coach.seen; coachTick++ }
    fun coachAgain(screen: String) { coach.again(screen); store.coachSeen = coach.seen; coachTick++ }
    /** 환영 한 장을 지나 서재로 (배울 말은 서재의 첫 작품 카드에서 고른다). */
    fun finishWelcome() { store.onboarded = true; nextStage() }

    var settingsOpen by mutableStateOf(false)

    // 어제 하루 몫이 넘쳐 미뤄 둔 카드: 앱을 켤 때 오늘 몫만큼 들인다 (모든 상태가 준비된 뒤)
    init { runCatching { DECKS.forEach { if (store.later(it).isNotEmpty()) admit(emptyList(), it) } } }
    // 예전 이정표 → 메달, 처음 레벨 (모든 상태가 준비된 뒤 한 번)
    init { runCatching { migrateMedals() } }
}
