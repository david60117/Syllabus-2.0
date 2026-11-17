package mx.uaeh.syllabus.ui;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JPanel;

import mx.uaeh.syllabus.logica.Autenticacion;
import mx.uaeh.syllabus.modelo.Cuenta;
import mx.uaeh.syllabus.modelo.ErrorValidacion;

/** Mi cuenta (todos los perfiles): datos de acceso y cambio de contraseña; obligatorio con la contraseña inicial. */
public class VistaMiCuenta extends Vista {
    public VistaMiCuenta(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        Cuenta cuenta = app.getCuenta();
        app.encabezado(app.getRol() + "  /  Mi cuenta", "Mi cuenta", "Datos de acceso y cambio de contraseña.");
        if (cuenta.debeCambiar()) {
            Redondo banda = Redondo.vertical(11, Tema.AMBAR_SUAVE, null, 14, 18);
            banda.add(etiqueta("Estás usando la contraseña inicial. Cámbiala para continuar.", 15, true, new Color(0xa26b34)));
            banda.setMaximumSize(new Dimension(Integer.MAX_VALUE, banda.getPreferredSize().height));
            app.agregar(banda);
        }
        JPanel datos = tarjeta("Datos de la cuenta");
        String[][] valores = {{"Usuario", cuenta.usuario()}, {"Nombre", cuenta.nombre()}, {"Rol", cuenta.rol()},
                {"Referencia", cuenta.referencia() == null ? "—" : cuenta.referencia()}};
        for (String[] v : valores) {
            poner(datos, dato(v[0], v[1], null, Tema.BLANCO), 8);
        }
        JPanel form = tarjeta("Cambiar contraseña");
        Entradas.Clave actual = new Entradas.Clave(null, 24);
        Entradas.Clave nueva = new Entradas.Clave(null, 24);
        Entradas.Clave confirmacion = new Entradas.Clave(null, 24);
        Object[][] campos = {{"Contraseña actual", actual}, {"Nueva contraseña", nueva}, {"Confirmar nueva contraseña", confirmacion}};
        for (Object[] c : campos) {
            poner(form, etiqueta((String) c[0], 12, false, Tema.SUAVE), 3);
            JPanel caja = Entradas.caja((Entradas.Clave) c[1], 6, 10);
            caja.setMaximumSize(new Dimension(380, caja.getPreferredSize().height));
            poner(form, caja, 8);
        }
        nota(form, "Mínimo 8 caracteres, con letras y números.");
        Runnable guardar = () -> {
            boolean forzado = cuenta.debeCambiar();
            try {
                Autenticacion.cambiarContrasena(cuenta, actual.valor(), nueva.valor(), confirmacion.valor());
            } catch (ErrorValidacion e) {
                app.avisar("No se cambió la contraseña", Character.toUpperCase(e.getDetalle().charAt(0)) + e.getDetalle().substring(1), true);
                return;
            }
            app.guardarAhora();
            app.registrarActividad("contrasena_cambiada · " + cuenta.usuario());
            app.informar("Contraseña actualizada", "Tu contraseña se cambió correctamente.");
            app.ir(forzado ? "Inicio" : "Mi cuenta");
        };
        actual.addActionListener(e -> nueva.requestFocusInWindow());
        nueva.addActionListener(e -> confirmacion.requestFocusInWindow());
        confirmacion.addActionListener(e -> guardar.run());
        poner(form, linea(null, boton("Guardar contraseña", guardar, true)));
        app.agregar(fila(Tema.FONDO, datos, form));
    }
}
