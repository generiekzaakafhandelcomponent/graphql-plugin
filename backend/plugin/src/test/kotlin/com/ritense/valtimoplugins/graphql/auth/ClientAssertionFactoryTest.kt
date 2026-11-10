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

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClientAssertionFactoryTest {
    private fun createAssertion() =
        SignedJWT.parse(
            ClientAssertionFactory.create(
                CLIENT_ID,
                TOKEN_ENDPOINT,
                PemLoader.loadPrivateKey(TestKeys.RSA_PKCS8),
                PemLoader.loadCertificate(TestKeys.CERTIFICATE),
            ),
        )

    @Test
    fun `signature should verify with the public key of the certificate`() {
        assertTrue(createAssertion().verify(RSASSAVerifier(TestKeys.publicKey)))
    }

    @Test
    @Suppress("DEPRECATION")
    fun `header should have RS256, JWT type and both certificate thumbprints`() {
        val header = createAssertion().header

        assertEquals(JWSAlgorithm.RS256, header.algorithm)
        assertEquals(JOSEObjectType.JWT, header.type)
        assertEquals(TestKeys.CERTIFICATE_X5T_S256, header.x509CertSHA256Thumbprint.toString())
        assertEquals(TestKeys.CERTIFICATE_X5T, header.x509CertThumbprint.toString())
    }

    @Test
    fun `claims should identify the client to the token endpoint for two minutes`() {
        val claims = createAssertion().jwtClaimsSet

        assertEquals(CLIENT_ID, claims.issuer)
        assertEquals(CLIENT_ID, claims.subject)
        assertEquals(listOf(TOKEN_ENDPOINT), claims.audience)
        assertNotNull(claims.jwtid)
        assertEquals(claims.issueTime, claims.notBeforeTime)
        assertEquals(120_000L, claims.expirationTime.time - claims.issueTime.time)
    }

    @Test
    fun `every assertion should have a unique jti`() {
        assertNotEquals(createAssertion().jwtClaimsSet.jwtid, createAssertion().jwtClaimsSet.jwtid)
    }

    private companion object {
        const val CLIENT_ID = "22222222-3333-4444-5555-666666666666"
        const val TOKEN_ENDPOINT = "https://login.microsoftonline.com/${TestKeys.TENANT_ID}/oauth2/v2.0/token"
    }
}
