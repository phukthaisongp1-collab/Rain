package com.rain.browser.downloads

import java.io.File

/** Path containment guard for future RAIN-managed video file operations. */
object ManagedVideoPathPolicy {
    fun isManagedFile(managedDirectory: File, candidate: File): Boolean {
        val root = runCatching { managedDirectory.canonicalFile }.getOrNull() ?: return false
        val target = runCatching { candidate.canonicalFile }.getOrNull() ?: return false
        if (target == root) return false
        val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
        return target.path.startsWith(rootPrefix)
    }
}
