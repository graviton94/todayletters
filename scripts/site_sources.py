#!/usr/bin/env python3
"""사이트의 ‘출처와 라이선스’ 페이지를 앱 데이터에서 만든다.

    python3 scripts/site_sources.py [사이트 저장소]           # 쓰기
    python3 scripts/site_sources.py [사이트 저장소] --check   # 바뀔 게 있으면 실패

- 사이트 저장소 기본값: 앱 저장소 옆의 ../graviton94.github.io
- data/<작품>/series.json + 프롤로그 · 챕터 · 맺음말 → todayletters/index.html 의
  <!-- sources:ko:start --> … <!-- sources:ko:end --> 와
  <!-- sources:en:start --> … <!-- sources:en:end --> 사이만 다시 쓴다.
  표시 밖의 글(소개 · 라이선스 묶음 · 권리 문의 · 관계 없음)은 손으로 고친다.
- 작품마다: 편지 글 · 그림(이미지 하나에 한 줄) · 음악 · 갤러리 소장품 · 초상과 맺음말 그림.
- 작품 밖: 상점 그림(data/shop.json) · 메달 진열장(data/medals.json).
- 출처나 라이선스가 빠진 데이터는 지어내지 않고 표준 오류로 알린다 (페이지에는 빠진 채로).
"""
import glob, json, os, re, sys
from html import escape
from urllib.parse import urlparse

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data")
ORDER = ["vincent", "mozart", "napoleon"]  # 앱에 나온 차례. 나머지 작품은 이름순으로 뒤에

# 편지 글의 출처는 데이터에 문장으로 들어 있지 않아 여기 적는다 (작품을 더하면 함께 더할 것).
LETTERS = {
    "vincent": {
        "edition": "https://archive.org/details/brievenaanzijnbr03gogh",
        "ko": [
            "빈센트 반 고흐(1853–1890)의 편지 원문(프랑스어)은 공공 영역입니다.",
            "앱의 프랑스어 문장은 1914년판 『Brieven aan zijn broeder』(요 반 고흐-봉어 엮음, 1925년 사망 · 공공 영역)에서 옮겨 적었습니다. 인터넷 아카이브 스캔본: {link}",
            "악센트 · 철자만 지금 표기로 다듬었고, 편지에서 일부 문장을 골라 실었습니다.",
            "영어 · 한국어 번역, 낱말 풀이, 설명 글은 이 앱이 새로 쓴 것입니다. 다른 출판 번역이나 편집본의 글을 옮기지 않았습니다.",
        ],
        "en": [
            "Vincent van Gogh’s letters (1853–1890; in French) are in the public domain.",
            "The French text in the app is transcribed from <i>Brieven aan zijn broeder</i>, edited by J. van Gogh-Bonger (1914; she died in 1925; public domain). Internet Archive scan: {link}",
            "Accents and spelling are lightly modernised and sentences are excerpted.",
            "The English and Korean translations, glosses and notes are written for this app; no published translation or edition text is copied.",
        ],
    },
    "mozart": {
        "edition": "https://archive.org/details/mozartsbriefehe00mozagoog",
        "ko": [
            "볼프강 아마데우스 모차르트(1756–1791)의 편지 원문(독일어)은 공공 영역입니다.",
            "앱의 독일어 문장은 루트비히 놀(Ludwig Nohl) 엮음 『Mozarts Briefe』(잘츠부르크, 1865 · 공공 영역)에서 옮겨 적었습니다. 판의 철자와 문장부호를 그대로 두었고, 편지에서 일부 문장을 골라 실었습니다. 인터넷 아카이브 스캔본: {link}",
            "영어 문장은 레이디 월리스(Lady Wallace)가 놀의 판을 옮긴 영어 번역 『The Letters of Wolfgang Amadeus Mozart』(1866 · 공공 영역)에서 가져왔습니다.",
            "한국어 번역, 낱말 풀이, 설명 글은 이 앱이 새로 쓴 것입니다.",
        ],
        "en": [
            "Wolfgang Amadeus Mozart’s letters (1756–1791; in German) are in the public domain.",
            "The German text in the app is transcribed from <i>Mozarts Briefe</i>, edited by Ludwig Nohl (Salzburg, 1865; public domain), keeping the edition’s spelling and punctuation; sentences are excerpted. Internet Archive scan: {link}",
            "The English text is Lady Wallace’s translation of Nohl’s edition, <i>The Letters of Wolfgang Amadeus Mozart</i> (1866; public domain).",
            "The Korean translation, glosses and notes are written for this app.",
        ],
    },
    "napoleon": {
        "edition": "https://archive.org/details/lettresdenapolo01josegoog",
        "ko": [
            "나폴레옹 보나파르트(1769–1821)의 편지 원문(프랑스어)은 공공 영역입니다.",
            "앱의 프랑스어 문장은 공공 영역 판본에서 옮겨 적었습니다: 『Lettres de Napoléon à Joséphine』(파리, 피르맹 디도, 1833, 2권), 찰스 테넌트 『A Tour through parts of the Netherlands …』(런던, 1824, 부록), 『Correspondance de Napoléon Ier』(파리, 1858–1870), 레옹 르세스트르 엮음 『Lettres inédites de Napoléon Ier』(파리, 1897). 편지마다 판과 쪽을 앱에 적었습니다. 디도판 1권 인터넷 아카이브 스캔본: {link}",
            "편지에서 일부 문장을 골라 실었고, 성적인 문장과 전투 보고는 뺐습니다.",
            "영어 · 한국어 번역, 낱말 풀이, 설명 글은 이 앱이 새로 쓴 것입니다. 다른 출판 번역이나 현대 비평판의 글을 옮기지 않았습니다.",
        ],
        "en": [
            "Napoleon Bonaparte’s letters (1769–1821; in French) are in the public domain.",
            "The French text in the app is transcribed from public-domain editions: <i>Lettres de Napoléon à Joséphine</i> (Paris, Firmin Didot, 1833, 2 vols.), Charles Tennant, <i>A Tour through parts of the Netherlands …</i> (London, 1824, appendix), the <i>Correspondance de Napoléon Ier</i> (Paris, 1858–1870) and Léon Lecestre (ed.), <i>Lettres inédites de Napoléon Ier</i> (Paris, 1897); the app gives the edition and page for each letter. Internet Archive scan of Didot vol. I: {link}",
            "Sentences are excerpted; sexual passages and battle reports are left out.",
            "The English and Korean translations, glosses and notes are written for this app; no published translation or modern critical edition is copied.",
        ],
    },
}

