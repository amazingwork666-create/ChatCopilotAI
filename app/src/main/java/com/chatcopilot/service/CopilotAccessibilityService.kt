package com.chatcopilot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CopilotAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var lastCapturedText: String? = null

        fun getLastCapturedText(): String? = lastCapturedText
        fun clearCapturedText() { lastCapturedText = null }
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        traverseNode(root, texts)
        if (texts.isNotEmpty()) {
            lastCapturedText = texts.takeLast(30).joinToString("\n")
        }
        root.recycle()
    }

    private fun traverseNode(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && text.length > 1) {
            texts.add(text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, texts)
            child.recycle()
        }
    }

    override fun onInterrupt() {
        lastCapturedText = null
    }
}
