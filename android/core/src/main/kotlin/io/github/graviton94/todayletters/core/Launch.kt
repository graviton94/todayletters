package io.github.graviton94.todayletters.core

/**
 * 앱을 켰을 때의 순서.
 *
 *   그날 처음 열 때: 첫 화면 (언어 미술관 입구 · 오늘의 작품) → (처음 설치면 처음 소개) → 시간의 서재
 *   같은 날 다시 열 때 · 알림으로 열 때: 바로 서재 (처음 설치면 처음 소개부터)
 */
enum class Stage { OPENING, ONBOARDING, MAIN }

object Launch {
    fun plan(firstRun: Boolean, firstOfDay: Boolean = true, deepLink: Boolean = false): List<Stage> = listOfNotNull(
        if (!deepLink && (firstRun || firstOfDay)) Stage.OPENING else null,
        if (firstRun) Stage.ONBOARDING else null,
        Stage.MAIN,
    )
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
