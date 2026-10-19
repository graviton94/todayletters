package io.github.graviton94.todayletters.core

/**
 * 기초 낱말 덱 (data/<작품>/basics[_en|_ko].json, 후보는 scripts/basics.py): 그 말에서 가장 많이 쓰는 표제어 300 ([BasicDeck.top]) 가운데
 * 편지 카드 · 표현 · 닻이 다루지 않는 것을 앱이 따로 카드로 ([BasicDeck.items]). 편지와 상관없이 오늘의 복습에 날마다 몇 장씩 섞여 들어온다.
 *
 *  - 뜻 [BasicItem.mean] · 예문 번역 [BasicExample.tr] 은 덱의 말을 뺀 언어 (원어 덱이면 ko · en, 영어 덱이면 ko, 한국어 덱이면 en).
 *  - 예문 [BasicExample.from] 은 그 문장이 나온 편지 ("c1-l3"), null 이면 앱이 쓴 문장.
 *  - [BasicItem.id] 는 sha1("<작품>[_말]/<낱말>") 앞 8자 ([Basics.id], scripts/spec_check.py 가 검사): 카드 열쇠 꼬리 "b<id>" ([CardRef.basic]).
 *  - 소리: 낱말 b_<말>_<id>.m4a · 예문 bx_<말>_<id>.m4a, 작품 폴더 audio/<작품>/B/basics/ (scripts/narrate.py NARRATE_ONLY=new).
 */
data class BasicExample(val text: String, val tr: Map<Lang, String> = emptyMap(), val from: String? = null) {
    /** 앱이 쓴 문장인가 (편지에서 온 문장이 아니다). */
    val written: Boolean get() = from == null
}

/** [k] 는 [BasicDeck.top] 의 표제어: 여러 낱말 표제어 (est-ce que) 면 파일의 "k" (est-ce), 없으면 [w]. 고정 이름은 [w] 로. */
data class BasicItem(val id: String, val w: String, val pos: String = "", val mean: Map<Lang, String> = emptyMap(), val ex: BasicExample? = null,
                     val k: String = w) {
    /** 뜻 ([lang] 이 없으면 영어 · 한국어 가운데 있는 것). */
    fun meaning(lang: Lang): String = mean[lang] ?: mean[Lang.EN] ?: mean[Lang.KO] ?: mean.values.firstOrNull().orEmpty()
}

/** [lang] 은 덱의 말 (파일의 "lang"), [checked] 는 사람이 본 덱인가 (아직 false). */
data class BasicDeck(val lang: Lang?, val checked: Boolean, val top: List<String>, val items: List<BasicItem>) {
    val isEmpty: Boolean get() = items.isEmpty()
    companion object { val EMPTY = BasicDeck(null, false, emptyList(), emptyList()) }
}

object Basics {
    /** 카드 열쇠의 장 · 편지 자리 (편지에 매이지 않는 카드): "<작품>:B:basics:<꼬리>". 소리 폴더도 audio/<작품>/B/basics/. */
    const val CHAPTER = "B"
    const val LETTER = "basics"

    /** 하루 새 기초 낱말: 하루 몫이 [Start.MORE_REVIEW] 장이면 [MORE], 아니면 볼 카드가 넉넉히 남을 때 [MOST] (빠듯하면 [LEAST]). */
    const val LEAST = 2
    const val MOST = 3
    const val MORE = 4

