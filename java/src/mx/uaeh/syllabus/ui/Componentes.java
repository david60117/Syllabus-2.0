package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Piezas de diseño reutilizables (tarjetas, botones, campos, mosaicos). */
public final class Componentes {
    private Componentes() {
    }

    public static String fmt(Object x) {
        if (x == null) {
            return "—";
        }
        if (x instanceof BigDecimal d) {
            return d.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        }
        if (x instanceof Double d) {
            return String.format("%.2f", d);
        }
        return String.valueOf(x);
    }

    /** Texto sin guiones bajos para mostrar: "SIN_RIESGO" → "Sin riesgo", "datos_cargados" → "Datos cargados". */
    public static String legible(Object texto) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b[A-Za-z]+(?:_[A-Za-z0-9]+)+\\b").matcher(String.valueOf(texto == null ? "" : texto));
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String s = m.group().replace('_', ' ').toLowerCase();
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(Character.toUpperCase(s.charAt(0)) + s.substring(1)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static <T extends JComponent> T izquierda(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    public static JLabel etiqueta(String texto, int tam, boolean negrita, Color color) {
        JLabel l = new JLabel(texto);
        l.setFont(Tema.fuente(tam, negrita));
        l.setForeground(color == null ? Tema.TEXTO : color);
        return izquierda(l);
    }

    public static JLabel etiqueta(String texto, int tam) {
        return etiqueta(texto, tam, false, null);
    }

    /** Texto que se ajusta al ancho disponible. */
    /** Párrafo con ancho fijo en píxeles (se parte en varias líneas). */
    public static JLabel parrafo(String texto, int tam, Color color, int ancho) {
        return etiqueta("<html><div style='width:" + ancho + "px'>" + texto.replace("<", "&lt;") + "</div></html>", tam, false, color);
    }

    public static JLabel parrafo(String texto, int tam, Color color) {
        return etiqueta("<html>" + texto.replace("<", "&lt;") + "</html>", tam, false, color);
    }

    public static JButton boton(String texto, Runnable accion, boolean primario) {
        return new Boton(texto, accion, primario);
    }

    public static JPanel vertical(Color fondo) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(fondo);
        return izquierda(p);
    }

    public static JPanel horizontal(Color fondo) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setBackground(fondo);
        return izquierda(p);
    }

    /** Tarjeta blanca con borde, título y subtítulo opcionales. */
    public static JPanel tarjeta(String titulo, String subtitulo) {
        JPanel t = Redondo.vertical(10, Tema.BLANCO, Tema.BORDE, 16, 18);
        if (titulo != null) {
            t.add(etiqueta(titulo, 15, true, null));
            t.add(Box.createVerticalStrut(10));
            t.add(separador());
            t.add(Box.createVerticalStrut(4));
        }
        if (subtitulo != null) {
            t.add(Box.createVerticalStrut(2));
            t.add(parrafo(subtitulo, 13, Tema.SUAVE));
            t.add(Box.createVerticalStrut(8));
        } else if (titulo != null) {
            t.add(Box.createVerticalStrut(6));
        }
        return t;
    }

    public static JComponent separador() {
        JPanel s = new JPanel();
        s.setBackground(Tema.BORDE);
        s.setPreferredSize(new Dimension(10, 1));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return izquierda(s);
    }

    public static JComponent separadorColor(Color color) {
        JPanel s = new JPanel();
        s.setBackground(color);
        s.setPreferredSize(new Dimension(10, 1));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return izquierda(s);
    }

    public static JPanel rejilla(int columnas, JComponent... piezas) {
        JPanel r = new JPanel(new GridLayout(1, columnas, 14, 14));
        r.setBackground(Tema.FONDO);
        for (JComponent p : piezas) {
            r.add(p);
        }
        return izquierda(r);
    }

