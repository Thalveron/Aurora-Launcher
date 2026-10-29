'use strict';
(function(){
 function value(n){return typeof n==='number'&&isFinite(n)&&n>=0;}
 function bytes(n){if(!value(n))return '—';var units=['B','KB','MB','GB','TB'],i=0;while(n>=1024&&i<units.length-1){n/=1024;i++;}return n.toFixed(i?1:0)+' '+units[i];}
 function rate(n){return value(n)?bytes(n)+'/s':'—';}
 function rows(apps,query,active,key,direction){
  query=String(query||'').trim().toLowerCase();
  return (apps||[]).filter(function(a){return (!active||(a.rxRate||0)>0||(a.txRate||0)>0)&&(!query||(a.name+' '+a.uid+' '+(a.packages||[]).map(function(p){return p.package+' '+p.name;}).join(' ')).toLowerCase().indexOf(query)>=0);}).slice().sort(function(a,b){
   var av=a[key],bv=b[key];if(!value(av)&&!value(bv))return a.uid-b.uid;if(!value(av))return 1;if(!value(bv))return -1;return (av-bv)*(direction==='asc'?1:-1)||a.uid-b.uid;
  });
 }
 function points(input,historical){return (input||[]).filter(function(p){return value(p.time);}).map(function(p){return {time:p.time,rx:historical?(value(p.rxBytes)&&p.seconds>0?p.rxBytes/p.seconds:null):p.rxRate,tx:historical?(value(p.txBytes)&&p.seconds>0?p.txBytes/p.seconds:null):p.txRate};}).sort(function(a,b){return a.time-b.time;});}
 window.AuroraTrafficModel={bytes:bytes,rate:rate,rows:rows,points:points,valid:value};
}());
