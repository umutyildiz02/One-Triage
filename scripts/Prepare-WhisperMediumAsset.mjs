// Copies only the already verified optional public model. No network or source-project changes.
import fs from 'node:fs';
import crypto from 'node:crypto';
const source='.runtime/quality-models/ggml-medium-q5_0.bin';
const destination='app/src/main/assets/models/ggml-medium-q5_0.bin';
const size=539212467;
const expected='19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f';
async function verify(file) {
 if(fs.statSync(file).size!==size)throw Error('Unexpected model size');
 const hash=crypto.createHash('sha256');
 for await(const chunk of fs.createReadStream(file))hash.update(chunk);
 if(hash.digest('hex')!==expected)throw Error('Publisher checksum mismatch');
}
await verify(source);
if(fs.existsSync(destination))await verify(destination);
else {
 const stat=fs.statfsSync('app');if(stat.bavail*stat.bsize<2_000_000_000)throw Error('Insufficient copy/build disk headroom');
 fs.mkdirSync('app/src/main/assets/models',{recursive:true});
 fs.copyFileSync(source,destination,fs.constants.COPYFILE_EXCL);
 await verify(destination);
}
console.log('Verified optional multilingual medium model prepared; no model downloaded, no existing file overwritten.');
