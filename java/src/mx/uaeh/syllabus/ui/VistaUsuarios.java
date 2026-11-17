package mx.uaeh.syllabus.ui;

import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JPanel;

import mx.uaeh.syllabus.archivos.Persistencia;
import mx.uaeh.syllabus.logica.Autenticacion;
import mx.uaeh.syllabus.modelo.Cuenta;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Estudiante;

/** Control escolar: cuentas de acceso, contraseñas iniciales, reporte de credenciales y respaldo. */
public class VistaUsuarios extends Vista {
    public VistaUsuarios(App app) {
        super(app);
    }

    private String grupoDe(Cuenta u) {
        Estudiante e = "Alumno".equals(u.rol()) ? g.getEstudiantes().get(u.referencia()) : null;
        return e == null ? "—" : e.grupo();
    }

    @Override
    public void dibujar() {
        app.encabezado("Control escolar  /  Usuarios", "Usuarios y accesos",
                "Haz clic en una cuenta para ver su usuario y contraseña. Restablecer deja la contraseña igual al usuario.");
        Map<String, String> filtros = app.estado("filtro:usuarios", Map.of("texto", "", "rol", "Todos", "grupo", "Todos", "sel", ""));
        Cuenta elegido = app.getCuentas().getUsuarios().stream().filter(u -> u.usuario().equals(filtros.get("sel"))).findFirst().orElse(null);
        JPanel det = tarjeta("Cuenta seleccionada");
        if (elegido == null) {
            poner(det, etiqueta("Selecciona una cuenta en la tabla.", 14, false, Tema.SUAVE));
        } else {
            String visible = Autenticacion.contrasenaVisible(elegido);
            boolean alumno = "Alumno".equals(elegido.rol());
            String materias = g.cursosDeDocente(elegido.referencia()).stream().map(Curso::id).collect(Collectors.joining(", "));
            JPanel rej = new JPanel(new GridLayout(2, 2, 40, 8));
            rej.setOpaque(false);
            rej.add(dato("Usuario", elegido.usuario(), null, null));
            rej.add(dato("Nombre", elegido.nombre(), null, null));
            rej.add(dato("Rol", elegido.rol(), null, null));
            rej.add(dato(alumno ? "Grupo" : "Materias", alumno ? grupoDe(elegido) : materias.isEmpty() ? "—" : materias, null, null));
            poner(det, rej, 10);
            Redondo caja = Redondo.vertical(8, Tema.acentoSuave(), null, 10, 14);
            caja.add(etiqueta("Contraseña", 12, false, Tema.SUAVE));
            if (visible != null) {
                caja.add(etiqueta(visible, 20, true, Tema.acento()));
                caja.add(etiqueta("Contraseña inicial: se le pedirá cambiarla al entrar.", 12, false, Tema.SUAVE));
            } else {
                caja.add(etiqueta("Personalizada por el usuario", 17, true, Tema.acento()));
                caja.add(parrafo("Está cifrada y no se puede consultar. Restablécela para asignarle una nueva.", 12, Tema.SUAVE));
            }
            poner(det, caja);
            if (!elegido.usuario().equals(app.getCuenta().usuario())) {
                det.add(javax.swing.Box.createVerticalStrut(12));
                poner(det, linea(null, boton("Restablecer contraseña", () -> {
                    if (app.confirmar("Restablecer contraseña", "La contraseña de " + elegido.usuario() + " será «" + elegido.usuario()
                            + "» y deberá cambiarla al entrar. ¿Continuar?")) {
                        Autenticacion.restablecer(elegido);
                        app.guardarAhora();
                        app.registrarActividad("contrasena_restablecida · " + elegido.usuario());
                        app.refrescar();
                    }
                }, false)));
            }
        }
        JPanel rep = tarjeta("Reporte de usuarios y contraseñas");
        poner(rep, etiqueta("Genera la lista con encabezado institucional en PDF, CSV o JSON.", 12, false, Tema.SUAVE), 10);
        List<String> opciones = new ArrayList<>(List.of("Docentes"));
        g.grupos().forEach(x -> opciones.add("Grupo " + x));
        opciones.add("Todos");
        JPanel botones = new JPanel(new GridLayout(0, 4, 8, 8));
        botones.setOpaque(false);
        for (int i = 0; i < opciones.size(); i++) {
            String filtro = opciones.get(i);
            botones.add(boton(filtro, () -> {
                Map<String, String> reporte = new HashMap<>(Map.of("tipo", "credenciales", "usuarios", filtro));
                app.guardarEnMemoria("filtro:reporte", reporte);
                app.ir("Reportes");
            }, i == 0));
        }
        botones.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, botones.getPreferredSize().height));
        poner(rep, botones);
        app.agregar(fila(Tema.FONDO, det, rep));

        JPanel t = tarjeta("Cuentas");
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Cuenta u : app.getCuentas().getUsuarios()) {
            String visible = Autenticacion.contrasenaVisible(u);
            filas.add(VistaAlumno.fila("usuario", u.usuario(), "nombre", u.nombre(), "rol", u.rol(), "grupo", grupoDe(u),
                    "contrasena", visible == null ? "Personalizada" : visible, "estado", visible == null ? "Activa" : "Contraseña inicial"));
        }
        Tabla tabla = new Tabla(new String[][]{{"usuario", "Usuario"}, {"nombre", "Nombre"}, {"rol", "Rol"}, {"grupo", "Grupo"},
                {"contrasena", "Contraseña"}, {"estado", "Estado"}}, 12, List.of());
        tabla.proporciones(List.of(1, 3, 2, 1, 2, 2));
        Runnable aplicar = () -> {
            String q = filtros.get("texto").strip().toLowerCase();
            tabla.cargar(filas.stream().filter(x -> ("Todos".equals(filtros.get("rol")) || filtros.get("rol").equals(x.get("rol")))
                    && ("Todos".equals(filtros.get("grupo")) || filtros.get("grupo").equals(x.get("grupo")))
                    && (q.isEmpty() || ((String) x.get("usuario")).toLowerCase().contains(q) || ((String) x.get("nombre")).toLowerCase().contains(q)))
                    .toList());
        };
        List<String> grupos = new ArrayList<>(List.of("Todos"));
        grupos.addAll(g.grupos());
        poner(t, linea(null, texto(filtros, "texto", "Buscar", 20, Tema.BLANCO, aplicar).panel(),
                opciones(filtros, "rol", "Rol", List.of("Todos", "Administrador", "Docente", "Alumno"), Tema.BLANCO, aplicar).panel(),
                opciones(filtros, "grupo", "Grupo", grupos, Tema.BLANCO, aplicar).panel()), 10);
        tabla.alSeleccionar(x -> {
            filtros.put("sel", (String) x.get("usuario"));
            javax.swing.SwingUtilities.invokeLater(app::refrescar);
        });
        aplicar.run();
        poner(t, tabla);
        app.agregar(t);

        JPanel r = tarjeta("Respaldo de datos", app.isPersistente() ? "Todo se guarda automáticamente en datos/base.json después de cada cambio."
                : "Modo demostración (--entrada): los cambios no se guardan.");
        Runnable respaldo = () -> {
            Path ruta = elegirArchivo("Exportar respaldo", "respaldo_syllabus.json");
            if (ruta != null) {
                try {
                    new Persistencia(ruta, ruta, ruta).guardar(g, app.getCuentas(), app.getInstitucion());
                    app.registrarActividad("respaldo_exportado · " + ruta.getFileName());
                    app.informar("Respaldo exportado", ruta.toString());
                } catch (IOException e) {
                    app.avisar("No se pudo exportar", e.getMessage(), true);
                }
            }
        };
        Runnable reiniciar = () -> {
            if (app.confirmar("Restablecer datos de ejemplo", "Se borrarán todos los cambios y se volverá a los datos de ejemplo. ¿Continuar?")) {
                try {
                    app.restablecerEjemplo();
                    app.mostrarAcceso();
                } catch (IOException e) {
                    app.avisar("No se pudo restablecer", e.getMessage(), true);
                }
            }
        };
        poner(r, app.isPersistente() ? linea(null, boton("Exportar respaldo", respaldo, true), boton("Restablecer datos de ejemplo", reiniciar, false))
                : linea(null, boton("Exportar respaldo", respaldo, true)));
        app.agregar(r);
    }
}
