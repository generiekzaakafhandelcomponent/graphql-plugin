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

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import com.ritense.valtimoplugins.graphql.auth.TestKeys.TENANT_ID
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.RestClient
import java.net.URLDecoder
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class AccessTokenProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: AccessTokenProvider
    private lateinit var privateKeyJwt: EntraPrivateKeyJwtAuthentication
    private lateinit var entraSecret: EntraClientSecretAuthentication

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        provider = AccessTokenProvider(RestClient.create(), server.url("").toString().trimEnd('/'), Duration.ofMillis(1))
        privateKeyJwt = EntraPrivateKeyJwtAuthentication(TENANT_ID, CLIENT_ID, TestKeys.RSA_PKCS8, TestKeys.CERTIFICATE, "api://graphql-api")
        entraSecret = EntraClientSecretAuthentication(TENANT_ID, CLIENT_ID, "s3cr3t", "https://graph.microsoft.com")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `should request token with a signed client assertion`() {
        server.enqueue(tokenResponse(TestKeys.accessToken()))

        assertEquals(TestKeys.accessToken(), provider.getAccessToken(privateKeyJwt))

        val request = server.takeRequest()
        val form = form(request)
        assertEquals("/$TENANT_ID/oauth2/v2.0/token", request.path)
        assertEquals("client_credentials", form["grant_type"])
        assertEquals(CLIENT_ID, form["client_id"])
        assertEquals("api://graphql-api/.default", form["scope"])
        assertEquals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", form["client_assertion_type"])
        assertFalse(form.containsKey("client_secret"))
        val assertion = SignedJWT.parse(form["client_assertion"])
        assertTrue(assertion.verify(RSASSAVerifier(TestKeys.publicKey)))
        assertEquals(listOf(server.url("/$TENANT_ID/oauth2/v2.0/token").toString()), assertion.jwtClaimsSet.audience)
    }

    @Test
    fun `should use a fresh client assertion for every token request`() {
        server.enqueue(tokenResponse(TestKeys.accessToken(), expiresIn = 0))
        server.enqueue(tokenResponse(TestKeys.accessToken(), expiresIn = 0))

        provider.getAccessToken(privateKeyJwt)
        provider.getAccessToken(privateKeyJwt)

        assertNotEquals(form(server.takeRequest())["client_assertion"], form(server.takeRequest())["client_assertion"])
    }

    @Test
    fun `should request Entra token with client secret in the form`() {
        server.enqueue(tokenResponse(TestKeys.accessToken()))

        provider.getAccessToken(entraSecret)

        val request = server.takeRequest()
        val form = form(request)
        assertEquals("s3cr3t", form["client_secret"])
        assertEquals("https://graph.microsoft.com/.default", form["scope"])
        assertFalse(form.containsKey("client_assertion"))
        assertEquals(null, request.getHeader("Authorization"))
    }

    @Test
    fun `should cache the token per configuration`() {
        server.enqueue(tokenResponse(TestKeys.accessToken(roles = listOf("A"))))
        server.enqueue(tokenResponse(TestKeys.accessToken(roles = listOf("B"))))

        val first = provider.getAccessToken(privateKeyJwt)
        assertEquals(first, provider.getAccessToken(privateKeyJwt))
        assertNotEquals(first, provider.getAccessToken(entraSecret))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `should refresh the token when less than 30 seconds of lifetime remain`() {
        server.enqueue(tokenResponse(TestKeys.accessToken(roles = listOf("A")), expiresIn = 29))
        server.enqueue(tokenResponse(TestKeys.accessToken(roles = listOf("B")), expiresIn = 3600))

        val first = provider.getAccessToken(privateKeyJwt)
        val second = provider.getAccessToken(privateKeyJwt)

        assertNotEquals(first, second)
        assertEquals(second, provider.getAccessToken(privateKeyJwt))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `concurrent callers should share one token request`() {
        server.enqueue(tokenResponse(TestKeys.accessToken()).setBodyDelay(300, TimeUnit.MILLISECONDS))
        val threads = 10
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)

        val tokens = (1..threads)
            .map { executor.submit(Callable { start.await(); provider.getAccessToken(privateKeyJwt) }) }
            .also { start.countDown() }
            .map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(setOf(TestKeys.accessToken()), tokens.toSet())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `invalidate should only remove the rejected token`() {
        server.enqueue(tokenResponse(TestKeys.accessToken(roles = listOf("A"))))
        server.enqueue(tokenResponse(TestKeys.accessToken(roles = listOf("B"))))
        val first = provider.getAccessToken(privateKeyJwt)

        provider.invalidate(privateKeyJwt, "some-older-token")
        assertEquals(first, provider.getAccessToken(privateKeyJwt))

        provider.invalidate(privateKeyJwt, first)
        assertNotEquals(first, provider.getAccessToken(privateKeyJwt))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `should reject token for another tenant`() {
        server.enqueue(tokenResponse(TestKeys.accessToken(tid = "99999999-2222-3333-4444-555555555555")))

        val e = assertThrows<TokenRequestException> { provider.getAccessToken(privateKeyJwt) }

        assertTrue(e.message!!.contains("tid=99999999-2222-3333-4444-555555555555"))
    }

    @Test
    fun `should reject token that is not an application token`() {
        server.enqueue(tokenResponse(TestKeys.accessToken(idtyp = "user")))

        assertThrows<TokenRequestException> { provider.getAccessToken(privateKeyJwt) }
    }

    @Test
    fun `should map invalid_client to an error with the AADSTS code and without secrets`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json").setBody(
                """{"error":"invalid_client","error_description":"AADSTS700027: The certificate with identifier used to sign the client assertion is not registered on application. Trace ID: abc","error_codes":[700027]}""",
            ),
        )

        val e = assertThrows<TokenRequestException> { provider.getAccessToken(privateKeyJwt) }

        assertEquals("Token request to ${server.hostName} failed with HTTP 401: invalid_client (AADSTS700027)", e.message)
        val assertion = form(server.takeRequest())["client_assertion"]!!
        assertFalse(e.stackTraceToString().contains(assertion))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `should map unauthorized_client without leaking the client secret`() {
        server.enqueue(
            MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                .setBody("""{"error":"unauthorized_client","error_description":"AADSTS700016: Application not found in the directory."}"""),
        )

        val e = assertThrows<TokenRequestException> { provider.getAccessToken(entraSecret) }

        assertEquals("Token request to ${server.hostName} failed with HTTP 400: unauthorized_client (AADSTS700016)", e.message)
        assertFalse(e.stackTraceToString().contains("s3cr3t"))
    }

    @Test
    fun `should retry 5xx responses with backoff and then succeed`() {
        // 502/500 rather than 503: Apache HttpClient 5 (on the Valtimo classpath) retries 503 by itself
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(tokenResponse(TestKeys.accessToken()))

        assertEquals(TestKeys.accessToken(), provider.getAccessToken(privateKeyJwt))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `should fail after a bounded number of 5xx responses`() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(502)) }

        val e = assertThrows<TokenRequestException> { provider.getAccessToken(privateKeyJwt) }

        assertEquals("Token request to ${server.hostName} failed after 3 attempts", e.message)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `should fail after a bounded number of network errors`() {
        val unreachable = AccessTokenProvider(RestClient.create(), "http://127.0.0.1:1", Duration.ofMillis(1))

        assertThrows<TokenRequestException> { unreachable.getAccessToken(privateKeyJwt) }
    }

    private fun tokenResponse(
        accessToken: String,
        expiresIn: Int = 3599,
    ) = MockResponse().setHeader("Content-Type", "application/json")
        .setBody("""{"token_type":"Bearer","expires_in":$expiresIn,"access_token":"$accessToken"}""")

    private fun form(request: RecordedRequest): Map<String, String> =
        request.body.readUtf8().split("&").associate {
            val (key, value) = it.split("=", limit = 2)
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private companion object {
        const val CLIENT_ID = "22222222-3333-4444-5555-666666666666"
    }
}
