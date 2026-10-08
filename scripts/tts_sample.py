"""
목소리 견본: Supertonic 3 (Supertone, OpenRAIL-M · 온디바이스 ONNX) 로 빈센트 편지 발췌를
원어(fr) · 영어 · 한국어로, 목소리마다 하나씩 만든다. 듣고 빈센트 배역을 고르기 위한 것.
숨: 문장부호에서 0.35초, 메시지와 메시지 사이 0.9초. 빠르기는 조금 느리게 (편지를 읽는 결).
GitHub Actions 에서 돌려요: python scripts/tts_sample.py <supertonic/py> <assets> <출력 폴더> [목소리 …]
"""
import json, os, re, sys
import numpy as np, soundfile as sf

py, assets, out = sys.argv[1:4]
voices = sys.argv[4:] or ["M1", "M2", "M3", "M4", "M5"]  # 빈센트 배역: 남성 목소리만
sys.path.insert(0, py)
from helper import load_text_to_speech, load_voice_style  # noqa: E402

letter = json.load(open("data/vincent/sample_letter.json", encoding="utf-8"))
langs = [letter["original"], "en", "ko"]
SPEED = 0.9
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


os.makedirs(out, exist_ok=True)
tts = load_text_to_speech(os.path.join(assets, "onnx"), False)
sr = tts.sample_rate


def silence(s):
    return np.zeros(int(s * sr), dtype=np.float32)


for lang in langs:
    for name in voices:
        style = load_voice_style([os.path.join(assets, "voice_styles", f"{name}.json")])
        parts = [silence(0.4)]
        for m in letter["messages"]:
            for p in phrases(m[lang], lang):
                wav, dur = tts(p, lang, style, STEPS, SPEED)
                parts.append(wav[0, : int(sr * dur[0].item())].astype(np.float32))
                parts.append(silence(0.35))
            parts[-1] = silence(0.9)
        sf.write(os.path.join(out, f"vincent_{lang}_{name}.wav"), np.concatenate(parts), sr)
        print("ok", lang, name, flush=True)
