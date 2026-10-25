#!/usr/bin/env python3
"""Lemma / POS audit of the lexicons with spaCy (lemma-audit.yml; needs spaCy + the *_core_*_md models + simplemma).

    python3 scripts/audit/lemma_audit.py [out_dir] [series ...]

For every message of every letter (data/<series>/prologue · chapter* · epilogue) and every learning-language layer
that has a gloss file (original fr/de/es, plus en; ko has no spaCy model here), each gloss token (gloss*.json order)
is matched by character offset to spaCy's token in context. A row is written when spaCy disagrees with our lexicon
key (lemma) or, for open classes, with our pos.

Output (out_dir, default scripts/audit/out):
  <series>_<lang>.tsv   kind, form, our_key, our_pos, sp_lemma, sp_pos, simplemma, n, of, sp_key, ref, context
      kind   lemma  = spaCy lemma ≠ our lemma (both lowercased)
             pos    = same lemma, open-class pos differs (n / v / adj, or n|v vs a closed class)
             ptcp   = our adj / n, spaCy VERB (participle used as adjective / noun: usually fine)
      n      occurrences of this (form, our_key, sp_lemma, sp_pos); of = all occurrences of form → our_key
      sp_key lexicon key whose lemma equals spaCy's lemma (or '-'), simplemma = simplemma's lemma of the form
      ref    letter:message:token (one example), context = a few words around it
  <series>_<lang>.refs.json   row number (1-based, header = 0) → every ref "series/letter:msg:idx" (same as *_occ.txt keys)
  summary.txt           row counts per series / lang / kind
"""
import collections, json, os, sys

import simplemma
import spacy

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ORDER = ['prologue'] + ['chapter%d' % i for i in range(1, 9)] + ['epilogue']
SERIES = {'vincent': 'fr', 'mozart': 'de', 'napoleon': 'fr', 'goya': 'es'}
MODELS = {'fr': 'fr_core_news_md', 'de': 'de_core_news_md', 'es': 'es_core_news_md', 'en': 'en_core_web_md'}
UPOS = {'NOUN': 'n', 'VERB': 'v', 'AUX': 'v', 'ADJ': 'adj', 'ADV': 'adv', 'ADP': 'prep', 'CCONJ': 'conj', 'SCONJ': 'conj',
        'PRON': 'pron', 'DET': 'det', 'PART': 'part', 'INTJ': 'interj'}
SKIP_OURS = ('name', 'abbr', 'num', 'interj')
SKIP_SP = ('PROPN', 'PUNCT', 'NUM', 'X', 'SYM', 'SPACE', 'INTJ')
OPEN = {'n', 'v', 'adj'}


def low(s):
    return s.lower().replace('’', "'")


def letters(series):
    for f in ORDER:
        p = os.path.join(ROOT, 'data', series, f + '.json')
        if os.path.exists(p):
            yield from json.load(open(p, encoding='utf-8'))['letters']