SPELLING = {
    "ko": "편지 원문의 명백한 오자와 옛 철자는 하나하나 표시하지 않고 지금 표기로 고쳐 실었습니다 (모든 작품 공통. 모차르트의 독일어는 1865년 판의 철자를 그대로 두었습니다).",
    "en": "Obvious typos and old spellings in the original letters are corrected to modern spelling without marking each change (all series; the Mozart German keeps the 1865 edition’s spelling).",
}

T = {
    "ko": {
        "letters": "편지 글", "plates": "그림", "music": "음악", "gallery": "갤러리 소장품",
        "portrait": "초상 · 맺음말 그림", "portrait_l": "초상", "outro_l": "맺음말 그림",
        "era": "시대의 그림", "sketch": "편지 속 스케치", "pdart": "PD-Art (공공 영역 평면 미술품의 충실한 복제 사진)",
        "perf": "연주:", "pd": "공공 영역",
        "plates_note": "편지마다 붙은 그림과, 편지 사이에 끼워 둔 사진 · 스케치입니다. 같은 이미지는 한 번만 적습니다.",
        "music_note": "60초 발췌 · 페이드 · 음량 맞춤 (원본에서 바꾼 점)",
        "music_sa": "발췌본도 같은 라이선스({lic})로 공유합니다",
        "gallery_note": "학습을 마칠 때 갤러리에 걸리는 작품들입니다.",
        "count": "{n}점",
        "vocab": "낱말 자료",
        "vocab_freq": "낱말 등급(A1–C2): Hermit Dave 의 {fw} (OpenSubtitles 2018 빈도 목록) 순위에서 계산 · CC BY-SA 4.0",
        "vocab_tatoeba": "예문 {n}개: {tt} · CC BY 2.0 FR · 작성자 {a}명 (앱에서 문장마다 작성자를 표기)",
        "shop": "상점 그림", "shop_note": "상점 줄의 작은 그림입니다. 모든 작품이 같은 그림을 쓰고, 원본의 정사각 부분만 작게 오려 냈습니다.",
        "medals": "메달 진열장", "medals_note": "고대 그리스 · 로마 동전과 붉은 그림 잔의 둥근 그림(톤도)을 오려 낸 메달입니다. 원본 사진은 모두 소장처의 공개 이미지(CC0)입니다.",
    },
    "en": {
        "letters": "Letter text", "plates": "Pictures", "music": "Music", "gallery": "Gallery pieces",
        "portrait": "Portrait · epilogue picture", "portrait_l": "Portrait", "outro_l": "Epilogue picture",
        "era": "art of the time", "sketch": "sketch in the letter", "pdart": "PD-Art (faithful reproduction of a public-domain 2D artwork)",
        "perf": "performed by", "pd": "public domain",
        "plates_note": "The picture on each letter, and the photos and sketches placed between messages. Each image is listed once.",
        "music_note": "60-second excerpt, faded and level-matched (changes from the original)",
        "music_sa": "the excerpt is shared under the same licence ({lic})",
        "gallery_note": "Works hung in the gallery as you finish lessons.",
        "count": "{n}",
        "vocab": "Vocabulary",
        "vocab_freq": "Word levels (A1–C2) computed from the ranks in {fw} by Hermit Dave (OpenSubtitles 2018 frequency lists) · CC BY-SA 4.0",
        "vocab_tatoeba": "{n} example sentences from {tt} · CC BY 2.0 FR · by {a} authors (the app credits the author with each sentence)",
        "shop": "Shop pictures", "shop_note": "The small pictures on the shop rows, shared by every series; each is a small square detail of the original.",
        "medals": "Medal cabinet", "medals_note": "Medals cut out from ancient Greek and Roman coins and the round interior paintings (tondi) of red-figure cups. Every source photo is an open-access image (CC0) from the holding museum.",
    },
}

