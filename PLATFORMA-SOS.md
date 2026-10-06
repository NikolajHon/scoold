# Platforma SOS – rýchly štart (lokálne)

Fork [Scoold](https://github.com/Erudika/scoold) pre Platformu SOS: fórum v štýle ID-SK 3.0 (slovenčina),
prihlásenie cez Keycloak (SSO + jednotné odhlásenie), vyhľadávanie cez Elasticsearch, karty predpisov zo Slov-Lexu,
demo aplikácia „Agenda sociálnych služieb“ (Spring Boot + Angular) a mock služieb IS CSRÚ.

## Predpoklady

- **Docker Desktop** (na Macu s Apple Silicon zapnutá emulácia amd64 / Rosetta – obraz Para je len amd64),
  pre Docker aspoň **6–8 GB RAM**
- **git**, prístup na internet pri prvom builde (Docker Hub, quay.io, Maven Central, npm, slov-lex.sk)
- voľné porty: **8000, 8080, 8081, 8082, 8090, 8091, 9200**

## Spustenie

```bash
git clone -b sos-idsk git@github.com:NikolajHon/scoold.git scoold-sos
cd scoold-sos
cp scoold-application.conf.example scoold-application.conf
docker compose up -d --build
```

Prvý štart trvá niekoľko minút (build + Para v emulácii). Hotovo, keď `docker compose logs scoold` obsahuje
`Started ScooldServer`. Kľúče Pary a Scooldu sa vygenerujú automaticky pri prvom štarte
(`para-application.conf` musí byť na začiatku **prázdny** – v repozitári je).

| Adresa | Čo |
|---|---|
| http://localhost:8000 | komunitné fórum |
| http://localhost:8082 | demo aplikácia Agenda sociálnych služieb |
| http://localhost:8081 | Keycloak (konzola: `admin` / `admin`) |
| http://localhost:8091 | mock IS CSRÚ (ŤZP, PPnO, Register sociálnych služieb) |

## Testovací používatelia (realm `sos`)

| Používateľ / heslo | Skupiny v Keycloaku | Fórum | Agenda |
|---|---|---|---|
| `admin.sos` / `admin` | sos-admin | správca | všetky žiadosti |
| `rezort.test` / `test` | rezort, sos-moderator | moderátor | všetky žiadosti, zmeny v registroch |
| `obec.test` / `test` | obce | používateľ | žiadosti obce, zmena stavu |
| `poskytovatel.test` / `test` | poskytovatelia | používateľ | žiadosti poskytovateľa, len čítanie |

Heslá, client secrety (`scoold-dev-secret`, `portal-dev-secret`) a `admin/admin` sú **len pre lokálny vývoj**.

## Úplne odznova (zmaže dáta fóra, Keycloak sa naimportuje znova)

```bash
docker compose down -v --remove-orphans && : > para-application.conf && docker compose up -d --build
```

## Dokumentácia

| Súbor | Téma |
|---|---|
| [KEYCLOAK.md](KEYCLOAK.md) | prihlásenie, roly zo skupín, používatelia |
| [ELASTICSEARCH.md](ELASTICSEARCH.md) | vyhľadávanie |
| [SLOVLEX.md](SLOVLEX.md) | karty právnych predpisov |
| [portal-demo/README.md](portal-demo/README.md) | demo aplikácia, SSO, jednotné odhlásenie (back-channel logout) |
| [csru-mock/README.md](csru-mock/README.md) | mock IS CSRÚ podľa integračného manuálu |
| [VERZIE.md](VERZIE.md) | platnosť obsahu KB od–do a verzie otázok |

## Časté problémy

- **Fórum sa nepripojí k Pare** (`No connection to Para` v logu) – Para v emulácii štartuje pomaly; počkajte
  alebo `docker compose restart scoold`. Ak sa menili dáta Pary, zopakujte „úplne odznova“.
- **Zmeny v `keycloak/sos-realm.json` sa neprejavia** – realm sa importuje len pri vytvorení kontajnera Keycloaku
  (`docker compose up -d --force-recreate keycloak`, dáta Keycloaku sa tým zmažú).
- **Na Windows/Linux (x86)** riadok `platform: linux/amd64` v `docker-compose.override.yml` nevadí.
