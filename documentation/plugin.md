# Plugin Documentation

<!-- Use this page to document your plugin. Below is a suggested structure. -->

## Overview

This is the GraphQL plugin demonstrating an API call action. It fetches data from a time API endpoint.

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

List the plugin configuration properties and how to set them.

| Property | Type   | Required | Description                          |
|----------|--------|----------|--------------------------------------|
| apiUrl   | string | Yes      | The URL of the time API to call      |

## Actions

### Time API test action

Sends a GET request to the configured API URL and returns the timezone response.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
|           |      |          |             |

## Usage

Explain how to use the plugin in a process, with examples if applicable.
