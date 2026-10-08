"""
낭독 자연스러움 비교 샘플 (검토용, 앱에 들어가지 않음). 같은 문장을 네 가지로 읽는다.
  A 덩어리 따로 읽기 (지금 앱): "/" "//" 표시마다 끊어서 따로 만들고 쉼을 넣음
  B 한 번에 읽기: 문장 전체를 한 번에, 단계 32 (억양이 끊기지 않음)
  C 한 번에 + 큰 쉼만 쉼표로: "//" 자리에만 쉼표를 넣어 한 번에 읽음
  D C 와 같되 조금 느리게 (빠르기 ×0.92)
사용: python scripts/tts_natural.py <supertonic/py> <assets> <편지 파일.json> <출력>
"""
import json, os, re, subprocess, sys, tempfile
import numpy as np, soundfile as sf

py, assets, src, out = sys.argv[1:5]
sys.path.insert(0, py)
from helper import Style, load_text_to_speech, load_voice_style  # noqa: E402

book = json.load(open(src, encoding="utf-8"))
cast = json.load(open("data/vincent/voices.json", encoding="utf-8"))
presets = json.load(open("data/voices/presets.json", encoding="utf-8"))
tts = load_text_to_speech(os.path.join(assets, "onnx"), False)
sr = tts.sample_rate
st = lambda n: load_voice_style([os.path.join(assets, "voice_styles", f"{n}.json")])
young, old = st(cast["young"]), st(cast["old"])
age = cast["chapters"][book["chapter"]]["age"]
voice = Style((1 - age) * young.ttl + age * old.ttl, (1 - age) * young.dp + age * old.dp)


def say(text, lang, steps, speed):
    wav, dur = tts(text, lang, voice, steps, speed)
    return wav[0, : int(sr * dur[0].item())].astype(np.float32)


def sil(s):
    return np.zeros(int(s * sr), dtype=np.float32)


def save(path, audio, pitch):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as w:
        sf.write(w.name, np.concatenate([sil(0.15), audio, sil(0.25)]), sr)
        flt = f"asetrate={int(sr * pitch)},aresample={sr},atempo={1 / pitch:.5f}," if pitch != 1.0 else ""
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", w.name, "-af", flt + "aresample=32000",
                        "-ac", "1", "-c:a", "libmp3lame", "-b:a", "64k", path], check=True)


picks = [("c1-l1", 0), ("c1-l1", 3), ("c1-l3", 0)]
letters = {l["id"]: l for l in book["letters"]}
for lid, i in picks:
    L = letters[lid]
    pr = presets[L.get("mood") or cast["chapters"][book["chapter"]]["mood"]]
    m = L["messages"][i]
    for lang in ("fr", "en", "ko"):
        marked = m["speak"][lang]
        plain = re.sub(r"\s+", " ", re.sub(r"\s*/{1,2}\s*", " ", marked)).strip()
        commas = re.sub(r"\s*/\s*", " ", re.sub(r"(?<![,.;:!?…])\s*//\s*", ", ", marked).replace("//", " "))
        commas = re.sub(r"\s+", " ", commas).strip()
        # A
        parts = []
        for k, big in enumerate(re.split(r"\s*//\s*", marked)):
            small = [x for x in re.split(r"\s*/\s*", big) if x]
            for j, x in enumerate(small):
                parts += [say(x, lang, 16, pr["speed"]), sil((0.18 if j < len(small) - 1 else 0.42) * pr["pause"])]
        base = os.path.join(out, f"{lid}_m{i + 1}_{lang}")
        save(base + "_A.mp3", np.concatenate(parts[:-1]), pr["pitch"])
        save(base + "_B.mp3", say(plain, lang, 32, pr["speed"]), pr["pitch"])
        save(base + "_C.mp3", say(commas, lang, 32, pr["speed"]), pr["pitch"])
        save(base + "_D.mp3", say(commas, lang, 32, pr["speed"] * 0.92), pr["pitch"])
        json.dump({"plain": plain, "commas": commas}, open(base + ".json", "w", encoding="utf-8"), ensure_ascii=False)
        print("ok", lid, i, lang, flush=True)
