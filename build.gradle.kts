plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "me.deob"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.23.0")
    implementation("org.openjdk.nashorn:nashorn-core:15.3")
}

application {
    mainClass.set("me.deob.Main")
}