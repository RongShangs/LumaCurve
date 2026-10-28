"""The maintenance guard must reject drift, and permit additive routes with old paths intact."""
from pathlib import Path
import copy, importlib.util, json

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('compatibility_contract',ROOT/'tools/verify_compatibility.py')
tool=importlib.util.module_from_spec(spec);spec.loader.exec_module(tool)
manifest=json.loads((tool.ARCH/'compatibility-manifest.json').read_text(encoding='utf-8'))
mapping=json.loads((tool.ARCH/'source-map.json').read_text(encoding='utf-8'))
baseline=json.loads((tool.ARCH/'compatibility-baseline.json').read_text(encoding='utf-8'))
catalogue=tool.load_catalogue()
cases=0
def check(ok):
    global cases
    assert ok,cases;cases+=1
check(not tool.validate(manifest,catalogue,mapping,baseline))
def reject(edit):
    m,c,s,b=map(copy.deepcopy,[manifest,catalogue,mapping,baseline]);edit(m,c,s,b)
    check(bool(tool.validate(m,c,s,b)))
reject(lambda m,c,s,b:m['invariants'].remove('preserve_existing_routes'))
reject(lambda m,c,s,b:m['invariants'].remove('preserve_saved_preferences'))
reject(lambda m,c,s,b:m.update(selection_authority='UI selects fallback'))
reject(lambda m,c,s,b:m['families'].append(m['families'][0]))
reject(lambda m,c,s,b:m['families'][0].update(selector=''))
reject(lambda m,c,s,b:m['families'][0].update(cleanup=''))
reject(lambda m,c,s,b:m['families'][0].update(evidence=[]))
reject(lambda m,c,s,b:m['families'][0].update(tests=['tests/missing_compatibility_test.py']))
reject(lambda m,c,s,b:m['families'][0].update(owners=['UnknownCompatibilityAdapter.java']))
reject(lambda m,c,s,b:m['families'][0]['routes'].append('unimplemented_route'))
# Remove a baseline route explicitly; the last item may now be a valid additive route.
reject(lambda m,c,s,b:c['curve']['routes'].pop(0))
reject(lambda m,c,s,b:[c[f]['routes'].pop() for f in m['families'][0]['features']])
reject(lambda m,c,s,b:c.update(unregistered_feature={'group':0,'routes':[{'id':'new'}]}))
reject(lambda m,c,s,b:s['files'].pop())
reject(lambda m,c,s,b:s['files'].append(s['files'][0]))
reject(lambda m,c,s,b:s['files'][0].update(layer='unowned'))
reject(lambda m,c,s,b:b.update(features={}))
reject(lambda m,c,s,b:next(f for f in m['families'] if f['id']=='hbm').pop('dimensions'))
reject(lambda m,c,s,b:m['abi_variants'].append(m['abi_variants'][0]))
reject(lambda m,c,s,b:m['abi_variants'][0].update(alternatives=[]))
reject(lambda m,c,s,b:m['abi_variants'][0].update(rule=''))
# A new actual route may be registered without editing or deleting the old baseline.
m,c=copy.deepcopy(manifest),copy.deepcopy(catalogue)
for feature in m['families'][0]['features']:
    c[feature]['routes'].append({'id':'future_verified_curve','name':'Future verified adapter','condition':'Complete actual ABI'})
m['families'][0]['routes'].append('future_verified_curve')
check(not tool.validate(m,c,mapping,baseline))
# Registry arrays are defensive copies and unknown features never become a guessed route.
check('return FEATURES.clone()' in (tool.SOURCE/'CompatibilityCatalog.java').read_text(encoding='utf-8'))
check(tool.render(manifest,catalogue,mapping)==(tool.ARCH/'compatibility-index.md').read_text(encoding='utf-8'))
print(f'Compatibility contract regression: {cases} cases PASS; metadata mutation checks, not device tests')
