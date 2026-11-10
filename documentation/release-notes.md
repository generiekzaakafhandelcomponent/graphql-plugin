# Release notes

Overzicht van wijzigingen per versie van de GraphQL plugin.

## 1.3.0

Nieuw:

- Keuze van het authenticatietype per configuratie (`authType`): `NONE`, `CLIENT_SECRET` of `PRIVATE_KEY_JWT`
- `PRIVATE_KEY_JWT`: OAuth2 client credentials met een ondertekende client assertion (certificaat) voor Microsoft
  Entra ID, met `tenantId`, `clientId`, `privateKey`, `certificate` en `resourceUri`
- `CLIENT_SECRET` zonder `tokenUrl` gebruikt Microsoft Entra ID met `tenantId` en `resourceUri`
- Validatie van de configuratie bij opslaan, met de naam van het ongeldige veld in de foutmelding
- Bij HTTP 401 van het GraphQL endpoint wordt eenmalig opnieuw geprobeerd met een nieuw access token
- Gelijktijdige service tasks delen één token request; 5xx- en netwerkfouten van het token endpoint worden tot 3 keer
  geprobeerd

Compatibiliteit:

- Bestaande configuraties zonder `authType` werken ongewijzigd (`CLIENT_SECRET` met `tokenUrl` als `tokenUrl` of
  `clientId` is ingevuld, anders `NONE`). Er is geen datamigratie nodig.
- Fouten van het token endpoint geven nu een `TokenRequestException` met de OAuth2 foutcode in plaats van de
  onderliggende HTTP-fout.

## 1.0.0

Eerste versie:

- Configuratie van een GraphQL endpoint (`graphqlUrl`)
- Optionele OAuth2 authenticatie via de client credentials flow (`tokenUrl`, `clientId`, `clientSecret`)
- Actie `graphql-by-input` voor het uitvoeren van GraphQL queries, met value resolving van variabelen (bijv. `pv:caseId`)
