package com.chronos.app.ui.screens.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface FocusClock {
    fun wallMillis(): Long
    fun elapsedMillis(): Long
    fun bootCount(): Int
}

@Singleton
class AndroidFocusClock @Inject constructor(@ApplicationContext private val context: Context) : FocusClock {
    override fun wallMillis() = System.currentTimeMillis()
    override fun elapsedMillis() = SystemClock.elapsedRealtime()
    override fun bootCount() = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) }.getOrDefault(0)
}
