import com.android.build.api.dsl.ApplicationExtension

dependencies {
    compileOnly(libs.morphe.extensions.library)
    compileOnly(project(":extensions:shared-youtube:library"))
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:youtube:stub"))
    compileOnly(libs.annotation)

    implementation("com.jakewharton.timber:timber:4.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.browser:browser:1.8.0")
}

configure<ApplicationExtension> {
    defaultConfig {
        minSdk = 26
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

