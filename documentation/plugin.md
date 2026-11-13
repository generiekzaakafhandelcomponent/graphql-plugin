# GraphQL Plugin

## Overview

The GraphQL plugin executes GraphQL queries on a configured GraphQL endpoint.

## Dependencies

### Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:graphql-plugin:1.0.1")
}
```

### Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/graphql-plugin": "1.0.1"
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

| Property      | Type   | Required                                       | Description                                                                                                      |
|---------------|--------|------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| graphqlUrl    | string | Yes                                            | The URL of the GraphQL endpoint, e.g. `https://example.com/graphql`                                              |
| authType      | enum   | No (see [Existing configurations](#existing-configurations)) | `NONE`, `CLIENT_SECRET` or `PRIVATE_KEY_JWT`                                                       |
| tokenUrl      | string | `CLIENT_SECRET` with another identity provider | OAuth2 token endpoint of an identity provider other than Microsoft Entra ID. Leave empty for Entra ID            |
| tenantId      | GUID   | `PRIVATE_KEY_JWT`, Entra `CLIENT_SECRET`       | Directory (tenant) ID of the Entra ID app registration                                                           |
| clientId      | string | `CLIENT_SECRET`, `PRIVATE_KEY_JWT`             | Client (application) ID                                                                                          |
| clientSecret  | string | `CLIENT_SECRET`                                | Client secret (stored as a secret)                                                                               |
| privateKey    | PEM    | `PRIVATE_KEY_JWT`                              | RSA private key (at least 2048 bits) as PKCS#8 (`BEGIN PRIVATE KEY`, preferred) or PKCS#1 (`BEGIN RSA PRIVATE KEY`). Not encrypted. Stored as a secret |
| certificate   | PEM    | `PRIVATE_KEY_JWT`                              | Public certificate (`BEGIN CERTIFICATE`) of `privateKey`, as uploaded to the app registration                     |
| resourceUri   | string | `PRIVATE_KEY_JWT`, Entra `CLIENT_SECRET`       | The API to call, e.g. `https://graph.microsoft.com` or `api://<app-id>`. The scope is always `{resourceUri}/.default` |

Properties of other auth types are ignored. The configuration is validated when it is saved; an invalid property fails
the save with an error that starts with the property name, e.g. `tenantId: must be a GUID (directory/tenant ID)`.

### Auth types

**`NONE`**: the GraphQL endpoint is called without an `Authorization` header.

**`CLIENT_SECRET`**: OAuth2 client credentials flow with a client secret. The access token is sent as
`Authorization: Bearer <token>`.

- With `tokenUrl` (any OAuth2 identity provider, e.g. Keycloak): the token is requested from `tokenUrl` with the client
  credentials as HTTP Basic authentication and no scope. This is the behaviour of plugin versions 1.0 to 1.2.
- Without `tokenUrl` (Microsoft Entra ID): the token is requested from
  `https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token` with `client_id`, `client_secret` and
  `scope={resourceUri}/.default` in the form body.

**`PRIVATE_KEY_JWT`**: OAuth2 client credentials flow against Microsoft Entra ID, authenticated with a signed client
assertion ([RFC 7523](https://www.rfc-editor.org/rfc/rfc7523)) instead of a secret. For every token request the plugin
signs a new JWT with `privateKey` (RS256) with:

- header: `alg=RS256`, `typ=JWT`, `x5t` (SHA-1) and `x5t#S256` (SHA-256) thumbprints of `certificate`
- claims: `iss` and `sub` = `clientId`, `aud` = the token endpoint, `iat`, `nbf`, `exp` = `iat` + 2 minutes, a random `jti`

Only application permissions are supported (no delegated permissions or user context). For both Entra ID variants
the plugin rejects an access token whose `tid` claim differs from `tenantId`, whose `idtyp` claim is not `app`, or that
has no `roles` claim (which means admin consent for the application permissions is missing).

### Token handling

- Access tokens are cached per configuration and refreshed when less than 30 seconds of their lifetime remain.
  Concurrent service tasks share one token request.
- When the GraphQL endpoint responds with HTTP 401, the cached token is discarded and the request is retried exactly
  once with a new token.
- Token endpoint errors (e.g. `invalid_client`, `unauthorized_client`) fail the service task with the OAuth2 error code
  and the `AADSTS` code, e.g. `Token request to login.microsoftonline.com failed with HTTP 401: invalid_client (AADSTS700027)`.
  Camunda's retry policy then applies and an incident is created. The message never contains the private key, client
  secret, client assertion or access token.
- HTTP 5xx responses and network errors from the token endpoint are retried up to 3 attempts with exponential backoff
  before the service task fails.
- Debug logging (`com.ritense.valtimoplugins.graphql`) shows the auth type, the token endpoint host and the token
  expiry time.

### Existing configurations

Configurations created before version 1.3.0 have no `authType`. They keep working unchanged: when `tokenUrl` or
`clientId` is set they use `CLIENT_SECRET` with `tokenUrl`, otherwise `NONE`. No data migration is needed; `authType` is
stored the next time the configuration is saved.

### Microsoft Entra ID setup for `PRIVATE_KEY_JWT`

1. Create a key pair and a self-signed certificate, for example:
   ```shell
   openssl req -x509 -newkey rsa:2048 -nodes -keyout private-key.pem -out certificate.pem -days 365 -subj "/CN=valtimo-graphql"
   ```
2. In the Azure portal, open the app registration (single tenant) and upload `certificate.pem` under
   **Certificates & secrets > Certificates**.
3. Under **API permissions**, add the **application permissions** the GraphQL API requires and click
   **Grant admin consent**.
4. Copy the **Directory (tenant) ID** and **Application (client) ID** from the **Overview** page to `tenantId` and
   `clientId`.
5. In the plugin configuration, select `PRIVATE_KEY_JWT`, paste the contents of `private-key.pem` into `privateKey`
   and of `certificate.pem` into `certificate`, and set `resourceUri` to the API (e.g. `https://graph.microsoft.com`).
6. Check that the SHA-1 thumbprint shown below the certificate field equals the thumbprint shown in Azure.

### Key rotation

1. Create a new key pair and certificate, and upload the new certificate to the app registration next to the old one.
2. Update the plugin configuration with the new `privateKey` and `certificate`. Check the shown thumbprint against Azure.
3. Run a process that uses the configuration and verify that it succeeds.
4. Delete the old certificate from the app registration.

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
