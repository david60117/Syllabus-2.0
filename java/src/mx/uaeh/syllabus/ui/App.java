package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import mx.uaeh.syllabus.archivos.Archivos;
import mx.uaeh.syllabus.archivos.Persistencia;
import mx.uaeh.syllabus.documentos.Documentos;
import mx.uaeh.syllabus.logica.Autenticacion;
import mx.uaeh.syllabus.logica.Concurrencia;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.modelo.BusEventos;
import mx.uaeh.syllabus.modelo.Cuenta;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.ErrorValidacion;
import mx.uaeh.syllabus.modelo.Evento;
import mx.uaeh.syllabus.modelo.RegistroError;
import mx.uaeh.syllabus.modelo.ResumenCurso;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/**
 * Ventana principal. Guarda el estado de la sesión (cuenta, rol, vista, formularios y filtros), arma el marco
 * (cabecera, menú lateral y contenido) y delega cada pantalla a una subclase de {@link Vista}. Se suscribe al bus
 * de eventos del gestor: después de cada cambio guarda en datos/base.json y refresca la vista.
 */
public class App extends JFrame {
    /** Menú de cada perfil: ícono de Lucide y sección (como en la versión de Python). */
    public static final Map<String, String[][]> MENUS = new LinkedHashMap<>();

    static {
        MENUS.put("Administrador", new String[][]{{"inicio", "Inicio"}, {"estudiantes", "Estudiantes"}, {"grupos", "Grupos y materias"},
                {"docentes", "Docentes"}, {"inscripciones", "Inscripciones"}, {"consultas", "Consultas"}, {"reportes", "Reportes"},
                {"usuarios", "Usuarios"}, {"ajustes", "Personalización"}});
        MENUS.put("Docente", new String[][]{{"inicio", "Inicio"}, {"cursos", "Mis cursos"}, {"plan", "Plan de evaluación"},
                {"captura", "Captura"}, {"consultas", "Consultas"}, {"reportes", "Reportes"}, {"ajustes", "Personalización"}});
        MENUS.put("Alumno", new String[][]{{"inicio", "Inicio"}, {"calificaciones", "Mis calificaciones"}, {"kardex", "Kárdex"},
                {"ajustes", "Personalización"}});
    }

    /** Ícono de cada sección (también lo usan las tarjetas de acceso del inicio). */
    public static String iconoDe(String vista) {
        for (String[][] menu : MENUS.values()) {
            for (String[] item : menu) {
                if (item[1].equals(vista)) {
                    return item[0];
                }
            }
        }
        return "cuenta";
    }

    private GestorEscolar gestor;
    private final Persistencia persistencia;
    private Autenticacion cuentas;
    private Map<String, Object> institucion = new LinkedHashMap<>();
    private Cuenta cuenta;
    private String rol;
    private String usuario;
    private String vista = "Inicio";
    private final Map<String, Boolean> opciones = new HashMap<>(Map.of("colorear", true, "avisos", true, "recordar", true));
    private Map<String, Object> memoria = new HashMap<>();
    private final List<String> actividad = new ArrayList<>();
    private boolean cargando;
    private boolean refrescoPendiente;
    private final Timer guardado;
    private JPanel contenido;
    private JScrollPane desplazamiento;
    /** Menú del avatar; accesible para pruebas. */
    JPopupMenu menuAvatar;

