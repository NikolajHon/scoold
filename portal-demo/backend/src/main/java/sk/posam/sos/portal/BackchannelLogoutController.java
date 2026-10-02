package sk.posam.sos.portal;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenID Connect Back-Channel Logout: Keycloak sem pošle (server → server) podpísaný logout_token,
 * keď sa používateľ odhlási kdekoľvek (napr. vo fóre). Portál overí podpis a zruší príslušnú session.
 */
@RestController
public class BackchannelLogoutController {

	private static final Logger log = LoggerFactory.getLogger(BackchannelLogoutController.class);
	private static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";

	private final SessionRegistry sessions;
	private final NimbusJwtDecoder decoder;

	public BackchannelLogoutController(SessionRegistry sessions,
			@Value("${spring.security.oauth2.client.provider.keycloak.jwk-set-uri}") String jwkSetUri,
			@Value("${app.keycloak-issuer}") String issuer,
			@Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId) {
		this.sessions = sessions;
		this.decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
			// Keycloak dáva logout tokenu iný "typ" v hlavičke než bežnému JWT
			.jwtProcessorCustomizer(p -> p.setJWSTypeVerifier((type, context) -> { }))
			.build();
		OAuth2TokenValidator<Jwt> logoutToken = jwt -> {
			boolean audience = jwt.getAudience() != null && jwt.getAudience().contains(clientId);
			Object events = jwt.getClaims().get("events");
			boolean event = events instanceof Map<?, ?> m && m.containsKey(EVENT);
			boolean subject = jwt.getClaimAsString("sid") != null || jwt.getSubject() != null;
			boolean noNonce = !jwt.hasClaim("nonce");
			return audience && event && subject && noNonce
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_logout_token"));
		};
		this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
			List.of(JwtValidators.createDefaultWithIssuer(issuer), logoutToken)));
	}

	@PostMapping(path = "/backchannel-logout", consumes = "application/x-www-form-urlencoded")
	public ResponseEntity<Void> logout(@RequestParam("logout_token") String token) {
		try {
			Jwt jwt = decoder.decode(token);
			int n = sessions.invalidate(jwt.getClaimAsString("sid"), jwt.getSubject());
			log.info("Back-channel logout z Keycloaku: sid={}, sub={}, zrušených sessions: {}",
				jwt.getClaimAsString("sid"), jwt.getSubject(), n);
			return ResponseEntity.ok().header("Cache-Control", "no-store").build();
		} catch (JwtException e) {
			log.warn("Neplatný logout_token: {}", e.getMessage());
			return ResponseEntity.badRequest().header("Cache-Control", "no-store").build();
		}
	}
}
