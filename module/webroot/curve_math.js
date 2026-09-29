/* Shared chart model: core log-lux interpolation, relative preference, conditional sunlight. */
(function (root) {
  'use strict';
  var lux = [0,1,5,10,50,100,500,1000,2000,5000,9000,10000,35000,100000];
  var points = [2,2.8,3.5,4.2,6.5,7.5,10,11,12,13.5,22,25,65,85];
  function valid(values) { return Array.isArray(values) && values.length === 14 && values.every(function (v,i) { return Number.isFinite(v) && v >= .1 && v <= 100 && (!i || v >= values[i-1]); }); }
  function edit(values, index, value) {
    if (!valid(values) || !Number.isInteger(index) || index < 0 || index >= 14 || !Number.isFinite(value) || value < .1 || value > 100) return null;
    var next = values.slice(); next[index] = value;
    for (var i = 0; i < index; i++) next[i] = Math.min(next[i], value);
    for (var i = index + 1; i < 14; i++) next[i] = Math.max(next[i], value);
    return next;
  }
  function base(value, values, gamma) {
    values = valid(values) ? values : points;
    if (!Number.isFinite(value) || value <= 0) return values[0];
    if (value >= 100000) return values[13];
    var hi = 1; while (hi < 13 && value > lux[hi]) hi++;
    var t = (Math.log10(value+1)-Math.log10(lux[hi-1]+1))/(Math.log10(lux[hi]+1)-Math.log10(lux[hi-1]+1));
    var g = Number.isFinite(gamma) && gamma >= 1 ? Math.min(3.5,gamma) : 2.2;
    return values[hi-1]+(values[hi]-values[hi-1])*Math.pow(Math.max(0,Math.min(1,t)),2.2/g);
  }
  function preference(value, config) { return Math.min(100,base(value,config.learnedPoints || config.points,config.gamma)*(config.circadian || 1)*(1+Math.max(-50,Math.min(100,config.offset || 0))/100)); }
  function sunlight(value, config) {
    var threshold = config.threshold || 5000, extreme = config.extreme || 30000;
    if (value < threshold) return null;
    return Math.min(100,Math.max(config.minimum === undefined ? 90 : config.minimum,
      preference(value,config)+(config.boost === undefined ? 50 : config.boost)*Math.max(0,Math.min(1,(value-threshold)/Math.max(1,extreme-threshold)))));
  }
  var api = {lux:lux,defaults:points,valid:valid,edit:edit,base:base,preference:preference,sunlight:sunlight};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.LumaCurveMath = api;
})(typeof window === 'undefined' ? globalThis : window);
