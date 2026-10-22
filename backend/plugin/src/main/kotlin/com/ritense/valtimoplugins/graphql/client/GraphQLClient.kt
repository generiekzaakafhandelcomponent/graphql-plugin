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

package com.ritense.valtimoplugins.graphql.client

import com.ritense.valtimo.contract.annotation.SkipComponentScan
import org.springframework.core.ParameterizedTypeReference
import org.springframework.graphql.client.HttpSyncGraphQlClient
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class OAuth2ClientCredentials(
    val tokenUrl: String,
    val clientId: String,
    val clientSecret: String,
)

@SkipComponentScan
@Component
class GraphQLClient(
    private val restClient: RestClient,
) {
    private val graphQlClientCache = ConcurrentHashMap<String, HttpSyncGraphQlClient>()
    private val tokenCache = ConcurrentHashMap<OAuth2ClientCredentials, CachedToken>()

    fun executeGraphQlQuery(
        graphqlUrl: String,
        query: String,
        variables: Map<String, Any?>,
        credentials: OAuth2ClientCredentials? = null,
    ): Map<String, Any>? {
        val cachedClient = graphQlClientCache.computeIfAbsent(graphqlUrl) {
            HttpSyncGraphQlClient.builder(restClient)
                .url(graphqlUrl)
                .build()
        }
        val client = if (credentials != null) {
            cachedClient.mutate().header(HttpHeaders.AUTHORIZATION, "Bearer ${getAccessToken(credentials)}").build()
        } else {
            cachedClient
        }

        val response = client.document(query)
            .variables(variables)
            .executeSync()

        if (response.errors.isNotEmpty()) {
            throw IllegalStateException("GraphQL errors: ${response.errors.joinToString { it.message }}")
        }

        return response.getData<Map<String, Any>>()
    }

    /**
     * Returns the cached access token, or retrieves a new one with the OAuth2 client credentials flow
     * when there is none or its lifespan has expired.
     */
    private fun getAccessToken(credentials: OAuth2ClientCredentials): String {
        tokenCache[credentials]?.takeIf { it.expiresAt.isAfter(Instant.now()) }?.let { return it.accessToken }

        val response = restClient.post()
            .uri(credentials.tokenUrl)
            .headers { it.setBasicAuth(credentials.clientId, credentials.clientSecret) }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(LinkedMultiValueMap<String, String>().apply { add("grant_type", "client_credentials") })
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any>>() {})

        val accessToken = response?.get("access_token") as? String
            ?: throw IllegalStateException("No access_token in OAuth2 token response from ${credentials.tokenUrl}")
        val expiresIn = (response["expires_in"] as? Number)?.toLong()
        if (expiresIn != null) {
            tokenCache[credentials] = CachedToken(accessToken, Instant.now().plusSeconds(expiresIn - EXPIRY_MARGIN_SECONDS))
        }
        return accessToken
    }

    private data class CachedToken(
        val accessToken: String,
        val expiresAt: Instant,
    )

    private companion object {
        const val EXPIRY_MARGIN_SECONDS = 30L
    }
}
