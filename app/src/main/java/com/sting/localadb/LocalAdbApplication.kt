package com.sting.localadb

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

class LocalAdbApplication : Application() {

    var pairingService: PairingService? = null
        private set

    var isServiceBound = false
        private set

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PairingService.LocalBinder
            pairingService = binder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            pairingService = null
            isServiceBound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    fun bindPairingService() {
        if (!isServiceBound) {
            val intent = Intent(this, PairingService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindPairingService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    companion object {
        lateinit var instance: LocalAdbApplication
            private set
    }
}
