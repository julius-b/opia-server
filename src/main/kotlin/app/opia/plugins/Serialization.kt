package app.opia.plugins

import app.opia.utils.UUIDSerializer
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            explicitNulls = false
            // NOTE disable for prod?
            prettyPrint = true
            namingStrategy = JsonNamingStrategy.SnakeCase
            serializersModule = SerializersModule {
                contextual(UUID::class, UUIDSerializer)
            }
        })
    }
}
