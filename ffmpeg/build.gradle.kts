// Media3's FFmpeg module (Apache 2.0), with video decoding added for the Thor (D49).
// The native library is built by build.sh and committed under src/main/jniLibs.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("proguard-rules.txt")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Media3's own code, using its unstable API from inside its package.
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    api(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.annotation)
    compileOnly(libs.checker.qual)
    testImplementation(libs.junit)
}
