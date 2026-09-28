plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 云端分片数据地址（dist/remote/ 上传后的公共访问前缀）。两种配置方式，任选其一：
//   1. gradle.properties 写 poetry.dataBaseUrl=...
//   2. 命令行传 -Ppoetry.dataBaseUrl=...
// 留空则仅使用内置数据（热门 5000 首可离线阅读，其余诗词提示未配置数据源）。
val dataBaseUrl: String = providers.gradleProperty("poetry.dataBaseUrl").getOrElse("")

android {
    namespace = "me.javayhu.poetry"
    compileSdk = 36

    signingConfigs {
        // 发布密钥从环境变量读取（CI 上由 GitHub Secrets 注入）。
        // 未提供时该配置不会创建，构建回退到 debug 签名 ——
        // 但 CI 上的 debug 密钥每次构建都会重新生成，那样升级必须卸载重装，
        // 所以正式分发务必配置 KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD。
        val keystorePath = System.getenv("KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "me.javayhu.poetry"
        minSdk = 28          // Android 9
        targetSdk = 36       // Android 16（当前最新）
        versionCode = 95
        versionName = "4.1.1"

        buildConfigField("String", "DATA_BASE_URL", "\"$dataBaseUrl\"")

        // 本项目为纯 Kotlin 实现，不含任何 native .so，因此 APK 天然同时支持
        // 32/64 位（arm64-v8a、armeabi-v7a、x86_64 均可运行）。
        // 若将来引入 native 库，请在此显式声明，且必须保留 arm64-v8a：
        //   ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        release {
            // 有固定密钥就用它，保证后续版本能直接覆盖升级；否则回退 debug
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        // AGP 8 起默认关闭，代码里用到 BuildConfig.DATA_BASE_URL，必须显式开启
        buildConfig = true
    }

    // 注意：assets 里的内置数据必须是纯文本（不带 .gz 后缀）。
    // AAPT2 会在打包时自动解压以 .gz 结尾的 assets，运行时读到的就不是 gzip 流了。
    // 纯文本交给 APK 自身压缩即可，实测压缩效果与 gzip 相当。
    packaging {
        resources.excludes += setOf(
            "META-INF/*.kotlin_module",
            "META-INF/DEPENDENCIES",
            "kotlin/**"
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
}
