package mx.uaeh.syllabus.logica;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Reglas de cálculo, redondeo institucional, orden y filtros. */
public final class Calculos {
    public static final BigDecimal SIETE = new BigDecimal("7.0");
    public static final BigDecimal SIETE_CINCO = new BigDecimal("7.5");

    private Calculos() {
    }

    /** Elimina el ruido de punto flotante (IEEE 754) antes de comparar o redondear. */
    public static BigDecimal limpiar10(double x) {
        return new BigDecimal(x).setScale(10, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal redondear2(double x) {
        return limpiar10(x).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal truncar2(double x) {
        return limpiar10(x).setScale(2, RoundingMode.DOWN);
    }

    /** Redondeo institucional: >= 7.0 mitad hacia arriba, < 7.0 truncado. */
    public static BigDecimal mostrar(double x) {
        return limpiar10(x).compareTo(SIETE) >= 0 ? redondear2(x) : truncar2(x);
    }

    public static BigDecimal mostrar(Double x) {
        return x == null ? null : mostrar(x.doubleValue());
    }

    /**
     * Calificación final que se asienta en el acta (entera, regla del .5): con promedio de 7.0 o más se redondea
     * al entero más próximo (8.5 → 9); con menos de 7.0 nunca sube, se trunca (6.9 → 6) y la materia queda reprobada.
     */
    public static Integer calificacionFinal(Double bruto) {
        if (bruto == null) {
            return null;
        }
        BigDecimal x = limpiar10(bruto);
        return x.setScale(0, x.compareTo(SIETE) >= 0 ? RoundingMode.HALF_UP : RoundingMode.DOWN).intValue();
    }

    /** Promedio de las calificaciones finales (enteras) a dos decimales: el del kárdex. */
    public static BigDecimal promedioFinales(List<Double> brutos) {
        List<Integer> finales = brutos.stream().filter(java.util.Objects::nonNull).map(Calculos::calificacionFinal).toList();
        if (finales.isEmpty()) {
            return null;
        }
        return redondear2(finales.stream().mapToInt(Integer::intValue).sum() / (double) finales.size());
    }

    public static boolean esUno(double suma) {
        return limpiar10(suma).compareTo(BigDecimal.ONE) == 0;
    }

    public static boolean pasaDeUno(double suma) {
        return limpiar10(suma).compareTo(BigDecimal.ONE) > 0;
    }

    public static String normalizado(double x) {
        return limpiar10(x).stripTrailingZeros().toPlainString();
    }

    public static String evaluarEstatusCurso(double bruto) {
        return limpiar10(bruto).compareTo(SIETE) >= 0 ? "APROBADO" : "REPROBADO";
    }

    private static boolean reprobado(double c) {
        return limpiar10(c).compareTo(SIETE) < 0;
    }

    public static String evaluarRiesgo(List<Double> promediosCursos, double promGeneral) {
        BigDecimal pg = limpiar10(promGeneral);
        if (promediosCursos.stream().anyMatch(Calculos::reprobado) || pg.compareTo(SIETE) < 0) {
            return "ALTO";
        }
        if (pg.compareTo(SIETE) >= 0 && pg.compareTo(SIETE_CINCO) <= 0) {
            return "MEDIO";
        }
        return "SIN_RIESGO";
    }

    public static String motivoRiesgo(List<Double> promediosCursos, double promGeneral) {
        long reprobados = promediosCursos.stream().filter(Calculos::reprobado).count();
        if (reprobados > 0) {
            return reprobados + " curso(s) reprobado(s)";
        }
        if (limpiar10(promGeneral).compareTo(SIETE) < 0) {
            return "Promedio general menor a 7.0";
        }
        if (limpiar10(promGeneral).compareTo(SIETE_CINCO) <= 0) {
            return "Promedio general entre 7.0 y 7.5";
        }
        return "Sin riesgo";
    }

    public static double suma(List<Double> xs) {
        double s = 0;
        for (double x : xs) {
            s += x;
        }
        return s;
    }

    /** Estadísticas de una lista de promedios brutos. */
    public static Map<String, Object> estadisticas(List<Double> promedios) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (promedios.isEmpty()) {
            r.put("estudiantes", 0);
            r.put("promedio", null);
            r.put("maximo", null);
            r.put("minimo", null);
            r.put("aprobados", 0);
            r.put("reprobados", 0);
            r.put("aprobacion", null);
            return r;
        }
        int aprobados = (int) promedios.stream().filter(p -> !reprobado(p)).count();
        r.put("estudiantes", promedios.size());
        r.put("promedio", mostrar(suma(promedios) / promedios.size()));
        r.put("maximo", mostrar(promedios.stream().mapToDouble(Double::doubleValue).max().orElseThrow()));
        r.put("minimo", mostrar(promedios.stream().mapToDouble(Double::doubleValue).min().orElseThrow()));
        r.put("aprobados", aprobados);
        r.put("reprobados", promedios.size() - aprobados);
        r.put("aprobacion", redondear2((double) aprobados / promedios.size() * 100));
        return r;
    }

    /** Texto sin acentos y en minúsculas; NFD también convierte la ñ en n. */
    public static String claveTexto(String s) {
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{Mn}", "");
    }

    private static Comparable<?> clave(Map<String, Object> fila, String columna) {
        Object v = fila.get(columna);
        if (columna.equals("apellidos") || columna.equals("nombre")) {
            return claveTexto(String.valueOf(v));
        }
        if (v == null) {
            return BigDecimal.ONE.negate();
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return String.valueOf(v);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int comparar(Comparable a, Comparable b) {
        if (a.getClass() != b.getClass()) {
            return a.toString().compareTo(b.toString());
        }
        return a.compareTo(b);
    }

    /** Ordena por la columna indicada; el desempate siempre es num_cuenta ascendente. */
    public static List<Map<String, Object>> ordenar(List<Map<String, Object>> filas, String columna, boolean descendente) {
        List<Map<String, Object>> porCuenta = new ArrayList<>(filas);
        porCuenta.sort(Comparator.comparing(f -> String.valueOf(f.get("num_cuenta"))));
        porCuenta.sort((a, b) -> comparar(clave(a, columna), clave(b, columna)));
        if (!descendente) {
            return porCuenta;
        }
        // para que el desempate siga ascendente se agrupa por valor y se invierten los grupos
        List<List<Map<String, Object>>> grupos = new ArrayList<>();
        Comparable<?> anterior = null;
        for (Map<String, Object> f : porCuenta) {
            Comparable<?> k = clave(f, columna);
            if (grupos.isEmpty() || comparar(anterior, k) != 0) {
                grupos.add(new ArrayList<>());
            }
            grupos.get(grupos.size() - 1).add(f);
            anterior = k;
        }
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (int i = grupos.size() - 1; i >= 0; i--) {
            resultado.addAll(grupos.get(i));
        }
        return resultado;
    }

    public static List<Map<String, Object>> ordenar(List<Map<String, Object>> filas) {
        return ordenar(filas, "apellidos", false);
    }

    public static Predicate<Map<String, Object>> filtroEstado(String estado) {
        return switch (estado) {
            case "aprobado" -> f -> "APROBADO".equals(f.get("estatus"));
            case "reprobado" -> f -> "REPROBADO".equals(f.get("estatus"));
            case "pendiente" -> f -> "PENDIENTE".equals(f.get("estatus"));
            case "riesgo" -> f -> "ALTO".equals(f.get("riesgo")) || "MEDIO".equals(f.get("riesgo"));
            default -> f -> true;
        };
    }

    public static Predicate<Map<String, Object>> filtroRango(Double minima, Double maxima) {
        return f -> {
            Object p = f.get("promedio");
            if (p == null) {
                return minima == null && maxima == null;
            }
            BigDecimal v = new BigDecimal(p.toString());
            return (minima == null || v.compareTo(BigDecimal.valueOf(minima)) >= 0)
                    && (maxima == null || v.compareTo(BigDecimal.valueOf(maxima)) <= 0);
        };
    }
}
