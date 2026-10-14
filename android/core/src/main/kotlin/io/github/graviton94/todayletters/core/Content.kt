package io.github.graviton94.todayletters.core

/** 세 언어 글: 원어 · 영어 · 한국어. 모든 편지 데이터는 세 줄을 다 갖는다. */
data class Tri(val lines: Map<Lang, String>) {
    operator fun get(lang: Lang): String = lines[lang] ?: lines.values.first()
}

data class Message(val text: Tri)
/**
 * 낱말. [icon] 이 있으면 ‘편지 속 물건’으로 수집 앨범에 들어간다.
 * [modern] 은 오늘의 철자 (de_modern · fr_modern): 편지는 판의 철자(daß)를 그대로 보여 주고, 쓰기 채점은 이 철자(dass)로 한다.
 * [ipaEn] · [romKo] 는 영어 발음 · 한국어 로마자 (카드에 보이는 것은 다음 단계).
 */
data class Word(val text: Tri, val pos: String = "", val ipa: String = "", val icon: String = "",
                val modern: String = "", val ipaEn: String = "", val romKo: String = "",
                /** 낱말 낭독 파일 이름 앞부분 (편지 폴더의 "w3" → w3_<언어>.m4a). 비면 소리 없음. */
                val audio: String = "",
                /** 어휘 사전 열쇠 (lexicon id||lemma) · 등급 (A1…C2) · 편지에 찍힌 꼴들. 사전에서 온 낱말만. */
                val lex: String = "", val level: String = "", val forms: List<String> = emptyList(),
                /** 표현 카드면 쓰임 설명 (ko · en). */
                val note: Tri? = null,
                /** 표현 카드면 그 표현이 나온 메시지 번호 (0부터, 모르면 -1). */
                val msg: Int = -1) {
    /** 표현 카드 (여러 낱말의 관용 표현). */
    val phrase get() = note != null
    /** 쓰기 · 말하기 채점에 쓰는 원어 철자: 오늘의 철자가 있으면 그것, 없으면 편지의 철자. */
    fun answer(learn: Lang, original: Lang): String = if (learn == original && modern.isNotEmpty()) modern else text[learn]
}
/** 그림 속 산책의 한 자리: 그림 위의 위치(0~1), 이름, 큐레이터 설명, 낱말, 그 요소가 나오는 편지 문장 번호. */
data class Spot(val x: Float, val y: Float, val title: Tri, val note: Tri, val word: Word?, val quote: Int? = null, val voiced: Boolean = true)

/**
 * 그림 한 점. [collection] 은 소장처와 라이선스 (“… · CC0”), [source] 는 소장처의 작품 페이지 (출처 화면).
 * [kind] 는 그림의 갈래 (mozart · score · era · place · person; 비면 편지 속 그 그림): era 는 ‘시대의 그림’ 꼬리표, score 는 ‘악보 속 산책’.
 * [credit] 은 작가 · 사진가, [license] 는 이미지 라이선스, [why] 는 이 편지 곁에 둔 까닭.
 */
data class Plate(val title: Tri, val date: String, val collection: String, val image: String = "", val spots: List<Spot> = emptyList(), val source: String = "",
                 val kind: String = "", val credit: String = "", val license: String = "", val why: Tri? = null) {
    val era get() = kind == "era"
    val score get() = kind == "score"
}

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
    /**
     * 편지 속 그 곡: 편지가 이름을 댄 곡의 60초 발췌 (assets/audio/<작품>/music/[audio]).
     * [audio] 가 비면 녹음이 없는 것이라 대화방에 아무것도 놓지 않는다. [performer] · [license] · [source] 는 출처 줄.
     */
    data class Music(override val after: Int, val piece: Tri, val audio: String = "", val performer: String = "", val license: String = "", val source: String = "") : Moment {
        val playable get() = audio.isNotEmpty()
        fun path(series: String) = "audio/$series/music/$audio"
    }
}

/** 작품의 장소 (손으로 그린 지도 위 0~1 위치, 실제 위도 · 경도). */
data class Place(val id: String, val x: Float, val y: Float, val name: Tri, val note: Tri, val lat: Double = 0.0, val lng: Double = 0.0)

