"""Tokens + lemma keys for the English ('en') and Korean ('ko') lines of the letters (learners of English / Korean).
run_tl(series, lang) -> [(letter id, message index, [(surface, key, fn)])]; fn is '' for English and the Korean
function morphemes ('었/EP+어/EF') otherwise. Hand fixes: src/<lang>_map.txt (form=key) and src/<lang>_occ.txt
(series/letter:message:token index=key)."""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tok import letters, tokens_tl
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src') + '/'
_F = {}
def freq(lang):
    if lang not in _F:
        _F[lang] = {l.split('\t')[0]: int(l.split('\t')[1]) for l in open('data/lexicon/%s_freq.tsv' % lang, encoding='utf-8')}
    return _F[lang]
def load_kv(path):
    d = {}
    if os.path.exists(path):
        for line in open(path, encoding='utf-8'):
            line = line.rstrip('\n')
            if '=' in line and not line.startswith('#'):
                a, b = line.split('=', 1); d[a] = b
    return d
# Korean homographs the analyser tells apart by tag: (lemma, tag) -> key
KO_SPLIT = {('안', 'NNG'): '안#2', ('천', 'NR'): '천#2'}
START = '.!?…:«»„“"—(;‘\'['
def en_key(w, start, m, F):
    import simplemma
    if w in m: return m[w]
    if w == 'I': return 'I'
    if w[0].isupper() and (not start or F.get(w.lower(), 99999) > 30000): return w
    if w.lower() in m: return m[w.lower()]
    return simplemma.lemmatize(w.lower(), lang='en')
def run_tl(series, lang):
    m = load_kv(SRC + f'{lang}_map.txt'); o = load_kv(SRC + f'{lang}_occ.txt')
    F = freq(lang) if lang == 'en' else None
    out = []
    for f, l in letters(series):
        for mi, msg in enumerate(l['messages']):
            text = msg[lang]; toks = tokens_tl(text, lang)
            spans = []; pos = 0
            for t in toks:
                j = text.find(t, pos); spans.append((j, j + len(t))); pos = j + len(t)
            if lang == 'ko':
                from ko_lemma import analyze
                ana = analyze(text, spans)
            pairs = []
            for i, (t, (a, b)) in enumerate(zip(toks, spans)):
                occ = o.get(f"{series}/{l['id']}:{mi}:{i}")
                if lang == 'en':
                    pre = text[:a].rstrip(); start = (not pre) or pre[-1] in START
                    key = occ or en_key(t, start, m, F); fn = ''
                else:
                    key, head, fn = ana[i]
                    key = KO_SPLIT.get((key, head), key)
                    hand = occ or m.get(t)
                    if hand:   # 'key' or 'key|fn'
                        key, _, fn2 = hand.partition('|'); fn = fn2 if '|' in hand else fn
                pairs.append((t, key, fn))
            out.append((l['id'], mi, pairs))
    return out
