/*
 * Platforma SOS – synchronizácia rolí Scooldu so skupinami v Keycloaku (bez Scoold Pro).
 *
 * Scoold pri prihlásení (a potom každých keycloak.role_sync_interval_sec sekúnd) zistí cez Keycloak
 * Admin REST API, v ktorých skupinách je používateľ, a podľa toho mu nastaví rolu:
 *   keycloak.admin_group (sos-admin)     -> správca (admins)
 *   keycloak.mod_group   (sos-moderator) -> moderátor (mods)
 *   inak                                 -> bežný používateľ (users)
 * Scoold sa do Keycloaku prihlasuje ako service account klienta (client credentials, rola view-users).
 */
package com.erudika.scoold.utils;

import com.erudika.para.core.User;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.scoold.ScooldConfig;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Načíta skupiny používateľa z Keycloaku a prevedie ich na rolu v Scoolde.
 */
public final class KeycloakRoleSync {

	private static final Logger logger = LoggerFactory.getLogger(KeycloakRoleSync.class);
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final long RETRY_AFTER_ERROR_MS = 60_000L;
	private static final Pattern KC_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
	/** Para ukladá OIDC používateľov ako "oa2:<sub>" (staršie verzie "oauth2:<sub>"). */
	private static final String[] OAUTH2_PREFIXES = {"oa2:", "oauth2:"};
	private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
	private static final Set<String> SKIPPED = ConcurrentHashMap.newKeySet();

	private static volatile String serviceToken;
	private static volatile long serviceTokenExpires;

	private KeycloakRoleSync() { }

	/**
	 * Záznam v cache: rola (môže byť null, ak Keycloak nebol dostupný), kedy bola načítaná a do kedy platí.
	 */
	private record Entry(String group, long loadedAt, long validUntil) { }

	private static ScooldConfig conf() {
		return ScooldUtils.getConfig();
	}

	/**
	 * @return true ak je synchronizácia zapnutá a nakonfigurovaná
	 */
	public static boolean isEnabled() {
		return conf().keycloakRoleSyncEnabled() && !StringUtils.isBlank(conf().keycloakUrl())
				&& !StringUtils.isBlank(conf().keycloakRealm()) && !StringUtils.isBlank(conf().keycloakClientId());
	}

	/**
	 * Vráti rolu používateľa podľa skupín v Keycloaku.
	 * @param u používateľ z Pary
	 * @param loginTime čas prihlásenia (iat z JWT); po novom prihlásení sa skupiny vždy načítajú nanovo
	 * @return {@code admins}, {@code mods}, {@code users}, alebo null ak sa rola nedá zistiť
	 * (synchronizácia vypnutá, používateľ sa neprihlásil cez Keycloak, Keycloak nedostupný)
	 */
	public static String resolveGroup(User u, long loginTime) {
		if (u == null || !isEnabled()) {
			return null;
		}
		String kcId = keycloakUserId(u);
		if (kcId == null) {
			if (SKIPPED.add(u.getId())) {
				logger.info("Keycloak role sync skipped for '{}' (id={}): identifier '{}' is not 'oa2:<keycloak id>' "
						+ "– user did not sign in via Keycloak.", u.getName(), u.getId(), u.getIdentifier());
			}
			return null;
		}
		long now = System.currentTimeMillis();
		Entry cached = CACHE.get(u.getId());
		if (cached != null && now < cached.validUntil() && cached.loadedAt() >= loginTime) {
			return cached.group();
		}
		try {
			List<String> groups = fetchGroups(kcId);
			String group = toScooldGroup(groups);
			long interval = Math.max(10, conf().keycloakRoleSyncIntervalSec()) * 1000L;
			CACHE.put(u.getId(), new Entry(group, now, now + interval));
			logger.info("Keycloak groups of '{}' (keycloak id {}): {} -> {}", u.getName(), kcId, groups, group);
			return group;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return cached == null ? null : cached.group();
		} catch (Exception e) {
			logger.warn("Keycloak role sync failed for user {}: {}", u.getId(), e.getMessage());
			// pri výpadku Keycloaku ponecháme poslednú známu rolu a skúsime to znova o minútu
			String last = cached == null ? null : cached.group();
			CACHE.put(u.getId(), new Entry(last, now, now + RETRY_AFTER_ERROR_MS));
			return last;
		}
	}

