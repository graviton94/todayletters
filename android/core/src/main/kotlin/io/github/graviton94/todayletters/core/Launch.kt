package io.github.graviton94.todayletters.core

/**
 * 앱을 켰을 때의 순서.
 *
 *   앱을 켤 때마다: 첫 화면 (언어 미술관 입구 · 모은 그림이 천천히 바뀜) → (처음 설치면 환영 한 장) → 시간의 서재
 *   처음 설치의 나머지 (배울 말 고르기 → 편지 I) 는 서재에서 이어진다 ([Tutorial]).
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
 * 화면별 도움말 (?). 처음 설치의 튜토리얼(편지 I)이 걸음마다 한 줄씩 안내하고, 그 뒤로는 어느 화면에서도 저절로 뜨지 않는다:
 * 맨 위 줄의 (?) 를 누를 때만 그 화면 것을 보여 준다 ([asked]). [auto] 를 켜면 예전처럼 처음 온 화면에서 한 번 저절로.
 * 설정 › 도움 › "사용법 다시 보기"는 모두 처음 상태로 되돌린다.
 */
class Coach(seen: Set<String> = emptySet()) {
    var seen: Set<String> = seen; private set
    /** (?) 로 불러낸 화면. */
    var asked: String? = null; private set

    /** 지금 보여 줄 차례인가: 화면이 조용할 때 (시트 · 타이핑 · 재생 중이 아닐 때), (?) 로 불렀거나 [auto] 이고 아직 안 봤으면. */
    fun due(screen: String, calm: Boolean, auto: Boolean = false) = calm && (asked == screen || (auto && screen !in seen))
    fun done(screen: String) { seen = seen + screen; if (asked == screen) asked = null }
    /** (?) */
    fun again(screen: String) { seen = seen - screen; asked = screen }
    fun reset() { seen = emptySet(); asked = null }
}

/**
 * 처음 설치의 튜토리얼 (한 번): 입구 → 환영 → 서재 (빈센트 카드에서 배울 말 고르기) → 편지 I (걸음마다 처음 한 번 한 줄 안내)
 * → “이제 혼자서도 괜찮아요” → 끝. 이미 편지를 한 통이라도 마친 사람은 끝난 것으로 본다.
 */
object Tutorial {
    /** 안내 한 줄을 두는 걸음 (strings 의 guide_<걸음>). */
    val steps = listOf("read", "match", "aloud", "reply", "walk")

    /** 튜토리얼을 마친 것으로 볼까: 저장된 표시가 있거나, 편지를 한 통이라도 마쳤다. */
    fun done(flag: Boolean, lettersDone: Int) = flag || lettersDone > 0

    /** 이 걸음의 안내 한 줄을 보여 줄까: 튜토리얼 중이고, 그 걸음에 처음 들어왔다. */
    fun guide(step: String, done: Boolean, seen: Set<String>) = !done && step in steps && step !in seen
}
