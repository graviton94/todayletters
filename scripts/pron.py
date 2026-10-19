#!/usr/bin/env python3
"""비슷하게 읽으면 (말하기 두 번째로 안 될 때 보여 주는 소리 힌트, 자동 · 어림): 사람이 쓰지 않고 만든다.

    python3 scripts/pron.py                 # 모든 작품 (data/*/series.json)
    python3 scripts/pron.py vincent         # 한 작품
    python3 scripts/pron.py --say fr "Oui, ça va. Et toi ?"   # 한 줄만 찍어 보기 (ko 는 로마자)

- 한국어 앱 글자 + 프랑스어 · 독일어 · 영어를 배우는 사람: 한글 어림 표기.
  espeak-ng (`espeak-ng -q --ipa -v fr|de|en-us`, CI 에서는 `sudo apt-get install -y espeak-ng`) 로 IPA → 언어마다 표 ([HANGUL]) 로 한글.
  국립국어원 외래어 표기법의 뜻을 따르되, 한국 사람이 읽었을 때 가장 가깝게: 프랑스어 p · t · k · s 는 된소리 (ㅃ ㄸ ㄲ ㅆ), 비모음은 앙 · 옹 · 앵,
  독일어 ç → 히 · x → 흐 · ʏ/y → 위 · ø → 외, 영어 r · l → ㄹ (모음 앞이 아닌 r 은 읽지 않음) · θ → ㅆ · ð → ㄷ · f → ㅍ · v → ㅂ · z → ㅈ.
  띄어쓰기 · 문장부호는 원문 그대로 (낱말마다 맞춰 놓고, 프랑스어 연음 자음은 다음 낱말 앞으로).
- 영어 앱 글자 + 한국어를 배우는 사람: 소리 나는 대로 바꾼 뒤 (연음 · 비음화 · 유음화 · 경음화 · 격음화 · 구개음화, ㅎ 탈락) 국어의 로마자 표기법.
  (ㄴ 첨가는 낱말 경계를 몰라서 하지 않는다.)
- 결과: data/<작품>/pron.json (원어를 배우는 사람) · pron_en.json · pron_ko.json
  {"lang": "fr", "auto": true, "items": {"<글 그대로>": "<힌트>"}} — 말해 볼 수 있는 모든 글 (편지 메시지 · 회화 내 줄 · 내 이야기로 세 문장 · 답장 (ko 는 koLow · koHigh 도) ·
  기초 낱말 · 예문 · 카드 낱말 · 표현 · 닻) 과 그 안의 낱말 하나하나 (앱은 글이 없으면 낱말 힌트를 이어 붙인다: 따라 읽기 마디).
- scripts/generate.py 가 이 파일들을 앱 assets 로 넣는다. pron 파일이 없는 작품은 espeak-ng 가 있으면 generate.py 가 이것을 불러 만든다 (`generate.py --pron` 이면 모두 다시).
"""
import glob, json, os, re, shutil, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VOICE = {"fr": "fr", "de": "de", "en": "en-us", "nl": "nl"}
PUNCT_ONLY = re.compile(r"^[^\w]+$", re.UNICODE)

