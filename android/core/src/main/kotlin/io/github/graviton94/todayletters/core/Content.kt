package io.github.graviton94.todayletters.core

/** 세 언어 글: 원어 · 영어 · 한국어. 모든 편지 데이터는 세 줄을 다 갖는다. */
data class Tri(val lines: Map<Lang, String>) {
    operator fun get(lang: Lang): String = lines[lang] ?: lines.values.first()
}

data class Message(val text: Tri)
data class Word(val text: Tri, val pos: String = "", val ipa: String = "")
/** 그림 속 산책의 한 자리: 그림 위의 위치(0~1), 이름, 큐레이터 설명, 낱말, 그 요소가 나오는 편지 문장 번호. */
data class Spot(val x: Float, val y: Float, val title: Tri, val note: Tri, val word: Word?, val quote: Int? = null)

data class Plate(val title: Tri, val date: String, val collection: String, val image: String = "", val spots: List<Spot> = emptyList())

/**
 * 메신저 같은 순간: 문장 [after] 다음에 끼어든다 (-1 이면 첫 문장 앞). 모두 편지에 실제로 나온 일에서만 만든다.
 * 위치 공유 · 사진(그림) · 스케치 · 송금 · 알림 줄 · 날씨 공유.
 */
sealed interface Moment {
    val after: Int
    data class Location(override val after: Int, val place: String, val title: Tri, val address: String) : Moment
    /** 그림 사진 (assets/plates) 또는 [sketch] 면 편지 원본의 스케치 (assets/sketches). */
    data class Photo(override val after: Int, val image: String, val caption: Tri, val sketch: Boolean = false, val credit: String = "") : Moment
    /** 송금: [outgoing] 이면 보내는 사람(빈센트)이 보낸 것, 아니면 받는 사람(테오)이 보낸 것. */
    data class Transfer(override val after: Int, val amount: String, val label: Tri, val memo: Tri, val outgoing: Boolean = false) : Moment
    /** 가운데 알림 줄: 도착 · 이사 같은 실제 사건. */
    data class Notice(override val after: Int, val text: Tri) : Moment
    /** 날씨 공유: [value] 는 큰 글자 (눈 60cm), [quote] 는 편지 원문 한 토막. */
    data class Weather(override val after: Int, val title: Tri, val value: Tri, val quote: String, val icon: String = "snow") : Moment
}

/** 작품의 장소 (손으로 그린 지도 위 0~1 위치, 실제 위도 · 경도). */
data class Place(val id: String, val x: Float, val y: Float, val name: Tri, val note: Tri, val lat: Double = 0.0, val lng: Double = 0.0)

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
    val moments: List<Moment> = emptyList(),
    /** 대화방 위의 상태 메시지 (그 무렵 빈센트의 한마디). */
    val status: Tri? = null,
)

data class Chapter(val series: String, val id: String, val title: Tri, val free: Boolean, val letters: List<Letter>)

/**
 * 하루 도착 규칙: 하루에 새 편지를 [perDay] 통까지 열 수 있다.
 * 다 읽은 편지 수와 오늘 이미 연 편지 수로, 지금 열 수 있는 편지(번호)를 정한다.
 */
object Arrivals {
    /**
     * 지금 열 수 있는 편지 수 (앞에서부터). [started] 는 한 번이라도 연 편지 수(다 읽은 것 포함).
     * 오늘 연 편지는 다 읽지 않았어도 계속 열려 있다. 하루 [perDay] 통까지 새로 도착한다.
     */
    fun openable(total: Int, started: Int, openedToday: Int, perDay: Int): Int =
        (started + (perDay - openedToday).coerceAtLeast(0)).coerceAtMost(total)

    /** 열 수 있지만 아직 다 읽지 않은 편지 수 (편지함의 숫자). */
    fun waiting(total: Int, done: Int, started: Int, openedToday: Int, perDay: Int): Int =
        (openable(total, started, openedToday, perDay) - done).coerceAtLeast(0)

    /** [index](0부터) 번째 편지가 며칠 뒤에 오는가. 0 이면 이미 왔다. */
    fun daysUntil(index: Int, openable: Int, perDay: Int): Int =
        if (index < openable) 0 else (index - openable) / perDay.coerceAtLeast(1) + 1

    /**
     * 아직 열지 않은 다음 편지가 도착한 날. 마지막으로 새 편지를 연 날([lastDay])에 하루 몫([perDay])을 다 열었으면
     * 그다음 날, 아니면 그날. 연 적이 없으면 null (처음 받은 편지).
     */
    fun arrivedOn(lastDay: Long, openedThatDay: Int, perDay: Int): Long? =
        if (lastDay < 0) null else if (openedThatDay >= perDay) lastDay + 1 else lastDay

    /** 부재중: 도착한 날이 오늘보다 앞인데 아직 열지 않은 편지. */
    fun missed(arrived: Long?, today: Long, unread: Int): Boolean = arrived != null && arrived < today && unread > 0
}

/** 이어 읽은 날 (편지를 끝낸 날이 하루씩 이어지면 늘어나고, 하루 건너뛰면 1부터). */
object Streak {
    fun after(lastDay: Long, count: Int, today: Long): Int = when (today - lastDay) {
        0L -> count.coerceAtLeast(1)
        1L -> count + 1
        else -> 1
    }
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

    /** 낱말 맞추기 판: 왼쪽은 편지 순서, 오른쪽 뜻은 섞어서 (같은 편지는 늘 같은 순서). 최대 [max] 짝. */
    fun matchBoard(letter: Letter, learn: Lang, read: Lang, max: Int = 6): Pair<List<Pair<String, String>>, List<Int>> {
        val pairs = pairs(letter, learn, read).take(max)
        val order = pairs.indices.shuffled(java.util.Random(letter.id.hashCode().toLong()))
        return pairs to (if (order == pairs.indices.toList() && pairs.size > 1) order.reversed() else order)
    }
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
    /**
     * 편지 한 통의 하루 순서: 읽기 다음에 낱말 맞추기 → 따라 읽기(연습) → 답장 쓰기(별자리 잇기) → 그림.
     * 작품 설정에서 끈 것은 건너뛴다. 듣고 쓰기는 단어장 복습에서.
     */
    val inLetter = listOf(ReplyMode.MATCH, ReplyMode.ALOUD, ReplyMode.CONSTELLATION)
}
