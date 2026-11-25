package org.imprime.ai.api.util;

import org.imprime.ai.api.http.response.model3d.ScadParameterDefinition;
import org.imprime.ai.api.model.enums.ScadParameterType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analisador e sanitizador de parâmetros top-level em scripts OpenSCAD (.scad).
 */
public final class ScadParameterParser {
    private static final int MAX_PARAMETERS = 40;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");
    private static final Pattern UNIT_SUFFIX_PATTERN = Pattern.compile("\\(([^()]{1,12})\\)\\s*$");
    private static final Pattern HIDDEN_SECTION_PATTERN = Pattern.compile("(?i)/\\*\\s*\\[\\s*hidden\\s*]\\s*\\*/");
    private static final Pattern STYLE_OPTION_PATTERN = Pattern.compile("(?i)^([^:]+:style=[^:]+)(?::.*)?$");

    private ScadParameterParser() {
    }

    /**
     * Extrai parâmetros top-level de um script OpenSCAD mantendo ordem e anotações do Customizer.
     */
    public static List<ScadParameterDefinition> parse(String scadCode) {
        if (scadCode == null || scadCode.isBlank()) {
            return List.of();
        }

        Map<String, ScadParameterDefinition> collected = new LinkedHashMap<>();
        List<String> pendingComments = new ArrayList<>();
        String[] lines = scadCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        boolean inBlockComment = false;
        int braceDepth = 0;

        for (String rawLine : lines) {
            if (collected.size() >= MAX_PARAMETERS) {
                break;
            }

            if (HIDDEN_SECTION_PATTERN.matcher(rawLine).find()) {
                break;
            }

            String trimmed = rawLine.trim();
            if (inBlockComment) {
                int endIdx = rawLine.indexOf("*/");
                if (endIdx >= 0) {
                    inBlockComment = false;
                    trimmed = rawLine.substring(endIdx + 2).trim();
                } else {
                    continue;
                }
            }

            if (trimmed.isEmpty()) {
                pendingComments.clear();
                continue;
            }

            if (trimmed.startsWith("/*")) {
                pendingComments.clear();
                int endIdx = trimmed.indexOf("*/", 2);
                if (endIdx < 0) {
                    inBlockComment = true;
                    continue;
                }
                trimmed = trimmed.substring(endIdx + 2).trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
            }

            if (trimmed.startsWith("//")) {
                String commentBody = trimmed.substring(2).trim();
                if (!commentBody.isEmpty() && !commentBody.startsWith("[")) {
                    pendingComments.add(commentBody);
                }
                continue;
            }

            if (braceDepth == 0) {
                ScadParameterDefinition parsed = tryParseAssignment(trimmed, pendingComments);
                if (parsed != null && !collected.containsKey(parsed.name())) {
                    collected.put(parsed.name(), parsed);
                }
            }

            pendingComments.clear();
            braceDepth = updateBraceDepth(trimmed, braceDepth);
        }

        return List.copyOf(collected.values());
    }

