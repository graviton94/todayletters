"""Korean eojeol -> dictionary lemma + function morphemes, with kiwipiepy (LGPL-3.0 tool; only its output is kept).
lemma: leading content morphemes joined; a verb/adjective stem gets 다 (공부+하 -> 공부하다, 즐겁 -> 즐겁다);
nouns lose their particles (편지를 -> 편지). fn: the rest as 'form/TAG' joined with '+' (Sejong tags as Kiwi prints them)."""
import re, unicodedata
from kiwipiepy import Kiwi
_K = None
def kiwi():
    global _K
    if _K is None: _K = Kiwi()
    return _K
NOUNISH = {'NNG', 'NNP', 'NNB', 'NR', 'NP', 'XR', 'SL', 'SH', 'MM', 'MAG', 'MAJ', 'IC', 'XPN', 'SN'}
VERBISH = {'VV', 'VA', 'VX', 'VCN', 'XSV', 'XSA', 'XSM'}
FN_XSN = {'들'}
JAMO = {'ᆫ': 'ㄴ', 'ᆯ': 'ㄹ', 'ᆷ': 'ㅁ', 'ᆸ': 'ㅂ', 'ᆼ': 'ㅇ', 'ᆻ': 'ㅆ', 'ᆺ': 'ㅅ'}
def tag0(t):
    return t.split('-')[0]
def cj(s):
    return ''.join(JAMO.get(c, c) for c in s)
def split_morphs(ms):
    """ms: list of (form, tag, lemma). Returns (lemma, head_tag, fn_string)."""
    parts = []; verb = None; head = None; i = 0
    while i < len(ms):
        f, t, lem = ms[i]; t0 = tag0(t)
        if verb is not None: break
        if t0 in NOUNISH:
            if parts and t0 in ('MAG', 'MAJ', 'IC', 'MM') : break
            parts.append(f); head = head or t0
        elif t0 == 'XSN' and parts and f not in FN_XSN:
            parts.append(f)
        elif t0 in VERBISH and (parts or t0 in ('VV', 'VA', 'VX', 'VCN')):
            verb = lem if t0 in ('VV', 'VA', 'VX', 'VCN') else (f + '다')
            if t0 in ('XSV', 'XSA'): head = 'VV' if t0 == 'XSV' else 'VA'
            elif not parts or head in ('XPN', None): head = t0
            elif head in ('XR', 'NNG', 'MAG'): head = 'VA' if t0 == 'VA' else 'VV'
        else:
            break
        i += 1
    if verb is not None:
        lemma = ''.join(parts) + (verb if verb.endswith('다') else verb + '다')
    elif parts:
        lemma = ''.join(parts)
    else:
        f, t, lem = ms[0]
        lemma = cj(lem if tag0(t) in VERBISH or t == 'VCP' else f); head = tag0(t)
        if t == 'VCP': lemma = '이다'
        i = 1
    fn = '+'.join(cj(f) + '/' + tag0(t) for f, t, _ in ms[i:])
    return lemma, head, fn

def analyze(text, spans):
    """text: message; spans: [(start, end)] of word tokens. Returns [(lemma, head_tag, fn)] per span."""
    toks = kiwi().tokenize(text)
    per = [[] for _ in spans]
    j = 0
    for tk in toks:
        for si, (a, b) in enumerate(spans):
            if a <= tk.start < b:
                per[si].append((tk.form, tk.tag, tk.lemma)); break
    out = []
    for si, ms in enumerate(per):
        a, b = spans[si]
        if any(tk.start + tk.len > b for tk in toks if a <= tk.start < b):
            # a morpheme runs past the eojeol (multi-word proper noun): analyse the eojeol alone
            ms = [(t.form, t.tag, t.lemma) for t in kiwi().tokenize(text[a:b])]
        if not ms:
            a, b = spans[si]; out.append((text[a:b], 'SL', '')); continue
        out.append(split_morphs(ms))
    return out