# ── IPA → 한글 ─────────────────────────────────────────
CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
JONG = ["", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"]


def compose(cho, jung, jong=""):
    return chr(0xAC00 + (CHO.index(cho) * 21 + JUNG.index(jung)) * 28 + JONG.index(jong))


YOT = {"ㅏ": "ㅑ", "ㅐ": "ㅒ", "ㅓ": "ㅕ", "ㅔ": "ㅖ", "ㅗ": "ㅛ", "ㅜ": "ㅠ", "ㅡ": "ㅠ", "ㅣ": "ㅣ", "ㅚ": "ㅚ", "ㅟ": "ㅟ", "ㅘ": "ㅘ", "ㅝ": "ㅝ", "ㅞ": "ㅞ"}
WAU = {"ㅏ": "ㅘ", "ㅐ": "ㅙ", "ㅔ": "ㅞ", "ㅣ": "ㅟ", "ㅓ": "ㅝ", "ㅗ": "ㅝ", "ㅡ": "ㅝ", "ㅜ": "ㅜ", "ㅚ": "ㅞ", "ㅟ": "ㅟ"}

# 모음 (이어진 홀소리는 따로 한 글자씩: aɪ → 아이)
VOWELS = {"a": "ㅏ", "ɑ": "ㅏ", "æ": "ㅐ", "e": "ㅔ", "ɛ": "ㅔ", "i": "ㅣ", "ɪ": "ㅣ", "o": "ㅗ", "ɔ": "ㅗ", "u": "ㅜ", "ʊ": "ㅜ",
          "oʊ": "ㅗ", "əʊ": "ㅗ", "y": "ㅟ", "ʏ": "ㅟ", "ø": "ㅚ", "œ": "ㅚ", "ʌ": "ㅓ", "ɜ": "ㅓ", "ɚ": "ㅓ", "ɝ": "ㅓ", "ɐ": "ㅓ", "ᵻ": "ㅣ", "ɒ": "ㅗ", "ɘ": "ㅓ"}
SCHWA = {"fr": "ㅡ", "de": "ㅔ", "en": "ㅓ", "nl": "ㅓ"}
NASAL = {"ɑ̃": "ㅏ", "ɔ̃": "ㅗ", "ɛ̃": "ㅐ", "œ̃": "ㅐ", "ã": "ㅏ", "õ": "ㅗ", "ẽ": "ㅐ"}
# 닿소리: (모음 앞 첫소리, 홀로 설 때 첫소리). 홀로 설 때는 + ㅡ (ʃ · ç 는 + ㅣ)
BASE = {"b": "ㅂ", "d": "ㄷ", "g": "ㄱ", "ɡ": "ㄱ", "f": "ㅍ", "v": "ㅂ", "z": "ㅈ", "ʒ": "ㅈ", "dʒ": "ㅈ", "tʃ": "ㅊ", "ts": "ㅊ", "pf": "ㅍ",
        "m": "ㅁ", "n": "ㄴ", "ŋ": "ㅇ", "l": "ㄹ", "h": "ㅎ", "ç": "ㅎ", "x": "ㅎ", "χ": "ㅎ", "θ": "ㅆ", "ð": "ㄷ", "ʃ": "ㅅ", "ɲ": "ㄴ",
        "p": "ㅍ", "t": "ㅌ", "k": "ㅋ", "s": "ㅅ", "r": "ㄹ", "ʁ": "ㄹ", "ɹ": "ㄹ", "ɾ": "ㄹ", "ʀ": "ㄹ", "c": "ㅋ", "ʎ": "ㄹ"}
TENSE_FR = {"p": "ㅃ", "t": "ㄸ", "k": "ㄲ", "s": "ㅆ"}
MULTI = ["dʒ", "tʃ", "ts", "pf", "ɑ̃", "ɔ̃", "ɛ̃", "œ̃", "ã", "õ", "ẽ", "oʊ", "əʊ"]
GLIDES = {"j", "w", "ɥ"}
SKIP = set("ˈˌːˑ‿ʔ̯̩̆.") | {"̆", "̯", "̩"}


def phonemes(ipa):
    s = "".join(c for c in ipa if c not in SKIP)
    out, i = [], 0
    while i < len(s):
        for m in MULTI:
            if s.startswith(m, i):
                out.append(m); i += len(m); break
        else:
            out.append(s[i]); i += 1
    return out


def is_vowel(p, lang):
    return p in VOWELS or p in NASAL or p == "ə"


def hangul_word(ipa, lang):
    """IPA 낱말 하나 → 한글 (글자 짓기는 첫소리 · 가운뎃소리 · 끝소리)."""
    P = [p for p in phonemes(ipa) if p in BASE or p in GLIDES or is_vowel(p, lang)]
    # 프랑스어 espeak 은 반모음 ɥ 를 모음 앞의 y 로 적는다 (lui → lyi)
    if lang == "fr":
        P = ["ɥ" if p == "y" and i + 1 < len(P) and is_vowel(P[i + 1], lang) else p for i, p in enumerate(P)]
    syl = []          # [첫, 가운데, 끝]
    onset, glide = None, None

    def flush_onset():
        nonlocal onset
        if onset is not None:
            syl.append([onset[1], "ㅣ" if onset[0] in ("ʃ", "ç") else "ㅡ", ""]); onset = None

    for i, p in enumerate(P):
        nxt = P[i + 1] if i + 1 < len(P) else None
        nxt2 = P[i + 2] if i + 2 < len(P) else None
        before_vowel = nxt is not None and (is_vowel(nxt, lang) or (nxt in GLIDES and nxt2 is not None and is_vowel(nxt2, lang)))
        if is_vowel(p, lang):
            m = NASAL.get(p) or VOWELS.get(p) or SCHWA.get(lang, "ㅓ")
            jong = "ㅇ" if p in NASAL else ""
            o = onset[1] if onset else "ㅇ"
            oc = onset[0] if onset else None
            if oc in ("ʃ", "ɲ"):
                m = YOT.get(m, m)
            if glide == "j":
                if oc is None or lang == "en" or oc in ("ʃ", "ɲ"):
                    m = YOT.get(m, m)
                else:
                    syl.append([o, "ㅣ", ""]); o = "ㅇ"
            elif glide == "w":
                if oc is None or lang == "en":
                    m = WAU.get(m, m)
                else:
                    syl.append([o, "ㅜ", ""]); o = "ㅇ"
            elif glide == "ɥ":
                if m == "ㅣ":
                    m = "ㅟ"
                else:
                    syl.append([o, "ㅟ", ""]); o = "ㅇ"
            syl.append([o, m, jong]); onset, glide = None, None
            continue
        if p in GLIDES:
            if nxt is not None and is_vowel(nxt, lang):
                glide = p; continue
            # 모음 앞이 아닌 반모음: 이 · 우 · 위 (프랑스어 끝의 j 는 유: travail 트라바유)
            flush_onset()
            if p == "j" and syl and not syl[-1][2] and syl[-1][1] in ("ㅏ", "ㅗ", "ㅓ", "ㅜ", "ㅔ", "ㅐ") and lang != "fr":
                syl.append(["ㅇ", "ㅣ", ""])
            else:
                syl.append(["ㅇ", {"j": "ㅠ" if lang == "fr" else "ㅣ", "w": "ㅜ", "ɥ": "ㅟ"}[p], ""])
            continue
        # 닿소리
        if before_vowel:
            flush_onset()
            # 모음 앞의 ŋ 은 앞 글자 받침 (singen 징엔)
            if p == "ŋ" and syl and not syl[-1][2]:
                syl[-1][2] = "ㅇ"; continue
            jamo = TENSE_FR.get(p) if lang == "fr" and p in TENSE_FR else BASE[p]
            # 모음 사이 · 닿소리 뒤의 l 은 ㄹㄹ (올리브 · 알레 · 블랑)
            if p == "l" and syl and not syl[-1][2] and i > 0 and (is_vowel(P[i - 1], lang) or (P[i - 1] in BASE and syl[-1][1] in ("ㅡ", "ㅜ"))):
                syl[-1][2] = "ㄹ"
            onset = (p, jamo)
            continue
        # 모음 앞이 아님: 앞 글자 받침이 되거나 홀로 (ㅡ)
        # 영어: 모음 앞이 아닌 r 은 읽지 않는다 (car 카), 이 · 에 · 우 뒤면 ‘어’ (dear 디어)
        if p in ("ɹ", "r") and lang == "en":
            if i > 0 and P[i - 1] in ("i", "ɪ", "e", "ɛ", "u", "ʊ") and syl and not syl[-1][2]:
                syl.append(["ㅇ", "ㅓ", ""])
            continue
        if onset is None and syl and not syl[-1][2] and (i > 0 and (is_vowel(P[i - 1], lang))):
            coda = {"m": "ㅁ", "n": "ㄴ", "ŋ": "ㅇ", "l": "ㄹ", "ɲ": "ㄴ"}.get(p)
            if coda is None and lang == "en":
                coda = {"p": "ㅂ", "t": "ㅅ", "k": "ㄱ"}.get(p)
            if coda is not None:
                syl[-1][2] = coda; continue
        flush_onset()
        if p == "ʃ":
            # 닿소리 앞의 ʃ 는 슈 (Schwester 슈베스터), 낱말 끝이면 시 (Tisch 티시)
            syl.append(["ㅅ", "ㅣ" if nxt is None else "ㅠ", ""]); continue
        onset = (p, BASE[p])
        flush_onset()
    flush_onset()
    return "".join(compose(a, b, c) for a, b, c in syl)


def espeak(text, lang):
    """espeak-ng IPA (줄마다 한 마디). 없으면 None."""
    exe = shutil.which("espeak-ng")
    if exe is None:
        return None
    r = subprocess.run([exe, "-q", "--ipa", "-v", VOICE.get(lang, lang), text], capture_output=True, text=True)
    return r.stdout


def _tokens(text):
    """원문 낱말 (문장부호만인 토막은 앞 낱말에 붙임)."""
    out = []
    for w in text.split():
        if out and PUNCT_ONLY.match(w):
            out[-1] = out[-1] + " " + w
        else:
            out.append(w)
    return out


def _core(tok):
    """낱말 알맹이와 앞뒤 문장부호: ("¿", "Hola", " !")."""
    m = re.match(r"^([^\w]*)(.*?)([^\w]*)$", tok, re.UNICODE)
    return m.group(1), m.group(2), m.group(3)


def _ipa_words(text, lang):
    raw = espeak(text, lang)
    if raw is None:
        return None
    words = raw.split()
    # 프랑스어 연음 자음 (le-z amˈi): 하이픈 뒤 닿소리는 다음 낱말 앞으로
    out = []
    carry = ""
    for w in words:
        w = carry + w; carry = ""
        if "-" in w:
            head, _, tail = w.rpartition("-")
            if tail and all(not is_vowel(p, lang) for p in phonemes(tail)):
                w, carry = head.replace("-", ""), tail
            else:
                w = w.replace("-", "")
        out.append(w)
    if carry and out:
        out[-1] += carry
    return out


SILENT_END = ("t", "s", "x", "z", "d")


def _liaison(ipa, cores, lang):
    """프랑스어 연음: 글자로는 소리 나지 않는 끝 (-t · -s · -x · -z · -d) 의 닿소리가 IPA 끝에 붙고 다음 낱말이 모음이면 다음 낱말 앞으로 (sont arrivés → 쏭 따리베)."""
    if lang != "fr":
        return ipa
    words = [c for c in cores if c[1]]
    out = list(ipa)
    for k in range(len(out) - 1):
        ph = phonemes(out[k]); nx = phonemes(out[k + 1])
        core = words[k][1].lower()
        if len(ph) >= 2 and core.endswith(SILENT_END) and not words[k][2].strip() and ph[-1] in BASE and is_vowel(ph[-2], lang) \
                and nx and (is_vowel(nx[0], lang) or nx[0] in GLIDES):
            out[k] = "".join(ph[:-1]); out[k + 1] = ph[-1] + out[k + 1]
    return out


def _tidy(s):
    """문장부호 앞 띄어쓰기는 뺀다 (프랑스어 “toi ?” → “뚜아?”)."""
    return re.sub(r"\s+([?!:;»])", r"\1", s).strip()


def hangul(text, lang, cache=None):
    """한 줄 → 한글 어림 (띄어쓰기 · 문장부호 그대로). 낱말 수가 IPA 와 안 맞으면 낱말마다 따로. 못 만들면 None."""
    toks = _tokens(text)
    if not toks:
        return None
    cores = [_core(t) for t in toks]
    ipa = _ipa_words(text, lang)
    if ipa is None:
        return None
    if len(ipa) != len([c for c in cores if c[1]]):
        ipa = []
        for _, core, _ in cores:
            if not core:
                continue
            key = (lang, core)
            if cache is not None and key in cache:
                ipa.append(cache[key]); continue
            w = "".join(_ipa_words(core, lang) or [])
            if cache is not None:
                cache[key] = w
            ipa.append(w)
    ipa = _liaison(ipa, cores, lang)
    out, k = [], 0
    for pre, core, post in cores:
        if core:
            h = hangul_word(ipa[k], lang); k += 1
            out.append(pre + (h or core) + post)
        else:
            out.append(pre + post)
    return _tidy(" ".join(out))


# ── 한국어 → 로마자 (소리 나는 대로) ─────────────────────────
R_CHO = ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"]
R_JUNG = ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"]
CHO_L = list("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ")
# 겹받침: (앞에 남는 것, 뒤로 넘어가는 것)
SPLIT = {"ㄳ": ("ㄱ", "ㅅ"), "ㄵ": ("ㄴ", "ㅈ"), "ㄶ": ("ㄴ", "ㅎ"), "ㄺ": ("ㄹ", "ㄱ"), "ㄻ": ("ㄹ", "ㅁ"), "ㄼ": ("ㄹ", "ㅂ"), "ㄽ": ("ㄹ", "ㅅ"),
         "ㄾ": ("ㄹ", "ㅌ"), "ㄿ": ("ㄹ", "ㅍ"), "ㅀ": ("ㄹ", "ㅎ"), "ㅄ": ("ㅂ", "ㅅ"), "ㄲ": ("", "ㄲ"), "ㅆ": ("", "ㅆ")}
# 끝소리 일곱 (대표음)
NEUT = {"ㄱ": "ㄱ", "ㄲ": "ㄱ", "ㅋ": "ㄱ", "ㄳ": "ㄱ", "ㄺ": "ㄱ", "ㄴ": "ㄴ", "ㄵ": "ㄴ", "ㄶ": "ㄴ", "ㄷ": "ㄷ", "ㅅ": "ㄷ", "ㅆ": "ㄷ", "ㅈ": "ㄷ",
        "ㅊ": "ㄷ", "ㅌ": "ㄷ", "ㅎ": "ㄷ", "ㄹ": "ㄹ", "ㄼ": "ㄹ", "ㄽ": "ㄹ", "ㄾ": "ㄹ", "ㅀ": "ㄹ", "ㅁ": "ㅁ", "ㄻ": "ㅁ", "ㅂ": "ㅂ", "ㅍ": "ㅂ",
        "ㅄ": "ㅂ", "ㄿ": "ㅂ", "ㅇ": "ㅇ"}
R_JONG = {"ㄱ": "k", "ㄴ": "n", "ㄷ": "t", "ㄹ": "l", "ㅁ": "m", "ㅂ": "p", "ㅇ": "ng", "": ""}
ASP = {"ㄱ": "ㅋ", "ㄷ": "ㅌ", "ㅂ": "ㅍ", "ㅈ": "ㅊ"}
TENSE = {"ㄱ": "ㄲ", "ㄷ": "ㄸ", "ㅂ": "ㅃ", "ㅅ": "ㅆ", "ㅈ": "ㅉ"}


def _decompose(c):
    n = ord(c) - 0xAC00
    return [CHO_L[n // 588], n % 588 // 28, JONG[n % 28]]


def _korean_word(word):
    """어절 하나 (한글만) → 소리 나는 대로 바꾼 로마자."""
    S = [_decompose(c) for c in word]
    for i in range(len(S) - 1):
        a, b = S[i], S[i + 1]
        j, c = a[2], b[0]
        if not j:
            continue
        if c == "ㅇ":
            # 구개음화: ㄷ · ㅌ + 이 → 지 · 치
            if j in ("ㄷ", "ㅌ") and b[1] == 20:
                a[2], b[0] = "", "ㅈ" if j == "ㄷ" else "ㅊ"; continue
            if j == "ㅎ":
                a[2] = ""; continue
            if j == "ㅇ":
                continue
            # 연음 (ㄶ · ㅀ 의 ㅎ 은 빠진다)
            if j in SPLIT:
                keep, move = SPLIT[j]
                if move == "ㅎ":
                    a[2], b[0] = "", keep
                else:
                    a[2], b[0] = keep, move
            else:
                a[2], b[0] = "", j
            continue
        # 격음화
        if j in ("ㅎ", "ㄶ", "ㅀ") and c in ASP:
            a[2] = {"ㅎ": "", "ㄶ": "ㄴ", "ㅀ": "ㄹ"}[j]; b[0] = ASP[c]; continue
        if c == "ㅎ" and NEUT.get(j) in ("ㄱ", "ㄷ", "ㅂ") and (j in ASP or NEUT[j] in ASP):
            b[0] = ASP[NEUT[j]]; a[2] = "ㄹ" if j in ("ㄺ", "ㄼ") else ""; continue
        if c == "ㅎ" and j in ("ㄴ", "ㄹ", "ㅁ", "ㅇ"):
            continue
        n = NEUT.get(j, j)
        # 비음화
        if c in ("ㄴ", "ㅁ") and n in ("ㄱ", "ㄷ", "ㅂ"):
            a[2] = {"ㄱ": "ㅇ", "ㄷ": "ㄴ", "ㅂ": "ㅁ"}[n]; continue
        if c == "ㄹ" and n in ("ㅁ", "ㅇ"):
            a[2] = n; b[0] = "ㄴ"; continue
        if c == "ㄹ" and n in ("ㄱ", "ㄷ", "ㅂ"):
            a[2] = {"ㄱ": "ㅇ", "ㄷ": "ㄴ", "ㅂ": "ㅁ"}[n]; b[0] = "ㄴ"; continue
        # 유음화
        if (n == "ㄴ" and c == "ㄹ") or (n == "ㄹ" and c == "ㄴ"):
            a[2], b[0] = "ㄹ", "ㄹ"; continue
        # 경음화
        if n in ("ㄱ", "ㄷ", "ㅂ") and c in TENSE:
            a[2] = n; b[0] = TENSE[c]; continue
        a[2] = n
    out = []
    for k, (cho, jung, jong) in enumerate(S):
        r = R_CHO[CHO_L.index(cho)]
        prev = R_JONG[NEUT.get(S[k - 1][2], S[k - 1][2])] if k else ""
        if cho == "ㄹ" and prev == "l":
            r = "l"
        # 된소리 앞 받침: 학꾜 → hak-kyo (k + kk 를 겹쳐 쓰지 않음), 맏쌀 → ma-ssal
        if prev and cho in ("ㄲ", "ㄸ", "ㅃ") and prev in ("k", "t", "p"):
            r = r[1:]
        elif prev == "t" and cho in ("ㅆ", "ㅉ"):
            out[-1] = out[-1][:-1]
        out.append(r + R_JUNG[jung] + R_JONG[NEUT.get(jong, jong)])
    return "".join(out)


def romanize(text):
    """한국어 한 줄 → 로마자 (띄어쓰기 · 문장부호 그대로, 한글이 아닌 글자는 그대로)."""
    text = text.replace("⁠", "")
    s = re.sub(r"[가-힣]+", lambda m: _korean_word(m.group(0)), text)
    return _tidy(s)


def hint(text, lang, cache=None):
    text = text.replace("⁠", "").strip()
    if not text:
        return None
    if lang == "ko":
        return romanize(text)
    return hangul(text, lang, cache)


# ── 작품마다 모으기 ─────────────────────────────────────
def _mine(talk, lang):
    m = (talk or {}).get("mine") or {}
    lines = (talk or {}).get("lines") or []
    li = m.get("line")
    if not isinstance(li, int) or not 0 <= li < len(lines):
        return []
    line, slot = lines[li].get(lang) or "", (m.get("slot") or {}).get(lang) or ""
    if not slot or slot not in line:
        return []
    return [line.replace(slot, o[lang], 1) for o in m.get("opts") or [] if o.get(lang)]


def texts(sid, lang):
    """이 작품 · 배우는 말 [lang] 에서 말해 볼 수 있는 글들 (차례 그대로, 겹치지 않게)."""
    folder = os.path.join(ROOT, "data", sid)
    meta = json.load(open(os.path.join(folder, "series.json"), encoding="utf-8"))
    orig = meta["original"].lower()
    deck = "" if lang == orig else "_" + lang
    files = [c["file"] for c in meta["chapters"]] + [meta[k]["file"] for k in ("prologue", "outro") if "file" in meta.get(k, {})]
    got = []
    for f in files:
        p = os.path.join(folder, f)
        if not os.path.exists(p):
            p = p.replace(".json", ".draft.json")
        if not os.path.exists(p):
            continue
        for l in json.load(open(p, encoding="utf-8")).get("letters", []):
            got += [m.get(lang, "") for m in l.get("messages", [])]
            t = l.get("talk") or {}
            got += [x.get(lang, "") for x in t.get("lines", []) if x.get("who") == "me"]
            got += _mine(t, lang)
            for r in l.get("replies") or ([l["reply"]] if l.get("reply") else []):
                got.append(r.get(lang, ""))
                if lang == "ko":
                    got += [r.get("koLow", ""), r.get("koHigh", "")]
            for w in l.get("words", []):
                got.append(w.get(lang, "")); got.append(w.get(lang + "_modern", ""))
    for name in ("daily", "basics", "anchors"):
        p = os.path.join(folder, f"{name}{deck}.json")
        if not os.path.exists(p):
            continue
        d = json.load(open(p, encoding="utf-8"))
        if name == "daily":
            for x in (d.get("letters") or {}).values():
                got += [k.split("#")[0] for k in x.get("words", [])] + [ph.get("t", "") for ph in x.get("phrases", [])]
        elif name == "basics":
            for it in d.get("items", []):
                got += [it.get("w", ""), ((it.get("ex") or {}).get("t") or "")]
        else:
            got += [a.get("t", "") for a in d.get("anchors", [])]
    clean = [re.sub(r"[〈〉]", "", x.replace("⁠", "")).strip() for x in got if isinstance(x, str)]
    return list(dict.fromkeys(x for x in clean if x))


def build(sid, langs=None, verbose=True):
    """data/<작품>/pron[_en|_ko].json 을 다시 쓴다. espeak-ng 가 없으면 프랑스어 · 독일어 · 영어는 건너뛴다."""
    meta = json.load(open(os.path.join(ROOT, "data", sid, "series.json"), encoding="utf-8"))
    orig = meta["original"].lower()
    written = []
    for lang in langs or [orig, "en", "ko"]:
        if lang != "ko" and shutil.which("espeak-ng") is None:
            print(f"pron: espeak-ng 없음 → {sid} {lang} 건너뜀", file=sys.stderr); continue
        cache = {}
        items = {}
        for t in texts(sid, lang):
            h = hint(t, lang, cache)
            if h:
                items[t] = h
            # 낱말 하나하나 (앱이 마디 · 다른 글을 낱말 힌트로 이어 붙인다)
            for tok in _tokens(t):
                core = _core(tok)[1]
                if core and core not in items:
                    w = hint(core, lang, cache)
                    if w:
                        items[core] = w
        path = os.path.join(ROOT, "data", sid, f"pron{'' if lang == orig else '_' + lang}.json")
        doc = {"lang": lang, "auto": True, "items": dict(sorted(items.items()))}
        open(path, "w", encoding="utf-8").write(json.dumps(doc, ensure_ascii=False, indent=1) + "\n")
        written.append(path)
        if verbose:
            print(f"pron: {sid} {lang} {len(items)}", file=sys.stderr)
    return written


def main():
    if "--say" in sys.argv:
        i = sys.argv.index("--say")
        print(hint(sys.argv[i + 2], sys.argv[i + 1])); return
    ids = [a for a in sys.argv[1:] if not a.startswith("-")] or sorted(
        os.path.basename(os.path.dirname(p)) for p in glob.glob(os.path.join(ROOT, "data", "*", "series.json")))
    for sid in ids:
        build(sid)


if __name__ == "__main__":
    main()
