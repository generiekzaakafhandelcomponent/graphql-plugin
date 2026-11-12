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

import {Component, EventEmitter, Input, OnDestroy, OnInit, Output} from "@angular/core";
import {PluginConfigurationComponent, PluginConfigurationData} from "@valtimo/plugin";
import {BehaviorSubject, combineLatest, from, map, Observable, of, Subscription, switchMap, take} from "rxjs";
import {AuthType, GraphQLPluginConfig} from "../../models";

const GUID = /^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/;
// Requires the END line too, so a truncated paste is reported in the form (v-input defaults to maxLength 250)
const PRIVATE_KEY_PEM = /-----BEGIN (RSA )?PRIVATE KEY-----[\s\S]+-----END \1?PRIVATE KEY-----/;
const CERTIFICATE_PEM = /-----BEGIN CERTIFICATE-----([\s\S]+?)-----END CERTIFICATE-----/;

/** Reads notAfter from a DER certificate: Certificate > tbsCertificate > [version], serial, signature, issuer, validity. */
export function certificateNotAfter(certificateBase64: string): Date | null {
  try {
    const der = Uint8Array.from(atob(certificateBase64.replace(/\s/g, "")), c => c.charCodeAt(0));
    // Returns [content start, content end] of the TLV at pos
    const tlv = (pos: number): [number, number] => {
      let length = der[pos + 1];
      let start = pos + 2;
      if (length & 0x80) {
        const bytes = length & 0x7f;
        length = 0;
        for (let i = 0; i < bytes; i++) length = length * 256 + der[start + i];
        start += bytes;
      }
      return [start, start + length];
    };
    let pos = tlv(tlv(0)[0])[0]; // first element inside tbsCertificate
    if (der[pos] === 0xa0) pos = tlv(pos)[1]; // skip optional version
    for (let i = 0; i < 3; i++) pos = tlv(pos)[1]; // skip serial, signature, issuer
    const notBefore = tlv(pos)[0];
    const notAfter = tlv(notBefore)[1];
    const [start, end] = tlv(notAfter);
    const text = String.fromCharCode(...der.subarray(start, end));
    // UTCTime (0x17) has a 2-digit year: 50-99 is 19xx, otherwise 20xx (RFC 5280)
    const full = der[notAfter] === 0x17 ? (Number(text.slice(0, 2)) >= 50 ? "19" : "20") + text : text;
    const m = full.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})Z$/);
    return m ? new Date(Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6])) : null;
  } catch {
    return null;
  }
}

@Component({
  standalone: false,
  selector: "valtimo-graphql-plugin-configuration",
  templateUrl: "./graphql-plugin-configuration.component.html",
})
export class GraphQLPluginConfigurationComponent implements PluginConfigurationComponent, OnInit, OnDestroy {
  @Input() save$!: Observable<void>;
  @Input() disabled$!: Observable<boolean>;
  @Input() pluginId!: string;
  @Input() prefillConfiguration$!: Observable<GraphQLPluginConfig>;
  @Output() valid: EventEmitter<boolean> = new EventEmitter<boolean>();
  @Output() configuration: EventEmitter<PluginConfigurationData> = new EventEmitter<PluginConfigurationData>();

  private saveSubscription!: Subscription;
  readonly formValue$ = new BehaviorSubject<GraphQLPluginConfig | null>(null);
  private readonly valid$ = new BehaviorSubject<boolean>(false);
  /** Translation keys of format errors per field; required fields are handled by the inputs themselves. */
  readonly errors$ = this.formValue$.pipe(map(formValue => (formValue ? this.formatErrors(formValue) : {})));
  /** SHA-1 thumbprint of the certificate in the form, as Azure shows it under Certificates & secrets. */
  readonly certificateThumbprint$ = this.formValue$.pipe(
    map(formValue => formValue?.certificate?.match(CERTIFICATE_PEM)?.[1]),
    switchMap(base64 => (base64 ? from(this.sha1Thumbprint(base64)) : of(null)))
  );
  /** notAfter of the certificate in the form, so an expiring certificate is noticed before token requests fail. */
  readonly certificateExpiry$ = this.formValue$.pipe(
    map(formValue => formValue?.certificate?.match(CERTIFICATE_PEM)?.[1]),
    map(base64 => (base64 ? certificateNotAfter(base64) : null))
  );

