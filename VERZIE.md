# Platnosť obsahu KB a verzie otázok (Platforma SOS)

Rozšírenie fóra (fork Scoold) pre znalostnú bázu: pri otázkach a odpovediach sa dá označiť **platnosť od–do**
a k otázke vytvoriť **novú verziu** (napr. po novele zákona). **Obe verzie sa dajú vyhľadať** – výsledky majú
štítok platnosti a vyhľadávanie má voliteľný filter.

## Čo pôvodný Scoold má a nemá

| | Pôvodný Scoold | Platforma SOS |
|---|---|---|
| História úprav (revízie, porovnanie, návrat) | áno | áno (bez zmeny) |
| Platnosť od–do | nie | **áno** – otázky aj odpovede |
| Verzie s rôznou platnosťou, prepojené | nie | **áno** – „Vytvoriť novú verziu“ pri otázke |
| Vyhľadanie starej aj novej verzie | nie (revízie sa nevyhľadávajú) | **áno** – každá verzia je samostatná otázka |

Revízie (bežné opravy textu) ostávajú, ako boli. Verzia s platnosťou je **samostatná otázka** s vlastnými
odpoveďami a komentármi, prepojená s predchádzajúcou a nasledujúcou verziou.

## Ako sa to používa (moderátor / správca)

1. Pri otázke alebo odpovedi rozbaliť **„Platnosť a verzie otázky“** / **„Platnosť odpovede“**.
2. **Platné od / Platné do** – dátumy (prázdne = bez obmedzenia; „do“ je vrátane dňa) → Uložiť.
3. **Nová verzia otázky** – zadať „Nová verzia platí od“ → *Vytvoriť novú verziu*:
   - vznikne kópia otázky (nadpis, text, štítky, oblasť) platná od zadaného dňa, autorom je moderátor,
   - pôvodná verzia dostane „platné do“ = deň pred začiatkom novej verzie,
   - obe sa prepoja (odkazy „Aktuálna verzia“ / „Predchádzajúca verzia“); odpovede sa nekopírujú,
   - otvorí sa nová verzia na úpravu obsahu.

Oprávnenie: **moderátori a správcovia** (skupiny `sos-moderator`, `sos-admin` v Keycloaku). Bežní
používatelia platnosť len vidia.

## Čo vidí používateľ

- **Štítok** pri otázke (v zozname, vo vyhľadávaní, v detaile) a pri odpovedi:
  „Platné od … do …“ (zelený), „Platí od …“ (budúca verzia), „Neplatné – platilo do …“ (sivý).
- **Neplatná verzia** – upozornenie „Táto verzia platila do …“ a odkaz na aktuálnu verziu.
- **Budúca verzia** – „Táto verzia platí až od …“ a odkaz na verziu, ktorá platí dovtedy.
- Pri platnej verzii odkaz na predchádzajúcu verziu, prípadne „Nová verzia platí od …“.

## Vyhľadávanie

Predvolene **všetky verzie** s označením platnosti. Filter „Platnosť obsahu“:

| Voľba | URL parameter | Výsledok |
|---|---|---|
| Všetky verzie | – | platné aj neplatné (so štítkom) |
| Len platné dnes | `platnost=platne` | len obsah platný k dnešku |
| Platné k dátumu | `platnost=k_datumu&k=2026-03-01` | obsah platný k zadanému dňu |

Filter sa uplatní na otázky aj odpovede vo výsledkoch. Príspevky bez nastavenej platnosti platia vždy.

## Technicky

| Súbor | Zmena |
|---|---|
| `core/Post.java` | polia `validFrom`, `validTo`, `previousVersionId`, `nextVersionId` (`@Stored`, ISO dátum `yyyy-MM-dd`); pomocné metódy `hasValidity()`, `validityState()`, `validOn(day)`, `validFromText()`… (bez prefixu get/is – neserializujú sa) |
| `controllers/SosVersionController.java` | `POST /question/{id}/validity`, `POST /question/{id}/new-version` (len `isMod`) |
| `controllers/SearchController.java` | filter platnosti (`platnost`, `k`) nad výsledkami |
| `utils/ScooldUtils.java` | `sosVersion(id)` – načítanie prepojenej verzie pre šablóny |
| `templates/idsk/macros.vm` | makro `#idskvalidity`, štítok v `#idskstatus` a pri odpovediach, formulár pre moderátorov |
| `templates/idsk/question.vm`, `search.vm` | upozornenia o verziách, filter vo vyhľadávaní |
| `lang_sk.properties`, `lang_en.properties` | texty `idsk.validity.*`, `idsk.version.*` |

Údaje sa ukladajú ako ostatné polia otázky v Pare (žiadna migrácia, existujúce otázky sú „bez obmedzenia“).
Pri veľkom množstve verzií sa filter dá presunúť priamo do dotazu Elasticsearchu (`properties.validTo` / `properties.validFrom`).
