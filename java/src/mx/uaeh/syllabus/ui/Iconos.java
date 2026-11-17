package mx.uaeh.syllabus.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.util.Map;

import javax.swing.Icon;

/**
 * Íconos de Lucide (https://lucide.dev, licencia ISC) tomados de un subconjunto de su fuente (iconos.ttf), el mismo
 * archivo que usa la versión de Python. Se dibujan como contornos con Java2D y suavizado, así que se ven nítidos a
 * cualquier escala (también en pantallas Retina).
 */
public final class Iconos {
    private static final Map<String, Integer> CODIGOS = Map.ofEntries(Map.entry("inicio", 0xE0F5),
            Map.entry("estudiantes", 0xE234), Map.entry("grupos", 0xE05F), Map.entry("docentes", 0xE4AE),
            Map.entry("inscripciones", 0xE219), Map.entry("consultas", 0xE151), Map.entry("reportes", 0xE2A3),
            Map.entry("usuarios", 0xE46E), Map.entry("ajustes", 0xE245), Map.entry("cursos", 0xE598), Map.entry("plan", 0xE1D0),
            Map.entry("captura", 0xE4F0), Map.entry("calificaciones", 0xE0CC), Map.entry("kardex", 0xE617),
            Map.entry("cuenta", 0xE462), Map.entry("ojo", 0xE0BA), Map.entry("ojo_cerrado", 0xE0BB));
    private static final Font FUENTE = cargar();

    private Iconos() {
    }

    private static Font cargar() {
        try (InputStream in = Iconos.class.getResourceAsStream("iconos.ttf")) {
            return in == null ? null : Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            return null; // sin la fuente se muestran sin ícono
        }
    }

    /** Ícono de tam × tam píxeles centrado en un área de caja × caja. */
    public static Icon icono(String nombre, int tam, int caja, Color color) {
        return new Glifo(CODIGOS.get(nombre), tam, caja, color);
    }

    public static Icon icono(String nombre, int tam, Color color) {
        return icono(nombre, tam, tam, color);
    }

    private record Glifo(Integer codigo, int tam, int caja, Color color) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (FUENTE == null || codigo == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Font f = FUENTE.deriveFont((float) tam);
            GlyphVector gv = f.createGlyphVector(g2.getFontRenderContext(), new String(Character.toChars(codigo)));
            java.awt.Shape forma = gv.getOutline();
            Rectangle2D b = gv.getVisualBounds();
            // se centra por su contorno visible dentro del área (el glifo ocupa el cuadro de 24 × 24 de Lucide)
            double dx = x + (caja - b.getWidth()) / 2.0 - b.getX();
            double dy = y + (caja - b.getHeight()) / 2.0 - b.getY();
            g2.translate(dx, dy);
            g2.setColor(color);
            g2.fill(forma);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return caja;
        }

        @Override
        public int getIconHeight() {
            return caja;
        }
    }
}
