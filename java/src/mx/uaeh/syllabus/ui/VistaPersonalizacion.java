package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Personalización: tema de color por institución, opciones de la interfaz y (administrador) datos de los reportes. */
public class VistaPersonalizacion extends Vista {
    public VistaPersonalizacion(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        app.encabezado(("Administrador".equals(app.getRol()) ? "Inicio" : app.getRol()) + "  /  Personalización", "Personalización",
                "Ajusta la apariencia");
        String pendiente = app.memoria("tema_pendiente") instanceof String s ? s : Tema.getActual();
        JPanel t = tarjeta("Apariencia por institución");
        int i = 0;
        for (Map.Entry<String, Color[]> tema : Tema.TEMAS.entrySet()) {
            JPanel f = new JPanel(new BorderLayout(12, 0));
            f.setOpaque(false);
            f.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
            Redondo muestra = new Redondo(7, tema.getValue()[0], null, 1f, null);
            muestra.setPreferredSize(new Dimension(28, 28));
            JPanel izq = new JPanel(new GridBagLayout());
            izq.setOpaque(false);
            izq.add(muestra);
            f.add(izq, BorderLayout.WEST);
            JPanel textos = Componentes.vertical(null);
            textos.setOpaque(false);
            textos.add(etiqueta(tema.getKey(), 15, true, null));
            textos.add(etiqueta(i == 0 ? "Predeterminado" : "Tema alternativo", 12, false, Tema.SUAVE));
            f.add(textos);
            boolean elegido = tema.getKey().equals(pendiente);
            String nombre = tema.getKey();
            JPanel der = new JPanel(new GridBagLayout());
            der.setOpaque(false);
            der.add(boton(elegido ? "● Seleccionado" : "○ Elegir", () -> {
                app.guardarEnMemoria("tema_pendiente", nombre);
                app.refrescar();
            }, false));
            f.add(der, BorderLayout.EAST);
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
            poner(t, f);
            i++;
        }
        t.add(javax.swing.Box.createVerticalStrut(12));
        poner(t, linea(null, boton("Guardar apariencia", () -> {
            Tema.setActual(pendiente);
            app.guardarEnMemoria("tema_pendiente", null);
            app.construirMarco();
        }, true)));
        JPanel o = tarjeta("Opciones de configuración");
        String[][] opciones = {{"colorear", "Resaltar aprobados y reprobados"}, {"avisos", "Mostrar eventos de validación"},
                {"recordar", "Recordar filtros seleccionados"}};
        for (String[] op : opciones) {
            JCheckBox c = Entradas.casilla(op[1], app.opcion(op[0]), Tema.BLANCO);
            c.addActionListener(e -> app.setOpcion(op[0], c.isSelected()));
            poner(o, c, 3);
        }
        o.add(javax.swing.Box.createVerticalStrut(20));
        poner(o, etiqueta("La personalización solo cambia la apariencia.", 12, false, Tema.SUAVE));
        JPanel fila = new JPanel(new GridBagLayout());
        fila.setBackground(Tema.FONDO);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 2;
        c.insets = new java.awt.Insets(0, 0, 0, 7);
        fila.add(t, c);
        c.weightx = 1;
        c.insets = new java.awt.Insets(0, 7, 0, 0);
        fila.add(o, c);
        app.agregar(fila);
        if ("Administrador".equals(app.getRol())) {
            datosInstitucion();
        }
        Color fuerte = Tema.TEMAS.get(pendiente)[0];
        JPanel p = tarjeta("Vista previa");
        Redondo marco = new Redondo(10, Tema.BLANCO, Tema.BORDE, 1f, new BorderLayout());
        JLabel barra = etiqueta("Sistema académico", 15, true, Color.WHITE);
        barra.setOpaque(true);
        barra.setBackground(fuerte);
        barra.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        marco.add(barra, BorderLayout.NORTH);
        JPanel nav = Componentes.vertical(null);
        nav.setOpaque(false);
        nav.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Tema.BORDE),
                BorderFactory.createEmptyBorder(12, 16, 12, 30)));
        String[] items = {"Registro", "Captura", "Reportes"};
        for (int k = 0; k < items.length; k++) {
            nav.add(etiqueta(items[k], 14, k == 0, k == 0 ? fuerte : Tema.TEXTO));
            nav.add(javax.swing.Box.createVerticalStrut(6));
        }
        marco.add(nav, BorderLayout.WEST);
        JPanel cont = Componentes.vertical(null);
        cont.setOpaque(false);
        cont.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        cont.add(etiqueta("Datos escolares", 15, true, null));
        cont.add(etiqueta("El tema cambia la apariencia, no la estructura de la información.", 12, false, Tema.SUAVE));
        marco.add(cont);
        poner(p, marco);
        app.agregar(p);
    }

    private void datosInstitucion() {
        Map<String, Object> inst = app.getInstitucion();
        Map<String, String> v = new HashMap<>();
        Map<String, String> omision = mx.uaeh.syllabus.documentos.Documentos.institucionPorOmision();
        for (String k : new String[]{"institucion", "programa", "ciclo"}) {
            v.put(k, String.valueOf(inst.getOrDefault(k, omision.get(k))));
        }
        JPanel t = tarjeta("Datos del encabezado de los reportes", "Aparecen en la boleta, el kárdex, las actas y los demás reportes.");
        JPanel rej = new JPanel(new GridLayout(1, 3, 14, 0));
        rej.setOpaque(false);
        rej.add(texto(v, "institucion", "Institución", 26, Tema.BLANCO).panel());
        rej.add(texto(v, "programa", "Programa educativo", 26, Tema.BLANCO).panel());
        rej.add(texto(v, "ciclo", "Ciclo escolar", 26, Tema.BLANCO).panel());
        poner(t, rej, 12);
        poner(t, linea(null, boton("Guardar datos", () -> {
            if (v.values().stream().anyMatch(x -> x.isBlank())) {
                app.avisar("Faltan datos", "Llena institución, programa y ciclo escolar.", true);
                return;
            }
            v.forEach((k, x) -> inst.put(k, x.strip()));
            app.guardarAhora();
            app.registrarActividad("institucion_actualizada · encabezado de reportes");
            app.informar("Datos guardados", "Los reportes usarán estos datos en su encabezado.");
            app.construirMarco();
        }, true)));
        app.agregar(t);
    }
}
