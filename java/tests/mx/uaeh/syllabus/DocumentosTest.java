package mx.uaeh.syllabus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mx.uaeh.syllabus.archivos.Archivos;
import mx.uaeh.syllabus.archivos.Json;
import mx.uaeh.syllabus.documentos.Documento;
import mx.uaeh.syllabus.documentos.Documentos;
import mx.uaeh.syllabus.logica.Autenticacion;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.modelo.Cuenta;
import mx.uaeh.syllabus.modelo.Curso;

/** Reportes institucionales: encabezado, alcance individual del kárdex, credenciales y exportación. */
class DocumentosTest {
    private static final Map<String, String> INST = Map.of("institucion", "Universidad de prueba", "programa",
            "Ingeniería de Software", "ciclo", "Julio – Diciembre 2026");
    private GestorEscolar g;
    private Documentos d;
    private Autenticacion auth;

    @BeforeEach
    void cargar() {
        g = new GestorEscolar();
        g.cargar(Archivos.cargarJson(Path.of("..", "datos", "solicitudes_pequenas.json")));
        d = new Documentos(g, INST, "admin");
        auth = new Autenticacion(Path.of("..", "datos", "usuarios.json"));
        auth.sincronizar(g);
    }

    private List<Curso> cursos() {
        return new ArrayList<>(g.getCursos().values());
    }

    @Test
    void encabezadoEnTodosLosReportes() {
        for (Documento doc : List.of(d.boleta("500001"), d.kardex("500001"), d.acta("C01"), d.concentrado("301", "apellidos"),
                d.aprovechamiento(cursos(), null), d.riesgoAcademico(null), d.aprobacion(cursos(), null, "Todos"))) {
            assertEquals("Universidad de prueba", doc.encabezado().institucion());
            assertEquals("Julio – Diciembre 2026", doc.encabezado().ciclo());
            assertTrue(!doc.titulo().isEmpty() && !doc.encabezado().emision().isEmpty());
        }
    }

    @Test
    void kardexEsIndividual() {
        Documento doc = d.kardex("500001");
        assertEquals("500001", doc.encabezado().campo("Número de cuenta"));
        assertEquals(g.cursosDe("500001").stream().map(Curso::id).collect(Collectors.toSet()),
                doc.filas().stream().map(f -> f.get("clave")).collect(Collectors.toSet()));
        assertTrue(doc.columnas().stream().noneMatch(c -> c.clave().equals("num_cuenta")));
    }

    private static long n(Documento doc, String etiqueta) {
        return ((Number) doc.resumen().stream().filter(x -> x.etiqueta().equals(etiqueta)).findFirst().orElseThrow().valor()).longValue();
    }

    @Test
    void actaResumenCuadra() {
        Documento doc = d.acta("C01");
        assertEquals(doc.filas().size(), n(doc, "Inscritos"));
        assertEquals(n(doc, "Inscritos"), n(doc, "Aprobados") + n(doc, "Reprobados") + n(doc, "Pendientes"));
    }

    @Test
    void concentradoPorPromedio() {
        List<BigDecimal> p = d.concentrado("301", "promedio").filas().stream().map(f -> (BigDecimal) f.get("promedio")).toList();
        List<BigDecimal> ordenados = new ArrayList<>(p);
        ordenados.sort(java.util.Comparator.reverseOrder());
        assertEquals(ordenados, p);
    }

    @Test
    @SuppressWarnings("unchecked")
    void csvYJsonConEncabezado() throws Exception {
        Documento doc = d.acta("C01");
        Path tmp = Files.createTempDirectory("syllabus");
        List<String> lineas = Files.readAllLines(Archivos.exportarDocumento(doc, tmp.resolve("acta.csv"), "csv", "#8b2024"));
        assertEquals("﻿Universidad de prueba", lineas.get(0));
        assertEquals("ACTA DE CALIFICACIONES", lineas.get(2));
        assertTrue(lineas.contains("Clave,C01"));
        Map<String, Object> json = (Map<String, Object>) Json.leer(Files.readString(
                Archivos.exportarDocumento(doc, tmp.resolve("acta.json"), "json", "#8b2024")));
        assertEquals("Acta de calificaciones", ((Map<String, Object>) json.get("encabezado")).get("reporte"));
        assertEquals(6, ((List<Object>) json.get("registros")).size());
    }

    @Test
    void credencialesPorGrupoYDocentes() {
        Documento doc = d.credenciales(auth.getUsuarios(), "Grupo 301");
        assertEquals(Set.of("Alumno"), doc.filas().stream().map(f -> f.get("rol")).collect(Collectors.toSet()));
        assertEquals(6, doc.filas().size());
        assertEquals("500003", doc.filas().stream().filter(f -> "500003".equals(f.get("usuario"))).findFirst().orElseThrow().get("contrasena"));
        assertEquals("Personalizada", doc.filas().stream().filter(f -> "500001".equals(f.get("usuario"))).findFirst().orElseThrow().get("contrasena"));
        assertEquals(List.of("pavila", "d02"), d.credenciales(auth.getUsuarios(), "Docentes").filas().stream().map(f -> f.get("usuario")).toList());
    }

    @Test
    void contrasenaCambiadaYaNoEsVisible() {
        Cuenta c = auth.buscar("500003");
        Autenticacion.cambiarContrasena(c, "500003", "Segura2026", "Segura2026");
        assertNull(Autenticacion.contrasenaVisible(c));
        Autenticacion.restablecer(c);
        assertEquals("500003", Autenticacion.contrasenaVisible(c));
    }

    @Test
    void pdfValidoConVariasPaginas() throws Exception {
        Path tmp = Files.createTempDirectory("syllabus");
        byte[] datos = Files.readAllBytes(Archivos.exportarDocumento(d.aprobacion(cursos(), null, "Todos"), tmp.resolve("a.pdf"), "pdf", "#8b2024"));
        String texto = new String(datos, StandardCharsets.ISO_8859_1);
        assertTrue(texto.startsWith("%PDF-1.4") && texto.strip().endsWith("%%EOF"));
        assertTrue(texto.contains("/Count 2"));
        assertTrue(texto.contains("(continuación\\)"));
    }
}
