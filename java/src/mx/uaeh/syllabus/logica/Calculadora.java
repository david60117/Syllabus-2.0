package mx.uaeh.syllabus.logica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Estudiante;
import mx.uaeh.syllabus.modelo.Evaluacion;
import mx.uaeh.syllabus.modelo.Parcial;
import mx.uaeh.syllabus.modelo.ResumenCurso;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/** Datos y cálculos de promedios (sin eventos). Es de solo lectura durante el cálculo concurrente. */
public class Calculadora {
    /** Par (estudiante, curso) o (estudiante, evaluación). */
    public record Par(String a, String b) {
    }

    protected final Map<String, Estudiante> estudiantes = new LinkedHashMap<>();
    protected final Map<String, Curso> cursos = new LinkedHashMap<>();
    protected final Map<String, Parcial> parciales = new LinkedHashMap<>();
    protected final Map<String, Evaluacion> evaluaciones = new LinkedHashMap<>();
    protected final Set<Par> inscripciones = new LinkedHashSet<>();
    protected final Map<Par, Double> calificaciones = new LinkedHashMap<>();

    private List<Integer> firma;
    private int version;
    private Map<String, List<Parcial>> parcialesPorCurso;
    private Map<String, List<Evaluacion>> evaluacionesPorParcial;
    private Map<String, List<String>> cursosPorEstudiante;

    /** Invalida los índices: altas, bajas y ediciones pueden dejar los mismos tamaños con otro contenido. */
    protected synchronized void cambio() {
        version++;
        firma = null;
    }

    /** Índices por curso, parcial y estudiante; se reconstruyen cuando cambian los datos. */
    protected synchronized void indice() {
        List<Integer> actual = List.of(version, parciales.size(), evaluaciones.size(), inscripciones.size(), cursos.size());
        if (actual.equals(firma)) {
            return;
        }
        parcialesPorCurso = new HashMap<>();
        evaluacionesPorParcial = new HashMap<>();
        cursosPorEstudiante = new HashMap<>();
        parciales.values().forEach(p -> parcialesPorCurso.computeIfAbsent(p.cursoId(), k -> new ArrayList<>()).add(p));
        parcialesPorCurso.values().forEach(l -> l.sort(Comparator.comparingInt(Parcial::numero)));
        evaluaciones.values().forEach(e -> evaluacionesPorParcial.computeIfAbsent(e.parcialId(), k -> new ArrayList<>()).add(e));
        inscripciones.forEach(i -> cursosPorEstudiante.computeIfAbsent(i.a(), k -> new ArrayList<>()).add(i.b()));
        firma = actual;
    }

    public List<Parcial> parcialesDe(String cursoId) {
        indice();
        return List.copyOf(parcialesPorCurso.getOrDefault(cursoId, List.of()));
    }

    public List<Evaluacion> evaluacionesDe(String parcialId) {
        indice();
        return List.copyOf(evaluacionesPorParcial.getOrDefault(parcialId, List.of()));
    }

    public List<Curso> cursosDe(String numCuenta) {
        indice();
        return cursosPorEstudiante.getOrDefault(numCuenta, List.of()).stream()
                .map(cursos::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(Curso::id)).toList();
    }

    public Double calificacion(String numCuenta, String evaluacionId) {
        return calificaciones.get(new Par(numCuenta, evaluacionId));
    }

    /** Calificación del parcial; null mientras falte capturar alguna evaluación. */
    public Double califParcial(String numCuenta, String parcialId) {
        List<Evaluacion> evals = evaluacionesDe(parcialId);
        if (evals.isEmpty()) {
            return null;
        }
        double suma = 0;
        for (Evaluacion e : evals) {
            Double c = calificacion(numCuenta, e.id());
            if (c == null) {
                return null;
            }
            suma += c * e.peso();
        }
        return suma;
    }

    public Double promedioCurso(String numCuenta, String cursoId) {
        List<Parcial> ps = parcialesDe(cursoId);
        if (ps.isEmpty()) {
            return null;
        }
        double suma = 0;
        for (Parcial p : ps) {
            Double c = califParcial(numCuenta, p.id());
            if (c == null) {
                return null;
            }
            suma += c * p.peso();
        }
        return suma;
    }

    public ResumenEstudiante resumenEstudiante(String numCuenta) {
        Estudiante e = estudiantes.get(numCuenta);
        List<ResumenCurso> rc = new ArrayList<>();
        for (Curso c : cursosDe(numCuenta)) {
            Double bruto = promedioCurso(numCuenta, c.id());
            List<Double> porParcial = new ArrayList<>();
            parcialesDe(c.id()).forEach(p -> porParcial.add(califParcial(numCuenta, p.id())));
            rc.add(new ResumenCurso(c.id(), c.asignatura(), c.grupo(), porParcial, bruto,
                    bruto == null ? "PENDIENTE" : Calculos.evaluarEstatusCurso(bruto)));
        }
        List<Double> cerrados = rc.stream().map(ResumenCurso::bruto).filter(Objects::nonNull).toList();
        Double general = cerrados.isEmpty() ? null : Calculos.suma(cerrados) / cerrados.size();
        int pendientes = (int) rc.stream().filter(c -> c.bruto() == null).count();
        return new ResumenEstudiante(e.numCuenta(), e.apellidos(), e.nombre(), e.grupo(), rc, general,
                general == null ? "PENDIENTE" : Calculos.evaluarEstatusCurso(general),
                general == null ? null : Calculos.evaluarRiesgo(cerrados, general),
                general == null ? "Sin cursos cerrados" : Calculos.motivoRiesgo(cerrados, general), pendientes);
    }

    public Map<String, Estudiante> getEstudiantes() {
        return estudiantes;
    }

    public Map<String, Curso> getCursos() {
        return cursos;
    }

    public Map<String, Parcial> getParciales() {
        return parciales;
    }

    public Map<String, Evaluacion> getEvaluaciones() {
        return evaluaciones;
    }

    public Set<Par> getInscripciones() {
        return inscripciones;
    }

    public Map<Par, Double> getCalificaciones() {
        return calificaciones;
    }
}
