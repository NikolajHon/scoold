# IS CSRÚ – mock služieb MPSVaR (Platforma SOS)

Mock referenčných údajov z **IS CSRÚ** pre demo aplikáciu `portal-demo`. Pokrýva tri objekty evidencie
z prílohy č. 2 integračného manuálu služieb IS CSRÚ:

| Kapitola manuálu | Objekt evidencie | ISVS / poskytovateľ | XSD (pôvodné, z manuálu) | Volanie mocku |
|---|---|---|---|---|
| 5.6 Evidencia ŤZP | FO – ťažko zdravotne postihnutý | IS RSD (isvs_279), MPSVaR | `OE_MPSVAR_TZP_OUT_v001.xsd` | `GET /api/v1/tzp?rc=` |
| 5.11 Peňažný príspevok na opatrovanie | osoba poberajúca PPnO (neformálny opatrovateľ) | IS RSD (isvs_279), MPSVaR | `OE_MPSVAR_PPnO_001.xsd` | `GET /api/v1/ppno?rc=` (poberateľ) · `?opatrovanaRc=` (opatrovaná osoba) |
| 5.13 Register sociálnych služieb | poskytovateľ sociálnych služieb | IS SOS (isvs_9627), MPSVaR | `OE_MPSVAR_RSS_Vypis_v001.xsd` | `GET /api/v1/rss?ico=` |
| (pri 5.6 a 5.11) Zmenené subjekty ŤZP / PPnO | identifikátory subjektov | IS CSRÚ | `CSRU_Pub_GetListChanges_1.0.xsd` | `GET /api/v1/zmeny/TZP` · `/api/v1/zmeny/PPnO` (`?od=&do=`, predvolene 14 dní) |

XSD sú v `src/main/resources/xsd/`, vytiahnuté z vložených objektov v manuáli, a mock ich vydáva na
`/xsd/<názov>.xsd`. Popisy atribútov (XLS prílohy manuálu) zodpovedajú týmto XSD.

## Spustenie

Služba `csru-mock` je v `docker-compose.override.yml`:

```bash
docker compose up -d --build csru-mock portal
```

- http://localhost:8091 – prehľad služieb, demo osoby a poskytovatelia, odkazy na XML odpovede
- Portál http://localhost:8082 → vyberte žiadosť → sekcia **Referenčné údaje z IS CSRÚ**
  (ŤZP, PPnO, RSS + tlačidlo „Zobraziť XML odpovede“)
- Pod zoznamom žiadostí (len `rezort.test` / správca) tabuľka **Zmeny v registroch** (GetListChanges)

Pri builde sa spúšťa `--selftest`: všetky demo odpovede sa validujú voči XSD z manuálu, takže pri
nekompatibilnej úprave dát build zlyhá.

## Demo údaje (všetky fiktívne)

| Žiadosť v portáli | Žiadateľ | ŤZP | PPnO (opatrovateľ) | Poskytovateľ (RSS) |
|---|---|---|---|---|
| Z-2026-0101 | Ján Kováčik `410312/0011` | áno, preukaz ŤZP-S + parkovací | Eva Kováčiková – poskytuje sa | Slnečný dom, n. o. `51234567` |
| Z-2026-0102 | Mária Horváthová `485705/0022` | áno, preukaz ŤZP | – | Obec Testovo `00312345` |
| Z-2026-0103 | Peter Sloboda `391120/0033` | nie je v evidencii | – | Slnečný dom, n. o. |
| Z-2026-0201 | Anna Bieliková `525214/0044` | áno, od 2026-08, preukaz zatiaľ nevydaný | – | Mesto Vzorová `00398765` (vrátane vymazanej služby) |
| Z-2026-0202 | Rudolf Tóth `440901/0055` | áno, dve obdobia | Miroslav Tóth – ukončený 08/2026 | Slnečný dom, n. o. |

Údaje sú v `DemoData.java`, rendrovanie XML podľa XSD v `Render.java`.

## Čo je predpoklad mocku (overiť s IS CSRÚ / MPSVaR)

- **Transport:** príloha č. 2 popisuje len dátové štruktúry. Spôsob volania (SOAP/WSDL, autentifikácia,
  obálka požiadavky) je v hlavnom integračnom manuáli. Mock preto používa jednoduché `GET` s XML odpoveďou;
  v portáli stačí vymeniť `CsruClient` (parsovanie XML zostáva rovnaké).
- **Kódy číselníkov** sú ilustračné: typ identifikátora (CL004001 – v mocku `RC`, `ICO`), úrad (UradPSVaR),
  okres (CL000024), stav dávky (StavPoskytovaniaSocDavky), druh/forma služby, cieľová skupina, typ poskytovateľa,
  dôvod výmazu a `ChangeType` pri zmenách.
- **PPnO podľa opatrovanej osoby** (`?opatrovanaRc=`) je rozšírenie mocku pre demo. Objekt evidencie je
  podľa manuálu „osoba poberajúca PPnO“, teda dopyt je pravdepodobne podľa poberateľa (opatrovateľa).
- Atribút `dataFrom` v PPnO je v mocku pevne `2020-01`.

## Štruktúra

```
csru-mock/
├── Dockerfile                 # javac + selftest + jar, bez Mavenu a závislostí
└── src/main/
    ├── java/sk/posam/sos/csru/
    │   ├── CsruMockServer.java   # HTTP (JDK HttpServer), routovanie, selftest
    │   ├── DemoData.java         # fiktívne osoby, ŤZP, PPnO, RSS, zmeny
    │   ├── Render.java           # XML podľa XSD
    │   └── Xml.java              # jednoduchý zapisovač XML
    └── resources/xsd/            # pôvodné XSD z integračného manuálu
```
