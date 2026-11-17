package mx.uaeh.syllabus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import javax.swing.JLabel;
import javax.swing.JPanel;

import mx.uaeh.syllabus.documentos.Documento;
import mx.uaeh.syllabus.logica.Calculos;
import mx.uaeh.syllabus.logica.Reportes;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Estudiante;
import mx.uaeh.syllabus.modelo.ResumenCurso;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/** Consultas y kárdex: búsqueda y filtros, promedios y riesgo, estadísticas por curso y expediente del alumno. */
public class VistaConsultas extends Vista {
    private static final Color VERDE = new Color(0x247457);
    private static final Color ROJO = new Color(0xa32e34);
    private static final Color AMBAR = new Color(0xa26b34);

    public VistaConsultas(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        String cuenta = (String) app.memoria("kardex_de");
        if (cuenta != null) {
            boolean docente = "Docente".equals(app.getRol());
            app.encabezado(rutaDe("Consultas") + "  /  " + (docente ? "Alumno" : "Kárdex"),
                    docente ? "Calificaciones del alumno en tus materias" : "Expediente del estudiante",
                    docente ? "Solo se muestran las materias que impartes."
                            : "Datos, desglose por evaluación, resumen final y reportes del alumno (kárdex y boleta).");
            ResumenEstudiante r = app.resumenesVisibles().stream().filter(x -> x.numCuenta().equals(cuenta)).findFirst().orElse(null);
            kardexCompleto(cuenta, r, docente);
            app.agregar(linea(Tema.FONDO, boton("←   Volver a consultas", () -> {
                app.guardarEnMemoria("kardex_de", null);
                app.refrescar();
            }, false)));
            return;
        }
        app.encabezado(app.getRol() + "  /  Consultas", "Consultas y kárdex",
                "Busca por estudiante, grupo o asignatura; ordena por promedio y revisa el riesgo académico.");
        int activa = pestanas(app.getVista(), "Estudiantes y kárdex", "Promedios y riesgo académico", "Estadísticas por curso");
        List<ResumenEstudiante> resumenes = app.resumenesVisibles();
        switch (activa) {
            case 0 -> estudiantes(resumenes);
            case 1 -> riesgo(resumenes);
            default -> estadisticas(resumenes);
        }
    }

    private void estudiantes(List<ResumenEstudiante> resumenes) {
        List<Map<String, Object>> filasCurso = Reportes.filasPorCurso(resumenes);
        Map<String, String> f = app.estado("filtro:consultas", Map.of("estudiante", "", "grupo", "Todos", "asignatura", "Todas",
                "estado", "todos", "minima", "", "maxima", ""));
        Map<String, String> riesgoPor = new HashMap<>();
        resumenes.forEach(r -> riesgoPor.put(r.numCuenta(), r.riesgo()));
        JPanel lista = tarjeta("Estudiantes por asignatura");
        Tabla tabla = new Tabla(new String[][]{{"num_cuenta", "Núm. de cuenta"}, {"apellidos", "Apellidos"}, {"nombre", "Nombre"},
                {"grupo", "Grupo"}, {"asignatura", "Asignatura"}, {"parcial_1", "P1"}, {"parcial_2", "P2"}, {"parcial_3", "P3"},
                {"promedio", "Promedio"}, {"estatus", "Estado"}}, 10);
        tabla.proporciones(List.of(2, 2, 2, 1, 3, 1, 1, 1, 2, 2));
        Runnable aplicar = () -> {
            String texto = f.get("estudiante").strip().toLowerCase();
            Predicate<Map<String, Object>> estado = Calculos.filtroEstado(f.get("estado"));
            Predicate<Map<String, Object>> rango = Calculos.filtroRango(aNumero(f.get("minima")), aNumero(f.get("maxima")));
            tabla.cargar(filasCurso.stream().filter(x -> (texto.isEmpty() || ((String) x.get("num_cuenta")).contains(texto)
                            || (x.get("apellidos") + " " + x.get("nombre")).toLowerCase().contains(texto))
                    && ("Todos".equals(f.get("grupo")) || f.get("grupo").equals(x.get("grupo")))
                    && ("Todas".equals(f.get("asignatura")) || f.get("asignatura").equals(x.get("asignatura")))
                    && estado.test(conRiesgo(x, riesgoPor)) && rango.test(x)).toList());
        };
        Set<String> grupos = new TreeSet<>();
        Set<String> asignaturas = new TreeSet<>();
        filasCurso.forEach(x -> {
            grupos.add((String) x.get("grupo"));
            asignaturas.add((String) x.get("asignatura"));
        });
        JPanel t = tarjeta("Buscar y filtrar");
        javax.swing.JCheckBox colorear = Entradas.casilla("Colorear estados", app.opcion("colorear"), Tema.BLANCO);
        colorear.addActionListener(e -> {
            app.setOpcion("colorear", colorear.isSelected());
            tabla.repaint();
        });
        poner(t, linea(null, texto(f, "estudiante", "Estudiante (cuenta o nombre)", 16, Tema.BLANCO, aplicar).panel(),
                opciones(f, "grupo", "Grupo", con("Todos", grupos), Tema.BLANCO, aplicar).panel(),
                opciones(f, "asignatura", "Asignatura", con("Todas", asignaturas), Tema.BLANCO, aplicar).panel(),
                opciones(f, "estado", "Estado", List.of("todos", "aprobado", "reprobado", "pendiente", "riesgo"), Tema.BLANCO, aplicar).panel(),
                texto(f, "minima", "Mínima", 4, Tema.BLANCO, aplicar).panel(), texto(f, "maxima", "Máxima", 4, Tema.BLANCO, aplicar).panel()), 6);
        poner(t, colorear);
        app.agregar(t);
        tabla.alSeleccionar(x -> {
            app.guardarEnMemoria("kardex_de", x.get("num_cuenta"));
            javax.swing.SwingUtilities.invokeLater(app::refrescar);
        });
        aplicar.run();
        poner(lista, tabla, 8);
        poner(lista, etiqueta("Haz clic en un estudiante para abrir su kárdex.", 12, false, Tema.SUAVE));
        app.agregar(lista);
    }

