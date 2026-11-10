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

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import java.security.interfaces.RSAPublicKey

/** Throwaway test keys generated with openssl, see src/test/resources/auth. Never used outside tests. */
internal object TestKeys {
    val RSA_PKCS8 = resource("rsa-pkcs8.pem")
    val RSA_PKCS1 = resource("rsa-pkcs1.pem")
    val OTHER_RSA_PKCS8 = resource("other-rsa-pkcs8.pem")
    val EC_PKCS8 = resource("ec-pkcs8.pem")
    val CERTIFICATE = resource("certificate.pem")

    // openssl x509 -in certificate.pem -outform der | openssl dgst -sha256|-sha1 -binary | base64url
    const val CERTIFICATE_X5T_S256 = "DR4-0R8V9qMOe4lQ5nV3H-d9LFbXy5DljgNH69Zpg0Y"
    const val CERTIFICATE_X5T = "iXVb8UvFfpvBzE_4x-qkmw5_xpQ"

    const val TENANT_ID = "11111111-2222-3333-4444-555555555555"

    val publicKey get() = PemLoader.loadCertificate(CERTIFICATE).publicKey as RSAPublicKey

    /** An unsigned access token with the given claims; the plugin only reads claims, the resource server verifies. */
    fun accessToken(
        tid: String? = TENANT_ID,
        idtyp: String? = "app",
        roles: List<String>? = listOf("Records.Read.All"),
    ): String =
        PlainJWT(
            JWTClaimsSet.Builder().claim("tid", tid).claim("idtyp", idtyp).claim("roles", roles).build(),
        ).serialize()

    private fun resource(name: String) = TestKeys::class.java.getResource("/auth/$name")!!.readText()
}
