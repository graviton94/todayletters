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
        pace = runCatching { TypingPace.valueOf(p.getString("pace", "") ?: "") }.getOrDefault(TypingPace.CALM),
    )

    fun save(a: AppSettings) = p.edit()
        .putString("ui", a.ui.name).putString("read", a.read.name).putString("theme", a.theme.name)
        .putBoolean("large", a.largeText).putBoolean("sound", a.sound).putBoolean("haptics", a.haptics)
        .putString("pace", a.pace.name).apply()

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
        io.github.graviton94.todayletters.core.Card(k.removePrefix("c:"), v[0].toInt(), v[1].toLong(), v.getOrNull(2)?.toInt() ?: 0)
    }
    fun save(c: io.github.graviton94.todayletters.core.Card) = p.edit().putString("c:${c.key}", "${c.box}|${c.due}|${c.seen}").apply()
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
    /** 지갑: 잔액 · 날 · 오늘 종류별 · 누적. */
    var wallet: io.github.graviton94.todayletters.core.Wallet
        get() {
            val today = p.getString("wallet:today", "") ?: ""
            val map = today.split(";").mapNotNull { e -> e.split("=").takeIf { it.size == 2 }?.let { (k, v) ->
                runCatching { io.github.graviton94.todayletters.core.Earn.valueOf(k) to v.toInt() }.getOrNull() } }.toMap()
            return io.github.graviton94.todayletters.core.Wallet(p.getInt("wallet:bal", 0), p.getLong("wallet:day", -1), map, p.getInt("wallet:total", 0))
        }
        set(w) = p.edit().putInt("wallet:bal", w.balance).putLong("wallet:day", w.day).putInt("wallet:total", w.total)
            .putString("wallet:today", w.today.entries.joinToString(";") { "${it.key.name}=${it.value}" }).apply()

    private fun set(key: String): Set<String> = p.getStringSet(key, emptySet())!!.toSet()
    private fun add(key: String, v: String) = p.edit().putStringSet(key, set(key) + v).apply()

    /** 받은 업적 id. */
    val achievements: Set<String> get() = set("ach")
    fun unlock(id: String) = add("ach", id)
    /** 받은 기념 우표 ("작품:id"). */
    val stamps: Set<String> get() = set("stamps")
    fun addStamp(key: String) = add("stamps", key)
    /** 금빛이 된 물건 (낱말 카드 키) — 새로 금빛이 되면 보상 순간에 보여 준다. */
    val goldItems: Set<String> get() = set("gold")
    fun gild(key: String) = add("gold", key)
    /** 따라 읽기를 끝까지 한 편지 · 봉인된 편지 · 봉인 화면을 이미 본 편지 (편지 키). */
    val shadowed: Set<String> get() = set("shadow")
    fun markShadowed(key: String) = add("shadow", key)
    val sealed: Set<String> get() = set("sealed")
    fun seal(key: String) = add("sealed", key)
    val sealSeen: Set<String> get() = set("sealseen")
    fun markSealSeen(key: String) = add("sealseen", key)
    /** 오늘의 일을 다 해서 보너스를 받은 날. */
    var dayCompleteDay: Long
        get() = p.getLong("daydone", -1)
        set(v) = p.edit().putLong("daydone", v).apply()
    /** 가장 길게 이어 읽은 날 수. */
    var bestStreak: Int
        get() = p.getInt("streak:best", 0)
        set(v) = p.edit().putInt("streak:best", v).apply()
    /** 마지막으로 잰 혼자 읽기 % (오른 만큼 보상). */
    var readAloneSeen: Int
        get() = p.getInt("alone:seen", 0)
        set(v) = p.edit().putInt("alone:seen", v).apply()
    /** 정기 소포: 마지막으로 보낸 달 · 보낸 횟수 (작품마다). */
    fun parcelMonth(series: String) = p.getInt("parcel:$series:month", -1)
    fun parcels(series: String) = p.getInt("parcel:$series:n", 0)
    fun sendParcel(series: String, month: Int) = p.edit().putInt("parcel:$series:month", month).putInt("parcel:$series:n", parcels(series) + 1).apply()
    /** 손님: 오늘 맞이했는지 · 모두 몇 명. */
    var visitorDay: Long
        get() = p.getLong("visitor:day", -1)
        set(v) = p.edit().putLong("visitor:day", v).putInt("visitor:n", p.getInt("visitor:n", 0) + 1).apply()
    val visitors: Int get() = p.getInt("visitor:n", 0)

    var notifyAsked: Boolean
        get() = p.getBoolean("notify_asked", false)
        set(v) = p.edit().putBoolean("notify_asked", v).apply()
}
