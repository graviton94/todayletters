# 다음 작업 (세션 인계)

마지막 갱신: 2026-10-24. 새 세션은 이 문서 → `CLAUDE.md`(작업 순서) → `docs/ia.md`(화면 · 규칙) 순서로 읽는다.
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
- 한국어 회화 말투를 실제 관계대로 바꿈 (빈센트↔테오 반말, 레오폴트→볼프강 반말 · 볼프강→아버지 해요체, 난네를 · 콘스탄체 · 자캥 반말, 클라인 · 푸흐베르크 해요체). 바뀐 회화 118개는 `audio: {"*": "t", "ko": "tk"}` — ko 소리 `tk*` 472개 있음 (회화 118 × 4줄, 2026-10-18 확인).

## 2026-10-16 추가 (천천히 듣기 · 시안 slow.html)

- 소리가 나는 곳마다 단추 둘 `ListenButtons` (`ui/Paper.kt`): 스피커 = 작품의 기본 듣기 속도, 0.7× = 그 소리를 0.7배로 한 번 (다시 누르면 멈춤, `SlowToggle`). 편지 방 문장마다 · 회화 말풍선 · 내 차례 아래 칸 · 이해 확인 지문 · 낱말 이름표 (맞추기 · 복습) · 낱말 카드 · 편지 낱말 목록 · 낱말 풀이 · 표현 카드 · 오늘의 기억 확인 · 복습 말하기 · 따라 읽기 원래 소리 · 받아쓰기 (같은 0.7× 부품). 편지 방 ≡ 메뉴의 1↔0.7 은 뺐다.
- 기본 듣기 속도: 작품 설정 0.7 / 0.85 / 1.0 + ‘등급에 맞춰 자동’ (켜짐: A1 0.85 · A2 0.9 · B1 이상 1.0, 직접 고르면 자동 꺼짐). core `Listen.speed` 한 곳, `Narrator.base` 로 모든 보통 재생에. 예전 방 0.7× 를 쓰던 작품은 직접 고른 0.7 로 이어짐. 따라 읽기 원래 소리의 고정 0.8 은 없앴다.
- 화면 글의 그림 글자(이모지) 뺌: 힌트 단추 💡 → 그린 전구, coach_series ⚙ → ‘톱니바퀴 단추’, ▶ · ■ → 그린 표시. 편지 · 회화 · 답장 글(메신저 내용)의 이모지는 그대로.
- 화면 사진 장면: `ScreensTest.slow*` (아직 사람이 본 적 없음).

## 2026-10-17 추가 (작품 받기 · 시안 assets.html)

- 앱 안에는 **빈센트 서문 · I장** 의 낭독 · 그림만 (`data/remote.json` 의 `"bundled": {"vincent": ["P", "I"]}`). 그 밖의 장 (빈센트 맺음, 모차르트 서문 · I · 맺음 · 곡, 나폴레옹 서문 · I · 맺음) 은 `remote/` 로 옮겨 R2 에서 받는다. 대표 그림 (kit.hero) · 초상 · 소장품 · 글꼴 · 소리 · 틀 · 메달은 그대로. 쓰이지 않던 그림 넷 (c2-l4 · c3-l3 · 스케치 v489 · v593) 은 지웠다.
- generate.py: 서문 P · 맺음 E 도 장 묶음 (맺음은 아웃트로 그림 포함). `bundled` 밖의 장 파일이 assets 에 남으면 멈춘다. 앱 안 장이 없는 작품은 remote.json 에 `"first": ["P", "I", "II"]` (서문 + 무료 장) → **새 작품은 아무것도 넣지 않는다.**
- 앱: 작품을 처음 열 때 (`enter`) 한 번, 처음 묶음 받기 시트 (“모차르트의 시대로 떠날까요?”, 이름은 series.json `known`). ‘다음에’ 면 표지에 ‘다운로드 · 27MB’ 단추. 서문 (`openPrologue`) · 맺음 (`openOutro`) · 편지 (`open`) 는 받지 않았으면 시트부터 (`Downloads.needed`). 그 뒤 장은 “III장으로 떠날까요?”. 시트의 긴 문장은 한 줄씩으로, ‘소리 없이 읽기’ 는 뺐다.
- 받는 중 `23.4 / 64MB` (소수 한 자리, core `Fetching.progress`). 화면 사진 `ScreensTest.dl*` (dl1_ask · dl2_loading · dl3_done · dl4_failed). 모차르트 화면 사진 (`mozart*`) 은 저장소 `remote/` 의 파일을 files/ 에 넣고 찍는다.

## 2026-10-18 추가 (기록 보관 · 시안 backup.html, 구매 화면 작품별 소장)

