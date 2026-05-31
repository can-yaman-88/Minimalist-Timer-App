package com.deepwork.focustimer.util

import com.deepwork.focustimer.data.FocusSession
import com.deepwork.focustimer.data.SessionType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Thrown when an imported file doesn't match the expected stats schema. */
class ImportException(message: String) : Exception(message)

/**
 * Pure inverse of [StatsExporter]: serialized text -> validated domain rows.
 * No Android I/O. Every malformed input raises [ImportException] with a
 * human-readable reason, so the UI can show exactly why a file was rejected.
 *
 * Required fields per row: `type`, `durationMillis`, `completed`,
 * `startedAtEpochMillis`, and a day key (`dateEpochDay` or `date`). The `id`
 * and `origin` columns are accepted but ignored — imported rows are always
 * re-keyed and tagged IMPORTED by the repository.
 */
object StatsImporter {

    private val REQUIRED = listOf("type", "durationMillis", "completed", "startedAtEpochMillis")

    fun parseCsv(text: String): List<FocusSession> {
        val lines = text.trim().lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) throw ImportException("File is empty.")

        val header = lines.first().split(",").map { it.trim() }
        val index = header.withIndex().associate { (i, name) -> name to i }
        requireColumns(index.keys)

        val out = ArrayList<FocusSession>(lines.size - 1)
        for (rowNum in 1 until lines.size) {
            val cells = lines[rowNum].split(",")
            fun cell(col: String): String {
                val i = index[col] ?: throw ImportException("Missing column '$col'.")
                if (i >= cells.size) throw ImportException("Row ${rowNum + 1}: too few values.")
                return cells[i].trim()
            }
            out += buildSession(
                rowLabel = "Row ${rowNum + 1}",
                type = cell("type"),
                durationMillis = cell("durationMillis"),
                completed = cell("completed"),
                startedAt = cell("startedAtEpochMillis"),
                dateEpochDay = if (index.containsKey("dateEpochDay")) cell("dateEpochDay") else null,
                date = if (index.containsKey("date")) cell("date") else null,
            )
        }
        if (out.isEmpty()) throw ImportException("No data rows found.")
        return out
    }

    fun parseJson(text: String): List<FocusSession> {
        val arr = try {
            JSONArray(text.trim())
        } catch (e: JSONException) {
            throw ImportException("Not a valid JSON array.")
        }
        if (arr.length() == 0) throw ImportException("No data rows found.")

        val out = ArrayList<FocusSession>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
                ?: throw ImportException("Item ${i + 1} is not a JSON object.")
            requireColumns(obj.keys().asSequence().toSet())
            out += buildSession(
                rowLabel = "Item ${i + 1}",
                type = obj.optString("type"),
                durationMillis = jsonField(obj, "durationMillis"),
                completed = jsonField(obj, "completed"),
                startedAt = jsonField(obj, "startedAtEpochMillis"),
                dateEpochDay = if (obj.has("dateEpochDay")) jsonField(obj, "dateEpochDay") else null,
                date = if (obj.has("date")) obj.optString("date") else null,
            )
        }
        return out
    }

    // --- helpers ---

    private fun requireColumns(present: Set<String>) {
        val missing = REQUIRED.filter { it !in present }
        if (missing.isNotEmpty()) {
            throw ImportException("Missing required field(s): ${missing.joinToString(", ")}.")
        }
        if ("dateEpochDay" !in present && "date" !in present) {
            throw ImportException("Missing a day field: 'dateEpochDay' or 'date'.")
        }
    }

    private fun jsonField(obj: JSONObject, key: String): String =
        if (obj.isNull(key)) "" else obj.get(key).toString()

    private fun buildSession(
        rowLabel: String,
        type: String,
        durationMillis: String,
        completed: String,
        startedAt: String,
        dateEpochDay: String?,
        date: String?,
    ): FocusSession {
        val sessionType = try {
            SessionType.valueOf(type.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            throw ImportException("$rowLabel: invalid type '$type' (expected FOCUS or BREAK).")
        }
        val duration = durationMillis.toLongOrThrow(rowLabel, "durationMillis")
        val started = startedAt.toLongOrThrow(rowLabel, "startedAtEpochMillis")
        val day = when {
            !dateEpochDay.isNullOrBlank() -> dateEpochDay.toLongOrThrow(rowLabel, "dateEpochDay")
            !date.isNullOrBlank() -> try {
                LocalDate.parse(date.trim()).toEpochDay()
            } catch (e: DateTimeParseException) {
                throw ImportException("$rowLabel: invalid date '$date' (expected YYYY-MM-DD).")
            }
            else -> throw ImportException("$rowLabel: missing day value.")
        }
        val done = completed.toBooleanStrictOrThrow(rowLabel)

        return FocusSession(
            type = sessionType,
            durationMillis = duration,
            completed = done,
            dateEpochDay = day,
            startedAtEpochMillis = started,
        )
    }

    private fun String.toLongOrThrow(rowLabel: String, field: String): Long =
        trim().toLongOrNull()
            ?: throw ImportException("$rowLabel: '$field' must be a whole number (got '$this').")

    private fun String.toBooleanStrictOrThrow(rowLabel: String): Boolean =
        when (trim().lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> throw ImportException("$rowLabel: 'completed' must be true/false (got '$this').")
        }
}
