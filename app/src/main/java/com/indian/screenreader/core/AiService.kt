package com.indian.screenreader.core

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object AiService {
    private const val TAG = "AiService"
    private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    
    // Separate executors so translation never blocks summarize/describe and vice versa
    private val aiExecutor = Executors.newFixedThreadPool(2)
    private val translateExecutor = Executors.newSingleThreadExecutor()
    private val isSummarizing = AtomicBoolean(false)
    private val isDescribingImage = AtomicBoolean(false)
    // Tracks the utterance ID of the last translation so stale results can be dropped
    @Volatile private var latestTranslationId = 0L

    private fun getApiKey(): String {
        return Settings.GEMINI_API_KEY.trim()
    }

    private fun makeGeminiRequest(prompt: String, base64Image: String? = null): String {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return "Google AI Studio API Key not set. Please set your API key in settings."
        }

        val urlString = "$GEMINI_API_URL?key=$apiKey"
        
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 20000
            connection.readTimeout = 20000

            val partsArray = JSONArray()
            
            if (base64Image != null) {
                val inlineData = JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Image)
                }
                val part = JSONObject().apply {
                    put("inline_data", inlineData)
                }
                partsArray.put(part)
            }

            val textPart = JSONObject().apply {
                put("text", prompt)
            }
            partsArray.put(textPart)

            val content = JSONObject().apply {
                put("parts", partsArray)
            }
            val contentsArray = JSONArray().apply {
                put(content)
            }
            val payload = JSONObject().apply {
                put("contents", contentsArray)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    val resParts = contentObj?.optJSONArray("parts")
                    if (resParts != null && resParts.length() > 0) {
                        return resParts.getJSONObject(0).optString("text", "").trim()
                    }
                }
                return "AI response empty."
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Gemini API Error: $responseCode - $errorBody")
                return "AI Service unavailable. Status code $responseCode."
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API", e)
            return "AI Service unavailable."
        }
    }

    private val summaryCache = android.util.LruCache<String, String>(20)

    fun summarizeScreenAsync(screenText: String, callback: (String) -> Unit, errorCallback: (String) -> Unit) {
        if (screenText.isBlank()) {
            callback("Screen has no readable text to summarize.")
            return
        }

        // Check response cache using SHA-256 hash to prevent collisions
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(screenText.toByteArray())
        val cacheKey = hashBytes.joinToString("") { "%02x".format(it) }
        val cached = summaryCache.get(cacheKey)
        if (cached != null) {
            callback("(Cached) $cached")
            return
        }

        if (!isSummarizing.compareAndSet(false, true)) {
            errorCallback("Already summarizing. Please wait.")
            return
        }

        aiExecutor.execute {
            try {
                if (getApiKey().isBlank()) {
                    callback("AI Offline (API Key not set). Raw screen text: " + screenText.take(200))
                    return@execute
                }
                val summary = summarizeScreen(screenText)
                if (summary.isNotBlank() && !summary.startsWith("AI Service")) {
                    summaryCache.put(cacheKey, summary)
                }
                callback(summary)
            } catch (e: Exception) {
                callback("AI Offline. Screen text: " + screenText.take(200))
            } finally {
                isSummarizing.set(false)
            }
        }
    }

    private fun summarizeScreen(screenText: String): String {
        val prompt = "You are an AI assistant for a blind screen reader user. " +
                "Summarize the following mobile app screen content into 1 or 2 concise, clear sentences:\n\n" +
                screenText
        return makeGeminiRequest(prompt)
    }

    fun translateTextAsync(text: String, targetLanguage: String = "Hindi", callback: (String) -> Unit) {
        if (text.isBlank()) {
            callback(text)
            return
        }
        val myId = System.currentTimeMillis()
        latestTranslationId = myId

        translateExecutor.execute {
            try {
                if (getApiKey().isBlank()) {
                    if (latestTranslationId == myId) callback(text)
                    return@execute
                }
                val prompt = "Translate the following text accurately into $targetLanguage. " +
                        "Output ONLY the translated string without commentary:\n\n$text"
                val result = makeGeminiRequest(prompt)
                if (latestTranslationId == myId) {
                    callback(result)
                }
            } catch (e: Exception) {
                if (latestTranslationId == myId) callback(text) // fallback to original
            }
        }
    }

    fun rewriteSimplifiedAsync(text: String, callback: (String) -> Unit, errorCallback: (String) -> Unit) {
        if (text.isBlank()) {
            callback(text)
            return
        }
        aiExecutor.execute {
            try {
                if (getApiKey().isBlank()) {
                    callback(text)
                    return@execute
                }
                val prompt = "You are an accessibility assistant for visually impaired users. " +
                        "Simplify and rewrite the following text so it is very easy to understand:\n\n$text"
                callback(makeGeminiRequest(prompt))
            } catch (e: Exception) {
                callback(text)
            }
        }
    }

    fun describeImageB64Async(base64Jpeg: String, callback: (String) -> Unit, errorCallback: (String) -> Unit) {
        if (!isDescribingImage.compareAndSet(false, true)) {
            errorCallback("Already describing an image. Please wait.")
            return
        }
        aiExecutor.execute {
            try {
                if (getApiKey().isBlank()) {
                    callback("AI Offline. Please set your Gemini API Key in settings to describe images.")
                    return@execute
                }
                val prompt = "Describe what is in this image concisely in 1 or 2 sentences for a blind user."
                callback(makeGeminiRequest(prompt, base64Jpeg))
            } catch (e: Exception) {
                errorCallback(e.message ?: "Unknown error")
            } finally {
                isDescribingImage.set(false)
            }
        }
    }

    fun extractImageTextB64Async(base64Jpeg: String, callback: (String) -> Unit, errorCallback: (String) -> Unit) {
        aiExecutor.execute {
            try {
                if (getApiKey().isBlank()) {
                    callback("AI OCR Offline. Please set your Gemini API Key in settings.")
                    return@execute
                }
                val prompt = "Extract and transcribe all readable text from this image accurately. Output ONLY the extracted text:"
                val result = makeGeminiRequest(prompt, base64Jpeg)
                callback(if (result.isNotBlank()) result else "No text found in image.")
            } catch (e: Exception) {
                errorCallback(e.message ?: "OCR failed.")
            }
        }
    }
}
