package io.github.graviton94.todayletters.ui

import io.github.graviton94.todayletters.core.Token

/**
 * 편지 방의 기능어 · 문형 · 관용구 노트 한 자리 (시안 room 9 · 11): 토큰 [from]..[to] (포함), 꼬리표 [tag] (“기능어 · 관사” · “관용구”),
 * 짧은 뜻 [short], 한두 줄 노트 [note]. 하나짜리는 점선 밑줄, 여럿이면 묶음 밑줄.
 *
 * 노트 자료와 맞추기는 core `NoteBook` (core/Notes.kt, `NoteBook.forToken(lexKey)` · `NoteBook.match(tokens)` → Hit(start, end, kind, short, note))
 * 가 맡는다. 그 파일이 들어오면 [roomNotes] 를 그것으로 잇는다 (지금은 빈 목록: 카드는 사전 뜻 · 표현 뜻만 보인다).
 */
internal data class RoomNote(val from: Int, val to: Int, val tag: String, val short: String, val note: String)

/** 이 메시지 토큰 [tokens] 의 노트 (작품 [series]). */
@Suppress("UNUSED_PARAMETER")
internal fun roomNotes(s: AppState, series: String, tokens: List<Token>): List<RoomNote> = emptyList()
