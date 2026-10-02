package sk.posam.sos.portal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Prihlásenie cez Keycloak (OIDC, Authorization Code). Session drží Spring na serveri,
 * Angular volá len /api/** a dostane 401, ak používateľ nie je prihlásený.
 */
@Configuration
public class SecurityConfig {

	@Value("${app.public-url}")
	private String publicUrl;

	@Value("${app.keycloak-logout-url}")
	private String keycloakLogoutUrl;

	@Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
	private String clientId;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessions) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/config", "/backchannel-logout").permitAll()
				.requestMatchers("/api/**").authenticated()
				.anyRequest().permitAll())
			// CSRF pre SPA: token v cookie XSRF-TOKEN, Angular ho posiela v hlavičke X-XSRF-TOKEN
			.csrf(csrf -> csrf
				.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
				// volá ho Keycloak (server → server), chránený podpisom logout tokenu
				.ignoringRequestMatchers("/backchannel-logout"))
			.addFilterAfter((request, response, chain) -> {
				CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
				if (token != null) {
					token.getToken(); // vynúti zapísanie cookie XSRF-TOKEN
				}
				chain.doFilter(request, response);
			}, BasicAuthenticationFilter.class)
			.oauth2Login(login -> login.successHandler((request, response, authentication) -> {
				// zapamätať si väzbu session ↔ relácia v Keycloaku (pre back-channel logout)
				if (authentication.getPrincipal() instanceof OidcUser user) {
					sessions.register(user.getIdToken().getClaimAsString("sid"), user.getSubject(),
						request.getSession());
				}
				response.sendRedirect("/");
			}))
			// pre /api/** vrátiť 401 namiesto presmerovania na Keycloak (rieši to Angular)
			.exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
				new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
				request -> request.getRequestURI().startsWith("/api/")))
			// demo: odhlásenie cez GET /logout (jednoduchý odkaz v Angulari)
			.logout(logout -> logout
				.logoutRequestMatcher(request -> "/logout".equals(request.getRequestURI()))
				.logoutSuccessHandler(keycloakLogout()));
		return http.build();
	}

	/**
	 * Po odhlásení z portálu ukončí aj reláciu v Keycloaku (RP-initiated logout).
	 */
	private LogoutSuccessHandler keycloakLogout() {
		return (request, response, authentication) -> {
			StringBuilder url = new StringBuilder(keycloakLogoutUrl)
				.append("?client_id=").append(enc(clientId))
				.append("&post_logout_redirect_uri=").append(enc(publicUrl + "/"));
			if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
				url.append("&id_token_hint=").append(enc(user.getIdToken().getTokenValue()));
			}
			response.sendRedirect(url.toString());
		};
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
