#!/usr/bin/env python3
"""Build data/<series>/lexicon_<lang>.json and gloss_<lang>.json for learners of English (lang=en) or Korean (lang=ko):
the same formats as lexicon.json / gloss.json (original language), built from the messages' "en" / "ko" lines.
Run from the repo root:  TATOEBA_DIR=<dir with tatoeba_eng.tsv / tatoeba_kor.tsv> python3 scripts/vocab/build_tl.py vincent en
(Tatoeba extracts: id<TAB>sentence<TAB>en<TAB>ko<TAB>author, sentences of <=12 words; not kept in git; see data/lexicon/README.md.)
Needs: pip install simplemma kiwipiepy; apt install espeak-ng.  Then run daily_tl.py and scripts/validate_lexicon.py.
Hand-made tables: src/<lang>_tab.txt (key|pos|en|ko|F), src/<lang>_map.txt, src/<lang>_occ.txt.
"""
import sys, os, json, re, subprocess, collections
V = os.path.dirname(os.path.abspath(__file__)) + '/'
sys.path.insert(0, V)
from tl_lemma import run_tl, freq
from tok import letters

SRC = V + 'src/'
SCR = os.environ.get('TATOEBA_DIR', '.').rstrip('/') + '/'
series, lang = sys.argv[1], sys.argv[2]
assert lang in ('en', 'ko')
CUT = [('A1', 500), ('A2', 1500), ('B1', 3500), ('B2', 7000), ('C1', 15000)]
NOLEVEL = {'name', 'abbr'}

def level(rank):
    if rank is None: return 'C2'
    for name, cut in CUT:
        if rank <= cut: return name
    return 'C2'

# ---------- annotation table: key|pos|en|ko|F ----------
tab = {}
for line in open(SRC + f'{lang}_tab.txt', encoding='utf-8'):
    p = line.rstrip('\n').split('|')
    if len(p) < 4: continue
    tab[p[0]] = dict(pos=p[1], en=p[2], ko=p[3], fn=len(p) > 4 and p[4] == 'F')

# ---------- tokens ----------
res = run_tl(series, lang)
by_letter = collections.OrderedDict()
for lid, mi, pairs in res:
    by_letter.setdefault(lid, []).append(pairs)
forms = collections.OrderedDict(); first = {}; lets = collections.OrderedDict()
for lid, msgs in by_letter.items():
    for pairs in msgs:
        for s, k, fn in pairs:
            forms.setdefault(k, [])
            if s not in forms[k]: forms[k].append(s)
            first.setdefault(k, lid)
            lets.setdefault(k, [])
            if lid not in lets[k]: lets[k].append(lid)
missing = [k for k in forms if k not in tab]
if missing:
    sys.exit(f'keys missing in src/{lang}_tab.txt: {missing}')

def nolevel(pos):
    return pos in NOLEVEL or pos.startswith('foreign')

# ---------- IPA (English only; Hangul is read as written) ----------
def espeak(word):
    o = subprocess.run(['espeak-ng', '-q', '--ipa', '-v', 'en-us', word], capture_output=True).stdout.decode('utf-8', 'replace').strip()
    o = re.sub(r'\([a-z]+\)', '', o).replace('_', '')
    return ' '.join(o.split())

# ---------- frequency / level ----------
F = freq(lang)
if lang == 'ko':
    # FrequencyWords lists eojeol (inflected word forms). Lemma frequency = summed counts of all list entries
    # whose analysis (kiwipiepy, same rules as the letters) gives that lemma; rank = position in that summed list.
    from ko_lemma import kiwi, split_morphs
    cnt = collections.Counter()
    path = 'data/lexicon/ko_freq.tsv'
    words = [l.split('\t')[0] for l in open(path, encoding='utf-8')]
    # the TSV keeps only rank; weight by Zipf (1/rank) so summing forms of one lemma is meaningful
    for r, w in enumerate(words, 1):
        ms = [(t.form, t.tag, t.lemma) for t in kiwi().tokenize(w)]
        if not ms: continue
        lem, head, fn = split_morphs(ms)
        cnt[lem] += 1.0 / r
    LR = {lem: i for i, (lem, _) in enumerate(cnt.most_common(), 1)}

def rank_of(key, entry_forms):
    if '#' in key: return None   # homograph entry: the shared spelling's frequency says nothing
    if lang == 'ko':
        cands = [LR.get(key)] + [F.get(f) for f in entry_forms]
    else:
        cands = [F.get(c.lower().replace('’', "'")) for c in [key] + entry_forms if len(c) >= 2]
    cands = [c for c in cands if c is not None]
    return min(cands) if cands else None

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

tsv = SCR + ('tatoeba_eng.tsv' if lang == 'en' else 'tatoeba_kor.tsv')
eligible = {k for k in forms if not tab[k]['fn'] and not nolevel(tab[k]['pos']) and tab[k]['pos'] not in ('num', 'interj', 'particle')}
want = collections.defaultdict(set)
if lang == 'en':
    for k in eligible:
        for x in set(forms[k] + [k.split('#')[0]]):
            x = x.lower().replace('’', "'")
            if len(x) >= 2: want[x].add(k)