LICENCE = re.compile(r"^(cc0|cc[ -]by.*|public domain.*|domaine public|open access.*)$", re.I)
WARN = []


def warn(msg):
    WARN.append(msg)


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def series_ids():
    # 출시 전 작품 ("hidden": true) 은 사이트에도 아직 싣지 않는다
    have = [os.path.basename(os.path.dirname(p)) for p in glob.glob(os.path.join(DATA, "*", "series.json")) if not load(p).get("hidden")]
    return [s for s in ORDER if s in have] + sorted(s for s in have if s not in ORDER)


def letters(sid, meta):
    """프롤로그 → 챕터 → 맺음말 차례로 편지를 내놓는다."""
    files = []
    if meta.get("prologue", {}).get("file"):
        files.append(meta["prologue"]["file"])
    files += [c["file"] for c in meta.get("chapters", [])]
    if meta.get("outro", {}).get("file"):
        files.append(meta["outro"]["file"])
    for name in files:
        path = os.path.join(DATA, sid, name)
        if not os.path.exists(path):
            warn(f"{sid}: {name} 없음")
            continue
        for L in load(path).get("letters", []):
            yield L


def split_holder(text, fallback=""):
    """‘소장처 · CC0’ 같은 글을 소장처와 라이선스로 나눈다."""
    segs = [s.strip() for s in (text or "").split(" · ") if s.strip()]
    lic = [s for s in segs if LICENCE.match(s)]
    rest = [s for s in segs if not LICENCE.match(s) and not s.startswith("http")]
    return " · ".join(rest), (lic[0] if lic else fallback)


def url_in(text):
    m = re.search(r"https?://\S+", text or "")
    return m.group(0) if m else ""


def host(url):
    h = urlparse(url).netloc.removeprefix("www.").removeprefix("collections.")
    return "Wikimedia Commons" if h == "commons.wikimedia.org" else h


def ref_of(L):
    """편지 ref 의 첫 마디 (‘Brieven 1914, n° 463’) 를 판 이름과 번호로."""
    first = (L.get("ref") or "").split(" · ")[0].strip()
    m = re.match(r"^(.*?)(n°|Nr\.)\s*(\S+)$", first)
    return (m.group(1) + m.group(2), m.group(3)) if m else (first, "")


