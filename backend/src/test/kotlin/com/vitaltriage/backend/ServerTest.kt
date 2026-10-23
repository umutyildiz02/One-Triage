package com.vitaltriage.backend
import com.vitaltriage.core.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.http.*
class ServerTest {
    private val r=TriageRequest("Synthetic mild irritation for two days","en",listOf("en"),true,false,"LOW")
    private fun call(server:TriageServer,body:String=Contract.gson.toJson(r),token:String=""):HttpResponse<String> =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI("http://127.0.0.1:"+server.port+"/v1/triage"))
            .header("Content-Type","application/json").header("Authorization","Bearer $token")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString())
    @Test fun demoApiReturnsValidatedResult(){
        TriageServer(0).use { s->s.start(); val response=call(s); assertEquals(200,response.statusCode())
            val json=com.google.gson.JsonParser.parseString(response.body()).asJsonObject
            assertEquals("DEMO",json["mode"].asString)
            assertEquals(Urgency.LOW,Contract.parse(json["result"].toString(),"en").urgency) }
    }
    @Test fun invalidRequestReturnsSafeCode(){ TriageServer(0).use { s->s.start(); val res=call(s,"{}");assertEquals(400,res.statusCode());assertFalse(res.body().contains("symptom")) } }
    @Test fun liveRequiresAuthentication(){
        TriageServer(0,"LIVE","a".repeat(32),ModelGateway{Demo.evaluate(it).result}).use { s->s.start();assertEquals(401,call(s).statusCode()) }
    }
    @Test fun upstreamFailureDoesNotLeakDetails(){
        TriageServer(0,"LIVE","a".repeat(32),ModelGateway{error("private synthetic text")}).use { s->s.start()
            val res=call(s,token="a".repeat(32));assertEquals(502,res.statusCode());assertFalse(res.body().contains("private")) }
    }
    @Test fun contradictoryUpstreamLowIsBlocked(){
        TriageServer(0,"LIVE","a".repeat(32),ModelGateway{Demo.evaluate(r).result}).use { s->s.start()
            val res=call(s,Contract.gson.toJson(r.copy(immediateDanger=true)),"a".repeat(32))
            assertEquals(200,res.statusCode());assertTrue(res.body().contains("UNDETERMINED")) }
    }
    @Test fun insufficientLiveInputCannotMasqueradeAsModelResponse(){
        var calls=0
        TriageServer(0,"LIVE","a".repeat(32),ModelGateway{ calls++; Demo.evaluate(it).result }).use { s->s.start()
            val res=call(s,Contract.gson.toJson(r.copy(symptoms="pain")),"a".repeat(32))
            assertEquals(422,res.statusCode());assertEquals(0,calls)
            assertTrue(res.body().contains("INSUFFICIENT_INFORMATION"))
            assertFalse(res.body().contains("\"mode\""));assertFalse(res.body().contains("\"result\""))
        }
    }
    @Test fun refusalAndIncompleteRejected(){
        listOf("""{"status":"incomplete","output":[]}""",
            """{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"no"}]}]}""").forEach {
            assertTrue(runCatching{OpenAiGateway.decodeResponse(it,"en")}.isFailure)
        }
    }
}
