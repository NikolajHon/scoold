package sk.posam.sos.csru;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

/**
 * Mock služieb IS CSRÚ pre objekty evidencie MPSVaR: Evidencia ŤZP, PPnO, Register sociálnych služieb
 * a zoznam zmenených subjektov. Bez externých závislostí (JDK HttpServer).
 *
 * <pre>
 * GET /api/v1/tzp?rc=...                 Evidencia ŤZP              (OE_MPSVAR_TZP_OUT_v001.xsd)
 * GET /api/v1/ppno?rc=...                PPnO podľa poberateľa      (OE_MPSVAR_PPnO_001.xsd)
 * GET /api/v1/ppno?opatrovanaRc=...      PPnO podľa opatrovanej osoby (rozšírenie mocku)
 * GET /api/v1/rss?ico=...                Register sociálnych služieb (OE_MPSVAR_RSS_Vypis_v001.xsd)
 * GET /api/v1/zmeny/{TZP|PPnO}?od=&do=   zoznam zmenených subjektov (CSRU_Pub_GetListChanges_1.0.xsd)
 * GET /xsd/{subor}.xsd                   pôvodné XSD z manuálu
 * GET /                                  prehľad + demo identifikátory
 * </pre>
 */
public final class CsruMockServer {

	private static final List<String> XSD = List.of("OE_MPSVAR_TZP_OUT_v001.xsd", "OE_MPSVAR_PPnO_001.xsd",
		"OE_MPSVAR_RSS_Vypis_v001.xsd", "CSRU_Pub_GetListChanges_1.0.xsd");

