plugins {
    java
}

group = "com.boogeraids"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper 26.x 버전 체계: <mcver>.build.<빌드번호>-<상태>
    compileOnly("io.papermc.paper:paper-api:26.3.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

// resourcepack/ 폴더를 zip 으로 묶어 jar 안에 포함 (플러그인이 내장 HTTP 서버로 배포)
val packZip by tasks.registering(Zip::class) {
    from("resourcepack")
    archiveFileName.set("BOOGERAIDS-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    dependsOn(packZip)
    from(packZip)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
