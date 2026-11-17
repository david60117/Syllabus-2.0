package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import mx.uaeh.syllabus.logica.Calculos;

/** Tabla con encabezados ordenables (⌄ ascendente, ^ descendente, - inactiva) y filas coloreadas por estado. */
public class Tabla extends JPanel {
    public static final List<String> ORDENABLES = List.of("num_cuenta", "apellidos", "nombre", "promedio");
    public static final List<String> COLUMNAS_ESTADO = List.of("estatus", "riesgo", "estado", "plan");
    private static final Color VERDE = new Color(0x247457);
    private static final Color ROJO = new Color(0xa32e34);
    private static final Color AMBAR = new Color(0xa26b34);
    private static final Color GRIS_ICONO = new Color(0xcdc3be);
    /** Opción "Colorear estados" de Personalización. */
    public static boolean colorear = true;

    /** Color del texto de estado: verde, rojo o ámbar, igual en las tres versiones. */
    static Color tono(Object valor) {
        String v = String.valueOf(valor).toUpperCase();
        if (v.startsWith("PENDIENTE")) {
            return AMBAR;
        }
        return switch (v) {
            case "APROBADO", "SIN_RIESGO", "SIN RIESGO", "GUARDADA", "CAPTURADA", "INSCRITO", "ASIGNADO", "ACTIVA", "COMPLETO" -> VERDE;
            case "REPROBADO", "ALTO", "RECHAZADA", "CON ERRORES" -> ROJO;
            case "MEDIO", "EN CURSO", "CONTRASEÑA INICIAL", "INCOMPLETO" -> AMBAR;
            default -> null;
        };
    }

    private final String[][] columnas;
    private final List<String> ordenables;
    private final JTable tabla;
    private final DefaultTableModel modelo;
    private List<Map<String, Object>> filas = new ArrayList<>();
    private List<Map<String, Object>> visibles = new ArrayList<>();
    private String orden = "apellidos";
    private boolean desc;