def audit(series, lang, nlp, out):
    sfx = '' if lang == SERIES[series] else '_' + lang
    d = os.path.join(ROOT, 'data', series)
    lex = json.load(open(os.path.join(d, f'lexicon{sfx}.json'), encoding='utf-8'))
    gl = json.load(open(os.path.join(d, f'gloss{sfx}.json'), encoding='utf-8'))['letters']
    F = lex['fields']
    ent = {}
    by_lemma = collections.defaultdict(list)
    for row in lex['entries']:
        e = dict(zip(F, row))
        k = e.get('id') or e['lemma']
        ent[k] = e
        by_lemma[low(e['lemma'])].append(k)
    rows = collections.OrderedDict()
    pair_n = collections.Counter()
    for l in letters(series):
        lid = l['id']
        for mi, (m, toks) in enumerate(zip(l['messages'], gl.get(lid, []))):
            text = m[lang]
            doc = nlp(text)
            starts = {t.idx: t for t in doc}
            pos = 0
            words = text.split()
            for ti, t in enumerate(toks):
                s, k = (t, t) if isinstance(t, str) else (t[0], t[1])
                j = text.find(s, pos)
                if j < 0:
                    continue
                pos = j + len(s)
                pair_n[(low(s), k)] += 1
                e = ent.get(k)
                if not e or e['pos'] in SKIP_OURS or e['pos'].startswith('foreign') or k.startswith("'"):
                    continue
                st = starts.get(j)
                # spaCy token must cover exactly this gloss token (hyphen / elision splits that differ are skipped)
                if st is None or len(st.text) != len(s) or st.pos_ in SKIP_SP:
                    continue
                ours, sp_lem = low(e['lemma']), low(st.lemma_)
                our_pos, sp_pos = e['pos'], UPOS.get(st.pos_, st.pos_.lower())
                if our_pos == 'art':
                    our_pos = 'det'
                if sp_lem != ours:
                    kind = 'ptcp' if our_pos in ('adj', 'n') and st.pos_ == 'VERB' else 'lemma'
                elif our_pos != sp_pos and (our_pos in OPEN or sp_pos in OPEN) and \
                        ({our_pos, sp_pos} <= OPEN or {our_pos, sp_pos} & {'n', 'v'}) and \
                        not ({our_pos, sp_pos} == {'adj', 'v'} and st.pos_ == 'AUX'):
                    kind = 'ptcp' if our_pos in ('adj', 'n') and st.pos_ == 'VERB' else 'pos'
                else:
                    continue
                key = (kind, low(s), k, sp_lem, st.pos_ + ('/' + st.tag_ if lang == 'de' else ''))
                r = rows.get(key)
                if r is None:
                    # context: a few words around the token
                    w0 = len(text[:j].split())
                    ctx = ' '.join(words[max(0, w0 - 4): w0 + 5])
                    r = rows[key] = {'refs': [], 'ctx': ctx, 'ref': f'{lid}:{mi}:{ti}'}
                r['refs'].append(f'{series}/{lid}:{mi}:{ti}')
    order = sorted(rows.items(), key=lambda kv: ({'lemma': 0, 'pos': 1, 'ptcp': 2}[kv[0][0]], -len(kv[1]['refs']), kv[0][1]))
    path = os.path.join(out, f'{series}_{lang}.tsv')
    refs = {}
    with open(path, 'w', encoding='utf-8') as f:
        f.write('kind\tform\tour_key\tour_pos\tsp_lemma\tsp_pos\tsimplemma\tn\tof\tsp_key\tref\tcontext\n')
        for i, ((kind, form, k, sp_lem, sp_pos), r) in enumerate(order, 1):
            try:
                sm = simplemma.lemmatize(form, lang=lang)
            except Exception:
                sm = '-'
            spk = ','.join(by_lemma.get(sp_lem, [])) or '-'
            f.write('\t'.join(map(str, [kind, form, k, ent[k]['pos'], sp_lem, sp_pos, low(sm), len(r['refs']), pair_n[(form, k)],
                                        spk, r['ref'], r['ctx'].replace('\t', ' ')])) + '\n')
            refs[i] = r['refs']
    json.dump(refs, open(os.path.join(out, f'{series}_{lang}.refs.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    return collections.Counter(k[0] for k in rows)


def main():
    args = sys.argv[1:]
    out = args.pop(0) if args and args[0] not in SERIES else os.path.join(ROOT, 'scripts', 'audit', 'out')
    names = args or [s for s in SERIES if os.path.exists(os.path.join(ROOT, 'data', s, 'lexicon.json'))]
    os.makedirs(out, exist_ok=True)
    nlps = {}
    lines = []
    for s in names:
        for lang in (SERIES[s], 'en'):
            if lang not in nlps:
                nlps[lang] = spacy.load(MODELS[lang], disable=['ner'])
            c = audit(s, lang, nlps[lang], out)
            lines.append(f'{s} {lang}: ' + ' · '.join(f'{k} {c[k]}' for k in ('lemma', 'pos', 'ptcp')) + f' (rows {sum(c.values())})')
            print(lines[-1], flush=True)
    open(os.path.join(out, 'summary.txt'), 'w', encoding='utf-8').write('\n'.join(lines) + '\n')


if __name__ == '__main__':
    main()
