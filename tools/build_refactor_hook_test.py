"""Build the current HyperLux LSPosed app, matching source and recovery bundle."""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess, urllib.request, zipfile, re, xml.etree.ElementTree as ET

parser=argparse.ArgumentParser();parser.add_argument('--skip-device-fixtures',action='store_true',help='Rebuild without locally supplied proprietary firmware disassembly');parser.add_argument('--verify-only',action='store_true',help='Compile and run all checks without replacing APK/source/site deliverables');args=parser.parse_args()

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'experimental/refactor_hook'
EXPECTED_RELEASE_CERT='a4c4759841927acf432b887f0c9bfc16fb8e30c0885ae00e8a6175b90731182d'
def validate_release_signing(key):
    if not key.is_file():raise FileNotFoundError('Formal release requires the existing signing key; refusing to generate a replacement')
    certificate=subprocess.check_output(['keytool','-exportcert','-keystore',str(key),'-storepass','androidtest','-alias','lumacurve-experimental'])
    if hashlib.sha256(certificate).hexdigest()!=EXPECTED_RELEASE_CERT:raise ValueError('Formal release certificate differs from existing releases')
def validate_output_paths(directory,names):
    existing=[directory/name for name in names if (directory/name).exists() or (directory/name).is_symlink()]
    if existing:raise FileExistsError('Existing deliverables are immutable; choose a new Beta/revision: '+', '.join(str(p) for p in existing))
def class_archive(target,files,root):
    # Feed D8 a jar instead of thousands of Windows command-line characters.
    root=Path(root).resolve();target=Path(target)
    if target.is_symlink() or target.resolve().parent!=root.parent:raise ValueError('Class archive must stay in the build directory')
    entries={}
    for file in files:
        file=Path(file);resolved=file.resolve()
        if root not in resolved.parents or not file.is_file() or file.suffix!='.class':raise ValueError('Class input must stay inside compiled classes')
        name=resolved.relative_to(root).as_posix()
        if name in entries:raise ValueError('Duplicate class input: '+name)
        entries[name]=file
    if not entries:raise ValueError('No class inputs')
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
        for name,file in sorted(entries.items()):archive.write(file,name)
    return target

identity=(SRC/'java/top/rongshangs/lumacurve/refactor/AppBuild.java').read_text(encoding='utf-8')
BUILD=re.search(r'BUILD="([^"]+)"',identity).group(1)
VERSION=re.search(r'VERSION="([^"]+)"',identity).group(1)
ARTIFACT_VERSION=re.search(r'ARTIFACT_VERSION="([^"]+)"',identity).group(1)
IS_TEST='TEST=true' in identity
IS_INTERNAL=bool(re.search(r'\bINTERNAL\s*=\s*true\b',identity))
IS_BETA=bool(re.search(r'\bBETA\s*=\s*true\b',identity))
manifest=ET.parse(SRC/'AndroidManifest.xml').getroot()
assert manifest.get('{http://schemas.android.com/apk/res/android}versionName')==ARTIFACT_VERSION,'manifest/build identity mismatch'
VERSION_CODE=int(manifest.get('{http://schemas.android.com/apk/res/android}versionCode'))
OUT=ROOT/'build/refactor-hook'
KEY=OUT/'test-signing.p12'
if not IS_TEST and not args.verify_only:validate_release_signing(KEY)
revision=re.fullmatch(r'release-'+re.escape(VERSION)+r'(-r[1-9][0-9]*)',BUILD)
DIST=ROOT/'dist'/('HyperLux-'+ARTIFACT_VERSION+(revision.group(1) if revision else '')) if IS_TEST or IS_BETA or revision else ROOT/'dist'
if not args.verify_only:
    archive_prefix='HyperLux' if IS_TEST else 'LumaCurve'
    validate_output_paths(DIST,['HyperLux-'+ARTIFACT_VERSION+'.apk',archive_prefix+'-'+ARTIFACT_VERSION+'-source.zip',archive_prefix+'-'+ARTIFACT_VERSION+'.zip',archive_prefix+'-'+ARTIFACT_VERSION+'-release.md'])

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
def maintenance_hashes():
    names=['compatibility-manifest.json','compatibility-baseline.json','source-map.json','compatibility-index.md','compatibility-policy.md']
    return {name:hashlib.sha256((ROOT/'docs/architecture'/name).read_bytes()).hexdigest() for name in names}
