package io.github.graviton94.todayletters.core

/**
 * 앱을 켰을 때의 순서.
 *
 *   그날 첫 실행: 봉투 인트로 (소인 → 봉랍 탭 → 첫 줄 미리보기 → 눌러서 들어가기)
 *   같은 날 다시: 짧은 인트로 (제목 → 눌러서 들어가기)
 *   처음 설치:    인트로 다음에 처음 소개 (읽는 언어 → 첫 작품 → 배우는 언어 → 답장 방식 · 하루 편지 수 → 알림)
 *
 * 불러오기(콘텐츠 확인 · 구매 복원)는 인트로 뒤에서 함께 돈다. 따로 로딩 화면을 두지 않고,
 * 준비가 끝나야 "눌러서 들어가기"가 나타난다. 움직임 줄이기 · 알림으로 바로 들어오기는 인트로를 건너뛴다.
 */
enum class Stage { INTRO_ENVELOPE, INTRO_SHORT, ONBOARDING, MAIN }

object Launch {
    fun plan(firstRun: Boolean, firstOfDay: Boolean, reducedMotion: Boolean, deepLink: Boolean): List<Stage> {
        val intro = when {
            deepLink || reducedMotion -> null
            firstRun || firstOfDay -> Stage.INTRO_ENVELOPE
            else -> Stage.INTRO_SHORT
        }
        return listOfNotNull(intro, if (firstRun) Stage.ONBOARDING else null, Stage.MAIN)
    }
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
