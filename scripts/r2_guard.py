"""앱 밖 파일(remote/)을 R2 에 올리기 전에: 올릴 모양을 만들고, 이미 올린 파일을 다른 내용으로 덮어쓰지 않는지 본다.

앱은 장마다 파일 크기를 remote.json 에 갖고 있다가 받은 크기로 다 받았는지 본다 (data/Downloads.kt).
같은 주소의 파일이 바뀌면 아직 업데이트하지 않은 폰은 크기가 맞지 않아 받기에 계속 실패하고,
이미 받은 폰은 옛 파일을 그대로 쓴다. 그래서 고친 파일은 data/remote.json 의 "revisions" 에 판을 올려
rev/<판>/<경로> 라는 새 주소로 올린다 (generate.py 의 r2_key 와 같은 규칙, docs/release.md 5).

  python3 scripts/r2_guard.py stage remote <폴더>   # <폴더>/<R2 주소> 로 하드링크 (narration-r2.yml 이 이 폴더를 올린다)
  aws s3api list-objects-v2 ... --output json | python3 scripts/r2_guard.py check <폴더>
check: R2 에 같은 주소가 있고 크기나 MD5(ETag, 나눠 올리지 않은 것)가 다르면 그 목록을 적고 멈춘다.
"""
import hashlib
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def r2_key(rel, revs):
    n = int(revs.get(rel, 1))
    return rel if n <= 1 else f"rev/{n}/{rel}"


def files(root):
    for d, _, names in os.walk(root):
        for n in names:
            path = os.path.join(d, n)
            yield os.path.relpath(path, root).replace(os.sep, "/"), path


def stage(src, out):
    revs = json.load(open(os.path.join(ROOT, "data", "remote.json"), encoding="utf-8")).get("revisions", {})
    shutil.rmtree(out, ignore_errors=True)
    n = 0
    for rel, path in files(src):
        dst = os.path.join(out, r2_key(rel, revs))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        try:
            os.link(path, dst)
        except OSError:
            shutil.copy2(path, dst)
        n += 1
    print(f"올릴 모양: {n}개 (고친 파일 {len(revs)}개는 rev/ 아래로)")
    return 0


def check(root):
    raw = sys.stdin.read().strip()
    objs = {}
    for o in (json.loads(raw).get("Contents") or []) if raw else []:
        objs[o["Key"]] = (int(o["Size"]), o.get("ETag", "").strip('"'))
    bad = []
    for key, path in files(root):
        if key not in objs:
            continue
        size, etag = objs[key]
        here = os.path.getsize(path)
        if here != size:
            bad.append(f"{key}: R2 {size} B, 여기 {here} B")
        elif etag and "-" not in etag and hashlib.md5(open(path, "rb").read()).hexdigest() != etag:
            bad.append(f"{key}: 크기는 같고 내용이 다름")
    if bad:
        print("::error::이미 올린 파일을 다른 내용으로 덮어쓰려고 해요. data/remote.json 의 revisions 에 그 경로의 판을 올리세요 (docs/release.md 5).")
        print("\n".join(sorted(bad)[:50]))
        return 1
    print(f"덮어쓰는 파일 없음 (R2 {len(objs)}개와 비교)")
    return 0


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[1] == "stage":
        sys.exit(stage(sys.argv[2], sys.argv[3]))
    if len(sys.argv) == 3 and sys.argv[1] == "check":
        sys.exit(check(sys.argv[2]))
    sys.exit(__doc__)
