// Official attribution assets only; no generated or altered Google branding.
import fs from 'node:fs';
const url='https://docs.cloud.google.com/static/translate/images/google-translate-attribution.zip';
const file='.runtime/google-translate-attribution.zip';
if(!fs.existsSync(file)) {
 const response=await fetch(url,{signal:AbortSignal.timeout(60000)});
 if(!response.ok)throw Error('Attribution archive HTTP '+response.status);
 const parts=[];let size=0;
 for await(const chunk of response.body){size+=chunk.length;if(size>1_000_000)throw Error('Archive too large');parts.push(chunk)}
 fs.writeFileSync(file,Buffer.concat(parts));
}
console.log('Official attribution archive available for inspection.');
