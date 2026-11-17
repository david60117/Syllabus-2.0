package mx.uaeh.syllabus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import mx.uaeh.syllabus.archivos.Archivos;
import mx.uaeh.syllabus.archivos.Json;
import mx.uaeh.syllabus.logica.Concurrencia;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.logica.Reportes;
import mx.uaeh.syllabus.modelo.ErrorValidacion;

class ArchivosTest {
    static final Path DATOS = Path.of("..", "datos");

    private static Path escribir(String texto) throws Exception {
        Path p = Files.createTempFile("syllabus", ".json");
        Files.writeString(p, texto);
        return p;
    }

    private static String codigo(Path p) {
        return assertThrows(ErrorValidacion.class, () -> Archivos.cargarJson(p)).getCodigo();
    }

    @Test
    void cargaEjemploValido() {
        Map<String, Object> d = Archivos.cargarJson(DATOS.resolve("ejemplo_entrada.json"));
        assertEquals(4, ((List<?>) d.get("estudiantes")).size());
        assertEquals(3, ((List<?>) d.get("parciales")).size());
    }

    @Test
    void erroresDeEstructura() throws Exception {
        assertEquals("ESTRUCTURA_INVALIDA", codigo(Path.of("no_existe.json")));
        assertEquals("ESTRUCTURA_INVALIDA", codigo(escribir("{\"estudiantes\": [")));
        assertEquals("ESTRUCTURA_INVALIDA", codigo(escribir("[]")));
        ErrorValidacion e = assertThrows(ErrorValidacion.class, () -> Archivos.cargarJson(escribir("{\"estudiantes\": [], \"cursos\": []}")));
        assertTrue(e.getDetalle().contains("parciales"));
    }

    @Test
    void csvConDosDecimalesYBom() throws Exception {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("num_cuenta", "500007");
        f.put("apellidos", "Peña Flores");
        f.put("promedio", new BigDecimal("7.40"));
        Path p = Archivos.exportarCsv(List.of(f), Files.createTempDirectory("s").resolve("r.csv"));
        String texto = Files.readString(p, StandardCharsets.UTF_8);
        assertTrue(texto.startsWith("﻿"));
        assertEquals("500007,Peña Flores,7.40", texto.substring(1).split("\r\n")[1]);
    }

    @Test
    void jsonConservaAcentos() throws Exception {
        Path p = Archivos.exportarJson(List.of(Map.of("apellidos", "Álvarez")), Files.createTempDirectory("s").resolve("r.json"));
        assertTrue(Files.readString(p).contains("Álvarez"));
        assertEquals(List.of(Map.of("apellidos", "Álvarez")), Json.leer(Files.readString(p)));
    }

    @Test
    void reportesConElEjemplo() {
        GestorEscolar g = new GestorEscolar();
        g.cargar(Archivos.cargarJson(DATOS.resolve("ejemplo_entrada.json")));
        var todos = Reportes.generarTodos(g, Concurrencia.calcularResumenes(g, "secuencial"));
        assertEquals(Reportes.NOMBRES, List.copyOf(todos.keySet()));
        assertEquals("7.60", todos.get("resumen_grupos").get(0).get("promedio").toString());
    }

    @Test
    void concurrenteIgualQueSecuencial() {
        GestorEscolar g = new GestorEscolar();
        g.cargar(Archivos.cargarJson(DATOS.resolve("solicitudes_pequenas.json")));
        assertEquals(Concurrencia.calcularResumenes(g, "secuencial"), Concurrencia.calcularResumenes(g, "concurrente", 4));
    }
}
