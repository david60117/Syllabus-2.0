package mx.uaeh.syllabus.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import mx.uaeh.syllabus.modelo.Cuenta;
import mx.uaeh.syllabus.modelo.ErrorValidacion;
import mx.uaeh.syllabus.modelo.Estudiante;
import mx.uaeh.syllabus.modelo.RegistroError;
import mx.uaeh.syllabus.ui.Componentes.Campo;

/** Control escolar: alta, edición y baja de estudiantes, padrón y carga masiva. */
public class VistaEstudiantes extends Vista {
    public VistaEstudiantes(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        app.encabezado("Control escolar  /  Estudiantes", "Estudiantes",
                "El número de cuenta y la cuenta de usuario se generan solos; el alumno queda inscrito en las materias de su grupo.");
        if (pestanas("Estudiantes", "Alta y edición", "Carga masiva") == 0) {
            alta();
        } else {
            cargaMasiva();
        }
    }

    private void alta() {
        List<String> grupos = new ArrayList<>(g.getGruposRegistro().keySet());
        grupos.sort(null);
        Map<String, String> estado = app.estado("form_estudiante", Map.of("sel", "", "apellidos", "", "nombre", "",
                "grupo", grupos.isEmpty() ? "" : grupos.get(0), "inscribir", "1"));
        String sel = g.getEstudiantes().containsKey(estado.get("sel")) ? estado.get("sel") : "";
        JPanel form = tarjeta(sel.isEmpty() ? "Nuevo estudiante" : "Editar estudiante " + sel);
        poner(form, soloLectura("Número de cuenta", sel.isEmpty() ? g.siguienteCuenta() + "  (se asigna al guardar)" : sel, 30, Tema.BLANCO).panel());
        Campo apellidos = texto(estado, "apellidos", "Apellidos", 30, Tema.BLANCO);
        poner(form, apellidos.panel());
        poner(form, texto(estado, "nombre", "Nombre(s)", 30, Tema.BLANCO).panel());
        poner(form, (grupos.isEmpty() ? texto(estado, "grupo", "Grupo (3 dígitos)", 30, Tema.BLANCO)
                : opciones(estado, "grupo", "Grupo", grupos, Tema.BLANCO, null)).panel());
        Runnable limpiar = () -> {
            estado.put("sel", "");
            estado.put("apellidos", "");
            estado.put("nombre", "");
            app.refrescar();
        };
        Runnable guardar = () -> {
            if (!sel.isEmpty()) {
                Estudiante anterior = g.getEstudiantes().get(sel);
                if (!anterior.grupo().equals(estado.get("grupo").strip()) && !app.confirmar("Cambio de grupo",
                        "Al cambiar de grupo se le dan de baja sus materias actuales (con sus calificaciones)\n"
                                + "y se inscribe en las del nuevo grupo. ¿Continuar?")) {
                    return;
                }
                if (app.hacer(() -> g.editarEstudiante(sel, estado.get("apellidos"), estado.get("nombre"), estado.get("grupo")))) {
                    limpiar.run();
                }
                return;
            }
            Estudiante e = app.obtener(() -> g.altaEstudiante(estado.get("apellidos"), estado.get("nombre"), estado.get("grupo")));
            if (e != null) {
                int n = g.cursosDe(e.numCuenta()).size();
                estado.put("apellidos", "");
                estado.put("nombre", "");
                app.informar("Estudiante registrado", e.nombreCompleto() + "\nNúmero de cuenta: " + e.numCuenta() + "\nInscrito en " + n
                        + " materias del grupo " + e.grupo() + ".\n\nUsuario: " + e.numCuenta() + "\nContraseña inicial: " + e.numCuenta()
                        + " (se le pedirá cambiarla).");
            }
        };
        Runnable baja = () -> {
            Estudiante e = g.getEstudiantes().get(sel);
            if (app.confirmar("Dar de baja", "¿Dar de baja a " + e.nombreCompleto() + " (" + sel + ")?\n"
                    + "Se eliminan sus inscripciones, calificaciones y su cuenta de usuario.") && app.hacer(() -> g.bajaEstudiante(sel))) {
                limpiar.run();
            }
        };
        form.add(javax.swing.Box.createVerticalStrut(10));
        if (sel.isEmpty()) {
            poner(form, linea(null, boton("Registrar estudiante", guardar, true)));
        } else {
            poner(form, linea(null, boton("Guardar cambios", guardar, true), boton("Nuevo", limpiar, false), boton("Dar de baja", baja, false)));
        }
        nota(form, "Los grupos se registran en «Grupos y materias». Al registrarlo queda inscrito en todas las materias de su grupo.");
        JPanel resumen = tarjeta("Grupos");
        Tabla tg = new Tabla(new String[][]{{"grupo", "Grupo"}, {"alumnos", "Alumnos"}, {"materias", "Materias"}}, 6, List.of());
        List<Map<String, Object>> filasGrupos = new ArrayList<>();
        for (String gr : grupos) {
            filasGrupos.add(VistaAlumno.fila("grupo", gr, "alumnos", g.getEstudiantes().values().stream().filter(e -> e.grupo().equals(gr)).count(),
                    "materias", g.cursosDeGrupo(gr).size()));
        }
        tg.cargar(filasGrupos);
        poner(resumen, tg);
        app.agregar(fila(Tema.FONDO, form, resumen));

        JPanel lista = tarjeta("Padrón de estudiantes");
        Map<String, String> filtros = app.estado("filtro:padron", Map.of("texto", "", "grupo", "Todos"));
        Map<String, String> usuarios = new java.util.HashMap<>();
        for (Cuenta u : app.getCuentas().getUsuarios()) {
            if ("Alumno".equals(u.rol())) {
                usuarios.put(u.referencia(), u.usuario());
            }
        }
        List<Map<String, Object>> todos = new ArrayList<>();
        for (Estudiante e : g.getEstudiantes().values()) {
            todos.add(VistaAlumno.fila("num_cuenta", e.numCuenta(), "apellidos", e.apellidos(), "nombre", e.nombre(), "grupo", e.grupo(),
                    "materias", g.cursosDe(e.numCuenta()).size(), "usuario", usuarios.getOrDefault(e.numCuenta(), "—")));
        }
        Tabla tabla = new Tabla(new String[][]{{"num_cuenta", "Núm. de cuenta"}, {"apellidos", "Apellidos"}, {"nombre", "Nombre"},
                {"grupo", "Grupo"}, {"materias", "Materias"}, {"usuario", "Usuario"}}, 10);
        tabla.proporciones(List.of(1, 2, 2, 1, 1, 1));
        Runnable aplicar = () -> {
            String q = filtros.get("texto").strip().toLowerCase();
            tabla.cargar(todos.stream().filter(x -> ("Todos".equals(filtros.get("grupo")) || filtros.get("grupo").equals(x.get("grupo")))
                    && (q.isEmpty() || ((String) x.get("num_cuenta")).contains(q)
                    || (x.get("apellidos") + " " + x.get("nombre")).toLowerCase().contains(q))).toList());
        };
        Campo buscar = texto(filtros, "texto", "Buscar (cuenta o nombre)", 24, Tema.BLANCO, aplicar);
        List<String> conTodos = new ArrayList<>(List.of("Todos"));
        conTodos.addAll(grupos);
        Campo grupo = opciones(filtros, "grupo", "Grupo", conTodos, Tema.BLANCO, aplicar);
        poner(lista, linea(null, buscar.panel(), grupo.panel()), 10);
        tabla.alSeleccionar(x -> {
            Estudiante e = g.getEstudiantes().get((String) x.get("num_cuenta"));
            estado.put("sel", e.numCuenta());
            estado.put("apellidos", e.apellidos());
            estado.put("nombre", e.nombre());
            estado.put("grupo", e.grupo());
            javax.swing.SwingUtilities.invokeLater(app::refrescar);
        });
        poner(lista, tabla, 8);
        poner(lista, etiqueta("Haz clic en un estudiante para editarlo o darlo de baja.", 12, false, Tema.SUAVE));
        aplicar.run();
        app.agregar(lista);
    }

