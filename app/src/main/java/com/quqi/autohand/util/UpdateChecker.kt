package com.quqi.autohand.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.quqi.autohand.BuildConfig
import com.quqi.autohand.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 检测新版本：比对 GitHub Release 与本包的 versionCode / 构建时间。 */
object UpdateChecker {

    const val RELEASE_PAGE = "https://github.com/hnzsm/autohand/releases/latest"
    private const val API = "https://api.github.com/repos/hnzsm/autohand/releases/latest"

    /** CI 发布同一个包时，资产上传时间只比编译时间晚几分钟，小于这个间隔视为同一个包。 */
    private const val SAME_BUILD_MARGIN_MS = 15 * 60 * 1000L

    private sealed interface Result {
        data object UpToDate : Result
        data class NewVersion(val label: String) : Result
        data class Failed(val reason: String) : Result
    }

    fun check(activity: AppCompatActivity) {
        val progress = AlertDialog.Builder(activity)
            .setTitle(R.string.update_checking)
            .create()
        progress.show()
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { fetch() }
            progress.dismiss()
            if (!activity.isFinishing && !activity.isDestroyed) showResult(activity, result)
        }
    }

    private fun fetch(): Result {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "AutoHand-UpdateChecker")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return Result.Failed("HTTP ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val body = json.optString("body")
            val remoteCode = Regex("versionCode=(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
            if (remoteCode != null) {
                val remoteName = Regex("versionName=([\\w.]+)").find(body)?.groupValues?.get(1)
                return if (remoteCode > BuildConfig.VERSION_CODE) {
                    Result.NewVersion(remoteName ?: "versionCode $remoteCode")
                } else {
                    Result.UpToDate
                }
            }
            // 旧发布没带版本号：退而比较发布时间与本包构建时间
            val remoteMs = parseIso(json.optString("published_at"))
                ?: parseIso(json.optJSONArray("assets")?.optJSONObject(0)?.optString("updated_at").orEmpty())
            when {
                remoteMs == null -> Result.Failed("no version info")
                remoteMs > BuildConfig.BUILD_TIME + SAME_BUILD_MARGIN_MS -> Result.NewVersion(formatDay(remoteMs))
                else -> Result.UpToDate
            }
        } catch (e: Exception) {
            Result.Failed(e.javaClass.simpleName)
        } finally {
            conn.disconnect()
        }
    }

    private fun showResult(context: Context, result: Result) {
        val current = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val builder = when (result) {
            is Result.UpToDate -> AlertDialog.Builder(context)
                .setTitle(R.string.update_latest)
                .setMessage(context.getString(R.string.update_latest_msg, current))
                .setPositiveButton(R.string.btn_ok, null)

            is Result.NewVersion -> AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.update_found, result.label))
                .setMessage(context.getString(R.string.update_found_msg, current, result.label))
                .setPositiveButton(R.string.update_go) { _, _ ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                .setNegativeButton(R.string.btn_cancel, null)

            is Result.Failed -> AlertDialog.Builder(context)
                .setTitle(R.string.update_fail)
                .setMessage(context.getString(R.string.update_fail_msg, result.reason))
                .setPositiveButton(R.string.btn_ok, null)
                .setNegativeButton(R.string.settings_repo) { _, _ ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
        }
        builder.show()
    }

    private fun parseIso(s: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(s)?.time
    } catch (e: Exception) {
        null
    }

    private fun formatDay(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))
}
