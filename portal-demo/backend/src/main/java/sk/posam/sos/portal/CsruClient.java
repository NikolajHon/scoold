package sk.posam.sos.portal;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Klient služieb IS CSRÚ (objekty evidencie MPSVaR). Odpovede sú XML podľa XSD z prílohy č. 2
 * integračného manuálu; v deme ich poskytuje služba csru-mock.
 */
@Component
public class CsruClient {

	// ---------------------------------------------------------------- výstupné záznamy pre UI

	/** Odpoveď jednej služby: spracované údaje + pôvodné XML (na ukážku) alebo chyba. */
	public record Odpoved<T>(String sluzba, String url, T data, String xml, String chyba) { }

	public record Obdobie(String zaciatok, String koniec) { }

	public record Preukaz(String cislo, String typ, String platnyOd, String platnyDo) { }

	public record Tzp(boolean evidovany, boolean aktualne, List<Obdobie> obdobia, List<Preukaz> preukazy,
			List<Preukaz> parkovaciePreukazy) { }

	public record StavObdobie(String zaciatok, String koniec, String stav) { }

	public record PpnoZaznam(String poberatel, String ziadostId, String urad, String okres, String podana,
			String opatrovanaOd, String opatrovanaDo, String rola, boolean aktivny, List<StavObdobie> obdobia) { }

	public record Ppno(boolean poskytuje, List<PpnoZaznam> zaznamy) { }

	public record Sluzba(String id, String druh, String forma, String miesto, Integer kapacita, String od,
			String koniec, List<String> cieloveSkupiny, String zodpovedna, String email, String telefon,
			String zapis, String vymaz, boolean aktivna) { }

	public record Rss(boolean najdeny, String nazov, String ico, String typ, String adresa, String statutar,
			List<Sluzba> sluzby) { }

	public record Zmena(String register, String datum, String typ, String osoba, String cas) { }

	// ---------------------------------------------------------------- volania

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private final String baseUrl;

	public CsruClient(@Value("${app.csru-url:http://localhost:8091}") String baseUrl) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	/** Evidencia ŤZP podľa rodného čísla. */
	public Odpoved<Tzp> tzp(String rc) {
		return call("Evidencia ŤZP", "/api/v1/tzp?rc=" + enc(rc), root -> {
			Element person = first(root, "person");
			if (person == null) {
				return new Tzp(false, false, List.of(), List.of(), List.of());
			}
			List<Obdobie> obdobia = children(person, "disability").stream()
				.map(d -> new Obdobie(text(d, "from"), text(d, "to"))).toList();
			boolean aktualne = obdobia.stream().anyMatch(o -> o.koniec() == null
				|| !YearMonth.parse(o.koniec()).isBefore(YearMonth.now()));
			List<Preukaz> preukazy = children(person, "disabilityCard").stream()
				.map(c -> new Preukaz(text(c, "id"), "tzp-s".equals(text(c, "type")) ? "ŤZP-S" : "ŤZP",
					text(c, "validFrom"), text(c, "validTo"))).toList();
			List<Preukaz> parkovacie = children(person, "parkingCard").stream()
				.map(c -> new Preukaz(text(c, "id"), "parkovací", text(c, "validFrom"), text(c, "validTo"))).toList();
			return new Tzp(true, aktualne, obdobia, preukazy, parkovacie);
		});
	}

	/** Peňažný príspevok na opatrovanie, kde je osoba opatrovanou osobou. */
	public Odpoved<Ppno> ppnoPreOpatrovanu(String rc) {
		return call("Peňažný príspevok na opatrovanie", "/api/v1/ppno?opatrovanaRc=" + enc(rc), root -> {
			List<PpnoZaznam> zaznamy = new ArrayList<>();
			for (Element person : children(root, "person")) {
				Element pp = first(person, "physicalPerson");
				String poberatel = text(pp, "givenName") + " " + text(pp, "familyName");
				for (Element app : children(person, "application")) {
					for (Element nursed : children(app, "nursedPerson")) {
						Element np = first(nursed, "person");
						if (!rc.equals(identifier(np))) {
							continue;
						}
						List<StavObdobie> obdobia = children(app, "period").stream()
							.map(p -> new StavObdobie(text(p, "monthFrom"), text(p, "monthTo"), text(p, "state"))).toList();
						String opatrovanaDo = text(nursed, "dateTo");
						boolean aktivny = opatrovanaDo == null || !LocalDate.parse(opatrovanaDo).isBefore(LocalDate.now());
						zaznamy.add(new PpnoZaznam(poberatel, text(app, "id"), text(app, "office"), text(app, "district"),
							text(app, "dateSubmitted"), text(nursed, "dateFrom"), opatrovanaDo, text(nursed, "role"),
							aktivny, obdobia));
					}
				}
			}
			return new Ppno(zaznamy.stream().anyMatch(PpnoZaznam::aktivny), zaznamy);
		});
	}

