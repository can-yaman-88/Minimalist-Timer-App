package com.deepwork.focustimer.util

import com.deepwork.focustimer.data.FocusSession
import com.deepwork.focustimer.data.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsImporterTest {

    private val sample = listOf(
        FocusSession(
            id = 1, type = SessionType.FOCUS, durationMillis = 3_000_000,
            completed = true, dateEpochDay = 20_000, startedAtEpochMillis = 1_700_000_000_000,
        ),
        FocusSession(
            id = 2, type = SessionType.BREAK, durationMillis = 600_000,
            completed = false, dateEpochDay = 20_000, startedAtEpochMillis = 1_700_000_600_000,
        ),
    )

    private fun assertMatches(parsed: List<FocusSession>) {
        assertEquals(sample.size, parsed.size)
        sample.forEachIndexed { i, expected ->
            val actual = parsed[i]
            assertEquals(expected.type, actual.type)
            assertEquals(expected.durationMillis, actual.durationMillis)
            assertEquals(expected.completed, actual.completed)
            assertEquals(expected.dateEpochDay, actual.dateEpochDay)
            assertEquals(expected.startedAtEpochMillis, actual.startedAtEpochMillis)
        }
    }

    @Test fun csvRoundTrip() {
        assertMatches(StatsImporter.parseCsv(StatsExporter.toCsv(sample)))
    }

    @Test fun jsonRoundTrip() {
        assertMatches(StatsImporter.parseJson(StatsExporter.toJson(sample)))
    }

    @Test fun csvAcceptsBooleanAndDateVariants() {
        val csv = """
            type,durationMillis,completed,date,startedAtEpochMillis
            FOCUS,1000,1,2024-01-17,1700000000000
            break,2000,0,2024-01-18,1700000600000
        """.trimIndent()
        val parsed = StatsImporter.parseCsv(csv)
        assertEquals(2, parsed.size)
        assertEquals(SessionType.FOCUS, parsed[0].type)
        assertTrue(parsed[0].completed)
        assertEquals(SessionType.BREAK, parsed[1].type)
        assertEquals(false, parsed[1].completed)
        // 'date' resolved into a day key.
        assertEquals(java.time.LocalDate.of(2024, 1, 17).toEpochDay(), parsed[0].dateEpochDay)
    }

    @Test fun emptyFileRejected() {
        val e = assertThrows(ImportException::class.java) { StatsImporter.parseCsv("   ") }
        assertTrue(e.message!!.contains("empty", ignoreCase = true))
    }

    @Test fun csvMissingRequiredColumnRejected() {
        val csv = "type,durationMillis,completed,dateEpochDay\nFOCUS,1000,true,20000"
        val e = assertThrows(ImportException::class.java) { StatsImporter.parseCsv(csv) }
        assertTrue(e.message!!.contains("startedAtEpochMillis"))
    }

    @Test fun csvMissingDayFieldRejected() {
        val csv = "type,durationMillis,completed,startedAtEpochMillis\nFOCUS,1000,true,1700000000000"
        assertThrows(ImportException::class.java) { StatsImporter.parseCsv(csv) }
    }

    @Test fun csvBadTypeRejected() {
        val csv = "type,durationMillis,completed,dateEpochDay,startedAtEpochMillis\n" +
            "SLEEP,1000,true,20000,1700000000000"
        val e = assertThrows(ImportException::class.java) { StatsImporter.parseCsv(csv) }
        assertTrue(e.message!!.contains("type", ignoreCase = true))
    }

    @Test fun csvNonNumericDurationRejected() {
        val csv = "type,durationMillis,completed,dateEpochDay,startedAtEpochMillis\n" +
            "FOCUS,abc,true,20000,1700000000000"
        val e = assertThrows(ImportException::class.java) { StatsImporter.parseCsv(csv) }
        assertTrue(e.message!!.contains("durationMillis"))
    }

    @Test fun csvBadCompletedRejected() {
        val csv = "type,durationMillis,completed,dateEpochDay,startedAtEpochMillis\n" +
            "FOCUS,1000,maybe,20000,1700000000000"
        assertThrows(ImportException::class.java) { StatsImporter.parseCsv(csv) }
    }

    @Test fun jsonNotArrayRejected() {
        assertThrows(ImportException::class.java) { StatsImporter.parseJson("{\"type\":\"FOCUS\"}") }
    }

    @Test fun jsonMissingFieldRejected() {
        val json = "[{\"type\":\"FOCUS\",\"durationMillis\":1000}]"
        assertThrows(ImportException::class.java) { StatsImporter.parseJson(json) }
    }

    @Test fun jsonEmptyArrayRejected() {
        assertThrows(ImportException::class.java) { StatsImporter.parseJson("[]") }
    }
}
