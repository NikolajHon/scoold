package sk.posam.sos.csru;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Fiktívne demo údaje pre tri objekty evidencie MPSVaR z IS CSRÚ (príloha č. 2 integračného manuálu):
 * Evidencia ŤZP, Peňažný príspevok na opatrovanie (PPnO), Register sociálnych služieb (RSS).
 *
 * Všetky osoby, rodné čísla, IČO a adresy sú vymyslené.
 */
final class DemoData {

	private DemoData() {
	}

	/*
	 * Kódy typu identifikátora sú podľa manuálu z číselníka CL004001. Presné kódy pre rodné číslo a IČO
	 * treba overiť v číselníku – v mocku sú zámerne čitateľné hodnoty.
	 */
	static final String ID_RC = "RC";
	static final String ID_ICO = "ICO";

	// ---------------------------------------------------------------- osoby

	record Person(String rc, String givenName, String familyName, LocalDate dateOfBirth) {
		String fullName() {
			return givenName + " " + familyName;
		}
	}

	static final Person KOVACIK = new Person("410312/0011", "Ján", "Kováčik", LocalDate.of(1941, 3, 12));
	static final Person HORVATHOVA = new Person("485705/0022", "Mária", "Horváthová", LocalDate.of(1948, 7, 5));
	static final Person SLOBODA = new Person("391120/0033", "Peter", "Sloboda", LocalDate.of(1939, 11, 20));
	static final Person BIELIKOVA = new Person("525214/0044", "Anna", "Bieliková", LocalDate.of(1952, 2, 14));
	static final Person TOTH = new Person("440901/0055", "Rudolf", "Tóth", LocalDate.of(1944, 9, 1));

	// opatrovatelia – poberatelia PPnO
	static final Person KOVACIKOVA = new Person("705510/1111", "Eva", "Kováčiková", LocalDate.of(1970, 5, 10));
	static final Person TOTH_M = new Person("720122/2222", "Miroslav", "Tóth", LocalDate.of(1972, 1, 22));

	static final List<Person> PERSONS = List.of(KOVACIK, HORVATHOVA, SLOBODA, BIELIKOVA, TOTH, KOVACIKOVA, TOTH_M);

	// ---------------------------------------------------------------- Evidencia ŤZP

	record Period(YearMonth from, YearMonth to) { }

	record Card(String id, LocalDate validFrom, LocalDate validTo, String type) { }

	record TzpRecord(Person person, List<Card> parkingCards, List<Card> disabilityCards, List<Period> disability) { }

	static final List<TzpRecord> TZP = List.of(
		new TzpRecord(KOVACIK,
			List.of(new Card("PP-2024-000777", LocalDate.of(2024, 4, 1), LocalDate.of(2029, 3, 31), null)),
			List.of(new Card("TZPS-2024-001234", LocalDate.of(2024, 3, 5), null, "tzp-s")),
			List.of(new Period(YearMonth.of(2024, 2), null))),
		new TzpRecord(HORVATHOVA,
			List.of(),
			List.of(new Card("TZP-2025-004567", LocalDate.of(2025, 12, 1), null, "tzp")),
			List.of(new Period(YearMonth.of(2025, 11), null))),
		new TzpRecord(BIELIKOVA,
			List.of(),
			List.of(),
			List.of(new Period(YearMonth.of(2026, 8), null))),
		new TzpRecord(TOTH,
			List.of(new Card("PP-2023-000412", LocalDate.of(2023, 3, 1), LocalDate.of(2028, 2, 29), null)),
			List.of(new Card("TZP-2019-000955", LocalDate.of(2019, 6, 10), LocalDate.of(2021, 4, 30), "tzp"),
				new Card("TZPS-2023-000321", LocalDate.of(2023, 2, 15), null, "tzp-s")),
			List.of(new Period(YearMonth.of(2019, 5), YearMonth.of(2021, 4)), new Period(YearMonth.of(2023, 1), null)))
	);

	static Optional<TzpRecord> tzp(String rc) {
		return TZP.stream().filter(t -> t.person().rc().equals(rc)).findFirst();
	}

	// ---------------------------------------------------------------- Peňažný príspevok na opatrovanie

	record Nursed(Person person, LocalDate dateFrom, LocalDate dateTo, String role) { }

