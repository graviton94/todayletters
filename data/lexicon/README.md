# data/lexicon — 어휘 등급 · 빈도 자료

- `de_freq.tsv`, `fr_freq.tsv`: `낱말<TAB>순위` (상위 50,000개). 출처: hermitdave/FrequencyWords, `content/2018/{de,fr}/{de,fr}_50k.txt` (OpenSubtitles 2018 기반), https://github.com/hermitdave/FrequencyWords
- 라이선스: 내용물은 **CC BY-SA 4.0** (저장소 README: "CC-by-sa-4.0 for content"; 코드는 MIT). 앱에 순위·등급 태그를 쓸 때 출처 표기 필요, 이 파일을 고쳐 배포하면 같은 라이선스로.
- 만든 방법 (2026-10-11): fetch-letters.yml 로 받은 원본에서 횟수 열을 빼고 줄 번호를 순위로 붙임 (`awk 'NF==2{print $1"\t"NR}'`). 소문자 · 활용형 그대로 (표제어 아님).
- 넣지 않은 것: 괴테 인스티투트 A1·A2·B1 낱말 목록은 "Das Werk und seine Teile sind urheberrechtlich geschützt" — 서면 허락 없이 저장·배포 불가라 공개 저장소에 두지 않음.
- FLELex 는 CC BY-NC-SA 4.0 (비영리) 이라 상업 앱에 못 씀 — 받지 않음.
- Tatoeba 예문(CC BY 2.0 FR, 문장마다 작성자 표기)은 12낱말 이하 · 영/한 번역 있는 것만 추려도 독일어 44MB · 프랑스어 31MB 라 git 에서 뺌.
