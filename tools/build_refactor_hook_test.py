"""Build the current HyperLux LSPosed app, matching source and recovery bundle."""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess, urllib.request, zipfile, re, xml.etree.ElementTree as ET

parser=argparse.ArgumentParser();parser.add_argument('--skip-device-fixtures',action='store_true',help='Rebuild without locally supplied proprietary firmware disassembly');args=parser.parse_args()

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'experimental/refactor_hook'
EXPECTED_RELEASE_CERT='a4c4759841927acf432b887f0c9bfc16fb8e30c0885ae00e8a6175b90731182d'
def validate_release_signing(key):
    if not key.is_file():raise FileNotFoundError('Formal release requires the existing signing key; refusing to generate a replacement')
    certificate=subprocess.check_output(['keytool','-exportcert','-keystore',str(key),'-storepass','androidtest','-alias','lumacurve-experimental'])
    if hashlib.sha256(certificate).hexdigest()!=EXPECTED_RELEASE_CERT:raise ValueError('Formal release certificate differs from existing releases')
identity=(SRC/'java/top/rongshangs/lumacurve/refactor/AppBuild.java').read_text(encoding='utf-8')
BUILD=re.search(r'BUILD="([^"]+)"',identity).group(1)
VERSION=re.search(r'VERSION="([^"]+)"',identity).group(1)
ARTIFACT_VERSION=re.search(r'ARTIFACT_VERSION="([^"]+)"',identity).group(1)
IS_TEST='TEST=true' in identity
IS_INTERNAL=bool(re.search(r'\bINTERNAL\s*=\s*true\b',identity))
manifest=ET.parse(SRC/'AndroidManifest.xml').getroot()
assert manifest.get('{http://schemas.android.com/apk/res/android}versionName')==ARTIFACT_VERSION,'manifest/build identity mismatch'
VERSION_CODE=int(manifest.get('{http://schemas.android.com/apk/res/android}versionCode'))
OUT=ROOT/'build/refactor-hook'
KEY=OUT/'test-signing.p12'
if not IS_TEST:validate_release_signing(KEY)
SDK=Path('D:/App/SDK'); BT=SDK/'build-tools/37.0.0'; ANDROID=SDK/'platforms/android-37.0/android.jar'
OUT.mkdir(parents=True,exist_ok=True)
# The web feed is updated independently; preserve the bundled offline fallback.
# Keep the source archive's local-site fallback in sync before packaging it.
thanks=json.loads((ROOT/'website/thanks.json').read_text(encoding='utf-8'))
(ROOT/'website/thanks.js').write_text('window.HyperLuxThanks='+json.dumps(thanks,ensure_ascii=False)+';\n',encoding='utf-8',newline='\n')
DEP=OUT/'deps/api-82.jar';DEP.parent.mkdir(exist_ok=True)
API_URL='https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar'
API_SHA='f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25'
if not DEP.exists():
    with urllib.request.urlopen(API_URL,timeout=30) as response:DEP.write_bytes(response.read())
