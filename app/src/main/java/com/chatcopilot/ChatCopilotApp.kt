package com.chatcopilot

import android.app.Application
import com.chatcopilot.data.AppDatabase

class ChatCopilotApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: ChatCopilotApp
            private set
    }
}
