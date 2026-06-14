import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost
import java.net.URI

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.dokka)
}

android {
    namespace = "com.valhalla.valhalla"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dokka {
    moduleName.set("Valhalla Mobile")

    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("docs"))
    }

    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(URI("https://github.com/Rallista/valhalla-mobile"))
            remoteLineSuffix.set("#L")
        }

        includes.from(
            fileTree("docs") {
                include("**/*.md")
            }
        )
    }
}

dependencies {
    implementation(libs.core.ktx)

    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)

    implementation(libs.valhalla.models.api)
    implementation(libs.valhalla.models.config)
    implementation(libs.osrm.api)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}

val archs = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

// Define a custom task to run the shell script
archs.forEach { arch ->
    tasks.register<Exec>("buildValhallaFor-${arch}") {
        description = "Build libValhalla for $arch architecture"
        group = "build"

        // Change the working door to the repository root.
        workingDir = file("${project.projectDir}/../../")
        environment("VCPKG_ROOT", "${workingDir.absolutePath}/vcpkg")

        commandLine("bash", "./build.sh", "--android", arch)

        onlyIf {
            !file("src/main/jniLibs/${arch}/libvalhalla-wrapper.so").exists()
        }
    }
}

tasks.named("preBuild") {
    // Efficiently build any architecture that doesn't exist in jniLibs.
    dependsOn("buildValhallaFor-arm64-v8a")
    dependsOn("buildValhallaFor-armeabi-v7a")
    dependsOn("buildValhallaFor-x86_64")
    dependsOn("buildValhallaFor-x86")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    // Signing disabled: the fork publishes to GitHub Packages (no signature requirement) and lacks
    // the upstream MAVEN_GPG_* secrets. Re-enable signAllPublications() only if publishing to a
    // repository that demands signed artifacts.
    // signAllPublications()

    if (project.version.toString() === "unspecified") {
        throw IllegalArgumentException("Version must be specified")
    }

    coordinates("io.github.rallista", "valhalla-mobile", project.version.toString())

    configure(AndroidSingleVariantLibrary(sourcesJar = true, publishJavadocJar = true))

    pom {
        name.set("Valhalla Mobile")
        url.set("https://github.com/Rallista/valhalla-mobile")
        description.set("A mobile app focused wrapper library for the valhalla routing engine")
        inceptionYear.set("2024")
        licenses {
            license {
                name.set("MIT")
                url.set("https://github.com/Rallista/valhalla-mobile?tab=MIT-1-ov-file#MIT-1-ov-file")
            }
        }
        developers {
            developer {
                name.set("Jacob Fielding")
                organization.set("Rallista")
                organizationUrl.set("https://rallista.app")
            }
        }
        contributors {
            contributor {
                name.set("Valhalla")
                organizationUrl.set("https://github.com/valhalla/valhalla")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/Rallista/valhalla-mobile.git")
            developerConnection.set("scm:git:ssh://github.com/Rallista/valhalla-mobile.git")
            url.set("https://github.com/Rallista/valhalla-mobile")
        }
    }
}

// Fork publishing target: Maven Central is unavailable (the io.github.rallista namespace and the
// MAVEN_CENTRAL_* / MAVEN_GPG_* secrets are owned by upstream Rallista). GitHub Packages publishes
// the same `io.github.rallista:valhalla-mobile` coordinates into this fork's package registry, so
// consumers only add this repository + a read:packages PAT — no coordinate changes.
// CI publishes via `publishAllPublicationsToGitHubPackagesRepository`; local builds skip it unless
// GITHUB_ACTOR/GITHUB_TOKEN are set (credentials block simply fails closed without them).
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/activetripme/valhalla-mobile")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
