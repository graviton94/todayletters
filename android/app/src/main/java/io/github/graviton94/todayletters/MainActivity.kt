package io.github.graviton94.todayletters

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.graviton94.todayletters.core.ThemeMode
import io.github.graviton94.todayletters.design.TodayLettersTheme
import io.github.graviton94.todayletters.ui.Specimen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 화면들이 들어오기 전까지는 토큰 견본을 보여 준다 (테마 · 글자 크기 확인용).
            var mode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
            var large by rememberSaveable { mutableStateOf(false) }
            TodayLettersTheme(mode, large) {
                Specimen(mode, large, onMode = { mode = it }, onLarge = { large = it })
            }
        }
    }
}
