package com.monkopedia.kodemirror

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform