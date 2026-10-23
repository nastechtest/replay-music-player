plugins { alias(libs.plugins.replay.android.library) }

android {
    namespace = "app.replay.sources.test"
}

dependencies {
    api(project(":sources:api"))
}
