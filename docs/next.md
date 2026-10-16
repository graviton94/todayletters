# 다음 작업 (세션 인계)

마지막 갱신: 2026-10-16. 새 세션은 이 문서 → `CLAUDE.md`(작업 순서) → `docs/ia.md`(화면 · 규칙) 순서로 읽는다.
작업 방식은 `CLAUDE.md` 그대로: **시안(아티팩트) → 허락 → 패치 → 스크린샷 점검(허락 뒤) → 빌드(“릴리즈하자” 뒤에만)**.
푸시 때 도는 컴파일 확인 `android.yml` 은 늘 돌아도 된다. `android-apk.yml` · `android-aab.yml` 은 사용자가 “릴리즈하자” 고 할 때만.

## 지금 상태

- 비공개 테스트(14일 · 12명 이상, 실제 15~20명 모집) 를 앞두고 있다. 버전 이름은 테스트 `0.9.x`, 프로덕션 `1.0.0` (합의).
- **결제(Play Billing) 는 건드리지 않는다.** Play Console 결제 프로필이 아직 없고, 연동되면 사용자가 따로 요청한다.
- **Play Console 신고 · 스토어 등록정보 · 아이콘 · 그래픽 · 스크린샷 · FGS 시연 영상** 은 첫 AAB 를 올릴 때 사용자가 따로 묻는다. 먼저 꺼내지 않는다.
- 낭독 · 그림은 Cloudflare R2 의 `r2.dev` 주소 그대로 (도메인 · 유료 플랜은 시기상조, 사용자 결정).
- 개인정보처리방침 · 출처 페이지는 `graviton94.github.io` 의 `todayletters/privacy/`, `todayletters/index.html` (main 에 반영됨).

## 이번 세션에 들어간 것 (커밋 순)

| 무엇 | 어디 |
|---|---|
| 알림 · 저녁 알림 · 위젯이 마지막으로 연 작품 기준 (`Arrivals.pick`), 알림을 누르면 그 작품으로. 위젯 갱신 때 SoundPool 새는 것 고침, `TIME_SET` 재예약 | `data/Notices.kt`, `MainActivity.kt` |
| 백업은 진도 · 설정(`letters.xml`)만 (받은 파일 · 녹음 · 오류 기록 제외, 25MB 한도) | `res/xml/data_extraction_rules.xml`, `backup_rules.xml` |
| R2 파일 덮어쓰기 금지: 고친 파일은 `data/remote.json` 의 `revisions` → `rev/<판>/<경로>` | `scripts/r2_guard.py`, `narration-r2.yml`, `docs/release.md` 5 |
| 하루 새 편지는 작품마다 1통 (설정 없음), 첫날만 튜토리얼 작품(빈센트) 2통. 더 받기는 상점 ‘오늘 편지 더 받기’ | `core/Content.kt` `Arrivals.perDay` |
| 그림은 백그라운드에서 풀기 + LRU (`rememberPicture`) | `ui/Viewer.kt` |
| 아는 낱말 비율(편지 머리) · 언어 등급 A1~B2 × 3단 · 번역 줄 물러나기 · 나의 기록 두 축 · 등급 축하 | `core/Reading.kt`, `ui/Record.kt`, `ui/Cabinet.kt` |
| 답장 사다리: “당신이 테오라고 생각하고 답장해 볼까요?”, 편지 문장 인용, 도움은 등급만큼 (A1·1단 잇기 → A1·2단~A2 빈칸 하나 → B1 빈칸 둘 → B2 직접 쓰기). 쓴 답장은 복습 카드 (`CardRef.reply`) | `core/Ladder.kt`, `ui/Ladder.kt`, I~II장 `reply.quote` |
| 언어 등급 메달 가족 `grade` (A2 · B1 · B2 · B2 3단, Met CC0 동전) | `data/medals.json`, `assets/medals/grade_*.webp` |

규칙의 숫자(등급 문턱, 접는 비율 등)는 `docs/ia.md` 와 각 core 파일 머리 주석에 있다. 데이터로 잰 근거: 아는 낱말 비율은 60% 안팎에서 시작해 한 작품을 다 익히면 80% 안팎.

