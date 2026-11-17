package mx.uaeh.syllabus.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import mx.uaeh.syllabus.logica.Calculadora.Par;
import mx.uaeh.syllabus.logica.Reportes;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Evaluacion;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/** Docente: cursos asignados con inscritos, promedio y accesos a plan, captura y consultas. */
public class VistaMisCursos extends Vista {
    public VistaMisCursos(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        app.encabezado("Docente  /  Cursos", "Mis cursos asignados",
                "Consulta los grupos y asignaturas a tu cargo. Solo puedes capturar notas de tus cursos.");
        List<Curso> cursos = app.cursosVisibles();
        List<ResumenEstudiante> resumenes = app.resumenesVisibles();
        long pendientes = 0;
        for (Curso c : cursos) {
            for (Par i : g.getInscripciones()) {
                if (i.b().equals(c.id())) {
                    for (var p : g.parcialesDe(c.id())) {
                        for (Evaluacion e : g.evaluacionesDe(p.id())) {
                            if (g.calificacion(i.a(), e.id()) == null) {
                                pendientes++;
                            }
                        }
                    }
                }
            }
        }
        app.agregar(Componentes.mosaicos(new Object[][]{{"Cursos asignados", cursos.size()},
                {"Grupos activos", cursos.stream().map(Curso::grupo).distinct().count()}, {"Evaluaciones pendientes", pendientes},
                {"Estudiantes", resumenes.size()}}));
        JPanel t = tarjeta(null);
        Tabla tabla = new Tabla(new String[][]{{"curso_id", "Curso"}, {"asignatura", "Asignatura"}, {"grupo", "Grupo"}, {"inscritos", "Inscritos"},
                {"promedio", "Promedio"}, {"estado", "Estado"}}, Math.max(3, Math.min(8, cursos.size())), List.of());
        tabla.proporciones(List.of(1, 3, 1, 1, 1, 1));
        Map<String, Map<String, Object>> est = new HashMap<>();
        Reportes.estadisticasCursos(g, resumenes).forEach(f -> est.put((String) f.get("curso_id"), f));
        tabla.cargar(cursos.stream().map(c -> VistaAlumno.fila("curso_id", c.id(), "asignatura", c.asignatura(), "grupo", c.grupo(),
                "inscritos", est.getOrDefault(c.id(), Map.of()).getOrDefault("inscritos", 0),
                "promedio", est.getOrDefault(c.id(), Map.of()).get("promedio"), "estado", "Asignado")).toList());
        poner(t, tabla);
        app.agregar(t);
        botonesPie(new String[][]{{"Plan de evaluación", "Plan de evaluación"}, {"Capturar calificaciones", "Captura"},
                {"Promedios y riesgo", "Consultas:1"}});
        notaPie("La asignación de cursos se determina por el administrador.");
    }
}
