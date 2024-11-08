package app.opia

import app.opia.plugins.*
import app.opia.routes.MessagePacket
import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

//fun main() {
//    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
//}
fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // NOTE could do defaults in code
    val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"
    val portCfg = environment.config.property("ktor.deployment.port").getString()
    val env = environment.config.propertyOrNull("ktor.environment")?.getString()
    log.info("env: $env, devMode: $developmentMode, port: $port / $portCfg")

    val tokenSecret = environment.config.property("jwt.secret").getString()
    val tokenIssuer = environment.config.property("jwt.issuer").getString()
    val tokenAudience = environment.config.property("jwt.audience").getString()
    val tokenRealm = environment.config.property("jwt.realm").getString()
    log.info("jwt - iss: $tokenIssuer, aud: $tokenAudience, realm: $tokenRealm")

    configureDatabases()
    configureAdministration()
    configureSockets()
    configureSerialization()
    configureMonitoring()
    configureSecurity(tokenRealm, tokenAudience, tokenIssuer, tokenSecret)
    configureRouting(tokenAudience, tokenIssuer, tokenSecret)
}
