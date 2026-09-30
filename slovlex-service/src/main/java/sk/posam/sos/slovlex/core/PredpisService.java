package sk.posam.sos.slovlex.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import sk.posam.sos.slovlex.core.Model.Hlavicka;
import sk.posam.sos.slovlex.core.Model.Predpis;
import sk.posam.sos.slovlex.core.Model.Verzia;

/**
 * Skladá "kartu predpisu" z histórie a hlavičky znenia a drží výsledky v cache,
 * aby sme Slov-Lex nezaťažovali (predpisy sa menia zriedka).
 */
public class PredpisService {

	/** Zdroj a upozornenie o záväznosti – súčasť každej odpovede. */
	private static final Map<String, String> ZDROJ = Map.of(
			"nazov", "Slov-Lex – právny a informačný portál Ministerstva spravodlivosti SR",
			"url", "https://www.slov-lex.sk",
			"upozornenie", "HTML znenie má informatívny charakter, právne záväzné je PDF znenie v Zbierke zákonov SR.");

	private final SlovLexClient client;
	private final String baseUrl;
	private final String portalUrl;
	private final Duration ttl;
	private final Duration notFoundTtl;
	private final Clock clock;

	private final Map<String, Cached<Optional<Historia>>> historiaCache = new ConcurrentHashMap<>();
	private final Map<String, Cached<Optional<Hlavicka>>> hlavickaCache = new ConcurrentHashMap<>();

	/**
	 * @param client      HTTP klient
	 * @param baseUrl     https://static.slov-lex.sk
	 * @param portalUrl   https://www.slov-lex.sk
	 * @param ttl         ako dlho držať úspešne načítané údaje
	 * @param notFoundTtl ako dlho si pamätať neexistujúci predpis
	 * @param clock       hodiny (časové pásmo Europe/Bratislava)
	 */
	public PredpisService(SlovLexClient client, String baseUrl, String portalUrl, Duration ttl, Duration notFoundTtl,
			Clock clock) {
		this.client = client;
		this.baseUrl = trimSlash(baseUrl);
		this.portalUrl = trimSlash(portalUrl);
		this.ttl = ttl;
		this.notFoundTtl = notFoundTtl;
		this.clock = clock;
	}

	/**
	 * Karta predpisu so znením účinným k danému dátumu.
	 *
	 * @param rok   rok (napr. 2008)
	 * @param cislo číslo (napr. 448)
	 * @param datum dátum, ku ktorému chceme znenie; null = dnes
	 * @return karta alebo prázdne, ak predpis neexistuje
	 */
	public Optional<Predpis> get(int rok, int cislo, LocalDate datum) {
		validate(rok, cislo);
		LocalDate den = datum != null ? datum : LocalDate.now(clock);
		Optional<Historia> historia = historia(rok, cislo);
		if (historia.isEmpty()) {
			return Optional.empty();
		}
		Historia h = historia.get();
		Verzia verzia = vyberVerziu(h.verzie(), den);
		Hlavicka hl = verzia != null ? hlavicka(verzia.url()).orElse(null) : null;
		String oznacenie = hl != null && hl.oznacenie() != null ? hl.oznacenie() : h.oznacenie();
		return Optional.of(new Predpis(
				oznacenie != null ? oznacenie : cislo + "/" + rok,
				rok, cislo,
				hl != null ? hl.nazov() : null,
				hl != null ? hl.typ() : null,
				hl != null ? hl.autor() : null,
				hl != null ? hl.datumSchvalenia() : null,
				hl != null ? hl.datumVyhlasenia() : null,
				hl != null ? hl.pravneOblasti() : List.of(),
				verzia,
				hl != null ? hl.pdfUrl() : null,
				portalUrl + "/ezbierky/pravne-predpisy/SK/ZZ/" + rok + "/" + cislo + "/",
				baseUrl + "/static/SK/ZZ/" + rok + "/" + cislo + "/",
				h.verzie().size(),
				den,
				h.nacitane(),
				ZDROJ));
	}

	/**
	 * Verzia účinná v daný deň. Ak taká nie je (predpis ešte nenadobudol účinnosť), vráti vyhlásené znenie,
	 * inak prvú verziu v zozname.
	 *
	 * @param verzie verzie z histórie
	 * @param den    deň
	 * @return verzia alebo null pri prázdnom zozname
	 */
	public static Verzia vyberVerziu(List<Verzia> verzie, LocalDate den) {
		Verzia vyhlasene = null;
		for (Verzia v : verzie) {
			if (v.ucinnaV(den)) {
				return v;
			}
			if (v.vyhlasene() && vyhlasene == null) {
				vyhlasene = v;
			}
		}
		if (vyhlasene != null) {
			return vyhlasene;
		}
		return verzie.isEmpty() ? null : verzie.get(0);
	}

	/** Vymaže cache (napr. pre administrátora). */
	public void clearCache() {
		historiaCache.clear();
		hlavickaCache.clear();
	}

	private Optional<Historia> historia(int rok, int cislo) {
		String url = baseUrl + "/static/SK/ZZ/" + rok + "/" + cislo + "/";
		return cached(historiaCache, url, () -> client.get(url).map(html -> new Historia(
				SlovLexParser.parseOznacenie(html), SlovLexParser.parseHistoria(html, baseUrl), Instant.now(clock))));
	}

	private Optional<Hlavicka> hlavicka(String verziaUrl) {
		return cached(hlavickaCache, verziaUrl, () -> client.read(verziaUrl, SlovLexParser::hasCompleteInfoTable)
				.map(html -> SlovLexParser.parseHlavicka(html, baseUrl)));
	}

	private <T> Optional<T> cached(Map<String, Cached<Optional<T>>> cache, String key,
			java.util.function.Supplier<Optional<T>> loader) {
		Instant now = Instant.now(clock);
		Cached<Optional<T>> c = cache.get(key);
		if (c != null && c.expires().isAfter(now)) {
			return c.value();
		}
		Optional<T> value = loader.get();
		cache.put(key, new Cached<>(value, now.plus(value.isPresent() ? ttl : notFoundTtl)));
		return value;
	}

	private static void validate(int rok, int cislo) {
		if (rok < 1918 || rok > 2100 || cislo < 1 || cislo > 9999) {
			throw new IllegalArgumentException("Neplatné číslo predpisu: " + cislo + "/" + rok);
		}
	}

	private static String trimSlash(String s) {
		return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
	}

	private record Historia(String oznacenie, List<Verzia> verzie, Instant nacitane) {
	}

	private record Cached<V>(V value, Instant expires) {
	}
}