	record BenefitPeriod(YearMonth from, YearMonth to, String state) { }

	record Application(YearMonth monthFrom, YearMonth monthTo, String id, String office, String district,
			LocalDate dateSubmitted, List<Nursed> nursed, List<BenefitPeriod> periods) { }

	record PpnoRecord(Person recipient, List<Application> applications) { }

	/*
	 * Kódy úradu (rezortný číselník UradPSVaR), okresu (CL000024) a stavu (StavPoskytovaniaSocDavky)
	 * sú v mocku ilustračné – skutočné hodnoty sú v číselníkoch MPSVaR / základných číselníkoch.
	 */
	static final List<PpnoRecord> PPNO = List.of(
		new PpnoRecord(KOVACIKOVA, List.of(
			new Application(YearMonth.of(2025, 3), null, "PPNO-2025-08812", "UPSVR-TT", "OKRES-TT",
				LocalDate.of(2025, 2, 17),
				List.of(new Nursed(KOVACIK, LocalDate.of(2025, 3, 1), null, "adult")),
				List.of(new BenefitPeriod(YearMonth.of(2025, 3), null, "POSKYTUJE_SA"))))),
		new PpnoRecord(TOTH_M, List.of(
			new Application(YearMonth.of(2023, 6), YearMonth.of(2026, 8), "PPNO-2023-03307", "UPSVR-TT", "OKRES-TT",
				LocalDate.of(2023, 5, 22),
				List.of(new Nursed(TOTH, LocalDate.of(2023, 6, 1), LocalDate.of(2026, 8, 31), "adult")),
				List.of(new BenefitPeriod(YearMonth.of(2023, 6), YearMonth.of(2026, 8), "POSKYTUJE_SA"),
					new BenefitPeriod(YearMonth.of(2026, 9), null, "ZASTAVENE")))))
	);

	/** Vyhľadanie podľa poberateľa (opatrovateľa). */
	static List<PpnoRecord> ppnoByRecipient(String rc) {
		return PPNO.stream().filter(p -> p.recipient().rc().equals(rc)).toList();
	}

	/** Vyhľadanie podľa opatrovanej osoby (rozšírenie mocku pre demo). */
	static List<PpnoRecord> ppnoByNursed(String rc) {
		return PPNO.stream()
			.filter(p -> p.applications().stream()
				.anyMatch(a -> a.nursed().stream().anyMatch(n -> n.person().rc().equals(rc))))
			.toList();
	}

	// ---------------------------------------------------------------- Register sociálnych služieb

	record Code(String code, String description) { }

	record Address(String street, String streetNumber, String postCode, String lau2) { }

	record Service(String id, Code type, Code form, Address address, String lau2Scope, LocalDate providedFrom,
			LocalDate providedTo, List<Code> targetGroups, Integer capacity, String responsible, String email,
			String phone, LocalDate registered, LocalDate erased, List<Code> erasureReasons, String registrar) { }

	record Provider(String name, String ico, Code providerType, Address address, String statutoryBody,
			List<Service> services) { }

	/* Kódy číselníkov TypPoskytovatela, DruhSluzby, FormaPoskytovaniaSluzby, CieloveSkupiny, PravnyDovod sú ilustračné. */
	private static final Code NEVEREJNY = new Code("NEVEREJNY", "neverejný poskytovateľ sociálnej služby");
	private static final Code VEREJNY = new Code("VEREJNY", "verejný poskytovateľ sociálnej služby");
	private static final Code ZPS = new Code("ZPS", "zariadenie pre seniorov (§ 35)");
	private static final Code DS = new Code("DS", "denný stacionár (§ 40)");
	private static final Code OS = new Code("OS", "opatrovateľská služba (§ 41)");
	private static final Code POBYTOVA = new Code("POBYTOVA_CELOROCNA", "pobytová – celoročná");
	private static final Code AMBULANTNA = new Code("AMBULANTNA", "ambulantná");
	private static final Code TERENNA = new Code("TERENNA", "terénna");
	private static final Code SENIORI = new Code("SENIORI", "fyzické osoby, ktoré dovŕšili dôchodkový vek");
	private static final Code ODKAZANI = new Code("ODKAZANI", "fyzické osoby odkázané na pomoc inej osoby");
	private static final Code VYMAZ_ZIADOST = new Code("NA_ZIADOST", "výmaz na žiadosť poskytovateľa");