def refs_text(refs):
    out, by = [], {}
    for pre, no in refs:
        by.setdefault(pre, [])
        if no and no not in by[pre]:
            by[pre].append(no)
    for pre, nos in by.items():
        out.append(f"{pre} {', '.join(nos)}" if nos else pre)
    return "; ".join(out)


def sa(lic):
    return "BY-SA" in (lic or "")


# ── 모으기 ───────────────────────────────────────────────────────────────

def collect(sid):
    meta = load(os.path.join(DATA, sid, "series.json"))
    orig = meta.get("original", "en")
    edition = LETTERS.get(sid, {}).get("edition", "")
    plates, by_image, by_source = [], {}, {}
    music, by_audio = [], {}

    def add(entry, L):
        key_img = entry["image"]
        key_src = (entry["source"], entry["title"].get("en", "")) if entry["source"] else None
        hit = by_image.get(key_img) or (by_source.get(key_src) if key_src else None)
        if hit is None:
            plates.append(entry)
            hit = entry
            by_image[key_img] = hit
            if key_src:
                by_source[key_src] = hit
        else:
            by_image.setdefault(key_img, hit)
        r = ref_of(L)
        if r not in hit["refs"]:
            hit["refs"].append(r)

    for L in letters(sid, meta):
        lid = f"{sid}:{L['id']}"
        p = L.get("plate") or {}
        if p.get("image"):
            holder, lic = split_holder(p.get("collection"), p.get("imageLicense", ""))
            e = {"image": p["image"], "title": {k[6:]: v for k, v in p.items() if k.startswith("title_")},
                 "date": p.get("date", ""), "holder": holder, "licence": lic, "credit": p.get("credit", ""),
                 "source": p.get("imageSource") or p.get("source") or "", "kind": p.get("kind", ""),
                 "pdart": p.get("exception") == "pd-art", "sketch": False, "refs": []}
            if not e["licence"]:
                warn(f"{lid}: 그림 라이선스 없음 (plate.imageLicense)")
            if not e["source"]:
                warn(f"{lid}: 그림 출처 주소 없음 (plate.imageSource)")
            add(e, L)
        for m in L.get("moments") or []:
            kind = m.get("type")
            if kind in ("photo", "sketch") and m.get("image") and m["image"] != p.get("image"):
                credit = m.get("credit", "")
                if not credit and not m.get("imageSource"):
                    warn(f"{lid}: {kind} 모먼트 {m['image']} 출처 · 라이선스 없음 (credit) — 페이지에서 뺌")
                    continue
                holder, lic = split_holder(credit, m.get("imageLicense", ""))
                src = m.get("imageSource") or url_in(credit)
                from_edition = "Brieven aan zijn broeder (1914)" in credit
                # 1914년판 쪽 사진은 판 자체가 공공 영역이므로 판의 스캔본으로 잇는다
                if from_edition and not lic:
                    lic = "public domain"
                if from_edition and not src:
                    src = edition
                if not lic:
                    warn(f"{lid}: {kind} 모먼트 {m['image']} 라이선스 없음")
                cap = m.get("caption") or {}
                e = {"image": m["image"], "title": cap, "date": "", "holder": holder, "licence": lic,
                     "credit": "", "source": src, "kind": "", "pdart": m.get("exception") == "pd-art",
                     "sketch": kind == "sketch", "refs": []}
                add(e, L)
            if kind == "music" and m.get("audio"):
                if m["audio"] in by_audio:
                    continue
                by_audio[m["audio"]] = True
                for f in ("performer", "audioLicense", "audioSource"):
                    if not m.get(f):
                        warn(f"{lid}: 음악 {m['audio']} {f} 없음")
                music.append({"piece": m.get("piece") or {}, "performer": m.get("performer", ""),
                              "licence": m.get("audioLicense", ""), "source": m.get("audioSource", ""),
                              "file": (m.get("commons") or "").removeprefix("File:"),
                              "excerpt": m.get("excerpt", ""), "ref": ref_of(L)})

    gallery = []
    for g in meta.get("kit", {}).get("collection", []):
        holder, lic = split_holder(g.get("collection"))
        page = re.search(r"p\.\s*(\S+)$", g.get("collection", ""))
        src = g.get("source", "")
        gid = g.get("id", "")
        if not src:  # 소장처 번호가 id 에 들어 있는 것만 소장처 페이지로 잇는다
            if gid.startswith("met-"):
                src = f"https://www.metmuseum.org/art/collection/search/{gid[4:]}"
            elif gid.startswith("aic-"):
                src = f"https://www.artic.edu/artworks/{gid[4:]}"
            elif gid.startswith("brieven-"):
                src = edition
        pdart = g.get("exception") == "pd-art"
        if not lic:
            warn(f"{sid}: 갤러리 {gid} 라이선스 없음" + (" (PD-Art 예외라 ‘공공 영역’으로 적음)" if pdart else ""))
            if pdart:
                lic = "Public domain"
        if not src:
            warn(f"{sid}: 갤러리 {gid} 출처 주소 없음")
        gallery.append({"title": g.get("title") or {}, "date": g.get("date", ""), "holder": re.sub(r"\s*·?\s*p\.\s*\S+$", "", holder),
                        "page": page.group(1) if page else "", "licence": lic, "source": src, "pdart": pdart})

    portrait = meta.get("credit", "")
    if meta.get("portrait") and not portrait:
        warn(f"{sid}: series.json credit (초상 출처) 없음")
    outro = meta.get("outro") or {}
    if outro.get("image") and not outro.get("credit"):
        warn(f"{sid}: outro.credit (맺음말 그림 {outro['image']} 출처) 없음 — 페이지에서 뺌")
    # 낱말 자료 (data/<작품>/lexicon.json): Tatoeba 예문 수 · 작성자 수
    vocab = None
    lp = os.path.join(DATA, sid, "lexicon.json")
    if os.path.exists(lp):
        lx = load(lp)
        ix = lx["fields"].index("example")
        ex = {r[ix][0]: r[ix][4] for r in lx["entries"] if len(r) > ix and r[ix] and r[ix][4] != "app"}  # "app": 앱이 쓴 예문 (Tatoeba 아님)
        vocab = {"examples": len(ex), "authors": len(set(ex.values()))}
    return {"id": sid, "meta": meta, "orig": orig, "plates": plates, "music": music, "gallery": gallery,
            "portrait": portrait, "outro": outro.get("credit", ""), "vocab": vocab}


