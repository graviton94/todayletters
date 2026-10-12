package io.github.graviton94.todayletters.core

/**
 * 메달 진열장 (data/medals.json · data/medals.md). 가족 여섯 (이어 읽기 · 읽은 편지 · 익힌 낱말 · 따라 읽기 별 · 감상한 그림 · 완주한 시리즈),
 * 메달마다 문턱 하나. 그림은 고대 그리스 · 로마 동전과 붉은 그림 잔의 톤도 (메트로폴리탄 미술관 Open Access, CC0).
 * 메달은 모든 작품을 합쳐서 센다 (시리즈 메달만 작품마다 하나). 예전 이정표는 [Medals.migrate] 로 겹치는 메달이 된다.
 */
enum class MedalTier { BRONZE, SILVER, GOLD }

/** 메달 그림의 원본: 소장처 · 소장품 번호 · 이름 · 시기 · 주소 · 라이선스 (작가가 알려졌으면). */
data class MedalSource(val museum: String, val objectId: Long, val title: String, val date: String, val url: String, val license: String, val artist: String = "")

/** 메달 가족: [metric] 은 [Stats] 의 이름 ("bestStreak" · "lettersDone" · "wordsKnown" · "readAlongStars" · "walksDone" · "seriesCompleted:<id>"). */
data class MedalFamily(val id: String, val title: Tri, val metric: String, val unit: Tri)

/** 메달 하나. [series] 는 시리즈 메달이면 그 작품 id. */
data class MedalDef(
    val id: String, val family: String, val tier: MedalTier, val threshold: Int, val title: Tri, val image: String, val source: MedalSource,
    val series: String? = null,
)

/** medals.json 한 벌: 가족 · 메달 · 새 시리즈용 예비 톤도 · 레벨 표. */
data class MedalBook(
    val families: List<MedalFamily>, val medals: List<MedalDef>,
    val spareImage: String = "", val spare: MedalSource? = null,
    val levels: LevelBook = LevelBook(),
) {
    fun family(id: String) = families.firstOrNull { it.id == id }
    fun inFamily(id: String) = medals.filter { it.family == id }.sortedBy { it.threshold }
    fun medal(id: String) = medals.firstOrNull { it.id == id }
}

object Medals {
    @Suppress("UNCHECKED_CAST")
    private fun Any?.obj() = this as? Map<String, Any?> ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    private fun Any?.list() = this as? List<Any?> ?: emptyList()
    private fun Map<String, Any?>.str(k: String) = this[k] as? String ?: ""
    private fun Map<String, Any?>.int(k: String) = (this[k] as? Double)?.toInt() ?: 0
    private fun tri(v: Any?): Tri = v.obj().let { m -> Tri(listOf(Lang.KO, Lang.EN).associateWith { m.str(it.code) }) }
    private fun source(v: Any?): MedalSource = v.obj().let { m ->
        MedalSource(m.str("museum"), (m["objectID"] as? Double)?.toLong() ?: 0, m.str("title"), m.str("date"), m.str("url"), m.str("license"), m.str("artist"))
    }

    /** medals.json 을 읽는다. */
    fun parse(text: String): MedalBook {
        val root = Json.parse(text).obj()
        val families = root["families"].list().map { f -> f.obj().let { MedalFamily(it.str("id"), tri(it["title"]), it.str("metric"), tri(it["unit"])) } }
        val medals = root["medals"].list().map { x -> x.obj().let { m ->
            val fam = m.str("family")
            MedalDef(m.str("id"), fam, MedalTier.valueOf(m.str("tier").uppercase()), m.int("threshold"), tri(m["title"]), m.str("image"), source(m["source"]),
                series = if (fam == SERIES) m.str("id").removePrefix("series_") else null)
        } }
        val spare = root["seriesSpare"].obj()
        return MedalBook(families, medals, spare.str("image"), spare["source"]?.let(::source), LevelBook.parse(root["levels"].obj()))
    }

    const val SERIES = "series"

    /** 이 메달의 숫자: 시리즈 메달은 완주했으면 1. */
    fun value(m: MedalDef, s: Stats): Int = when (m.family) {
        "streak" -> s.bestStreak
        "letters" -> s.lettersDone
        "words" -> s.wordsKnown
        "stars" -> s.readAlongStars
        "walks" -> s.walksDone
        SERIES -> if (m.series in s.seriesCompleted) 1 else 0
        else -> 0
    }

    fun reached(m: MedalDef, s: Stats) = value(m, s) >= m.threshold
    fun progress(m: MedalDef, s: Stats): Float = if (m.threshold <= 0) 1f else (value(m, s).toFloat() / m.threshold).coerceIn(0f, 1f)

    /**
     * 지금 숫자로 받을 수 있는데 아직 안 받은 메달 (가족마다 문턱 차례대로). [series] 가 있으면 그 작품들의 시리즈 메달만 (앱에 있는 작품).
     */
    fun newly(book: MedalBook, s: Stats, have: Set<String>, series: Set<String>? = null): List<MedalDef> =
        book.medals.filter { it.id !in have && (it.series == null || series == null || it.series in series) && reached(it, s) }
            .sortedWith(compareBy({ book.families.indexOfFirst { f -> f.id == it.family } }, { it.threshold }))

    /** 가족에서 다음에 받을 메달 (아직 안 받은 것 가운데 문턱이 가장 낮은 것). 다 받았으면 null. */
    fun next(book: MedalBook, family: String, have: Set<String>): MedalDef? = book.inFamily(family).firstOrNull { it.id !in have }

    /**
     * 예전 이정표 열쇠 → 메달 id (겹치는 것만). 시리즈 이정표는 "<작품>:<id>", 전체 이정표는 "<id>".
     * 따라 읽기 50문장 · 혼자 읽기 % · 갤러리 자료 · 첫 장 · 두 번째 시리즈 · 두 언어는 메달 가족에 없어서 넘기지 않는다
     * (그동안 받은 화폐 · 명작은 그대로 남는다).
     */
    fun migrate(old: Set<String>): Set<String> = old.mapNotNull { k ->
        when (k.substringAfter(":")) {
            "alone_1" -> "letters_1"          // 첫 봉인 = 첫 편지를 다 읽음
            "streak_7" -> "streak_7"
            "words_50" -> "words_25"          // 혼자 읽는 낱말 50 ≥ 25
            "all_letters_30" -> "letters_28"
            "all_streak_30" -> "streak_30"
            "all_streak_100" -> "streak_100"
            else -> null
        }
    }.toSet()

    /** 메달 셋째마다 갤러리에 유화 한 점 확정 (예전 이정표의 약속을 이어 간다). [count] 는 이 메달을 받고 난 뒤의 메달 수. */
    fun grantsPainting(count: Int) = count > 0 && count % 3 == 0
}
