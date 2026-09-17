package com.chatcopilot.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.*
import com.chatcopilot.R
import com.chatcopilot.data.SnippetRepository

class ResultOverlayView(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val view: View = LayoutInflater.from(context)
        .inflate(R.layout.view_result_overlay, null)

    private val tvResult: TextView = view.findViewById(R.id.tvResult)
    private val btnStop: ImageButton = view.findViewById(R.id.btnStop)
    private val btnCopy: Button = view.findViewById(R.id.btnCopy)
    private val btnShorten: Button = view.findViewById(R.id.btnShorten)
    private val btnRegenerate: Button = view.findViewById(R.id.btnRegenerate)
    private val btnSave: Button = view.findViewById(R.id.btnSave)
    private val progressBar: ProgressBar = view.findViewById(R.id.progressBar)

    private val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = 80
    }

    private var isAdded = false
    private val buffer = StringBuilder()
    private var lastUserContext = ""

    var onStop: (() -> Unit)? = null
    var onRegenerate: ((String) -> Unit)? = null

    init {
        view.layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        btnStop.setOnClickListener {
            onStop?.invoke()
        }

        btnCopy.setOnClickListener {
            val text = buffer.toString()
            if (text.isNotBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AI Reply", text))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        btnShorten.setOnClickListener {
            if (buffer.isNotBlank()) {
                buffer.clear()
                tvResult.text = ""
                setActionButtonsEnabled(false)
                progressBar.visibility = View.VISIBLE
                onRegenerate?.invoke(
                    "Shorten this reply to 1-2 sentences maximum. Keep the same tone and language.\n\nOriginal reply:\n${tvResult.text}"
                )
            }
        }

        btnRegenerate.setOnClickListener {
            buffer.clear()
            tvResult.text = ""
            setActionButtonsEnabled(false)
            progressBar.visibility = View.VISIBLE
            onRegenerate?.invoke(lastUserContext)
        }

        btnSave.setOnClickListener {
            val text = buffer.toString()
            if (text.isNotBlank()) {
                SnippetRepository(context).save(text)
                Toast.makeText(context, "Snippet saved", Toast.LENGTH_SHORT).show()
            }
        }

        // Dismiss on outside touch
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                dismiss()
                true
            } else false
        }
    }

    fun show(userContext: String = "") {
        if (userContext.isNotBlank()) lastUserContext = userContext
        buffer.clear()
        tvResult.text = ""
        progressBar.visibility = View.VISIBLE
        setActionButtonsEnabled(false)
        if (!isAdded) {
            windowManager.addView(view, params)
            isAdded = true
        }
    }

    fun appendToken(token: String) {
        buffer.append(token)
        view.post { tvResult.text = buffer.toString() }
    }

    fun onStreamComplete() {
        view.post {
            progressBar.visibility = View.GONE
            setActionButtonsEnabled(true)
        }
    }

    fun showError(message: String) {
        view.post {
            tvResult.text = "⚠ $message"
            progressBar.visibility = View.GONE
            setActionButtonsEnabled(false)
            btnStop.isEnabled = false
        }
    }

    fun dismiss() {
        if (isAdded) {
            runCatching { windowManager.removeView(view) }
            isAdded = false
        }
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        listOf(btnCopy, btnShorten, btnRegenerate, btnSave).forEach {
            it.post { it.isEnabled = enabled }
        }
    }
}
