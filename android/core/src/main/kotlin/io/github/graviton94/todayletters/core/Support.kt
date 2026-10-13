package io.github.graviton94.todayletters.core

/**
 * 의견 보내기 (설정 › 의견 보내기, 지난번에 앱이 멈췄을 때): 메일 앱에 채워 넣을 제목 · 본문.
 * 외부 오류 수집 서비스는 쓰지 않는다. 사용자가 메일 앱에서 직접 보낼 때만 밖으로 나간다.
 */
object Feedback {
    const val TO = "ruahn49@gmail.com"
    /** 메일 주소(mailto)에 실을 수 있게 오류 기록은 이만큼까지만 (앞쪽: 예외와 맨 위 줄들이 가장 쓸모 있다). */
    const val CRASH_MAX = 3500

    enum class Kind { BUG, IDEA, OTHER }

    /** 기기 정보 한 줄에 들어갈 것. */
    data class Device(val android: String, val model: String, val app: String, val learn: String, val crashes: Int)

    /** 제목: "[언어 미술관] 오류 · 1.21". */
    fun subject(appName: String, kindLabel: String, version: String) = "[$appName] $kindLabel · $version"

    /**
     * 본문: 사용자가 쓴 글, (함께 보내기를 골랐으면) 기기 정보 한 줄과 최근 오류 기록.
     * [deviceLine] 은 화면의 미리 보기와 같은 글자. 오류 기록은 [trimCrash] 로 줄인다.
     */
    fun body(text: String, deviceLine: String?, crash: String?, crashHeader: String): String = buildString {
        append(text.trim())
        if (deviceLine != null) {
            append("\n\n— — —\n").append(deviceLine)
            val c = crash?.let(::trimCrash)
            if (!c.isNullOrBlank()) append("\n\n").append(crashHeader).append('\n').append(c)
        }
    }

    /** 오류 기록을 [max] 글자 안으로: 넘치면 앞부분만 남기고 끝에 "…" (줄 단위로 자른다). */
    fun trimCrash(log: String, max: Int = CRASH_MAX): String {
        val t = log.trim()
        if (t.length <= max) return t
        val cut = t.substring(0, max)
        val nl = cut.lastIndexOf('\n')
        return (if (nl > max / 2) cut.substring(0, nl) else cut).trimEnd() + "\n…"
    }

    /** 멈춘 기록 한 편: 때 · 버전 · 기기 · 스레드 · 쌓인 호출. */
    fun crashReport(time: String, version: String, android: String, model: String, thread: String, stack: String) = buildString {
        append("time: ").append(time).append('\n')
        append("app: ").append(version).append('\n')
        append("android: ").append(android).append(" · ").append(model).append('\n')
        append("thread: ").append(thread).append('\n')
        append(stack.trim())
    }
}

/** 미리 받기 (data/DownloadService): 알림 · 받기 화면에 쓰는 글자와 다시 받기 간격. */
object Fetching {
    /** 바이트 → "5.1MB" (1MB 아래면 "820KB"). */
    fun size(bytes: Long): String =
        if (bytes >= 1_000_000) String.format(java.util.Locale.US, "%.1fMB", bytes / 1_000_000.0) else "${(bytes / 1000).coerceAtLeast(1)}KB"

    /** 진행: "23 / 64MB" (전체가 1MB 아래면 "120 / 820KB"). 받은 양은 전체를 넘지 않는다. */
    fun progress(done: Long, total: Long): String {
        val d = done.coerceIn(0, total.coerceAtLeast(0))
        return if (total >= 1_000_000) "${d / 1_000_000} / ${(total + 999_999) / 1_000_000}MB"
        else "${d / 1000} / ${((total + 999) / 1000).coerceAtLeast(1)}KB"
    }

    /** 0..100. */
    fun percent(done: Long, total: Long): Int = if (total <= 0) 100 else ((done.coerceIn(0, total) * 100) / total).toInt()

    /** 인터넷이 끊기거나 받기가 실패했을 때 다시 해 보기까지 (밀리초): 2 · 4 · 8 · 16 · 32 · 60 · 60 … 초. */
    fun backoff(attempt: Int): Long = (2_000L shl attempt.coerceIn(0, 5)).coerceAtMost(60_000L)

    /** 인터넷은 있는데 잇따라 이만큼 실패하면 멈추고 “다시 받기”를 보인다. */
    const val MAX_FAILURES = 6

    /**
     * 받다 만 파일([have] 바이트)에서 이어 받기: 서버 응답 코드로 어떻게 쓸지.
     * 206 이면 뒤에 이어 쓰기, 200 이면 처음부터 다시 쓰기, 416 이면 받다 만 것을 버리고 처음부터 다시 요청.
     */
    enum class Resume { APPEND, RESTART, DISCARD, FAIL }
    fun resume(have: Long, code: Int): Resume = when {
        have > 0 && code == 206 -> Resume.APPEND
        code == 200 -> Resume.RESTART
        code == 416 -> Resume.DISCARD
        else -> Resume.FAIL
    }
}
