package io.github.graviton94.todayletters.core

/**
 * 이동 위치 공유 (시안 moments ④, 2026-10-25 확정): 편지를 쓴 곳 ([Letter.place]) 이 읽는 차례의 앞 편지와 달라지면
 * 그 편지 앞 (after -1) 에 위치 공유를 하나 놓는다 (“볼프강이 위치를 공유했어요 · 로마”, 앞 곳에서 오는 길 한 줄). 데이터 손질 없이 규칙 하나.
 * 이미 위치 순간이 있는 편지에는 놓지 않는다. 작품의 series.json "travel": {"auto": true, "names": {편지의 place → 세 언어 이름}}.
 *
 * 원정지 위치가 이미 많은 작품 (나폴레옹, "travel": {"dedupe": true}) 은 놓지 않고, 같은 곳에서 연달아 쓴 편지의 위치 순간을 뺀다
 * (앞 편지의 위치 순간과 같은 장소 id).
 */
object Travel {
    /** [letters] 는 읽는 차례 (서문 · 장 · 맺음). [names] 는 place 글자 → 이름 (없으면 그 글자 그대로). */
    fun apply(letters: List<Letter>, auto: Boolean, dedupe: Boolean, names: Map<String, Tri> = emptyMap()): List<Letter> {
        if (!auto && !dedupe) return letters
        fun name(p: String) = names[p] ?: Tri(Lang.entries.associateWith { p })
        var prev: String? = null
        var prevSpot: String? = null
        return letters.map { l ->
            val locs = l.moments.filterIsInstance<Moment.Location>()
            val out = when {
                auto && locs.isEmpty() && prev != null && l.place.isNotBlank() && prev != l.place ->
                    l.copy(moments = listOf(Moment.Location(-1, "", name(l.place), "", from = name(prev!!))) + l.moments)
                dedupe && prevSpot != null && locs.any { it.place == prevSpot } ->
                    l.copy(moments = l.moments.filterNot { it is Moment.Location && it.place == prevSpot })
                else -> l
            }
            if (l.place.isNotBlank()) prev = l.place
            locs.lastOrNull()?.let { prevSpot = it.place }
            out
        }
    }
}

/**
 * 금액의 한국어 읽기 (작게, 원문 아래): “100 francs” → “100프랑”. 숫자 (천 단위 점 · 쉼표 · 띄어쓰기) + 화폐 낱말 하나일 때만,
 * 아니면 null (원문만). 소수 · 여러 금액 · 화폐가 아닌 것 (23 cafés) 은 읽지 않는다.
 */
object Amounts {
    private val units = listOf(
        Regex("(?i)francs?|fr\\.?") to "프랑",
        Regex("(?i)fl\\.?|florins?|gulden") to "플로린",
        Regex("(?i)reales|real|rs\\.?") to "레알",
        Regex("(?i)duc\\.?|ducaten|dukaten|ducats?") to "두카트",
        Regex("(?i)louisdors?|louis d'or") to "루이도르",
    )
    private val shape = Regex("^(\\d+(?:[ .\\u00A0\\u202F]\\d{3})*)\\s*(\\S.*)$")

    fun ko(amount: String): String? {
        val m = shape.find(amount.trim()) ?: return null
        val digits = m.groupValues[1].filter { it.isDigit() }
        val unit = units.firstOrNull { it.first.matches(m.groupValues[2].trim()) }?.second ?: return null
        val n = digits.toLongOrNull() ?: return null
        return "%,d".format(java.util.Locale.ROOT, n) + unit
    }
}
