package app.opia

import app.opia.plugins.*
import app.opia.services.SecurityConfig
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // NOTE could do defaults in code
    val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"
    val portCfg = environment.config.property("ktor.deployment.port").getString()
    val env = environment.config.propertyOrNull("ktor.environment")?.getString()
    log.info("env: $env, devMode: $developmentMode, port: $port / $portCfg")

    val tokenIssuer = environment.config.property("jwt.issuer").getString()
    val tokenAudience = environment.config.property("jwt.audience").getString()
    val tokenRealm = environment.config.property("jwt.realm").getString()
    val tokenSecret = environment.config.property("jwt.secret").getString()
    log.info("jwt - iss: $tokenIssuer, aud: $tokenAudience, realm: $tokenRealm")

    val securityCfg = SecurityConfig(tokenIssuer, tokenAudience, tokenRealm, tokenSecret)

    configureDatabases()
    configureAdministration()
    configureSockets()
    configureSerialization()
    configureMonitoring()
    configureSecurity(securityCfg)
    configureRouting(securityCfg)
}
