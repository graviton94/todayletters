#!/usr/bin/env python3
"""
홍보 영상의 해설 (빈센트 목소리 더빙): 장면마다 한 줄, 한국어판은 한국어 · 영어판은 영어.
앱이 빈센트 낭독에 쓰는 것과 똑같이 만든다 (scripts/narrate.py 와 같은 엔진 · 목소리 · 설정):
  Supertonic 3 (버전 고정, narration-r2.yml 과 같은 판), data/vincent/voices.json 의 young · old 를
  I장 age 로 섞은 목소리, 편지 I-1 의 mood 프리셋 (data/voices/presets.json), 정밀도 32 · 조금 느리게 0.92.

쓰는 법 (GitHub Actions):  python scripts/promo/voiceover.py <supertonic/py> <supertonic/assets> <출력 폴더>
결과: <출력>/<ko|en>/vo<장면 1–6>.wav  (make.py --vo 로 넘긴다)
"""
import json
import os
import subprocess
import sys
import tempfile

import numpy as np
import soundfile as sf

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
py, assets, out = sys.argv[1:4]
sys.path.insert(0, py)
from helper import Style, load_text_to_speech, load_voice_style  # noqa: E402

# 소리 내어 읽는 글 (숫자는 글자로: 엔진이 연도를 잘못 읽지 않게). 화면 자막은 make.py 의 TEXT.
LINES = {
    "ko": [
        "천팔백팔십팔 년 이월, 테오에게 편지를 썼어.",
        "내 편지를, 내가 쓴 말 그대로 읽어 봐.",
        "모르는 낱말엔 뜻이 같이 있어.",
        "나랑 직접 이야기해 보자.",
        "볼프강도, 나폴레옹도, 고야도 편지를 기다리고 있어.",
        "언어 미술관에서 만나.",
    ],
    "en": [
        "In February eighteen eighty-eight, I wrote to Theo.",
        "Read my letters, in my own words.",
        "Every word you don't know comes with its meaning.",
        "Come, let's talk.",
        "Wolfgang, Napoleon and Goya are waiting to write to you too.",
        "See you at the Lingua Museum.",
    ],
}

STEPS_FINE, SLOWER = 32, 0.92  # scripts/narrate.py 와 같은 값
cast = json.load(open(os.path.join(ROOT, "data/vincent/voices.json"), encoding="utf-8"))
presets = json.load(open(os.path.join(ROOT, "data/voices/presets.json"), encoding="utf-8"))
book = json.load(open(os.path.join(ROOT, "data/vincent/chapter1.json"), encoding="utf-8"))
letter = book["letters"][0]
pr = presets[letter.get("mood") or cast["chapters"]["I"]["mood"]]
SPEED = float(cast.get("speed", 1.0))

tts = load_text_to_speech(os.path.join(assets, "onnx"), False)
sr = tts.sample_rate


def style_of(name):
    return load_voice_style([os.path.join(assets, "voice_styles", f"{name}.json")])


def along(path, age):
    styles = [style_of(n) for n in path]
    if len(styles) == 1:
        return styles[0]
    x = min(max(age, 0.0), 1.0) * (len(styles) - 1)
    i = min(int(x), len(styles) - 2)
    k = x - i
    a, b = styles[i], styles[i + 1]
    return Style((1 - k) * a.ttl + k * b.ttl, (1 - k) * a.dp + k * b.dp)


voice = along(cast.get("path") or [cast["young"], cast["old"]], cast["chapters"]["I"]["age"])
print("voice", cast.get("path") or [cast["young"], cast["old"]], "age", cast["chapters"]["I"]["age"], "preset", pr, "speed", SPEED)

for lang, lines in LINES.items():
    d = os.path.join(out, lang)
    os.makedirs(d, exist_ok=True)
    for n, text in enumerate(lines, 1):
        wav, dur = tts(text, lang, voice, STEPS_FINE, pr["speed"] * SLOWER * SPEED)
        audio = wav[0, : int(sr * dur[0].item())].astype(np.float32)
        p = os.path.join(d, f"vo{n}.wav")
        with tempfile.NamedTemporaryFile(suffix=".wav") as w:
            sf.write(w.name, audio, sr)
            flt = f"asetrate={int(sr * pr['pitch'])},aresample={sr},atempo={1 / pr['pitch']:.5f}," if pr["pitch"] != 1.0 else ""
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", w.name, "-af", flt + "aresample=48000", "-ac", "1", p], check=True)
        print(lang, n, f"{len(audio) / sr:.2f}s", text, flush=True)
