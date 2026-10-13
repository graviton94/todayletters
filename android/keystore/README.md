# 서명 키

- `sideload.jks`: 내 폰에 직접 설치하는 시험용 APK 의 고정 키 (비밀번호 `android`, 별칭 `sideload`). 비밀이 아닌 시험 키라 저장소에 둬요.
  늘 같은 키로 서명되므로 새 APK 를 받아 그대로 덮어 설치할 수 있어요.
- `upload.jks.gpg`: Play 에 올리는 AAB 의 업로드 키 (별칭 `upload`). **Android signing key** 워크플로가 GitHub 안에서 만들어
  Secrets 의 `ANDROID_UPLOAD_PASSPHRASE` 로 GPG 잠금한 파일만 저장소에 둬요. 자세한 건 `docs/release.md`.
