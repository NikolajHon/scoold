package sk.posam.sos.slovlex.core;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dátový model služby (JSON odpovede).
 */
public final class Model {

	private Model() {
	}

	/**
	 * Jedna časová verzia (znenie) predpisu z tabuľky histórie na Slov-Lexe.
	 *
	 * @param id           "vyhlasene_znenie" alebo dátum začiatku účinnosti v tvare yyyyMMdd
	 * @param ucinnostOd   začiatok účinnosti (null pri vyhlásenom znení)
	 * @param ucinnostDo   koniec účinnosti (null = neobmedzene / nie je známy)
	 * @param vyhlasene    true, ak ide o vyhlásené (pôvodné) znenie
	 * @param novela       predpis, ktorý túto verziu spôsobil (napr. "406/2025 Z. z."), môže byť null
	 * @param url          odkaz na HTML znenie na static.slov-lex.sk
	 */
	public record Verzia(String id, LocalDate ucinnostOd, LocalDate ucinnostDo, boolean vyhlasene,
			String novela, String url) {

		/**
		 * @param datum dátum
		 * @return true, ak je táto verzia účinná v daný deň
		 */
		public boolean ucinnaV(LocalDate datum) {
			if (vyhlasene || ucinnostOd == null || datum.isBefore(ucinnostOd)) {
				return false;
			}
			return ucinnostDo == null || !datum.isAfter(ucinnostDo);
		}
	}

	/**
	 * Údaje z hlavičky konkrétneho znenia (InfoTable + odkaz na PDF).
	 *
	 * @param oznacenie       napr. "448/2008 Z. z."
	 * @param nazov           úplný názov predpisu
	 * @param typ             Zákon, Vyhláška, Nariadenie vlády...
	 * @param autor           autor (napr. Národná rada Slovenskej republiky)
	 * @param datumSchvalenia dátum schválenia
	 * @param datumVyhlasenia dátum vyhlásenia
	 * @param pravneOblasti   právne oblasti
	 * @param pdfUrl          odkaz na právne záväzné PDF znenie (môže byť null)
	 */
	public record Hlavicka(String oznacenie, String nazov, String typ, String autor, LocalDate datumSchvalenia,
			LocalDate datumVyhlasenia, List<String> pravneOblasti, String pdfUrl) {
	}

	/**
	 * Karta predpisu – odpoveď API.
	 *
	 * @param oznacenie       napr. "448/2008 Z. z."
	 * @param rok             rok
	 * @param cislo           číslo predpisu
	 * @param nazov           úplný názov (z hlavičky zvolenej verzie)
	 * @param typ             typ predpisu
	 * @param autor           autor
	 * @param datumSchvalenia dátum schválenia
	 * @param datumVyhlasenia dátum vyhlásenia
	 * @param pravneOblasti   právne oblasti
	 * @param verzia          verzia účinná k požadovanému dátumu (alebo vyhlásené znenie)
	 * @param pdfUrl          PDF zvolenej verzie
	 * @param portalUrl       odkaz na predpis v portáli www.slov-lex.sk
	 * @param historiaUrl     odkaz na históriu predpisu
	 * @param pocetVerzii     počet všetkých časových verzií
	 * @param datum           dátum, ku ktorému bola verzia zvolená
	 * @param nacitane        kedy boli údaje načítané zo Slov-Lexu
	 * @param zdroj           informácia o zdroji a právnej záväznosti
	 */
	public record Predpis(String oznacenie, int rok, int cislo, String nazov, String typ, String autor,
			LocalDate datumSchvalenia, LocalDate datumVyhlasenia, List<String> pravneOblasti, Verzia verzia,
			String pdfUrl, String portalUrl, String historiaUrl, int pocetVerzii, LocalDate datum,
			Instant nacitane, Map<String, String> zdroj) {
	}
}
