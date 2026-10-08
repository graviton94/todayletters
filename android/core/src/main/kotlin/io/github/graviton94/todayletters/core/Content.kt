package io.github.graviton94.todayletters.core

/** 세 언어 글: 원어 · 영어 · 한국어. 모든 편지 데이터는 세 줄을 다 갖는다. */
data class Tri(val lines: Map<Lang, String>) {
    operator fun get(lang: Lang): String = lines[lang] ?: lines.values.first()
}

data class Message(val text: Tri)
data class Word(val text: Tri, val pos: String = "", val ipa: String = "")
data class Plate(val title: Tri, val date: String, val collection: String, val image: String = "")

/**
 * 편지 한 통 = 대화방 한 번.
 * [reply] 는 별자리 잇기로 만들 답장 문장 (테오가 쓰는 말). 없으면 편지의 짧은 문장 하나로 대신한다.
 */
data class Letter(
    val id: String,
    val date: String,
    val place: String,
    val mood: String,
    val messages: List<Message>,
    val words: List<Word>,
    val note: Tri?,
    val plate: Plate?,
    val reply: Tri? = null,
)

data class Chapter(val series: String, val id: String, val title: Tri, val free: Boolean, val letters: List<Letter>)

/**
 * 하루 도착 규칙: 하루에 새 편지를 [perDay] 통까지 열 수 있다.
 * 다 읽은 편지 수와 오늘 이미 연 편지 수로, 지금 열 수 있는 편지(번호)를 정한다.
 */
object Arrivals {
    fun openable(total: Int, done: Int, openedToday: Int, perDay: Int): Int =
        (done + (perDay - openedToday).coerceAtLeast(0)).coerceAtMost(total)

    /** 오늘 더 받을 편지가 남았는가 (편지함의 "새 편지 n통"). */
    fun waiting(total: Int, done: Int, openedToday: Int, perDay: Int): Int =
        (openable(total, done, openedToday, perDay) - done).coerceAtLeast(0)
}

/** 답장 문제 만들기. 편지 데이터에서 저절로 만든다 (사람이 문제를 따로 쓰지 않음). */
object Exercises {
    /** 별자리 잇기: 문장을 낱말(띄어쓰기 단위) 조각으로. 순서는 섞되 정답 순서와 같지 않게. */
    fun constellation(sentence: String, seed: Int): Pair<List<String>, List<String>> {
        val answer = sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (answer.size < 2) return answer to answer
        val rnd = java.util.Random(seed.toLong())
        var shuffled = answer
        repeat(8) {
            shuffled = answer.shuffled(rnd)
            if (shuffled != answer) return answer to shuffled
        }
        return answer to answer.reversed()
    }

    fun isRight(answer: List<String>, picked: List<String>) = answer == picked

    /** 별자리 잇기에서 이 낱말이 다음 차례인가. 같은 낱말이 두 번 나와도 글자로 비교하므로 어느 쪽을 눌러도 된다. */
    fun isNext(answer: List<String>, done: Int, word: String) = answer.getOrNull(done) == word

    /** 답장 문장: 저자가 쓴 [Letter.reply] 가 있으면 그것, 없으면 낱말 수가 4~10개인 가장 짧은 메시지. */
    fun replyFor(letter: Letter, learn: Lang): Pair<String, Tri> {
        letter.reply?.let { return it[learn] to it }
        val pick = letter.messages.map { it.text }
            .filter { it[learn].split(Regex("\\s+")).size in 4..10 }
            .minByOrNull { it[learn].length } ?: letter.messages.last().text
        return pick[learn] to pick
    }

    /** 낱말 맞추기: (배우는 언어, 읽는 언어) 짝. */
    fun pairs(letter: Letter, learn: Lang, read: Lang) = letter.words.map { it.text[learn] to it.text[read] }
}

/** 편지 한 통의 진도: 몇 번째 메시지까지 받았는지, 답장을 마쳤는지. 메시지 단위로 저장해 중간에 나가도 이어진다. */
data class LetterProgress(val shown: Int = 0, val replied: Set<ReplyMode> = emptySet(), val done: Boolean = false) {
    fun reveal(total: Int) = copy(shown = (shown + 1).coerceAtMost(total))
    fun allShown(total: Int) = shown >= total
    fun reply(mode: ReplyMode) = copy(replied = replied + mode)
    /** 고른 답장 방식 중 이 편지에서 하는 것 (별자리 잇기 · 따라 읽기). 다 하면 편지 완료. */
    fun complete(modes: Set<ReplyMode>) = copy(done = modes.filter { it in Plays.inLetter }.all { it in replied })
}

object Plays {
    /** 편지 안에서 하는 답장. 낱말 맞추기 · 듣고 쓰기는 단어장 복습에서. */
    val inLetter = listOf(ReplyMode.CONSTELLATION, ReplyMode.ALOUD)
}
