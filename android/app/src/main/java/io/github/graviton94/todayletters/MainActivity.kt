package io.github.graviton94.todayletters

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import io.github.graviton94.todayletters.design.TodayLettersTheme
import io.github.graviton94.todayletters.ui.AppState
import io.github.graviton94.todayletters.ui.Root

class MainActivity : ComponentActivity() {
    companion object { const val FROM_NOTICE = "from_notice"; const val SERIES = "series" }
    private var state: AppState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 앱 안에서 볼륨 버튼은 늘 미디어 볼륨을 움직인다
        volumeControlStream = AudioManager.STREAM_MUSIC
        // 첫 화면은 늘 나온다. 언어를 바꿔 다시 그릴 때만 건너뛰고, 알림으로 들어오면 첫 화면 뒤에 오늘 화면으로.
        val recreated = savedInstanceState != null
        val fromNotice = intent.getBooleanExtra(FROM_NOTICE, false)
        // debug 빌드의 녹화용 설정 (release 에서는 아무것도 하지 않음)
        if (!recreated) DevHooks.apply(applicationContext, intent)
        // 알림이 말한 작품으로 연다 (누른 것이 곧 그 작품을 고른 것)
        if (fromNotice && !recreated) noticeSeries(intent)?.let { io.github.graviton94.todayletters.data.Store(applicationContext).lastSeries = it }
        // 도착 알림을 다음 그 시각으로 다시 잡는다 (권한이 없으면 울릴 때 조용히 넘어감)
        runCatching { io.github.graviton94.todayletters.data.Notices.schedule(applicationContext) }
        setContent {
            val s = remember { AppState(applicationContext, deepLink = fromNotice || recreated, recreated = recreated).also { state = it; connectBilling(it) } }
            TodayLettersTheme(s.app.theme, s.app.largeText) {
                Root(s, onExit = { finish() })
            }
        }
    }

    /** 앱이 떠 있을 때 알림을 눌렀다: 그 작품의 오늘로. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!intent.getBooleanExtra(FROM_NOTICE, false)) return
        val s = state ?: return
        val id = noticeSeries(intent) ?: s.current
        if (s.stage == io.github.graviton94.todayletters.core.Stage.MAIN && s.store.onboarded && !s.needsWelcome(id)) s.enter(id)
        else s.store.lastSeries = id
    }

    private fun noticeSeries(i: android.content.Intent): String? = i.getStringExtra(SERIES)?.takeIf { id ->
        id.isNotEmpty() && io.github.graviton94.todayletters.data.Library.works(applicationContext).any { it.series.id == id }
    }

    /** 자정을 넘겨 다시 열면 오늘 기준으로 다시 센다 (오늘의 편지 · 인사 · 오늘의 봉투). */
    override fun onResume() {
        super.onResume()
        state?.resumed()
        // 결제: Play 에 붙어 값과 산 것을 다시 읽는다 (밖에서 구독을 해지 · 결제했을 수 있다). Play 가 없어도 조용히 넘어간다.
        state?.let(::connectBilling)
    }

    private fun connectBilling(s: AppState) { runCatching { s.billing.connect(s.baseWorks.map { it.series.id }) } }

    override fun onDestroy() {
        state?.billing?.end()
        super.onDestroy()
    }
}
