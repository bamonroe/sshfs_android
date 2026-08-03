plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.bam.sshfs"
    // 35 and 34 are the platforms baked into the /data/android builder image.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bam.sshfs"
        // 26 is the floor for StorageManager.openProxyFileDescriptor, which the
        // DocumentsProvider needs for random-access streaming reads/writes.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Room's exported schema JSON doubles as fixture data for migration tests.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    // The Bouncy Castle and SSHJ jars are signed and multi-release; their signature
    // and versioned entries have no meaning inside an APK and collide if kept.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/versions/**",
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
            )
        }
    }
}

// Room writes its schema JSON here so migrations can diff against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // SSHJ parses imported private keys — including bcrypt-KDF OpenSSH v1 blocks —
    // and will carry the SFTP transport later. Bouncy Castle backs both it and the
    // on-device key generation; Android's cut-down "BC" provider is not enough.
    implementation("com.hierynomus:sshj:0.38.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // The bottom nav bar's tab icons come from the core icon set.
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // A real SFTP server in-process, so SshjSftpSession is tested against the
    // protocol rather than against a mock of it.
    testImplementation("org.apache.sshd:sshd-core:2.12.1")
    testImplementation("org.apache.sshd:sshd-sftp:2.12.1")
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
