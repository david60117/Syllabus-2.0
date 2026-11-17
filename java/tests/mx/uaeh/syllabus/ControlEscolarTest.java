package mx.uaeh.syllabus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
import mx.uaeh.syllabus.archivos.Persistencia;
import mx.uaeh.syllabus.logica.Autenticacion;
import mx.uaeh.syllabus.logica.Calculadora.Par;
import mx.uaeh.syllabus.logica.Calculos;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.logica.GestorEscolar.CambioActa;
import mx.uaeh.syllabus.logica.GestorEscolar.ResultadoActa;
import mx.uaeh.syllabus.logica.GestorEscolar.Rubro;
import mx.uaeh.syllabus.modelo.Cuenta;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Docente;
import mx.uaeh.syllabus.modelo.ErrorValidacion;
import mx.uaeh.syllabus.modelo.Estudiante;
import mx.uaeh.syllabus.modelo.Evaluacion;
import mx.uaeh.syllabus.modelo.Grupo;
import mx.uaeh.syllabus.modelo.Parcial;

/** Flujo de control escolar: altas en un paso, IDs automáticos, plan de evaluación, acta, cuentas y respaldo. */
class ControlEscolarTest {
    private GestorEscolar g;

    @BeforeEach
    void cargar() {
        g = new GestorEscolar();
        g.cargar(Archivos.cargarJson(Path.of("..", "datos", "solicitudes_pequenas.json")));
    }

    private static Set<String> gruposDe(List<Curso> cursos) {
        return cursos.stream().map(Curso::grupo).collect(Collectors.toSet());
    }

    @Test
    void materiaNuevaCreaParcialesEInscribeAlGrupo() {
        Curso c = g.altaCurso("Ética Profesional", "301", List.of(0.3, 0.3, 0.4), "D01");
        assertEquals("C07", c.id());
        assertEquals(List.of(1, 2, 3), g.parcialesDe(c.id()).stream().map(Parcial::numero).toList());
        assertEquals(6, g.alumnosDeCurso(c.id()).size());
        assertEquals("D01", g.docenteDe(c.id()).id());
    }

    @Test
    void pesosDeParcialesDebenSumar100() {
        assertEquals("PORCENTAJES_INVALIDOS", assertThrows(ErrorValidacion.class,
                () -> g.altaCurso("Ética", "301", List.of(0.3, 0.3, 0.3), null)).getCodigo());
        assertThrows(ErrorValidacion.class, () -> g.altaCurso("redes", "302", GestorEscolar.PESOS_PARCIALES, null));
    }

    @Test
    void alumnoNuevoConCuentaAutomaticaEInscripcion() {
        Estudiante e = g.altaEstudiante("Pérez Soto", "Juan", "302");
        assertEquals("500013", e.numCuenta());
        assertEquals(3, g.cursosDe(e.numCuenta()).size());
        assertEquals(Set.of("302"), gruposDe(g.cursosDe(e.numCuenta())));
    }

    @Test
    void grupoRegistradoManualmente() {
        assertThrows(ErrorValidacion.class, () -> g.altaCurso("Álgebra", "303", GestorEscolar.PESOS_PARCIALES, null));
        Grupo grupo = g.altaGrupo("303", "3", "Vespertino");
        assertEquals(3, grupo.semestre());
        assertEquals("Vespertino", grupo.turno());
        assertThrows(ErrorValidacion.class, () -> g.altaGrupo("303", 3, "Matutino"));
        assertThrows(ErrorValidacion.class, () -> g.altaGrupo("304", 12, "Matutino"));
        assertTrue(g.grupos().contains("303"));
        Curso c = g.altaCurso("Álgebra", "303", GestorEscolar.PESOS_PARCIALES, null);
        assertEquals(0, g.alumnosDeCurso(c.id()).size());
        Estudiante e = g.altaEstudiante("Luna Díaz", "Marta", "303");
        assertEquals(1, g.cursosDe(e.numCuenta()).size());
        assertThrows(ErrorValidacion.class, () -> g.bajaGrupo("303"));
        g.altaGrupo("305", 5, "Matutino");
        g.bajaGrupo("305");
        assertFalse(g.grupos().contains("305"));
        assertEquals(3, g.getGruposRegistro().get("301").semestre());
    }

    @Test
    void cambioDeGrupoYBaja() {
        g.editarEstudiante("500001", "Martínez García", "Lucía", "302");
        assertEquals(Set.of("302"), gruposDe(g.cursosDe("500001")));
        assertFalse(g.getCalificaciones().keySet().stream().anyMatch(k -> k.a().equals("500001")));
        g.bajaEstudiante("500001");
        assertFalse(g.getEstudiantes().containsKey("500001"));
        assertFalse(g.getInscripciones().stream().anyMatch(i -> i.a().equals("500001")));
    }