  ngOnInit(): void {
    this.openSaveSubscription();
  }

  ngOnDestroy() {
    this.saveSubscription?.unsubscribe();
  }

  /** Configurations from before 1.3.0 have no authType: they use OAuth2 when tokenUrl or clientId is set. */
  defaultAuthType(prefill: GraphQLPluginConfig | null): AuthType {
    return prefill?.authType ?? (prefill?.tokenUrl || prefill?.clientId ? "CLIENT_SECRET" : "NONE");
  }

  formValueChange(formValue: GraphQLPluginConfig): void {
    this.formValue$.next(formValue);
    this.handleValid(formValue);
  }

  private handleValid(formValue: GraphQLPluginConfig): void {
    const relevant = this.relevantConfiguration(formValue);
    const required = Object.keys(relevant).filter(key => key !== "tokenUrl");
    const valid =
      !!formValue.configurationTitle &&
      required.every(key => !!relevant[key as keyof GraphQLPluginConfig]) &&
      Object.keys(this.formatErrors(formValue)).length === 0;
    this.valid$.next(valid);
    this.valid.emit(valid);
  }

  private formatErrors(formValue: GraphQLPluginConfig): Record<string, string> {
    const {tenantId, resourceUri, privateKey, certificate} = this.relevantConfiguration(formValue);
    const errors: Record<string, string> = {};
    if (tenantId && !GUID.test(tenantId.trim())) errors["tenantId"] = "invalidTenantId";
    if (resourceUri && /\/\.default\/?$|\s/.test(resourceUri.trim())) errors["resourceUri"] = "invalidResourceUri";
    if (privateKey && !PRIVATE_KEY_PEM.test(privateKey)) errors["privateKey"] = "invalidPrivateKey";
    if (certificate && !CERTIFICATE_PEM.test(certificate)) errors["certificate"] = "invalidCertificate";
    return errors;
  }

  /** The properties of the selected auth type; required unless noted. Properties of other types are left out. */
  private relevantConfiguration(formValue: GraphQLPluginConfig): Partial<GraphQLPluginConfig> {
    const {graphqlUrl, authType, tokenUrl, tenantId, clientId, clientSecret, privateKey, certificate, resourceUri} = formValue;
    switch (authType) {
      case "CLIENT_SECRET":
        // tokenUrl is optional: set for other identity providers, empty for Entra ID
        return tokenUrl
          ? {graphqlUrl, authType, tokenUrl, clientId, clientSecret}
          : {graphqlUrl, authType, tokenUrl, tenantId, clientId, clientSecret, resourceUri};
      case "PRIVATE_KEY_JWT":
        return {graphqlUrl, authType, tenantId, clientId, privateKey, certificate, resourceUri};
      default:
        return {graphqlUrl, authType: "NONE"};
    }
  }

  private async sha1Thumbprint(certificateBase64: string): Promise<string | null> {
    try {
      const der = Uint8Array.from(atob(certificateBase64.replace(/\s/g, "")), c => c.charCodeAt(0));
      const digest = new Uint8Array(await crypto.subtle.digest("SHA-1", der));
      return Array.from(digest, b => b.toString(16).padStart(2, "0")).join("").toUpperCase();
    } catch {
      return null;
    }
  }

  private openSaveSubscription(): void {
    this.saveSubscription = this.save$?.subscribe(() => {
      combineLatest([this.formValue$, this.valid$])
        .pipe(take(1))
        .subscribe(([formValue, valid]) => {
          if (valid) {
            this.configuration.emit(<PluginConfigurationData>{
              configurationTitle: formValue!.configurationTitle,
              ...this.relevantConfiguration(formValue!),
            });
          }
        });
    });
  }
}
