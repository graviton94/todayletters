package io.github.graviton94.todayletters.core

import io.github.graviton94.todayletters.core.UpdateFlow.Install
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateFlowTest {
    private val hidden = UpdateBand.Hidden

    @Test fun offerOncePerDay() {
        assertEquals(UpdateBand.Offer(18_000_000), UpdateFlow.onInfo(hidden, true, Install.NONE, 0, 18_000_000, askedToday = false, readyDismissed = false))
        assertEquals(hidden, UpdateFlow.onInfo(hidden, true, Install.NONE, 0, 0, askedToday = true, readyDismissed = false))
        assertEquals(hidden, UpdateFlow.onInfo(hidden, false, Install.NONE, 0, 0, askedToday = false, readyDismissed = false))
        assertEquals(UpdateBand.Offer(0), UpdateFlow.onInfo(hidden, true, Install.NONE, 0, -1, askedToday = false, readyDismissed = false))
    }

    @Test fun resumeKeepsProgressAndReady() {
        assertEquals(UpdateBand.Fetching(5, 10), UpdateFlow.onInfo(hidden, false, Install.DOWNLOADING, 5, 10, askedToday = true, readyDismissed = false))
        assertEquals(UpdateBand.Ready, UpdateFlow.onInfo(hidden, false, Install.DOWNLOADED, 10, 10, askedToday = true, readyDismissed = false))
        assertEquals(hidden, UpdateFlow.onInfo(UpdateBand.Ready, false, Install.DOWNLOADED, 10, 10, askedToday = true, readyDismissed = true))
        // 실패한 띠는 다시 받기 전까지 그대로
        assertEquals(UpdateBand.Failed, UpdateFlow.onInfo(UpdateBand.Failed, true, Install.NONE, 0, 0, askedToday = true, readyDismissed = false))
    }

    @Test fun installStates() {
        val f = UpdateFlow.onInstall(hidden, Install.DOWNLOADING, 42, 100, false)
        assertEquals(42, (f as UpdateBand.Fetching).percent)
        assertEquals(0, UpdateBand.Fetching(0, 0).percent)
        assertEquals(UpdateBand.Ready, UpdateFlow.onInstall(f, Install.DOWNLOADED, 100, 100, false))
        assertEquals(UpdateBand.Failed, UpdateFlow.onInstall(f, Install.FAILED, 0, 0, false))
        assertEquals(UpdateBand.Failed, UpdateFlow.onInstall(f, Install.CANCELED, 0, 0, false))
        assertEquals(hidden, UpdateFlow.onInstall(UpdateBand.Ready, Install.INSTALLING, 0, 0, false))
    }

    @Test fun dialog() {
        assertEquals(hidden, UpdateFlow.onDialog(UpdateBand.Offer(0), accepted = false, failed = false, total = 0))
        assertEquals(UpdateBand.Fetching(0, 9), UpdateFlow.onDialog(UpdateBand.Offer(9), accepted = true, failed = false, total = 9))
        assertEquals(UpdateBand.Fetching(3, 9), UpdateFlow.onDialog(UpdateBand.Fetching(3, 9), accepted = true, failed = false, total = 9))
        assertEquals(UpdateBand.Failed, UpdateFlow.onDialog(UpdateBand.Offer(9), accepted = false, failed = true, total = 9))
    }
}
