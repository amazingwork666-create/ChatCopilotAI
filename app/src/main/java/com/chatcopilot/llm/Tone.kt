package com.chatcopilot.llm

enum class Tone(val displayName: String, val instruction: String) {
    SALES(
        "Sales & Persuasive",
        "Write in a persuasive, benefit-focused, and engaging sales tone. " +
        "Highlight value, create gentle urgency, and guide the customer toward a decision."
    ),
    CONCISE(
        "Concise & Direct",
        "Write concisely and directly. Maximum 2 sentences. No filler, no pleasantries. " +
        "Get straight to the answer."
    ),
    WARM(
        "Warm & Friendly",
        "Write in a warm, friendly, and empathetic tone. " +
        "Make the customer feel heard and valued. Be conversational and approachable."
    )
}
