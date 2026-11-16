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

    const val SAMPLE_GAS_SCRIPT = """// ========================================================
// NotiPassDroid - 구글 드라이브(Google Docs) 백업 및 AI 요약 스크립트
// ========================================================

function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var action = data.action || "append_message";
    var roomName = (data.roomName || "카카오톡_대화").replace(/[/\\?%*:|"<>]/g, "_");
    var folderName = "카카오톡_대화_백업";
    
    // API 키가 전달된 경우 자동 저장 (스케줄 요약용)
    if (data.apiKey) {
      PropertiesService.getScriptProperties().setProperty("GEMINI_API_KEY", data.apiKey);
    }
    
    // 1. '카카오톡_대화_백업' 폴더 찾기 또는 생성
    var folders = DriveApp.getFoldersByName(folderName);
    var folder = folders.hasNext() ? folders.next() : DriveApp.createFolder(folderName);
    
    // 2. 단톡방 이름의 Google Docs(구글 문서) 찾기 또는 생성
    var docName = roomName;
    var files = folder.getFilesByName(docName);
    var doc;
    
    if (files.hasNext()) {
      var file = files.next();
      doc = DocumentApp.openById(file.getId());
    } else {
      doc = DocumentApp.create(docName);
      var docFile = DriveApp.getFileById(doc.getId());
      docFile.moveTo(folder);
      // 첫 헤더 추가
      var body = doc.getBody();
      body.appendParagraph("📁 [" + roomName + "] 카카오톡 대화 아카이브")
          .setHeading(DocumentApp.ParagraphHeading.HEADING1);
      body.appendHorizontalRule();
    }
    
    var body = doc.getBody();
    
    // 3. 동작 수행
    if (action === "save_summary") {
      var summary = data.summary || "";
      var count = data.messageCount || 0;
      var time = Utilities.formatDate(new Date(), "GMT+9", "yyyy-MM-dd HH:mm:ss");
      
      body.appendHorizontalRule();
      var summaryHeader = body.appendParagraph("✨ [AI 핵심 요약 (" + time + ") - " + count + "개 대화]");
      summaryHeader.setHeading(DocumentApp.ParagraphHeading.HEADING2);
      
      var summaryPara = body.appendParagraph(summary);
      summaryPara.setBold(true);
      body.appendHorizontalRule();
      
    } else if (action === "test") {
      var testPara = body.appendParagraph("✅ [연동 성공] " + Utilities.formatDate(new Date(), "GMT+9", "yyyy-MM-dd HH:mm:ss") + " - NotiPassDroid 구글 문서(Docs) 연동 완료!");
      testPara.setItalic(true);
    } else {
      // 일반 메시지 추가 (단락 추가)
      var textToAppend = data.line || (data.sender + ": " + data.text);
      body.appendParagraph(textToAppend);
    }
    
    doc.saveAndClose();
    
    return ContentService.createTextOutput(JSON.stringify({ status: "success", room: roomName, docUrl: doc.getUrl() }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet(e) {
  return ContentService.createTextOutput(JSON.stringify({ status: "ok", message: "NotiPass Google Docs Sync Ready!" }))
    .setMimeType(ContentService.MimeType.JSON);
}

// ========================================================
// ⏰ [무인 자동화] 매일 정해진 시간에 자동 실행되는 일일 요약 트리거
// 설정 방법: script.google.com 좌측 '트리거(시계 아이콘)' -> [트리거 추가]
// 실행할 함수: 'dailyScheduleSummary' 선택 -> 이벤트 소스: '시간 기반' -> '일 단위 타이머(예: 오후 10시~11시)'
// ========================================================
function dailyScheduleSummary() {
  var apiKey = PropertiesService.getScriptProperties().getProperty("GEMINI_API_KEY");
  if (!apiKey) {
    Logger.log("Gemini API 키가 없습니다. NotiPassDroid 앱에서 연동 테스트를 한 번 실행해주세요.");
    return;
  }
  
  var folderName = "카카오톡_대화_백업";
  var folders = DriveApp.getFoldersByName(folderName);
  if (!folders.hasNext()) return;
  var folder = folders.next();
  var files = folder.getFiles();
  
  while (files.hasNext()) {
    var file = files.next();
    if (file.getMimeType() === MimeType.GOOGLE_DOCS) {
      try {
        var doc = DocumentApp.openById(file.getId());
        var fullText = doc.getBody().getText();
        if (fullText.length > 80) {
          var recentText = fullText.slice(-4000); // 최근 대화 4,000자 발췌
          var summary = requestGemini(apiKey, file.getName(), recentText);
          if (summary) {
            var time = Utilities.formatDate(new Date(), "GMT+9", "yyyy-MM-dd HH:mm");
            var body = doc.getBody();
            body.appendHorizontalRule();
            var pHead = body.appendParagraph("🤖 [Gemini 일일 정기 요약 - " + time + "]");
            pHead.setHeading(DocumentApp.ParagraphHeading.HEADING2);
            body.appendParagraph(summary).setBold(true);
            body.appendHorizontalRule();
            doc.saveAndClose();
            Logger.log("요약 완료: " + file.getName());
          }
        }
      } catch (e) {
        Logger.log("Error in file " + file.getName() + ": " + e);
      }
    }
  }
}

function requestGemini(apiKey, roomName, chatText) {
  var url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
  var prompt = "당신은 카카오톡 단톡방 비서입니다. 아래는 '" + roomName + "' 단톡방의 최근 대화 내용입니다.\n" +
               "잡담을 제외하고 오늘 있었던 주요 약속, 일시, 장소, 결정된 안건, 공지사항을 1~3문장으로 간결하게 핵심 요약해주세요.\n\n" +
               chatText;
  
  var payload = {
    contents: [{
      parts: [{ text: prompt }]
    }]
  };
  
  var options = {
    method: "post",
    contentType: "application/json",
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  };
  
  var response = UrlFetchApp.fetch(url, options);
  var json = JSON.parse(response.getContentText());
  if (json.candidates && json.candidates.length > 0) {
    return json.candidates[0].content.parts[0].text.trim();
  }
  return null;
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
            val apiKey = AppPreferences.getGeminiApiKey(context)
            val json = JSONObject().apply {
                put("action", "save_summary")
                put("roomName", roomName)
                put("summary", summary)
                put("messageCount", messageCount)
                put("timestamp", System.currentTimeMillis())
                if (apiKey.isNotBlank()) {
                    put("apiKey", apiKey)
                }
            }
            sendJsonToUrl(url, json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send summary to Google Drive for $roomName", e)
            DriveSyncResult(false, e.message)
        }
    }

    suspend fun testConnection(url: String, apiKey: String = ""): DriveSyncResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext DriveSyncResult(false, "URL을 입력해주세요.")
        }
        try {
            val testJson = JSONObject().apply {
                put("action", "test")
                put("roomName", "NotiPass_연동테스트")
                put("line", "[테스트] NotiPassDroid 구글 문서(Docs) 연동 성공!")
                put("timestamp", System.currentTimeMillis())
                if (apiKey.isNotBlank()) {
                    put("apiKey", apiKey)
                }
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
