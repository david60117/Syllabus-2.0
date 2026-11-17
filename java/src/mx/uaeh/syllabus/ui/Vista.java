package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import mx.uaeh.syllabus.archivos.Archivos;
import mx.uaeh.syllabus.documentos.Documento;
import mx.uaeh.syllabus.documentos.Documentos;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Docente;
import mx.uaeh.syllabus.modelo.Evaluacion;
import mx.uaeh.syllabus.ui.Componentes.Campo;

/**
 * Pantalla de la aplicación. Cada módulo (Estudiantes, Captura, Reportes...) es una subclase que implementa
 * {@link #dibujar()}; esta clase reúne las piezas comunes del diseño (tarjetas, filas, campos ligados a un
 * formulario, pestañas, exportación de reportes).
 */
public abstract class Vista {
    protected final App app;
    protected final GestorEscolar g;

    protected Vista(App app) {
        this.app = app;
        this.g = app.getGestor();
    }

    /** Dibuja la pantalla en el contenido de la App (se vuelve a llamar en cada refresco). */
    public abstract void dibujar();

    // ---------------------------------------------------------------- texto y formato
    protected static String pct(double peso) {
        return mx.uaeh.syllabus.logica.Calculos.normalizado(peso * 100);
    }

    /** Número escrito por el usuario; si no es número se deja el texto para que el gestor lo rechace. */
    protected static Object num(String texto) {
        try {
            return Double.parseDouble(texto.strip());
        } catch (NumberFormatException e) {
            return texto;
        }
    }

    /** Porcentaje escrito (30) → peso decimal (0.3); el gestor valida el resto. */
    protected static Object peso(String texto) {
        Object x = num(texto);
        return x instanceof Double d ? Math.round(d / 100 * 1e6) / 1e6 : x;
    }

    protected static String docenteTxt(Docente d) {
        return d == null ? "Sin asignar" : d.id() + " · " + d.nombre();
    }

    protected static String primero(String opcion) {
        return opcion == null ? "" : opcion.split(" · ")[0];
    }

    /** Cuántos de los 3 parciales tienen evaluaciones que suman 100 %. */
    protected int planCompleto(String cursoId) {
        return (int) g.parcialesDe(cursoId).stream().filter(p -> {
            List<Evaluacion> es = g.evaluacionesDe(p.id());
            return !es.isEmpty() && Math.abs(es.stream().mapToDouble(Evaluacion::peso).sum() - 1) < 1e-9;
        }).count();
    }

    /** {capturadas, total} de un curso. */
    protected int[] avanceCaptura(String cursoId) {
        List<String> alumnos = g.alumnosDeCurso(cursoId).stream().map(e -> e.numCuenta()).toList();
        List<String> evals = g.parcialesDe(cursoId).stream().flatMap(p -> g.evaluacionesDe(p.id()).stream()).map(Evaluacion::id).toList();
        int hechas = 0;
        for (String n : alumnos) {
            for (String e : evals) {
                if (g.calificacion(n, e) != null) {
                    hechas++;
                }
            }
        }
        return new int[]{hechas, alumnos.size() * evals.size()};
    }

    protected String rutaDe(String modulo) {
        return ("Docente".equals(app.getRol()) ? "Docente" : "Inicio") + "  /  " + modulo;
    }

    // ---------------------------------------------------------------- piezas
    protected static JLabel etiqueta(String texto, int tam, boolean negrita, Color color) {
        return Componentes.etiqueta(texto, tam, negrita, color);
    }

    protected static JLabel parrafo(String texto, int tam, Color color) {
        return Componentes.parrafo(texto, tam, color);
    }

    protected static JPanel tarjeta(String titulo, String subtitulo) {
        return Componentes.tarjeta(titulo, subtitulo);
    }

    protected static JPanel tarjeta(String titulo) {
        return Componentes.tarjeta(titulo, null);
    }