**아직 사람이 화면으로 본 적이 없다.** 위 패치는 모두 `android.yml` (core 테스트 + debug APK 컴파일) 만 통과했다.

## 2026-10-14 추가 (런처 아이콘 · FGS 영상)

- 런처 아이콘을 스토어 아이콘과 같은 〈별이 빛나는 밤〉 우표로 바꿨다 (적응형: 밤하늘색 한 빛깔 바탕 + 우표 · 소인 앞 그림, 예전 PNG 도). `docs/image-policy.md` 3 에 PD-Art 아이콘 결정 한 줄.
- Play Console FGS 시연 영상 워크플로 `fgs-video.yml` + `scripts/fgs_video.sh` + debug 전용 `DevHooks.kt` 를 넣었지만, 에뮬레이터가 서재에서 멈춰 **영상은 아직 없다** (`docs/release.md` 4-1 의 상태 · 다음 시도).

## 2026-10-15 추가 (폰 피드백 반영)

- 편지 방: 풀이 카드는 누른 문장 바로 아래, 튜토리얼 안내는 가리키는 것 옆 (‘다 읽었어요’ 뒤 단추 테두리 반짝임).
- 낱말 맞추기 = **문맥 카드 (B)**: 낱말 이름표 + 편지 속 문장(밑줄) + 뜻 A–D. 복습 뜻 고르기와 같은 부품 (`WordLabel` · `MeaningOptions`, `ui/Review.kt`). 블록 · 선 잇기는 지웠다.
- 틀린 보기: 옅은 빨강 바탕 · 취소선 · 오른쪽 ✕ (복습도 같이).
- FGS 영상 둘은 `design/store/play/` 에 있다. 다음 AAB(0.9.1) 은 “릴리즈하자” 뒤에.

## 2026-10-16 추가 (학습 깊이 · 규격 v1)

- 편지 걸음에 **이해 확인** (`check`, 독해 문제: 원문 문장 → 질문 → 보기) 과 **오늘의 회화** (`talk`, 그 인물과 오늘 말투 4줄, 메신저 화면) 이 들어갔다. 답장 턴 뒤 ‘소리 내어 말해 보기’.
- **도움 사다리** 한 곳 (`core/Scaffold.kt` Support): 등급(A1~B2) · 낱말 익숙함으로 번역 · 힌트 · 빈칸이 줄어든다. 회화 내 차례는 `core/TalkTurn.kt` 상태 기계 (A1 따라 말하기 · A2 빈칸 뒤 녹음 · B1 순서대로 조각 뒤 녹음 · B2 뜻만, 두 번 틀리면 원문 보여 주고 넘어감).
- 발음: 영어 학습자 IPA (lexicon_en `ipa`), 한국어 학습자 [소리] · 로마자 · 규칙 (lexicon_ko `pron` · `rom` · `pron_note`).
- 카드: 세 언어 덱 모두 A1–A2 61%. 표현 2개씩.
- **시리즈 규격 v1**: `python3 scripts/spec_check.py` 가 통과해야 새 시리즈가 들어간다 (두 시리즈 통과). 데이터 넣기 `scripts/apply_depth.py`.
- 소리: narration-r2 `make=new` 로 회화 · 새 카드 · 표현 2,103개 (새 파일만). 회화 줄을 고치면 `talk.audio` 를 새 이름(예 "tb") 으로 (R2 덮어쓰기 금지). 한 언어만 고쳤으면 언어별 `{"*": "t", "ko": "tk"}` (core `Talk.audioName`).
- 한국어 회화 말투를 실제 관계대로 바꿈 (빈센트↔테오 반말, 레오폴트→볼프강 반말 · 볼프강→아버지 해요체, 난네를 · 콘스탄체 · 자캥 반말, 클라인 · 푸흐베르크 해요체). 바뀐 회화 118개는 `audio: {"*": "t", "ko": "tk"}` — **ko 소리 `tk*` 는 아직 없음** (목소리 다시 고른 뒤 narration-r2 `make=new`). 목소리 품질은 아직 사람이 안 들어 봤다.

## 2026-10-16 추가 (천천히 듣기 · 시안 slow.html)

