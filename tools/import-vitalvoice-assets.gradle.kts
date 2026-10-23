// One-time, allowlisted binary resource import. Never reads credentials or overwrites a different file.
gradle.projectsEvaluated {
    rootProject.tasks.register("importVitalVoiceAssets") {
        doLast {
            val source = java.io.File(providers.gradleProperty("legacyResources").get())
            val target = java.io.File(rootProject.projectDir, "app/src/main/res")
            val files = mapOf(
                "drawable/app_icon.png" to "drawable-nodpi/vitalvoice_logo.png",
                "font/nunito_sans_semibold.ttf" to "font/nunito_sans_semibold.ttf",
                "font/nunito_sans_light.ttf" to "font/nunito_sans_light.ttf",
                "font/nunito_sans.ttf" to "font/nunito_sans.ttf"
            )
            files.forEach { (from, to) ->
                val original = java.io.File(source, from)
                val destination = java.io.File(target, to)
                check(original.isFile) { "Missing resource: $from" }
                if (destination.exists()) {
                    check(original.readBytes().contentEquals(destination.readBytes())) { "Refusing to overwrite: $to" }
                } else {
                    check(destination.parentFile.isDirectory || destination.parentFile.mkdirs())
                    java.nio.file.Files.copy(original.toPath(), destination.toPath())
                }
                println("Verified resource: $from -> $to")
            }
        }
    }
}
