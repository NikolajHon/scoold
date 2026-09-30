/*
 * Platforma SOS – odkazy na právne predpisy zo Slov-Lexu.
 *
 * 1) V texte príspevkov a komentárov nájde citácie predpisov ("448/2008 Z. z.", "č. 455/1991 Zb.",
 *    "§ 49 zákona č. 448/2008 Z. z.") a zmení ich na odkazy na portál Slov-Lex (funguje aj bez služby).
 * 2) Pri prechode myšou / fokuse klávesnicou zobrazí kartu predpisu (názov, účinné znenie, PDF)
 *    z CONTEXT_PATH + /slovlex/{rok}/{cislo} (proxy v Scoolde na slovlex-service).
 */
(function () {
	"use strict";

	var PORTAL = "https://www.slov-lex.sk/ezbierky/pravne-predpisy/SK/ZZ/";
	var SCOPE = ".postbody, .comment-text, .sos-richtext, .idsk-card__description";
	// [§ 49 [ods. 2] [písm. a)]] [zákona|vyhlášky|nariadenia vlády|...] [č.] 448/2008 [Z. z.|Zb.]
	// Bez "Z. z."/"Zb." sa citácia uzná len so slovom "zákon(a)/vyhláška/..." alebo "č." (napr. "zákona č. 448/2008"),
	// aby sa neprelinkovali náhodné čísla typu "1/2024".
	var CITATION = new RegExp(
		"(?:(§{1,2})\\s*(\\d+[a-z]?)(?:\\s+ods\\.\\s*\\d+[a-z]?)?(?:\\s+písm\\.\\s*[a-z]\\))?\\s+)?" +
		"(?:((?:zákon(?:a|om|e|u)?|vyhlášk(?:a|y|e|ou|u)|nariadeni(?:e|a|u|m)(?:\\s+vlády(?:\\s+SR)?)?|opatreni(?:e|a|u)|výnos(?:u|om)?))\\s+)?" +
		"(č\\.\\s*)?(?<![\\d/])(\\d{1,4})\\/(\\d{4})(?![\\d/])(?:\\s*(Z\\.\\s?z\\.|Zb\\.))?", "gi");
	var cache = {};
	var card = null;
	var cardFor = null;
	var hideTimer = null;

	function lang(key, fallback) {
		var l = window.SLOVLEX_LANG || {};
		return l[key] || fallback;
	}

	function linkify(root) {
		var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
			acceptNode: function (n) {
				if (!n.nodeValue || n.nodeValue.indexOf("/") < 0) { return NodeFilter.FILTER_REJECT; }
				var p = n.parentNode;
				while (p && p !== root) {
					if (/^(A|CODE|PRE|SCRIPT|STYLE|TEXTAREA)$/.test(p.nodeName)) { return NodeFilter.FILTER_REJECT; }
					p = p.parentNode;
				}
				return NodeFilter.FILTER_ACCEPT;
			}
		});
		var nodes = [];
		while (walker.nextNode()) { nodes.push(walker.currentNode); }
		nodes.forEach(function (node) {
			var text = node.nodeValue, last = 0, m, frag = null;
			CITATION.lastIndex = 0;
			while ((m = CITATION.exec(text)) !== null) {
				var rok = parseInt(m[6], 10), cislo = parseInt(m[5], 10);
				if (rok < 1918 || rok > 2100 || cislo < 1) { continue; }
				if (!m[7] && !m[3] && !m[4]) { continue; } // bez zbierky aj bez "zákona"/"č." – nie je to citácia
				frag = frag || document.createDocumentFragment();
				frag.appendChild(document.createTextNode(text.slice(last, m.index)));
				var a = document.createElement("a");
				a.className = "sos-law govuk-link";
				a.href = PORTAL + rok + "/" + cislo + "/";
				a.target = "_blank";
				a.rel = "noopener";
				a.setAttribute("data-law", rok + "/" + cislo);
				if (m[2]) { a.setAttribute("data-paragraf", m[2]); }
				a.textContent = m[0];
				a.setAttribute("aria-describedby", "sos-law-card");
				frag.appendChild(a);
				last = m.index + m[0].length;
			}
			if (frag) {
				frag.appendChild(document.createTextNode(text.slice(last)));
				node.parentNode.replaceChild(frag, node);
			}
		});
	}

	function fetchLaw(key) {
		if (!cache[key]) {
			var base = (window.CONTEXT_PATH || "") + "/slovlex/" + key;
			cache[key] = fetch(base, { headers: { "Accept": "application/json" }, credentials: "same-origin" })
				.then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); });
			cache[key].catch(function () { setTimeout(function () { delete cache[key]; }, 60000); });
		}
		return cache[key];
	}

	function fmt(d) {
		if (!d) { return ""; }
		var p = d.split("-");
		return p.length === 3 ? parseInt(p[2], 10) + ". " + parseInt(p[1], 10) + ". " + p[0] : d;
	}

	function el(tag, cls, text) {
		var e = document.createElement(tag);
		if (cls) { e.className = cls; }
		if (text) { e.textContent = text; }
		return e;
	}

	function link(href, text) {
		var a = el("a", "govuk-link", text);
		a.href = href;
		a.target = "_blank";
		a.rel = "noopener";
		return a;
	}

	function ensureCard() {
		if (!card) {
			card = el("div", "sos-law-card");
			card.id = "sos-law-card";
			card.setAttribute("role", "tooltip");
			card.hidden = true;
			card.addEventListener("mouseenter", function () { clearTimeout(hideTimer); });
			card.addEventListener("mouseleave", scheduleHide);
			document.body.appendChild(card);
		}
		return card;
	}

	function position(a) {
		var r = a.getBoundingClientRect();
		var c = ensureCard();
		var w = Math.min(420, window.innerWidth - 32);
		c.style.width = w + "px";
		var left = Math.min(Math.max(16, r.left + window.scrollX), window.scrollX + window.innerWidth - w - 16);
		c.style.left = left + "px";
		c.style.top = (r.bottom + window.scrollY + 8) + "px";
	}

	function render(a, data) {
		var c = ensureCard();
		c.textContent = "";
		c.appendChild(el("p", "sos-law-card__caption", (data.typ || lang("law", "Právny predpis")) + " " + data.oznacenie));
		c.appendChild(el("p", "sos-law-card__title", data.nazov || data.oznacenie));
		if (data.verzia) {
			var v = data.verzia;
			var txt = v.vyhlasene ? lang("published", "Vyhlásené znenie") :
				lang("effective", "Znenie účinné od") + " " + fmt(v.ucinnostOd) + (v.ucinnostDo ? " " + lang("to", "do") + " " + fmt(v.ucinnostDo) : "");
			c.appendChild(el("p", "sos-law-card__meta", txt + (v.novela ? " (" + lang("amended", "posledná novela") + " " + v.novela + ")" : "")));
		}
		var ul = el("ul", "sos-law-card__links");
		var par = a.getAttribute("data-paragraf");
		if (par && data.verzia && data.verzia.url) {
			var li0 = el("li");
			li0.appendChild(link(data.verzia.url + "#paragraf-" + par, "§ " + par + " " + lang("incurrent", "v účinnom znení")));
			ul.appendChild(li0);
		}
		var li1 = el("li");
		li1.appendChild(link(data.portalUrl || a.href, lang("open", "Otvoriť na Slov-Lex")));
		ul.appendChild(li1);
		if (data.pdfUrl) {
			var li2 = el("li");
			li2.appendChild(link(data.pdfUrl, lang("pdf", "PDF (právne záväzné znenie)")));
			ul.appendChild(li2);
		}
		c.appendChild(ul);
		c.appendChild(el("p", "sos-law-card__source", lang("source", "Zdroj: Slov-Lex, Ministerstvo spravodlivosti SR")));
	}

	function show(a) {
		clearTimeout(hideTimer);
		cardFor = a;
		var c = ensureCard();
		c.textContent = "";
		c.appendChild(el("p", "sos-law-card__meta", lang("loading", "Načítavam údaje zo Slov-Lexu…")));
		position(a);
		c.hidden = false;
		fetchLaw(a.getAttribute("data-law")).then(function (data) {
			if (cardFor === a) { render(a, data); position(a); }
		}, function () {
			if (cardFor === a) {
				c.textContent = "";
				c.appendChild(el("p", "sos-law-card__meta", lang("unavailable", "Údaje zo Slov-Lexu sú momentálne nedostupné.")));
				var p = el("p");
				p.appendChild(link(a.href, lang("open", "Otvoriť na Slov-Lex")));
				c.appendChild(p);
			}
		});
	}

	function scheduleHide() {
		clearTimeout(hideTimer);
		hideTimer = setTimeout(function () { if (card) { card.hidden = true; } cardFor = null; }, 250);
	}

	function bind() {
		document.addEventListener("mouseover", function (e) {
			var a = e.target.closest && e.target.closest("a.sos-law");
			if (a && a !== cardFor) { show(a); }
		});
		document.addEventListener("mouseout", function (e) {
			var a = e.target.closest && e.target.closest("a.sos-law");
			if (a) { scheduleHide(); }
		});
		document.addEventListener("focusin", function (e) {
			var a = e.target.closest && e.target.closest("a.sos-law");
			if (a) { show(a); } else if (card && !card.contains(e.target)) { scheduleHide(); }
		});
		document.addEventListener("keydown", function (e) {
			if (e.key === "Escape" && card && !card.hidden) { card.hidden = true; cardFor = null; }
		});
	}

	function scan(root) {
		(root.matches && root.matches(SCOPE) ? [root] : []).concat(Array.prototype.slice.call(root.querySelectorAll ? root.querySelectorAll(SCOPE) : []))
			.forEach(linkify);
	}

	function init() {
		scan(document);
		bind();
		// príspevky a komentáre pridané cez AJAX
		var main = document.getElementById("pageMain") || document.body;
		new MutationObserver(function (muts) {
			muts.forEach(function (m) {
				Array.prototype.forEach.call(m.addedNodes, function (n) {
					if (n.nodeType === 1 && !(n.classList && n.classList.contains("sos-law"))) { scan(n); }
				});
			});
		}).observe(main, { childList: true, subtree: true });
	}

	if (document.readyState === "loading") {
		document.addEventListener("DOMContentLoaded", init);
	} else {
		init();
	}
})();
