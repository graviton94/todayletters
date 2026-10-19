package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 기초 낱말 덱 (core [Basics]) · 따라 읽기 차례 ([ShadowLines]) · 예열의 ‘오늘 말해 볼 말’. */
class BasicsTest {
    private val fixture = BasicsTest::class.java.getResource("/basics_fixture.json")!!.readText()

    @Test fun loadsDeckAndToleratesMissingFile() {
        val d = Basics.parse(fixture)
        assertEquals(Lang.FR, d.lang)
        assertFalse(d.checked)
        assertEquals(8, d.top.size)
        assertEquals(listOf("où", "aller", "bien", "temps", "voir"), d.items.map { it.w })
        val ou = d.items[0]
        assertEquals("adv", ou.pos)
        assertEquals("어디", ou.meaning(Lang.KO))
        assertEquals("where", ou.meaning(Lang.EN))
        assertEquals("Où es-tu ?", ou.ex?.text)
        assertEquals("너 어디 있어?", ou.ex?.tr?.get(Lang.KO))
        assertTrue(ou.ex!!.written)                       // from: null = 앱이 쓴 문장
        assertEquals("c1-l3", d.items[1].ex?.from)
        assertFalse(d.items[1].ex!!.written)
        assertNull(d.items[2].ex)                        // 예문 없는 줄
        // 고정 이름 = sha1("<작품>[_말]/<낱말>") 앞 8자 (scripts/spec_check.py 와 같다)
        d.items.forEach { assertEquals(Basics.id("vincent", it.w), it.id) }
        assertEquals("b6e7e00c", Basics.id("vincent", "où"))
        // 파일이 아직 없거나 깨졌으면 빈 덱
        assertTrue(Basics.parse(null).isEmpty)
        assertTrue(Basics.parse("").isEmpty)
        assertTrue(Basics.parse("{not json").isEmpty)
        // 뜻이 한 언어뿐인 덱 (영어 덱 = ko 만): 다른 언어를 물어도 있는 뜻
        assertEquals("어디", BasicItem("x", "where", mean = mapOf(Lang.KO to "어디")).meaning(Lang.EN))
    }

    @Test fun cardKeyRoundTrips() {
        val k = CardRef.basic("vincent", "b6e7e00c")
        assertEquals("vincent:B:basics:bb6e7e00c", k)
        val r = CardRef.parse(k)!!
        assertEquals("b6e7e00c", r.basic)
        assertEquals(Basics.CHAPTER, r.chapter)
        assertEquals("", r.anchor)
        assertEquals(-1, r.index)
        val en = CardRef.parse(CardRef.basic("mozart", "0123abcd", "en"))!!
        assertEquals("en", en.deck)
        assertEquals("0123abcd", en.basic)
        // 다른 꼬리와 섞이지 않는다
        assertEquals("", CardRef.parse("vincent:I:c1-l1:a0123abcd")!!.basic)
        assertEquals("", CardRef.parse("vincent:I:c1-l1:3")!!.basic)
        assertNull(CardRef.parse("vincent:B:basics:bxyz"))
        assertEquals("b_fr_b6e7e00c", Basics.wordAudio(Lang.FR, "b6e7e00c"))
        assertEquals("bx_ko_b6e7e00c", Basics.exampleAudio(Lang.KO, "b6e7e00c"))
    }

    private fun keys(n: Int) = (1..n).map { CardRef.basic("vincent", "%08x".format(it)) }