	static final String SLNECNY_DOM_ICO = "51234567";
	static final String TESTOVO_ICO = "00312345";
	static final String VZOROVA_ICO = "00398765";

	static final List<Provider> RSS = List.of(
		new Provider("Slnečný dom, n. o.", SLNECNY_DOM_ICO, NEVEREJNY,
			new Address("Hlavná", "12", "91701", "Testovo"), "Ing. Zuzana Pokojná, riaditeľka",
			List.of(
				new Service("RSS-10021", ZPS, POBYTOVA, new Address("Hlavná", "12", "91701", "Testovo"), null,
					LocalDate.of(2018, 1, 1), null, List.of(SENIORI, ODKAZANI), 40, "Mgr. Ivana Dobrá",
					"zps@slnecny-dom.test", "+421330000111", LocalDate.of(2017, 11, 20), null, List.of(),
					"Trnavský samosprávny kraj"),
				new Service("RSS-10022", DS, AMBULANTNA, new Address("Hlavná", "12", "91701", "Testovo"), null,
					LocalDate.of(2021, 9, 1), null, List.of(SENIORI), 15, "Mgr. Ivana Dobrá",
					"stacionar@slnecny-dom.test", "+421330000112", LocalDate.of(2021, 7, 14), null, List.of(),
					"Trnavský samosprávny kraj"))),
		new Provider("Obec Testovo", TESTOVO_ICO, VEREJNY,
			new Address("Obecná", "1", "91701", "Testovo"), "Ján Starostlivý, starosta",
			List.of(
				new Service("RSS-20110", OS, TERENNA, null, "Testovo",
					LocalDate.of(2015, 1, 1), null, List.of(ODKAZANI), 25, "Bc. Lucia Ochotná",
					"socialne@testovo.test", "+421330000200", LocalDate.of(2014, 12, 3), null, List.of(),
					"Trnavský samosprávny kraj"))),
		new Provider("Mesto Vzorová", VZOROVA_ICO, VEREJNY,
			new Address("Námestie SNP", "5", "92101", "Vzorová"), "Mgr. Peter Vzorný, primátor",
			List.of(
				new Service("RSS-30201", OS, TERENNA, null, "Vzorová",
					LocalDate.of(2012, 3, 1), null, List.of(ODKAZANI), 60, "Mgr. Katarína Pomocná",
					"opatrovanie@vzorova.test", "+421330000300", LocalDate.of(2012, 2, 1), null, List.of(),
					"Trnavský samosprávny kraj"),
				new Service("RSS-30202", ZPS, POBYTOVA, new Address("Parková", "3", "92101", "Vzorová"), null,
					LocalDate.of(2010, 1, 1), LocalDate.of(2024, 12, 31), List.of(SENIORI), null, "Mgr. Katarína Pomocná",
					null, null, LocalDate.of(2009, 10, 1), LocalDate.of(2025, 1, 15), List.of(VYMAZ_ZIADOST),
					"Trnavský samosprávny kraj")))
	);

	static Optional<Provider> provider(String ico) {
		return RSS.stream().filter(p -> p.ico().equals(ico)).findFirst();
	}

	// ---------------------------------------------------------------- zoznam zmien (GetListChanges)

	record Change(String register, int daysAgo, Person person, String changeType) {
		LocalDate date(LocalDate today) {
			return today.minusDays(daysAgo);
		}

		OffsetDateTime timestamp(LocalDate today) {
			return date(today).atTime(LocalTime.of(6, 15).plusMinutes(daysAgo * 7L)).atOffset(ZoneOffset.ofHours(2));
		}
	}

	/* Typ zmeny – manuál hodnoty nešpecifikuje, v mocku ilustračné. Dátumy sú relatívne k dnešku. */
	static final List<Change> CHANGES = List.of(
		new Change("TZP", 1, BIELIKOVA, "NOVY"),
		new Change("TZP", 4, HORVATHOVA, "ZMENA"),
		new Change("TZP", 9, TOTH, "ZMENA"),
		new Change("PPnO", 2, TOTH_M, "ZMENA"),
		new Change("PPnO", 12, KOVACIKOVA, "ZMENA")
	);
}
