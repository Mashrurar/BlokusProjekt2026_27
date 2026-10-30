plugins {
    java
    application
    id("com.gradleup.shadow") version "8.3.10"
}

sourceSets.main.get().java.srcDir("src/main")
sourceSets.main.get().resources.srcDir("src/resources")

application {
    mainClass.set("sc.player.util.Starter")
}

repositories {
    mavenCentral()
    maven("https://maven.wso2.org/nexus/content/groups/wso2-public/")
    maven("https://jitpack.io")
}

dependencies {
    if(gradle.startParameter.isOffline) {
        implementation(fileTree("lib"))
    } else {
        implementation("com.github.software-challenge.backend", "plugin2027", "27.0.6")
        implementation("ch.qos.logback", "logback-classic", "1.5.32")
    }
}

tasks.shadowJar {
    archiveBaseName.set("blokus_2027_client")
    archiveClassifier.set("")
    destinationDirectory.set(rootDir)
}
