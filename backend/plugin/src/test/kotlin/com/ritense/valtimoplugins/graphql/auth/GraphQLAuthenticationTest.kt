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

import com.ritense.valtimoplugins.graphql.auth.AuthType.CLIENT_SECRET
import com.ritense.valtimoplugins.graphql.auth.AuthType.NONE
import com.ritense.valtimoplugins.graphql.auth.AuthType.PRIVATE_KEY_JWT
import com.ritense.valtimoplugins.graphql.auth.TestKeys.TENANT_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class GraphQLAuthenticationTest {
    @Test
    fun `existing configuration without authType and OAuth2 settings should be NONE`() {
        assertEquals(NoAuthentication, GraphQLAuthentication.of(null))
        // Valtimo keeps a stored secret when the UI clears the other OAuth2 settings
        assertEquals(NoAuthentication, GraphQLAuthentication.of(null, clientSecret = "secret"))
    }

    @Test
    fun `existing configuration without authType but with tokenUrl should be CLIENT_SECRET as before`() {
        assertEquals(
            ClientSecretAuthentication("https://idp.example.com/token", "client", "secret"),
            GraphQLAuthentication.of(null, tokenUrl = "https://idp.example.com/token", clientId = "client", clientSecret = "secret"),
        )
    }

    @Test
    fun `existing incomplete OAuth2 configuration should fail with the missing property`() {
        val e = assertThrows<InvalidPluginPropertyException> {
            GraphQLAuthentication.of(null, tokenUrl = "https://idp.example.com/token", clientId = "client")
        }

        assertEquals("clientSecret", e.property)
    }

    @Test
    fun `NONE should ignore properties of other auth types`() {
        assertEquals(NoAuthentication, GraphQLAuthentication.of(NONE, tokenUrl = "x", clientId = "x", clientSecret = "x", privateKey = "x"))
    }

    @Test
    fun `CLIENT_SECRET without tokenUrl should use Entra ID with scope derived from resourceUri`() {
        val authentication = GraphQLAuthentication.of(
            CLIENT_SECRET,
            tenantId = TENANT_ID,
            clientId = "client",
            clientSecret = "secret",
            resourceUri = "https://graph.microsoft.com/",
        ) as EntraClientSecretAuthentication

        assertEquals("https://graph.microsoft.com/.default", authentication.scope)
    }

    @Test
    fun `CLIENT_SECRET should only require its own properties`() {
        assertEquals("clientId", invalidProperty { GraphQLAuthentication.of(CLIENT_SECRET, tokenUrl = "https://idp.example.com/token") })
        assertEquals("tenantId", invalidProperty { GraphQLAuthentication.of(CLIENT_SECRET, clientId = "c", clientSecret = "s", resourceUri = "api://x") })
        assertEquals("resourceUri", invalidProperty { GraphQLAuthentication.of(CLIENT_SECRET, tenantId = TENANT_ID, clientId = "c", clientSecret = "s") })
        assertEquals("clientSecret", invalidProperty { GraphQLAuthentication.of(CLIENT_SECRET, tenantId = TENANT_ID, clientId = "c", resourceUri = "api://x") })
    }

    @Test
    fun `PRIVATE_KEY_JWT should only require its own properties`() {
        val authentication = privateKeyJwt()

        assertEquals(AuthType.PRIVATE_KEY_JWT, authentication.authType)
        assertEquals("api://graphql-api/.default", (authentication as EntraAuthentication).scope)
        assertEquals("privateKey", invalidProperty { privateKeyJwt(privateKey = null) })
        assertEquals("certificate", invalidProperty { privateKeyJwt(certificate = null) })
        assertEquals("clientId", invalidProperty { privateKeyJwt(clientId = " ") })
        assertEquals("resourceUri", invalidProperty { privateKeyJwt(resourceUri = null) })
    }

    @Test
    fun `PRIVATE_KEY_JWT should validate key and certificate`() {
        assertEquals("privateKey", invalidProperty { privateKeyJwt(privateKey = TestKeys.EC_PKCS8) })
        assertEquals("certificate", invalidProperty { privateKeyJwt(privateKey = TestKeys.OTHER_RSA_PKCS8) })
    }

    @Test
    fun `tenantId must be a GUID`() {
        assertEquals("tenantId", invalidProperty { privateKeyJwt(tenantId = "contoso.onmicrosoft.com") })
        assertEquals("tenantId", invalidProperty { privateKeyJwt(tenantId = "$TENANT_ID/../common") })
    }

    @Test
    fun `scope can never be supplied through resourceUri`() {
        assertEquals("resourceUri", invalidProperty { privateKeyJwt(resourceUri = "https://graph.microsoft.com/.default") })
        assertEquals("resourceUri", invalidProperty { privateKeyJwt(resourceUri = "api://x/.default offline_access") })
    }

    @Test
    fun `toString should never contain secrets`() {
        val clientSecret = GraphQLAuthentication.of(CLIENT_SECRET, tokenUrl = "https://idp", clientId = "c", clientSecret = "s3cr3t").toString()
        val entraSecret =
            GraphQLAuthentication.of(CLIENT_SECRET, tenantId = TENANT_ID, clientId = "c", clientSecret = "s3cr3t", resourceUri = "api://x").toString()

        assertFalse(clientSecret.contains("s3cr3t"))
        assertFalse(entraSecret.contains("s3cr3t"))
        assertFalse(privateKeyJwt().toString().contains("PRIVATE KEY"))
    }

    private fun privateKeyJwt(
        tenantId: String? = TENANT_ID,
        clientId: String? = "client",
        privateKey: String? = TestKeys.RSA_PKCS8,
        certificate: String? = TestKeys.CERTIFICATE,
        resourceUri: String? = "api://graphql-api",
    ) = GraphQLAuthentication.of(
        PRIVATE_KEY_JWT,
        tenantId = tenantId,
        clientId = clientId,
        privateKey = privateKey,
        certificate = certificate,
        resourceUri = resourceUri,
    )

    private fun invalidProperty(block: () -> Unit) = assertThrows<InvalidPluginPropertyException> { block() }.property
}
