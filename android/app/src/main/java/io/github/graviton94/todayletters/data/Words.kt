package io.github.graviton94.todayletters.data

import android.content.Context
import io.github.graviton94.todayletters.core.WordTimes

/**
 * 낭독 소리의 낱말 시각 읽기 (core [WordTimes]): 소리 경로 "audio/…/m1_fr.m4a" 옆 "audio/…/m1_fr.words.json",
 * 받아 둔 것 (files/) 먼저, 없으면 앱 안 assets ([AssetFiles]). 파일이 없거나 (아직 만들지 않은 소리 · 이 파일이 생기기 전에 받은 장)
 * 읽을 수 없으면 null — 화면은 그때 마디 시각 ([Narrator.chunks]) 을 쓴다.
 */
object WordTimesFiles {
    /** [audio] 소리의 낱말 시각. [shown] (그 소리와 함께 보여 주는 글) 을 주면 낱말이 그 글과 다를 때도 null. */
    fun load(ctx: Context, audio: String, shown: String? = null): WordTimes? {
        if (audio.isBlank()) return null
        val text = AssetFiles.text(ctx, WordTimes.pathFor(audio)) ?: return null
        return if (shown == null) WordTimes.parse(text) else WordTimes.parseFor(text, shown)
    }
}
