package com.notipass.droid.engine

import android.content.Context
import android.util.Log
import com.notipass.droid.data.preference.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DriveSyncResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

object GoogleDriveDispatcher {

    private const val TAG = "GoogleDriveDispatcher"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // GAS Web Apps return 302 on POST and execute the script.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    const val SAMPLE_GAS_SCRIPT = """function doPost(e) {
  try {
    var rawData = e.postData.contents;
    var data = JSON.parse(rawData);
    var action = data.action || "append_message";
    var roomName = (data.roomName || "일반대화").replace(/[/\\?%*:|"<>]/g, "_");
    var folderName = "카카오톡_대화_백업";
    
    // 폴더 생성 또는 가져오기
    var folders = DriveApp.getFoldersByName(folderName);
    var folder = folders.hasNext() ? folders.next() : DriveApp.createFolder(folderName);
    
    // 파일 생성 또는 가져오기
    var fileName = roomName + ".txt";
    var files = folder.getFilesByName(fileName);
    var file = files.hasNext() ? files.next() : folder.createFile(fileName, "");
    
    var textToAppend = "";
    if (action === "save_summary") {
      var summary = data.summary || "";
      var count = data.messageCount || 0;
      var time = Utilities.formatDate(new Date(), "GMT+9", "yyyy-MM-dd HH:mm:ss");
      textToAppend = "\n\n========================================\n" +
                     "[AI 요약 (" + time + ") - " + count + "개 대화]\n" +
                     summary + "\n" +
                     "========================================\n\n";
    } else {
      textToAppend = data.line ? (data.line + "\n") : (data.text + "\n");
    }
    
    var currentContent = file.getBlob().getDataAsString();
    file.setContent(currentContent + textToAppend);
    
    return ContentService.createTextOutput(JSON.stringify({ status: "success", room: roomName }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet(e) {
  return ContentService.createTextOutput(JSON.stringify({ status: "ok", message: "NotiPass Google Drive Sync Ready" }))
    .setMimeType(ContentService.MimeType.JSON);
}"""

    fun appendMessageAsync(
        context: Context,
        roomName: String,
        sender: String,
        text: String,
        timestamp: Long,
        formattedLine: String
    ) {
        if (!AppPreferences.isGoogleDriveEnabled(context)) return
        val url = AppPreferences.getGoogleDriveUrl(context)
        if (url.isBlank()) return

        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("action", "append_message")
                    put("roomName", roomName)
                    put("sender", sender)
                    put("text", text)
                    put("timestamp", timestamp)
                    put("line", formattedLine.trimEnd('\n'))
                }
                sendJsonToUrl(url, json.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message to Google Drive for $roomName", e)
            }
        }
    }

    suspend fun appendSummary(
        context: Context,
        roomName: String,
        summary: String,
        messageCount: Int
    ): DriveSyncResult = withContext(Dispatchers.IO) {
        if (!AppPreferences.isGoogleDriveEnabled(context)) {
            return@withContext DriveSyncResult(true)
        }
        val url = AppPreferences.getGoogleDriveUrl(context)
        if (url.isBlank()) {
            return@withContext DriveSyncResult(false, "구글 드라이브 WebApp URL이 비어있습니다.")
        }

        try {
            val json = JSONObject().apply {
                put("action", "save_summary")
                put("roomName", roomName)
                put("summary", summary)
                put("messageCount", messageCount)
                put("timestamp", System.currentTimeMillis())
            }
            sendJsonToUrl(url, json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send summary to Google Drive for $roomName", e)
            DriveSyncResult(false, e.message)
        }
    }

    suspend fun testConnection(url: String): DriveSyncResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext DriveSyncResult(false, "URL을 입력해주세요.")
        }
        try {
            val testJson = JSONObject().apply {
                put("action", "test")
                put("roomName", "NotiPass_연동테스트")
                put("line", "[테스트] NotiPassDroid 구글 드라이브 연동 성공!")
                put("timestamp", System.currentTimeMillis())
            }
            sendJsonToUrl(url.trim(), testJson.toString())
        } catch (e: Exception) {
            DriveSyncResult(false, e.message ?: "연동 테스트 중 오류가 발생했습니다.")
        }
    }

    private fun sendJsonToUrl(url: String, jsonString: String): DriveSyncResult {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = jsonString.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url.trim())
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            // Google Apps Script redirects (302) or returns 200
            if (response.isSuccessful || response.code == 302) {
                return DriveSyncResult(true)
            }
            return DriveSyncResult(false, "HTTP ${response.code}: ${response.message}")
        }
    }
}
