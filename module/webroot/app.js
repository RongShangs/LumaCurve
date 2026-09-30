/* LumaCurve 1.0.0 · GPL-3.0 · 戎Shang */
(function () {
  'use strict';
  var BASE = '/data/local/tmp/luma_curve';
  var CTL = '/data/adb/modules/luma_curve/luma_curvectl.sh';
  var CONFIG_VERSION = '12';
  var device = window.KSU && window.KSU.hasKernelSU();
  var active = 'status', configText = '', configReady = false, configBusy = false;
  var commandBusy = false, stateBusy = false, stateText = '', lastState = null, demo = false;
  var toastTimer, logText = '', exportBusy = false, editing = null, editorTrigger = null;
  var history = [], historyStamp = '', viewportBaseline = window.innerHeight;
  var defaults = {
    preference_learning: 1,
    alpha_fast: 0.42, alpha_mid: 0.18, alpha_slow: 0.05, fast_threshold: 1.1,
    slow_threshold: 0.06, spike_up: 12, spike_down: 0.06, hyst_low: 0.03, hyst_high: 0.06,
    indoor_stability: 1, manual_gamma: 2.2, brighten_speed: 1, darken_speed: 1, min_step: 4,
    thermal_cap_enable: 1, thermal_hard_trigger: 60000, thermal_hard_resume: 55000,
    thermal_hard_floor_pct: 0.20, charging_heat_guard: 1,
    high_lux_threshold: 5000, high_lux_extreme_lux: 30000, high_lux_boost_max: 0.50,
    high_lux_min_pct: 0.90, high_lux_max_active_ms: 0, high_lux_cooldown_ms: 10000,
    high_lux_temp_thresh: 52000, high_lux_temp_resume: 48000, high_lux_bat_thresh: 10,
    high_lux_hbm_enable: 1, hbm_node_path: '', hbm_on_value: '1', hbm_off_value: '0'
  };
  // [key, label, explanation, min, max, step, displayed units per raw unit, unit]
  var preferenceEdited = false, preferenceBusy = false, preferenceRecord = null;
  var learnedDraft = null;
  var curveDraft = window.LumaCurveMath.defaults.slice(), curveCustom = false, curvePointEditing = false;
  var presets = [], presetText = '', presetsReady = false, presetBusy = false;
  var groups = [
    {title: '日常调节', hint: '优先调整速度，数值越大，变化越快。', open: true, fields: [
      ['indoor_stability', '场景自适应', '正面优先；双侧同向变化更快响应，单侧突变先确认。', 'bool'],
      ['brighten_speed', '变亮速度', '从暗处走向亮处时的调节速度。', 0.5, 2.5, 0.1, 1, '倍'],
      ['darken_speed', '变暗速度', '进入暗处时的调节速度。', 0.5, 2.5, 0.1, 1, '倍'],
      ['manual_gamma', '曲线形状', '只改变曲线形状，不切换系统亮度模式。', 1, 3.5, 0.1, 1, 'γ']
    ]},
    {title: '阳光增强', hint: '强光下提高目标亮度，受温度、电量和设备能力限制。', fields: [
      ['high_lux_threshold', '开始增强的照度', '连续确认强光后才进入增强。', 500, 100000, 100, 1, 'lux'],
      ['high_lux_extreme_lux', '达到最大增强的照度', '至少比开始增强的照度高 100 lux。', 600, 200000, 100, 1, 'lux'],
      ['high_lux_min_pct', '增强期间的目标下限', '以最大背光为基准，仍受其他保护约束。', 20, 100, 1, 100, '%'],
      ['high_lux_boost_max', '最大附加亮度', '附加量占最大背光的比例，最终不会超过最大背光。', 0, 60, 1, 100, '%'],
      ['high_lux_max_active_ms', '单次持续时间', '0 表示条件安全时持续；其他值至少 3 秒。', 0, 600, 1, 0.001, '秒'],
      ['high_lux_cooldown_ms', '冷却时间', '时间上限触发退出后，再次进入前的等待。', 0, 600, 1, 0.001, '秒'],
      ['high_lux_temp_thresh', '停止增强的温度', '可信温度达到此值后停止阳光增强。', 40, 80, 1, 0.001, '℃'],
      ['high_lux_temp_resume', '允许恢复的温度', '必须低于停止增强的温度。', 35, 79, 1, 0.001, '℃'],
      ['high_lux_bat_thresh', '最低电量', '低于该电量且未充电时不启用阳光增强。', 5, 50, 1, 1, '%'],
      ['high_lux_hbm_enable', '允许硬件高亮', '需要设备存在可用的 HBM 节点。', 'bool']
    ]},
    {title: '温控保护', hint: '保留默认值即可。恢复门槛低于触发门槛，避免反复进出。', fields: [
      ['thermal_cap_enable', '启用硬温控', '极端温度下限制目标背光。', 'bool'],
      ['thermal_hard_trigger', '触发温度', '可信温度达到此值时进入保护；充电会调整门槛。', 45, 90, 1, 0.001, '℃'],
      ['thermal_hard_resume', '恢复温度', '降至该温度后按恢复逻辑退出保护。', 35, 89, 1, 0.001, '℃'],
      ['thermal_hard_floor_pct', '保护期间的亮度上限', '最大背光的比例；本来更暗时不会被提高。', 5, 80, 1, 100, '%'],
      ['charging_heat_guard', '充电温控保护', '充电时使用更保守的温度门槛。', 'bool']
    ]},
    {title: '高级采样与滤波', hint: '影响传感器平滑和变化确认，不建议一次修改多个参数。', fields: [
      ['alpha_fast', '快速平滑系数', '环境变化明显时使用，越大越跟随新数据。', 0.01, 0.95, 0.01, 1, ''],
      ['alpha_mid', '日常平滑系数', '一般环境变化时使用。', 0.01, 0.95, 0.01, 1, ''],
      ['alpha_slow', '稳定平滑系数', '环境稳定时使用，较小的值更平稳。', 0.01, 0.95, 0.01, 1, ''],
      ['fast_threshold', '快速模式变化阈值', '照度相对变化足够大时使用快速响应。', 0.1, 20, 0.1, 1, ''],
      ['slow_threshold', '稳定模式变化阈值', '照度相对变化较小时使用慢速响应。', 0.001, 1, 0.001, 1, ''],
      ['spike_up', '突亮过滤阈值', '用于识别异常升亮样本的比例门槛。', 1, 50, 0.1, 1, ''],
      ['spike_down', '突暗过滤阈值', '用于识别异常降亮样本的比例门槛。', 0.001, 1, 0.001, 1, ''],
      ['hyst_low', '低照度滞回', '减小低照度下的反复调节。', 0.1, 50, 0.1, 100, '%'],
      ['hyst_high', '高照度滞回', '不得小于低照度滞回。', 0.1, 50, 0.1, 100, '%'],
      ['min_step', '最小变化门槛', '背光数值单位，用于目标确认等门槛计算。', 1, 2048, 1, 1, '级']
    ]},
    {title: '硬件高亮节点', hint: '留空自动检测，仅在了解设备节点时修改。', fields: [
      ['hbm_node_path', 'HBM 节点路径', '填写 /sys/ 下的完整路径，留空自动发现。', 'text'],
      ['hbm_on_value', '开启值', '设备开启 HBM 所需的节点值。', 'text'],
      ['hbm_off_value', '关闭值', '设备关闭 HBM 所需的节点值。', 'text']
    ]}
  ];
  var fields = [];
  groups.forEach(function (g) { fields = fields.concat(g.fields); });
  // Starting ranges around the recovered defaults, not device calibration results.
  var recommended = {
    indoor_stability: '建议开启', brighten_speed: '0.8–1.4 倍', darken_speed: '0.8–1.4 倍', manual_gamma: '1.8–2.4',
    high_lux_threshold: '3000–10000 lux', high_lux_extreme_lux: '20000–50000 lux',
    high_lux_min_pct: '90–100%', high_lux_boost_max: '30–50%', high_lux_max_active_ms: '0（持续）或 3–120 秒',
    high_lux_cooldown_ms: '10–60 秒', high_lux_temp_thresh: '50–55℃', high_lux_temp_resume: '45–50℃', high_lux_bat_thresh: '10–20%',
    high_lux_hbm_enable: '启用，设备支持时生效', thermal_cap_enable: '启用', charging_heat_guard: '启用',
    thermal_hard_trigger: '55–65℃', thermal_hard_resume: '50–55℃，低于触发温度', thermal_hard_floor_pct: '15–25%',
    alpha_fast: '0.30–0.60', alpha_mid: '0.10–0.25', alpha_slow: '0.03–0.10', fast_threshold: '0.8–2.0', slow_threshold: '0.03–0.10',
    spike_up: '8–20', spike_down: '0.03–0.10', hyst_low: '2–5%', hyst_high: '5–10%', min_step: '2–8 级',
    hbm_node_path: '留空自动检测', hbm_on_value: '通常为 1，以设备节点为准', hbm_off_value: '通常为 0，以设备节点为准'
  };
  function $(id) { return document.getElementById(id); }
  ['wechat', 'alipay'].forEach(function (type) {
    $('donate-' + type).addEventListener('click', function () {
      var name = type === 'wechat' ? '微信' : '支付宝';
      set('donation-title', name + '打赏'); $('donation-qr').src = 'donate-' + type + '.jpg';
      $('donation-qr').alt = name + '收款码'; $('donation-dialog').showModal();
    });
  });
  $('close-donation').addEventListener('click', function () { $('donation-dialog').close(); });
  function set(id, value) { $(id).textContent = value; }
  function quote(s) { return "'" + String(s).replace(/'/g, "'\\''") + "'"; }
  function toast(s) { set('toast', s); $('toast').classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(function () { $('toast').classList.remove('show'); }, 3200); }
  // Resolve a generic web URL, then launch that browser with the actual URL.
  // MAIN/APP_BROWSER selectors can inherit the URL and fail intent resolution.
  function browserComponent(text) {
    return String(text).split(/\r?\n/).map(function (line) { return line.trim(); }).filter(function (line) {
      return /^[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*\/[a-zA-Z0-9_.$]+$/.test(line) && line.split('/')[0] !== 'android' && !/(?:Resolver|Chooser)Activity/i.test(line);
    })[0];
  }
  function openBrowser(url) {
    var query = ' --brief --user current -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d ' + quote('https://example.com/');
    return exec('cmd package resolve-activity' + query).catch(function () { return ''; }).then(function (output) {
      var component = browserComponent(output);
      if (component) return component;
      return exec('cmd package query-activities' + query).then(function (items) {
        var candidate = browserComponent(items);
        if (!candidate) throw new Error('未找到可以打开网页的浏览器');
        return candidate;
      });
    }).then(function (component) {
      return exec('am start --user current -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -n ' + quote(component) + ' -d ' + quote(url));
    }).then(function (output) {
      if (/Error:|Exception|unable to resolve/i.test(output)) throw new Error(output.trim());
    });
  }
  document.addEventListener('click', function (event) {
    var link = event.target.closest('a[href]');
    if (!link || !/^https:\/\//.test(link.href) || !device) return;
    event.preventDefault();
    openBrowser(link.href).catch(function (error) { toast('打开浏览器失败：' + error.message); });
  });
  function copyText(text, label) {
    if (!text) { toast('暂无可复制的' + label); return; }
    var copy = navigator.clipboard && navigator.clipboard.writeText ? navigator.clipboard.writeText(text) : Promise.reject(new Error('剪贴板不可用'));
    copy.then(function () { toast(label + '已复制'); }).catch(function () {
      var area = document.createElement('textarea'); area.value = text; area.style.position = 'fixed'; area.style.opacity = '0'; area.readOnly = true;
      document.body.appendChild(area); area.select(); var ok = document.execCommand('copy'); area.remove();
      toast(ok ? label + '已复制' : '复制失败，请长按日志选择文本');
    });
  }
  var keyboardPointer = false;
  document.addEventListener('pointerdown',function () { keyboardPointer = document.documentElement.classList.contains('keyboard-open'); },true);
  ['pointerup','pointercancel'].forEach(function (type) { document.addEventListener(type,function () {
    if (keyboardPointer) setTimeout(function () { keyboardPointer=false; syncViewport(); },150);
  },true); });
  function syncViewport() {
    var view = window.visualViewport, height = view ? view.height : window.innerHeight;
    var element = document.activeElement, typing = element && (element.tagName === 'TEXTAREA' ||
      element.tagName === 'INPUT' && ['number', 'text', 'search', 'tel', 'email', 'password'].indexOf(element.type) >= 0);
    var keyboard = keyboardPointer || !!typing || document.documentElement.classList.contains('keyboard-open') && viewportBaseline - height > 140 && (!view || view.scale === 1);
    document.documentElement.classList.toggle('keyboard-open', keyboard);
    document.documentElement.style.setProperty('--visual-height', height + 'px');
    document.documentElement.style.setProperty('--visual-top', (view ? view.offsetTop : 0) + 'px');
    if (!keyboard) viewportBaseline = window.innerHeight;
  }
  document.addEventListener('focusin', syncViewport);
  document.addEventListener('focusout', function () { setTimeout(syncViewport, 0); });
  window.addEventListener('resize', syncViewport);
  window.addEventListener('orientationchange', function () { viewportBaseline = window.innerHeight; syncViewport(); });
  if (window.visualViewport) { window.visualViewport.addEventListener('resize', syncViewport); window.visualViewport.addEventListener('scroll', syncViewport); }
  syncViewport();
  function parse(text) {
    var result = {};
    String(text || '').split(/\r?\n/).forEach(function (line) {
      if (/^\s*#/.test(line)) return;
      var i = line.indexOf('=');
      if (i > 0) result[line.slice(0, i).trim()] = line.slice(i + 1).trim();
    });
    return result;
  }
  function exec(command) {
    if (!device) return Promise.reject(new Error('请在模块管理器中打开 WebUI'));
    return window.KSU.exec(command, {}, 15000).then(function (result) {
      if (!result || Number(result.errno) !== 0) throw new Error((result && result.stderr || '设备操作失败').trim());
      return result.stdout || '';
    });
  }
  function control(command, args) {
    var allowed = ['pause', 'resume', 'restart', 'reload-config', 'prune-logs', 'clear-log', 'set-log-retention'];
    if (allowed.indexOf(command) < 0) return Promise.reject(new Error('不支持的操作'));
    args = args || [];
    if (command === 'set-log-retention') {
      if (args.length !== 1 || !/^(?:[0-9]|[12][0-9]|30)$/.test(String(args[0]))) return Promise.reject(new Error('保留天数应为 0–30'));
    } else if (args.length) return Promise.reject(new Error('操作参数不正确'));
    return exec('sh ' + quote(CTL) + ' ' + quote(command) + args.map(function (a) { return ' ' + quote(a); }).join(''));
  }
  function format(n, digits) { var value = Number(n); return n === undefined || n === '' || !isFinite(value) ? '—' : value.toFixed(digits || 0); }
  var owners = {daemon: '模块调节', manual: '系统手动', wake_readonly: '唤醒观察', screen_off_passthrough: '系统接管', lock_failed_passthrough: '权限受限', system_owned: '系统接管'};
  function withUnit(value, digits, unit) { var text = format(value, digits); return text === '—' ? text : text + unit; }
  function sensorReading(value) { return value === undefined || value === '' || !Number.isFinite(Number(value)) || Number(value) < 0 ? '—' : withUnit(value,1,' lux'); }
  function effectiveTarget(s) {
    var limited = Number(s.framework_effective_target_br);
    return s.output_backend === 'hyperos4_framework' && s.framework_owned === '1' &&
      s.framework_effective_target_br !== undefined && Number.isFinite(limited) ? limited : Number(s.target_br);
  }
  function renderSensors(s) {
    var sources = {front: '前置光感', back: '后置光感（备用）', ndk: '系统光感', unknown: '未确定', none: '暂无来源'};
    set('sensor-source', sources[s.lux_source] || (s.lux_source || '—'));
    set('sensor-health', s.sensor_stale === '1' ? '样本过期' : s.sensor_hold_active === '1' ? '保持亮度' : s.lux_valid === '1' ? '照度有效' : s.lux_valid === '0' ? '等待有效样本' : '未上报有效性');
    set('sensor-age', withUnit(s.lux_age_ms, 0, ' ms'));
    set('front-lux', sensorReading(s.front_lux)); set('back-lux', sensorReading(s.back_lux));
    set('front-age', s.front_lux_age_ms === undefined ? '未上报样本年龄' : (s.front_reporting_mode === '1' ? '距最后变化 ' : '样本年龄 ') + withUnit(s.front_lux_age_ms, 0, ' ms'));
    set('back-age', s.back_lux_age_ms === undefined ? '未上报样本年龄' : (s.back_reporting_mode === '1' ? '距最后变化 ' : '样本年龄 ') + withUnit(s.back_lux_age_ms, 0, ' ms'));
    set('sensor-interval', Number(s.sensor_rate_us) > 0 ? (Number(s.sensor_rate_us) / 1000).toFixed(0) + ' ms' : '—');
    set('loop-interval', withUnit(s.target_poll_ms === undefined ? s.poll_ms : s.target_poll_ms, 0, ' ms'));
    set('device-temperature', Number(s.thermal_trusted_temp) > 0 ? (Number(s.thermal_trusted_temp) / 1000).toFixed(1) + '℃' : '暂无可信读数');
    var battery = Number(s.battery_pct);
    set('device-battery', s.battery_pct !== undefined && battery >= 0 && battery <= 100 ? battery + '% · ' + (s.charging === '1' ? '充电中' : s.charging === '0' ? '未充电' : '状态未知') : '—');
    set('write-permission', s.daemon_can_write === '1' ? '允许写入' : s.daemon_can_write === '0' ? '暂缓写入' : '—');
    set('target-confirmation', s.target_hold_active === '1' ? '等待候选稳定' : s.target_hold_active === '0' ? '允许调节，尚需平滑过渡' : '—');
  }
  function renderTrend(s, fresh) {
    var now = Date.now(), raw = Number(s.lux), smooth = Number(s.smooth);
    if (fresh && s.lux !== undefined && s.smooth !== undefined && isFinite(raw) && isFinite(smooth) && raw >= 0 && smooth >= 0 && historyStamp !== s.updated_unix) {
      history.push({time:now,raw:raw,smooth:smooth,target:effectiveTarget(s)/Number(s.max_br)*100,current:Number(s.current_br)/Number(s.max_br)*100}); historyStamp = s.updated_unix;
    }
    history = history.filter(function (point) { return now - point.time <= 60000; }).slice(-90);
    var ceiling = Math.max(1, ...history.map(function (point) { return Math.max(point.raw, point.smooth); }));
    var start = history.length ? history[0].time : now, span = history.length > 1 ? Math.max(1, history[history.length - 1].time - start) : 1;
    ['raw', 'smooth'].forEach(function (key) {
      $(key === 'raw' ? 'raw-trend' : 'smooth-trend').setAttribute('points', history.map(function (point) {
        return (history.length === 1 ? 320 : (point.time - start) / span * 320).toFixed(1) + ',' + (80 - point[key] / ceiling * 70).toFixed(1);
      }).join(' '));
      var dot = $(key === 'raw' ? 'raw-dot' : 'smooth-dot');
      dot.setAttribute('visibility', history.length ? 'visible' : 'hidden');
      if (history.length) { dot.setAttribute('cx', '320'); dot.setAttribute('cy', (80 - history[history.length - 1][key] / ceiling * 70).toFixed(1)); }
    });
    set('trend-scale', history.length ? '共同刻度 0–' + ceiling.toFixed(1) + ' lux · ' + history.length + ' 次状态更新' : '等待新照度数据');
  }
  function responseX(lux) { return 32 + Math.log10(Math.max(0, Math.min(100000, lux)) + 1) / Math.log10(100001) * 310; }
  function responseY(pct) { return 120 - Math.max(0, Math.min(100, pct)) / 100 * 108; }
  function renderScene(s, stale) {
    var paused = s._ui_paused === '1' || stale || s.mode !== 'auto' || s.screen === '0';
    if (s._ui_paused === '1' || s.screen === '0') set('owner', '系统接管');
    else if (s.mode === 'manual') set('owner', '系统手动');
    else if (stale) set('owner', '状态待确认');
    var guarded = s.sensor_stale === '1' || s.sensor_hold_active === '1' || s.scene_hold_active === '1' || s.low_lux_bright_spike_guard === '1' || s.fast_dark_candidate === '1';
    var sourceReady = !paused && s.lux_valid === '1' && !guarded;
    ['front', 'back'].forEach(function (source) {
      var selected = s.lux_source === source && s.lux_valid === '1';
      var assists = source === 'back' && s.lux_source === 'front' && s.lux_valid === '1' &&
        ['common_rise', 'common_fall', 'divergent'].indexOf(s.scene_relation) >= 0;
      $(source + '-node').setAttribute('data-active', String((selected || assists) && !stale));
      $(source + '-wire').setAttribute('data-active', String((selected || assists) && sourceReady));
      set('scene-' + source, sensorReading(s[source + '_lux']));
    });
    var rawSource = s.lux_source === 'front' ? s.front_lux : s.back_lux;
    var conditioned = s.low_lux_bright_spike_guard === '1' || s.zero_lux_suspect === '1' || s.scene_hold_active === '1' || s.sensor_hold_active === '1' ||
      (Number.isFinite(Number(rawSource)) && Math.abs(Number(rawSource)-Number(s.lux)) > Math.max(.1,Math.abs(Number(rawSource))*.03));
    set('scene-source', conditioned ? '保持 / 限幅输入' : '已确认输入');
    set('scene-lux', s.lux_valid === '0' ? '—' : format(s.lux, 1)); set('scene-smooth', s.lux_valid === '0' ? '—' : withUnit(s.smooth, 1, ' lux'));
    $('filter-node').setAttribute('data-state', stale ? 'unknown' : guarded ? 'warning' : sourceReady ? 'active' : 'idle');
    var relations = {common_rise: '环境变亮', common_fall: '环境变暗', front_only: '正面变化', back_only: '正面优先', divergent: '方向不一致'};
    set('filter-label', s.sensor_stale === '1' || s.sensor_hold_active === '1' ? '保持亮度' : s.scene_hold_active === '1' ? '单侧确认中' : s.low_lux_bright_spike_guard === '1' ? '过滤突亮' : s.fast_dark_candidate === '1' ? '突暗确认中' : s.fast_dark === '1' ? '突暗已确认' : relations[s.scene_relation] || '平滑滤波');
    $('filter-wire').setAttribute('data-active', String(sourceReady));
    $('filter-wire').setAttribute('data-blocked', String(guarded && !stale));
    $('sunlight-gate').setAttribute('data-state', stale || s.sunlight_active === undefined ? 'unknown' : s.sunlight_active === '1' ? 'active' : 'idle');
    set('scene-sunlight', s.sunlight_active === '1' ? (s.hbm_active === '1' ? '增强 + HBM' : '增强中') : s.sunlight_active === '0' ? '未触发' : '未上报');
    $('thermal-gate').setAttribute('data-state', stale || s.heat_guard_active === undefined ? 'unknown' : s.heat_guard_active === '1' ? 'warning' : 'idle');
    var trusted = Number(s.thermal_trusted_temp), suspect = Number(s.thermal_suspect_temp);
    var thermalLabel = s.thermal_enabled === '0' ? '已关闭' : s.heat_guard_active === '1' ? '高温限亮' :
      trusted > 0 ? (trusted / 1000).toFixed(1) + '℃' : suspect > 0 ? '辅助 ' + (suspect / 1000).toFixed(1) + '℃' :
      s.thermal_scan_done === '0' ? '等待扫描' : Number(s.thermal_zone_count) > 0 ? '节点不可读' :
      s.thermal_scan_done === '1' ? '未找到温度源' : '温度未上报';
    set('scene-thermal', thermalLabel);
    $('thermal-gate').title = trusted > 0 ? '电池、机身等温度源；达到门槛才限制亮度。' :
      suspect > 0 ? '仅有屏幕等辅助温度，需持续确认；不会当作可信电池温度。' :
      '没有可用温度读数，不能据此判断设备凉爽。详细读数可查看温度源与过滤原因。';
    $('confirm-gate').setAttribute('data-state', paused || s.target_hold_active === undefined ? 'unknown' : s.target_hold_active === '1' ? 'warning' : 'active');
    set('scene-confirm', paused ? '等待接管' : s.target_hold_active === '1' ? '等待稳定' : s.target_hold_active === '0' ? '允许调节' : '状态未知');
    $('confirm-gate').title = '照度变化先经过防抖确认，避免短暂遮挡或抖动立即改变亮度。允许调节不等于已到达目标亮度。';
    $('confirm-symbol').setAttribute('d', s.target_hold_active === '1' ? 'M8 5v14M16 5v14' : 'M5 12l4 4L19 6');
    var canWrite = !paused && s.screen === '1' && s.brightness_owner === 'daemon' && s.daemon_can_write === '1';
    $('output-wire').setAttribute('data-active', String(canWrite && s.transition_active === '1' && s.target_hold_active !== '1'));
    $('output-wire').setAttribute('data-blocked', String(!paused && (s.target_hold_active === '1' || s.daemon_can_write === '0')));
    $('engine-scene').setAttribute('data-idle', String(paused || !canWrite));
    var maximum = Number(s.max_br), target = effectiveTarget(s), current = Number(s.current_br), lux = Number(s.smooth);
    var valid = maximum > 0 && s.target_br !== undefined && s.current_br !== undefined && s.smooth !== undefined && isFinite(target) && isFinite(current) && isFinite(lux) && lux >= 0;
    $('response-target').setAttribute('visibility', valid ? 'visible' : 'hidden'); $('response-current').setAttribute('visibility', valid ? 'visible' : 'hidden');
    $('response-trail').setAttribute('points', ''); drawCurve('response', curveFromState(s));
    set('meter-current', valid ? (current / maximum * 100).toFixed(1) + '%' : '—');
    if (valid) {
      var x = responseX(lux), y = responseY(target / maximum * 100), cy = responseY(current / maximum * 100);
      $('response-target').setAttribute('cx', x.toFixed(1)); $('response-target').setAttribute('cy', y.toFixed(1));
      $('response-current').setAttribute('cx', x.toFixed(1)); $('response-current').setAttribute('cy', cy.toFixed(1));
      $('response-gap').setAttribute('d', 'M' + x.toFixed(1) + ' ' + cy.toFixed(1) + 'V' + y.toFixed(1));
      $('response-guide').setAttribute('d', 'M32 ' + y.toFixed(1) + 'H' + x.toFixed(1) + 'V120');
      var pct = Math.max(0, Math.min(100, current / maximum * 100)), level = Math.round(38 + 205 * pct / 100);
      $('engine-scene').style.setProperty('--display-fill', 'rgb(' + level + ',' + Math.min(255, level + 7) + ',' + Math.min(255, level + 16) + ')');
      $('engine-scene').style.setProperty('--display-ink', pct < 30 ? '#f4f6fa' : '#1d2a3c');
      $('target-marker').hidden = false; $('target-marker').style.left = Math.max(0, Math.min(100, target / maximum * 100)) + '%';
    } else { $('response-gap').setAttribute('d', ''); $('response-guide').setAttribute('d', ''); $('target-marker').hidden = true; }
    set('screen-action', stale ? '等待新状态' : paused ? '系统调节' : s.brightness_owner === 'wake_readonly' ? '系统先调节' : guarded || s.lux_valid === '0' || s.target_hold_active === '1' ? '保持亮度' : !canWrite ? '等待调节' : s.transition_active === '1' ? (target > current ? '变亮中' : target < current ? '变暗中' : '收尾中') : '已稳定');
  }
  function renderState(s) {
    lastState = s;
    var maximum = Number(s.max_br), current = Number(s.current_br), target = effectiveTarget(s);
    var pct = maximum > 0 && isFinite(current) ? Math.max(0, Math.min(100, current / maximum * 100)) : NaN;
    set('brightness', isFinite(pct) ? pct.toFixed(1) : '—');
    set('target', maximum > 0 && isFinite(target) ? (target / maximum * 100).toFixed(1) + '%' : '—');
    set('flow-target', $('target').textContent); set('flow-current', isFinite(pct) ? pct.toFixed(1) + '%' : '—');
    set('flow-relation', maximum > 0 && isFinite(target) && isFinite(current) ?
      (target > current ? '正在变亮' : target < current ? '正在变暗' : '亮度已稳定') + ' · 当前背光 ' + current + ' → 目标 ' + target + ' / 最大 ' + maximum : '等待背光数据');
    renderSensors(s);
    $('brightness-meter').firstElementChild.style.width = (isFinite(pct) ? pct : 0) + '%';
    if (isFinite(pct)) $('brightness-meter').setAttribute('aria-valuenow', pct.toFixed(1));
    else $('brightness-meter').removeAttribute('aria-valuenow');
    set('lux', format(s.lux, 1)); set('smooth', format(s.smooth, 1));
    set('mode', s.mode === 'auto' ? '自动亮度' : s.mode === 'manual' ? '手动亮度' : '—');
    set('owner', s.output_backend === 'hyperos4_framework' && s.brightness_owner === 'lock_failed_passthrough' ?
      '系统接管' : owners[s.brightness_owner] || s.brightness_owner || '—');
    set('sunlight', s.sunlight_active === '1' ? (s.hbm_active === '1' ? '增强 + HBM' : '增强中') : '未触发');
    set('thermal', s.heat_guard_active === '1' ? '限制亮度中' : '未触发');
    set('transition', s.transition_active === '1' ? '平滑调节中' : '稳定');
    var rate = {reactive: '快速追踪', stable: '稳定采样', active: '日常采样', normal: '日常采样'};
    set('sampling', rate[s.sensor_rate_mode] || ((s.target_poll_ms || s.poll_ms || '—') + ' ms'));
    var updated = Number(s.updated_unix), age = updated > 0 ? Math.max(0, Math.floor(Date.now() / 1000 - updated)) : -1;
    var stale = !demo && (age < 0 || age > 45);
    renderTrend(s, !stale && s._ui_paused !== '1');
    set('state-age', demo ? '示例数据' : age < 0 ? '更新时间未知' : age < 2 ? '刚刚更新' : age + ' 秒前更新');
    var description = '环境光变化时，屏幕会自动跟随。', heading = '亮度已稳定', status = '运行中', warning = false;
    if (s._ui_paused === '1') { status = '已暂停'; heading = '自动调节已暂停'; description = '当前亮度由系统调节，可在设置中恢复。'; warning = true; }
    else if (stale) { status = '状态过期'; heading = '等待引擎更新'; description = '暂未收到新状态，请检查模块是否运行。'; warning = true; }
    else if (s.mode === 'manual') { status = '手动模式'; heading = '你正在手动调节亮度'; description = '开启系统自动亮度后，流光亮度会继续运行。'; }
    else if (s.screen === '0' || s.brightness_owner === 'screen_off_passthrough') { status = '屏幕休眠'; heading = '屏幕已熄灭'; description = '当前由系统控制，等待再次亮屏。'; }
    else if (s.brightness_owner === 'wake_readonly') { status = '唤醒观察'; heading = '刚刚亮屏'; description = '先保留系统亮度，等光感读数稳定后再接管。'; }
    else if (s.brightness_owner === 'lock_failed_passthrough') {
      status = s.output_backend === 'hyperos4_framework' ? '框架未接管' : '权限受限';
      heading = '当前由系统调节亮度';
      description = s.output_backend === 'hyperos4_framework' ? '框架输出暂未满足接管条件，系统继续调节。' : '未能取得背光写入权限，请检查模块运行情况。';
      warning = true;
    }
    else if (s.output_backend === 'hyperos4_framework' && s.brightness_owner === 'daemon' && s.framework_owned === '0') {
      status = '等待接管'; heading = '当前由系统调节亮度'; description = '正在核对主屏状态，稍后继续接管。';
    }
    else if (s.brightness_owner !== 'daemon') { heading = '当前由系统调节亮度'; description = '流光亮度正在等待接管。'; }
    else if (s.heat_guard_active === '1') { heading = '正在限制亮度'; description = '温度偏高，屏幕暂时调暗，降温后恢复。'; }
    else if (s.framework_user_hold === '1') { heading = '保持你的亮度'; description = '已按你的手动设置保持；环境明显变化或锁屏后恢复自动曲线。'; }
    else if (s.sensor_stale === '1' || s.sensor_hold_active === '1' || s.lux_valid === '0') { heading = '暂时保持亮度'; description = '当前读数尚不能确认环境变化，先保持亮度。'; }
    else if (s.low_lux_bright_spike_guard === '1') { heading = '正在确认环境变化'; description = '光感突然变亮，先确认一下，避免屏幕误升亮。'; }
    else if (s.fast_dark_candidate === '1') { heading = '正在确认环境变化'; description = '光感突然变暗，确认后再降低亮度。'; }
    else if (s.target_hold_active === '1') { heading = '正在确认新的亮度'; description = '先保持原亮度，避免屏幕来回调整。'; }
    else if (s.output_backend === 'hyperos4_framework' && s.framework_owned === '1' && Number(s.target_br) > target + Math.max(32, maximum * .01)) {
      heading = Math.abs(target - current) < Math.max(32, maximum * .005) ? '已达系统当前上限' : '正在接近可用亮度';
      description = '曲线希望更亮，系统当前亮度范围限制了目标。';
    }
    else if (s.daemon_can_write === '0') { heading = '暂时保持当前亮度'; description = '正在等待背光写入条件，之后继续调节。'; }
    else if (s.fast_dark === '1') { heading = '环境变暗了'; description = '屏幕正在加快降低亮度。'; }
    else if (s.sunlight_active === '1') { heading = '阳光增强已开启'; description = '环境持续强光，正在提高屏幕亮度。'; }
    else if (s.transition_active === '1') { heading = target > current ? '屏幕正在变亮' : target < current ? '屏幕正在变暗' : '即将完成调节'; description = '亮度正平滑靠近目标，减少突兀变化。'; }
    set('scene-heading', heading); set('explanation', description); set('connection', demo ? '示例状态' : status);
    if (s._ui_paused === '1' || stale || s.mode !== 'auto' || s.screen === '0') set('flow-relation', '当前由系统控制或等待数据，以下保留最近一次目标。');
    else if (s.target_hold_active === '1') set('flow-relation', '候选亮度正在确认，暂时保持原目标。');
    else if (s.daemon_can_write === '0') set('flow-relation', '暂缓写入，待观察窗或控制权限制解除后调节。');
    $('connection').className = 'badge' + (warning || demo ? ' warn' : '');
    renderScene(s, stale);
    $('diagnostics').textContent = stateText || '暂无原始状态';
  }
  function unavailable(error, paused) {
    stateText = ''; lastState = null; history = []; historyStamp = '';
    $('engine-scene').setAttribute('data-idle', 'true'); $('engine-scene').style.removeProperty('--display-fill'); $('engine-scene').style.removeProperty('--display-ink');
    document.querySelectorAll('.signal-wire').forEach(function (wire) { wire.setAttribute('data-active', 'false'); wire.setAttribute('data-blocked', 'false'); });
    ['front-node','back-node'].forEach(function (id) { $(id).setAttribute('data-active', 'false'); });
    ['filter-node','sunlight-gate','thermal-gate','confirm-gate'].forEach(function (id) { $(id).setAttribute('data-state', 'unknown'); });
    ['scene-front','scene-back','scene-lux','scene-smooth','scene-sunlight','scene-thermal','scene-confirm'].forEach(function (id) { set(id, '—'); });
    set('filter-label', '等待采样'); set('scene-source', '环境光'); set('screen-action', '等待数据');
    $('response-trail').setAttribute('points', ''); $('response-gap').setAttribute('d', ''); $('response-guide').setAttribute('d', '');
    $('response-target').setAttribute('visibility', 'hidden'); $('response-current').setAttribute('visibility', 'hidden'); $('target-marker').hidden = true;
    $('raw-dot').setAttribute('visibility', 'hidden'); $('smooth-dot').setAttribute('visibility', 'hidden');
    $('raw-trend').setAttribute('points', ''); $('smooth-trend').setAttribute('points', ''); set('trend-scale', '等待新照度数据');
    set('connection', paused ? '已暂停' : device ? '状态不可用' : '未连接设备'); $('connection').className = 'badge warn';
    set('scene-heading', paused ? '自动调节已暂停' : device ? '等待连接引擎' : '未连接设备');
    set('explanation', paused ? '模块保留了此前的暂停状态。请在设置页恢复引擎。' : device ? '无法读取状态。请确认模块已启动，页面会自动重试。' : '请从模块管理器打开 WebUI。浏览器中可查看界面示例。');
    set('state-age', '未读取到有效状态');
    ['brightness', 'target', 'lux', 'smooth', 'mode', 'owner', 'sunlight', 'thermal', 'transition', 'sampling', 'flow-target', 'flow-current', 'sensor-health', 'sensor-source', 'sensor-age', 'front-lux', 'back-lux', 'front-age', 'back-age', 'sensor-interval', 'loop-interval', 'device-temperature', 'device-battery', 'write-permission', 'target-confirmation'].forEach(function (id) { set(id, '—'); });
    set('flow-relation', '等待环境光和背光数据…');
    $('brightness-meter').firstElementChild.style.width = '0%'; $('brightness-meter').removeAttribute('aria-valuenow');
    set('diagnostics', error || '未读取到设备状态');
  }
  function pollState() {
    if (demo) { renderState(lastState); return Promise.resolve(); }
    if (!device || stateBusy) return Promise.resolve();
    stateBusy = true;
    var watch = active === 'status' && !document.hidden ? ': > ' + quote(BASE + '_ui_watch') + '; ' : '';
    return exec(watch + 'cat ' + quote(BASE + '_state') + ' && printf "\\n_ui_paused=" && if [ -f ' + quote(BASE + '.paused') + ' ]; then printf 1; else printf 0; fi').then(function (text) {
      var s = parse(text); if (!s.mode || s.current_br === undefined) throw new Error('状态内容不完整');
      stateText = text; renderState(s);
    }).catch(function (e) {
      return exec('if [ -f ' + quote(BASE + '.paused') + ' ]; then printf paused; else printf active; fi').then(function (status) {
        unavailable(e.message, status.trim() === 'paused');
      }).catch(function () { unavailable(e.message); });
    }).finally(function () { stateBusy = false; });
  }
  function parseCurve(text) {
    var values = typeof text === 'string' ? text.split(',').map(Number) : [];
    return window.LumaCurveMath.valid(values) ? values : window.LumaCurveMath.defaults.slice();
  }
  function curveFromState(s) {
    var values = parse(configText);
    return {points: parseCurve(s.curve_points || (values.curve_custom === '1' ? values.curve_points : undefined)),
      learnedPoints: parseCurve(s.curve_learned_points || s.curve_points),
      gamma: Number(s.curve_gamma || values.manual_gamma || 2.2), circadian: Number(s.curve_circadian_factor || 1),
      offset: Number(s.preference_effective_offset || 0),
      threshold: Number(s.curve_sunlight_threshold || values.high_lux_threshold || 5000),
      extreme: Number(s.curve_sunlight_extreme || values.high_lux_extreme_lux || 30000),
      boost: Number(s.curve_sunlight_boost === undefined ? (values.high_lux_boost_max || .5) : s.curve_sunlight_boost) * 100,
      minimum: Number(s.curve_sunlight_min || values.high_lux_min_pct || .9) * 100};
  }
  function drawCurve(prefix, config) {
    var base = [], biased = [], sun = [];
    for (var i = 0; i <= 160; i++) {
      var value = Math.pow(100001,i/160)-1, x = responseX(value).toFixed(2);
      base.push(x + ',' + responseY(window.LumaCurveMath.base(value,config.points,config.gamma)*(config.circadian || 1)).toFixed(2));
      biased.push(x + ',' + responseY(window.LumaCurveMath.preference(value,config)).toFixed(2));
      var enhanced = window.LumaCurveMath.sunlight(value,config);
      if (enhanced !== null) sun.push(x + ',' + responseY(enhanced).toFixed(2));
    }
    if (config.threshold <= 100000) sun.unshift(responseX(config.threshold).toFixed(2)+','+responseY(window.LumaCurveMath.sunlight(config.threshold,config)).toFixed(2));
    $(prefix + '-base').setAttribute('points',base.join(' '));
    $(prefix + '-preference').setAttribute('points',biased.join(' '));
    $(prefix + '-sunlight').setAttribute('points',sun.join(' '));
  }
  function renderCurveEditor() {
    if (!inputFor('manual_gamma')) return;
    var config = {points:curveCustom ? curveDraft : window.LumaCurveMath.defaults,
      learnedPoints:learnedDraft || (curveCustom ? curveDraft : window.LumaCurveMath.defaults),
      gamma:Number(inputFor('manual_gamma').value), offset:learnedDraft ? 0 : preferenceRecord ? Number(preferenceRecord.offset || 0) : Number(parse(configText).preference_offset || 0),
      threshold:Number(inputFor('high_lux_threshold').value), extreme:Number(inputFor('high_lux_extreme_lux').value),
      boost:Number(inputFor('high_lux_boost_max').value), minimum:Number(inputFor('high_lux_min_pct').value)};
    drawCurve('editor',config);
    var selected = Number($('curve-anchor').value || 0), group = $('curve-anchors'); group.textContent = '';
    (learnedDraft || curveDraft).forEach(function (point,index) {
      var circle = document.createElementNS('http://www.w3.org/2000/svg','circle');
      circle.setAttribute('cx',responseX(window.LumaCurveMath.lux[index])); circle.setAttribute('cy',responseY(point));
      circle.setAttribute('r',index === selected ? 4.5 : 3); circle.setAttribute('data-selected',String(index === selected)); group.appendChild(circle);
    });
    if (!curvePointEditing) { $('curve-value').value = (learnedDraft || curveDraft)[selected]; $('curve-range').value = (learnedDraft || curveDraft)[selected]; }
    ['curve-value','curve-range'].forEach(function (id) { $(id).min = .1; $(id).max = 100; });
    ['curve-anchor','curve-value','curve-range','curve-apply','curve-default','preset-name','cfg-preference_learning'].forEach(function (id) { if ($(id)) $(id).disabled = !device || !configReady || configBusy || presetBusy; });
    ['preset-save','preset-load','preset-delete','preset-select'].forEach(function (id) { $(id).disabled = !device || !configReady || configBusy || presetBusy || !presetsReady; });
  }
  function refreshPreference() {
    if (!device || !configReady || configBusy || preferenceBusy) return;
    preferenceBusy = true;
    exec('cat ' + quote(BASE + '_preference') + ' 2>/dev/null || true').then(function (text) {
      var data = parse(text), count = Number(data.samples), config = parse(configText);
      var base = typeof data.base_points === 'string' ? data.base_points.split(',').map(Number) : [], points = typeof data.learned_points === 'string' ? data.learned_points.split(',').map(Number) : [];
      var expected = config.curve_custom === '1' ? parseCurve(config.curve_points) : window.LumaCurveMath.defaults;
      var valid = data.format === '2' && window.LumaCurveMath.valid(base) && window.LumaCurveMath.valid(points) &&
        data.offset !== undefined && Number.isFinite(Number(data.offset)) && Number(data.offset) >= -50 && Number(data.offset) <= 100 &&
        Number.isInteger(count) && count >= 0 && count <= 2147483647 &&
        Number(data.config_revision) === Number(config.preference_revision || 0) &&
        Number(data.config_offset) === Number(config.preference_offset || 0) &&
        base.every(function (v,i) { return Math.abs(v-expected[i]) < .00001 && points[i] >= Math.max(.1,v*.8)-.00001 && points[i] <= Math.min(100,v*1.2)+.00001; });
      preferenceRecord = valid ? data : null;
      if (!preferenceEdited && !curvePointEditing) learnedDraft = valid ? points.map(function (point) { return Math.max(.1,Math.min(100,point*(1+Number(data.offset || 0)/100))); }) : null;
      set('preference-status', (inputFor('preference_learning').checked ? '缓慢学习已开启' : '学习已关闭，保留现有曲线') + ' · 已学习 ' + (valid ? count : 0) + ' 次');
      if (lastState && lastState.preference_evidence === 'unavailable') $('preference-status').textContent += ' · 系统未提供手动记录';
      renderCurveEditor();
    }).catch(function () { set('preference-status','学习记录读取失败，已保留当前曲线'); }).finally(function () { preferenceBusy = false; });
  }
  var presetKeys = ['preference_learning','manual_gamma','high_lux_threshold','high_lux_extreme_lux','high_lux_boost_max','high_lux_min_pct','high_lux_max_active_ms','high_lux_cooldown_ms','high_lux_temp_thresh','high_lux_temp_resume','high_lux_bat_thresh','high_lux_hbm_enable'];
  function validPreset(entry) {
    return entry && typeof entry.name === 'string' && entry.name.trim().length > 0 && entry.name.length <= 40 &&
      typeof entry.custom === 'boolean' && window.LumaCurveMath.valid(entry.points) && entry.values && presetKeys.every(function (key) {
        var f = key === 'preference_learning' ? ['preference_learning','','','bool'] : fields.find(function (field) { return field[0] === key; }), v = entry.values[key], displayed = v * (f[6] || 1);
        return Number.isFinite(v) && (f[3] === 'bool' ? v === 0 || v === 1 : displayed >= f[3] && displayed <= f[4] &&
          (f[6] !== .001 && key !== 'high_lux_bat_thresh' || Number.isInteger(v)));
      }) && entry.values.high_lux_extreme_lux >= entry.values.high_lux_threshold+100 &&
      entry.values.high_lux_temp_resume < entry.values.high_lux_temp_thresh &&
      (entry.values.high_lux_max_active_ms === 0 || entry.values.high_lux_max_active_ms >= 3000);
  }
  function showPresets(selected) {
    var select = $('preset-select'); select.textContent = '';
    if (!presets.length) { var empty = document.createElement('option'); empty.value = ''; empty.textContent = '暂无预设'; select.appendChild(empty); }
    presets.forEach(function (entry,index) { var option = document.createElement('option'); option.value = index; option.textContent = entry.name; select.appendChild(option); });
    if (selected !== undefined && selected < presets.length) select.value = selected;
    renderCurveEditor();
  }
  function readPresets() {
    if (!device || !configReady || presetBusy) return;
    presetBusy = true;
    exec('if [ -f ' + quote(BASE + '_presets.json') + ' ]; then head -c 32769 ' + quote(BASE + '_presets.json') + '; fi').then(function (text) {
      if (new TextEncoder().encode(text).length > 32768) throw new Error('预设文件过大');
      var data = text.trim() ? JSON.parse(text) : {format:1,entries:[]};
      if (data.format !== 1 || !Array.isArray(data.entries) || data.entries.length > 16 || !data.entries.every(validPreset) || new Set(data.entries.map(function (e) { return e.name; })).size !== data.entries.length) throw new Error('预设格式无效');
      presetText = text; presets = data.entries; presetsReady = true; showPresets();
    }).catch(function (e) { presetsReady = false; set('preset-status','读取失败：'+e.message); }).finally(function () { presetBusy = false; renderCurveEditor(); });
  }
  function savePresets(next, selected) {
    if (!device || presetBusy || !presetsReady || configBusy) return;
    var path = BASE + '_presets.json', pending = path + '.tmp-ui-' + Date.now() + '-' + Math.random().toString(36).slice(2), text = JSON.stringify({format:1,entries:next});
    var command = 'umask 077; trap '+quote('rm -f '+quote(pending))+' EXIT; current=""; '+
      'if [ -f '+quote(path)+' ]; then raw=$(head -c 32769 '+quote(path)+') || exit 1; current=$(printf %s "$raw" | base64) || exit 1; fi; '+
      'current=$(printf %s "$current" | tr -d '+quote('\\r\\n')+'); '+
      '[ "$current" = '+quote(base64(presetText.trimEnd()))+' ] || { echo "预设已被修改，请重新读取设置" >&2; exit 1; }; '+
      'printf %s '+quote(base64(text))+' | base64 -d > '+quote(pending)+' && chmod 0600 '+quote(pending)+' && mv -f '+quote(pending)+' '+quote(path);
    presetBusy = true; renderCurveEditor();
    exec(command).then(function () { presetText = text; presets = next; showPresets(selected); set('preset-status','预设已保存；运行配置不会因此改变。'); })
      .catch(function (e) { set('preset-status','保存失败：'+e.message); }).finally(function () { presetBusy = false; renderCurveEditor(); });
  }
  function inputFor(key) { return $('cfg-' + key); }
  function displayedDefault(f) { return f[3] === 'bool' ? (defaults[f[0]] ? '启用' : '关闭') : f[3] === 'text' ? (defaults[f[0]] || '自动检测') : Number((defaults[f[0]] * f[6]).toFixed(6)) + (f[7] || ''); }
  function updateField(f) {
    var input = inputFor(f[0]);
    set('edit-' + f[0], f[3] === 'bool' ? (input.checked ? '启用' : '关闭') : f[3] === 'text' ? (input.value || '自动检测') : input.value + (f[7] || ''));
  }
  function openEditor(f) {
    if (!device || !configReady || configBusy) return;
    editing = f; editorTrigger = $('edit-' + f[0]);
    set('editor-title', f[1]); set('editor-hint', f[2]); set('editor-recommended', '建议：' + recommended[f[0]] + ' · 默认：' + displayedDefault(f)); set('editor-error', '');
    $('editor-numeric').hidden = typeof f[3] !== 'number'; $('editor-toggle-row').hidden = f[3] !== 'bool'; $('editor-text-row').hidden = f[3] !== 'text';
    if (typeof f[3] === 'number') {
      ['editor-range', 'editor-number'].forEach(function (id) { $(id).min = f[3]; $(id).max = f[4]; $(id).step = f[5]; $(id).value = inputFor(f[0]).value; });
      set('editor-output', inputFor(f[0]).value + (f[7] || '')); set('editor-min', f[3] + (f[7] || '')); set('editor-max', f[4] + (f[7] || '')); set('editor-unit', f[7] || '');
    } else if (f[3] === 'bool') $('editor-toggle').checked = inputFor(f[0]).checked;
    else { $('editor-text').value = inputFor(f[0]).value; $('editor-text').maxLength = f[0] === 'hbm_node_path' ? 255 : 31; }
    document.documentElement.classList.add('dialog-open');
    $('setting-dialog').showModal(); $('editor-cancel').focus(); syncViewport();
  }
  $('editor-range').addEventListener('input', function () { $('editor-number').value = this.value; set('editor-output', this.value + (editing[7] || '')); set('editor-error', ''); });
  $('editor-number').addEventListener('input', function () { $('editor-range').value = this.value; set('editor-output', this.value + (editing[7] || '')); set('editor-error', ''); });
  $('editor-default').addEventListener('click', function () {
    if (!editing) return;
    if (typeof editing[3] === 'number') { $('editor-number').value = Number((defaults[editing[0]] * editing[6]).toFixed(6)); $('editor-number').dispatchEvent(new Event('input')); }
    else if (editing[3] === 'bool') $('editor-toggle').checked = !!defaults[editing[0]];
    else $('editor-text').value = defaults[editing[0]];
    set('editor-error', '');
  });
  $('editor-cancel').addEventListener('click', function () { $('setting-dialog').close(); });
  $('setting-dialog').addEventListener('close', function () { editing = null; keyboardPointer = false; document.documentElement.classList.remove('dialog-open'); set('config-error', ''); fields.forEach(function (f) { $('edit-' + f[0]).removeAttribute('aria-invalid'); }); if (editorTrigger) editorTrigger.focus(); syncViewport(); });
  $('editor-form').addEventListener('submit', function (event) {
    event.preventDefault(); if (!editing || configBusy) return;
    var f = editing, input = inputFor(f[0]), before = input.value, checked = input.checked;
    if (f[3] === 'bool') input.checked = $('editor-toggle').checked;
    else input.value = f[3] === 'text' ? $('editor-text').value.trim() : $('editor-number').value;
    if (!collect()) { input.value = before; input.checked = checked; set('editor-error', $('config-error').textContent); return; }
    updateField(f); markDirty(); renderCurveEditor(); $('setting-dialog').close();
  });
  function renderForm(data) {
    var container = $('config-fields'); container.textContent = '';
    inputFor('preference_learning').checked = String(data.preference_learning === undefined ? 1 : data.preference_learning) === '1';
    inputFor('preference_learning').onchange = function () { markDirty(); refreshPreference(); };
    groups.forEach(function (group) {
      var section = document.createElement(group.open ? 'section' : 'details'); section.className = 'config-group';
      if (group.open) { var title = document.createElement('h2'); title.textContent = group.title; section.appendChild(title); var p = document.createElement('p'); p.className = 'caption'; p.textContent = group.hint; section.appendChild(p); }
      else { var summary = document.createElement('summary'); summary.textContent = group.title; var small = document.createElement('small'); small.textContent = group.hint; summary.appendChild(small); section.appendChild(summary); }
      var body = document.createElement('div'); body.className = 'fields';
      group.fields.forEach(function (f) {
        var row = document.createElement('div'); row.className = 'field';
        var copy = document.createElement('div'); var label = document.createElement('label'); label.className = 'field-title'; label.htmlFor = 'edit-' + f[0]; label.textContent = f[1];
        var hint = document.createElement('span'); hint.className = 'hint'; hint.id = 'hint-' + f[0]; hint.textContent = f[2]; copy.appendChild(label); copy.appendChild(hint);
        var range = document.createElement('span'); range.className = 'recommendation'; range.textContent = '建议：' + recommended[f[0]]; copy.appendChild(range);
        var control = document.createElement('div'); control.className = 'field-control'; var input = document.createElement('input'); input.id = 'cfg-' + f[0]; input.name = f[0]; input.type = 'hidden';
        var value = data[f[0]] === undefined ? defaults[f[0]] : data[f[0]];
        if (f[3] === 'bool') { input.checked = String(value) === '1'; input.value = input.checked ? '1' : '0'; }
        else input.value = f[3] === 'text' ? String(value) : String(Number((Number(value) * f[6]).toFixed(6)));
        control.appendChild(input);
        var button = document.createElement('button'); button.type = 'button'; button.id = 'edit-' + f[0]; button.className = 'field-value'; button.setAttribute('aria-label', '编辑' + f[1]); button.setAttribute('aria-haspopup', 'dialog'); button.setAttribute('aria-describedby', hint.id);
        button.addEventListener('click', function () { openEditor(f); }); control.appendChild(button);
        row.appendChild(copy); row.appendChild(control); body.appendChild(row);
      });
      section.appendChild(body); container.appendChild(section);
    });
    fields.forEach(updateField); set('dirty-status', '未修改'); set('config-error', ''); syncButtons();
  }
  function syncButtons() {
    renderCurveEditor();
    $('save-config').disabled = !device || !configReady || configBusy;
    $('restore-defaults').disabled = !device || !configReady || configBusy;
    $('reload-config').disabled = !device || configBusy;
    document.querySelectorAll('[data-command],#clear-log,#save-log-policy').forEach(function (b) { b.disabled = !device || commandBusy || configBusy; });
    fields.forEach(function (f) { var button = $('edit-' + f[0]); if (button) button.disabled = !device || !configReady || configBusy; });
    $('export-log').disabled = !device || exportBusy;
  }
  function markDirty() { set('dirty-status', '有未保存的修改'); set('config-error', ''); }
  function readConfigText() {
    // Some manager bridges trim stdout or rebuild its line endings. Transport
    // bytes as base64 so the optimistic comparison uses the actual file bytes.
    return exec('base64 ' + quote(BASE + '.conf')).then(function (encoded) {
      try {
        return decodeURIComponent(Array.from(atob(encoded.replace(/\s/g, '')), function (c) {
          return '%' + c.charCodeAt(0).toString(16).padStart(2, '0');
        }).join(''));
      } catch (error) { throw new Error('配置内容不是有效的 UTF-8 或读取结果不完整'); }
    });
  }
  function readConfig() {
    if (configBusy) return Promise.resolve();
    if (!device) { set('config-notice', '浏览器预览仅显示默认设置；设备操作不可用。'); renderForm(defaults); return Promise.resolve(); }
    configBusy = true; syncButtons();
    return readConfigText().then(function (text) {
      var values = parse(text); if (values.config_version !== CONFIG_VERSION) throw new Error('配置版本不兼容，暂不允许保存');
      configText = text; configReady = true; configBusy = false; preferenceEdited = false; renderForm(values); syncLogDays(values.log_retention_days);
      learnedDraft = null; curvePointEditing = false; curveDraft = parseCurve(values.curve_points); curveCustom = values.curve_custom === '1'; renderCurveEditor(); refreshPreference(); readPresets();
      $('config-notice').hidden = true;
    }).catch(function (e) { configReady = false; $('config-notice').hidden = false; set('config-notice', '读取失败：' + e.message); }).finally(function () { configBusy = false; syncButtons(); });
  }
  function configError(message, key) {
    var input = key && $('edit-' + key);
    if (input) { input.setAttribute('aria-invalid', 'true'); var details = input.closest('details'); if (details) details.open = true; if (!$('setting-dialog').open) input.focus(); }
    set('config-error', message); return null;
  }
  function collect() {
    var values = {}, error = null;
    fields.forEach(function (f) {
      var input = inputFor(f[0]); $('edit-' + f[0]).removeAttribute('aria-invalid');
      if (f[3] === 'bool') values[f[0]] = input.checked ? 1 : 0;
      else if (f[3] === 'text') {
        values[f[0]] = input.value.trim();
        if (/\r|\n/.test(values[f[0]]) || values[f[0]].length > (f[0] === 'hbm_node_path' ? 255 : 31) || (f[0] !== 'hbm_node_path' && !values[f[0]])) error = error || [f[1] + '格式不正确', f[0]];
        if (f[0] === 'hbm_node_path' && values[f[0]] && !/^\/sys\//.test(values[f[0]])) error = error || ['HBM 路径必须位于 /sys/ 下', f[0]];
      } else {
        var number = Number(input.value);
        if (!input.value.trim() || !isFinite(number) || number < f[3] || number > f[4]) error = error || [f[1] + '应在 ' + f[3] + '–' + f[4] + ' ' + f[7] + '之间', f[0]];
        var raw = Number((number / f[6]).toFixed(6));
        if ((f[6] === 0.001 || f[0] === 'min_step' || f[0] === 'high_lux_bat_thresh') && !Number.isInteger(raw)) error = error || [f[1] + '换算后必须为整数', f[0]];
        values[f[0]] = raw;
      }
    });
    if (error) return configError(error[0], error[1]);
    if (values.thermal_hard_resume >= values.thermal_hard_trigger) return configError('温控恢复温度必须低于触发温度', 'thermal_hard_resume');
    if (values.high_lux_temp_resume >= values.high_lux_temp_thresh) return configError('阳光恢复温度必须低于停止增强的温度', 'high_lux_temp_resume');
    if (values.high_lux_extreme_lux < values.high_lux_threshold + 100) return configError('最大增强照度至少比开始照度高 100 lux', 'high_lux_extreme_lux');
    if (values.hyst_high < values.hyst_low) return configError('高照度滞回不得小于低照度滞回', 'hyst_high');
    if (values.high_lux_max_active_ms !== 0 && values.high_lux_max_active_ms < 3000) return configError('单次持续时间应为 0，或至少 3 秒', 'high_lux_max_active_ms');
    return values;
  }
  function serialize(values) {
    var original = parse(configText);
    values.preference_learning = inputFor('preference_learning').checked ? 1 : 0;
    if (preferenceEdited) { values.preference_offset = 0; values.preference_revision = (Number(original.preference_revision || 0) + 1) % 2147483648; }
    values.curve_custom = curveCustom ? 1 : 0; values.curve_points = curveDraft.join(',');
    var seen = {}, lines = configText.replace(/\r\n/g, '\n').split('\n').map(function (line) {
      var match = line.match(/^\s*([^#=\s]+)\s*=/);
      if (match && Object.prototype.hasOwnProperty.call(values, match[1])) { seen[match[1]] = true; return match[1] + '=' + values[match[1]]; }
      return line;
    });
    Object.keys(values).forEach(function (key) { if (!seen[key]) lines.push(key + '=' + values[key]); });
    return lines.join('\n').replace(/\n*$/, '\n');
  }
  function base64(text) { return btoa(encodeURIComponent(text).replace(/%([0-9A-F]{2})/g, function (_, hex) { return String.fromCharCode(parseInt(hex, 16)); })); }
  function atomicConfigWrite(text) {
    var path = BASE + '.conf', token = Date.now() + '-' + Math.random().toString(36).slice(2);
    var pending = path + '.tmp-ui-' + token;
    // Compare the exact previously read file before replacing; preserve edits by another writer.
    var command = 'umask 077; trap ' + quote('rm -f ' + quote(pending)) + ' EXIT; ' +
      'current=$(base64 ' + quote(path) + ') || { echo "无法读取配置文件进行校验" >&2; exit 1; }; ' +
      'current=$(printf %s "$current" | tr -d ' + quote('\\r\\n') + ') || { echo "配置校验编码失败" >&2; exit 1; }; ' +
      '([ "$current" = ' + quote(base64(configText)) + ' ] || { echo "配置已被其他操作修改，请重新读取" >&2; exit 1; }) && ' +
      'printf %s ' + quote(base64(text)) + ' | base64 -d > ' + quote(pending) + ' && chmod 0600 ' + quote(pending) + ' && mv -f ' + quote(pending) + ' ' + quote(path);
    return exec(command);
  }
  function saveConfig(event) {
    event.preventDefault(); if (!configReady || configBusy || !device) return;
    var values = collect(); if (!values) return;
    var next = serialize(values); configBusy = true; syncButtons(); set('dirty-status', '保存中…');
    var wrote = false;
    atomicConfigWrite(next).then(function () {
      wrote = true; configText = next; return control('reload-config');
    }).then(function () { return readConfigText(); }).then(function (text) {
      if (text !== next) throw new Error('配置文件回读不一致，请重新读取');
      preferenceEdited = false; refreshPreference();
      set('dirty-status', '已保存并发送重载'); set('config-notice', '配置已保存并请求应用，可到状态页查看运行情况。'); toast('配置已保存');
    }).catch(function (e) {
      set('dirty-status', wrote ? '已保存，重载或确认未完成' : '保存未完成');
      set('config-error', (wrote ? '配置已写入，但后续操作未完成：' : '保存失败：') + e.message);
    }).finally(function () { configBusy = false; syncButtons(); });
  }
  function runCommand(command) {
    if (commandBusy || configBusy || !device) return;
    commandBusy = true; syncButtons(); set('command-status', '执行中…'); set('tools-feedback', '执行中…');
    var names = {pause: '已暂停', resume: '已恢复', restart: '重启请求已完成', 'prune-logs': '过期日志已整理', 'clear-log': '当前日志已清空'};
    control(command).then(function () { var text = names[command] || '操作完成'; set('command-status', text); set('tools-feedback', text); toast(text); return pollState(); }).catch(function (e) { set('command-status', '操作失败：' + e.message); set('tools-feedback', '操作失败：' + e.message); }).finally(function () { commandBusy = false; syncButtons(); if (active === 'tools') loadLog(); });
  }
  function loadLog(forceBottom) {
    if (!device) { set('log', '浏览器预览不读取设备日志。'); return; }
    var box = $('log'), follow = forceBottom === true || box.scrollHeight - box.scrollTop - box.clientHeight < 48;
    var previous = box.scrollTop;
    exec('sh ' + quote(CTL) + ' current-log').then(function (text) {
      logText = text;
      var lines = text.trimEnd().split('\n');
      set('log', text ? lines.slice(-150).join('\n') : '暂无本次开机日志');
      box.scrollTop = follow ? box.scrollHeight : previous; $('copy-log').disabled = !text;
    }).catch(function (e) { logText = ''; $('copy-log').disabled = true; set('log', '读取失败：' + e.message); });
  }
  function syncLogDays(value) {
    var number = Number(value === undefined ? 3 : value); if (!Number.isInteger(number) || number < 0 || number > 30) number = 3;
    var option = Array.from($('log-days').options).find(function (o) { return o.value === String(number); });
    if (!option) { option = document.createElement('option'); option.value = number; option.textContent = number + ' 天'; $('log-days').appendChild(option); }
    $('log-days').value = String(number);
  }
  function switchPanel(name) {
    if (['status', 'settings', 'tools', 'about'].indexOf(name) < 0) name = 'status'; active = name;
    document.querySelectorAll('.panel').forEach(function (p) { var selected = p.id === 'panel-' + name; p.hidden = !selected; p.classList.toggle('active', selected); });
    document.querySelectorAll('[data-panel]').forEach(function (b) { var selected = b.dataset.panel === name; b.classList.toggle('active', selected); if (selected) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current'); });
    document.documentElement.classList.toggle('status-page', name === 'status');
    document.documentElement.classList.toggle('about-page', name === 'about');
    syncStatusLayout();
    window.scrollTo(0, 0); if (name === 'status') pollState(); if (name === 'tools') { pollState(); loadLog(true); } if (name === 'settings') { refreshPreference(); renderCurveEditor(); }
  }
  document.querySelectorAll('[data-panel]').forEach(function (b) { b.addEventListener('click', function () { location.hash = b.dataset.panel; switchPanel(b.dataset.panel); }); });
  window.addEventListener('hashchange', function () { switchPanel(location.hash.slice(1)); });
  document.querySelectorAll('[data-command]').forEach(function (b) { b.addEventListener('click', function () { runCommand(b.dataset.command); }); });
  function syncStatusLayout() {
    var header = document.querySelector('.header').getBoundingClientRect();
    var notice = $('preview-notice').hidden ? null : $('preview-notice').getBoundingClientRect();
    var navigation = document.querySelector('.navigation').getBoundingClientRect();
    document.documentElement.style.setProperty('--status-top', Math.max(header.bottom, notice ? notice.bottom : 0) + 12 + 'px');
    document.documentElement.style.setProperty('--status-bottom', window.innerHeight - navigation.top + 12 + 'px');
  }
  if (window.ResizeObserver) {
    var layoutObserver = new ResizeObserver(syncStatusLayout);
    [document.querySelector('.header'), $('preview-notice'), document.querySelector('.navigation')].forEach(function (element) { layoutObserver.observe(element); });
  }
  window.addEventListener('resize', syncStatusLayout);
  $('show-readings').addEventListener('click', function () { $('readings-dialog').showModal(); });
  $('close-readings').addEventListener('click', function () { $('readings-dialog').close(); });
  $('copy-qq-group').addEventListener('click', function () { copyText('314981836', 'QQ群号'); });
  $('refresh-diagnostics').addEventListener('click', pollState); $('refresh-log').addEventListener('click', loadLog);
  $('settings-form').addEventListener('submit', saveConfig);
  $('reload-config').addEventListener('click', function () { if ($('dirty-status').textContent === '有未保存的修改' && !confirm('重新读取会丢弃未保存的修改，继续吗？')) return; readConfig(); });
  $('restore-defaults').addEventListener('click', function () { if (!configReady || configBusy || !device) return; if (!confirm('将表单恢复为默认值？保存后才会应用。')) return; preferenceEdited = true; learnedDraft = null; curvePointEditing = false; curveDraft = window.LumaCurveMath.defaults.slice(); curveCustom = false; renderForm(defaults); renderCurveEditor(); markDirty(); });
  $('clear-log').addEventListener('click', function () { if (confirm('清空当前日志？历史归档不会被删除。')) runCommand('clear-log'); });
  $('copy-diagnostics').addEventListener('click', function () { copyText(stateText, '状态'); });
  $('copy-log').addEventListener('click', function () { copyText(logText, '日志'); });
  $('export-log').addEventListener('click', function () {
    if (!device || exportBusy) return;
    exportBusy = true; syncButtons(); set('export-status', '正在导出…');
    var filename = 'LumaCurve-' + new Date().toISOString().replace(/[:.]/g, '-') + '-' + Math.random().toString(36).slice(2, 7) + '.log';
    var command = 'umask 022; user_id=$(am get-current-user) || exit 1; case "$user_id" in ""|*[!0-9]*) exit 1;; esac; ' +
      'directory="/storage/emulated/$user_id/Download"; mkdir -p "$directory" && destination="$directory/"' + quote(filename) + ' && ' +
      '(set -C; sh ' + quote(CTL) + ' current-log > "$destination") && chmod 0644 "$destination" && printf %s "$destination"';
    exec(command).then(function (path) { set('export-status', '已导出：' + path.trim()); toast('日志已保存到下载目录'); })
      .catch(function (error) { set('export-status', '导出失败：' + error.message); }).finally(function () { exportBusy = false; syncButtons(); });
  });
  $('save-log-policy').addEventListener('click', function () {
    if (!device || configBusy || commandBusy) return;
    if ($('dirty-status').textContent === '有未保存的修改' && !confirm('保存日志策略后会重新读取配置，未保存的设置会丢失。继续吗？')) return;
    commandBusy = true; syncButtons();
    control('set-log-retention', [$('log-days').value]).then(function () { set('tools-feedback', '日志保留策略已保存'); return readConfig(); }).catch(function (e) { set('tools-feedback', '保存失败：' + e.message); }).finally(function () { commandBusy = false; syncButtons(); });
  });
  $('demo-button').addEventListener('click', function () {
    demo = true; stateText = 'mode=auto\nscreen=1\ncurrent_br=1720\ntarget_br=1860\nmax_br=4095\nlux=128.4\nsmooth=112.8\nbrightness_owner=daemon\ntransition_active=1\nsunlight_active=0\nheat_guard_active=0\nsensor_rate_mode=active\nlux_source=front\nlux_valid=1\nfront_lux=128.4\nback_lux=85.2\nfront_lux_age_ms=50\nback_lux_age_ms=80\nlux_age_ms=50\nsensor_rate_us=200000\ntarget_poll_ms=500\nthermal_trusted_temp=38500\nbattery_pct=76\ncharging=0\ndaemon_can_write=1\ntarget_hold_active=0\nupdated_unix=' + Math.floor(Date.now() / 1000);
    renderState(parse(stateText));
  });
  $('preview-notice').hidden = !!device; $('log-days').disabled = !device;
  window.LumaCurveMath.lux.forEach(function (lux,index) { var option = document.createElement('option'); option.value = index; option.textContent = lux + ' lux'; $('curve-anchor').appendChild(option); });
  $('curve-anchor').addEventListener('change',function () { curvePointEditing=false; renderCurveEditor(); });
  $('curve-editor-plot').addEventListener('click',function (event) {
    var box = this.getBoundingClientRect(), coordinate = (event.clientX-box.left)/box.width*360, best = 0;
    window.LumaCurveMath.lux.forEach(function (lux,i) { if (Math.abs(responseX(lux)-coordinate)<Math.abs(responseX(window.LumaCurveMath.lux[best])-coordinate)) best=i; });
    $('curve-details').open = true;
    $('curve-anchor').value=best; curvePointEditing=false; renderCurveEditor();
  });
  $('curve-range').addEventListener('input',function () { curvePointEditing=true; $('curve-value').value=this.value; });
  $('curve-value').addEventListener('input',function () { curvePointEditing=true; $('curve-range').value=this.value; });
  $('curve-apply').addEventListener('click',function () {
    if (!device || !configReady || configBusy) return;
    var index=Number($('curve-anchor').value), value=Number($('curve-value').value), previous=(learnedDraft || curveDraft).slice();
    var next=window.LumaCurveMath.edit(previous,index,value);
    if (!$('curve-value').value.trim() || !next) { set('curve-status','请输入 0.1%–100% 之间的背光比例。'); return; }
    var linked=next.filter(function (point,i) { return i!==index && point!==previous[i]; }).length;
    preferenceEdited=true; learnedDraft=null; curvePointEditing=false; curveDraft=next; curveCustom=true; renderCurveEditor(); markDirty(); set('curve-status','锚点已调整' + (linked ? '，联动 '+linked+' 个相邻点以保持曲线不下降' : '') + '；保存并应用后生效。');
  });
  $('curve-default').addEventListener('click',function () { if (!device || !configReady || configBusy) return; preferenceEdited=true; learnedDraft=null; curvePointEditing=false; curveDraft=window.LumaCurveMath.defaults.slice(); curveCustom=false; renderCurveEditor(); markDirty(); set('curve-status','已恢复默认曲线并重置锚点学习；阳光参数保留。保存后生效。'); });
  $('preset-save').addEventListener('click',function () {
    var values=collect(), name=$('preset-name').value.trim(); if (!values || !name) { set('preset-status','请填写预设名称并检查设置。'); return; }
    var subset={}; values.preference_learning=inputFor('preference_learning').checked ? 1 : 0; presetKeys.forEach(function (key) { subset[key]=values[key]; });
    var entry={name:name,custom:curveCustom || !!learnedDraft,points:(learnedDraft || curveDraft).slice(),values:subset}, next=presets.slice(), index=next.findIndex(function (e) { return e.name===name; });
    if (!validPreset(entry)) { set('preset-status','预设参数无效'); return; }
    if (index>=0) { if (!confirm('覆盖同名预设？')) return; next[index]=entry; }
    else { if (next.length>=16) { set('preset-status','最多保存 16 个预设'); return; } index=next.length; next.push(entry); }
    savePresets(next,index);
  });
  $('preset-load').addEventListener('click',function () {
    var entry=presets[Number($('preset-select').value)]; if (!entry || !validPreset(entry) || configBusy) return;
    if (!confirm('将预设载入表单？保存并应用后生效。')) return;
    presetKeys.forEach(function (key) { var input=inputFor(key); if (key==='preference_learning') { input.checked=entry.values[key]===1; return; } var f=fields.find(function (field) { return field[0]===key; }); if (f[3]==='bool') input.checked=entry.values[key]===1; else input.value=Number((entry.values[key]*f[6]).toFixed(6)); updateField(f); });
    preferenceEdited=true; learnedDraft=null; curvePointEditing=false; curveCustom=entry.custom; curveDraft=entry.points.slice(); renderCurveEditor(); markDirty(); set('preset-status','已载入“'+entry.name+'”，保存并应用后生效。');
  });
  $('preset-delete').addEventListener('click',function () { var index=Number($('preset-select').value); if (!presets[index] || !confirm('删除该预设？当前配置不变。')) return; var next=presets.slice(); next.splice(index,1); savePresets(next); });
  renderForm(defaults); renderCurveEditor(); syncButtons(); if (!device) unavailable();
  readConfig(); switchPanel(location.hash.slice(1) || 'status');
  setInterval(function () { if (active === 'status' && !document.hidden) pollState(); }, 1000);
  setInterval(function () { if (!document.hidden) { if (active === 'settings') refreshPreference(); if (active === 'tools') loadLog(); } }, 5000);
  document.addEventListener('visibilitychange', function () { if (!document.hidden && active === 'status') pollState(); });
})();
