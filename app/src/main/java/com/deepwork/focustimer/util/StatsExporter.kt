package com.deepwork.focustimer.util

import com.deepwork.focustimer.data.FocusSession
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Pure transformations: domain rows -> serialized text. No Android I/O here. */
object StatsExporter {

    fun toCsv(sessions: List<FocusSession>): String {
        val sb = StringBuilder()
        sb.append("id,type,durationMillis,durationMinutes,completed,date,dateEpochDay,startedAtEpochMillis,origin\n")
        for (s in sessions) {
            val date = LocalDate.ofEpochDay(s.dateEpochDay)
            sb.append(s.id).append(',')
                .append(s.type.name).append(',')
                .append(s.durationMillis).append(',')
                .append(s.durationMillis / 60_000.0).append(',')
                .append(s.completed).append(',')
                .append(date).append(',')
                .append(s.dateEpochDay).append(',')
                .append(s.startedAtEpochMillis).append(',')
                .append(s.origin.name).append('\n')
        }
        return sb.toString()
    }

    fun toJson(sessions: List<FocusSession>): String {
        val arr = JSONArray()
        for (s in sessions) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("type", s.type.name)
                    .put("durationMillis", s.durationMillis)
                    .put("completed", s.completed)
                    .put("date", LocalDate.ofEpochDay(s.dateEpochDay).toString())
                    .put("dateEpochDay", s.dateEpochDay)
                    .put("startedAtEpochMillis", s.startedAtEpochMillis)
                    .put("origin", s.origin.name)
            )
        }
        return arr.toString(2)
    }
}

enum class ExportFormat(val mime: String, val extension: String) {
    CSV("text/csv", "csv"),
    JSON("application/json", "json"),
}
