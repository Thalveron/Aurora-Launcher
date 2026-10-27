/* Deliberately loaded only by the built-in cards theme. */
window.AuroraCardsLayout=function(layout){
 var main=document.querySelector('main.aurora-grid');if(!main)return;
 main.classList.toggle('cards-freeform',!!layout.freeform);
 main.querySelectorAll('[data-area]').forEach(function(n){var r=layout.freeform&&layout.freeform[n.dataset.area];['left','top','width','height'].forEach(function(k){n.style.removeProperty(k);});if(r){n.style.left=r.x+'%';n.style.top=r.y+'%';n.style.width=r.w+'%';n.style.height=r.h+'%';}});
};
