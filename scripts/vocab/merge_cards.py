#!/usr/bin/env python3
"""After build.py / build_tl.py for a series whose lexicon was first made for its "today's words" cards (napoleon):
  python3 scripts/vocab/merge_cards.py napoleon <dir with the card lexicon*.json, e.g. from `git show HEAD:...`>
Card rows keep their hand-checked meaning, IPA, pronunciation and example (forms / first / letters follow the text);
rows shared with Vincent / Mozart reuse those series' IPA, pronunciation and reviewed example; other rows keep the build
(meaning from src/<lang>_tab.txt, espeak IPA, Tatoeba example). Tatoeba examples listed in src/example_drop_<series>.txt
(lang<TAB>key<TAB>tatoeba id: wrong sense or unsuitable for the app) are removed. Then run scripts/validate_lexicon.py."""
import json, os, sys
series, cards = sys.argv[1], sys.argv[2]
V = os.path.dirname(os.path.abspath(__file__))
key = lambda e: e.get('id') or e['lemma']
drop = {}
p = os.path.join(V, 'src', f'example_drop_{series}.txt')
if os.path.exists(p):
    for line in open(p, encoding='utf-8'):
        a = line.rstrip('\n').split('\t')
        if len(a) == 3: drop[(a[0], a[1])] = int(a[2])
# 원문 언어는 series.json 의 original (napoleon fr · goya es). 원문 줄은 같은 원문 언어의 앞 작품 (빈센트 · 모차르트) 을 빌려 쓴다.
orig = json.load(open(f'data/{series}/series.json', encoding='utf-8'))['original']
SHARED = {'fr': ['vincent'], 'de': ['mozart']}
for lang, sfx in [(orig, ''), ('en', '_en'), ('ko', '_ko')]:
    built = json.load(open(f'data/{series}/lexicon{sfx}.json', encoding='utf-8'))
    card = json.load(open(os.path.join(cards, f'lexicon{sfx}.json'), encoding='utf-8'))
    CF, BF = card['fields'], built['fields']
    cmap = {key(dict(zip(CF, r))): dict(zip(CF, r)) for r in card['entries']}
    refs = {}
    for s in (SHARED.get(lang, []) if lang == orig else ['vincent', 'mozart']):
        d = json.load(open(f'data/{s}/lexicon{sfx}.json', encoding='utf-8'))
        for r in d['entries']:
            e = dict(zip(d['fields'], r)); refs.setdefault(key(e), e)
    out = []; n = {'card': 0, 'shared': 0, 'new': 0}
    for r in built['entries']:
        b = dict(zip(BF, r)); k = key(b)
        if k in cmap:
            e = dict(cmap[k]); e.update(forms=b['forms'], first=b['first'], letters=b['letters']); n['card'] += 1
        elif k in refs:
            e = dict(b); e.pop('example', None)
            for f in ('ipa', 'example', 'pron', 'rom', 'pron_note'):
                if refs[k].get(f) is not None: e[f] = refs[k][f]
            n['shared'] += 1
        else:
            e = dict(b); n['new'] += 1
            if e.get('example') and drop.get((lang, k)) == e['example'][0]: e.pop('example')
        row = [e.get(f) for f in CF]
        while row and row[-1] is None: row.pop()
        out.append(row)
    lost = set(cmap) - {key(dict(zip(CF, x))) for x in out}
    if lost: sys.exit(f'{lang}: card keys not in the text gloss (fix src/{lang}_map_{series}.txt / _occ.txt): {sorted(lost)}')
    doc = dict(card); doc['entries'] = out
    doc['_doc'] = built['_doc'] + (f' {series}: every word of the letters (full-text gloss). Rows of the "today\'s words" cards keep their hand-checked '
        'meaning, IPA, pronunciation and example; rows shared with Vincent / Mozart reuse those series\' IPA, pronunciation and reviewed example; '
        'other rows: meaning written for this letter text (not yet checked by a person), IPA from espeak-ng (approximate), example from Tatoeba.')
    json.dump(doc, open(f'data/{series}/lexicon{sfx}.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    print(lang, n)
