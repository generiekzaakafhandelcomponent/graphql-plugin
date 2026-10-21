# Plugin Documentation

<!-- Use this page to document your plugin. Below is a suggested structure. -->

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

List the plugin configuration properties and how to set them.

| Property   | Type   | Required | Description                                                         |
|------------|--------|----------|---------------------------------------------------------------------|
| graphqlUrl | string | Yes      | The URL of the GraphQL endpoint, e.g. `https://example.com/graphql` |

## Actions

No actions yet.

## Usage

Explain how to use the plugin in a process, with examples if applicable.
