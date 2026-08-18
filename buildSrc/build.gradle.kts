plugins {
    `kotlin-dsl`
}

repositories {
    maven {
        name = "AliyunPublicMirror"
        url = uri("https://maven.aliyun.com/repository/public")
    }
    mavenCentral()
}