- 앱 설정 › **기록 보관**: ‘기록 내보내기’ (마지막 내보낸 날) → 시트 “기록을 어디에 둘까요?” (휴대폰에 저장 = CreateDocument · Drive · 메신저로 보내기 = 공유 창, FileProvider `cache/records/`), ‘기록 가져오기’ → OpenDocument → 확인 시트 “이 기록으로 바꿀까요?” → 저장소 바꾸고 `activity.recreate()` (새 AppState, Play 에서 이용권 다시 확인).
- 파일 = SharedPreferences "letters" 그대로 (안드로이드 백업과 같은 범위) — core `Backup` (꼴 · 검사 · 거르기, `BackupTest`). `ent:*` (이용권 캐시) · `debug:*` · `backup:*` 는 넣지 않고, 가져올 때도 파일의 것은 버리고 이 폰의 것을 둔다. 파일 머리 `app` · `kind` · `format` (지금 1, 더 새 꼴은 읽지 않음).
- 구매 화면: 지금 작품 소장 칸 아래에 다른 작품 소장 칸 (작은 칸), 작품마다 값 · 소장함 · 잠긴 장 범위.
- 오늘 화면 보관 한 줄 (알림 아님, core `BackupNudge`): 마지막 내보내기 30일 이상 · 그 뒤 편지 7통 이상이면 “보관 뒤 편지 N통 · 기록 보관하기 › · 나중에”, 내보낸 적이 없으면 처음 한 장을 다 마친 다음 날부터 “편지 N통을 마쳤어요 …”. ‘나중에’ 14일, 내보내면 다시 셈. 상태는 `backup:*` (파일에 넣지 않음).
- 개인정보처리방침 (사이트, PR #6) 에 기록 파일 한 단락. 화면 사진 `ScreensTest.backup*` (bk1~bk5).

## 2026-10-18 추가 (금빛 결 · 바로 오늘로 · 시안 nav.html A)

- 금빛 결 (`Modifier.giltSweep`, `Primary(shine)`): 흐름상 다음 단추마다 (다음 걸음 · 계속 · 마침 · 복습 시작 (오늘 · 복습 탭) · 확인 · 받기 · 다시 받기 · 이어 읽기 · 낭독회). 띠 빛깔은 단추 바탕에 맞춰 (밝은 테마 짙은 단추 = 밝은 금, 어두운 테마 밝은 단추 = 짙은 금), 글자 아래, 4초마다 1.4초.
- 오늘의 편지 마침 한 장의 단추는 ‘오늘로 돌아가기’ (그 작품의 오늘, 예전 서재). 이해 확인 질문이 뜨면 지문 낭독을 한 번 저절로 (자동 낭독 설정).
- 앱을 켜면 마지막으로 읽던 작품의 오늘 (스택 서재 → 오늘). 처음 쓰는 사람 · 한 통도 안 연 사람만 서재. 오늘 맨 위: 다른 작품 소식 한 줄 · 초상 + “○○의 편지” + “…로 배우는 중 ▾”. 여러 통이 와 있으면 ‘와 있는 편지 N통’ + 나머지 한 줄씩. ‘목차 · 모든 편지 ›’. 서재 카드: 장 · 마지막 읽은 날 · 금빛 표 ‘새 편지 N통’ (읽기 시작하면 ‘읽는 중’). 새 편지 표시 (시안 newletter A+B): 오늘 띠 “볼프강에게서도 새 편지 1통이 왔어요 ›” 는 금빛 칸 + 금테, 새 편지가 처음 보일 때 한 번 내려앉기 + 종이 소리 (효과음 설정, `drop_seen` 에 기억) · 숨 쉬는 금빛 점 (`arrival` · `GiltDot`, 움직임 줄이기면 멈춤). 업데이트 안내 한 장은 서재 · 오늘 둘 다에서 (아래 띠는 서재만). 화면 사진 `ScreensTest.nav*` · `shine*` · `finish01_sheet_dark`.

- 결제 뒤 고마워요 한 장 (`ui/Thanks.kt`, `Billing.onThanks`): 결제 창에서 산 것이 PURCHASED · 확인(acknowledge) 된 뒤 한 번 (구매 토큰마다 `buy_thanked`). 구독 “이제 모든 작품의 모든 편지가 열렸어요”, 소장 “○○의 편지는 이제 평생 당신 것이에요”, ‘읽으러 가기’ → 그 작품의 오늘 (잠긴 편지 · 장에서 왔으면 그것을 연다, `afterPurchase`). PENDING 이면 구매 화면에 “결제가 확인되면 바로 열려요” (`buy_pending` 토큰, 나중에 refresh 에서 끝난 것을 보면 그때 한 장). 구매 복원 · 앱을 켤 때 다시 읽은 예전 구매에는 없음. 화면 사진 `thanks1` · `thanks2`.

## 2026-10-18 추가 (등급 다시 맞추기 · 처음 시작 · 시안 grade.html + onboard.html)

- 등급 이름은 화면에 이름만 (A1~B2 글자는 계산 안쪽): 낱말을 줍는 사람 · 문장을 읽는 사람 · 편지를 읽는 사람 · 답장을 쓰는 사람 + “N단” (en Word gatherer · Sentence reader · Letter reader · Reply writer). 나의 기록 · 등급 축하 · 메달 (제목 앞 A2/B1/B2 뺌, 받는 법 “○○ 되기”) · 작품 설정 ‘도움 한 단계 더’ 설명. grade_note 문장은 뺐다. 이름 함수 `gradeName` · `gradeStep` · `gradeLine` (`ui/Cabinet.kt`).
- 문턱: 낱말 0 · 20 · 45 / 75 · 100 · 130 / 170 · 215 · 260 / 310 · 360 · 400, 비율 A2 65 · B1 78 · B2 85%. 등급의 낱말은 **써 보기 이상** (`Growth.knownForGrade`, box ≥ 3) — `AppState.gradeWords` 가 등급 · 기록의 ‘다음 단까지’ 에만. 읽을 수 있는 비율 · 뜻풀이 · 혼자 읽기 · 기록의 낱말 수 · 도움 사다리 (`Support.KNOWN_BOX`) 는 그대로 떠올리기. 이미 오른 사람은 그대로 (바닥).
- 번역은 **접지 않는다**: `Reading.foldAt/folds` · `AppState.folds` · gradeup_fold 글자를 지우고 `Support.readAlpha` (A1 1 · A2 0.85 · B1 0.7 · B2 0.55) 로 옅게만 (`Room.kt` LetterSlip · SentencePairs). 작품 설정 이름 ‘실력만큼 번역 옅게’ (저장 열쇠 `s:<id>:fold` 그대로, 칸 이름 `fadeRead`).
- 도움 사다리 (`core/Scaffold.kt` Support): `interlinear` · `interlinearWord` (B2 아래 기본, 내 것이 아닌 낱말만) · `warmUp` (A1 · A2 ON, B1 OPTIONAL, B2 OFF) · `slowByDefault` — 낱말 아래 뜻 · 예열을 만드는 쪽이 읽는다. 이해 확인 지문 번역은 모든 단계에서 눌러서 (B2 포함).
- 처음 시작 세 화면 (`ui/StartSheet.kt`, `AppState.startAsk` · `needsStart` · `finishStart`): `chooseLearn` 뒤, 새로 쓰는 사람만 (`Store.startLegacy` = 이 버전 첫 실행 때 진도가 있었으면 묻지 않음, 그 말로 읽은 편지가 있어도 묻지 않음). 실력 → 시작 단계 (`Store.startGrade`, 바닥) + 천천히 듣는 기간, 왜 → 응원 한 줄 · 작품을 다 마친 마침 한 장의 ‘다음엔 ○○ 어때요? ›’ (`Start.suggest`), 하루 분량 두 가지 (`Memory.dueToday(daily)`: 12 · 20장). 작품 설정에 실력 · 하루 분량 · 천천히 듣는 2주 · 배우는 까닭.
- 천천히 듣는 2주 (`core/Slow`): 오늘 맨 위 `SlowCard` (어제 편지 다시 듣기 · 1분, `AppState.playYesterday`), 오늘 편지 카드 “먼저 0.7×로 한 번 들어요”, 편지 방에 있는 동안 기본 빠르기 0.7× (`listenSpeed` → `slowPassing`, 마지막 메시지 낭독이 끝나면 `slow:pass:<열쇠>`). 첫 편지 · 3일째 안내 한 줄 (`slowNote`, 마침 한 장 · 튜토리얼 끝 한 장).
- 들은 시간 (`core/Listening`, `Narrator.heard` · `ended`): 파일 이름 언어 꼬리로 언어마다, 재생한 만큼만 (`lis:<언어>` 모두 · `lis:<언어>:<날>` 8일). 나의 기록 `ListenBlock`, 오늘 이어 읽기 줄 옆 “오늘 N분”.
- 화면 사진 `ScreensTest.start*` (st1~st3 처음 시작 · st4 오늘 천천히 · st5 기록 들은 시간 · st6 등급 축하). 다른 사진은 `startLegacy(true)` 로 묻지 않게.
- 남은 것: 문턱 · 들은 시간은 출시 뒤 기록으로 다시 맞춘다 (어림값). 왜 → ‘그림이면 오늘 편지의 그림 먼저’ 같은 화면 바꿈은 아직 없다.

## 2026-10-18 추가 (입문자 길 · 시안 beginner.html ① ② ⑤)

- **낱말 아래 뜻** (편지 방, core `Interlinear` · ui/Interlinear.kt): 원문 낱말 묶음마다 아래에 짧은 뜻 (읽는 말, 사전 표제어 뜻의 첫 뜻 · 14자). 내 것 (`Growth.known`, `AppState.mine`) 이 된 낱말 · 이름 · 숫자는 뜻 없음, 기능어는 도움 등급 A1·A2 일 때만. 짧은 표현 카드 (4낱말까지) · 닻 표현은 한 칸 (금빛 밑줄). 사전에 없는 낱말은 옅은 점선만. 낭독 중인 문장의 뜻이 금빛. 맨 위 ‘낱말 아래 뜻’ 켜고 끄기 (작품마다 `s:<작품>:il`, 고르지 않으면 `Support.interlinear`: B2 아래 켜짐). 문장 번역 줄은 그대로.
  활용형 뜻 (pensé → 생각한) 은 데이터에 없어 **표제어 뜻** (생각하다) 을 쓴다. 꼴마다 뜻을 넣으려면 gloss 토큰에 뜻 칸을 더해야 한다 (다음 일).
- **편지 전 예열** (ui/WarmUp.kt, `Route.WarmUp`, `AppState.open` 이 처음 여는 새 편지에만): 카드 낱말 중 아직 모르는 것 앞쪽 3개 + 덩어리 1 (아직 안 익힌 닻 → 짧은 표현), 듣기 (카드 낱말 · 표현 소리, 없으면 숨김) → ‘따라 말했어요’ (믿고 맡김, 녹음 없음) → 점 진행 → ‘편지 열기 ›’. 작품 설정 ‘편지 전 예열’ = 등급에 맞춰 (도움 등급 A1·A2 켜기, B1~ 끄기) · 켜기 · 끄기 (`s:<작품>:warm`). 본 말은 편지 방에서 금빛 점선 + 위에 ‘아까 본 말’ (`wu:<편지 열쇠>`).
- **닻 표현**: `python3 scripts/anchors.py` (후보 `--candidates vincent [en|ko]`, 검사 `--check`) 가 손으로 고른 `scripts/anchors/<작품>[_en|_ko].txt` 를 gloss 로 세어 `data/<작품>/anchors*.json` (2통 이상만). 세 작품 × 세 말 × 25 = 225개 (2026-10-19 다시 고름: 입문자 문장 틀 — 나는 ~이다 · 가졌다/했다 · 이것은 ~이다 · ~이 있다 · 하고 싶다 · 할 수 있다 · 했다 · 너는 — 을 쓸모 순으로 먼저, 그다음 많이 되풀이되는 덩어리. 한국어 글은 어절 단위라 어미만은 못 맞추고 뒤 어절 (싶어 · 수 있어 · 거야) 을, 독일어 ich habe … gemacht 처럼 떨어진 틀도 못 맞춤.), 뜻은 앱이 쓴 것 (**사람 확인 전**, 파일 `"checked": false`). 닻마다 고정 이름 `id` = sha1(`<작품>[_말]/<k>`) 앞 8자 (anchors*.json, core `Anchors.id`) — 예열에서 배우면 `anc:<작품>/<자료>` 에 그 이름을 적고 복습 카드 (`CardRef` 꼬리 `a<id>`, 표현 카드처럼), 그래서 목록 순서를 고치거나 줄을 더하고 빼도 저장된 카드가 다른 닻을 가리키지 않는다 (덩어리 글자를 바꾸면 새 닻), 편지를 다 읽어도 카드로 들어간다. 익힌 닻은 편지에서 뜻 없이 금빛 바탕. 닻 소리는 없음 (듣기 숨김) — 필요하면 narration-r2 새 파일로.
- 테스트 도우미 (ScreensTest · NavTest `state()`) 는 예열을 끈다 (편지를 열면 바로 편지 방). 화면 사진 `ScreensTest.il*` (il1 · il2 편지 방 fr 밝음 · 어둠, il3 · il3b 예열, il4 닻 · 아까 본 말, il5 한국어 배우는 사람).

## 2026-10-24 추가 (업데이트 띠 · 기록 보관 방법 고르기 · 시안 update_backup.html)

- **업데이트**: Play 의 flexible 창을 저절로 띄우지 않는다. 서재 아래 우리 띠 (core `UpdateBand` · 상태 규칙 `UpdateFlow`, `UpdateFlowTest`): 새 버전 알림 “새 버전이 나왔어요” (+ Play 가 알려 줄 때만 “약 18.4MB”, 버전 이름은 Play 가 주지 않아 쓰지 않음) · 나중에 · 받기 → ‘받기’ 때만 Play 확인 창 (`Updates.start`) → 받는 중 % · 막대 → 다 받음 100% · 나중에 · 다시 열기 (`completeUpdate`). 실패 · 취소는 한 줄 + 다시 받기. 알림은 하루 한 번 (‘나중에’ · Play 창 닫기 = `updateAskedDay` 오늘), 다 받은 띠의 ‘나중에’ 는 다음에 앱을 열 때까지. 앱으로 돌아오면 (`onResume` → `Updates.check`) 받는 중 · 다 받음을 다시 맞춘다. 띠는 서재에서만, Play 설치가 아니면 띠 없음.
- **기록 보관**: 내보내기 시트 = 방법 카드 둘 (Google Drive 에 저장 = 공유 창, 휴대폰에 저장 = CreateDocument) + 할 일 한두 줄, 가져오기 = 먼저 “어디에 둔 기록을 가져올까요?” (Drive · 휴대폰, 둘 다 OpenDocument), 확인 시트는 ‘파일의 기록’ · ‘지금 폰의 기록’ 두 칸. 설정 줄 설명은 새 열쇠 `backup_import_d2` (`backup_import_d` · `backup_share` 는 이제 안 쓰지만 값은 그대로 둠).
- 화면 사진 `ScreensTest.ub*` (ub1 알림 · ub2 받는 중 42% · ub2b 실패 · ub3 다 받음 어둠 · ub4 내보내기 · ub5 가져오기 · ub6 확인). 예전 `updateReady` · `backupExport` · `backupImport` (u02 · bk2 · bk3) 는 이것으로 바꿨다.
- 남은 것: 실제 Play 내부 테스트 트랙에서 받기 → 다시 열기를 폰으로 한 번 확인해야 한다 (Robolectric 은 Play 없이 상태만).

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

- `data/napoleon/` 에 셋째 작품: 프롤로그 4 + 8장 × 7 + 맺음 1 = 61통 (tier 2 ₩4,900, `series_napoleon`, 무료 I · II장). 규격 v1 `spec_check.py napoleon` 통과.
- **`"hidden": true`** (series.json, 2026-10-17 지움): generate.py 가 앱 assets 에 넣지 않고 판매 규칙만 본다 → 서재의 ‘곧 도착’ 나폴레옹 카드 그대로. site_sources.py 도 건너뜀. 출시 때 이 칸을 지우고 generate.py.
- 원문은 지금 철자 (명백한 오자 · 옛 철자는 앱 글에 표시 없이 고침), 찍힌 글자는 메시지 `orig.fr` 에만. 이 원칙은 출처 화면 `credits_text` 와 사이트 출처 페이지 머리 한 줄로만 알린다 (편지마다 적지 않음).
- 어휘 (2026-10-17): lexicon*.json 을 편지 낱말 전부로 넓히고 **gloss*.json 을 만들었다** (fr 989 · en 986 · ko 1,079 표제어, 토큰 100%). 카드 줄은 그대로, 새 뜻 (fr 557 · en 383 · ko 454) 은 문맥에 맞춰 썼고 사람 확인 전. 만드는 법 `data/lexicon/README.md` 나폴레옹. Tatoeba 는 `fetch-tatoeba.yml` → `tatoeba` 가지.
  `validate_lexicon.py napoleon`: 글 · 사전 오류 0, 남은 59개는 예전 daily 카드 낱말 (수사 · 기능어 · 등급 구간 밖, 예 deux · six · thousand · 입맞춤 C2) — 규격 v1 `spec_check.py` 는 통과. 고칠지 사용자 결정.
  ko 글 표기 둘: 튀일리 궁 (V장) · 튈르리 궁 (VII장) 이 섞여 있다.
- 배역표 voices.json: 길 M3 → M4 → M5 (I 0.25 · E 0.75), `speed` 1.05 (narrate.py 가 인물 목소리에만 곱함), talk_me 조제핀 F3 · 마리 루이즈 F5 · 조제프 M2 · 외젠 M1 · 아들 M1 · 오르탕스 F4. **낭독은 아직 없다.**
- 그림 (plates `n-*.webp`, 아웃트로 `n-outro.webp`) 은 아직 받지 않았다: 후보와 `[미확인]` 표시는 편지마다 `plate.imageSource`. fetch-portraits.yml 로 확정 → 점(spots) 찍기. 지도 자리 x · y 는 임시값.
- c4-l5 (‘위대한 황후’ 놀림 편지) 의 ko 는 이 편지만 높임말 (나폴레옹이 tu 대신 vous 로 놀림) — 반말 원칙의 예외, 사용자 확인 필요 (`_note_register`).

## 2026-10-16 추가 (처음 쓰는 사람 15곳, 시안 firstuser)

- 오늘: 맨 위 한 줄 (`today_line`, 마치면 ✓) · 편지 마친 카드 아래 다음 편지 시각 (`today_next_at`, 알림이 꺼져 있으면 ‘알림 켜기’ → 권한 · `AppState.noticeOn`) · 상점 ‘내일 편지 미리 열기 ›’ (살 수 있을 때만) · 여정 완료의 남은 걸음 · 무료 장 끝 2~3통 전 한 줄 (`FreeLeftLine`, 목차 · 장 화면에도).
- 숫자 풀이: 아는 낱말 % (편지 방 머리) · 이어 읽기 (오늘 · 나의 기록) · 화폐 (머리) 를 누르면 `NumberNote` 두 줄.
- 첫날 빈 복습 = ‘첫 복습은 내일부터’ (카드가 모두 seen 0). 첫 건너뛰기에 한 번 토스트 (`Store.skipNoted`). 편지 완료 뒤 알림 권한 앞 한 줄 (`notice_pre_t`). 앱 설정 › 언어에 ‘작품 설정 · 이름’. 빈 갤러리 칸에 어디서 모이는지.
- 안 한 것: 첫 편지 ‘눌러서 다음 문장’ — 편지 방은 문장이 저절로 이어져서 (누르는 넘김이 없다) 쓰지 않았다. 나의 기록의 등급 막대 · ‘다음 단까지 낱말 N개’는 이미 있었다.
- 화면 사진: ScreensTest `first01`~`first04` (아직 안 찍음). ScreensTest 의 state() 는 이제 알림 권한을 이미 물은 것으로 둔다.

## 0.9.5 할 일 (2026-10-17 사용자 테스트)
- **[했음]** 편지 방: 큐레이터 노트가 마지막 메시지 낭독이 끝나기 전에 떠서 화면을 가린다. 마지막 메시지의 마지막 문장 낭독이 **끝난 뒤** 노트를 띄운다 (스크롤도 그때). → `Room.kt` `heardOut` (도착 고리의 마지막 낭독 join 뒤에 켬, 소리 끔 · 낭독 없음이면 바로).
- **[했음]** 낭독 밑줄 싱크: 메시지가 길거나 한 칸에 문단이 2개 이상이면 둘째 문단부터 밑줄(지금 읽는 곳)이 소리와 어긋나는 곳이 많다. 타이밍 파일 · 문단 나눔 · 오프셋 계산을 전수 점검 (빈센트 · 모차르트 · 나폴레옹). → 까닭: 타이밍 json 의 마디는 “//” 자리에서만 나뉘어 한 마디에 문장이 여럿 (메시지 2,493개 중 마디가 문장 둘에 걸친 것 500개, 번역 줄 · speak 없는 언어는 메시지 전체가 한 마디) → 그 마디 내내 둘째 문단까지 밑줄. 글자 수 · 소리 길이는 모두 맞음 (2,493/2,493). core `Spoken.timed` 가 마디 안을 문장으로 나누고 목소리 몫을 글자 비율로 나눔 (토막 4,665 → 5,379, 걸침 500 → 0, 차례 · 범위 어긋남 0).
- **[했음]** 다음 단추 빛 (시안 A 확정, scratchpad audit/shimmer.html): 각 화면의 흐름상 다음 걸음 단추 하나에, 누를 수 있는 동안 언제나, 금빛 결이 4초마다 왼쪽→오른쪽으로 지나감 (둘째 단추 · 밑줄 글자는 없음, 움직임 줄이기면 멈춤). Primary 의 wake/glow 를 이것으로 바꾼다. → `Primary(shine = true)`.
- **[했음]** 별자리 잇기: 다음 별의 숨 쉬는 빛 고리가 지금은 처음 안내(guided) 때만 나온다 → 언제나 (단추 금빛 결과는 다른 성격, 그대로 둠). ui/Play.kt hint.
- **[했음]** 표현 카드: ‘편지 속 문장’ 원문 글자가 너무 작다 → 번역 줄과 같거나 더 크게.
- **[했음]** 대화(Talk) 말하기 = ‘듣고 따라 말해요’ 와 **완전히 같은** 다시 듣기 · 말 알아듣기 · 채점 체계 (같은 코드를 쓴다, 대화용 인식 경로 · SELF 판정 단계는 없앤다). 왜 대화에서만 인식이 SELF 로 떨어졌는지도 찾는다 (사용자 결정 2026-10-17). → 따라 읽기의 부품 `ShadowTake` · `ShadowBoard` · `ShadowControls` (`ui/Shadow.kt`) 를 회화 내 차례가 그대로 씀 (녹음 `files/talk/…`). SELF · 폰 음성 인식(Ear) 은 지움. 까닭: 따라 읽기는 기기 안 녹음 + Prosody 채점이라 인식 서비스가 필요 없고, 회화는 `SpeechRecognizer` (PREFER_OFFLINE) 를 써서 그 언어의 오프라인 인식이 없거나 오류(언어 없음 · 못 알아들음 · 바쁨)가 나면 모두 onError → SELF 로 떨어졌다.
- **[했음]** 말하기는 믿고 맡긴다 (사용자 결정 2026-10-17: 시험이 아니라 스스로 배우는 앱, 낱말 알아듣기는 넣지 않음). 답장 뒤 ‘소리 내어 말해 보기’(`SpeakLine`) · 복습 말하기(`SpeakPane`)도 `ShadowTake` · `ShadowBoard` · `ShadowControls` 로 (인식 · 스스로 판정 · 글자 8개 · core `SpeechMatch` 지움). 통과 = 목소리가 들어간 녹음: core `Prosody.spoke` (음높이 잡힌 소리 250ms 이상 · 가장 큰 소리 −45dB 위) — 침묵 · 잡음 · 툭 소리는 ‘다시 해 봐요’. 따라 읽기 편지 걸음 · 회화도 같은 기준 (예전에는 파일만 있으면 · 점수 > 0 이면 통과). 복습 말하기는 원래 소리를 답이 나온 뒤에만, ‘정답 보기’ = 틀림. 녹음 `files/talk/…/r<턴>.wav` · `cache/speak/take.wav`.
- **[했음]** 나폴레옹 카드 낱말: 장 구간보다 어려운 낱말은 그대로 (validate_lexicon 은 알림만, 빈센트 · 모차르트도 같은 규칙). 숫자 · 요일 · 기능어 34+3개를 같은 편지의 내용어로 바꿈 (fr 13 · en 18 · ko 6, 새 ko 카드 4개에 발음 줄). 오류 0, 규격 v1 통과.
- **[했음]** 튀일리 (프랑스 소리 [tɥilʁi] 에 가까운 꼴) 로 통일: VII · VIII장 · series · 사전 · gloss (사전 ko 두 줄 → 하나, fr IPA tɥilʁi). 낭독에 그 낱말이 든 것은 c7-l3 m2_ko · t3_ko 둘 → narration-r2 make=messages 로 새 판 (rev/2/…). narrate.py messages 목록에 회화 줄(t<줄>_<언어>)도 적을 수 있게 했다.
- **[했음]** 나폴레옹 공개 (사용자 확인 2026-10-17: 목소리 · 공개): series.json `hidden` 지움 → generate.py 가 assets/letters/napoleon 을 만들고 remote.json 처음 묶음 P+I+II. 새 카드 낱말 37개 소리는 narration-r2 make=new 로 (새 파일만). 사이트 출처 페이지(site_sources.py)는 아직 다시 돌리지 않음. 화면 사진 ScreensTest `napoleonLibrary` · `napoleonCover`.
- (예전 메모) 해제하면 remote.json 처음 묶음 P+I+II (25.6MB), 그 뒤 III~VIII · E — 모의로 확인함 (대표 그림 n-c1-l1 만 앱 안).

## 홈페이지 (graviton94.github.io · 당신의 하루) — 언어 미술관은 이 세션이 맡음
- 공통 틀 · 앱별 할 일: 사이트 저장소 docs/for-app-sessions.md. 출처는 scripts/site_sources.py 로만 다시 쓴다 (표시 밖 머리 · 꼬리는 남음).
- 스토어 공개되면 첫 화면 언어 미술관 줄에 Google Play 링크 (id=io.github.graviton94.todayletters).
- 판매자 프로필: 신용카드 명세서 이름 ‘당신의 하루 스튜디오’, 제품 유형 ‘인터넷 디지털’ (사용자 2026-10-18).

## 2026-10-18 추가 (답장 말해 보기 원래 소리)

- 폰 버그: ‘소리 내어 말해 보기’ 의 원래 소리가 늘 비어 있었다 (`SpeakLine(s, null, …)`, 답장 낭독 파일이 없었음).
- 답장 턴마다 언어마다 `r<턴 1부터>_<언어>.m4a` (편지 폴더, 회화 "me" 줄과 같은 talk_me 목소리 · 차분한 프리셋). `narrate.py NARRATE_ONLY=replies`, `narration-r2.yml make=replies` (새 파일만, 회화 tk* 같은 다른 빠진 소리는 만들지 않음). 앱 안 장(빈센트 I) 은 assets, 나머지는 remote/.
- 앱: core `Exercises.replyAudioName` (답장이 없는 서문 편지는 대신 말하는 메시지 낭독 m<번호>). 복습의 답장 카드도 같은 소리 (`Word.audio = "r<턴>"`). 원래 소리가 없으면 듣기 단추를 숨긴다 (`ShadowControls`, 따라 읽기 · 회화 · 복습 말하기 공통).
- 답장 문장을 고치면 새 이름으로 내야 한다 (R2 덮어쓰기 금지): 지금은 앞부분 "r" 고정이라, 고칠 때 `replies[].audio` 같은 칸을 더한다.

## 2026-10-18 추가 (받기 빠르게: 장 묶음 zip)

- 장 묶음마다 zip 하나 (`generate.py pack_zip`, 압축 없음 · 늘 같은 바이트). remote.json 장마다 `"pack": [packs/<작품>/<장>-<sha256 앞 16자>.zip, 길이, sha256]` + 검사용 `"files"` 그대로. 이름에 합이 들어가 내용이 바뀌면 새 이름 (R2 덮어쓰기 금지). 고친 파일 (revisions) 은 zip 안에 새 판 내용으로, 하나씩 받기는 예전처럼 rev/<판>/ 주소.
- `narration-r2.yml` 올리기가 `generate.py --check --packs` 로 zip 을 만들어 packs/ 로 올린다 (저장소에는 넣지 않음).
- 앱 `Downloads.fetch`: zip 하나를 이어 받기(Range) → sha256 → files/.packs/<이름>.x 에 풀며 이름 · 크기 검사 → 다 맞으면 제자리로 (core `Packs`, `PacksTest`). 깨지면 지우고 다시. R2 에 zip 이 없으면 (404 · 403) 그 묶음은 파일을 하나씩 (예전 길).

## 2026-10-19 추가 (단계성 보강)

- **오늘의 한 조각 = 오늘의 회화 내 줄**: 회화 줄이 장마다 짧게 → 길게 (말하기 계단) 다시 쓰여서, 따라 읽기 걸음 (`ui/Shadow.kt` `ReadAlong`, 편지 걸음일 때만) 은 그 편지 회화의 “me” 줄을 먼저 (원래 소리 = 회화 소리 `t<줄>_<말>`, `Talk.audioName`), 그다음 예전처럼 편지 문장 하나. 차례는 core `ShadowLines.order` (`ShadowSpot`, 통과 · 별 꼬리 “t<줄>:0”, 녹음 `readings/<편지>/t<줄>.wav`). 봉인까지 가는 전체 따라 읽기 (`Route.Shadow`) 는 편지 문장만 그대로. 채점 (`Prosody.spoke`, 목소리가 들어가면 통과) 은 그대로.
- **예열에 ‘오늘 말해 볼 말’**: 낱말 · 덩어리 뒤에 회화 내 줄 앞 두 줄 (core `WarmUp.Kind.TALK`, `WarmUp.TALK = 2`), 회화 소리로 듣기. 본 것 꼬리 “t<줄>”.
- **기초 낱말 덱** (`data/<작품>/basics[_en|_ko].json`, core `Basics` · `BasicDeck` · `BasicItem`, 여러 낱말 표제어는 `k` 가 top 의 표제어): 카드 꼬리 `b<id>` (`CardRef.basic`, 열쇠 “<작품>:B:basics:[<자료>/]b<id>”), 낱말 카드와 같은 상자 · 간격. 오늘의 복습 한 판에 새 기초 낱말 12장이면 2~3 (볼 카드가 10장 이상이면 2), ‘복습 더’ 20장이면 4 (`Basics.session` · `perDay`), 덱 차례대로, 판을 새로 시작할 때 카드함에 들임 (`AppState.startReview`, 하루 한 번 — 이번 주 기록 `log:basic`). 앱 쪽은 `ui/Basics.kt` (덱 읽기 · 예문 한 줄짜리 편지 · 카드 칸 `BasicCardPanel`: ‘기초 낱말’ · 큰 낱말 · 품사 · 듣기, 답이 나면 뜻, 예문 칸 ‘편지에서’ / ‘예문’ + “앱이 쓴 문장이에요”, 번역은 앱 글자 언어). 사전에 같은 표제어가 있으면 그 열쇠로 (내 것 · 등급 · 낱말 아래 뜻이 같이 센다).
- 소리: 낱말 `b_<말>_<id>.m4a` · 예문 `bx_<말>_<id>.m4a`, `audio/<작품>/B/basics/` (`remote/` → R2 묶음 “B”, generate.py). 앱은 다른 장을 받을 때 함께 받는다 (`Downloads.needed` · `all`). 앱 안 장만 읽는 빈센트 I장 사용자는 II장 (또는 모두 받기) 때 받는다. 만들기는 narration-r2 `make=new` (서문 작업에서 작품마다 한 번, 새 파일만). **아직 만들지 않았다.**
- 나의 기록 › 언어 등급 아래 “기초 낱말 N / 300 · 많이 쓰는 300개 중” (`basicsKnown`: top 표제어 가운데 써 보기 이상, 낱말 카드 · 기초 낱말 카드).
- `spec_check.py` 가 basics 파일을 검사 (id = sha1(“<작품>[_말]/<w>”) 앞 8자, k ⊆ top, 뜻 · 예문 언어, from). generate.py 가 basics*.json 을 assets 로 넣는다 (**아직 돌리지 않음** — 데이터 작업이 끝난 뒤 `python3 scripts/generate.py` · `build_fonts.py --check`).
- core 테스트 `BasicsTest` (덱 읽기 · 없는 파일, 12 → 2~3 · 20 → 4, 하루 한 번, 기록 수, 따라 읽기 차례, 예열 회화 줄). 화면 사진 `ScreensTest.basic*` (basic1 카드 · basic2 맞힌 뒤 · basic3 기록) · `warmUpTalk` (il6) · `shadowTalkLine` (sh1) — 아직 사람이 본 적 없음.

## 2026-10-19 추가 (말하기 · 소리, 시안 vox — 화면 사진은 아직)

- **말하기 검사 = 음절** (말 알아듣기 · 길이 규칙 없음): core `Prosody.syllables` (목소리 프레임의 크기 곡선 50ms 다듬기, 봉우리 사이 100ms · 골 3dB, 가장 큰 소리 −25dB 아래 봉우리 뺌) · `SpeakCheck` (통과 = `Prosody.spoke` 이고 |내 음절 − 원래 음절| ≤ 원래의 40%, 짧은 말은 적어도 한 음절 봐줌). 원래 = 원래 소리에서 센 음절, 원래 소리가 없으면 (내 이야기로) 글자로 어림 (`SpeakCheck.estimate`: fr · de · en 모음 덩어리, 끝의 소리 없는 e 뺌, ko 한글 글자 수).
  어긋나면 ‘조금 더 또박또박 · 끝까지 말해 봐요’ (`speak_clearer`) 로 다시, 같은 칸 두 번째 실패는 목소리만 들어가면 통과 (사용자 결정 가). 한 곳 `ShadowTake` (ui/Shadow.kt) 라 따라 읽기 편지 걸음 · 회화 · 답장 말해 보기 · 복습 말하기 · 떠올리기 · 내 이야기로가 다 같다. 예전에 녹음해 둔 파일 (다시 들어온 칸) 은 목소리만. 봉인까지의 전체 따라 읽기는 예전처럼 점수 55. 예열은 녹음이 없어 해당 없음.
- **비슷하게 읽으면** (자동 · 어림): 두 번째로 안 된 뒤 (봐주기 전 판정) 원래 소리 단추 위에 한 줄, 그 뒤로 늘 (`SayLikeLine`, core `SayLike`). 한국어 앱 글자 + fr · de · en 은 한글, 영어 앱 글자 + ko 는 로마자. 만들기는 `scripts/pron.py` (espeak-ng IPA → 언어별 표로 한글 · 한국어는 연음 · 비음화 · 유음화 · 경음화 · 격음화 · 구개음화 뒤 로마자, ㄴ 첨가는 안 함) → `data/<작품>/pron[_en|_ko].json` (`"auto": true`, 글 그대로 + 낱말마다; 앱은 글이 없으면 낱말 힌트를 이어 붙이고, 하나라도 없으면 숨김). **사람이 본 적 없는 어림 표기.** 시험 `python3 -m unittest scripts/test_pron.py`.
  generate.py 가 pron 파일이 없는 작품은 만들고 (`--pron` 이면 모두 다시, espeak-ng 필요 — CI 에서 돌릴 때는 `sudo apt-get install -y espeak-ng`), assets 로 넣는다. 데이터 (회화 · 답장 · mine) 를 고치면 `python3 scripts/pron.py` 다시. 새 한글이 많아 `build_fonts.py --check` → 글꼴 다시 필요.
- **어제 말 떠올리기**: 오늘 화면 맨 위 카드 (`RecallCard`, ui/Recall.kt) — 지난 공부한 날 (이 작품에서 편지를 마쳤거나 회화를 한 날) 회화를 했으면 그 편지의 내 줄 앞 둘. 퀴즈 (`Route.RecallQuiz`): 뜻 (앱 글자 언어) → 2~4 조각 (core `Recall.chunks`: 문장부호, 아니면 낱말 묶음) + 다른 내 줄의 조각 하나를 차례대로. 맞으면 줄 + 회화 소리 + ‘소리 내어 말해 보기’, 틀리면 답 · 끝에 한 번 더. 기록 `log:talk` (회화를 마친 날 · 편지 열쇠, `replyAndContinue`) · `log:recall` (마친 날 · 진도 이름). ‘어제 편지 다시 듣기’ 카드는 그대로.
- **이번엔 내 이야기로**: 회화의 `"mine"` (Library 가 읽음, core `TalkMine` · `Mine`) 이 있으면 회화 ‘마치기’ 뒤 한 장 (ui/Mine.kt `MineStep`): 빈칸 문장 · 보기 셋 → ‘말해 보기’ (글자로 어림한 음절과 견줌) · ‘지금은 소리 내기 어려워요 · 건너뛰기’. 고른 문장은 복습 카드 ‘내가 만든 말’ (`CardRef.mine`, 꼬리 `n<보기>`, 답장 카드처럼 문장, 소리 없음). 다시 하기에서는 안 뜸.
- **이번 장의 소리**: `data/<작품>/sounds[_en|_ko].json` (core `Sounds`, generate.py 가 assets 로) — I~VIII 장 첫 편지를 처음 열 때 장마다 · 배우는 말마다 한 번 (`Route.SoundCheck`, ui/Sounds.kt, prefs `snd:<진도 이름>`), 예열 · 편지 앞. **예열을 하는 사람에게만** (`warmOn`, 테스트 도우미는 예열을 끔) · 그 장 낱말 소리가 기기에 다 있을 때만. 소리 `s_<말>_<slug>.m4a` → `audio/<작품>/S/sounds/` (R2 묶음 “S”, `Downloads.EXTRAS` 로 기초 낱말 “B” 처럼 함께 받음, narrate.py `NARRATE_ONLY=new` 서문 작업 · narration-r2 make=new). **아직 만들지 않았다** → 그때까지 이 화면은 뜨지 않는다.
- spec_check.py: `mine` (내 줄 · slot 이 줄에 그대로 · opts 3 세 언어) · `sounds` (lang · checked · I~VIII · 4쌍 · name/tip 은 덱의 말 뺀 ko/en · mean 은 쌍 안 낱말).
- core 시험 `VoxTest` (음절: 소리 다섯 → 5 · 긴 소리 → 1 · 침묵 → 0 · 이어진 소리 안 봉우리 넷 · 100ms 안은 하나, 판정 규칙, 글자 어림, 조각 · 차례 · 한 번 더, mine 문장 · 카드 열쇠, sounds 읽기 · 장마다 한 번 · 네 판, SayLike 찾기). 화면 사진 `*ScreensTest.vox*` (vox1 또박또박 · vox2 비슷하게 읽으면 · vox3 오늘 카드 · vox4/5 떠올리기 · vox6 내 이야기로 · vox7/8 이번 장의 소리) — 아직 안 찍음. assets 에 아직 mine · sounds 가 없으면 시험용 데이터로 그린다.
- **generate.py 를 아직 돌리지 않았다** (데이터 작업 중): 글자만 다시 만들었다 (strings.xml). 데이터가 끝나면 `python3 scripts/generate.py` (pron 은 이미 있으면 그대로, 데이터를 고쳤으면 `--pron`) · `build_fonts.py --check`.

## 2026-10-23 추가 (편지 카드 공유, 시안 Gbowc2Rqd6VJJpfmMPP6jC 2 — 화면 사진은 아직)

- **편지 카드**: 너비 1080px, 높이는 내용만큼 (말줄임 없이 메시지 전체 · 사용자 고침). 위에 그 편지의 그림 (4:3 으로 자름, 긴 그림은 위쪽 35% 자리) → 원문 (배우는 말, 세리프) → 번역 (앱 글자 언어, 같은 말이면 없음) → 가는 줄 → “빈센트 → 테오 · 1888. 2. 21.” / 오른쪽 ‘언어 미술관’ (길면 다음 줄) → 작은 줄 작가 · 제목 · 연도 (image-policy 7, 빈센트 그림은 credit 이 비어 작품 fullName). 밝은 종이 · 어두운 두 가지는 앱 테마 (`Ink.isDark`).
- 그리기 `ui/LetterCard.kt` (`letterCard` Canvas · `rememberLetterCard` · `LetterCardImage` · `LetterCardSheet` 미리 보기 + ‘카드로 보내기’ · `shareLetterCard` PNG → cache/postcards (FileProvider) + 한 줄 글과 스토어 주소 `BuildConfig.APPLICATION_ID`). 글 · 날짜 · 자르기는 core `LetterCard` (`LetterCardTest`).
- 여는 곳: 편지 방 문장 길게 누르기 (`Modifier.longPressCard`, 다 써진 문장만, 화면 읽기는 ‘길게 누르기’ 동작) · 내 구절 구절마다 아래 ‘카드로 보내기’ 줄 · 봉인한 편지 ‘이 편지 카드’ (첫 메시지, 목소리 엽서 단추 아래). 기존 ‘엽서로’(작품 카드) 는 그대로.
- 글자 키 새로: `letter_card_title` · `letter_card_send` · `letter_card_seal` · `letter_card_text` (strings.xml 만 다시 만듦).
- 화면 사진 `ScreensTest.cardShare1` (밝은, 가장 긴 메시지) · `cardShare2` (어두운, 첫 메시지) — 카드 그림만 바로 그린다. 아직 안 찍음.

## 2026-10-23 · 나폴레옹 날짜 기준 (역사 검토 답변)
- 디도(1833)와 Hall(1901) 날짜가 다르면 **Hall (및 Fondation Napoléon 『Correspondance générale』)** 을 따른다. 디도의 인쇄 연도는 쓰지 않는다 (편집자가 연도 없는 편지에 임의로 붙인 것이 많음).
- VII-7 (디도 CCXXVIII, 'Vendredi, huit heures du matin'): Hall 대로 1811. III-3 (디도 'an X'): Hall 대로 1801 (an IX) 여름 플롱비에르.

## 2026-10-23 추가 (고야 시리즈 · 서문 + I장)

- `data/goya/`: 넷째 작품 (스페인어 원문, 배우는 말 es · en · ko). 지금은 **서문 4 + I장 7 = 11통** (편지 목록은 사용자 승인한 goya_plan, 49통 중). 나머지 II~VI장 · 보르도 맺음 3통은 다음 단계. `spec_check.py goya` 통과.
- 원문 두 겹: `es` = 지금 철자 (앱 글), `orig.es` = 판본에 찍힌 그대로. series.json `"origLine": true` 면 편지 방 문장 아래 ‘원래 철자 ›’ 접힌 줄 (누르면 펼침, 문장부호 · 대소문자만 다르면 줄 없음). core `Message.orig`, `Library.parseLetters(origLang)`, `Room.kt OrigLine`, 글자 키 `orig_spelling`. 나폴레옹의 orig.fr 은 그대로 숨김 (origLine 없음).
- 판매: `series_goya`, 지금 편지 수에 맞춰 **tier 1** (generate.py 판매 검사). 49통이 다 들어가면 tier 2 로 (Play Console 상품 등록은 출시 때).
- 배역 voices.json: 고야 M4 → M5 (age 0.4 = M4 0.6 + M5 0.4), speed 1.02 (견본 0.92 ÷ 기본 0.90), talk_me 마르틴 M2 · 반데르고텐 M1 · 호세파 F2 (speed 0.94, pitch 0.96). narrate.py: talk_me 항목이 {voice, speed, pitch} 일 수 있고, 회화의 `"me"` (p4 "Josefa") 가 편지 "to" 보다 먼저. `NARRATE_ONLY=new` + SKIP_EXISTING 이면 빠진 메시지 낭독 m<번호> 도 만든다 (새 작품 첫 낭독). narration-r2 넣기 단계는 마디 시각 m*.json 도 옮긴다.
- 어휘: `scripts/vocab/src/es_tab.txt` · `es_map.txt` · `en_map_goya.txt` · `ko_map_goya.txt` · phrases_goya*.txt · daily_override_goya.json · example_drop_goya.txt. Tatoeba spa 는 `tatoeba_spa` 가지 (fetch-tatoeba). ko 카드 발음은 다른 작품과 같은 낱말은 그쪽 것, 나머지는 pron.py 규칙으로 자동 [소리] · 로마자 (사람 확인 전). tok.py · basics.py · validate_lexicon.py 는 아직 없는 장 파일을 건너뛴다.
- 그림: 서문 · I장 그림 11 + 대화 속 사진 11 (`remote/plates/g-*` · `gm-*`, PD-Art 는 `exception`), 대표 그림 `plates/g-c1-l2.webp` (〈양산〉) · 초상 `portraits/goya.webp` (〈카프리초스〉 1번, Met CC0) 만 앱 안. 검색어 `data/images/gplates*_queries.json` (fetch-portraits → gplates · gplates2 가지).
- 사람 확인 전: 편지 글 `verified: false` (판본 쪽 그림과 마지막 대조), 앱 번역 · 회화 · 답장 · 기초 낱말 · 소리 · 닻 뜻. 출처 사이트 (site_sources.py) 는 아직 다시 돌리지 않음.

### 고야 2단계 (2026-10-23): 49통 모두
- series.json: II~VI장 (VI = Sordo · The Silent Years · 들리지 않는 날들), 맺음 아웃트로 (g-outro.webp 〈아직 배우고 있다〉, Prado · PD-Art, 본문 세 단락), 곳 aranjuez · arenas · san_francisco_el_grande · burdeos, 새 받는 사람 말투 (위원회 · 알루에 · 플로리다블랑카 · 베나벤테 · 솔레르 · 페레르 · 모라틴 · 하비에르), **tier 2**. 처음 묶음 P · I · II.
- 맺음 편지가 셋 (보르도 e1~e3): 앱 `Outro.epilogues` (예전 `epilogue` 는 첫 편지), 아웃트로 화면이 차례로 보여 주고 둘째부터 날짜 · 곳 한 줄, 답장 단추는 `Route.Letter(작품, EPILOGUE, n)`. `AppState.letterOf` · 카드 낱말 찾기 · 출처 화면도 셋 다. 다른 작품은 하나 그대로.
- 맺음 첫 단락: “하비에르가 도착하고 며칠 뒤” 는 기록이 엇갈려 쓰지 않았다 (e3 노트도 같은 이유로 고침) — 사용자 확인.
- c6-l2: 지금 철자 venirme, 찍힌 글자 heñirme (orig.es, excerpt 에 적음). IV~VI · 맺음 복습 턴 8개를 II~V 문법으로 (c4-l1 III.0 · c4-l2 II.2 · c4-l4 II.0 · c4-l7 III.1 · c6-l1 V.1 · c6-l3 III.0 · c6-l7 IV.1 · e2 IV.0).
- 어휘: es_tab +678 · en_tab +260 · ko_tab +330 (사람 확인 전). 카드 낱말을 장마다 5–8 · A1–A2 62% 로 맞춘 것은 `scripts/vocab/src/goya_cards/` (apply_depth.py 로 넣음, daily.py 를 다시 돌리면 다시 넣을 것). 예문 거르기 88개 `example_drop_goya.txt`.
- 이번 장의 소리 I~VI (es: ñ/n · r/l · 강세 · b/p · g/k, en · ko 는 나폴레옹과 같은 차례).
- 소리: narration-r2 make=new (편지 낭독 · 회화 · 카드 · 기초 · 소리) 와 make=replies 를 goya 로 돌려 R2 에 올림 (remote/audio/goya). 화면 사진 `ScreensTest.goya*` g01~g06 (g05 IV-1 편지 방, g06 맺음 아웃트로).
- 남은 것: 낱말 아래 뜻의 동형어 real (왕립의 / 레알 돈) 을 가르지 않아 “quince mil reales” 아래 ‘왕립의’ 로 보인다 (es_occ 로 real#2 를 나눌 것). 출처 사이트 (site_sources.py) 아직.

## 2026-10-24 폰 시험 고침 (말하기 판 · 걸음 흐름 · 다음 편지)

- **판정 한 곳** core `SpeakCheck.judge(spoke, 내 음절, 원래 음절, misses, score)`: 통과 = 목소리 AND (음절 맞음 OR 억양 · 리듬 점수 ≥ `Prosody.PASS` 55), 같은 칸 두 번째 실패는 목소리만 (사용자 결정 가 그대로). 점수 91 인데 ‘또박또박’ 으로 막히던 것 (음절 규칙만 보고 점수는 안 봄) 고침. `ShadowTake.passed` 가 판의 칭찬 한 줄 · 안내 한 줄 · 다음 단추 · 효과음 모두의 기준 (안 된 녹음엔 ‘훌륭해요’ 대신 ‘다시’ 한 줄).
- 녹음은 `<파일>.rec` 에 받고 끝낼 때 0.3초 이상이면 바꿔 넣는다 (아니면 지난 녹음도 지움 → 점수 없이 ‘다시 해 봐요’). 채점은 녹음을 끝낼 때만 (`tick`), 듣기는 판정을 바꾸지 않는다. 목소리가 없으면 점수 판을 보이지 않는다 (0점 없음). 다시 들어온 칸은 점수 기록 · 효과음 없이.
- 화면 선 core `Prosody.line` / `curves`: 작은 소리 프레임 버림 · 이웃 가운데에서 6반음 넘게 튀면 옥타브 접기 (아니면 버림) · 7프레임 중앙값 · 60ms 안 되는 조각 버림 (선이 끊김), 내 선은 크기 흐름 DTW 로 원래 시간축에 맞춤, 두 선 같은 반음 폭 (3~12) 으로 -1..1. 채점 (`score`) 은 그대로.
- 이해 확인: 맞히면 낭독을 멈추고 효과음 · CONFIRM 진동 · 금빛 원 (`CorrectBurst`) + ‘맞았어요.’ (복습과 같은 부품).
- 걸음을 마치면 편지 방으로 돌아가지 않고 바로 다음 걸음 (`AppState.stepEnded`, 방에서 들어온 걸음만 · 마친 편지 다시 하기는 그대로). 방은 뒤에 남아 × 로 편지로.
- 회화: 채점 판이 생기거나 녹음을 시작할 때 끝 자리가 바뀔 때마다 아래로 따라감 (판이 아래 칸 뒤로 반쯤 가려져 멈추던 것).
- ‘다음 편지 열기’: 방금 마친 편지 **뒤의** 첫 안 마친 편지 (core `Arrivals.next`, `AppState.nextLetter`). 예전에는 앞쪽 첫 안 마친 편지라 읽다 둔 편지가 끝 부분부터 열렸다.
- 시험: core `VoxTest` (점수로 통과 · 옥타브 접기 · 시간 맞춤) · `CoreTest.nextLetterIsAfterCurrent`, `NavTest.stepGoesStraightToNextStep` · `nextLetterOpensAtStart`. 화면 사진 `ScreensTest.fix*` (fix1 점수 91 통과 · fix2 낮은 점수 · fix3 녹음 없음).

## 2026-10-24 추가 (말하기 단추 · 시안 rec_controls 2 ABCD)

- **A 단추 셋** (`ui/Shadow.kt` `ShadowControls`, 따라 읽기 · 회화 내 차례 · 답장 말해 보기 · 복습 말하기 공통): 원래 소리 56dp · 말하기 80dp · 내 소리 56dp 를 한 줄 (92dp 칸 가운데라 높이 · 이름표 줄이 같음). 떠 있던 0.7× 동그라미 대신 원래 소리 아래 칩 “1× · 0.7×” (`ShadowTake.slow`, 고른 쪽 금빛 글자 · 옅은 금빛 바탕) — 원래 소리 · 처음 저절로 듣기 · 비교 듣기가 칩 빠르기로. 말하기는 빨강 대신 금테 · 금빛 점, 녹음 중 금빛 바탕 + 종이색 멈춤 네모. 녹음 중에는 원래 소리 · 내 소리 흐림.
- **B 녹음 고리 · 저절로 멈추기** (core `AutoStop`, `AutoStopTest`): 고리는 첫 목소리부터 원래 소리 길이 (앞뒤 침묵 뺀 것, `Prosody.preview().ms`; 원래 소리가 없으면 글자로 어림한 음절 × 250ms, 그것도 없으면 고리 없음) 만큼 차오름 (움직임 줄이기면 프레임 값 그대로). 저절로 멈춤 = 목소리 들어감 (음높이 + −45dB 위 250ms) AND 첫 목소리부터 원래 길이의 80% 지남 AND 그 자리부터 1초 조용 (−45dB 아래 또는 이번 녹음 가장 큰 소리 −20dB 아래). 80% 전의 쉼으로는 멈추지 않음. 상한 원래 × 2 + 3초 (모르면 15초). 직접 멈추기 그대로. 녹음 중 안내 ‘듣고 있어요 · 다 말하면 저절로 멈춰요’ (`shadow_listening_auto`, 길이를 모르면 예전 글).
- **C 비교 듣기** (`ShadowTake.compareListen` · `compare`): 녹음이 있고 원래 소리를 들을 수 있으면 내 소리 자리가 ‘비교 듣기’ — 원래 소리 → 내 소리, 다시 누르면 멈춤, 위 한 줄 “비교 듣기: 원래 소리 → 내 소리” 에서 지금 쪽이 금빛. 녹음 · 판정 (`tick` · `voice` · `result`) 은 건드리지 않는다 (채점은 녹음을 끝낼 때만, 그대로).
- **D 미리 보는 원래 선**: core `Prosody.preview` (채점 뒤 `curves` 의 원래 선과 같은 칸 · 다듬기 · 폭) 를 칸마다 한 번 읽어 녹음 전부터 판에 점선 + ‘선을 따라 오르내리듯 읽어 보세요.’. 녹음 중 내 선은 core `LiveLine` (첫 목소리부터 시간 / 원래 길이, 원래 선과 같은 반음 폭) 으로 점선 위에. 채점 뒤 두 선은 예전 `curves` (내 선이 원래보다 넓으면 그 폭으로 조금 줄어듦). 답을 아직 못 본 복습 말하기 · 회화 빈칸 · 조각 전에는 점선을 보이지 않는다 (`ShadowBoard(preview = …)`).
- 글자 키 새로: `shadow_compare` · `shadow_compare_line` · `shadow_follow_line` · `shadow_listening_auto` · `shadow_speed` (기존 키 값은 그대로, strings.xml 만 다시 만듦, 글꼴 검사 통과).
- 화면 사진 `ScreensTest.abcd*` (abcd1 녹음 전 · abcd2 녹음 중 · abcd3 비교 듣기 중 · abcd4 채점 뒤 0.7×).

## 2026-10-24 소리 차례 고침 (0.9.9 폰 피드백: 메뉴 · 보상 · 다음 화면에서 소리가 안 나거나 앞 화면 소리가 나옴)

- 원인 1: `Narrator.stop()` 이 앞 소리의 ‘끝났을 때’를 부르고, 이어 읽는 쪽 (이해 확인 지문 `Depth.listen` · 편지 방 `Room.say` · 알림 이어 읽기 `Playback.handOff` · 비교 듣기 `ShadowTake.compareListen` · 어제 편지 다시 듣기) 이 멈춘 것도 끝난 것으로 보고 다음 소리를 틀었다 → 화면을 옮기면 앞 화면의 다음 문장 · 내 녹음이 새 화면 소리를 자름. 끝까지 들었는지를 함께 주는 `playThen` · `playRangeThen` (토막 끝에 닿으면 끝까지 들은 것) 으로 바꾸고, 이어 읽기는 true 일 때만. 멈추는 중 (앞 소리의 ‘끝’ 안) 에 부른 새 소리는 받지 않는다.
- 원인 2: 화면 전환(AnimatedContent) 동안 나가는 화면이 살아 있어서, 전환이 끝날 때의 `onDispose { narrator.stop() }` 가 새 화면이 막 튼 자동 낭독을 껐고, 나가는 화면의 늦은 자동 재생 (도착 첫 줄 delay 뒤 · 회화 상대 줄 차례 · 편지 방 다음 조각) 이 새 화면 소리를 잘랐다. `go` · `back` · `enter` 뒤 한 곳 `AppState.moved()` → `Narrator.enter(route)`: 새 화면이 그려지기 전에 앞 소리를 멈추고 (효과음 sounds/ 와 밀어 닫은 뒤 이어 읽기는 그대로), 지난 화면 이름(`owner`)의 소리는 받지 않는다. 화면의 onDispose 는 `stop(me)` (제 소리일 때만).
- 원인 3: 알림 이어 읽기는 앱에서 그냥 멈춰도 (뒤로 가기 등) 다음 문장으로 넘어갔다 → 단추(다음 · 이전) 없이 멈추면 그만 (`Voice.next`). `back()` 은 이제 낭독을 직접 멈추지 않고 `moved()` 에 맡긴다.
- 보상 화면의 소리는 SoundPool (`Effects`) 이라 낭독과 겹치지 않음 (확인만, 고친 것 없음).
- 판단은 core `Voice` (`VoiceTest`), 앱 쪽 확인은 `NavTest.narrationFollowsTheRoute` (android.yml 은 core 테스트만 돌린다). 실제 폰에서 아직 들어 본 적 없음.

## 2026-10-24 추가 (하루 흐름 정리 · 시안 ux/src.html, 규칙 다섯 · 정할 것 셋 모두 허락)

- **억양 선 한 줄** (core `PitchLine`, 채점 `Prosody.score` 는 그대로): 그리기 전용 YIN 0.35 (안 잡히면 가장 깊은 골 < 0.5) · 가운데 높이 0.6~1.9배 · 가장 큰 소리 −25dB · 80ms 넘는 조각 양끝 30ms 떼기 · ±250ms 옥타브 (7반음) · Hampel ±200ms · 처음~끝 틈 곧게 잇기 · 250ms 넘는 쉼은 120ms 로 (낱말 · 재생 자리도 같은 시간축 `Line.x`) · 중앙값 5 → 70ms 가우스 · 160칸 평균. 내 선도 같은 길 (메움). 앱에 든 실제 낭독 6개에서 파이썬 시제품과 5e-5 안으로 같고 모두 1조각 · 빈칸 0% (예전 4~19조각 · 76~91% 빈칸). 실제 낭독 토막 하나를 core 테스트 자료로 (`core/src/test/resources/pitch/`).
- **낱말 자리** (core `WordSpots`): 낱말 시간 파일 (`<소리>.words.json`, core `WordTimes` · `data/Words.kt`, align.yml) 이 있으면 그것, 없으면 기기에서 어림 (음절 수를 소리 크기 봉우리에, 쉼 · 문장부호 · 낭독 덩어리에 맞춤, 시안 words.py). 영어 · 한국어는 낱말 시간 파일이 있을 때만 따라 읽기 마디를 4초 · 6~10 낱말 안쪽으로 (`WordSpots.split`, `AppState.shadowChunks`) — 그때부터 그 편지의 마디 번호 (통과 기록 "문장:마디") 가 바뀐다.
- **말하기 판 하나** (`ui/Shadow.kt` `SpeakPanel` = `SpeakBoard` 164dp + 아래 칸 고정): 따라 읽기 · 회화 내 차례 · 답장 말해 보기 · 내 이야기로 · 떠올리기 · 복습 말하기. 판 = 선 68dp · 낱말 줄 (재생 중 금빛 세로줄 · 지금 낱말) · 점수 한 줄 “96 ★★★ · 억양 · 리듬” (i) · 칭찬 / 다시 한 줄. 아래 칸 = 안내 한 줄 · 단추 셋 · 큰 단추 (`MainButton`, 못 누르면 까닭 한 줄). 건너뛰기는 위 오른쪽 “건너뛰기” 하나 (`PracticeHeader(onSkip)`, 걸음 · 소리 · 내 이야기로 · 답장 말해 보기). “잘 들렸어요” 빈칸 없앰. 회화 칩 · 힌트는 내 말풍선 아래 (내용 쪽).
- **먼저 듣기**: `ShadowTake.listenFirst` · `heard` · `locked` (끝까지 들어야 녹음, 고리가 들은 만큼), 소리 없는 줄은 막지 않음. 회화는 core `TalkTurn.Phase.LISTEN` (빈칸 · 조각은 맞춘 뒤, 뜻만 보는 B2 는 없음). 예열 ‘따라 말했어요’ 도 그 카드 소리를 들은 뒤.
- **덮개** `AppState.covered` (보상 · 레벨 · 등급 · 받기 · 처음 시작 · 묻는 창 `Covers` · 도움말 `coachShowing` · 설정 · 의견 · 고마워요): 자동 재생 · 회화 상대 줄은 `uncovered()` 뒤. 화면 안 작은 단계는 `Narrator.step(key)` 로 소리 주인을 바꾼다 (질문 · 마디 · 차례 · 턴 · 카드 · 소리 판).
- **보상**: 걸음 안 정산은 화폐만이면 작은 “+N” (`AppState.chip`, `RewardChip`), 자료 · 메달 · 도록 · 이정표면 화면 전체. 편지를 마친 정산은 보상 한 번에 등급 · 레벨 한 줄 (`RewardMoment.grade/level`, 따로 뜨지 않음).
- **편지 끝**: 완료 화면 큰 단추 “오늘로” 하나 (맺음이 있으면 맺음) + 작은 “그림 속 산책”. ‘오늘의 편지 마침’ 한 장 · ‘처음부터 다시 보기’ · 두 번째 ‘오늘로 돌아가기’ 없앰 (다음 시각 · 건너뛴 걸음 줄은 완료 화면 안). 튜토리얼 끝은 완료 화면의 한 줄 (그 자리에서 graduate). 알림 허락은 다음에 오늘에 왔을 때 (`Inbox`).
- × 는 늘 나가기 + 한 번 묻기 (답장 말해 보기 · 내 이야기로 · 소리 · 떠올리기, 새 열쇠 `leave_*`). 복습 한 판은 시작한 탭으로 (`leaveSession`, 복습 탭이면 “복습으로 돌아가기”). 서재 업데이트 띠만큼 아래 여백. 편지 방 모두 듣기는 낱말 카드를 닫으면 그 문장부터 이어 듣기. 오늘 걸음 점 이름표 한 줄.
- 시험: core `PitchLineTest` · `WordSpotsTest` · `TalkTurnTest` (LISTEN), NavTest `listenFirstLocksRecordingUntilHeard` · `coveredGate` · `endOfLetterQueue` · `reviewReturnsToItsTab` · `closeAlwaysLeaves`. 화면 사진 `ScreensTest.rules*` (말하기 여섯 곳 × 듣기 · 녹음 · 채점 뒤, “+2”, 접힌 보상, 완료 화면, 오늘 알림 묻기) — 실제 화면의 칸을 `AppState.takeHook` 으로 꾸민다. `slowTalkBubble` 은 아래 칸의 0.7× 가 이제 빠르기 칩이라 그것을 센다.
- 남은 것: 낱말 시간 파일이 앱에 들어오면 (align.yml) 따라 읽기 밑줄 · 낱말 줄이 그것을 쓴다 (지금은 어림). 폰으로 먼저 듣기 고리 · 판 높이를 한 번 봐야 한다.

## 2026-10-24 추가 (낭독 낱말 시각 `.words.json`)

- 낭독 소리마다 낱말 시각 파일 **`<소리 이름>.words.json`** (소리 옆, 새 파일만): `{"v":1,"ok":true,"words":[{"t":"Mon","s":641,"e":762,"q":0.999},…]}`.
  `t` = 앱이 그 소리와 함께 보여 주는 글을 띄어쓰기로 나눈 낱말 그대로 (`Breaks.plain` 뒤 `\s+`), `s` · `e` = 소리 처음부터 밀리초 (마디 시각 m*.json 과 달리 앞 쉼 없이 목소리가 실제로 나는 때, 마디 s 보다 중앙값 0.6초 늦음), `q` = 정렬 점수 0..1 (없으면 소리 없는 기호 낱말, s = e = 앞 낱말 끝). `ok` false = 약한 정렬 (낱말 평균 q < 0.35 또는 q < 0.05 인 낱말 25% 넘음) → 화면은 마디 시각으로.
- 대상: 편지 메시지 m* · 오늘의 회화 (지금 쓰는 이름 t · tb · tc · tk …, core `Talk.audioName`) · 답장 r* · 표현 ph* · 기초 낱말 예문 bx_ (B/basics). 낱말 하나짜리 소리 (v_ · b_ · w · s · s_) 는 없음.
- 만들기: `scripts/align_words.py` (torchaudio 2.5.1 `MMS_FA` 강제 정렬, fr · de · es · en 은 소문자 · 악센트 · 구두점 뺌 + 숫자는 num2words, ko 는 uroman 로마자), 워크플로 **`align.yml`** (수동: series · chapters (예 `I`, `P E`, `B` = 기초 낱말 예문) · dry_run). 이미 .words.json 이 있는 소리는 건너뛴다.
  dry_run=true 는 요약 + 미리 보기 가지 `align-dry-<run id>` (이 환경은 actions 내려받기 묶음을 못 연다), false 는 앱 안 장 (assets/audio) · 앱 밖 장 (remote/audio) 에 커밋 → generate.py (장 묶음 zip 에 들어가 새 이름) → R2 에 **없는 주소만** 올림 (낱말 시각 파일 + 새 zip, 이미 있는 주소가 다른 내용이면 멈춤).
- **새 편지 · 새 소리를 만든 뒤**: narration-r2 (make=new · replies) 가 끝나면 `align.yml` 을 그 작품 (· 장) 으로 dry_run=false 한 번. 소리를 고쳐 새 이름이 되면 (tb → tc) 새 이름의 시각만 생긴다. 같은 이름의 소리를 고친 경우 (make=messages, revisions) 는 옛 시각 파일이 남으니 그 파일을 지우지 말고 **새 판 규칙을 정해야 한다 (아직 없음)**.
- 앱: core `WordTimes` (`parse` · `parseFor(글, 화면 글)` = 낱말이 화면 글과 다르면 null · `at(ms)` 읽는 낱말 · `span` · `pieces(max)` 긴 마디를 가장 긴 숨에서 나누기 · `pathFor` · `optional`), 읽기 `data/Words.kt` `WordTimesFiles.load(ctx, 소리 경로, 화면 글?)` (없으면 null). 받기 `Downloads.ready` 는 .words.json 이 빠져도 받은 것으로 친다 (이 파일이 생기기 전에 받은 장을 다시 받게 하지 않음 → 그런 폰은 다음 받기 전까지 시각 없음). 화면 쪽은 `Narrator.words` 가 이것을 읽는다 (ok=false 면 기기 어림값, 말하기 판 작업).
- 만든 것 (2026-10-24, align.yml 전 작품): 9,675개 · 약함 63 (거의 한국어 짧은 표현 · 기초 낱말 예문 · 회화 줄). 빈센트 828 메시지 · 732 회화 · 366 답장 · 357 표현 · 316 예문 (약함 15), 모차르트 843 · 708 · 363 · 345 · 301 (14), 나폴레옹 948 · 732 · 378 · 366 · 314 (22), 고야 459 · 588 · 258 · 282 · 191 (12). 앱 안 장 (빈센트 서문 · I) 은 assets 에, 나머지는 remote/ + R2 (새 장 묶음 zip 이름은 assets/letters/<작품>/remote.json).
- 검증 (빈센트 I장 234개): 마디 첫 낱말 시작 − 마디 s 중앙값 601ms (p10 480 · p90 684, 앞 쉼 0.57초와 맞음), 낱말 구간이 무음인 몫 중앙값 0.

## 2026-10-24 추가 (보상 · 새 편지 · 화면 사이, 시안 rw2 허락)

- **정답** (공통 부품): `MeaningOptions` 의 맞은 보기는 먹색 + 오른쪽 가는 금선 원 · 체크 (`GiltCheck`, 원 0.7초 → 체크 0.4초, 튀지 않음). 보기가 없는 문제의 `CorrectBurst` 도 같은 금선 체크. 맞음 띠 (`FeedbackBar` ok) 는 금빛 한 줄 + 체크, 오늘의 복습이면 오른쪽에 화폐가 제자리에서 한 칸 (`CurrencyTicker`, `Rewards.earn` 미리 셈 — 실제로 들어오는 것은 ‘다음’ 때 그대로). 이해 확인 지문은 `EvidenceText`: 답이 나면 근거 (`check.mark`, 예 “pensé à toi”) 만 먹색 · 나머지 옅게 · 금선 밑줄이 왼쪽 → 오른쪽, 아래 한 줄 “맞았어요. 근거는 위의 밑줄이에요.” 떠올리기 · 답장 사다리의 맞음 줄도 금선 체크.
- **걸음 마침 한 장** (`AppState.stepNote`, `StepNoteOverlay`, 예전 “+2” 칩 `RewardChip` 은 지움): 그날 편지의 그림 (테두리 · 그림자, 흐림 → 또렷, 우표 모양 없음) · “+2 · ○○을 마쳤어요 · 우표 2 · 그림 이름 · 해” · 걸음 선. 덮개 (`covered`) 라 아래에 이미 열린 다음 걸음의 소리는 단추 뒤에. 단추 “다음 걸음 · 따라 읽기  n / N →” (core `Next.afterStep`, 다시 하기면 ‘편지로 돌아가기’) → 그림이 다음 걸음 머리 (× 옆 30×38dp, `PracticeHeader` 의 `LocalStepThumb`) 로 옮겨 가 그 걸음 내내.
- **편지 완료** (`Done` 다시 씀): 전시실 벽 · 그림 화면 가득 9초 물러남 · 벽 라벨 (머리말 작품의 말 core `Wall` “Lettre I · 마침” / “Brief I” / “Carta I”, 원제, 번역 제목 · 갤러리에 걸었어요, 작가 · 해 · 곳 — `plate.credit`/작가 이름 · `plate.date` 의 해와 괄호 속 곳만, 없으면 뺌) · 금선 봉인 (장 번호) 흐림 → 또렷 + 진동 한 번 · 셈 줄 (+화폐 · +낱말 카드 · 갤러리 n / N) · 등급 · 레벨 한 줄. 편지 끝 정산은 `AppState.letterTally` (화폐만이면 보상 층 없음, 자료 · 메달이면 그 층이 먼저). 단추 “오늘로 · 복습 3장 · 기초 낱말 2개 →” (core `Next.afterLetter`, `AppState.todayPlan`).
- **레벨 업** (`LevelUpOverlay` 다시 씀): 머리말 “Niveau 15”, 옛 칭호 흐려짐 → 새 칭호 (한글 세운 글자 · 로마 숫자 Cormorant, core `Wall.rank`), 금선, ‘새로 열린 것’ (입구 장식 이름 · 레벨, 그림은 데이터에 없어 문양만), XP 줄, 진동 한 번. 단추: 장식이 열렸으면 “입구에 가 보기” (`AppState.visitEntrance` = 첫 화면 한 번 더), 걸음 중이면 그 걸음, 아니면 나의 기록. 편지 끝에서는 예전처럼 완료 화면 한 줄 (따로 뜨지 않음).
- **새 메달** (`MedalMoment`, 새 메달만인 보상): 동전이 옆에서 정면으로 (1.8초) · 빛 한 번 · 진동 한 번 · 같은 가족 칸 줄 · “4 / 31”. “진열장에 넣기 · 4 / 31 →” → 진열장으로 가며 동전이 그 칸으로, 그 가족 아래 “다음 메달 · 열나흘의 순례 · 7일 남음” (`medalLanding`).
- **서재의 새 편지**: 읽던 작품에 안 연 편지가 있으면 그 작품이 맨 위 큰 카드 (표지 그림, `HeroCard`), 크림색 편지지 “Lettre II · 오늘 08:00 도착” 이 내려와 꽂힘 (편지마다 한 번, `drop_seen` 의 `hero|<열쇠>`) · 빛 한 번 · ‘편지 열기 · Lettre II →’ (`AppState.openFresh`), 나머지 작품은 96dp 낮은 카드.
- **화면 사이** (`ui/Flight.kt`): 보상은 덮개 층, 다음 화면은 길이라 SharedTransitionLayout 하나에 둘 수 없어 잰 자리 (창 좌표) 로 그리는 층 `FlightLayer` (Root 맨 위 + 편지 도착 대화창 안). 받는 자리 `Modifier.flightSlot(s, 이름)` 은 날아오는 동안 비고, 누른 쪽 `flightSource` 도 빈다. 날아가는 동안 길 전환은 미끄러짐 없이 밝아지기만. 걸음 그림 → `step-thumb`, 완료 그림 → 오늘 맨 위 `today-pic` (오늘 마친 편지의 그림 150dp, 오늘의 여정 I 에 체크가 그어지고 II 가 옅은 금빛으로 밝아짐, 복습 단추 “복습 시작 · 3장 →”), 메달 → `medal:<id>`, 서재 카드 → 편지 도착 `arrival-pic` (표지와 편지 그림이 달라 끝 3할에 옅어지며 넘겨줌). 움직임 줄이기면 날지 않는다. 셈은 core `FlightMath` (`WallTest`).
- 소리: 걸음 마침 종이 소리, 편지 완료 tier_painting, 레벨 업 tier_drawing, 메달 tier_master (있는 효과음만).
- 스크린샷 점검 고침: (a) 낱말 줄의 띄어 쓴 문장부호는 앞 낱말에 (core `WordSpots.display`, “Vraiment ? Le” → “Vraiment ?” · “Le”) · (b) 내 이야기로 녹음 중 크기 막대 지움 (같은 한 줄 선) · (c) 떠올리기 · 복습 말하기의 다음 단추는 녹음 · 채점 중 막힘 + 까닭 (`need_stop`) · (d) 화면 사진의 채점 뒤 장면은 실제 녹음 파일을 써서 `stopRecording` → 화면의 채점 · 판정이 그대로 돈다 (`ScreensTest.staged`, `waitHook`) · (e) 답장 사다리 ‘확인’ 은 `MainButton` (까닭 `need_typed`).
- 글자 키 새로 27개 (`wall_*` · `go_*` · `step_note_*` · `medal_next_line` · `level_*` · `slip_*` · `lib_new_*` · `verdict_evidence` · `need_stop` · `need_typed`), 기존 값은 그대로. 시험: core `WallTest`, NavTest `endOfLetterQueue`(바꿈) · `coveredGate` · `destinationsAndFlights` · `openFreshAndEntrance`. 화면 사진 `ScreensTest.rw*` (rw1 정답 · rw2 걸음 마침 → 머리 그림 · rw3 편지 완료 → 오늘 · rw4 레벨 · rw5 메달 → 진열장 · rw6 서재 → 편지 도착, 가운데 장면은 날아가는 중).

## 2026-10-24 추가 (시스템 막대 점검 · 메달과 함께 온 그림 1안)

- **막대 · 컷아웃** (`ui/Insets.kt`, 앱은 `enableEdgeToEdge` · targetSdk 36 · 방향 잠금 없음 · 컷아웃은 화면 끝까지): 모든 층이 `statusBarsSafe` (상태 바 + 위 컷아웃) · `navigationBarsSafe` (내비게이션 바 어느 쪽이든 + 옆 · 아래 컷아웃, 가로 화면의 세 단추 막대 · 펀치홀) · `systemBarsSafe` 로만 막대를 피한다. 셋 다 `windowInsetsPadding` 이라 바깥 층이 이미 피한 만큼은 안에서 다시 띄우지 않는다. 예전 `statusBarsPadding` · `navigationBarsPadding` 은 모두 이것으로 (Root 의 길 화면 · 보상 · 걸음 마침 · 메달 · 레벨 · 설정 · 처음 시작 · 시트들 · 편지 카드 · 프롤로그 등).
- 고친 빈틈: 가로 화면의 옆 컷아웃 (어느 층도 피하지 않았음) · 아래에서 올라오는 시트의 위쪽 (가로 화면에서 시트가 상태 바 밑까지 자람 → 막 Box 에 `statusBarsSafe`) · 가운데 묻는 창 `Ask` · 고마워요 · 언어 안내 · 새로 바뀐 것 · 숫자 풀이 (막대를 전혀 안 피했음 → `systemBarsSafe`) · 도움말 카드 위쪽 · 뜻 카드 (`GlossSheet`, 옆 막대 · 컷아웃 · 대화창 안 값) · `FullDialog` 의 바깥 값에 컷아웃 · 옆 쪽.
- **화면 사이 (`ui/Flight.kt`)**: 처음 자리 · 닿을 자리 · 그리는 층 모두 화면 좌표 (창 좌표 + 그 창의 화면 위치). 편지 도착 대화창 창이 상태 바 아래에서 시작하는 기기에서 서재 카드 → 편지 도착 그림이 막대 두께만큼 어긋나던 것.
- **화면 사진에서 막대**: Robolectric 은 막대 두께가 0 이라, `ScreensTest.barsShot` 이 앱의 실제 `Root` 를 그리고 Compose 뷰에 상태 바 24dp · 내비게이션 바 48dp 인셋 (`WindowInsetsCompat` 을 `dispatchApplyWindowInsets`) 을 보낸 뒤 막대 자리에 반투명 빨간 띠를 긋는다. 띠 밑에 누를 것 · 글자가 없으면 된다. `*ScreensTest.insets*` (01 서재 + 업데이트 띠 · 02 편지 방 · 03 따라 읽기 · 04 회화 · 05 걸음 마침 · 06 편지 완료 · 07 레벨 업 · 08 메달 + 그림 · 09 받기 시트 · 10 기록 보관 시트 · 11 묻는 창 · 12 오늘).
- **메달과 함께 온 그림** (메달 셋째마다, 시안 medal 1안): 예전 보상 화면 대신 메달 한 장 (`MedalMoment`) 그대로 + 진열장 줄 아래 “함께 온 그림” 한 줄 (작은 그림 · 가는 테두리 · 원제 (작품의 말 → 없으면 소장처의 영어 이름) · 작가 · 해). 단추는 “진열장에 넣기 · n / 31 →” 그대로 (동전이 진열장 칸으로), 그림은 이미 갤러리에 들어가 있다 (`draw`). `RewardOverlay` 는 메달이 있으면 (도록이 아니면) 그리지 않는다, `reward_from_ach` 줄은 이제 안 씀 (값은 그대로). 새 글자 키 `medal_with_painting`. 작가는 소장품에 칸이 없어 그 작품 화가 이름 (`kit.fullName`) — 다른 화가의 소장품이 들어오면 `Piece` 에 작가 칸을 더해야 한다. 화면 사진 `rw5f_medal_painting`, `insets08`.

## 2026-10-25 추가 (남은 옛 보상 · 알림 화면을 시안 rw2 의 말씨로)

- **한 장이 덮은 화면의 이름** (core `Next.under(route)`, `WallTest.noticeButtonNamesTheScreenUnderIt`): 보상 · 알림 한 장의 큰 단추는 ‘좋아요 · 닫기’ 대신 그 밑 화면 이름 — 걸음 “다음 걸음 · 따라 읽기” · 오늘 “오늘로 · 복습 3장 · 기초 낱말 2개” · 서재 “서재로” · 편지 완료 “마친 편지 보기 · Lettre IV” · 복습 결과 “복습 결과 보기” · 복습 탭 “복습으로 돌아가기” · 편지 방 “편지로 돌아가기” · 갤러리 · 나의 기록 · 상점, 그 밖은 “돌아가기”. 누르면 한 장만 걷힌다 (길은 그대로). 화면 쪽은 `underLabel` · `UnderButton` · `UnderLink` (ui/Reward.kt).
- **혼자 읽기 이정표 · 받은 화폐만** (`NoticeMoment`, 예전 “잘 읽었어요 · 이제 25%를 혼자 읽어요 · 오늘 받은 우표 +50 · 좋아요”): 레벨 업과 같은 밤의 벽. 머리말은 작품의 말 (`Wall.milestone` “Jalon” · “Meilenstein” · “Hito”, 화폐만이면 “오늘”), 제목 “이제 25%를 혼자 읽어요” 흐림 → 또렷, 금선, “잘 읽었어요”, 혼자 읽기 줄 (방금 넘은 이정표까지 금선 · 다음 이정표 자리 눈금 · “다음 이정표 · 50%”, `Growth.nextMilestone`), 셈 줄 “+50 · 오늘 받은 우표” (내역은 접어 둠), 접힌 등급 · 레벨 한 줄.
- **오늘의 자료** (`PieceMoment`, 예전 조명 · 실제 액자 · 명작의 금빛 명판 · “갤러리에 걸기 · 닫기”): 편지 완료처럼 그림이 화면 가득 9초 물러나고 벽 라벨 — “오늘의 자료 · 유화” (명작이면 “명작”), 원제 (작품의 말 → 소장처 영어 이름), 번역 제목 · 갤러리에 걸었어요, 작가 · 해 · 곳, 소장처 · 액자 · 뽑기 100번 중 n번꼴. 셈 줄 (+우표 · 모은 작품 n / N, 편지 그림이면 편지의 그림). 단추 “갤러리에 걸기 · n / N →” → 갤러리로 가며 그림이 그 묶음 맨 앞 칸 (`flightSlot "gallery:<id>"`, `AppState.galleryLanding`, 갤러리를 떠나면 비움) 으로 옮겨 간다. 작게 덮인 화면 링크.
- **이달의 전시를 다 모음** (`CatalogueMoment`): 머리말 “이달의 전시”, 제목, 금선, 도록 표지 흐림 → 또렷, 셈 줄 (+우표 · 전시 자료 n / n), 단추 “이달의 전시로 · 전시 이름 →” + 작게 덮인 화면.
- **언어 등급** (`GradeUpOverlay` 다시 씀): 머리말 “언어 등급 · 프랑스어”, 새 등급이면 옛 이름이 흐려지고 새 이름 (한글 세운 글자), 그 아래 “A2 1단” (같은 등급 안이면 이것만 넘어감), 금선, “프랑스어 실력이 한 단 올랐어요” (‘됐어요’ 줄은 제목과 겹쳐 뺌), 번역이 옅어짐 · 도움 한 칸 줄은 가는 선 사이 한 줄씩, 단추는 덮인 화면.
- 단추 글만 바꾼 곳: 완독 봉인 맨 아래 ‘닫기’ → ‘편지로 돌아가기’, 맺음 ‘닫기’ → ‘오늘로’ (오늘로 감), 새로 바뀐 것 ‘좋아요’ → 덮인 화면 이름 (보통 ‘서재로’).
- 그대로 둔 것: 도구 시트의 ‘닫기’ (액자 · 테두리 고르기, 상점 고르기, 메달 자세히, 숫자 풀이, 말 바꾸기 · 낱말 시트) — 보상이 아니라 고르는 창이라. 상점에서 산 것 · 쉼표 · 미리 받은 편지 · 이어 읽기는 따로 뜨는 화면이 없다 (정산의 내역 한 줄). `graduate_go` · `whatsnew_ok` · `reward_ok` · `outro_close` · `reward_piece` · `reward_master` 는 이제 안 쓰지만 값은 그대로.
- 새 글자 키 9개 (`rw_today_piece` · `rw_next_milestone` · `go_library` · `go_finished` · `go_review_result` · `go_gallery` · `go_record_back` · `go_shop` · `go_exhibition`). 시험: NavTest `oldRewardScreensNameWhereTheyGo`, 화면 사진 `*ScreensTest.ow*` (ow1 이정표 · ow2 화폐만 · ow3 오늘의 자료 · 명작 · 갤러리 칸 · ow4 전시 · ow5 등급 · ow6 봉인 · ow7 맺음 · ow8 새로 바뀐 것, ko · en × 밝음 · 어둠). 예전 사진 70 · g07 · g08 · st6 · rules8 도 새 모습으로 찍히고, 45 · 69 · 89 (메달이 함께라 메달 한 장이 맡아 이 층이 빈 화면으로 찍히던 것) 는 메달 없이 오늘의 자료 장면으로 바꿨다.

## 2026-10-25 폰 시험 고침 (1.0.0 · 13 회화: 다시 말하기 · 저절로 멈춘 점수 · 상대 줄 소리)

- **맞힌 뒤 ‘다시 말하기’ 가 흐린 단추로 막힘**: 원인 core `TalkTurn.State.canRecord` 가 READY · RETRY 만이라 PASSED · REVEALED 에서 회화 판 (`Depth.kt` TalkStep → `SpeakPanel(canRecord = t.canRecord || LISTEN)`) 의 녹음 단추가 꺼짐 (그때 `Heard` 도 무시). 이제 채점된 뒤 (맞음 · 원문) 에도 늘 다시 말하기: 맞음은 거두지 않고 (안 되면 판의 ‘다시’ 한 줄만), 원문을 본 뒤 맞히면 맞음. 다른 다섯 곳은 원래 막지 않았다. `ShadowTake.locked` 는 한 번 녹음해 채점된 뒤 (`tick > 0`) 로는 다시 잠그지 않는다.
- **저절로 멈춘 녹음의 낮은 점수 (리듬 56)**: 저절로 멈추기 (`AutoStop`) 는 마지막 목소리 뒤 1초를 기다리는데, ‘조용함’ 은 가장 큰 소리 −20dB 아래라 폰 마이크의 방 소음 (−20 ~ −30dB) 이 채점의 앞뒤 자르기 (`Prosody.trim`, −30dB) 에 남아 꼬리 1초 · 첫 목소리 앞이 그대로 채점됐다. core `Prosody.span` · `voicedOnly` · `voicedSamples` (음높이가 잡힌 −30dB 위 첫 · 마지막 프레임, 앞뒤 여유 120ms): 화면은 내 녹음을 이것으로 자른 뒤 채점 · 선 · 음절, `Prosody.score` 도 두 소리 모두 `voicedOnly` → `trim`. 시험 `TakeTrimTest` (+0.5초 앞 · +1.1초 뒤 방 소음: 예전 리듬 100 → 73, 이제 ≤ 6 차이 · 총점 ≤ 3).
- **내 선의 계단 (긴 평평 → 뛰어오름)**: 원인 `PitchLine.mine` 이 크기 흐름 DTW 길을 그대로 써서 원래 칸 여럿이 내 칸 하나에 묶임 (+ 꼬리 소음이 끝 칸을 평평하게). 이제 꼬리를 떼고 (`voicedOnly`), 맞춘 길 (`PitchLine.warp`: 칸마다 평균 자리 → 가우스 (칸 수 / 16) → 늘 0.35 기울기 이상 앞으로 → 0 · m−1 맞춤) 의 소수 자리에서 곧게 읽는다. 시험: 미끄러지는 음높이의 내 선 가장 긴 평평 ≤ 8칸 (예전 16 ~ 40), 한 칸 뜀 < 0.08, `warpNeverStalls`.
- **회화 상대 줄 (빈센트) 이 안 들림**: 원인 `TalkStep` 에서 상대 줄이 열리면 같은 프레임에 내 줄 차례가 생겨 LISTEN 의 `take.original` 이 막 시작한 상대 줄 소리를 바로 잘랐다 (걸음 마침 한 장 아래에서 열리든 아니든). 이제 상대 줄은 `AppState.autoPlay` (덮개가 걷힐 때까지 기다림 · 받을 수 없으면 다시 걷힐 때 · 끝날 때까지 기다림) 로 틀고, 끝나면 `spoken`, 내 줄의 먼저 듣기는 `spoken` 을 기다린 뒤 같은 `autoPlay` 로. 따라 읽기 첫 듣기 · 이해 확인 지문 · 답장 말해 보기도 `autoPlay` 한 곳으로 (`ShadowTake.listen`).
- 시험 도우미 `Narrator.fake` · `fakeEnd` (소리 없이 시작한 파일만 적음, 앱에서는 null). NavTest `talkAfterStepNotePlaysPartnerThenMine` (덮개 동안 소리 없음 → 걷히면 상대 줄 → 끝나기 전엔 그대로 → 끝나면 내 줄 → 다 들으면 녹음 단추 켜짐) · `checkAfterStepNotePlaysPassage` · `readAlongAfterStepNotePlaysOriginal`, core `TalkTurnTest.retryAlwaysOpenAfterAnyScoredTake`. 글자 값 · 키 바뀐 것 없음.

## 2026-10-25 추가 (낱말 아래 뜻 노트: 기능어 · 문형 · 관용구 — 자료 · core, 화면은 따로)

- **무엇**: 편지 방 낱말 아래 뜻에 기능어 · 여러 낱말 문형 · 관용구의 짧은 뜻, 누르면 짧은 문법 카드 (꼬리표 · 짧은 뜻 · 두 문장 안 노트, ko · en). 다섯 배우는 말 fr · de · es · en · ko.
  모든 토큰을 덮지는 않는다: 노트가 없는 토큰은 예전대로 사전 첫 뜻.
- **자료** (손으로 고치는 원본 → 만든 파일, `python3 scripts/notes.py` · `--check`):
  - `scripts/notes/<말>.txt` → `data/lexicon/notes_<말>.json` (v1): `words` (사전 열쇠 → tag · short · note) · `endings` (한국어만, gloss_ko fn 조각 "를/JKO" → 같음 + show) · `classes` · `patterns` (토큰 조건 · `gap` · `stop` · `next_not` · `prev_not` · `always` · `compound`). 원본 줄 꼴과 조건 글 (l= f= p= c= r= fn= i u, @head @part) 은 notes.py 머리 주석.
    지금 fr 96 · 28, de 99 · 37, es 89 · 31, en 111 · 29, ko 66 낱말 + 103 어미 · 조사 · 37 문형 (낱말 · 문형).
  - `scripts/idioms/<작품>[_en|_ko].txt` → `data/<작품>/idioms[_en|_ko].json` (`lem` = 사전 열쇠 잇단 꼴, t · ko · en · note_ko · note_en · n · first). 후보는 `--candidates <작품> [en|ko]` (일반 목록 `scripts/idioms/known/<말>.txt` 에서 찾은 것 + 자주 나오는 n-gram), 처음 목록은 `--seed` (없는 파일만). 닻 안에 드는 관용구 · 편지에 없는 줄은 저절로 빠진다.
    지금 vincent 45 · 45 · 14, mozart 31 · 37 · 22, napoleon 36 · 39 · 14, goya 24 · 28 · 11 (원어 · en · ko). 한국어 줄은 어절 안의 어미 · 조사가 문형으로 먼저 잡혀 관용구가 적다.
  - 점검 `--report <md>`: 작품 · 말마다 덮인 비율 (A1·A2 / B1+, 전 → 후), 맥락 뜻 · 카드 비율, 말마다 무작위 문장 20개의 칸.
  - 앱 안: `generate.py` 가 notes → `assets/notes/notes_<말>.json`, idioms → `assets/letters/<작품>/` (어휘 자료처럼 번들, R2 아님). 모두 새 파일 이름.
- **core** (`core/Notes.kt`): `NoteBook.parse(notes, idioms, lexicon)` · `forToken(lexKey)` · `forEnding(piece)` · `match(tokens, blocked)` → `Hit(start, end, kind PATTERN|IDIOM|WORD, short, note, parts, roles, id, always, suffix)`.
  `Interlinear.units(…, notes = book)` (선택 인자): 차례 표현 카드 > 문형 > 관용구 > 닻 > 낱말. 문형의 mark 부분 = 문형 뜻 (떨어진 부분은 마지막 칸에만), head = 그 낱말 뜻, part = 뜻 없음 · 같은 `Unit.hit`. 기능어 뜻을 끈 등급 (B1+) 에도 `always` 문형 (ne … que = ~만, 분리동사, il y a …) 과 관용구는 뜻.
  한국어 어미 뜻은 낱말 뜻 뒤 괄호 (to think (past), `Notes.withEnding`). `Token.fn` (gloss_ko 셋째 칸) 을 `Library.gloss` 가 읽는다. 읽기 `data/NoteFiles.load(ctx, 작품, 자료, 사전)`.
  `Anchors.find`: 토큰 하나가 여러 낱말 닻을 통째로 맞추지 않게 고침 (영어 ill · its 가 닻 I'll · it's 로 잡히던 것).
- **테스트**: `NotesTest` (je ne te dis que cela → ~만, j'ai rapporté → ~했다, fängt … an → 시작하다, estar/ser, have seen, 를/JKO · 었 · -고 있다, 관용구 > 닻), `NotesDataTest` (실제 자료로 core 와 notes.py 가 같은 수를 잡는지: `scripts/notes/expect.json`). 규칙을 고치면 notes.py 를 돌려 expect.json 도 새로.
- **넓히는 법**: 새 기능어 = `W|열쇠|꼬리표|짧은 뜻 ko|en|노트 ko|en` 한 줄. 새 문형 = `P|id|부분 + 부분|설정|…` (파일 위쪽이 먼저 잡힘; 넓은 문형은 아래로). 새 작품 = idioms 원본 `--seed` 후 손으로 고르기, notes 는 말마다라 그대로. 새 배우는 말 = `scripts/notes/<말>.txt` + notes.py 의 LANGS · SERIES.
- **남은 것**: 화면 (편지 방 카드 · 묶음 밑줄) 은 다른 작업 (`ui/RoomNotes.kt`). 노트 · 관용구 뜻은 앱이 쓴 것 (사람 확인 전, idioms `"checked": false`). 사전 표제어가 틀린 토큰 (영어 least → little, 프랑스어 porte → 문) 은 문형이 못 고친다 (lexicon 쪽 일). 독일어 비분리 동사의 3인칭 -t (versteht) 가 과거분사로 잡히는 일이 가끔 있다. 문장부호를 모르는 core 라 떨어진 문형 (gap) 이 문장 경계를 넘을 수 있어 gap · stop 을 작게 둠.