# ── 그리기 ───────────────────────────────────────────────────────────────

def link(url, text=None):
    if not url:
        return ""
    return f'<a href="{escape(url)}">{escape(text or host(url))}</a>'


def plate_li(e, orig, lang):
    t = T[lang]
    o = e["title"].get(orig) or e["title"].get("en") or e["title"].get("ko", "")
    other = e["title"].get(lang, "")
    head = [f"<b>{escape(o)}</b>"]
    if other and other != o:
        head.append(escape(other))
    if e["date"]:
        head.append(escape(e["date"]))
    if e["kind"] == "era":
        head.append(t["era"])
    if e["sketch"] and not re.search("스케치|sketch", o + other, re.I):
        head.append(t["sketch"])
    soft = []
    if e["credit"] and e["credit"] not in e["holder"]:
        soft.append(escape(e["credit"]))
    if e["holder"]:
        soft.append(escape(e["holder"]))
    if e["licence"]:
        soft.append(escape(t["pd"] if e["licence"].lower() in ("public domain", "domaine public") else e["licence"]))
    if e["pdart"]:
        soft.append(t["pdart"])
    r = refs_text(e["refs"])
    if r:
        soft.append(escape(r))
    if e["source"]:
        soft.append(link(e["source"]))
    return f'<li>{" · ".join(head)}<br><span class="soft">{" · ".join(soft)}</span></li>'


def music_li(m, lang):
    t = T[lang]
    piece = m["piece"].get(lang) or m["piece"].get("en", "")
    soft = []
    if m["performer"]:
        soft.append(f'{t["perf"]} {escape(m["performer"])}')
    if m["licence"]:
        soft.append(escape(m["licence"]))
    if sa(m["licence"]):
        soft.append(t["music_sa"].format(lic=escape(m["licence"])))
    if m["excerpt"] and m["excerpt"] != "60 s excerpt, faded and level-matched":
        soft.append(escape(m["excerpt"]))
    r = refs_text([m["ref"]])
    if r:
        soft.append(escape(r))
    if m["source"]:
        soft.append(link(m["source"], m["file"] or None))
    return f'<li><b>{escape(piece)}</b><br><span class="soft">{" · ".join(soft)}</span></li>'


