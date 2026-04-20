package com.example.nozokima

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class DownloadNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "model_download"
        const val NOTIFICATION_ID = 1001
    }

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AIモデルダウンロード",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Gemmaモデルのダウンロード進捗を表示します"
            setSound(null, null)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showProgress(progress: Int) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AIモデルをダウンロード中")
            .setContentText("Gemma-4-E4B-it  $progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setSilent(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // 通知権限なし — 無視して続行
        }
    }

    fun showComplete() {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("AIモデルの準備完了")
            .setContentText("Gemma-4-E4B-it のダウンロードが完了しました")
            .setAutoCancel(true)
            .setSilent(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) { }
    }

    fun showError() {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("ダウンロード失敗")
            .setContentText("AIモデルのダウンロードに失敗しました")
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) { }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