    /**
     * Sin entrada se trabaja sobre datos/base.json y cada cambio se guarda solo; con entrada se abre ese archivo
     * como demostración, sin guardar.
     */
    public App(String entrada) {
        super("Syllabus 2.0 · Sistema de Gestión Académica (Java)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1360, 860);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Tema.FONDO);
        guardado = new Timer(250, e -> guardarAhora());
        guardado.setRepeats(false);
        persistencia = entrada == null ? Persistencia.predeterminada() : null;
        nuevoGestor();
        cargando = true;
        try {
            if (persistencia != null) {
                Persistencia.Estado estado = persistencia.abrir(gestor);
                cuentas = estado.cuentas();
                institucion = new LinkedHashMap<>(estado.institucion());
            } else {
                gestor.cargar(Archivos.cargarJson(Path.of(entrada)));
                cuentas = Autenticacion.predeterminada();
                cuentas.sincronizar(gestor);
            }
        } catch (ErrorValidacion e) {
            JOptionPane.showMessageDialog(null, Componentes.legible(e.getMessage()), "No se pudieron abrir los datos",
                    JOptionPane.ERROR_MESSAGE);
            cuentas = Autenticacion.predeterminada();
        }
        cargando = false;
        if (persistencia != null && !Files.exists(persistencia.getBase())) {
            guardarAhora();
        }
        mostrarAcceso();
    }

    public static void iniciar(String entrada) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); // listas y barras nativas
            } catch (Exception ignorado) {
                // se usa el aspecto predeterminado
            }
            atajosDeTeclado();
            new App(entrada).setVisible(true);
        });
    }

    /** Cmd+C/V/X/A en macOS (Ctrl en Windows y Linux) también en los campos de texto. */
    private static void atajosDeTeclado() {
        int tecla = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        Object[][] atajos = {{java.awt.event.KeyEvent.VK_C, javax.swing.text.DefaultEditorKit.copyAction},
                {java.awt.event.KeyEvent.VK_V, javax.swing.text.DefaultEditorKit.pasteAction},
                {java.awt.event.KeyEvent.VK_X, javax.swing.text.DefaultEditorKit.cutAction},
                {java.awt.event.KeyEvent.VK_A, javax.swing.text.DefaultEditorKit.selectAllAction}};
        for (String mapa : new String[]{"TextField.focusInputMap", "PasswordField.focusInputMap", "TextArea.focusInputMap"}) {
            if (UIManager.get(mapa) instanceof javax.swing.InputMap im) {
                for (Object[] a : atajos) {
                    im.put(javax.swing.KeyStroke.getKeyStroke((Integer) a[0], tecla), a[1]);
                }
            }
        }
    }

    // ---------------------------------------------------------------- datos y eventos
    private void nuevoGestor() {
        BusEventos bus = new BusEventos();
        bus.suscribirTodos(this::alEvento);
        gestor = new GestorEscolar(bus);
    }

    /** Sincroniza las cuentas (alta automática de alumnos y docentes) y guarda en datos/base.json. */
    public void guardarAhora() {
        guardado.stop();
        if (cuentas.sincronizar(gestor) > 0) {
            registrarActividad("cuentas_creadas · usuario y contraseña inicial = número de cuenta o ID");
        }
        if (persistencia != null) {
            try {
                persistencia.guardar(gestor, cuentas, institucion);
            } catch (IOException e) {
                avisar("No se pudo guardar", e.getMessage(), true);
            }
        }
    }

    /** Carga masiva: agrega los registros válidos de un JSON y devuelve los rechazados. */
    public List<RegistroError> importarArchivo(Path ruta) {
        Map<String, Object> datos = Archivos.cargarJson(ruta);
        int antes = gestor.getErrores().size();
        cargando = true;
        try {
            gestor.cargar(datos);
        } finally {
            cargando = false;
        }
        guardarAhora();
        registrarActividad("carga_masiva · " + ruta.getFileName() + " · " + (gestor.getErrores().size() - antes) + " rechazados");
        refrescar();
        return new ArrayList<>(gestor.getErrores().subList(antes, gestor.getErrores().size()));
    }

    /** Borra datos/base.json y vuelve a los datos de ejemplo. */
    public void restablecerEjemplo() throws IOException {
        Files.deleteIfExists(persistencia.getBase());
        nuevoGestor();
        cargando = true;
        Persistencia.Estado estado = persistencia.abrir(gestor);
        cargando = false;
        cuentas = estado.cuentas();
        institucion = new LinkedHashMap<>(estado.institucion());
        guardarAhora();
    }

    public boolean isPersistente() {
        return persistencia != null;
    }

    public Persistencia getPersistencia() {
        return persistencia;
    }

    private void alEvento(Evento e) {
        if (cargando) {
            return;
        }
        registrarActividad(e.hora() + "  " + e.nombre() + " · " + e.dato("detalle"));
        if (e.nombre().equals("error_validacion") && opcion("avisos")) {
            avisar(e.dato("codigo"), e.dato("detalle"), true);
        }
        if (!Persistencia.NO_GUARDAN.contains(e.nombre())) {
            guardado.restart();
        }
        // varios eventos seguidos (una operación por lote) producen un solo refresco
        if (!refrescoPendiente) {
            refrescoPendiente = true;
            SwingUtilities.invokeLater(() -> {
                refrescoPendiente = false;
                refrescar();
            });
        }
    }

    public void registrarActividad(String texto) {
        if (!texto.matches("^\\d{2}:\\d{2}:\\d{2}.*")) {
            texto = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + texto;
        }
        actividad.add(0, texto);
        while (actividad.size() > 30) {
            actividad.remove(actividad.size() - 1);
        }
    }

    /** Ejecuta una operación del gestor; si falla, el error llega como evento (aviso en pantalla). */
    public boolean hacer(Runnable accion) {
        try {
            accion.run();
            return true;
        } catch (ErrorValidacion e) {
            gestor.registrarError("interfaz", Map.of(), e);
            return false;
        }
    }

    /** Como {@link #hacer(Runnable)}, pero devuelve el resultado (null si falló). */
    public <T> T obtener(Supplier<T> accion) {
        try {
            return accion.get();
        } catch (ErrorValidacion e) {
            gestor.registrarError("interfaz", Map.of(), e);
            return null;
        }
    }

    public void avisar(String titulo, String texto, boolean error) {
        if (!isVisible()) {
            return;
        }
        JOptionPane.showMessageDialog(this, Componentes.legible(texto), Componentes.legible(titulo),
                error ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    public void informar(String titulo, String texto) {
        avisar(titulo, texto, false);
    }

    public boolean confirmar(String titulo, String texto) {
        return JOptionPane.showConfirmDialog(this, texto, titulo, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    // ---------------------------------------------------------------- alcance por rol
    public List<Curso> cursosVisibles() {
        if ("Docente".equals(rol)) {
            return gestor.cursosDeDocente(usuario);
        }
        if ("Alumno".equals(rol)) {
            return gestor.cursosDe(usuario);
        }
        List<Curso> cursos = new ArrayList<>(gestor.getCursos().values());
        cursos.sort(Comparator.comparing(Curso::grupo).thenComparing(Curso::asignatura));
        return cursos;
    }

    /** Resúmenes de los alumnos que puede ver el perfil (el docente, solo con sus materias). */
    public List<ResumenEstudiante> resumenesVisibles() {
        List<ResumenEstudiante> resumenes = Concurrencia.calcularResumenes(gestor, "secuencial");
        if ("Administrador".equals(rol)) {
            return resumenes;
        }
        List<String> ids = cursosVisibles().stream().map(Curso::id).toList();
        List<ResumenEstudiante> filtrados = new ArrayList<>();
        for (ResumenEstudiante r : resumenes) {
            List<ResumenCurso> propios = r.cursos().stream().filter(c -> ids.contains(c.cursoId())).toList();
            if (!propios.isEmpty()) {
                filtrados.add(r.conCursos(propios));
            }
        }
        return filtrados;
    }

    public String nombreUsuario() {
        if ("Docente".equals(rol) && gestor.getDocentes().containsKey(usuario)) {
            return gestor.getDocentes().get(usuario).nombre();
        }
        if ("Alumno".equals(rol) && gestor.getEstudiantes().containsKey(usuario)) {
            return gestor.getEstudiantes().get(usuario).nombreCompleto();
        }
        return cuenta == null ? "" : cuenta.nombre();
    }

    /** Fábrica de reportes con el encabezado de la institución y quien los emite. */
    public Documentos documentos() {
        return new Documentos(gestor, institucion, cuenta == null ? "" : cuenta.usuario());
    }

    // ---------------------------------------------------------------- pantallas
    public void mostrarAcceso() {
        rol = null;
        usuario = null;
        cuenta = null;
        contenido = null;
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(new VistaAcceso(this).construir());
        revalidate();
        repaint();
    }

    public void entrar(Cuenta nueva) {
        cuenta = nueva;
        rol = nueva.rol();
        usuario = nueva.referencia();
        memoria = new HashMap<>();
        vista = nueva.debeCambiar() ? "Mi cuenta" : "Inicio";
        construirMarco();
    }

    public void ir(String nuevaVista) {
        // con la contraseña inicial solo se puede usar "Mi cuenta"
        vista = cuenta != null && cuenta.debeCambiar() ? "Mi cuenta" : nuevaVista;
        memoria.remove("kardex_de");
        if (!opcion("recordar")) {
            memoria.keySet().removeIf(k -> k.startsWith("filtro:"));
        }
        construirMarco();
    }

    /** Destino "Vista:pestaña" (p. ej. "Consultas:1" abre Promedios y riesgo). */
    public void irA(String destino) {
        String[] partes = destino.split(":", 2);
        if (partes.length == 2) {
            memoria.put("pestana:" + partes[0], Integer.parseInt(partes[1]));
        }
        ir(partes[0]);
    }

    public void construirMarco() {
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(cabecera(), BorderLayout.NORTH);
        getContentPane().add(lateral(), BorderLayout.WEST);
        contenido = Componentes.vertical(Tema.FONDO);
        contenido.setBorder(BorderFactory.createEmptyBorder(22, 30, 30, 30));
        JPanel envoltura = new Lienzo();
        envoltura.add(contenido, BorderLayout.NORTH);
        desplazamiento = new JScrollPane(envoltura, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        desplazamiento.setBorder(null);
        desplazamiento.getViewport().setBackground(Tema.FONDO);
        desplazamiento.getVerticalScrollBar().setUnitIncrement(18);
        getContentPane().add(desplazamiento, BorderLayout.CENTER);
        mostrarVista();
        revalidate();
        repaint();
    }

    private JComponent cabecera() {
        JPanel cab = new JPanel(new BorderLayout());
        cab.setBackground(Tema.BLANCO);
        cab.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDE),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)));
        JPanel izq = Componentes.horizontal(Tema.BLANCO);
        izq.add(Componentes.numero("S"));
        JPanel titulos = Componentes.vertical(Tema.BLANCO);
        titulos.add(Componentes.etiqueta("Syllabus 2.0", 20, true, null));
        titulos.add(Componentes.etiqueta("Gestión académica", 12, false, Tema.SUAVE));
        izq.add(titulos);
        cab.add(izq, BorderLayout.WEST);
        JPanel der = Componentes.horizontal(Tema.BLANCO);
        String nombre = nombreUsuario();
        Redondo pastilla = new Redondo(18, Tema.acentoSuave(), null, 1f, new BorderLayout());
        pastilla.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        pastilla.add(Componentes.etiqueta(rol, 12, true, Tema.acento()));
        der.add(pastilla);
        Redondo avatar = new Redondo(16, Tema.acentoSuave(), null, 1f, new java.awt.GridBagLayout());
        avatar.setPreferredSize(new Dimension(32, 32));
        avatar.setToolTipText(nombre.isEmpty() ? rol : nombre);
        String inicial = (cuenta == null || cuenta.nombre() == null || cuenta.nombre().isEmpty() ? rol : cuenta.nombre()).substring(0, 1);
        avatar.add(Componentes.etiqueta(inicial.toUpperCase(), 13, true, Tema.acento()));
        // el avatar abre el menú emergente del sistema: Mi cuenta y Cerrar sesión
        JPopupMenu menu = new JPopupMenu();
        JMenuItem quien = new JMenuItem(nombre.isEmpty() ? rol : nombre);
        quien.setEnabled(false);
        menu.add(quien);
        menu.addSeparator();
        JMenuItem miCuenta = new JMenuItem("Mi cuenta y contraseña");
        miCuenta.addActionListener(e -> ir("Mi cuenta"));
        menu.add(miCuenta);
        JMenuItem salir = new JMenuItem("Cerrar sesión");
        salir.addActionListener(e -> mostrarAcceso());
        menu.add(salir);
        avatar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        avatar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                menu.show(avatar, 0, avatar.getHeight() + 4);
            }
        });
        menuAvatar = menu;
        der.add(avatar);
        cab.add(der, BorderLayout.EAST);
        return cab;
    }

    /** Menú lateral de 240 px: ícono de 20 px en un área de 28 px, separación de 16 px y barra activa de 4 × 28. */
    private JComponent lateral() {
        JPanel lat = Componentes.vertical(Tema.BLANCO);
        lat.setPreferredSize(new Dimension(240, 10));
        lat.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Tema.BORDE),
                BorderFactory.createEmptyBorder(22, 10, 16, 10)));
        JLabel titulo = Componentes.etiqueta("MENÚ PRINCIPAL", 11, true, Tema.SUAVE);
        titulo.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 0));
        lat.add(titulo);
        for (String[] item : MENUS.get(rol)) {
            boolean activo = item[1].equals(vista);
            Color fondo = activo ? Tema.acentoSuave() : Tema.BLANCO;
            Redondo fila = new Redondo(14, fondo, null, 1f, new BorderLayout());
            fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            fila.setPreferredSize(new Dimension(200, 44));
            fila.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JLabel l = Componentes.etiqueta(item[1], 14, activo, activo ? Tema.acento() : Tema.TEXTO);
            l.setIcon(Iconos.icono(item[0], 20, 28, activo ? Tema.acento() : Tema.SUAVE));
            l.setIconTextGap(16);
            l.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 6));
            fila.add(l);
            if (activo) {
                JPanel barra = new Redondo(2, Tema.acento(), null, 1f, null);
                barra.setPreferredSize(new Dimension(4, 28));
                JPanel lado = new JPanel(new java.awt.GridBagLayout());
                lado.setOpaque(false);
                lado.add(barra);
                fila.add(lado, BorderLayout.EAST);
            }
            fila.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    ir(item[1]);
                }
            });
            lat.add(fila);
            lat.add(Box.createVerticalStrut(4));
        }
        lat.add(Box.createVerticalGlue());
        JPanel pie = Componentes.vertical(Tema.BLANCO);
        pie.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Tema.BORDE),
                BorderFactory.createEmptyBorder(10, 10, 0, 0)));
        pie.add(Componentes.etiqueta("Perfil de institución", 11, false, Tema.SUAVE));
        pie.add(Componentes.etiqueta("Syllabus 2.0", 11, true, Tema.TEXTO));
        pie.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        lat.add(pie);
        return lat;
    }

    public void encabezado(String ruta, String titulo, String subtitulo) {
        contenido.add(Componentes.etiqueta(ruta.toUpperCase(), 12, true, Tema.acento()));
        contenido.add(Box.createVerticalStrut(6));
        contenido.add(Componentes.etiqueta(titulo, 28, true, null));
        contenido.add(Box.createVerticalStrut(2));
        contenido.add(Componentes.parrafo(subtitulo, 14, Tema.SUAVE));
        contenido.add(Box.createVerticalStrut(18));
    }

    /** Agrega una pieza al contenido con separación inferior. */
    public void agregar(JComponent pieza) {
        pieza.setAlignmentX(LEFT_ALIGNMENT);
        contenido.add(pieza);
        contenido.add(Box.createVerticalStrut(14));
    }

    public JPanel getContenido() {
        return contenido;
    }

    public void refrescar() {
        if (rol == null || contenido == null) {
            return;
        }
        int y = desplazamiento.getVerticalScrollBar().getValue();
        contenido.removeAll();
        mostrarVista();
        contenido.revalidate();
        contenido.repaint();
        SwingUtilities.invokeLater(() -> desplazamiento.getVerticalScrollBar().setValue(y));
    }

    private static final Map<String, Function<App, Vista>> VISTAS = new HashMap<>();

    static {
        for (String r : MENUS.keySet()) {
            VISTAS.put(r + "/Inicio", VistaInicio::new);
            VISTAS.put(r + "/Personalización", VistaPersonalizacion::new);
            VISTAS.put(r + "/Mi cuenta", VistaMiCuenta::new);
        }
        VISTAS.put("Administrador/Estudiantes", VistaEstudiantes::new);
        VISTAS.put("Administrador/Grupos y materias", VistaGruposMaterias::new);
        VISTAS.put("Administrador/Docentes", VistaDocentes::new);
        VISTAS.put("Administrador/Inscripciones", VistaInscripciones::new);
        VISTAS.put("Administrador/Usuarios", VistaUsuarios::new);
        VISTAS.put("Administrador/Consultas", VistaConsultas::new);
        VISTAS.put("Docente/Consultas", VistaConsultas::new);
        VISTAS.put("Administrador/Reportes", VistaReportes::new);
        VISTAS.put("Docente/Reportes", VistaReportes::new);
        VISTAS.put("Docente/Mis cursos", VistaMisCursos::new);
        VISTAS.put("Docente/Plan de evaluación", VistaPlan::new);
        VISTAS.put("Docente/Captura", VistaCaptura::new);
        VISTAS.put("Alumno/Mis calificaciones", VistaAlumno.Calificaciones::new);
        VISTAS.put("Alumno/Kárdex", VistaAlumno.Kardex::new);
    }

    private void mostrarVista() {
        Function<App, Vista> fabrica = VISTAS.get(rol + "/" + vista);
        if (fabrica != null) {
            fabrica.apply(this).dibujar();
        }
    }

    // ---------------------------------------------------------------- estado de la sesión que usan las vistas
    public GestorEscolar getGestor() {
        return gestor;
    }

    public Autenticacion getCuentas() {
        return cuentas;
    }

    public Map<String, Object> getInstitucion() {
        return institucion;
    }

    public Cuenta getCuenta() {
        return cuenta;
    }

    public String getRol() {
        return rol;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getVista() {
        return vista;
    }

    public List<String> getActividad() {
        return actividad;
    }

    public boolean opcion(String clave) {
        return opciones.get(clave);
    }

    public void setOpcion(String clave, boolean valor) {
        opciones.put(clave, valor);
        if (clave.equals("colorear")) {
            Tabla.colorear = valor;
        }
    }

    /** Estado de un formulario o filtro que sobrevive a los refrescos de la vista. */
    @SuppressWarnings("unchecked")
    public Map<String, String> estado(String clave, Map<String, String> valores) {
        return (Map<String, String>) memoria.computeIfAbsent(clave, k -> new HashMap<>(valores));
    }

    /** Cualquier otro dato de la sesión (borradores del plan, cambios del acta, materias abiertas...). */
    @SuppressWarnings("unchecked")
    public <T> T memoria(String clave, Supplier<T> crear) {
        return (T) memoria.computeIfAbsent(clave, k -> crear.get());
    }

    public Object memoria(String clave) {
        return memoria.get(clave);
    }

    public void guardarEnMemoria(String clave, Object valor) {
        if (valor == null) {
            memoria.remove(clave);
        } else {
            memoria.put(clave, valor);
        }
    }

    public Color acento() {
        return Tema.acento();
    }
}
