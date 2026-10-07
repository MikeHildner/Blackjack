// Top-level build file. Plugins are declared here with `apply false` so that
// each module can opt in to the ones it needs while sharing one version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
