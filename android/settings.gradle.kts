pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AstraAssistant"
include(":app")

val llamaDir = file("third_party/llama.cpp/examples/llama.android/lib")
check(llamaDir.exists()) {
    "llama.cpp Android library is missing. Run scripts/fetch_native_deps.sh (or .bat on Windows) before Gradle."
}
include(":llama-lib")
project(":llama-lib").projectDir = llamaDir

val whisperDir = file("third_party/whisper.cpp/examples/whisper.android/lib")
check(whisperDir.exists()) {
    "whisper.cpp Android library is missing. Run scripts/fetch_native_deps.sh (or .bat on Windows) before Gradle."
}
include(":whisper-lib")
project(":whisper-lib").projectDir = whisperDir
