plugins { alias(libs.plugins.replay.android.library) }

android {
    namespace = "app.replay.player.remote"
}

dependencies {
    api(project(":player:api"))
    implementation(libs.androidx.core.ktx)
}
