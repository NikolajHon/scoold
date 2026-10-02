package sk.posam.sos.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Platforma SOS – demo portál (Spring Boot + Angular) so SSO s fórom cez Keycloak.
 */
@SpringBootApplication
public class PortalApplication {

	public static void main(String[] args) {
		SpringApplication.run(PortalApplication.class, args);
	}
}
