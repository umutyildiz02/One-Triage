// Optional authorized quality experiment. Public MIT model, never app credentials/audio.
import fs from 'node:fs';
import os from 'node:os';
import crypto from 'node:crypto';
import {once} from 'node:events';
const directory='.runtime/quality-models';fs.mkdirSync(directory,{recursive:true});
const candidates={
 'small-q8':{name:'ggml-small-q8_0.bin',expectedBytes:264464607,expectedHash:'49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f',revision:'0b364b566045a405be7225ee1e415a073e04da77'},
 'medium-q5':{name:'ggml-medium-q5_0.bin',expectedBytes:539212467,expectedHash:'19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f',revision:'f281eb45af861ab5e5297d23694b7d46e090c02c'}
};
const candidate=candidates[process.argv[2]??'small-q8'];
if(!candidate)throw Error('Unknown pinned quality candidate');
const {name,expectedBytes,expectedHash,revision}=candidate;
const url=`https://huggingface.co/ggerganov/whisper.cpp/resolve/${revision}/${name}`;
const file=directory+'/'+name;
async function hash(path){const h=crypto.createHash('sha256');for await(const b of fs.createReadStream(path))h.update(b);return h.digest('hex')}
if(fs.existsSync(file)) {
 if(fs.statSync(file).size!==expectedBytes || await hash(file)!==expectedHash)throw Error('Existing quality model differs; not overwriting');
 console.log('Existing verified quality model reused; no download.');
} else {
 const disk=fs.statfsSync(directory);const free=disk.bavail*disk.bsize;
 console.log(JSON.stringify({freeDiskBytes:free,freeRamBytes:os.freemem(),modelBytes:expectedBytes}));
 if(free<3_000_000_000 || os.freemem()<2_000_000_000)throw Error('Insufficient resource headroom; defer quality experiment');
 const ledgerPath='.runtime/model-downloads.json';
 const ledger=fs.existsSync(ledgerPath)?JSON.parse(fs.readFileSync(ledgerPath,'utf8')):{version:1,attempts:[]};
 if(ledger.version!==1 || !Array.isArray(ledger.attempts))throw Error('Invalid download history; do not reset');
 const spent=ledger.attempts.reduce((sum,a)=>sum+a.reservedBytes,0);
 if(!Number.isFinite(spent)||spent<0||spent+expectedBytes>5_000_000_000)throw Error('Aggregate authorized model-download limit');
 const entry={name,revision,time:new Date().toISOString(),reservedBytes:expectedBytes,status:'RESERVED',license:'MIT',sha256:expectedHash};
 ledger.attempts.push(entry);
 function save(){fs.writeFileSync(ledgerPath+'.tmp',JSON.stringify(ledger,null,2)+'\n');fs.renameSync(ledgerPath+'.tmp',ledgerPath)}
 save();
 const partial=file+'.partial';let count=0;
 try {
  const response=await fetch(url,{signal:AbortSignal.timeout(600000)});
  if(!response.ok)throw Error('Public model HTTP '+response.status);
  const output=fs.createWriteStream(partial,{flags:'wx'});
  try { for await(const chunk of response.body){count+=chunk.length;if(count>expectedBytes)throw Error('Model exceeds verified size');if(!output.write(chunk))await once(output,'drain')} output.end();await once(output,'finish'); }
  catch(error){output.destroy();throw error}
  if(count!==expectedBytes || await hash(partial)!==expectedHash)throw Error('Published model size/SHA256 mismatch');
  fs.renameSync(partial,file);entry.status='VERIFIED';entry.actualBytes=count;save();
  console.log(JSON.stringify({model:name,bytes:count,sha256:expectedHash,totalReservedModelDownloadBytes:spent+expectedBytes,paidOpenAiRequests:0}));
 } catch(error) {
  entry.status='FAILED';entry.actualBytes=count;save();
  if(fs.existsSync(partial))fs.unlinkSync(partial);
  throw error;
 }
}
