package io.github.graviton94.todayletters.data

import android.content.Context
import io.github.graviton94.todayletters.core.Lexicon
import io.github.graviton94.todayletters.core.NoteBook
import io.github.graviton94.todayletters.core.VocabSet
import java.util.concurrent.ConcurrentHashMap

/**
 * 기능어 · 문형 · 관용구 노트 읽기 (core [NoteBook], scripts/notes.py): 배우는 말의 assets `notes/notes_<말>.json` +
 * 그 작품 · 자료의 `letters/<작품>/idioms[_en|_ko].json`. 둘 다 앱 안 (작아서 R2 에 두지 않는다). 없으면 [NoteBook.EMPTY].
 * 배우는 말은 사전 [lexicon] 의 말 ([Lexicon.lang]) — 품사 · 분리동사 뜻도 그 사전에서 찾는다. 작품 · 자료마다 한 번 읽어 둔다.
 */
object NoteFiles {
    private val books = ConcurrentHashMap<String, NoteBook>()

    /** [series] 작품, [deck] 자료 ("" 원어 · "en" · "ko") 의 노트. 사전이 없으면 빈 것. */
    fun load(ctx: Context, series: String, deck: String, lexicon: Lexicon?): NoteBook {
        if (lexicon == null) return NoteBook.EMPTY
        return books.getOrPut("$series/$deck/${lexicon.lang.code}") {
            fun read(path: String) = runCatching { ctx.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
            val notes = read("notes/notes_${lexicon.lang.code}.json") ?: return@getOrPut NoteBook.EMPTY
            runCatching { NoteBook.parse(notes, read("letters/$series/${VocabSet.file("idioms", deck)}")) { lexicon[it] } }.getOrDefault(NoteBook.EMPTY)
        }
    }
}
