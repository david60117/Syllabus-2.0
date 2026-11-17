package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/** Inicio de cada perfil: saludo, indicadores, pendientes y accesos principales. */
public class VistaInicio extends Vista {
    private static final Color AMBAR = new Color(0xa26b34);
    private static final Color VERDE = new Color(0x247457);

    /** Pendiente: texto, sección a la que lleva y si es alerta. */
    private record Pendiente(String texto, String destino, boolean alerta) {
    }

    public VistaInicio(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        String rol = app.getRol();
        String[] panel = switch (rol) {
            case "Administrador" -> new String[]{"Control escolar", "Estudiantes, grupos, materias, docentes, inscripciones, consultas y reportes.",
                    "ADMINISTRACIÓN ESCOLAR", "Registra la información una sola vez: lo demás se genera solo."};
            case "Docente" -> new String[]{"Panel docente", "Tus materias, su plan de evaluación y la captura de calificaciones.",
                    "DOCENCIA", "Define el plan de cada parcial y captura el acta del grupo completo."};
            default -> new String[]{"Mi espacio académico", "Tus materias, calificaciones, promedio y estado académico.",
                    "ALUMNO", "Consulta tu avance; las calificaciones las registran tus docentes."};
        };
        app.encabezado("Inicio  /  " + rol, panel[0], panel[1]);
        Redondo banda = Redondo.vertical(11, Tema.acentoSuave(), null, 18, 24);
        banda.add(etiqueta(panel[2], 11, true, Tema.acento()));
        banda.add(javax.swing.Box.createVerticalStrut(4));
        banda.add(etiqueta("Hola, " + app.nombreUsuario(), 20, true, null));
        banda.add(javax.swing.Box.createVerticalStrut(2));
        banda.add(etiqueta(panel[3], 14, false, Tema.SUAVE));
        banda.setMaximumSize(new Dimension(Integer.MAX_VALUE, banda.getPreferredSize().height));
        app.agregar(banda);
        if ("Alumno".equals(rol)) {
            VistaAlumno.inicio(this);
            return;
        }
        List<ResumenEstudiante> resumenes = app.resumenesVisibles();
        long riesgo = resumenes.stream().filter(r -> "ALTO".equals(r.riesgo()) || "MEDIO".equals(r.riesgo())).count();
        List<Pendiente> pendientes;
        String[][] tarjetas;
        if ("Administrador".equals(rol)) {
            app.agregar(Componentes.mosaicos(new Object[][]{{"Estudiantes", g.getEstudiantes().size()}, {"Grupos", g.grupos().size()},
                    {"Materias", g.getCursos().size()}, {"Docentes", g.getDocentes().size()}, {"En riesgo", riesgo}}));
            pendientes = pendientesAdmin();
            tarjetas = new String[][]{
                    {"Estudiantes", "Alta con número de cuenta automático e inscripción al grupo.", "Abrir Estudiantes", "Estudiantes"},
                    {"Grupos y materias", "Grupos (semestre y turno) y materias con su docente.", "Abrir Grupos y materias", "Grupos y materias"},
                    {"Docentes", "Alta de docentes y asignación de materias.", "Abrir Docentes", "Docentes"},
                    {"Inscripciones", "Altas y bajas individuales de materias.", "Abrir Inscripciones", "Inscripciones"},
                    {"Consultas y kárdex", "Promedios, orden por promedio y riesgo académico.", "Abrir Consultas", "Consultas"},
                    {"Reportes y exportación", "Boleta, kárdex, actas y estadísticas en PDF, CSV o JSON.", "Abrir Reportes", "Reportes"}};
        } else {
            List<Curso> cursos = app.cursosVisibles();
            int porCapturar = cursos.stream().mapToInt(c -> {
                int[] a = avanceCaptura(c.id());
                return a[1] - a[0];
            }).sum();
            app.agregar(Componentes.mosaicos(new Object[][]{{"Mis materias", cursos.size()}, {"Alumnos", resumenes.size()},
                    {"Por capturar", porCapturar}, {"En riesgo", riesgo}}));
            pendientes = pendientesDocente(cursos);
            tarjetas = new String[][]{
                    {"Mis cursos", "Materias y grupos que tienes asignados.", "Ver cursos", "Mis cursos"},
                    {"Plan de evaluación", "Evaluaciones y porcentajes de cada parcial.", "Abrir plan", "Plan de evaluación"},
                    {"Acta de calificaciones", "Captura todo el grupo y guarda de una vez.", "Abrir captura", "Captura"},
                    {"Consultas", "Promedios y kárdex de tus alumnos.", "Abrir Consultas", "Consultas"},
                    {"Riesgo académico", "Alumnos en riesgo de tus materias.", "Ver riesgo", "Consultas:1"},
                    {"Reportes", "Aprobados, reprobados y estadísticas.", "Abrir Reportes", "Reportes"}};
        }
        JPanel t = tarjeta("Pendientes");
        for (Pendiente p : pendientes) {
            JPanel f = new JPanel(new BorderLayout(10, 0));
            f.setOpaque(false);
            f.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            f.add(punto(p.alerta() ? AMBAR : VERDE), BorderLayout.WEST);
            f.add(etiqueta(p.texto(), 14, false, null));
            if (p.alerta()) {
                f.add(enlace("Ir a " + p.destino() + "  →", () -> app.irA(p.destino())), BorderLayout.EAST);
            }
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
            poner(t, f);
        }
        app.agregar(t);
        app.agregar(etiqueta("ACCESOS PRINCIPALES", 12, true, Tema.SUAVE));
        JPanel rejilla = new JPanel(new GridLayout(0, 3, 16, 16));
        rejilla.setBackground(Tema.FONDO);
        for (String[] x : tarjetas) {
            Redondo c = Redondo.vertical(10, Tema.BLANCO, Tema.BORDE, 19, 19);
            Redondo icono = new Redondo(8, Tema.acentoSuave(), null, 1f, new java.awt.GridBagLayout());
            icono.add(new JLabel(Iconos.icono(App.iconoDe(x[3].split(":")[0]), 18, 22, Tema.acento())));
            icono.setPreferredSize(new Dimension(38, 38));
            icono.setMaximumSize(new Dimension(38, 38));
            c.add(icono);
            c.add(javax.swing.Box.createVerticalStrut(10));
            c.add(etiqueta(x[0], 16, true, null));
            c.add(javax.swing.Box.createVerticalStrut(4));
            c.add(parrafo(x[1], 13, Tema.SUAVE));
            c.add(javax.swing.Box.createVerticalStrut(8));
            c.add(enlace(x[2] + "   →", () -> app.irA(x[3])));
            rejilla.add(c);
        }
        app.agregar(rejilla);
    }

