# Vyhľadávanie cez Elasticsearch

Scoold sám nevyhľadáva – vyhľadávanie robí jeho backend **Para**. Pôvodne Para používa
vstavaný **Lucene** (index v súboroch vo volume `paraData`). Para má oficiálny plugin
[para-search-elasticsearch](https://github.com/Erudika/para-search-elasticsearch), ktorým sa Lucene
nahradí Elasticsearchom. Kód Scooldu sa nemení.

## Kedy to má zmysel

| | Lucene (pôvodné v Scoolde) | Elasticsearch (u nás) |
|---|---|---|
| Prevádzka | nič navyše | ďalšia služba (RAM ~1 GB a viac) |
| Viac inštancií Scooldu/Pary | nie (index je lokálny súbor) | áno, zdieľaný index |
| Škálovanie, zálohy, monitoring indexu | obmedzené | cluster, snapshoty, Kibana |
| Napojenie iných systémov na index | nie | áno (napr. spoločné vyhľadávanie na portáli) |

Pre jeden server stačí Lucene. Elasticsearch je príprava na produkciu (viac inštancií, vysoká dostupnosť).

## Architektúra

```
Prehliadač ──> Scoold :8000 ──> Para :8080 ──┬──> H2 (dáta: otázky, odpovede, používatelia)
                                             └──> Elasticsearch :9200 (index na vyhľadávanie)
```

Dáta ostávajú v databáze Pary (H2). Elasticsearch drží len index – dá sa kedykoľvek zmazať a znova
vytvoriť z databázy („Obnoviť vyhľadávací index" v Správe).

## Súbory

Všetko je v `docker-compose.override.yml` (žiadny samostatný súbor):

- služba `elasticsearch` (9.x, single-node, bez hesla – len lokálny vývoj);
- služba `para` sa zostaví ako image `para-es:dev` = `erudikaltd/para:latest_stable` + plugin
  `para-search-elasticsearch` 1.43.1 (pre Para 1.55.x), cez `dockerfile_inline`.

Nastavenie Pary sa odovzdáva cez `JAVA_OPTS` (system properties), `para-application.conf` sa nemení:

```properties
para.search = "ElasticSearch"
para.plugin_folder = "/para/plugins/"   # odtiaľ Para načíta plugin
para.es.flavor = "elasticsearch"
para.es.restclient_host = "elasticsearch"
para.es.restclient_port = 9200
para.es.shards = 1
para.es.replicas = 0
```

## Spustenie

```bash
cd ~/POC/scoold-sos
docker compose up -d --build
```

Po prvom spustení treba existujúce dáta preniesť do nového indexu:

1. Prihláste sa ako správca (`admin.sos`).
2. **Správa** → **Obnoviť vyhľadávací index** (prebuduje index z databázy).

## Overenie

```bash
# beží Elasticsearch?
curl -s http://localhost:9200
# vytvorila Para index pre aplikáciu scoold? (stĺpec docs.count = počet objektov)
curl -s "http://localhost:9200/_cat/indices?v"
# nájde sa otázka?
curl -s "http://localhost:9200/scoold/_search?q=zákon&size=3&pretty" | head -40
# Para naozaj používa Elasticsearch
docker compose logs para | grep -i elasticsearch | head
```

Potom vo fóre vyhľadajte slovo z niektorej otázky – výsledky už idú z Elasticsearchu.
