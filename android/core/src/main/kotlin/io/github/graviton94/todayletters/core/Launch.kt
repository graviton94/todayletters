package io.github.graviton94.todayletters.core

/**
 * 앱을 켰을 때의 순서.
 *
 *   처음 설치: 처음 소개 (이렇게 도착해요 → 작품과 배울 언어) → 서재
 *   그다음부터: 바로 서재 (오늘 도착한 편지는 시리즈 카드에 ‘오늘 도착’으로)
 */
enum class Stage { ONBOARDING, MAIN }

object Launch {
    fun plan(firstRun: Boolean): List<Stage> = listOfNotNull(if (firstRun) Stage.ONBOARDING else null, Stage.MAIN)
}

/**
 * 화면별 도움말. 처음 그 화면에 왔을 때 한 번 저절로 보여 주고, (?) 를 누르면 그 화면 것만 다시 보여 준다.
 * 설정 › 도움 › "사용법 다시 보기"는 모두 처음 상태로 되돌린다.
 */
class Coach(seen: Set<String> = emptySet()) {
    var seen: Set<String> = seen; private set

    /** 지금 보여 줄 차례인가: 아직 안 봤고, 화면이 조용할 때 (시트 · 타이핑 · 재생 중이 아닐 때). */
    fun due(screen: String, calm: Boolean) = calm && screen !in seen
    fun done(screen: String) { seen = seen + screen }
    /** (?) */
    fun again(screen: String) { seen = seen - screen }
    fun reset() { seen = emptySet() }
}