    public Tabla(String[][] columnas, int alto, List<String> ordenables) {
        super(new BorderLayout());
        this.columnas = columnas;
        this.ordenables = ordenables;
        setBackground(Tema.BLANCO);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        modelo = new DefaultTableModel(0, columnas.length) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        tabla.setRowHeight(34);
        tabla.setIntercellSpacing(new Dimension(0, 0));
        tabla.setShowHorizontalLines(false); // las filas se distinguen por el color alterno, sin líneas
        tabla.setFont(Tema.fuente(13, false));
        tabla.setShowGrid(false);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setBackground(Tema.BLANCO);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int c = tabla.columnAtPoint(e.getPoint());
                if (c >= 0 && esOrdenable(columnas[c][0])) {
                    desc = columnas[c][0].equals(orden) ? !desc : false;
                    orden = columnas[c][0];
                    cargar(filas);
                }
            }
        });
        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foco, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, false, r, c);
                l.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                l.setFont(Tema.fuente(13, false));
                l.setText(Componentes.legible(v));
                l.setBackground(sel ? Tema.acentoSuave() : r % 2 == 1 ? new Color(0xfafbfd) : Tema.BLANCO);
                Color estado = colorear && COLUMNAS_ESTADO.contains(columnas[c][0]) ? tono(v) : null;
                l.setForeground(sel ? Tema.acento() : estado != null ? estado : Tema.TEXTO);
                return l;
            }
        });
        tabla.getTableHeader().setDefaultRenderer((t, v, sel, foco, r, c) -> {
            String clave = columnas[c][0];
            boolean ordenable = esOrdenable(clave);
            boolean activa = ordenable && clave.equals(orden);
            JLabel l = new JLabel(columnas[c][1]);
            l.setFont(Tema.fuente(12, true));
            l.setForeground(activa ? Tema.TEXTO : Tema.SUAVE);
            l.setOpaque(true);
            l.setBackground(Tema.BLANCO);
            l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDE),
                    BorderFactory.createEmptyBorder(0, 12, 0, 12)));
            l.setPreferredSize(new Dimension(10, 36));
            if (ordenable) {
                l.setIcon(new IconoOrden(activa, desc));
                l.setHorizontalTextPosition(JLabel.LEFT);
                l.setIconTextGap(8);
            }
            return l;
        });
        java.awt.FontMetrics fm = tabla.getFontMetrics(Tema.fuente(12, true));
        for (int i = 0; i < columnas.length; i++) {
            int minimo = fm.stringWidth(columnas[i][1]) + 24 + (ordenables.contains(columnas[i][0]) ? 20 : 0);
            tabla.getColumnModel().getColumn(i).setMinWidth(Math.max(COLUMNAS_ESTADO.contains(columnas[i][0]) ? 110 : 60, minimo));
            tabla.getColumnModel().getColumn(i).setPreferredWidth(Math.max(90, minimo + 30));
        }
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(new Redondo.Borde(4, Tema.BORDE, new java.awt.Insets(1, 1, 1, 1)));
        scroll.getViewport().setBackground(Tema.BLANCO);
        scroll.setPreferredSize(new Dimension(900, 40 + alto * 34));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40 + alto * 34));
        add(scroll);
        encabezados();
    }

    public Tabla(String[][] columnas, int alto) {
        this(columnas, alto, ORDENABLES);
    }

    private boolean esOrdenable(String clave) {
        return ordenables.contains(clave) && (filas.isEmpty() || filas.get(0).containsKey("num_cuenta"));
    }

    private void encabezados() {
        tabla.getTableHeader().repaint();
    }

    public String ordenActual() {
        return orden;
    }

    public boolean descendente() {
        return desc;
    }

    /** Anchos relativos de las columnas (1 = normal, 3 = triple). */
    public Tabla proporciones(List<Integer> pesos) {
        for (int i = 0; i < pesos.size() && i < columnas.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(90 * pesos.get(i));
        }
        return this;
    }

    public Tabla ordenarPor(String columna, boolean descendente) {
        orden = columna;
        desc = descendente;
        cargar(filas);
        return this;
    }

    public Tabla alSeleccionar(Consumer<Map<String, Object>> accion) {
        tabla.getSelectionModel().addListSelectionListener(e -> {
            int r = tabla.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0 && r < visibles.size()) {
                accion.accept(visibles.get(r));
            }
        });
        return this;
    }

    public Tabla cargar(List<Map<String, Object>> nuevas) {
        filas = new ArrayList<>(nuevas);
        boolean conCuenta = !ordenables.isEmpty() && !filas.isEmpty() && filas.get(0).containsKey("num_cuenta") && filas.get(0).containsKey(orden);
        visibles = conCuenta ? Calculos.ordenar(filas, orden, desc) : filas;
        modelo.setRowCount(0);
        if (visibles.isEmpty()) {
            Object[] vacia = new Object[columnas.length];
            vacia[0] = "Sin resultados";
            modelo.addRow(vacia);
        }
        for (Map<String, Object> f : visibles) {
            Object[] fila = new Object[columnas.length];
            for (int i = 0; i < columnas.length; i++) {
                fila[i] = Componentes.fmt(f.get(columnas[i][0]));
            }
            modelo.addRow(fila);
        }
        encabezados();
        return this;
    }

    /** Ícono de orden: dos chevrones simétricos; el de la dirección activa va en el color del tema. */
    private static final class IconoOrden implements javax.swing.Icon {
        private final boolean activa;
        private final boolean desc;

        IconoOrden(boolean activa, boolean desc) {
            this.activa = activa;
            this.desc = desc;
        }

        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.setColor(activa && !desc ? Tema.acento() : GRIS_ICONO);
            g2.draw(new java.awt.geom.Path2D.Float() {{ moveTo(x + 2, y + 5.5); lineTo(x + 5, y + 2.5); lineTo(x + 8, y + 5.5); }});
            g2.setColor(activa && desc ? Tema.acento() : GRIS_ICONO);
            g2.draw(new java.awt.geom.Path2D.Float() {{ moveTo(x + 2, y + 8.5); lineTo(x + 5, y + 11.5); lineTo(x + 8, y + 8.5); }});
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}
