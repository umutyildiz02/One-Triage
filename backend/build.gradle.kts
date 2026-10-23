plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(17) }
application { mainClass.set("com.vitaltriage.backend.ServerKt") }
dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}
