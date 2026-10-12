package io.github.graviton94.todayletters.data

import android.content.Context

/**
 * 결제 (구독 · 작품 사기) 는 모두 여기 한 곳에서. 아직 Play 결제를 붙이지 않아서 [ready] 가 false 이고,
 * 상점 › 구독 · 시리즈 화면의 단추는 ‘곧 열려요’로 보인다.
 *
 * Play Billing 을 붙일 때는 이 파일만 고친다: BillingClient 를 연결해 [ready] 를 true 로, [buy] 에서 launchBillingFlow,
 * 산 것 (구독 · 작품) 은 [owned] 로. 화면 (PurchaseScreen) 은 [Product] 와 이 세 가지만 본다.
 */
object Billing {
    /** 파는 것: 작품 하나 (무료인 1장 뒤 전부) · 구독 (모든 작품). [sku] 는 Play Console 의 상품 id. */
    sealed interface Product {
        val sku: String
        data class Series(val id: String) : Product {
            override val sku: String get() = "series_$id"
        }
        data object Subscription : Product {
            override val sku: String get() = "sub_all"
        }
    }

    /** 결제를 쓸 수 있나 (아직은 아니다). */
    val ready: Boolean get() = false

    /** 사기 시작. 결제가 열리기 전에는 아무것도 하지 않고 false. */
    @Suppress("UNUSED_PARAMETER")
    fun buy(ctx: Context, product: Product): Boolean = false

    /** 산 것 (상품 id). 아직은 없다: 지금은 모든 장이 열려 있다. */
    @Suppress("UNUSED_PARAMETER")
    fun owned(ctx: Context): Set<String> = emptySet()
}
