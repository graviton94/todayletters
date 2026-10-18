package io.github.graviton94.todayletters.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.graviton94.todayletters.BuildConfig
import io.github.graviton94.todayletters.core.Entitlement
import io.github.graviton94.todayletters.core.Plan
import io.github.graviton94.todayletters.core.Price
import io.github.graviton94.todayletters.core.Products
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 결제 (Google Play Billing) 는 모두 여기 한 곳에서. 상품 id 는 core 의 [Products] (docs/billing.md 에 Play Console 등록 목록).
 *
 *  - [connect]: Play 에 붙어 상품 값 ([offers]) 과 산 것 ([refresh]) 을 읽는다. 앱으로 돌아올 때마다 (MainActivity.onResume).
 *  - [buy]: 결제 창. 끝나면 [Purchase] 를 확인(acknowledge) 하고 이용권에 더한다.
 *  - [restore]: 구매 복원 (queryPurchasesAsync 로 다시 읽기).
 *  - [entitlement]: 이용권. 기기 안에 저장해 두어 (Store.entitlement) 결제 서버에 닿지 않아도 그대로 연다.
 *
 * Play Console 에 상품이 아직 없거나 Play 에 닿지 않으면 [available] 이 false: 화면은 계획한 값 ([Plan.fallbackWon]) 과
 * ‘곧 열려요’ 단추를 보여 주고, 아무것도 깨지지 않는다.
 */
class Billing(ctx: Context, private val store: Store) {
    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** 상품 하나의 Play 정보: 값 · 결제 창에 넘길 상품 정보와 (구독이면) 요금제 토큰. */
    class Offer(val price: Price, internal val details: ProductDetails, internal val token: String?)

    private val _entitlement = MutableStateFlow(store.entitlement)
    /** Play 에서 확인한 이용권 (마지막으로 확인한 것, 기기 안에 저장). */
    val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _offers = MutableStateFlow<Map<String, Offer>>(emptyMap())
    /** Play 에서 찾은 상품 ([Plan.key] → 값). Play Console 에 없으면 비어 있다. */
    val offers: StateFlow<Map<String, Offer>> = _offers.asStateFlow()

    /** 이용권이나 값이 바뀌었을 때 (화면을 다시 그리게). 늘 main 스레드에서 부른다. */
    var onChange: () -> Unit = {}

    /**
     * 결제 창에서 방금 산 것이 끝났을 때 (PURCHASED · 확인까지): 고마워요 한 장. [Thanks.series] 가 null 이면 구독.
     * 구매 복원 · 앱을 켤 때 다시 읽은 예전 구매에는 부르지 않는다 (대기 중이던 결제가 끝난 것만 예외).
     */
    class Thanks(val series: String?)
    var onThanks: (Thanks) -> Unit = {}
    /** 결제가 대기 중 (PENDING): 확인되면 바로 열린다는 한 줄. */
    var onPending: () -> Unit = {}

    /** 실제로 쓰는 이용권: debug 빌드에서 ‘모두 열기’ (Store.debugUnlockAll) 를 켰으면 모든 시리즈. */
    val effective: Entitlement
        get() = if (BuildConfig.DEBUG && store.debugUnlockAll) _entitlement.value.copy(subscribed = true) else _entitlement.value

    /** 이 상품을 지금 살 수 있나 (Play 에 붙었고 상품을 찾았다). */
    fun available(plan: Plan): Boolean = client?.isReady == true && plan.key in _offers.value

    /** 보여 줄 값: Play 값, 모르면 계획한 값 (₩). */
    fun price(plan: Plan): Price = _offers.value[plan.key]?.price ?: Price.planned(plan.fallbackWon)

    private var client: BillingClient? = null
    private var connecting = false
    private var series: List<String> = emptyList()
    /** 결제 창에서 고른 구독 요금제 (결제가 끝나면 이용권에 적는다). Play 의 Purchase 에는 요금제 id 가 없어서. */
    private var buyingBase: String? = null