    /**
     * Valida e normaliza uma lista explícita de definições de parâmetros informada pelo cliente.
     */
    public static List<ScadParameterDefinition> sanitize(List<ScadParameterDefinition> rawParameters) {
        if (rawParameters == null || rawParameters.isEmpty()) {
            return List.of();
        }

        Map<String, ScadParameterDefinition> sanitized = new LinkedHashMap<>();
        for (ScadParameterDefinition raw : rawParameters) {
            if (raw == null || raw.name() == null) {
                continue;
            }
            String name = raw.name().trim();
            if (!IDENTIFIER_PATTERN.matcher(name).matches() || name.startsWith("$") || sanitized.containsKey(name)) {
                continue;
            }

            List<String> options = sanitizeOptions(raw.options());
            ScadParameterType type = raw.type();
            if (type == null) {
                if (!options.isEmpty()) {
                    type = ScadParameterType.SELECT;
                } else if (raw.defaultValue() instanceof Boolean) {
                    type = ScadParameterType.BOOLEAN;
                } else if (raw.defaultValue() instanceof String str && !NUMBER_PATTERN.matcher(str.trim()).matches()) {
                    type = ScadParameterType.STRING;
                } else {
                    type = ScadParameterType.NUMBER;
                }
            }

            if (type == ScadParameterType.SELECT && options.isEmpty()) {
                type = ScadParameterType.STRING;
            }

            String label = raw.label() != null && !raw.label().isBlank()
                    ? raw.label().trim()
                    : humanizeIdentifier(name);
            String unit = raw.unit() != null && !raw.unit().isBlank()
                    ? raw.unit().trim()
                    : extractUnit(label);

            BigDecimal min = raw.min();
            BigDecimal max = raw.max();
            BigDecimal step = raw.step();
            if (type == ScadParameterType.NUMBER) {
                if (min != null && max != null && min.compareTo(max) > 0) {
                    BigDecimal tmp = min;
                    min = max;
                    max = tmp;
                }
                if (step != null && step.compareTo(BigDecimal.ZERO) <= 0) {
                    step = null;
                }
            } else {
                min = null;
                max = null;
                step = null;
            }

            Object defaultValue = normalizeDefaultValue(raw.defaultValue(), type, options);
            sanitized.put(name, new ScadParameterDefinition(
                    name,
                    label,
                    type,
                    defaultValue,
                    min,
                    max,
                    step,
                    unit,
                    type == ScadParameterType.SELECT ? options : null
            ));

            if (sanitized.size() >= MAX_PARAMETERS) {
                break;
            }
        }

        return List.copyOf(sanitized.values());
    }

    private static ScadParameterDefinition tryParseAssignment(String line, List<String> pendingComments) {
        int eqIdx = line.indexOf('=');
        if (eqIdx <= 0) {
            return null;
        }

        String name = line.substring(0, eqIdx).trim();
        if (name.startsWith("$") || !IDENTIFIER_PATTERN.matcher(name).matches()) {
            return null;
        }

        String rhs = line.substring(eqIdx + 1).trim();
        if (rhs.isEmpty()) {
            return null;
        }

        ParsedRhs parsedRhs = parseRhsLiteral(rhs);
        if (parsedRhs == null) {
            return null;
        }

        String inlineComment = parsedRhs.inlineComment();
        String bracketSpec = extractBracketSpec(inlineComment);

        String label = !pendingComments.isEmpty()
                ? pendingComments.getLast()
                : null;
        if ((label == null || label.isBlank()) && inlineComment != null && bracketSpec == null && !inlineComment.isBlank()) {
            label = inlineComment;
        }
        if (label == null || label.isBlank()) {
            label = humanizeIdentifier(name);
        }

        String unit = extractUnit(label);

        if (parsedRhs.kind() == LiteralKind.BOOLEAN) {
            return new ScadParameterDefinition(
                    name,
                    label,
                    ScadParameterType.BOOLEAN,
                    parsedRhs.value(),
                    null,
                    null,
                    null,
                    unit,
                    null
            );
        }

        if (parsedRhs.kind() == LiteralKind.STRING) {
            List<String> options = parseOptionsSpec(bracketSpec);
            if (!options.isEmpty()) {
                String current = String.valueOf(parsedRhs.value());
                if (!options.contains(current)) {
                    List<String> merged = new ArrayList<>(options.size() + 1);
                    merged.add(current);
                    merged.addAll(options);
                    options = List.copyOf(merged);
                }
                return new ScadParameterDefinition(
                        name,
                        label,
                        ScadParameterType.SELECT,
                        current,
                        null,
                        null,
                        null,
                        unit,
                        options
                );
            }
            return new ScadParameterDefinition(
                    name,
                    label,
                    ScadParameterType.STRING,
                    parsedRhs.value(),
                    null,
                    null,
                    null,
                    unit,
                    null
            );
        }

        BigDecimal numValue = (BigDecimal) parsedRhs.value();
        List<String> numericOptions = parseOptionsSpec(bracketSpec);
        if (!numericOptions.isEmpty()) {
            String current = numValue.stripTrailingZeros().toPlainString();
            return new ScadParameterDefinition(
                    name,
                    label,
                    ScadParameterType.SELECT,
                    current,
                    null,
                    null,
                    null,
                    unit,
                    numericOptions
            );
        }

        RangeSpec range = parseRangeSpec(bracketSpec);
        return new ScadParameterDefinition(
                name,
                label,
                ScadParameterType.NUMBER,
                numValue,
                range != null ? range.min() : null,
                range != null ? range.max() : null,
                range != null ? range.step() : null,
                unit,
                null
        );
    }

