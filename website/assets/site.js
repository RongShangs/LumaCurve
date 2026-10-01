'use strict';
(function () {
  // Illustrative OEM logical anchors, not a universal device calibration.
  var slider = document.getElementById('demo-lux');
  var strength = document.getElementById('demo-strength');
  var luxPoints = [0, 30, 600, 5000], nitPoints = [2, 135, 220, 1060];
  function x(lux) { return 40 + Math.log10(lux + 1) / Math.log10(5001) * 360; }
  function y(percent) { return 200 - percent / 100 * 180; }
  function percentAt(lux) {
    var values = nitPoints.slice(); values[1] *= Number(strength.value) / 100;
    for (var i = 1; i < luxPoints.length; i++) {
      if (lux <= luxPoints[i]) return (values[i - 1] + (values[i] - values[i - 1]) * (lux - luxPoints[i - 1]) / (luxPoints[i] - luxPoints[i - 1])) / values[3] * 100;
    }
    return 100;
  }
  function render() {
    var points = [];
    for (var i = 0; i <= 200; i++) { var sample = Math.pow(5001, i / 200) - 1; points.push(x(sample).toFixed(2) + ',' + y(percentAt(sample)).toFixed(2)); }
    document.getElementById('demo-line').setAttribute('points', points.join(' '));
    var lux = Math.pow(5001, Number(slider.value) / 1000) - 1;
    var percent = percentAt(lux), px = x(lux), py = y(percent);
    document.getElementById('demo-point').setAttribute('cx', px); document.getElementById('demo-point').setAttribute('cy', py);
    document.getElementById('demo-guide').setAttribute('d', 'M' + px + ' ' + py + 'V200');
    document.getElementById('demo-output').value = lux.toLocaleString('zh-CN', {maximumFractionDigits:1}) + ' lux · 示意 ' + percent.toFixed(1) + '%';
    document.getElementById('strength-output').textContent = strength.value + '%';
  }
  slider.addEventListener('input', render); strength.addEventListener('input', render); render();
  document.getElementById('copy-group').addEventListener('click', async function () {
    var status = document.getElementById('copy-status');
    try {
      if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText('314981836');
      else {
        var box = document.createElement('textarea'); box.value = '314981836'; box.style.position = 'fixed'; box.style.opacity = '0'; document.body.appendChild(box);
        try { box.select(); if (!document.execCommand('copy')) throw new Error('copy unavailable'); } finally { box.remove(); }
      }
      status.textContent = '已复制群号';
    } catch (error) { status.textContent = '请手动复制群号：314981836'; }
  });
  var dialog = document.getElementById('donation-dialog');
  document.querySelectorAll('[data-donate]').forEach(function (button) {
    button.addEventListener('click', function () {
      var wechat = button.dataset.donate === 'wechat';
      document.getElementById('donation-title').textContent = wechat ? '微信打赏' : '支付宝打赏';
      var qr = document.getElementById('donation-qr'); qr.src = wechat ? 'assets/donate-wechat.jpg' : 'assets/donate-alipay.jpg'; qr.alt = wechat ? '微信收款码' : '支付宝收款码';
      dialog.showModal();
    });
  });
  document.getElementById('close-donation').addEventListener('click', function () { dialog.close(); });
  dialog.addEventListener('click', function (event) { if (event.target === dialog) { var rect = dialog.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close(); } });
})();
