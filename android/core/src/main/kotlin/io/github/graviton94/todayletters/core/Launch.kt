package io.github.graviton94.todayletters.core

/**
 * 앱을 켰을 때의 순서.
 *
 *   늘:          오프닝 (앱 이름 · 앞으로 올 발신인들의 봉투 · 눌러서 열기)
 *   처음 설치:    오프닝 다음에 처음 소개 두 단계 (이렇게 도착해요 → 작품과 배울 언어)
 *   그날 첫 실행: 오프닝 다음에 오늘의 봉투 (고른 작품의 편지 한 통이 도착)
 *
 * 불러오기는 오프닝 뒤에서 함께 돈다. 따로 로딩 화면을 두지 않고, 준비가 끝나야 "눌러서 열기"가 나타난다.
 * 알림으로 바로 들어오기(언어를 바꿔 다시 그릴 때 포함)는 오프닝과 봉투를 건너뛴다.
 */
enum class Stage { OPENING, ONBOARDING, TODAY, MAIN }

object Launch {
    fun plan(firstRun: Boolean, firstOfDay: Boolean, deepLink: Boolean): List<Stage> = listOfNotNull(
        if (deepLink) null else Stage.OPENING,
        when {
            firstRun -> Stage.ONBOARDING
            firstOfDay && !deepLink -> Stage.TODAY
            else -> null
        },
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
