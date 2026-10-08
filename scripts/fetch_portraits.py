"""Wikimedia Commons 에서 인물 초상 후보를 찾아 작은 이미지와 라이선스 정보를 받는다 (시안·검토용).

사용: python3 scripts/fetch_portraits.py <out> [검색어 파일.json] [폭]
  검색어 파일: {"이름": ["검색어", …]} (없으면 아래 인물 초상)
"""
import json, os, sys, urllib.parse, urllib.request

UA = {"User-Agent": "TodayLetters/0.1 (https://github.com/graviton94/todayletters)"}
QUERIES = {
    "vincent": ["Van Gogh self-portrait 1887 Art Institute Chicago", "Van Gogh self-portrait 1889 Musee d'Orsay"],
    "mozart": ["Mozart Joseph Lange 1782 portrait", "Mozart Barbara Krafft 1819"],
    "napoleon": ["Napoleon in His Study at the Tuileries David", "Bonaparte at the Pont d'Arcole Gros"],
    "austen": ["Jane Austen Cassandra Austen sketch National Portrait Gallery"],
    "basho": ["Matsuo Basho portrait painting", "Basho Buson portrait"],
}

def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=40) as r:
        return r.read()

WIDTH = int(sys.argv[3]) if len(sys.argv) > 3 else 600
if len(sys.argv) > 2:
    QUERIES = json.load(open(sys.argv[2], encoding="utf-8"))


def search(q):
    p = {"action": "query", "format": "json", "generator": "search", "gsrnamespace": 6, "gsrsearch": q,
         "gsrlimit": 4, "prop": "imageinfo", "iiprop": "url|extmetadata|mime|size", "iiurlwidth": WIDTH}
    d = json.loads(get("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(p)))
    pages = sorted(d.get("query", {}).get("pages", {}).values(), key=lambda x: x.get("index", 0))
    return pages

out = sys.argv[1]
for who, qs in QUERIES.items():
    os.makedirs(f"{out}/{who}", exist_ok=True)
    meta, n = [], 0
    for q in qs:
        for pg in search(q):
            ii = (pg.get("imageinfo") or [{}])[0]
            if not ii.get("mime", "").startswith("image/jpeg") and not ii.get("mime", "").startswith("image/png"):
                continue
            em = ii.get("extmetadata", {})
            n += 1
            name = f"{n}.jpg"
            try:
                open(f"{out}/{who}/{name}", "wb").write(get(ii["thumburl"]))
            except Exception as e:
                print("skip", pg["title"], e); n -= 1; continue
            meta.append({"file": name, "title": pg["title"], "page": ii.get("descriptionurl"),
                         "license": em.get("LicenseShortName", {}).get("value"),
                         "artist": em.get("Artist", {}).get("value"), "date": em.get("DateTimeOriginal", {}).get("value"),
                         "credit": em.get("Credit", {}).get("value")})
            print(who, name, pg["title"], meta[-1]["license"])
    json.dump(meta, open(f"{out}/{who}/meta.json", "w"), ensure_ascii=False, indent=1)