/**
 * 오늘의 문법 (장마다 셋, 챕터 파일의 "grammar"): 배우는 말마다 그 언어 사다리의 이름 · 한 줄 설명 ([GrammarLine], 앱 글자 언어 ko · en).
 * id 는 "III.0" 처럼 장 · 순번. 답장 턴이 이 id 를 가리킨다 ([ReplyTurn.grammar]).
 */
data class Grammar(val id: String, val lines: Map<Lang, GrammarLine>) {
    /** 이 배우는 말의 칸 (없으면 null: 그 말의 줄을 아직 쓰지 않은 것). */
    fun line(learn: Lang): GrammarLine? = lines[learn]
}
data class GrammarLine(val name: Tri, val hint: Tri)

/**
 * 답장 한 턴 (편지의 "replies"): 편지 문장 하나([quote], 메시지 번호)에 대답하는 한 줄. 각 언어 줄은 그 언어 사다리의 [grammar] 를 쓴다.
 * [focus] 는 언어마다 빈칸으로 비울 1~2 덩어리 (그 줄에 그대로 있는 글자). 한국어 줄은 해요체이고,
 * [koLow] · [koHigh] 는 같은 뜻의 반말 · 높임 (한국어를 배울 때 말투 노트 · 말투 바꿔 보기 카드).
 * [wrong] 은 언어마다 덩어리마다 틀린 보기 2개 ([focus] 와 같은 차례, 없으면 사전 꼴로 미끼를 만든다: [Ladder.authored]).
 * 예전 형식 ("reply" 하나 + "quote") 은 문법 없는 한 턴으로 읽는다.
 */
data class ReplyTurn(
    val quote: Int,
    val grammar: String,
    val text: Tri,
    val koLow: String = "",
    val koHigh: String = "",
    val focus: Map<Lang, List<String>> = emptyMap(),
    val wrong: Map<Lang, List<List<String>>> = emptyMap(),
) {
    /** 말투 노트를 보여 줄 수 있나 (반말 · 높임 둘 다 있을 때). */
    val styled get() = koLow.isNotBlank() && koHigh.isNotBlank()
}

/**
 * 편지 한 통 = 대화방 한 번.
 * [replies] 는 답장 턴들 (받는 사람이 쓰는 말, 차례로). 없으면 편지의 짧은 문장 하나로 대신한다 ([Exercises.turns]).
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
    val replies: List<ReplyTurn> = emptyList(),
    val moments: List<Moment> = emptyList(),
    /** 대화방 위의 상태 메시지 (그 무렵 빈센트의 한마디). */
    val status: Tri? = null,
    /** 이 편지를 받는 사람 (아버지 · 누나 · 콘스탄체…). 없으면 작품의 받는 사람. */
    val to: Tri? = null,
    /** 오늘의 표현 (daily.json phrases): 낱말 맞추기 뒤의 표현 카드 · 복습 카드. */
    val phrases: List<Word> = emptyList(),
    /** 챕터 파일의 낱말 그대로 (낭독 w<i> 가 붙은 것). [words] 가 daily.json 의 낱말로 바뀌어도 소리를 찾을 때 쓴다. */
    val source: List<Word> = emptyList(),
) {
    /** 녹음이 있는 ‘편지 속 그 곡’. */
    val music: Moment.Music? get() = moments.firstOrNull { it is Moment.Music && it.playable } as Moment.Music?
}

/** [grammar] 는 이 장의 오늘의 문법 셋 (없으면 빈 목록). */
data class Chapter(val series: String, val id: String, val title: Tri, val free: Boolean, val letters: List<Letter>, val grammar: List<Grammar> = emptyList())