def gallery_li(g, lang):
    t = T[lang]
    head = [f'<b>{escape(g["title"].get("en", ""))}</b>']
    if lang == "ko" and g["title"].get("ko"):
        head.append(escape(g["title"]["ko"]))
    if g["date"]:
        head.append(escape(g["date"]))
    soft = [escape(g["holder"])] if g["holder"] else []
    if g["page"]:
        soft.append(escape(("p. " if g["page"][0].isdigit() else "") + g["page"]))
    if g["licence"]:
        soft.append(escape(t["pd"] if g["licence"].lower() == "public domain" else g["licence"]))
    if g["pdart"]:
        soft.append(t["pdart"])
    if g["source"]:
        soft.append(link(g["source"]))
    return f'<li>{" · ".join(head)}<br><span class="soft">{" · ".join(soft)}</span></li>'


def block(S, lang):
    t, meta = T[lang], S["meta"]
    title = (meta.get("title") or {}).get(lang) or meta.get("fullName", S["id"])
    out = [f'<h2 id="{S["id"]}-{lang}">{escape(title)} · {escape(meta.get("fullName", ""))} ({escape(meta.get("years", ""))})</h2>']
    text = LETTERS.get(S["id"], {})
    if text.get(lang):
        out.append(f'<h3>{t["letters"]}</h3>')
        out.append("<ul>")
        for line in text[lang]:
            out.append("<li>" + line.format(link=link(text["edition"], text["edition"].removeprefix("https://"))) + "</li>")
        out.append("</ul>")
    else:
        warn(f"{S['id']}: scripts/site_sources.py LETTERS 에 편지 글 출처가 없음")
    if S["plates"]:
        out.append(f'<h3>{t["plates"]} ({t["count"].format(n=len(S["plates"]))})</h3>')
        out.append(f'<p class="soft">{t["plates_note"]}</p>')
        out.append("<ul>")
        out += [plate_li(e, S["orig"], lang) for e in S["plates"]]
        out.append("</ul>")
    if S["music"]:
        out.append(f'<h3>{t["music"]} ({t["count"].format(n=len(S["music"]))})</h3>')
        out.append(f'<p class="soft">{t["music_note"]}</p>')
        out.append("<ul>")
        out += [music_li(m, lang) for m in S["music"]]
        out.append("</ul>")
    if S["gallery"]:
        out.append(f'<h3>{t["gallery"]} ({t["count"].format(n=len(S["gallery"]))})</h3>')
        out.append(f'<p class="soft">{t["gallery_note"]}</p>')
        out.append("<ul>")
        out += [gallery_li(g, lang) for g in S["gallery"]]
        out.append("</ul>")
    if S.get("vocab"):
        v = S["vocab"]
        out.append(f'<h3>{t["vocab"]}</h3>')
        out.append("<ul>")
        out.append("<li>" + t["vocab_freq"].format(fw=link("https://github.com/hermitdave/FrequencyWords", "FrequencyWords")) + "</li>")
        out.append("<li>" + t["vocab_tatoeba"].format(n=v["examples"], a=v["authors"], tt=link("https://tatoeba.org", "Tatoeba")) + "</li>")
        out.append("</ul>")
    lines = []
    if S["portrait"]:
        lines.append(f'<li>{t["portrait_l"]}: {escape(S["portrait"])}</li>')
    if S["outro"]:
        lines.append(f'<li>{t["outro_l"]}: {escape(S["outro"])}</li>')
    if lines:
        out.append(f'<h3>{t["portrait"]}</h3>')
        out += ["<ul>"] + lines + ["</ul>"]
    return out


