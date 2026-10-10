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

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

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
    )

    fun save(a: AppSettings) = p.edit()
        .putString("ui", a.ui.name).putString("read", a.read.name).putString("theme", a.theme.name)
        .putBoolean("large", a.largeText).putBoolean("sound", a.sound).putBoolean("haptics", a.haptics)
        .putBoolean("effects", a.effects).putString("pace", a.pace.name).apply()

    fun hasApp() = p.contains("read")

    fun series(id: String): SeriesSettings? {
        val learn = p.getString("s:$id:learn", null) ?: return null
        return SeriesSettings(
            learn = Lang.valueOf(learn),
            showRead = p.getBoolean("s:$id:show", true),
            modes = (p.getString("s:$id:modes", null) ?: "MATCH,CONSTELLATION,ALOUD").split(",").filter { it.isNotBlank() }.map { ReplyMode.valueOf(it) }.toSet(),
            lettersPerDay = p.getInt("s:$id:perDay", 1),
            arrivalNotice = p.getBoolean("s:$id:notice", true),
        )
    }

    fun save(id: String, s: SeriesSettings) = p.edit()
        .putString("s:$id:learn", s.learn.name).putBoolean("s:$id:show", s.showRead)
        .putString("s:$id:modes", s.modes.joinToString(",") { it.name }).putInt("s:$id:perDay", s.lettersPerDay)
        .putBoolean("s:$id:notice", s.arrivalNotice).apply()

    fun progress(key: String): LetterProgress {
        val raw = p.getString("p:$key", null) ?: return LetterProgress()
        val parts = raw.split("|")
        return LetterProgress(
            shown = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            replied = parts.getOrNull(1).orEmpty().split(",").filter { it.isNotBlank() }.map { ReplyMode.valueOf(it) }.toSet(),
            done = parts.getOrNull(2) == "1",
        )
    }

    fun save(key: String, pr: LetterProgress) =
        p.edit().putString("p:$key", "${pr.shown}|${pr.replied.joinToString(",") { it.name }}|${if (pr.done) 1 else 0}").apply()

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

    /** 편지 방 낭독 빠르기 (시리즈마다, 1 또는 0.7). */
    fun roomSpeed(series: String) = p.getFloat("speed:$series", 1f)
    fun setRoomSpeed(series: String, v: Float) = p.edit().putFloat("speed:$series", v).apply()
    fun hasCard(key: String) = p.contains("c:$key")

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

    /** 도착 알림 시각 (시). */
    var noticeHour: Int
        get() = p.getInt("notice:hour", 8)
        set(v) = p.edit().putInt("notice:hour", v).apply()

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

    /** 받은 이정표: 시리즈 것은 "시리즈:id", 전체는 "id". 받은 날도 함께. */
    val achievements: Set<String> get() = set("ach")
    fun unlock(id: String) = add("ach", id)
    fun achievedDay(key: String): Long? = p.getLong("ach:$key:day", -1).takeIf { it >= 0 } ?: if (key in achievements) -1L else null
    fun setAchievedDay(key: String, day: Long) = p.edit().putLong("ach:$key:day", day).apply()

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

    /** 오늘의 할 일 진행 (날마다 새로) · 다 해서 보너스를 받은 날. */
    fun quest(day: Long, q: io.github.graviton94.todayletters.core.Quest) = if (p.getLong("quest:day", -1) == day) p.getInt("quest:${q.name}", 0) else 0
    fun setQuest(day: Long, q: io.github.graviton94.todayletters.core.Quest, n: Int) {
        val e = p.edit()
        if (p.getLong("quest:day", -1) != day) io.github.graviton94.todayletters.core.Quest.entries.forEach { e.remove("quest:${it.name}") }
        e.putLong("quest:day", day).putInt("quest:${q.name}", n).apply()
    }
    var questsPaidDay: Long
        get() = p.getLong("quest:paid", -1)
        set(v) = p.edit().putLong("quest:paid", v).apply()

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
    val sealSeen: Set<String> get() = set("sealseen")
    fun markSealSeen(key: String) = add("sealseen", key)
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

    var notifyAsked: Boolean
        get() = p.getBoolean("notify_asked", false)
        set(v) = p.edit().putBoolean("notify_asked", v).apply()
}
