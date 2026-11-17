package mx.uaeh.syllabus.archivos;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import mx.uaeh.syllabus.modelo.ErrorValidacion;

/** Lectura del JSON de entrada y exportación de reportes a JSON y CSV. */
public final class Archivos {
    public static final List<String> LLAVES_RAIZ = List.of("estudiantes", "cursos", "parciales", "evaluaciones",
            "inscripciones", "calificaciones");

    private Archivos() {
    }

    /** Verifica la estructura general; la validación registro por registro la hace el gestor. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> validarEstructura(Object datos) {
        if (!(datos instanceof Map)) {
            throw new ErrorValidacion("ESTRUCTURA_INVALIDA", "la raíz del JSON debe ser un objeto");
        }
        Map<String, Object> m = (Map<String, Object>) datos;
        List<String> faltan = LLAVES_RAIZ.stream().filter(k -> !m.containsKey(k)).toList();
        if (!faltan.isEmpty()) {
            throw new ErrorValidacion("ESTRUCTURA_INVALIDA", "faltan las llaves " + String.join(", ", faltan));
        }
        List<String> noListas = LLAVES_RAIZ.stream().filter(k -> !(m.get(k) instanceof List)).toList();
        if (!noListas.isEmpty()) {
            throw new ErrorValidacion("ESTRUCTURA_INVALIDA", "deben ser listas: " + String.join(", ", noListas));
        }
        return m;
    }

    public static Map<String, Object> cargarJson(Path ruta) {
        String texto;
        try {
            texto = Files.readString(ruta, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new ErrorValidacion("ESTRUCTURA_INVALIDA", "no existe el archivo " + ruta);
        } catch (IOException e) {
            throw new ErrorValidacion("ESTRUCTURA_INVALIDA", "no se pudo leer " + ruta + ": " + e.getMessage());
        }
        try {
            return validarEstructura(Json.leer(texto));
        } catch (Json.ErrorSintaxis e) {
            throw new ErrorValidacion("ESTRUCTURA_INVALIDA", "JSON mal formado en " + ruta.getFileName() + ", " + e.getMessage());
        }
    }

    public static Path exportarJson(Object datos, Path ruta) throws IOException {
        Files.createDirectories(ruta.toAbsolutePath().getParent());
        Files.writeString(ruta, Json.escribir(datos), StandardCharsets.UTF_8);
        return ruta;
    }

    private static String celda(Object v) {
        String s = v == null ? "" : v instanceof BigDecimal d ? d.setScale(2).toPlainString() : String.valueOf(v);
        return s.matches("(?s).*[\",\r\n].*") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    /** CSV como lo genera el módulo csv de Python: comillas solo cuando hacen falta y fin de línea \r\n. */
    public static String aCsv(List<Map<String, Object>> filas) {
        Set<String> columnas = new LinkedHashSet<>();
        filas.forEach(f -> columnas.addAll(f.keySet()));
        List<String> lineas = new ArrayList<>();
        lineas.add(columnas.stream().map(Archivos::celda).collect(Collectors.joining(",")));
        filas.forEach(f -> lineas.add(columnas.stream().map(c -> celda(f.get(c))).collect(Collectors.joining(","))));
        return String.join("\r\n", lineas) + "\r\n";
    }

    public static Path exportarCsv(List<Map<String, Object>> filas, Path ruta) throws IOException {
        Files.createDirectories(ruta.toAbsolutePath().getParent());
        Files.writeString(ruta, "﻿" + aCsv(filas), StandardCharsets.UTF_8); // BOM para que Excel muestre los acentos
        return ruta;
    }

    public static List<Path> exportarReportes(Map<String, List<Map<String, Object>>> reportes, List<?> errores, Path carpeta)
            throws IOException {
        List<Path> rutas = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : reportes.entrySet()) {
            rutas.add(exportarJson(e.getValue(), carpeta.resolve(e.getKey() + ".json")));
            rutas.add(exportarCsv(e.getValue(), carpeta.resolve(e.getKey() + ".csv")));
        }
        rutas.add(exportarJson(errores, carpeta.resolve("errores.json")));
        return rutas;
    }

    /** Renglones de CSV (sin encabezado de columnas): así se exporta un reporte institucional. */
    public static String aCsvRenglones(List<List<Object>> renglones) {
        StringBuilder sb = new StringBuilder();
        renglones.forEach(r -> sb.append(r.stream().map(Archivos::celda).collect(Collectors.joining(","))).append("\r\n"));
        return sb.toString();
    }

    /** Reporte institucional con encabezado: PDF (igual que en pantalla), CSV (como se ve impreso) o JSON. */
    public static Path exportarDocumento(mx.uaeh.syllabus.documentos.Documento doc, Path ruta, String formato, String acento)
            throws IOException {
        if ("pdf".equals(formato)) {
            return mx.uaeh.syllabus.documentos.Pdf.guardar(doc, ruta, acento);
        }
        if ("json".equals(formato)) {
            return exportarJson(mx.uaeh.syllabus.documentos.Documentos.aJson(doc), ruta);
        }
        Files.createDirectories(ruta.toAbsolutePath().getParent());
        Files.writeString(ruta, "\ufeff" + aCsvRenglones(mx.uaeh.syllabus.documentos.Documentos.aTabla(doc)), StandardCharsets.UTF_8);
        return ruta;
    }
}
