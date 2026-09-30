/*
 * Platforma SOS – proxy na slovlex-service (karty právnych predpisov zo Slov-Lexu).
 *
 * Prehliadač volá /slovlex/{rok}/{cislo} na doméne Scooldu; Scoold požiadavku prepošle na internú
 * službu slovlex-service (konfigurácia scoold.slovlex_service_url). Služba tak nemusí byť dostupná
 * z internetu a netreba riešiť CORS.
 */
package com.erudika.scoold.controllers;

import com.erudika.scoold.ScooldConfig;
import com.erudika.scoold.utils.ScooldUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Proxy pre karty právnych predpisov.
 */
@Controller
@RequestMapping("/slovlex")
public class SlovLexController {

	private static final Logger logger = LoggerFactory.getLogger(SlovLexController.class);
	private static final ScooldConfig CONF = ScooldUtils.getConfig();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	/**
	 * @param rok rok predpisu
	 * @param cislo číslo predpisu
	 * @param datum voliteľný dátum účinnosti (yyyy-MM-dd)
	 * @return JSON karta predpisu
	 */
	@ResponseBody
	@GetMapping(path = "/{rok}/{cislo}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> get(@PathVariable int rok, @PathVariable int cislo,
			@RequestParam(required = false) String datum) {
		String base = StringUtils.removeEnd(StringUtils.trimToEmpty(CONF.slovlexServiceUrl()), "/");
		if (base.isEmpty()) {
			return ResponseEntity.status(503).body("{\"chyba\":\"slovlex-service nie je nakonfigurovaná\"}");
		}
		if (rok < 1918 || rok > 2100 || cislo < 1 || cislo > 9999) {
			return ResponseEntity.badRequest().body("{\"chyba\":\"Neplatné číslo predpisu\"}");
		}
		String query = "";
		if (!StringUtils.isBlank(datum)) {
			try {
				query = "?datum=" + LocalDate.parse(datum.trim());
			} catch (DateTimeParseException e) {
				return ResponseEntity.badRequest().body("{\"chyba\":\"Neplatný dátum\"}");
			}
		}
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/predpisy/" + rok + "/" + cislo + query))
					.timeout(Duration.ofSeconds(30)).header("Accept", "application/json").GET().build();
			HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			ResponseEntity.BodyBuilder out = ResponseEntity.status(res.statusCode());
			if (res.statusCode() == 200) {
				out.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic());
			}
			return out.contentType(MediaType.APPLICATION_JSON).body(res.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return ResponseEntity.status(502).body("{\"chyba\":\"Prerušené\"}");
		} catch (Exception e) {
			logger.warn("slovlex-service nedostupná: {}", e.getMessage());
			return ResponseEntity.status(502).body("{\"chyba\":\"slovlex-service je nedostupná\"}");
		}
	}
}
