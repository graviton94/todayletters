package io.github.graviton94.todayletters.core

/**
 * 처음부터 다시 보기: 마친 편지를 첫 메시지부터 새 편지처럼 다시 받는다 (걸음도 다시 할 수 있다).
 * 그동안의 진도는 따로 두고, 보상(화폐 · XP · 메달 · 도장 · 이어 읽은 날)은 처음 마칠 때 한 번만.
 * 다시 보기에서 처음으로 실제로 한 걸음 (전에 건너뛰었던 걸음) 만 저장된 진도에 ‘한 것’ 으로 남기고 그 걸음 보상을 준다.
 */
object Replay {
    /** 다시 보기를 시작할 때의 진도: 첫 메시지부터, 걸음도 처음부터. */
    fun start(): LetterProgress = LetterProgress()

    /** 마친 편지만 처음부터 다시 볼 수 있다. */
    fun can(saved: LetterProgress): Boolean = saved.done

    /**
     * 다시 보기에서 걸음 [mode] 를 실제로 마쳤다: 저장된 진도 [saved] 에 남길 진도와 걸음 보상을 줄지.
     * 이미 한 걸음이면 그대로 · 보상 없음. 건너뛰었던 (아직 안 한) 걸음이면 한 것으로 · 그 걸음 보상 한 번.
     */
    fun stepDone(saved: LetterProgress, mode: ReplyMode): Pair<LetterProgress, Boolean> =
        if (mode in saved.replied) saved to false else saved.reply(mode) to true

    /** 편지를 마친 보상 (편지 화폐 · XP · 이어 읽은 날 · 오늘의 편지 마침 한 장): 처음 마칠 때만, 다시 보기로 마친 것은 없음. */
    fun paysLetter(savedDoneBefore: Boolean, replaying: Boolean): Boolean = !savedDoneBefore && !replaying
}