    @Test
    void planYActa() {
        Curso c = g.altaCurso("Ética", "301", GestorEscolar.PESOS_PARCIALES, null);
        Parcial p1 = g.parcialesDe(c.id()).get(0);
        assertThrows(ErrorValidacion.class, () -> g.definirPlan(p1.id(),
                List.of(new Rubro(null, "Examen", 0.6), new Rubro(null, "Tareas", 0.3))));
        g.definirPlan(p1.id(), List.of(new Rubro(null, "Examen", 0.6), new Rubro(null, "Tareas", 0.4)));
        Evaluacion examen = g.evaluacionesDe(p1.id()).get(0);
        Evaluacion tareas = g.evaluacionesDe(p1.id()).get(1);
        List<String> alumnos = g.alumnosDeCurso(c.id()).stream().map(Estudiante::numCuenta).toList();
        List<CambioActa> cambios = new ArrayList<>();
        alumnos.forEach(n -> cambios.add(new CambioActa(n, examen.id(), 8.0)));
        alumnos.forEach(n -> cambios.add(new CambioActa(n, tareas.id(), 10.0)));
        cambios.add(new CambioActa(alumnos.get(0), tareas.id(), 9.55));
        ResultadoActa r = g.guardarActa(cambios);
        assertEquals(12, r.capturadas());
        assertEquals(List.of("FORMATO_INVALIDO"), r.errores().stream().map(e -> e.get("codigo")).toList());
        assertEquals(8.8, g.califParcial(alumnos.get(0), p1.id()), 1e-9);
        r = g.guardarActa(List.of(new CambioActa(alumnos.get(0), examen.id(), 9.0), new CambioActa(alumnos.get(1), examen.id(), null)));
        assertEquals(1, r.modificadas());
        assertEquals(1, r.borradas());
        assertEquals(6, g.definirPlan(p1.id(), List.of(new Rubro(examen.id(), "Examen", 1.0))));
        assertEquals(9.0, g.getCalificaciones().get(new Par(alumnos.get(0), examen.id())));
        assertEquals("OPERACION_NO_PERMITIDA", assertThrows(ErrorValidacion.class, () -> g.eliminarCurso(c.id())).getCodigo());
    }

    @Test
    void asignarYExcepcionesDeInscripcion() {
        Docente d = g.altaDocente("Ramón Gil");
        assertEquals("D03", d.id());
        g.asignarCursos(d.id(), List.of("C01", "C04"));
        assertEquals(List.of("C01", "C04"), g.cursosDeDocente(d.id()).stream().map(Curso::id).toList());
        assertFalse(g.cursosDeDocente("D01").stream().anyMatch(c -> c.id().equals("C01")));
        int[] r = g.actualizarInscripciones("500001", List.of("C01"));
        assertEquals(0, r[0]);
        assertEquals(2, r[1]);
        assertThrows(ErrorValidacion.class, () -> g.actualizarInscripciones("500001", List.of("C04")));
        assertEquals(2, g.inscribirGrupo("301"));
    }

    @Test
    void cuentasYRespaldo() throws Exception {
        Autenticacion auth = new Autenticacion(Path.of("..", "datos", "usuarios.json"));
        auth.sincronizar(g);
        Cuenta cuenta = auth.iniciarSesion("500002", "500002", g);
        assertTrue(cuenta.debeCambiar());
        assertThrows(ErrorValidacion.class, () -> Autenticacion.cambiarContrasena(cuenta, "500002", "corta1", "corta1"));
        Autenticacion.cambiarContrasena(cuenta, "500002", "Nueva2026", "Nueva2026");
        assertFalse(cuenta.debeCambiar());
        g.altaDocente("Ramón Gil");
        assertEquals(1, auth.sincronizar(g));
        g.bajaEstudiante("500003");
        auth.sincronizar(g);
        assertNull(auth.buscar("500003"));
        Path tmp = Files.createTempDirectory("syllabus");
        Persistencia per = new Persistencia(tmp.resolve("base.json"), Path.of("..", "datos", "solicitudes_pequenas.json"),
                Path.of("..", "datos", "usuarios.json"));
        per.guardar(g, auth, Map.of("ciclo", "Prueba"));
        GestorEscolar otro = new GestorEscolar();
        Persistencia.Estado estado = per.abrir(otro);
        assertEquals(mx.uaeh.syllabus.archivos.Json.escribir(g.aDiccionario()), mx.uaeh.syllabus.archivos.Json.escribir(otro.aDiccionario()));
        assertEquals("Prueba", estado.institucion().get("ciclo"));
        estado.cuentas().iniciarSesion("500002", "Nueva2026", otro);
    }

    @Test
    void calificacionFinalReglaDelPuntoCinco() {
        assertEquals(9, Calculos.calificacionFinal(8.5));
        assertEquals(8, Calculos.calificacionFinal(8.4));
        assertEquals(7, Calculos.calificacionFinal(7.0));
        assertEquals(10, Calculos.calificacionFinal(9.5));
        assertEquals(9, Calculos.calificacionFinal(0.3 * 8.5 + 0.3 * 8.5 + 0.4 * 8.5));
        assertEquals(6, Calculos.calificacionFinal(6.9));
        assertEquals(6, Calculos.calificacionFinal(6.96));
        assertEquals(6, Calculos.calificacionFinal(6.5));
        assertNull(Calculos.calificacionFinal(null));
        List<Double> brutos = new ArrayList<>(List.of(8.5, 6.9));
        brutos.add(null);
        assertEquals(new BigDecimal("7.50"), Calculos.promedioFinales(brutos));
    }
}
