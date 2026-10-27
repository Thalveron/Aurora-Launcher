'use strict';
(async function(){
 try{
  var state=await AuroraAPI.request('bootstrap');
  AuroraPresentation.update(state);
  AuroraAPI.events(function(data){AuroraPresentation.update(data);});
  document.documentElement.dataset.auroraReady='true';
 }catch(error){document.body.textContent='屏保加载失败：'+error.message;}
})();
