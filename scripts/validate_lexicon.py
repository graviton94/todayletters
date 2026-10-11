#!/usr/bin/env python3
"""Check data/<series>/lexicon.json, gloss.json and daily.json against the letters.

  python3 scripts/validate_lexicon.py            # both series
  python3 scripts/validate_lexicon.py vincent

Checks: every word of every message is covered by gloss.json (in order, nothing left over);
gloss keys exist in the lexicon; lexicon forms / first / letters agree with the text;
daily words exist, occur in that letter, are content words, never repeat across letters and sit in
the chapter's level band (or one band either side); spot replacements occur in the quoted message;
phrases are verbatim; examples have an author, <= 12 words and contain the lemma or a printed form.
Exit code 1 on any error.
"""
import json, os, re, sys, unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ORDER = ['prologue'] + ['chapter%d' % i for i in range(1, 9)] + ['epilogue']
SERIES = {'vincent': 'fr', 'mozart': 'de'}
LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']
LETTER = re.compile(r'[^\W\d_]')


def band(lid):
    if lid.startswith('p'): return ['A1', 'A2']
    if lid.startswith('e'): return ['B2', 'C1']
    ch = int(lid[1:].split('-')[0])
    return ['A1', 'A2'] if ch <= 2 else ['B1'] if ch <= 5 else ['B2', 'C1']


def widen(b):
    lo = max(0, LEVELS.index(b[0]) - 1); hi = min(5, LEVELS.index(b[-1]) + 1)
    return LEVELS[lo:hi + 1]


