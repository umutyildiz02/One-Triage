// Local-only synthetic PCM -> Android Emulator microphone, using documented gRPC.
// No transcript injection, no OpenAI call, no dependency installation.
// Temporary public JWK only; private signing key/token remain in memory and expire.
// https://android.googlesource.com/platform/external/qemu/+/686efa16baf59d776cadc3f975d12570fe44bbb9/android/android-grpc/docs/
import fs from 'node:fs';
import path from 'node:path';
import http2 from 'node:http2';
import crypto from 'node:crypto';
import {setTimeout as sleep} from 'node:timers/promises';
const [discovery, pcmPath, delayArg='0']=process.argv.slice(2);
if(!discovery || !pcmPath)throw Error('Usage: node scripts/Inject-EmulatorAudio.mjs discovery.ini synthetic-16khz-mono-s16le.pcm [delaySeconds]');
const entries=Object.fromEntries(fs.readFileSync(discovery,'utf8').split(/\r?\n/).filter(l=>l.includes('=')).map(l=>{const i=l.indexOf('=');return [l.slice(0,i).trim(),l.slice(i+1).trim()]}));
const port=Number(entries['grpc.port']);
if(!Number.isInteger(port)||port<1024||port>65535||!entries['grpc.jwks'])throw Error('Authenticated emulator discovery unavailable');
const pcm=fs.readFileSync(pcmPath);
if(pcm.length<3200||pcm.length>29*32000||pcm.length%2)throw Error('Expected bounded 16kHz mono PCM16 fixture');
const delay=Number(delayArg);if(!Number.isFinite(delay)||delay<0||delay>30)throw Error('Invalid delay');
const method='/android.emulation.control.EmulatorController/injectAudio';
const kid='vt-synthetic-'+crypto.randomUUID();
const {publicKey,privateKey}=crypto.generateKeyPairSync('rsa',{modulusLength:2048});
const publicJwk={...publicKey.export({format:'jwk'}),kid,alg:'RS256',use:'sig'};
const jwkFile=path.join(entries['grpc.jwks'],kid+'.jwk');
const encode=v=>Buffer.from(JSON.stringify(v)).toString('base64url');
const now=Math.floor(Date.now()/1000);
const unsigned=encode({alg:'RS256',kid})+'.'+encode({iss:'vitaltriage-local-audio-test',aud:[method],iat:now-5,exp:now+180});
const token=unsigned+'.'+crypto.sign('RSA-SHA256',Buffer.from(unsigned),privateKey).toString('base64url');
let client;
function varint(n){const a=[];while(n>127){a.push((n&127)|128);n=Math.floor(n/128)}a.push(n);return Buffer.from(a)}
function field(tag,data){return Buffer.concat([varint(tag*8+2),varint(data.length),data])}
function frame(payload){const h=Buffer.alloc(5);h.writeUInt32BE(payload.length,1);return Buffer.concat([h,payload])}
try {
    fs.writeFileSync(jwkFile,JSON.stringify({keys:[publicJwk]}),{flag:'wx'});
    await sleep(1500);await sleep(delay*1000);
    client=http2.connect('http://127.0.0.1:'+port);
    client.on('error',()=>{});
    const request=client.request({':method':'POST',':path':method,'content-type':'application/grpc','te':'trailers','authorization':'Bearer '+token});
    let status;let httpStatus;let grpcMessage;
    request.on('response',headers=>{httpStatus=headers[':status'];if(headers['grpc-status']!==undefined)status=String(headers['grpc-status']);grpcMessage=headers['grpc-message']});
    request.on('trailers',headers=>{status=String(headers['grpc-status']);grpcMessage=headers['grpc-message']});
    request.on('data',()=>{});
    const completed=new Promise((resolve,reject)=>{request.on('end',resolve);request.on('error',reject)});
    request.setTimeout(45000,()=>request.destroy(Error('Local audio stream timeout')));
    const format=Buffer.concat([Buffer.from([8]),varint(16000),Buffer.from([24,1,32,1])]);
    const audio=Buffer.concat([Buffer.alloc(16000),pcm,Buffer.alloc(64000)]);
    console.log('Synthetic waveform streaming to authenticated loopback emulator microphone; no API request.');
    for(let offset=0;offset<audio.length;offset+=640){
        if(request.destroyed)break;
        const packet=Buffer.concat([field(1,format),field(3,audio.subarray(offset,offset+640))]);
        request.write(frame(packet));await sleep(20);
    }
    request.end();await completed;
    audio.fill(0);
    console.log(JSON.stringify({httpStatus,grpcStatus:status,grpcMessage:grpcMessage ? decodeURIComponent(grpcMessage).slice(0,300) : undefined,synthetic:true,pcmBytes:pcm.length,paidRequests:0}));
    if(status!=='0')process.exitCode=1;
} catch(error) {
    console.error('Local emulator audio injection failed: '+error.name);
    process.exitCode=1;
} finally {
    pcm.fill(0);client?.close();
    if(fs.existsSync(jwkFile))fs.unlinkSync(jwkFile);
}
