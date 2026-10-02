package sk.posam.sos.portal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API pre Angular.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

	private final CaseService cases;

	@Value("${app.forum-url}")
	private String forumUrl;

	public ApiController(CaseService cases) {
		this.cases = cases;
	}

	/** Verejná konfigurácia (adresa fóra). */
	@GetMapping("/config")
	public Map<String, Object> config() {
		return Map.of("forumUrl", forumUrl);
	}

	/** Prihlásený používateľ z ID tokenu Keycloaku + čo smie vidieť. */
	@GetMapping("/me")
	public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
		List<String> groups = groups(user);
		Map<String, Object> me = new LinkedHashMap<>();
		me.put("username", user.getPreferredUsername());
		me.put("name", user.getFullName());
		me.put("email", user.getEmail());
		me.put("groups", groups);
		me.put("scope", cases.scope(groups));
		me.put("loginTime", String.valueOf(user.getAuthenticatedAt()));
		return me;
	}

	/** Žiadosti, ktoré používateľ vidí podľa skupiny (obec / poskytovateľ / rezort). */
	@GetMapping("/cases")
	public List<CaseService.Ziadost> list(@AuthenticationPrincipal OidcUser user) {
		return cases.list(groups(user));
	}

	/** Zmena stavu žiadosti (len obec a rezort). */
	@PutMapping("/cases/{id}/status")
	public ResponseEntity<CaseService.Ziadost> status(@AuthenticationPrincipal OidcUser user,
			@PathVariable String id, @RequestBody Map<String, String> body) {
		CaseService.Status stav;
		try {
			stav = CaseService.Status.valueOf(body.getOrDefault("stav", ""));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}
		return cases.changeStatus(id, stav, groups(user))
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.status(403).build());
	}

	private static List<String> groups(OidcUser user) {
		List<String> groups = user.getClaimAsStringList("groups");
		return groups == null ? List.of() : groups;
	}
}
