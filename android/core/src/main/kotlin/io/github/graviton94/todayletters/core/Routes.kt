package io.github.graviton94.todayletters.core

/**
 * 앱의 화면 계층 (docs/ia.md 의 트리를 코드로).
 *
 *   서재 (앱을 열면 여기: 칭호 줄 + 시리즈 카드)
 *   ├ 나의 기록 (칭호 · 레벨 · 이어 읽기 · 편지 · 낱말 · 시리즈 진도 · 메달 여섯 → 메달 전체 · 이번 주 · 일요일 낭독회 · 이용권 → 구매). 모든 작품 합산, 하나뿐
 *   └ 시리즈로 들어가는 짧은 전환 → (처음이면 배울 말 고르기 → 편지 I) → 시리즈 안 이름표: 오늘 · 복습 · 갤러리 · 상점
 *        오늘 = 오늘의 여정 (편지 → 복습 → 완료) → 편지(대화방) → 걸음 / 편지 완료 / 따라 읽기 / 봉인
 *        오늘 → 목차 → (프롤로그) · 챕터 → 편지
 *        복습 → 오늘의 복습 · 자유 연습 · 다시 하기 (건너뛴 걸음) · 내 구절 · 모은 낱말
 *        갤러리 → 편지의 그림 (전부) · 모은 작품 (전부) · 이달의 전시,  상점 → 모든 편지 열기 (구매)
 *
 * 시리즈 안 화면은 지금 들어간 시리즈 하나를 본다 (AppState.current). 이름표 코드 이름은 Inbox · Words · Gallery · Shop.
 * [up] 은 "뒤로"가 갈 곳. 화면 상태가 어디서 왔든 계층은 하나라서, 알림으로 바로 들어와도 뒤로 가기가 길을 잃지 않는다.
 */
sealed interface Route {
    /** 시리즈 안 이름표 맨 위 화면. */
    sealed interface Tab : Route
    data object Inbox : Tab
    data object Words : Tab
    data object Gallery : Tab
    /** 상점: 화폐로 사는 것 (갤러리 칸 · 액자 · 미리 열기 · 쉼표 · 카드 테두리) · 맨 아래 금테 배너 ‘모든 편지 열기’ (구매). */
    data object Shop : Tab

    /** 서재: 앱의 맨 위. */
    data object Library : Route
    /** 서재 › 나의 기록 (모든 작품 합산): 칭호 · 레벨 · 이어 읽기 · 편지 · 낱말 · 메달. 작품 머리의 칭호 줄을 눌러도 여기. */
    data object Record : Route
    /** 나의 기록 › 메달 전체 (진열장). */
    data object Medals : Route
    /**
     * 구매 (모든 편지 열기): 연 구독 · 월 구독 · 지금 시리즈 (AppState.current) 소장. 결제는 data/Billing, 정책은 [Access] · docs/billing.md.
     * 들어오는 곳: 상점 맨 아래 배너 · 잠긴 장 (III장부터) · 나의 기록의 이용권 줄. 뒤로는 들어온 곳 (계층의 부모는 상점).
     */
    data object Purchase : Route
    /** 서재에서 시리즈를 고른 뒤의 전환 (대표 그림 · “OO의 세상으로 떠납니다” · 눌러서 들어가기). */
    data class Enter(val series: String) : Route

    data class Series(val series: String) : Route
    data class SeriesSettings(val series: String) : Route
    data class Chapter(val series: String, val chapter: Int) : Route

    /** 편지 = 대화방. [from] 은 들어온 곳 (오늘에서 바로 들어왔으면 Inbox). */
    data class Letter(val series: String, val chapter: Int, val letter: Int, val from: Tab? = null) : Route {
        companion object {
            /** 맺음 편지 (장 "E") 의 장 번호: 답장 걸음 (Play) 만 이 번호로 들어간다 (대화방 · 목차에는 없다). */
            const val EPILOGUE = 99
        }
        val epilogue get() = chapter == EPILOGUE
    }
    data class RoomInfo(val room: Letter) : Route
    /** 편지 전 예열 ([WarmUp]): 새 편지를 처음 열 때 그 편지의 말 3~4개를 듣고 따라 말한 뒤 [room] 으로. */
    data class WarmUp(val room: Letter) : Route
    /** 이번 장의 소리 ([Sounds]): I~VIII 장의 첫 편지를 처음 열 때 한 번, 예열 · 편지 앞. 마치거나 건너뛰면 예열 또는 [room] 으로. */
    data class SoundCheck(val room: Letter) : Route
    /** 어제 말 떠올리기 ([Recall]): 오늘 화면 맨 위 카드에서. */
    data class RecallQuiz(val series: String) : Route
    data class Play(val room: Letter, val mode: ReplyMode) : Route
    data class Done(val room: Letter) : Route
    /** 따라 읽기: 편지 문장을 낭독 마디째 듣고 따라 말한다 (내 낭독이 남는다). */
    data class Shadow(val room: Letter, val only: Int? = null) : Route
    /** 완독 봉인: 혼자 다 읽고 다 따라 읽은 편지. */
    data class Seal(val room: Letter) : Route

