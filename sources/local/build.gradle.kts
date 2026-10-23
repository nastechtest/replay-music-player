plugins { alias(libs.plugins.replay.android.library) }

android {
    namespace = "app.replay.sources.local"
}

dependencies {
    api(project(":sources:api"))
    implementation(libs.androidx.core.ktx)
}
