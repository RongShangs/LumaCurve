"""Analyze a returned temporary-strategy trace without confusing its units."""
from pathlib import Path
import argparse, json, statistics
p=argparse.ArgumentParser();p.add_argument('report',type=Path);a=p.parse_args()
rows=[]
for line in (a.report/'trace.txt').read_text(encoding='utf-8',errors='replace').splitlines():
 if line.startswith('SAMPLE,'):
  _,t,phase,target,base,adjusted,node=line.split(',')
  rows.append(dict(t=int(t)/1e9,phase=phase,target=float(target),base=float(base),adjusted=float(adjusted),node=int(node)))
if not rows:raise ValueError('no samples')
summary={'samples':len(rows),'duration_s':rows[-1]['t']-rows[0]['t'],
 'median_interval_s':statistics.median(b['t']-a['t'] for a,b in zip(rows,rows[1:])),
 'brightness_values':sorted({r['base'] for r in rows}),'phases':[]}
for phase in dict.fromkeys(r['phase'] for r in rows):
 xs=[r for r in rows if r['phase']==phase];tail=xs[-10:]
 steps=[b['node']-a['node'] for a,b in zip(xs,xs[1:])]
 summary['phases'].append(dict(phase=phase,samples=len(xs),request_start=xs[0]['target'],request_end=xs[-1]['target'],
  node_start=xs[0]['node'],node_end=xs[-1]['node'],node_step_min=min(steps,default=0),node_step_max=max(steps,default=0),
  tail_node_min=min(r['node'] for r in tail),tail_node_max=max(r['node'] for r in tail),
  max_adjusted_request_error=max(abs(r['adjusted']-r['target']) for r in xs)))
(a.report/'trace-summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
x=[r['t']-rows[0]['t'] for r in rows]
fig,(top,bottom)=plt.subplots(2,1,figsize=(10,6),sharex=True,layout='constrained')
top.plot(x,[r['target'] for r in rows],label='Requested temporary brightness',color='#2862d6',lw=2)
top.plot(x,[r['adjusted'] for r in rows],label='AdjustedBrightness feedback',color='#ea9824',lw=1.4,ls='--')
top.plot(x,[r['base'] for r in rows],label='Brightness field (unchanged)',color='#88919d',lw=1,ls=':')
top.set_ylabel('Framework float');top.legend(fontsize=8);top.grid(alpha=.2)
bottom.plot(x,[r['node'] for r in rows],color='#2862d6',lw=2)
bottom.set_ylabel('Main backlight node');bottom.set_xlabel('Elapsed seconds');bottom.grid(alpha=.2)
fig.suptitle('HyperOS 4 temporary brightness test - numerical trace, not optical measurement')
fig.savefig(a.report/'trace.png',dpi=170)
plt.close(fig)
print(json.dumps(summary,indent=2))
