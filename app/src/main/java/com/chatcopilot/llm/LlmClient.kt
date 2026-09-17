package com.chatcopilot.llm

import android.content.Context
import com.chatcopilot.service.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object LlmClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent?alt=sse"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun stream(
        context: Context,
        systemPrompt: String,
        userMessage: String,
        tone: Tone,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val apiKey = PreferencesManager.getApiKey(context)
        if (apiKey.isNullOrBlank()) {
            onError("API key not configured. Please add it in Settings.")
            return
        }

        val requestBodyJson = buildRequestBody(systemPrompt, userMessage, tone)

        val request = Request.Builder()
            .url(BASE_URL)
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .addHeader("x-goog-api-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .build()

        withContext(Dispatchers.IO) {
            var call: Call? = null
            try {
                call = httpClient.newCall(request)

                // Wire coroutine cancellation to OkHttp call abort
                currentCoroutineContext()[Job]?.invokeOnCompletion {
                    call?.cancel()
                }

                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown API error"
                        withContext(Dispatchers.Main) {
                            onError("API error ${response.code}: $errorBody")
                        }
                        return@withContext
                    }

                    val reader = response.body!!.charStream().buffered()

                    while (true) {
                        val line = reader.readLine() ?: break
                        ensureActive()

                        val trimmed = line.trim()
                        if (!trimmed.startsWith("data: ")) continue
                        val data = trimmed.removePrefix("data: ").trim()
                        if (data.isEmpty() || data == "[DONE]") continue

                        runCatching {
                            val json = JSONObject(data)
                            val candidates = json.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val candidate = candidates.getJSONObject(0)
                                val content = candidate.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val token = part.optString("text", "")
                                        if (token.isNotEmpty()) {
                                            withContext(Dispatchers.Main) { onToken(token) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) { onComplete() }
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: IOException) {
                if (e.message?.contains("Canceled", ignoreCase = true) == true) {
                    withContext(Dispatchers.Main) { onComplete() }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Network error: ${e.message ?: "Connection failed"}")
                    }
                }
            }
        }
    }

    private fun buildRequestBody(
        systemPrompt: String,
        userMessage: String,
        tone: Tone
    ): String {
        val fullSystem = """
            $systemPrompt

            TONE: ${tone.instruction}

            HARD RULES — NEVER BREAK THESE:
            1. Only answer using information from the knowledge base above.
            2. If a question is outside the knowledge base, respond exactly:
               "Let me connect you with a team member who can help with that."
            3. Never guess, invent, or infer information not in the knowledge base.
            4. Detect the customer's language and reply in the same language.
               Supported: Kurdish Sorani (کوردی), Arabic (العربية), English.
            5. Keep replies natural and conversational, not robotic.
        """.trimIndent()

        val contentsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", userMessage))
                })
            })
        }

        val systemInstruction = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", fullSystem))
            })
        }

        val generationConfig = JSONObject().apply {
            put("maxOutputTokens", 1024)
        }

        return JSONObject().apply {
            put("contents", contentsArray)
            put("system_instruction", systemInstruction)
            put("generationConfig", generationConfig)
        }.toString()
    }
}
