plugins { `java-library` }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.133-stable")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}
