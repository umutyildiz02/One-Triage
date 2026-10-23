package com.vitaltriage.backend

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vitaltriage.core.Contract
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset

/**
 * Optional production access policy behind a loopback TLS reverse proxy.
 * Each provisioned user has a different revocable opaque credential; only SHA256 is stored.
 * Usage counters contain opaque IDs and counts, never symptoms, audio or credentials.
 * This does not replace the global durable LiveBudget or establish clinical/hosting readiness.
 */
class ProductionAccess(private val usersFile:Path, private val usageFile:Path,
                       private val now:()->Instant={Instant.now()}) {
    data class Principal(val id:String,val minuteLimit:Int,val dayLimit:Int)
    init { require(usersFile.toAbsolutePath()!=usageFile.toAbsolutePath()); users() }
    private data class User(val principal:Principal,val digest:ByteArray,val expires:Instant,val enabled:Boolean)
    private fun readBounded(path:Path):String=Files.newInputStream(path).use {
        val bytes=it.readNBytes(262145)
        check(bytes.size<=262144) { "Access configuration exceeds limit" }
        bytes.toString(Charsets.UTF_8)
    }
    private fun users():List<User> {
        val root=JsonParser.parseString(readBounded(usersFile)).asJsonObject
        require(root.keySet()==setOf("version","users") && root["version"].asInt==1)
        val rows=root["users"].asJsonArray
        require(rows.size() in 1..256)
        val result=rows.map { item ->
            val row=item.asJsonObject
            require(row.keySet()==setOf("id","tokenSha256","expiresAt","enabled","requestsPerMinute","requestsPerDay"))
            val id=row["id"].asString
            val hash=row["tokenSha256"].asString
            val minute=row["requestsPerMinute"].asInt
            val day=row["requestsPerDay"].asInt
            require(id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && hash.matches(Regex("[a-f0-9]{64}")))
            require(minute in 1..30 && day in 1..1000)
            require(row["enabled"].asJsonPrimitive.isBoolean)
            User(Principal(id,minute,day),hash.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                Instant.parse(row["expiresAt"].asString),row["enabled"].asBoolean)
        }
        require(result.map { it.principal.id }.distinct().size==result.size)
        require(result.map { it.digest.toList() }.distinct().size==result.size)
        return result
    }
    fun authenticate(header:String?):Principal? {
        if(header==null || !header.startsWith("Bearer ") || header.length !in 39..263)return null
        val token=header.removePrefix("Bearer ")
        if(!token.matches(Regex("[A-Za-z0-9_-]{32,256}")))return null
        val digest=MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII))
        val instant=now()
        // Reload to honor revocation/expiry without a server restart.
        return users().firstOrNull { MessageDigest.isEqual(it.digest,digest) && it.enabled && instant.isBefore(it.expires) }?.principal
    }
    /** Reserve before provider work. Failed attempts also consume quota; no retry/refund loop. */
    @Synchronized fun reserve(principal:Principal):Boolean {
        val instant=now()
        val day=instant.atOffset(ZoneOffset.UTC).toLocalDate().toString()
        val seconds=instant.epochSecond
        Files.createDirectories(usageFile.toAbsolutePath().parent)
        FileChannel.open(usageFile.resolveSibling(usageFile.fileName.toString()+".lock"),CREATE,WRITE).use { lock ->
            lock.lock().use {
                val root=if(Files.exists(usageFile))JsonParser.parseString(readBounded(usageFile)).asJsonObject else JsonObject().apply {
                    addProperty("version",1);add("users",JsonObject())
                }
                require(root.keySet()==setOf("version","users") && root["version"].asInt==1)
                val counters=root.getAsJsonObject("users")
                require(counters.size()<=256)
                val previous=counters.getAsJsonObject(principal.id)
                val minuteStart=previous?.get("minuteStart")?.asLong ?: seconds
                val previousDay=previous?.get("day")?.asString ?: day
                if(seconds<minuteStart || day<previousDay)return false // clock rollback must not replenish quota
                val minuteCount=if(seconds-minuteStart<60)previous?.get("minuteCount")?.asInt ?: 0 else 0
                val dayCount=if(day==previousDay)previous?.get("dayCount")?.asInt ?: 0 else 0
                check(minuteCount>=0 && dayCount>=0)
                if(minuteCount>=principal.minuteLimit || dayCount>=principal.dayLimit)return false
                if(previous==null && counters.size()>=256)return false
                counters.add(principal.id,JsonObject().apply {
                    addProperty("minuteStart",if(seconds-minuteStart<60)minuteStart else seconds)
                    addProperty("minuteCount",minuteCount+1);addProperty("day",day);addProperty("dayCount",dayCount+1)
                })
                val temp=usageFile.resolveSibling(usageFile.fileName.toString()+".tmp")
                FileChannel.open(temp,CREATE,TRUNCATE_EXISTING,WRITE).use { channel ->
                    val buffer=java.nio.ByteBuffer.wrap(Contract.gson.toJson(root).toByteArray(Charsets.UTF_8))
                    while(buffer.hasRemaining())channel.write(buffer)
                    channel.force(true)
                }
                Files.move(temp,usageFile,ATOMIC_MOVE,REPLACE_EXISTING)
                return true
            }
        }
    }
}
