'use strict';
// External script: production console CSP intentionally forbids inline JavaScript.
document.documentElement.classList.add('console-booting');
// Older cached control panels must not survive the server-side cutover.
(async function(){
  try{
    if('serviceWorker' in navigator){var registrations=await navigator.serviceWorker.getRegistrations();for(var registration of registrations){var scope=new URL(registration.scope);if(scope.origin===location.origin&&(scope.pathname==='/'||/\/(console|desktop)\//.test(scope.pathname)))await registration.unregister();}}
    if('caches' in window){var keys=await caches.keys();for(var key of keys){var cache=await caches.open(key),requests=await cache.keys();if(requests.some(function(request){var url=new URL(request.url);return url.origin===location.origin&&/\/(console|desktop|core)\//.test(url.pathname);}))await caches.delete(key);}}
  }catch(unavailable){/* Cache APIs are unavailable on ordinary HTTP LAN pages. */}
}());
