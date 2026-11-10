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
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

/** Builds the RFC 7523 client assertion (private_key_jwt) for Microsoft Entra ID. Create a new one for every token request. */
object ClientAssertionFactory {
    private const val LIFETIME_SECONDS = 120L

    @Suppress("DEPRECATION") // x5t (SHA-1) is deprecated in JOSE but still the thumbprint Entra ID matches on
    fun create(
        clientId: String,
        tokenEndpoint: String,
        privateKey: RSAPrivateKey,
        certificate: X509Certificate,
    ): String {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .type(JOSEObjectType.JWT)
            .x509CertThumbprint(thumbprint("SHA-1", certificate))
            .x509CertSHA256Thumbprint(thumbprint("SHA-256", certificate))
            .build()
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val claims = JWTClaimsSet.Builder()
            .issuer(clientId)
            .subject(clientId)
            .audience(tokenEndpoint)
            .issueTime(Date.from(now))
            .notBeforeTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(LIFETIME_SECONDS)))
            .jwtID(UUID.randomUUID().toString())
            .build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(privateKey)) }.serialize()
    }

    private fun thumbprint(
        algorithm: String,
        certificate: X509Certificate,
    ) = Base64URL.encode(MessageDigest.getInstance(algorithm).digest(certificate.encoded))
}
