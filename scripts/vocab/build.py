#!/usr/bin/env python3
"""Build data/<series>/lexicon.json and gloss.json from the letters + the hand-made tables in scripts/vocab/src.
Run from the repo root:  TATOEBA_DIR=<dir with tatoeba_deu.tsv / tatoeba_fra.tsv> python3 scripts/vocab/build.py vincent fr
(Tatoeba extracts: id<TAB>sentence<TAB>en<TAB>ko<TAB>author, sentences of <=12 words; not kept in git.)
Needs: pip install simplemma; apt install espeak-ng.  Then run daily.py and scripts/validate_lexicon.py.
"""
import sys, os, json, re, subprocess, unicodedata, collections
V = os.path.dirname(os.path.abspath(__file__)) + '/'
sys.path.insert(0, V)
from lemmatize import run, modern, fq
from tok import letters, tokens

SRC = V + 'src/'
SCR = os.environ.get('TATOEBA_DIR', '.').rstrip('/') + '/'
series, lang = sys.argv[1], sys.argv[2]
CUT = [('A1', 500), ('A2', 1500), ('B1', 3500), ('B2', 7000), ('C1', 15000)]

def level(rank):
    if rank is None: return 'C2'
    for name, cut in CUT:
        if rank <= cut: return name
    return 'C2'

def strip_acc(s):
    return ''.join(c for c in unicodedata.normalize('NFD', s) if unicodedata.category(c) != 'Mn')

# ---------- annotation table ----------
tab = collections.OrderedDict()
for line in open(SRC + f'{lang}_tab.txt', encoding='utf-8'):
    p = line.rstrip('\n').split('|')
    if len(p) < 4: continue
    tab[p[0]] = dict(pos=p[1], en=p[2], ko=p[3], fn=len(p) > 4 and p[4] == 'F')

# ---------- tokens ----------
res = run(series, lang)
by_letter = collections.OrderedDict()
for lid, mi, pairs in res:
    by_letter.setdefault(lid, []).append(pairs)
forms = collections.OrderedDict(); first = {}; lets = collections.OrderedDict()
for lid, msgs in by_letter.items():
    for pairs in msgs:
        for s, l in pairs:
            forms.setdefault(l, [])
            if s not in forms[l]: forms[l].append(s)
            first.setdefault(l, lid)
            lets.setdefault(l, [])
            if lid not in lets[l]: lets[l].append(lid)

# ---------- hand IPA from the chapter files ----------
hand_ipa = {}
ART = re.compile(r'^(der|die|das|le|la|les|l\'|un|une)\s+', re.I)
for f, l in letters(series):
    ws = list(l.get('words') or [])
    for sp in (l.get('plate') or {}).get('spots', []):
        ws.append(sp.get('word') or {})
    for w in ws:
        t = w.get('de_modern') or w.get(lang)
        ipa = (w.get('ipa') or '').strip('/[] ')
        if not t or not ipa or ' ' in ART.sub('', t): continue
        hand_ipa.setdefault(ART.sub('', t), ipa)

ONSET_DE = {'ʃt', 'ʃp', 'ʃl', 'ʃm', 'ʃn', 'ʃv', 'ʃʁ', 'pʁ', 'bʁ', 'tʁ', 'dʁ', 'kʁ', 'ɡʁ', 'fʁ', 'pl', 'bl', 'kl', 'ɡl', 'fl', 'kv', 'ʃtʁ', 'ʃpʁ', 'kn', 'ɡn', 'ts', 'pf', 'tʃ', 'ʃr'}
VOW = set('aeiouyøœɛɪʊɔəɐæɑɒʏɜɵ')

def de_fix(ipa):
    ipa = ipa.replace('??', 'ʊʁ').replace('ɑː', 'aː').replace('ɑ', 'a').replace('ɜ', 'ɐ')
    ipa = re.sub(r'ː([ˈˌ]?)ɾ(?=$|[^aeiouyøœɛɪʊɔəɐ])', r'ː\1ɐ̯', ipa)
    ipa = ipa.replace('ɾ', 'ʁ').replace('r', 'ʁ').replace('ɔø', 'ɔʏ̯')
    ipa = re.sub(r'a([ɪʊ])(?!̯)', r'a\1̯', ipa)
    # move stress marks to the syllable onset
    out = list(ipa)
    i = 0
    while i < len(out):
        if out[i] in 'ˈˌ':
            mark = out.pop(i)
            j = i
            while j > 0 and out[j - 1] not in VOW and out[j - 1] not in 'ːˈˌ̯':
                j -= 1
            run_ = ''.join(out[j:i])
            if j > 0 and len(run_) >= 2:
                k = j
                for cut in range(j, i):
                    if ''.join(out[cut:i]) in ONSET_DE:
                        k = cut; break
                else:
                    k = i - 1
                j = k
            out.insert(j, mark)
            i += 1
        i += 1
    return ''.join(out)