	/**
	 * Vymaže cache (napr. po odhlásení), aby sa rola pri ďalšom prihlásení načítala nanovo.
	 * @param userId id používateľa v Pare
	 */
	public static void forget(String userId) {
		if (userId != null) {
			CACHE.remove(userId);
		}
	}

	static String keycloakUserId(User u) {
		String identifier = u.getIdentifier();
		if (identifier == null) {
			return null;
		}
		for (String prefix : OAUTH2_PREFIXES) {
			if (identifier.startsWith(prefix)) {
				String id = identifier.substring(prefix.length());
				return KC_ID.matcher(id).matches() ? id : null;
			}
		}
		return null;
	}

	static String toScooldGroup(List<String> groups) {
		if (containsAny(groups, conf().keycloakAdminGroup())) {
			return User.Groups.ADMINS.toString();
		} else if (containsAny(groups, conf().keycloakModGroup())) {
			return User.Groups.MODS.toString();
		}
		return User.Groups.USERS.toString();
	}

	private static boolean containsAny(List<String> groups, String configured) {
		Set<String> wanted = Arrays.stream(StringUtils.split(StringUtils.trimToEmpty(configured), ','))
				.map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
		return groups.stream().anyMatch(wanted::contains);
	}

	private static String baseUrl() {
		return StringUtils.removeEnd(StringUtils.trimToEmpty(conf().keycloakUrl()), "/");
	}

	@SuppressWarnings("unchecked")
	private static List<String> fetchGroups(String kcId) throws Exception {
		String url = baseUrl() + "/admin/realms/" + enc(conf().keycloakRealm()) + "/users/" + kcId
				+ "/groups?briefRepresentation=true&max=1000";
		HttpResponse<String> res = get(url, token(false));
		if (res.statusCode() == 401) {
			res = get(url, token(true)); // token mohol expirovať
		}
		if (res.statusCode() == 404) {
			return List.of(); // používateľ v Keycloaku už neexistuje
		}
		if (res.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + res.statusCode() + " from Keycloak Admin API");
		}
		List<Map<String, Object>> list = ParaObjectUtils.getJsonReader(List.class).readValue(res.body());
		List<String> groups = new ArrayList<>();
		for (Map<String, Object> g : list) {
			Object name = g.get("name");
			Object path = g.get("path");
			if (name != null) {
				groups.add(name.toString());
			}
			if (path != null) {
				groups.add(StringUtils.removeStart(path.toString(), "/")); // podskupiny: "rodic/dieta"
			}
		}
		return groups;
	}

	private static HttpResponse<String> get(String url, String token) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
				.header("Authorization", "Bearer " + token).header("Accept", "application/json").GET().build();
		return HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	@SuppressWarnings("unchecked")
	private static synchronized String token(boolean forceNew) throws Exception {
		if (!forceNew && serviceToken != null && System.currentTimeMillis() < serviceTokenExpires) {
			return serviceToken;
		}
		String body = "grant_type=client_credentials&client_id=" + enc(conf().keycloakClientId())
				+ "&client_secret=" + enc(conf().keycloakClientSecret());
		HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + "/realms/" + enc(conf().keycloakRealm())
				+ "/protocol/openid-connect/token")).timeout(TIMEOUT)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() != 200) {
			throw new IllegalStateException("cannot get service account token (HTTP " + res.statusCode()
					+ ") – is 'Service accounts' enabled for the client?");
		}
		Map<String, Object> json = ParaObjectUtils.getJsonReader(Map.class).readValue(res.body());
		Object expiresIn = json.getOrDefault("expires_in", 60);
		long ttl = Math.max(10, Long.parseLong(expiresIn.toString()) - 15);
		serviceToken = String.valueOf(json.get("access_token"));
		serviceTokenExpires = System.currentTimeMillis() + ttl * 1000L;
		return serviceToken;
	}

	private static String enc(String s) {
		return URLEncoder.encode(StringUtils.trimToEmpty(s), StandardCharsets.UTF_8);
	}
}
