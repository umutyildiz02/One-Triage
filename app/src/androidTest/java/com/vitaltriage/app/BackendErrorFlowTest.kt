package com.vitaltriage.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.vitaltriage.core.Urgency
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

/** Real Android HTTP to emulator loopback mock only. Never contacts the live backend/OpenAI. */
class BackendErrorFlowTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Before fun before()=IsolatedTestState.reset(completed=true)
    @After fun after()=IsolatedTestState.finishActivities()
    private fun review() {
        compose.onNodeWithTag("write").performClick()
        compose.onNodeWithTag("symptoms").performTextInput("Synthetic adult with irritation lasting two days.")
        compose.onNodeWithTag("editor-review").performScrollTo().performClick()
        compose.onNodeWithTag("analyze").assertIsNotEnabled()
    }
    private fun approve() {
        compose.onNodeWithTag("confirm").performScrollTo().performClick()
        compose.onNodeWithTag("analyze").performScrollTo().performClick()
    }
    @Test fun authCreditBudgetAndInvalidSchemaShowUnknownWithoutDemoFallback() {
        for(code in listOf("AUTH","OPENAI_CREDIT","TEST_BUDGET_LIMIT","INVALID_JSON")) {
            IsolatedTestState.reset(completed=true)
            MockWebServer().use { server ->
                server.enqueue(if(code=="INVALID_JSON")MockResponse().setBody("{}")
                    else MockResponse().setResponseCode(503).setBody("{\"error\":\"$code\"}"))
                server.start()
                UserPreferences(IsolatedTestState.context).apply {demo=false;endpoint=server.url("/").toString().trimEnd('/')}
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    lateinit var vm:TriageViewModel
                    scenario.onActivity {vm=ViewModelProvider(it)[TriageViewModel::class.java];vm.token("synthetic-local-test-token")}
                    review()
                    assertNull("No HTTP before consent",server.takeRequest(200,TimeUnit.MILLISECONDS))
                    approve()
                    compose.waitUntil(10000){vm.state.value.evaluation!=null}
                    compose.onNodeWithTag("risk-UNDETERMINED").assertIsDisplayed()
                    assertTrue(vm.state.value.evaluation!!.failed)
                    assertEquals(Urgency.UNDETERMINED,vm.state.value.evaluation!!.result.urgency)
                    assertEquals("UNAVAILABLE",vm.state.value.evaluation!!.mode)
                    assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))
                    assertEquals(1,server.requestCount)
                    compose.onNodeWithTag("result-summary",useUnmergedTree=true).performClick()
                    compose.onNodeWithText(UiCopy.backendError(vm.state.value.backendFailure!!,vm.state.value.language))
                        .performScrollTo().assertIsDisplayed()
                    IsolatedTestState.screenshot("emulator-error-"+code.lowercase())
                    androidx.test.espresso.Espresso.pressBack()
                    compose.onNodeWithTag("write").performClick()
                    compose.onNodeWithTag("symptoms").performTextReplacement("Edited synthetic fallback")
                    assertNull(vm.state.value.evaluation)
                    assertFalse(vm.state.value.confirmed)
                }
            }
        }
    }
    @Test fun stalledBackendTimesOutOnceAndKeepsEditableInput() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));server.start()
            UserPreferences(IsolatedTestState.context).apply {demo=false;endpoint=server.url("/").toString().trimEnd('/')}
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var vm:TriageViewModel
                scenario.onActivity {vm=ViewModelProvider(it)[TriageViewModel::class.java]}
                review();approve()
                compose.waitUntil(75000){vm.state.value.evaluation!=null}
                assertEquals(1,server.requestCount)
                assertEquals("UNAVAILABLE",vm.state.value.evaluation!!.mode)
                assertTrue(vm.state.value.evaluation!!.failed)
                compose.onNodeWithTag("write").performClick()
                compose.onNodeWithTag("symptoms").assertIsDisplayed()
            }
        }
    }
}
