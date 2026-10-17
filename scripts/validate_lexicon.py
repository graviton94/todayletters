#!/usr/bin/env python3
"""Check data/<series>/lexicon[_xx].json, gloss[_xx].json and daily[_xx].json against the letters, for the three
languages a learner can study: the original (vincent fr, mozart de; files without suffix), English (_en) and Korean (_ko).

  python3 scripts/validate_lexicon.py                 # both series, all three languages
  python3 scripts/validate_lexicon.py vincent         # one series
  python3 scripts/validate_lexicon.py vincent ko      # one series, one language (fr|de|en|ko)

Checks: every word of every message is covered by gloss.json (in order, nothing left over);
gloss keys exist in the lexicon; lexicon forms / first / letters agree with the text;
daily words exist, occur in that letter, are content words, never repeat across letters and sit in
the chapter's level band (or one band either side); spot replacements occur in the quoted message;
phrases are verbatim; examples have an author, <= 12 words and contain the lemma or a printed form;
Korean gloss tokens [surface, key, fn] only use tags explained in gloss_ko.json "tags"; each language's three files
stay under 460 KB per series. Exit code 1 on any error.
"""
import json, os, re, sys, unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ORDER = ['prologue'] + ['chapter%d' % i for i in range(1, 9)] + ['epilogue']
SERIES = {'vincent': 'fr', 'mozart': 'de', 'napoleon': 'fr'}
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


LIMIT_KB = 460   # 한국어 사전에 발음 칸(pron · rom · pron_note) 이 붙어 400 → 460


def check(series, lang=None):
    lang = lang or SERIES[series]
    orig = lang == SERIES[series]
    sfx = '' if orig else '_' + lang
    errs, warns = [], []
    d = os.path.join(ROOT, 'data', series)
    letters = []
    for f in ORDER:
        letters += json.load(open(os.path.join(d, f + '.json'), encoding='utf-8'))['letters']
    lex = json.load(open(os.path.join(d, f'lexicon{sfx}.json'), encoding='utf-8'))
    gdoc = json.load(open(os.path.join(d, f'gloss{sfx}.json'), encoding='utf-8'))
    gl = gdoc['letters']
    tags = gdoc.get('tags', {})
    daily = json.load(open(os.path.join(d, f'daily{sfx}.json'), encoding='utf-8'))['letters']
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
                if isinstance(t, str): s, k, fn = t, t, ''
                elif len(t) == 2: s, k = t; fn = ''
                elif len(t) == 3 and lang == 'ko': s, k, fn = t
                else: errs.append(f'{lid}:{mi}: bad token {t!r}'); continue
                ntok += 1
                for part in filter(None, fn.split('+')):
                    if '/' not in part or part.rsplit('/', 1)[1] not in tags:
                        errs.append(f'{lid}:{mi}: function info {part!r} of {s!r} has no entry in "tags"')
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
        if orig and e['pos'] == 'n' and not e.get('gender'): errs.append(f'lexicon {k}: noun without gender')
        if e['pos'] not in ('name', 'abbr') and not e['pos'].startswith('foreign') and e.get('level') not in LEVELS:
            errs.append(f'lexicon {k}: bad level {e.get("level")}')
        ex = e.get('example')
        if ex:
            exd = dict(zip(lex['example_fields'], ex))
            if not (exd.get('author') or '').strip(): errs.append(f'lexicon {k}: example without author')
            if lang != 'en' and not exd.get('en'): errs.append(f'lexicon {k}: example without en')
            if len(exd['text'].split()) > 12: errs.append(f'lexicon {k}: example longer than 12 words')
            words = set(w.lower() for w in re.findall(r"[^\W\d_]+(?:['’][^\W\d_]+)*", exd['text']))
            words |= set(w.split("'")[-1] for w in words if "'" in w)
            cands = set(f.lower() for f in e['forms']) | {e['lemma'].lower()}
            if lang == 'ko':   # Korean: the stem (lemma minus 다) inside any word of the sentence
                stem = e['lemma'][:-1] if e['lemma'].endswith('다') and len(e['lemma']) > 1 else e['lemma']
                cands = {stem[:max(1, len(stem) - 1)]} if len(stem) > 1 else {stem}
                words = {exd['text']}
                if not any(c in exd['text'] for c in cands): words = set()
                else: cands = words
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
            if e.get('function') or e['pos'] not in ('n', 'v', 'adj', 'adv') or (lang != 'ko' and len(e['lemma']) < 2):
                errs.append(f'daily {lid}: {k} is not a content word')
            # 규격 v1: 쉬운 낱말(A1–A2)은 어느 장에나 둘 수 있다 (시리즈 60% 이상은 spec_check.py 가 본다)
            if e.get('level') not in widen(band(lid)) and e.get('level') not in ('A1', 'A2'):
                errs.append(f'daily {lid}: {k} level {e.get("level")} outside {widen(band(lid))}')
        spots = (l.get('plate') or {}).get('spots', [])
        if not orig and r.get('spots'): errs.append(f'daily {lid}: spots must be [] for {lang}')
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

    size = sum(os.path.getsize(os.path.join(d, f + sfx + '.json')) for f in ('lexicon', 'gloss', 'daily'))
    if size > LIMIT_KB * 1024: errs.append(f'{series} {lang}: files total {size/1024:.0f} KB > {LIMIT_KB} KB')
    lv = {}
    for e in ent.values():
        lv[e.get('level') or '-'] = lv.get(e.get('level') or '-', 0) + 1
    print(f'{series} {lang}: {len(ent)} lemmas, {ntok} tokens, {sum(1 for e in ent.values() if e.get("example"))} examples, '
          f'{nwords} daily words ({nshort} short letters), {sum(len(r.get("spots", [])) for r in daily.values())} spot replacements, '
          f'{sum(len(r.get("phrases", [])) for r in daily.values())} phrases, {size/1024:.0f} KB; levels {dict(sorted(lv.items()))}')
    for w in warns: print('  warn:', w)
    for e in errs: print('  ERROR:', e)
    return not errs


if __name__ == '__main__':
    names = [a for a in sys.argv[1:] if a in SERIES] or list(SERIES)
    langs = [a for a in sys.argv[1:] if a not in SERIES]
    ok = all([check(s, l if l != SERIES[s] else None) for s in names for l in (langs or [SERIES[s], 'en', 'ko'])])
    sys.exit(0 if ok else 1)
