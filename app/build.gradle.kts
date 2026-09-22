import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    // El plugin oss-licenses-plugin es incompatible con AGP 9.x debido al uso de 'applicationVariants'
    // id("com.google.android.gms.oss-licenses-plugin")
}

android {
    namespace = "com.gaspar.rutassendero"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gaspar.rutassendero"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        val currentDate = SimpleDateFormat("dd/MMM/yyyy hh:mm a", Locale.forLanguageTag("es-MX")).format(Date())
        buildConfigField("String", "UPDATE_DATE", "\"$currentDate\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("tksenderismo.apk")
        }
    }
}


dependencies {
    // Biometric authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // AndroidX Core y Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0") // Requerido por la actividad de Licencias
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Jetpack Compose (Material 3)
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room (Base de Datos SQLite Moderna)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Google Play Services Location (para GPS preciso y eficiente)
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // Google Play Services Maps (para visualizar el mapa de rutas)
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    // Google Play Services Auth (para inicio de sesión de Google y tokens de Drive)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // API REST PostgREST y Cloudinary para sincronización en la nube
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.cloudinary:cloudinary-android:3.0.2")

    // Pantalla de Licencias Open Source de Google
    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")

    // Pruebas
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.postgresql:postgresql:42.2.5")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
