package com.vitaltriage.app

import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import com.vitaltriage.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class BackendRepositoryTest {
    private val request=TriageRequest("Synthetic irritation for two days","en",listOf("en"),true,false,"LOW")
    private fun withResponse(body:String,block:(String)->Unit) {
        val server=MockWebServer()
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type","application/json"))
        server.start()
        try { block(server.url("/").toString().trimEnd('/')) } finally {server.shutdown()}
    }
    @Test fun cancellationClosesPendingNetworkWorkPromptly() = runBlocking {
        val server=MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        server.start()
        try {
            val job=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                BackendRepository().assess(request,server.url("/").toString(),"")
            }
            assertNotNull(server.takeRequest(5,java.util.concurrent.TimeUnit.SECONDS))
            kotlinx.coroutines.withTimeout(1500) { job.cancel();job.join() }
            assertTrue(job.isCancelled)
        } finally { server.shutdown() }
    }
    @Test fun insufficientInformationKeepsSpecificSafeCode() {
        val server=MockWebServer()
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":"INSUFFICIENT_INFORMATION"}"""))
        server.start()
        try {
            val failure=runCatching { runBlocking { BackendRepository().assess(request,server.url("/").toString(),"") } }.exceptionOrNull()
            assertEquals("INSUFFICIENT_INFORMATION",(failure as BackendFailure).code)
        } finally { server.shutdown() }
    }
    @Test fun validDemoEnvelopeReachesClient() {
        withResponse(Contract.gson.toJson(Demo.evaluate(request))){url->
            val result=runBlocking{BackendRepository().assess(request,url,"")}
            assertEquals(Urgency.LOW,result.result.urgency)
            assertEquals("DEMO",result.mode)
        }
    }
    @Test fun failureEnvelopeCanNeverDisplayLow() {
        val bad=Evaluation(Demo.evaluate(request).result,"DEMO",true)
        withResponse(Contract.gson.toJson(bad)){url->
            val result=runBlocking{BackendRepository().assess(request,url,"")}
            assertEquals(Urgency.UNDETERMINED,result.result.urgency)
            assertNull(result.result.criticality)
            assertTrue(result.failed)
        }
    }
    @Test fun malformedEnvelopeIsRejected() {
        withResponse("""{"mode":"DEMO","failed":"false","result":{}}"""){url->
            val failure=runCatching{runBlocking{BackendRepository().assess(request,url,"")}}
            assertTrue(failure.isFailure)
        }
    }
}
