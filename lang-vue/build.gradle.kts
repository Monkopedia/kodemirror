plugins {
    id("kodemirror.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":lezer-common"))
            implementation(project(":lezer-highlight"))
            implementation(project(":lezer-lr"))
            implementation(project(":language"))
            implementation(project(":state"))
            implementation(project(":lang-html"))
            implementation(project(":lang-javascript"))
        }
    }
}
