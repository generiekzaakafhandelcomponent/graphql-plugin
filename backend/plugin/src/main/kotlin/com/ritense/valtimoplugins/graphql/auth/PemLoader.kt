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

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** Parses the PEM values of the PRIVATE_KEY_JWT configuration. Errors name the property; they never contain key material. */
object PemLoader {
    private val PEM_BLOCK = Regex("-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----", RegexOption.DOT_MATCHES_ALL)

    // AlgorithmIdentifier for rsaEncryption (1.2.840.113549.1.1.1) with NULL parameters, DER encoded
    private val RSA_ALGORITHM_ID = byteArrayOf(0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00)
    private const val MIN_KEY_SIZE = 2048

    /** Accepts an RSA key as PKCS#8 (BEGIN PRIVATE KEY) or PKCS#1 (BEGIN RSA PRIVATE KEY) PEM. */
    fun loadPrivateKey(
        pem: String,
        property: String = "privateKey",
    ): RSAPrivateKey {
        val (type, der) = decode(pem, property)
        val pkcs8 = when (type) {
            "PRIVATE KEY" -> der
            "RSA PRIVATE KEY" -> pkcs1ToPkcs8(der)
            "EC PRIVATE KEY", "DSA PRIVATE KEY", "OPENSSH PRIVATE KEY" -> throw InvalidPluginPropertyException(property, "must be an RSA key")
            "ENCRYPTED PRIVATE KEY" -> throw InvalidPluginPropertyException(property, "must not be encrypted with a passphrase")
            else -> throw InvalidPluginPropertyException(property, "expected a PEM private key (BEGIN PRIVATE KEY or BEGIN RSA PRIVATE KEY)")
        }
        val key = try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8)) as RSAPrivateKey
        } catch (e: GeneralSecurityException) {
            val otherAlgorithm = listOf("EC", "Ed25519", "Ed448", "DSA").any {
                runCatching { KeyFactory.getInstance(it).generatePrivate(PKCS8EncodedKeySpec(pkcs8)) }.isSuccess
            }
            throw InvalidPluginPropertyException(property, if (otherAlgorithm) "must be an RSA key" else "is not a valid private key")
        }
        if (key.modulus.bitLength() < MIN_KEY_SIZE) throw InvalidPluginPropertyException(property, "RSA key must be at least $MIN_KEY_SIZE bits")
        return key
    }

    fun loadCertificate(
        pem: String,
        property: String = "certificate",
    ): X509Certificate {
        val (type, der) = decode(pem, property)
        if (type != "CERTIFICATE") throw InvalidPluginPropertyException(property, "expected a PEM certificate (BEGIN CERTIFICATE)")
        val certificate = try {
            CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
        } catch (e: GeneralSecurityException) {
            throw InvalidPluginPropertyException(property, "is not a valid X.509 certificate")
        }
        if (certificate.publicKey !is RSAPublicKey) throw InvalidPluginPropertyException(property, "must contain an RSA public key")
        return certificate
    }

    fun requireMatchingKeyPair(
        privateKey: RSAPrivateKey,
        certificate: X509Certificate,
    ) {
        if ((certificate.publicKey as RSAPublicKey).modulus != privateKey.modulus) {
            throw InvalidPluginPropertyException("certificate", "does not belong to the configured private key")
        }
    }

    private fun decode(
        pem: String,
        property: String,
    ): Pair<String, ByteArray> {
        val block = PEM_BLOCK.find(pem) ?: throw InvalidPluginPropertyException(property, "is not in PEM format")
        val der = try {
            Base64.getMimeDecoder().decode(block.groupValues[2].trim())
        } catch (e: IllegalArgumentException) {
            throw InvalidPluginPropertyException(property, "contains invalid PEM data")
        }
        if (der.isEmpty()) throw InvalidPluginPropertyException(property, "contains invalid PEM data")
        return block.groupValues[1] to der
    }

    // PrivateKeyInfo ::= SEQUENCE { version INTEGER (0), algorithm AlgorithmIdentifier, privateKey OCTET STRING (RSAPrivateKey) }
    private fun pkcs1ToPkcs8(pkcs1: ByteArray): ByteArray = der(0x30, byteArrayOf(0x02, 0x01, 0x00) + RSA_ALGORITHM_ID + der(0x04, pkcs1))

    private fun der(
        tag: Int,
        content: ByteArray,
    ): ByteArray {
        val size = content.size
        val length = if (size < 0x80) {
            byteArrayOf(size.toByte())
        } else {
            val bytes = generateSequence(size) { it shr 8 }.takeWhile { it > 0 }.map { it.toByte() }.toList().reversed()
            byteArrayOf((0x80 or bytes.size).toByte()) + bytes
        }
        return byteArrayOf(tag.toByte()) + length + content
    }
}
