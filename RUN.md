# 실행 가이드

Pleos Connect 에뮬레이터에 MCC 를 설치·실행하고, 차량 신호를 바꿔 가며 기능을 확인하는 절차입니다.
개발 환경을 처음 구성하는 경우 약 40~60분이 걸립니다 (대부분 다운로드 시간).

---

## 0. 두 가지 에뮬레이터

| | 구글 **AAOS 에뮬레이터** | 현대 **Pleos Connect 에뮬레이터 v2.1.0** (공식) |
|---|---|---|
| 받는 곳 | Android Studio 안에서 바로 | SDK Update Site 를 추가해서 (아래 0-1) |
| 시스템 이미지 | API 34·35 Automotive | API 34 · Pleos Connect v2.1.0 |
| `android.car` API | 동작 | 동작 + Pleos Vehicle Service 포함 |

**최종 확인과 시연 녹화는 Pleos Connect 에뮬레이터로 하세요.** Pleos 공식 가이드가 지정한 환경입니다.
구글 AAOS 에뮬레이터도 MCC 가 쓰는 표준 API 는 똑같이 동작하므로, 개발 중에는 둘 다 써도 됩니다.

### 0-1. Pleos Connect 에뮬레이터 설치 (공식 가이드)

1. Android Studio → **Tools → SDK Manager → SDK Update Sites** 탭
2. **+** → Name `Pleos Connect System Image`,
   URL (Windows) `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   (Mac 은 `…/connect-avd/2.x/crp-arm64-sys-img.xml`) → **OK**
3. **SDK Platforms** 탭 → **Show Package Details** 체크 → Android API 34 아래 **Pleos Connect system image** 체크 → **OK** 로 설치
4. Android Studio 재시작
5. **Tools → Device Manager → Create device** → Form Factor **Automotive** → **Pleos Connect v2.1.0** → Next
6. **Advanced Settings** → Startup **Default boot: Cold**, **RAM 4 GB**
7. 시스템 이미지에서 **API 34 · Pleos Connect v2.1.0 APIs** 선택 → **Finish**

> 예전 버전 Pleos 에뮬레이터가 있으면 먼저 지우고 설치하세요.
> 출처: [Pleos 개발 환경 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)

---

## 1. Android Studio 설치

<https://developer.android.com/studio> 에서 받아 설치합니다.
설치 중 "Android SDK" 항목은 기본값 그대로 두세요.

> 디스크 20GB, RAM 8GB 이상 필요합니다. 에뮬레이터가 무겁습니다.

---

## 2. Automotive 시스템 이미지 받기

차량용 에뮬레이터 이미지는 기본 설치에 안 들어 있습니다. 따로 받아야 합니다.
**이 단계가 전체에서 가장 틀리기 쉬운 곳입니다.** 이미지 하나가 10GB 넘으니
잘못 받으면 시간을 크게 낭비합니다.

### 2-1. SDK Manager 열기

| 지금 화면 | 여는 방법 |
|---|---|
| 프로젝트가 열려 있음 | **Tools → SDK Manager** |
| 환영 화면 (프로젝트 없음) | 오른쪽 **⚙ 아이콘** 또는 **More Actions → SDK Manager** |
| 어느 쪽이든 | 설정 열기 (`Mac ⌘,` / `Win Ctrl+Alt+S`) → **Languages & Frameworks → Android SDK** |

### 2-2. Show Package Details 켜기

창 상단에 탭이 3개 있습니다: `SDK Platforms` / `SDK Tools` / `SDK Update Sites`.
**`SDK Platforms`** 탭으로 갑니다.

그리고 **오른쪽 아래의 `Show Package Details` 체크박스를 켭니다.**

이걸 켜지 않으면 시스템 이미지가 **아예 목록에 안 나옵니다.** API 레벨별로
한 줄씩만 보이고 끝입니다. 켜면 각 API 아래로 하위 항목이 쭉 펼쳐집니다.

### 2-3. 두 개 체크

목록에서 **`Android 14.0 ("UpsideDownCake")`** 또는 **`Android API 34`** 를 찾습니다.
그 아래 펼쳐진 항목 중 **두 개**를 체크합니다.

**① 플랫폼 (필수)**

```
Android SDK Platform 34
```

이게 있어야 `compileSdk = 34` 로 빌드되고, 차량 API 파일
`android.car.jar` 도 여기 안에 들어옵니다.

**② 시스템 이미지 — 본인 컴퓨터에 맞는 것 하나만**

| 컴퓨터 | 체크할 항목 |
|---|---|
| **Mac (M1·M2·M3·M4)** | `Android Automotive with Google APIs arm64-v8a System Image` |
| **Windows / Intel Mac** | `Android Automotive with Google APIs x86_64 System Image` |

세 가지를 주의하세요.

- **`Google APIs` 를 고르세요. `Google Play` 아닙니다.** Play 버전은 구글 로그인을
  요구하고 더 무겁습니다. MCC는 Play 기능을 쓰지 않습니다.
- **`Automotive` 가 이름에 있어야 합니다.** 그냥 `Google APIs ...System Image` 는
  휴대폰용입니다. 이걸 받으면 MCC가 설치조차 안 됩니다.
- ARM / x86_64 를 반대로 받으면 **부팅이 안 되거나 극도로 느립니다.**

### 2-4. 설치

**Apply → 라이선스 동의 → OK.** 다운로드 10~30분 걸립니다.

> **API 34가 목록에 없으면 API 33으로 하세요.** 33이 가장 안정적입니다.
> 이름이 조금 다릅니다:
> `Android Automotive with Google APIs ARM 64 v8a System Image` (ARM) /
> `Android Automotive with Google APIs Intel x86_64 Atom System Image` (x86).
> 33으로 받았다면 `app/build.gradle.kts` 의 `compileSdk` 와 `targetSdk` 를
> **34 → 33** 으로 바꾸세요. (`minSdk = 28` 은 그대로 둡니다)
>
> SDK Manager에 `34-ext9` 처럼 뒤에 숫자가 붙어 보일 수 있습니다. 같은 것입니다.

---

## 3. 가상 차량(AVD) 만들기

### 3-1. Device Manager 열기

| 지금 화면 | 여는 방법 |
|---|---|
| 프로젝트가 열려 있음 | 오른쪽 사이드바의 **휴대폰 모양 아이콘**, 또는 **Tools → Device Manager** |
| 환영 화면 | **More Actions → Virtual Device Manager** |
| 구버전 Android Studio | **Tools → AVD Manager** |

### 3-2. 기기 만들기

1. **`+`** 버튼 또는 **`Create Virtual Device`** / **`Create Device`**
2. **카테고리에서 `Automotive` 를 고릅니다**
   - 구버전: 왼쪽 **Category** 목록에 `Phone / Tablet / Wear OS / TV / Automotive` 가 있습니다
   - 최신 버전: 상단에 **폼팩터 필터 칩**이 있고 거기서 `Automotive` 를 선택합니다
   - **`Phone` 으로 만들면 MCC가 설치되지 않습니다.** 차량 전용 앱이라서 그렇습니다
3. 기기는 **`Automotive (1024p landscape)`** 를 고르면 충분합니다 → **Next**
4. 시스템 이미지 화면에서 **2번에서 받은 이미지**를 고릅니다
   - 탭이 `Recommended` / `x86 Images` / `Other Images` 로 나뉘어 있으면
     받은 이미지가 있는 탭을 찾으세요
   - 이름 옆에 **⬇ 화살표**가 붙어 있으면 아직 안 받은 것입니다.
     목록에 아무것도 없으면 2번으로 돌아가세요
5. 이름은 아무거나 → **Finish**

### 3-3. 부팅 확인

Device Manager에서 만든 기기의 **▶** 를 눌러 켭니다. 첫 부팅은 2~5분 걸립니다.

**제대로 됐는지 판단하는 기준:** 부팅이 끝나면 **가로로 긴 차량용 화면**이 뜨고,
지도와 미디어 바가 보입니다.

휴대폰 홈 화면처럼 보이면 Automotive 이미지가 아닙니다. 2번부터 다시 하세요.

> 에뮬레이터가 느리면 Device Manager에서 기기 → **Edit** →
> **Show Advanced Settings** → `RAM` 2048MB 이상, `Internal Storage` 2048MB 이상.
>
> **Windows에서 에뮬레이터가 아예 안 뜨면** BIOS에서 가상화
> (Intel VT-x 또는 AMD-V)가 꺼져 있는 경우가 대부분입니다.

---

## 4. 프로젝트 열고 빌드

1. Android Studio → **File → Open** → 압축 푼 **`MCC`** 폴더 선택
   (폴더를 고르세요. 안에 있는 파일이 아니라 `MCC` 폴더 자체입니다)
2. 오른쪽 아래 **Gradle Sync** 가 자동으로 돌아갑니다. 첫 Sync는 5~15분 걸립니다.
3. 상단에서 3번에 만든 AVD를 선택하고 **▶ Run** (`Shift + F10`)

처음 실행하면 권한 요청이 뜹니다. 전부 **허용**을 누르세요.
일부 권한은 거부되어도 정상입니다 (아래 7번 참고).

---

## 5. 바로 확인하기 — 데모 버튼 (시연 도구)

> 데모 버튼·직접 운전 시뮬레이터·샘플 운전자·차량 신호 점검은 **시연 도구**입니다.
> Pleos App Market 에 제출할 때는 `app/build.gradle.kts` 의 `SHOW_DEMO_TOOLS` 를 `"false"` 로 바꾸면 모두 숨겨집니다
> (심사 기준 '더미 데이터·임시 콘텐츠 금지'). 공모전 시연 빌드는 `"true"` 그대로 둡니다.

처음 켜면 **시작 알**이 하나 있습니다. 화면 맨 아래 **`데모 주행`** 버튼을 누르세요.

누를 때마다 지역·스타일이 다른 **가상 주행 1회**가 들어갑니다. 실제 주행과 **같은 경로**로
처리되므로 알이 자라고, 부화하고, 지역 악세사리도 얻습니다.

| 누른 횟수 | 데모 주행 | 1회 거리 | 일어나는 일 |
|---|---|---|---|
| 1 | 출퇴근 도심 (서울) | 2~6km | 알 10% 안팎. 확률표에서 '택시기사 모자·일자 입' 이 ▲운전 으로 오름 |
| 2 | 장거리 고속 (강원) | 91~109km | **부화!** 강원 악세사리를 반드시 가지고 태어남 |
| 3 | 야간 주행 (부산) | 22~33km | 대표 캐릭터가 **부산 악세사리** 획득 |
| 4 | 여유 여행 (제주) | 7~15km | 제주 거리가 쌓임 (20km 까지) |
| 5 | 급한 운전 (경기·인천) | 10~18km | 경기·인천 거리가 쌓임 |
| 6 | 느긋한 운전 (전라) | 9~17km | 전라 거리가 쌓임 |
| 7 | 비 오는 날 (충청) | 약 15~30km | 와이퍼를 켠 채 주행 → 물방울 모자·우천형 ▲ → 다시 1번부터 |

> 1·2·3번 결과는 1,000번 돌려 1,000번 모두 같았습니다. 시연 순서를 그대로 믿어도 됩니다.

### 내 캐릭터 둘 만들기

첫 캐릭터가 부화하면 알 칸 아래에 **`기본 알 받기 (부모 없음)`** 버튼이 생깁니다.
누르면 시작 알과 같은 알을 하나 더 받습니다. 부화시키면 내 캐릭터가 둘이 되어
**내 캐릭터끼리 유전**할 수 있습니다. (알이 이미 있거나 7마리가 다 차면 버튼이 사라집니다)

### 유전까지 시연하기 (1분)

1. 캐릭터 카드의 **`부모로 선택`**
2. **`다른 운전자 유전 코드 입력`** → **`샘플 운전자`** → 예: 부산 택시 기사
3. 화면에 **'이 부모로 알을 만들면'** 확률이 뜹니다 — 부모 형질에 ▲유전, 부모의 악세사리 10~20%
4. **`알 만들기`** → 새 알이 생김. 데모 주행 2~3번이면 부화합니다

> 심사위원도 이 순서로 APK 하나만으로 전체 흐름을 볼 수 있습니다.
> 알 부화에 실제 30km 가 필요해서, 데모가 없으면 아무 일도 안 일어나는 화면만 보게 됩니다.
> 처음부터 다시 하려면 **`전체 초기화`**.

### 직접 운전해 보기 — 앱 안의 시뮬레이터 ★

데모 버튼은 정해진 주행을 한 번에 넣습니다. **내 운전 방식으로 직접 달려 보고 싶으면**
화면의 **`직접 운전해 보기`** 칸을 쓰세요. 에뮬레이터 조작이나 명령어가 필요 없습니다.

| 줄 | 버튼 | 하는 일 |
|---|---|---|
| 기어 | `주행 시작 (기어 D)` / `주차하고 기록 (기어 P)` | 기록 시작 / 끝내고 알·캐릭터에 반영 |
| 목표 속도 | `정지` `30` `60` `100` `130` | 차가 그 속도까지 실제처럼 가속·감속 |
| 운전·날씨 | `운전: 부드럽게 / 급하게` | 급하게 = 급가속·급제동으로 잡힘 (슬픈 눈·번개 모자·꾹 다문 입 ▲) |
| | `야간 ON/OFF` | 빛나는 눈·야간형 ▲ |
| | `지역: 서울 ▸` | 누를 때마다 다음 지역. 그 지역에서 20km 달리면 악세사리 |
| 배속 | `×1` `×10` `×30` | 시간을 빨리 흘림. ×30 이면 60km/h 30km 가 **1분** |

**알 하나 부화시키기 (약 1분)**

1. `주행 시작` → 배속 `×30` → 목표 속도 `60`
2. 상단 숫자가 `거리 30.00 km` 를 넘으면 `주차하고 기록`
3. 알이 부화하고, 그동안의 운전 방식이 확률에 반영됩니다

> 시뮬레이터는 **실제 차량 신호와 똑같은 경로**(`DrivingSession` → 주행 기록 → 부화)로 들어갑니다.
> 배속은 시간만 빨리 흘릴 뿐 결과는 ×1 과 같습니다(테스트로 확인).
> 130 을 고르면 110km/h 를 넘어 과속으로 잡힙니다 — 성장이 느려지고 모범형 확률이 안 오릅니다.

---

### 도감 · 방 꾸미기 · 말풍선

| 기능 | 확인하는 법 |
|---|---|
| 말풍선 | 앱을 켜면 1초 뒤 캐릭터 머리 위에 인사 (3초). 캐릭터(알) **그림을 직접** 누르면 콩 뛰며 한마디. 배경이나 그림 주변 빈 곳을 누르면 반응하지 않고, 그 자리에 소품이 있으면 소품이 잡힘 |
| 주행 화면 | 시뮬레이터가 아니라 에뮬레이터 기어를 `D` 로 → 메뉴가 사라지고 캐릭터 + 아래 한 줄만. `P` 로 돌아오면 복구 |
| 도감 | 메뉴 `도감`. 데모 주행으로 몇 마리 부화시키면 칸이 채워지고, 같은 파츠 3회면 ★ |
| 파츠 꾸미기 | `보관함` → 캐릭터 카드 `파츠 꾸미기 ★` → 슬롯 → ★/★★ (열린 것만 보임) |
| 방 꾸미기 | 메뉴 `방 꾸미기` → 테마를 누르면 배경이 바뀌고, 소품을 누르면 방에 꺼냄 (최대 4개, 다시 누르면 넣음). 잠긴 것은 누르면 여는 조건 안내 |
| 소품 옮기기 | 홈 화면에서 소품을 **0.25초 길게 누르면** 살짝 커지며 들림 → 끌어서 원하는 곳에 놓으면 고정 (기어 P 에서만, 위치는 저장됨) |
| 밤 연출 | 홈 오른쪽 위 **🌙 밤** 버튼으로 켜고 끔. 차량 야간 모드가 바뀌면 자동으로 따라감. 방·캐릭터·소품이 함께 푸르게 어두워지고(0.9초), 켜진 스탠드는 주변에 따뜻한 빛을 비춰 가까운 캐릭터·소품일수록 밝고 따뜻하게 보임. 켜지면 방이 남색으로 어두워지고 스탠드 소품에 불빛, 캐릭터가 "밤이 됐어!" |
| 비 연출 | 홈 오른쪽 위 **☔ 비** 버튼으로 켜고 끔. 와이퍼를 켜면 자동으로 켜지고, 끄면 자동으로 꺼짐 (그 사이에는 버튼이 우선). 방 창문 안에만 비가 내림. 와이퍼는 Pleos 차량 SDK(`ai.pleos.playground:Vehicle`)로 읽음. Pleos 차량 서비스가 없거나 값이 안 오면 버튼으로만 켜짐 (`테스트` → 차량 신호 점검에서 O/△/X 확인) |
| 모두 해금 (시연용) | `테스트` 메뉴 → **시연용 모두 해금** → 도감 31종 ★★, 지역 악세서리 8종, 방 10개·소품 8개가 모두 열리고 보관함 캐릭터가 ★★ 파츠를 입음. 누르기 전 기록은 백업되어 같은 버튼(**해금 전으로 되돌리기**)으로 돌아감. 해금 뒤의 주행 기록은 되돌릴 때 사라짐 |
| 차량 반응 | 기어 `P` 에서 에뮬레이터의 문·동승석·창문·배터리/연료(15% 이하)를 바꾸면 캐릭터가 한마디. 실내등을 켜면 낮에도 방 스탠드가 켜짐. 내비 길 안내를 시작하면 "어디 가?", 도착하면 잠금이 풀릴 때 "도착!". 주행 요약에 방향지시등·목적지 도착 횟수 |
| 창문 마스크 | 방 그림과 같은 크기의 PNG에 **비가 보일 유리만 흰색**, 창살·창호지·커튼·벽은 **투명**으로 칠해 `방이름_window.png`(예: `room_seoul_window.png`)로 넣으면 그 모양대로만 비가 내림. 없으면 `Room.kt` 의 창문 범위(네모·둥근·동그란)를 씀. 기본 방(`room_default_window.png`)이 예시 |
| 방 소품 그림 | 물건만 잘라낸 **투명 PNG**, 위아래·좌우 여백 없이 꽉 맞게 (물건 바닥 = 그림 맨 아래). 이름: `room_item_plant` · `_hatstand` · `_lamp` · `_books` · `_toycar` · `_suitcase` · `_globe` · `_trophy` · `_trophy_silver`. 스탠드는 **켜진 그림 `room_item_lamp_on.png`** 를 따로 넣으면 밤에 자동으로 바뀜 |
| 지역 방 | 그 지역 아이템을 얻으면 열립니다. 그림: `room_seoul` · `room_gyeonggi` · `room_gangwon` · `room_chungcheong` · `room_jeolla` · `room_gyeongsang` · `room_busan` · `room_jeju` (.jpg, 1672×941) |

> ★·★★ 전용 그림(`hair_01_wave_s1.png`, `_s2.png` …)이 없으면 앱이 기본 그림의 색을 바꿔 그립니다.
> 전용 그림을 넣으면 그 그림이 우선합니다.

---

## 6. 에뮬레이터 차량 신호로 주행 — 기어와 속도 조작 (고급)

에뮬레이터 창 오른쪽의 **⋮ (점 세 개)** → **Car data** → **Car sensor data** 탭.

| 하려는 것 | 조작 |
|---|---|
| **주행 시작** | `Car speed` 를 0이 아닌 값으로, `Gear` 를 **P 이외**로 |
| **주행 종료** | `Gear` 를 **P (Park)** 로 |

MCC는 **기어가 P에서 빠지면 기록을 시작하고, P로 돌아오면 형질을 갱신**합니다.
그러니 이 두 개만 조작하면 전체 흐름이 돌아갑니다.

### 최소 재현 (3분)

1. `Gear` → **D**, `Car speed` → **60**
2. 화면 상단이 `기어 D (주행) · 속도 60 km/h` 로 바뀌는지 확인
3. 1~2분 두기 (거리가 쌓입니다)
4. `Gear` → **P**
5. 주행 요약(거리·급가감속·정차시간)과 형질이 화면에 나오는지 확인

> 30km를 채우면 알이 부화합니다. 60km/h로 30분입니다.
> 기다리기 싫으면 `Car speed` 를 **200** 으로 두면 9분, 최대값 **240** 이면 7분 30초입니다.
> (110km/h 를 넘기면 과속으로 잡혀 성장이 느려지고 모범형 확률이 안 오릅니다)

### 지역 악세사리 테스트 — 에뮬레이터 위치 바꾸기

에뮬레이터 기본 위치는 **미국 Mountain View** 라서 화면에 `지역 확인 안 됨` 으로 나옵니다.
정상입니다.

에뮬레이터 **⋮ → Location** → 검색창에 `부산역` 같은 장소 입력 → **Set Location**.
화면 상단이 `지역 부산` 으로 바뀌면, 그 상태로 주행한 거리가 부산에 쌓입니다.

> 위치 권한 창이 떴을 때 **'정확한 위치'** 를 허용해야 합니다.
> 실수로 거부했으면 앱 정보 → 권한 → 위치 에서 다시 켜세요.

---

## 7. 어떤 신호가 실제로 읽히는지 확인

**`차량 신호 확인`** 버튼을 누르세요. 표준 API 신호 · 주행 규제 · Pleos 차량 SDK 신호 8개 · 내비게이션 연결 상태가 나옵니다.

| 표시 | 뜻 |
|---|---|
| O | 값을 실제로 받음 |
| △ | 연결은 됐지만 아직 값이 안 옴 (에뮬레이터에서 그 값을 한 번 바꿔 보세요) 또는 읽기 실패 (괄호 안에 이유) |
| X | Pleos 서비스 없음 (구글 AAOS 에뮬레이터 등) |

에뮬레이터 확장 컨트롤(⋮) → Car data / Vehicle 에서 문·좌석·연료·온도를 바꾸면 주차(P) 상태에서 캐릭터가 한마디 합니다.
이 화면을 캡처해 두면 "어떤 차량 신호를 실제로 받는지"의 증빙이 됩니다.

```
[ MCC 가 쓰는 차량 신호 ]
  O  기어 — 주행 시작·종료 판정
  O  속도 — 거리·급가감속·정차·과속
  O  야간 모드 — 빛나는 눈·야간형
  O  주행 규제 상태 — 주행 중 화면 잠금