/** 문법 id 로 찾기: 여러 장의 문법 셋에서 처음 나오는 것 (앞 장 문법 복습 · 맺음 편지가 VIII 장 문법을 가리킬 때도). */
fun List<Chapter>.grammar(id: String): Grammar? = if (id.isEmpty()) null else firstNotNullOfOrNull { c -> c.grammar.firstOrNull { it.id == id } }

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

    /**
     * 하루에 새로 오는 편지 수: 작품마다 1통 (고르는 설정 없음. 더 받기는 상점의 ‘오늘 편지 더 받기’).
     * 처음 앱을 연 날([firstDay])에만 튜토리얼 작품([tutorial], 빈센트)은 2통 (튜토리얼 편지 I 과 그다음 한 통).
     */
    fun perDay(series: String, tutorial: String, firstDay: Long, day: Long): Int =
        if (series == tutorial && firstDay >= 0 && day == firstDay) 2 else 1

    /**
     * 알림 · 위젯이 말할 작품: 마지막으로 들어간 작품([last])에 기다리는 편지가 있으면 그것,
     * 없으면 기다리는 편지가 있는 첫 작품, 그것도 없으면 마지막 작품 (없으면 첫 작품). [ids] 는 서재 순서.
     */
    fun pick(last: String?, ids: List<String>, waiting: (String) -> Int): String? {
        val home = last?.takeIf { it in ids } ?: ids.firstOrNull() ?: return null
        if (waiting(home) > 0) return home
        return ids.firstOrNull { waiting(it) > 0 } ?: home
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

    /**
     * 답장 턴들: 저자가 쓴 [Letter.replies] 가 있으면 그것 (차례로), 없으면 낱말 수가 4~10개인 가장 짧은 메시지 하나 (인용 없음 · 문법 없음).
     * [learn] 은 그 짧은 메시지를 고를 때만 쓴다.
     */
    fun turns(letter: Letter, learn: Lang): List<ReplyTurn> {
        if (letter.replies.isNotEmpty()) return letter.replies
        if (letter.messages.isEmpty()) return emptyList()
        val pick = letter.messages.map { it.text }
            .filter { it[learn].split(Regex("\\s+")).size in 4..10 }
            .minByOrNull { it[learn].length } ?: letter.messages.last().text
        return listOf(ReplyTurn(-1, "", pick))
    }

    /** 답장 문장 (턴 [turn], 0부터): (배우는 말 줄, 세 언어). 턴이 없으면 마지막 턴. */
    fun replyFor(letter: Letter, learn: Lang, turn: Int = 0): Pair<String, Tri> {
        val ts = turns(letter, learn)
        val t = ts.getOrNull(turn) ?: ts.lastOrNull() ?: return "" to Tri(mapOf(learn to ""))
        return t.text[learn] to t.text
    }

    /** 낱말 맞추기: (배우는 언어, 읽는 언어) 짝. */
    fun pairs(letter: Letter, learn: Lang, read: Lang) = letter.words.map { it.text[learn] to it.text[read] }

    /** 낱말 맞추기 판: 왼쪽은 편지 순서, 오른쪽 뜻은 섞어서 (같은 편지는 늘 같은 순서). 최대 [max] 짝. */
    fun matchBoard(letter: Letter, learn: Lang, read: Lang, max: Int = 8): Pair<List<Pair<String, String>>, List<Int>> {
        val pairs = pairs(letter, learn, read).take(max)
        val order = pairs.indices.shuffled(java.util.Random(letter.id.hashCode().toLong()))
        return pairs to (if (order == pairs.indices.toList() && pairs.size > 1) order.reversed() else order)
    }
}

/**
 * 편지 한 통의 진도: 몇 번째 메시지까지 받았는지, 어떤 걸음을 마쳤는지 · 건너뛰었는지. 메시지 단위로 저장해 중간에 나가도 이어진다.
 * 건너뛴 걸음([skipped])도 편지를 마치는 데는 한 것으로 치지만, 따로 남겨 두어 편지 목록 · 복습에서 ‘다시 하기’로 언제든 한다.
 */
