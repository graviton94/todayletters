"""
편지 낭독 만들기 (Supertonic 3, 버전 고정). 배역표 · 감정 프리셋 · 편지 데이터를 읽어
메시지마다 · 언어마다 파일 하나, 낱말마다 · 언어마다 파일 하나를 만든다.

  목소리: data/<인물>/voices.json 의 young · old 를 챕터 age 로 섞은 것 (세 언어 같은 비율)
  감정:   편지의 "mood" (없으면 챕터 기본값) → data/voices/presets.json 의 빠르기 · 높낮이 · 쉼 · 앞 숨
  끊어 읽기: 메시지의 "speak" 에서 긴 끊음("//", 쉼표 자리)마다 마디를 나눠, 마디는 한 번에 읽고(억양이 이어짐)
          마디 사이에는 긴 숨을 둔다. 짧은 끊음("/")은 이어 읽는다. 마디마다 시작 · 끝 시각을
          m<번호>_<언어>.json 에 적어, 앱이 따라 읽기에서 읽는 자리의 낱말을 칠한다.
  결과:   <출력>/<작품>/<챕터>/<편지 id>/m<번호>_<언어>.m4a (+ .json), w<번호>_<언어>.m4a
          32kHz 모노 AAC 48k (하루의 성경과 같은 결). 메시지 사이 쉼은 앱이 넣는다.

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


STEPS_FINE = 32      # 문장을 한 번에 읽을 때의 정밀도 (비교 샘플 B)
SLOWER = 0.92        # 조금 느리게 (비교 샘플 D)
BREATH = 0.62        # 쉼표 자리(“//”)의 숨, 초. 프리셋의 쉼 배율을 곱한다


def clauses(text):
    """ "a / b // c" → ["a b", "c"] : 긴 끊음(//)에서만 나누고, 짧은 끊음(/)은 이어 읽는다."""
    return [re.sub(r"\s*/\s*", " ", c).strip() for c in re.split(r"\s*//\s*", text.strip()) if c.strip()]


def speak(text, lang, pr, lead="", spoken=None):
    """
    사람처럼 읽기: 마디(쉼표 자리)마다 한 번에 읽어 억양이 이어지게 하고, 마디 사이에 긴 숨을 둔다.
    돌려주는 것: 소리와 마디 시각 [(시작, 끝, 글자)] (따라 읽기에서 읽는 자리를 칠할 때 쓴다).
    """
    chunks = clauses(spoken) if spoken else [text]
    parts, times, t = [silence(0.2)], [], 0.2
    for j, p in enumerate(chunks):
        said = f"{lead} {p}" if j == 0 and lead else p
        wav, dur = tts(said, lang, voice, STEPS_FINE, pr["speed"] * SLOWER)
        audio = wav[0, : int(sr * dur[0].item())].astype(np.float32)
        parts.append(audio)
        times.append((round(t, 3), round(t + len(audio) / sr, 3), p))
        t += len(audio) / sr
        pause = BREATH * pr["pause"] if j < len(chunks) - 1 else 0.3
        parts.append(silence(pause)); t += pause
    return np.concatenate(parts), times


def save(path, audio, pitch):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as w:
        sf.write(w.name, audio, sr)
        flt = f"asetrate={int(sr * pitch)},aresample={sr},atempo={1 / pitch:.5f}," if pitch != 1.0 else ""
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", w.name, "-af", flt + "aresample=32000",
                        "-ac", "1", "-c:a", "aac", "-b:a", "48k", path], check=True)


# NARRATE_ONLY=spots: 그림 속 산책 낱말만 만든다 (편지 낭독은 그대로 둠)
ONLY = os.environ.get("NARRATE_ONLY", "")
for letter in book["letters"]:
    pr = presets[letter.get("mood") or cast["chapters"][chapter]["mood"]]
    base = os.path.join(out, series, chapter, letter["id"])
    for lang in langs:
        # 그림 속 산책의 낱말 (s<k>): 낱말 발음과 같은 방식
        for k, sp in enumerate((letter.get("plate") or {}).get("spots", []), 1):
            w = sp.get("word") or {}
            if lang in w:
                save(os.path.join(base, f"s{k}_{lang}.m4a"), speak(w[lang], lang, presets["calm"])[0], 1.0)
        if ONLY == "spots":
            continue
        for i, m in enumerate(letter["messages"], 1):
            audio, times = speak(m[lang], lang, pr, pr["lead"] if i == 1 else "", (m.get("speak") or {}).get(lang))
            save(os.path.join(base, f"m{i}_{lang}.m4a"), audio, pr["pitch"])
            # 높낮이를 바꿔도 길이는 같다 (asetrate + atempo), 그래서 시각은 그대로 쓴다
            json.dump({"chunks": [{"s": a, "e": b, "text": x} for a, b, x in times]},
                      open(os.path.join(base, f"m{i}_{lang}.json"), "w", encoding="utf-8"), ensure_ascii=False)
        # 낱말 발음: 차분하게, 숨 없이
        for i, w in enumerate(letter.get("words", []), 1):
            if lang in w:
                save(os.path.join(base, f"w{i}_{lang}.m4a"), speak(w[lang], lang, presets["calm"])[0], 1.0)
    print("ok", letter["id"], flush=True)
