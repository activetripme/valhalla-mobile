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

    coordinates("io.github.activetripme", "valhalla-mobile", project.version.toString())

    configure(AndroidSingleVariantLibrary(sourcesJar = true, publishJavadocJar = true))

    pom {
        name.set("Valhalla Mobile")
        url.set("https://github.com/activetripme/valhalla-mobile")
        description.set("A mobile app focused wrapper library for the valhalla routing engine")
        inceptionYear.set("2024")
        licenses {
            license {
                name.set("MIT")
                url.set("https://github.com/activetripme/valhalla-mobile?tab=MIT-1-ov-file#MIT-1-ov-file")
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
            connection.set("scm:git:https://github.com/activetripme/valhalla-mobile.git")
            developerConnection.set("scm:git:ssh://github.com/activetripme/valhalla-mobile.git")
            url.set("https://github.com/activetripme/valhalla-mobile")
        }
    }
}

// Publish a full maven layout to a local directory; CI then deploys it to the gh-pages branch,
// which GitHub Pages serves at https://activetripme.github.io/valhalla-mobile/maven/. Anonymous
// for consumers — no PAT, unlike GitHub Packages — so any developer's build resolves out of the
// box. The .pom/.module carry transitive deps (moshi, valhalla-models, osrm-api), so consumers
// don't list them by hand. CI runs `publishAllPublicationsToGitHubPagesRepository`.
publishing {
    repositories {
        maven {
            name = "GitHubPages"
            url = uri(layout.buildDirectory.dir("maven-repo"))
        }
    }
}
