package io.github.graviton94.todayletters.core

/**
 * 낭독 재생기 하나를 여러 화면이 나눠 쓸 때의 차례 (0.9.9 폰 피드백: 메뉴를 옮기면 소리가 안 나거나 앞 화면 소리가 나옴).
 * 소리 자체는 앱의 Narrator 가 내고, 누가 언제 소리를 낼 수 있는지는 여기서 고른다.
 *
 * - 끝났을 때 부를 것은 한 번만 부르되 **끝까지 들었는가**(true) · 멈췄거나 다른 소리로 바뀌었는가(false) 를 함께 준다.
 *   다음 문장 · 다음 줄로 이어 읽는 쪽은 true 일 때만 잇는다 (멈춘 소리의 ‘끝’이 다음 소리를 불러 새 화면 소리를 자르지 않게).
 * - 멈추는 중에 (앞 소리의 ‘끝’ 안에서 곧바로) 새 소리를 부르면 받지 않는다.
 * - 화면이 바뀌면 [enter]. 지난 화면 이름으로 오는 소리 (나가는 화면의 늦은 자동 재생) 는 받지 않는다.
 * - 화면이 사라질 때는 [owns] 로 제 소리일 때만 멈춘다 (나가는 화면의 onDispose 가 새 화면의 소리를 끄지 않게).
 */
class Voice {
    /** 지금 화면 (AppState 가 길을 옮길 때마다 건다). null 이면 누구의 소리든 받는다. */
    var scene: Any? = null
        private set
    /** 지금 (또는 마지막) 소리를 낸 주인. */
    var owner: Any? = null
        private set
    private var done: ((Boolean) -> Unit)? = null
    private var cutting = false

    fun enter(next: Any?) { scene = next }

    /** 새 소리를 시작해도 되는가: 멈추는 중이 아니고, 주인이 지난 화면이 아닐 때. */
    fun admits(by: Any?): Boolean = !cutting && (by == null || scene == null || by == scene)

    /** 새 소리가 시작됐다 (앞 소리는 이미 [end] 로 끝낸 뒤). */
    fun begin(by: Any?, onEnd: (Boolean) -> Unit) { owner = by; done = onEnd }

    /** 지금 소리가 [by] 의 것인가 (주인 없이 낸 소리는 누구의 것도 아니어서 늘 멈출 수 있다). */
    fun owns(by: Any?): Boolean = owner == null || owner == by

    /** 기다리던 것을 바꾼다 ([Narrator.follow]). */
    fun follow(onEnd: (Boolean) -> Unit) { done = onEnd }

    /** 소리가 끝났다: [completed] 면 끝까지 들은 것. 기다리던 것을 한 번 부른다. */
    fun end(completed: Boolean) {
        val d = done ?: return
        done = null
        if (completed) { d(true); return }
        cutting = true
        try { d(false) } finally { cutting = false }
    }

    companion object {
        /**
         * 차례로 읽기 (편지 방 · 알림 이어 읽기): [k] 번째 소리가 끝난 뒤 갈 곳. [jump] 는 단추로 넘길 곳
         * (0 없음 · 1 다음 · -1 이전 · 2 이 문장 처음부터). 단추 없이 멈췄으면 (다른 소리 · 화면을 떠남) null = 그만.
         */
        fun next(k: Int, completed: Boolean, jump: Int): Int? = when {
            !completed && jump == 0 -> null
            jump == -1 -> (k - 1).coerceAtLeast(0)
            jump == 2 -> k
            else -> k + 1
        }
    }
}
