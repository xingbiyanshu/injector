plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "com.sissi.kinjector"
version = "1.0.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

dependencies{
    implementation("com.android.tools.build:gradle:4.2.2")
    implementation("org.javassist:javassist:3.28.0-GA")
}