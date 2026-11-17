package mx.uaeh.syllabus.logica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Docente;
import mx.uaeh.syllabus.modelo.Parcial;
import mx.uaeh.syllabus.modelo.ResumenCurso;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/** Reportes en forma de filas (columna → valor) listas para exportar a JSON o CSV. */
public final class Reportes {
    public static final List<String> NOMBRES = List.of("resumen_grupos", "estudiantes", "parciales_por_asignatura",
            "estadisticas_cursos", "riesgo_academico", "kardex");

    private Reportes() {
    }

    private static Map<String, Object> fila(Object... claveValor) {
        Map<String, Object> f = new LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            f.put((String) claveValor[i], claveValor[i + 1]);
        }
        return f;
    }

    public static Map<String, Object> filaEstudiante(ResumenEstudiante r) {
        return fila("grupo", r.grupo(), "num_cuenta", r.numCuenta(), "apellidos", r.apellidos(), "nombre", r.nombre(),
                "promedio", Calculos.mostrar(r.general()), "estatus", r.estatus(),
                "riesgo", r.riesgo() == null ? "PENDIENTE" : r.riesgo(), "motivo", r.motivo());
    }

    public static List<Map<String, Object>> resumenGrupos(List<ResumenEstudiante> resumenes) {
        Map<String, List<ResumenEstudiante>> grupos = new TreeMap<>();
        resumenes.forEach(r -> grupos.computeIfAbsent(r.grupo(), k -> new ArrayList<>()).add(r));
        List<Map<String, Object>> filas = new ArrayList<>();
        grupos.forEach((g, lista) -> {
            List<Double> cerrados = lista.stream().map(ResumenEstudiante::general).filter(Objects::nonNull).toList();
            Map<String, Object> est = Calculos.estadisticas(cerrados);
            filas.add(fila("grupo", g, "estudiantes", lista.size(), "promedio", est.get("promedio"), "maximo", est.get("maximo"),
                    "minimo", est.get("minimo"), "aprobacion", est.get("aprobacion"), "pendientes", lista.size() - cerrados.size()));
        });
        return filas;
    }

    public static List<Map<String, Object>> filasPorCurso(List<ResumenEstudiante> resumenes) {
        Map<List<String>, List<Map<String, Object>>> porCurso = new TreeMap<>(
                Comparator.<List<String>, String>comparing(k -> k.get(0)).thenComparing(k -> k.get(1)).thenComparing(k -> k.get(2)));
        for (ResumenEstudiante r : resumenes) {
            for (ResumenCurso c : r.cursos()) {
                List<Double> p = new ArrayList<>(c.parciales());
                while (p.size() < 3) {
                    p.add(null);
                }
                porCurso.computeIfAbsent(List.of(c.grupo(), c.asignatura(), c.cursoId()), k -> new ArrayList<>())
                        .add(fila("grupo", c.grupo(), "curso_id", c.cursoId(), "asignatura", c.asignatura(),
                                "num_cuenta", r.numCuenta(), "apellidos", r.apellidos(), "nombre", r.nombre(),
                                "parcial_1", Calculos.mostrar(p.get(0)), "parcial_2", Calculos.mostrar(p.get(1)),
                                "parcial_3", Calculos.mostrar(p.get(2)), "promedio", Calculos.mostrar(c.bruto()),
                                "estatus", c.estatus()));
            }
        }
        List<Map<String, Object>> filas = new ArrayList<>();
        porCurso.values().forEach(l -> filas.addAll(Calculos.ordenar(l)));
        return filas;
    }

    public static List<Map<String, Object>> estadisticasCursos(GestorEscolar gestor, List<ResumenEstudiante> resumenes) {
        Map<String, List<Double>> brutos = new LinkedHashMap<>();
        Map<String, Integer> inscritos = new LinkedHashMap<>();
        for (ResumenEstudiante r : resumenes) {
            for (ResumenCurso c : r.cursos()) {
                inscritos.merge(c.cursoId(), 1, Integer::sum);
                if (c.bruto() != null) {
                    brutos.computeIfAbsent(c.cursoId(), k -> new ArrayList<>()).add(c.bruto());
                }
            }
        }
        List<Curso> cursos = new ArrayList<>(gestor.getCursos().values());
        cursos.sort(Comparator.comparing(Curso::grupo).thenComparing(Curso::asignatura));
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Curso c : cursos) {
            Map<String, Object> est = Calculos.estadisticas(brutos.getOrDefault(c.id(), List.of()));
            Docente d = gestor.docenteDe(c.id());
            filas.add(fila("curso_id", c.id(), "asignatura", c.asignatura(), "grupo", c.grupo(),
                    "docente", d == null ? "" : d.nombre(), "inscritos", inscritos.getOrDefault(c.id(), 0),
                    "promedio", est.get("promedio"), "maximo", est.get("maximo"), "minimo", est.get("minimo"),
                    "aprobados", est.get("aprobados"), "reprobados", est.get("reprobados"), "aprobacion", est.get("aprobacion")));
        }
        return filas;
    }

    public static List<ResumenEstudiante> ordenarResumenes(List<ResumenEstudiante> resumenes) {
        List<ResumenEstudiante> r = new ArrayList<>(resumenes);
        r.sort(Comparator.comparing(ResumenEstudiante::numCuenta));
        r.sort(Comparator.comparing(x -> Calculos.claveTexto(x.apellidos())));
        return r;
    }

    public static List<Map<String, Object>> kardex(Calculadora calc, List<ResumenEstudiante> resumenes) {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (ResumenEstudiante r : ordenarResumenes(resumenes)) {
            for (ResumenCurso c : r.cursos()) {
                for (Parcial p : calc.parcialesDe(c.cursoId())) {
                    Double calif = calc.califParcial(r.numCuenta(), p.id());
                    filas.add(fila("num_cuenta", r.numCuenta(), "apellidos", r.apellidos(), "nombre", r.nombre(),
                            "curso_id", c.cursoId(), "asignatura", c.asignatura(), "parcial", p.numero(),
                            "ponderacion", Math.round(p.peso() * 100) + "%", "calificacion", Calculos.mostrar(calif),
                            "aporte", calif == null ? null : Calculos.mostrar(calif * p.peso())));
                }
                filas.add(fila("num_cuenta", r.numCuenta(), "apellidos", r.apellidos(), "nombre", r.nombre(),
                        "curso_id", c.cursoId(), "asignatura", c.asignatura(), "parcial", "Promedio",
                        "ponderacion", "100%", "calificacion", null, "aporte", Calculos.mostrar(c.bruto())));
            }
        }
        return filas;
    }

    public static Map<String, List<Map<String, Object>>> generarTodos(GestorEscolar gestor, List<ResumenEstudiante> resumenes) {
        List<Map<String, Object>> estudiantes = Calculos.ordenar(resumenes.stream().map(Reportes::filaEstudiante).toList());
        List<Map<String, Object>> porGrupo = new ArrayList<>(estudiantes);
        porGrupo.sort(Comparator.comparing(f -> (String) f.get("grupo")));
        Map<String, List<Map<String, Object>>> todos = new LinkedHashMap<>();
        todos.put("resumen_grupos", resumenGrupos(resumenes));
        todos.put("estudiantes", porGrupo);
        todos.put("parciales_por_asignatura", filasPorCurso(resumenes));
        todos.put("estadisticas_cursos", estadisticasCursos(gestor, resumenes));
        todos.put("riesgo_academico", estudiantes.stream()
                .filter(f -> "ALTO".equals(f.get("riesgo")) || "MEDIO".equals(f.get("riesgo"))).toList());
        todos.put("kardex", kardex(gestor, resumenes));
        return todos;
    }
}
