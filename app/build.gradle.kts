import java.net.URI
import java.util.Properties

plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "com.vitaltriage.app"
    compileSdk = 36
    ndkVersion = "29.0.14033849"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    androidResources { noCompress += "bin" }
    bundle { language { enableSplit = false } } // In-app EN/TR/FR/AR switching must also work offline.
    defaultConfig {
        applicationId = "com.vitaltriage.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.2-personal"
        buildConfigField("boolean", "LOCAL_DEVELOPMENT", "false")
        buildConfigField("boolean", "DIRECT_OPENAI", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
        externalNativeBuild { cmake { arguments += listOf("-DCMAKE_BUILD_TYPE=Release"); cppFlags += "-O3" } }
    }
    val signingStore = providers.environmentVariable("VT_SIGNING_STORE").orNull
    if (signingStore != null) signingConfigs.create("ownerRelease") {
        storeFile = file(signingStore)
        storePassword = providers.environmentVariable("VT_SIGNING_STORE_PASSWORD").get()
        keyAlias = providers.environmentVariable("VT_SIGNING_ALIAS").get()
        keyPassword = providers.environmentVariable("VT_SIGNING_KEY_PASSWORD").get()
        enableV1Signing = false
        enableV2Signing = true
        enableV3Signing = true
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "DEFAULT_ENDPOINT", "\"http://127.0.0.1:8787\"")
            buildConfigField("boolean", "LOCAL_DEVELOPMENT", "true")
        }
        getByName("release") {
            // A missing verified production endpoint is an explicit setup state, never localhost.
            val endpoint = providers.gradleProperty("productionEndpoint").orNull.orEmpty()
            require(endpoint.isEmpty() || runCatching {
                val uri = URI(endpoint)
                uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null
            }.getOrDefault(false)) { "productionEndpoint must be a credential-free HTTPS URL" }
            buildConfigField("String", "DEFAULT_ENDPOINT", "\"$endpoint\"")
            if (signingStore != null) signingConfig = signingConfigs.getByName("ownerRelease")
            isDebuggable = false
            isMinifyEnabled = false // Enable only after JNI/serialization shrinker verification.
        }
        create("device") {
            initWith(getByName("release"))
            isDebuggable = true // Personal Android Studio Run; key is intentionally extractable.
            buildConfigField("boolean", "DIRECT_OPENAI", "true")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        create("uiTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            matchingFallbacks += listOf("debug")
        }
    }
    // Release models are explicitly prepared after install; never embed 729 MB of weights.
    sourceSets.getByName("main").assets.setSrcDirs(listOf("src/commonAssets"))
    if (providers.gradleProperty("compactModels").orNull != "true") {
        sourceSets.getByName("debug").assets.srcDir("src/main/assets")
        sourceSets.getByName("uiTest").assets.srcDir("src/main/assets")
    }
    testBuildType = when {
        providers.gradleProperty("isolatedUiTest").orNull == "true" -> "uiTest"
        providers.gradleProperty("deviceSmokeTest").orNull == "true" -> "device"
        else -> "debug"
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    // ggml discovers x86 CPU variants by enumerating nativeLibraryDir. APK-only mmap
    // packaging leaves that directory empty and makes valid Whisper models fail to load.
    // Extract signed packaged libraries; every ELF still uses 16 KB page alignment.
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"; jniLibs.useLegacyPackaging = true }
}
dependencies {
    implementation(project(":core"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    add("uiTestImplementation", "androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.02"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}


// Owner-authorized personal device build. Never package this generated secret in other variants.
val personalDeviceAssets = layout.buildDirectory.dir("generated/personalDeviceAssets")
android.sourceSets.getByName("device").assets.srcDir(personalDeviceAssets)
val preparePersonalDeviceAssets = tasks.register("preparePersonalDeviceAssets") {
    outputs.upToDateWhen { false } // Re-read the preserved budget and exact current prompt for each build.
    doLast {
        val settingsFile = rootProject.file(".runtime/device-secrets.properties")
        require(settingsFile.isFile) { "Personal device secrets configuration missing; see docs/HANDOFF.md." }
        val properties = Properties().apply { settingsFile.inputStream().use { load(it) } }
        val reference = properties.getProperty("referenceFile").orEmpty()
        val explicit = properties.getProperty("openaiApiKey").orEmpty()
        val keys = if (explicit.isNotBlank()) setOf(explicit) else {
            require(reference.isNotBlank()) { "No authorized personal key source configured." }
            Regex("sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}").findAll(file(reference).readText()).map { it.value }.toSet()
        }
        require(keys.size == 1 && keys.single().matches(Regex("sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}"))) {
            "Exactly one valid personal key is required; values are suppressed."
        }
        val budgetFile = rootProject.file(".runtime/live-budget.json")
        require(budgetFile.isFile) { "Preserved API budget is missing; refusing a fresh allowance." }
        val seed = budgetFile.readText()
        val parsed = groovy.json.JsonSlurper().parseText(seed) as Map<*, *>
        val attempts = parsed["attempts"] as List<*>
        require((parsed["version"] as Number).toInt() == 1 && attempts.size >= 17) {
            "Preserved API history is incomplete; no budget reset permitted."
        }
        val directory = personalDeviceAssets.get().asFile.apply { mkdirs() }
        directory.resolve("personal-openai-key.txt").writeText(keys.single())
        directory.resolve("openai-budget-seed.json").writeText(seed)
        directory.resolve("triage-system.txt").writeText(rootProject.file("backend/src/main/resources/triage-system.txt").readText())
    }
}
tasks.matching { it.name == "mergeDeviceAssets" }.configureEach { dependsOn(preparePersonalDeviceAssets) }

// Recreate only generated isolated APKs when switching bundled/compact model packaging.
// Incremental ZIP updates can retain hundreds of MB of unused space from removed assets.
tasks.register<Delete>("cleanUiTestPackage") {
    delete(layout.buildDirectory.dir("outputs/apk/uiTest"))
    delete(layout.buildDirectory.dir("intermediates/incremental/packageUiTest"))
}
