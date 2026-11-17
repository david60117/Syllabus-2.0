package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import mx.uaeh.syllabus.modelo.ErrorValidacion;

/** Pantalla de inicio de sesión (Figma: Acceso · Syllabus 2.0). */
public class VistaAcceso {
    private final App app;

    public VistaAcceso(App app) {
        this.app = app;
    }

    public JComponent construir() {
        JPanel todo = new JPanel(new BorderLayout());
        todo.setBackground(Tema.FONDO);
        // cabecera blanca: logo, nombre del sistema y leyenda a la derecha
        JPanel cab = new JPanel(new BorderLayout());
        cab.setBackground(Tema.BLANCO);
        cab.setPreferredSize(new Dimension(10, 122));
        cab.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDE));
        JPanel marca = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        marca.setOpaque(false);
        JLabel s = Componentes.etiqueta("S", 26, true, Tema.acento());
        s.setBorder(BorderFactory.createEmptyBorder(0, 72, 0, 30));
        marca.add(s);
        marca.add(Componentes.etiqueta("Syllabus 2.0", 28, true, null));
        JPanel centroIzq = new JPanel(new GridBagLayout());
        centroIzq.setOpaque(false);
        centroIzq.add(marca);
        cab.add(centroIzq, BorderLayout.WEST);
        JLabel leyenda = Componentes.etiqueta("PLATAFORMA ACADÉMICA", 13, true, Tema.acento());
        leyenda.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 240));
        JPanel centroDer = new JPanel(new GridBagLayout());
        centroDer.setOpaque(false);
        centroDer.add(leyenda);
        cab.add(centroDer, BorderLayout.EAST);
        todo.add(cab, BorderLayout.NORTH);

        Redondo tarjeta = Redondo.vertical(14, Tema.BLANCO, Tema.BORDE, 48, 52);
        Redondo logo = new Redondo(10, Tema.acentoSuave(), null, 1f, new GridBagLayout());
        logo.add(Componentes.etiqueta("S", 22, true, Tema.acento()));
        logo.setPreferredSize(new Dimension(48, 46));
        logo.setMaximumSize(new Dimension(48, 46));
        tarjeta.add(logo);
        tarjeta.add(Box.createVerticalStrut(26));
        tarjeta.add(Componentes.etiqueta("Iniciar sesión", 32, true, null));
        tarjeta.add(Box.createVerticalStrut(4));
        tarjeta.add(Componentes.etiqueta("Entra a Syllabus 2.0 para continuar.", 15, false, Tema.SUAVE));
        tarjeta.add(Box.createVerticalStrut(40));
        Entradas.Texto usuario = new Entradas.Texto("Escribe tu correo o usuario", 40);
        Entradas.Clave contrasena = new Entradas.Clave("••••••••", 40);
        tarjeta.add(Componentes.etiqueta("Correo o usuario", 14, true, null));
        tarjeta.add(Box.createVerticalStrut(10));
        tarjeta.add(Entradas.caja(usuario, 14, 16));
        tarjeta.add(Box.createVerticalStrut(26));
        tarjeta.add(Componentes.etiqueta("Contraseña", 14, true, null));
        tarjeta.add(Box.createVerticalStrut(10));
        tarjeta.add(Entradas.caja(contrasena, 14, 16));
        tarjeta.add(Box.createVerticalStrut(14));
        JLabel mensaje = Componentes.etiqueta(" ", 13, false, Tema.ROJO);
        tarjeta.add(mensaje);
        tarjeta.add(Box.createVerticalStrut(10));
        Runnable ingresar = () -> {
            try {
                app.entrar(app.getCuentas().iniciarSesion(usuario.getText(), contrasena.valor(), app.getGestor()));
            } catch (ErrorValidacion e) {
                mensaje.setText(Character.toUpperCase(e.getDetalle().charAt(0)) + e.getDetalle().substring(1) + ".");
            }
        };
        usuario.addActionListener(e -> ingresar.run());
        contrasena.addActionListener(e -> ingresar.run());
        Boton entrar = new Boton("Ingresa", ingresar, true) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, 50);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, 50);
            }
        };
        entrar.setFont(Tema.fuente(15, true));
        tarjeta.add(entrar);
        tarjeta.setPreferredSize(new Dimension(620, tarjeta.getPreferredSize().height));

        JPanel cuerpo = new JPanel(new GridBagLayout());
        cuerpo.setBackground(Tema.FONDO);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTH;
        c.weighty = 1;
        c.insets = new Insets(54, 0, 16, 0);
        cuerpo.add(tarjeta, c);
        JScrollPane desplazamiento = new JScrollPane(cuerpo);
        desplazamiento.setBorder(null);
        desplazamiento.getViewport().setBackground(Tema.FONDO);
        todo.add(desplazamiento);
        SwingUtilities.invokeLater(usuario::requestFocusInWindow);
        return todo;
    }
}
