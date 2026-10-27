'use strict';
(function () {
  var neighbors={};
  function items() {return Array.prototype.filter.call((document.querySelector('.queue-overlay')||document).querySelectorAll('[data-focus-id]'), function (item) {return !item.disabled && item.getClientRects().length > 0;});}
  function first() {if(new URLSearchParams(location.search).get('preview')==='1')return;var list=items(),selector=!document.querySelector('.queue-overlay')&&document.body.getAttribute('data-home-focus'),preferred=selector&&document.querySelector(selector);if(preferred&&list.indexOf(preferred)>=0){document.body.dataset.homeReset='true';preferred.focus();window.dispatchEvent(new Event('aurora-focus-reset'));requestAnimationFrame(function(){requestAnimationFrame(function(){delete document.body.dataset.homeReset;});});return;}if(list.length)list[0].focus();}
  function move(key) {
    var current = document.activeElement, list = items();
    if (list.indexOf(current) < 0) {first(); return;}
    var declared=neighbors[current.dataset.focusId]||{};
    var explicit = declared[key.replace('Arrow','').toLowerCase()]||current.getAttribute('data-nav-' + key.replace('Arrow', '').toLowerCase());
    if (explicit) {var target = document.querySelector('[data-focus-id="' + explicit.replace(/[^a-zA-Z0-9_.-]/g, '') + '"]'); if (target && !target.disabled) {target.focus(); return;}}
    var box = current.getBoundingClientRect(), x = box.left + box.width / 2, y = box.top + box.height / 2;
    var horizontal = key === 'ArrowLeft' || key === 'ArrowRight', positive = key === 'ArrowRight' || key === 'ArrowDown';
    var best = null, score = Infinity;
    list.forEach(function (item) {
      if (item === current) return;
      var other = item.getBoundingClientRect(), dx = other.left + other.width / 2 - x, dy = other.top + other.height / 2 - y;
      var forward = horizontal ? dx : dy, across = horizontal ? dy : dx;
      if (positive ? forward <= 1 : forward >= -1) return;
      var value = Math.abs(forward) + Math.abs(across) * 2.5;
      if (value < score) {score = value; best = item;}
    });
    if(best) {best.focus();var bounds=best.getBoundingClientRect();if(bounds.top<0||bounds.left<0||bounds.bottom>innerHeight||bounds.right>innerWidth)best.scrollIntoView({block:'nearest',inline:'nearest'});}
  }
  document.addEventListener('keydown', function (event) {
    if (/^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName)) return;
    var key = event.key || ({37: 'ArrowLeft', 38: 'ArrowUp', 39: 'ArrowRight', 40: 'ArrowDown', 13: 'Enter'})[event.keyCode];
    if (/^Arrow/.test(key)) {event.preventDefault(); move(key);}
    else if (key === 'Enter') {event.preventDefault(); if (document.activeElement && !document.activeElement.disabled) document.activeElement.click();}
    else if (key === 'Home') {event.preventDefault(); first();}
    else if (key === 'ContextMenu' || event.keyCode === 82) {event.preventDefault(); window.dispatchEvent(new Event('aurora-menu'));}
  });
  window.addEventListener('focus',function(){if(document.body.hasAttribute('data-home-focus'))first();});
  window.AuroraFocus = {first: first, move: move, configure:function(value){neighbors=value||{};}};
}());
