'use strict';
(function(){
 var key='aurora-console-theme',mode='light';
 try{mode=localStorage.getItem(key)||'light';}catch(e){}
 function apply(){document.documentElement.dataset.consoleTheme=mode;var b=document.getElementById('console-theme-toggle');if(b){b.textContent=document.body&&document.body.classList.contains('console-v2')?(mode==='light'?'☀':'☾'):(mode==='light'?'☀ 白天':'☾ 夜间');b.setAttribute('aria-label',mode==='light'?'切换为夜间主题':'切换为白天主题');}}
 apply();document.addEventListener('DOMContentLoaded',function(){document.getElementById('console-theme-toggle').onclick=function(){mode=mode==='light'?'dark':'light';try{localStorage.setItem(key,mode);}catch(e){}apply();};apply();});
 window.addEventListener('storage',function(e){if(e.key===key){mode=e.newValue==='dark'?'dark':'light';apply();}});
}());
