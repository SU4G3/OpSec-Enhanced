package aurick.opsec.mod.config;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One entry of the ordered server-rule list.
 *
 * <p>A rule binds a <em>matcher</em> (the address the player typed) to a full set of
 * {@link SpoofSettings}. Rules are evaluated in list order and the first match wins, so
 * list position <em>is</em> the priority — there is no separate priority field to keep
 * out of sync with the order the user sees in the UI.</p>
 *
 * <p>The matcher input is the address the player typed (the hostname or IP literal they
 * entered in the connect screen), <strong>not</strong> whatever the connection ended up
 * resolving to. A player typing {@code mc.example.com} matches a rule for that domain
 * even when the connection goes through a proxy, a redirect or a multi-A-record DNS
 * answer. The resolved IP is only used as a secondary input, so a rule can also target
 * an IP literal directly.</p>
 */
public class ServerRule {

    /** How a rule compares itself against the address the player typed. */
    public enum MatchType {
        /** The typed address must equal the pattern (optionally with {@code :port}). */
        EXACT,
        /** The pattern is a Java regex that must fully match the typed address. */
        REGEX,
        /** The pattern is an IP literal and must equal the resolved address. */
        IP,
        /**
         * The mandatory global fallback rule. It matches every server, carries the
         * baseline settings used when no other rule hits, is always enabled, always
         * evaluated last and can never be deleted or reordered by the user.
         */
        ANY
    }

    private MatchType matchType = MatchType.EXACT;
    private String pattern = "";
    private boolean enabled = true;
    private final SpoofSettings settings = new SpoofSettings();

    public ServerRule() {
    }

    public ServerRule(MatchType matchType, String pattern) {
        this.matchType = matchType == null ? MatchType.EXACT : matchType;
        this.pattern = pattern == null ? "" : pattern.trim();
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType == null ? MatchType.EXACT : matchType;
        if (this.matchType == MatchType.ANY) {
            this.pattern = "";
        }
    }

    /** True when this rule is the global fallback rule. */
    public boolean isAnyRule() {
        return matchType == MatchType.ANY;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern == null ? "" : pattern.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** The settings this rule applies when it matches. */
    public SpoofSettings getSettings() {
        return settings;
    }

    /** Copies {@code source} into this rule's settings. */
    public void copySettingsFrom(SpoofSettings source) {
        if (source != null) {
            settings.copyFrom(source);
        }
    }

    /**
     * Tests this rule against one connection.
     *
     * @param host the address the player typed (hostname or IP literal), may be null
     * @param port the port the player connected to, {@code 0} when unknown
     * @param ip   the resolved IP address, may be null
     * @return true when this rule applies to that connection
     */
    public boolean matches(String host, int port, String ip) {
        if (!enabled) {
            return false;
        }
        if (matchType == MatchType.ANY) {
            return true;
        }
        for (String p : splitPatterns(pattern)) {
            boolean hit = switch (matchType) {
                case EXACT -> matchesExact(p, host, port, ip);
                case REGEX -> matchesRegex(p, host, ip);
                case IP -> matchesIp(p, host, ip);
                case ANY -> true;
            };
            if (hit) {
                return true;
            }
        }
        return false;
    }

    /**
     * Splits a comma-separated pattern field into the individual patterns it holds.
     *
     * <p>Commas at brace/paren/bracket depth zero separate patterns; commas nested in a
     * regex quantifier such as {@code a{1,3}} do not. A literal comma inside a regex is
     * written {@code \x2c} — the Java-regex escape for {@code ,} — which contains no
     * literal comma and therefore never acts as a separator.</p>
     */
    public static List<String> splitPatterns(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                current.append(c).append(raw.charAt(++i));
                continue;
            }
            if (c == '{' || c == '(' || c == '[') {
                depth++;
            } else if ((c == '}' || c == ')' || c == ']') && depth > 0) {
                depth--;
            }
            if (c == ',' && depth == 0) {
                addPattern(out, current);
            } else {
                current.append(c);
            }
        }
        addPattern(out, current);
        return out;
    }

    private static void addPattern(List<String> out, StringBuilder current) {
        String value = current.toString().trim();
        current.setLength(0);
        if (!value.isEmpty()) {
            out.add(value);
        }
    }

    private static boolean matchesExact(String p, String host, int port, String ip) {
        String wantHost = p;
        int wantPort = -1;
        int colon = p.lastIndexOf(':');
        // "host:port" — but not the colons inside a bare IPv6 literal, and not the
        // colon of a bracketed IPv6 literal ("[::1]:25565" splits after the bracket).
        boolean bracketed = p.indexOf(']') >= 0;
        if (colon > 0 && (!bracketed || p.indexOf(']') < colon)) {
            String portPart = p.substring(colon + 1);
            try {
                wantPort = Integer.parseInt(portPart);
                wantHost = p.substring(0, colon);
            } catch (NumberFormatException ignored) {
                // Not a port — treat the whole pattern as the host.
            }
        }
        if (wantHost.startsWith("[") && wantHost.endsWith("]")) {
            wantHost = wantHost.substring(1, wantHost.length() - 1);
        }
        if (wantPort > 0 && port > 0 && wantPort != port) {
            return false;
        }
        String typed = normalize(host);
        if (!typed.isEmpty() && typed.equalsIgnoreCase(wantHost)) {
            return true;
        }
        String resolved = normalize(ip);
        return !resolved.isEmpty() && resolved.equalsIgnoreCase(wantHost);
    }

    private static boolean matchesRegex(String p, String host, String ip) {
        Pattern compiled;
        try {
            compiled = Pattern.compile(p);
        } catch (PatternSyntaxException e) {
            // A broken pattern must not take the whole rule list down — it simply
            // never matches. The UI surfaces the compile error when the user edits it.
            return false;
        }
        String typed = normalize(host);
        if (!typed.isEmpty() && compiled.matcher(typed).matches()) {
            return true;
        }
        String resolved = normalize(ip);
        return !resolved.isEmpty() && compiled.matcher(resolved).matches();
    }

    private static boolean matchesIp(String p, String host, String ip) {
        String want = normalize(p);
        String resolved = normalize(ip);
        if (!resolved.isEmpty() && resolved.equals(want)) {
            return true;
        }
        String typed = normalize(host);
        return !typed.isEmpty() && typed.equals(want);
    }

    /** Lower-cases and strips the brackets of an IPv6 literal; returns "" for null/blank. */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("[") && v.endsWith("]")) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("matchType", matchType.name());
        json.addProperty("pattern", pattern);
        json.addProperty("enabled", enabled);
        json.add("settings", settings.toJson());
        return json;
    }

    public static ServerRule fromJson(JsonObject json) {
        ServerRule rule = new ServerRule();
        if (json.has("matchType")) {
            try {
                rule.matchType = MatchType.valueOf(json.get("matchType").getAsString().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                rule.matchType = MatchType.EXACT;
            }
        }
        if (json.has("pattern")) {
            rule.pattern = json.get("pattern").getAsString();
        }
        if (json.has("enabled")) {
            rule.enabled = json.get("enabled").getAsBoolean();
        }
        if (rule.matchType == MatchType.ANY) {
            // The ANY rule is structurally always-on and pattern-less: a hand-edited
            // config must not be able to disable it or give it a pattern.
            rule.pattern = "";
            rule.enabled = true;
        }
        if (json.has("settings")) {
            rule.settings.copyFrom(SpoofSettings.fromJson(json.getAsJsonObject("settings")));
        }
        return rule;
    }
}
