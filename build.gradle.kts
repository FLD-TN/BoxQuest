plugins {
    java
}

group = "vn.boxpvp"
version = "0.3.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.12")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
    // BoxCore API: chỉ dùng lúc biên dịch, KHÔNG đóng gói vào jar (BoxCore cung cấp lúc chạy)
    compileOnly(files("libs/boxcore-api-1.2.0.jar"))

    // Test: cùng phiên bản với BoxCore
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // Paper cung cấp driver SQLite lúc chạy; test cần bản riêng
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
