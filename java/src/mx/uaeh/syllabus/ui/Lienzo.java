package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.Scrollable;

/** Panel que se ajusta al ancho de la ventana y solo se desplaza verticalmente. */
public class Lienzo extends JPanel implements Scrollable {
    public Lienzo() {
        super(new BorderLayout());
        setBackground(Tema.FONDO);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle r, int orientacion, int direccion) {
        return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle r, int orientacion, int direccion) {
        return r.height - 40;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
