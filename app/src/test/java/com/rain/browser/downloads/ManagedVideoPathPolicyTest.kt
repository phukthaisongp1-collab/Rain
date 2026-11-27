package com.rain.browser.downloads

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedVideoPathPolicyTest {
    @Test
    fun acceptsFilesInsideManagedDirectory() {
        val root = Files.createTempDirectory("rain-videos").toFile()
        try {
            assertTrue(ManagedVideoPathPolicy.isManagedFile(root, File(root, "video.mp4")))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsManagedDirectoryItselfAndSiblingFiles() {
        val parent = Files.createTempDirectory("rain-storage").toFile()
        val root = File(parent, "videos").apply { mkdirs() }
        try {
            assertFalse(ManagedVideoPathPolicy.isManagedFile(root, root))
            assertFalse(ManagedVideoPathPolicy.isManagedFile(root, File(parent, "other.mp4")))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalOutsideManagedDirectory() {
        val parent = Files.createTempDirectory("rain-storage").toFile()
        val root = File(parent, "videos").apply { mkdirs() }
        try {
            assertFalse(ManagedVideoPathPolicy.isManagedFile(root, File(root, "../important.db")))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkThatEscapesManagedDirectoryWhenSupported() {
        val parent = Files.createTempDirectory("rain-storage").toFile()
        val root = File(parent, "videos").apply { mkdirs() }
        val outside = File(parent, "outside.mp4").apply { writeText("keep") }
        val link = File(root, "linked.mp4")
        try {
            try {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            } catch (_: Exception) {
                return
            }
            assertFalse(ManagedVideoPathPolicy.isManagedFile(root, link))
        } finally {
            parent.deleteRecursively()
        }
    }
}
