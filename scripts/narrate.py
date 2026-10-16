"""
편지 낭독 만들기 (Supertonic 3, 버전 고정). 배역표 · 감정 프리셋 · 편지 데이터를 읽어
메시지마다 · 언어마다 파일 하나, 낱말마다 · 언어마다 파일 하나를 만든다.

  목소리: data/<인물>/voices.json 의 young · old 를 챕터 age 로 섞은 것 (세 언어 같은 비율)
  감정:   편지의 "mood" (없으면 챕터 기본값) → data/voices/presets.json 의 빠르기 · 높낮이 · 쉼 · 앞 숨
  끊어 읽기: 메시지의 "speak" 에서 긴 끊음("//", 쉼표 자리)마다 마디를 나눠, 마디는 한 번에 읽고(억양이 이어짐)
          마디 사이에는 긴 숨을 둔다. 짧은 끊음("/")은 이어 읽는다. 마디마다 시작 · 끝 시각을
          m<번호>_<언어>.json 에 적어, 앱이 따라 읽기에서 읽는 자리의 낱말을 칠한다.
  결과:   <출력>/<작품>/<챕터>/<편지 id>/m<번호>_<언어>.m4a (+ .json), w<번호>_<언어>.m4a
          32kHz 모노 AAC 32k. 메시지 사이 쉼은 앱이 넣는다.
  NARRATE_ONLY=vocab: 오늘의 낱말 · 표현만 (data/<작품>/daily.json · daily_en.json · daily_ko.json, 학습 언어마다)
          v_<언어>_<slug>.m4a  오늘의 낱말 (그 언어의 표제어를 읽음, 사전 키의 "#2" 같은 동형어 표시는 읽지 않음)
          ph<번호>_<언어>.m4a  오늘의 표현 (phrases 의 t, 번호는 그 언어 phrases 목록에서 1부터)
          차분한(calm) 프리셋, 높낮이 그대로. 편지 낭독 · 낱말 · 그림 점은 만들지 않는다.
  NARRATE_ONLY=talk: 오늘의 회화만 (편지의 "talk".lines, 학습 언어마다)
          <talk.audio 또는 t><줄 번호>_<언어>.m4a  (core Talk.audioName: 줄 i 의 소리 = <audio><i+1>_<언어>;
          "audio" 가 {"*": "t", "ko": "tk"} 면 그 언어는 그 앞부분, 없는 언어는 "*")
          "them" 줄은 그 장의 인물 목소리(편지 낭독과 같은 섞음) · 편지 mood 의 빠르기 · 높낮이,
          "me" 줄은 받는 사람 목소리 (배역표의 "talk_me": 편지 "to" 의 원어 글자 → 목소리, 없으면 "default") · 차분한 프리셋.
  NARRATE_ONLY=new: talk + vocab 을 한 번에.
  NARRATE_ONLY=messages: NARRATE_MESSAGES 에 적은 메시지 낭독만 다시 (m<번호>_<언어>.m4a + .json). 띄어쓰기로 나눈
          "<작품>/<편지 id>/m<번호>_<언어>" 목록 (예: "mozart/c1-l2/m4_en"), 이 편지 파일에 없는 것은 건너뛴다.
  NARRATE_SKIP_EXISTING=1: 이 저장소의 assets/audio · remote/audio 에 이미 있는 파일은 만들지 않는다
          (R2 의 파일은 덮어쓰지 않는다 · docs/release.md 5: 빠진 소리만 새로 만든다).

GitHub Actions 에서 돌려요:
  python scripts/narrate.py <supertonic/py> <assets> <편지 파일.json> <출력 폴더> [언어 …]
"""
import json, os, re, subprocess, sys, tempfile
import os as _os, sys as _sys; _sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
from abbrev import split_keep  # 줄임말 뒤에서는 끊지 않는다 (St. · Mr. · Hr. …)
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


def along(path, age):
    """목소리 길 [젊은 결, …, 나이 든 결] 위의 age(0~1) 자리: 이웃한 두 결을 섞는다."""
    styles = [style_of(n) for n in path]
    if len(styles) == 1:
        return styles[0]
    x = min(max(age, 0.0), 1.0) * (len(styles) - 1)
    i = min(int(x), len(styles) - 2)
    k = x - i
    a, b = styles[i], styles[i + 1]
    return Style((1 - k) * a.ttl + k * b.ttl, (1 - k) * a.dp + k * b.dp)