def medals_block(lang):
    """data/medals.json 의 메달 그림 출처: 소장처 · 작품 하나에 한 줄."""
    path = os.path.join(DATA, "medals.json")
    if not os.path.exists(path):
        return []
    doc = load(path)
    t = T[lang]
    seen, items = set(), []
    for m in doc.get("medals", []) + [doc.get("seriesSpare") or {}]:
        src = m.get("source") or {}
        key = (src.get("museum"), src.get("objectID"))
        if not src or key in seen:
            continue
        seen.add(key)
        for f in ("museum", "objectID", "license", "url"):
            if not src.get(f):
                warn(f"medals: {m.get('id', 'seriesSpare')} source.{f} 없음")
        head = [f"<b>{escape(src.get('title', ''))}</b>"]
        if src.get("artist"):
            head.append(escape(src["artist"]))
        if src.get("date"):
            head.append(escape(src["date"]))
        if m.get("title"):
            head.append(escape(m["title"].get(lang, "")))
        soft = [escape(src.get("museum", "")), escape(str(src.get("objectID", ""))), escape(src.get("license", "")), link(src.get("url", ""))]
        items.append(f'<li>{" · ".join(head)}<br><span class="soft">{" · ".join(x for x in soft if x)}</span></li>')
    if not items:
        return []
    return [f'<h2 id="medals-{lang}">{t["medals"]} ({t["count"].format(n=len(items))})</h2>',
            f'<p class="soft">{t["medals_note"]}</p>', "<ul>"] + items + ["</ul>"]


def shop_block(lang):
    """data/shop.json 의 상점 그림 출처: 그림 하나에 한 줄."""
    path = os.path.join(DATA, "shop.json")
    if not os.path.exists(path):
        return []
    t = T[lang]
    items = []
    for pc in load(path).get("pictures", []):
        for f in ("museum", "objectID", "license", "url"):
            if not pc.get(f):
                warn(f"shop: {pc.get('image', '?')} {f} 없음")
        head = [f"<b>{escape((pc.get('title') or {}).get(lang, ''))}</b>"] + [escape(pc[k]) for k in ("artist", "date") if pc.get(k)]
        soft = [escape(pc.get("museum", "")), escape(pc.get("objectID", "")), escape(pc.get("license", "")),
                escape((pc.get("detail") or {}).get(lang, "")), link(pc.get("url", ""))]
        items.append(f'<li>{" · ".join(head)}<br><span class="soft">{" · ".join(x for x in soft if x)}</span></li>')
    if not items:
        return []
    return [f'<h2 id="shop-{lang}">{t["shop"]} ({t["count"].format(n=len(items))})</h2>',
            f'<p class="soft">{t["shop_note"]}</p>', "<ul>"] + items + ["</ul>"]


def render(all_series, lang):
    head = f"<!-- 자동 생성: scripts/site_sources.py (앱 저장소 data/). 이 표시 사이는 손으로 고치지 말 것. -->"
    body = [head, f'<p class="soft">{SPELLING[lang]}</p>']
    for S in all_series:
        body += block(S, lang)
    body += shop_block(lang)
    body += medals_block(lang)
    return "\n".join(body)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    check = "--check" in sys.argv
    site = os.path.abspath(args[0]) if args else os.path.join(os.path.dirname(ROOT), "graviton94.github.io")
    page = os.path.join(site, "todayletters", "index.html")
    if not os.path.exists(page):
        sys.exit(f"사이트 페이지가 없어요: {page}")
    html = open(page, encoding="utf-8").read()
    all_series = [collect(s) for s in series_ids()]
    new = html
    for lang in ("ko", "en"):
        start, end = f"<!-- sources:{lang}:start -->", f"<!-- sources:{lang}:end -->"
        if new.count(start) != 1 or new.count(end) != 1 or new.index(start) > new.index(end):
            sys.exit(f"{page}: {start} … {end} 표시가 한 쌍 있어야 해요")
        a, b = new.index(start) + len(start), new.index(end)
        new = new[:a] + "\n" + render(all_series, lang) + "\n" + new[b:]
    for w in dict.fromkeys(WARN):
        print("확인 필요:", w, file=sys.stderr)
    for S in all_series:
        print(f"{S['id']}: 그림 {len(S['plates'])} · 음악 {len(S['music'])} · 갤러리 {len(S['gallery'])}")
    if new == html:
        print("바뀐 것 없음")
        return
    if check:
        sys.exit(f"{page} 가 데이터와 달라요: python3 scripts/site_sources.py 로 다시 만들 것")
    with open(page, "w", encoding="utf-8") as f:
        f.write(new)
    print("씀:", page)


if __name__ == "__main__":
    main()
