'use strict';
const fs=require('fs'),assert=require('assert'),M=require('../app/src/main/assets/aurora/console/services-model.js');
const defaults=JSON.parse(fs.readFileSync(__dirname+'/../app/src/main/assets/aurora/console/service-defaults.json'));
const original=M.state(defaults,false),selected=M.city(original,{name:'Los Angeles',lat:34.05223,lon:-118.24368,timezone:'America/Los_Angeles'}),wire=M.payload(selected,original,false);
assert.equal(wire.weather.city,'Los Angeles');assert.equal(wire.weather.timezone,'America/Los_Angeles');assert.equal(wire.time.timezone,'America/Los_Angeles');assert.equal(wire.time.syncSystem,false);assert.equal(original.config.weather.city,'上海');
const typed=M.change(original,'weather','city','Los Angeles');assert.throws(()=>M.payload(typed,original,false),/搜索并选择城市/);
const manual=M.change(M.change(typed,'weather','lat',34.05223),'weather','lon',-118.24368);assert(M.manualLocation(manual,original));assert.equal(M.payload(manual,original,false).weather.lat,34.05223);
console.log('PASS city selection links weather coordinates and IANA time zone; changed city requires resolved or manually entered coordinates');
