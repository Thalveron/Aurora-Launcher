'use strict';
(function(){
 var key='aurora-console-theme',mode='light';
 try{mode=localStorage.getItem(key)||'light';}catch(e){}
 function frame(f){try{if(!f.contentDocument||!f.contentDocument.documentElement)return;var d=f.contentDocument;d.documentElement.dataset.consoleTheme=mode;if(!d.getElementById('console-day-css')){var l=d.createElement('link');l.id='console-day-css';l.rel='stylesheet';l.href='/console/day.css';d.head.append(l);}if(!f._sizeDocument||f._sizeDocument!==d){if(f._sizeObserver)f._sizeObserver.disconnect();f._sizeDocument=d;var fit=function(){if(f.contentDocument!==d)return;var main=d.querySelector('main');if(main)f.style.height=Math.ceil(main.getBoundingClientRect().height+32)+'px';};f._sizeObserver=new MutationObserver(fit);f._sizeObserver.observe(d.body,{childList:true,subtree:true});f.addEventListener('load',fit);fit();}}catch(e){}}
 function apply(){document.documentElement.dataset.consoleTheme=mode;var b=document.getElementById('console-theme-toggle');if(b){b.textContent=mode==='light'?'☀ 白天':'☾ 夜间';b.setAttribute('aria-label',mode==='light'?'切换为夜间主题':'切换为白天主题');}['native-category-frame','legacy-settings'].forEach(function(id){var f=document.getElementById(id);if(f)frame(f);});}
 apply();document.addEventListener('DOMContentLoaded',function(){document.getElementById('console-theme-toggle').onclick=function(){mode=mode==='light'?'dark':'light';try{localStorage.setItem(key,mode);}catch(e){}apply();};['native-category-frame','legacy-settings'].forEach(function(id){var f=document.getElementById(id);if(f)f.addEventListener('load',function(){frame(f);});});apply();});
 window.addEventListener('aurora-frame-ready',apply);
 window.addEventListener('storage',function(e){if(e.key===key){mode=e.newValue==='dark'?'dark':'light';apply();}});
}());
