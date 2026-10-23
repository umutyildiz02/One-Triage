// Conservative allowance before explicit Android system TTS voice setup; no download is started here.
import fs from 'node:fs';
const path='.runtime/model-downloads.json';
const ledger=JSON.parse(fs.readFileSync(path,'utf8'));
if(ledger.version!==1 || !Array.isArray(ledger.attempts) || ledger.attempts.some(x=>!Number.isSafeInteger(x.reservedBytes)||x.reservedBytes<0))throw Error('Invalid model history; do not reset');
const key='google-tts-tr-fr-ar-emulator-5554';
if(ledger.attempts.some(x=>x.name===key))console.log('Existing TTS model allowance retained.');
else {
 const reservedBytes=500_000_000;
 const total=ledger.attempts.reduce((sum,x)=>sum+x.reservedBytes,0)+reservedBytes;
 if(total>5_000_000_000)throw Error('Authorized aggregate model limit');
 ledger.attempts.push({name:key,time:new Date().toISOString(),reservedBytes,status:'SYSTEM_TTS_RESERVATION',license:'Google Speech Services terms',note:'Explicit system UI setup for Turkish/French/Arabic voices; conservative500MB combined allowance, not measured transport bytes. No paid API, extraction or automatic retries.'});
 fs.writeFileSync(path+'.tmp',JSON.stringify(ledger,null,2)+'\n');fs.renameSync(path+'.tmp',path);
 console.log(JSON.stringify({totalReservedModelBytes:total,paidOpenAiRequests:0}));
}
