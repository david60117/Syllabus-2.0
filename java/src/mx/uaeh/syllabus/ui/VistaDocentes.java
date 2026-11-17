package mx.uaeh.syllabus.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import mx.uaeh.syllabus.modelo.Cuenta;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Docente;

/** Control escolar: alta de docentes y asignación de materias (cada materia tiene un solo docente). */
public class VistaDocentes extends Vista {
    public VistaDocentes(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        app.encabezado("Control escolar  /  Docentes", "Docentes", "Al registrar un docente se crea su cuenta. Cada materia tiene un solo docente.");
        Map<String, String> estado = app.estado("form_docente", Map.of("nombre", "", "docente", ""));
        JPanel alta = tarjeta("Nuevo docente");
        poner(alta, soloLectura("ID", g.siguienteDocente() + "  (se asigna al guardar)", 30, Tema.BLANCO).panel());
        poner(alta, texto(estado, "nombre", "Nombre completo", 30, Tema.BLANCO).panel());
        alta.add(javax.swing.Box.createVerticalStrut(10));
        poner(alta, linea(null, boton("Registrar docente", () -> {
            Docente d = app.obtener(() -> g.altaDocente(estado.get("nombre")));
            if (d != null) {
                estado.put("nombre", "");
                estado.put("docente", docenteTxt(d));
                app.informar("Docente registrado", d.id() + " · " + d.nombre() + "\n\nUsuario: " + d.id().toLowerCase()
                        + "\nContraseña inicial: " + d.id().toLowerCase() + " (se le pedirá cambiarla).\n\nAhora asígnale sus materias.");
            }
        }, true)));

        JPanel asig = tarjeta("Materias del docente");
        List<String> docentes = g.getDocentes().values().stream().sorted(Comparator.comparing(Docente::id)).map(Vista::docenteTxt).toList();
        if (docentes.isEmpty()) {
            poner(asig, etiqueta("Registra un docente primero.", 13, false, Tema.SUAVE));
        } else {
            poner(asig, opciones(estado, "docente", "Docente", docentes, Tema.BLANCO, app::refrescar).panel(), 8);
            String did = primero(estado.get("docente"));
            Map<String, JCheckBox> marcas = new LinkedHashMap<>();
            Redondo lista = Redondo.vertical(7, Tema.BLANCO, Tema.BORDE, 8, 12);
            for (Curso c : g.getCursos().values().stream().sorted(Comparator.comparing(Curso::grupo).thenComparing(Curso::asignatura)).toList()) {
                Docente actual = g.docenteDe(c.id());
                String extra = actual == null || actual.id().equals(did) ? "" : "   · ahora: " + actual.nombre();
                JCheckBox caja = VistaEstudiantes.casilla(c.id() + "  " + c.asignatura() + " · " + c.grupo() + extra, actual != null && actual.id().equals(did));
                marcas.put(c.id(), caja);
                lista.add(caja);
            }
            if (marcas.isEmpty()) {
                lista.add(etiqueta("No hay materias registradas.", 13, false, Tema.SUAVE));
            }
            poner(asig, lista, 10);
            poner(asig, linea(null, boton("Guardar asignación", () -> app.hacer(() -> g.asignarCursos(did,
                    marcas.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList())), true)));
            nota(asig, "Si marcas una materia de otro docente, pasa a este docente.");
        }
        app.agregar(fila(Tema.FONDO, alta, asig));

        JPanel t = tarjeta("Plantilla docente");
        Tabla tabla = new Tabla(new String[][]{{"id", "ID"}, {"nombre", "Nombre"}, {"usuario", "Usuario"}, {"materias", "Materias"},
                {"alumnos", "Alumnos"}}, 8, List.of());
        tabla.proporciones(List.of(1, 2, 1, 4, 1));
        Map<String, String> usuarios = new java.util.HashMap<>();
        for (Cuenta u : app.getCuentas().getUsuarios()) {
            if ("Docente".equals(u.rol())) {
                usuarios.put(u.referencia(), u.usuario());
            }
        }
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Docente d : g.getDocentes().values().stream().sorted(Comparator.comparing(Docente::id)).toList()) {
            List<Curso> cursos = g.cursosDeDocente(d.id());
            String materias = cursos.stream().map(c -> c.asignatura() + " (" + c.grupo() + ")").collect(Collectors.joining(", "));
            filas.add(VistaAlumno.fila("id", d.id(), "nombre", d.nombre(), "usuario", usuarios.getOrDefault(d.id(), "—"),
                    "materias", materias.isEmpty() ? "Sin materias" : materias,
                    "alumnos", cursos.stream().mapToInt(c -> g.alumnosDeCurso(c.id()).size()).sum()));
        }
        tabla.cargar(filas);
        poner(t, tabla);
        app.agregar(t);
    }
}