    data class Artwork(val series: String, val plate: Int, val from: Route) : Route
    /**
     * 복습 한 판. 아무것도 없으면 오늘의 복습 (오늘의 여정 II: 지금 작품의 카드만, 뜻 → 빈칸 → 듣고 쓰기).
     * [free] 면 자유 연습: 연 장들의 카드 가운데 무작위로, 꼴도 무작위로 한 판 ([Memory.freeSet], [seed] 로 섞는다). 기록도 된다 (Memory.practiced).
     * [only] 가 있으면 그 낱말만 (틀린 것 다시 · 낱말 카드에서 말해 보기), [kind] 가 있으면 그 꼴로만.
     * [quote] 가 있으면 그 구절(내 구절 열쇠)로 빈칸 연습 (v21 2단계 F5).
     */
    data class Session(val kind: ReviewKind? = null, val only: List<String>? = null, val quote: String? = null, val free: Boolean = false, val seed: Long = 0) : Route
    /** 모은 낱말 찾기 · 내 구절. */
    data object WordList : Route
    data object Quotes : Route
    /** 이달의 전시. */
    data object Exhibition : Route
    /** 일요일 낭독회: 이번 주 내 낭독. */
    data object Recital : Route
    /** 이번 주 돌아보기 (v21 3단계 E1): 읽은 날 · 편지 · 내 것 · 따라 읽기 점수 · 모은 작품. */
    data object Week : Route
    /** 프롤로그: 첫 편지 앞의 도슨트 (아를 이전 35년). [page] 0..4. 목차에서 언제든 (처음에 저절로 띄우지 않는다). */
    data class Prologue(val series: String, val page: Int = 0) : Route
    /** 작품에 처음 들어갈 때 한 번: 무엇을 배울지만 고른다. 고르면 바로 첫 편지. */
    data class Welcome(val series: String) : Route
    /** 맺음말 · 아웃트로: 마지막 편지 뒤 (그가 지니고 있던 편지 + 생애를 맺는 글). */
    data class Outro(val series: String) : Route
    /** 편지의 그림 전부: 편지마다 받은 그림을 장마다 날짜 순으로 (완결이면 표지와 메달). */
    data class Catalogue(val series: String) : Route
    /** 모은 작품 전부 (예전 ‘나의 도록’과 합침): 가진 소장품과 다 모은 전시의 도록 표지. */
    data class Collection(val series: String) : Route
}

object Nav {
    fun up(r: Route): Route? = when (r) {
        Route.Library -> null
        Route.Record, is Route.Enter, is Route.Welcome -> Route.Library
        Route.Medals -> Route.Record
        Route.Inbox, Route.Words, Route.Gallery, Route.Shop -> Route.Library
        Route.Purchase -> Route.Shop
        is Route.Series -> Route.Inbox
        is Route.SeriesSettings -> Route.Series(r.series)
        is Route.Chapter -> Route.Series(r.series)
        is Route.Letter -> r.from ?: Route.Chapter(r.series, r.chapter)
        is Route.RoomInfo -> r.room
        is Route.WarmUp -> r.room.from ?: Route.Chapter(r.room.series, r.room.chapter)
        is Route.SoundCheck -> r.room.from ?: Route.Chapter(r.room.series, r.room.chapter)
        is Route.RecallQuiz -> Route.Inbox
        is Route.Play -> r.room
        is Route.Done -> r.room
        is Route.Shadow -> r.room
        is Route.Seal -> r.room
        is Route.Artwork -> r.from
        is Route.Session, Route.WordList, Route.Quotes -> Route.Words
        Route.Exhibition -> Route.Gallery
        Route.Recital -> Route.Record
        Route.Week -> Route.Record
        is Route.Prologue -> Route.Series(r.series)
        is Route.Outro -> Route.Inbox
        is Route.Catalogue, is Route.Collection -> Route.Gallery
    }

    /** 맨 위(서재)까지 거슬러 오르는 길. 알림으로 바로 들어왔을 때 뒤로 가기 스택을 만든다. */
    fun trail(r: Route): List<Route> = generateSequence(r) { up(it) }.toList().reversed()

    /** 이 화면에서 ⚙ 설정을 열 수 있는가: 서재 · 시리즈 안 이름표 맨 위 화면에서만. */
    fun showsAppSettings(r: Route) = r is Route.Tab || r == Route.Library

    /** 이 화면에서 작품 설정으로 갈 수 있는가. */
    fun showsSeriesSettings(r: Route) = r is Route.Series || r is Route.RoomInfo

    /** 답장 중 뒤로 가기는 바로 나가지 않고 한 번 묻는다 ("답장은 나중에 쓸까요?"). */
    fun asksBeforeLeaving(r: Route) = r is Route.Play || r is Route.Session

    /** 시리즈 안 화면인가 (그 시리즈의 색으로 칠한다). 서재 · 나의 기록 · 메달 · 설정은 공통 색. */
    fun inSeries(r: Route) = r != Route.Library && r != Route.Record && r != Route.Medals
}