	/** Register sociálnych služieb – výpis poskytovateľa podľa IČO. */
	public Odpoved<Rss> rss(String ico) {
		return call("Register sociálnych služieb", "/api/v1/rss?ico=" + enc(ico), root -> {
			Element p = first(root, "provider");
			if (p == null) {
				return new Rss(false, null, ico, null, null, null, List.of());
			}
			String nazov = text(p, "name") != null ? text(p, "name") : text(p, "givenName") + " " + text(p, "familyName");
			List<Sluzba> sluzby = children(p, "providedService").stream().map(s -> {
				Element scope = first(s, "territorialScope");
				String miesto = first(s, "address") != null ? address(first(s, "address"))
					: scope != null ? "územná pôsobnosť: " + scope.getTextContent().trim() : null;
				String kapacita = text(s, "currentCapacity");
				String vymaz = text(s, "dateOfErasure");
				String koniec = text(s, "providedTo");
				boolean aktivna = vymaz == null && text(s, "providedFrom") != null
					&& (koniec == null || !LocalDate.parse(koniec).isBefore(LocalDate.now()));
				return new Sluzba(text(s, "identifier"), codeText(first(s, "type")), codeText(first(s, "form")), miesto,
					kapacita == null ? null : Integer.valueOf(kapacita), text(s, "providedFrom"), koniec,
					children(s, "targetGroup").stream().map(CsruClient::codeText).toList(),
					text(s, "responsiblePerson"), text(s, "email"), text(s, "phone"),
					text(s, "dateOfRegistration"), vymaz, aktivna);
			}).toList();
			return new Rss(true, nazov, identifier(p), codeText(first(p, "providerType")), address(first(p, "address")),
				text(p, "statutoryBody"), sluzby);
		});
	}

	/** Zoznam zmenených subjektov (GetListChanges) za posledných 14 dní. */
	public Odpoved<List<Zmena>> zmeny(String register) {
		return call("Zmenené subjekty " + register, "/api/v1/zmeny/" + enc(register), root -> {
			List<Zmena> zmeny = new ArrayList<>();
			Element changes = first(root, "Changes");
			if (changes != null) {
				for (Element day : children(changes, "ChangesInDay")) {
					for (Element subject : children(first(day, "Subjects"), "Subject")) {
						Element data = first(subject, "SubjectData");
						String osoba = initials(text(data, "GivenName"), text(data, "FamilyName"));
						zmeny.add(new Zmena(register, text(day, "DateOfChange"), text(subject, "ChangeType"), osoba,
							text(subject, "TimeStamp")));
					}
				}
			}
			return zmeny;
		});
	}

	// ---------------------------------------------------------------- pomocné

	private <T> Odpoved<T> call(String sluzba, String path, Function<Element, T> mapper) {
		String url = baseUrl + path;
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
				.header("Accept", "application/xml").GET().build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (res.statusCode() != 200) {
				return new Odpoved<>(sluzba, path, null, res.body(), "IS CSRÚ vrátil HTTP " + res.statusCode());
			}
			DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
			f.setNamespaceAware(true);
			f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			Element root = f.newDocumentBuilder()
				.parse(new ByteArrayInputStream(res.body().getBytes(StandardCharsets.UTF_8))).getDocumentElement();
			return new Odpoved<>(sluzba, path, mapper.apply(root), res.body(), null);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Odpoved<>(sluzba, path, null, null, "Prerušené");
		} catch (Exception e) {
			return new Odpoved<>(sluzba, path, null, null, "IS CSRÚ nedostupný (" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()) + ")");
		}
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	/** Priame podelementy podľa lokálneho mena (namespace sa líši podľa služby). */
	static List<Element> children(Element parent, String name) {
		List<Element> out = new ArrayList<>();
		if (parent == null) {
			return out;
		}
		for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element e && name.equals(e.getLocalName())) {
				out.add(e);
			}
		}
		return out;
	}

	static Element first(Element parent, String name) {
		List<Element> l = children(parent, name);
		return l.isEmpty() ? null : l.get(0);
	}

	static String text(Element parent, String name) {
		Element e = first(parent, name);
		return e == null ? null : e.getTextContent().trim();
	}

	private static String identifier(Element parent) {
		Element id = first(parent, "identifier");
		return id == null ? null : text(id, "value");
	}

	private static String codeText(Element code) {
		return code == null ? null : text(code, "itemDescription");
	}

	private static String address(Element a) {
		if (a == null) {
			return null;
		}
		if (text(a, "fullTextAddress") != null) {
			return text(a, "fullTextAddress");
		}
		String psc = text(a, "postCode");
		return String.join(" ", nz(text(a, "street")), nz(text(a, "streetNumber"))).trim() + ", "
			+ (psc == null ? "" : psc.replaceFirst("^(\\d{3})(\\d{2})$", "$1 $2") + " ") + nz(text(a, "lau2"));
	}

	private static String initials(String given, String family) {
		return (given == null ? "" : given.charAt(0) + ". ") + (family == null ? "" : family.charAt(0) + ".");
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