- 소리가 나는 곳마다 단추 둘 `ListenButtons` (`ui/Paper.kt`): 스피커 = 작품의 기본 듣기 속도, 0.7× = 그 소리를 0.7배로 한 번 (다시 누르면 멈춤, `SlowToggle`). 편지 방 문장마다 · 회화 말풍선 · 내 차례 아래 칸 · 이해 확인 지문 · 낱말 이름표 (맞추기 · 복습) · 낱말 카드 · 편지 낱말 목록 · 낱말 풀이 · 표현 카드 · 오늘의 기억 확인 · 복습 말하기 · 따라 읽기 원래 소리 · 받아쓰기 (같은 0.7× 부품). 편지 방 ≡ 메뉴의 1↔0.7 은 뺐다.
- 기본 듣기 속도: 작품 설정 0.7 / 0.85 / 1.0 + ‘등급에 맞춰 자동’ (켜짐: A1 0.85 · A2 0.9 · B1 이상 1.0, 직접 고르면 자동 꺼짐). core `Listen.speed` 한 곳, `Narrator.base` 로 모든 보통 재생에. 예전 방 0.7× 를 쓰던 작품은 직접 고른 0.7 로 이어짐. 따라 읽기 원래 소리의 고정 0.8 은 없앴다.
- 화면 글의 그림 글자(이모지) 뺌: 힌트 단추 💡 → 그린 전구, coach_series ⚙ → ‘톱니바퀴 단추’, ▶ · ■ → 그린 표시. 편지 · 회화 · 답장 글(메신저 내용)의 이모지는 그대로.
- 화면 사진 장면: `ScreensTest.slow*` (아직 사람이 본 적 없음).

## 다음 할 일 (차례대로)

### 1. 스크린샷 점검 (사용자 허락 뒤 `screens.yml`)
볼 화면: 답장 사다리 (고르기 · 둘 고르기 · 직접 쓰기), 편지 머리의 “아는 낱말 %”, 다 읽은 편지의 “처음 → 지금”, 번역이 접힌 문장, 나의 기록의 언어 등급 블록, 등급 축하 장면, 메달 진열장의 언어 등급 가족, 작품 설정(하루 편지 수 줄이 빠지고 ‘실력만큼 번역 물러나기’).
`ui/ScreensTest.kt` 에 장면이 없으면 더한다 (Robolectric 에서는 `rememberPicture` 가 그 자리에서 푼다).

### 2. 문형 사다리 (다음 단계로 합의됨) → 화면에서는 “오늘의 문법”
**상태 (2026-10-14):** 시안 v8 승인. **형식 · 화면 코드 들어감** (턴 · 오늘의 문법 줄 · focus 빈칸 · 한국어 말투 노트 · 말투 바꿔 보기 카드 · 턴별 복습 카드, `docs/ia.md` 답장 사다리).
데이터 넣기 도구 `scripts/apply_replies.py <폴더> [--check]` (형식은 그 파일 머리 주석). 남은 것: 장마다 답장 데이터 (병렬 작성 중) 를 `--check` → 넣기 → `generate.py` → `build_fonts.py --check` (새 한글이면 글꼴 다시) → 화면 사진 `*ScreensTest.grammar*`.
맺음 편지 (장 "E") 도 턴 셋으로 답장한다 (아웃트로 화면의 답장 단추, `apply_replies.py` 의 chapter "E" → epilogue.json).
화면 사진의 문법 장면은 실제 데이터가 아니라 I장 첫 편지에 끼운 시험용 두 턴이다.

답장 **문장 자체**를 장마다 새 문형으로 다시 쓴다. 지금 답장 문장은 예전에 쓴 짧은 문장 그대로이고, 인용(`quote`) 은 I~II장만 있다.
- 장마다 문형 (프랑스어 예): I 현재형 · 맞장구 / II 명령 · 부정 / III 복합과거 / IV 가까운 미래 · parce que / V 반과거 · 대명동사 / VI si 조건 · 조건법 / VII 관계사 qui·que·où / VIII · 맺음 접속법.
  독일어: 현재 → 화법조동사 → 현재완료 → 분리동사 → 부문장 어순(weil · dass) → 과거 → 접속법 II.
  영어 · 한국어를 배우는 사람에게도 그 언어의 사다리 (한국어 예: 해요체 → 과거 → -아서 → -ㄹ게요 → -면).
