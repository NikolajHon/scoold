package sk.posam.sos.csru;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import sk.posam.sos.csru.DemoData.Address;
import sk.posam.sos.csru.DemoData.Change;
import sk.posam.sos.csru.DemoData.Code;
import sk.posam.sos.csru.DemoData.Person;
import sk.posam.sos.csru.DemoData.PpnoRecord;
import sk.posam.sos.csru.DemoData.Provider;
import sk.posam.sos.csru.DemoData.TzpRecord;

/**
 * Výstupy v štruktúre XSD z prílohy č. 2 integračného manuálu IS CSRÚ (poradie elementov podľa xs:sequence).
 */
final class Render {

	static final String NS_TZP = "http://csru/gov/sk/TZP_OUT/1.0";
	static final String NS_PPNO = "http://data.gov.sk/ppno_out/v1";
	static final String NS_RSS = "http://csru/gov/sk/MPSVaR_RSS_Potvrdenie/1.0";
	static final String NS_CHANGES = "http://csru/gov/sk/Pub_GetListChanges/1.0";

	private Render() {
	}

	/** xs:dateTime vždy so sekundami (OffsetDateTime.toString() ich pri :00 vynechá). */
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

	static String now() {
		return dateTime(OffsetDateTime.now(ZoneOffset.ofHours(2)));
	}

	static String dateTime(OffsetDateTime t) {
		return DATE_TIME.format(t);
	}

	// ---------------------------------------------------------------- OE_MPSVAR_TZP_OUT_v001.xsd

	static String tzp(List<TzpRecord> records) {
		Xml x = new Xml();
		if (records.isEmpty()) {
			return x.empty("tzp", "xmlns", NS_TZP, "dateChanged", now()).toString();
		}
		x.open("tzp", "xmlns", NS_TZP, "dateChanged", now());
		for (TzpRecord t : records) {
			x.open("person");
			physicalPerson(x, "physicalPerson", t.person());
			for (DemoData.Card c : t.parkingCards()) {
				x.open("parkingCard").el("id", c.id()).el("validFrom", c.validFrom()).el("validTo", c.validTo())
					.close("parkingCard");
			}
			for (DemoData.Card c : t.disabilityCards()) {
				x.open("disabilityCard").el("id", c.id()).el("validFrom", c.validFrom()).el("validTo", c.validTo())
					.el("type", c.type()).close("disabilityCard");
			}
			for (DemoData.Period p : t.disability()) {
				x.open("disability").el("from", p.from()).el("to", p.to()).close("disability");
			}
			x.close("person");
		}
		return x.close("tzp").toString();
	}

	// ---------------------------------------------------------------- OE_MPSVAR_PPnO_001.xsd

	static String ppno(List<PpnoRecord> records) {
		Xml x = new Xml();
		String dataFrom = YearMonth.of(2020, 1).toString();
		if (records.isEmpty()) {
			return x.empty("persons", "xmlns", NS_PPNO, "dataFrom", dataFrom, "dateChanged", now()).toString();
		}
		x.open("persons", "xmlns", NS_PPNO, "dataFrom", dataFrom, "dateChanged", now());
		for (PpnoRecord r : records) {
			x.open("person");
			physicalPerson(x, "physicalPerson", r.recipient());
			for (DemoData.Application a : r.applications()) {
				x.open("application")
					.el("monthFrom", a.monthFrom()).el("monthTo", a.monthTo()).el("id", a.id())
					.el("office", a.office()).el("district", a.district()).el("dateSubmitted", a.dateSubmitted());
				for (DemoData.Nursed n : a.nursed()) {
					x.open("nursedPerson");
					physicalPerson(x, "person", n.person());
					x.el("dateFrom", n.dateFrom()).el("dateTo", n.dateTo()).el("role", n.role()).close("nursedPerson");
				}
				for (DemoData.BenefitPeriod p : a.periods()) {
					x.open("period").el("monthFrom", p.from()).el("monthTo", p.to()).el("state", p.state())
						.close("period");
				}
				x.close("application");
			}
			x.close("person");
		}
		return x.close("persons").toString();
	}

	// ---------------------------------------------------------------- OE_MPSVAR_RSS_Vypis_v001.xsd

