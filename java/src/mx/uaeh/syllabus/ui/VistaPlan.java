package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import mx.uaeh.syllabus.logica.GestorEscolar.Rubro;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Evaluacion;
import mx.uaeh.syllabus.modelo.Parcial;

/** Docente: plan de evaluación de cada parcial (evaluaciones y porcentajes que suman 100 %). */
public class VistaPlan extends Vista {
    private static final String[][] PLANTILLA = {{"Examen", "60"}, {"Tareas", "20"}, {"Actividades", "20"}};

    public VistaPlan(App app) {
        super(app);
    }

    private static Map<String, String> renglon(String id, String nombre, String pct) {
        Map<String, String> r = new HashMap<>();
        r.put("id", id);
        r.put("nombre", nombre);
        r.put("pct", pct);
        return r;
    }

    @Override
    public void dibujar() {
        app.encabezado("Docente  /  Plan de evaluación", "Plan de evaluación",
                "Define las evaluaciones de cada parcial y su porcentaje. Cada parcial debe sumar 100 %.");
        JPanel barra = tarjeta(null);
        JPanel f = linea(null);
        Curso curso = selectorCurso(f, "filtro:plan_curso", app.cursosVisibles());
        poner(barra, f);
        if (curso == null) {
            poner(barra, etiqueta("No tienes materias asignadas.", 14, false, Tema.SUAVE));
            app.agregar(barra);
            return;
        }
        Map<String, List<Map<String, String>>> borradores = app.memoria("plan", HashMap::new);
        List<Parcial> parciales = g.parcialesDe(curso.id());
        for (Parcial p : parciales) {
            borradores.computeIfAbsent(p.id(), k -> {
                List<Map<String, String>> l = new ArrayList<>();
                g.evaluacionesDe(p.id()).forEach(e -> l.add(renglon(e.id(), e.nombre(), pct(e.peso()))));
                return l;
            });
        }
        f.add(boton("Usar plantilla (Examen 60 · Tareas 20 · Actividades 20)", () -> {
            for (Parcial p : parciales) {
                if (g.evaluacionesDe(p.id()).isEmpty() || app.confirmar("Usar plantilla", "El parcial " + p.numero() + " ya tiene plan. ¿Reemplazarlo?")) {
                    List<Map<String, String>> l = new ArrayList<>();
                    for (String[] x : PLANTILLA) {
                        l.add(renglon(null, x[0], x[1]));
                    }
                    borradores.put(p.id(), l);
                }
            }
            app.refrescar();
        }, false));
        app.agregar(barra);
        JPanel columnas = new JPanel(new GridLayout(1, 3, 14, 0));
        columnas.setBackground(Tema.FONDO);
        for (Parcial p : parciales) {
            JPanel t = tarjeta("Parcial " + p.numero() + "  ·  " + pct(p.peso()) + " % del curso");
            tarjetaParcial(t, borradores.get(p.id()));
            columnas.add(t);
        }
        app.agregar(columnas);
        Runnable guardar = () -> {
            Map<String, List<Rubro>> planes = new LinkedHashMap<>();
            int perdidas = 0;
            for (Parcial p : parciales) {
                List<Rubro> rubros = new ArrayList<>();
                Set<String> conservados = new HashSet<>();
                for (Map<String, String> r : borradores.get(p.id())) {
                    rubros.add(new Rubro(r.get("id"), r.get("nombre"), peso(r.get("pct"))));
                    if (r.get("id") != null) {
                        conservados.add(r.get("id"));
                    }
                }
                planes.put(p.id(), rubros);
                for (Evaluacion e : g.evaluacionesDe(p.id())) {
                    if (!conservados.contains(e.id())) {
                        perdidas += (int) g.getCalificaciones().keySet().stream().filter(k -> k.b().equals(e.id())).count();
                    }
                }
            }
            if (perdidas > 0 && !app.confirmar("Quitar evaluaciones", "Se borrarán " + perdidas + " calificaciones ya capturadas. ¿Continuar?")) {
                return;
            }
            if (app.hacer(() -> g.definirPlanes(planes))) {
                parciales.forEach(p -> borradores.remove(p.id()));
                app.informar("Plan guardado", "Plan de evaluación de " + curso.asignatura() + " guardado. Ya puedes capturar calificaciones.");
                app.refrescar();
            }
        };
        app.agregar(linea(Tema.FONDO, boton("Guardar plan", guardar, true), boton("Descartar cambios", () -> {
            parciales.forEach(p -> borradores.remove(p.id()));
            app.refrescar();
        }, false)));
        notaPie("Al quitar una evaluación que ya tiene calificaciones, esas calificaciones se borran (se te pedirá confirmar). "
                + "Cambiar porcentajes recalcula los promedios.");
    }

    private void tarjetaParcial(JPanel t, List<Map<String, String>> filas) {
        JLabel suma = etiqueta("", 14, true, null);
        Runnable actualizar = () -> {
            double total = 0;
            for (Map<String, String> r : filas) {
                if (num(r.getOrDefault("pct", "")) instanceof Double d) {
                    total += d;
                }
            }
            boolean ok = Math.abs(total - 100) < 1e-9;
            suma.setText("Suma: " + mx.uaeh.syllabus.logica.Calculos.normalizado(total) + " %  " + (ok ? "✓" : "· debe ser 100 %"));
            suma.setForeground(ok ? new Color(0x247457) : new Color(0xa32e34));
        };
        JPanel cab = new JPanel(new BorderLayout());
        cab.setOpaque(false);
        cab.add(etiqueta("Evaluación", 12, false, Tema.SUAVE));
        JLabel por = etiqueta("%", 12, false, Tema.SUAVE);
        por.setPreferredSize(new Dimension(108, 16));
        cab.add(por, BorderLayout.EAST);
        cab.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        poner(t, cab, 2);
        for (Map<String, String> r : filas) {
            JPanel f = new JPanel(new BorderLayout(6, 0));
            f.setOpaque(false);
            f.add(campo(r, "nombre", 14, actualizar));
            JPanel der = new JPanel(new BorderLayout(4, 0));
            der.setOpaque(false);
            der.add(campo(r, "pct", 4, actualizar));
            JLabel x = etiqueta("×", 18, true, Tema.SUAVE);
            clicable(x, () -> {
                filas.remove(r);
                app.refrescar();
            });
            der.add(x, BorderLayout.EAST);
            f.add(der, BorderLayout.EAST);
            f.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
            poner(t, f);
        }
        t.add(javax.swing.Box.createVerticalStrut(6));
        poner(t, enlace("+  Agregar evaluación", () -> {
            filas.add(renglon(null, "", ""));
            app.refrescar();
        }), 6);
        poner(t, suma);
        actualizar.run();
    }

    private static JPanel campo(Map<String, String> r, String clave, int columnas, Runnable alCambiar) {
        javax.swing.JTextField texto = new javax.swing.JTextField(r.getOrDefault(clave, ""), columnas);
        texto.setFont(Tema.fuente(14, false));
        texto.setBorder(new Redondo.Borde(5, Tema.BORDE, new java.awt.Insets(6, 8, 6, 8)));
        texto.getDocument().addDocumentListener(new DocumentListener() {
            private void cambio() {
                r.put(clave, texto.getText());
                alCambiar.run();
            }

            public void insertUpdate(DocumentEvent e) {
                cambio();
            }

            public void removeUpdate(DocumentEvent e) {
                cambio();
            }

            public void changedUpdate(DocumentEvent e) {
                cambio();
            }
        });
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(texto);
        return p;
    }
}
