package io.github.graviton94.todayletters.core

/**
 * 앱의 화면 계층 (docs/ia.md 의 트리를 코드로).
 *
 *   이름표(탭) ─ 오늘 · 서재 · 복습 · 갤러리   (코드 이름은 Inbox · Library · Words · Gallery 그대로)
 *   서재 → 작품 → 챕터 → 편지(대화방) → 답장(플레이어블) / 편지 완료
 *   편지함 → 편지(대화방)  (지름길: 뒤로 가면 편지함)
 *
 * [up] 은 "뒤로"가 갈 곳. 화면 상태가 어디서 왔든 계층은 하나라서, 알림으로 바로 들어와도 뒤로 가기가 길을 잃지 않는다.
 */
sealed interface Route {
    /** 이름표 맨 위 화면. 여기서만 ⚙ 설정이 보인다. */
    sealed interface Tab : Route
    data object Inbox : Tab
    data object Library : Tab
    data object Words : Tab
    data object Gallery : Tab

    data class Series(val series: String) : Route
    data class SeriesSettings(val series: String) : Route
    data class Purchase(val series: String) : Route
    data class Chapter(val series: String, val chapter: Int) : Route

    /** 편지 = 대화방. [from] 은 들어온 이름표 (편지함 지름길이면 Inbox). */
    data class Letter(val series: String, val chapter: Int, val letter: Int, val from: Tab = Library) : Route
    data class RoomInfo(val room: Letter) : Route
    data class Play(val room: Letter, val mode: ReplyMode) : Route
    data class Done(val room: Letter) : Route

    data class Artwork(val series: String, val plate: Int, val from: Route) : Route
    data class Review(val series: String?) : Route
    /** 복습 한 판. [kind] 가 null 이면 오늘의 복습 (섞어서). */
    data class Session(val kind: ReviewKind? = null) : Route
    /** 모은 낱말 찾기 · 내 구절. */
    data object WordList : Route
    data object Quotes : Route
    /** 오늘의 손님 (미니게임). */
    data object Visitor : Route
    /** 정기 소포 보내기 · 답례. */
    data class Parcel(val series: String) : Route
    data object Settings : Route
}

object Nav {
    fun up(r: Route): Route? = when (r) {
        Route.Inbox -> null
        Route.Library, Route.Words, Route.Gallery -> Route.Inbox
        is Route.Series -> Route.Library
        is Route.SeriesSettings -> Route.Series(r.series)
        is Route.Purchase -> Route.Series(r.series)
        is Route.Chapter -> Route.Series(r.series)
        is Route.Letter -> if (r.from == Route.Inbox) Route.Inbox else Route.Chapter(r.series, r.chapter)
        is Route.RoomInfo -> r.room
        is Route.Play -> r.room
        is Route.Done -> r.room
        is Route.Artwork -> r.from
        is Route.Review -> Route.Words
        is Route.Session, Route.WordList, Route.Quotes -> Route.Words
        Route.Visitor -> Route.Inbox
        is Route.Parcel -> Route.Gallery
        Route.Settings -> Route.Inbox
    }

    /** 맨 위(편지함)까지 거슬러 오르는 길. 알림으로 바로 들어왔을 때 뒤로 가기 스택을 만든다. */
    fun trail(r: Route): List<Route> = generateSequence(r) { up(it) }.toList().reversed()

    /** 이 화면에서 ⚙ 설정을 열 수 있는가: 이름표 맨 위 화면에서만. 작품 설정은 작품 표지 · 대화방 정보에서. */
    fun showsAppSettings(r: Route) = r is Route.Tab

    /** 이 화면에서 작품 설정으로 갈 수 있는가. */
    fun showsSeriesSettings(r: Route) = r is Route.Series || r is Route.RoomInfo

    /** 답장 중 뒤로 가기는 바로 나가지 않고 한 번 묻는다 ("답장은 나중에 쓸까요?"). */
    fun asksBeforeLeaving(r: Route) = r is Route.Play || r is Route.Session
}
