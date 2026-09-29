/* KernelSU WebUI bridge helper */
(function() {
  var callbackCounter = 0;
  var DEFAULT_TIMEOUT = 8000;

  function getUniqueCallbackName(prefix) {
    return prefix + '_callback_' + Date.now() + '_' + (callbackCounter++);
  }

  function hasKernelSU() {
    return typeof window !== 'undefined' &&
      window.ksu &&
      typeof window.ksu.exec === 'function';
  }

  function exec(command, options, timeoutMs) {
    return new Promise(function(resolve, reject) {
      if (!hasKernelSU()) {
        reject(new Error('KernelSU WebUI bridge is not available'));
        return;
      }
      options = options || {};
      var t = timeoutMs || DEFAULT_TIMEOUT;
      var callbackName = getUniqueCallbackName('exec');
      var done = false;
      var timer = setTimeout(function() {
        if (done) return;
        done = true;
        delete window[callbackName];
        reject(new Error('KSU exec timeout (' + t + 'ms): ' + command.substring(0, 80)));
      }, t);
      window[callbackName] = function(errno, stdout, stderr) {
        if (done) return;
        done = true;
        clearTimeout(timer);
        delete window[callbackName];
        resolve({ errno: errno, stdout: stdout, stderr: stderr });
      };
      try {
        window.ksu.exec(command, JSON.stringify(options), callbackName);
      } catch (error) {
        if (!done) {
          done = true;
          clearTimeout(timer);
          delete window[callbackName];
        }
        reject(error);
      }
    });
  }

  function toast(message) {
    if (window.ksu && typeof window.ksu.toast === 'function') {
      window.ksu.toast(String(message));
    }
  }

  window.KSU = {
    hasKernelSU: hasKernelSU,
    exec: exec,
    toast: toast
  };
})();
