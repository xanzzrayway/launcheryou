package com.launcheryou

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.view.accessibility.AccessibilityEvent

/** Cukup diaktifkan pengguna: memberi akses membaca sesi media (judul lagu & kontrol putar). */
class MediaListener : NotificationListenerService()

/** Cukup diaktifkan pengguna: memungkinkan kunci layar, buka notifikasi, dan panel Quick Settings sistem. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: LockService? = null
    }
}