    private static Map<String, Object> conRiesgo(Map<String, Object> x, Map<String, String> riesgoPor) {
        Map<String, Object> m = new HashMap<>(x);
        m.put("riesgo", riesgoPor.get((String) x.get("num_cuenta")));
        return m;
    }

    private static Double aNumero(String s) {
        try {
            return Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> con(String primero, Set<String> resto) {
        List<String> l = new ArrayList<>(List.of(primero));
        l.addAll(resto);
        return l;
    }

    private void riesgo(List<ResumenEstudiante> resumenes) {
        List<Curso> cursos = app.cursosVisibles();
        JPanel prom = tarjeta("Promedio del curso");
        Map<String, String> estado = app.estado("filtro:promedio_curso", Map.of("curso", ""));
        List<String> ops = cursos.stream().map(c -> c.id() + " · " + c.asignatura()).toList();
        JLabel resultado = etiqueta("", 17, true, Tema.acento());
        Runnable consultar = () -> {
            String cid = primero(estado.get("curso"));
            List<Double> brutos = new ArrayList<>();
            resumenes.forEach(r -> r.cursos().stream().filter(c -> c.cursoId().equals(cid) && c.bruto() != null).forEach(c -> brutos.add(c.bruto())));
            Map<String, Object> est = Calculos.estadisticas(brutos);
            resultado.setText("Promedio del curso: " + Componentes.fmt(est.get("promedio")) + "   ·   Máx " + Componentes.fmt(est.get("maximo"))
                    + "   ·   Mín " + Componentes.fmt(est.get("minimo")) + "   ·   Aprobación " + Componentes.fmt(est.get("aprobacion")) + " %");
        };
        poner(prom, opciones(estado, "curso", "Curso", ops, Tema.BLANCO, consultar).panel(), 10);
        poner(prom, resultado);
        consultar.run();
        app.agregar(prom);
        List<Map<String, Object>> filas = resumenes.stream().map(Reportes::filaEstudiante).toList();
        JPanel t = tarjeta("Promedio general por estudiante");
        Tabla tabla = new Tabla(new String[][]{{"num_cuenta", "Número de cuenta"}, {"apellidos", "Apellidos"}, {"nombre", "Nombre"},
                {"grupo", "Grupo"}, {"promedio", "Promedio"}, {"estatus", "Estado"}, {"riesgo", "Riesgo"}}, 8);
        tabla.ordenarPor("promedio", true);
        tabla.cargar(filas);
        poner(t, tabla);
        app.agregar(t);
        JPanel r = tarjeta("Estudiantes en riesgo académico",
                "ALTO: algún curso reprobado o promedio general < 7.0 · MEDIO: promedio general entre 7.0 y 7.5.");
        Tabla tr = new Tabla(new String[][]{{"num_cuenta", "Número de cuenta"}, {"apellidos", "Apellidos"}, {"nombre", "Nombre"},
                {"grupo", "Grupo"}, {"promedio", "Promedio"}, {"riesgo", "Riesgo"}, {"motivo", "Motivo"}}, 8);
        tr.proporciones(List.of(2, 2, 2, 1, 1, 1, 3));
        tr.cargar(filas.stream().filter(x -> "ALTO".equals(x.get("riesgo")) || "MEDIO".equals(x.get("riesgo"))).toList());
        poner(r, tr);
        app.agregar(r);
    }

    private void estadisticas(List<ResumenEstudiante> resumenes) {
        Set<String> visibles = new HashSet<>();
        app.cursosVisibles().forEach(c -> visibles.add(c.id()));
        List<Map<String, Object>> filas = Reportes.estadisticasCursos(g, resumenes).stream().filter(x -> visibles.contains(x.get("curso_id"))).toList();
        List<Double> cerrados = new ArrayList<>();
        resumenes.forEach(r -> r.cursos().stream().map(ResumenCurso::bruto).filter(Objects::nonNull).forEach(cerrados::add));
        Map<String, Object> est = Calculos.estadisticas(cerrados);
        app.agregar(Componentes.mosaicos(new Object[][]{{"Promedio de cursos", est.get("promedio")}, {"Calificación máxima", est.get("maximo")},
                {"Calificación mínima", est.get("minimo")}, {"Aprobación (%)", est.get("aprobacion")}}));
        JPanel t = tarjeta("Resultados agrupados por asignatura y grupo");
        Tabla tabla = new Tabla(new String[][]{{"curso_id", "Curso"}, {"asignatura", "Asignatura"}, {"grupo", "Grupo"}, {"docente", "Docente"},
                {"inscritos", "Estudiantes"}, {"promedio", "Promedio"}, {"maximo", "Máx."}, {"minimo", "Mín."}, {"aprobacion", "% aprobado"}},
                10, List.of());
        tabla.proporciones(List.of(1, 3, 1, 2, 1, 1, 1, 1, 1));
        tabla.cargar(filas);
        poner(t, tabla);
        app.agregar(t);
    }

    /**
     * Expediente del estudiante: datos, desglose por evaluación (desplegable por materia), resumen final y reportes.
     * Con soloMaterias (docente) se muestran únicamente sus materias, sin promedio general ni riesgo.
     */
    private void kardexCompleto(String cuenta, ResumenEstudiante resumen, boolean soloMaterias) {
        ResumenEstudiante r = resumen != null ? resumen : g.getEstudiantes().containsKey(cuenta) ? g.resumenEstudiante(cuenta) : null;
        Estudiante e = g.getEstudiantes().get(cuenta);
        if (r == null || e == null) {
            JPanel t = tarjeta(null);
            poner(t, etiqueta("Estudiante sin información.", 14, false, Tema.SUAVE));
            app.agregar(t);
            return;
        }
        Map<String, Color> tonos = Map.of("APROBADO", VERDE, "REPROBADO", ROJO);
        JPanel datos = tarjeta(null);
        JPanel rej = new JPanel(new GridLayout(1, 4, 14, 0));
        rej.setOpaque(false);
        rej.add(dato("Número de cuenta", cuenta, null, null));
        rej.add(dato("Estudiante", e.apellidos() + " " + e.nombre(), null, null));
        rej.add(dato("Grupo", e.grupo(), null, null));
        rej.add(soloMaterias ? dato("Materias contigo", String.valueOf(r.cursos().size()), null, null)
                : dato("Estado académico", Componentes.legible(r.estatus()), tonos.getOrDefault(r.estatus(), AMBAR), null));
        poner(datos, rej);
        app.agregar(datos);
        List<Object[]> mosaicos = new ArrayList<>();
        if (!soloMaterias) {
            mosaicos.add(new Object[]{"Promedio general", Calculos.mostrar(r.general())});
        }
        mosaicos.add(new Object[]{"Aprobadas", VistaAlumno.cuenta(r.cursos(), "APROBADO")});
        mosaicos.add(new Object[]{"Reprobadas", VistaAlumno.cuenta(r.cursos(), "REPROBADO")});
        mosaicos.add(new Object[]{"Pendientes", r.cursos().stream().filter(c -> c.bruto() == null).count()});
        app.agregar(Componentes.mosaicos(mosaicos.toArray(new Object[0][])));
        JPanel d = tarjeta("Desglose por evaluación", "Abre cada materia para ver sus parciales y evaluaciones con su peso.");
        Set<String> abiertas = app.memoria("kardex_abiertas", HashSet::new);
        for (ResumenCurso c : r.cursos()) {
            String clave = cuenta + "|" + c.cursoId();
            boolean abierta = abiertas.contains(clave);
            String estado = c.bruto() == null ? "Pendiente de cierre" : "APROBADO".equals(c.estatus()) ? "Aprobado" : "Reprobado";
            Redondo caja = Redondo.vertical(10, Tema.BLANCO, Tema.BORDE, 10, 14);
            JPanel cab = new JPanel(new BorderLayout());
            cab.setOpaque(false);
            JPanel izq = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
            izq.setOpaque(false);
            izq.add(etiqueta(abierta ? "⌄" : "›", 16, true, Tema.acento()));
            izq.add(etiqueta(c.cursoId(), 13, true, Tema.acento()));
            izq.add(etiqueta(c.asignatura(), 15, true, null));
            cab.add(izq, BorderLayout.WEST);
            JPanel der = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
            der.setOpaque(false);
            der.add(etiqueta("Promedio: " + Componentes.fmt(Calculos.mostrar(c.bruto())) + "   ·   Final: "
                    + Componentes.fmt(Calculos.calificacionFinal(c.bruto())), 14, false, Tema.SUAVE));
            der.add(etiqueta(estado, 14, false, c.bruto() == null ? AMBAR : tonos.getOrDefault(c.estatus(), AMBAR)));
            cab.add(der, BorderLayout.EAST);
            cab.setMaximumSize(new Dimension(Integer.MAX_VALUE, cab.getPreferredSize().height));
            clicable(cab, () -> {
                if (!abiertas.remove(clave)) {
                    abiertas.add(clave);
                }
                app.refrescar();
            });
            poner(caja, cab);
            if (abierta) {
                List<Map<String, Object>> filas = VistaAlumno.desglose(this, cuenta, c.cursoId(), true);
                Tabla t = new Tabla(new String[][]{{"parcial", "Parcial"}, {"nota_parcial", "Nota del parcial"}, {"evaluacion", "Evaluación"},
                        {"calificacion", "Calificación"}, {"estado", "Estado"}}, Math.max(2, Math.min(10, filas.size())), List.of());
                t.cargar(filas);
                caja.add(javax.swing.Box.createVerticalStrut(10));
                poner(caja, t);
            }
            caja.setMaximumSize(new Dimension(Integer.MAX_VALUE, caja.getPreferredSize().height));
            poner(d, caja, 8);
        }
        if (r.cursos().isEmpty()) {
            poner(d, etiqueta("Sin materias inscritas.", 13, false, Tema.SUAVE));
        }
        app.agregar(d);
        if (soloMaterias) {
            return;
        }
        JPanel res = tarjeta("Resumen final");
        JPanel lineaRes = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        lineaRes.setOpaque(false);
        lineaRes.add(etiqueta("Promedio general: ", 15, false, null));
        lineaRes.add(etiqueta(Componentes.fmt(Calculos.mostrar(r.general())), 15, true, Tema.acento()));
        lineaRes.add(etiqueta("     ·     Riesgo académico: ", 15, false, null));
        lineaRes.add(etiqueta(Componentes.legible(r.riesgo() == null ? "PENDIENTE" : r.riesgo()), 15, true,
                "ALTO".equals(r.riesgo()) ? ROJO : "MEDIO".equals(r.riesgo()) ? AMBAR : VERDE));
        lineaRes.setMaximumSize(new Dimension(Integer.MAX_VALUE, lineaRes.getPreferredSize().height));
        poner(res, lineaRes, 6);
        poner(res, etiqueta(r.motivo() == null ? "" : r.motivo(), 12, false, Tema.SUAVE), 10);
        Documento kardex = app.documentos().kardex(cuenta);
        poner(res, botonesExportar(kardex, null, new Object[]{"Boleta PDF", (Runnable) () -> exportar(app.documentos().boleta(cuenta), "pdf")}));
        app.agregar(res);
    }

}
