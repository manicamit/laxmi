plugins {
    // AGP 9 has built-in Kotlin — do NOT also apply org.jetbrains.kotlin.android
    // (it collides: "extension with name 'kotlin' already registered").
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
