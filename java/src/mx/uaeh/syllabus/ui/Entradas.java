package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Campos de texto del diseño: texto guía (placeholder), contraseña con ojo para mostrarla y casillas. */
public final class Entradas {
    private Entradas() {
    }

    /** Texto gris dentro del campo mientras esté vacío. */
    private static void guia(Graphics g, JTextField campo, String guia) {
        if (guia == null || !campo.getText().isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(new Color(0x9a928e));
        g2.setFont(campo.getFont());
        Insets i = campo.getInsets();
        java.awt.FontMetrics fm = g2.getFontMetrics();
        g2.drawString(guia, i.left, (campo.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    public static class Texto extends JTextField {
        private final String textoGuia;

        public Texto(String guia, int columnas) {
            super(columnas);
            this.textoGuia = guia;
            setFont(Tema.fuente(14, false));
            setBorder(BorderFactory.createEmptyBorder());
            setBackground(Tema.BLANCO);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            guia(g, this, textoGuia);
        }
    }

    public static class Clave extends JPasswordField {
        private final String textoGuia;

        public Clave(String guia, int columnas) {
            super(columnas);
            this.textoGuia = guia;
            setFont(Tema.fuente(14, false));
            setBorder(BorderFactory.createEmptyBorder());
            setBackground(Tema.BLANCO);
            setEchoChar('•');
        }

        public String valor() {
            return new String(getPassword());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            guia(g, this, textoGuia);
        }
    }

    /** Caja redondeada con el campo y, si es contraseña, el ícono de ojo (eye / eye-off de Lucide). */
    public static JPanel caja(JTextField campo, int padV, int padH) {
        Redondo borde = new Redondo(7, Tema.BLANCO, Tema.BORDE, 1f, new BorderLayout(10, 0));
        borde.setBorder(BorderFactory.createEmptyBorder(padV, padH, padV, padH));
        borde.add(campo);
        if (campo instanceof JPasswordField clave) {
            JLabel ojo = new JLabel();
            ojo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            ojo.setToolTipText("Mostrar u ocultar la contraseña");
            Runnable pintar = () -> {
                boolean oculta = clave.getEchoChar() != 0;
                ojo.setIcon(Iconos.icono(oculta ? "ojo" : "ojo_cerrado", 18, 22, oculta ? Tema.SUAVE : Tema.acento()));
            };
            pintar.run();
            ojo.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    clave.setEchoChar(clave.getEchoChar() == 0 ? '•' : (char) 0);
                    pintar.run();
                    clave.requestFocusInWindow();
                }
            });
            borde.add(ojo, BorderLayout.EAST);
        }
        borde.setMaximumSize(new Dimension(Integer.MAX_VALUE, borde.getPreferredSize().height));
        return borde;
    }

    public static JCheckBox casilla(String texto, boolean marcada, Color fondo) {
        JCheckBox c = new JCheckBox(texto, marcada);
        c.setFont(Tema.fuente(13, false));
        c.setForeground(Tema.TEXTO);
        c.setBackground(fondo);
        c.setOpaque(false);
        c.setFocusPainted(false);
        c.setAlignmentX(LEFT);
        return c;
    }

    private static final float LEFT = 0f;
}
