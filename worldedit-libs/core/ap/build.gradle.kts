applyLibrariesConfiguration()

repositories{
    maven {
        name = "EngineHub"
        url = uri("https://maven.enginehub.org/repo/")
    }
}

dependencies {
    "shade"(libs.pistonAnnotations)
    "shade"(libs.pistonProcessor)
}
