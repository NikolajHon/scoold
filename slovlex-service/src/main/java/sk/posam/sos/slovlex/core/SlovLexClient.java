package sk.posam.sos.slovlex.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * HTTP klient pre static.slov-lex.sk.
 *
 * Zásady "slušného" scrapovania: vlastný User-Agent s kontaktom, obmedzený počet súbežných požiadaviek,
 * čítanie len potrebnej časti stránky (znenia majú aj niekoľko MB) a cache v {@link PredpisService}.
 */
public class SlovLexClient {

	/** Maximálny počet znakov, ktoré sa prečítajú z jednej stránky. */
	static final int MAX_CHARS = 6_000_000;

	private final HttpClient http;
	private final Duration timeout;
	private final String userAgent;
	private final Semaphore permits;

	/**
	 * @param timeout       timeout jednej požiadavky
	 * @param userAgent     User-Agent (uveďte kontakt na prevádzkovateľa)
	 * @param maxConcurrent max. počet súbežných požiadaviek na Slov-Lex
	 */
	public SlovLexClient(Duration timeout, String userAgent, int maxConcurrent) {
		this.timeout = timeout;
		this.userAgent = userAgent;
		this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
		this.http = HttpClient.newBuilder()
				.connectTimeout(timeout)
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	/**
	 * Stiahne celú stránku (použité pre históriu predpisu, ~20 kB).
	 *
	 * @param url URL
	 * @return HTML alebo prázdne, ak stránka neexistuje (404)
	 * @throws SlovLexException pri chybe siete alebo neočakávanej odpovedi
	 */
	public Optional<String> get(String url) {
		return read(url, s -> false);
	}

	/**
	 * Číta stránku postupne a skončí, keď {@code stop} vráti true (napr. po nájdení InfoTable).
	 *
	 * @param url  URL
	 * @param stop podmienka ukončenia čítania nad doteraz prečítaným obsahom
	 * @return prečítaná časť HTML alebo prázdne pri 404
	 * @throws SlovLexException pri chybe siete alebo neočakávanej odpovedi
	 */
	public Optional<String> read(String url, Predicate<CharSequence> stop) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(timeout)
				.header("User-Agent", userAgent)
				.header("Accept", "text/html")
				.GET().build();
		boolean acquired = false;
		try {
			acquired = permits.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!acquired) {
				throw new SlovLexException("Príliš veľa súbežných požiadaviek na Slov-Lex", null);
			}
			HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = res.body()) {
				if (res.statusCode() == 404) {
					return Optional.empty();
				}
				if (res.statusCode() != 200) {
					throw new SlovLexException("Slov-Lex vrátil HTTP " + res.statusCode() + " pre " + url, null);
				}
				return Optional.of(readUntil(in, stop));
			}
		} catch (IOException e) {
			throw new SlovLexException("Chyba pri čítaní " + url + ": " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SlovLexException("Prerušené čítanie " + url, e);
		} finally {
			if (acquired) {
				permits.release();
			}
		}
	}

	static String readUntil(InputStream in, Predicate<CharSequence> stop) throws IOException {
		StringBuilder sb = new StringBuilder(64 * 1024);
		char[] buf = new char[16 * 1024];
		Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
		int n;
		while ((n = r.read(buf)) > 0) {
			sb.append(buf, 0, n);
			if (sb.length() > MAX_CHARS || stop.test(sb)) {
				break;
			}
		}
		return sb.toString();
	}

	/** Chyba komunikácie so Slov-Lexom. */
	public static class SlovLexException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		/**
		 * @param message správa
		 * @param cause   príčina
		 */
		public SlovLexException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