def espeak(word):
    v = 'de' if lang == 'de' else 'fr'
    o = subprocess.run(['espeak-ng', '-q', '--ipa', '-v', v, word], capture_output=True).stdout.decode('utf-8', 'replace').strip()
    o = re.sub(r'\([a-z]+\)', '', o).replace('_', '')
    o = ' '.join(o.split())
    if lang == 'fr':
        return FR_FIX.get(word) or o.replace('ˈ', '').replace('ˌ', '').replace('ː', '').replace('-', '')
    o = de_fix(o)
    if len(re.findall(r'[aeiouyøœɛɪʊɔəɐʏ]+', o.replace('ɐ̯', ''))) <= 1:
        o = o.replace('ˈ', '').replace('ˌ', '')
    return o
FR_FIX = {'y': 'i', 't': 't', 'tas': 'ta', 'G': 'ʒe', 'M': 'məsjø'}

# ---------- frequency / level ----------
F = fq(lang)
def rank_of(key, entry_forms, pos):
    base = key.split('#')[0]
    cands = [base] + entry_forms
    if lang == 'de':
        cands += [modern(x) for x in entry_forms]
    best = None
    for c in cands:
        c = c.lower().replace('’', "'")
        if len(c) < 2: continue
        r = F.get(c)
        if r is not None and (best is None or r < best): best = r
    if '#' in key:   # homograph entry: frequency of the shared spelling says nothing
        best = None
    return best

NOLEVEL = {'name', 'it', 'fr', 'la', 'abbr'}

# ---------- Tatoeba examples ----------
STOP = set('to the a an of be in on at for and or with as it is by out up off oneself one someone something sb sth its his her'.split())
def keywords(en):
    en = re.sub(r'\([^)]*\)', ' ', en)
    ks = set()
    for part in re.split(r'[,;/]', en):
        for w in re.findall(r"[a-z]+", part.lower()):
            if w in STOP or len(w) < 3: continue
            ks.add(w[:5] if len(w) > 5 else w)
    return ks

tsv = SCR + ('tatoeba_deu.tsv' if lang == 'de' else 'tatoeba_fra.tsv')
want = collections.defaultdict(set)   # lowercase form -> lemma keys
for key, fs in forms.items():
    t = tab[key]
    if t['fn'] or t['pos'] in NOLEVEL or t['pos'] in ('num', 'interj'): continue
    base = key.split('#')[0]
    for x in set(fs + [base] + ([modern(f) for f in fs] if lang == 'de' else [])):
        x = x.lower().replace('’', "'")
        if len(x) >= 2: want[x].add(key)
