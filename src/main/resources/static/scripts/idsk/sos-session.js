/*
 * Platforma SOS – jednotné odhlásenie vo fóre.
 * Keycloak oznámi odhlásenie serveru (back-channel logout). Aby to používateľ videl hneď, keď sa
 * vráti na kartu fóra (bez pravidelného dopytovania), skript sa pri návrate opýta /sos/session
 * a pri odhlásení prejde na prihlasovaciu stránku.
 */
(function () {
	"use strict";
	var base = (typeof CONTEXT_PATH === "string") ? CONTEXT_PATH : "";
	var busy = false;

	function check() {
		if (busy || !window.fetch) { return; }
		busy = true;
		fetch(base + "/sos/session", { credentials: "same-origin", redirect: "manual", cache: "no-store" })
			.then(function (r) {
				if (r.type === "opaqueredirect" || r.status === 401) {
					window.location.href = base + "/signin?code=5&success=true";
				}
			})
			.catch(function () { /* sieť – skúsime nabudúce */ })
			.then(function () { busy = false; });
	}

	document.addEventListener("visibilitychange", function () {
		if (document.visibilityState === "visible") { check(); }
	});
})();
