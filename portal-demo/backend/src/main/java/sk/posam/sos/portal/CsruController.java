package sk.posam.sos.portal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Referenčné údaje z IS CSRÚ k žiadosti: Evidencia ŤZP, Peňažný príspevok na opatrovanie,
 * Register sociálnych služieb. Prístup len k žiadostiam, ktoré používateľ vidí.
 */
@RestController
@RequestMapping("/api")
public class CsruController {

	private final CaseService cases;
	private final CsruClient csru;

	public CsruController(CaseService cases, CsruClient csru) {
		this.cases = cases;
		this.csru = csru;
	}

	@GetMapping("/cases/{id}/csru")
	public ResponseEntity<Map<String, Object>> forCase(@AuthenticationPrincipal OidcUser user, @PathVariable String id) {
		List<String> groups = groups(user);
		if (cases.find(id, groups).isEmpty()) {
			return ResponseEntity.status(403).build();
		}
		return cases.csruKluce(id).map(k -> {
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("ziadost", id);
			out.put("rcMaskovane", k.rc().replaceFirst("/\\d+$", "/****"));
			out.put("ico", k.ico());
			out.put("tzp", csru.tzp(k.rc()));
			out.put("ppno", csru.ppnoPreOpatrovanu(k.rc()));
			out.put("rss", csru.rss(k.ico()));
			out.put("zdroj", "IS CSRÚ – mock (fiktívne údaje)");
			return ResponseEntity.ok(out);
		}).orElseGet(() -> ResponseEntity.notFound().build());
	}

	/** Zmenené subjekty v Evidencii ŤZP a PPnO za 14 dní (len pre rezort / správcu). */
	@GetMapping("/csru/zmeny")
	public ResponseEntity<Map<String, Object>> zmeny(@AuthenticationPrincipal OidcUser user) {
		if (!cases.scope(groups(user)).vsetko()) {
			return ResponseEntity.status(403).build();
		}
		List<CsruClient.Odpoved<List<CsruClient.Zmena>>> odpovede = List.of(csru.zmeny("TZP"), csru.zmeny("PPnO"));
		List<CsruClient.Zmena> vsetky = new ArrayList<>();
		List<String> chyby = new ArrayList<>();
		for (var o : odpovede) {
			if (o.data() != null) {
				vsetky.addAll(o.data());
			}
			if (o.chyba() != null) {
				chyby.add(o.sluzba() + ": " + o.chyba());
			}
		}
		vsetky.sort(Comparator.comparing(CsruClient.Zmena::cas).reversed());
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("zmeny", vsetky);
		out.put("chyby", chyby);
		return ResponseEntity.ok(out);
	}

	private static List<String> groups(OidcUser user) {
		List<String> groups = user.getClaimAsStringList("groups");
		return groups == null ? List.of() : groups;
	}
}
