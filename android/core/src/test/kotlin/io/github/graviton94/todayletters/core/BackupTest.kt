package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTest {
    private val prefs: Map<String, Any> = mapOf(
        "p:vincent/I/1" to "6|MATCH,ALOUD|1|",
        "p:vincent/I/2" to "3||0|",
        "p:mozart/I/1" to "5|MATCH|1|ALOUD",
        "c:fr:soleil" to "2|20400|3|0|20380|0",
        "c:fr:lune" to "1|20390|1|0|20380|1",
        "onboarded" to true,
        "xp:total" to 1234,
        "first_day" to 20380L,
        "update:asked" to 1_760_000_000_123L,
        "s:vincent:lspeed" to 0.85f,
        "ui" to "KO",
        "quotes" to setOf("a \"b\"", "줄\n바꿈", "c\\d"),
        "ent:sub" to true,
        "ent:owned" to setOf("series_vincent"),
        "ent:sub_token" to "tok",
        "debug:unlock_all" to true,
        "backup:last" to 20380L,
    )

    @Test fun roundTripKeepsTypesAndDropsEntitlement() {
        val text = Backup.encode(prefs, "2026-10-18")
        val r = assertNotNull(Backup.decode(text))
        assertEquals(Backup.FORMAT, r.format)
        assertEquals(prefs.filterKeys(Backup::keep), r.values)
        assertTrue(r.values.keys.none { it.startsWith("ent:") || it.startsWith("debug:") || it.startsWith("backup:") })
        assertEquals(20380L, r.values["first_day"])
        assertEquals(1234, r.values["xp:total"])
        assertEquals(0.85f, r.values["s:vincent:lspeed"])
        assertEquals(Backup.Summary(2, 2, "2026-10-18"), r.summary)
        assertTrue("ent:" !in text && "debug:" !in text)
    }

    @Test fun importIgnoresEntitlementWrittenIntoFile() {
        val text = """{"app":"${Backup.APP}","kind":"records","format":1,"made":"2026-10-18","prefs":{
            "ent:sub":["b","true"],"ent:owned":["ss",["series_vincent","series_mozart"]],"debug:unlock_all":["b","true"],"onboarded":["b","true"]}}"""
        val r = assertNotNull(Backup.decode(text))
        assertEquals(mapOf<String, Any>("onboarded" to true), r.values)
    }

    @Test fun unreadableFiles() {
        val ok = Backup.encode(prefs, "2026-10-18")
        assertNull(Backup.decode(""))
        assertNull(Backup.decode("not json"))
        assertNull(Backup.decode("[1,2]"))
        assertNull(Backup.decode(ok.dropLast(5)))
        assertNull(Backup.decode(ok.replace(Backup.APP, "com.other.app")))
        assertNull(Backup.decode(ok.replace("\"format\":1", "\"format\":2")))
        assertNull(Backup.decode(ok.replace("\"kind\":\"records\"", "\"kind\":\"other\"")))
        assertNull(Backup.decode("""{"app":"${Backup.APP}","kind":"records","format":1,"made":"2026-10-18","prefs":{"x":["i","oops"]}}"""))
        assertNull(Backup.decode("""{"app":"${Backup.APP}","kind":"records","format":1,"made":"2026-10-18","prefs":{"x":["zz","1"]}}"""))
        assertNotNull(Backup.decode("﻿" + ok))
    }

    @Test fun fileName() = assertEquals("언어미술관-기록-2026-10-18.json", Backup.fileName("언어미술관-기록", "2026-10-18"))
}
