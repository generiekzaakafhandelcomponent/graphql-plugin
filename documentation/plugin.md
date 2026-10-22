# GraphQL Plugin

## Overview

The GraphQL plugin executes GraphQL queries on a configured GraphQL endpoint.

## Dependencies

### Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:graphql-plugin:0.0.1")
}
```

### Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/graphql-plugin": "0.0.1"
  }
}
```

In your `app.module.ts`:

```typescript
import {
    GraphQLPluginModule, graphqlPluginSpecification,
} from '@valtimo-plugins/graphql-plugin';

@NgModule({
    imports: [
        GraphQLPluginModule,
    ],
    providers: [
        {
            provide: PLUGIN_TOKEN,
            useValue: [
                graphqlPluginSpecification,
            ]
        }
    ]
})
```

## Configuration

| Property     | Type   | Required | Description                                                                 |
|--------------|--------|----------|-----------------------------------------------------------------------------|
| graphqlUrl   | string | Yes      | The URL of the GraphQL endpoint, e.g. `https://example.com/graphql`         |
| tokenUrl     | string | No       | OAuth2 token endpoint for the client credentials flow                       |
| clientId     | string | No       | OAuth2 client ID                                                            |
| clientSecret | string | No       | OAuth2 client secret (stored as a secret)                                   |

In the configuration screen, the OAuth2 settings are shown after ticking "Use OAuth2 authentication"; all three are then
required. With OAuth2 enabled, the plugin requests an access token with the OAuth2 client credentials flow (client
authentication via HTTP Basic) and sends it as `Authorization: Bearer <token>` with every GraphQL request. Tokens are
cached until shortly before they expire. Unticking the checkbox clears `tokenUrl` and `clientId`; without those the
endpoint is called without authentication, and a stored `clientSecret` is ignored.

## Actions

### GraphQL by Input

Key: `graphql-by-input`. Executes a GraphQL query on the configured endpoint and stores the response `data` in a process
variable. When the response contains GraphQL errors, the service task fails with an exception listing them.

| Parameter                 | Type   | Required | Description                                                                                                  |
|---------------------------|--------|----------|--------------------------------------------------------------------------------------------------------------|
| query                     | string | Yes      | The GraphQL query document                                                                                   |
| variables                 | string | No       | JSON object with query variables. String values with a value resolver prefix (e.g. `pv:caseId`) are resolved |
| resultProcessVariableName | string | Yes      | Name of the process variable where the response data is stored                                               |

## Usage

Link a service task to the `graphql-by-input` action, either in the process link screen or as auto-deployed
configuration. Example process link that looks up a record by the id in process variable `caseId`:

```json
{
    "activityId": "query-record",
    "activityType": "bpmn:ServiceTask:start",
    "processLinkType": "plugin",
    "pluginDefinitionKey": "graphql-plugin",
    "pluginActionDefinitionKey": "graphql-by-input",
    "pluginConfigurationId": "<plugin configuration id>",
    "actionProperties": {
        "query": "query GetRecord($id: Int!) { record(id: $id) { id name } }",
        "variables": "{\"id\": \"pv:caseId\"}",
        "resultProcessVariableName": "queryResult"
    }
}
```

After the task, process variable `queryResult` holds the response data, e.g. `{"record": {"id": 1, "name": "..."}}`.