    private void cargaMasiva() {
        JPanel t = tarjeta("Importar archivo JSON", "Agrega estudiantes, materias, parciales, evaluaciones, inscripciones y calificaciones con el "
                + "formato de datos/ejemplo_entrada.json. Los registros válidos se guardan; los inválidos se listan sin detener la carga.");
        Map<String, Object> resultados = app.memoria("carga_masiva", java.util.HashMap::new);
        poner(t, linea(null, boton("Seleccionar archivo e importar", () -> {
            Path ruta = elegirParaAbrir("Importar archivo JSON");
            if (ruta == null) {
                return;
            }
            try {
                List<RegistroError> errores = app.importarArchivo(ruta);
                resultados.put("archivo", ruta.getFileName().toString());
                resultados.put("errores", errores);
            } catch (ErrorValidacion e) {
                app.avisar("Archivo inválido", e.getDetalle(), true);
                return;
            }
            app.refrescar();
        }, true)));
        app.agregar(t);
        if (resultados.get("errores") instanceof List<?> errores) {
            JPanel r = tarjeta("Resultado · " + resultados.get("archivo"));
            poner(r, etiqueta("Registros rechazados: " + errores.size(), 14, true, Tema.acento()), 8);
            Tabla tabla = new Tabla(new String[][]{{"tipo", "Tipo"}, {"codigo", "Código"}, {"detalle", "Detalle"}}, 8, List.of());
            tabla.proporciones(List.of(1, 2, 5));
            tabla.cargar(errores.stream().map(x -> (RegistroError) x)
                    .map(e -> VistaAlumno.fila("tipo", e.tipo(), "codigo", e.codigo(), "detalle", e.detalle())).toList());
            poner(r, tabla);
            app.agregar(r);
        }
    }

    /** Casilla con fondo blanco (la usan otras vistas). */
    static JCheckBox casilla(String texto, boolean marcada) {
        return Entradas.casilla(texto, marcada, Tema.BLANCO);
    }
}
