package io.github.graviton94.todayletters.ui

import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Notes

/**
 * 편지 방의 기능어 · 문형 · 관용구 노트 한 자리 (시안 room 9 · 11): 토큰 [from]..[to] (포함), 꼬리표 [tag] (“기능어 · 관사” · “관용구”),
 * 짧은 뜻 [short], 한두 줄 노트 [note]. 하나짜리는 점선 밑줄, 여럿이면 묶음 밑줄.
 * 노트 자료와 맞추기는 core `NoteBook` (core/Notes.kt, data/NoteFiles.kt) 이 맡고, [interlinearLines] 가 칸마다 맞은 자리 ([io.github.graviton94.todayletters.core.Interlinear.Unit.hit]) 를 붙인다.
 */
internal data class RoomNote(val from: Int, val to: Int, val tag: String, val short: String, val note: String)

/** 이 메시지 칸들의 노트 (같은 문형의 떨어진 부분은 하나로). 노트 글이 없는 자리는 뺀다. [lang] 은 뜻 · 노트 글의 말. */
internal fun roomNotes(line: IlLine?, lang: Lang): List<RoomNote> =
    line?.units.orEmpty().mapNotNull { it.hit }.distinct().mapNotNull { h ->
        val n = h.note ?: return@mapNotNull null
        val short = Notes.clip(Notes.pick(h.short, lang)).ifEmpty { n.short(lang) }
        RoomNote(h.start, h.end, n.tag(lang), short, n.note(lang)).takeIf { it.tag.isNotEmpty() || it.note.isNotEmpty() }
    }
