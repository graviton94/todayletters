"""기초 낱말 덱 후보: 자막 빈도 목록(data/lexicon/<lang>_freq.tsv) 상위 300 표제어 가운데 카드 · 표현 · 닻이 안 다루는 것.
쓰기: python3 scripts/basics.py <출력 폴더>  → <작품>_<언어>.json (사람이 다듬어 data/<작품>/basics[_언어].json 으로)."""
import json, os, re, sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','data')
# goya (스페인어) 는 data/goya 가 생기면 함께
SER={s:l for s,l in {'vincent':'fr','mozart':'de','napoleon':'fr','goya':'es'}.items() if os.path.exists(os.path.join(R,s,'lexicon.json'))}
FILES=['prologue']+[f'chapter{i}' for i in range(1,9)]+['epilogue']
CH=['P','I','II','III','IV','V','VI','VII','VIII','E']
def sfx(lang,orig): return '' if lang==orig else '_'+lang
def letters(s):
    out=[]
    for f,c in zip(FILES,CH):
        if not os.path.exists(f'{R}/{s}/{f}.json'): continue   # 작업 중인 작품: 아직 없는 장
        for l in json.load(open(f'{R}/{s}/{f}.json'))['letters']: out.append((c,l))
    return out
def lexicon(s,lang):
    lx=json.load(open(f'{R}/{s}/lexicon{sfx(lang,SER[s])}.json')); F=lx['fields']
    ents={}
    for e in lx['entries']:
        d=dict(zip(F,e)); ents[d['id'] or d['lemma']]=d
    return ents
def gloss(s,lang): return json.load(open(f'{R}/{s}/gloss{sfx(lang,SER[s])}.json'))['letters']
def tok(t): # (printed, key)
    if isinstance(t,str): return t,t
    return t[0],t[1]

sys.path.insert(0,os.path.join(os.path.dirname(os.path.abspath(__file__)),'vocab'))
import simplemma
def freq_lemmas(lang,N=1000):
    out=[];seen=set()
    if lang=='ko': import ko_lemma
    for line in open(f'{R}/lexicon/{lang}_freq.tsv'):
        w=line.split('\t')[0]
        if not re.fullmatch(r"[^\W\d_][\w'’-]*",w): continue
        if lang=='ko':
            lem,head,_=ko_lemma.analyze(w,[(0,len(w))])[0]
            if head in ('SL','SN','SH'): continue
        else:
            lem=simplemma.lemmatize(w,lang=lang)
        lem=lem.lower()
        if len(lem)<2 and lang!='ko' and lem not in('a','à','y','i','o'): continue
        if lem in seen: continue
        seen.add(lem); out.append(lem)
        if len(out)>=N: break
    return out

def candidates(out):
    """작품 × 언어마다: 많이 쓰는 표제어 300 (top) 과 그 가운데 카드 · 표현 · 편지 낱말 · 닻이 안 다루는 것 (todo)."""
    os.makedirs(out,exist_ok=True)
    for lang in ['fr','de','es','en','ko']:
        if lang not in('en','ko') and lang not in SER.values(): continue
        top=freq_lemmas(lang,300)
        for s,orig in SER.items():
            if lang not in('en','ko') and orig!=lang: continue
            lex=lexicon(s,lang); f2l={}
            for k,e in lex.items():
                L=e['lemma'].lower(); f2l[L]=L
                for f in e['forms'] or []: f2l.setdefault(f.lower(),L)
            da=json.load(open(f'{R}/{s}/daily{sfx(lang,orig)}.json'))['letters']
            cov=set()
            for v in da.values():
                for w in v.get('words',[]): cov.add(lex[w]['lemma'].lower() if w in lex else w.split('#')[0].lower())
                for p in v.get('phrases',[]):
                    for t in re.findall(r"[^\W\d_]+",p['t'].lower()): cov.add(f2l.get(t,t))
            for ch,l in letters(s):
                for w in l.get('words') or []:
                    for x in re.findall(r"[^\W\d_]+",(w.get(lang) or '').lower()): cov.add(f2l.get(x,x))
            for a in json.load(open(f'{R}/{s}/anchors{sfx(lang,orig)}.json'))['anchors']:
                for t in a['k'].split(): cov.add(f2l.get(t,t))
            todo=[w for w in top if w not in cov]
            json.dump(dict(series=s,lang=lang,top=top,todo=todo),open(f'{out}/{s}_{lang}.json','w'),ensure_ascii=False,indent=0)
            print(s,lang,'top',len(top),'todo',len(todo))

if __name__=='__main__':
    candidates(sys.argv[1] if len(sys.argv)>1 else 'basics_candidates')