    private static ParsedRhs parseRhsLiteral(String rhs) {
        if (rhs.startsWith("\"")) {
            StringBuilder valueBuilder = new StringBuilder();
            boolean escaped = false;
            int closeQuoteIdx = -1;
            for (int i = 1; i < rhs.length(); i++) {
                char c = rhs.charAt(i);
                if (escaped) {
                    valueBuilder.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    closeQuoteIdx = i;
                    break;
                } else {
                    valueBuilder.append(c);
                }
            }
            if (closeQuoteIdx < 0) {
                return null;
            }
            String remainder = rhs.substring(closeQuoteIdx + 1).trim();
            if (!remainder.startsWith(";")) {
                return null;
            }
            String inlineComment = extractLineComment(remainder.substring(1));
            return new ParsedRhs(LiteralKind.STRING, valueBuilder.toString(), inlineComment);
        }

        int semiIdx = rhs.indexOf(';');
        if (semiIdx <= 0) {
            return null;
        }
        String literal = rhs.substring(0, semiIdx).trim();
        String inlineComment = extractLineComment(rhs.substring(semiIdx + 1));

        if ("true".equalsIgnoreCase(literal) || "false".equalsIgnoreCase(literal)) {
            return new ParsedRhs(LiteralKind.BOOLEAN, Boolean.parseBoolean(literal.toLowerCase(Locale.ROOT)), inlineComment);
        }

        if (NUMBER_PATTERN.matcher(literal).matches()) {
            return new ParsedRhs(LiteralKind.NUMBER, new BigDecimal(literal), inlineComment);
        }

        return null;
    }

    private static String extractLineComment(String afterSemicolon) {
        if (afterSemicolon == null) {
            return null;
        }
        String trimmed = afterSemicolon.trim();
        if (trimmed.startsWith("//")) {
            return trimmed.substring(2).trim();
        }
        return null;
    }

    private static String extractBracketSpec(String inlineComment) {
        if (inlineComment == null || inlineComment.isBlank()) {
            return null;
        }
        int openIdx = inlineComment.indexOf('[');
        int closeIdx = inlineComment.lastIndexOf(']');
        if (openIdx >= 0 && closeIdx > openIdx) {
            return inlineComment.substring(openIdx + 1, closeIdx).trim();
        }
        return null;
    }