    /** Play 에 붙는다 (이미 붙었으면 산 것만 다시 읽는다). [seriesIds] 는 소장 상품을 찾을 작품들. */
    fun connect(seriesIds: List<String>) {
        series = seriesIds
        runCatching {
            val c = client ?: BillingClient.newBuilder(app)
                .setListener { r, list -> main.post { onPurchases(r, list) } }
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build().also { client = it }
            if (c.isReady) { if (_offers.value.isEmpty()) queryProducts(c); refresh(); return }
            if (connecting) return
            connecting = true
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) {
                    main.post {
                        connecting = false
                        if (r.responseCode == BillingClient.BillingResponseCode.OK) { queryProducts(c); refresh() }
                    }
                }
                override fun onBillingServiceDisconnected() { main.post { connecting = false } }
            })
        }.onFailure { connecting = false }
    }

    /** 앱이 끝날 때 (MainActivity.onDestroy). */
    fun end() {
        runCatching { client?.endConnection() }
        client = null; connecting = false
    }

    private fun queryProducts(c: BillingClient) {
        fun product(id: String, type: String) = QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()
        c.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product(Products.SUB, BillingClient.ProductType.SUBS))).build(),
        ) { r, res -> if (r.responseCode == BillingClient.BillingResponseCode.OK) main.post { addOffers(res.productDetailsList) } }
        if (series.isEmpty()) return
        c.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(series.map { product(Products.series(it), BillingClient.ProductType.INAPP) }).build(),
        ) { r, res -> if (r.responseCode == BillingClient.BillingResponseCode.OK) main.post { addOffers(res.productDetailsList) } }
    }

    private fun addOffers(list: List<ProductDetails>) {
        val found = mutableMapOf<String, Offer>()
        list.forEach { pd ->
            if (pd.productType == BillingClient.ProductType.SUBS) {
                // 기본 요금제만 (무료 체험 · 할인 제안은 쓰지 않는다): offerId 가 없는 것
                pd.subscriptionOfferDetails.orEmpty().filter { it.offerId == null }.forEach { o ->
                    val ph = o.pricingPhases.pricingPhaseList.lastOrNull() ?: return@forEach
                    found["${pd.productId}:${o.basePlanId}"] = Offer(Price(ph.priceAmountMicros, ph.priceCurrencyCode, ph.formattedPrice, true), pd, o.offerToken)
                }
            } else {
                @Suppress("DEPRECATION")
                pd.oneTimePurchaseOfferDetails?.let { o ->
                    found[pd.productId] = Offer(Price(o.priceAmountMicros, o.priceCurrencyCode, o.formattedPrice, true), pd, null)
                }
            }
        }
        if (found.isEmpty()) return
        _offers.value = _offers.value + found
        onChange()
    }

    /** 결제 창을 연다. 열지 못하면 (상품이 없거나 Play 에 닿지 않으면) false. */
    fun buy(activity: Activity, plan: Plan): Boolean {
        val c = client?.takeIf { it.isReady } ?: return false
        val o = _offers.value[plan.key] ?: return false
        val pp = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(o.details)
            .apply { o.token?.let { setOfferToken(it) } }.build()
        buyingBase = plan.basePlan
        return runCatching {
            c.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(pp)).build()).responseCode ==
                BillingClient.BillingResponseCode.OK
        }.getOrDefault(false).also { if (!it) buyingBase = null }
    }

    private fun onPurchases(r: BillingResult, list: List<Purchase>?) {
        when (r.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (list != null) {
                grant(list, full = false, chosen = buyingBase)
                list.filter { it.purchaseState == Purchase.PurchaseState.PENDING }.forEach { store.markPendingBuy(it.purchaseToken); onPending() }
                list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { thank(it) }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
        }
        buyingBase = null
    }

    /** 산 것을 Play 에서 다시 읽는다 (구독 · 소장 둘 다 읽혀야 이용권을 바꾼다: 하나라도 못 읽으면 저장한 것 그대로). */
    fun refresh(done: (Entitlement?) -> Unit = {}) {
        val c = client?.takeIf { it.isReady } ?: return done(null)
        fun q(type: String) = QueryPurchasesParams.newBuilder().setProductType(type).build()
        c.queryPurchasesAsync(q(BillingClient.ProductType.SUBS)) { r1, subs ->
            if (r1.responseCode != BillingClient.BillingResponseCode.OK) { main.post { done(null) }; return@queryPurchasesAsync }
            c.queryPurchasesAsync(q(BillingClient.ProductType.INAPP)) { r2, inapp ->
                main.post {
                    if (r2.responseCode != BillingClient.BillingResponseCode.OK) done(null)
                    else {
                        grant(subs + inapp, full = true)
                        // 대기 중이던 결제가 그사이 끝났으면 그때 고마워요 (그 밖의 예전 구매에는 묻지 않는다)
                        (subs + inapp).filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && store.pendingBuy(it.purchaseToken) }.forEach { thank(it) }
                        done(_entitlement.value)
                    }
                }
            }
        }
    }

    /** 구매 복원: Play 에 붙어 있지 않으면 먼저 붙는다. [done] 은 확인한 이용권 (Play 에 닿지 않으면 null). */
    fun restore(done: (Entitlement?) -> Unit) {
        if (client?.isReady == true) refresh(done) else { connect(series); done(null) }
    }

    /**
     * 결제가 끝난 것만 (대기 중은 빼고) 이용권으로. 아직 확인하지 않은 것은 확인한다 (3일 안에 하지 않으면 Play 가 환불).
     * [full] 이면 이것이 산 것 전부 (구독이 끝났으면 꺼진다), 아니면 지금 이용권에 더한다.
     */
    private fun grant(purchases: List<Purchase>, full: Boolean, chosen: String? = null) {
        val done = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        done.filter { !it.isAcknowledged }.forEach { p ->
            runCatching { client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { } }
        }
        // 구독 요금제: 방금 결제 창에서 고른 것이면 그것 (구매 토큰과 함께 저장), 아니면 저장한 토큰과 같을 때만 저장한 요금제, 다르면 모름
        val sub = done.firstOrNull { Products.SUB in it.products }
        val base = when {
            sub == null -> null
            chosen != null -> chosen.also { store.subToken = sub.purchaseToken }
            sub.purchaseToken == store.subToken -> _entitlement.value.basePlan
            else -> null
        }
        val got = Entitlement.of(done.flatMap { it.products }).copy(basePlan = base)
        val next = if (full) got else Entitlement(_entitlement.value.subscribed || got.subscribed, _entitlement.value.owned + got.owned,
            if (got.subscribed) base else _entitlement.value.basePlan)
        if (next == _entitlement.value) return
        _entitlement.value = next
        store.entitlement = next
        onChange()
    }

    /** 고마워요: 확인(acknowledge) 이 끝난 뒤에 (이미 확인된 것이면 바로). 같은 구매로는 한 번만. */
    private fun thank(p: Purchase) {
        store.clearPendingBuy(p.purchaseToken)
        if (store.thanked(p.purchaseToken)) return
        val t = Thanks(if (Products.SUB in p.products) null else p.products.firstNotNullOfOrNull { Products.seriesOf(it) })
        if (t.series == null && Products.SUB !in p.products) return
        fun fire() { if (!store.thanked(p.purchaseToken)) { store.markThanked(p.purchaseToken); onThanks(t) } }
        if (p.isAcknowledged) { fire(); return }
        runCatching {
            client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r ->
                if (r.responseCode == BillingClient.BillingResponseCode.OK) main.post { fire() }
            }
        }
    }

    companion object {
        /** Play 스토어의 구독 관리 화면 (구독 중일 때 ‘Play 스토어에서 구독 관리’). */
        fun manageUrl(pkg: String) = "https://play.google.com/store/account/subscriptions?sku=${Products.SUB}&package=$pkg"

        /** 화면의 Context 에서 결제 창을 띄울 Activity 를 찾는다. */
        fun activityOf(ctx: Context): Activity? =
            generateSequence(ctx) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    }
}
