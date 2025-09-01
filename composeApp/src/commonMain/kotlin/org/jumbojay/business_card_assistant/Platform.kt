package org.jumbojay.business_card_assistant

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform