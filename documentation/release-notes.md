# Release notes

Overzicht van wijzigingen per versie van de GraphQL plugin.

## 0.0.1

Eerste versie:

- Configuratie van een GraphQL endpoint (`graphqlUrl`)
- Optionele OAuth2 authenticatie via de client credentials flow (`tokenUrl`, `clientId`, `clientSecret`)
- Actie `graphql-by-input` voor het uitvoeren van GraphQL queries, met value resolving van variabelen (bijv. `pv:caseId`)
