import sys,json,re,collections
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tok import *
import simplemma
V = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src') + '/'
def load_map(lang, series=None):
    """src/<lang>_map.txt (form=lemma, all series) + src/<lang>_map_<series>.txt (that series only, wins);
    src/<lang>_occ.txt keys are <series>/<letter id>:<message>:<token index>."""
    m={};o={}
    import os as _os
    for path in [V+f'{lang}_map.txt'] + ([V+f'{lang}_map_{series}.txt'] if series else []):
        if not _os.path.exists(path): continue
        for line in open(path, encoding='utf-8'):
            line=line.rstrip('\n')
            if '=' in line and not line.startswith('#'):
                a,b=line.split('=',1); m[a]=b
    try:
        for line in open(V+f'{lang}_occ.txt'):
            line=line.strip()
            if line and not line.startswith('#'):
                a,b=line.split('=',1); o[a]=b
    except FileNotFoundError: pass
    return m,o
def run(series,lang):
    m,o=load_map(lang, series)
    global M
    M=m
    out=[]  # (lid, mi, [(surface,lemma)])
    lower_seen=set()
    caps=set()
    for f,l in letters(series):
        for msg in l['messages']:
            text=msg[lang]; pos=0
            for t in tokens(text,lang):
                j=text.find(t,pos); pre=text[:j].rstrip(); pos=j+len(t)
                if t[0].islower(): lower_seen.add(t)
                if t[0].isupper() and pre and pre[-1] not in '.!?…:«»„“”"—(;' and t not in m: caps.add(t)
    for f,l in letters(series):
        for mi,msg in enumerate(l['messages']):
            text=msg[lang]
            toks=tokens(text,lang)
            pairs=[]
            # sentence-start detection
            pos=0
            for i,t in enumerate(toks):
                j=text.find(t,pos); pre=text[:j].rstrip(); pos=j+len(t)
                start = (not pre) or pre[-1] in '.!?…:«»„“"—(;'
                key=f"{series}/{l['id']}:{mi}:{i}"
                if key in o: lem=o[key]
                elif t in m: lem=m[t]
                elif t.lower() in m and t not in caps: lem=m[t.lower()]
                elif t in caps and lang in ('fr','es'): lem=t
                elif lang=='de': lem=de_lemma(t,start,caps,lower_seen)
                else: lem=simplemma.lemmatize(t.lower(),lang=lang)
                pairs.append([t,lem])
            out.append((l['id'],mi,pairs))
    return out

FQ={}
def fq(lang):
    if lang not in FQ:
        FQ[lang]={l.split('\t')[0]:int(l.split('\t')[1]) for l in open('data/lexicon/%s_freq.tsv'%lang)}
    return FQ[lang]
def modern(w):
    f=fq('de')
    cands=[w]
    r=w
    if re.search(r'(^|[^cs])[Tt]h',r): r=re.sub(r'(?<![cCsS])([Tt])h',r'\1',r)
    r=r.replace('seyn','sein').replace('sey','sei').replace('beyd','beid').replace('meyn','mein').replace('Bey','Bei').replace('bey','bei')
    r=re.sub(r'irt\b','iert',r);r=re.sub(r'iren\b','ieren',r);r=re.sub(r'irte','ierte',r)
    r=re.sub(r'^C(?=[aouhlr])','K',r);r=re.sub(r'^C(?=[eiy])','Z',r)
    cands.append(r)
    for c in list(cands): cands.append(c.replace('ß','ss'))
    best=w;br=f.get(w.lower(),10**9)
    for c in cands:
        rk=f.get(c.lower(),10**9)
        if rk<br: best,br=c,rk
    return best
def de_lemma(t,start,caps,lower_seen):
    if t in M: return M[t]
    if t.lower() in M and t[0].islower(): return M[t.lower()]
    w=t
    if start and t[0].isupper():
        lw=t[0].lower()+t[1:]
        if lw in lower_seen and t not in caps: w=lw
    mo=modern(w)
    if mo.lower() in M: return M[mo.lower()]
    return simplemma.lemmatize(mo,lang='de')
