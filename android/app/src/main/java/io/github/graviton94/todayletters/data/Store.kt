package io.github.graviton94.todayletters.data

import android.content.Context
import io.github.graviton94.todayletters.core.AppSettings
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.LetterProgress
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.SeriesSettings
import io.github.graviton94.todayletters.core.ThemeMode
import io.github.graviton94.todayletters.core.TypingPace
import io.github.graviton94.todayletters.core.UiLang
import java.time.LocalDate

/** 기기 안에만 저장한다 (SharedPreferences "letters"). 서버 · 계정 없음. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("letters", Context.MODE_PRIVATE)

    /** 새 버전 알림을 마지막으로 물은 날 (data/Updates). */
    var updateAskedDay: Long
        get() = p.getLong("update:asked", -1L)
        set(v) = p.edit().putLong("update:asked", v).apply()
    /** ‘새로 바뀐 것’ 을 보여 준 마지막 버전 (versionCode). 0 이면 아직 없음. */
    var seenVersion: Long
        get() = p.getLong("update:seen", 0L)
        set(v) = p.edit().putLong("update:seen", v).apply()
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    /**
     * 처음 앱을 연 날 (epoch day): 그날만 튜토리얼 작품이 2통 ([io.github.graviton94.todayletters.core.Arrivals.perDay]).
     * 없으면 -1 (예전 사용자는 -1 로 정해 두어 덤이 없다, [AppState] 의 init).
     */
    var firstDay: Long
        get() = p.getLong("first_day", -1)
        set(v) = p.edit().putLong("first_day", v).apply()
    fun hasFirstDay() = p.contains("first_day")

    /** 마지막으로 앱을 연 날 (그날 첫 실행이면 봉투 인트로). */
    var openedDay: Long
        get() = p.getLong("opened_day", -1)
        set(v) = p.edit().putLong("opened_day", v).apply()

    fun app(): AppSettings = AppSettings(
        ui = runCatching { UiLang.valueOf(p.getString("ui", "") ?: "") }.getOrDefault(UiLang.EN),
        read = runCatching { Lang.valueOf(p.getString("read", "") ?: "") }.getOrDefault(Lang.KO),
        theme = runCatching { ThemeMode.valueOf(p.getString("theme", "") ?: "") }.getOrDefault(ThemeMode.SYSTEM),
        largeText = p.getBoolean("large", false),
        sound = p.getBoolean("sound", true),
        haptics = p.getBoolean("haptics", true),
        effects = p.getBoolean("effects", true),
        pace = runCatching { TypingPace.valueOf(p.getString("pace", "") ?: "") }.getOrDefault(TypingPace.CALM),
        hold = runCatching { io.github.graviton94.todayletters.core.RoomFlow.Pause.valueOf(p.getString("hold", "") ?: "") }.getOrDefault(io.github.graviton94.todayletters.core.RoomFlow.Pause.NORMAL),
    )

    fun save(a: AppSettings) = p.edit()
        .putString("ui", a.ui.name).putString("read", a.read.name).putString("theme", a.theme.name)
        .putBoolean("large", a.largeText).putBoolean("sound", a.sound).putBoolean("haptics", a.haptics)
        .putBoolean("effects", a.effects).putString("pace", a.pace.name).putString("hold", a.hold.name).apply()

    fun hasApp() = p.contains("read")

    fun series(id: String): SeriesSettings? {
        val learn = p.getString("s:$id:learn", null) ?: return null
        return SeriesSettings(
            learn = Lang.valueOf(learn),
            showRead = p.getBoolean("s:$id:show", true),
            fadeRead = p.getBoolean("s:$id:fold", true),
            modes = modes(p.getString("s:$id:modes", null) ?: "MATCH,CONSTELLATION,ALOUD"),
            arrivalNotice = p.getBoolean("s:$id:notice", true),
            easier = p.getBoolean("s:$id:easier", false),
            // 예전 편지 방의 0.7× 를 쓰던 작품은 직접 고른 천천히로 이어 간다. 예전 값 0.7 · 0.85 는 지금 칸 0.8 · 0.9 로 (core Listen.migrate)
            listenAuto = p.getBoolean("s:$id:lauto", p.getFloat("speed:$id", 1f) >= 1f),
            listenSpeed = io.github.graviton94.todayletters.core.Listen.migrate(p.getFloat("s:$id:lspeed", p.getFloat("speed:$id", 1f))),
            amount = p.getString("s:$id:amount", null)?.let { a -> io.github.graviton94.todayletters.core.Start.Amount.entries.firstOrNull { it.name == a } }
                ?: io.github.graviton94.todayletters.core.Start.Amount.ONE,
            why = p.getString("s:$id:why", null)?.let { w -> io.github.graviton94.todayletters.core.Start.Why.entries.firstOrNull { it.name == w } },
            slowFrom = p.getLong("s:$id:slowfrom", -1L),
            slowDays = p.getInt("s:$id:slowdays", 0),
        )
    }

    fun save(id: String, s: SeriesSettings) = p.edit()
        .putString("s:$id:learn", s.learn.name).putBoolean("s:$id:show", s.showRead).putBoolean("s:$id:fold", s.fadeRead)
        .putString("s:$id:modes", s.modes.joinToString(",") { it.name })
        .putBoolean("s:$id:notice", s.arrivalNotice).putBoolean("s:$id:easier", s.easier)
        .putBoolean("s:$id:lauto", s.listenAuto).putFloat("s:$id:lspeed", s.listenSpeed)
        .putString("s:$id:amount", s.amount.name).putString("s:$id:why", s.why?.name)
        .putLong("s:$id:slowfrom", s.slowFrom).putInt("s:$id:slowdays", s.slowDays).apply()

    /** 걸음 이름 목록 → 걸음들. 예전 판의 이름(DICTATION 등 지금은 없는 걸음)은 건너뛴다. */
    private fun modes(raw: String): Set<ReplyMode> =
        raw.split(",").filter { it.isNotBlank() }.mapNotNull { n -> ReplyMode.entries.firstOrNull { it.name == n } }.toSet()

    /** 편지 진도: "보인 수|한 걸음|완료|건너뛴 걸음". */
    fun progress(key: String): LetterProgress = decode(p.getString("p:$key", null))

    fun save(key: String, pr: LetterProgress) = p.edit().putString("p:$key", encode(pr)).apply()

    private fun decode(raw: String?): LetterProgress {
        raw ?: return LetterProgress()
        val parts = raw.split("|")
        return LetterProgress(
            shown = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            replied = modes(parts.getOrNull(1).orEmpty()),
            done = parts.getOrNull(2) == "1",
            skipped = modes(parts.getOrNull(3).orEmpty()),
        )
    }
    private fun encode(pr: LetterProgress) = "${pr.shown}|${pr.replied.joinToString(",") { it.name }}|${if (pr.done) 1 else 0}|${pr.skipped.joinToString(",") { it.name }}"

    /**
     * 튜토리얼 다시 하기 (설정 › 도움): 연습하는 편지 (빈센트 편지 I) 의 진도 열쇠. 비었으면 다시 하는 중이 아니다.
     * 그동안 그 편지의 진도는 [replayProgress] 에 따로 두어서, 저장된 진도는 그대로 남는다.
     */
    var replayKey: String
        get() = p.getString("replay_key", "")!!
        set(v) = p.edit().putString("replay_key", v).apply()
    var replayProgress: LetterProgress
        get() = decode(p.getString("replay_p", null))
        set(v) = p.edit().putString("replay_p", encode(v)).apply()
    fun endReplay() = p.edit().remove("replay_key").remove("replay_p").apply()

    /**
     * 처음부터 다시 보기 (마친 편지, core 의 Replay): 다시 보는 편지의 진도 열쇠. 비었으면 다시 보는 중이 아니다.
     * 그동안의 진도는 [againProgress] 에 따로 (저장된 진도 · 보상은 그대로).
     */
    var againKey: String
        get() = p.getString("again_key", "")!!
        set(v) = p.edit().putString("again_key", v).apply()
    var againProgress: LetterProgress
        get() = decode(p.getString("again_p", null))
        set(v) = p.edit().putString("again_p", encode(v)).apply()
    fun endAgain() = p.edit().remove("again_key").remove("again_p").apply()

    /** 진도 열쇠 전부 ("p:" 뒤): 건너뛴 걸음 모으기 · 튜토리얼을 마친 것으로 볼지. */
    fun progressKeys(): List<String> = p.all.keys.filter { it.startsWith("p:") }.map { it.removePrefix("p:") }

    // ── 처음 설치의 튜토리얼 (core 의 Tutorial) ──────────────
    /** 튜토리얼을 마쳤나 (편지 I 뒤 “이제 혼자서도 괜찮아요”). */
    var tutorialDone: Boolean
        get() = p.getBoolean("tutorial_done", false)
        set(v) = p.edit().putBoolean("tutorial_done", v).apply()
    /** 튜토리얼에서 안내 한 줄을 이미 본 걸음. */
    val guideSeen: Set<String> get() = set("guide_seen")
    fun markGuideSeen(step: String) = add("guide_seen", step)
    /** 설정 › 사용법 다시 보기: 안내 한 줄을 다시 (편지를 마친 사람이어도 튜토리얼을 한 번 더). */
    var tutorialReplay: Boolean
        get() = p.getBoolean("tutorial_replay", false)
        set(v) = p.edit().putBoolean("tutorial_replay", v).apply()
    fun clearGuideSeen() = p.edit().remove("guide_seen").apply()

    // ── 오늘의 여정 (작품의 진도 이름 [space] 마다) ─────────────
    /** 오늘의 복습을 마친 날. */
    fun reviewedOn(space: String) = p.getLong("reviewed:$space", -1)
    fun setReviewedOn(space: String, day: Long) = p.edit().putLong("reviewed:$space", day).apply()
    /** 여정 보너스를 받은 날. */
    fun journeyPaid(space: String) = p.getLong("journey:$space:paid", -1)
    fun setJourneyPaid(space: String, day: Long) = p.edit().putLong("journey:$space:paid", day).apply()

    /** ‘소리 없이 읽기’를 고른 장 (작품:장): 다시 묻지 않는다 (F12). */
    fun silent(chapter: String) = chapter in set("silent")
    fun markSilent(chapter: String) = add("silent", chapter)

    /** 오늘 새로 연 편지 수 (작품마다). 날이 바뀌면 0. */
    fun openedToday(series: String): Int =
        if (p.getLong("o:$series:day", -1) == LocalDate.now().toEpochDay()) p.getInt("o:$series:n", 0) else 0

    /** 마지막으로 새 편지를 연 날과 그날 연 수 (날이 지나도 남아 있음). 연 적 없으면 (-1, 0). */
    fun lastOpen(series: String): Pair<Long, Int> = p.getLong("o:$series:day", -1) to p.getInt("o:$series:n", 0)

    fun markOpened(series: String) {
        val n = openedToday(series) + 1
        p.edit().putLong("o:$series:day", LocalDate.now().toEpochDay()).putInt("o:$series:n", n).apply()
    }

    /** 한 번이라도 연 편지 (열자마자 나가도 그 편지는 계속 열려 있게). */
    fun started(key: String) = p.getBoolean("st:$key", false)
    fun markStarted(key: String) = p.edit().putBoolean("st:$key", true).apply()

    /** 이어 읽은 날: 마지막으로 편지를 끝낸 날 · 이어진 날 수. */
    val streakDay: Long get() = p.getLong("streak:day", -10)
    val streakCount: Int get() = p.getInt("streak:n", 0)
    fun saveStreak(day: Long, n: Int) = p.edit().putLong("streak:day", day).putInt("streak:n", n).apply()

    /** 낱말 카드 (복습): "c:<키>" = "칸|다시 볼 날|본 횟수". */
    fun cards(): List<io.github.graviton94.todayletters.core.Card> = p.all.keys.filter { it.startsWith("c:") }.mapNotNull { k ->
        val v = p.getString(k, null)?.split("|") ?: return@mapNotNull null
        io.github.graviton94.todayletters.core.Card(k.removePrefix("c:"), v[0].toInt(), v[1].toLong(), v.getOrNull(2)?.toInt() ?: 0,
            keep = v.getOrNull(3)?.toIntOrNull() ?: 0, last = v.getOrNull(4)?.toLongOrNull() ?: 0, lapsed = v.getOrNull(5) == "1")
    }
    fun save(c: io.github.graviton94.todayletters.core.Card) =
        p.edit().putString("c:${c.key}", "${c.box}|${c.due}|${c.seen}|${c.keep}|${c.last}|${if (c.lapsed) 1 else 0}").apply()

    /** 갑자기 한 문제를 낸 날 · 그 낱말 (하루 한 번). */
    var surpriseDay: Long
        get() = p.getLong("surprise_day", -1)
        set(v) = p.edit().putLong("surprise_day", v).apply()

    fun hasCard(key: String) = p.contains("c:$key")
    fun cardKeys(): List<String> = p.all.keys.filter { it.startsWith("c:") }.map { it.removePrefix("c:") }

    /** 하루 몫이 차서 미뤄 둔 새 카드 열쇠 (앞이 먼저). */
    var later: List<String>
        get() = later("")
        set(v) = setLater("", v)
    /** 배우는 말마다 ([deck]: "" 원어 · "en" · "ko") 따로 쌓는 나중 줄. */
    fun later(deck: String): List<String> = p.getString("later" + sfx(deck), "")!!.split("\n").filter { it.isNotBlank() }
    fun setLater(deck: String, v: List<String>) = p.edit().putString("later" + sfx(deck), v.joinToString("\n")).apply()
    private fun sfx(deck: String) = if (deck.isEmpty()) "" else ":$deck"
    /** 그날 새로 들인 카드 수 (다른 날이면 0). 배우는 말마다 따로. */
    fun addedOn(day: Long, deck: String = ""): Int = if (p.getLong("intake:day" + sfx(deck), -1) == day) p.getInt("intake:n" + sfx(deck), 0) else 0
    fun setAdded(day: Long, n: Int, deck: String = "") = p.edit().putLong("intake:day" + sfx(deck), day).putInt("intake:n" + sfx(deck), n).apply()

    /** 오늘 복습을 마친 날 (오늘 화면 · 우표). */
    var reviewedDay: Long
        get() = p.getLong("reviewed:day", -1)
        set(v) = p.edit().putLong("reviewed:day", v).apply()

    /** 편지를 끝낸 날들 (이번 주 우표). */
    fun doneDays(): Set<Long> = p.getStringSet("donedays", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    fun markDoneDay(day: Long) = p.edit().putStringSet("donedays", (doneDays() + day).filter { day - it < 60 }.map { it.toString() }.toSet()).apply()

    /** 내 구절: "작품:챕터:편지:문장번호". */
    var quotes: List<String>
        get() = p.getString("quotes", "")!!.split("\n").filter { it.isNotBlank() }
        set(v) = p.edit().putString("quotes", v.joinToString("\n")).apply()

    /** 도착 알림 시각 (시, 0–23). */
    var noticeHour: Int
        get() = p.getInt("notice:hour", 8)
        set(v) = p.edit().putInt("notice:hour", v).apply()
    /** 도착 알림 시각 (분, 5분 단위). */
    var noticeMinute: Int
        get() = p.getInt("notice:minute", 0)
        set(v) = p.edit().putInt("notice:minute", v).apply()

    var coachSeen: Set<String>
        get() = p.getString("coach_seen", "")!!.split(",").filter { it.isNotBlank() }.toSet()
        set(v) = p.edit().putString("coach_seen", v.joinToString(",")).apply()

    /** 복습 한 판의 중간 저장: 그날 · 문제 카드 순서 · 몇 번째까지 · 맞고 틀림. [name] 은 오늘의 복습이면 "today", 아니면 문제 꼴. */
    data class SavedSession(val day: Long, val keys: List<String>, val at: Int, val oks: List<Boolean>)
    fun session(name: String): SavedSession? {
        val v = p.getString("sess:$name", null)?.split("|") ?: return null
        if (v.size < 4) return null
        return runCatching {
            SavedSession(v[0].toLong(), v[1].split(";").filter { it.isNotEmpty() }, v[2].toInt(), v[3].map { it == '1' })
        }.getOrNull()
    }
    fun saveSession(name: String, s: SavedSession) =
        p.edit().putString("sess:$name", "${s.day}|${s.keys.joinToString(";")}|${s.at}|${s.oks.joinToString("") { if (it) "1" else "0" }}").apply()
    fun clearSession(name: String) = p.edit().remove("sess:$name").apply()

    // ── 보상 엔진 (모든 시리즈 공통) ─────────────────────────
    /** 마지막으로 들어간 시리즈. */
    var lastSeries: String?
        get() = p.getString("series:last", null)
        set(v) = p.edit().putString("series:last", v).apply()

    /** 시리즈 지갑: 잔액 · 날 · 오늘 종류별 · 누적. (예전 하나뿐이던 지갑은 처음 연 시리즈가 이어받는다.) */
    fun wallet(series: String): io.github.graviton94.todayletters.core.Wallet {
        val k = if (p.contains("wallet:$series:bal") || !p.contains("wallet:bal")) "wallet:$series:" else "wallet:"
        val today = p.getString("${k}today", "") ?: ""
        val map = today.split(";").mapNotNull { e -> e.split("=").takeIf { it.size == 2 }?.let { (x, v) ->
            runCatching { io.github.graviton94.todayletters.core.Earn.valueOf(x) to v.toInt() }.getOrNull() } }.toMap()
        return io.github.graviton94.todayletters.core.Wallet(p.getInt("${k}bal", 0), p.getLong("${k}day", -1), map, p.getInt("${k}total", 0))
    }
    fun saveWallet(series: String, w: io.github.graviton94.todayletters.core.Wallet) {
        val k = "wallet:$series:"
        p.edit().putInt("${k}bal", w.balance).putLong("${k}day", w.day).putInt("${k}total", w.total)
            .putString("${k}today", w.today.entries.joinToString(";") { "${it.key.name}=${it.value}" })
            .remove("wallet:bal").apply()
    }

    private fun set(key: String): Set<String> = p.getStringSet(key, emptySet())!!.toSet()
    private fun add(key: String, v: String) = p.edit().putStringSet(key, set(key) + v).apply()

    /** 예전 이정표 (메달 진열장 전): 시리즈 것은 "시리즈:id", 전체는 "id". 메달로 옮길 때만 읽는다 ([io.github.graviton94.todayletters.core.Medals.migrate]). */
    val achievements: Set<String> get() = set("ach")
    fun unlock(id: String) = add("ach", id)
    fun achievedDay(key: String): Long? = p.getLong("ach:$key:day", -1).takeIf { it >= 0 } ?: if (key in achievements) -1L else null
    fun setAchievedDay(key: String, day: Long) = p.edit().putLong("ach:$key:day", day).apply()

    // ── 메달 진열장 · 레벨 (core 의 Medals · Levels) ─────────────────
    /** 받은 메달 id (모든 작품 공통, 시리즈 메달은 "series_<작품>") 와 받은 날. */
    val medals: Set<String> get() = set("medals")
    fun awardMedal(id: String, day: Long) = p.edit().putStringSet("medals", set("medals") + id).putLong("medal:$id:day", day).apply()
    fun medalDay(id: String): Long? = if (id in medals) p.getLong("medal:$id:day", -1) else null
    /** 예전 이정표를 메달로 옮겼나 (한 번만). */
    var medalsMigrated: Boolean
        get() = p.getBoolean("medals:migrated", false)
        set(v) = p.edit().putBoolean("medals:migrated", v).apply()
    /** 따라 읽기 별: 마디 ("편지키:문장:마디") 마다 가장 많이 받은 별 (0~3). */
    fun stars(): Map<String, Int> = set("stars").mapNotNull { e -> e.substringAfterLast("=").toIntOrNull()?.let { e.substringBeforeLast("=") to it } }.toMap()
    fun setStars(key: String, n: Int) = p.edit().putStringSet("stars", set("stars").filterNot { it.substringBeforeLast("=") == key }.toSet() + "$key=$n").apply()
    /** 끝까지 걸은 그림 산책 ("작품:편지"). */
    val walks: Set<String> get() = set("walks")
    fun addWalk(key: String) = add("walks", key)
    /** 편지를 다 읽은 작품 (한 번 다 읽으면 남는다). */
    val seriesDone: Set<String> get() = set("series_done")
    fun addSeriesDone(id: String) = add("series_done", id)
    /** XP 장부: 누적 · 날 · 오늘 일마다. */
    fun xp(): io.github.graviton94.todayletters.core.XpLedger {
        val map = (p.getString("xp:today", "") ?: "").split(";").mapNotNull { e -> e.split("=").takeIf { it.size == 2 }?.let { (x, v) ->
            runCatching { io.github.graviton94.todayletters.core.XpAction.valueOf(x) to v.toInt() }.getOrNull() } }.toMap()
        return io.github.graviton94.todayletters.core.XpLedger(p.getLong("xp:total", 0), p.getLong("xp:day", -1), map)
    }
    fun saveXp(l: io.github.graviton94.todayletters.core.XpLedger) = p.edit().putLong("xp:total", l.total).putLong("xp:day", l.day)
        .putString("xp:today", l.today.entries.joinToString(";") { "${it.key.name}=${it.value}" }).apply()
    /** XP 를 처음 채웠나 (예전 사용자의 기록으로 한 번). */
    var xpSeeded: Boolean
        get() = p.getBoolean("xp:seeded", false)
        set(v) = p.edit().putBoolean("xp:seeded", v).apply()
    /** 마지막으로 레벨 업 순간을 보여 준 레벨 (같은 레벨을 두 번 축하하지 않는다). */
    var levelSeen: Int
        get() = p.getInt("level:seen", 1)
        set(v) = p.edit().putInt("level:seen", v).apply()
    /** 익힌 낱말 수의 가장 높은 기록 (새로 익힌 만큼만 XP). */
    var wordsSeen: Int
        get() = p.getInt("xp:words", 0)
        set(v) = p.edit().putInt("xp:words", v).apply()

    /** 갤러리: 시리즈마다 건 소장품 id · 오늘의 자료를 뽑은 표시 ("날:까닭"). */
    fun pieces(series: String): Set<String> = set("pieces:$series")
    fun addPiece(series: String, id: String) = add("pieces:$series", id)
    fun drawTags(series: String): Set<String> = set("draws:$series")
    fun addDrawTag(series: String, tag: String) = p.edit().putStringSet("draws:$series", (set("draws:$series") + tag).toList().takeLast(60).toSet()).apply()

    /** 쉼표 (하루 쉬어도 이어 읽기가 이어짐) 개수. */
    var rests: Int
        get() = p.getInt("rests", 0)
        set(v) = p.edit().putInt("rests", v).apply()
    /** 다음 편지를 미리 연 날 (시리즈마다). */
    fun earlyDay(series: String) = p.getLong("early:$series", -1)
    fun setEarlyDay(series: String, day: Long) = p.edit().putLong("early:$series", day).apply()

    /** 편지 낭독을 듣다 멈춘 문장 (편지 열쇠마다, 0부터). 없으면 -1. 끝까지 들으면 지운다. */
    fun heard(key: String) = p.getInt("heard:$key", -1)
    fun setHeard(key: String, i: Int) = p.edit().apply { if (i < 0) remove("heard:$key") else putInt("heard:$key", i) }.apply()

    /** 따라 읽기 점수 기록: 날 → (억양 합, 리듬 합, 마디 수). 최근 120일. */
    fun scores(): Map<Long, Triple<Int, Int, Int>> = set("scores").mapNotNull { e ->
        e.split("|").takeIf { it.size == 4 }?.let { (d, a, b, n) -> runCatching { d.toLong() to Triple(a.toInt(), b.toInt(), n.toInt()) }.getOrNull() }
    }.toMap()
    fun addScore(day: Long, intonation: Int, rhythm: Int) {
        val m = scores().toMutableMap()
        val (a, b, n) = m[day] ?: Triple(0, 0, 0)
        m[day] = Triple(a + intonation, b + rhythm, n + 1)
        // 첫 주 기록은 늘 남긴다 (비교의 기준)
        val first = m.keys.minOrNull() ?: day
        p.edit().putStringSet("scores", m.filterKeys { day - it < 120 || it - first < 7 }.map { (d, v) -> "$d|${v.first}|${v.second}|${v.third}" }.toSet()).apply()
    }
    /** 낭독회를 마친 주 (그 주 월요일 epoch day). */
    var recitalWeek: Long
        get() = p.getLong("recital:week", -1)
        set(v) = p.edit().putLong("recital:week", v).apply()

    /** 따라 읽기를 끝까지 한 편지 · 봉인된 편지 · 봉인 화면을 이미 본 편지 (편지 키). */
    val shadowed: Set<String> get() = set("shadow")
    fun markShadowed(key: String) = add("shadow", key)
    val sealed: Set<String> get() = set("sealed")
    fun seal(key: String) = add("sealed", key)
    /** 따라 읽기에서 억양 · 리듬 70점을 넘은 마디 ("편지키:문장:마디"). */
    val passed: Set<String> get() = set("pass")
    fun markPassed(key: String) = add("pass", key)
    /** 따라 읽기에서 별을 하나 이상 받은 편지 (편지 키): 봉인 조건 (F13). */
    val starred: Set<String> get() = set("starred")
    fun markStar(key: String) = add("starred", key)
    val sealSeen: Set<String> get() = set("sealseen")
    fun markSealSeen(key: String) = add("sealseen", key)
    // ── v21 3단계 ─────────────────────────────────────────
    /** 작품마다 고른 액자 (Frames.Kind 이름). 없으면 등급의 기본 액자. */
    fun frameOf(piece: String): String? = p.getString("frame:$piece", null)
    fun setFrame(piece: String, kind: String) = p.edit().putString("frame:$piece", kind).apply()
    /**
     * 산 작품 카드 테두리 · 지금 쓰는 테두리: 작품마다 (그 작품의 화폐로 사니까, F19). 예전에 하나로 산 테두리는 모든 작품에 그대로.
     */
    fun borders(series: String): Set<String> = set("borders:$series") + set("borders") + "PLAIN"
    fun addBorder(series: String, b: String) = add("borders:$series", b)
    fun border(series: String): String = p.getString("border:$series", null) ?: p.getString("border", "PLAIN")!!
    fun setBorder(series: String, b: String) = p.edit().putString("border:$series", b).apply()
    /** 다 모은 전시 (전시 id:연월) — 도록 표지. */
    fun catalogues(): Set<String> = set("catalogues")
    fun addCatalogue(k: String) = add("catalogues", k)
    /** 이번 주 돌아보기 (E1): 날마다 쉼표로 이은 날 · 끝낸 편지 · 새로 ‘내 것’ · 걸린 작품. 값은 "날:열쇠". 60일만 둔다. */
    fun dayLog(kind: String): List<Pair<Long, String>> = set("log:$kind").mapNotNull { e -> e.substringBefore(":").toLongOrNull()?.let { it to e.substringAfter(":") } }
    fun logDay(kind: String, day: Long, key: String) =
        p.edit().putStringSet("log:$kind", (set("log:$kind") + "$day:$key").filter { (it.substringBefore(":").toLongOrNull() ?: 0) > day - 60 }.toSet()).apply()
    /** 저녁 알림 (E2): 이어 읽기가 끊기기 전 한 번. -1 이면 끔. */
    var eveningHour: Int
        get() = p.getInt("evening", 20)
        set(v) = p.edit().putInt("evening", v).apply()

    /** 편지 도착 연출을 본 편지 (v21 2단계 D1: 처음 열 때 한 번만). */
    fun arrived(key: String) = key in set("arrived")
    fun markArrived(key: String) = add("arrived", key)
    /** 가장 길게 이어 읽은 날 수. */
    var bestStreak: Int
        get() = p.getInt("streak:best", 0)
        set(v) = p.edit().putInt("streak:best", v).apply()
    /** 마지막으로 잰 혼자 읽기 % (오른 만큼 보상). */
    var readAloneSeen: Int
        get() = p.getInt("alone:seen", 0)
        set(v) = p.edit().putInt("alone:seen", v).apply()

    /** 배우는 말 [lang] (언어 코드) 에서 닿았던 가장 높은 언어 등급 ([io.github.graviton94.todayletters.core.Reading.Grade.index], 없으면 0). 내려가지 않는다. */
    fun grade(lang: String) = p.getInt("grade:$lang", 0)
    fun setGrade(lang: String, index: Int) = p.edit().putInt("grade:$lang", index).apply()

    /** 이 편지를 처음 열 때의 읽을 수 있는 비율 (편지 진도 열쇠마다, 없으면 -1): 다시 읽을 때 견준다. */
    fun firstCoverage(key: String) = p.getInt("cov0:$key", -1)
    fun setFirstCoverage(key: String, pct: Int) = p.edit().putInt("cov0:$key", pct).apply()

    /**
     * 주마다 읽을 수 있는 비율 (배우는 말마다): 이번 주 (epoch day / 7) 의 값과 지난주 값. 주가 바뀌면 이번 값이 지난주 값이 된다.
     * 반환은 지난주 값 (없으면 null).
     */
    fun weekCoverage(lang: String, week: Long, now: Int): Int? {
        val parts = p.getString("covwk:$lang", null)?.split("|")
        val w = parts?.getOrNull(0)?.toLongOrNull(); val cur = parts?.getOrNull(1)?.toIntOrNull(); val prev = parts?.getOrNull(2)?.toIntOrNull()
        val last = if (w == null) null else if (w == week) prev else cur
        p.edit().putString("covwk:$lang", "$week|$now|${last ?: ""}").apply()
        return last
    }

    var notifyAsked: Boolean
        get() = p.getBoolean("notify_asked", false)
        set(v) = p.edit().putBoolean("notify_asked", v).apply()

    /** 처음 걸음을 건너뛰면 한 번: ‘다시 하기’ 자리 안내를 보였다. */
    var skipNoted: Boolean
        get() = p.getBoolean("skip_noted", false)
        set(v) = p.edit().putBoolean("skip_noted", v).apply()

    /** 프롤로그를 끝까지 본 작품 (첫 편지 앞에서 한 번만 저절로 나온다). */
    fun prologueSeen(series: String) = series in set("prologue_seen")
    fun markPrologueSeen(series: String) = add("prologue_seen", series)
    /** 처음 묶음 (서문 + 무료 장) 받기를 물은 작품 (작품을 처음 열 때 한 번만 저절로 묻는다). */
    fun firstAsked(series: String) = series in set("first_asked")
    fun markFirstAsked(series: String) = add("first_asked", series)

    /** 결제 대기 중이던 구매 토큰 (끝나면 고마워요 한 장) · 이미 고마워요를 보인 구매 토큰. */
    fun pendingBuy(token: String) = token in set("buy_pending")
    fun markPendingBuy(token: String) = add("buy_pending", token)
    fun clearPendingBuy(token: String) { if (pendingBuy(token)) p.edit().putStringSet("buy_pending", set("buy_pending") - token).apply() }
    fun thanked(token: String) = token in set("buy_thanked")
    fun markThanked(token: String) = add("buy_thanked", token)

    /** 새 편지 표시가 한 번 내려앉은 것 ("자리|편지 열쇠"): 같은 편지로는 다시 떨어지지 않는다. */
    fun dropSeen(k: String) = k in set("drop_seen")
    fun markDropSeen(ks: Collection<String>) { val cur = set("drop_seen"); if (!cur.containsAll(ks)) p.edit().putStringSet("drop_seen", cur + ks).apply() }
    /** ‘언어마다 진도가 따로 저장돼요’ 알림을 본 적이 있나 (한 번만). */
    var langNoticeSeen: Boolean
        get() = p.getBoolean("lang_notice_seen", false)
        set(v) = p.edit().putBoolean("lang_notice_seen", v).apply()

    /**
     * 언어마다 진도 (v22): 예전에 언어 구분 없이 쌓은 진도를 그때 배우던 말([space], "vincent@en")의 열쇠로 옮긴다.
     * 원어로 배우던 작품은 열쇠가 그대로라 부르지 않는다. 작품마다 한 번만 (옮긴 표시 "spaces:<작품>").
     * 옮기는 것: 편지 진도 · 연 편지 · 듣다 멈춘 자리 · 도착을 본 편지 · 따라 읽기 · 봉인 · 통과한 마디 · 이번 주 편지 기록 · 오늘의 할 일 · 하루 편지 수 · 미리 연 날.
     * 남는 것: 낱말 카드 (열쇠에 이미 배우는 말이 있다) · 지갑 · 이정표 · 갤러리 · 내 구절 (작품 하나에 하나).
     */
    fun migrateSpace(series: String, space: String) {
        val flag = "spaces:$series"
        if (p.getBoolean(flag, false)) return
        val e = p.edit()
        if (space != series) {
            val all = p.all
            fun mv(prefix: String) = all.keys.filter { it.startsWith(prefix) }.forEach { k ->
                val rest = k.removePrefix(prefix)
                io.github.graviton94.todayletters.core.Spaces.move(rest, series, space)?.let { to ->
                    when (val v = all[k]) {
                        is String -> e.putString(prefix + to, v); is Int -> e.putInt(prefix + to, v); is Long -> e.putLong(prefix + to, v)
                        is Boolean -> e.putBoolean(prefix + to, v); is Float -> e.putFloat(prefix + to, v)
                        else -> return@forEach
                    }
                    e.remove(k)
                }
            }
            listOf("p:", "st:", "heard:").forEach(::mv)
            // 작품 열쇠: 오늘의 할 일 "quest:<작품>:" · 하루 편지 수 "o:<작품>:" · 미리 연 날 "early:<작품>"
            listOf("quest:", "o:").forEach { pre ->
                all.keys.filter { it.startsWith("$pre$series:") }.forEach { k ->
                    val to = "$pre$space:" + k.removePrefix("$pre$series:")
                    when (val v = all[k]) { is Int -> e.putInt(to, v); is Long -> e.putLong(to, v); is String -> e.putString(to, v); else -> return@forEach }
                    e.remove(k)
                }
            }
            if (p.contains("early:$series")) { e.putLong("early:$space", p.getLong("early:$series", -1)); e.remove("early:$series") }
            fun mvSet(key: String, sep: String = "") {
                val v = set(key).ifEmpty { return }
                e.putStringSet(key, v.map { x ->
                    if (sep.isEmpty()) io.github.graviton94.todayletters.core.Spaces.move(x, series, space) ?: x
                    else x.substringBefore(sep) + sep + (io.github.graviton94.todayletters.core.Spaces.move(x.substringAfter(sep), series, space) ?: x.substringAfter(sep))
                }.toSet())
            }
            listOf("arrived", "shadow", "sealed", "pass", "sealseen").forEach { mvSet(it) }
            mvSet("log:letters", ":")
        }
        e.putBoolean(flag, true).apply()
    }

    /** 무엇을 배울지 고른 작품 (처음 소개 · 작품에 처음 들어갈 때). */
    fun learnChosen(series: String) = series in set("learn_chosen")
    fun markLearnChosen(series: String) = add("learn_chosen", series)

    // ── 이용권 (data/Billing, core 의 Entitlement) ─────────────────
    /** 마지막으로 Play 에서 확인한 이용권. 결제 서버에 닿지 않을 때 (비행기 모드) 이것으로 연다. */
    var entitlement: io.github.graviton94.todayletters.core.Entitlement
        get() = io.github.graviton94.todayletters.core.Entitlement(p.getBoolean("ent:sub", false), set("ent:owned"), p.getString("ent:base", null))
        set(v) = p.edit().putBoolean("ent:sub", v.subscribed).putStringSet("ent:owned", v.owned).putString("ent:base", v.basePlan).apply()

    /** 구독의 구매 토큰 (이 기기에서 결제했을 때). 같은 토큰이면 저장한 요금제 ([entitlement] 의 basePlan) 가 아직 맞다. */
    var subToken: String?
        get() = p.getString("ent:sub_token", null)
        set(v) = p.edit().putString("ent:sub_token", v).apply()

    // ── 기록 보관 (설정 › 기록 보관, core 의 Backup) ─────────────────
    /** 저장소 값 전부 (내보낼 때 core Backup.encode 가 거른다). */
    fun snapshot(): Map<String, *> = p.all
    /**
     * 가져오기: [Backup.keep] 인 열쇠를 모두 지우고 [values] 로 바꾼다 (한 번에, 디스크까지 쓰고 돌아온다).
     * 이용권 "ent:*" · 개발용 · 마지막 내보낸 날은 이 폰의 것을 그대로 둔다.
     */
    fun replaceRecords(values: Map<String, Any>): Boolean {
        val e = p.edit()
        p.all.keys.filter(io.github.graviton94.todayletters.core.Backup::keep).forEach { e.remove(it) }
        values.forEach { (k, v) ->
            if (!io.github.graviton94.todayletters.core.Backup.keep(k)) return@forEach
            when (v) {
                is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v); is String -> e.putString(k, v)
                is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
            }
        }
        return e.commit()
    }
    /** 마지막으로 기록을 내보낸 날 (epoch day), 없으면 -1. 파일에는 넣지 않는다. */
    var backupDay: Long
        get() = p.getLong("backup:last", -1L)
        set(v) = p.edit().putLong("backup:last", v).apply()
    /** 마지막으로 내보낼 때 마친 편지 수 (core BackupNudge: 그 뒤로 몇 통). */
    var backupLetters: Int
        get() = p.getInt("backup:letters", 0)
        set(v) = p.edit().putInt("backup:letters", v).apply()
    /** 오늘 화면의 보관 한 줄을 ‘나중에’ 로 미룬 끝날 (epoch day, 이날부터 다시). */
    var backupSnooze: Long
        get() = p.getLong("backup:snooze", -1L)
        set(v) = p.edit().putLong("backup:snooze", v).apply()
    /** 처음으로 한 장을 다 마친 날 (epoch day), 없으면 -1. 내보낸 적이 없으면 그다음 날부터 한 줄. */
    var backupChapterDay: Long
        get() = p.getLong("backup:chapter_day", -1L)
        set(v) = p.edit().putLong("backup:chapter_day", v).apply()

    // ── 처음 시작 · 천천히 듣는 2주 · 들은 시간 (core 의 Start · Slow · Listening) ─────────
    /** 처음 시작에서 고른 시작 단계 (배우는 말 [lang] 마다, [io.github.graviton94.todayletters.core.Reading.Grade.index], 없으면 0). 등급의 바닥. */
    fun startGrade(lang: String) = p.getInt("start:grade:$lang", 0)
    fun setStartGrade(lang: String, index: Int) = p.edit().putInt("start:grade:$lang", index).apply()
    /** 이 작품에서 처음 시작을 답했나 (또는 건너뛰었나). */
    fun startAnswered(id: String) = p.getBoolean("start:asked:$id", false)
    fun markStartAnswered(id: String) = p.edit().putBoolean("start:asked:$id", true).apply()
    /** 처음 시작이 생기기 전부터 쓰던 사람 (진도가 있었음): 묻지 않는다. 처음 한 번 정해 둔다. */
    fun startLegacy(hadProgress: Boolean): Boolean {
        if (!p.contains("start:legacy")) p.edit().putBoolean("start:legacy", hadProgress).apply()
        return p.getBoolean("start:legacy", false)
    }
    /** 천천히 듣는 2주의 안내를 띄웠나: 첫 편지 뒤 · 3일째 (작품마다). */
    fun slowNoted(id: String, which: String) = p.getBoolean("slow:note:$which:$id", false)
    fun markSlowNoted(id: String, which: String) = p.edit().putBoolean("slow:note:$which:$id", true).apply()
    /** 이 편지 (진도 열쇠) 를 처음 한 번 천천히 다 들었나. */
    fun slowPassed(key: String) = p.getBoolean("slow:pass:$key", false)
    fun markSlowPassed(key: String) = p.edit().putBoolean("slow:pass:$key", true).apply()

    /** 들은 시간 (밀리초) 을 더한다: 언어마다 모두 · 날마다 (8일 넘은 날은 지운다). */
    fun addListen(lang: String, day: Long, ms: Long) {
        if (ms <= 0) return
        val e = p.edit().putLong("lis:$lang", p.getLong("lis:$lang", 0L) + ms).putLong("lis:$lang:$day", p.getLong("lis:$lang:$day", 0L) + ms)
        p.all.keys.filter { it.startsWith("lis:$lang:") }.forEach { k -> k.substringAfterLast(':').toLongOrNull()?.let { if (it < day - 8) e.remove(k) } }
        e.apply()
    }
    /** 이 말로 들은 시간 모두 (밀리초). */
    fun listenTotal(lang: String) = p.getLong("lis:$lang", 0L)
    /** [from]~[to] 날 (epoch day, 둘 다 포함) 에 들은 시간 (밀리초). */
    fun listenBetween(lang: String, from: Long, to: Long) = (from..to).sumOf { p.getLong("lis:$lang:$it", 0L) }

    /** 개발용: 모든 시리즈 열기 (debug 빌드에서만 읽는다, [Billing.effective]). */
    var debugUnlockAll: Boolean
        get() = p.getBoolean("debug:unlock_all", false)
        set(v) = p.edit().putBoolean("debug:unlock_all", v).apply()
}
