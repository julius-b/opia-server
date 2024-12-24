package app.opia.services

data class SecurityConfig(
    val iss: String,
    val aud: String,
    val realm: String,
    val secret: String
)
