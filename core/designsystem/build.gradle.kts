plugins {
    alias(libs.plugins.replay.android.library)
    alias(libs.plugins.replay.android.compose)
}

android {
    namespace = "app.replay.core.designsystem"
}

dependencies {
    api(project(":core:interaction"))
    implementation(libs.backdrop)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
