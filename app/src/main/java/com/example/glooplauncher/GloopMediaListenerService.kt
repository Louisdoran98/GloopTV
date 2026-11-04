package com.example.glooplauncher

import android.app.Service
import android.content.Intent
import android.os.IBinder

class GloopMediaListenerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
