package com.rain.browser.downloads

import com.rain.browser.data.VideoRepository
import java.io.File

/** Applies the 30-day policy without touching liked or unmanaged files. */
object VideoRetentionCleanup {
    fun removeExpired(
        repository: VideoRepository,
        managedDirectory: File,
        now: Long = System.currentTimeMillis()
    ): Int {
        var removed = 0
        repository.findExpired(now).forEach { record ->
            val path = record.filePath
            if (path.isBlank()) {
                if (repository.removeMetadata(record.id)) removed++
                return@forEach
            }

            val file = File(path)
            if (!file.exists()) {
                if (repository.removeMetadata(record.id)) removed++
                return@forEach
            }

            if (!ManagedVideoPathPolicy.isManagedFile(managedDirectory, file)) {
                return@forEach
            }

            if (file.delete() && repository.removeMetadata(record.id)) {
                removed++
            }
        }
        return removed
    }
}
