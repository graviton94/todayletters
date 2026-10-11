"""
목소리 견본: Supertonic 3 (Supertone, OpenRAIL-M · 온디바이스 ONNX) 로 편지 발췌(data/<작품>/sample_letter.json)를
원어 · 영어 · 한국어로, 목소리마다 하나씩 만든다. 듣고 배역을 고르기 위한 것.
목소리 적는 법: "M3" (하나), "M1+M3@0.5" (두 결을 섞음, 뒤쪽 비율), "M1>M3>M5@0.7" (목소리 길 위의 자리),
끝에 ":0.9" 를 붙이면 빠르기 (기본 0.9).
높낮이는 손대지 않는다 (만든 뒤에 음을 옮기면 소리가 떨린다).
숨: 문장부호에서 0.35초, 메시지와 메시지 사이 0.9초.
GitHub Actions 에서 돌려요: python scripts/tts_sample.py <supertonic/py> <assets> <출력 폴더> [목소리 …]
환경 변수 SAMPLE_SERIES (기본 vincent)
"""
import json, os, re, sys
import numpy as np, soundfile as sf

py, assets, out = sys.argv[1:4]
voices = sys.argv[4:] or ["M1", "M2", "M3", "M4", "M5"]
sys.path.insert(0, py)
from helper import Style, load_text_to_speech, load_voice_style  # noqa: E402

series = os.environ.get("SAMPLE_SERIES") or "vincent"
letter = json.load(open(f"data/{series}/sample_letter.json", encoding="utf-8"))
langs = [letter["original"], "en", "ko"]
STEPS = 16


def phrases(text: str, lang: str):
    """문장부호에서 끊되, 너무 짧은 토막은 앞 토막에 붙인다."""
    cut = r"(?<=[,;:.?!])\s+" if lang != "ko" else r"(?<=[,.?!])\s+"
    res = []
    for p in re.split(cut, text.strip()):
        if res and (len(p) < 10 or len(res[-1]) < 10):
            res[-1] += " " + p
        else:
            res.append(p)
    return res


def style_of(name):
    return load_voice_style([os.path.join(assets, "voice_styles", f"{name}.json")])


def voice(spec):
    """'M1+M3@0.5:1.0' → (Style, 빠르기)."""
    spec, _, speed = spec.partition(":")
    names, _, mix = spec.partition("@")
    if ">" in names:
        # 목소리 길 "M1>M3>M5@0.7": 길 위의 자리 (narrate.py 의 path 와 같은 셈)
        path = [style_of(n) for n in names.split(">")]
        x = min(max(float(mix or 0), 0.0), 1.0) * (len(path) - 1)
        i = min(int(x), len(path) - 2)
        k = x - i
        a, b = path[i], path[i + 1]
        return Style((1 - k) * a.ttl + k * b.ttl, (1 - k) * a.dp + k * b.dp), float(speed or 0.9)
    parts = names.split("+")
    if len(parts) == 1:
        st = style_of(parts[0])
    else:
        a, b, k = style_of(parts[0]), style_of(parts[1]), float(mix or 0.5)
        st = Style((1 - k) * a.ttl + k * b.ttl, (1 - k) * a.dp + k * b.dp)
    return st, float(speed or 0.9)


os.makedirs(out, exist_ok=True)
tts = load_text_to_speech(os.path.join(assets, "onnx"), False)
sr = tts.sample_rate


def silence(s):
    return np.zeros(int(s * sr), dtype=np.float32)


for lang in langs:
    for spec in voices:
        style, speed = voice(spec)
        parts = [silence(0.4)]
        for m in letter["messages"]:
            for p in phrases(m[lang], lang):
                wav, dur = tts(p, lang, style, STEPS, speed)
                parts.append(wav[0, : int(sr * dur[0].item())].astype(np.float32))
                parts.append(silence(0.35))
            parts[-1] = silence(0.9)
        name = spec.replace("+", "-").replace(">", "-").replace("@", "_").replace(":", "_s")
        sf.write(os.path.join(out, f"{series}_{lang}_{name}.wav"), np.concatenate(parts), sr)
        print("ok", lang, spec, flush=True)
