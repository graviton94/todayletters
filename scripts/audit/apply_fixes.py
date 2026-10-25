#!/usr/bin/env python3
"""Apply scripts/audit/fixes.txt to data/<series>/gloss*.json · lexicon*.json (+ scripts/vocab/src/<lang>_occ.txt · _tab.txt).

    python3 scripts/audit/apply_fixes.py <audit dir>     # dir with the lemma_audit.py TSV + refs (lemma-audit branch)

Idempotent: a fix whose tokens already carry the new key does nothing. Afterwards run
python3 scripts/validate_lexicon.py and python3 scripts/notes.py.
"""
import collections, csv, json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'scripts', 'vocab', 'src')
SERIES = {'vincent': 'fr', 'mozart': 'de', 'napoleon': 'fr', 'goya': 'es'}
CUT = [('A1', 500), ('A2', 1500), ('B1', 3500), ('B2', 7000), ('C1', 15000)]
ESPEAK = {'fr': 'fr', 'de': 'de', 'es': 'es', 'en': 'en-us'}


def low(s):
    return s.lower().replace('’', "'")


def sfx(series, lang):
    return '' if lang == SERIES[series] else '_' + lang


FREQ = {}


def level(key, forms, lang):
    if '#' in key:          # homograph entry: the shared spelling's rank says nothing (same as build.py)
        return 'C2'
    if lang not in FREQ:
        FREQ[lang] = {l.split('\t')[0]: int(l.split('\t')[1]) for l in open(os.path.join(ROOT, 'data', 'lexicon', f'{lang}_freq.tsv'), encoding='utf-8')}
    rs = [FREQ[lang].get(low(c)) for c in [key] + forms if len(c) >= 2]
    rs = [r for r in rs if r is not None]
    r = min(rs) if rs else None
    for name, cut in CUT:
        if r is not None and r <= cut:
            return name
    return 'C2'


def espeak(word, lang):
    try:
        o = subprocess.run(['espeak-ng', '-q', '--ipa', '-v', ESPEAK[lang], word], capture_output=True).stdout.decode('utf-8', 'replace').strip()
    except FileNotFoundError:
        return None
    o = ' '.join(re.sub(r'\([a-z]+\)', '', o).replace('_', '').split())
    if lang == 'fr':
        o = o.replace('ˈ', '').replace('ˌ', '').replace('ː', '').replace('-', '')
    elif lang == 'es':
        o = o.replace('ː', '').replace('-', '')
    return o or None