- 답장은 세 언어(원어 · en · ko)를 다 갖고, 각 줄이 그 언어 사다리의 문형을 따른다. 받는 사람에 맞는 말투 (빈센트 → 테오, 모차르트 I장 → 누나 난네를, II장~ → 아버지 레오폴트).
- 장이 갈수록 턴 수: I~II 1턴, III~V 2턴, VI~ 3턴 (턴마다 인용할 편지 문장). 지금 데이터는 `reply` 한 개 + `quote` 숫자 하나 → 여러 턴 형식으로 넓혀야 한다 (예: `replies: [{fr,en,ko,quote}]`, Library 파싱 · `Exercises.replyFor` · `ui/Ladder.kt` · `CardRef.reply` 를 턴 번호까지).
- 장마다 “오늘의 문형” 한 줄 설명 (배우는 말마다 ko · en) 을 답장 화면에 작게.
- 순서: 먼저 형식 · 화면 시안(아티팩트) → 허락 → III장부터 데이터 작성 (빈센트 · 모차르트 III~VIII · 맺음, 편지 100여 통 × 세 언어).
- 원어 문장은 문법 · 시대 말투를 확인하고, 실제 테오 · 레오폴트의 말로 꾸미지 않는다 (앱이 쓴 답장).

### 3. 테스트 운영
- 테스터 안내문 시안은 이전 아티팩트에 있다 (하루 한 통 · 꼭 해 볼 것 · III장 잠김 · 의견 보내기). 내용이 바뀐 것(하루 1통 고정, 답장 사다리)을 반영해 다시 낸다.
- 사용자가 테스트 시작 · 종료일을 알려 주면, 그 사이 커밋으로 **Play Console 프로덕션 신청서용 문구**(테스트 피드백으로 바꾼 것)를 정리한다.

### 4. 열린 결정 · 참고
- 배우는 말을 바꾸면 같은 작품에서 그날 한 통이 더 열린다 (진도가 언어마다 따로) → **그대로 둔다** (시리즈별 구매 원칙, 사용자 결정).
- 다음 작품(초상만 있는 나폴레옹 · 바쇼) 은 하루 한 통 기준 약 두 달 간격으로 나와야 분량 기반 유료화가 이어진다 (사용자도 동의, 개발 동기).
- 다시 읽기(R2) 는 지금 “처음 → 지금 %” 표시까지. 14일 · 30일 뒤 다시 도착시키는 기능은 아직 없다 (단계적으로, 번역을 한 번에 다 빼지 않기).

## UI 글 원칙

- 메뉴 · 설정의 설명 글은 최소로. 이름표만으로 알 수 있으면 설명을 달지 않는다.
- 쓸데없는 줄바꿈 없이, 이름표는 자리(폭)에 맞게 짧게.
- 설명은 사용자가 그것 없이는 막힐 때만, 짧은 한 줄로.

## 일하는 법 메모

- 이 환경에서는 Google Maven · 메트로폴리탄 API 가 막혀 로컬 빌드 · 그림 받기가 안 된다. 컴파일은 푸시 후 `android.yml`, 그림은 `fetch-letters.yml` 로 `sources` 가지에 받아서 쓴다 (메달 그림은 동전을 오려 256px WebP 품질 80).
- 생성 파일은 손으로 고치지 않는다: `python3 scripts/generate.py` (글자 `design/strings.json`, 편지 `data/` → assets). 출처 페이지는 `python3 scripts/site_sources.py ../graviton94.github.io`.
- 메달 이름은 겹치지 않게 (`data/medals.json`).

## 2026-10-16 추가 (폰 테스트 버그 넷)

