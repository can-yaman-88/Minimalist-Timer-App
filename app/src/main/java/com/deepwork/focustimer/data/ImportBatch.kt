package com.deepwork.focustimer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One import operation: the file the user picked and how many rows it added.
 * Deleting a batch removes every [FocusSession] that carries its id, so the
 * Data screen can offer "remove imported file" as a single action.
 */
@Entity(tableName = "import_batches")
data class ImportBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val format: String,                 // "CSV" / "JSON"
    val importedAtEpochMillis: Long,
    val sessionCount: Int,
)
