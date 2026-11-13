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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class PemLoaderTest {
    @Test
    fun `should load PKCS#8 and PKCS#1 PEM to the same RSA key`() {
        val pkcs8 = PemLoader.loadPrivateKey(TestKeys.RSA_PKCS8)
        val pkcs1 = PemLoader.loadPrivateKey(TestKeys.RSA_PKCS1)

        assertEquals(pkcs8.modulus, pkcs1.modulus)
        assertEquals(pkcs8.privateExponent, pkcs1.privateExponent)
    }

    @Test
    fun `should accept PEM with surrounding whitespace and Windows line endings`() {
        assertDoesNotThrow { PemLoader.loadPrivateKey("\n  " + TestKeys.RSA_PKCS8.replace("\n", "\r\n") + "  \n") }
    }

    @Test
    fun `should reject malformed PEM with property name`() {
        val noPem = assertThrows<InvalidPluginPropertyException> { PemLoader.loadPrivateKey("not a key") }
        val badBase64 = assertThrows<InvalidPluginPropertyException> {
            PemLoader.loadPrivateKey("-----BEGIN PRIVATE KEY-----\n!!!notbase64!!!\n-----END PRIVATE KEY-----")
        }
        val truncated = assertThrows<InvalidPluginPropertyException> {
            PemLoader.loadPrivateKey(TestKeys.RSA_PKCS8.lines().filterIndexed { i, _ -> i != 3 }.joinToString("\n"))
        }

        listOf(noPem, badBase64, truncated).forEach { assertEquals("privateKey", it.property) }
        assertEquals("privateKey: is not in PEM format", noPem.message)
    }

    @Test
    fun `should reject non-RSA key`() {
        val e = assertThrows<InvalidPluginPropertyException> { PemLoader.loadPrivateKey(TestKeys.EC_PKCS8) }

        assertEquals("privateKey: must be an RSA key", e.message)
    }

    @Test
    fun `should reject certificate as private key and key as certificate`() {
        assertThrows<InvalidPluginPropertyException> { PemLoader.loadPrivateKey(TestKeys.CERTIFICATE) }
        val e = assertThrows<InvalidPluginPropertyException> { PemLoader.loadCertificate(TestKeys.RSA_PKCS8) }

        assertEquals("certificate", e.property)
    }

    @Test
    fun `should reject certificate that does not belong to the private key`() {
        val e = assertThrows<InvalidPluginPropertyException> {
            PemLoader.requireMatchingKeyPair(PemLoader.loadPrivateKey(TestKeys.OTHER_RSA_PKCS8), PemLoader.loadCertificate(TestKeys.CERTIFICATE))
        }

        assertEquals("certificate", e.property)
    }

    @Test
    fun `error messages should never contain key material`() {
        val keyBody = TestKeys.RSA_PKCS8.lines()[1]
        val e = assertThrows<InvalidPluginPropertyException> { PemLoader.loadPrivateKey(TestKeys.RSA_PKCS8.replace("PRIVATE KEY", "PUBLIC KEY")) }

        assert(!e.message!!.contains(keyBody))
    }
}
