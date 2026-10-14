package com.tonewerk.eq.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tonewerk.eq.MainActivity

/** Keeps a foreground service alive holding effects on the global output mix (session 0). */
class GlobalFxService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(CH, "Global EQ", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Tonewerk — Global EQ active")
            .setContentText("Equalizing audio from every app")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(1, n)
        FxEngine.attach(0, isGlobal = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { FxEngine.release(); super.onDestroy() }

    companion object {
        private const val CH = "global_fx"
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, GlobalFxService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, GlobalFxService::class.java))
    }
}
