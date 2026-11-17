package mx.uaeh.syllabus.logica;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import mx.uaeh.syllabus.modelo.ErrorValidacion;

/** Reglas de formato y tipo de cada campo. Lanza ErrorValidacion con el código correspondiente. */
public final class Validacion {
    public static final Pattern RE_CUENTA = Pattern.compile("^\\d{6}$");
    public static final Pattern RE_GRUPO = Pattern.compile("^\\d{3}$");
    public static final Pattern RE_CURSO = Pattern.compile("^C\\d{2,}$");
    public static final Pattern RE_PARCIAL = Pattern.compile("^P\\d{2,}$");
    public static final Pattern RE_EVALUACION = Pattern.compile("^EV\\d{2,}$");
    public static final Pattern RE_DOCENTE = Pattern.compile("^D\\d{2,}$");
    public static final Pattern RE_NOMBRE = Pattern.compile("^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$");

    private Validacion() {
    }

    /** Número como lo imprime Python (8 → "8.0") para que los mensajes coincidan entre versiones. */
    public static String pyNum(double v) {
        return Double.toString(v);
    }

    private static String repr(Object valor) {
        if (valor == null) {
            return "None";
        }
        if (valor instanceof String s) {
            return "'" + s + "'";
        }
        if (valor instanceof Boolean b) {
            return b ? "True" : "False";
        }
        return String.valueOf(valor);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> campos(Object registro, String... nombres) {
        if (!(registro instanceof Map)) {
            throw new ErrorValidacion("TIPO_INVALIDO", "el registro debe ser un objeto");
        }
        Map<String, Object> r = (Map<String, Object>) registro;
        List<String> faltan = new ArrayList<>();
        for (String n : nombres) {
            Object v = r.get(n);
            if (v == null || (v instanceof String s && s.isBlank())) {
                faltan.add(n);
            }
        }
        if (!faltan.isEmpty()) {
            throw new ErrorValidacion("CAMPO_FALTANTE", "falta " + String.join(", ", faltan));
        }
        return r;
    }

    public static String texto(Object valor, String nombre) {
        if (!(valor instanceof String s)) {
            throw new ErrorValidacion("TIPO_INVALIDO", nombre + " debe ser texto");
        }
        return s.strip();
    }

    public static double numero(Object valor, String nombre) {
        if (!(valor instanceof Number n) || Double.isNaN(n.doubleValue()) || Double.isInfinite(n.doubleValue())) {
            throw new ErrorValidacion("TIPO_INVALIDO", nombre + " debe ser numérico: " + repr(valor));
        }
        return n.doubleValue();
    }

    public static String formato(String valor, Pattern patron, String nombre) {
        if (!patron.matcher(valor).matches()) {
            throw new ErrorValidacion("FORMATO_INVALIDO", nombre + " con formato inválido: " + valor);
        }
        return valor;
    }

    public static void decimales(double valor, int maximo, String nombre) {
        double escala = Math.pow(10, maximo);
        if (Math.abs(Math.round(valor * escala) / escala - valor) > 1e-9) {
            throw new ErrorValidacion("FORMATO_INVALIDO", nombre + " admite máximo " + maximo + " decimal(es): " + pyNum(valor));
        }
    }

    public static double peso(Object valor, String nombre) {
        double v = numero(valor, nombre);
        if (!(v > 0 && v <= 1)) {
            throw new ErrorValidacion("PORCENTAJES_INVALIDOS", nombre + " debe ser mayor que 0 y hasta 1: " + pyNum(v));
        }
        decimales(v, 2, nombre);
        return v;
    }

    public static double calificacion(Object valor) {
        if (valor == null) {
            throw new ErrorValidacion("CAMPO_FALTANTE", "falta valor");
        }
        double v = numero(valor, "valor");
        if (!(v >= 0 && v <= 10)) {
            throw new ErrorValidacion("CALIFICACION_FUERA_DE_RANGO", "la calificación debe estar entre 0 y 10: " + pyNum(v));
        }
        decimales(v, 1, "valor");
        return v;
    }
}
