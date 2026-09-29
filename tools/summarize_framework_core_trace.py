"""Analyze full-core numerical output and control windows; no optical claims."""
from pathlib import Path
import argparse,csv,json,statistics,math,re
p=argparse.ArgumentParser();p.add_argument('report',type=Path);a=p.parse_args()
rows=[]
for line in csv.DictReader((a.report/'framework.trace').open(encoding='utf-8')):
    row={key:float(value) for key,value in line.items()}
    if not all(math.isfinite(v) for v in row.values()):raise ValueError('nonfinite trace')
    rows.append(row)
if len(rows)<2:raise ValueError('incomplete trace')
first=rows[0]['time_ms'];groups=[]
for row in rows:
    key=(int(row['mode']),int(row['owned']))
    if not groups or groups[-1]['key']!=key:groups.append({'key':key,'rows':[]})
    groups[-1]['rows'].append(row)
summary={'samples':len(rows),'duration_s':(rows[-1]['time_ms']-first)/1000,
 'median_interval_ms':statistics.median(b['time_ms']-a['time_ms'] for a,b in zip(rows,rows[1:])),
 'units':'algorithm_goal=legacy nominal fraction; limited/request/feedback=framework float; node=raw readback',
 'windows':[],'final_recovery':(a.report/'recovery.txt').read_text(encoding='utf-8'),'optical_linearity_verified':False}
for group in groups:
    s=group['rows'];tail=s[-20:]
    summary['windows'].append({'mode':group['key'][0],'owned':group['key'][1],
      'from_s':(s[0]['time_ms']-first)/1000,'until_s':(s[-1]['time_ms']-first)/1000,'samples':len(s),
      'request_start':s[0]['request'],'request_end':s[-1]['request'],
      'goal_start':s[0]['algorithm_goal'],'goal_end':s[-1]['algorithm_goal'],
      'node_start':s[0]['node'],'node_end':s[-1]['node'],
      'tail_node_min':min(r['node'] for r in tail),'tail_node_max':max(r['node'] for r in tail)})
process_file=a.report/'process-samples.txt'
event_file=a.report/'framework.events'
if process_file.exists() and event_file.exists():
    clock=re.search(r'CPU_CLK_TCK,value=(\d+)',event_file.read_text(encoding='utf-8'))
    summary['process_usage']={'verified_clk_tck':int(clock[1]) if clock else None,'processes':[],
        'scope':'direct core and broker processes only; excludes system_server, children and tracing overhead; CPU=one core 100%'}
    samples={}; text=process_file.read_text(encoding='utf-8')
    marks=list(re.finditer(r'--- tick=(\d+) pid=(\d+) ---\n',text))
    for i,m in enumerate(marks):
        block=text[m.end():marks[i+1].start() if i+1<len(marks) else len(text)]
        lines=block.splitlines(); stat=lines[1]; fields=stat[stat.rfind(')')+2:].split()
        rss=re.search(r'VmRSS:\s+(\d+)',block)
        if not rss:raise ValueError('missing process RSS')
        samples.setdefault(m[2],[]).append({'uptime':float(lines[0].split()[0]),'ticks':int(fields[11])+int(fields[12]),
            'rss_kib':int(rss[1]),'threads':int(fields[17]),'start_ticks':int(fields[19]),'comm':stat[stat.find('(')+1:stat.rfind(')')]})
    for pid,items in samples.items():
        if len(items)<2:continue
        if len({v['start_ticks'] for v in items})!=1:raise ValueError('PID reuse in usage samples')
        elapsed=items[-1]['uptime']-items[0]['uptime']; ticks=items[-1]['ticks']-items[0]['ticks']
        if elapsed<=0 or ticks<0:raise ValueError('invalid process times')
        summary['process_usage']['processes'].append({'pid':pid,'comm':items[0]['comm'],'samples':len(items),
            'duration_s':elapsed,'cpu_one_core_percent':ticks/int(clock[1])/elapsed*100 if clock else None,
            'rss_mib_min':min(v['rss_kib'] for v in items)/1024,'rss_mib_max':max(v['rss_kib'] for v in items)/1024,
            'max_threads':max(v['threads'] for v in items)})
(a.report/'trace-summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
times=[(r['time_ms']-first)/1000 for r in rows]
fig,axes=plt.subplots(3,1,figsize=(11,8),sharex=True,layout='constrained')
axes[0].plot(times,[r['algorithm_goal'] if r['owned'] else math.nan for r in rows],label='Algorithm goal (legacy nominal fraction)',color='#666')
axes[0].set_ylabel('Algorithm coordinate');axes[0].legend(fontsize=8)
axes[1].plot(times,[r['request'] if r['owned'] else math.nan for r in rows],label='Request while owned',color='#2762d6')
axes[1].plot(times,[r['adjusted'] for r in rows],label='Adjusted feedback',ls='--',color='#d88a12')
axes[1].plot(times,[r['limited_goal'] if r['owned'] and r['limited_goal']>=0 else math.nan for r in rows],label='Limited goal',ls=':',color='#22a06b')
axes[1].set_ylabel('Framework float');axes[1].legend(fontsize=8)
axes[2].plot(times,[r['node'] for r in rows],color='#2762d6');axes[2].set_ylabel('Main backlight node');axes[2].set_xlabel('Elapsed seconds')
for group in summary['windows']:
    if not group['owned']:
        for ax in axes:ax.axvspan(group['from_s'],group['until_s'],color='#888',alpha=.13)
for ax in axes:ax.grid(alpha=.2)
fig.suptitle('HyperOS 4 full C core + framework backend (shaded: not owned)')
fig.savefig(a.report/'trace.png',dpi=150);plt.close(fig)
print(json.dumps(summary,indent=2))