assert hashlib.sha256(DEP.read_bytes()).hexdigest()==API_SHA,'compile dependency changed'
def run(args):subprocess.run([str(a) for a in args],check=True)
def checked_test(path,pattern,*parameters):
    result=subprocess.run(['python',str(path),*[str(p) for p in parameters]],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(result.stdout,end='')
    if result.returncode:print(result.stderr,end='')
    result.check_returncode();matched=re.search(pattern,result.stdout)
    assert matched,'test result count missing'
    return int(matched.group(1))
display_scheduling_cases=checked_test(ROOT/'tests/refactor_display_scheduling.py',r'(\d+) cases PASS')
state_writer_cases=checked_test(ROOT/'tests/refactor_state_writer.py',r'(\d+) cases PASS')
device_observer_cases=checked_test(ROOT/'tests/device_runtime_observer.py',r'(\d+) cases PASS')
CLASSES=OUT/'classes'
if CLASSES.exists():
    assert CLASSES.resolve().parent==OUT.resolve(),'unexpected generated class directory'
    shutil.rmtree(CLASSES)
CLASSES.mkdir()
run(['javac','-encoding','UTF-8','--release','8','-cp',str(ANDROID)+';'+str(DEP),'-d',CLASSES,*sorted((SRC/'java').rglob('*.java'))])
run(['javac','-encoding','UTF-8','--release','8','-cp',CLASSES,'-d',CLASSES,*sorted((SRC/'tests').glob('*.java'))])
tested=subprocess.run(['java','-cp',str(CLASSES)+';'+str(ROOT/'build/refactor-diagnostics/json-20240303.jar'),'top.rongshangs.lumacurve.refactor.RefactorTest'],check=True,capture_output=True,text=True)
print(tested.stdout,end='');host_cases=int(re.search(r'(\d+) cases PASS',tested.stdout).group(1))
diagnostic_cases=checked_test(ROOT/'tests/refactor_diagnostics.py',r'(\d+) host cases PASS')
advanced_cases=checked_test(ROOT/'tests/refactor_advanced_hooks.py',r'(\d+) cases PASS')
traditional_cases=checked_test(ROOT/'tests/refactor_traditional.py',r'(\d+) cases PASS')
curve_comparison_cases=checked_test(ROOT/'tests/refactor_curve_comparison.py',r'(\d+) cases PASS')
outdoor_cases=checked_test(ROOT/'tests/refactor_outdoor.py',r'(\d+) cases PASS')
native_panel_cases=checked_test(ROOT/'tests/refactor_native_panel.py',r'(\d+) cases PASS')
node_discovery_cases=checked_test(ROOT/'tests/refactor_panel_nodes.py',r'Panel node validation total: (\d+) cases PASS')
native_transport_cases=checked_test(ROOT/'tests/refactor_native_transport.py',r'(\d+) cases PASS')
panel_access_cases=checked_test(ROOT/'tests/refactor_panel_access.py',r'Panel access total: (\d+) cases PASS')
panel_interaction_cases=checked_test(ROOT/'tests/refactor_panel_interaction.py',r'(\d+) cases PASS')
raw_output_cases=checked_test(ROOT/'tests/refactor_raw_output.py',r'(\d+) cases PASS')
raw_panel_transaction_cases=checked_test(ROOT/'tests/refactor_raw_panel_transaction.py',r'(\d+) cases PASS')
brightness_control_cases=checked_test(ROOT/'tests/refactor_brightness_control.py',r'(\d+) cases PASS')
recovery_path_cases=checked_test(ROOT/'tests/refactor_recovery_paths.py',r'(\d+) cases PASS')
status_advice_cases=checked_test(ROOT/'tests/refactor_status_advice.py',r'(\d+) cases PASS')
configuration_cases=checked_test(ROOT/'tests/refactor_configuration.py',r'(\d+) cases PASS')
ui_navigation_cases=checked_test(ROOT/'tests/refactor_ui_navigation.py',r'(\d+) cases PASS')
release_packaging_cases=checked_test(ROOT/'tests/refactor_release_packaging.py',r'(\d+) cases PASS')
app_update_cases=checked_test(ROOT/'tests/refactor_app_updates.py',r'(\d+) cases PASS')
advice_text_cases=checked_test(ROOT/'tests/refactor_advice_text.py',r'(\d+) cases PASS')
status_layout_cases=checked_test(ROOT/'tests/refactor_status_layout.py',r'(\d+) cases PASS')
status_presentation_cases=checked_test(ROOT/'tests/refactor_status_presentation.py',r'(\d+) cases PASS')
draft_health_cases=checked_test(ROOT/'tests/refactor_draft_and_health.py',r'(\d+) cases PASS')
user_identity_cases=checked_test(ROOT/'tests/refactor_user_identity.py',r'(\d+) cases PASS')
system_version_cases=checked_test(ROOT/'tests/refactor_system_version.py',r'(\d+) cases PASS')
status_transport_cases=checked_test(ROOT/'tests/refactor_status_transport.py',r'(\d+) cases PASS')
memory_cases=checked_test(ROOT/'tests/refactor_persistent_memory.py',r'(\d+) cases PASS')
memory_lifecycle_cases=checked_test(ROOT/'tests/refactor_memory_lifecycle.py',r'(\d+) cases PASS')
memory_firmware_cases=0
if not args.skip_device_fixtures:memory_firmware_cases=checked_test(ROOT/'tests/refactor_memory_firmware.py',r'(\d+) checks PASS')
if not args.skip_device_fixtures:run(['python',ROOT/'tests/refactor_hook_firmware.py'])
else:print('OEM firmware checks skipped explicitly; runtime compatibility validation remains enabled')
advanced_firmware_cases=0
if not args.skip_device_fixtures:advanced_firmware_cases=checked_test(ROOT/'tests/refactor_advanced_firmware.py',r'(\d+) checks PASS')
traditional_firmware_cases=0
if not args.skip_device_fixtures:traditional_firmware_cases=checked_test(ROOT/'tests/refactor_traditional_firmware.py',r'(\d+) checks PASS')
outdoor_firmware_cases=0
if not args.skip_device_fixtures:outdoor_firmware_cases=checked_test(ROOT/'tests/refactor_outdoor_firmware.py',r'(\d+) checks PASS')
scene_settings_cases=checked_test(ROOT/'tests/refactor_scene_settings.py',r'(\d+) cases PASS')
scene_cases=checked_test(ROOT/'tests/refactor_scenes.py',r'(\d+) cases PASS')
scene_hook_cases=checked_test(ROOT/'tests/refactor_scene_hooks.py',r'(\d+) cases PASS')
scene_firmware_cases=0
if not args.skip_device_fixtures:scene_firmware_cases=checked_test(ROOT/'tests/refactor_scene_firmware.py',r'(\d+) checks PASS')
NATIVE=SRC/'assets/hyperlux-main-panel'
NDK=SDK/'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe'
run([NDK,'--target=aarch64-linux-android34','-Wl,--strip-all','-std=c11','-Os','-static','-Wall','-Wextra','-Werror','-Wno-misleading-indentation','-o',NATIVE,SRC/'native/main_panel.c'])
COMPILED=OUT/'resources.zip';UNSIGNED=OUT/'unsigned.apk'
run([BT/'aapt2.exe','compile','--dir',SRC/'res','-o',COMPILED])
run([BT/'aapt2.exe','link','-I',ANDROID,'--manifest',SRC/'AndroidManifest.xml','-A',SRC/'assets','-o',UNSIGNED,COMPILED])
DEX=OUT/'dex';DEX.mkdir(exist_ok=True)
inputs=[p for p in CLASSES.rglob('*.class') if not p.name.startswith('RefactorTest')]
run(['java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','34','--lib',ANDROID,'--lib',DEP,'--output',DEX,*inputs])
with zipfile.ZipFile(UNSIGNED,'a',zipfile.ZIP_DEFLATED) as z:z.write(DEX/'classes.dex','classes.dex')
ALIGNED=OUT/'aligned.apk';run([BT/'zipalign.exe','-f','-p','4',UNSIGNED,ALIGNED])
if not KEY.exists():run(['keytool','-genkeypair','-keystore',KEY,'-storepass','androidtest','-keypass','androidtest','-alias','lumacurve-experimental','-dname','CN=LumaCurve Experimental,O=RongShangs','-keyalg','RSA','-keysize','3072','-validity','3650'])
DIST=ROOT/'dist'/('HyperLux-'+ARTIFACT_VERSION) if IS_TEST else ROOT/'dist';DIST.mkdir(parents=True,exist_ok=True)
APK=DIST/('HyperLux-'+ARTIFACT_VERSION+'.apk')
run(['java','-jar',BT/'lib/apksigner.jar','sign','--ks',KEY,'--ks-pass','pass:androidtest','--ks-key-alias','lumacurve-experimental','--out',APK,ALIGNED])
run(['java','-jar',BT/'lib/apksigner.jar','verify','--verbose',APK])
run([BT/'zipalign.exe','-c','4',APK])
native_asset_cases=checked_test(ROOT/'tests/refactor_native_asset.py',r'(\d+) cases PASS',APK)
# Root recovery does not load any Xposed classes, and works if the app is uninstalled.
HELPER=DIST/'luma-refactor-helper.jar'
names={'PanelNodeDiscovery.class','SystemVersion.class','SystemVersion$Reader.class','SystemVersion$Result.class','NativePanelAsset.class','NativePanelClient.class','NativePanelRoot.class','BrightnessControlOptions.class','RootControl.class','RootSettings.class','ForegroundUser.class','CurvePlan.class','CurveIdentity.class','TraditionalCurve.class','ThermalPolicy.class','MemoryPolicy.class','MemoryOptions.class','MemoryScene.class','LowLightThresholds.class','LowLightAssistGate.class','PersistentMemory.class','DelayPolicy.class','LegacyModules.class','AppBuild.class','LowLightPolicy.class','DiagnosticCollector.class','AdvancedOptions.class','AdvancedPolicy.class','OutdoorOptions.class','OutdoorPolicy.class','ConfigurationFile.class','ConfigurationFile$Imported.class','StatusTransport.class','StatusTransport$Reader.class'}
run(['java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','34','--lib',ANDROID,'--output',HELPER,*[p for p in inputs if p.name in names or p.name.startswith('DiagnosticCollector$')]])
run(['C:/msys64/usr/bin/bash.exe','-n',SRC/'restore_refactor_hook_android.sh'])
meta={'sensor_callback_bursts_coalesced':True,'sensor_confirmation_interruptions_preserved':True,'dark_lock_requested_period_us':1000000,'disabled_output_paths_skip_service_checks':True,'device_observer_cases':device_observer_cases,'display_scheduling_cases':display_scheduling_cases,'screen_hook_deferred_cleanup':True,'unowned_screen_off_provider_writes':False,'oem_recalculation_queued':True,'sample_diagnostics_queued':True,'scene_settings_cases':scene_settings_cases,'scene_settings_inline':True,'scene_entry_exit_confirmation_separate':True,'scene_effect_controls':['touch_release_seconds','sunlight_enter','sunlight_exit'],'status_reading_gap_dp':8,'status_sensor_and_screen_row_dp':80,'status_fusion_row_dp':88,'status_fusion_inner_inset_dp':12,'status_fusion_value_gap_dp':4,'status_fusion_scene_gap_dp':2,'status_scene_separate_container':False,'status_thermal_limiting_text_red':False,'status_thermal_temperature_alert_red':True,'status_thermal_temperature_alert_scope':'thermal_output_limit_or_outdoor_temperature_block','status_fusion_rows_share_available_height':True,'outdoor_range_controls_in_main_settings':True,'status_protection_row_dp':108,'status_lux_clickable':False,'scene_cases':scene_cases,'scene_hook_cases':scene_hook_cases,'scene_custom_rules_max':0,'scene_legacy_rules_ignored':True,'scene_controls':9,'scene_catalog_entries':17,'scene_defaults_follow_oem':True,'scene_rules_only_restrict_entry':True,'scene_rules_confirm_entry_and_exit':True,'scene_rules_missing_facts_follow_oem':True,'status_curve_footer_single_line':True,'status_scene_entry_in_fusion':True,'status_output_protection_columns':3,'status_presentation_cases':status_presentation_cases,'scene_firmware_cases':scene_firmware_cases,'status_curve_guidance_matches_live_kind':True,'status_curve_guidance_in_blue_container':True,'status_modes_visible':True,'branch_report_sections':7,'build':BUILD,'version':ARTIFACT_VERSION,'version_code':VERSION_CODE,'test_build':IS_TEST,'internal_test_build':IS_INTERNAL,'release_channel':'internal' if IS_INTERNAL else 'compatibility_test' if IS_TEST else 'release','app_name':'HyperLux','architecture':'oem_active_curve_backend_hook','status_layout_cases':status_layout_cases,'status_page_uniform_spacing':False,'status_page_regular_gap_dp':16,'status_page_threshold_gap_dp':32,'status_page_horizontal_inset_dp':18,'status_protection_inner_inset_dp':12,'status_protection_cell_gap_dp':8,'status_protection_centered_cells':True,'status_protection_temperature_source':'battery','settings_home_scroll_restored':True,'scene_existing_conditions_editable':True,'status_page_short_viewport_scroll':True,'advice_text_cases':advice_text_cases,'status_advice_inline_guidance':True,'status_advice_guidance_in_all_states':True,'status_advice_height_changes_with_state':False,'app_update_cases':app_update_cases,'app_update_ignore_per_version':True,'app_update_downloads':['website','github_apk'],'ui_navigation_cases':ui_navigation_cases,'release_packaging_cases':release_packaging_cases,'formal_release_certificate_sha256':EXPECTED_RELEASE_CERT,'memory_pending_model_scope_checked':True,'live_limit_evidence_reset_on_config':True,'brightness_observer_cleanup_isolated':True,'status_advice_cases':status_advice_cases,'recovery_path_cases':recovery_path_cases,'status_advice_read_only':True,'status_limit_evidence_max_age_ms':5000,'settings_apply_in_navigation':True,'dark_lock_sensor_manager':'automatic_brightness_controller','dark_lock_sensor_matching':'type_and_handle','protected_hbm_threshold_access_fixed':True,'confirmed_night_driving_low_light_guard':True,'oem_night_debounce_budget_preserved':True,'low_light_threshold_value_diagnostics':True,'valid_zero_assist_bounded_confirmation':True,'manual_panel_backend':'raw_main_node','framework_output_bridge':False,'primary_node_auto_discovery':True,'node_discovery_cases':node_discovery_cases,'primary_node_permissions_bound_to_identity':True,'system_version_preflight':True,'system_version_cases':system_version_cases,'thermal_ceiling_range':[38,50],'outdoor_maximum_preset':True,'panel_interaction_cases':panel_interaction_cases,'raw_output_cases':raw_output_cases,'raw_panel_unlock_restore':True,'raw_panel_pauses_during_keyguard_and_aod':True,'raw_panel_relative_gesture':True,'raw_panel_positive_output_isolation':True,
      'device_verified':False,'state_writer_cases':state_writer_cases,'state_writes_off_display_thread':True,'state_writer_pending_groups_max':2,'state_writer_chunk_order_preserved':True,'panel_access_cases':panel_access_cases,'primary_node_access_open_probe':True,'primary_node_probe_no_brightness_write':True,'native_build':'raw07-openprobe','draft_health_cases':draft_health_cases,'draft_changes_during_save_preserved':True,'native_health_monotonic_heartbeat_ms':2000,'native_health_max_age_ms':5000,'raw_dark_handover_auto_recovery':True,'raw_lost_ack_session_rollback':True,'memory_firmware_checks':memory_firmware_cases,'compile_api':82,'compile_api_sha256':API_SHA,
      'sdk_compile':37,'sdk_min':34,'separate_output_daemon':True,'raw_main_panel_native_guard':True,'native_source_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (SRC/'native').glob('*') if p.is_file()},'native_guard_sha256':hashlib.sha256(NATIVE.read_bytes()).hexdigest(),
      'firmware_static_checks':not args.skip_device_fixtures,'host_cases':host_cases,
      'package':'top.rongshangs.lumacurve','languages':['zh','en'],'direct_point_editor':True,'system_light_change_delays_optional':True,'apk_update_repo':'RongShangs/LumaCurve',
      'pipeline_readonly_trace':True,'pipeline_calculation_capacity':24,'output_trace_capacity':16,'low_light_stability_optional':True,'low_light_stability_default':False,'low_light_scoped_thresholds':True,'low_light_small_route_protected':True,'low_light_aux_gate_bounded':True,'low_light_aux_gate_default':False,
      'advanced_optional_parameters':14,'advanced_default_enabled':False,'advanced_hook_model_verified':True,'advanced_firmware_collections':2 if not args.skip_device_fixtures else 0,
      'memory_restore_deferred_from_oem_hooks':True,'memory_reset_notification_deferred':True,'miui_watchdog_file_capture':True,'miui_watchdog_max_files':6,'fault_diagnostics_prioritized':True,'fault_dropbox_cli_date_filter':False,'fault_dropbox_cli_scope':'retained_tag_entries','fault_raw_lookback_days':3,'fault_command_truncation':'tail','fault_raw_anr_max_files':6,'fault_raw_dropbox_max_files':10,'fault_raw_file_limit_bytes':8*1024*1024,'fault_logs_on_demand_only':True,'diagnostic_cases':diagnostic_cases,'status_transport_cases':status_transport_cases,'advanced_hook_model_cases':advanced_cases,'advanced_firmware_checks':advanced_firmware_cases,
      'user_identity_cases':user_identity_cases,'native_asset_cases':native_asset_cases,'native_transport_cases':native_transport_cases,'native_guard_bytes':NATIVE.stat().st_size,'raw_panel_transaction_cases':raw_panel_transaction_cases,'native_panel_cases':native_panel_cases,'brightness_control_cases':brightness_control_cases,'dark_lock_default':True,'dark_lock_enter_lux_default':10,'dark_lock_exit_lux_default':50,'dark_lock_minutes_default':1,'dark_lock_exit_seconds_default':1,'dark_lock_existing_preferences_preserved':True,'manual_tile':True,'mode_owner_system_server':True,'configuration_cases':configuration_cases,'configuration_schema':2,'curve_floor_editable':True,'settings_submenus':10,'settings_independent_screens':True,'settings_parameters_always_visible':False,'settings_details_default_collapsed':True,'settings_category_presets':True,'settings_preset_groups':9,'accessible_draft_switches':True,'sensor_status_explained':True,'unsaved_settings_notice':True,'page_transitions':True,'page_transitions_respect_system':True,'curve_comparison_cases':curve_comparison_cases,'curve_comparison_lines':2,'state_curve_lines':1,'state_curve_actual_only':True,'curve_stroke_dp':2,'responsive_dialogs':True,'outdoor_opr_optional':True,'outdoor_opr_default_enabled':False,'outdoor_opr_before_thermal':True,'outdoor_cases':outdoor_cases,'outdoor_firmware_checks':outdoor_firmware_cases,'outdoor_firmware_collections':3 if not args.skip_device_fixtures else 0,'outdoor_optional':True,'outdoor_default_enabled':False,'outdoor_uses_oem_animation':True,'outdoor_driver_writes':False,
      'curve_backends':['refactor','physical_mapping'],'baseline':'current_device_local_curve','traditional_hook_model_cases':traditional_cases,'traditional_firmware_checks':traditional_firmware_cases,
      'persistent_manual_memory':True,'persistent_memory_default':True,'persistent_memory_cases':memory_cases,'memory_lifecycle_cases':memory_lifecycle_cases,'persistent_memory_record_limit':8192,'persistent_memory_retention_days':[1,90],'persistent_memory_capacity':'backend_native','memory_lifecycle_observed':True,'memory_wake_same_scene_default':True,'memory_native_reset_override_default':False,'dark_mode':'system','editable_base_points':4,'module_long_term_learning':False,'oem_anchor_memory_adjustable':True,'thermal_display_relaxation':True,'source_sha256':{p.relative_to(SRC).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in (SRC/'java').rglob('*.java')},
      'apk_sha256':hashlib.sha256(APK.read_bytes()).hexdigest()}
# A smoke report belongs to one exact APK; a rebuild must not inherit old device claims.
device_report=ROOT/'build'/('device-smoke-'+BUILD+'.json')
if device_report.is_file() and device_report.stat().st_size<=16384:
    report=json.loads(device_report.read_text(encoding='utf-8'))
    if report.get('build')==BUILD and report.get('apk_sha256')==meta['apk_sha256']:
        meta['device_smoke']=report
(OUT/'verification.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
with zipfile.ZipFile(APK) as z:
    assert z.testzip() is None
    assert z.read('assets/xposed_init').strip()==b'top.rongshangs.lumacurve.refactor.HookEntry'
    assert 'classes.dex' in z.namelist()
    assert b'Lde/robv/android/xposed/XposedBridge;' in z.read('classes.dex')
    assert not any(name.endswith('.jar') or 'firmware' in name for name in z.namelist());assert 'assets/hyperlux-main-panel' in z.namelist()
archive_prefix='HyperLux' if IS_TEST else 'LumaCurve'
source=DIST/(archive_prefix+'-'+ARTIFACT_VERSION+'-source.zip')
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
    for name in ['refactor_state_writer.py','refactor_recovery_paths.py','refactor_status_advice.py','refactor_ui_navigation.py','refactor_release_packaging.py','refactor_app_updates.py','refactor_advice_text.py','refactor_status_layout.py','refactor_status_presentation.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    for name in ['refactor_scene_firmware.py','refactor_scenes.py','refactor_scene_hooks.py','refactor_scene_settings.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    for p in sorted(SRC.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(ROOT))
    z.write(ROOT/'LICENSE','LICENSE');z.write(ROOT/'README.md','README.md');z.write(ROOT/'CHANGELOG.md','CHANGELOG.md');z.write(Path(__file__),'tools/build_refactor_hook_test.py');z.write(ROOT/'tests/refactor_hook_firmware.py','tests/refactor_hook_firmware.py');z.write(ROOT/'tests/refactor_diagnostics.py','tests/refactor_diagnostics.py')
    for name in ['device_runtime_observer.py','refactor_display_scheduling.py','refactor_panel_access.py','refactor_draft_and_health.py','refactor_panel_nodes.py','refactor_curve_comparison.py','refactor_panel_interaction.py','refactor_raw_output.py','refactor_native_transport.py','refactor_native_asset.py','refactor_raw_panel_transaction.py','refactor_native_panel.py','refactor_brightness_control.py','refactor_advanced_hooks.py','refactor_advanced_firmware.py','refactor_traditional.py','refactor_traditional_firmware.py','refactor_user_identity.py','refactor_system_version.py','refactor_outdoor.py','refactor_outdoor_firmware.py','refactor_configuration.py','refactor_status_transport.py','refactor_persistent_memory.py','refactor_memory_lifecycle.py','refactor_memory_firmware.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    for p in sorted((ROOT/'docs/releases').glob('*.md')):z.write(p,p.relative_to(ROOT))
    for p in sorted((ROOT/'docs/reviews').glob('*.md')):z.write(p,p.relative_to(ROOT))
    z.write(ROOT/'docs/release-policy.md','docs/release-policy.md')
    review=ROOT/'docs'/('review-'+VERSION+'.md')
    if review.is_file():z.write(review,review.relative_to(ROOT))
    handoff=ROOT/'AGENT_HANDOFF.md'
    if handoff.is_file():z.write(handoff,'AGENT_HANDOFF.md')
    z.write(OUT/'verification.json','build-info.json')
    for name in ['observe_device_runtime.py','sync_website.py','package_website.py']:z.write(ROOT/'tools'/name,'tools/'+name)
    z.write(ROOT/'tools/analyze_outdoor_firmware.py','tools/analyze_outdoor_firmware.py')
    for p in sorted((ROOT/'website').rglob('*')):
        if p.is_file() and 'downloads' not in p.relative_to(ROOT/'website').parts and p.name not in ['update.json','update.js']:
            z.write(p,p.relative_to(ROOT))
script=DIST/'restore_refactor_hook_android.sh';shutil.copy2(SRC/script.name,script)
notes=DIST/(archive_prefix+'-'+ARTIFACT_VERSION+'-README.md');shutil.copy2(SRC/'README.md',notes)
bundle=DIST/(archive_prefix+'-'+ARTIFACT_VERSION+'.zip')
research=ROOT/'docs/releases'/(ARTIFACT_VERSION+'.md')
research_copy=None
if research.is_file():research_copy=DIST/(archive_prefix+'-'+ARTIFACT_VERSION+('-research.md' if IS_TEST else '-release.md'));shutil.copy2(research,research_copy)
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
    for file in [APK,HELPER,script,notes,source]:z.write(file,file.name)
    if research_copy:z.write(research_copy,research_copy.name)
    z.write(OUT/'verification.json','build-info.json')
print(APK);print('SHA256',meta['apk_sha256']);print(bundle)
