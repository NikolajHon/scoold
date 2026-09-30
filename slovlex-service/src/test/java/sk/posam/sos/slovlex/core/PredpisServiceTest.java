package sk.posam.sos.slovlex.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.posam.sos.slovlex.core.Model.Predpis;

/**
 * End-to-end test služby proti lokálnemu HTTP serveru, ktorý vracia fixtures (bez prístupu na internet).
 */
class PredpisServiceTest {

	private HttpServer server;
	private final AtomicInteger hits = new AtomicInteger();
	private PredpisService service;

	@BeforeEach
	void start() throws IOException {
		String historia = SlovLexParserTest.fixture("historia-2008-448.html");
		// znenie + veľký "zvyšok" stránky: klient má čítanie ukončiť hneď po InfoTable
		String verzia = SlovLexParserTest.fixture("verzia-2008-448-20260701.html") + "x".repeat(3_000_000);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			hits.incrementAndGet();
			String path = ex.getRequestURI().getPath();
			byte[] body;
			int code = 200;
			if ("/static/SK/ZZ/2008/448/".equals(path)) {
				body = historia.getBytes(StandardCharsets.UTF_8);
			} else if (path.startsWith("/static/SK/ZZ/2008/448/")) {
				body = verzia.getBytes(StandardCharsets.UTF_8);
			} else {
				code = 404;
				body = "not found".getBytes(StandardCharsets.UTF_8);
			}
			ex.sendResponseHeaders(code, body.length);
			try (OutputStream out = ex.getResponseBody()) {
				out.write(body);
			} catch (IOException ignored) {
				// klient zatvoril spojenie po prečítaní hlavičky – očakávané
			}
		});
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		Clock clock = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneId.of("Europe/Bratislava"));
		service = new PredpisService(new SlovLexClient(Duration.ofSeconds(5), "test", 2), base,
				"https://www.slov-lex.sk", Duration.ofHours(24), Duration.ofHours(1), clock);
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void kartaPredpisu() {
		Predpis p = service.get(2008, 448, null).orElseThrow();
		assertEquals("448/2008 Z. z.", p.oznacenie());
		assertTrue(p.nazov().startsWith("Zákon o sociálnych službách"));
		assertEquals("20260701", p.verzia().id());
		assertEquals(LocalDate.of(2026, 9, 30), p.datum());
		assertTrue(p.pdfUrl().endsWith("/pdf/SK/ZZ/2008/448/ZZ_2008_448_20260701.pdf"));
		assertEquals("https://www.slov-lex.sk/ezbierky/pravne-predpisy/SK/ZZ/2008/448/", p.portalUrl());
		assertEquals(6, p.pocetVerzii());
	}

	@Test
	void cache() {
		service.get(2008, 448, null);
		int afterFirst = hits.get();
		service.get(2008, 448, null);
		assertEquals(afterFirst, hits.get());
	}

	@Test
	void neexistujuci() {
		assertFalse(service.get(2008, 9999, null).isPresent());
	}

	@Test
	void neplatny() {
		assertThrows(IllegalArgumentException.class, () -> service.get(1800, 1, null));
		assertThrows(IllegalArgumentException.class, () -> service.get(2008, 0, null));
	}
}
