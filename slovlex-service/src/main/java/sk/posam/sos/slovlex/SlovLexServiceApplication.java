package sk.posam.sos.slovlex;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import sk.posam.sos.slovlex.core.PredpisService;
import sk.posam.sos.slovlex.core.SlovLexClient;

/**
 * slovlex-service: karty právnych predpisov zo Slov-Lexu pre Platformu SOS.
 */
@SpringBootApplication
@EnableConfigurationProperties(SlovLexServiceApplication.SlovLexProperties.class)
public class SlovLexServiceApplication {

	/**
	 * @param args argumenty
	 */
	public static void main(String[] args) {
		SpringApplication.run(SlovLexServiceApplication.class, args);
	}

	/**
	 * @param p konfigurácia
	 * @return služba
	 */
	@Bean
	public PredpisService predpisService(SlovLexProperties p) {
		SlovLexClient client = new SlovLexClient(p.timeout(), p.userAgent(), p.maxConcurrent());
		return new PredpisService(client, p.baseUrl(), p.portalUrl(), p.cacheTtl(), p.notFoundTtl(),
				Clock.system(ZoneId.of(p.zone())));
	}

	/**
	 * Konfigurácia (application.yml, prefix "slovlex", dá sa prepísať env premennými SLOVLEX_...).
	 *
	 * @param baseUrl       statická verzia portálu
	 * @param portalUrl     hlavný portál (odkazy pre používateľov)
	 * @param cacheTtl      platnosť cache
	 * @param notFoundTtl   platnosť cache pre neexistujúce predpisy
	 * @param timeout       timeout požiadavky
	 * @param maxConcurrent max. súbežných požiadaviek na Slov-Lex
	 * @param userAgent     User-Agent
	 * @param zone          časové pásmo pre výber účinného znenia
	 */
	@ConfigurationProperties(prefix = "slovlex")
	public record SlovLexProperties(String baseUrl, String portalUrl, Duration cacheTtl, Duration notFoundTtl,
			Duration timeout, int maxConcurrent, String userAgent, String zone) {
	}
}
