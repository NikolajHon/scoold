/*
 * Platforma SOS – jednotné odhlásenie (OpenID Connect Back-Channel Logout).
 *
 * POST /sos/backchannel-logout – volá Keycloak (server → server) s podpísaným logout_tokenom, keď sa
 *                                používateľ odhlási v ktorejkoľvek aplikácii Platformy SOS.
 * GET  /sos/session            – ľahká kontrola pre skript sos-session.js (pri návrate na kartu);
 *                                checkAuth pri nej zruší reláciu odhláseného používateľa.
 */
package com.erudika.scoold.controllers;

import com.erudika.scoold.utils.KeycloakBackchannelLogout;
import com.erudika.scoold.utils.ScooldUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/sos")
public class SosSessionController {

	private static final Logger logger = LoggerFactory.getLogger(SosSessionController.class);

	private final ScooldUtils utils;

	public SosSessionController(ScooldUtils utils) {
		this.utils = utils;
	}

	/**
	 * @return 204 ak je používateľ prihlásený, 401 ak nie
	 */
	@ResponseBody
	@GetMapping("/session")
	public ResponseEntity<Void> session(HttpServletRequest req, HttpServletResponse res) {
		if (res.isCommitted()) {
			return null; // checkAuth už odhlásil a presmeroval
		}
		HttpStatus status = utils.isAuthenticated(req) ? HttpStatus.NO_CONTENT : HttpStatus.UNAUTHORIZED;
		return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).build();
	}

	/**
	 * Back-channel logout z Keycloaku.
	 * @param logoutToken podpísaný logout_token
	 * @return 200 ak bol token platný, inak 400
	 */
	@ResponseBody
	@PostMapping(path = "/backchannel-logout", consumes = "application/x-www-form-urlencoded")
	public ResponseEntity<Void> backchannelLogout(@RequestParam(name = "logout_token", required = false) String logoutToken) {
		try {
			String sub = KeycloakBackchannelLogout.handle(logoutToken);
			logger.info("Back-channel logout from Keycloak for user {}.", sub);
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
		} catch (IllegalArgumentException e) {
			logger.warn("Rejected back-channel logout: {}", e.getMessage());
			return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
		}
	}
}
