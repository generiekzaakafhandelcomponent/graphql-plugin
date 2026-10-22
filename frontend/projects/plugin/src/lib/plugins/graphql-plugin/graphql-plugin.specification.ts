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
      useOAuth2: "OAuth2 authenticatie gebruiken",
      tokenUrl: "OAuth2 token URL",
      clientId: "OAuth2 client ID",
      clientSecret: "OAuth2 client secret",
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
      useOAuth2: "Use OAuth2 authentication",
      tokenUrl: "OAuth2 token URL",
      clientId: "OAuth2 client ID",
      clientSecret: "OAuth2 client secret",
      "graphql-by-input": "GraphQL by Input",
      query: "GraphQL query",
      variables: "Variables (JSON)",
      resultProcessVariableName: "Result process variable",
    },
  },
};

export {graphqlPluginSpecification};
