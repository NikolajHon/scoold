package sk.posam.sos.slovlex.web;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.posam.sos.slovlex.core.Model.Predpis;
import sk.posam.sos.slovlex.core.PredpisService;
import sk.posam.sos.slovlex.core.SlovLexClient.SlovLexException;

/**
 * REST API:
 * <pre>
 * GET  /api/predpisy/{rok}/{cislo}?datum=yyyy-MM-dd   karta predpisu (znenie účinné k dátumu, predvolene dnes)
 * POST /api/cache/clear                               vymazanie cache
 * </pre>
 * Služba je určená na volanie zo Scooldu (interná sieť), nie priamo z prehliadača.
 */
@RestController
@RequestMapping("/api")
public class PredpisController {

	private static final Logger LOG = LoggerFactory.getLogger(PredpisController.class);

	private final PredpisService service;

	/**
	 * @param service služba
	 */
	public PredpisController(PredpisService service) {
		this.service = service;
	}

	/**
	 * @param rok   rok
	 * @param cislo číslo
	 * @param datum dátum účinnosti (voliteľný)
	 * @return karta predpisu alebo 404
	 */
	@GetMapping("/predpisy/{rok}/{cislo}")
	public ResponseEntity<Predpis> get(@PathVariable int rok, @PathVariable int cislo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum) {
		return service.get(rok, cislo, datum)
				.map(p -> ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS)).body(p))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	/**
	 * @return 204
	 */
	@PostMapping("/cache/clear")
	public ResponseEntity<Void> clearCache() {
		service.clearCache();
		return ResponseEntity.noContent().build();
	}

	/**
	 * @param e chyba
	 * @return 400
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(Map.of("chyba", e.getMessage()));
	}

	/**
	 * @param e chyba Slov-Lexu
	 * @return 502
	 */
	@ExceptionHandler(SlovLexException.class)
	public ResponseEntity<Map<String, String>> upstream(SlovLexException e) {
		LOG.warn("Slov-Lex nedostupný: {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("chyba", "Slov-Lex je momentálne nedostupný."));
	}
}
