# 언어 미술관 · 출시 빌드 (Play 스토어용 AAB)

`CLAUDE.md` 의 순서대로, 사용자가 **“릴리즈하자”** 고 할 때만 돌려요. 그 전에는 빌드 워크플로를 돌리지 않아요.

| 워크플로 | 무엇 | 서명 |
|---|---|---|
| `android.yml` | 푸시마다 컴파일 확인 · debug APK | 저장소의 시험 키 (`sideload`) |
| `android-apk.yml` | 내 폰에 직접 설치할 release APK (`android-latest`) | 업로드 키 비밀값이 없으면 `sideload` |
| `android-aab.yml` | Play 스토어에 올릴 App Bundle (`android-aab-latest` · 실행의 Artifacts) | **업로드 키** (없으면 실패) |

`versionCode` 는 세 워크플로 모두 그 실행 번호(`GITHUB_RUN_NUMBER`)예요. 워크플로마다 번호가 따로 늘어나므로
Play 에 올린 번호보다 작은 번호의 AAB 는 올릴 수 없어요. 그럴 때는 `android-aab.yml` 을 한 번 더 돌리면 돼요.
`versionName` 은 워크플로의 `TL_VERSION_NAME` 이에요.

## 1. 업로드 키 만들기 (한 번)

내 컴퓨터에서 (JDK 의 `keytool`):

```sh
keytool -genkeypair -v -keystore upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Lingua Museum, O=graviton94"
# 저장소 비밀번호와 키 비밀번호를 물어요 (같게 해도 돼요)

base64 -w0 upload.jks > upload.jks.b64      # macOS: base64 -i upload.jks -o upload.jks.b64
```

`upload.jks` 와 비밀번호는 저장소에 넣지 말고 안전한 곳(비밀번호 관리자 등)에 따로 보관해요.
잃어버려도 Play App Signing 을 쓰면 Play Console 에서 업로드 키를 바꿀 수 있어요 (아래 3).

## 2. GitHub 비밀값 (Settings › Secrets and variables › Actions)

| 이름 | 값 |
|---|---|
| `ANDROID_UPLOAD_KEYSTORE_B64` | `upload.jks.b64` 의 내용 (한 줄) |
| `ANDROID_UPLOAD_STORE_PASSWORD` | 저장소 비밀번호 |
| `ANDROID_UPLOAD_KEY_ALIAS` | `upload` |
| `ANDROID_UPLOAD_KEY_PASSWORD` | 키 비밀번호 |

워크플로는 base64 를 풀어 임시 파일로 두고 `ANDROID_UPLOAD_KEYSTORE` (경로) 와 나머지 셋을 환경 변수로 넘겨요.
`android/app/build.gradle.kts` 는 넷이 다 있을 때만 `upload` 서명을 만들고 release 에 써요. 하나라도 없으면 release 는 `sideload` 키로 서명돼서
`android-apk.yml` 은 지금처럼 돌아가요. `android-aab.yml` 은 비밀값이 빠지면 어떤 것이 없는지 적고 멈춰요.

## 3. Play App Signing

- Play Console › 앱 만들기 → 첫 버전을 올릴 때 **Play App Signing** 에 등록돼요 (기본). 앱 서명 키는 Google 이 갖고, 우리는 **업로드 키**로 서명한 AAB 만 올려요.
- 처음 올린 AAB 의 서명 인증서가 업로드 키로 등록돼요. 그 뒤로는 같은 업로드 키로만 올릴 수 있어요.
- 업로드 키를 잃어버리면: Play Console › 설정 › 앱 무결성 › 앱 서명 › 업로드 키 재설정 요청 (새 키의 인증서를 `keytool -export -rfc -keystore new.jks -alias upload -file upload_cert.pem` 으로 만들어 올림).
- 결제 · 서명 확인에 쓰는 SHA-256 은 Play Console › 앱 무결성 › 앱 서명 키 인증서에서 봐요 (스토어 설치본은 업로드 키가 아니라 이 키로 서명돼요).
- 스토어 설치본과 `sideload` 로 서명한 시험 APK 는 서명이 달라서 서로 덮어 설치되지 않아요 (지우고 설치).

## 4. 올리기

1. Actions › **Android AAB** › Run workflow (사용자가 “릴리즈하자” 고 한 뒤).
2. 실행이 끝나면 Artifacts 의 `TodayLetters-aab` 또는 Releases 의 `android-aab-latest` 에서 `.aab` 를 받아요.
3. Play Console › 테스트(내부 · 비공개) 또는 프로덕션 › 새 버전 만들기 → `.aab` 업로드 → 출시 노트.
4. 앱 콘텐츠 › 포그라운드 서비스 신고: 미디어 재생 (편지 낭독) · 데이터 동기화 (낭독 미리 받기). 문구는 배포 준비 시안 ⑤, 시연 영상은 시험 APK 로 짧게.
