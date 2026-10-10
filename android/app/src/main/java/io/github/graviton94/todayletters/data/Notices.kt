package io.github.graviton94.todayletters.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.graviton94.todayletters.MainActivity
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.ui.AppState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 오늘의 요약: 알림과 위젯이 같이 쓴다. */
data class TodaySummary(
    val name: String,
    /** 오늘 읽을 편지의 첫 문장 (배우는 언어), 다 읽었으면 마지막으로 읽은 편지의 것. */
    val line: String,
    val lineRead: String,
    val waiting: Int,
    val due: Int,
    val doneToday: Boolean,
    val streak: Int,
) {
    companion object {
        fun of(ctx: Context): TodaySummary? {
            val s = runCatching { AppState(ctx.applicationContext, deepLink = true) }.getOrNull() ?: return null
            val work = s.works.firstOrNull() ?: return null
            val id = work.series.id
            val view = s.room(id)
            val open = s.openable(id)
            val current = open.firstOrNull { (c, l) -> !s.progress(id, c, l.id).done } ?: open.lastOrNull()
            val m = current?.second?.messages?.firstOrNull()?.text
            val ui = if (ctx.resources.configuration.locales[0].language == "ko") Lang.KO else Lang.EN
            return TodaySummary(
                name = work.name[ui],
                line = m?.get(view.learn)?.let(Breaks::plain).orEmpty(),
                lineRead = m?.get(view.read)?.let(Breaks::plain).orEmpty(),
                waiting = s.waiting(id),
                due = s.dueCards().size,
                doneToday = s.today in s.store.doneDays(),
                streak = s.streak,
            )
        }
    }
}

/**
 * 도착 알림: 하루 한 번, 고른 시각에 전보처럼 짧게. 정확한 시각 권한 없이 대략 그 무렵에 (배터리 아끼기).
 * 오늘 편지를 이미 끝냈거나 작품 설정에서 껐으면 울리지 않는다.
 */
object Notices {
    private const val CHANNEL = "arrivals"
    private const val ID = 1
    private const val CHANNEL_EVENING = "evening"
    private const val ID_EVENING = 2

    fun schedule(ctx: Context) {
        val store = Store(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(store.noticeHour, 0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pending(ctx))
        // 저녁 알림 (E2): 끄지 않았으면 그 시각에 한 번
        val eh = store.eveningHour
        if (eh < 0) { am.cancel(evening(ctx)); return }
        var ev = now.toLocalDate().atTime(eh, 0)
        if (!ev.isAfter(now)) ev = ev.plusDays(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ev.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), evening(ctx))
    }

    private fun evening(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, ArrivalReceiver::class.java).setAction("io.github.graviton94.todayletters.EVENING"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 이어 읽기 지키기 (v21 3단계 E2): 저녁에 한 번만, 오늘 아무것도 안 했고 이어 읽기가 있을 때.
     * 쉼표가 있으면 ‘오늘 쉬어도 괜찮아요’로 (쉼표가 이어 준다).
     */
    fun eveningNotice(ctx: Context) {
        val store = Store(ctx)
        if (store.eveningHour < 0 || !canPost(ctx)) return
        val sum = TodaySummary.of(ctx) ?: return
        if (sum.doneToday || sum.streak <= 0) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENING, ctx.getString(R.string.evening_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.FROM_NOTICE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val rest = store.rests > 0
        val title = ctx.getString(if (rest) R.string.evening_rest_title else R.string.evening_title)
        val text = if (rest) ctx.getString(R.string.evening_rest_text, store.rests, sum.streak) else ctx.getString(R.string.evening_text, sum.streak)
        val n = NotificationCompat.Builder(ctx, CHANNEL_EVENING)
            .setSmallIcon(R.drawable.ic_notice)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_EVENING, n) }
    }

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, ArrivalReceiver::class.java).setAction("io.github.graviton94.todayletters.ARRIVE"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun arrive(ctx: Context) {
        val store = Store(ctx)
        val sum = TodaySummary.of(ctx) ?: return
        val on = Library.works(ctx).firstOrNull()?.let { store.series(it.series.id)?.arrivalNotice ?: true } ?: false
        if (!on || sum.doneToday || sum.waiting == 0 || !canPost(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.notice_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.FROM_NOTICE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (sum.due > 0) ctx.getString(R.string.notice_text_review, sum.due) else ctx.getString(R.string.notice_text)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notice)
            .setContentTitle(ctx.getString(R.string.notice_title, sum.name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (sum.line.isNotEmpty()) "“${sum.line}”\n$text" else text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID, n) }
    }
}

/** 알람 · 기기 재시작 · 시간대 변경: 알림을 띄우고 다음 날을 다시 잡는다. 위젯도 새로 그린다. */
class ArrivalReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == "io.github.graviton94.todayletters.ARRIVE") Notices.arrive(ctx)
        if (intent.action == "io.github.graviton94.todayletters.EVENING") Notices.eveningNotice(ctx)
        Notices.schedule(ctx)
        TodayWidget.refresh(ctx)
    }
}

/** 홈 화면 위젯: 오늘 날짜, 오늘의 편지 첫 문장, 기다리는 편지와 복습 수. 누르면 앱. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = views(ctx)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx) ?: return
            val ids = m.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java))
            if (ids.isNotEmpty()) { val v = views(ctx); ids.forEach { m.updateAppWidget(it, v) } }
        }

        private fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_today)
            val sum = TodaySummary.of(ctx)
            val ko = ctx.resources.configuration.locales[0].language == "ko"
            v.setTextViewText(R.id.w_date, LocalDate.now().format(if (ko) DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN) else DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)).uppercase())
            if (sum != null) {
                v.setTextViewText(R.id.w_line, if (sum.line.isNotEmpty()) "“${sum.line}”" else ctx.getString(R.string.app_name))
                v.setTextViewText(R.id.w_read, sum.lineRead)
                v.setTextViewText(
                    R.id.w_status,
                    if (sum.doneToday) ctx.getString(R.string.widget_done, sum.streak)
                    else ctx.getString(R.string.widget_status, sum.waiting, sum.due),
                )
            }
            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.w_root, open)
            return v
        }
    }
}