cands = collections.defaultdict(list)
TW = re.compile(r"[^\W\d_]+(?:['’\-][^\W\d_]+)*")
for line in open(tsv, encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) < 5 or not p[4].strip() or not p[2].strip(): continue
    sid, text, en, ko, author = p[:5]
    ws = TW.findall(text)
    if not ws or len(text.split()) > 12: continue
    low = []
    for wi, w in enumerate(ws):
        lw = w.lower().replace('’', "'")
        if lang == 'de':
            low.append(('N' if w[0].isupper() else 'l') + lw)
            if wi == 0: low.append('l' + lw)
            continue
        low.append(lw)
        if lang == 'fr':
            m = re.match(r"^(jusqu|lorsqu|puisqu|quoiqu|qu|[ldjmtscn])'(.+)$", lw)
            if m: low.append(m.group(2))
    hit = set()
    for lw in low:
        if lang == 'de':
            for key in want.get(lw[1:], ()):
                isn = tab[key]['pos'].startswith('n.')
                if (lw[0] == 'N') == isn: hit.add(key)
            continue
        for key in want.get(lw, ()):
            hit.add(key)
    if not hit: continue
    rs = [F.get(lw[1:] if lang == 'de' else lw, 60000) for lw in low]
    diff = sorted(rs)[-2] if len(rs) > 1 else rs[0]
    enw = set(w[:5] if len(w) > 5 else w for w in re.findall(r'[a-z]+', en.lower()))
    for key in hit:
        sem = bool(keywords(tab[key]['en']) & enw)
        n = len(text.split())
        score = (0 if sem else 1, 0 if ko.strip() else 1, diff // 2000, abs(n - 7))
        cands[key].append((score, int(sid), text, en, ko, author))
examples = {}
used = collections.Counter()
for key in forms:
    c = cands.get(key)
    if not c: continue
    c.sort()
    best = None
    for sc, sid, text, en, ko, author in c[:40]:
        if sc[0] == 1:   # no semantic confirmation through the English line: skip
            continue
        pen = used[sid]
        if best is None or (pen, sc) < best[0]:
            best = ((pen, sc), sid, text, en, ko, author)
    if best:
        _, sid, text, en, ko, author = best
        used[sid] += 1
        ex = {'id': sid, 'text': text, 'en': en, 'author': author}
        if ko.strip(): ex['ko'] = ko
        examples[key] = ex

# ---------- assemble ----------
FIELDS = ['lemma', 'id', 'forms', 'pos', 'gender', 'ko', 'en', 'ipa', 'level', 'first', 'letters', 'example', 'function']
EXF = ['id', 'text', 'en', 'ko', 'author']
lid_order = list(by_letter)
entries = []
for key in forms:
    t = tab[key]
    pos = t['pos']; e = {}
    base = key.split('#')[0]
    e['lemma'] = base
    if '#' in key: e['id'] = key
    e['forms'] = forms[key]
    if pos.startswith('n.'):
        e['pos'] = 'n'; e['gender'] = pos[2:]
    elif pos in ('it', 'fr', 'la'):
        e['pos'] = 'foreign:' + pos
    else:
        e['pos'] = pos
    e['ko'] = t['ko']; e['en'] = t['en']
    if pos not in ('it', 'fr', 'la', 'abbr'):
        e['ipa'] = hand_ipa.get(base) or espeak(base)
    if pos not in NOLEVEL:
        e['level'] = level(rank_of(key, forms[key], pos))
    e['first'] = first[key]
    e['letters'] = ' '.join(lets[key])
    if key in examples:
        ex = examples[key]; e['example'] = [ex.get(k) for k in EXF]
    if t['fn']: e['function'] = 1
    row = [e.get(f) for f in FIELDS]
    while row and row[-1] is None: row.pop()
    entries.append(row)

doc = {
    '_doc': ('Vocabulary of the original-language letter text, one row per lemma (modern spelling). Rows are arrays in the order of "fields" '
             '(trailing nulls dropped): lemma; id (only for homographs such as "ton#2", else null; gloss.json refers to entries by id||lemma); '
             'forms as printed; pos (n, v, adj, adv, prep, conj, pron, det, art, num, interj, name, abbr, foreign:it|fr|la); gender for nouns (m, f, n, pl); '
             'ko; en (gloss fitting the letter); ipa (original language, modern form: hand IPA from the chapter files when present, else espeak-ng, approximate); '
             'level from FrequencyWords rank, the lower of lemma and printed forms: A1<=500, A2<=1500, B1<=3500, B2<=7000, C1<=15000, else C2 '
             '(names, foreign words and abbreviations have none); first = letter id of first occurrence; letters = space-separated letter ids; '
             'example = [tatoeba id, text, en, ko|null, author] (CC BY 2.0 FR, credit the author, https://tatoeba.org/sentences/show/<id>); function = 1 for function words. '
             'See data/lexicon/README.md.'),
    'lang': lang,
    'fields': FIELDS,
    'example_fields': EXF,
    'entries': entries,
}
os.makedirs(f'data/{series}', exist_ok=True)
json.dump(doc, open(f'data/{series}/lexicon.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))

gloss = {'_doc': ('Per letter id: one array per message (same order as letter.messages) with every word token of the original text in order. '
                  'A token is either a string (the surface form, which is itself the lexicon key) or [surface, key] where key = lexicon id||lemma. '
                  'Digits and punctuation are not tokens.'),
         'lang': lang, 'letters': {}}
for lid, msgs in by_letter.items():
    gloss['letters'][lid] = [[s if s == l else [s, l] for s, l in pairs] for pairs in msgs]
json.dump(gloss, open(f'data/{series}/gloss.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print(series, 'entries', len(entries), 'examples', len(examples), 'tokens', sum(len(p) for m in by_letter.values() for p in m))
