// Conservative model-size reservation for explicit SDK preparation in the two authorized packages.
import fs from 'node:fs';
const name=process.argv[2];
if(!['com.vitaltriage.app','com.vitaltriage.app.uitest'].includes(name))throw Error('Unknown test package');
const path='.runtime/model-downloads.json';
const ledger=JSON.parse(fs.readFileSync(path,'utf8'));
if(ledger.version!==1 || !Array.isArray(ledger.attempts) || ledger.attempts.some(x=>!Number.isSafeInteger(x.reservedBytes)||x.reservedBytes<0))throw Error('Invalid history; do not reset');
const key='mlkit-tr-fr-ar-'+name;
if(ledger.attempts.some(x=>x.name===key))console.log('Existing SDK model reservation retained.');
else {
 const reservedBytes=300_000_000;
 const total=ledger.attempts.reduce((sum,x)=>sum+x.reservedBytes,0)+reservedBytes;
 if(total>5_000_000_000)throw Error('Authorized model limit');
 ledger.attempts.push({name:key,time:new Date().toISOString(),reservedBytes,status:'SDK_RESERVATION',license:'Google ML Kit terms',note:'Three approximately30MB models;100MB each conservative size allowance, not measured transport bytes. SDK-managed verified distribution, no extracted models.'});
 fs.writeFileSync(path+'.tmp',JSON.stringify(ledger,null,2)+'\n');fs.renameSync(path+'.tmp',path);
 console.log(JSON.stringify({package:name,totalReservedModelBytes:total,paidOpenAiRequests:0}));
}
