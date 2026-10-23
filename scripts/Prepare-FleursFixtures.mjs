// Google FLEURS, CC-BY-4.0. Public non-clinical speech fixtures only.
// Download three individual clips, never the multi-GB dataset. No credentials.
import fs from 'node:fs';
import crypto from 'node:crypto';
const out='app/src/androidTest/assets/speech-fixtures';
fs.mkdirSync(out,{recursive:true});
const selection=[{language:'tr',config:'tr_tr',split:'validation',index:0},{language:'fr',config:'fr_fr',split:'validation',index:1},{language:'ar',config:'ar_eg',split:'test',index:0}];
const report=[];
for(const item of selection) {
 const response=await fetch('https://datasets-server.huggingface.co/first-rows?dataset=google%2Ffleurs&config='+item.config+'&split='+item.split,{signal:AbortSignal.timeout(30000)});
 if(!response.ok)throw Error('Public dataset metadata unavailable: '+response.status);
 const data=await response.json();const row=data.rows.find(r=>r.row_idx===item.index).row;
 const url=row.audio[0].src;const host=new URL(url).hostname;
 if(host!=='datasets-server.huggingface.co')throw Error('Unexpected audio host');
 const download=await fetch(url,{signal:AbortSignal.timeout(30000)});
 if(!download.ok)throw Error('Public audio unavailable: '+download.status);
 if(Number(download.headers.get('content-length'))>2000000)throw Error('Unexpected fixture size');
 const chunks=[];let size=0;
 for await(const chunk of download.body){size+=chunk.length;if(size>2000000)throw Error('Fixture size limit');chunks.push(chunk)}
 const wav=Buffer.concat(chunks);
 if(wav.toString('ascii',0,4)!=='RIFF'||wav.toString('ascii',8,12)!=='WAVE')throw Error('Unsupported fixture format');
 let fmt,pcm;
 for(let p=12;p+8<=wav.length;){const n=wav.readUInt32LE(p+4);const id=wav.toString('ascii',p,p+4);if(p+8+n>wav.length)throw Error('Truncated WAV');if(id==='fmt ')fmt=wav.subarray(p+8,p+8+n);if(id==='data')pcm=wav.subarray(p+8,p+8+n);p+=8+n+(n%2)}
 if(!fmt||!pcm)throw Error('WAV chunks missing');
 const codec=fmt.readUInt16LE(0),channels=fmt.readUInt16LE(2),rate=fmt.readUInt32LE(4),bits=fmt.readUInt16LE(14);
 if(channels!==1||rate!==16000||!((codec===1&&bits===16)||(codec===3&&bits===32)))throw Error('Unsupported WAV parameters: '+[codec,channels,rate,bits]);
 const count=pcm.length/(bits/8);if(count>29*16000)throw Error('Fixture too long');
 const audio=Buffer.alloc(count*2);
 for(let i=0;i<count;i++){const value=codec===1?pcm.readInt16LE(i*2):Math.max(-1,Math.min(1,pcm.readFloatLE(i*4)))*32767;audio.writeInt16LE(Math.round(value),i*2)}
 fs.writeFileSync(out+'/'+item.language+'.pcm',audio);
 report.push({...item,dataset:'google/fleurs',license:'CC-BY-4.0',source:'https://huggingface.co/datasets/google/fleurs',id:row.id,reference:row.transcription,sampleRate:16000,samples:count,wavSha256:crypto.createHash('sha256').update(wav).digest('hex'),pcmSha256:crypto.createHash('sha256').update(audio).digest('hex'),modification:'Decoded mono 16kHz WAV to PCM16; no text injection',downloadBytes:size});
 console.log(JSON.stringify({language:item.language,samples:count,downloadBytes:size}));
}
fs.writeFileSync(out+'/manifest.json',JSON.stringify(report,null,2)+'\n');
console.log('Prepared licensed public speech fixtures; zero OpenAI calls.');
