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

import com.ritense.valtimoplugins.graphql.auth.ClientSecretAuthentication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.util.Base64

internal class GraphQLClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var graphqlClient: GraphQLClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        graphqlClient = GraphQLClient(builder.build())
    }

    @Test
    fun `should execute query without authorization header when no credentials are given`() {
        expectGraphQlRequest().andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess(GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON))

        val data = graphqlClient.executeGraphQlQuery(GRAPHQL_URL, QUERY, emptyMap())

        assertEquals(mapOf("cases" to listOf(mapOf("id" to 1))), data)
        server.verify()
    }

    @Test
    fun `should retrieve access token with client credentials and send it as bearer token`() {
        val basicAuth = Base64.getEncoder().encodeToString("client-id:client-secret".toByteArray())
        server.expect(once(), requestTo(TOKEN_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic $basicAuth"))
            .andExpect(content().formDataContains(mapOf("grant_type" to "client_credentials")))
            .andRespond(withSuccess("""{"access_token": "token-1", "expires_in": 300}""", MediaType.APPLICATION_JSON))
        expectGraphQlRequest().andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
            .andRespond(withSuccess(GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON))

        graphqlClient.executeGraphQlQuery(GRAPHQL_URL, QUERY, emptyMap(), CREDENTIALS)

        server.verify()
    }

    @Test
    fun `should reuse cached access token until its lifespan has expired`() {
        server.expect(once(), requestTo(TOKEN_URL))
            .andRespond(withSuccess("""{"access_token": "token-1", "expires_in": 300}""", MediaType.APPLICATION_JSON))
        repeat(2) {
            expectGraphQlRequest().andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withSuccess(GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON))
        }

        graphqlClient.executeGraphQlQuery(GRAPHQL_URL, QUERY, emptyMap(), CREDENTIALS)
        graphqlClient.executeGraphQlQuery(GRAPHQL_URL, QUERY, emptyMap(), CREDENTIALS)

        server.verify()
    }

    @Test
    fun `should retrieve new access token when the cached one has expired`() {
        server.expect(once(), requestTo(TOKEN_URL))
            .andRespond(withSuccess("""{"access_token": "token-1", "expires_in": 0}""", MediaType.APPLICATION_JSON))
        expectGraphQlRequest().andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
            .andRespond(withSuccess(GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON))
        server.expect(once(), requestTo(TOKEN_URL))
            .andRespond(withSuccess("""{"access_token": "token-2", "expires_in": 300}""", MediaType.APPLICATION_JSON))
        expectGraphQlRequest().andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-2"))
            .andRespond(withSuccess(GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON))

        graphqlClient.executeGraphQlQuery(GRAPHQL_URL, QUERY, emptyMap(), CREDENTIALS)
        graphqlClient.executeGraphQlQuery(GRAPHQL_URL, QUERY, emptyMap(), CREDENTIALS)

        server.verify()
    }

    @Test
    fun `should fail when token response has no access token`() {
        server.expect(once(), requestTo(TOKEN_URL))
            .andRespond(withSuccess("""{"token_type": "bearer"}""", MediaType.APPLICATION_JSON))

        assertThrows<IllegalStateException> { graphqlClient.executeGraphQlQuery(GRAPHQL_URL, QUERY, emptyMap(), CREDENTIALS) }
    }

    @Test
    fun `should retry exactly once with a new access token when the endpoint returns 401`() {
        expectTokenRequest("token-1")
        expectGraphQlRequest().andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        expectTokenRequest("token-2")
        expectGraphQlRequest().andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-2"))
            .andRespond(withSuccess(GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON))

        val data = graphqlClient.executeGraphQlQuery(GRAPHQL_URL, QUERY, emptyMap(), CREDENTIALS)

        assertEquals(mapOf("cases" to listOf(mapOf("id" to 1))), data)
        server.verify()
    }

    @Test
    fun `should fail when the endpoint returns 401 again after the retry`() {
        expectTokenRequest("token-1")
        expectGraphQlRequest().andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        expectTokenRequest("token-2")
        expectGraphQlRequest().andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThrows<HttpClientErrorException.Unauthorized> { graphqlClient.executeGraphQlQuery(GRAPHQL_URL, QUERY, emptyMap(), CREDENTIALS) }
        server.verify()
    }

    @Test
    fun `should not retry 401 without authentication`() {
        expectGraphQlRequest().andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThrows<HttpClientErrorException.Unauthorized> { graphqlClient.executeGraphQlQuery(GRAPHQL_URL, QUERY, emptyMap()) }
        server.verify()
    }

    private fun expectTokenRequest(accessToken: String) =
        server.expect(once(), requestTo(TOKEN_URL))
            .andRespond(withSuccess("""{"access_token": "$accessToken", "expires_in": 300}""", MediaType.APPLICATION_JSON))

    private fun expectGraphQlRequest() =
        server.expect(once(), requestTo(GRAPHQL_URL))
            .andExpect(method(HttpMethod.POST))

    private companion object {
        const val GRAPHQL_URL = "http://graphql.example.com/graphql"
        const val TOKEN_URL = "http://auth.example.com/token"
        const val QUERY = "query { cases { id } }"
        const val GRAPHQL_RESPONSE = """{"data": {"cases": [{"id": 1}]}}"""
        val CREDENTIALS = ClientSecretAuthentication(TOKEN_URL, "client-id", "client-secret")
    }
}
