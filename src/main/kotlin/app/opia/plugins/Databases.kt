package app.opia.plugins

import app.opia.services.DatabaseSingleton
import io.ktor.server.application.*

fun Application.configureDatabases() {
    DatabaseSingleton.init(environment.config)
}
