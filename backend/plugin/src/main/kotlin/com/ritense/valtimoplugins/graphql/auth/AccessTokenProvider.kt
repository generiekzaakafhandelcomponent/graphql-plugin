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

import com.nimbusds.jwt.JWTParser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.URI
import java.text.ParseException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/** Thrown when no access token can be obtained. Never contains secrets, assertions or tokens. */
class TokenRequestException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Obtains OAuth2 access tokens with the client credentials flow and caches them per configuration until less than
 * 30 seconds of their lifetime remain. Concurrent callers for the same configuration share one token request.
 */
class AccessTokenProvider(
    private val restClient: RestClient,
    private val entraAuthority: String = "https://login.microsoftonline.com",
    private val retryBackoff: Duration = Duration.ofMillis(500),
) {
    private val tokenCache = ConcurrentHashMap<OAuth2Authentication, CachedToken>()
    private val locks = ConcurrentHashMap<OAuth2Authentication, Any>()

    fun getAccessToken(authentication: OAuth2Authentication): String {
        validToken(authentication)?.let { return it }
        synchronized(locks.computeIfAbsent(authentication) { Any() }) {
            validToken(authentication)?.let { return it }
            val token = requestToken(authentication)
            if (token.expiresAt != null) tokenCache[authentication] = token
            return token.accessToken
        }
    }

    /** Removes [rejectedToken] from the cache, unless another caller has already replaced it. */
    fun invalidate(
        authentication: OAuth2Authentication,
        rejectedToken: String,
    ) {
        tokenCache.computeIfPresent(authentication) { _, cached -> cached.takeIf { it.accessToken != rejectedToken } }
    }

    fun tokenEndpoint(authentication: OAuth2Authentication): String =
        when (authentication) {
            is ClientSecretAuthentication -> authentication.tokenUrl
            is EntraAuthentication -> "$entraAuthority/${authentication.tenantId}/oauth2/v2.0/token"
        }

    private fun validToken(authentication: OAuth2Authentication): String? =
        tokenCache[authentication]
            ?.takeIf { it.expiresAt!!.minusSeconds(EXPIRY_MARGIN_SECONDS).isAfter(Instant.now()) }
            ?.accessToken

    private fun requestToken(authentication: OAuth2Authentication): CachedToken {
        val tokenEndpoint = tokenEndpoint(authentication)
        val host = URI(tokenEndpoint).host
        logger.debug { "Requesting access token for auth type ${authentication.authType} from $host" }

        val response = try {
            withRetry(host) {
                restClient.post()
                    .uri(tokenEndpoint)
                    .headers { if (authentication is ClientSecretAuthentication) it.setBasicAuth(authentication.clientId, authentication.clientSecret) }
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(tokenRequestForm(authentication, tokenEndpoint))
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Map<String, Any>>() {})
            }
        } catch (e: HttpClientErrorException) {
            throw tokenErrorException(e, host)
        }

        val accessToken = response?.get("access_token") as? String
            ?: throw TokenRequestException("No access_token in OAuth2 token response from $tokenEndpoint")
        if (authentication is EntraAuthentication) verifyApplicationToken(accessToken, authentication)
        val expiresAt = (response["expires_in"] as? Number)?.let { Instant.now().plusSeconds(it.toLong()) }
        logger.debug { "Obtained access token for auth type ${authentication.authType} from $host, expires at $expiresAt" }
        return CachedToken(accessToken, expiresAt)
    }

    // Built per HTTP request, so every attempt gets a fresh client assertion.
    private fun tokenRequestForm(
        authentication: OAuth2Authentication,
        tokenEndpoint: String,
    ) = LinkedMultiValueMap<String, String>().apply {
        add("grant_type", "client_credentials")
        if (authentication is EntraAuthentication) {
            add("client_id", authentication.clientId)
            add("scope", authentication.scope)
        }
        when (authentication) {
            is ClientSecretAuthentication -> Unit // HTTP Basic, see headers
            is EntraClientSecretAuthentication -> add("client_secret", authentication.clientSecret)
            is EntraPrivateKeyJwtAuthentication -> {
                add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                add(
                    "client_assertion",
                    ClientAssertionFactory.create(
                        authentication.clientId,
                        tokenEndpoint,
                        PemLoader.loadPrivateKey(authentication.privateKey),
                        PemLoader.loadCertificate(authentication.certificate),
                    ),
                )
            }
        }
    }

    /** Retries 5xx responses and network errors with exponential backoff; other errors are thrown immediately. */
    private fun <T> withRetry(
        host: String,
        request: () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return request()
            } catch (e: Exception) {
                if (e !is HttpServerErrorException && e !is ResourceAccessException) throw e
                if (attempt == MAX_ATTEMPTS) throw TokenRequestException("Token request to $host failed after $attempt attempts", e)
                logger.debug { "Token request to $host failed (attempt $attempt), retrying: ${e.message}" }
                Thread.sleep(retryBackoff.toMillis() shl (attempt - 1))
                attempt++
            }
        }
    }

    private fun tokenErrorException(
        e: HttpClientErrorException,
        host: String,
    ): TokenRequestException {
        val body = runCatching { e.getResponseBodyAs(object : ParameterizedTypeReference<Map<String, Any>>() {}) }.getOrNull()
        val error = body?.get("error") as? String ?: "unknown_error"
        val aadsts = AADSTS_CODE.find(body?.get("error_description") as? String ?: "")?.value
        return TokenRequestException("Token request to $host failed with HTTP ${e.statusCode.value()}: $error${aadsts?.let { " ($it)" } ?: ""}")
    }

    private fun verifyApplicationToken(
        accessToken: String,
        authentication: EntraAuthentication,
    ) {
        val claims = try {
            JWTParser.parse(accessToken).jwtClaimsSet
        } catch (e: ParseException) {
            throw TokenRequestException("Access token from Entra ID is not a JWT")
        }
        val tid = claims.getClaim("tid") as? String
        if (!authentication.tenantId.equals(tid, ignoreCase = true)) {
            throw TokenRequestException("Access token tenant (tid=$tid) does not match the configured tenantId ${authentication.tenantId}")
        }
        val idtyp = claims.getClaim("idtyp") as? String
        if (idtyp != "app") {
            throw TokenRequestException("Access token is not an application token (idtyp=$idtyp); only application permissions are supported")
        }
        if ((claims.getClaim("roles") as? List<*>).isNullOrEmpty()) {
            throw TokenRequestException(
                "Access token has no roles claim: add application permissions for ${authentication.resourceUri} and grant admin consent",
            )
        }
    }

    private data class CachedToken(
        val accessToken: String,
        val expiresAt: Instant?,
    ) {
        override fun toString() = "CachedToken(expiresAt=$expiresAt)"
    }

    private companion object {
        const val EXPIRY_MARGIN_SECONDS = 30L
        const val MAX_ATTEMPTS = 3
        val AADSTS_CODE = Regex("AADSTS\\d+")
    }
}
