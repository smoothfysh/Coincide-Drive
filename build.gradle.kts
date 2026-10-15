plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val externalBuildDir = file("${System.getProperty("user.home")}/.gradle-builds/CoincideDrive")

allprojects {
    layout.buildDirectory.set(file("$externalBuildDir/${project.name}"))
}

