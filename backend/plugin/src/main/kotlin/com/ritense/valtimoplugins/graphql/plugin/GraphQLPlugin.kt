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

package com.ritense.valtimoplugins.graphql.plugin

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginAction
import com.ritense.plugin.annotation.PluginActionProperty
import com.ritense.plugin.annotation.PluginEvent
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.plugin.domain.EventType
import com.ritense.processlink.domain.ActivityTypeWithEventName.SERVICE_TASK_START
import com.ritense.valtimoplugins.graphql.auth.AuthType
import com.ritense.valtimoplugins.graphql.auth.GraphQLAuthentication
import com.ritense.valtimoplugins.graphql.client.GraphQLClient
import com.ritense.valueresolver.ValueResolverService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.operaton.bpm.engine.delegate.DelegateExecution

private val logger = KotlinLogging.logger {}

/**
 * GraphQL plugin for executing GraphQL queries on a configured GraphQL endpoint.
 * Note that the key in the @Plugin annotation must be unique, and
 * should be equal to the pluginId in the plugin's frontend configuration.
 */
@Plugin(
    key = "graphql-plugin",
    title = "GraphQL Plugin",
    description = "Execute GraphQL queries on a configured GraphQL endpoint.",
)
open class GraphQLPlugin(
    private val graphqlClient: GraphQLClient,
    private val valueResolver: ValueResolverService,
) {
    private val objectMapper = jacksonObjectMapper()

    @PluginProperty(key = "graphqlUrl", secret = false)
    lateinit var graphqlUrl: String

    /** Null for configurations created before 1.3.0, see [GraphQLAuthentication.of]. */
    @PluginProperty(key = "authType", required = false, secret = false)
    var authType: AuthType? = null

    @PluginProperty(key = "tokenUrl", required = false, secret = false)
    var tokenUrl: String? = null

    @PluginProperty(key = "clientId", required = false, secret = false)
    var clientId: String? = null

    @PluginProperty(key = "clientSecret", required = false, secret = true)
    var clientSecret: String? = null

    @PluginProperty(key = "tenantId", required = false, secret = false)
    var tenantId: String? = null

    @PluginProperty(key = "resourceUri", required = false, secret = false)
    var resourceUri: String? = null

    @PluginProperty(key = "privateKey", required = false, secret = true)
    var privateKey: String? = null

    @PluginProperty(key = "certificate", required = false, secret = false)
    var certificate: String? = null

    @PluginEvent(invokedOn = [EventType.CREATE, EventType.UPDATE])
    open fun validateAuthentication() {
        authentication()
    }

    @PluginAction(
        key = "graphql-by-input",
        title = "GraphQL by Input",
        description = "Executes a GraphQL query and stores the result in given named process variable",
        activityTypes = [SERVICE_TASK_START],
    )
    open fun graphQlByInput(
        execution: DelegateExecution,
        @PluginActionProperty query: String,
        @PluginActionProperty variables: String?,
        @PluginActionProperty resultProcessVariableName: String,
    ) {
        logger.info { "Executing GraphQL query at $graphqlUrl" }
        val data = graphqlClient.executeGraphQlQuery(graphqlUrl, query, parseVariables(execution, variables), authentication())
        execution.setVariable(resultProcessVariableName, data)
    }

    /** Properties of other auth types are ignored, because Valtimo keeps stored secrets when the UI clears them. */
    private fun authentication() =
        GraphQLAuthentication.of(authType, tokenUrl, tenantId, clientId, clientSecret, privateKey, certificate, resourceUri)

    private fun parseVariables(
        execution: DelegateExecution,
        variables: String?,
    ): Map<String, Any?> =
        if (!variables.isNullOrBlank()) {
            val variableMap: Map<String, Any> = objectMapper.readValue(variables, object : TypeReference<Map<String, Any>>() {})
            val requestedValues = variableMap.values.filterIsInstance<String>().filter { valueResolver.supportsValue(it) }
            val resolvedValues = valueResolver.resolveValues(execution.processInstanceId, execution, requestedValues)
            variableMap.mapValues { (_, value) -> if (value in resolvedValues) resolvedValues[value] else value }
        } else {
            emptyMap()
        }
}
