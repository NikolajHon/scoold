/*
 * Platforma SOS – návrat do partnerskej aplikácie (portálu).
 *
 * Portál pridá k odkazu do fóra parameter sos_back=<adresa v portáli> (napr. konkrétna žiadosť).
 * Fórum si adresu zapamätá pre túto kartu (sessionStorage) a použije ju v odkaze "späť" v hlavičke.
 * Prijme sa len adresa, ktorá začína na scoold.sos_portal_url (ochrana proti open redirectu).
 */
(function () {
	"use strict";
	var KEY = "sos_back";
	var link = document.getElementById("sos-back-link");
	if (!link) { return; }
	var portal = link.getAttribute("data-portal") || "";

	function allowed(url) {
		return portal && (url === portal || url.indexOf(portal + "/") === 0 || url.indexOf(portal + "#") === 0
			|| url.indexOf(portal + "?") === 0);
	}

	try {
		var params = new URLSearchParams(window.location.search);
		var back = params.get(KEY);
		if (back && allowed(back)) {
			sessionStorage.setItem(KEY, back);
		}
		if (params.has(KEY)) {
			// parameter z adresy odstránime, aby sa neukladal do záložiek ani do odpovedí
			params.delete(KEY);
			var q = params.toString();
			history.replaceState(history.state, "", window.location.pathname + (q ? "?" + q : "") + window.location.hash);
		}
		var saved = sessionStorage.getItem(KEY);
		if (saved && allowed(saved)) {
			link.setAttribute("href", saved);
		}
	} catch (e) {
		// bez sessionStorage zostane odkaz na úvodnú stránku portálu
	}
})();
