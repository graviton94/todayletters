#!/usr/bin/env python3
"""Build data/<series>/daily.json from lexicon.json + gloss.json + chapter files + src/phrases_<series>.txt
(lid|message index|verbatim text|en|ko|note_ko|note_en) + src/daily_override_<series>.json (hand-checked spot replacements).
Run from the repo root after build.py:  python3 scripts/vocab/daily.py vincent fr"""
import sys, os, json, re, unicodedata, collections
V = os.path.dirname(os.path.abspath(__file__)) + '/'
sys.path.insert(0, V)
from tok import letters

series, lang = sys.argv[1], sys.argv[2]
LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']
lex = json.load(open(f'data/{series}/lexicon.json', encoding='utf-8'))
F = lex['fields']
ent = {}
for row in lex['entries']:
    e = dict(zip(F, row))
    ent[e['id'] or e['lemma']] = e
gloss = json.load(open(f'data/{series}/gloss.json', encoding='utf-8'))['letters']
override = {}
op = V + f'src/daily_override_{series}.json'
if os.path.exists(op):
    override = json.load(open(op, encoding='utf-8'))
phr = {}
pp = V + f'src/phrases_{series}.txt'
if os.path.exists(pp):
    for line in open(pp, encoding='utf-8'):
        p_ = line.rstrip('\n').split('|')
        if len(p_) != 7: continue
        lid_, m_, t_, en_, ko_, nk_, ne_ = p_
        phr.setdefault(lid_, []).append({'t': t_, 'm': int(m_), 'ko': ko_, 'en': en_, 'note_ko': nk_, 'note_en': ne_})

def key_of(t):
    return t if isinstance(t, str) else t[1]
def surf_of(t):
    return t if isinstance(t, str) else t[0]

def strip_acc(s):
    return ''.join(c for c in unicodedata.normalize('NFD', s) if unicodedata.category(c) != 'Mn').lower()

def cognate(e):
    lem = strip_acc(e['lemma'])
    en = re.sub(r'\([^)]*\)', '', e['en'])
    for alt in re.split(r'[,;/]', en):
        alt = alt.strip().lower()
        alt = re.sub(r'^(to|the|a|an) ', '', alt)
        if strip_acc(alt) == lem: return True
    return False

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
    return (e['pos'] in CONTENT and not e.get('function') and len(e['lemma']) > 1
            and e.get('level') and not cognate(e) and not e.get('id'))

DEBUG = len(sys.argv) > 3
form2key = {}
for k, e in ent.items():
    for f_ in e['forms']: form2key.setdefault(f_, k)
ART = re.compile(r'^(der|die|das|le|la|les|l\'|un|une|el|los|las|una)\s+', re.I)
chosen = set()
out = {'_doc': ('Per letter id (order P, I…VIII, E): words = 8 lemma keys for the "today\'s words" card, chosen from that letter by chapter band '
                '(P/I–II: A1–A2, III–V: B1, VI–VIII/E: B2–C1; widened by one band only when needed), never repeating a lemma of an earlier letter; '
                'no names, function words, single letters or words spelled exactly like their English gloss. '
                'spots = [{i: plate spot index, w: lemma key, was: old spot word}] — a replacement only for weak spot words (names, pronouns, '
                'verb forms, cognates, letters); the lemma occurs in the quoted message and names something in the picture; [] when none is needed or none fits. '
                'phrases = 1–2 expressions copied verbatim from the letter: {t: text, m: message index, ko, en, note_ko, note_en}.'),
       'lang': lang, 'letters': {}}
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
        t = ART.sub('', (w.get('de_modern') or w.get(lang) or ''))
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
    if lid in override.get('words', {}):
        sel = override['words'][lid]
    sel.sort(key=order.index)
    chosen.update(sel)
    stats['words'] += len(sel)
    if len(sel) < 8: stats['short'] += 1; print('short', lid, len(sel))
    for k in sel: stats['in_band' if ent[k]['level'] in b else 'widened'] += 1

    # ---- spots ----
    spots = []
    for i, sp in enumerate((l.get('plate') or {}).get('spots', [])):
        w = sp.get('word') or {}
        word = ART.sub('', w.get(lang) or '')
        q = sp.get('quote')
        if q is None or q >= len(msgs): continue
        qt = msgs[q]
        surf2key = {surf_of(t): key_of(t) for t in qt}
        cur = surf2key.get(word) or surf2key.get(word.split()[0] if word else '')
        if not cur and word in ent: cur = word
        if not cur and word.split() and word.split()[0] in ent: cur = word.split()[0]
        if not cur:
            allk = {surf_of(t): key_of(t) for m in msgs for t in m}
            cur = allk.get(word) or allk.get(word.split()[0] if word else '') or form2key.get(word) or form2key.get(word.split()[0] if word else '')
        ce = ent.get(cur) if cur else None
        if DEBUG and (ce is None or ce['pos'] not in ('n', 'adj') or ce.get('function') or len(word) <= 1 or cognate(ce)):
            print('SPOT', f'{lid}:{i}', repr(w.get(lang)), cur, '|', sp['title']['en'], '|', (sp.get('note') or {}).get('en', ''), '| Q', q, ':', ' '.join(f"{surf_of(t)}={key_of(t)}" if ent[key_of(t)]['pos']=='n' and not cognate(ent[key_of(t)]) else surf_of(t) for t in qt))
        weak = (ce is None or ce['pos'] not in ('n', 'adj') or ce.get('function') or len(word) <= 1 or cognate(ce))
        if not weak: continue
        title = (sp.get('title') or {}).get('en', '').lower() + ' ' + (sp.get('note') or {}).get('en', '').lower()
        tw = set(x[:5] for x in re.findall(r'[a-z]+', title))
        best = None
        for t in qt:
            k = key_of(t); e = ent[k]
            if e['pos'] != 'n' or e.get('function') or cognate(e) or k == cur: continue
            ks = set(x[:5] for x in re.findall(r'[a-z]+', re.sub(r'\([^)]*\)', '', e['en'])) if len(x) > 2)
            if ks & tw:
                best = k; break
        ov = override.get('spots', {}).get(f'{lid}:{i}')
        best = ov or None   # replacements are reviewed by hand (daily_override_<series>.json)
        if best:
            spots.append({'i': i, 'w': best, 'was': w.get(lang)})
            stats['spots'] += 1
        else:
            stats['weak_kept'] += 1
    rec = {'words': sel, 'spots': spots}
    if lid in phr:
        rec['phrases'] = phr[lid]; stats['phrases'] += len(phr[lid])
    out['letters'][lid] = rec
json.dump(out, open(f'data/{series}/daily.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print(series, dict(stats))
