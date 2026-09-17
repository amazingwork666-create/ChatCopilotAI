package com.chatcopilot.llm

import android.content.Context
import com.chatcopilot.data.AppDatabase

object KnowledgeBaseManager {

    suspend fun getSystemPrompt(context: Context): String {
        val entries = AppDatabase.getInstance(context).knowledgeDao().getAll()

        if (entries.isEmpty()) {
            return """
                You are a helpful business assistant.
                No specific knowledge base has been configured yet.
                For any product, pricing, or service questions, say:
                "I'll need to check on that — let me connect you with a team member."
            """.trimIndent()
        }

        val grouped = entries.groupBy { it.category }

        val kbBlock = grouped.entries.joinToString("\n\n") { (category, items) ->
            "### $category\n" + items.joinToString("\n") { entry ->
                "- **${entry.title}**: ${entry.content}"
            }
        }

        return """
            You are an AI customer assistant. Your ENTIRE knowledge comes only from below.
            
            ===== KNOWLEDGE BASE START =====
            $kbBlock
            ===== KNOWLEDGE BASE END =====
            
            Answer ONLY from the above. For anything else, defer to a human.
        """.trimIndent()
    }
}
