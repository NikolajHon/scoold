# Agenda sociálnych služieb – demo SSO (Spring Boot + Angular)

Ukážková „odborná“ aplikácia k fóru Platformy SOS: **posudzovanie odkázanosti na sociálnu službu**.
Ukazuje, ako sa fórum zapojí do bežnej práce:

- **jedno prihlásenie (SSO)** – aplikácia aj fórum sú klienti toho istého realmu `sos` v Keycloaku;
- **rovnaké skupiny = rovnaké oprávnenia** – podľa skupiny z Keycloaku vidí používateľ iné žiadosti:

  | Skupina | Vidí | Môže meniť stav |
  |---|---|---|
  | `obce` (`obec.test`) | žiadosti obce *Obec Testovo* | áno |
  | `poskytovatelia` (`poskytovatel.test`) | žiadosti pre *Slnečný dom, n. o.* | nie |
  | `rezort` / `sos-moderator` / `sos-admin` | všetky žiadosti | áno |

- **„Opýtať sa v komunite“** – pri žiadosti otvorí vo fóre formulár otázky predvyplnený číslom žiadosti,
  službou, popisom situácie, predpisom (fórum k nemu zobrazí kartu zo Slov-Lexu) a tagmi;
- **„Podobné otázky vo fóre“** – vyhľadávanie vo fóre podľa služby.

Údaje sú **ilustračné** (žiadne osobné údaje) a držia sa len v pamäti.

```
Prehliadač ──> Agenda :8082 (Spring Boot + Angular) ─┐
          └──> Fórum  :8000 (Scoold / Para)          ─┴──> Keycloak :8081 (realm sos, jedna relácia)
```

| Časť | Čo robí |
|---|---|
| `backend/` | Spring Boot 3.5, `oauth2Login` (Authorization Code, dôverný klient `portal-demo`), session na serveri, REST `/api/me`, `/api/cases`, `/api/cases/{id}/status`, `/api/config`, odhlásenie aj z Keycloaku, **back-channel logout** `/backchannel-logout` (single logout), CSRF pre SPA |
| `frontend/` | Angular 19: prehľad (dlaždice), zoznam a detail žiadostí, zmena stavu, odkazy do fóra, kontrola relácie (každých 10 s a pri návrate na kartu) |
| `keycloak-client.json` | definícia klienta `portal-demo` v Keycloaku (vrátane mappera `groups`) |
| `Dockerfile` | build Angularu → vloženie do `static/` Spring Bootu → jeden jar |

## Spustenie

1. **Klient v Keycloaku** (realm už existuje, preto ho pridáme cez kcadm – stačí raz):

   ```bash
   cd ~/POC/scoold-sos
   docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password admin
   docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh create clients -r sos -f - < portal-demo/keycloak-client.json
   ```

2. **Portál** (služba `portal` je v `docker-compose.override.yml`):

   ```bash
   docker compose up -d --build portal
   ```

3. Otvorte http://localhost:8082.

### Plynulé SSO aj smerom do fóra (odporúčané)

Bez tejto voľby fórum zobrazí svoju prihlasovaciu stránku a treba kliknúť „Prihlásiť sa cez Keycloak“
(heslo sa už nepýta). S ňou fórum presmeruje rovno na Keycloak:

```properties
# scoold-application.conf
scoold.redirect_signin_to_idp = true
```

a `docker compose restart scoold`. Po odhlásení (`/signin?code=5`) sa prihlasovacia stránka fóra zobrazí normálne.

## Test (scenár na ukážku)

1. http://localhost:8082 → „Prihlásiť sa cez Keycloak“ → `obec.test` / `test`.
   Vidíte 3 žiadosti *Obce Testovo*, dlaždice so stavmi a v hlavičke rozsah „Obec Testovo“.
2. Vyberte žiadosť → „Opýtať sa v komunite“ → fórum sa otvorí **bez hesla** s predvyplnenou otázkou;
   pod textom sa zobrazí karta zákona č. 448/2008 Z. z.
3. Zmeňte stav žiadosti (napr. na „V posudzovaní“) – dlaždice sa prepočítajú.
4. Odhláste sa, prihláste sa ako `poskytovatel.test` – iné žiadosti, len na čítanie.
   Ako `rezort.test` – všetky žiadosti.
5. Opačný smer SSO: prihláste sa najprv vo fóre, potom otvorte aplikáciu – heslo sa nepýta.
6. **Jednotné odhlásenie (oba smery):** majte otvorený portál aj fórum.
   - Vo fóre „Odhlásiť“ (Keycloak sa opýta „Do you want to log out?“ → **Logout**) → portál pri návrate
     na kartu ukáže „Boli ste odhlásený“.
   - V portáli „Odhlásiť sa“ → fórum pri ďalšom kliknutí / návrate na kartu prejde na prihlásenie.
   - Logy: `docker compose logs portal scoold | grep -i "back-channel"`.