def checked_test(path,pattern,*parameters):
    result=subprocess.run(['python',str(path),*[str(p) for p in parameters]],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(result.stdout,end='')
    if result.returncode:print(result.stderr,end='')
    result.check_returncode();matched=re.search(pattern,result.stdout)
    assert matched,'test result count missing'
    return int(matched.group(1))
run(['python',ROOT/'tools/verify_compatibility.py'])
compatibility_contract_cases=checked_test(ROOT/'tests/refactor_compatibility_contract.py',r'Compatibility contract regression: (\d+) cases PASS')
diagnostic_cases=checked_test(ROOT/'tests/refactor_diagnostics.py',r'(\d+) host cases PASS')
display_scheduling_cases=checked_test(ROOT/'tests/refactor_display_scheduling.py',r'(\d+) cases PASS')
outdoor_node_cases=checked_test(ROOT/'tests/refactor_outdoor_node.py',r'Outdoor node compatibility: (\d+) cases PASS')
outdoor_node_settings_cases=checked_test(ROOT/'tests/refactor_outdoor_node_settings.py',r'Outdoor node settings: (\d+) cases PASS')
outdoor_retry_cases=checked_test(ROOT/'tests/refactor_outdoor_write_retry.py',r'Outdoor retry and recovery total: (\d+) cases PASS')
legacy_api_cases=checked_test(ROOT/'tests/refactor_legacy_api.py',r'(\d+) cases PASS')
hook_transaction_cases=checked_test(ROOT/'tests/refactor_hook_transactions.py',r'Hook installation isolation: (\d+) cases PASS')
assist_compat_firmware_cases=checked_test(ROOT/'tests/refactor_assist_compat_firmware.py',r'Assist compatibility firmware: (\d+) checks PASS')
fold_sensor_firmware_cases=0
if not args.skip_device_fixtures:fold_sensor_firmware_cases=checked_test(ROOT/'tests/refactor_fold_sensor_firmware.py',r'Fold sensor firmware: (\d+) checks PASS')
assist_compat_cases=checked_test(ROOT/'tests/refactor_assist_compat.py',r'Assist sensor compatibility: (\d+) cases PASS')
curve_integrity_cases=checked_test(ROOT/'tests/refactor_curve_integrity.py',r'Refactor curve integrity: (\d+) cases PASS')
control_center_demo_cases=checked_test(ROOT/'tests/refactor_control_center_demo.py',r'Control center demo: (\d+) cases PASS')
control_center_partition_cases=checked_test(ROOT/'tests/refactor_control_center_partition.py',r'Control center partition: (\d+) cases PASS')
control_center_node_cases=checked_test(ROOT/'tests/refactor_control_center_nodes.py',r'Control center full nodes: (\d+) cases PASS')
control_center_settings_cases=checked_test(ROOT/'tests/refactor_control_center_settings.py',r'Mapping settings and secondary insets: (\d+) cases PASS')
control_center_calibration_cases=checked_test(ROOT/'tests/refactor_control_center_calibration.py',r'Control center calibration domain: (\d+) cases PASS')
control_center_cases=checked_test(ROOT/'tests/refactor_control_center.py',r'Control center total: (\d+) cases PASS')
session_tools_cases=checked_test(ROOT/'tests/refactor_session_tools.py',r'(\d+) cases PASS')
floating_search_cases=checked_test(ROOT/'tests/refactor_floating_search.py',r'Floating navigation and settings search: (\d+) cases PASS')
log_summary_cases=checked_test(ROOT/'tests/refactor_log_summary.py',r'Log compatibility summary: (\d+) cases PASS')
scroll_elasticity_cases=checked_test(ROOT/'tests/refactor_scroll_elasticity.py',r'Elastic scrolling: (\d+) cases PASS')
diagnostic_sharing_cases=checked_test(ROOT/'tests/refactor_diagnostic_sharing.py',r'Diagnostic sharing: (\d+) cases PASS')
about_page_cases=checked_test(ROOT/'tests/refactor_about_page.py',r'About presentation: (\d+) cases PASS')
review_safety_cases=checked_test(ROOT/'tests/refactor_review_safety.py',r'Review safety total: (\d+) cases PASS')
root_access_cases=checked_test(ROOT/'tests/refactor_root_access.py',r'Root access total: (\d+) cases PASS')
feature_self_check_cases=checked_test(ROOT/'tests/refactor_feature_self_check.py',r'Independent feature self-check: (\d+) cases PASS')
feature_compatibility_cases=checked_test(ROOT/'tests/refactor_feature_compatibility.py',r'Feature compatibility catalogue: (\d+) cases PASS')
feature_page_cases=checked_test(ROOT/'tests/refactor_feature_page.py',r'Feature check page: (\d+) cases PASS')
welcome_cases=checked_test(ROOT/'tests/refactor_welcome.py',r'Welcome and tutorial: (\d+) cases PASS')
quick_setup_cases=checked_test(ROOT/'tests/refactor_quick_setup.py',r'Quick setup: (\d+) cases PASS')
os3_compatibility_cases=checked_test(ROOT/'tests/refactor_os3_compatibility.py',r'OS3 compatibility: (\d+) cases PASS')
firmware_revalidation_cases=checked_test(ROOT/'tests/refactor_firmware_revalidation.py',r'Firmware revalidation: (\d+) cases PASS')
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
advanced_cases=checked_test(ROOT/'tests/refactor_advanced_hooks.py',r'(\d+) cases PASS')
traditional_animation_cases=checked_test(ROOT/'tests/refactor_traditional_animation.py',r'Traditional animation: (\d+) cases PASS')
traditional_animation_firmware_cases=0
if not args.skip_device_fixtures:traditional_animation_firmware_cases=checked_test(ROOT/'tests/refactor_traditional_animation_firmware.py',r'Traditional animation firmware: (\d+) checks PASS')
single_sensor_firmware_cases=0
if not args.skip_device_fixtures:single_sensor_firmware_cases=checked_test(ROOT/'tests/refactor_single_sensor_firmware.py',r'Single sensor firmware: (\d+) checks PASS')
traditional_cases=checked_test(ROOT/'tests/refactor_traditional.py',r'(\d+) cases PASS')
physical_calibration_cases=checked_test(ROOT/'tests/refactor_physical_calibration.py',r'Physical calibration: (\d+) cases PASS')
physical_calibration_firmware_cases=0
if not args.skip_device_fixtures:physical_calibration_firmware_cases=checked_test(ROOT/'tests/refactor_physical_calibration_firmware.py',r'Physical calibration firmware: (\d+) checks PASS')
curve_comparison_cases=checked_test(ROOT/'tests/refactor_curve_comparison.py',r'(\d+) cases PASS')
saved_curve_cases=checked_test(ROOT/'tests/refactor_saved_curve.py',r'(\d+) cases PASS')
saved_curve_transaction_cases=checked_test(ROOT/'tests/refactor_saved_curve_transaction.py',r'(\d+) cases PASS')
opr_envelope_cases=checked_test(ROOT/'tests/refactor_opr_envelope.py',r'OPR envelope: (\d+) cases PASS')
opr_envelope_firmware_cases=0
if not args.skip_device_fixtures:opr_envelope_firmware_cases=checked_test(ROOT/'tests/refactor_opr_envelope_firmware.py',r'OPR envelope firmware: (\d+) checks PASS')
outdoor_cases=checked_test(ROOT/'tests/refactor_outdoor.py',r'(\d+) cases PASS')
outdoor_compatibility_cases=checked_test(ROOT/'tests/refactor_outdoor_compatibility.py',r'Outdoor compatibility total: (\d+) cases PASS')
native_panel_cases=checked_test(ROOT/'tests/refactor_native_panel.py',r'(\d+) cases PASS')
node_discovery_cases=checked_test(ROOT/'tests/refactor_panel_nodes.py',r'Panel node validation total: (\d+) cases PASS')
fold_panel_cases=checked_test(ROOT/'tests/refactor_fold_panel.py',r'Fold panel compatibility: (\d+) execution groups PASS')
outdoor_fold_cases=checked_test(ROOT/'tests/refactor_outdoor_fold.py',r'Outdoor fold compatibility: (\d+) cases PASS')
native_transport_cases=checked_test(ROOT/'tests/refactor_native_transport.py',r'(\d+) cases PASS')
panel_access_cases=checked_test(ROOT/'tests/refactor_panel_access.py',r'Panel access total: (\d+) cases PASS')
panel_interaction_cases=checked_test(ROOT/'tests/refactor_panel_interaction.py',r'(\d+) cases PASS')
raw_output_cases=checked_test(ROOT/'tests/refactor_raw_output.py',r'(\d+) cases PASS')
raw_panel_transaction_cases=checked_test(ROOT/'tests/refactor_raw_panel_transaction.py',r'(\d+) cases PASS')
brightness_control_cases=checked_test(ROOT/'tests/refactor_brightness_control.py',r'(\d+) cases PASS')
recovery_path_cases=checked_test(ROOT/'tests/refactor_recovery_paths.py',r'(\d+) cases PASS')
status_advice_cases=checked_test(ROOT/'tests/refactor_status_advice.py',r'(\d+) cases PASS')
configuration_cases=checked_test(ROOT/'tests/refactor_configuration.py',r'(\d+) cases PASS')
dim_aod_cases=checked_test(ROOT/'tests/refactor_dim_aod.py',r'DIM AOD coordination: (\d+) cases PASS')
theme_curve_cases=checked_test(ROOT/'tests/refactor_theme_curve.py',r'Theme curve and system defaults: (\d+) cases PASS')
ui_navigation_cases=checked_test(ROOT/'tests/refactor_ui_navigation.py',r'(\d+) cases PASS')
release_packaging_cases=checked_test(ROOT/'tests/refactor_release_packaging.py',r'(\d+) cases PASS')
refactor_coefficient_cases=checked_test(ROOT/'tests/refactor_coefficient_curve.py',r'(\d+) cases PASS')
refactor_coefficient_firmware_cases=0 if args.skip_device_fixtures else checked_test(ROOT/'tests/refactor_coefficient_firmware.py',r'(\d+) checks PASS')
app_update_cases=checked_test(ROOT/'tests/refactor_app_updates.py',r'(\d+) cases PASS')
dialog_layout_cases=checked_test(ROOT/'tests/refactor_dialog_layout.py',r'(\d+) cases PASS')
app_preferences_cases=checked_test(ROOT/'tests/refactor_app_preferences.py',r'(\d+) cases PASS')
ui_preferences_cases=checked_test(ROOT/'tests/refactor_ui_preferences.py',r'(\d+) cases PASS')
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
outdoor_compatibility_firmware_cases=0
if not args.skip_device_fixtures:outdoor_compatibility_firmware_cases=checked_test(ROOT/'tests/refactor_outdoor_compatibility_firmware.py',r'(\d+) checks PASS')
foreground_firmware_cases=0
if not args.skip_device_fixtures:foreground_firmware_cases=checked_test(ROOT/'tests/refactor_foreground_firmware.py',r'(\d+) checks PASS')
scene_settings_cases=checked_test(ROOT/'tests/refactor_scene_settings.py',r'(\d+) cases PASS')
scene_cases=checked_test(ROOT/'tests/refactor_scenes.py',r'(\d+) cases PASS')
scene_hook_cases=checked_test(ROOT/'tests/refactor_scene_hooks.py',r'(\d+) cases PASS')
scene_firmware_cases=0
if not args.skip_device_fixtures:scene_firmware_cases=checked_test(ROOT/'tests/refactor_scene_firmware.py',r'(\d+) checks PASS')
if args.verify_only:
    verification={'build':BUILD,'version':VERSION,'version_code':VERSION_CODE,'mode':'verification_only','device_fixtures':not args.skip_device_fixtures,'compatibility_contract_sha256':maintenance_hashes(),
                  'cases':{key:value for key,value in list(locals().items()) if key.endswith('_cases') and isinstance(value,int)},
                  'source_sha256':{p.relative_to(SRC).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((SRC/'java').rglob('*.java'))},
                  'apk_replaced':False,'published':False,'device_tested':False}
    (OUT/'verification-only.json').write_text(json.dumps(verification,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Verification-only complete; existing APK, source, bundle and website artifacts retained')
    raise SystemExit(0)
NATIVE=SRC/'assets/hyperlux-main-panel'
NDK=SDK/'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe'
run([NDK,'--target=aarch64-linux-android34','-Wl,--strip-all','-std=c11','-Os','-static','-Wall','-Wextra','-Werror','-Wno-misleading-indentation','-o',NATIVE,SRC/'native/main_panel.c'])
COMPILED=OUT/'resources.zip';UNSIGNED=OUT/'unsigned.apk'
run([BT/'aapt2.exe','compile','--dir',SRC/'res','-o',COMPILED])
run([BT/'aapt2.exe','link','-I',ANDROID,'--manifest',SRC/'AndroidManifest.xml','-A',SRC/'assets','-o',UNSIGNED,COMPILED])
DEX=OUT/'dex';DEX.mkdir(exist_ok=True)
inputs=[p for p in CLASSES.rglob('*.class') if not p.name.startswith('RefactorTest')]
APP_INPUT=class_archive(OUT/'d8-app-inputs.jar',inputs,CLASSES)
run(['java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','34','--lib',ANDROID,'--lib',DEP,'--output',DEX,APP_INPUT])
with zipfile.ZipFile(UNSIGNED,'a',zipfile.ZIP_DEFLATED) as z:
    z.write(DEX/'classes.dex','classes.dex')
ALIGNED=OUT/'aligned.apk';run([BT/'zipalign.exe','-f','-p','4',UNSIGNED,ALIGNED])
if not KEY.exists():run(['keytool','-genkeypair','-keystore',KEY,'-storepass','androidtest','-keypass','androidtest','-alias','lumacurve-experimental','-dname','CN=LumaCurve Experimental,O=RongShangs','-keyalg','RSA','-keysize','3072','-validity','3650'])
DIST.mkdir(parents=True,exist_ok=True)
APK=DIST/('HyperLux-'+ARTIFACT_VERSION+'.apk')
run(['java','-jar',BT/'lib/apksigner.jar','sign','--ks',KEY,'--ks-pass','pass:androidtest','--ks-key-alias','lumacurve-experimental','--out',APK,ALIGNED])
run(['java','-jar',BT/'lib/apksigner.jar','verify','--verbose',APK])
run([BT/'zipalign.exe','-c','4',APK])
native_asset_cases=checked_test(ROOT/'tests/refactor_native_asset.py',r'(\d+) cases PASS',APK)
# Root recovery does not load any Xposed classes, and works if the app is uninstalled.
HELPER=DIST/'luma-refactor-helper.jar'
names={'DiagnosticShareFiles.class','RootCommand.class','RootWorkspace.class','RootAccess.class','ActivePanelNode.class','ActivePanelNode$DisplaySource.class','ControlCenterRoot.class','ControlCenterMapping.class','OutdoorNodeTarget.class','RootFeatureChecks.class','FeatureSelfCheck.class','FeatureSelfCheck$Probe.class','OutdoorNodeRoot.class','OutdoorNodeProfile.class','ConfigurationCompatibility.class','PanelNodeDiscovery.class','SystemVersion.class','SystemVersion$Reader.class','SystemVersion$Result.class','NativePanelAsset.class','NativePanelClient.class','NativePanelRoot.class','BrightnessControlOptions.class','RootControl.class','RootSettings.class','ForegroundUser.class','CurvePlan.class','ThemeCurveOptions.class','SavedCurve.class','CurveIdentity.class','TraditionalCurve.class','ThermalPolicy.class','MemoryPolicy.class','MemoryOptions.class','MemoryScene.class','LowLightThresholds.class','LowLightAssistGate.class','PersistentMemory.class','DelayPolicy.class','LegacyModules.class','AppBuild.class','LowLightPolicy.class','DiagnosticCollector.class','AdvancedOptions.class','AdvancedPolicy.class','OutdoorOptions.class','OutdoorPolicy.class','ConfigurationFile.class','ConfigurationFile$Imported.class','StatusTransport.class','StatusTransport$Reader.class'}
HELPER_INPUT=class_archive(OUT/'d8-helper-inputs.jar',[p for p in inputs if p.name in names or p.name.startswith(('DiagnosticCollector$','RootSettings$','RootCommand$'))],CLASSES)
run(['java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','34','--lib',ANDROID,'--output',HELPER,HELPER_INPUT])
run(['C:/msys64/usr/bin/bash.exe','-n',SRC/'restore_refactor_hook_android.sh'])
meta={'control_center_demo_countdown_visible':False,'control_center_demo_finger_style':'solid','control_center_demo_finger_color_fixed_per_theme':True,'control_center_mapping_route_shortcut':False,'control_center_demo_track_width_dp':60,'control_center_demo_track_radius_dp':14,'control_center_demo_finger_inside_track':True,'control_center_demo_white_handle':False,'control_center_limit_reset_inline':True,'control_center_mapping_footnotes':False,'control_center_mapping_duplicate_recheck':False,'control_center_demo_cases':control_center_demo_cases,'control_center_partition_ordinary_span':.75,'control_center_partition_user_ceiling':True,'control_center_partition_source_thumb_clamped':True,'control_center_auto_hbm_display_only':True,'control_center_auto_high_takeover_revalidated':True,'control_center_demo_visible_only':True,'control_center_partition_cases':control_center_partition_cases,'control_center_partition_mode':4,'control_center_partition_second_stroke_ms':3000,'control_center_partition_return_policy_configurable':True,'control_center_memory_ordinary_auto_only':True,'control_center_partition_device_tested':False,'control_center_whole_node_cases':control_center_node_cases,'control_center_whole_node_mode':3,'control_center_whole_node_route':'raw_node_v3','control_center_whole_node_device_tested':False,'control_center_settings_cases':control_center_settings_cases,'control_center_settings_independent_card':True,'secondary_navigation_insets_inside_scroll':True,'control_center_ui_ack_assumed':False,'control_center_calibration_cases':control_center_calibration_cases,'control_center_calibration_domains':True,'control_center_calibration_device_tested':False,'dim_aod_cases':dim_aod_cases,'dim_aod_coordination':True,'dim_aod_device_tested':False,'dim_aod_extra_sampling':False,'theme_curve_cases':theme_curve_cases,'independent_dark_mode_curve':True,'restore_system_defaults':True,'control_center_mapping_entry_visible':True,'about_page_cases':about_page_cases,'about_square_hero':True,'about_sheet_covers_hero':False,'about_icon_in_scroll_flow':True,'about_marquee_independent_speeds':True,'about_marquee_adaptive_rows':True,'about_compact_button_minimum_dp':40,'about_icon_radius_dp':48,'about_icon_sized_before_measure':True,'about_marquee_max_rows':10,'about_marquee_max_per_row':1,'about_version_inline_baseline':False,'about_identity_centered':True,'about_version_line':'below_description','about_hero_card_surface_matches':False,'about_header_corner_radii_dp':[48,48,18,18],'about_button_system_elevation_disabled':True,'about_supporter_marquee':True,'about_marquee_only_while_visible':True,'dark_surface_fallback':True,'navigation_press_rebound':True,'navigation_hide_show_scale':True,'navigation_press_scale':.97,'navigation_hidden_scale':.90,'navigation_minimum_bottom_gap_dp':18,'scroll_elasticity_cases':scroll_elasticity_cases,'diagnostic_sharing_cases':diagnostic_sharing_cases,'scroll_indicators_hidden':True,'elastic_scroll_viewports':True,'diagnostic_share_confirmation_chooser':True,'diagnostic_share_read_only_grant':True,'floating_search_cases':floating_search_cases,'log_summary_cases':log_summary_cases,'floating_navigation':True,'navigation_autohide_pages':['settings','about'],'primary_header_pages':['status'],'live_settings_search':True,'transparent_system_bars':True,'review_safety_cases':review_safety_cases,'temporary_field_ownership_checks':True,'hook_rollback_continues_on_failure':True,'root_access_cases':root_access_cases,'root_capability_isolation':True,'root_settings_routes':['external_provider','cmd_settings','settings_cli'],'root_settings_write_replay':False,'app_partial_diagnostics_fallback':True,'ui_preferences_cases':ui_preferences_cases,'ui_preferences_alias_independent':True,'app_preferences_cases':app_preferences_cases,'launcher_icon_toggle':True,'launcher_settings_entry_retained':True,'settings_notice_first_visit':False,'settings_notice_countdown_ms':0,'settings_notice_local_acknowledgement':False,'outdoor_nit_ceiling':True,'outdoor_nit_ceiling_default':0,'outdoor_nit_ceiling_mapping':'primary_display_physical_nit','outdoor_nit_ceiling_preserved_by_presets':True,'sensor_callback_bursts_coalesced':True,'sensor_confirmation_interruptions_preserved':True,'dark_lock_requested_period_us':1000000,'disabled_output_paths_skip_service_checks':True,'device_observer_cases':device_observer_cases,'display_scheduling_cases':display_scheduling_cases,'screen_hook_deferred_cleanup':True,'unowned_screen_off_provider_writes':False,'oem_recalculation_queued':True,'sample_diagnostics_queued':True,'scene_settings_cases':scene_settings_cases,'scene_settings_inline':True,'scene_entry_exit_confirmation_separate':True,'scene_effect_controls':['touch_release_seconds','sunlight_enter','sunlight_exit'],'status_reading_gap_dp':8,'status_sensor_and_screen_row_dp':80,'status_fusion_row_dp':88,'status_fusion_inner_inset_dp':12,'status_fusion_value_gap_dp':4,'status_fusion_scene_gap_dp':2,'status_scene_separate_container':False,'status_thermal_limiting_text_red':False,'status_thermal_temperature_alert_red':True,'status_thermal_temperature_alert_scope':'thermal_output_limit_or_outdoor_temperature_block','status_fusion_rows_share_available_height':True,'outdoor_range_controls_in_main_settings':True,'status_protection_row_dp':108,'status_lux_clickable':False,'scene_cases':scene_cases,'scene_hook_cases':scene_hook_cases,'scene_custom_rules_max':0,'scene_legacy_rules_ignored':True,'scene_controls':9,'scene_catalog_entries':17,'scene_defaults_follow_oem':True,'scene_rules_only_restrict_entry':True,'scene_rules_confirm_entry_and_exit':True,'scene_rules_missing_facts_follow_oem':True,'status_curve_footer_single_line':True,'status_scene_entry_in_fusion':True,'status_output_protection_columns':3,'status_presentation_cases':status_presentation_cases,'scene_firmware_cases':scene_firmware_cases,'status_curve_guidance_matches_live_kind':True,'status_curve_guidance_in_blue_container':True,'status_modes_visible':True,'branch_report_sections':7,'build':BUILD,'version':ARTIFACT_VERSION,'version_code':VERSION_CODE,'test_build':IS_TEST,'internal_test_build':IS_INTERNAL,'release_channel':'internal' if IS_INTERNAL else 'beta' if IS_BETA else 'compatibility_test' if IS_TEST else 'release','app_name':'HyperLux','architecture':'oem_active_curve_backend_hook','status_layout_cases':status_layout_cases,'status_page_uniform_spacing':False,'status_page_regular_gap_dp':16,'status_page_threshold_gap_dp':32,'status_page_horizontal_inset_dp':18,'status_protection_inner_inset_dp':12,'status_protection_cell_gap_dp':8,'status_protection_centered_cells':True,'status_protection_temperature_source':'battery','settings_home_scroll_restored':True,'scene_existing_conditions_editable':True,'status_page_short_viewport_scroll':False,'status_page_fixed_viewport':True,'navigation_indicator_translation':True,'navigation_shadow_in_view_bounds':True,'navigation_auto_reveal_at_scroll_edge':False,'navigation_inset_source':'tappable_elements_and_cutout','advice_text_cases':advice_text_cases,'status_advice_inline_guidance':True,'status_advice_guidance_in_all_states':True,'status_advice_height_changes_with_state':False,'app_update_cases':app_update_cases,'app_update_ignore_per_version':True,'app_update_downloads':['website','github_apk'],'ui_navigation_cases':ui_navigation_cases,'release_packaging_cases':release_packaging_cases,'formal_release_certificate_sha256':EXPECTED_RELEASE_CERT,'memory_pending_model_scope_checked':True,'live_limit_evidence_reset_on_config':True,'brightness_observer_cleanup_isolated':True,'status_advice_cases':status_advice_cases,'recovery_path_cases':recovery_path_cases,'status_advice_read_only':True,'status_limit_evidence_max_age_ms':5000,'settings_apply_in_navigation':True,'dark_lock_sensor_manager':'automatic_brightness_controller','dark_lock_sensor_matching':'type_and_handle','protected_hbm_threshold_access_fixed':True,'confirmed_night_driving_low_light_guard':True,'oem_night_debounce_budget_preserved':True,'low_light_threshold_value_diagnostics':True,'valid_zero_assist_bounded_confirmation':True,'manual_panel_backend':'raw_main_node','framework_output_bridge':False,'primary_node_auto_discovery':True,'node_discovery_cases':node_discovery_cases,'primary_node_permissions_bound_to_identity':True,'system_version_preflight':True,'system_version_cases':system_version_cases,'thermal_ceiling_range':[38,50],'outdoor_maximum_preset':True,'panel_interaction_cases':panel_interaction_cases,'raw_output_cases':raw_output_cases,'raw_panel_unlock_restore':True,'raw_panel_pauses_during_keyguard_and_aod':True,'raw_panel_relative_gesture':True,'raw_panel_positive_output_isolation':True,
      'device_verified':False,'state_writer_cases':state_writer_cases,'state_writes_off_display_thread':True,'state_writer_pending_groups_max':3,'state_writer_chunk_order_preserved':True,'panel_access_cases':panel_access_cases,'primary_node_access_open_probe':True,'primary_node_probe_no_brightness_write':True,'native_build':'raw08-slider','draft_health_cases':draft_health_cases,'draft_changes_during_save_preserved':True,'native_health_monotonic_heartbeat_ms':2000,'native_health_max_age_ms':5000,'raw_dark_handover_auto_recovery':True,'raw_lost_ack_session_rollback':True,'memory_firmware_checks':memory_firmware_cases,'compile_api':82,'legacy_compile_api':82,'compile_api_sha256':API_SHA,
      'sdk_compile':37,'sdk_min':34,'separate_output_daemon':True,'raw_main_panel_native_guard':True,'native_source_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (SRC/'native').glob('*') if p.is_file()},'native_guard_sha256':hashlib.sha256(NATIVE.read_bytes()).hexdigest(),
      'firmware_static_checks':not args.skip_device_fixtures,'host_cases':host_cases,
      'package':'top.rongshangs.lumacurve','languages':['zh','en'],'direct_point_editor':True,'system_light_change_delays_optional':True,'apk_update_repo':'RongShangs/LumaCurve',
      'pipeline_readonly_trace':True,'pipeline_calculation_capacity':24,'output_trace_capacity':16,'low_light_stability_optional':True,'low_light_stability_default':False,'low_light_scoped_thresholds':True,'low_light_small_route_protected':True,'low_light_aux_gate_bounded':True,'low_light_aux_gate_default':False,
      'advanced_optional_parameters':14,'advanced_default_enabled':False,'advanced_hook_model_verified':True,'advanced_firmware_collections':2 if not args.skip_device_fixtures else 0,
      'memory_restore_deferred_from_oem_hooks':True,'memory_reset_notification_deferred':True,'miui_watchdog_file_capture':True,'miui_watchdog_max_files':6,'fault_diagnostics_prioritized':True,'fault_dropbox_cli_date_filter':False,'fault_dropbox_cli_scope':'retained_tag_entries','fault_raw_lookback_days':3,'fault_command_truncation':'tail','fault_raw_anr_max_files':6,'fault_raw_dropbox_max_files':10,'fault_raw_file_limit_bytes':8*1024*1024,'fault_logs_on_demand_only':True,'diagnostic_cases':diagnostic_cases,'status_transport_cases':status_transport_cases,'advanced_hook_model_cases':advanced_cases,'advanced_firmware_checks':advanced_firmware_cases,
      'user_identity_cases':user_identity_cases,'native_asset_cases':native_asset_cases,'native_transport_cases':native_transport_cases,'native_guard_bytes':NATIVE.stat().st_size,'raw_panel_transaction_cases':raw_panel_transaction_cases,'native_panel_cases':native_panel_cases,'brightness_control_cases':brightness_control_cases,'dark_lock_default':False,'dark_lock_enter_lux_default':10,'dark_lock_exit_lux_default':50,'dark_lock_minutes_default':1,'dark_lock_exit_seconds_default':1,'dark_lock_existing_preferences_preserved':True,'manual_tile':True,'mode_owner_system_server':True,'configuration_cases':configuration_cases,'configuration_schema':2,'curve_floor_editable':True,'settings_submenus':10,'settings_independent_screens':True,'settings_parameters_always_visible':False,'settings_details_default_collapsed':True,'settings_category_presets':True,'settings_preset_groups':9,'accessible_draft_switches':True,'sensor_status_explained':True,'unsaved_settings_notice':True,'page_transitions':True,'page_transitions_respect_system':True,'curve_comparison_cases':curve_comparison_cases,'curve_comparison_lines':2,'state_curve_lines':1,'state_curve_actual_only':True,'curve_stroke_dp':2,'responsive_dialogs':True,'outdoor_opr_optional':True,'outdoor_opr_default_enabled':False,'outdoor_opr_before_thermal':True,'outdoor_cases':outdoor_cases,'outdoor_firmware_checks':outdoor_firmware_cases,'outdoor_firmware_collections':3 if not args.skip_device_fixtures else 0,'outdoor_optional':True,'outdoor_default_enabled':False,'outdoor_uses_oem_animation':True,'outdoor_driver_writes':False,
      'curve_backends':['refactor','physical_mapping'],'baseline':'current_device_local_curve','traditional_hook_model_cases':traditional_cases,'traditional_firmware_checks':traditional_firmware_cases,
      'persistent_manual_memory':True,'persistent_memory_default':False,'persistent_memory_cases':memory_cases,'memory_lifecycle_cases':memory_lifecycle_cases,'persistent_memory_record_limit':8192,'persistent_memory_retention_days':[1,90],'persistent_memory_capacity':'backend_native','memory_lifecycle_observed':True,'memory_wake_same_scene_default':True,'memory_native_reset_override_default':False,'dark_mode':'system','editable_base_points':4,'module_long_term_learning':False,'oem_anchor_memory_adjustable':True,'thermal_display_relaxation':True,'source_sha256':{p.relative_to(SRC).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in (SRC/'java').rglob('*.java')},
      'apk_sha256':hashlib.sha256(APK.read_bytes()).hexdigest()}
# A smoke report belongs to one exact APK; a rebuild must not inherit old device claims.
meta.update({'physical_calibration_cases':physical_calibration_cases,'physical_calibration_firmware_cases':physical_calibration_firmware_cases,'physical_calibration_routes':['in_range_original_route','aosp_physical_clamped'],'physical_calibration_neutral_original_object':True,'physical_calibration_native_limits_preserved':True,'physical_calibration_device_tested':False})
meta.update({'refactor_coefficient_cases':refactor_coefficient_cases,'refactor_coefficient_firmware_cases':refactor_coefficient_firmware_cases,'refactor_profiles':['anchors_7','coefficients_9_22'],'refactor_coefficient_device_tested':False,'dialog_layout_cases':dialog_layout_cases,'update_dialog_height_fraction':.82,'update_dialog_fixed_actions_measured_first':True,'beta_build':IS_BETA,'single_sensor_firmware_cases':single_sensor_firmware_cases,'traditional_animation_cases':traditional_animation_cases,'traditional_animation_firmware_cases':traditional_animation_firmware_cases,'animation_routes':['oem_animation','traditional_ramp'],'single_main_input_status':True})
meta.update({'feature_compatibility_cases':feature_compatibility_cases,'feature_page_cases':feature_page_cases,'feature_check_page':'logs_secondary','feature_check_routes_expandable':True,'feature_check_unused_routes_explained':True,'feature_check_no_brightness_probe':True,'compatibility_additive_required':True,'upgrade_preserves_preferences_required':True,'hbm_tuning_profiles':['timed_budget','ambient_only'],'hbm_zero_timers_preserved':True,'ordinary_proximity_disabled_explained':True,'control_center_mapping_entry_visible':True,'memory_keep_ranges':['mode_switch','screen_off','reboot'],'memory_keep_ranges_independent':True,'memory_keep_mode_default':False,'memory_keep_forever_default':False,'memory_session_only_without_disk':True,'memory_mode_change_uses_existing_observer':True,'memory_keep_group':'保持记忆','curve_integrity_cases':curve_integrity_cases,'curve_baseline_readback':True,'curve_repair_scope':'reset_drag_handover_known_baselines','curve_integrity_device_tested':False,'assist_compat_firmware_cases':assist_compat_firmware_cases,'assist_compat_cases':assist_compat_cases,'assist_sensor_compatibility':'missing_oem_slot_and_constructor_cache','assist_sensor_extra_listener':False,'assist_sensor_device_tested':False,'control_center_cases':control_center_cases,'legacy_api_cases':legacy_api_cases,'session_tools_cases':session_tools_cases,'modern_api':None,'hook_interface':'legacy_api_82','modern_api_packaged':False,'hot_reload_enabled':False,'update_popup_page':'status_once_per_foreground_visit','control_center_node_modes':['off','manual_only','manual_and_auto','full_node_manual_hold','partitioned_nodes'],'control_center_mapping':'coordinate_v2_or_raw_node_v3_or_partition_v4','control_center_auto_kept_enabled':False,'control_center_legacy_auto_kept_enabled':True,'control_center_raw_writer':True,'control_center_raw_writer_shared_with_manual_panel':True,'control_center_node_hold_backend':'raw_panel','control_center_v3_releases_dark_lock':True,'control_center_v3_auto_off_on_drag':True,'control_center_v3_auto_on_releases_hold':True,'control_center_v3_reboot_app_connection_required':True,'control_center_v3_dynamic_fold_listener_supported':False,'control_center_calibration_off_display_thread':True,'control_center_device_tested':False,'temporary_screen_on_scope':'until_screen_off_or_disable','beta_build':IS_BETA,'release_channel':'beta' if IS_BETA else meta['release_channel'],
    'feature_self_check_cases':feature_self_check_cases,'feature_checks_every_start':True,'os_version_gate':False,'feature_check_readiness_retry_ms':[1000,5000,15000],'outdoor_node_settings_cases':outdoor_node_settings_cases,'outdoor_node_cases':outdoor_node_cases,'outdoor_node_opt_in':True,'outdoor_node_model_allowlist':False,'outdoor_node_max_burst_ms':None,'outdoor_node_duration_policy':'outdoor_session','outdoor_node_target_kind':'raw_node','outdoor_node_target_default':'device_maximum','outdoor_node_target_independent_of_strength':True,'outdoor_node_target_maximum_source':'primary_node_max_brightness','outdoor_node_lease_ms':1500,'outdoor_node_system_override_yields':True,'firmware_revalidation_cases':firmware_revalidation_cases,'ota_matching_baseline_revalidated':True,'saved_curve_cases':saved_curve_cases,'learned_curve_to_baseline':True,'dark_lock_manual_behavior_selectable':True,'dark_lock_manual_exit_default':False,'dark_lock_manual_default_continues':True,'memory_restore_unlock_default':False,
    'saved_curve_transaction_cases':saved_curve_transaction_cases,'learned_curve_save_requires_fresh_snapshot':True,
    'application_auto_brightness_list':True,'foreground_source':'system_focus_and_visibility_events','foreground_settle_ms':150,'foreground_polling':False,'foreground_multiwindow_visible_tasks':True,'foreground_service_queries_off_display_thread':True,'foreground_query_timeout_ms':3000,'foreground_query_worker_idle_seconds':10,'foreground_query_queue_limit':8,'foreground_task_query_limit':128,'foreground_fallback':'focus_only_with_notice','additional_resident_process':False,'additional_resident_process_scope':'foreground_application_list','outdoor_node_additional_process_when_enabled':True,
    'foreground_firmware_cases':foreground_firmware_cases,
    'outdoor_compatibility_cases':outdoor_compatibility_cases,'outdoor_compatibility_firmware_cases':outdoor_compatibility_firmware_cases,
    'outdoor_sdr_profiles':['privacy_aware','legacy_opr'],'outdoor_sdr_profiles_mutually_exclusive':True,
    'os3_compatibility_cases':os3_compatibility_cases,'os3_device_tested':False,
    'response_scene_profiles':['native_driving','native_without_driving'],
    'hbm_controller_profiles':['enabled_field','native_support_method'],
    'outdoor_target_profiles':['override_target','legacy_custom_target'],
    'feature_check_page_open_rechecks':False,'feature_check_resume_rechecks':False,
    'feature_check_timestamp':'fixed_wall_time_with_elapsed_fallback','feature_check_compact_rows':True,
    'quick_setup_cases':quick_setup_cases,'quick_setup_questions':6,'quick_setup_option_text_views_separate':True,'quick_setup_content_centered':False,'quick_setup_text_alignment':'start','quick_setup_summary':'single_compact_list','quick_setup_unselected_preserves':True,'quick_setup_entry_requires_invitation_confirmation':True,'quick_setup_header_exit':False,'quick_setup_choice_minimum_dp':64,'quick_setup_invitation_max_width_dp':480,'quick_setup_invitation_style':'title_and_description','quick_setup_invitation_adaptive_body':True,'quick_setup_optional':True,'quick_setup_invitation_once':True,'quick_setup_draft_until_confirmation':True,'welcome_cases':welcome_cases,'first_run_welcome':True,'tutorial_pages':5,'tutorial_replay_separate_card':True,'tutorial_page_transitions':True,'tutorial_feedback_page':True,'tutorial_feedback_github':False,
    'tutorial_replay_entry':'运行状态与界面','tutorial_replay_includes_welcome':True,'welcome_optional_tutorial':False,'welcome_logo_float':True,
    'welcome_logo_float_duration_ms':3600,'welcome_logo_float_amplitude_dp':4,'welcome_first_run_completion':'after_page_5',
    'welcome_header_visible':False,'welcome_secondary_description':False,'welcome_brand_highlight':True,'tutorial_replay_hint_pages':[1,2,3,4,5],
    'welcome_before_root_request':True,'legacy_notice_record_preserved':True})
device_report=ROOT/'build'/('device-smoke-'+BUILD+'.json')
if device_report.is_file() and device_report.stat().st_size<=16384:
    report=json.loads(device_report.read_text(encoding='utf-8'))
    if report.get('build')==BUILD and report.get('apk_sha256')==meta['apk_sha256']:
        meta['device_smoke']=report
meta.update({'fold_sensor_firmware_cases':fold_sensor_firmware_cases,'fold_sensor_device_tested':False,'main_sensor_profiles':['oem_main','oem_selected_secondary','oem_selected_fov'],'sensor_rebind_resets_samples':True,'sensor_listener_generation_isolated':True,'sensor_status_read_only':True,'compatibility_contract_cases':compatibility_contract_cases,'hook_transaction_cases':hook_transaction_cases,'compatibility_contract_sha256':maintenance_hashes()})
meta.update({'opr_envelope_cases':opr_envelope_cases,'opr_envelope_firmware_cases':opr_envelope_firmware_cases,'opr_envelope_device_tested':False})
meta.update({'outdoor_write_retry_cases':outdoor_retry_cases,'outdoor_write_retry_max_attempts':3,'outdoor_write_retry_delays_ms':[80,160],'outdoor_write_retry_device_tested':False})
meta.update({'saved_curve_physical_capture_rounding_ulps':4,'saved_curve_persisted_validation_strict':True,'saved_curve_refactor_capture_unchanged':True,'saved_curve_precision_device_tested':False})
meta.update({'update_automatic_offer_page':'status','update_automatic_offer_once_per_foreground_visit':True,'update_about_automatic_popup':False,'update_about_manual_check':True,'update_ignore_preference_preserved':True,'update_configuration_recreation_preserves_offer':True,'update_dialog_buttons_wrap_content':True,'update_dialog_button_minimum_dp':48,'update_dialog_layout_device_tested':False})
meta.update({'outdoor_fold_cases':outdoor_fold_cases,'outdoor_provenance_routes':['selected_strategy','automatic_strategy2_reason'],'outdoor_fold_physical_lease':True,'outdoor_fold_target_device_tested':False})
(OUT/'verification.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
with zipfile.ZipFile(APK) as z:
    assert z.testzip() is None
    assert z.read('assets/xposed_init').strip()==b'top.rongshangs.lumacurve.refactor.LegacyEntry'
    assert 'classes.dex' in z.namelist()
    assert not any(name.startswith('META-INF/xposed/') for name in z.namelist()),'unexpected modern entry metadata'
    assert b'Lio/github/libxposed/' not in z.read('classes.dex'),'unexpected modern API runtime reference'
    assert not any(name.endswith('.jar') or 'firmware' in name for name in z.namelist());assert 'assets/hyperlux-main-panel' in z.namelist()
archive_prefix='HyperLux' if IS_TEST else 'LumaCurve'
source=DIST/(archive_prefix+'-'+ARTIFACT_VERSION+'-source.zip')
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
    for name in ['refactor_control_center_demo.py','refactor_control_center_partition.py','refactor_control_center_nodes.py','refactor_control_center_settings.py','refactor_control_center_calibration.py','workspace_scratch.py','refactor_dim_aod.py','refactor_theme_curve.py','refactor_physical_calibration.py','refactor_physical_calibration_firmware.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    for name in ['refactor_outdoor_write_retry.py','refactor_opr_envelope.py','refactor_opr_envelope_firmware.py','refactor_coefficient_curve.py','refactor_coefficient_firmware.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    z.write(ROOT/'tests/refactor_dialog_layout.py','tests/refactor_dialog_layout.py')
    z.write(ROOT/'tests/refactor_outdoor_fold.py','tests/refactor_outdoor_fold.py')
    for name in ['refactor_traditional_animation.py','refactor_traditional_animation_firmware.py','refactor_single_sensor_firmware.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    for n in ['refactor_fold_panel.py','refactor_fold_sensor_firmware.py','refactor_assist_compat.py','refactor_assist_compat_firmware.py','refactor_control_center.py','refactor_control_center_root.py','refactor_legacy_api.py','refactor_session_tools.py','refactor_outdoor_node_settings.py','refactor_feature_self_check.py','refactor_outdoor_node.py','refactor_firmware_revalidation.py']:z.write(ROOT/'tests'/n,'tests/'+n)
    z.write(ROOT/'tests/refactor_saved_curve.py','tests/refactor_saved_curve.py')
    z.write(ROOT/'tests/refactor_curve_integrity.py','tests/refactor_curve_integrity.py')
    for name in ['refactor_feature_compatibility.py','refactor_feature_page.py','refactor_os3_compatibility.py','refactor_welcome.py','refactor_quick_setup.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    z.write(ROOT/'tests/refactor_saved_curve_transaction.py','tests/refactor_saved_curve_transaction.py')
    z.write(ROOT/'tests/refactor_foreground_firmware.py','tests/refactor_foreground_firmware.py')
    for name in ['refactor_outdoor_compatibility.py','refactor_outdoor_compatibility_firmware.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    for name in ['refactor_state_writer.py','refactor_recovery_paths.py','refactor_status_advice.py','refactor_ui_navigation.py','refactor_release_packaging.py','refactor_app_updates.py','refactor_root_access.py','refactor_review_safety.py','refactor_floating_search.py','refactor_log_summary.py','refactor_scroll_elasticity.py','refactor_diagnostic_sharing.py','refactor_about_page.py','refactor_app_preferences.py','refactor_ui_preferences.py','refactor_advice_text.py','refactor_status_layout.py','refactor_status_presentation.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    for name in ['refactor_scene_firmware.py','refactor_scenes.py','refactor_scene_hooks.py','refactor_scene_settings.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    for p in sorted(SRC.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(ROOT))
    z.write(ROOT/'LICENSE','LICENSE');z.write(ROOT/'README.md','README.md');z.write(ROOT/'CHANGELOG.md','CHANGELOG.md');z.write(Path(__file__),'tools/build_refactor_hook_test.py');z.write(ROOT/'tests/refactor_hook_firmware.py','tests/refactor_hook_firmware.py');z.write(ROOT/'tests/refactor_diagnostics.py','tests/refactor_diagnostics.py')
    for name in ['device_runtime_observer.py','refactor_display_scheduling.py','refactor_panel_access.py','refactor_draft_and_health.py','refactor_panel_nodes.py','refactor_curve_comparison.py','refactor_panel_interaction.py','refactor_raw_output.py','refactor_native_transport.py','refactor_native_asset.py','refactor_raw_panel_transaction.py','refactor_native_panel.py','refactor_brightness_control.py','refactor_advanced_hooks.py','refactor_advanced_firmware.py','refactor_traditional.py','refactor_traditional_firmware.py','refactor_user_identity.py','refactor_system_version.py','refactor_outdoor.py','refactor_outdoor_firmware.py','refactor_configuration.py','refactor_status_transport.py','refactor_persistent_memory.py','refactor_memory_lifecycle.py','refactor_memory_firmware.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    for folder in ['docs/releases','docs/reviews','docs/guides','docs/archive','docs/architecture']:
        for p in sorted((ROOT/folder).rglob('*.md')):z.write(p,p.relative_to(ROOT))
    for p in sorted((ROOT/'docs/architecture').glob('*.json')):z.write(p,p.relative_to(ROOT))
    # Unpublished local workflows are excluded, matching docs/release-policy.md.
    z.write(ROOT/'tools/verify_compatibility.py','tools/verify_compatibility.py')
    for name in ['refactor_hook_transactions.py','refactor_compatibility_contract.py']:z.write(ROOT/'tests'/name,'tests/'+name)
    for name in ['AGENTS.md','PROJECT.md','PROJECT_SCOPE.md','docs/README.md','docs/来源与许可证.md']:z.write(ROOT/name,name)
    z.write(ROOT/'tools/sync_project_document.py','tools/sync_project_document.py')
    z.write(ROOT/'docs/release-policy.md','docs/release-policy.md')
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
