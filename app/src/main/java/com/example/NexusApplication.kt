package com.example

import android.app.Application
import android.util.Log
import com.example.aza.execution.SystemNotificationHelper
import com.example.aza.security.SecureCredentialStore

class NexusApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            SecureCredentialStore.init(this)
        } catch (e: Throwable) {
            Log.e("NexusApplication", "Failed to initialize SecureCredentialStore", e)
        }

        try {
            SystemNotificationHelper.init(this)
        } catch (e: Throwable) {
            Log.e("NexusApplication", "Failed to initialize SystemNotificationHelper", e)
        }
    }
}
