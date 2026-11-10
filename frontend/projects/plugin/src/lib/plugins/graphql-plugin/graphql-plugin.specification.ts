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

import {PluginSpecification} from "@valtimo/plugin";
import {GraphQLPluginConfigurationComponent} from "./components/graphql-plugin-configuration/graphql-plugin-configuration.component";
import {GRAPHQL_PLUGIN_LOGO_BASE64} from "./assets";
import {GraphQlByInputActionConfigurationComponent} from "./components/graphql-by-input-action-configuration/graphql-by-input-action-configuration.component";

const graphqlPluginSpecification: PluginSpecification = {
  pluginId: "graphql-plugin",
  pluginConfigurationComponent: GraphQLPluginConfigurationComponent,
  pluginLogoBase64: GRAPHQL_PLUGIN_LOGO_BASE64,
  functionConfigurationComponents: {
    "graphql-by-input": GraphQlByInputActionConfigurationComponent,
  },
  pluginTranslations: {
    nl: {
      title: "GraphQL Plugin",
      description: "Voer GraphQL queries uit op een geconfigureerd GraphQL endpoint.",
      configurationTitle: "Configuratienaam",
      graphqlUrl: "GraphQL endpoint URL",
      authType: "Authenticatie",
      authTypeNone: "Geen",
      authTypeClientSecret: "Client secret (OAuth2 client credentials)",
      authTypePrivateKeyJwt: "Certificaat (Microsoft Entra ID, private_key_jwt)",
      tokenUrl: "OAuth2 token URL",
      tokenUrlTooltip: "Alleen voor andere identity providers dan Microsoft Entra ID. Laat leeg voor Entra ID.",
      tenantId: "Tenant ID (directory ID)",
      clientId: "Client ID (application ID)",
      clientSecret: "Client secret",
      resourceUri: "Resource URI",
      resourceUriTooltip: "De API waarvoor een token wordt aangevraagd, bijv. https://graph.microsoft.com of api://<app-id>. De scope wordt {resourceUri}/.default.",
      privateKey: "Private key (PEM)",
      certificate: "Publiek certificaat (PEM)",
      certificateThumbprint: "SHA-1 thumbprint (vergelijk met Azure)",
      invalidTenantId: "Tenant ID moet een GUID zijn",
      invalidResourceUri: "Geef de resource URI op zonder /.default",
      invalidPrivateKey: "Verwacht een PEM private key (BEGIN PRIVATE KEY of BEGIN RSA PRIVATE KEY)",
      invalidCertificate: "Verwacht een PEM certificaat (BEGIN CERTIFICATE)",
      "graphql-by-input": "GraphQL via invoer",
      query: "GraphQL query",
      variables: "Variabelen (JSON)",
      resultProcessVariableName: "Resultaat procesvariabele",
    },
    en: {
      title: "GraphQL Plugin",
      description: "Execute GraphQL queries on a configured GraphQL endpoint.",
      configurationTitle: "Configuration Name",
      graphqlUrl: "GraphQL endpoint URL",
      authType: "Authentication",
      authTypeNone: "None",
      authTypeClientSecret: "Client secret (OAuth2 client credentials)",
      authTypePrivateKeyJwt: "Certificate (Microsoft Entra ID, private_key_jwt)",
      tokenUrl: "OAuth2 token URL",
      tokenUrlTooltip: "Only for identity providers other than Microsoft Entra ID. Leave empty for Entra ID.",
      tenantId: "Tenant ID (directory ID)",
      clientId: "Client ID (application ID)",
      clientSecret: "Client secret",
      resourceUri: "Resource URI",
      resourceUriTooltip: "The API to request a token for, e.g. https://graph.microsoft.com or api://<app-id>. The scope becomes {resourceUri}/.default.",
      privateKey: "Private key (PEM)",
      certificate: "Public certificate (PEM)",
      certificateThumbprint: "SHA-1 thumbprint (compare with Azure)",
      invalidTenantId: "Tenant ID must be a GUID",
      invalidResourceUri: "Enter the resource URI without /.default",
      invalidPrivateKey: "Expected a PEM private key (BEGIN PRIVATE KEY or BEGIN RSA PRIVATE KEY)",
      invalidCertificate: "Expected a PEM certificate (BEGIN CERTIFICATE)",
      "graphql-by-input": "GraphQL by Input",
      query: "GraphQL query",
      variables: "Variables (JSON)",
      resultProcessVariableName: "Result process variable",
    },
  },
};

export {graphqlPluginSpecification};
