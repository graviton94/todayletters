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

    var notifyAsked: Boolean
        get() = p.getBoolean("notify_asked", false)
        set(v) = p.edit().putBoolean("notify_asked", v).apply()
}