	static String rss(Provider p) {
		Xml x = new Xml();
		if (p == null) {
			return x.empty("rss", "xmlns", NS_RSS, "dateChanged", now()).toString();
		}
		x.open("rss", "xmlns", NS_RSS, "dateChanged", now());
		x.open("provider").el("name", p.name());
		identifier(x, "identifier", DemoData.ID_ICO, p.ico());
		code(x, "providerType", p.providerType());
		address(x, p.address());
		x.el("statutoryBody", p.statutoryBody());
		for (DemoData.Service s : p.services()) {
			x.open("providedService").el("identifier", s.id());
			code(x, "type", s.type());
			code(x, "form", s.form());
			if (s.address() != null) {
				address(x, s.address());
			}
			if (s.lau2Scope() != null) {
				x.open("territorialScope").el("lau2", s.lau2Scope()).close("territorialScope");
			}
			x.el("providedFrom", s.providedFrom()).el("providedTo", s.providedTo());
			for (Code c : s.targetGroups()) {
				code(x, "targetGroup", c);
			}
			x.el("currentCapacity", s.capacity()).el("responsiblePerson", s.responsible())
				.el("email", s.email()).el("phone", s.phone())
				.el("dateOfRegistration", s.registered()).el("dateOfErasure", s.erased());
			for (Code c : s.erasureReasons()) {
				code(x, "erasureReason", c);
			}
			x.el("registrar", s.registrar()).close("providedService");
		}
		x.close("provider");
		return x.close("rss").toString();
	}

	// ---------------------------------------------------------------- CSRU_Pub_GetListChanges_1.0.xsd

	static String changes(String register, LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.now();
		List<Change> list = DemoData.CHANGES.stream()
			.filter(c -> c.register().equalsIgnoreCase(register))
			.filter(c -> !c.date(today).isBefore(from) && !c.date(today).isAfter(to))
			.toList();
		Map<LocalDate, List<Change>> byDay = list.stream()
			.collect(Collectors.groupingBy(c -> c.date(today), TreeMap::new, Collectors.toList()));
		Xml x = new Xml();
		x.open("RegisterChanges", "xmlns", NS_CHANGES).el("Register", register);
		if (!byDay.isEmpty()) {
			x.open("Changes");
			byDay.forEach((day, changes) -> {
				x.open("ChangesInDay").el("DateOfChange", day).el("CountChangesInDay", changes.size()).open("Subjects");
				changes.stream().sorted(Comparator.comparing(c -> c.timestamp(today))).forEach(c -> {
					x.open("Subject").open("SubjectData").open("Identifiers").open("Identifier")
						.el("IdentifierType", DemoData.ID_RC).el("IdentifierValue", c.person().rc())
						.close("Identifier").close("Identifiers")
						.el("GivenName", c.person().givenName()).el("FamilyName", c.person().familyName())
						.el("DateOfBirth", c.person().dateOfBirth())
						.close("SubjectData")
						.el("ChangeType", c.changeType()).el("TimeStamp", dateTime(c.timestamp(today)))
						.close("Subject");
				});
				x.close("Subjects").close("ChangesInDay");
			});
			x.close("Changes");
		}
		long days = from.until(to, ChronoUnit.DAYS) + 1;
		return x.el("CountDays", days).el("CountChangesTotal", list.size()).close("RegisterChanges").toString();
	}

	// ---------------------------------------------------------------- spoločné časti

	private static void physicalPerson(Xml x, String element, Person p) {
		x.open(element).el("givenName", p.givenName()).el("familyName", p.familyName()).el("dateOfBirth", p.dateOfBirth());
		identifier(x, "identifier", DemoData.ID_RC, p.rc());
		x.close(element);
	}

	private static void identifier(Xml x, String element, String type, String value) {
		x.open(element).el("identifierType", type).el("value", value).close(element);
	}

	private static void code(Xml x, String element, Code c) {
		x.open(element).el("itemCode", c.code()).el("itemDescription", c.description()).close(element);
	}

	private static void address(Xml x, Address a) {
		x.open("address").el("street", a.street()).el("streetNumber", a.streetNumber()).el("postCode", a.postCode())
			.el("lau2", a.lau2()).close("address");
	}
}
