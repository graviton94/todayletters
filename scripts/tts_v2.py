"""
목소리 견본 v2 (한국어): 빈센트 목소리를 M2(young)와 M5(old) 의 결 벡터를 섞어 만든다.
  A  섞는 비율: old 0 · 25 · 50 · 75 · 100 %            (편지 전체, 차분)
  B  나이 흐름: 같은 짧은 문장을 챕터 I → V 비율로 이어 붙인 파일 하나
  C  빠르기 2 × 높낮이 2                                 (비율 50 %, 편지 전체)
  D  감정 프리셋 4 (data/voices/presets.json)            (비율 50 %, 편지 전체)
목소리 결 = Supertonic 의 style_ttl(말소리 결) · style_dp(말 길이 · 리듬 결). 섞기는 두 벡터의 가중 평균.
높낮이는 ffmpeg 로 (빠르기는 그대로 두고 음만 옮김). 결과는 32kHz 모노 AAC (앱과 같은 결).
GitHub Actions 에서 돌려요: python scripts/tts_v2.py <supertonic/py> <assets> <출력 폴더>
"""
import json, os, re, subprocess, sys, tempfile
import numpy as np, soundfile as sf

py, assets, out = sys.argv[1:4]
sys.path.insert(0, py)
from helper import Style, load_text_to_speech, load_voice_style  # noqa: E402

LANG = "ko"
STEPS = 16
letter = json.load(open("data/vincent/sample_letter.json", encoding="utf-8"))
presets = {k: v for k, v in json.load(open("data/voices/presets.json", encoding="utf-8")).items() if not k.startswith("_")}
cast = json.load(open("data/vincent/voices.json", encoding="utf-8"))
texts = [m[LANG] for m in letter["messages"]]

os.makedirs(out, exist_ok=True)
tts = load_text_to_speech(os.path.join(assets, "onnx"), False)
sr = tts.sample_rate
young = load_voice_style([os.path.join(assets, "voice_styles", f"{cast['young']}.json")])
old = load_voice_style([os.path.join(assets, "voice_styles", f"{cast['old']}.json")])


def blend(age: float) -> Style:
    """age 0 = young 그대로, 1 = old 그대로."""
    return Style((1 - age) * young.ttl + age * old.ttl, (1 - age) * young.dp + age * old.dp)


def silence(s):
    return np.zeros(int(s * sr), dtype=np.float32)


def phrases(text: str):
    res = []
    for p in re.split(r"(?<=[,.?!])\s+", text.strip()):
        if res and (len(p) < 10 or len(res[-1]) < 10):
            res[-1] += " " + p
        else:
            res.append(p)
    return res


def speak(lines, style, speed, pause=1.0, lead=""):
    parts = [silence(0.4)]
    for i, line in enumerate(lines):
        for j, p in enumerate(phrases(line)):
            if i == 0 and j == 0 and lead:
                p = f"{lead} {p}"
            wav, dur = tts(p, LANG, style, STEPS, speed)
            parts.append(wav[0, : int(sr * dur[0].item())].astype(np.float32))
            parts.append(silence(0.35 * pause))
        parts[-1] = silence(0.9 * pause)
    return np.concatenate(parts)


def save(name, audio, pitch=1.0):
    with tempfile.NamedTemporaryFile(suffix=".wav") as w:
        sf.write(w.name, audio, sr)
        flt = f"asetrate={int(sr * pitch)},aresample={sr},atempo={1 / pitch:.5f}," if pitch != 1.0 else ""
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", w.name, "-af", flt + "aresample=32000",
                        "-ac", "1", "-c:a", "aac", "-b:a", "48k", os.path.join(out, f"{name}.m4a")], check=True)
    print("ok", name, flush=True)


calm = presets["calm"]
# A: 섞는 비율
for pct in (0, 25, 50, 75, 100):
    save(f"a_{pct:03d}", speak(texts, blend(pct / 100), calm["speed"]))

# B: 나이 흐름 (챕터 I → V), 같은 문장을 이어서
line = "그래도 별을 보면 나는 늘 꿈을 꾸게 돼."
parts = []
for ch in ("I", "II", "III", "IV", "V"):
    parts += [speak([line], blend(cast["chapters"][ch]["age"]), calm["speed"]), silence(0.8)]
save("b_aging", np.concatenate(parts))

# C: 빠르기 × 높낮이 (비율 50 %)
for speed in (0.85, 0.95):
    for pitch in (0.96, 1.00):
        save(f"c_s{int(speed * 100)}_p{int(round(pitch * 100))}", speak(texts, blend(0.5), speed), pitch)

# D: 감정 프리셋 (비율 50 %)
for key, pr in presets.items():
    save(f"d_{key}", speak(texts, blend(0.5), pr["speed"], pr["pause"], pr["lead"]), pr["pitch"])
