# NotiPassDroid (노티패스)

> **안드로이드 알림 감지 및 웹훅 자동화 앱 (MacroDroid 스타일의 경량화 자동화 도구)**

NotiPassDroid는 안드로이드 기기에 발생하는 모든 푸시 알림을 실시간으로 인터셉트하여, 사용자가 정의한 조건(앱, 제목, 본문 키워드, 정규표현식)에 따라 Discord, Slack, Telegram, 개인 서버 등으로 웹훅(HTTP POST/GET)을 자동 전달하는 기기 자동화 어플리케이션입니다.

---

## 🌟 주요 기능 (MVP)

1. **실시간 알림 인터셉터 (Notification Interceptor)**
   - Android `NotificationListenerService` 기반의 실시간 알림 가로채기
   - 기기 설치 앱 목록 검색 및 간편한 대상 앱 지정 (카카오톡, 문자, 은행, 업무 툴 등)

2. **정밀 필터링 엔진 (Rule Matcher)**
   - **제목 / 본문 필터**: `무시(전부)`, `포함(Contains)`, `일치(Exact)`, `정규표현식(Regex)` 지원
   - 정규표현식 매칭 시 캡처 그룹(예: `{group1}`, `{group2}`)을 동적 토큰으로 추출 가능

3. **유연한 웹훅 액션 (Webhook Action)**
   - HTTP 메서드(`POST`, `GET`) 및 커스텀 HTTP 헤더 지원
   - **동적 치환 토큰 (Magic Tokens)**:
     - `{title}` : 알림 제목
     - `{content}` / `{text}` : 알림 본문
     - `{app}` : 앱 이름 (예: 카카오톡)
     - `{package}` : 앱 패키지명 (예: `com.kakao.talk`)
     - `{time}` : 발생 타임스탬프 (ms)
     - `{date}` : 발생 일시 (`yyyy-MM-dd HH:mm:ss`)
     - `{groupN}` : 정규식 캡처 그룹
   - JSON 템플릿 작성 시 특수문자/따옴표 자동 이스케이프 처리
   - 앱 내에서 실제 알림 없이도 웹훅 URL과 템플릿을 바로 테스트할 수 있는 **[웹훅 테스트 전송]** 기능

4. **실행 히스토리 & 모니터링 (Execution Logs)**
   - 수신된 알림 및 웹훅 전송 성공/실패 내역 실시간 기록
   - HTTP 응답 코드(`200 OK`, `400`, `500` 등) 및 에러 메시지 상세 조회
   - 1-클릭 로그 전체 삭제

5. **최신 안드로이드 아키텍처**
   - Jetpack Compose + Material Design 3 UI
   - Room Database (비동기 Flow 기반 반응형 UI)
   - OkHttp 4 비동기 네트워크 통신
   - 최신 권한 가이드라인 준수 (알림 접근 권한, 백그라운드 배터리 최적화 해제 지원)

---

## 🏗️ 프로젝트 구조

```
NotiPassDroid/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/notipass/droid/
│   │   │   ├── MainActivity.kt               # 네비게이션 및 진입점
│   │   │   ├── NotiPassApplication.kt        # Room DB 싱글톤 관리
│   │   │   ├── model/
│   │   │   │   ├── Rule.kt                   # 자동화 규칙 엔티티
│   │   │   │   ├── NotificationLog.kt        # 실행 로그 엔티티
│   │   │   │   ├── MatchMode.kt              # 필터링 모드 enum
│   │   │   │   └── LogStatus.kt              # 성공/실패 상태 enum
│   │   │   ├── data/db/
│   │   │   │   ├── AppDatabase.kt            # Room 데이터베이스
│   │   │   │   ├── RuleDao.kt                # 규칙 DAO
│   │   │   │   └── LogDao.kt                 # 로그 DAO
│   │   │   ├── engine/
│   │   │   │   ├── RuleMatcher.kt            # 키워드/정규식 매칭 엔진
│   │   │   │   ├── TemplateEngine.kt         # 동적 토큰 치환기
│   │   │   │   └── WebhookDispatcher.kt      # OkHttp 웹훅 발송기
│   │   │   ├── service/
│   │   │   │   └── NotificationPassService.kt# 알림 가로채기 백그라운드 서비스
│   │   │   ├── ui/
│   │   │   │   ├── screens/
│   │   │   │   │   ├── HomeScreen.kt         # 대시보드 & 권한 가이드
│   │   │   │   │   ├── RulesScreen.kt        # 규칙 목록 & 토글
│   │   │   │   │   ├── RuleEditScreen.kt     # 규칙 생성/편집 & 테스트 전송
│   │   │   │   │   ├── LogsScreen.kt         # 실행 내역 & 상세 모니터링
│   │   │   │   │   └── AppPickerDialog.kt    # 설치된 앱 선택 다이얼로그
│   │   │   │   └── theme/                    # Compose 테마/컬러
│   │   │   └── util/
│   │   │       └── PermissionUtil.kt         # 권한 검사 및 시스템 설정 인텐트
│   │   └── res/                              # 리소스
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 빌드 및 실행 방법

### 요구 사항
- JDK 17 (또는 JDK 21)
- Android SDK (API 35/36)

### 빌드 명령어
```bash
./gradlew assembleDebug
```
빌드 성공 시 `app/build/outputs/apk/debug/app-debug.apk`에 설치 파일이 생성됩니다.
