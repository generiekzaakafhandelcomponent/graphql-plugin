/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ritense.valtimoplugins.graphql.auth

enum class AuthType { NONE, CLIENT_SECRET, PRIVATE_KEY_JWT }

/** Thrown when a plugin configuration property is missing or invalid. The message starts with the property key. */
class InvalidPluginPropertyException(
    val property: String,
    reason: String,
) : IllegalArgumentException("$property: $reason")

/**
 * How the plugin authenticates against the GraphQL endpoint. Instances are also the access token cache key, so they
 * hold the complete configuration; toString() is overridden wherever a secret is held.
 */
sealed interface GraphQLAuthentication {
    val authType: AuthType

    companion object {
        /**
         * Builds and validates the authentication from the plugin properties. Configurations from before authType
         * existed have no authType: they use CLIENT_SECRET when tokenUrl or clientId is set, and NONE otherwise.
         */
        fun of(
            authType: AuthType?,
            tokenUrl: String? = null,
            tenantId: String? = null,
            clientId: String? = null,
            clientSecret: String? = null,
            privateKey: String? = null,
            certificate: String? = null,
            resourceUri: String? = null,
        ): GraphQLAuthentication =
            when (authType ?: if (tokenUrl.isNullOrBlank() && clientId.isNullOrBlank()) AuthType.NONE else AuthType.CLIENT_SECRET) {
                AuthType.NONE -> NoAuthentication
                AuthType.CLIENT_SECRET ->
                    if (!tokenUrl.isNullOrBlank()) {
                        ClientSecretAuthentication(tokenUrl, required("clientId", clientId), required("clientSecret", clientSecret))
                    } else {
                        EntraClientSecretAuthentication(
                            tenantId(tenantId),
                            required("clientId", clientId),
                            required("clientSecret", clientSecret),
                            resourceUri(resourceUri),
                        )
                    }
                AuthType.PRIVATE_KEY_JWT -> {
                    val key = PemLoader.loadPrivateKey(required("privateKey", privateKey))
                    val cert = PemLoader.loadCertificate(required("certificate", certificate))
                    PemLoader.requireMatchingKeyPair(key, cert)
                    EntraPrivateKeyJwtAuthentication(
                        tenantId(tenantId),
                        required("clientId", clientId),
                        privateKey!!,
                        certificate!!,
                        resourceUri(resourceUri),
                    )
                }
            }

        private val GUID = Regex("^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$")

        private fun required(
            property: String,
            value: String?,
        ): String = value?.takeIf { it.isNotBlank() } ?: throw InvalidPluginPropertyException(property, "is required")

        private fun tenantId(value: String?): String =
            required("tenantId", value).trim().also {
                if (!GUID.matches(it)) throw InvalidPluginPropertyException("tenantId", "must be a GUID (directory/tenant ID)")
            }

        // The scope is always {resourceUri}/.default, so the resource URI must not carry a scope itself.
        private fun resourceUri(value: String?): String =
            required("resourceUri", value).trim().trimEnd('/').also {
                if (it.any(Char::isWhitespace) || it.endsWith("/.default")) {
                    throw InvalidPluginPropertyException("resourceUri", "must be the resource URI without scope, e.g. https://graph.microsoft.com")
                }
            }
    }
}

data object NoAuthentication : GraphQLAuthentication {
    override val authType = AuthType.NONE
}

/** OAuth2 client credentials flow with an access token sent as bearer token. */
sealed interface OAuth2Authentication : GraphQLAuthentication {
    val clientId: String
}

/** Client credentials against any OAuth2 token URL with HTTP Basic client authentication (behaviour of plugin 1.0-1.2). */
data class ClientSecretAuthentication(
    val tokenUrl: String,
    override val clientId: String,
    val clientSecret: String,
) : OAuth2Authentication {
    override val authType = AuthType.CLIENT_SECRET

    override fun toString() = "ClientSecretAuthentication(tokenUrl=$tokenUrl, clientId=$clientId)"
}

/** Client credentials against Microsoft Entra ID, for application permissions on [resourceUri]. */
sealed interface EntraAuthentication : OAuth2Authentication {
    val tenantId: String
    val resourceUri: String
    val scope get() = "$resourceUri/.default"
}

data class EntraClientSecretAuthentication(
    override val tenantId: String,
    override val clientId: String,
    val clientSecret: String,
    override val resourceUri: String,
) : EntraAuthentication {
    override val authType = AuthType.CLIENT_SECRET

    override fun toString() = "EntraClientSecretAuthentication(tenantId=$tenantId, clientId=$clientId, resourceUri=$resourceUri)"
}

/** Client credentials with a client assertion signed by [privateKey] (PEM); [certificate] (PEM) is the uploaded public certificate. */
data class EntraPrivateKeyJwtAuthentication(
    override val tenantId: String,
    override val clientId: String,
    val privateKey: String,
    val certificate: String,
    override val resourceUri: String,
) : EntraAuthentication {
    override val authType = AuthType.PRIVATE_KEY_JWT

    override fun toString() = "EntraPrivateKeyJwtAuthentication(tenantId=$tenantId, clientId=$clientId, resourceUri=$resourceUri)"
}
