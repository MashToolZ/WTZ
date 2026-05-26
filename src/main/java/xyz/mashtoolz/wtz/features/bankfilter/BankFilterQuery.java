package xyz.mashtoolz.wtz.features.bankfilter;

import xyz.mashtoolz.wtz.config.WTZConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BankFilterQuery {
    private final List<Criterion> criteria;

    private BankFilterQuery(List<Criterion> criteria) {
        this.criteria = criteria;
    }

    public static BankFilterQuery parse(String query) {
        List<Criterion> criteria = new ArrayList<>();
        for (Token token : tokens(query)) {
            if (!token.isFilter()) continue;

            BankFilter filter = BankFilterRegistry.byKey(token.normalizedKey());
            if (filter == null) continue;

            boolean negative = token.normalizedKey().startsWith("!");
            if (negative && !filter.supportsNegative()) continue;

            criteria.add(new Criterion(filter, negative, token.value()));
        }
        return new BankFilterQuery(criteria);
    }

    public static boolean hasFilters(String query, WTZConfig config) {
        for (Token token : tokens(query)) {
            BankFilter filter = token.isFilter() ? BankFilterRegistry.byKey(token.normalizedKey()) : null;
            if (filter != null && filter.isEnabled(config)) {
                return true;
            }
        }
        return false;
    }

    public static String stripFilters(String query, WTZConfig config) {
        if (query == null || query.isBlank()) return query;

        StringBuilder stripped = new StringBuilder();
        for (Token token : tokens(query)) {
            BankFilter filter = token.isFilter() ? BankFilterRegistry.byKey(token.normalizedKey()) : null;
            if (filter != null && filter.isEnabled(config)) continue;

            if (!stripped.isEmpty()) stripped.append(' ');
            stripped.append(token.raw());
        }
        return stripped.toString();
    }

    public boolean isEmpty() {
        return criteria.isEmpty();
    }

    public boolean matches(BankFilterContext context, WTZConfig config) {
        for (Criterion criterion : criteria) {
            if (!criterion.filter().isEnabled(config)) continue;

            String actual = criterion.filter().value(context);
            boolean matched = matchesAny(criterion.expected(), actual);
            if (criterion.negative()) {
                if (matched) return false;
            } else if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAny(String expectedValues, String actual) {
        for (String expected : splitValues(expectedValues)) {
            if (!expected.isBlank() && matchesSingle(expected, actual)) return true;
        }
        return false;
    }

    private static boolean matchesSingle(String expected, String actual) {
        if (actual == null || actual.isBlank()) return false;
        if (expected.equals("*")) return true;

        String normalizedExpected = stripStrictQuotes(expected);
        if (isStrict(expected)) {
            return actual.equalsIgnoreCase(normalizedExpected);
        }
        return actual.toLowerCase(Locale.ROOT).contains(normalizedExpected.toLowerCase(Locale.ROOT));
    }

    private static List<String> splitValues(String value) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') quoted = !quoted;
            if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        values.add(current.toString());
        return values;
    }

    private static boolean isStrict(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"");
    }

    private static String stripStrictQuotes(String value) {
        return isStrict(value) ? value.substring(1, value.length() - 1) : value;
    }

    private static List<Token> tokens(String query) {
        if (query == null || query.isBlank()) return List.of();

        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '"') quoted = !quoted;
            if (Character.isWhitespace(c) && !quoted) {
                addToken(tokens, current);
                continue;
            }
            current.append(c);
        }
        addToken(tokens, current);
        return tokens;
    }

    private static void addToken(List<Token> tokens, StringBuilder current) {
        if (current.isEmpty()) return;
        tokens.add(Token.of(current.toString()));
        current.setLength(0);
    }

    private record Criterion(BankFilter filter, boolean negative, String expected) {
    }

    private record Token(String raw, String normalizedKey, String value) {
        static Token of(String raw) {
            int separator = raw.indexOf(':');
            if (separator <= 0 || separator == raw.length() - 1) {
                return new Token(raw, "", "");
            }
            String key = raw.substring(0, separator).toLowerCase(Locale.ROOT);
            return new Token(raw, key, raw.substring(separator + 1));
        }

        boolean isFilter() {
            return !normalizedKey.isEmpty();
        }
    }
}
