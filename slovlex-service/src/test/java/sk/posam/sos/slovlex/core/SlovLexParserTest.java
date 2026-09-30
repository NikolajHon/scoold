package sk.posam.sos.slovlex.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import sk.posam.sos.slovlex.core.Model.Hlavicka;
import sk.posam.sos.slovlex.core.Model.Verzia;

/**
 * Testy parsera nad skutočnými (skrátenými) stránkami Slov-Lexu. Ak Slov-Lex zmení šablónu,
 * stiahnite nové fixtures a upravte {@link SlovLexParser}.
 */
class SlovLexParserTest {

	private static final String BASE = "https://static.slov-lex.sk";

	static String fixture(String name) throws IOException {
		try (InputStream in = SlovLexParserTest.class.getResourceAsStream("/fixtures/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void historia() throws IOException {
		String html = fixture("historia-2008-448.html");
		assertEquals("448/2008 Z. z.", SlovLexParser.parseOznacenie(html));
		List<Verzia> v = SlovLexParser.parseHistoria(html, BASE);
		assertEquals(6, v.size());

		Verzia vyhlasene = v.get(0);
		assertTrue(vyhlasene.vyhlasene());
		assertEquals("vyhlasene_znenie", vyhlasene.id());
		assertNull(vyhlasene.ucinnostOd());

		Verzia druha = v.get(2);
		assertEquals("20091101", druha.id());
		assertEquals(LocalDate.of(2009, 11, 1), druha.ucinnostOd());
		assertEquals(LocalDate.of(2010, 8, 2), druha.ucinnostDo());
		assertEquals("317/2009 Z. z.", druha.novela());
		assertEquals(BASE + "/static/SK/ZZ/2008/448/20091101.html", druha.url());

		Verzia posledna = v.get(5);
		assertNull(posledna.ucinnostDo());
	}

	@Test
	void vyberVerzie() throws IOException {
		List<Verzia> v = SlovLexParser.parseHistoria(fixture("historia-2008-448.html"), BASE);
		assertEquals("20260701", PredpisService.vyberVerziu(v, LocalDate.of(2026, 9, 30)).id());
		assertEquals("20260701", PredpisService.vyberVerziu(v, LocalDate.of(2026, 12, 30)).id());
		assertEquals("20261231", PredpisService.vyberVerziu(v, LocalDate.of(2030, 1, 1)).id());
		assertEquals("vyhlasene_znenie", PredpisService.vyberVerziu(v, LocalDate.of(2008, 12, 1)).id());
		assertNull(PredpisService.vyberVerziu(List.of(), LocalDate.now()));
	}

	@Test
	void hlavicka() throws IOException {
		String html = fixture("verzia-2008-448-20260701.html");
		assertTrue(SlovLexParser.hasCompleteInfoTable(html));
		Hlavicka h = SlovLexParser.parseHlavicka(html, BASE);
		assertNotNull(h);
		assertEquals("448/2008 Z. z.", h.oznacenie());
		assertTrue(h.nazov().startsWith("Zákon o sociálnych službách"));
		assertEquals("Zákon", h.typ());
		assertEquals("Národná rada Slovenskej republiky", h.autor());
		assertEquals(LocalDate.of(2008, 10, 30), h.datumSchvalenia());
		assertEquals(LocalDate.of(2008, 11, 20), h.datumVyhlasenia());
		assertEquals(List.of("Živnostenské podnikanie", "Právo sociálneho zabezpečenia"), h.pravneOblasti());
		assertEquals(BASE + "/pdf/SK/ZZ/2008/448/ZZ_2008_448_20260701.pdf", h.pdfUrl());
	}

	@Test
	void chybajucaTabulka() {
		assertFalse(SlovLexParser.hasCompleteInfoTable("<html><table id=\"InfoTable\"><tr>"));
		assertNull(SlovLexParser.parseHlavicka("<html></html>", BASE));
		assertTrue(SlovLexParser.parseHistoria("<html></html>", BASE).isEmpty());
	}

	@Test
	void entity() {
		assertEquals("406/2025 Z. z.", SlovLexParser.text("406/2025&nbsp;Z.&nbsp;z."));
		assertEquals("a & b \"c\" é", SlovLexParser.text("<b>a</b> &amp; b &quot;c&quot; &#233;"));
	}
}