```

| 표시 | 뜻 |
|---|---|
| `O` | 정상 |
| `△` | 차량은 지원하지만 읽기 실패 — 권한을 거부했는지 확인 (앱 정보 → 권한) |
| `X` | 이 차량(에뮬레이터)이 그 신호를 제공하지 않음 — 앱은 그 신호만 건너뜁니다 |

2026-11-13 Pleos Connect v2.1.0 에뮬레이터에서 확인한 결과는 README 3-6 에 캡처와 함께 실었습니다.

> 조향각은 제조사 전용 권한이라 MCC 가 읽지 않습니다. 와이퍼·주행거리는 Pleos 차량 SDK(`CAR_WINDOWS`·`CAR_DRIVE`)로 읽습니다.

---

## 8. GUI에 없는 신호 넣기 (야간 모드)

Car data 패널에는 속도와 기어만 있습니다. 나머지는 명령으로 넣습니다.
터미널(Android Studio 하단 **Terminal** 탭)에서:

```bash
# 야간 모드 같은 '변경 시 통지' 속성 — 한 번 넣으면 유지됩니다
#   형식: inject-vhal-event <ID> <영역> <값>
adb shell cmd car_service inject-vhal-event 0x11200407 0 1
```

속도와 기어도 같은 명령으로 넣습니다.

```bash
# 기어 D(8) / P(4)
adb shell cmd car_service inject-vhal-event 0x11400400 0 8
# 60km/h = 16.7 m/s
adb shell cmd car_service inject-vhal-event 0x11600207 0 16.7
```

> `inject-continuous-events` 는 Pleos Connect 에뮬레이터(user 빌드)에서 권한 오류(SecurityException)가 나서 쓸 수 없습니다.
> 넣은 속도가 곧 0 으로 돌아가면 ⋮ → Car data 의 속도 막대를 쓰세요.
> 형질별로 무엇을 조작하면 확률이 오르는지는 docs/DETAILS.md 2-2 에 표로 정리했습니다.
> 속도는 **m/s** 단위입니다. km/h ÷ 3.6 하세요. (60km/h = 16.7)
> 쓸 수 있는 명령 전체는 `adb shell cmd car_service` 를 그냥 실행하면 나옵니다.
> MCC 가 쓰는 신호 ID: 속도 `0x11600207` · 기어 `0x11400400` · 야간 모드 `0x11200407`

---

## 9. 확률·유전 로직만 따로 검증 — 단위 테스트

주행 시나리오를 에뮬레이터로 확인하려면 실시간이 필요합니다(60분 주행 = 60분 대기).
확률·유전·유전 코드·지역 판정은 단위 테스트 112개로 몇 초 안에 검증합니다.

**Android Studio 왼쪽 프로젝트 창 → `app/src/test` 폴더 우클릭 → Run 'Tests in com.mcc.app'**

전부 초록색이면 정상입니다. `Genetics.kt` 의 확률 숫자를 바꿨다면 이걸 먼저 돌려보세요.
(숫자를 바꾸면 일부 테스트가 의도적으로 실패합니다 — 테스트의 기대값도 같이 고치면 됩니다)

---

## 10. 그림 넣기

### 넣는 곳

```
app\src\main\res\drawable-nodpi\
```

탐색기로 이 폴더에 PNG 를 복사하고 **▶ Run** 하면 끝입니다. 코드는 건드릴 필요 없습니다.
캐릭터 카드와 알 칸에 자동으로 겹쳐서 나타나고, 숨쉬듯 살짝 움직입니다.

### 파일 이름 = 아래 이름 + `.png`

| 종류 | 파일 이름 |
|---|---|
| 몸 (1) | `body_base` |
| 종족 (3) | `set_cat` · `set_shiba` · `set_bear` — 귀·꼬리·무늬를 한 장에 |
| 모자 (5) | `hair_01_wave` · `hair_02_spike` · `hair_03_wet` · `hair_04_wind` · `hair_05_flat` |
| 눈 (5) | `eye_01_calm` · `eye_02_sharp` · `eye_03_shiny` · `eye_04_sleepy` · `eye_05_happy` |
| 입 (5) | `mouth_01_flat` · `mouth_02_smile` · `mouth_03_yawn` · `mouth_04_hum` · `mouth_05_tight` |
| 의상 (5) | `outfit_01_marathon` · `outfit_02_medal` · `outfit_03_commute` · `outfit_04_rain` · `outfit_05_night` |
| 지역 악세사리 (8) | `acc_seoul` · `acc_gyeonggi` · `acc_gangwon` · `acc_chungcheong` · `acc_jeolla` · `acc_gyeongsang` · `acc_busan` · `acc_jeju` |
| 알 (1) | `egg` |

**이름 규칙을 어기면 빌드가 실패합니다.** 영어 소문자·숫자·밑줄(`_`)만, 한글·대문자·띄어쓰기 금지.
Procreate 에서 내보내면 `제목 없음.png` 같은 이름이 붙으니 꼭 바꿔 주세요.

### 규격

- **1024 × 1024**, 투명 배경 PNG, **모든 레이어 같은 캔버스** (잘라내지 말 것)
- 기준은 `body_base.png` 입니다. 새 그림은 이 몸 그림을 맨 아래 레이어로 깔고 같은 캔버스에 그리세요
- 앱이 화면 크기에 맞춰 줄여서 읽으므로 1024 그대로 넣어도 됩니다

### 그림 상태 (2026-11-13)

캐릭터 파츠(모자 5 · 눈 5 · 입 5 · 의상 5)·알·방·소품·지역 악세사리 그림이 **모두 팀이 직접 그린 그림**으로 들어가 있습니다. 임시 그림(`ph_`)은 남아 있지 않습니다.

- 그림을 바꾸려면 같은 이름으로 덮어쓰면 됩니다
- 앱은 `ph_` 로 시작하는 파일을 '임시 그림'으로 취급하는 기능을 그대로 가지고 있어, 새 파츠를 추가할 때 임시로 쓸 수 있습니다

### 움직이는 그림 (선택)

번호를 붙여 여러 장 넣으면 자동으로 왕복 재생합니다 (초당 8장).

```
body_base_f1.png  body_base_f2.png  body_base_f3.png  body_base_f4.png
→ 1 → 2 → 3 → 4 → 3 → 2 → 1 … (4장으로 6장처럼)
```

- 움직이는 레이어끼리는 **장수를 똑같이** 맞추세요 (몸 4장이면 머리도 4장)
- 프레임 그림이 하나라도 있으면 코드 숨쉬기는 꺼지고 그림이 움직임을 맡습니다
- `egg_f1` ~ `egg_f4` 로 알이 깨지는 장면도 넣을 수 있습니다

---

## 막혔을 때

| 증상 | 원인과 해결 |
|---|---|
| `import android.car...` 가 빨간 줄 | `app/build.gradle.kts` 의 `useLibrary("android.car")` 확인. IDE만 못 찾는 경우가 있으니 **Build → Rebuild Project** 로 실제 빌드되는지 보세요. 빌드가 되면 무시해도 됩니다 |
| `android.car.jar` 를 못 찾는다 | `<SDK>/platforms/android-34/optional/android.car.jar` 가 있는지 확인. 없으면 2번에서 `Android SDK Platform 34` 를 안 받은 것입니다 |
| 에뮬레이터에 앱이 안 보인다 | Automotive **아닌** 이미지로 AVD를 만든 것입니다. MCC는 `required="true"` 로 차량 전용 선언을 해서 일반 이미지엔 설치되지 않습니다 |
| 에뮬레이터가 안 뜨거나 너무 느리다 | ARM Mac에 x86 이미지(또는 반대)를 받은 경우가 대부분입니다. 2번의 아키텍처를 다시 확인하세요. Windows면 BIOS 가상화 설정도 확인 |
| SDK Manager에 시스템 이미지가 안 보인다 | **Show Package Details** 체크박스를 안 켰습니다 (2-2) |
| `INSTALL_FAILED_MISSING_SHARED_LIBRARY` | 차량 API가 없는 이미지입니다. Automotive 이미지인지 확인하세요 |
| `차량 서비스에 연결할 수 없습니다` | 휴대폰 에뮬레이터에서 돌린 것입니다. Automotive AVD인지 확인하세요 |
| Gradle Sync 가 실패한다 | 인터넷 연결 확인 후 **File → Sync Project with Gradle Files**. 그래도 안 되면 Build 창의 에러 메시지를 확인합니다 |
| 기어를 P로 해도 요약이 안 나온다 | 주행 거리가 1km 미만이면 기록되지 않습니다. 속도를 올리거나 조금 더 기다리세요 |
| 지역이 계속 `확인 안 됨` | 에뮬레이터 위치가 해외입니다 (6번 '위치 바꾸기'). 또는 위치 권한을 거부했습니다 |
| 앱이 꺼진다 | 다시 켜면 아래 '기록' 칸에 **지난번 종료 원인**이 뜹니다. 원인(스택 트레이스)이 함께 표시됩니다 |
| 그림을 넣었더니 빌드 실패 `Invalid resource file name` | 파일 이름에 한글·대문자·띄어쓰기가 있습니다. 10번 이름 규칙을 확인하세요 |
| 그림을 넣었는데 임시 그림이 계속 보인다 | 이름이 한 글자라도 다른 경우입니다. 카드 아래 '아직 필요한 파일' 이름과 비교하세요 |

---

## 참고

- [Android Automotive OS 에뮬레이터 테스트 (공식)](https://developer.android.com/training/cars/testing/emulator)
- [AAOS 첫 앱 만들기 — android.car.jar 경로 포함](https://grapeup.com/blog/how-to-build-your-first-app-for-android-automotive-os)
- [속도 주입이 1초 뒤 덮어쓰이는 문제와 해결](https://github.com/kaeawc/auto-mobile/issues/15910)

## 성능 측정 (Pleos 기준: 주행 중 CPU 5% 미만 · 정차 중 25% 이하 · RAM 400MB 이하)

앱을 켜고 아래 순서로 1분씩 둔 뒤 잽니다: ① 홈(P, 비 연출 켬) ② 도감 화면 ③ 기어 D (주행 중) ④ 다른 앱을 띄워 MCC 를 뒤로.

**메모리** — PowerShell:
```powershell
& "C:\Android\Sdk\platform-tools\adb.exe" shell dumpsys meminfo com.mcc.app
```
`TOTAL PSS` 값(KB)이 400,000 이하면 통과입니다.

**CPU** — Android Studio → View → Tool Windows → **Profiler** → `com.mcc.app` → CPU 그래프의 % 를 봅니다 (전체 CPU 대비).
터미널로 보려면:
```powershell
& "C:\Android\Sdk\platform-tools\adb.exe" shell top -d 5 -n 3 -o PID,%CPU,RES,ARGS | findstr mcc
```
`top` 의 %CPU 는 코어 수만큼 커집니다 (첫 줄의 `800%cpu` = 8코어). 기준과 비교할 때는 코어 수로 나누세요.
에뮬레이터는 실차보다 느려서 값이 높게 나올 수 있습니다. 캡처해서 PT 자료로 쓰세요.
