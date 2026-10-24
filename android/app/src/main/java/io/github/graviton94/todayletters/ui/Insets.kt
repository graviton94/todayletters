package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/*
 * 시스템 막대 · 컷아웃 (앱은 enableEdgeToEdge 라 화면 끝까지 그린다, targetSdk 36).
 * 화면의 모든 층은 이 셋으로만 막대를 피한다: 위 [statusBarsSafe] · 아래와 옆 [navigationBarsSafe] · 둘 다 [systemBarsSafe].
 * - 위: 상태 바 + 위쪽 컷아웃 (세로 화면의 펀치홀이 상태 바보다 길어도).
 * - 아래와 옆: 내비게이션 바 (제스처 막대 · 세 단추, 가로 화면이면 옆으로 간다) + 옆 · 아래 컷아웃 (가로 화면의 펀치홀).
 * - [FullDialog] 안에서는 바깥 화면에서 잰 값과 대화창이 받은 값 중 큰 쪽 (대화창 창이 막대 두께를 0 으로 받는 기기).
 * 모두 windowInsetsPadding 이라 바깥 층이 이미 피한 만큼은 안쪽에서 다시 비우지 않는다 (Root 의 길 화면 안에서 써도 두 번 띄지 않음).
 * 화면 사진 (ScreensTest.insets*) 은 Compose 뷰에 상태 바 24dp · 내비게이션 바 48dp 인셋을 직접 보내 이 길을 그대로 돈다.
 */

/** 대화창 밖(액티비티 화면)의 막대 · 컷아웃. [FullDialog] 안에서만 값이 있다 (바깥 창의 WindowInsets 그대로라 늘 지금 값). */
internal val LocalOuterBars = staticCompositionLocalOf<WindowInsets?> { null }

private val SIDES_BOTTOM = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom

/** 위로 피할 두께: 상태 바 + 위 컷아웃. */
@Composable
fun safeTop(): WindowInsets {
    val own = WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
    return LocalOuterBars.current?.let { own.union(it.only(WindowInsetsSides.Top)) } ?: own
}

/** 아래 · 옆으로 피할 두께: 내비게이션 바 (어느 쪽이든) + 옆 · 아래 컷아웃. */
@Composable
fun safeBottom(): WindowInsets {
    val own = WindowInsets.navigationBars.union(WindowInsets.displayCutout.only(SIDES_BOTTOM))
    return LocalOuterBars.current?.let { own.union(it.only(SIDES_BOTTOM)) } ?: own
}

/** 네 쪽 모두. */
@Composable
fun safeBars(): WindowInsets = safeTop().union(safeBottom())

/** 상태 바 · 위 컷아웃 아래로. */
fun Modifier.statusBarsSafe(): Modifier = composed { windowInsetsPadding(safeTop()) }

/** 내비게이션 바 위로 (가로 화면이면 그 옆으로) · 옆 컷아웃 안쪽으로. */
fun Modifier.navigationBarsSafe(): Modifier = composed { windowInsetsPadding(safeBottom()) }

/** 네 쪽 모두 (가운데 뜨는 묻는 창 · 고마워요 카드). */
fun Modifier.systemBarsSafe(): Modifier = composed { windowInsetsPadding(safeBars()) }
