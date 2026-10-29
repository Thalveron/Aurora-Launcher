'use strict';
(function(){
 var current=null;
 function bind(sheet){
  var trigger=null;
  function close(){if(!sheet.open&&!sheet.hasAttribute('open'))return;if(sheet.close)sheet.close();else sheet.removeAttribute('open');if(current===controller)current=null;if(trigger&&document.contains(trigger))trigger.focus({preventScroll:true});}
  var controller={open:function(){if(current&&current!==controller)current.close();if(sheet.open)return;trigger=document.activeElement;current=controller;if(sheet.showModal)sheet.showModal();else sheet.setAttribute('open','');var first=sheet.querySelector('[data-drawer-close],button,input,select');if(first)first.focus({preventScroll:true});},close:close,isOpen:function(){return sheet.hasAttribute('open');}};
  sheet.querySelectorAll('[data-drawer-close]').forEach(function(b){b.onclick=close;});
  sheet.addEventListener('cancel',function(e){e.preventDefault();close();});
  sheet.addEventListener('click',function(e){if(e.target===sheet){var r=sheet.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)close();}});
  sheet.addEventListener('keydown',function(e){if(e.key==='Escape'){e.preventDefault();close();return;}if(e.key!=='Tab')return;var all=Array.from(sheet.querySelectorAll('button,input,select,textarea,a[href],[tabindex="0"]')).filter(function(n){return !n.disabled&&n.getClientRects().length;});if(!all.length){e.preventDefault();return;}var first=all[0],last=all[all.length-1];if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}});
  return controller;
 }
 function confirmAction(options){
  return new Promise(function(resolve){
   var dialog=document.createElement('dialog'),heading=document.createElement('h3'),message=document.createElement('p'),actions=document.createElement('div'),cancel=document.createElement('button'),accept=document.createElement('button'),trigger=document.activeElement,finished=false;
   dialog.className='ui-confirm-dialog';dialog.setAttribute('role','dialog');dialog.setAttribute('aria-modal','true');dialog.setAttribute('aria-label',options.title);heading.textContent=options.title;message.textContent=options.message;cancel.textContent='取消';cancel.className='secondary';accept.textContent=options.confirmLabel||'确认';accept.className='danger';actions.className='ui-confirm-actions';actions.append(cancel,accept);dialog.append(heading,message,actions);document.body.append(dialog);
   function finish(ok){if(finished)return;finished=true;if(dialog.close)dialog.close();dialog.remove();if(trigger&&document.contains(trigger))trigger.focus({preventScroll:true});resolve(ok);}
   cancel.onclick=function(){finish(false);};accept.onclick=function(){finish(true);};dialog.addEventListener('cancel',function(e){e.preventDefault();finish(false);});
   if(!dialog.showModal){finish(false);return;}dialog.showModal();cancel.focus();
  });
 }
 window.AuroraDrawer={bind:bind,confirm:confirmAction,close:function(){if(current)current.close();}};
}());
