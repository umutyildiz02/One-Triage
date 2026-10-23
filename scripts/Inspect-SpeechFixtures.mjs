// Public CC-BY-4.0 Google FLEURS metadata only; no API key or private content.
for(const config of ['tr_tr','fr_fr','ar_eg']) {
 const url='https://datasets-server.huggingface.co/first-rows?dataset=google%2Ffleurs&config='+config+'&split=validation';
 const response=await fetch(url,{signal:AbortSignal.timeout(30000)});
 if(!response.ok){console.log(JSON.stringify({config,status:response.status,error:(await response.text()).slice(0,600)}));continue}
 const body=await response.json();
 console.log(JSON.stringify({config,rows:body.rows?.slice(0,3).map(({row_idx,row})=>({row:row_idx,id:row.id,samples:row.num_samples,text:row.transcription,audioType:row.audio?.[0]?.type}))}));
}
