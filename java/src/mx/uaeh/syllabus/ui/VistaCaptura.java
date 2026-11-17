package mx.uaeh.syllabus.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import mx.uaeh.syllabus.archivos.Archivos;
import mx.uaeh.syllabus.logica.Calculos;
import mx.uaeh.syllabus.logica.GestorEscolar.CambioActa;
import mx.uaeh.syllabus.logica.GestorEscolar.ResultadoActa;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.ErrorValidacion;
import mx.uaeh.syllabus.modelo.Estudiante;
import mx.uaeh.syllabus.modelo.Evaluacion;
import mx.uaeh.syllabus.modelo.Parcial;
import mx.uaeh.syllabus.modelo.RegistroError;

/** Docente: acta de calificaciones (todo el grupo, se guarda de una vez) e importación desde archivo. */
public class VistaCaptura extends Vista {
    private static final Color VERDE = new Color(0x247457);
    private static final Color ROJO = new Color(0xa32e34);
    private static final Color AMBAR = new Color(0xa26b34);

    public VistaCaptura(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        int activa = app.memoria("pestana:captura", () -> 0);
        app.encabezado(rutaDe("Captura"), activa == 0 ? "Acta de calificaciones" : "Importar calificaciones",
                activa == 0 ? "Captura las calificaciones de todo el grupo y guárdalas juntas."
                        : "Carga y procesa calificaciones de varios estudiantes desde un archivo.");
        if (pestanas("captura", "Acta de calificaciones", "Importar archivo") == 0) {
            acta();
        } else {
            importar();
        }
    }

