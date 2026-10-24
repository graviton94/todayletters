"""
낭독 소리마다 낱말 시각: <소리 이름>.words.json (소리 파일 옆, 새 파일만 · 이미 있으면 건드리지 않는다).

  앱이 그 소리와 함께 보여 주는 글을 띄어쓰기로 나눈 낱말 (core Breaks.plain 뒤 \\s+ 로 나누는 것과 같다) 하나하나가
  소리의 몇 밀리초에 나오는지 강제 정렬 (torchaudio MMS_FA, wav2vec2 다국어 CTC) 로 잰다.
  정렬에 넣는 글: fr · de · es · en 은 소문자 · 악센트 · 구두점 뺌 (숫자는 그 말로 읽은 글, num2words), ko 는 uroman 로마자.
  파일에는 화면 글의 낱말 그대로 ("t") 를 적는다.

  {"v": 1, "ok": true, "words": [{"t": "Mon", "s": 812, "e": 990, "q": 0.83}, …]}
    s · e = 그 소리 처음부터 밀리초 (정수), q = 정렬 점수 0..1 (낱말 글자들의 평균 확률).
    소리가 없는 낱말 (— · 같은 기호뿐) 은 q 없이 앞 낱말의 끝 (s = e).
    ok = false: 정렬이 약하다 (낱말 평균 q < OK_MEAN 이거나 q < LOW 인 낱말이 LOW_SHARE 넘게). 앱은 그때 마디 시각을 쓴다.

  어떤 소리 (narrate.py 와 같은 이름 규칙):
    편지 메시지 m<번호>_<언어> (글 = 메시지의 그 언어 줄) · 오늘의 회화 <talk.audio><줄>_<언어> (core Talk.audioName, 지금 쓰는 이름만)
    · 답장 r<턴>_<언어> · 오늘의 표현 ph<번호>_<언어> (daily*.json phrases t) · 기초 낱말 예문 bx_<언어>_<id> (서문 파일에서, 장 "B").
    낱말 하나짜리 소리 (v_ · b_ · w · s · s_) 는 만들지 않는다.

  python scripts/align_words.py <data/작품/파일.json> <출력 폴더> [--chapters "P I B"] [--list]
    출력: <출력>/<작품>/<장>/<편지 id>/<이름>.words.json (assets/audio · remote/audio 와 같은 모양)
    --list: 정렬하지 않고 할 소리 수만 (torch 없이 돈다).
  .github/workflows/align.yml 이 돌린다 (docs/next.md 2026-10-24 낱말 시각).
"""
import json
import os
import re
import subprocess
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOTS = [os.path.join(ROOT, "android", "app", "src", "main", "assets", "audio"), os.path.join(ROOT, "remote", "audio")]
OK_MEAN, LOW, LOW_SHARE = 0.35, 0.05, 0.25


def talk_stem(t, lang):
    """narrate.py talk_stem · core Talk.audioName 과 같은 규칙."""
    a = t.get("audio")
    if isinstance(a, dict):
        return a.get(lang) or a.get("*") or a.get("default") or "t"
    return a or "t"


def clips(book, chapters=None):
    """(소리 경로 audio/ 아래 · 확장자 없이, 언어, 화면 글) 목록."""
    series, chapter, orig = book["series"], book["chapter"], book["original"]
    langs = [orig, "en", "ko"]
    out = []
    want = lambda c: not chapters or c in chapters
    daily = {}
    for lg in langs:
        f = os.path.join(ROOT, "data", series, f"daily{'' if lg == orig else '_' + lg}.json")
        daily[lg] = json.load(open(f, encoding="utf-8"))["letters"] if os.path.exists(f) else {}
    if want(chapter):
        for letter in book["letters"]:
            base = f"{series}/{chapter}/{letter['id']}"
            for lang in langs:
                for i, m in enumerate(letter.get("messages", []), 1):
                    if m.get(lang):
                        out.append((f"{base}/m{i}_{lang}", lang, m[lang]))
                t = letter.get("talk") or {}
                stem = talk_stem(t, lang)
                for i, line in enumerate(t.get("lines") or [], 1):
                    if (line.get(lang) or "").strip():
                        out.append((f"{base}/{stem}{i}_{lang}", lang, line[lang]))
                for i, r in enumerate(letter.get("replies") or [], 1):
                    if (r.get(lang) or "").strip():
                        out.append((f"{base}/r{i}_{lang}", lang, r[lang]))
                for i, ph in enumerate((daily[lang].get(letter["id"]) or {}).get("phrases", []), 1):
                    if (ph.get("t") or "").strip():
                        out.append((f"{base}/ph{i}_{lang}", lang, ph["t"]))
    if chapter == "P" and want("B"):
        for lang in langs:
            f = os.path.join(ROOT, "data", series, f"basics{'' if lang == orig else '_' + lang}.json")
            if not os.path.exists(f):
                continue
            for it in json.load(open(f, encoding="utf-8")).get("items", []):
                t = ((it.get("ex") or {}).get("t") or "").strip()
                if t:
                    out.append((f"{series}/B/basics/bx_{lang}_{it['id']}", lang, t))
    return out


