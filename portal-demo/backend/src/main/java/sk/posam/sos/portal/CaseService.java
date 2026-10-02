package sk.posam.sos.portal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Ukážková agenda: žiadosti o posúdenie odkázanosti na sociálnu službu.
 * Údaje sú ilustračné a držia sa len v pamäti (po reštarte sa obnovia).
 */
@Service
public class CaseService {

	/** Obec, ktorú vidí skupina "obce". */
	public static final String DEMO_OBEC = "Obec Testovo";
	/** Poskytovateľ, ktorého vidí skupina "poskytovatelia". */
	public static final String DEMO_POSKYTOVATEL = "Slnečný dom, n. o.";

	private static final Set<String> ALL_ACCESS = Set.of("sos-admin", "sos-moderator", "rezort");

	public enum Status { NOVA, V_POSUDZOVANI, ROZHODNUTE }

	public record Predpis(String rok, String cislo, String paragraf, String nazov) { }

	public record Ziadost(String id, String ziadatel, String sluzba, String obec, String poskytovatel,
			Status stav, LocalDate podana, LocalDate termin, String popis, Predpis predpis, List<String> tagy) { }

	public record Scope(String nazov, boolean vsetko, boolean mozeUpravovat) { }

	/** Kľúče do IS CSRÚ (fiktívne RČ žiadateľa a IČO poskytovateľa) – do UI sa neposielajú. */
	public record CsruKluce(String rc, String ico) { }

	private static final Predpis ZSS_49 = new Predpis("2008", "448", "§ 49",
			"zákon č. 448/2008 Z. z. o sociálnych službách – konanie o odkázanosti");
	private static final Predpis ZSS_35 = new Predpis("2008", "448", "§ 35",
			"zákon č. 448/2008 Z. z. o sociálnych službách – zariadenie pre seniorov");
	private static final Predpis ZSS_41 = new Predpis("2008", "448", "§ 41",
			"zákon č. 448/2008 Z. z. o sociálnych službách – opatrovateľská služba");
	private static final Predpis ZSS_72 = new Predpis("2008", "448", "§ 72",
			"zákon č. 448/2008 Z. z. o sociálnych službách – úhrada za sociálnu službu");

	private final Map<String, Ziadost> data = new ConcurrentHashMap<>(Stream.of(
		new Ziadost("Z-2026-0101", "J. K. (ilustračný žiadateľ)", "Zariadenie pre seniorov", DEMO_OBEC, DEMO_POSKYTOVATEL,
			Status.NOVA, LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 22),
			"Žiadosť o posúdenie odkázanosti, žiadateľ býva sám, potrebuje celodennú starostlivosť.",
			ZSS_49, List.of("odkazanost", "zariadenie-pre-seniorov")),
		new Ziadost("Z-2026-0102", "M. H. (ilustračná žiadateľka)", "Opatrovateľská služba", DEMO_OBEC, "Opatrovateľská služba obce",
			Status.V_POSUDZOVANI, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10),
			"Čaká sa na lekársky posudok, otázka rozsahu hodín opatrovania.",
			ZSS_41, List.of("opatrovatelska-sluzba")),
		new Ziadost("Z-2026-0103", "P. S. (ilustračný žiadateľ)", "Zariadenie pre seniorov", DEMO_OBEC, DEMO_POSKYTOVATEL,
			Status.ROZHODNUTE, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
			"Rozhodnuté, otvorená otázka výšky úhrady pri nízkom príjme.",
			ZSS_72, List.of("uhrada")),
		new Ziadost("Z-2026-0201", "A. B. (ilustračná žiadateľka)", "Opatrovateľská služba", "Mesto Vzorová", "Opatrovateľská služba mesta",
			Status.NOVA, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 28),
			"Nová žiadosť, žiadateľka po hospitalizácii.",
			ZSS_49, List.of("odkazanost")),
		new Ziadost("Z-2026-0202", "R. T. (ilustračný žiadateľ)", "Zariadenie pre seniorov", "Mesto Vzorová", DEMO_POSKYTOVATEL,
			Status.V_POSUDZOVANI, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15),
			"Prebieha sociálna posudková činnosť.",
			ZSS_35, List.of("zariadenie-pre-seniorov"))
	).collect(Collectors.toMap(Ziadost::id, Function.identity())));

	private static final Map<String, CsruKluce> CSRU = Map.of(
		"Z-2026-0101", new CsruKluce("410312/0011", "51234567"),
		"Z-2026-0102", new CsruKluce("485705/0022", "00312345"),
		"Z-2026-0103", new CsruKluce("391120/0033", "51234567"),
		"Z-2026-0201", new CsruKluce("525214/0044", "00398765"),
		"Z-2026-0202", new CsruKluce("440901/0055", "51234567"));

	/** Žiadosť, ak ju používateľ smie vidieť. */
	public Optional<Ziadost> find(String id, Collection<String> groups) {
		return Optional.ofNullable(data.get(id)).filter(z -> visible(z, groups));
	}

	public Optional<CsruKluce> csruKluce(String id) {
		return Optional.ofNullable(CSRU.get(id));
	}

	/** Čo používateľ vidí, podľa skupín z Keycloaku. */
	public Scope scope(Collection<String> groups) {
		if (groups.stream().anyMatch(ALL_ACCESS::contains)) {
			return new Scope("Celá SR (rezort / správca)", true, true);
		}
		if (groups.contains("obce")) {
			return new Scope(DEMO_OBEC, false, true);
		}
		if (groups.contains("poskytovatelia")) {
			return new Scope(DEMO_POSKYTOVATEL, false, false);
		}
		return new Scope("bez prístupu", false, false);
	}

	public List<Ziadost> list(Collection<String> groups) {
		return data.values().stream()
			.filter(z -> visible(z, groups))
			.sorted(Comparator.comparing(Ziadost::termin))
			.toList();
	}

	public Optional<Ziadost> changeStatus(String id, Status stav, Collection<String> groups) {
		Ziadost z = data.get(id);
		if (z == null || !visible(z, groups) || !scope(groups).mozeUpravovat()) {
			return Optional.empty();
		}
		Ziadost updated = new Ziadost(z.id(), z.ziadatel(), z.sluzba(), z.obec(), z.poskytovatel(), stav,
			z.podana(), z.termin(), z.popis(), z.predpis(), z.tagy());
		data.put(id, updated);
		return Optional.of(updated);
	}

	private boolean visible(Ziadost z, Collection<String> groups) {
		if (groups.stream().anyMatch(ALL_ACCESS::contains)) {
			return true;
		}
		if (groups.contains("obce")) {
			return DEMO_OBEC.equals(z.obec());
		}
		if (groups.contains("poskytovatelia")) {
			return DEMO_POSKYTOVATEL.equals(z.poskytovatel());
		}
		return false;
	}
}
