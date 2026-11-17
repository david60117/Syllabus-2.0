package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Docente;
import mx.uaeh.syllabus.modelo.Grupo;
import mx.uaeh.syllabus.modelo.Parcial;

/** Control escolar: registro de grupos y de materias (con sus 3 parciales, docente e inscripción del grupo). */
public class VistaGruposMaterias extends Vista {
    public VistaGruposMaterias(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        app.encabezado("Control escolar  /  Grupos y materias", "Grupos y materias",
                "Registra los grupos y crea cada materia con sus 3 parciales y su docente; el grupo completo queda inscrito.");
        if (pestanas("Grupos y materias", "Grupos", "Materias") == 0) {
            grupos();
        } else {
            materias();
        }
    }

    private List<Grupo> gruposOrdenados() {
        return g.getGruposRegistro().values().stream().sorted(Comparator.comparing(Grupo::clave)).toList();
    }

    private long sinDocente(String grupo) {
        return g.cursosDeGrupo(grupo).stream().filter(c -> g.docenteDe(c.id()) == null).count();
    }

    private void grupos() {
        Map<String, String> estado = app.estado("form_grupo", Map.of("sel", "", "clave", "", "semestre", "1", "turno", GestorEscolar.TURNOS.get(0)));
        String sel = g.getGruposRegistro().containsKey(estado.get("sel")) ? estado.get("sel") : "";
        JPanel form = tarjeta(sel.isEmpty() ? "Nuevo grupo" : "Editar grupo " + sel);
        poner(form, sel.isEmpty() ? texto(estado, "clave", "Clave del grupo (3 dígitos, p. ej. 303)", 30, Tema.BLANCO).panel()
                : soloLectura("Clave del grupo", sel, 30, Tema.BLANCO).panel());
        List<String> semestres = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
        poner(form, linea(null, opciones(estado, "semestre", "Semestre", semestres, Tema.BLANCO, null).panel(),
                opciones(estado, "turno", "Turno", GestorEscolar.TURNOS, Tema.BLANCO, null).panel()));
        Runnable limpiar = () -> {
            estado.putAll(Map.of("sel", "", "clave", "", "semestre", "1", "turno", GestorEscolar.TURNOS.get(0)));
            app.refrescar();
        };
        Runnable guardar = () -> {
            if (!sel.isEmpty()) {
                if (app.hacer(() -> g.editarGrupo(sel, estado.get("semestre"), estado.get("turno")))) {
                    limpiar.run();
                }
                return;
            }
            Grupo gr = app.obtener(() -> g.altaGrupo(estado.get("clave"), estado.get("semestre"), estado.get("turno")));
            if (gr != null) {
                estado.put("clave", "");
                app.informar("Grupo registrado", "Grupo " + gr.clave() + " · " + gr.semestre() + ".º semestre · " + gr.turno()
                        + "\n\nAhora crea sus materias en la pestaña «Materias» y registra a sus alumnos.");
            }
        };
        Runnable baja = () -> {
            if (app.confirmar("Eliminar grupo", "¿Eliminar el grupo " + sel + "?") && app.hacer(() -> g.bajaGrupo(sel))) {
                limpiar.run();
            }
        };
        form.add(javax.swing.Box.createVerticalStrut(10));
        poner(form, sel.isEmpty() ? linea(null, boton("Registrar grupo", guardar, true))
                : linea(null, boton("Guardar cambios", guardar, true), boton("Nuevo", limpiar, false), boton("Eliminar", baja, false)));
        nota(form, "Un grupo solo se puede eliminar si no tiene alumnos ni materias.");
        JPanel pasos = tarjeta("Orden de registro");
        String[] textos = {"Registra el grupo (clave, semestre y turno).", "Crea sus materias y asigna el docente.",
                "Registra a los alumnos: se inscriben solos en las materias del grupo.",
                "Cada docente define su plan de evaluación y captura calificaciones."};
        for (int i = 0; i < textos.length; i++) {
            JPanel f = new JPanel(new BorderLayout(12, 0));
            f.setOpaque(false);
            f.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
            JPanel izq = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
            izq.setOpaque(false);
            izq.add(insignia(String.format("%02d", i + 1)));
            f.add(izq, BorderLayout.WEST);
            f.add(Componentes.parrafo(textos[i], 14, null, 320));
            f.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
            poner(pasos, f);
        }
        app.agregar(fila(Tema.FONDO, form, pasos));
        JPanel t = tarjeta("Grupos registrados");
        Tabla tabla = new Tabla(new String[][]{{"grupo", "Grupo"}, {"semestre", "Semestre"}, {"turno", "Turno"}, {"alumnos", "Alumnos"},
                {"materias", "Materias"}, {"sin_docente", "Sin docente"}}, 8, List.of());
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Grupo gr : gruposOrdenados()) {
            filas.add(VistaAlumno.fila("grupo", gr.clave(), "semestre", gr.semestre() + ".º", "turno", gr.turno(),
                    "alumnos", g.getEstudiantes().values().stream().filter(e -> e.grupo().equals(gr.clave())).count(),
                    "materias", g.cursosDeGrupo(gr.clave()).size(), "sin_docente", sinDocente(gr.clave())));
        }
        tabla.cargar(filas);
        tabla.alSeleccionar(x -> {
            Grupo gr = g.getGruposRegistro().get((String) x.get("grupo"));
            estado.putAll(Map.of("sel", gr.clave(), "clave", gr.clave(), "semestre", String.valueOf(gr.semestre()), "turno", gr.turno()));
            javax.swing.SwingUtilities.invokeLater(app::refrescar);
        });
        poner(t, tabla, 8);
        poner(t, etiqueta("Haz clic en un grupo para editarlo o eliminarlo.", 12, false, Tema.SUAVE));
        app.agregar(t);
    }

    private void materias() {
        List<String> docentes = new ArrayList<>(List.of("Sin asignar"));
        g.getDocentes().values().stream().sorted(Comparator.comparing(Docente::id)).forEach(d -> docentes.add(docenteTxt(d)));
        Map<String, String> inicial = new HashMap<>(Map.of("sel", "", "asignatura", "", "grupo", "", "docente", "Sin asignar"));
        inicial.putAll(Map.of("p1", "30", "p2", "30", "p3", "40"));
        Map<String, String> estado = app.estado("form_materia", inicial);
        String sel = g.getCursos().containsKey(estado.get("sel")) ? estado.get("sel") : "";
        JPanel form = tarjeta(sel.isEmpty() ? "Nueva materia" : "Editar materia " + sel);
        poner(form, texto(estado, "asignatura", "Asignatura", 30, Tema.BLANCO).panel());
        List<String> grupos = gruposOrdenados().stream().map(Grupo::clave).toList();
        poner(form, sel.isEmpty() ? opciones(estado, "grupo", "Grupo", grupos.isEmpty() ? List.of("Registra un grupo primero") : grupos,
                Tema.BLANCO, null).panel() : soloLectura("Grupo", estado.get("grupo"), 30, Tema.BLANCO).panel());
        poner(form, opciones(estado, "docente", "Docente", docentes, Tema.BLANCO, null).panel());
        form.add(javax.swing.Box.createVerticalStrut(8));
        poner(form, etiqueta("Peso de cada parcial en la calificación final (%)", 12, false, Tema.SUAVE));
        poner(form, linea(null, texto(estado, "p1", "Parcial 1", 5, Tema.BLANCO).panel(), texto(estado, "p2", "Parcial 2", 5, Tema.BLANCO).panel(),
                texto(estado, "p3", "Parcial 3", 5, Tema.BLANCO).panel()));
        java.util.function.Supplier<String> docenteId = () -> "Sin asignar".equals(estado.get("docente")) ? null : primero(estado.get("docente"));
        Runnable limpiar = () -> {
            estado.putAll(Map.of("sel", "", "asignatura", "", "docente", "Sin asignar", "p1", "30", "p2", "30", "p3", "40"));
            app.refrescar();
        };
        Runnable guardar = () -> {
            List<Object> pesos = List.of(peso(estado.get("p1")), peso(estado.get("p2")), peso(estado.get("p3")));
            if (!sel.isEmpty()) {
                if (app.hacer(() -> g.editarCurso(sel, estado.get("asignatura"), pesos, docenteId.get()))) {
                    limpiar.run();
                }
                return;
            }
            Curso c = app.obtener(() -> g.altaCurso(estado.get("asignatura"), estado.get("grupo"), pesos, docenteId.get()));
            if (c != null) {
                estado.put("asignatura", "");
                app.informar("Materia creada", c.id() + " · " + c.asignatura() + " · grupo " + c.grupo() + "\nParciales creados: 3\n"
                        + "Alumnos inscritos: " + g.alumnosDeCurso(c.id()).size()
                        + "\n\nEl docente asignado define las evaluaciones de cada parcial en «Plan de evaluación».");
            }
        };
        Runnable eliminar = () -> {
            Curso c = g.getCursos().get(sel);
            if (app.confirmar("Eliminar materia", "¿Eliminar " + c.id() + " · " + c.asignatura() + " (" + c.grupo() + ")?\n"
                    + "Se quitan sus parciales, evaluaciones e inscripciones.") && app.hacer(() -> g.eliminarCurso(sel))) {
                limpiar.run();
            }
        };
        form.add(javax.swing.Box.createVerticalStrut(10));
        poner(form, sel.isEmpty() ? linea(null, boton("Crear materia", guardar, true))
                : linea(null, boton("Guardar cambios", guardar, true), boton("Nueva", limpiar, false), boton("Eliminar", eliminar, false)));
        JPanel resumen = tarjeta("Grupos");
        Tabla tg = new Tabla(new String[][]{{"grupo", "Grupo"}, {"semestre", "Semestre"}, {"turno", "Turno"}, {"materias", "Materias"},
                {"sin_docente", "Sin docente"}}, 6, List.of());
        tg.cargar(gruposOrdenados().stream().map(gr -> VistaAlumno.fila("grupo", gr.clave(), "semestre", gr.semestre() + ".º", "turno", gr.turno(),
                "materias", g.cursosDeGrupo(gr.clave()).size(), "sin_docente", sinDocente(gr.clave()))).toList());
        poner(resumen, tg);
        nota(resumen, "Los grupos se registran en la pestaña «Grupos».");
        app.agregar(fila(Tema.FONDO, form, resumen));

        JPanel t = tarjeta("Materias");
        Tabla tabla = new Tabla(new String[][]{{"clave", "Clave"}, {"asignatura", "Asignatura"}, {"grupo", "Grupo"}, {"docente", "Docente"},
                {"inscritos", "Inscritos"}, {"pesos", "Parciales %"}, {"plan", "Plan de evaluación"}}, 10, List.of());
        tabla.proporciones(List.of(1, 3, 1, 2, 1, 1, 2));
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Curso c : g.getCursos().values().stream().sorted(Comparator.comparing(Curso::grupo).thenComparing(Curso::asignatura)).toList()) {
            int plan = planCompleto(c.id());
            filas.add(VistaAlumno.fila("clave", c.id(), "asignatura", c.asignatura(), "grupo", c.grupo(), "docente", docenteTxt(g.docenteDe(c.id())),
                    "inscritos", g.alumnosDeCurso(c.id()).size(),
                    "pesos", g.parcialesDe(c.id()).stream().map(p -> pct(p.peso())).collect(Collectors.joining(" / ")),
                    "plan", plan == 3 ? "Completo" : "Pendiente " + plan + "/3"));
        }
        tabla.cargar(filas);
        tabla.alSeleccionar(x -> {
            String cid = (String) x.get("clave");
            Curso c = g.getCursos().get(cid);
            List<Parcial> ps = g.parcialesDe(cid);
            estado.putAll(Map.of("sel", cid, "asignatura", c.asignatura(), "grupo", c.grupo(), "docente", docenteTxt(g.docenteDe(cid))));
            for (int i = 0; i < ps.size() && i < 3; i++) {
                estado.put("p" + (i + 1), pct(ps.get(i).peso()));
            }
            javax.swing.SwingUtilities.invokeLater(app::refrescar);
        });
        poner(t, tabla, 8);
        poner(t, etiqueta("Haz clic en una materia para editarla, cambiar su docente o eliminarla.", 12, false, Tema.SUAVE));
        app.agregar(t);
    }
}