def audio_of(rel):
    for r in ROOTS:
        p = os.path.join(r, rel + ".m4a")
        if os.path.isfile(p):
            return p
    return None


def done(rel):
    return any(os.path.exists(os.path.join(r, rel + ".words.json")) for r in ROOTS)


def tokens(text):
    """앱과 같은 낱말 나누기: 이음표(U+2060) 를 빼고 띄어쓰기로."""
    return [w for w in re.split(r"\s+", text.replace("⁠", "")) if w]


NUM_LANG = {"fr": "fr", "de": "de", "es": "es", "en": "en", "ko": "ko"}
FOLD = {"ß": "ss", "œ": "oe", "æ": "ae", "ø": "o", "ł": "l", "đ": "d", "ð": "d", "þ": "th", "’": "'", "‘": "'", "ʼ": "'"}


def spoken(tok, lang, roman):
    """정렬에 넣는 글 (a–z 와 ' 만). 소리가 없는 낱말이면 ""."""
    t = re.sub(r"[〈〉]", "", tok)
    if re.search(r"\d", t):
        try:
            from num2words import num2words
            t = re.sub(r"\d+", lambda m: " " + num2words(int(m.group()), lang=NUM_LANG.get(lang, "en")) + " ", t)
        except Exception:
            pass
    if lang == "ko" or re.search(r"[가-힣]", t):
        t = roman(t)
    t = t.lower()
    t = "".join(FOLD.get(c, c) for c in t)
    t = "".join(c for c in unicodedata.normalize("NFKD", t) if not unicodedata.combining(c))
    t = re.sub(r"[^a-z']", "", t)
    return t.strip("'") if t.strip("'") else ""


class Aligner:
    def __init__(self):
        import torch, torchaudio
        self.torch = torch
        self.bundle = torchaudio.pipelines.MMS_FA
        self.model = self.bundle.get_model(with_star=False).eval()
        self.dict = self.bundle.get_dict(star=None)
        self.align_fn = self.bundle.get_aligner()
        import uroman
        u = uroman.Uroman()
        self.roman = lambda s: u.romanize_string(s)

    def wave(self, path):
        raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", "16000", "-f", "f32le", "-"],
                             check=True, capture_output=True).stdout
        import numpy as np
        return self.torch.from_numpy(np.frombuffer(raw, dtype=np.float32).copy())[None]

    def run(self, path, text, lang):
        shown = tokens(text)
        said = [spoken(w, lang, self.roman) for w in shown]
        said = ["".join(c for c in s if c in self.dict) for s in said]
        idx = [i for i, s in enumerate(said) if s]
        wav = self.wave(path)
        words = [{"t": w, "s": 0, "e": 0} for w in shown]
        if not idx:
            return {"v": 1, "ok": False, "words": words}
        with self.torch.inference_mode():
            em, _ = self.model(wav)
        frames = em.size(1)
        ms = wav.size(1) / 16.0 / frames
        try:
            spans = self.align_fn(em[0], [[self.dict[c] for c in said[i]] for i in idx])
        except Exception as e:  # 소리가 글보다 짧다 등
            print("align failed", path, e, file=sys.stderr)
            return {"v": 1, "ok": False, "words": words}
        for i, sp in zip(idx, spans):
            n = sum(s.end - s.start for s in sp)
            q = sum(s.score * (s.end - s.start) for s in sp) / max(1, n)
            words[i].update(s=int(round(sp[0].start * ms)), e=int(round(sp[-1].end * ms)), q=round(float(q), 3))
        # 소리가 없는 낱말: 앞 낱말의 끝 (맨 앞이면 다음 낱말의 시작)
        last = None
        for k, w in enumerate(words):
            if "q" in w:
                last = w["e"]
            else:
                nxt = next((x["s"] for x in words[k + 1:] if "q" in x), 0)
                w["s"] = w["e"] = last if last is not None else nxt
        qs = [w["q"] for w in words if "q" in w]
        mean = sum(qs) / len(qs)
        low = sum(1 for q in qs if q < LOW) / len(qs)
        return {"v": 1, "ok": bool(mean >= OK_MEAN and low <= LOW_SHARE), "words": words}


