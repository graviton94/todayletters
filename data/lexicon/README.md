# data/lexicon — 어휘 등급 · 빈도 자료

- `de_freq.tsv`, `fr_freq.tsv`, `es_freq.tsv`, `en_freq.tsv`, `ko_freq.tsv`: `낱말<TAB>순위` (상위 50,000개). 출처: hermitdave/FrequencyWords, `content/2018/{de,fr,es,en,ko}/{de,fr,es,en,ko}_50k.txt` (OpenSubtitles 2018 기반), https://github.com/hermitdave/FrequencyWords. en · ko 는 2026-10-11 fetch-letters.yml 로 받음 (sources 가지). es (고야) 는 2026-10-23 같은 저장소 `content/2018/es/es_50k.txt` 를 받아 같은 방법으로.
- 라이선스: 내용물은 **CC BY-SA 4.0** (저장소 README: "CC-by-sa-4.0 for content"; 코드는 MIT). 앱에 순위·등급 태그를 쓸 때 출처 표기 필요, 이 파일을 고쳐 배포하면 같은 라이선스로.
- 만든 방법 (2026-10-11): fetch-letters.yml 로 받은 원본에서 횟수 열을 빼고 줄 번호를 순위로 붙임 (`awk 'NF==2{print $1"\t"NR}'`). 소문자 · 활용형 그대로 (표제어 아님).
- 넣지 않은 것: 괴테 인스티투트 A1·A2·B1 낱말 목록은 "Das Werk und seine Teile sind urheberrechtlich geschützt" — 서면 허락 없이 저장·배포 불가라 공개 저장소에 두지 않음.
- FLELex 는 CC BY-NC-SA 4.0 (비영리) 이라 상업 앱에 못 씀 — 받지 않음.
- Tatoeba 예문(CC BY 2.0 FR, 문장마다 작성자 표기)은 12낱말 이하 · 영/한 번역 있는 것만 추려도 독일어 44MB · 프랑스어 31MB 라 git 에서 뺌.

## 작품별 어휘 자료 (data/<작품>/lexicon.json · gloss.json · daily.json)

학습 대상은 원문 언어 — 빈센트는 프랑스어(Brieven 1914), 모차르트는 독일어(Nohl 1865, 옛 철자 그대로 두고 표제어만 현대 철자). 뜻풀이 언어는 ko · en. 챕터 JSON 은 건드리지 않았고, 앱 연결은 나중에.

**출처 · 라이선스**
- 편지 원문: 퍼블릭 도메인 (각 메시지의 source).
- 표제어 · 품사 · 성 · 뜻풀이(ko/en) · 관용 표현 설명: 이 저장소에서 새로 씀 (편지 문맥에 맞춘 뜻). 다른 사전을 베끼지 않음. 표제어 추출은 simplemma(MIT) 결과를 손으로 고침.
- 등급: FrequencyWords 순위(CC BY-SA 4.0, 위 참조)에서 계산 → 앱에 등급을 보이면 "FrequencyWords (Hermit Dave), CC BY-SA 4.0" 표기.
- IPA: 챕터 파일에 손으로 넣은 IPA 가 있으면 그것, 없으면 espeak-ng(GPL 프로그램, 출력물은 자유) 로 만든 근사치. 독일어 합성어 · 접두사 동사는 강세가 틀릴 수 있음.
- 예문: Tatoeba (CC BY 2.0 FR) — 12낱말 이하, 표제어나 편지 속 꼴이 들어 있고, 영어 번역에 뜻풀이 낱말이 보이는 문장만 (다른 뜻 예문 거르기). 한국어 번역은 Tatoeba 에 있을 때만. **앱에서 예문마다 작성자(author) 와 https://tatoeba.org/sentences/show/<id> 를 표기해야 함.** 예문을 지어내지 않음 — 단, 아래 ‘앱이 쓴 예문’ 은 예외.
- 괴테 인스티투트 목록 · FLELex 는 쓰지 않음.

