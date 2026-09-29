'use strict';
(function () {
  var math = window.LumaCurveMath, slider = document.getElementById('demo-lux');
  function x(lux) { return 40 + Math.log10(lux + 1) / Math.log10(100001) * 360; }
  function y(percent) { return 200 - percent / 100 * 180; }
  var points = [];
  for (var i = 0; i <= 200; i++) { var lux = Math.pow(100001, i / 200) - 1; points.push(x(lux).toFixed(2) + ',' + y(math.base(lux, math.defaults, 2.2)).toFixed(2)); }
  document.getElementById('demo-line').setAttribute('points', points.join(' '));
  function render() {
    var lux = Math.pow(100001, Number(slider.value) / 1000) - 1;
    var percent = math.base(lux, math.defaults, 2.2), px = x(lux), py = y(percent);
    document.getElementById('demo-point').setAttribute('cx', px); document.getElementById('demo-point').setAttribute('cy', py);
    document.getElementById('demo-guide').setAttribute('d', 'M' + px + ' ' + py + 'V200');
    document.getElementById('demo-output').value = lux.toLocaleString('zh-CN', {maximumFractionDigits:1}) + ' lux · ' + percent.toFixed(2) + '%';
  }
  slider.addEventListener('input', render); render();
})();
