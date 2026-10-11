#!/usr/bin/env python3
"""Build data/<series>/daily_<lang>.json (lang = en | ko: learners of English / Korean) from lexicon_<lang>.json + gloss_<lang>.json
+ chapter files + src/phrases_<series>_<lang>.txt (lid|message index|verbatim text|en|ko|note_ko|note_en).
Same format as daily.json; spots are always [] (plate spots keep their words for these languages).
Run from the repo root after build_tl.py:  python3 scripts/vocab/daily_tl.py vincent en"""
import sys, os, json, re, collections
V = os.path.dirname(os.path.abspath(__file__)) + '/'
sys.path.insert(0, V)
from tok import letters

series, lang = sys.argv[1], sys.argv[2]
LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']
lex = json.load(open(f'data/{series}/lexicon_{lang}.json', encoding='utf-8'))
F = lex['fields']
ent = {}
for row in lex['entries']:
    e = dict(zip(F, row))
    ent[e['id'] or e['lemma']] = e
gloss = json.load(open(f'data/{series}/gloss_{lang}.json', encoding='utf-8'))['letters']
phr = {}
pp = V + f'src/phrases_{series}_{lang}.txt'
if os.path.exists(pp):
    for line in open(pp, encoding='utf-8'):
        p_ = line.rstrip('\n').split('|')
        if len(p_) != 7: continue
        lid_, m_, t_, en_, ko_, nk_, ne_ = p_
        phr.setdefault(lid_, []).append({'t': t_, 'm': int(m_), 'ko': ko_, 'en': en_, 'note_ko': nk_, 'note_en': ne_})

def key_of(t):
    return t if isinstance(t, str) else t[1]

def band(lid):
    if lid.startswith('p'): return ['A1', 'A2']
    if lid.startswith('e'): return ['B2', 'C1']
    ch = int(lid[1:].split('-')[0])
    if ch <= 2: return ['A1', 'A2']
    if ch <= 5: return ['B1']
    return ['B2', 'C1']

def widen(b):
    lo = max(0, LEVELS.index(b[0]) - 1); hi = min(5, LEVELS.index(b[-1]) + 1)
    return LEVELS[lo:hi + 1]

CONTENT = {'n', 'v', 'adj', 'adv'}
def eligible(e):
    if e['pos'] not in CONTENT or e.get('function') or not e.get('level') or e.get('id'): return False
    if lang == 'en':   # no single letters, no capitalised words (days, titles), no hyphenated compounds
        return len(e['lemma']) > 1 and e['lemma'].islower() and '-' not in e['lemma'] and "'" not in e['lemma']
    return True        # Korean: one-syllable words (꽃, 별, 밤) are real words here

chosen = set()
doc = ('Per letter id (order P, I…VIII, E): words = up to 8 lemma keys of lexicon_%s.json for the "today\'s words" card, chosen from that '
       'letter\'s %s line by chapter band (P/I–II: A1–A2, III–V: B1, VI–VIII/E: B2–C1; widened by one band only when needed), never repeating '
       'a lemma of an earlier letter; nouns, verbs, adjectives, adverbs only — no names, function words, homograph entries%s. '
       'spots = [] (plate spots keep their words for this language). phrases = 1–2 expressions copied verbatim from the %s line: '
       '{t: text, m: message index, ko, en, note_ko, note_en}%s.') % (
    lang, 'English' if lang == 'en' else 'Korean',
    ', single letters, capitalised or hyphenated words' if lang == 'en' else ' (one-syllable words are allowed)',
    'English' if lang == 'en' else 'Korean',
    ' — ko = Korean translation, en = plain-English paraphrase' if lang == 'en' else ' — en = English translation, ko = plain-Korean paraphrase')
out = {'_doc': doc, 'lang': lang, 'letters': {}}
stats = collections.Counter()
for f, l in letters(series):
    lid = l['id']
    msgs = gloss[lid]
    toks = [key_of(t) for m in msgs for t in m]
    cnt = collections.Counter(toks)
    order = []
    for k in toks:
        if k not in order: order.append(k)
    curated = set()
    for w in l.get('words') or []:
        t = re.sub(r'\([^)]*\)', '', w.get(lang) or '').strip()
        t = re.sub(r'^(to|the|a|an) ', '', t)
        curated.add(t)
    b = band(lid)
    def pick(levels):
        cands = []
        for k in order:
            e = ent[k]
            if k in chosen or not eligible(e) or e['level'] not in levels: continue
            s = 0.0
            s += 3 if e['lemma'] in curated else 0
            s += 1 if e['pos'] in ('n', 'v', 'adj') else 0
            s += 0.5 * min(cnt[k] - 1, 2)
            s += 1 if e['first'] == lid else 0
            s += 0.5 if e.get('example') else 0
            s += 1 if e['level'] in b else 0
            s -= LEVELS.index(e['level']) * 0.1 if b == ['A1', 'A2'] else 0
            cands.append((-s, order.index(k), k))
        cands.sort()
        return cands
    sel = []
    for levels in (b, widen(b)):
        nadv = sum(1 for k in sel if ent[k]['pos'] == 'adv')
        for _, _, k in pick(levels):
            if len(sel) >= 8: break
            if k in sel: continue
            if ent[k]['pos'] == 'adv' and nadv >= 2: continue
            nadv += ent[k]['pos'] == 'adv'
            sel.append(k)
        if len(sel) >= 8: break
    sel.sort(key=order.index)
    chosen.update(sel)
    stats['words'] += len(sel)
    if len(sel) < 8: stats['short'] += 1
    for k in sel: stats['in_band' if ent[k]['level'] in b else 'widened'] += 1
    rec = {'words': sel, 'spots': []}
    if lid in phr:
        rec['phrases'] = phr[lid]; stats['phrases'] += len(phr[lid])
    out['letters'][lid] = rec
json.dump(out, open(f'data/{series}/daily_{lang}.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print(series, lang, dict(stats))
