package mx.uaeh.syllabus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import mx.uaeh.syllabus.archivos.Json;
import mx.uaeh.syllabus.logica.Calculos;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/** Los 5 casos de prueba comunes (CP-01 a CP-05) definidos en Notion, más reglas de cálculo. */
class CasosComunesTest {
    static final Path DATOS = Path.of("..", "datos");

    @SuppressWarnings("unchecked")
    static GestorEscolar caso(String id) throws Exception {
        Map<String, Object> todo = (Map<String, Object>) Json.leer(Files.readString(DATOS.resolve("casos_prueba.json")));
        for (Object c : (List<Object>) todo.get("casos")) {
            Map<String, Object> m = (Map<String, Object>) c;
            if (id.equals(m.get("id"))) {
                GestorEscolar g = new GestorEscolar();
                g.cargar((Map<String, Object>) m.get("entrada"));
                return g;
            }
        }
        throw new IllegalArgumentException(id);
    }

    static String txt(Double x) {
        return Calculos.mostrar(x).toPlainString();
    }

    @Test
    void cp01PromedioPonderado() throws Exception {
        GestorEscolar g = caso("CP-01");
        assertEquals("7.95", txt(g.promedioCurso("600001", "C91")));
        assertEquals("APROBADO", g.resumenEstudiante("600001").cursos().get(0).estatus());
    }

    @Test
    void cp02LimiteDeAprobacion() throws Exception {
        GestorEscolar g = caso("CP-02");
        assertEquals("7.00", txt(g.promedioCurso("600001", "C92")));
        assertEquals("APROBADO", g.resumenEstudiante("600001").cursos().get(0).estatus());
    }

    @Test
    void cp03ReprobacionYRiesgoAlto() throws Exception {
        ResumenEstudiante r = caso("CP-03").resumenEstudiante("600001");
        assertEquals("6.99", txt(r.cursos().get(0).bruto()));
        assertEquals("REPROBADO", r.cursos().get(0).estatus());
        assertEquals("ALTO", r.riesgo());
    }

    @Test
    void cp04EstadisticasDelCurso() throws Exception {
        GestorEscolar g = caso("CP-04");
        Map<String, Object> est = Calculos.estadisticas(List.of("600001", "600002", "600003").stream()
                .map(nc -> g.promedioCurso(nc, "C94")).toList());
        assertEquals("7.33", est.get("promedio").toString());
        assertEquals("9.00", est.get("maximo").toString());
        assertEquals("6.00", est.get("minimo").toString());
        assertEquals("66.67", est.get("aprobacion").toString());
        assertEquals(2, est.get("aprobados"));
        assertEquals(1, est.get("reprobados"));
    }

    @Test
    void cp05CalificacionFueraDeRango() throws Exception {
        GestorEscolar g = caso("CP-05");
        assertEquals(List.of("CALIFICACION_FUERA_DE_RANGO"), g.getErrores().stream().map(e -> e.codigo()).toList());
        assertEquals(0, g.getCalificaciones().size());
    }

    @Test
    void redondeoInstitucional() {
        assertEquals("6.99", txt(6.996));
        assertEquals("7.96", txt(7.955));
        assertEquals("7.00", txt(0.3 * 7 + 0.3 * 7 + 0.4 * 7));
    }

    @Test
    void fronterasDeRiesgo() {
        assertEquals("ALTO", Calculos.evaluarRiesgo(List.of(6.996), 6.996));
        assertEquals("MEDIO", Calculos.evaluarRiesgo(List.of(7.0, 7.0), 7.0));
        assertEquals("MEDIO", Calculos.evaluarRiesgo(List.of(7.5, 7.5), 7.5));
        assertEquals("SIN_RIESGO", Calculos.evaluarRiesgo(List.of(7.51, 7.51), 7.51));
        assertEquals("ALTO", Calculos.evaluarRiesgo(List.of(8.5, 6.98), 7.74));
    }

    @Test
    void ordenPorApellidosSinAcentosYDesempate() {
        List<Map<String, Object>> filas = List.of(
                Map.of("num_cuenta", "3", "apellidos", "García", "promedio", 8.0),
                Map.of("num_cuenta", "2", "apellidos", "Álvarez", "promedio", 9.0),
                Map.of("num_cuenta", "1", "apellidos", "Núñez", "promedio", 9.0));
        assertEquals(List.of("2", "3", "1"), Calculos.ordenar(filas).stream().map(f -> f.get("num_cuenta")).toList());
        assertEquals(List.of("1", "2", "3"), Calculos.ordenar(filas, "promedio", true).stream().map(f -> f.get("num_cuenta")).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ejemploDeEntradaPorParciales() throws Exception {
        GestorEscolar g = new GestorEscolar();
        g.cargar((Map<String, Object>) Json.leer(Files.readString(DATOS.resolve("ejemplo_entrada.json"))));
        assertEquals(0, g.getErrores().size());
        ResumenEstudiante r = g.resumenEstudiante("500001");
        assertEquals(List.of("9.00", "10.00", "8.25"), r.cursos().get(0).parciales().stream().map(CasosComunesTest::txt).toList());
        assertEquals("9.00", txt(r.general()));
        assertEquals("MEDIO", g.resumenEstudiante("500007").riesgo());
    }
}
