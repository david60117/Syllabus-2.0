package mx.uaeh.syllabus.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import mx.uaeh.syllabus.logica.Calculadora.Par;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Estudiante;

/** Control escolar: la inscripción al grupo es automática; aquí se atienden altas y bajas individuales. */
public class VistaInscripciones extends Vista {
    public VistaInscripciones(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        app.encabezado("Control escolar  /  Inscripciones", "Inscripciones",
                "La inscripción al grupo es automática; aquí se atienden altas y bajas individuales.");
        List<String> grupos = g.grupos();
        Map<String, String> estado = app.estado("form_inscripcion", Map.of("grupo", grupos.isEmpty() ? "" : grupos.get(0), "alumno", ""));
        JPanel ind = tarjeta("Por estudiante");
        var campoGrupo = opciones(estado, "grupo", "Grupo", grupos, Tema.BLANCO, app::refrescar);
        String grupo = estado.get("grupo");
        List<Estudiante> alumnos = g.getEstudiantes().values().stream().filter(e -> e.grupo().equals(grupo))
                .sorted(Comparator.comparing(Estudiante::apellidos).thenComparing(Estudiante::nombre)).toList();
        List<String> nombres = alumnos.stream().map(e -> e.numCuenta() + " · " + e.nombreCompleto()).toList();
        var campoAlumno = opciones(estado, "alumno", "Estudiante", nombres, Tema.BLANCO, app::refrescar);
        poner(ind, linea(null, campoGrupo.panel(), campoAlumno.panel()), 10);
        String nc = primero(estado.get("alumno"));
        Map<String, JCheckBox> marcas = new LinkedHashMap<>();
        Redondo lista = Redondo.vertical(7, Tema.BLANCO, Tema.BORDE, 8, 12);
        for (Curso c : g.cursosDeGrupo(grupo)) {
            JCheckBox caja = VistaEstudiantes.casilla(c.id() + "  " + c.asignatura(), g.getInscripciones().contains(new Par(nc, c.id())));
            marcas.put(c.id(), caja);
            lista.add(caja);
        }
        if (marcas.isEmpty()) {
            lista.add(etiqueta("El grupo no tiene materias.", 13, false, Tema.SUAVE));
        }
        poner(ind, lista, 10);
        poner(ind, linea(null, boton("Guardar inscripción", () -> {
            if (!g.getEstudiantes().containsKey(nc)) {
                return;
            }
            List<String> elegidos = marcas.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList();
            long conNotas = marcas.entrySet().stream().filter(e -> !e.getValue().isSelected() && g.getInscripciones().contains(new Par(nc, e.getKey())))
                    .filter(e -> {
                        Set<String> evals = new HashSet<>();
                        g.parcialesDe(e.getKey()).forEach(p -> g.evaluacionesDe(p.id()).forEach(x -> evals.add(x.id())));
                        return g.getCalificaciones().keySet().stream().anyMatch(k -> k.a().equals(nc) && evals.contains(k.b()));
                    }).count();
            if (conNotas > 0 && !app.confirmar("Baja de materias", "Se borrarán las calificaciones de " + conNotas
                    + " materia(s) que ya tienen captura. ¿Continuar?")) {
                return;
            }
            int[] r = app.obtener(() -> g.actualizarInscripciones(nc, elegidos));
            if (r != null) {
                app.informar("Inscripción actualizada", "Altas: " + r[0] + "   ·   Bajas: " + r[1]);
            }
        }, true)));

        JPanel grp = tarjeta("Por grupo");
        long faltan = alumnos.stream().mapToLong(e -> g.cursosDeGrupo(grupo).stream()
                .filter(c -> !g.getInscripciones().contains(new Par(e.numCuenta(), c.id()))).count()).sum();
        poner(grp, etiqueta("Grupo " + (grupo.isEmpty() ? "—" : grupo) + ": " + alumnos.size() + " alumnos, " + g.cursosDeGrupo(grupo).size()
                + " materias", 15, true, null), 4);
        poner(grp, etiqueta("Inscripciones faltantes: " + faltan, 14, false, faltan > 0 ? Tema.acento() : Tema.SUAVE), 10);
        poner(grp, linea(null, boton("Inscribir al grupo en todas sus materias", () -> {
            if (!grupo.isEmpty()) {
                app.hacer(() -> g.inscribirGrupo(grupo));
            }
        }, faltan > 0)));
        nota(grp, "Útil si se dieron bajas individuales por error o después de una carga masiva.");
        app.agregar(fila(Tema.FONDO, ind, grp));

        JPanel t = tarjeta("Inscritos por materia");
        Tabla tabla = new Tabla(new String[][]{{"clave", "Clave"}, {"asignatura", "Asignatura"}, {"grupo", "Grupo"}, {"inscritos", "Inscritos"},
                {"del_grupo", "Alumnos del grupo"}, {"estado", "Estado"}}, 10, List.of());
        tabla.proporciones(List.of(1, 3, 1, 1, 2, 1));
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Curso c : g.getCursos().values().stream().sorted(Comparator.comparing(Curso::grupo).thenComparing(Curso::asignatura)).toList()) {
            int n = g.alumnosDeCurso(c.id()).size();
            long total = g.getEstudiantes().values().stream().filter(e -> e.grupo().equals(c.grupo())).count();
            filas.add(VistaAlumno.fila("clave", c.id(), "asignatura", c.asignatura(), "grupo", c.grupo(), "inscritos", n, "del_grupo", total,
                    "estado", n == total ? "Completo" : "Pendiente"));
        }
        tabla.cargar(filas);
        poner(t, tabla);
        app.agregar(t);
    }
}