data class LetterProgress(val shown: Int = 0, val replied: Set<ReplyMode> = emptySet(), val done: Boolean = false, val skipped: Set<ReplyMode> = emptySet()) {
    fun reveal(total: Int) = copy(shown = (shown + 1).coerceAtMost(total))
    fun allShown(total: Int) = shown >= total
    /** 걸음을 실제로 했다 (건너뛰었던 것이면 건너뜀 표시를 지운다). */
    fun reply(mode: ReplyMode) = copy(replied = replied + mode, skipped = skipped - mode)
    /** 걸음을 건너뛰었다 (이미 한 걸음은 그대로). */
    fun skip(mode: ReplyMode) = if (mode in replied) this else copy(skipped = skipped + mode)
    /** 이 편지에서 하는 걸음([modes], [Plays.forLetter]) 이 모두 했거나 건너뛰었으면 편지 완료. */
    fun complete(modes: Collection<ReplyMode>) = copy(done = modes.filter { it in Plays.inLetter }.all { it in replied || it in skipped })
    /** 아직 다시 하지 않은 건너뛴 걸음 (편지 순서대로). */
    val toRedo: List<ReplyMode> get() = Plays.inLetter.filter { it in skipped && it !in replied }
}

object Plays {
    /**
     * 편지 한 통의 하루 순서: 읽기 다음에 낱말 맞추기 → 따라 읽기(연습) → 답장 쓰기(별자리 잇기) → 그림.
     * 작품 설정에서 끈 것은 건너뛴다. 듣고 쓰기는 단어장 복습에서.
     */
    val inLetter = listOf(ReplyMode.MATCH, ReplyMode.ALOUD, ReplyMode.CONSTELLATION)

    /** 이 편지에서 할 걸음: 작품 설정에서 켠 것 가운데, 낭독이 없는 편지([narrated] = false)는 따라 읽기를 뺀다 (따라 할 소리가 없다). */
    fun forLetter(modes: Set<ReplyMode>, narrated: Boolean): List<ReplyMode> =
        inLetter.filter { it in modes && (narrated || it != ReplyMode.ALOUD) }
}

/**
 * 시리즈 키트: 공통 화면(엔진)에 이 작품만의 이름 · 색 · 소장품을 채우는 데이터. 시리즈가 늘어도 코드는 그대로.
 * 틀은 모든 시리즈가 같고, 시리즈가 바꾸는 것은 강조 색과 종이 · 잉크 빛, 초상, 편지마다의 자료뿐이다.
 * 색은 0xAARRGGBB.
 */
data class Kit(
    /** 시리즈의 빛깔: 밝게 · 어둡게. 없으면 공통 색. */
    val light: Tone? = null,
    val dark: Tone? = null,
    /** 서재 카드와 들어가는 전환의 대표 그림 (assets 안 경로). */
    val hero: String = "",
    /** 머리글 지명 (ARLES · 1888). */
    val place: String = "",
    /** “OO의 세상으로 떠납니다”. */
    val world: Tri = Tri(mapOf(Lang.EN to "")),
    /** 화폐 이름 (우표 · 두카트). */
    val currency: Tri = Tri(mapOf(Lang.EN to "")),
    /** 요일 머리글자 일곱 (월요일부터, 그 시리즈 언어로: LMMJVSD). */
    val weekdays: String = "MTWTFSS",
    /** 갤러리 소장품 (공공 영역 · 소장처가 자유 이용을 밝힌 것만). */
    val collection: List<Piece> = emptyList(),
    /** 이달의 전시 (달마다 돌아가며). */
    val exhibitions: List<Exhibition> = emptyList(),
    /** 서재 카드의 작품 주제 꼬리표 (빈센트: 미술관 · 모차르트: 오페라). 앱 글자 언어로. 없으면 빈 글자. */
    val tag: Tri = Tri(mapOf(Lang.EN to "")),
    /** 대화방 정보의 한 줄 소개 (빈센트: 35세 · 화가 · …). 앱 글자 언어로. 없으면 빈 글자 (F21). */
    val about: Tri = Tri(mapOf(Lang.EN to "")),
    /** 앱 글자 언어로 쓴 온이름 (빈센트 반 고흐). 없으면 빈 글자 → 원래 이름. */
    val fullName: Tri = Tri(mapOf(Lang.EN to "")),
)

/** 시리즈 빛깔 한 벌. */
data class Tone(val paper: Long, val ink: Long, val soft: Long, val faint: Long, val accent: Long)