    public static JPanel rejilla(int columnas, List<? extends JComponent> piezas) {
        JPanel r = new JPanel(new GridLayout(0, columnas, 14, 14));
        r.setBackground(Tema.FONDO);
        piezas.forEach(r::add);
        return izquierda(r);
    }

    public static JPanel numero(String texto) {
        Redondo r = new Redondo(8, Tema.acentoSuave(), null, 1f, new BorderLayout());
        r.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        r.add(etiqueta(texto, 14, true, Tema.acento()));
        r.setMaximumSize(r.getPreferredSize());
        return r;
    }

    public static JPanel mosaicos(Object[][] datos) {
        JPanel fila = new JPanel(new GridLayout(1, datos.length, 12, 0));
        fila.setBackground(Tema.FONDO);
        for (Object[] d : datos) {
            JPanel t = Redondo.vertical(10, Tema.BLANCO, Tema.BORDE, 12, 16);
            t.add(etiqueta(String.valueOf(d[0]), 13, false, Tema.SUAVE));
            t.add(etiqueta(fmt(d[1]), 26, true, Tema.acento()));
            fila.add(t);
        }
        return izquierda(fila);
    }

    public static JPanel enlace(String texto, Runnable accion) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Tema.BLANCO);
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JLabel l = etiqueta(texto, 12, true, Tema.acento());
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                accion.run();
            }
        });
        p.add(l, BorderLayout.WEST);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return izquierda(p);
    }

    /** Campo con etiqueta arriba: caja de texto o lista de opciones. */
    public static final class Campo {
        private final JPanel panel;
        private final JComponent control;

        private Campo(String etiqueta, JComponent control, Color fondo) {
            this.control = control;
            panel = vertical(fondo);
            panel.add(Componentes.etiqueta(etiqueta, 12, false, Tema.SUAVE));
            panel.add(Box.createVerticalStrut(3));
            control.setAlignmentX(Component.LEFT_ALIGNMENT);
            control.setFont(Tema.fuente(14, false));
            if (control instanceof JComboBox) {
                control.setMaximumSize(control.getPreferredSize());
            }
            if (!(control instanceof JComboBox)) {
                control.setBackground(Tema.BLANCO);
            }
            panel.add(control);
            panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 10));
        }

        public static Campo texto(String etiqueta, int columnas, Color fondo) {
            JTextField t = new JTextField(columnas);
            t.setBorder(new Redondo.Borde(5, Tema.BORDE, new java.awt.Insets(7, 10, 7, 10)));
            t.setMaximumSize(new Dimension(t.getPreferredSize().width, t.getPreferredSize().height));
            return new Campo(etiqueta, t, fondo);
        }

        public static Campo opciones(String etiqueta, List<String> opciones, Color fondo) {
            JComboBox<String> c = new JComboBox<>(opciones.toArray(new String[0]));
            // aspecto nativo del sistema: menú emergente de macOS o lista de Windows
            c.setMaximumRowCount(14);
            return new Campo(etiqueta, c, fondo);
        }

        public JPanel panel() {
            return panel;
        }

        public String valor() {
            if (control instanceof JTextField t) {
                return t.getText();
            }
            Object s = ((JComboBox<?>) control).getSelectedItem();
            return s == null ? "" : s.toString();
        }

        public void setValor(String v) {
            if (control instanceof JTextField t) {
                t.setText(v);
            } else {
                ((JComboBox<?>) control).setSelectedItem(v);
            }
        }

        /** Llama a la acción cada vez que cambia el valor (tecla o selección). */
        public void alCambiar(Runnable accion) {
            if (control instanceof JTextField t) {
                t.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) {
                        accion.run();
                    }

                    public void removeUpdate(javax.swing.event.DocumentEvent e) {
                        accion.run();
                    }

                    public void changedUpdate(javax.swing.event.DocumentEvent e) {
                        accion.run();
                    }
                });
            } else {
                ((JComboBox<?>) control).addActionListener(e -> accion.run());
            }
        }

        public JComponent control() {
            return control;
        }
    }
}
