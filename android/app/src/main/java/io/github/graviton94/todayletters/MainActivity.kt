package io.github.graviton94.todayletters

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
        // 언어를 바꿔 다시 그릴 때는 인트로를 건너뛴다
        val restarted = savedInstanceState != null
        setContent {
            val s = remember { AppState(applicationContext, deepLink = restarted) }
            TodayLettersTheme(s.app.theme, s.app.largeText) {
                Root(s, onExit = { finish() })
            }
        }
    }
}