- 뜻 고르기 보기: core `Distractors` 한 곳 — 정답과 같은 갈래 (낱말 ↔ 낱말 · 표현 ↔ 표현 · 답장 ↔ 문장) · 같은 작품, 같은 뜻 · 같은 낱말 · 빈 뜻 뺌, 같은 품사 · 비슷한 길이 먼저. 복습 (`buildQuestions`) · 편지 걸음 낱말 맞추기 (`Match`) 둘 다.
- 영어 IPA: lexicon_en 의 나머지 espeak 줄 (빈센트 688 · 모차르트 720) 을 CMUdict GA 로. CMUdict 에 없는 이름 · 외래어 (각 57 · 59) 는 espeak 를 GA 꼴로 고쳐 씀. `scripts/vocab/build.py` 로 사전을 다시 만들면 espeak 로 돌아가니 그때 다시 바꿀 것.
- 문장 나누기: core `Abbrev` (St. · Mr. · M. · Hr. · Sig. · heil. · No. 5 · J. · den 3. Juni) — `Pairs.sentences` · `Ladder` 가 쓴다. 스크립트는 `scripts/abbrev.py` `split_keep`.
  **이미 만든 낭독 중 줄임말 뒤 긴 끊음(//)** — 모차르트 c1-l2 m2 en (St. // Peter's), m4 en (St. // Peter · Paul · Luke), m4 de (heil. // Petrus), m5 de (heil. // Petrus · S. // Pietro), m5 en (St. // Peter's), c1-l6 m4 de (Sig. // Cassiglioni). speak 글 · 소리는 아직 그대로 (고치면 새 이름으로, R2 덮어쓰기 금지).
- 별자리 잇기: 굴리지 않고 한 화면 (하늘이 남은 높이를 채움, 720dp 아래 화면은 제목 빼고 작은 낱말). 장면 `ScreensTest.fixFitConstellationSmall` (360×640, 아직 안 찍음).

## 2026-10-16 추가 (나폴레옹 시리즈 데이터 · 출시 전 숨김)

- `data/napoleon/` 에 셋째 작품: 프롤로그 4 + 8장 × 7 + 맺음 1 = 61통 (tier 2 ₩2,900, `series_napoleon`, 무료 I · II장). 규격 v1 `spec_check.py napoleon` 통과.
- **`"hidden": true`** (series.json): generate.py 가 앱 assets 에 넣지 않고 판매 규칙만 본다 → 서재의 ‘곧 도착’ 나폴레옹 카드 그대로. site_sources.py 도 건너뜀. 출시 때 이 칸을 지우고 generate.py.
- 원문은 지금 철자 (명백한 오자 · 옛 철자는 앱 글에 표시 없이 고침), 찍힌 글자는 메시지 `orig.fr` 에만. 이 원칙은 출처 화면 `credits_text` 와 사이트 출처 페이지 머리 한 줄로만 알린다 (편지마다 적지 않음).
- 어휘: lexicon*.json 은 카드 낱말만 (빈센트 · 모차르트 줄의 뜻 · IPA · 발음 · 예문을 다시 씀). **gloss*.json 은 아직 없음** (눌러서 뜻 보기 · 아는 낱말 % 는 나폴레옹에서 안 나온다; 만들려면 Tatoeba 추출본 + scripts/vocab 표에 나폴레옹 낱말을 더해 build.py · build_tl.py, validate_lexicon.py 의 SERIES 에 napoleon).
- 배역표 voices.json: 길 M3 → M4 → M5 (I 0.25 · E 0.75), `speed` 1.05 (narrate.py 가 인물 목소리에만 곱함), talk_me 조제핀 F3 · 마리 루이즈 F5 · 조제프 M2 · 외젠 M1 · 아들 M1 · 오르탕스 F4. **낭독은 아직 없다.**
- 그림 (plates `n-*.webp`, 아웃트로 `n-outro.webp`) 은 아직 받지 않았다: 후보와 `[미확인]` 표시는 편지마다 `plate.imageSource`. fetch-portraits.yml 로 확정 → 점(spots) 찍기. 지도 자리 x · y 는 임시값.
- c4-l5 (‘위대한 황후’ 놀림 편지) 의 ko 는 이 편지만 높임말 (나폴레옹이 tu 대신 vous 로 놀림) — 반말 원칙의 예외, 사용자 확인 필요 (`_note_register`).
