import json,re
ORDER=['prologue']+['chapter%d'%i for i in range(1,9)]+['epilogue']
WORD=re.compile(r"(?<![\w½])[^\W\d_]+(?:['’\-][^\W\d_]+)*")
ELI=re.compile(r"^(jusqu|lorsqu|puisqu|quoiqu|presqu|qu|[ldjmtscn])['’]",re.I)
KEEP_FR={"aujourd'hui","quelqu'un","quelqu'une","peut-être","avant-plan","pont-levis","c'est-à-dire","au-dessus","là-dessus","là-bas","avant-hier","ci-inclus","saintes-maries","ville-d'avray","au-delà","là-haut","grand-père","grand-mère","arc-en-ciel","vis-à-vis","non-seulement"}
SPLIT_SUFFIX={"ci","là","même","mêmes","je","tu","il","elle","on","nous","vous","ils","elles","moi","toi","le","la","les","lui","leur","en","y","t","ce"}
def letters(series):
    for f in ORDER:
        d=json.load(open(f'data/{series}/{f}.json'))
        for l in d['letters']:
            yield f,l
def tok_fr(w):
    out=[]
    while True:
        lw=w.lower().replace('’',"'")
        if lw in KEEP_FR: out.append(w);return out
        m=ELI.match(w)
        if m and lw not in KEEP_FR:
            out.append(w[:m.end()]); w=w[m.end():]; continue
        break
    if '-' in w:
        lw=w.lower()
        if lw in KEEP_FR: return out+[w]
        parts=w.split('-')
        if all(p.lower() in SPLIT_SUFFIX for p in parts[1:]) or len(parts)>1:
            # keep-list checked; split everything
            res=[]
            for p in parts:
                res+=tok_fr(p) if ("'" in p or '’' in p) else [p]
            return out+res
    out.append(w); return out
def tokens(text,lang):
    res=[]
    for m in WORD.finditer(text):
        w=m.group(0)
        if lang=='fr': res+=tok_fr(w)
        else: res.append(w)
    return [t for t in res if t.replace("'","").replace("’","").replace("-","").isalpha()]