def kind(name):
    """소리 이름 → 갈래 (요약용)."""
    for k, p in (("basic", r"bx_"), ("message", r"m\d"), ("reply", r"r\d"), ("phrase", r"ph\d"), ("talk", r"t[a-z]*\d")):
        if re.match(p, name):
            return k
    return "other"


def summary(folder):
    """<폴더> 아래 .words.json 을 작품 · 갈래마다 세어 마크다운 표 (약한 것 수 · 낱말 평균 q)."""
    rows = {}
    weak = []
    for d, _, names in os.walk(folder):
        for n in names:
            if not n.endswith(".words.json"):
                continue
            p = os.path.join(d, n)
            rel = os.path.relpath(p, folder).replace(os.sep, "/")
            doc = json.load(open(p, encoding="utf-8"))
            key = (rel.split("/")[0], kind(n))
            r = rows.setdefault(key, [0, 0, 0.0, 0])
            r[0] += 1
            r[1] += not doc["ok"]
            qs = [w["q"] for w in doc["words"] if "q" in w]
            r[2] += sum(qs); r[3] += len(qs)
            if not doc["ok"]:
                weak.append(rel)
    lines = ["| series | kind | clips | weak | mean q |", "|---|---|---:|---:|---:|"]
    tot = [0, 0]
    for (s, k), (n, w, q, c) in sorted(rows.items()):
        lines.append(f"| {s} | {k} | {n} | {w} | {q / max(1, c):.2f} |")
        tot[0] += n; tot[1] += w
    lines.append(f"| **all** | | **{tot[0]}** | **{tot[1]}** | |")
    if weak:
        lines += ["", "weak (first 40): " + " · ".join(sorted(weak)[:40])]
    return "\n".join(lines)


def main():
    args = sys.argv[1:]
    if args and args[0] == "--summary":
        print(summary(args[1]))
        return
    chapters = None
    if "--chapters" in args:
        k = args.index("--chapters"); chapters = set(args[k + 1].split()); del args[k:k + 2]
    listing = "--list" in args
    args = [a for a in args if a != "--list"]
    src, out = args[0], args[1]
    book = json.load(open(src, encoding="utf-8"))
    todo = [(rel, lang, text, audio_of(rel)) for rel, lang, text in clips(book, chapters)]
    missing = [rel for rel, _, _, a in todo if not a]
    todo = [c for c in todo if c[3] and not done(c[0])]
    print(f"{src}: {len(todo)} clips to align ({len(missing)} without audio)", flush=True)
    for rel in missing[:10]:
        print("  no audio:", rel)
    if listing or not todo:
        return
    al = Aligner()
    weak = 0
    for n, (rel, lang, text, audio) in enumerate(todo, 1):
        res = al.run(audio, text, lang)
        weak += not res["ok"]
        dst = os.path.join(out, rel + ".words.json")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, "w", encoding="utf-8") as f:
            json.dump(res, f, ensure_ascii=False, separators=(",", ":"))
        if n % 50 == 0:
            print(f"  {n}/{len(todo)} (weak {weak})", flush=True)
    print(f"done {len(todo)} clips, weak {weak}")


if __name__ == "__main__":
    main()
