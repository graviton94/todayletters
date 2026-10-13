package io.github.graviton94.todayletters.core

/**
 * 판매 정책 (docs/billing.md). 결제 연결은 앱의 data/Billing.kt, 여기는 규칙만.
 *
 *  - 무료: 모든 시리즈의 프롤로그 + I · II장 (series.json 의 장마다 "free"). 잠금은 III장부터.
 *  - 시리즈 소장 (한 번 결제, 계속 내 것): 편지 수에 맞춘 값 [PriceTier].
 *  - 구독 [Products.SUB]: 월 · 연 기본 요금제, 모든 시리즈 (새 시리즈 포함). 무료 체험 없음.
 *  - 우표(화폐)는 돈으로 팔지 않는다. 구독이 끝나도 메달 · 레벨 · 그림 · 진도는 남고, 소장한 시리즈는 계속 열린다.
 */
object Products {
    /** 구독 상품 하나 (모든 시리즈). 기본 요금제 둘: [MONTHLY] · [YEARLY]. */
    const val SUB = "sub_all"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
    /** 시리즈 소장 상품: "series_<작품 id>" (series_vincent · series_mozart). */
    const val SERIES_PREFIX = "series_"

    fun series(id: String) = SERIES_PREFIX + id
    fun seriesOf(product: String): String? = product.takeIf { it.startsWith(SERIES_PREFIX) }?.removePrefix(SERIES_PREFIX)?.takeIf { it.isNotEmpty() }
}

/** 시리즈 소장 값 (원): 편지 40통 미만 · 40–70통 · 70통 넘거나 기능이 더 붙은 시리즈. series.json 의 "tier" (1 · 2 · 3). */
enum class PriceTier(val won: Int) {
    T1(1_900), T2(2_900), T3(3_900);

    companion object {
        fun of(n: Int): PriceTier = entries.getOrElse(n - 1) { T2 }
        /** 편지 수 (프롤로그 · 맺음 편지 포함) 로 정하는 값. [extra] 면 (학습량 · 대화 같은 기능) 맨 위. */
        fun forVolume(letters: Int, extra: Boolean = false): PriceTier = when {
            extra || letters > 70 -> T3
            letters >= 40 -> T2
            else -> T1
        }
    }
}

/** 고를 수 있는 것: 연 구독 · 월 구독 · 이 시리즈 소장. [fallbackWon] 은 Play 값을 모를 때 보여 줄 계획한 값. */
sealed interface Plan {
    val product: String
    /** 구독의 기본 요금제 id (소장이면 null). */
    val basePlan: String?
    val fallbackWon: Int
    /** 값 · 상품 정보를 찾는 열쇠 ("sub_all:yearly" · "series_vincent"). */
    val key: String get() = basePlan?.let { "$product:$it" } ?: product

    data object Yearly : Plan {
        override val product = Products.SUB
        override val basePlan = Products.YEARLY
        override val fallbackWon = 12_900
    }
    data object Monthly : Plan {
        override val product = Products.SUB
        override val basePlan = Products.MONTHLY
        override val fallbackWon = 1_900
    }
    data class Series(val id: String, val tier: PriceTier = PriceTier.T2) : Plan {
        override val product get() = Products.series(id)
        override val basePlan: String? get() = null
        override val fallbackWon get() = tier.won
    }
}

/** 값 하나. [micros] 는 Play 의 백만분의 1 단위, [formatted] 는 화면에 그대로. [fromPlay] 가 아니면 계획한 값 (Play 상품이 아직 없을 때). */
data class Price(val micros: Long, val currency: String, val formatted: String, val fromPlay: Boolean) {
    companion object {
        /** 계획한 값 (원): "₩12,900". */
        fun planned(won: Int) = Price(won * 1_000_000L, "KRW", won(won.toLong()), false)
        fun won(n: Long) = "₩" + "%,d".format(java.util.Locale.ROOT, n)
    }
}

/**
 * 지금 쓸 수 있는 것: 구독 중인가 · 소장한 시리즈들. 기기 안에 저장해 두어 결제 서버에 닿지 않아도 (비행기 모드) 그대로.
 * 구독이 끝나면 [subscribed] 만 꺼진다 (소장 · 진도는 그대로). [basePlan] 은 구독 중인 기본 요금제 (이 기기에서 결제할 때 고른 것), 모르면 null.
 */
data class Entitlement(val subscribed: Boolean = false, val owned: Set<String> = emptySet(), val basePlan: String? = null) {
    fun owns(series: String) = subscribed || series in owned
    /** 지금 쓰고 있는 구독 요금제가 이것인가 ([Products.MONTHLY] · [Products.YEARLY]). 어느 요금제인지 모르면 (다른 기기에서 샀거나 Play 에서 바꿈) 둘 다 false. */
    fun onPlan(plan: Plan) = subscribed && plan.basePlan != null && plan.basePlan == basePlan
    val free: Boolean get() = !subscribed && owned.isEmpty()

    companion object {
        /** 결제가 끝난 (대기 중이 아닌) 상품 id 들 → 이용권. */
        fun of(products: Collection<String>) = Entitlement(Products.SUB in products, products.mapNotNull(Products::seriesOf).toSet())
    }
}

object Access {
    /** 이 장을 읽을 수 있나: 무료 장이거나 (프롤로그 · I · II) 시리즈를 소장 · 구독. */
    fun open(chapter: Chapter, ent: Entitlement) = chapter.free || ent.owns(chapter.series)

    /** 잠긴 장의 처음과 끝 (1부터 센 장 번호). 잠긴 장이 없으면 null. 예: 여덟 장 중 I · II 가 무료면 3..8. */
    fun lockedRange(chapters: List<Chapter>): IntRange? {
        val idx = chapters.indices.filter { !chapters[it].free }
        return if (idx.isEmpty()) null else (idx.first() + 1)..(idx.last() + 1)
    }

    /** 연 구독이 월 구독 열두 번보다 몇 % 싼가 (반올림). 월 ₩1,900 · 연 ₩12,900 이면 43. */
    fun yearlyDiscount(monthlyMicros: Long, yearlyMicros: Long): Int {
        if (monthlyMicros <= 0 || yearlyMicros <= 0) return 0
        return Math.round((1.0 - yearlyMicros.toDouble() / (monthlyMicros * 12.0)) * 100).toInt().coerceAtLeast(0)
    }

    /** 연 구독을 달로 나눈 값 (백만분의 1 단위). ₩12,900 → ₩1,075. */
    fun perMonth(yearlyMicros: Long): Long = yearlyMicros / 12
}
