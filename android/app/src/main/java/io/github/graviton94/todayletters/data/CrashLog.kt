package io.github.graviton94.todayletters.data

import android.content.Context
import android.os.Build
import io.github.graviton94.todayletters.core.Feedback
import java.io.File

/**
 * 앱이 멈췄을 때의 기록: 폰 안에만 (files/crash/last.txt) 남긴다. 밖으로는 보내지 않는다.
 * 다음에 열 때 한 번만 “지난번에 앱이 멈췄어요” 를 묻고, 사용자가 의견 보내기에서 함께 보내기를 고를 때만 메일 본문에 실린다.
 * 외부 오류 수집 서비스(Firebase 등)는 쓰지 않는다.
 */
object CrashLog {
    private const val PREFS = "crash"
    private fun file(ctx: Context) = File(File(ctx.filesDir, "crash"), "last.txt")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 앱 버전 "1.21 (121)". */
    fun version(ctx: Context): String = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        "${pi.versionName.orEmpty()} (${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi)})"
    }.getOrDefault("?")

    fun versionName(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() }.getOrDefault("?")

    /** 멈춤 잡이: 기록만 남기고 원래 처리(시스템의 ‘앱이 멈춤’)로 넘긴다. 앱이 켜질 때 한 번. */
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, previous))
    }

    private class Handler(private val app: Context, private val next: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching { record(app, t, e) }
            if (next != null) next.uncaughtException(t, e) else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }

    internal fun record(ctx: Context, t: Thread, e: Throwable) {
        val sw = java.io.StringWriter()
        e.printStackTrace(java.io.PrintWriter(sw))
        val text = Feedback.crashReport(
            time = java.time.OffsetDateTime.now().withNano(0).toString(),
            version = version(ctx),
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            thread = t.name,
            stack = sw.toString().take(32_000),
        )
        val f = file(ctx); f.parentFile?.mkdirs()
        f.writeText(text)
        // 멈추는 중이라 바로 쓴다 (commit)
        prefs(ctx).edit().putInt("count", count(ctx) + 1).putBoolean("asked", false).commit()
    }

    /** 남은 오류 기록 (없으면 null). */
    fun last(ctx: Context): String? = runCatching { file(ctx).takeIf { it.isFile }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    /** 지금까지 남은 멈춤 횟수 (지우면 0). */
    fun count(ctx: Context): Int = prefs(ctx).getInt("count", 0)

    /** 다음에 열 때 한 번만 묻는다: 새 기록이 있고 아직 묻지 않았으면. */
    fun shouldAsk(ctx: Context): Boolean = !prefs(ctx).getBoolean("asked", true) && last(ctx) != null
    fun asked(ctx: Context) { prefs(ctx).edit().putBoolean("asked", true).apply() }

    /** 기록을 보내고 나면 (메일 앱을 열고 나면) 지운다. */
    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
        prefs(ctx).edit().putInt("count", 0).putBoolean("asked", true).apply()
    }
}

/** 앱 프로세스: 멈춤 잡이를 맨 먼저 건다 (화면 없이 서비스만 켜져도). */
class LettersApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
