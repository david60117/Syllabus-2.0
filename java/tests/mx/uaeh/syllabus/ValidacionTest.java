package mx.uaeh.syllabus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mx.uaeh.syllabus.archivos.Json;
import mx.uaeh.syllabus.logica.Calculos;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.modelo.ErrorValidacion;

class ValidacionTest {
    private GestorEscolar g;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invalidos() throws Exception {
        return (Map<String, Object>) Json.leer(Files.readString(Path.of("..", "datos", "invalidos.json")));
    }

    @BeforeEach
    void preparar() {
        g = new GestorEscolar();
        g.registrarEstudiante(Map.of("num_cuenta", "500001", "apellidos", "García López", "nombre", "Ana", "grupo", "301"));
        g.registrarCurso(Map.of("id", "C01", "asignatura", "Bases de Datos", "grupo", "301"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void erroresEsperados() throws Exception {
        Map<String, Object> datos = invalidos();
        GestorEscolar gi = new GestorEscolar();
        gi.cargar(datos);
        List<String> obtenidos = new ArrayList<>(gi.getErrores().stream().map(e -> e.caso() + "|" + e.codigo()).toList());
        List<String> esperados = new ArrayList<>(((List<Object>) datos.get("errores_esperados")).stream()
                .map(e -> ((Map<String, Object>) e).get("caso") + "|" + ((Map<String, Object>) e).get("codigo")).toList());
        obtenidos.sort(null);
        esperados.sort(null);
        assertEquals(esperados, obtenidos);
        assertTrue(gi.getEstudiantes().containsKey("545049"));
        assertEquals("8.34", Calculos.mostrar(gi.promedioCurso("545049", "C01")).toPlainString());
        assertEquals(8.0, gi.calificacion("545049", "EV01"));
    }

    @Test
    void parcialesNoSuperanUno() {
        g.registrarParcial(Map.of("id", "P01", "curso_id", "C01", "numero", 1.0, "peso", 0.6));
        ErrorValidacion e = assertThrows(ErrorValidacion.class,
                () -> g.registrarParcial(Map.of("id", "P02", "curso_id", "C01", "numero", 2.0, "peso", 0.5)));
        assertEquals("PORCENTAJES_INVALIDOS", e.getCodigo());
    }

    @Test
    void inscripcionDeOtroGrupo() {
        g.registrarEstudiante(Map.of("num_cuenta", "500002", "apellidos", "Ruiz Mora", "nombre", "Luis", "grupo", "302"));
        ErrorValidacion e = assertThrows(ErrorValidacion.class, () -> g.inscribir(Map.of("num_cuenta", "500002", "curso_id", "C01")));
        assertEquals("REFERENCIA_INEXISTENTE", e.getCodigo());
    }

    @Test
    void eventosEmitidos() {
        List<String> vistos = new ArrayList<>();
        g.getBus().suscribir("inscripcion_registrada", ev -> vistos.add(ev.dato("detalle")));
        g.inscribir(Map.of("num_cuenta", "500001", "curso_id", "C01"));
        assertEquals(List.of("500001 → C01"), vistos);
    }
}