    /** Agrega una pieza dentro de un panel vertical con separación inferior. */
    protected static <T extends JComponent> T poner(JPanel padre, T pieza, int separacion) {
        pieza.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        padre.add(pieza);
        if (separacion > 0) {
            padre.add(Box.createVerticalStrut(separacion));
        }
        return pieza;
    }

    protected static <T extends JComponent> T poner(JPanel padre, T pieza) {
        return poner(padre, pieza, 0);
    }

    /** Fila de columnas del mismo ancho (como el grid de Figma). */
    protected static JPanel fila(Color fondo, JComponent... piezas) {
        JPanel f = new JPanel(new GridLayout(1, piezas.length, 14, 0));
        f.setBackground(fondo);
        f.setOpaque(fondo != null);
        for (JComponent p : piezas) {
            f.add(p);
        }
        f.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return f;
    }

    /** Piezas en línea alineadas a la izquierda y abajo (campos y botones de una barra). */
    protected static JPanel linea(Color fondo, JComponent... piezas) {
        JPanel f = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }

            @Override
            public java.awt.Component add(java.awt.Component p) {
                JPanel envoltura = new JPanel(new BorderLayout());
                envoltura.setOpaque(false);
                envoltura.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
                envoltura.add(p, BorderLayout.SOUTH);
                return super.add(envoltura);
            }
        };
        f.setBackground(fondo);
        f.setOpaque(fondo != null);
        for (JComponent p : piezas) {
            f.add(p);
        }
        f.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return f;
    }

    protected static JComponent boton(String texto, Runnable accion, boolean primario) {
        return Componentes.boton(texto, accion, primario);
    }

    protected static void nota(JPanel padre, String texto) {
        padre.add(Box.createVerticalStrut(6));
        poner(padre, parrafo(texto, 12, Tema.SUAVE), 4);
    }

    protected void notaPie(String texto) {
        JPanel t = tarjeta(null);
        poner(t, parrafo(texto, 12, Tema.SUAVE));
        app.agregar(t);
    }

    /** Fila de botones del mismo ancho (el primero es el principal) al pie de la vista. */
    protected void botonesPie(String[][] botones) {
        JPanel f = new JPanel(new GridLayout(1, botones.length, 12, 0));
        f.setBackground(Tema.FONDO);
        for (int i = 0; i < botones.length; i++) {
            String destino = botones[i][1];
            Boton b = new Boton(botones[i][0], () -> app.irA(destino), i == 0) {
                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
                }
            };
            f.add(b);
        }
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
        app.agregar(f);
    }

    /** Número o texto corto sobre fondo suave (pasos numerados). */
    protected static JPanel insignia(String texto) {
        return Componentes.numero(texto);
    }

    /** Punto de color (pendientes y actividad). */
    protected static JComponent punto(Color color) {
        Redondo p = new Redondo(4, color, null, 1f, null);
        p.setPreferredSize(new Dimension(8, 8));
        JPanel lado = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
        lado.setOpaque(false);
        lado.add(p);
        return lado;
    }

    /** Texto con acción al hacer clic (enlaces "Ir a ... →"). */
    protected static JLabel enlace(String texto, Runnable accion) {
        JLabel l = etiqueta(texto, 12, true, Tema.acento());
        clicable(l, accion);
        return l;
    }

    protected static <T extends JComponent> T clicable(T c, Runnable accion) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                accion.run();
            }
        });
        return c;
    }

    /** Dato con título pequeño arriba y valor en negrita. */
    protected static JPanel dato(String titulo, String valor, Color color, Color fondo) {
        JPanel c = Componentes.vertical(fondo);
        c.setOpaque(fondo != null);
        c.add(etiqueta(titulo, 12, false, Tema.SUAVE));
        c.add(etiqueta(valor, 15, true, color));
        return c;
    }

    // ---------------------------------------------------------------- campos ligados a un formulario
    protected static Campo texto(Map<String, String> estado, String clave, String etiqueta, int columnas, Color fondo) {
        return texto(estado, clave, etiqueta, columnas, fondo, null);
    }

    /** Campo de texto ligado al formulario; despues se ejecuta tras guardar cada cambio (filtros en vivo). */
    protected static Campo texto(Map<String, String> estado, String clave, String etiqueta, int columnas, Color fondo, Runnable despues) {
        Campo c = Campo.texto(etiqueta, columnas, fondo);
        c.setValor(estado.getOrDefault(clave, ""));
        c.alCambiar(() -> {
            estado.put(clave, c.valor());
            if (despues != null) {
                despues.run();
            }
        });
        return c;
    }

    protected static Campo soloLectura(String etiqueta, String valor, int columnas, Color fondo) {
        Campo c = Campo.texto(etiqueta, columnas, fondo);
        c.setValor(valor);
        javax.swing.JTextField t = (javax.swing.JTextField) c.control();
        t.setEditable(false);
        t.setForeground(Tema.SUAVE);
        return c;
    }

    /** Lista de opciones; si el valor guardado no es una opción, se elige la primera. */
    protected Campo opciones(Map<String, String> estado, String clave, String etiqueta, List<String> opciones, Color fondo,
                             Runnable alCambiar) {
        if (!opciones.isEmpty() && !opciones.contains(estado.get(clave))) {
            estado.put(clave, opciones.get(0));
        }
        Campo c = Campo.opciones(etiqueta, opciones.isEmpty() ? List.of("—") : opciones, fondo);
        c.setValor(estado.getOrDefault(clave, ""));
        c.alCambiar(() -> {
            if (!Objects.equals(estado.get(clave), c.valor())) {
                estado.put(clave, c.valor());
                if (alCambiar != null) {
                    SwingUtilities.invokeLater(alCambiar);
                }
            }
        });
        return c;
    }

    /** Selector de materia del docente (o de todas para el administrador); devuelve la elegida. */
    protected Curso selectorCurso(JPanel barra, String clave, List<Curso> cursos) {
        List<String> ops = cursos.stream().map(c -> c.id() + " · " + c.asignatura() + " · " + c.grupo()).toList();
        Map<String, String> estado = app.estado(clave, Map.of("curso", ""));
        Campo c = opciones(estado, "curso", "Materia", ops, Tema.BLANCO, app::refrescar);
        ((javax.swing.JComboBox<?>) c.control()).setPrototypeDisplayValue(null);
        barra.add(c.panel());
        return g.getCursos().get(primero(estado.get("curso")));
    }

    // ---------------------------------------------------------------- pestañas
    /** Barra de pestañas como en Figma; devuelve el índice de la pestaña elegida. */
    protected int pestanas(String clave, String... titulos) {
        int activa = app.memoria("pestana:" + clave, () -> 0);
        if (activa >= titulos.length) {
            activa = 0;
        }
        Redondo barra = new Redondo(6, Tema.BLANCO, Tema.BORDE, 1f, new FlowLayout(FlowLayout.LEFT, 6, 0));
        barra.setBorder(BorderFactory.createEmptyBorder(5, 7, 0, 7));
        for (int i = 0; i < titulos.length; i++) {
            boolean elegida = i == activa;
            int n = i;
            JPanel col = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(java.awt.Graphics gr) {
                    if (!elegida) {
                        return;
                    }
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) gr.create();
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Tema.acentoSuave());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight() + 10, 10, 10);
                    g2.setColor(Tema.acento());
                    g2.fillRect(0, getHeight() - 3, getWidth(), 3);
                    g2.dispose();
                }
            };
            col.setOpaque(false);
            col.setBorder(BorderFactory.createEmptyBorder(9, 22, 12, 22));
            JLabel texto = etiqueta(titulos[i], 13, elegida, elegida ? Tema.acento() : Tema.SUAVE);
            texto.setHorizontalAlignment(JLabel.CENTER);
            col.add(texto);
            clicable(col, () -> {
                app.guardarEnMemoria("pestana:" + clave, n);
                app.refrescar();
            });
            barra.add(col);
        }
        barra.setMaximumSize(new Dimension(Integer.MAX_VALUE, barra.getPreferredSize().height));
        app.agregar(barra);
        return activa;
    }

    // ---------------------------------------------------------------- reportes
    protected Path elegirArchivo(String titulo, String nombre) {
        JFileChooser f = new JFileChooser();
        f.setDialogTitle(titulo);
        f.setSelectedFile(new java.io.File(nombre));
        return f.showSaveDialog(app) == JFileChooser.APPROVE_OPTION ? f.getSelectedFile().toPath() : null;
    }

    protected Path elegirCarpeta(String titulo) {
        JFileChooser f = new JFileChooser();
        f.setDialogTitle(titulo);
        f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        return f.showOpenDialog(app) == JFileChooser.APPROVE_OPTION ? f.getSelectedFile().toPath() : null;
    }

    protected Path elegirParaAbrir(String titulo) {
        JFileChooser f = new JFileChooser(new java.io.File("."));
        f.setDialogTitle(titulo);
        f.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON", "json"));
        return f.showOpenDialog(app) == JFileChooser.APPROVE_OPTION ? f.getSelectedFile().toPath() : null;
    }

    protected static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    protected void exportar(Documento doc, String formato) {
        Path ruta = elegirArchivo("Exportar " + doc.titulo(), Documentos.nombreArchivo(doc) + "." + formato);
        if (ruta == null) {
            return;
        }
        if (!ruta.getFileName().toString().toLowerCase().endsWith("." + formato)) {
            ruta = ruta.resolveSibling(ruta.getFileName() + "." + formato);
        }
        try {
            Archivos.exportarDocumento(doc, ruta, formato, hex(Tema.acento()));
        } catch (IOException e) {
            app.avisar("No se pudo exportar", e.getMessage(), true);
            return;
        }
        app.registrarActividad("reporte_exportado · " + ruta.getFileName());
        app.informar("Reporte exportado", doc.titulo() + "\n" + ruta);
    }

    /** Botones Exportar PDF · CSV · JSON (y extras) de un reporte. */
    protected JPanel botonesExportar(Documento doc, Color fondo, Object[]... extra) {
        JPanel f = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        f.setBackground(fondo);
        f.setOpaque(fondo != null);
        f.add(boton("Exportar PDF", () -> exportar(doc, "pdf"), true));
        f.add(boton("CSV", () -> exportar(doc, "csv"), false));
        f.add(boton("JSON", () -> exportar(doc, "json"), false));
        for (Object[] e : extra) {
            f.add(boton((String) e[0], (Runnable) e[1], false));
        }
        f.setBorder(BorderFactory.createEmptyBorder(0, -8, 0, 0));
        f.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
        return f;
    }

    /** Columnas anchas en la vista del reporte (proporción del ancho). */
    private static final Map<String, Integer> ANCHOS = Map.of("asignatura", 3, "alumno", 3, "docente", 2, "motivo", 3, "reprobadas", 3);

    /** El reporte como se ve impreso: encabezado institucional, tabla, resumen, firmas y nota. */
    protected JPanel vistaDocumento(Documento doc) {
        Documento.Encabezado enc = doc.encabezado();
        Redondo hoja = new Redondo(10, Tema.BLANCO, Tema.BORDE, 1f, null);
        hoja.setLayout(new javax.swing.BoxLayout(hoja, javax.swing.BoxLayout.Y_AXIS));
        Redondo cab = Redondo.vertical(10, Tema.acentoSuave(), null, 16, 24);
        JPanel arriba = new JPanel(new BorderLayout());
        arriba.setOpaque(false);
        JPanel izq = Componentes.vertical(null);
        izq.setOpaque(false);
        izq.add(etiqueta(enc.institucion(), 17, true, null));
        izq.add(etiqueta(enc.programa(), 12, false, Tema.SUAVE));
        arriba.add(izq, BorderLayout.WEST);
        JPanel der = Componentes.vertical(null);
        der.setOpaque(false);
        JLabel ciclo = etiqueta("CICLO ESCOLAR", 11, true, Tema.SUAVE);
        ciclo.setAlignmentX(JComponent.RIGHT_ALIGNMENT);
        JLabel valorCiclo = etiqueta(enc.ciclo(), 14, true, null);
        valorCiclo.setAlignmentX(JComponent.RIGHT_ALIGNMENT);
        der.add(ciclo);
        der.add(valorCiclo);
        arriba.add(der, BorderLayout.EAST);
        poner(cab, arriba, 12);
        poner(cab, etiqueta(enc.titulo().toUpperCase(), 19, true, Tema.acento()), 8);
        JPanel campos = new JPanel(new GridLayout(0, 3, 40, 6));
        campos.setOpaque(false);
        for (Documento.Dato d : enc.campos()) {
            campos.add(dato(d.etiqueta(), Componentes.fmt(d.valor()), null, null));
        }
        poner(cab, campos, 8);
        JLabel emision = etiqueta("Emitido el " + enc.emision() + (enc.emitidoPor().isEmpty() ? "" : " por " + enc.emitidoPor()),
                11, false, Tema.SUAVE);
        JPanel pieCab = new JPanel(new BorderLayout());
        pieCab.setOpaque(false);
        pieCab.add(emision, BorderLayout.EAST);
        poner(cab, pieCab);
        cab.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        hoja.add(cab);
        JPanel cuerpo = Componentes.vertical(Tema.BLANCO);
        cuerpo.setOpaque(false);
        cuerpo.setBorder(BorderFactory.createEmptyBorder(16, 24, 18, 24));
        String[][] columnas = doc.columnas().stream().map(c -> new String[]{c.clave(), c.titulo()}).toArray(String[][]::new);
        Tabla tabla = new Tabla(columnas, Math.max(3, Math.min(14, doc.filas().size())), List.of());
        tabla.proporciones(doc.columnas().stream().map(c -> ANCHOS.getOrDefault(c.clave(), 1)).toList());
        tabla.cargar(doc.filas());
        poner(cuerpo, tabla, 14);
        JPanel resumen = new JPanel(new GridLayout(1, Math.max(1, doc.resumen().size()), 8, 0));
        resumen.setOpaque(false);
        for (Documento.Dato d : doc.resumen()) {
            Redondo caja = Redondo.vertical(8, Tema.CAMPO, Tema.BORDE, 8, 12);
            caja.add(etiqueta(d.etiqueta(), 11, false, Tema.SUAVE));
            caja.add(etiqueta(Componentes.fmt(d.valor()), 15, true, Tema.acento()));
            resumen.add(caja);
        }
        poner(cuerpo, resumen);
        if (!doc.firmas().isEmpty()) {
            JPanel firmas = new JPanel(new GridLayout(1, doc.firmas().size(), 60, 0));
            firmas.setOpaque(false);
            firmas.setBorder(BorderFactory.createEmptyBorder(40, 30, 0, 30));
            for (String f : doc.firmas()) {
                JPanel celda = new JPanel(new BorderLayout(0, 4));
                celda.setOpaque(false);
                celda.add(Componentes.separadorColor(Tema.TEXTO), BorderLayout.NORTH);
                JLabel l = etiqueta(f, 12, false, Tema.SUAVE);
                l.setHorizontalAlignment(JLabel.CENTER);
                celda.add(l);
                firmas.add(celda);
            }
            poner(cuerpo, firmas);
        }
        if (!doc.nota().isEmpty()) {
            cuerpo.add(Box.createVerticalStrut(14));
            poner(cuerpo, parrafo(doc.nota(), 11, Tema.SUAVE));
        }
        cuerpo.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        hoja.add(cuerpo);
        hoja.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return hoja;
    }
}
