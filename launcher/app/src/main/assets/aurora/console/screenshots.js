'use strict';
(function(){
 var api=window.AuroraAPI,previewUrl='',busy=false;
 function el(id){return document.getElementById(id);}
 function status(s){el('capture-status').textContent=s;}
 async function requestFile(url){var r=await fetch(url,{credentials:'same-origin',cache:'no-store'});if(!r.ok){var detail;try{detail=await r.json();}catch(ignored){}if(r.status===401)throw Error('登录已过期，请重新登录控制台');throw Error(detail&&detail.error&&detail.error.message||'读取电视画面失败（'+r.status+'），请稍后重试');}if((r.headers.get('Content-Type')||'').indexOf('image/')!==0)throw Error('未收到截图，请重新登录控制台后重试');return r.blob();}
 async function gallery(){
  var items=await api.request('system/screenshots'),box=el('capture-gallery');box.replaceChildren();
  if(!items.length){box.textContent='还没有保存截图。';return;}
  items.forEach(function(v){
   var card=document.createElement('article');card.className='theme-card';
   var img=document.createElement('img');img.src=v.url;img.alt='截图';img.loading='lazy';img.style.cssText='width:100%;aspect-ratio:16/9;object-fit:contain;border-radius:10px';
   var title=document.createElement('p');title.textContent=new Date(v.createdAt).toLocaleString()+' · '+v.width+'×'+v.height+(v.upscaled?'（由 '+v.sourceWidth+'×'+v.sourceHeight+' 放大）':'（原生）');
   var buttons=document.createElement('div');buttons.className='actions';
   var download=document.createElement('a');download.href=v.url+'?download=1';download.download='Aurora-'+v.createdAt+'.png';download.textContent='保存到电脑';
   var remove=document.createElement('button');remove.className='secondary';remove.textContent='删除';remove.onclick=function(){if(confirm('删除电视上这张截图？已下载到电脑的文件不受影响。'))run(async function(){await api.request('system/screenshots/delete',{id:v.id});await gallery();status('已删除电视上的截图');});};
   buttons.append(download,remove);card.append(img,title,buttons);box.append(card);
  });
 }
 async function run(fn){if(busy)return;busy=true;el('capture-save').disabled=true;el('capture-refresh').disabled=true;try{await fn();}catch(e){status(e.message);}finally{busy=false;el('capture-save').disabled=false;el('capture-refresh').disabled=false;}}
 el('capture-refresh').onclick=function(){run(async function(){status('正在读取电视画面…');var blob=await requestFile('/api/v1/system/screenshot');if(previewUrl)URL.revokeObjectURL(previewUrl);previewUrl=URL.createObjectURL(blob);el('capture-preview').src=previewUrl;el('capture-preview').hidden=false;status('预览已更新 · '+new Date().toLocaleTimeString());});};
 el('capture-save').onclick=function(){run(async function(){status('正在截图并保存到电视…');var v=await api.request('system/screenshots/capture',{resolution:el('capture-resolution').value});await gallery();status('已保存 '+v.width+'×'+v.height+(v.upscaled?'，由 '+v.sourceWidth+'×'+v.sourceHeight+' 放大':' 原生截图'));});};
 el('capture-list-refresh').onclick=function(){run(gallery);};
 document.querySelector('[data-page="capture"]').addEventListener('click',function(){run(gallery);});
})();