# 배역표의 "path" (셋 이상의 결) 가 있으면 그 길을 따라, 없으면 young → old 두 결
age = cast["chapters"][chapter]["age"]
voice = along(cast.get("path") or [cast["young"], cast["old"]], age)


def phrases(text: str, lang: str):
    cut = r"(?<=[,;:.?!…])\s+" if lang != "ko" else r"(?<=[,.?!…])\s+"
    res = []
    for p in split_keep(cut, text.strip()):
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


def speak(text, lang, pr, lead="", spoken=None, who=None):
    """
    사람처럼 읽기: 마디(쉼표 자리)마다 한 번에 읽어 억양이 이어지게 하고, 마디 사이에 긴 숨을 둔다.
    돌려주는 것: 소리와 마디 시각 [(시작, 끝, 글자)] (따라 읽기에서 읽는 자리를 칠할 때 쓴다).
    """
    chunks = clauses(spoken) if spoken else [text]
    parts, times, t = [silence(0.2)], [], 0.2
    for j, p in enumerate(chunks):
        said = f"{lead} {p}" if j == 0 and lead else p
        wav, dur = tts(said, lang, who or voice, STEPS_FINE, pr["speed"] * SLOWER)
        audio = wav[0, : int(sr * dur[0].item())].astype(np.float32)
        parts.append(audio)
        times.append((round(t, 3), round(t + len(audio) / sr, 3), p))
        t += len(audio) / sr
        pause = BREATH * pr["pause"] if j < len(chunks) - 1 else 0.3
        parts.append(silence(pause)); t += pause
    return np.concatenate(parts), times


def slug(key):
    """사전 키 → 파일 이름 조각. 영어 소문자 a-z · 숫자 0-9 · '-' 는 그대로, 그 밖의 글자(대문자 · 악센트 · 한글 · '#' 등)는
    _<유니코드 코드포인트 16진 소문자>_ 로 바꾼다. 대소문자가 다른 키(Leben / leben)도 겹치지 않고, 어느 파일 시스템 · URL 에서도 안전하다.
      Leben → _4c_eben · côté → c_f4_t_e9_ · ton#2 → ton_23_2 · 누나 → _b204__b098_ · avant-hier → avant-hier"""
    return "".join(c if ("a" <= c <= "z" or "0" <= c <= "9" or c == "-") else f"_{ord(c):x}_" for c in key)


def save(path, audio, pitch):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as w:
        sf.write(w.name, audio, sr)
        flt = f"asetrate={int(sr * pitch)},aresample={sr},atempo={1 / pitch:.5f}," if pitch != 1.0 else ""
        # AAC 32k 모노 32kHz (.m4a): 앱이 MediaPlayer 로 마디만 골라 듣고(seekTo) 따라 읽기 채점이 MediaCodec 으로 원음을 푼다.
        # Opus(.ogg) 는 쪽(page) 단위로만 찾아가서 마디의 시작이 어긋날 수 있어 쓰지 않는다.
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", w.name, "-af", flt + "aresample=32000",
                        "-ac", "1", "-c:a", "aac", "-b:a", "32k", path], check=True)


# NARRATE_ONLY=spots: 그림 속 산책 낱말만 만든다 (편지 낭독은 그대로 둠)
ONLY = os.environ.get("NARRATE_ONLY", "")
SKIP = os.environ.get("NARRATE_SKIP_EXISTING", "") == "1"


def exists(rel):
    """<작품>/<장>/<편지>/<파일> 이 이미 이 저장소(앱 안 assets 또는 앱 밖 remote/)에 있나."""
    return any(os.path.exists(os.path.join(r, rel)) for r in ("android/app/src/main/assets/audio", "remote/audio"))


def wanted(path):
    return not (SKIP and exists(os.path.relpath(path, out)))


def talk_stem(t, lang):
    """core Talk.audioName 과 같은 규칙: "audio" 가 글자면 모든 언어, {"*": "t", "ko": "tk"} 면 언어마다 (없는 언어는 "*")."""
    a = t.get("audio")
    if isinstance(a, dict):
        return a.get(lang) or a.get("*") or a.get("default") or "t"
    return a or "t"