    @Test fun twelveCardDayTakesTwoOrThreeNewBasics() {
        val today = 100L
        val pending = keys(10)
        // 볼 카드가 적으면 셋
        val few = (1..5).map { Card("vincent:I:c1-l1:$it", box = 2, due = today) }
        val a = Basics.session(few, today, Memory.DAILY, pending, emptySet())
        assertEquals(pending.take(3), a.introduce)
        assertEquals(8, a.cards.size)
        // 볼 카드가 하루 몫을 넘으면 둘, 한 판은 12장
        val many = (1..30).map { Card("vincent:I:c1-l1:$it", box = 1, due = today - 1) }
        val b = Basics.session(many, today, Memory.DAILY, pending, emptySet())
        assertEquals(Basics.LEAST, b.introduce.size)
        assertEquals(Memory.DAILY, b.cards.size)
        assertEquals(pending.take(2).toSet(), b.cards.filter { it.box == 0 }.map { it.key }.toSet())
        // 새 기초 낱말은 덱 차례대로, 상자 0 · 오늘
        assertTrue(b.cards.filter { it.key in b.introduce }.all { it.box == 0 && it.due == today })
        assertEquals(3, Basics.perDay(Memory.DAILY, 0))
        assertEquals(3, Basics.perDay(Memory.DAILY, 9))
        assertEquals(2, Basics.perDay(Memory.DAILY, 10))
    }

    @Test fun twentyCardDayTakesFour() {
        val today = 100L
        val many = (1..40).map { Card("vincent:I:c1-l1:$it", box = 1, due = today) }
        val d = Basics.session(many, today, Start.daily(Start.Amount.MORE), keys(10), emptySet())
        assertEquals(Basics.MORE, d.introduce.size)
        assertEquals(Start.MORE_REVIEW, d.cards.size)
        assertEquals(4, Basics.perDay(Start.MORE_REVIEW, 0))
    }

    @Test fun introducedOncePerDayThenUsualSchedule() {
        val today = 100L
        val pending = keys(10)
        val first = Basics.session(emptyList(), today, Memory.DAILY, pending, emptySet())
        // 들인 뒤: 같은 날에는 더 들이지 않고 그 카드들 그대로
        val made = first.introduce.map { Card(it, box = 0, due = today) }
        val again = Basics.session(made, today, Memory.DAILY, pending.drop(made.size), made.map { it.key }.toSet())
        assertTrue(again.introduce.isEmpty())
        assertEquals(made.map { it.key }.toSet(), again.cards.map { it.key }.toSet())
        // 맞혀서 내일로 간 뒤에도 그날은 새로 들이지 않는다
        val answered = made.map { Memory.after(it, true, today) }
        assertTrue(Basics.session(answered, today, Memory.DAILY, pending.drop(made.size), made.map { it.key }.toSet()).cards.isEmpty())
        // 다음 날: 볼 날이 된 기초 낱말 카드는 보통 카드처럼, 새 기초 낱말은 덱 차례 다음 것
        val next = Basics.session(answered, today + 1, Memory.DAILY, pending.drop(made.size), emptySet())
        assertEquals(pending.drop(3).take(3), next.introduce)
        assertTrue(answered.all { a -> next.cards.any { it.key == a.key } })
        // 덱이 없으면 예전 그대로
        val plain = (1..20).map { Card("vincent:I:c1-l1:$it", box = 1, due = today) }
        assertEquals(Memory.dueToday(plain, today), Basics.session(plain, today, Memory.DAILY, emptyList(), emptySet()).cards)
    }

    @Test fun recordCountsKnownTopLemmas() {
        val top = Basics.parse(fixture).top
        // 낱말 카드 (사전 열쇠 "voir#2" 같은 동형어 표시는 떼고) · 기초 낱말 카드, 대소문자 무시, 목록 밖은 세지 않음
        assertEquals(3, Basics.knownTop(top, setOf("Être", "voir#2", "aller", "chat")))
        // 여러 낱말 표제어: top 과는 "k" 로 맞춘다 (없으면 k = w)
        val multi = Basics.parse("""{"lang":"fr","top":["est-ce"],"items":[{"id":"0123abcd","w":"est-ce que","k":"est-ce","pos":"adv"},{"id":"0123abce","w":"où"}]}""")
        assertEquals(listOf("est-ce", "où"), multi.items.map { it.k })
        assertEquals(1, Basics.knownTop(multi.top, setOf(multi.items[0].k)))
        assertEquals(0, Basics.knownTop(top, emptySet()))
        assertEquals(top.size, Basics.knownTop(top, top.toSet()))
    }