    /** Calificación válida: vacía, o de 0 a 10 con máximo un decimal. */
    static boolean valida(String texto) {
        String t = texto.strip();
        if (t.isEmpty()) {
            return true;
        }
        try {
            double x = Double.parseDouble(t);
            return x >= 0 && x <= 10 && Math.abs(Math.round(x * 10) / 10.0 - x) < 1e-9;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String guardado(String nc, String eid) {
        Double x = g.calificacion(nc, eid);
        return x == null ? "" : java.math.BigDecimal.valueOf(x).stripTrailingZeros().toPlainString();
    }

    private void acta() {
        JPanel barra = tarjeta(null);
        JPanel f = linea(null);
        Curso curso = selectorCurso(f, "filtro:acta_curso", app.cursosVisibles());
        poner(barra, f);
        if (curso == null) {
            poner(barra, etiqueta("No tienes materias asignadas.", 14, false, Tema.SUAVE));
            app.agregar(barra);
            return;
        }
        List<Parcial> parciales = g.parcialesDe(curso.id());
        List<String> nombres = parciales.stream().map(p -> "Parcial " + p.numero() + " · " + pct(p.peso()) + " %").toList();
        Map<String, String> estado = app.estado("filtro:acta_parcial", Map.of("parcial", ""));
        f.add(opciones(estado, "parcial", "Parcial", nombres, Tema.BLANCO, app::refrescar).panel());
        app.agregar(barra);
        Parcial parcial = nombres.isEmpty() ? null : parciales.get(nombres.indexOf(estado.get("parcial")));
        List<Evaluacion> evals = parcial == null ? List.of() : g.evaluacionesDe(parcial.id());
        if (evals.isEmpty() || Math.abs(evals.stream().mapToDouble(Evaluacion::peso).sum() - 1) > 1e-9) {
            JPanel t = tarjeta(null);
            poner(t, etiqueta("Este parcial no tiene su plan de evaluación completo (100 %).", 15, true, AMBAR), 10);
            poner(t, linea(null, boton("Definir plan de evaluación", () -> app.ir("Plan de evaluación"), true)));
            app.agregar(t);
            return;
        }
        List<Estudiante> alumnos = g.alumnosDeCurso(curso.id());
        Map<String, Map<String, String>> todas = app.memoria("acta", HashMap::new);
        Map<String, String> cambios = todas.computeIfAbsent(parcial.id(), k -> new HashMap<>());
        JPanel t = tarjeta(curso.asignatura() + " · grupo " + curso.grupo() + " · " + estado.get("parcial"),
                "Escribe calificaciones de 0 a 10 con un decimal. Enter o ↓ baja al siguiente alumno; deja vacío para no capturar.");
        JPanel rejilla = new JPanel(new GridBagLayout());
        rejilla.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 6, 2, 6);
        List<String> titulos = new ArrayList<>(List.of("No.", "Cuenta", "Alumno"));
        evals.forEach(e -> titulos.add(e.nombre() + " " + pct(e.peso()) + " %"));
        titulos.add("Parcial");
        titulos.add("Estado");
        for (int j = 0; j < titulos.size(); j++) {
            c.gridx = j;
            c.gridy = 0;
            c.weightx = j == 2 ? 1 : 0;
            rejilla.add(etiqueta(titulos.get(j), 12, true, Tema.SUAVE), c);
        }
        JTextField[][] entradas = new JTextField[alumnos.size()][evals.size()];
        JLabel contador = etiqueta("", 12, false, Tema.acento());
        for (int i = 0; i < alumnos.size(); i++) {
            Estudiante e = alumnos.get(i);
            c.gridy = i + 1;
            String[] fijos = {String.valueOf(i + 1), e.numCuenta(), e.nombreCompleto()};
            for (int j = 0; j < 3; j++) {
                c.gridx = j;
                c.weightx = j == 2 ? 1 : 0;
                rejilla.add(etiqueta(fijos[j], 14, false, null), c);
            }
            c.weightx = 0;
            JLabel nota = etiqueta("—", 14, true, Tema.acento());
            nota.setPreferredSize(new java.awt.Dimension(60, 20));
            JLabel est = etiqueta("", 13, false, null);
            est.setPreferredSize(new java.awt.Dimension(100, 20));
            int fila = i;
            Runnable recalcular = () -> {
                List<String> textos = new ArrayList<>();
                boolean todasValidas = true;
                for (int j = 0; j < evals.size(); j++) {
                    String x = entradas[fila][j].getText();
                    textos.add(x);
                    boolean ok = valida(x);
                    todasValidas &= ok;
                    entradas[fila][j].setBackground(ok ? Tema.BLANCO : Tema.ROJO_SUAVE);
                }
                if (todasValidas && textos.stream().noneMatch(x -> x.strip().isEmpty())) {
                    double notaP = 0;
                    for (int j = 0; j < evals.size(); j++) {
                        notaP += Double.parseDouble(textos.get(j).strip()) * evals.get(j).peso();
                    }
                    nota.setText(Componentes.fmt(Calculos.mostrar(notaP)));
                    est.setText("Completo");
                    est.setForeground(VERDE);
                } else {
                    nota.setText("—");
                    est.setText(todasValidas ? "Incompleto" : "Con errores");
                    est.setForeground(todasValidas ? AMBAR : ROJO);
                }
                contador.setText(cambios.isEmpty() ? "Sin cambios pendientes" : "Cambios sin guardar: " + cambios.size());
            };
            for (int j = 0; j < evals.size(); j++) {
                String eid = evals.get(j).id();
                String k = e.numCuenta() + "|" + eid;
                JTextField campo = new JTextField(cambios.getOrDefault(k, guardado(e.numCuenta(), eid)), 5);
                campo.setHorizontalAlignment(JTextField.CENTER);
                campo.setFont(Tema.fuente(14, false));
                campo.setBorder(new Redondo.Borde(4, Tema.BORDE, new Insets(4, 6, 4, 6)));
                campo.setName("celda-" + i + "-" + j);
                entradas[i][j] = campo;
                campo.getDocument().addDocumentListener(new DocumentListener() {
                    private void escrito() {
                        if (campo.getText().strip().equals(guardado(e.numCuenta(), eid))) {
                            cambios.remove(k);
                        } else {
                            cambios.put(k, campo.getText());
                        }
                        recalcular.run();
                    }

                    public void insertUpdate(DocumentEvent ev) {
                        escrito();
                    }

                    public void removeUpdate(DocumentEvent ev) {
                        escrito();
                    }

                    public void changedUpdate(DocumentEvent ev) {
                        escrito();
                    }
                });
                int col = j;
                campo.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent ev) {
                        int paso = ev.getKeyCode() == KeyEvent.VK_ENTER || ev.getKeyCode() == KeyEvent.VK_DOWN ? 1
                                : ev.getKeyCode() == KeyEvent.VK_UP ? -1 : 0;
                        if (paso != 0 && fila + paso >= 0 && fila + paso < alumnos.size()) {
                            JTextField destino = entradas[fila + paso][col];
                            destino.requestFocusInWindow();
                            destino.selectAll();
                            ev.consume();
                        }
                    }
                });
                c.gridx = 3 + j;
                rejilla.add(campo, c);
            }
            c.gridx = 3 + evals.size();
            rejilla.add(nota, c);
            c.gridx = 4 + evals.size();
            rejilla.add(est, c);
            recalcular.run();
        }
        poner(t, rejilla, 10);
        if (alumnos.isEmpty()) {
            poner(t, etiqueta("No hay alumnos inscritos en esta materia.", 13, false, Tema.SUAVE), 8);
        }
        contador.setText(cambios.isEmpty() ? "Sin cambios pendientes" : "Cambios sin guardar: " + cambios.size());
        poner(t, contador, 12);
        Runnable guardar = () -> {
            List<CambioActa> lote = new ArrayList<>();
            cambios.forEach((k, texto) -> {
                String[] p = k.split("\\|");
                lote.add(new CambioActa(p[0], p[1], texto.strip().isEmpty() ? null : num(texto)));
            });
            if (lote.isEmpty()) {
                app.informar("Acta", "No hay cambios por guardar.");
                return;
            }
            long borrar = lote.stream().filter(x -> x.valor() == null).count();
            if (borrar > 0 && !app.confirmar("Borrar calificaciones", "Dejaste vacías " + borrar
                    + " calificaciones que ya estaban capturadas; se borrarán. ¿Continuar?")) {
                return;
            }
            ResultadoActa r = g.guardarActa(lote);
            Set<String> conError = r.errores().stream().map(x -> x.get("num_cuenta") + "|" + x.get("evaluacion_id")).collect(Collectors.toSet());
            cambios.keySet().removeIf(k -> !conError.contains(k));
            String mensaje = "Nuevas: " + r.capturadas() + "   ·   Modificadas: " + r.modificadas() + "   ·   Borradas: " + r.borradas();
            if (r.errores().isEmpty()) {
                app.informar("Acta guardada", mensaje);
            } else {
                String detalle = r.errores().stream().limit(8).map(x -> x.get("num_cuenta") + " · " + x.get("evaluacion_id") + ": "
                        + Componentes.legible(x.get("detalle"))).collect(Collectors.joining("\n"));
                app.avisar("Acta guardada con errores", mensaje + "\n\nNo se guardaron " + r.errores().size() + ":\n" + detalle, true);
            }
            app.refrescar();
        };
        poner(t, linea(null, boton("Guardar acta", guardar, true), boton("Descartar cambios", () -> {
            cambios.clear();
            app.refrescar();
        }, false)));
        app.agregar(t);
        if (!alumnos.isEmpty()) {
            SwingUtilities.invokeLater(entradas[0][0]::requestFocusInWindow);
        }
    }

    @SuppressWarnings("unchecked")
    private void importar() {
        Map<String, Object> estado = app.memoria("importar_calificaciones", HashMap::new);
        List<Map<String, Object>> resultados = (List<Map<String, Object>>) estado.computeIfAbsent("resultados", k -> new ArrayList<>());
        JPanel t = tarjeta("Captura de varias calificaciones");
        String ruta = (String) estado.get("archivo");
        Map<String, String> vista = new HashMap<>(Map.of("archivo", ruta == null ? "Seleccionar archivo..." : Path.of(ruta).getFileName().toString()));
        var campo = soloLectura("Archivo de calificaciones", vista.get("archivo"), 36, Tema.BLANCO);
        Runnable elegir = () -> {
            Path r = elegirParaAbrir("Archivo de calificaciones");
            if (r != null) {
                estado.put("archivo", r.toString());
                app.refrescar();
            }
        };
        Runnable procesar = () -> {
            if (estado.get("archivo") == null) {
                app.avisar("Falta archivo", "Selecciona un archivo JSON y pulsa Cargar.", true);
                return;
            }
            Map<String, Object> datos;
            try {
                datos = Archivos.cargarJson(Path.of((String) estado.get("archivo")));
            } catch (ErrorValidacion e) {
                app.avisar("Archivo inválido", e.getDetalle(), true);
                return;
            }
            resultados.clear();
            List<Object> registros = datos.get("calificaciones") instanceof List<?> l ? (List<Object>) l : List.of();
            int i = 1;
            for (Object r : registros) {
                int antes = g.getErrores().size();
                boolean ok = g.procesar("calificacion", r, x -> g.capturarCalificacion(x));
                Map<String, Object> m = r instanceof Map<?, ?> mm ? (Map<String, Object>) mm : Map.of();
                RegistroError err = ok ? null : g.getErrores().get(antes);
                resultados.add(VistaAlumno.fila("no", i++, "num_cuenta", String.valueOf(m.getOrDefault("num_cuenta", "—")),
                        "evaluacion", m.getOrDefault("evaluacion_id", "—"), "valor", m.get("valor"),
                        "estado", ok ? "Guardada" : "Rechazada", "detalle", ok ? "" : err.codigo() + ": " + err.detalle()));
            }
            app.registrarActividad("procesamiento · " + resultados.size() + " recibidas");
            app.refrescar();
        };
        poner(t, linea(null, campo.panel(), boton("Cargar", elegir, true), boton("Procesar calificaciones", procesar, true)), 10);
        poner(t, etiqueta("Se validan los registros antes de guardar las calificaciones.", 12, false, Tema.SUAVE), 2);
        poner(t, etiqueta("Los registros con errores se identifican sin detener el procesamiento de los demás.", 12, false, Tema.SUAVE));
        app.agregar(t);
        JPanel r = tarjeta("Resultados de la captura");
        Tabla tabla = new Tabla(new String[][]{{"no", "No."}, {"num_cuenta", "Estudiante"}, {"evaluacion", "Evaluación"}, {"valor", "Calificación"},
                {"estado", "Estado"}, {"detalle", "Detalle"}}, 10, List.of());
        tabla.proporciones(List.of(1, 1, 1, 1, 1, 5));
        tabla.cargar(resultados);
        poner(r, tabla);
        app.agregar(r);
        long ok = resultados.stream().filter(x -> "Guardada".equals(x.get("estado"))).count();
        int n = resultados.size();
        JPanel s = tarjeta("Resumen del procesamiento");
        poner(s, etiqueta("Recibidos: " + (n == 0 ? "—" : n) + "     Procesados: " + (n == 0 ? "—" : ok) + "     Rechazados: "
                + (n == 0 ? "—" : n - ok), 14, true, Tema.acento()));
        app.agregar(s);
    }
}
