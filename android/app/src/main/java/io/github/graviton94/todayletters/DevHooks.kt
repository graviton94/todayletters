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
 *   tl.keepAudio : 소리 없는 재생을 계속 흘려 둔다. CI 의 가상 기기는 소리가 그치고 출력이 쉬는 순간 통째로 멈춰서,
 *                  녹화하는 동안 출력이 쉬지 않게 한다 (앱 동작과는 상관없음).
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
        if (intent.getBooleanExtra("tl.keepAudio", false)) keepAudio()
    }

    @Volatile private var keeping = false
    private fun keepAudio() {
        if (keeping) return
        keeping = true
        Thread {
            runCatching {
                val rate = 44100
                val size = android.media.AudioTrack.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_OUT_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(8192)
                val track = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                    .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(rate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(size).setTransferMode(android.media.AudioTrack.MODE_STREAM).build()
                val zeros = ByteArray(size)
                track.play()
                while (true) track.write(zeros, 0, zeros.size)
            }
        }.apply { isDaemon = true; name = "dev-keep-audio" }.start()
    }
}
