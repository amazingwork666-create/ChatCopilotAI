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

    private const val BASE_URL = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-sonnet-4-6"

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
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
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
                        if (data == "[DONE]" || data.isEmpty()) continue

                        runCatching {
                            val json = JSONObject(data)
                            if (json.optString("type") == "content_block_delta") {
                                val delta = json.optJSONObject("delta")
                                if (delta?.optString("type") == "text_delta") {
                                    val token = delta.optString("text", "")
                                    if (token.isNotEmpty()) {
                                        withContext(Dispatchers.Main) { onToken(token) }
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

        return JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 1024)
            put("stream", true)
            put("system", fullSystem)
            put("messages", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                }
            ))
        }.toString()
    }
}
