/*
 * Platforma SOS – jednotné odhlásenie cez OpenID Connect Back-Channel Logout.
 *
 * Keycloak pri odhlásení (v ktorejkoľvek aplikácii Platformy SOS) pošle na
 * POST /sos/backchannel-logout podpísaný logout_token (server → server). Tu ho overíme
 * (podpis cez JWKS Keycloaku, iss, aud, events, bez nonce) a zapamätáme si čas odhlásenia
 * používateľa (claim "sub"). Relácia Scooldu je JWT v cookie (nedá sa zrušiť na serveri),
 * preto pri ďalšej požiadavke ScooldUtils.checkAuth porovná čas prihlásenia (iat JWT Scooldu)
 * s časom odhlásenia a staršie prihlásenie zruší. Žiadne pravidelné dotazy do Keycloaku.
 *
 * Pozn.: záznamy sú v pamäti jednej inštancie Scooldu. Pri viacerých inštanciách treba
 * zdieľané úložisko (napr. cache Pary / Redis).
 */
package com.erudika.scoold.utils;

import com.erudika.para.core.User;
import com.erudika.scoold.ScooldConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Overenie logout_tokenu z Keycloaku a evidencia odhlásených používateľov.
 */
public final class KeycloakBackchannelLogout {

	private static final Logger logger = LoggerFactory.getLogger(KeycloakBackchannelLogout.class);
	private static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final long MAX_CLOCK_SKEW_MS = 5 * 60_000L;

	/** Keycloak user id (sub) → čas odhlásenia v ms. */
	private static final Map<String, Long> LOGGED_OUT = new ConcurrentHashMap<>();

	private static volatile JWKSet jwks;
	private static volatile long jwksLoadedAt;

	private KeycloakBackchannelLogout() { }

	private static ScooldConfig conf() {
		return ScooldUtils.getConfig();
	}

	/**
	 * Overí logout_token a zaznamená odhlásenie.
	 * @param logoutToken hodnota parametra logout_token
	 * @return Keycloak id odhláseného používateľa
	 * @throws IllegalArgumentException ak token nie je platný
	 */
	public static String handle(String logoutToken) {
		if (StringUtils.isBlank(logoutToken)) {
			throw new IllegalArgumentException("missing logout_token");
		}
		try {
			SignedJWT jwt = SignedJWT.parse(logoutToken);
			if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
				throw new IllegalArgumentException("unsupported alg " + jwt.getHeader().getAlgorithm());
			}
			RSAKey key = findKey(jwt.getHeader().getKeyID());
			JWSVerifier verifier = new RSASSAVerifier(key);
			if (!jwt.verify(verifier)) {
				throw new IllegalArgumentException("invalid signature");
			}
			JWTClaimsSet c = jwt.getJWTClaimsSet();
			String issuer = issuer();
			if (!issuer.equals(c.getIssuer())) {
				throw new IllegalArgumentException("unexpected iss " + c.getIssuer());
			}
			List<String> aud = c.getAudience();
			if (aud == null || !aud.contains(conf().keycloakClientId())) {
				throw new IllegalArgumentException("unexpected aud " + aud);
			}
			Object events = c.getClaim("events");
			if (!(events instanceof Map<?, ?> m) || !m.containsKey(EVENT)) {
				throw new IllegalArgumentException("missing backchannel-logout event");
			}
			if (c.getClaim("nonce") != null) {
				throw new IllegalArgumentException("nonce not allowed");
			}
			long now = System.currentTimeMillis();
			Date iat = c.getIssueTime();
			if (iat == null || Math.abs(now - iat.getTime()) > MAX_CLOCK_SKEW_MS) {
				throw new IllegalArgumentException("iat missing or too old");
			}
			Date exp = c.getExpirationTime();
			if (exp != null && exp.getTime() + MAX_CLOCK_SKEW_MS < now) {
				throw new IllegalArgumentException("token expired");
			}
			String sub = c.getSubject();
			if (StringUtils.isBlank(sub)) {
				throw new IllegalArgumentException("missing sub");
			}
			LOGGED_OUT.put(sub, now);
			cleanup(now);
			return sub;
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("invalid logout_token: " + e.getMessage(), e);
		}
	}

	/**
	 * Odhlásil sa používateľ v Keycloaku po tom, čo sa prihlásil do Scooldu?
	 * @param u používateľ z Pary (môže byť null)
	 * @param sessionClaims claims JWT relácie Scooldu (iat = čas prihlásenia)
	 * @return true ak treba reláciu Scooldu zrušiť
	 */
	public static boolean isLoggedOut(User u, JWTClaimsSet sessionClaims) {
		if (u == null || LOGGED_OUT.isEmpty()) {
			return false;
		}
		String kcId = KeycloakRoleSync.keycloakUserId(u);
		Long loggedOutAt = kcId == null ? null : LOGGED_OUT.get(kcId);
		if (loggedOutAt == null) {
			return false;
		}
		Date iat = sessionClaims == null ? null : sessionClaims.getIssueTime();
		return iat == null || iat.getTime() <= loggedOutAt;
	}

	/** Issuer tokenov Keycloaku: scoold.keycloak.issuer, inak odvodený z adresy autorizácie OAuth2. */
	static String issuer() {
		String configured = conf().keycloakIssuer();
		if (!StringUtils.isBlank(configured)) {
			return StringUtils.removeEnd(configured.trim(), "/");
		}
		return StringUtils.substringBefore(conf().oauthAuthorizationUrl(""), "/protocol/openid-connect");
	}

	private static RSAKey findKey(String kid) throws Exception {
		JWKSet set = jwks(false);
		JWK key = kid == null ? null : set.getKeyByKeyId(kid);
		if (key == null) {
			set = jwks(true); // Keycloak mohol rotovať kľúče
			key = kid == null ? null : set.getKeyByKeyId(kid);
		}
		if (!(key instanceof RSAKey rsa)) {
			throw new IllegalArgumentException("unknown signing key " + kid);
		}
		return rsa;
	}

	private static synchronized JWKSet jwks(boolean reload) throws Exception {
		long now = System.currentTimeMillis();
		if (!reload && jwks != null && now - jwksLoadedAt < 3_600_000L) {
			return jwks;
		}
		if (reload && jwks != null && now - jwksLoadedAt < 10_000L) {
			return jwks; // ochrana pred zahltením Keycloaku
		}
		String url = StringUtils.removeEnd(StringUtils.trimToEmpty(conf().keycloakUrl()), "/") + "/realms/"
				+ URLEncoder.encode(conf().keycloakRealm(), StandardCharsets.UTF_8) + "/protocol/openid-connect/certs";
		HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
				.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() != 200) {
			throw new IllegalStateException("JWKS HTTP " + res.statusCode());
		}
		jwks = JWKSet.parse(res.body());
		jwksLoadedAt = now;
		return jwks;
	}

	/** Staršie záznamy, než je platnosť relácie Scooldu, už netreba. */
	private static void cleanup(long now) {
		long maxAge = Math.max(3600, conf().sessionTimeoutSec()) * 1000L;
		LOGGED_OUT.values().removeIf(t -> now - t > maxAge);
	}
}
