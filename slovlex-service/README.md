# slovlex-service

Malá Spring Boot služba pre Platformu SOS, ktorá poskytuje **karty právnych predpisov** zo Slov-Lexu
(názov, typ, znenie účinné k dátumu, posledná novela, odkaz na PDF).

Slov-Lex nemá verejné API, preto služba číta HTML statickej verzie portálu (`static.slov-lex.sk`):

| Stránka | Čo z nej berieme |
|---|---|
| `/static/SK/ZZ/{rok}/{cislo}/` (história, ~20 kB) | zoznam časových verzií (`tr.effectivenessHistoryItem`, atribúty `data-iri`, `data-ucinnostod`, `data-ucinnostdo`), novely |
| `/static/SK/ZZ/{rok}/{cislo}/{verzia}.html` | `table#InfoTable` (názov, typ, autor, dátumy, právne oblasti) a odkaz na PDF – čítanie sa ukončí hneď po tabuľke (stránky majú aj niekoľko MB) |

Zásady: vlastný User-Agent, max. 2 súbežné požiadavky, cache 24 h (neexistujúce predpisy 1 h).
HTML znenie má len informatívny charakter – právne záväzné je PDF (uvádzame v odpovedi aj v UI).

## API

```
GET  /api/predpisy/{rok}/{cislo}[?datum=yyyy-MM-dd]   karta predpisu (predvolene znenie účinné dnes)
POST /api/cache/clear                                  vymaže cache
GET  /actuator/health                                  stav služby
```

Príklad: `GET /api/predpisy/2008/448` →

```json
{
  "oznacenie": "448/2008 Z. z.",
  "nazov": "Zákon o sociálnych službách a o zmene a doplnení zákona č. 455/1991 Zb. ...",
  "typ": "Zákon",
  "verzia": { "id": "20260701", "ucinnostOd": "2026-07-01", "ucinnostDo": "2026-12-30", "novela": "406/2025 Z. z.",
              "url": "https://static.slov-lex.sk/static/SK/ZZ/2008/448/20260701.html" },
  "pdfUrl": "https://static.slov-lex.sk/pdf/SK/ZZ/2008/448/ZZ_2008_448_20260701.pdf",
  "portalUrl": "https://www.slov-lex.sk/ezbierky/pravne-predpisy/SK/ZZ/2008/448/"
}
```

Služba je interná – prehliadač volá Scoold (`/slovlex/{rok}/{cislo}`, `SlovLexController`), ktorý požiadavku
prepošle sem podľa `scoold.slovlex_service_url`. Odkazy na predpisy v príspevkoch vytvára
`static/scripts/idsk/slovlex.js` aj vtedy, keď služba nebeží.

## Konfigurácia

`src/main/resources/application.yml`, prefix `slovlex` (env premenné `SLOVLEX_BASE_URL`, `SLOVLEX_CACHE_TTL`, ...).
V cieľovej infraštruktúre musí byť povolený odchádzajúci HTTPS prístup na `static.slov-lex.sk`.

## Build a testy

```bash
docker build -t slovlex-service .          # build + testy (mvn package)
mvn test                                   # testy lokálne
```

Testy (`src/test`) bežia nad skrátenými kópiami skutočných stránok (`src/test/resources/fixtures`).
Ak Slov-Lex zmení šablónu stránok, testy to odhalia – upravuje sa len `SlovLexParser`.