    private static List<String> splitOptionsPreservingParens(String bracketSpec) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < bracketSpec.length(); i++) {
            char ch = bracketSpec.charAt(i);
            if (inSingleQuote || inDoubleQuote) {
                current.append(ch);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (inSingleQuote && ch == '\'') {
                    inSingleQuote = false;
                } else if (inDoubleQuote && ch == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }

            if (ch == '\'') {
                inSingleQuote = true;
                current.append(ch);
            } else if (ch == '"') {
                inDoubleQuote = true;
                current.append(ch);
            } else if (ch == '(') {
                parenDepth++;
                current.append(ch);
            } else if (ch == ')') {
                parenDepth = Math.max(0, parenDepth - 1);
                current.append(ch);
            } else if (ch == ',' && parenDepth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        parts.add(current.toString());
        return parts;
    }

    private static List<String> parseOptionsSpec(String bracketSpec) {
        if (bracketSpec == null || bracketSpec.isBlank() || !bracketSpec.contains(",")) {
            return List.of();
        }
        List<String> rawParts = splitOptionsPreservingParens(bracketSpec);
        if (rawParts.size() <= 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        for (String rawPart : rawParts) {
            String trimmed = rawPart.trim();
            Matcher styleMatcher = STYLE_OPTION_PATTERN.matcher(trimmed);
            if (styleMatcher.matches()) {
                trimmed = styleMatcher.group(1).trim();
            } else {
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx > 0) {
                    trimmed = trimmed.substring(0, colonIdx).trim();
                }
            }
            if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                    || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
            if (!trimmed.isEmpty() && !options.contains(trimmed)) {
                options.add(trimmed);
            }
        }
        return options;
    }

    private static RangeSpec parseRangeSpec(String bracketSpec) {
        if (bracketSpec == null || bracketSpec.isBlank() || bracketSpec.contains(",") || !bracketSpec.contains(":")) {
            return null;
        }
        String[] parts = bracketSpec.split(":");
        try {
            if (parts.length == 2) {
                BigDecimal min = new BigDecimal(parts[0].trim());
                BigDecimal max = new BigDecimal(parts[1].trim());
                if (min.compareTo(max) > 0) {
                    BigDecimal tmp = min;
                    min = max;
                    max = tmp;
                }
                BigDecimal step = (min.scale() > 0 || max.scale() > 0)
                        ? new BigDecimal("0.1")
                        : BigDecimal.ONE;
                return new RangeSpec(min, step, max);
            }
            if (parts.length == 3) {
                BigDecimal min = new BigDecimal(parts[0].trim());
                BigDecimal step = new BigDecimal(parts[1].trim());
                BigDecimal max = new BigDecimal(parts[2].trim());
                if (min.compareTo(max) > 0) {
                    BigDecimal tmp = min;
                    min = max;
                    max = tmp;
                }
                if (step.compareTo(BigDecimal.ZERO) <= 0) {
                    step = BigDecimal.ONE;
                }
                return new RangeSpec(min, step, max);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private static int updateBraceDepth(String line, int currentDepth) {
        int depth = currentDepth;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                break;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return depth;
    }

    private static List<String> sanitizeOptions(List<String> rawOptions) {
        if (rawOptions == null || rawOptions.isEmpty()) {
            return List.of();
        }
        return rawOptions.stream()
                .filter(opt -> opt != null && !opt.isBlank())
                .map(String::trim)
                .distinct()
                .limit(30)
                .toList();
    }

    private static Object normalizeDefaultValue(Object value, ScadParameterType type, List<String> options) {
        return switch (type) {
            case BOOLEAN -> {
                if (value instanceof Boolean b) {
                    yield b;
                }
                yield value != null && Boolean.parseBoolean(String.valueOf(value).trim());
            }
            case STRING -> value != null ? String.valueOf(value) : "";
            case SELECT -> {
                String str = value != null ? String.valueOf(value).trim() : "";
                if (!str.isEmpty() && options.contains(str)) {
                    yield str;
                }
                yield options.isEmpty() ? str : options.getFirst();
            }
            case NUMBER -> {
                if (value instanceof BigDecimal bd) {
                    yield bd;
                }
                if (value instanceof Number n) {
                    yield new BigDecimal(n.toString());
                }
                if (value != null) {
                    String s = String.valueOf(value).trim();
                    if (NUMBER_PATTERN.matcher(s).matches()) {
                        yield new BigDecimal(s);
                    }
                }
                yield BigDecimal.ZERO;
            }
        };
    }

    private static String extractUnit(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        Matcher matcher = UNIT_SUFFIX_PATTERN.matcher(label.trim());
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String humanizeIdentifier(String name) {
        String spaced = name
                .replace('_', ' ')
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .trim();
        if (spaced.isEmpty()) {
            return name;
        }
        String[] words = spaced.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                sb.append(w.substring(1));
            }
        }
        return sb.toString();
    }

    private enum LiteralKind {
        NUMBER,
        BOOLEAN,
        STRING
    }

    private record ParsedRhs(LiteralKind kind, Object value, String inlineComment) {
    }

    private record RangeSpec(BigDecimal min, BigDecimal step, BigDecimal max) {
    }
}
