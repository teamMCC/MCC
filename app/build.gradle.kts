plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mcc.app"
    compileSdk = 34

    // ★ 차량 API(android.car)를 컴파일 경로에 올립니다.
    //   이 줄이 없으면 import android.car... 가 전부 실패합니다.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.mcc.app"
        minSdk = 28          // 공모전 요구: Android API 28 이상
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // ★ 시연 도구 (데모 주행 · 직접 운전 시뮬레이터 · 샘플 운전자 · 차량 신호 점검)
        //   공모전 시연용 빌드: true
        //   Pleos App Market 제출 빌드: false  ← 심사 기준 '더미 데이터·임시 콘텐츠 금지'
        buildConfigField("boolean", "SHOW_DEMO_TOOLS", "true")
    }

    buildTypes {
        release {
            // 공모전 제출은 release 빌드입니다.
            // 서명 설정 전까지는 디버그 키로 서명해 설치 가능하게 둡니다.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        // android.car 는 선택적 라이브러리라 Lint 가 NewApi 로 오탐합니다.
        // 빌드가 멈추면 제출이 막히므로 경고로만 남깁니다.
        abortOnError = false
        disable += setOf("NewApi")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Pleos 전용 차량 SDK — 와이퍼·문·좌석·연료처럼 표준 API 로는 일반 앱이 읽기 어려운 신호에 씁니다.
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // Pleos 내비게이션 SDK — 길 안내 시작·도착 알림
    implementation("ai.pleos.playground:NaviHelper:3.0.5")

    // 단위 테스트 (app/src/test) — 확률·유전·유전 코드·지역 판정 검증
    // Android Studio: app/src/test 폴더 우클릭 → Run 'Tests in com.mcc.app'
    testImplementation("junit:junit:4.13.2")
}
