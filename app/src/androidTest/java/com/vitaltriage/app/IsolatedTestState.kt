package com.vitaltriage.app

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.rules.ExternalResource
import java.io.File

internal object IsolatedTestState {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    fun reset(completed: Boolean = false) {
        check(context.packageName.endsWith(".uitest")) { "Run with -PisolatedUiTest=true. Never reset the user's installed app." }
        finishActivities()
        check(context.getSharedPreferences(UserPreferences.FILE, Context.MODE_PRIVATE).edit().clear().commit())
        UserPreferences(context).apply { if (completed) { nickname = "Synthetic User"; setupComplete = true } }
    }
    fun finishActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Stage.entries.flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .distinct().filter { it.packageName.endsWith(".uitest") }.forEach(Activity::finish)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(400, 5000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(context.getExternalFilesDir(null), "ui-comparison/full-flow").apply { mkdirs() }
        File(directory, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
class CompletedSetupRule : ExternalResource() {
    override fun before() = IsolatedTestState.reset(completed = true)
    override fun after() = IsolatedTestState.finishActivities()
}