    /** 고정 이름: sha1("<작품>[_말]/<낱말>") 앞 8자. [name] 은 "vincent" · "vincent_en" (scripts/spec_check.py 의 basic_id 와 같다). */
    fun id(name: String, w: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest("$name/$w".toByteArray(Charsets.UTF_8))
            .take(4).joinToString("") { "%02x".format(it) }

    /** 소리 파일 이름 (확장자 없이): 낱말 · 예문. */
    fun wordAudio(lang: Lang, id: String) = "b_${lang.code}_$id"
    fun exampleAudio(lang: Lang, id: String) = "bx_${lang.code}_$id"

    /** basics*.json 글 → 덱. 비었거나 깨졌으면 빈 덱 (파일이 아직 없을 때와 같다). */
    fun parse(text: String?): BasicDeck {
        if (text.isNullOrBlank()) return BasicDeck.EMPTY
        val o = runCatching { Json.parse(text) as? Map<*, *> }.getOrNull() ?: return BasicDeck.EMPTY
        fun langs(x: Any?): Map<Lang, String> = (x as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val l = Lang.entries.firstOrNull { it.code == k } ?: return@mapNotNull null
            (v as? String)?.takeIf { it.isNotBlank() }?.let { l to it }
        }.toMap()
        val items = (o["items"] as? List<*>).orEmpty().mapNotNull { x ->
            val m = x as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            val w = m["w"] as? String ?: return@mapNotNull null
            val ex = (m["ex"] as? Map<*, *>)?.let { e ->
                (e["t"] as? String)?.takeIf { it.isNotBlank() }?.let { t -> BasicExample(t, langs(e.filterKeys { it != "t" && it != "from" }), e["from"] as? String) }
            }
            BasicItem(id, w, m["pos"] as? String ?: "", langs(m["mean"]), ex, (m["k"] as? String)?.takeIf { it.isNotBlank() } ?: w)
        }
        return BasicDeck(Lang.entries.firstOrNull { it.code == o["lang"] }, o["checked"] == true,
            (o["top"] as? List<*>).orEmpty().filterIsInstance<String>(), items)
    }

    /** 하루 새 기초 낱말 수: 하루 몫 [daily] 장 가운데 볼 날이 된 카드가 [due] 장일 때. */
    fun perDay(daily: Int, due: Int): Int = when {
        daily >= Start.MORE_REVIEW -> MORE
        due + MOST <= daily -> MOST
        else -> LEAST
    }

    /** 오늘의 복습 한 판: [cards] 는 오늘 볼 카드, [introduce] 는 그 가운데 아직 카드함에 없는 새 기초 낱말 (판을 시작할 때 들인다). */
    data class Day(val cards: List<Card>, val introduce: List<String>)

    /**
     * 오늘의 복습 한 판 = 새 기초 낱말 + 볼 날이 된 카드 ([Memory.dueToday], 하루 몫에서 새 기초 낱말 자리를 뺀 만큼).
     * [cards] 는 카드함 (이 작품 · 이 말, 오늘 들인 기초 낱말 카드 [todayNew] 포함), [pending] 은 아직 카드가 없는 기초 낱말 열쇠 (덱 차례).
     * 오늘 이미 기초 낱말을 들였으면 더 들이지 않고 그 카드들 (아직 볼 날이면), 아니면 [perDay] 장을 [pending] 앞에서 (상자 0, 오늘).
     * 그 뒤로는 낱말 카드와 같은 간격 ([Memory.after]).
     */
    fun session(cards: Collection<Card>, today: Long, daily: Int, pending: List<String>, todayNew: Set<String>): Day {
        val olds = cards.filter { it.key !in todayNew }
        val due = olds.count { it.due <= today }
        val fresh = if (todayNew.isNotEmpty()) cards.filter { it.key in todayNew && it.due <= today }
            else pending.distinct().take(perDay(daily, due)).map { Card(it, box = 0, due = today) }
        val room = if (todayNew.isNotEmpty()) todayNew.size else fresh.size
        val rest = Memory.dueToday(olds, today, (daily - room).coerceAtLeast(0))
        val all = (rest + fresh).sortedWith(compareBy({ it.box }, { it.due }, { it.key }))
        return Day(all, if (todayNew.isNotEmpty()) emptyList() else fresh.map { it.key })
    }

    /** 표제어 맞추는 꼴 (소문자, 동형어 표시 "#2" 뺌). */
    fun lemma(x: String): String = x.substringBefore('#').trim().lowercase()

    /** 나의 기록의 ‘기초 낱말 N / 300’: [top] 가운데 [known] (아는 표제어, 낱말 카드 · 기초 낱말 카드 가운데 써 보기 이상) 에 든 수. */
    fun knownTop(top: List<String>, known: Set<String>): Int {
        val k = known.map(::lemma).toSet()
        return top.map(::lemma).distinct().count { it in k }
    }
}
