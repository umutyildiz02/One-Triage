package com.vitaltriage.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Real AudioRecord adapter; owns/releases hardware on one worker. Never writes audio to disk. */
class SourceAudioCapture {
    private class Session { @Volatile var cancelled=false; @Volatile var stop=false }
    private val current=AtomicReference<Session?>()
    private val worker=Executors.newSingleThreadExecutor { Thread(it,"vt-source-capture") }
    private val main=Handler(Looper.getMainLooper())
    val running:Boolean get()=current.get()!=null
    @SuppressLint("MissingPermission") // caller checks permission; revocation is handled below
    fun start(settings:CaptureSettings,automatic:Boolean,onLevel:(Float)->Unit,onAudio:(ShortArray)->Unit,onError:(String)->Unit) {
        cancel()
        val session=Session();current.set(session)
        worker.execute {
            var record:AudioRecord?=null
            val segment=VoiceSegmenter(settings,automatic)
            var result:ShortArray?=null
            var error:String?=null
            try {
                if(!session.cancelled) {
                    val min=AudioRecord.getMinBufferSize(VoiceSegmenter.RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_FLOAT)
                    check(min>0)
                    record=AudioRecord(MediaRecorder.AudioSource.MIC,VoiceSegmenter.RATE,AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_FLOAT,maxOf(min,VoiceSegmenter.FRAME*4*4))
                    check(record.state==AudioRecord.STATE_INITIALIZED)
                    record.startRecording()
                    check(record.recordingState==AudioRecord.RECORDSTATE_RECORDING)
                    val floats=FloatArray(VoiceSegmenter.FRAME)
                    val frame=ShortArray(VoiceSegmenter.FRAME)
                    var filled=0;var lastLevel=0L
                    val deadline=SystemClock.elapsedRealtime()+30000
                    while(!session.cancelled && !session.stop && !segment.finished && SystemClock.elapsedRealtime()<deadline) {
                        val n=record.read(floats,0,floats.size-filled,AudioRecord.READ_NON_BLOCKING)
                        check(n>=0)
                        if(n==0){Thread.sleep(5);continue}
                        for(i in 0 until n) frame[filled+i]=(if(floats[i].isFinite()) floats[i].coerceIn(-1f,1f)*32767 else 0f).toInt().toShort()
                        filled+=n
                        if(filled==frame.size) {
                            segment.accept(frame);filled=0
                            val now=SystemClock.elapsedRealtime()
                            if(now-lastLevel>=80) {
                                val level=segment.level;lastLevel=now
                                main.post { if(current.get()===session && !session.cancelled)onLevel(level) }
                            }
                        }
                    }
                    if(filled>0)segment.accept(frame,filled)
                    if(!session.cancelled) {
                        result=segment.finish()
                        if(result!!.isEmpty() || (automatic && !segment.hasVoice)) { result!!.fill(0);result=null;error="speechNoMatch" }
                    }
                    floats.fill(0f);frame.fill(0)
                }
            } catch(_:SecurityException){ error="permission" }
              catch(_:InterruptedException){ session.cancelled=true }
              catch(_:Exception){ error="speechAudio" }
            finally {
                runCatching { record?.stop() };runCatching { record?.release() };segment.clear()
            }
            val audio=result;val failure=error
            main.post {
                if(current.compareAndSet(session,null) && !session.cancelled) {
                    if(audio!=null)onAudio(audio) else if(failure!=null)onError(failure)
                } else audio?.fill(0)
            }
        }
    }
    fun finish(){current.get()?.stop=true}
    fun cancel(){current.getAndSet(null)?.cancelled=true}
    fun destroy(){cancel();worker.shutdownNow()}
}