def check(series):
    lang = SERIES[series]
    errs, warns = [], []
    d = os.path.join(ROOT, 'data', series)
    letters = []
    for f in ORDER:
        letters += json.load(open(os.path.join(d, f + '.json'), encoding='utf-8'))['letters']
    lex = json.load(open(os.path.join(d, 'lexicon.json'), encoding='utf-8'))
    gl = json.load(open(os.path.join(d, 'gloss.json'), encoding='utf-8'))['letters']
    daily = json.load(open(os.path.join(d, 'daily.json'), encoding='utf-8'))['letters']
    F = lex['fields']
    ent = {}
    for row in lex['entries']:
        e = dict(zip(F, row))
        k = e.get('id') or e['lemma']
        if k in ent: errs.append(f'duplicate lexicon key {k}')
        ent[k] = e

    # ---- gloss covers every token, in order ----
    seen_forms = {}; seen_letters = {}
    ntok = 0
    for l in letters:
        lid = l['id']
        if lid not in gl: errs.append(f'{lid}: missing in gloss.json'); continue
        if len(gl[lid]) != len(l['messages']):
            errs.append(f'{lid}: gloss has {len(gl[lid])} messages, letter has {len(l["messages"])}'); continue
        for mi, (m, toks) in enumerate(zip(l['messages'], gl[lid])):
            text = m[lang]; pos = 0; rest = []
            for t in toks:
                s, k = (t, t) if isinstance(t, str) else t
                ntok += 1
                if k not in ent: errs.append(f'{lid}:{mi}: key {k!r} not in lexicon'); continue
                j = text.find(s, pos)
                if j < 0: errs.append(f'{lid}:{mi}: token {s!r} not found in order'); continue
                rest.append(text[pos:j]); pos = j + len(s)
                seen_forms.setdefault(k, set()).add(s)
                seen_letters.setdefault(k, [])
                if lid not in seen_letters[k]: seen_letters[k].append(lid)
            rest.append(text[pos:])
            left = ''.join(rest)
            # letters glued to digits (1er, 3me, 12te) are ordinals, not words
            left = re.sub(r'\d+[^\W\d_]+', '', left)
            if any(ch.isalpha() for ch in left):
                errs.append(f'{lid}:{mi}: words not covered by gloss: {" ".join(w for w in re.findall(r"[^\W\d_]+", left) if w.isalpha())!r}')

    # ---- lexicon vs text ----
    for k, e in ent.items():
        if k not in seen_forms: errs.append(f'lexicon {k}: never used in gloss'); continue
        if set(e['forms']) != seen_forms[k]:
            errs.append(f'lexicon {k}: forms {e["forms"]} != text {sorted(seen_forms[k])}')
        lets = e['letters'].split()
        if lets != seen_letters[k]: errs.append(f'lexicon {k}: letters mismatch')
        if e['first'] != seen_letters[k][0]: errs.append(f'lexicon {k}: first mismatch')
        for need in ('pos', 'ko', 'en'):
            if not e.get(need): errs.append(f'lexicon {k}: missing {need}')
        if e['pos'] == 'n' and not e.get('gender'): errs.append(f'lexicon {k}: noun without gender')
        if e['pos'] not in ('name', 'abbr') and not e['pos'].startswith('foreign') and e.get('level') not in LEVELS:
            errs.append(f'lexicon {k}: bad level {e.get("level")}')
        ex = e.get('example')
        if ex:
            exd = dict(zip(lex['example_fields'], ex))
            if not (exd.get('author') or '').strip(): errs.append(f'lexicon {k}: example without author')
            if not exd.get('en'): errs.append(f'lexicon {k}: example without en')
            if len(exd['text'].split()) > 12: errs.append(f'lexicon {k}: example longer than 12 words')
            words = set(w.lower() for w in re.findall(r"[^\W\d_]+(?:['’][^\W\d_]+)*", exd['text']))
            words |= set(w.split("'")[-1] for w in words if "'" in w)
            cands = set(f.lower() for f in e['forms']) | {e['lemma'].lower()}
            if not (words & cands) and not any(c in exd['text'].lower() for c in cands if len(c) > 3):
                warns.append(f'lexicon {k}: example does not show the lemma or a printed form (modern spelling?): {exd["text"]}')

    # ---- daily ----
    used = {}
    nwords = nshort = 0
    for l in letters:
        lid = l['id']
        r = daily.get(lid)
        if r is None: errs.append(f'daily: {lid} missing'); continue
        keys_here = {(t if isinstance(t, str) else t[1]) for m in gl[lid] for t in m}
        ws = r.get('words', [])
        nwords += len(ws)
        if len(ws) < 8: nshort += 1; warns.append(f'daily {lid}: only {len(ws)} words (short letter / band exhausted)')
        if len(ws) > 8: errs.append(f'daily {lid}: more than 8 words')
        for k in ws:
            e = ent.get(k)
            if not e: errs.append(f'daily {lid}: {k} not in lexicon'); continue
            if k not in keys_here: errs.append(f'daily {lid}: {k} does not occur in this letter')
            if k in used: errs.append(f'daily {lid}: {k} already used in {used[k]}')
            used[k] = lid
            if e.get('function') or e['pos'] not in ('n', 'v', 'adj', 'adv') or len(e['lemma']) < 2:
                errs.append(f'daily {lid}: {k} is not a content word')
            if e.get('level') not in widen(band(lid)):
                errs.append(f'daily {lid}: {k} level {e.get("level")} outside {widen(band(lid))}')
        spots = (l.get('plate') or {}).get('spots', [])
        for s in r.get('spots', []):
            i, k = s['i'], s['w']
            if i >= len(spots): errs.append(f'daily {lid}: spot {i} out of range'); continue
            q = spots[i].get('quote')
            qkeys = {(t if isinstance(t, str) else t[1]) for t in gl[lid][q]} if q is not None and q < len(gl[lid]) else set()
            if k not in ent: errs.append(f'daily {lid}: spot word {k} not in lexicon')
            elif k not in qkeys: errs.append(f'daily {lid}: spot word {k} not in quoted message {q}')
        ph = r.get('phrases', [])
        if not 1 <= len(ph) <= 2: errs.append(f'daily {lid}: {len(ph)} phrases')
        for p in ph:
            m = p.get('m')
            if m is None or m >= len(l['messages']) or p['t'] not in l['messages'][m][lang]:
                errs.append(f'daily {lid}: phrase not verbatim: {p["t"]!r}')
            for need in ('ko', 'en', 'note_ko', 'note_en'):
                if not p.get(need): errs.append(f'daily {lid}: phrase {p["t"]!r} missing {need}')

    size = sum(os.path.getsize(os.path.join(d, f)) for f in ('lexicon.json', 'gloss.json', 'daily.json'))
    lv = {}
    for e in ent.values():
        lv[e.get('level') or '-'] = lv.get(e.get('level') or '-', 0) + 1
    print(f'{series}: {len(ent)} lemmas, {ntok} tokens, {sum(1 for e in ent.values() if e.get("example"))} examples, '
          f'{nwords} daily words ({nshort} short letters), {sum(len(r.get("spots", [])) for r in daily.values())} spot replacements, '
          f'{sum(len(r.get("phrases", [])) for r in daily.values())} phrases, {size/1024:.0f} KB; levels {dict(sorted(lv.items()))}')
    for w in warns: print('  warn:', w)
    for e in errs: print('  ERROR:', e)
    return not errs


if __name__ == '__main__':
    names = sys.argv[1:] or list(SERIES)
    ok = all([check(s) for s in names])
    sys.exit(0 if ok else 1)
