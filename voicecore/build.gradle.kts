import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure-Kotlin core: NLU (tagger, numbers, fuzzy, matcher) + the command catalog.
// No Android dependencies, so the matching logic is unit-tested on the JVM in seconds.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// Recognizer comparison harness (see src/test/.../eval/NluEval.kt).
tasks.register<JavaExec>("nluEval") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.offline.bydvoice.eval.NluEvalKt")
}
