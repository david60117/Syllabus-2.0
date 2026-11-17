package mx.uaeh.syllabus.archivos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mx.uaeh.syllabus.modelo.RegistroError;

/**
 * Lector y escritor de JSON (RFC 8259) sin dependencias externas.
 * Objetos → LinkedHashMap, arreglos → ArrayList, números → Double, true/false → Boolean.
 */
public final class Json {
    private final String texto;
    private int pos;

    private Json(String texto) {
        this.texto = texto;
    }

    /** Error de sintaxis con línea y columna, como el que reporta Python. */
    public static class ErrorSintaxis extends RuntimeException {
        public ErrorSintaxis(String mensaje) {
            super(mensaje);
        }
    }

    public static Object leer(String texto) {
        Json p = new Json(texto.startsWith("﻿") ? texto.substring(1) : texto);
        p.espacios();
        Object valor = p.valor();
        p.espacios();
        if (p.pos < p.texto.length()) {
            throw p.error("datos extra");
        }
        return valor;
    }

    private ErrorSintaxis error(String que) {
        int linea = 1;
        int columna = 1;
        for (int i = 0; i < Math.min(pos, texto.length()); i++) {
            if (texto.charAt(i) == '\n') {
                linea++;
                columna = 1;
            } else {
                columna++;
            }
        }
        return new ErrorSintaxis(que + ": línea " + linea + ", columna " + columna);
    }

    private void espacios() {
        while (pos < texto.length() && " \t\r\n".indexOf(texto.charAt(pos)) >= 0) {
            pos++;
        }
    }

    private char actual() {
        if (pos >= texto.length()) {
            throw error("fin inesperado");
        }
        return texto.charAt(pos);
    }

    private void esperar(char c) {
        if (actual() != c) {
            throw error("se esperaba '" + c + "'");
        }
        pos++;
    }

    private Object valor() {
        char c = actual();
        switch (c) {
            case '{':
                return objeto();
            case '[':
                return arreglo();
            case '"':
                return cadena();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || Character.isDigit(c)) {
                    return numero();
                }
                throw error("valor inesperado");
        }
    }

    private Object literal(String palabra, Object valor) {
        if (!texto.startsWith(palabra, pos)) {
            throw error("valor inesperado");
        }
        pos += palabra.length();
        return valor;
    }

    private Map<String, Object> objeto() {
        Map<String, Object> m = new LinkedHashMap<>();
        esperar('{');
        espacios();
        if (actual() == '}') {
            pos++;
            return m;
        }
        while (true) {
            espacios();
            String k = cadena();
            espacios();
            esperar(':');
            espacios();
            m.put(k, valor());
            espacios();
            if (actual() == ',') {
                pos++;
                continue;
            }
            esperar('}');
            return m;
        }
    }

    private List<Object> arreglo() {
        List<Object> l = new ArrayList<>();
        esperar('[');
        espacios();
        if (actual() == ']') {
            pos++;
            return l;
        }
        while (true) {
            espacios();
            l.add(valor());
            espacios();
            if (actual() == ',') {
                pos++;
                continue;
            }
            esperar(']');
            return l;
        }
    }

    private String cadena() {
        esperar('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = actual();
            pos++;
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = actual();
            pos++;
            switch (e) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (pos + 4 > texto.length()) {
                        throw error("escape incompleto");
                    }
                    sb.append((char) Integer.parseInt(texto.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> sb.append(e);
            }
        }
    }

    private Double numero() {
        int inicio = pos;
        while (pos < texto.length() && "+-0123456789.eE".indexOf(texto.charAt(pos)) >= 0) {
            pos++;
        }
        try {
            return Double.valueOf(texto.substring(inicio, pos));
        } catch (NumberFormatException e) {
            pos = inicio;
            throw error("número inválido");
        }
    }

    // ---------------------------------------------------------------- escritura
    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, valor, 0);
        return sb.toString();
    }

    private static void sangria(StringBuilder sb, int n) {
        sb.append('\n').append("  ".repeat(n));
    }

    private static void escribir(StringBuilder sb, Object v, int nivel) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            cadena(sb, s);
        } else if (v instanceof BigDecimal d) {
            sb.append(d.toPlainString());
        } else if (v instanceof Double d) {
            sb.append(Double.toString(d));
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof RegistroError e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("caso", e.caso());
            m.put("tipo", e.tipo());
            m.put("codigo", e.codigo());
            m.put("detalle", e.detalle());
            m.put("registro", e.registro());
            escribir(sb, m, nivel);
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                sb.append(primero ? "" : ",");
                primero = false;
                sangria(sb, nivel + 1);
                cadena(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                escribir(sb, e.getValue(), nivel + 1);
            }
            sangria(sb, nivel);
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            if (c.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            boolean primero = true;
            for (Object x : c) {
                sb.append(primero ? "" : ",");
                primero = false;
                sangria(sb, nivel + 1);
                escribir(sb, x, nivel + 1);
            }
            sangria(sb, nivel);
            sb.append(']');
        } else {
            cadena(sb, v.toString());
        }
    }

    private static void cadena(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
