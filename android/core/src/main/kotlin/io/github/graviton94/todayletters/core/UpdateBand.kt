package io.github.graviton94.todayletters.core

/**
 * 서재 아래 업데이트 띠 (시안 update_backup.html): Play 의 창을 저절로 띄우지 않고, 우리 띠로 알리고 → 받는 중 % → 다시 열기.
 * Play (data/Updates) 가 알려 주는 것을 이 상태로 바꾸는 규칙만 여기에 둔다 (Play 없이 시험).
 */
sealed interface UpdateBand {
    /** 띠 없음. */
    data object Hidden : UpdateBand
    /** “새 버전이 나왔어요” · 나중에 · 받기. [bytes] 는 Play 가 알려 줄 때만 (0 이면 크기를 쓰지 않는다). */
    data class Offer(val bytes: Long) : UpdateBand
    /** “새 버전을 받는 중” + % + 막대. */
    data class Fetching(val done: Long, val total: Long) : UpdateBand {
        val percent: Int get() = if (total <= 0) 0 else io.github.graviton94.todayletters.core.Fetching.percent(done, total)
    }
    /** 받다 멈춤: 한 줄 + 다시 받기. */
    data object Failed : UpdateBand
    /** “새 버전을 받았어요” 100% · 나중에 · 다시 열기. */
    data object Ready : UpdateBand
}

object UpdateFlow {
    /** Play InstallStatus 를 옮긴 것 (play-core 없이 core 에서 쓰려고). */
    enum class Install { NONE, PENDING, DOWNLOADING, DOWNLOADED, INSTALLING, INSTALLED, FAILED, CANCELED }

    /**
     * Play 에 물어본 결과 (서재에 들어올 때 · 앱으로 돌아올 때).
     * [available] = 새 버전이 있고 flexible 로 받을 수 있음, [askedToday] = 오늘 이미 ‘나중에’ · Play 창 닫기를 함,
     * [readyDismissed] = 이번에 앱을 연 동안 다 받은 띠에서 ‘나중에’ 를 누름 (다음에 앱을 열면 다시 보인다).
     */
    fun onInfo(
        now: UpdateBand, available: Boolean, install: Install, done: Long, total: Long,
        askedToday: Boolean, readyDismissed: Boolean,
    ): UpdateBand = when (install) {
        Install.DOWNLOADED -> if (readyDismissed) UpdateBand.Hidden else UpdateBand.Ready
        Install.PENDING, Install.DOWNLOADING -> UpdateBand.Fetching(done, total)
        Install.INSTALLING, Install.INSTALLED -> UpdateBand.Hidden
        else -> when {
            now is UpdateBand.Failed && available -> now
            available && !askedToday -> UpdateBand.Offer(total.coerceAtLeast(0))
            else -> UpdateBand.Hidden
        }
    }

    /** 받는 동안 Play 가 보내는 상태. */
    fun onInstall(now: UpdateBand, install: Install, done: Long, total: Long, readyDismissed: Boolean): UpdateBand = when (install) {
        Install.PENDING, Install.DOWNLOADING -> UpdateBand.Fetching(done, total)
        Install.DOWNLOADED -> if (readyDismissed) UpdateBand.Hidden else UpdateBand.Ready
        Install.FAILED, Install.CANCELED -> UpdateBand.Failed
        Install.INSTALLING, Install.INSTALLED -> UpdateBand.Hidden
        Install.NONE -> now
    }

    /** ‘받기’ 뒤 Play 의 확인 창이 닫힘. [accepted] 면 받기 시작 (아직 상태가 안 왔으면 0%), 아니면 그날은 띠 없음. */
    fun onDialog(now: UpdateBand, accepted: Boolean, failed: Boolean, total: Long): UpdateBand = when {
        failed -> UpdateBand.Failed
        !accepted -> UpdateBand.Hidden
        now is UpdateBand.Fetching || now is UpdateBand.Ready -> now
        else -> UpdateBand.Fetching(0, total)
    }
}