    private List<Pendiente> pendientesAdmin() {
        long sinDocente = g.getCursos().values().stream().filter(c -> g.docenteDe(c.id()) == null).count();
        long sinPlan = g.getCursos().values().stream().filter(c -> planCompleto(c.id()) < 3).count();
        long sinMaterias = g.getEstudiantes().values().stream().filter(e -> g.cursosDe(e.numCuenta()).isEmpty()).count();
        long iniciales = app.getCuentas().getUsuarios().stream().filter(u -> u.debeCambiar()).count();
        return List.of(new Pendiente(sinDocente + " materias sin docente asignado", "Grupos y materias", sinDocente > 0),
                new Pendiente(sinPlan + " materias con plan de evaluación incompleto (lo define cada docente)", "Grupos y materias", sinPlan > 0),
                new Pendiente(sinMaterias + " estudiantes sin materias inscritas", "Inscripciones", sinMaterias > 0),
                new Pendiente(iniciales + " cuentas con contraseña inicial", "Usuarios", iniciales > 0));
    }

    private List<Pendiente> pendientesDocente(List<Curso> cursos) {
        List<Pendiente> filas = new ArrayList<>();
        for (Curso c : cursos) {
            int[] a = avanceCaptura(c.id());
            int plan = planCompleto(c.id());
            if (plan < 3) {
                filas.add(new Pendiente(c.asignatura() + " (" + c.grupo() + "): plan de evaluación " + plan + "/3 parciales", "Plan de evaluación", true));
            } else if (a[0] < a[1]) {
                filas.add(new Pendiente(c.asignatura() + " (" + c.grupo() + "): " + (a[1] - a[0]) + " calificaciones por capturar", "Captura", true));
            }
        }
        return filas.isEmpty() ? List.of(new Pendiente("Sin pendientes: tus materias tienen plan y calificaciones completas.", "Mis cursos", false)) : filas;
    }
}
