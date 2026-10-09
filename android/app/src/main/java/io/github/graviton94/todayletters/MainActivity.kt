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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 앱 안에서 볼륨 버튼은 늘 미디어 볼륨을 움직인다
        volumeControlStream = AudioManager.STREAM_MUSIC
        // 언어를 바꿔 다시 그릴 때는 인트로를 건너뛴다
        val restarted = savedInstanceState != null
        // 도착 알림을 다음 그 시각으로 다시 잡는다 (권한이 없으면 울릴 때 조용히 넘어감)
        runCatching { io.github.graviton94.todayletters.data.Notices.schedule(applicationContext) }
        setContent {
            val s = remember { AppState(applicationContext, deepLink = restarted) }
            TodayLettersTheme(s.app.theme, s.app.largeText) {
                Root(s, onExit = { finish() })
            }
        }
    }
}
