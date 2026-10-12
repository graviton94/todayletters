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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.graviton94.todayletters.MainActivity
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.ui.AppState
import io.github.graviton94.todayletters.ui.roman
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
    /** 그림 자산 경로 (plates/...), 없으면 null. */
    val plate: String? = null,
    /** 작품 전체에서 몇 번째 편지인지 (1부터). */
    val letterNo: Int = 0,
    /** 일요일이고 이번 주 낭독회를 아직 안 했으면. */
    val recital: Boolean = false,
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
                plate = current?.second?.plate?.image?.takeIf { it.isNotEmpty() }?.let { "plates/$it" },
                letterNo = current?.let { (_, l) -> work.chapters.flatMap { it.letters }.indexOfFirst { it.id == l.id } + 1 } ?: 0,
                recital = io.github.graviton94.todayletters.core.Recital.isSunday(s.today) && !s.recitalDone,
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

/** 홈 화면 위젯: 4×2 는 그림 · 편지 번호 · 첫 문장 · 상태, 2×2 는 그림 한 장과 한 줄. 너비에 따라 고른다. 누르면 앱. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        draw(ctx, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        draw(ctx, manager, intArrayOf(id))
    }

    companion object {
        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx) ?: return
            val ids = m.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java))
            if (ids.isNotEmpty()) draw(ctx, m, ids)
        }

        private fun draw(ctx: Context, m: AppWidgetManager, ids: IntArray) {
            val sum = TodaySummary.of(ctx)
            val art = sum?.plate?.let { plate(ctx, it) }
            ids.forEach { id ->
                val small = m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) < 200
                m.updateAppWidget(id, views(ctx, sum, art, small))
            }
        }

        /** 위젯에 넣을 그림: 큰 비트맵은 위젯에 못 실으니 짧은 변을 400px 남짓으로 줄여 읽는다. */
        internal fun plate(ctx: Context, path: String): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            AssetFiles.open(ctx, path).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 400) sample *= 2
            AssetFiles.open(ctx, path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }.getOrNull()

        internal fun views(ctx: Context, sum: TodaySummary?, art: Bitmap?, small: Boolean): RemoteViews {
            val v = RemoteViews(ctx.packageName, if (small) R.layout.widget_small else R.layout.widget_today)
            val ko = ctx.resources.configuration.locales[0].language == "ko"
            val date = LocalDate.now().format(if (ko) DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN) else DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)).uppercase()
            art?.let { v.setImageViewBitmap(R.id.w_art, it) }
            val no = sum?.letterNo?.takeIf { it > 0 }?.let(::roman)
            if (sum == null) {
                v.setTextViewText(R.id.w_date, date)
                v.setTextViewText(R.id.w_line, ctx.getString(R.string.app_name))
            } else if (small) {
                // 2×2: 편지 번호 · 오늘의 편지 · 이어 읽기. 일요일엔 낭독회.
                v.setTextViewText(R.id.w_date, if (sum.recital) ctx.getString(R.string.widget_sunday) else no?.let { ctx.getString(R.string.letter_n, it) } ?: date)
                v.setTextViewText(R.id.w_line, ctx.getString(if (sum.recital) R.string.widget_recital else R.string.widget_name))
                v.setTextViewText(
                    R.id.w_status,
                    when {
                        sum.doneToday -> ctx.getString(R.string.widget_done, sum.streak)
                        sum.recital -> ctx.getString(R.string.recital_today)
                        else -> ctx.getString(R.string.widget_streak, sum.streak)
                    },
                )
            } else {
                v.setTextViewText(R.id.w_date, no?.let { ctx.getString(R.string.widget_caps, it) } ?: date)
                v.setTextViewText(R.id.w_line, if (sum.line.isNotEmpty()) "“${sum.line}”" else ctx.getString(R.string.app_name))
                v.setTextViewText(R.id.w_read, sum.lineRead)
                val status = ctx.getString(R.string.widget_status, sum.waiting, sum.due)
                v.setTextViewText(
                    R.id.w_status,
                    when {
                        sum.doneToday -> ctx.getString(R.string.widget_done, sum.streak)
                        sum.recital -> status + " · " + ctx.getString(R.string.widget_recital)
                        sum.streak > 0 -> status + " · " + ctx.getString(R.string.widget_streak, sum.streak)
                        else -> status
                    },
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
