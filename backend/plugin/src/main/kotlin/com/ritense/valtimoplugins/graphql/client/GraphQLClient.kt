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
import com.ritense.valtimoplugins.graphql.auth.AccessTokenProvider
import com.ritense.valtimoplugins.graphql.auth.GraphQLAuthentication
import com.ritense.valtimoplugins.graphql.auth.NoAuthentication
import com.ritense.valtimoplugins.graphql.auth.OAuth2Authentication
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.graphql.client.HttpSyncGraphQlClient
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@SkipComponentScan
@Component
class GraphQLClient(
    private val restClient: RestClient,
    private val accessTokenProvider: AccessTokenProvider = AccessTokenProvider(restClient),
) {
    private val graphQlClientCache = ConcurrentHashMap<String, HttpSyncGraphQlClient>()

    fun executeGraphQlQuery(
        graphqlUrl: String,
        query: String,
        variables: Map<String, Any?>,
        authentication: GraphQLAuthentication = NoAuthentication,
    ): Map<String, Any>? {
        val cachedClient = graphQlClientCache.computeIfAbsent(graphqlUrl) {
            HttpSyncGraphQlClient.builder(restClient)
                .url(graphqlUrl)
                .build()
        }
        if (authentication !is OAuth2Authentication) {
            return execute(cachedClient, query, variables)
        }

        val accessToken = accessTokenProvider.getAccessToken(authentication)
        return try {
            execute(cachedClient.withBearer(accessToken), query, variables)
        } catch (e: HttpClientErrorException.Unauthorized) {
            // The token may have been revoked or rotated before it expired: retry exactly once with a new token.
            logger.debug { "GraphQL endpoint returned 401, retrying once with a new access token" }
            accessTokenProvider.invalidate(authentication, accessToken)
            execute(cachedClient.withBearer(accessTokenProvider.getAccessToken(authentication)), query, variables)
        }
    }

    private fun HttpSyncGraphQlClient.withBearer(accessToken: String) =
        mutate().header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken").build()

    private fun execute(
        client: HttpSyncGraphQlClient,
        query: String,
        variables: Map<String, Any?>,
    ): Map<String, Any>? {
        val response = client.document(query)
            .variables(variables)
            .executeSync()

        if (response.errors.isNotEmpty()) {
            throw IllegalStateException("GraphQL errors: ${response.errors.joinToString { it.message }}")
        }

        return response.getData<Map<String, Any>>()
    }
}
