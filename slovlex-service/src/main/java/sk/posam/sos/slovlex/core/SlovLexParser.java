package sk.posam.sos.slovlex.core;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import sk.posam.sos.slovlex.core.Model.Hlavicka;
import sk.posam.sos.slovlex.core.Model.Verzia;

/**
 * Parser HTML stránok statickej verzie portálu Slov-Lex (static.slov-lex.sk).
 *
 * Slov-Lex nemá verejné API, preto čítame HTML. Parser zámerne používa len niekoľko stabilných
 * "kotiev", ktoré portál generuje strojovo:
 * <ul>
 *   <li>história: riadky {@code <tr class="effectivenessHistoryItem" data-iri=... data-ucinnostod=...>}</li>
 *   <li>znenie: tabuľka {@code <table id="InfoTable">} a odkaz na PDF – v surovom HTML je
 *       {@code /static/pdf/SK/ZZ/...pdf} (prepisuje ho až JavaScript), funkčná adresa je {@code /pdf/SK/ZZ/...pdf}</li>
 * </ul>
 * Ak Slov-Lex zmení šablónu, zlyhajú testy v {@code SlovLexParserTest} – treba upraviť len túto triedu.
 */
public final class SlovLexParser {

	private static final Pattern HISTORY_ROW = Pattern.compile(
			"<tr\\b([^>]*\\beffectivenessHistoryItem\\b[^>]*)>(.*?)</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	private static final Pattern TD = Pattern.compile("<td\\b[^>]*>(.*?)</td>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	private static final Pattern H1_HISTORY = Pattern.compile(
			"<h1[^>]*>\\s*História predpisu\\s+(.*?)</h1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	private static final Pattern INFO_TABLE = Pattern.compile(
			"<table[^>]*\\bid=\"InfoTable\"[^>]*>(.*?)</table>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	private static final Pattern INFO_ROW = Pattern.compile(
			"<tr[^>]*>\\s*<td[^>]*>(.*?)</td>\\s*<td[^>]*>(.*?)</td>\\s*</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	private static final Pattern LI = Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	private static final Pattern PDF_LINK = Pattern.compile(
			"href=\"(?:/static)?(/pdf/SK/ZZ/\\d{4}/\\d+/[^\"]+\\.pdf)\"", Pattern.CASE_INSENSITIVE);
	private static final Pattern TAG = Pattern.compile("<[^>]+>");
	private static final Pattern NUM_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");
	private static final DateTimeFormatter SK_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	/** Značka konca hlavičky znenia – po nej už nepotrebujeme čítať zvyšok (často niekoľko MB) HTML. */
	public static final String INFO_TABLE_MARKER = "id=\"InfoTable\"";

	private SlovLexParser() {
	}

	/**
	 * Označenie predpisu z nadpisu stránky histórie, napr. "448/2008 Z. z.".
	 *
	 * @param html HTML stránky histórie
	 * @return označenie alebo null
	 */
	public static String parseOznacenie(String html) {
		Matcher m = H1_HISTORY.matcher(html);
		return m.find() ? text(m.group(1)) : null;
	}

	/**
	 * Zoznam časových verzií predpisu zo stránky histórie.
	 *
	 * @param html    HTML stránky https://static.slov-lex.sk/static/SK/ZZ/{rok}/{cislo}/
	 * @param baseUrl napr. https://static.slov-lex.sk
	 * @return verzie v poradí zo stránky
	 */
	public static List<Verzia> parseHistoria(String html, String baseUrl) {
		List<Verzia> verzie = new ArrayList<>();
		Matcher row = HISTORY_ROW.matcher(html);
		while (row.find()) {
			String attrs = row.group(1);
			String iri = attr(attrs, "data-iri");
			if (iri == null || iri.isBlank()) {
				continue;
			}
			boolean vyhlasene = "1".equals(attr(attrs, "data-vyhlasene")) || iri.endsWith("/vyhlasene_znenie");
			LocalDate od = isoDate(attr(attrs, "data-ucinnostod"));
			LocalDate dO = isoDate(attr(attrs, "data-ucinnostdo"));
			List<String> cells = new ArrayList<>();
			Matcher td = TD.matcher(row.group(2));
			while (td.find()) {
				cells.add(text(td.group(1)));
			}
			String novela = cells.size() >= 3 && !cells.get(2).isBlank() ? cells.get(2) : null;
			String id = iri.substring(iri.lastIndexOf('/') + 1);
			verzie.add(new Verzia(id, od, dO, vyhlasene, novela, baseUrl + "/static" + iri + ".html"));
		}
		return verzie;
	}

	/**
	 * Údaje z hlavičky znenia (InfoTable).
	 *
	 * @param html    začiatok HTML znenia (stačí po koniec InfoTable)
	 * @param baseUrl napr. https://static.slov-lex.sk
	 * @return hlavička alebo null, ak sa tabuľka nenašla
	 */
	public static Hlavicka parseHlavicka(String html, String baseUrl) {
		Matcher table = INFO_TABLE.matcher(html);
		if (!table.find()) {
			return null;
		}
		Map<String, String> values = new HashMap<>();
		List<String> oblasti = new ArrayList<>();
		Matcher r = INFO_ROW.matcher(table.group(1));
		while (r.find()) {
			String label = text(r.group(1)).replaceAll(":\\s*$", "").trim().toLowerCase();
			String raw = r.group(2);
			if (label.startsWith("právna oblasť")) {
				Matcher li = LI.matcher(raw);
				while (li.find()) {
					String o = text(li.group(1));
					if (!o.isBlank()) {
						oblasti.add(o);
					}
				}
			}
			values.put(label, text(raw));
		}
		Matcher pdf = PDF_LINK.matcher(html);
		String pdfUrl = pdf.find() ? baseUrl + pdf.group(1) : null;
		return new Hlavicka(values.get("číslo predpisu"), values.get("názov"), values.get("typ"), values.get("autor"),
				skDate(values.get("dátum schválenia")), skDate(values.get("dátum vyhlásenia")), List.copyOf(oblasti), pdfUrl);
	}

	/**
	 * @param html prečítaná časť HTML
	 * @return true, ak už obsahuje celú InfoTable (zvyšok stránky netreba čítať)
	 */
	public static boolean hasCompleteInfoTable(CharSequence html) {
		String s = html.toString();
		int i = s.indexOf(INFO_TABLE_MARKER);
		return i >= 0 && s.indexOf("</table>", i) > 0;
	}

	static String attr(String attrs, String name) {
		Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(attrs);
		return m.find() ? decode(m.group(1)) : null;
	}

	static String text(String html) {
		if (html == null) {
			return "";
		}
		String t = TAG.matcher(html).replaceAll(" ");
		return decode(t).replace(' ', ' ').replaceAll("\\s+", " ").trim();
	}

	static String decode(String s) {
		String t = s.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
				.replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">");
		Matcher m = NUM_ENTITY.matcher(t);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			int cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
			m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
		}
		m.appendTail(sb);
		return sb.toString().replace("&amp;", "&");
	}

	static LocalDate isoDate(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(s.trim());
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	static LocalDate skDate(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(s.trim(), SK_DATE);
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
