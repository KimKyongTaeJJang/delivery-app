# 배달 주문전표 앱

주문전표 사진을 찍으면 전화번호를 자동 인식하여 통화 또는 문자를 보낼 수 있는 앱입니다.

## APK 빌드 방법 (GitHub Actions)

### 1단계: GitHub 계정 만들기
1. https://github.com 접속
2. [Sign up] 클릭 → 이메일, 비밀번호 입력 → 가입 완료

### 2단계: 새 저장소 만들기
1. 로그인 후 우측 상단 [+] → [New repository] 클릭
2. Repository name: `delivery-app` 입력
3. [Create repository] 클릭

### 3단계: 코드 업로드
1. 저장소 페이지에서 [uploading an existing file] 클릭
2. `C:\aideliver` 폴더 전체를 드래그 앤 드롭
3. [Commit changes] 클릭

### 4단계: APK 다운로드
1. 저장소 상단 [Actions] 탭 클릭
2. [Build APK] 워크플로우 클릭
3. 완료 후 하단 [Artifacts] 에서 `DeliveryApp-debug` 다운로드
4. zip 파일 압축 해제 → `app-debug.apk` 파일 획득

### 5단계: 안드로이드에 설치
1. `app-debug.apk` 파일을 카카오톡/구글드라이브 등으로 폰에 전송
2. 폰에서 파일 열기
3. "알 수 없는 앱 설치 허용" 설정 후 설치

## 앱 사용 방법

1. 앱 실행 → 권한 허용 (카메라, 전화, 문자)
2. **[주문전표 촬영]** 버튼 → 전표 사진 촬영
3. 전화번호 자동 인식 (여러 개면 목록에서 선택)
4. **[통화]** 또는 **[문자 전송]** 버튼 클릭
5. 문자 내용 변경: **[문자 내용 설정]** 버튼

## 앱 수정 후 재빌드

코드 수정 후 GitHub에 다시 업로드하면 자동으로 새 APK가 빌드됩니다.