**등급 구간** (표제어와 편지에 찍힌 꼴 가운데 더 높은 순위 기준): A1 ≤500 · A2 ≤1,500 · B1 ≤3,500 · B2 ≤7,000 · C1 ≤15,000 · C2 그 밖 / 목록에 없음. 인명 · 외국어(모차르트의 이탈리아어·프랑스어·라틴어) · 약어는 등급 없음. 자막 말뭉치라 구어 낱말(Teufel, bébé 등)이 쉽게, 옛 문어(gemeiniglich, besogne)는 어렵게 나옴. 동형어(ton 너의 / ton#2 색조)는 순위가 의미를 못 가려 아쉬운 대로 둠.

**파일 형식** (모두 한 줄 JSON, 각 파일 `_doc` 에 설명)
- `lexicon.json`: `fields` 순서의 배열 한 줄 = 표제어 하나 — lemma, id(동형어만 "ton#2", 아니면 null), forms(인쇄된 꼴), pos(n v adj adv prep conj pron det art num interj name abbr foreign:it|fr|la), gender(m f n pl), ko, en, ipa, level, first(처음 나오는 편지 id), letters(공백으로 이은 편지 id), example([tatoeba id, 문장, en, ko|null, author]), function(기능어면 1). 뒤쪽 null 은 생략.
- `gloss.json`: `letters[편지 id][메시지 순번]` = 원문 낱말 순서대로의 토큰 목록. 토큰은 문자열(그 자체가 사전 키) 또는 `[인쇄된 꼴, 사전 키]`. 사전 키 = id 가 있으면 id, 없으면 lemma. 숫자 · 문장부호는 토큰 아님. 모든 낱말이 덮여 있어 어느 낱말이든 눌러 뜻을 볼 수 있음.
- `daily.json`: `letters[편지 id]` = { words: 오늘의 낱말 8개(사전 키), spots: [{i: 그림 점 번호, w: 바꿀 낱말, was: 원래 낱말}], phrases: [{t: 편지에서 그대로 옮긴 표현, m: 메시지 순번, ko, en, note_ko, note_en}] }.
  - words: 순서 P → I…VIII → E, 챕터 구간 P · I–II: A1–A2, III–V: B1, VI–VIII · E: B2–C1. 모자라면 한 구간 넓힘(그래도 모자라면 8개 미만 — 짧은 프롤로그 등). 앞 편지에서 고른 낱말은 다시 안 고름. 인명 · 한 글자 · 대명사 · 기능어 · 영어와 철자가 같은 낱말(portrait, Arm 등) 제외.
  - spots: 그림 점 낱말이 약할 때(인명 · 대명사 · 동사 꼴 · 한 글자 · 영어와 같은 철자)만, 인용 메시지 원문에 실제로 있고 그림에 보이는 것을 이름하는 명사로 바꿈 — 손으로 확인. 맞는 게 없으면 바꾸지 않음(목록에 안 들어감).
- 크기: 작품마다 세 파일 합쳐 약 360 KB (gzip 하면 훨씬 작음).

**다시 만들기**: `pip install simplemma`, `apt install espeak-ng`, Tatoeba 추출본(id, 문장, en, ko, author) 을 한 폴더에 두고
`TATOEBA_DIR=<폴더> python3 scripts/vocab/build.py vincent fr` → `python3 scripts/vocab/daily.py vincent fr` (mozart de 도 같음) → `python3 scripts/validate_lexicon.py`.
손으로 고치는 곳은 `scripts/vocab/src/` — `*_tab.txt`(표제어|품사|en|ko|F=기능어), `*_map.txt`(꼴=표제어), `*_occ.txt`(편지:메시지:토큰=표제어, 동형어 구분), `phrases_<작품>.txt`, `daily_override_<작품>.json`(그림 점 바꾸기).

## 영어 · 한국어 학습용 어휘 (data/<작품>/lexicon_en · gloss_en · daily_en, lexicon_ko · gloss_ko · daily_ko)

학습 언어를 영어나 한국어로 고른 사람을 위한 같은 형식의 자료. 텍스트는 각 메시지의 `en` 줄(모차르트는 Wallace 1866, 빈센트는 앱 번역) · `ko` 줄(앱 번역). 다른 점은 각 파일 `_doc` 에 적음.

- **영어**: 표제어는 simplemma(MIT) + 손 고침(`src/en_map.txt`). `'s 'm 're 've 'll 'd` 는 따로 떼어 각자 표제어, `don't` 같은 n't 꼴과 `o'clock`, 하이픈 낱말은 통째로. 뜻풀이는 `ko`(한국어 뜻) + `en`(쉬운 영어 정의, 번역 아님). IPA 는 espeak-ng en-us 근사치. 등급은 en_50k 순위(표제어 · 찍힌 꼴 중 높은 쪽).
- **한국어**: 토큰은 어절(문장부호 · 숫자에서 끊김). 어절마다 사전 표제어로 — 명사는 조사를 뗀 꼴, 동사 · 형용사는 어간 + 다(공부하다, 즐겁다). 분석은 kiwipiepy(LGPL-3.0 도구, 결과물만 씀) + 손 고침(`src/ko_map.txt` 어절=표제어|기능, `src/ko_occ.txt` 작품/편지:메시지:토큰=표제어, 동형어 `눈#2`(눈 雪) 등). 조사 · 어미는 gloss_ko 토큰의 셋째 칸에 `형태/품사` 를 `+` 로 이어 둠(`["그렸어","그리다","었/EP+어/EF"]`), 품사 뜻은 gloss_ko.json `tags`(ko · en). 뜻풀이는 `en`(영어 뜻) + `ko`(짧은 한국어 풀이). IPA 없음.
- **한국어 등급**: ko_50k 는 어절 목록이라 그대로는 표제어 순위가 안 됨 → 목록의 어절을 하나씩 같은 규칙으로 분석해 표제어로 모으고, 순위의 역수(1/순위, 지프 근사 — tsv 에 횟수를 안 남겼으므로)를 더한 값으로 다시 순위를 매김. 그 순위와 찍힌 어절의 순위 중 높은 쪽으로 같은 구간(A1 ≤500 … C2). 대략적인 값.
- **예문**: Tatoeba. 한국어는 kor 문장(작성자 표기, CC BY 2.0 FR) 가운데 분석한 표제어가 들어 있고 영어 번역에 뜻풀이 낱말이 보이는 것. 영어는 Tatoeba 의 CC0 영어 문장만 씀(per-language `eng_sentences_detailed` 가 35 MB 라 작성자를 못 받음 → CC0 라 표기 의무 없음, author 칸은 "CC0"); 한국어 번역이 있으면 ko 칸에(드묾). 예문 형식은 [id, 문장, en|null, ko|null, author].
- **오늘의 낱말**: 원문과 같은 규칙(편지당 8개, 챕터 구간, 반복 없음, 인명 · 기능어 제외). 영어는 한 글자 · 대문자 · 하이픈 낱말 제외, 한국어는 한 음절 낱말(꽃, 별) 허용. spots 는 늘 `[]`. phrases 는 손으로 고른 편지 문구(`src/phrases_<작품>_<en|ko>.txt`).
- **다시 만들기**: `pip install simplemma kiwipiepy`, Tatoeba 추출본 `tatoeba_eng.tsv` · `tatoeba_kor.tsv`(id, 문장, en, ko, author) 를 한 폴더에 두고 `TATOEBA_DIR=<폴더> python3 scripts/vocab/build_tl.py vincent ko` → `python3 scripts/vocab/daily_tl.py vincent ko` (en · mozart 도 같음) → `python3 scripts/validate_lexicon.py` (세 언어 모두 검사, 언어별 세 파일 합계 400 KB 미만).

## 오늘의 낱말 · 표현 소리 (scripts/narrate.py, `NARRATE_ONLY=vocab`)

편지의 낭독 폴더(`audio/<작품>/<챕터>/<편지 id>/` — P · I · E 는 앱 assets, II–VIII 은 remote/ → R2)에 함께 둔다. 목소리는 그 장의 낭독과 같고(배역표 · 나이), 차분한(calm) 프리셋, 높낮이 그대로.

- `v_<언어>_<slug>.m4a`: 그 언어 daily 파일(`daily.json` = 원문 de/fr, `daily_en.json`, `daily_ko.json`)의 `words` 낱말 하나. 언어는 실제 코드(de · fr · en · ko). 사전 키의 표제어를 읽는다(`ton#2` 는 "ton").
- `ph<번호>_<언어>.m4a`: 같은 파일 `phrases` 의 표현(`t`) 하나. 번호는 그 언어 phrases 목록에서 **1부터** (첫 표현 = ph1).
- **slug 규칙** (사전 키 → 파일 이름): 영어 소문자 `a-z` · 숫자 `0-9` · `-` 는 그대로, 그 밖의 글자는 모두 `_<유니코드 코드포인트 16진 소문자>_`. 예: `Leben` → `_4c_eben`, `côté` → `c_f4_t_e9_`, `ton#2` → `ton_23_2`, `누나` → `_b204__b098_`, `avant-hier` → `avant-hier`. Kotlin: `key.codePoints().toArray().joinToString("") { c -> if (c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c == '-'.code) c.toChar().toString() else "_" + Integer.toHexString(c) + "_" }`.
- 오늘의 낱말이 아닌 낱말(눌러 본 낱말 등)은 소리 파일이 없다 → 앱은 소리 없이 보여 준다.

## 앱이 쓴 예문 (2026-10-16)
Tatoeba 예문 가운데 학습 앱에 맞지 않는 것(성적 · 혐오 · 폭력 · 의료 농담 · 정치 공격 · 깨진 문장 · 다른 뜻)은 낱말은 그대로 두고 예문만 앱이 쓴 짧은 문장으로 바꿨다 (세 작품 × 세 언어 1,189개).
형식은 `[0, 문장, en|null, ko|null, "app"]` — id 0, author `"app"`. 앱은 이 예문에 ‘Tatoeba · …’ 줄을 달지 않고 출처 목록 · 사이트 예문 수에서도 뺀다 (core `Example.fromTatoeba`).
`build.py` · `build_tl.py` 로 다시 만들면 Tatoeba 예문으로 돌아가니 그때 다시 바꿀 것.


## 나폴레옹 (2026-10-17)
- 카드 낱말뿐이던 lexicon*.json 을 편지 낱말 전부로 넓히고 gloss*.json 을 만들었다 (fr 989 · en 986 · ko 1,079 표제어, 토큰 100% 덮음).
- 다시 만들기: `fetch-tatoeba.yml` 이 받은 tatoeba 가지의 `*.tsv.gz` 를 풀어 TATOEBA_DIR 로 → `build.py napoleon fr` · `build_tl.py napoleon en|ko` → `merge_cards.py napoleon <카드 lexicon 폴더>` (카드 줄은 손본 뜻 · IPA · 발음 · 예문 그대로, 빈센트 · 모차르트와 같은 낱말은 그쪽 IPA · 발음 · 검토한 예문) → `validate_lexicon.py napoleon`.
- 나폴레옹에만 쓰는 손 고침: `src/<lang>_map_napoleon.txt` (공용 map 뒤에 읽음). `src/<lang>_occ.txt` 키는 이제 모두 `작품/편지:메시지:토큰` (fr · de 도).
- 새로 쓴 뜻 (카드 · 공용 표에 없던 fr 557 · en 383 · ko 454줄) 은 이 편지 문맥에 맞춰 썼고 **아직 사람이 확인하지 않았다**. IPA 는 espeak-ng 근사치 (영어는 CMUdict GA 로 아직 안 바꿈).
- 새 Tatoeba 예문 가운데 다른 뜻 · 앱에 맞지 않는 것 78개는 `src/example_drop_napoleon.txt` 로 뺐다 (예문 없음, 앱이 쓴 예문으로 바꾸지 않음).
