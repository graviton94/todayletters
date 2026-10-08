"""
편지 낭독 만들기 (Supertonic 3, 버전 고정). 배역표 · 감정 프리셋 · 편지 데이터를 읽어
메시지마다 · 언어마다 파일 하나, 낱말마다 · 언어마다 파일 하나를 만든다.

  목소리: data/<인물>/voices.json 의 young · old 를 챕터 age 로 섞은 것 (세 언어 같은 비율)
  감정:   편지의 "mood" (없으면 챕터 기본값) → data/voices/presets.json 의 빠르기 · 높낮이 · 쉼 · 앞 숨
  결과:   <출력>/<작품>/<챕터>/<편지 id>/m<번호>_<언어>.m4a , w<번호>_<언어>.m4a
          32kHz 모노 AAC 48k (하루의 성경과 같은 결). 쉼은 메시지 안의 문장부호에서만; 메시지 사이 쉼은 앱이 넣는다.

GitHub Actions 에서 돌려요:
  python scripts/narrate.py <supertonic/py> <assets> <편지 파일.json> <출력 폴더> [언어 …]
"""
import json, os, re, subprocess, sys, tempfile
import numpy as np, soundfile as sf

py, assets, src, out = sys.argv[1:5]
sys.path.insert(0, py)
from helper import Style, load_text_to_speech, load_voice_style  # noqa: E402

STEPS = 16
book = json.load(open(src, encoding="utf-8"))
langs = sys.argv[5:] or [book["original"], "en", "ko"]
series, chapter = book["series"], book["chapter"]
cast = json.load(open(f"data/{series}/voices.json", encoding="utf-8"))
presets = json.load(open("data/voices/presets.json", encoding="utf-8"))

tts = load_text_to_speech(os.path.join(assets, "onnx"), False)
sr = tts.sample_rate


def style_of(name):
    return load_voice_style([os.path.join(assets, "voice_styles", f"{name}.json")])


young, old = style_of(cast["young"]), style_of(cast["old"])
age = cast["chapters"][chapter]["age"]
voice = Style((1 - age) * young.ttl + age * old.ttl, (1 - age) * young.dp + age * old.dp)


def phrases(text: str, lang: str):
    cut = r"(?<=[,;:.?!…])\s+" if lang != "ko" else r"(?<=[,.?!…])\s+"
    res = []
    for p in re.split(cut, text.strip()):
        if res and (len(p) < 10 or len(res[-1]) < 10):
            res[-1] += " " + p
        else:
            res.append(p)
    return res


def silence(s):
    return np.zeros(int(s * sr), dtype=np.float32)


def speak(text, lang, pr, lead=""):
    parts = [silence(0.15)]
    for j, p in enumerate(phrases(text, lang)):
        if j == 0 and lead:
            p = f"{lead} {p}"
        wav, dur = tts(p, lang, voice, STEPS, pr["speed"])
        parts += [wav[0, : int(sr * dur[0].item())].astype(np.float32), silence(0.35 * pr["pause"])]
    parts[-1] = silence(0.2)
    return np.concatenate(parts)


def save(path, audio, pitch):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as w:
        sf.write(w.name, audio, sr)
        flt = f"asetrate={int(sr * pitch)},aresample={sr},atempo={1 / pitch:.5f}," if pitch != 1.0 else ""
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", w.name, "-af", flt + "aresample=32000",
                        "-ac", "1", "-c:a", "aac", "-b:a", "48k", path], check=True)


for letter in book["letters"]:
    pr = presets[letter.get("mood") or cast["chapters"][chapter]["mood"]]
    base = os.path.join(out, series, chapter, letter["id"])
    for lang in langs:
        for i, m in enumerate(letter["messages"], 1):
            save(os.path.join(base, f"m{i}_{lang}.m4a"), speak(m[lang], lang, pr, pr["lead"] if i == 1 else ""), pr["pitch"])
        # 낱말 발음: 차분하게, 숨 없이
        for i, w in enumerate(letter.get("words", []), 1):
            if lang in w:
                save(os.path.join(base, f"w{i}_{lang}.m4a"), speak(w[lang], lang, presets["calm"]), 1.0)
    print("ok", letter["id"], flush=True)
