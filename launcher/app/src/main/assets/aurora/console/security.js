'use strict';
(function(){
 var api=window.AuroraAPI,UI=window.AuroraConsoleUI,form=document.getElementById('security-form'),status=document.getElementById('security-status'),draft,initialized=true;
 function el(id){return document.getElementById('security-'+id);}
 function read(){return {openAccess:el('open').checked,username:el('username').value,port:Number(el('port').value),path:el('path').value,password:el('password').value,passwordConfirm:el('confirm').value};}
 function requirements(){el('username').required=!el('open').checked;el('password').required=el('confirm').required=!el('open').checked&&!initialized;}
 function render(s){el('open').checked=s.openAccess;el('username').value=s.username;el('port').value=s.port;el('path').value=s.path;el('password').value=s.password||'';el('confirm').value=s.passwordConfirm||'';requirements();}
 function validate(s){if(!form.reportValidity())throw Error('请检查安全设置中的输入');if(s.password!==s.passwordConfirm)throw Error('两次输入的密码不一致');}
 function saved(s){initialized=s.initialized;status.textContent='已同步到电视';if(draft)draft.replace(Object.assign({},draft.value,{password:'',passwordConfirm:''}));if(s.loginRequired){var url=new URL('/'+s.loginPath,location.origin);url.port=String(s.port);status.textContent='已保存，正在转到新的登录地址…';setTimeout(function(){location.replace(url.href);},1800);}}
 async function load(){try{var s=await api.request('security');initialized=s.initialized;s.password=s.passwordConfirm='';render(s);if(UI){draft=UI.draft('security',{label:'安全设置',initial:read(),render:function(v){if(JSON.stringify(v)!==JSON.stringify(read()))render(v);requirements();},validate:validate,save:function(v){return api.request('security',v);},saved:saved});form.querySelector('button').hidden=true;['input','change'].forEach(function(type){form.addEventListener(type,function(){requirements();draft.set(read());});});}if(new URLSearchParams(location.search).has('setup'))document.getElementById('security-hint').textContent='访问方式已设置。建议在这里添加专属访问路径，并保存新的访问地址。';}catch(e){status.textContent=e.message;}}
 form.onsubmit=async function(e){e.preventDefault();if(draft){draft.set(read());await UI.save();return;}var button=form.querySelector('button');button.disabled=true;try{var data=read();validate(data);saved(await api.request('security',data));}catch(e){status.textContent=e.message;}finally{button.disabled=false;}};
 load();
}());
