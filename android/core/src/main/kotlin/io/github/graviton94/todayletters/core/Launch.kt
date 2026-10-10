package io.github.graviton94.todayletters.core

/**
 * 앱을 켰을 때의 순서.
 *
 *   앱을 켤 때마다: 첫 화면 (언어 미술관 입구 · 오늘의 작품) → (처음 설치면 처음 소개) → 시간의 서재
 *   화면을 다시 그릴 때(언어 바꿈 · 회전, [deepLink]): 첫 화면 없이. 앱은 firstOfDay = true 로 부른다.
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