class Lex:
    def __init__(self, series, lang):
        self.series, self.lang = series, lang
        d = os.path.join(ROOT, 'data', series)
        self.lp, self.gp = os.path.join(d, f'lexicon{sfx(series, lang)}.json'), os.path.join(d, f'gloss{sfx(series, lang)}.json')
        self.lex = json.load(open(self.lp, encoding='utf-8'))
        self.gl = json.load(open(self.gp, encoding='utf-8'))
        self.F = self.lex['fields']
        self.ent = collections.OrderedDict()
        for row in self.lex['entries']:
            e = dict(zip(self.F, row))
            self.ent[e.get('id') or e['lemma']] = e
        self.touched = set()

    def tokens(self):
        for lid, msgs in self.gl['letters'].items():
            for mi, toks in enumerate(msgs):
                for ti, t in enumerate(toks):
                    yield lid, mi, ti, t

    def save(self):
        # forms · first · letters of touched keys again from the gloss (validate_lexicon.py checks them)
        forms, lets = collections.defaultdict(list), collections.defaultdict(list)
        for lid, mi, ti, t in self.tokens():
            s, k = (t, t) if isinstance(t, str) else (t[0], t[1])
            if k in self.touched:
                if s not in forms[k]: forms[k].append(s)
                if lid not in lets[k]: lets[k].append(lid)
        gone = []
        for k in self.touched:
            if k not in forms:
                gone.append(k); self.ent.pop(k, None); continue
            e = self.ent[k]
            old = [f for f in e['forms'] if f in forms[k]]
            e['forms'] = old + [f for f in forms[k] if f not in old]
            e['letters'] = ' '.join(lets[k]); e['first'] = lets[k][0]
        rows = []
        for e in self.ent.values():
            row = [e.get(f) for f in self.F]
            while row and row[-1] is None: row.pop()
            rows.append(row)
        self.lex['entries'] = rows
        for p, obj in ((self.lp, self.lex), (self.gp, self.gl)):
            json.dump(obj, open(p, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
        return gone


def other_entry(lang, key, series):
    for s, ol in SERIES.items():
        if s == series: continue
        if lang != ol and lang not in ('en', 'ko'): continue
        p = os.path.join(ROOT, 'data', s, f'lexicon{sfx(s, lang)}.json')
        if not os.path.exists(p): continue
        L = json.load(open(p, encoding='utf-8'))
        for row in L['entries']:
            e = dict(zip(L['fields'], row))
            if (e.get('id') or e['lemma']) == key:
                return e
    return None


def audit_refs(adir, series, lang, form, old, which):
    tsv = os.path.join(adir, f'{series}_{lang}.tsv')
    refs = json.load(open(os.path.join(adir, f'{series}_{lang}.refs.json'), encoding='utf-8'))
    out = []
    with open(tsv, encoding='utf-8') as f:
        for i, r in enumerate(csv.DictReader(f, delimiter='\t', quoting=csv.QUOTE_NONE), 1):
            if r['form'] == form and r['our_key'] == old and r['sp_pos'].startswith(which):
                out += [x.split('/', 1)[1] for x in refs[str(i)]]
    return out


def append(path, lines):
    if not lines: return
    text = open(path, encoding='utf-8').read()
    with open(path, 'a', encoding='utf-8') as f:
        f.write(('' if not text or text.endswith('\n') else '\n') + '\n'.join(lines) + '\n')


def main():
    adir = sys.argv[1]
    lexes = {}
    occ_add = collections.defaultdict(list)
    tab_add = collections.defaultdict(list)
    log = []
    for line in open(os.path.join(ROOT, 'scripts', 'audit', 'fixes.txt'), encoding='utf-8'):
        line = line.rstrip('\n')
        if not line.strip() or line.startswith('#'):
            continue
        op, rest = line[0], line[2:]
        p = rest.split('|')
        series, lang = p[0].split()
        L = lexes.setdefault((series, lang), Lex(series, lang))
        if op == 'E':
            k, pos, en, ko = p[1:5]
            e = L.ent[k]; e['pos'], e['en'], e['ko'] = pos, en, ko
            log.append(f'{series} {lang}: entry {k} → {pos} · {en} · {ko}')
            continue
        form, old, which, new = p[1:5]
        if which == '*':
            refs = [f'{lid}:{mi}:{ti}' for lid, mi, ti, t in L.tokens()
                    if not isinstance(t, str) and low(t[0]) == form and t[1] == old or isinstance(t, str) and low(t) == form and t == old]
        else:
            refs = audit_refs(adir, series, lang, form, old, which)
        if not refs:
            continue
        if new not in L.ent:
            o = other_entry(lang, new, series)
            if o:
                e = {f: o.get(f) for f in L.F}
                e['example'] = None   # examples are checked per series (validate_lexicon.py); none rather than a wrong one
            else:
                pos, gender, en, ko = (p[5:9] + [''] * 4)[:4]
                e = {f: None for f in L.F}
                e.update(lemma=new.split('#')[0], id=new if '#' in new else None, pos=pos, gender=gender or None, en=en, ko=ko)
                if lang == 'ko': e['ipa'] = None
                else: e['ipa'] = espeak(e['lemma'], lang)
                tab_add[lang].append('|'.join([new, pos + ('.' + gender if gender else ''), en, ko]))
            e['forms'] = []
            L.ent[new] = e
            log.append(f'{series} {lang}: new entry {new} ({e["pos"]}) {e["en"]} · {e["ko"]}' + (' [copied]' if o else ''))
        n = 0
        for ref in refs:
            lid, mi, ti = ref.rsplit(':', 2)
            mi, ti = int(mi), int(ti)
            t = L.gl['letters'][lid][mi][ti]
            s, k = (t, t) if isinstance(t, str) else (t[0], t[1])
            if k == new: continue
            if k != old: sys.exit(f'{series} {lang} {ref}: expected {old}, found {k}')
            L.gl['letters'][lid][mi][ti] = s if s == new else [s, new]
            occ_add[lang].append(f'{series}/{ref}={new}')
            L.touched |= {old, new}; n += 1
        if n:
            log.append(f'{series} {lang}: {form} {old} → {new} ×{n}')
    for (series, lang), L in lexes.items():
        gone = L.save()
        # level for the new entries (forms are known only now)
        changed = False
        for k in L.touched:
            e = L.ent.get(k)
            if e and not e.get('level'):
                e['level'] = level(k, e['forms'], lang); changed = True
        if changed:
            L.touched = set(); L.save()
        for k in gone:
            log.append(f'{series} {lang}: entry {k} no longer used — removed')
    for lang, lines in occ_add.items():
        append(os.path.join(SRC, f'{lang}_occ.txt'), lines)
    for lang, lines in tab_add.items():
        path = os.path.join(SRC, f'{lang}_tab.txt')
        have = {l.split('|')[0] for l in open(path, encoding='utf-8')}
        new = [l for l in dict.fromkeys(lines) if l.split('|')[0] not in have]
        append(path, new)
    log += fix_daily()
    print('\n'.join(log))


def fix_daily():
    """A daily word whose tokens moved to another key in that letter: take the new key when it is a content word not
    used as a daily word elsewhere in the series, else drop it (the letter then has fewer than 8; validate only warns)."""
    moves = collections.defaultdict(list)
    for line in open(os.path.join(ROOT, 'scripts', 'audit', 'fixes.txt'), encoding='utf-8'):
        if line.startswith('R '):
            p = line[2:].rstrip('\n').split('|')
            moves[tuple(p[0].split())].append((p[2], p[4]))
    log = []
    for (series, lang), mv in moves.items():
        d = os.path.join(ROOT, 'data', series)
        L = Lex(series, lang)
        dp = os.path.join(d, f'daily{sfx(series, lang)}.json')
        daily = json.load(open(dp, encoding='utf-8'))
        used = {k for r in daily['letters'].values() for k in r.get('words', [])}
        changed = False
        for lid, r in daily['letters'].items():
            here = {(t if isinstance(t, str) else t[1]) for m in L.gl['letters'].get(lid, []) for t in m}
            ws = r.get('words', [])
            for i, k in enumerate(list(ws)):
                if k in here and k in L.ent:
                    continue
                cand = [new for old, new in mv if old == k and new in here]
                e = L.ent.get(cand[0]) if cand else None
                if e and cand[0] not in used and not e.get('function') and e['pos'] in ('n', 'v', 'adj', 'adv') and len(e['lemma']) >= 2:
                    ws[ws.index(k)] = cand[0]; used.add(cand[0])
                    log.append(f'{series} {lang} daily {lid}: {k} → {cand[0]} (needs a new v_{lang}_<slug> sound)')
                else:
                    ws.remove(k)
                    log.append(f'{series} {lang} daily {lid}: {k} dropped')
                changed = True
        if changed:
            json.dump(daily, open(dp, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    return log


if __name__ == '__main__':
    main()
