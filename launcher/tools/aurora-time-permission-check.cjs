'use strict';
const fs=require('fs'),assert=require('assert'),path=require('path'),M=require('../app/src/main/assets/aurora/console/services-model.js');
const root=path.join(__dirname,'../app/src/main/assets');
const defaults=JSON.parse(fs.readFileSync(path.join(root,'aurora/console/service-defaults.json')));
function check(allowed,selected){const config=M.copy(defaults);config.time.syncSystem=selected;const initial=M.state(config,allowed);assert.equal(initial.config.time.syncSystem,allowed&&selected);assert.equal(M.payload(initial,initial,allowed).time.syncSystem,allowed&&selected);if(!allowed)assert.equal(M.payload(initial,initial,true).time.syncSystem,false,'new permission must not silently opt in');}
check(false,true);check(false,false);check(true,false);check(true,true);
assert.equal(JSON.parse(fs.readFileSync(path.join(root,'private-defaults/bundle.json'))).serviceDefaults.time.syncSystem,false);
assert.equal(JSON.parse(fs.readFileSync(path.join(root,'aurora/console/service-defaults.json'))).time.syncSystem,false);
console.log('PASS time sync defaults off; no-permission checkbox unchecked/disabled; permission does not auto-enable sync');
