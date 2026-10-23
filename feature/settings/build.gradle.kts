plugins {
    alias(libs.plugins.replay.android.library)
    alias(libs.plugins.replay.android.compose)
}

android {
    namespace = "app.replay.feature.settings"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:interaction"))
    implementation(project(":sources:api"))
    implementation(project(":player:api"))
    implementation(libs.androidx.lifecycle.runtime.compose)
}