    private fun talk() = Talk(
        Tri(mapOf(Lang.KO to "자리")),
        listOf(
            TalkLine(false, Tri(mapOf(Lang.FR to "Bonjour", Lang.EN to "Hello", Lang.KO to "안녕"))),
            TalkLine(true, Tri(mapOf(Lang.FR to "Salut !", Lang.EN to "Hi!", Lang.KO to "안녕!"))),
            TalkLine(false, Tri(mapOf(Lang.FR to "Ça va ?", Lang.EN to "How are you?", Lang.KO to "잘 지내?"))),
            TalkLine(true, Tri(mapOf(Lang.FR to "Oui, très bien.", Lang.EN to "Yes, very well.", Lang.KO to "응, 아주 잘."))),
        ),
        audio = "t",
    )

    @Test fun shadowStartsWithTalkMeLines() {
        val t = talk()
        assertEquals(listOf(1, 3), ShadowLines.talkMe(t, Lang.FR))
        assertTrue(ShadowLines.talkMe(null, Lang.FR).isEmpty())
        // 회화 내 줄 먼저 (소리는 회화 소리 t<줄>_<말>), 그다음 편지 문장 마디 (고른 문장만)
        val order = ShadowLines.order(t, Lang.FR, chunks = listOf(1, 2, 1), only = listOf(1))
        assertEquals(listOf(ShadowSpot(talk = 1), ShadowSpot(talk = 3), ShadowSpot(msg = 1, chunk = 0), ShadowSpot(msg = 1, chunk = 1)), order)
        assertEquals("t2_fr", t.audioName(order[0].talk, Lang.FR))
        assertEquals(listOf("t1:0", "t3:0", "1:0", "1:1"), order.map { it.tag })
        // 회화가 없는 편지 · 회화를 넣지 않는 자리 (봉인까지 가는 전체 따라 읽기) 는 예전 그대로
        val plain = listOf(ShadowSpot(msg = 0, chunk = 0), ShadowSpot(msg = 1, chunk = 0), ShadowSpot(msg = 1, chunk = 1), ShadowSpot(msg = 2, chunk = 0))
        assertEquals(plain, ShadowLines.order(null, Lang.FR, listOf(1, 2, 1), null))
        assertEquals(plain, ShadowLines.order(t, Lang.FR, listOf(1, 2, 1), null, withTalk = false))
        // 그 말의 글이 빈 줄은 뺀다
        val partial = t.copy(lines = t.lines.mapIndexed { i, l -> if (i == 1) l.copy(text = Tri(mapOf(Lang.EN to "Hi!"))) else l })
        assertEquals(listOf(3), ShadowLines.talkMe(partial, Lang.FR))
    }

    @Test fun warmUpAddsTodaysTalkLines() {
        val words = listOf(5 to false, 9 to false, 12 to false)
        val got = WarmUp.pick(words, emptyList(), listOf(0 to 3), talk = listOf(1, 3, 5))
        assertEquals(listOf(WarmUp.Item(WarmUp.Kind.WORD, 0), WarmUp.Item(WarmUp.Kind.WORD, 1), WarmUp.Item(WarmUp.Kind.WORD, 2),
            WarmUp.Item(WarmUp.Kind.ANCHOR, 0), WarmUp.Item(WarmUp.Kind.TALK, 1), WarmUp.Item(WarmUp.Kind.TALK, 3)), got)
        // 회화가 없으면 예전 그대로
        assertEquals(got.take(4), WarmUp.pick(words, emptyList(), listOf(0 to 3)))
        // 낱말 한 장뿐이어도 회화 줄이 있으면 예열
        assertEquals(2, WarmUp.pick(listOf(1 to false), emptyList(), emptyList(), talk = listOf(1)).size)
    }
}
