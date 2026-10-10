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
    companion object { const val FROM_NOTICE = "from_notice" }
    private var state: AppState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 앱 안에서 볼륨 버튼은 늘 미디어 볼륨을 움직인다
        volumeControlStream = AudioManager.STREAM_MUSIC
        // 첫 화면은 늘 나온다. 언어를 바꿔 다시 그릴 때만 건너뛰고, 알림으로 들어오면 첫 화면 뒤에 오늘 화면으로.
        val recreated = savedInstanceState != null
        val fromNotice = intent.getBooleanExtra(FROM_NOTICE, false)
        // 도착 알림을 다음 그 시각으로 다시 잡는다 (권한이 없으면 울릴 때 조용히 넘어감)
        runCatching { io.github.graviton94.todayletters.data.Notices.schedule(applicationContext) }
        setContent {
            val s = remember { AppState(applicationContext, deepLink = fromNotice || recreated, recreated = recreated).also { state = it } }
            TodayLettersTheme(s.app.theme, s.app.largeText) {
                Root(s, onExit = { finish() })
            }
        }
    }

    /** 자정을 넘겨 다시 열면 오늘 기준으로 다시 센다 (오늘의 편지 · 인사 · 오늘의 봉투). */
    override fun onResume() {
        super.onResume()
        state?.resumed()
    }
}
