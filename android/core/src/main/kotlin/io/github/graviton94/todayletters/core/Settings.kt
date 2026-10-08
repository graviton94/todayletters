package io.github.graviton94.todayletters.core

/** 테마: 사용자 설정을 따른다 (기본: 시스템). 편지의 시간대로 바뀌지 않는다. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 편지에 쓰이는 언어. 모든 편지는 원어 · 영어 · 한국어 세 줄을 늘 갖고 있다. */
enum class Lang(val code: String) { KO("ko"), EN("en"), FR("fr"), NL("nl"), DE("de"), JA("ja") }

/**
 * 앱 글자(설정 · 단추 · 도움말 · 큐레이터 노트 · 낱말 뜻풀이) 언어. 출시는 영어 · 한국어 둘.
 * 폰이 한국어면 한국어, 그 밖에는 영어. 편지 줄 언어([Lang])와는 따로 간다.
 */
enum class UiLang(val code: String) {
    EN("en"), KO("ko");
    companion object {
        fun fromSystem(code: String) = if (code.lowercase().startsWith("ko")) KO else EN
    }
}

/** 타이핑 효과의 빠르기. 바로 보기는 움직임 줄이기와 같다. */
enum class TypingPace { CALM, QUICK, INSTANT }

/** 답장(학습) 방식. */
enum class ReplyMode { MATCH, CONSTELLATION, ALOUD, DICTATION }

/** 작품(시리즈). 원어는 작품마다 다르다: 고흐 아를 시기는 프랑스어, 모차르트는 독일어. */
data class Series(val id: String, val original: Lang)

/**
 * 앱 설정: 모든 작품에 함께 쓰인다. 설정 화면(⚙)에서 바꾼다.
 * [ui] 는 앱 글자 언어 (영어 · 한국어), [read] 는 말풍선의 작은 줄 언어 (원어 · 영어 · 한국어 어느 것이든).
 */
data class AppSettings(
    val ui: UiLang = UiLang.EN,
    val read: Lang = Lang.KO,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val largeText: Boolean = false,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val pace: TypingPace = TypingPace.CALM,
)

/**
 * 작품 설정: 작품마다 따로. 작품 표지의 ⚙ · 대화방 정보 서랍 · 처음 소개에서 바꾼다.
 * [learn] 은 말풍선의 큰 줄 언어.
 */
data class SeriesSettings(
    val learn: Lang,
    val showRead: Boolean = true,
    val modes: Set<ReplyMode> = setOf(ReplyMode.MATCH, ReplyMode.CONSTELLATION, ReplyMode.ALOUD),
    val lettersPerDay: Int = 1,
    val arrivalNotice: Boolean = true,
)

object Langs {
    /** 처음 켰을 때: 앱 글자는 폰 언어로, 작은 줄은 앱 글자 언어와 같게 시작한다. */
    fun firstRun(systemCode: String): AppSettings {
        val ui = UiLang.fromSystem(systemCode)
        return AppSettings(ui = ui, read = if (ui == UiLang.KO) Lang.KO else Lang.EN)
    }

    /** 이 작품에서 고를 수 있는 배우는 언어: 원어 · 영어 · 한국어 중 읽는 언어를 뺀 것. */
    fun learnChoices(series: Series, read: Lang): List<Lang> =
        listOf(series.original, Lang.EN, Lang.KO).distinct().filter { it != read }

    /** 처음 고를 때 권하는 배우는 언어: 원어 (원어가 읽는 언어면 그다음). */
    fun defaultLearn(series: Series, read: Lang): Lang = learnChoices(series, read).first()

    /** 작품을 처음 열 때의 설정. */
    fun start(series: Series, app: AppSettings) = SeriesSettings(learn = defaultLearn(series, app.read))

    /**
     * 읽는 언어를 바꾼 뒤: 배우는 언어와 같아진 작품은 권하는 언어로 옮긴다.
     * 바뀐 작품 id 를 함께 돌려줘서 화면이 한 줄 알림을 띄울 수 있게 한다.
     */
    fun afterReadChange(app: AppSettings, all: Map<Series, SeriesSettings>): Pair<Map<Series, SeriesSettings>, List<String>> {
        val moved = mutableListOf<String>()
        val next = all.mapValues { (series, s) ->
            if (s.learn in learnChoices(series, app.read)) s
            else s.copy(learn = defaultLearn(series, app.read)).also { moved += series.id }
        }
        return next to moved
    }
}

/**
 * 대화방에서 실제로 쓰는 값. 대화방의 빠른 토글(번역 줄 가리기 · 바로 보기)은 작품 설정에 저장되므로
 * 여기서는 앱 설정과 작품 설정만 합친다. 움직임 줄이기가 켜진 폰은 늘 바로 보기.
 */
data class RoomView(val learn: Lang, val read: Lang, val showRead: Boolean, val pace: TypingPace) {
    companion object {
        fun of(app: AppSettings, series: SeriesSettings, reducedMotion: Boolean) = RoomView(
            learn = series.learn, read = app.read, showRead = series.showRead,
            pace = if (reducedMotion) TypingPace.INSTANT else app.pace,
        )
    }
}