## Jednotné odhlásenie – OIDC Back-Channel Logout

Bez pravidelného dopytovania Keycloaku. Keycloak pri odhlásení **sám oznámi** (server → server) všetkým
aplikáciám, v ktorých bol používateľ prihlásený, že relácia skončila:

```
Odhlásenie v ktorejkoľvek aplikácii → Keycloak /logout (ukončí SSO reláciu)
  ├─ POST http://portal:8082/backchannel-logout      (logout_token)
  │     portál overí podpis (JWKS), iss, aud=portal-demo, events, sid → zruší HTTP session (SessionRegistry)
  └─ POST http://scoold:8000/sos/backchannel-logout  (logout_token)
        fórum overí podpis, iss, aud=scoold, events → zapamätá si „sub odhlásený o T“;
        pri ďalšej požiadavke zruší reláciu, ak bola vytvorená pred T (KeycloakBackchannelLogout)
Prehliadač: portál overí /api/me pri návrate na kartu (a reaguje na každé 401), fórum /sos/session pri návrate na kartu
```

| Aplikácia | Endpoint | Kód |
|---|---|---|
| Portál | `POST /backchannel-logout` | `BackchannelLogoutController`, `SessionRegistry` |
| Fórum | `POST /sos/backchannel-logout` | `SosSessionController`, `KeycloakBackchannelLogout` (+ kontrola v `ScooldUtils.checkAuth`) |

Nastavenie klientov (`Back-channel logout URL`, `session required = ON`, `Front channel logout = OFF`) je
v `keycloak/sos-realm.json`. Do **už bežiaceho** Keycloaku ho nastaví skript (stačí raz):

```bash
bash keycloak/enable-backchannel-logout.sh
```

Adresy `portal:8082` a `scoold:8000` sú interné v docker sieti – volá ich kontajner Keycloaku, nie prehliadač.
Obmedzenie dema: fórum drží „odhlásených“ v pamäti jednej inštancie; pri viacerých inštanciách treba
zdieľané úložisko (napr. Redis).

## Referenčné údaje z IS CSRÚ (mock)

V detaile žiadosti portál načíta údaje z troch objektov evidencie MPSVaR cez IS CSRÚ:
**Evidencia ŤZP**, **Peňažný príspevok na opatrovanie** (neformálni opatrovatelia) a **Register sociálnych služieb**.
Rezort/správca vidí navyše **Zmeny v registroch** (GetListChanges, 14 dní). V deme ich poskytuje služba
`csru-mock` (fiktívne údaje, XML podľa XSD z integračného manuálu) – pozri [`../csru-mock/README.md`](../csru-mock/README.md).

| Súbor | Čo robí |
|---|---|
| `CsruClient.java` | volá IS CSRÚ (`app.csru-url`, env `CSRU_URL`), parsuje XML (ŤZP, PPnO, RSS, zmeny) |
| `CsruController.java` | `GET /api/cases/{id}/csru` (len k žiadostiam, ktoré používateľ vidí), `GET /api/csru/zmeny` (len rezort) |
| `CaseService.java` | k žiadosti drží fiktívne RČ žiadateľa a IČO poskytovateľa (do UI ide RČ len maskované) |

```bash
docker compose up -d --build csru-mock portal
```

## Lokálny vývoj bez dockeru

```bash
cd portal-demo/backend && mvn spring-boot:run                 # http://localhost:8082
cd portal-demo/frontend && npm install && npm start           # http://localhost:4200 (proxy na 8082)
```

Pri `npm start` treba do klienta v Keycloaku doplniť aj redirect `http://localhost:4200/login/oauth2/code/keycloak`
a spustiť backend s `PUBLIC_URL=http://localhost:4200`.

## Konfigurácia (premenné prostredia)

| Premenná | Predvolene | Význam |
|---|---|---|
| `KC_PUBLIC` | `http://localhost:8081` | Keycloak z pohľadu prehliadača |
| `KC_INTERNAL` | `http://localhost:8081` | Keycloak z pohľadu backendu (v dockeri `http://keycloak:8080`) |
| `PUBLIC_URL` | `http://localhost:8082` | adresa portálu (návrat po odhlásení) |
| `FORUM_URL` | `http://localhost:8000` | adresa fóra |
| `PORTAL_CLIENT_SECRET` | `portal-dev-secret` | secret klienta – **len pre vývoj** |

Pozn.: `issuer-uri` zámerne nie je nastavené – issuer tokenov je `http://localhost:8081/...`, ale backend
v dockeri volá Keycloak cez `keycloak:8080`; endpointy sú preto zadané ručne (rovnako ako pri Pare).
