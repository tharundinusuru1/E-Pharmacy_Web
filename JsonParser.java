import java.util.HashMap;
import java.util.Map;


public class JsonParser {


    public static Map<String, String> parse(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null) return result;
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return result;
        }

        // Strip the outer curly braces
        String inner = json.substring(1, json.length() - 1).trim();
        if (inner.isEmpty()) return result;

        boolean inQuote = false;
        StringBuilder currentToken = new StringBuilder();
        String currentKey = null;

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);

            if (c == '\\') {
                // If it is an escape char, append the next character directly
                if (i + 1 < inner.length()) {
                    currentToken.append(inner.charAt(i + 1));
                    i++; // skip next char
                }
                continue;
            }

            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }

            if (inQuote) {
                currentToken.append(c);
            } else {
                if (c == ':') {
                    currentKey = currentToken.toString().trim();
                    currentToken.setLength(0);
                } else if (c == ',') {
                    if (currentKey != null) {
                        result.put(currentKey, currentToken.toString().trim());
                        currentKey = null;
                    }
                    currentToken.setLength(0);
                } else if (!Character.isWhitespace(c)) {
                    currentToken.append(c);
                }
            }
        }

        // Add the last key-value pair remaining in builders
        if (currentKey != null) {
            result.put(currentKey, currentToken.toString().trim());
        }

        return result;
    }

    /**
     * Escape special characters for valid JSON output.
     */
    public static String escape(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
