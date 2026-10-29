'use strict';
(function(){
 var form=document.getElementById('login'),message=document.getElementById('message'),button=form.querySelector('button'),setup=false,ready=false;
 function render(state){
  if(state.openAccess){location.replace('/console/');return;}
  setup=!state.initialized;ready=true;
  var openButton=document.getElementById('open-setup');if(openButton)openButton.hidden=!setup;
  document.getElementById('confirm-row').hidden=!setup;
  form.elements.passwordConfirm.required=setup;
  form.querySelector('h2').textContent=setup?'首次设置你的控制中心':'登录控制中心';
  button.textContent=setup?'创建账号并进入':'进入控制中心';
  if(setup){form.elements.password.autocomplete='new-password';message.textContent='请自行设置账号和密码，密码至少 8 位';}
  form.hidden=false;button.disabled=false;document.getElementById('login-loading').hidden=true;
  var card=document.getElementById('login-card');if(card&&card.setAttribute)card.setAttribute('aria-busy','false');
 }
 var state=form.getAttribute?form.getAttribute('data-initialized'):null;
 if(state==='true'||state==='false')render({initialized:state==='true',openAccess:false});
 else fetch('/api/v1/auth/status',{credentials:'same-origin',cache:'no-store'}).then(function(response){return response.json();}).then(function(value){if(!value.ok)throw Error('无法读取初始化状态');render(value.data);}).catch(function(error){
  var loading=document.getElementById('login-loading');loading.textContent='暂时无法连接电视，请刷新重试；不会重置账号';loading.hidden=false;message.textContent=error.message;
 });
 form.onsubmit=async function(event){
  event.preventDefault();if(!ready)return;button.disabled=true;message.textContent='正在连接…';
  try{
   if(setup&&form.elements.password.value!==form.elements.passwordConfirm.value)throw Error('两次输入的密码不一致');
   var response=await fetch('/api/v1/auth/'+(setup?'setup':'login'),{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:form.elements.username.value,password:form.elements.password.value,passwordConfirm:form.elements.passwordConfirm.value})}),result=await response.json();
   if(!response.ok||!result.ok)throw Error(result.error&&result.error.message||'账号或密码不正确');
   var target='/console/';
   if(result.data.needsPath)target='/console/app.html?setup=1#security';
   else{
    var next=new URLSearchParams(location.search).get('next');
    try{var url=new URL(next||target,location.origin);if(url.origin===location.origin&&(url.pathname==='/console/'||url.pathname==='/console/app.html'))target=url.pathname+url.search+url.hash;}catch(invalid){}
   }
   location.replace(target);
  }catch(error){message.textContent=error.message;}finally{button.disabled=false;}
 };
 var openButton=document.getElementById('open-setup');if(openButton)openButton.onclick=async function(){
  if(!ready||!setup)return;
  if(!confirm('启用无密码访问？知道此地址的局域网设备将可以管理桌面。以后可在安全设置开启密码保护。'))return;
  openButton.disabled=button.disabled=true;
  try{var response=await fetch('/api/v1/auth/open-setup',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:JSON.stringify({confirmed:true})}),result=await response.json();if(!response.ok||!result.ok)throw Error(result.error&&result.error.message||'设置失败');location.replace('/console/app.html?setup=1#security');}
  catch(error){message.textContent=error.message;}finally{openButton.disabled=button.disabled=false;}
 };
}());
