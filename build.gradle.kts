// Root build script: declare plugin versions once, apply them in the modules.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Room's annotation processor; the version tracks the Kotlin version above.
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
