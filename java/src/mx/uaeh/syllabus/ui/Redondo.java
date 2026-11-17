package mx.uaeh.syllabus.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;

/** Panel con fondo y borde de esquinas redondeadas (tarjetas, menú, insignias). */
public class Redondo extends JPanel {
    private final int radio;
    private Color fondo;
    private Color borde;
    private final float grosor;

    public Redondo(int radio, Color fondo, Color borde, float grosor, LayoutManager layout) {
        super(layout);
        this.radio = radio;
        this.fondo = fondo;
        this.borde = borde;
        this.grosor = grosor;
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /** Panel vertical (BoxLayout) con relleno interior. */
    public static Redondo vertical(int radio, Color fondo, Color borde, int padV, int padH) {
        Redondo r = new Redondo(radio, fondo, borde, 1f, null);
        r.setLayout(new BoxLayout(r, BoxLayout.Y_AXIS));
        r.setBorder(BorderFactory.createEmptyBorder(padV, padH, padV, padH));
        return r;
    }

    public void setColores(Color nuevoFondo, Color nuevoBorde) {
        fondo = nuevoFondo;
        borde = nuevoBorde;
        repaint();
    }

    @Override
    public Color getBackground() {
        return fondo == null ? super.getBackground() : fondo;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float m = grosor / 2f;
        int w = getWidth();
        int h = getHeight();
        int r = Math.min(radio * 2, Math.min(w, h));
        g2.setColor(fondo);
        g2.fillRoundRect(0, 0, w - 1, h - 1, r, r);
        if (borde != null) {
            g2.setColor(borde);
            g2.setStroke(new BasicStroke(grosor));
            g2.drawRoundRect(Math.round(m), Math.round(m), Math.round(w - 1 - grosor), Math.round(h - 1 - grosor), r, r);
        }
        g2.dispose();
        super.paintComponent(g);
    }

    /** Borde redondeado para campos de texto, listas y tablas. */
    public static class Borde extends AbstractBorder {
        private final int radio;
        private final Color color;
        private final Insets relleno;

        public Borde(int radio, Color color, Insets relleno) {
            this.radio = radio;
            this.color = color;
            this.relleno = relleno;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color exterior = c.getParent() == null ? Tema.BLANCO : c.getParent().getBackground();
            // tapa las esquinas cuadradas del componente con el fondo del contenedor
            g2.setColor(exterior);
            g2.setStroke(new BasicStroke(radio));
            g2.drawRoundRect(x - radio / 2, y - radio / 2, w + radio - 1, h + radio - 1, radio * 3, radio * 3);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radio * 2, radio * 2);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(relleno.top + 1, relleno.left + 1, relleno.bottom + 1, relleno.right + 1);
        }
    }
}
