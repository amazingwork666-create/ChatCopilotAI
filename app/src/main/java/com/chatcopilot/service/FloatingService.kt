package com.chatcopilot.service

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.core.app.NotificationCompat
import com.chatcopilot.R
import com.chatcopilot.llm.KnowledgeBaseManager
import com.chatcopilot.llm.LlmClient
import com.chatcopilot.ui.ResultOverlayView
import kotlinx.coroutines.*

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var resultOverlay: ResultOverlayView

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var llmJob: Job? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    companion object {
        const val CHANNEL_ID = "chatcopilot_overlay"
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, FloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        inflateBubble()
        resultOverlay = ResultOverlayView(this, windowManager)
        resultOverlay.onStop = { cancelStreaming() }
        resultOverlay.onRegenerate = { prompt ->
            cancelStreaming()
            launchStream(prompt)
        }
    }

    private fun inflateBubble() {
        bubbleView = LayoutInflater.from(this)
            .inflate(R.layout.view_floating_bubble, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        windowManager.addView(bubbleView, params)

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(bubbleView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onBubbleTapped()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onBubbleTapped() {
        val context = captureContext()
        if (context.isBlank()) {
            resultOverlay.showError("No text captured. Open a chat and try again.")
            resultOverlay.show()
            return
        }
        resultOverlay.show()
        launchStream(context)
    }

    private fun launchStream(userContext: String) {
        val tone = PreferencesManager.getTone(this)
        llmJob = serviceScope.launch {
            val systemPrompt = withContext(Dispatchers.IO) {
                KnowledgeBaseManager.getSystemPrompt(this@FloatingService)
            }
            LlmClient.stream(
                context = this@FloatingService,
                systemPrompt = systemPrompt,
                userMessage = userContext,
                tone = tone,
                onToken = { token -> resultOverlay.appendToken(token) },
                onComplete = { resultOverlay.onStreamComplete() },
                onError = { err -> resultOverlay.showError(err) }
            )
        }
    }

    private fun cancelStreaming() {
        llmJob?.cancel()
        llmJob = null
        resultOverlay.onStreamComplete()
    }

    private fun captureContext(): String {
        val fromA11y = CopilotAccessibilityService.getLastCapturedText()
        if (!fromA11y.isNullOrBlank()) return fromA11y.trim()

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(this).toString().trim()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        runCatching { windowManager.removeView(bubbleView) }
        resultOverlay.dismiss()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChatCopilot AI Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating AI assistant bubble active"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatCopilot AI")
            .setContentText("Tap the bubble over any chat to generate a reply")
            .setSmallIcon(R.drawable.ic_bubble)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
