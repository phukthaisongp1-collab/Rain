package com.rain.browser.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateGateActivity : Activity() {
    private data class UpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val apkAssetName: String,
        val sha256: String,
        val releaseNotes: String,
        val apkUrl: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    private var pendingUpdate: UpdateInfo? = null
    private var waitingForInstallPermission = false
    private var waitingForInstaller = false
    private var mainStarted = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
            addView(ProgressBar(this@UpdateGateActivity))
            addView(TextView(this@UpdateGateActivity).apply {
                text = "กำลังตรวจสอบการอัปเดต RAIN"
                setPadding(0, 20, 0, 0)
            })
        }
        setContentView(content)
        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForInstallPermission) {
            waitingForInstallPermission = false
            val update = pendingUpdate
            if (update != null && canInstallPackages()) {
                downloadAndInstall(update)
            } else {
                pendingUpdate = null
                launchBrowser()
            }
        } else if (waitingForInstaller) {
            waitingForInstaller = false
            launchBrowser()
        }
    }

    private fun checkForUpdate() {
        mainHandler.postDelayed({
            if (!mainStarted && pendingUpdate == null) launchBrowser()
        }, STARTUP_CHECK_DEADLINE_MS)
        Thread {
            val update = runCatching { fetchLatestUpdate() }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed || mainStarted) return@runOnUiThread
                if (update == null || update.versionCode <= installedVersionCode()) {
                    launchBrowser()
                } else {
                    pendingUpdate = update
                    showUpdatePrompt(update)
                }
            }
        }.start()
    }

    private fun fetchLatestUpdate(): UpdateInfo? {
        val releaseRequest = Request.Builder()
            .url("$RELEASES_API/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "RAIN-Android-AutoUpdate")
            .build()
        val releaseJson = executeText(releaseRequest)
        val release = JSONObject(releaseJson)
        if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) return null
        val assets = release.optJSONArray("assets") ?: return null
        var metadataUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name") == UPDATE_METADATA_ASSET) {
                metadataUrl = asset.optString("browser_download_url").takeIf(String::isNotBlank)
                break
            }
        }
        val url = metadataUrl ?: return null
        requireTrustedReleaseUrl(url)
        val metadata = JSONObject(executeText(Request.Builder().url(url).header("User-Agent", "RAIN-Android-AutoUpdate").build()))
        val versionCode = metadata.getLong("versionCode")
        val versionName = metadata.getString("versionName")
        val apkAssetName = metadata.getString("apkAssetName")
        val sha256 = metadata.getString("sha256").lowercase()
        require(versionCode > 0L)
        require(versionName.isNotBlank())
        require(apkAssetName.endsWith(".apk", ignoreCase = true))
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        val apkUrl = (0 until assets.length()).asSequence()
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name") == apkAssetName }
            ?.optString("browser_download_url")
            ?.takeIf(String::isNotBlank)
            ?: return null
        requireTrustedReleaseUrl(apkUrl)
        return UpdateInfo(
            versionCode,
            versionName,
            apkAssetName,
            sha256,
            metadata.optString("releaseNotes", release.optString("body")),
            apkUrl
        )
    }

    private fun requireTrustedReleaseUrl(value: String) {
        val uri = Uri.parse(value)
        require(uri.scheme == "https")
        require(uri.host.equals("github.com", ignoreCase = true))
        require(uri.userInfo == null)
    }

    private fun executeText(request: Request): String {
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GitHub returned HTTP ${response.code}" }
            return response.body?.string() ?: error("Empty response")
        }
    }

    private fun installedVersionCode(): Long = if (Build.VERSION.SDK_INT >= 28) {
        packageManager.getPackageInfo(packageName, 0).longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
    }

    private fun showUpdatePrompt(update: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle("มี RAIN เวอร์ชันใหม่")
            .setMessage(
                "เวอร์ชัน ${update.versionName} พร้อมใช้งาน\n\n" +
                    update.releaseNotes.take(MAX_RELEASE_NOTES_CHARS) +
                    "\n\nRAIN จะดาวน์โหลดและตรวจสอบ APK ให้ จากนั้น Android จะแสดงหน้าต่างยืนยันการติดตั้ง"
            )
            .setPositiveButton("อัปเดต") { _, _ -> requestInstallPermissionOrDownload(update) }
            .setNegativeButton("ภายหลัง") { _, _ -> launchBrowser() }
            .setOnCancelListener { launchBrowser() }
            .show()
    }

    private fun requestInstallPermissionOrDownload(update: UpdateInfo) {
        pendingUpdate = update
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            AlertDialog.Builder(this)
                .setTitle("อนุญาตให้ RAIN ติดตั้งการอัปเดต")
                .setMessage("Android ต้องให้สิทธิ์ RAIN เปิดตัวติดตั้ง APK ที่คุณเลือกดาวน์โหลด สิทธิ์นี้ใช้เพื่อการอัปเดต RAIN เท่านั้น และคุณสามารถปฏิเสธได้")
                .setPositiveButton("ไปที่การตั้งค่า") { _, _ ->
                    waitingForInstallPermission = true
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .setNegativeButton("ยกเลิก") { _, _ ->
                    pendingUpdate = null
                    launchBrowser()
                }
                .setOnCancelListener {
                    pendingUpdate = null
                    launchBrowser()
                }
                .show()
        } else {
            downloadAndInstall(update)
        }
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun downloadAndInstall(update: UpdateInfo) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            addView(ProgressBar(this@UpdateGateActivity))
            addView(TextView(this@UpdateGateActivity).apply {
                text = "กำลังดาวน์โหลดและตรวจสอบไฟล์อัปเดต"
                setPadding(0, 24, 0, 0)
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("กำลังอัปเดต RAIN")
            .setView(layout)
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val result = runCatching { downloadAndVerify(update) }
            runOnUiThread {
                dialog.dismiss()
                val apk = result.getOrNull()
                if (apk == null) {
                    pendingUpdate = null
                    AlertDialog.Builder(this)
                        .setTitle("อัปเดตไม่สำเร็จ")
                        .setMessage("RAIN ตรวจสอบหรือดาวน์โหลดไฟล์ไม่ผ่าน แอปเดิมยังใช้งานได้ตามปกติ")
                        .setPositiveButton("ตกลง") { _, _ -> launchBrowser() }
                        .show()
                } else {
                    openAndroidInstaller(apk)
                }
            }
        }.start()
    }

    private fun downloadAndVerify(update: UpdateInfo): File {
        val request = Request.Builder()
            .url(update.apkUrl)
            .header("User-Agent", "RAIN-Android-AutoUpdate")
            .build()
        val directory = File(cacheDir, "updates").apply { mkdirs() }
        check(directory.isDirectory)
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        val apk = File(directory, "rain-update-${update.versionCode}.apk")
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "APK download failed: HTTP ${response.code}" }
            val body = response.body ?: error("Empty APK response")
            check(body.contentLength() in 1..MAX_APK_BYTES) { "APK size is invalid" }
            body.byteStream().use { input ->
                FileOutputStream(apk).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_APK_BYTES) { "APK exceeds the size limit" }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        }
        check(apk.isFile && apk.length() > 0L)
        check(sha256(apk).equals(update.sha256, ignoreCase = true)) { "APK SHA-256 mismatch" }

        val archive = packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: error("APK package metadata is invalid")
        check(archive.packageName == packageName) { "Unexpected application ID" }
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archive.versionCode.toLong()
        }
        check(archiveVersion == update.versionCode)
        check(archive.versionName == update.versionName)
        val signer = archive.signingInfo?.apkContentsSigners?.singleOrNull()
            ?: error("APK signing certificate is missing or ambiguous")
        val certificateDigest = MessageDigest.getInstance("SHA-256")
            .digest(signer.toByteArray())
            .joinToString("") { "%02x".format(it) }
        check(certificateDigest.equals(EXPECTED_CERTIFICATE_SHA256, ignoreCase = true)) {
            "APK signing certificate does not match RAIN"
        }
        return apk
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun openAndroidInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        waitingForInstaller = true
        runCatching { startActivity(intent) }.onFailure {
            waitingForInstaller = false
            AlertDialog.Builder(this)
                .setTitle("เปิดตัวติดตั้งไม่ได้")
                .setMessage("Android ไม่สามารถเปิด Package Installer ได้ แอปเดิมยังใช้งานได้")
                .setPositiveButton("ตกลง") { _, _ -> launchBrowser() }
                .show()
        }
    }

    private fun launchBrowser() {
        if (mainStarted || isFinishing || isDestroyed) return
        mainStarted = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/phukthaisongp1-collab/Rain/releases/latest"
        private const val UPDATE_METADATA_ASSET = "rain-update.json"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val MAX_APK_BYTES = 512L * 1024L * 1024L
        private const val MAX_RELEASE_NOTES_CHARS = 3000
        private const val STARTUP_CHECK_DEADLINE_MS = 4000L
        private const val EXPECTED_CERTIFICATE_SHA256 =
            "d1b9587fd636c1e0e92a5718576381aadbc72190f5c927f45e7d4ebe0eadb592"
    }
}
