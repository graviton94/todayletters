package io.github.graviton94.todayletters

import android.content.Context
import android.content.Intent
import io.github.graviton94.todayletters.data.Library
import io.github.graviton94.todayletters.data.Store

/**
 * 개발자 도구 (debug 빌드에만, [BuildConfig.DEV_TOOLS]): 가상 기기 녹화 (scripts/fgs_video.sh) 가
 * 처음 소개 · 튜토리얼을 건너뛰고 바로 쓰는 사람의 화면에서 시작하게 한다. release 빌드에서는 읽지 않는다.
 *
 *   adb shell am start -n …/.MainActivity --ez tl.ready true [--ez tl.unlock true]
 *   tl.ready  : 처음 소개 · 튜토리얼 · 배울 말 고르기 · 프롤로그 · 알림 묻기를 이미 한 것으로
 *   tl.unlock : 모든 장 열기 (Store.debugUnlockAll) — 미리 받기가 여러 장을 받도록
 */
internal object DevHooks {
    fun apply(ctx: Context, intent: Intent?) {
        if (!BuildConfig.DEV_TOOLS || intent == null) return
        val store = Store(ctx)
        if (intent.getBooleanExtra("tl.ready", false)) {
            store.onboarded = true
            store.tutorialDone = true
            store.notifyAsked = true
            store.langNoticeSeen = true
            Library.works(ctx).forEach { w -> store.markLearnChosen(w.series.id); store.markPrologueSeen(w.series.id) }
        }
        if (intent.hasExtra("tl.unlock")) store.debugUnlockAll = intent.getBooleanExtra("tl.unlock", false)
    }
}