if ONLY in ("talk", "new"):
    me_cast = cast.get("talk_me") or {}
    n = 0
    for letter in book["letters"]:
        t = letter.get("talk") or {}
        if not t.get("lines"):
            continue
        base = os.path.join(out, series, chapter, letter["id"])
        pr = presets[letter.get("mood") or cast["chapters"][chapter]["mood"]]
        to = (letter.get("to") or {}).get(book["original"]) if isinstance(letter.get("to"), dict) else letter.get("to")
        me = style_of((me_cast.get("by_to") or {}).get(to or "", me_cast.get("default", "M4")))
        for lang in langs:
            stem = talk_stem(t, lang)
            for i, line in enumerate(t["lines"], 1):
                path = os.path.join(base, f"{stem}{i}_{lang}.m4a")
                text = re.sub(r"[〈〉]", "", line.get(lang) or "").strip()
                if not text or not wanted(path):
                    continue
                if line.get("who") == "me":
                    save(path, speak(text, lang, presets["calm"], who=me)[0], 1.0)
                else:
                    save(path, speak(text, lang, pr)[0], pr["pitch"])
                n += 1
        print("talk ok", letter["id"], flush=True)
    print("talk files", n)
    if ONLY == "talk":
        sys.exit(0)
if ONLY in ("vocab", "new"):
    daily = {lg: json.load(open(f"data/{series}/daily{'' if lg == book['original'] else '_' + lg}.json",
                                encoding="utf-8"))["letters"] for lg in langs}
    n = 0
    for letter in book["letters"]:
        base = os.path.join(out, series, chapter, letter["id"])
        for lang in langs:
            d = daily[lang].get(letter["id"]) or {}
            for key in d.get("words", []):
                path = os.path.join(base, f"v_{lang}_{slug(key)}.m4a")
                if wanted(path):
                    save(path, speak(key.split("#")[0], lang, presets["calm"])[0], 1.0); n += 1
            for i, ph in enumerate(d.get("phrases", []), 1):
                path = os.path.join(base, f"ph{i}_{lang}.m4a")
                if wanted(path):
                    save(path, speak(ph["t"], lang, presets["calm"])[0], 1.0); n += 1
        print("ok", letter["id"], flush=True)
    print("vocab files", n)
    sys.exit(0)
PICK = {tuple(x.split("/")) for x in os.environ.get("NARRATE_MESSAGES", "").split()} if ONLY == "messages" else None
for letter in book["letters"]:
    if PICK is not None and not any(s == series and lid == letter["id"] for s, lid, _ in PICK):
        continue
    pr = presets[letter.get("mood") or cast["chapters"][chapter]["mood"]]
    base = os.path.join(out, series, chapter, letter["id"])
    for lang in langs:
        # 그림 속 산책의 낱말 (s<k>): 낱말 발음과 같은 방식
        for k, sp in enumerate([] if PICK is not None else (letter.get("plate") or {}).get("spots", []), 1):
            w = sp.get("word") or {}
            if lang in w:
                save(os.path.join(base, f"s{k}_{lang}.m4a"), speak(w[lang], lang, presets["calm"])[0], 1.0)
        if ONLY == "spots":
            continue
        for i, m in enumerate(letter["messages"], 1):
            if PICK is not None and (series, letter["id"], f"m{i}_{lang}") not in PICK:
                continue
            audio, times = speak(m[lang], lang, pr, pr["lead"] if i == 1 else "", (m.get("speak") or {}).get(lang))
            save(os.path.join(base, f"m{i}_{lang}.m4a"), audio, pr["pitch"])
            # 높낮이를 바꿔도 길이는 같다 (asetrate + atempo), 그래서 시각은 그대로 쓴다
            json.dump({"chunks": [{"s": a, "e": b, "text": x} for a, b, x in times]},
                      open(os.path.join(base, f"m{i}_{lang}.json"), "w", encoding="utf-8"), ensure_ascii=False)
        # 낱말 발음: 차분하게, 숨 없이
        for i, w in enumerate([] if PICK is not None else letter.get("words", []), 1):
            if lang in w:
                save(os.path.join(base, f"w{i}_{lang}.m4a"), speak(w[lang], lang, presets["calm"])[0], 1.0)
    print("ok", letter["id"], flush=True)