else:
    for k in eligible:
        want[k.split('#')[0]].add(k)
cands = collections.defaultdict(list)
TW = re.compile(r"[^\W\d_]+(?:['’\-][^\W\d_]+)*")
lines = [l.rstrip('\n').split('\t') for l in open(tsv, encoding='utf-8')]
if lang == 'ko':
    from ko_lemma import analyze
for p in lines:
    if len(p) < 5 or not p[4].strip(): continue
    sid, text, en, ko, author = p[:5]
    if len(text.split()) > 12: continue
    if lang == 'en':
        low = [w.lower().replace('’', "'") for w in TW.findall(text)]
        hit = {k for w in low for k in want.get(w, ())}
        rs = [F.get(w, 60000) for w in low] or [60000]
    else:
        spans = [(m.start(), m.end()) for m in TW.finditer(text)]
        if not spans: continue
        lem = [a[0] for a in analyze(text, spans)]
        hit = {k for w in lem for k in want.get(w, ())}
        rs = [LR.get(w, 60000) for w in lem]
    if not hit: continue
    diff = sorted(rs)[-2] if len(rs) > 1 else rs[0]
    n = len(text.split())
    for k in hit:
        if lang == 'ko':   # the English translation must show the gloss (filters other senses)
            enw = set(w[:5] if len(w) > 5 else w for w in re.findall(r'[a-z]+', en.lower()))
            sem = bool(keywords(tab[k]['en']) & enw)
            score = (0 if sem else 1, diff // 2000, abs(n - 7))
        else:              # English sentence: prefer one with a Korean translation
            score = (0, 0 if ko.strip() else 1, diff // 2000, abs(n - 7))
        cands[k].append((score, int(sid), text, en, ko, author))
examples = {}
used = collections.Counter()
for k in forms:
    c = cands.get(k)
    if not c: continue
    c.sort()
    best = None
    for sc, sid, text, en, ko, author in c[:40]:
        if sc[0] == 1: continue
        pen = used[sid]
        if best is None or (pen, sc) < best[0]:
            best = ((pen, sc), sid, text, en, ko, author)
    if best:
        _, sid, text, en, ko, author = best
        used[sid] += 1
        examples[k] = [sid, text, en or None, ko or None, author]

# ---------- assemble ----------
FIELDS = ['lemma', 'id', 'forms', 'pos', 'gender', 'ko', 'en', 'ipa', 'level', 'first', 'letters', 'example', 'function']
EXF = ['id', 'text', 'en', 'ko', 'author']
entries = []
for k in forms:
    t = tab[k]; e = {}
    base = k.split('#')[0]
    e['lemma'] = base
    if '#' in k: e['id'] = k
    e['forms'] = forms[k]
    e['pos'] = t['pos']
    e['ko'] = t['ko']; e['en'] = t['en']
    if lang == 'en' and not t['pos'].startswith('foreign') and t['pos'] != 'abbr':
        e['ipa'] = espeak(base)
    if not nolevel(t['pos']):
        e['level'] = level(rank_of(k, forms[k]))
    e['first'] = first[k]
    e['letters'] = ' '.join(lets[k])
    if k in examples: e['example'] = examples[k]
    if t['fn']: e['function'] = 1
    row = [e.get(f) for f in FIELDS]
    while row and row[-1] is None: row.pop()
    entries.append(row)

if lang == 'en':
    doc_txt = ('Vocabulary of the English line of the letters (learners of English), one row per lemma. Same format as lexicon.json: rows are arrays in '
               'the order of "fields" (trailing nulls dropped): lemma; id (only for homographs, else null; gloss_en.json refers to entries by id||lemma); '
               'forms as printed; pos (n, v, adj, adv, prep, conj, pron, det, art, num, interj, name, abbr, foreign:fr|it|la|de); gender unused (null); '
               'ko = Korean gloss; en = short learner definition in simple English (not a translation); ipa = espeak-ng en-us, approximate '
               '(none for foreign words and abbreviations); level from FrequencyWords en_50k rank, the lower of lemma and printed forms: A1<=500, A2<=1500, '
               'B1<=3500, B2<=7000, C1<=15000, else C2 (names, foreign words, abbreviations have none); first = letter id of first occurrence; '
               'letters = space-separated letter ids; example = [tatoeba id, English sentence, null, ko translation|null, author] — English examples come '
               'from Tatoeba\'s CC0 export, so author is "CC0" (no attribution required; still link https://tatoeba.org/sentences/show/<id>); '
               'function = 1 for function words. Clitics are their own entries (\'s \'m \'re \'ve \'ll \'d); n\'t contractions are whole entries. '
               'See data/lexicon/README.md.')
else:
    doc_txt = ('Vocabulary of the Korean line of the letters (learners of Korean), one row per dictionary lemma: nouns without particles, '
               'verbs and adjectives as stem + 다 (공부하다, 즐겁다). Same format as lexicon.json: rows are arrays in the order of "fields" '
               '(trailing nulls dropped): lemma; id (only for homographs such as "눈#2" snow, else null; gloss_ko.json refers to entries by id||lemma); '
               'forms = eojeol as printed (with their particles and endings); pos (n, v, adj, adv, det, pron, num, interj, particle, name, '
               'foreign:fr|it|la); gender unused; en = English gloss; ko = short Korean definition; no ipa (Hangul is read as written); '
               'level from FrequencyWords ko_50k: the list holds eojeol, so each list entry is analysed (kiwipiepy) to its lemma and the 1/rank weights '
               'of all its forms are summed; lemma rank in that summed list (or the best rank of a printed form, if better) gives A1<=500, A2<=1500, '
               'B1<=3500, B2<=7000, C1<=15000, else C2 (names and foreign words have none); first, letters as in lexicon.json; '
               'example = [tatoeba id, Korean sentence, en translation, null, author] (CC BY 2.0 FR, credit the author, '
               'https://tatoeba.org/sentences/show/<id>; the sentence contains the lemma by analysis and its English line shows the gloss); '
               'function = 1 for function words. See data/lexicon/README.md.')
doc = {'_doc': doc_txt, 'lang': lang, 'fields': FIELDS, 'example_fields': EXF, 'entries': entries}
json.dump(doc, open(f'data/{series}/lexicon_{lang}.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))

TAGS = {
    'JKS': ['주격 조사', 'subject marker'], 'JKC': ['보격 조사', 'complement marker'], 'JKG': ['관형격 조사 (의)', 'possessive (of)'],
    'JKO': ['목적격 조사', 'object marker'], 'JKB': ['부사격 조사 (에, 에서, 로…)', 'adverbial particle (at, in, to, with…)'],
    'JKV': ['호격 조사', 'vocative (calling)'], 'JKQ': ['인용 조사', 'quotation particle'], 'JX': ['보조사 (은/는, 도, 만…)', 'topic/focus particle (as for, also, only…)'],
    'JC': ['접속 조사 (와/과, 랑)', 'connective particle (and, with)'], 'EP': ['선어말 어미 (시제·높임)', 'pre-final ending (tense, honorific)'],
    'EF': ['종결 어미', 'sentence ending'], 'EC': ['연결 어미', 'connective ending'], 'ETN': ['명사형 어미', 'nominalizing ending (-ing)'],
    'ETM': ['관형형 어미', 'adnominal ending (which/that …)'], 'XSN': ['명사 파생 접미사 (들, 쯤, 씩…)', 'noun suffix (plural, about, each…)'],
    'XSV': ['동사 파생 접미사', 'verb suffix'], 'XSA': ['형용사 파생 접미사', 'adjective suffix'], 'VCP': ['서술격 조사 (이다)', 'copula (to be)'],
    'VX': ['보조 용언', 'auxiliary verb'], 'VV': ['동사', 'verb'], 'VA': ['형용사', 'adjective'], 'NNG': ['명사', 'noun'], 'NNB': ['의존 명사', 'bound noun'],
    'MAG': ['부사', 'adverb'], 'SSC': ['닫는 따옴표', 'closing quote'], 'SSO': ['여는 따옴표', 'opening quote'], 'SN': ['숫자', 'number'],
}
gloss = {'lang': lang, 'letters': {}}
if lang == 'en':
    gloss['_doc'] = ('Per letter id: one array per message (same order as letter.messages) with every word token of the English line in order. '
                     'A token is a string (the surface form, itself the lexicon_en key) or [surface, key] where key = lexicon id||lemma. '
                     'Clitics are separate tokens ("I" + "\'m", "Theo" + "\'s"). Digits and punctuation are not tokens.')
else:
    gloss['_doc'] = ('Per letter id: one array per message (same order as letter.messages) with every eojeol of the Korean line in order '
                     '(an eojeol is cut where punctuation or digits interrupt it). A token is a string (surface = lexicon_ko key), [surface, key] '
                     'or [surface, key, fn]: key = lexicon id||lemma; fn = the particles and endings after the stem, "form/TAG" joined with "+" '
                     '(편지를 -> ["편지를","편지","를/JKO"], 그렸어 -> ["그렸어","그리다","었/EP+어/EF"]); "tags" explains each TAG in ko and en. '
                     'Digits and punctuation are not tokens.')
    used_tags = set()
for lid, msgs in by_letter.items():
    out = []
    for pairs in msgs:
        row = []
        for s, k, fn in pairs:
            if lang == 'ko' and fn:
                row.append([s, k, fn])
                used_tags.update(x.rsplit('/', 1)[1] for x in fn.split('+'))
            else:
                row.append(s if s == k else [s, k])
        out.append(row)
    gloss['letters'][lid] = out
if lang == 'ko':
    gloss['tags'] = {t: TAGS.get(t, [t, t]) for t in sorted(used_tags)}
json.dump(gloss, open(f'data/{series}/gloss_{lang}.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print(series, lang, 'entries', len(entries), 'examples', len(examples), 'tokens', sum(len(p) for m in by_letter.values() for p in m))
