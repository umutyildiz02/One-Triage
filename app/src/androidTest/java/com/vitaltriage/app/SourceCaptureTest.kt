package com.vitaltriage.app
import android.Manifest
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.vitaltriage.core.Contract
import org.junit.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SourceCaptureTest {
    @get:Rule val permission=GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    @Before fun before()=IsolatedTestState.reset()
    @After fun after()=IsolatedTestState.finishActivities()
    @Test fun actualAudioRecordProducesBoundedPcmAndCancelSuppressesDelivery() {
        val context=IsolatedTestState.context
        val prefs=UserPreferences(context);prefs.nickname="Capture probe";prefs.setupComplete=true
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val capture=SourceAudioCapture()
        val delivered=AtomicInteger()
        val level=CountDownLatch(1);val audio=CountDownLatch(1)
        var samples=0;var peak=0;var failure=""
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                instrumentation.runOnMainSync {
                    capture.start(CaptureSettings(),false,{level.countDown()},{
                        delivered.incrementAndGet();samples=it.size
                        peak=it.maxOfOrNull { value->kotlin.math.abs(value.toInt()) } ?: 0
                        it.fill(0);audio.countDown()
                    },{failure=it;audio.countDown();level.countDown()})
                }
                Assert.assertTrue("No microphone frames",level.await(10,TimeUnit.SECONDS))
                SystemClock.sleep(250)
                instrumentation.runOnMainSync {capture.finish()}
                Assert.assertTrue("Capture did not finish",audio.await(5,TimeUnit.SECONDS))
                Assert.assertEquals("",failure);Assert.assertTrue(samples in 320..VoiceSegmenter.MAX_SAMPLES)
                val restarted=CountDownLatch(1)
                instrumentation.runOnMainSync {
                    capture.start(CaptureSettings(),false,{restarted.countDown()},{it.fill(0);delivered.incrementAndGet()},{failure=it;restarted.countDown()})
                }
                Assert.assertTrue("Microphone not released for restart",restarted.await(5,TimeUnit.SECONDS))
                instrumentation.runOnMainSync {capture.cancel()}
                SystemClock.sleep(400)
                Assert.assertEquals("",failure);Assert.assertEquals(1,delivered.get())
                java.io.File(context.getExternalFilesDir(null),"source-capture.json").writeText(Contract.gson.toJson(mapOf(
                    "hardwareAudioRecord" to true,"sampleRate" to 16000,"samples" to samples,"peakAbsolute" to peak,
                    "cancelSuppressedDelivery" to true,"transcriptionProven" to false,"paidCalls" to 0)))
            } finally {instrumentation.runOnMainSync {capture.destroy()}}
        }
    }
    @Test fun localModelChoiceAndTranslationPreferenceSurviveRecreation() {
        val prefs=UserPreferences(IsolatedTestState.context)
        prefs.nickname="Model settings";prefs.setupComplete=true;prefs.offlineWhisper=true
        ActivityScenario.launch(LegacySettingsActivity::class.java).use { activity ->
            onView(withText("Offline speech model")).perform(scrollTo(),click())
            onView(withText(WhisperModel.MEDIUM.label)).perform(click())
            Assert.assertEquals(WhisperModel.MEDIUM,prefs.whisperModel)
            onView(withText(R.string.vv_translation_title)).perform(scrollTo(),click())
            Assert.assertTrue(prefs.englishVoiceTranslation)
            activity.recreate()
            Assert.assertEquals(WhisperModel.MEDIUM,UserPreferences(IsolatedTestState.context).whisperModel)
            Assert.assertTrue(UserPreferences(IsolatedTestState.context).englishVoiceTranslation)
            onView(withText("Offline speech model")).perform(scrollTo(),click())
            onView(withText(WhisperModel.SMALL.label)).perform(click())
            Assert.assertEquals(WhisperModel.SMALL,prefs.whisperModel)
        }
    }
    @Test fun sourceControlsPersistAndResetUsingOriginalRows() {
        val prefs=UserPreferences(IsolatedTestState.context);prefs.nickname="Settings probe";prefs.setupComplete=true
        ActivityScenario.launch(LegacySettingsActivity::class.java).use {
            onView(withText("Source recorder / VAD (experimental)")).perform(scrollTo(),click())
            Assert.assertTrue(prefs.sourceCapture)
            onView(withContentDescription("Microphone sensitivity")).perform(scrollTo(),swipeRight())
            Assert.assertTrue("Slider must update actual capture configuration",prefs.captureSettings().sensitivity>50)
            onView(withContentDescription("Reset Microphone sensitivity")).perform(scrollTo(),click())
            Assert.assertEquals(50,prefs.captureSettings().sensitivity)
            IsolatedTestState.screenshot("20-source-microphone-setting")
            onView(withContentDescription("End of voice timeout")).perform(scrollTo(),swipeRight())
            Assert.assertTrue(prefs.silenceMs>1300)
            IsolatedTestState.screenshot("21-source-advanced-settings")
            it.recreate()
            Assert.assertTrue(UserPreferences(IsolatedTestState.context).sourceCapture)
            Assert.assertEquals(prefs.silenceMs,UserPreferences(IsolatedTestState.context).captureSettings().silenceMs)
            onView(withText("Settings")).check(matches(isDisplayed()))
        }
    }
}