	private CsruMockServer() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length > 0 && "--selftest".equals(args[0])) {
			selfTest();
			return;
		}
		int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8091"));
		HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
		server.createContext("/", CsruMockServer::handle);
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.start();
		System.out.println("IS CSRÚ mock beží na porte " + port + " (fiktívne údaje)");
	}

	private static void handle(HttpExchange ex) throws IOException {
		try (ex) {
			try {
				route(ex);
			} catch (IllegalArgumentException | DateTimeParseException e) {
				send(ex, 400, "text/plain", e.getMessage());
			}
		}
	}

	private static void route(HttpExchange ex) throws IOException {
		{
			URI uri = ex.getRequestURI();
			String path = uri.getPath();
			Map<String, String> q = query(uri.getRawQuery());
			System.out.println(ex.getRequestMethod() + " " + uri);
			if (!"GET".equals(ex.getRequestMethod())) {
				send(ex, 405, "text/plain", "Len GET");
				return;
			}
			switch (path) {
				case "/", "/index.html" -> send(ex, 200, "text/html", index());
				case "/health" -> send(ex, 200, "application/json", "{\"status\":\"UP\"}");
				case "/api/v1/tzp" -> {
					String rc = required(q, "rc");
					send(ex, 200, "application/xml", Render.tzp(DemoData.tzp(rc).stream().toList()));
				}
				case "/api/v1/ppno" -> {
					if (q.containsKey("opatrovanaRc")) {
						send(ex, 200, "application/xml", Render.ppno(DemoData.ppnoByNursed(q.get("opatrovanaRc"))));
					} else {
						send(ex, 200, "application/xml", Render.ppno(DemoData.ppnoByRecipient(required(q, "rc"))));
					}
				}
				case "/api/v1/rss" -> {
					String ico = required(q, "ico");
					send(ex, 200, "application/xml", Render.rss(DemoData.provider(ico).orElse(null)));
				}
				default -> {
					if (path.startsWith("/api/v1/zmeny/")) {
						String register = path.substring("/api/v1/zmeny/".length());
						if (!register.equals("TZP") && !register.equals("PPnO")) {
							send(ex, 404, "text/plain", "Neznámy register (TZP, PPnO): " + register);
							return;
						}
						LocalDate to = q.containsKey("do") ? LocalDate.parse(q.get("do")) : LocalDate.now();
						LocalDate from = q.containsKey("od") ? LocalDate.parse(q.get("od")) : to.minusDays(13);
						send(ex, 200, "application/xml", Render.changes(register, from, to));
					} else if (path.startsWith("/xsd/") && XSD.contains(path.substring(5))) {
						send(ex, 200, "application/xml", resource("/xsd/" + path.substring(5)));
					} else {
						send(ex, 404, "text/plain", "Nenájdené: " + path);
					}
				}
			}
		}
	}

	private static String required(Map<String, String> q, String name) {
		String v = q.get(name);
		if (v == null || v.isBlank()) {
			throw new IllegalArgumentException("Chýba parameter '" + name + "'");
		}
		return v.trim();
	}

	private static Map<String, String> query(String raw) {
		Map<String, String> m = new LinkedHashMap<>();
		if (raw == null) {
			return m;
		}
		for (String part : raw.split("&")) {
			int i = part.indexOf('=');
			if (i > 0) {
				m.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8),
					URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
			}
		}
		return m;
	}

	private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
		ex.getResponseHeaders().set("X-Csru-Mock", "true");
		ex.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(bytes);
		}
	}

	private static String resource(String name) throws IOException {
		try (InputStream in = CsruMockServer.class.getResourceAsStream(name)) {
			if (in == null) {
				throw new IOException("Chýba " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String index() {
		StringBuilder sb = new StringBuilder("""
			<!doctype html><html lang="sk"><head><meta charset="utf-8"><title>IS CSRÚ – mock</title>
			<style>body{font-family:system-ui,sans-serif;max-width:900px;margin:24px auto;padding:0 16px;color:#0b0c0c}
			code{background:#f3f2f1;padding:1px 4px}table{border-collapse:collapse;width:100%}
			td,th{border-bottom:1px solid #ddd;padding:6px;text-align:left}.warn{background:#fff7bf;padding:8px 12px}</style>
			</head><body><h1>IS CSRÚ – mock služieb MPSVaR</h1>
			<p class="warn">Ukážka pre Platformu SOS. Všetky osoby, rodné čísla a IČO sú <b>fiktívne</b>.
			Štruktúra odpovedí podľa XSD z prílohy č. 2 integračného manuálu IS CSRÚ.</p>
			<h2>Služby</h2><table><tr><th>Objekt evidencie</th><th>Volanie</th><th>XSD</th></tr>
			<tr><td>Evidencia ŤZP</td><td><code>/api/v1/tzp?rc=</code></td><td><a href="/xsd/OE_MPSVAR_TZP_OUT_v001.xsd">TZP_OUT_v001</a></td></tr>
			<tr><td>Peňažný príspevok na opatrovanie</td><td><code>/api/v1/ppno?rc=</code> alebo <code>?opatrovanaRc=</code></td><td><a href="/xsd/OE_MPSVAR_PPnO_001.xsd">PPnO_001</a></td></tr>
			<tr><td>Register sociálnych služieb</td><td><code>/api/v1/rss?ico=</code></td><td><a href="/xsd/OE_MPSVAR_RSS_Vypis_v001.xsd">RSS_Vypis_v001</a></td></tr>
			<tr><td>Zmenené subjekty ŤZP / PPnO</td><td><code>/api/v1/zmeny/TZP?od=&amp;do=</code></td><td><a href="/xsd/CSRU_Pub_GetListChanges_1.0.xsd">Pub_GetListChanges_1.0</a></td></tr>
			</table><h2>Demo osoby</h2><table><tr><th>Osoba</th><th>RČ (fiktívne)</th><th>Odkazy</th></tr>
			""");
		for (DemoData.Person p : DemoData.PERSONS) {
			sb.append("<tr><td>").append(Xml.esc(p.fullName())).append("</td><td><code>").append(p.rc())
				.append("</code></td><td><a href=\"/api/v1/tzp?rc=").append(p.rc()).append("\">ŤZP</a> · ")
				.append("<a href=\"/api/v1/ppno?rc=").append(p.rc()).append("\">PPnO (poberateľ)</a> · ")
				.append("<a href=\"/api/v1/ppno?opatrovanaRc=").append(p.rc()).append("\">PPnO (opatrovaná)</a></td></tr>");
		}
		sb.append("</table><h2>Demo poskytovatelia</h2><table><tr><th>Poskytovateľ</th><th>IČO (fiktívne)</th></tr>");
		for (DemoData.Provider p : DemoData.RSS) {
			sb.append("<tr><td>").append(Xml.esc(p.name())).append("</td><td><a href=\"/api/v1/rss?ico=")
				.append(p.ico()).append("\">").append(p.ico()).append("</a></td></tr>");
		}
		return sb.append("</table><p><a href=\"/api/v1/zmeny/TZP\">Zmeny ŤZP (14 dní)</a> · ")
			.append("<a href=\"/api/v1/zmeny/PPnO\">Zmeny PPnO (14 dní)</a></p></body></html>").toString();
	}

	/** Overí všetky demo odpovede voči pôvodným XSD z manuálu (spúšťa sa pri builde). */
	private static void selfTest() throws Exception {
		SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Map<String, List<String>> docs = new LinkedHashMap<>();
		List<String> tzp = new ArrayList<>();
		List<String> ppno = new ArrayList<>();
		for (DemoData.Person p : DemoData.PERSONS) {
			tzp.add(Render.tzp(DemoData.tzp(p.rc()).stream().toList()));
			ppno.add(Render.ppno(DemoData.ppnoByRecipient(p.rc())));
			ppno.add(Render.ppno(DemoData.ppnoByNursed(p.rc())));
		}
		List<String> rss = new ArrayList<>();
		DemoData.RSS.forEach(p -> rss.add(Render.rss(p)));
		rss.add(Render.rss(null));
		LocalDate today = LocalDate.now();
		docs.put("OE_MPSVAR_TZP_OUT_v001.xsd", tzp);
		docs.put("OE_MPSVAR_PPnO_001.xsd", ppno);
		docs.put("OE_MPSVAR_RSS_Vypis_v001.xsd", rss);
		docs.put("CSRU_Pub_GetListChanges_1.0.xsd", List.of(
			Render.changes("TZP", today.minusDays(13), today), Render.changes("PPnO", today.minusDays(13), today),
			Render.changes("TZP", today.minusDays(100), today.minusDays(90))));
		int n = 0;
		for (Map.Entry<String, List<String>> e : docs.entrySet()) {
			var validator = f.newSchema(new StreamSource(new StringReader(resource("/xsd/" + e.getKey())))).newValidator();
			for (String xml : e.getValue()) {
				validator.validate(new StreamSource(new StringReader(xml)));
				n++;
			}
		}
		System.out.println("Self-test OK: " + n + " odpovedí zodpovedá XSD z manuálu.");
	}
}
