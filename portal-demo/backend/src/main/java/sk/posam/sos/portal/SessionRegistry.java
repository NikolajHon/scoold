package sk.posam.sos.portal;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Pamätá si, ktorá HTTP session patrí ku ktorej relácii v Keycloaku (claim "sid" z ID tokenu)
 * a ku ktorému používateľovi ("sub"). Keycloak pri odhlásení pošle back-channel logout s "sid"/"sub"
 * a my podľa toho zrušíme session v portáli.
 */
@Component
public class SessionRegistry implements HttpSessionListener {

	private final Map<String, Set<HttpSession>> bySid = new ConcurrentHashMap<>();
	private final Map<String, Set<HttpSession>> bySub = new ConcurrentHashMap<>();

	public void register(String sid, String sub, HttpSession session) {
		if (sid != null) {
			bySid.computeIfAbsent(sid, k -> ConcurrentHashMap.newKeySet()).add(session);
		}
		if (sub != null) {
			bySub.computeIfAbsent(sub, k -> ConcurrentHashMap.newKeySet()).add(session);
		}
	}

	/** Zruší sessions podľa sid (ak je), inak podľa sub. Vráti počet zrušených sessions. */
	public int invalidate(String sid, String sub) {
		Set<HttpSession> sessions = sid != null ? bySid.remove(sid) : (sub != null ? bySub.remove(sub) : null);
		if (sessions == null) {
			return 0;
		}
		int count = 0;
		for (HttpSession s : sessions) {
			try {
				s.invalidate();
				count++;
			} catch (IllegalStateException alreadyInvalid) {
				// session už neplatí
			}
		}
		return count;
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		HttpSession s = event.getSession();
		bySid.values().forEach(set -> set.remove(s));
		bySub.values().forEach(set -> set.remove(s));
		bySid.values().removeIf(Set::isEmpty);
		bySub.values().removeIf(Set::isEmpty);
	}
}
