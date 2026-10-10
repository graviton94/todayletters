package io.github.graviton94.todayletters.core

/**
 * 앱의 화면 계층 (docs/ia.md 의 트리를 코드로).
 *
 *   서재 (앱을 열면 여기: 전체 기록 + 시리즈 카드)
 *   ├ 내 기록 · 전체 (이어 읽기 · 시리즈별 화폐 · 합친 갤러리 · 전체 이정표)
 *   └ 시리즈로 들어가는 전환 → 시리즈 안 이름표: 오늘 · 복습 · 갤러리 · 이정표
 *        오늘 → 목차 → 챕터 → 편지(대화방) → 답장 / 편지 완료 / 따라 읽기 / 봉인
 *        오늘 → 편지(대화방)  (지름길: 뒤로 가면 오늘)
 *
 * 시리즈 안 화면은 지금 들어간 시리즈 하나를 본다 (AppState.current). 이름표 코드 이름은 Inbox · Words · Gallery · Milestones.
 * [up] 은 "뒤로"가 갈 곳. 화면 상태가 어디서 왔든 계층은 하나라서, 알림으로 바로 들어와도 뒤로 가기가 길을 잃지 않는다.
 */
sealed interface Route {
    /** 시리즈 안 이름표 맨 위 화면. */
    sealed interface Tab : Route
    data object Inbox : Tab
    data object Words : Tab
    data object Gallery : Tab
    data object Milestones : Tab

    /** 서재: 앱의 맨 위. */
    data object Library : Route
    /** 서재 › 내 기록 · 전체. */
    data object Overall : Route
    /** 서재에서 시리즈를 고른 뒤의 전환 (대표 그림 · “OO의 세상으로 떠납니다” · 눌러서 들어가기). */
    data class Enter(val series: String) : Route

    data class Series(val series: String) : Route
    data class SeriesSettings(val series: String) : Route
    data class Purchase(val series: String) : Route
    data class Chapter(val series: String, val chapter: Int) : Route

    /** 편지 = 대화방. [from] 은 들어온 곳 (오늘에서 바로 들어왔으면 Inbox). */
    data class Letter(val series: String, val chapter: Int, val letter: Int, val from: Tab? = null) : Route
    data class RoomInfo(val room: Letter) : Route
    data class Play(val room: Letter, val mode: ReplyMode) : Route
    data class Done(val room: Letter) : Route
    /** 따라 읽기: 편지 문장을 낭독 마디째 듣고 따라 말한다 (내 낭독이 남는다). */
    data class Shadow(val room: Letter, val only: Int? = null) : Route
    /** 완독 봉인: 혼자 다 읽고 다 따라 읽은 편지. */
    data class Seal(val room: Letter) : Route

    data class Artwork(val series: String, val plate: Int, val from: Route) : Route
    data class Review(val series: String?) : Route
    /** 복습 한 판. [kind] 가 null 이면 오늘의 복습 (섞어서). */
    /** 복습 한 판. [only] 가 있으면 그 낱말만 (틀린 것 다시 · 낱말 카드에서 말해 보기), 연습이라 기억 칸은 움직이지 않는다. */
    /** [quote] 가 있으면 그 구절(내 구절 열쇠)로 빈칸 연습 (v21 2단계 F5). */
    data class Session(val kind: ReviewKind? = null, val only: List<String>? = null, val quote: String? = null) : Route
    /** 모은 낱말 찾기 · 내 구절. */
    data object WordList : Route
    data object Quotes : Route
    /** 이달의 전시. */
    data object Exhibition : Route
    /** 일요일 낭독회: 이번 주 내 낭독. */
    data object Recital : Route
    /** 이번 주 돌아보기 (v21 3단계 E1): 읽은 날 · 편지 · 내 것 · 따라 읽기 점수 · 모은 작품. */
    data object Week : Route
    data object Settings : Route
}

object Nav {
    fun up(r: Route): Route? = when (r) {
        Route.Library -> null
        Route.Overall, is Route.Enter, Route.Settings -> Route.Library
        Route.Inbox, Route.Words, Route.Gallery, Route.Milestones -> Route.Library
        is Route.Series -> Route.Inbox
        is Route.SeriesSettings -> Route.Series(r.series)
        is Route.Purchase -> Route.Series(r.series)
        is Route.Chapter -> Route.Series(r.series)
        is Route.Letter -> r.from ?: Route.Chapter(r.series, r.chapter)
        is Route.RoomInfo -> r.room
        is Route.Play -> r.room
        is Route.Done -> r.room
        is Route.Shadow -> r.room
        is Route.Seal -> r.room
        is Route.Artwork -> r.from
        is Route.Review -> Route.Words
        is Route.Session, Route.WordList, Route.Quotes -> Route.Words
        Route.Exhibition -> Route.Gallery
        Route.Recital -> Route.Inbox
        Route.Week -> Route.Inbox
    }

    /** 맨 위(서재)까지 거슬러 오르는 길. 알림으로 바로 들어왔을 때 뒤로 가기 스택을 만든다. */
    fun trail(r: Route): List<Route> = generateSequence(r) { up(it) }.toList().reversed()

    /** 이 화면에서 ⚙ 설정을 열 수 있는가: 서재 · 시리즈 안 이름표 맨 위 화면에서만. */
    fun showsAppSettings(r: Route) = r is Route.Tab || r == Route.Library

    /** 이 화면에서 작품 설정으로 갈 수 있는가. */
    fun showsSeriesSettings(r: Route) = r is Route.Series || r is Route.RoomInfo

    /** 답장 중 뒤로 가기는 바로 나가지 않고 한 번 묻는다 ("답장은 나중에 쓸까요?"). */
    fun asksBeforeLeaving(r: Route) = r is Route.Play || r is Route.Session

    /** 시리즈 안 화면인가 (그 시리즈의 색으로 칠한다). 서재 · 내 기록 · 설정은 공통 색. */
    fun inSeries(r: Route) = r != Route.Library && r != Route.Overall && r != Route.Settings
}
