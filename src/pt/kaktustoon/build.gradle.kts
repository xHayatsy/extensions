import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kaktustoon"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://kaktustoon.com"
    }

    deeplink {
        path("/pt/anime/..*")
    }
}
