package sk.posam.sos.csru;

/** Minimalistický zapisovač XML (bez závislostí), elementy v jednom namespace. */
final class Xml {

	private final StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
	private int depth;

	Xml open(String name, String... attrs) {
		indent();
		sb.append('<').append(name);
		for (int i = 0; i + 1 < attrs.length; i += 2) {
			sb.append(' ').append(attrs[i]).append("=\"").append(esc(attrs[i + 1])).append('"');
		}
		sb.append(">\n");
		depth++;
		return this;
	}

	Xml close(String name) {
		depth--;
		indent();
		sb.append("</").append(name).append(">\n");
		return this;
	}

	/** Jednoduchý element s textom; pri null sa nevypíše (nepovinné elementy). */
	Xml el(String name, Object value) {
		if (value == null) {
			return this;
		}
		indent();
		sb.append('<').append(name).append('>').append(esc(String.valueOf(value)))
			.append("</").append(name).append(">\n");
		return this;
	}

	/** Prázdny koreň s atribútmi (napr. žiadny záznam). */
	Xml empty(String name, String... attrs) {
		indent();
		sb.append('<').append(name);
		for (int i = 0; i + 1 < attrs.length; i += 2) {
			sb.append(' ').append(attrs[i]).append("=\"").append(esc(attrs[i + 1])).append('"');
		}
		sb.append("/>\n");
		return this;
	}

	@Override
	public String toString() {
		return sb.toString();
	}

	private void indent() {
		sb.append("  ".repeat(Math.max(0, depth)));
	}

	static String esc(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
