package mx.uaeh.syllabus.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import mx.uaeh.syllabus.documentos.Documento;
import mx.uaeh.syllabus.logica.Calculos;
import mx.uaeh.syllabus.modelo.Docente;
import mx.uaeh.syllabus.modelo.Evaluacion;
import mx.uaeh.syllabus.modelo.Parcial;
import mx.uaeh.syllabus.modelo.ResumenCurso;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/** Vistas del alumno: inicio, desglose de calificaciones y kárdex (solo lectura). */
public final class VistaAlumno {
    private VistaAlumno() {
    }

    static long cuenta(List<ResumenCurso> cursos, String estatus) {
        return cursos.stream().filter(c -> c.bruto() != null && estatus.equals(c.estatus())).count();
    }

    static Map<String, Object> fila(Object... kv) {
        Map<String, Object> f = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            f.put((String) kv[i], kv[i + 1]);
        }
        return f;
    }

    /** Banda de riesgo con el color de su nivel. */
    static JPanel bandaRiesgo(ResumenEstudiante r) {
        Color fondo = "ALTO".equals(r.riesgo()) ? Tema.ROJO_SUAVE : "MEDIO".equals(r.riesgo()) ? Tema.AMBAR_SUAVE : Tema.VERDE_SUAVE;
        Color color = "ALTO".equals(r.riesgo()) ? new Color(0xa32e34) : "MEDIO".equals(r.riesgo()) ? new Color(0xa26b34) : new Color(0x247457);
        Redondo banda = Redondo.vertical(11, fondo, null, 14, 18);
        banda.add(Componentes.etiqueta("Riesgo académico: " + Componentes.legible(r.riesgo() == null ? "PENDIENTE" : r.riesgo()), 16, true, color));
        banda.add(Componentes.etiqueta(r.motivo(), 12, false, color));
        banda.setMaximumSize(new Dimension(Integer.MAX_VALUE, banda.getPreferredSize().height));
        return banda;
    }

    static void inicio(Vista v) {
        ResumenEstudiante r = v.g.resumenEstudiante(v.app.getUsuario());
        v.app.agregar(Componentes.mosaicos(new Object[][]{{"Promedio general", Calculos.mostrar(r.general())},
                {"Aprobadas", cuenta(r.cursos(), "APROBADO")}, {"Reprobadas", cuenta(r.cursos(), "REPROBADO")}, {"Pendientes", r.pendientes()}}));
        v.app.agregar(bandaRiesgo(r));
        JPanel t = Vista.tarjeta("Mis materias");
        Tabla tabla = new Tabla(new String[][]{{"asignatura", "Asignatura"}, {"docente", "Docente"}, {"parcial_1", "P1"}, {"parcial_2", "P2"},
                {"parcial_3", "P3"}, {"promedio", "Promedio"}, {"final", "Final"}, {"estatus", "Estado"}},
                Math.max(3, Math.min(8, r.cursos().size())), List.of());
        tabla.proporciones(List.of(3, 2, 1, 1, 1, 1, 1, 1));
        List<Map<String, Object>> filas = new ArrayList<>();
        for (ResumenCurso c : r.cursos()) {
            Docente d = v.g.docenteDe(c.cursoId());
            List<Double> p = new ArrayList<>(c.parciales());
            while (p.size() < 3) {
                p.add(null);
            }
            filas.add(fila("asignatura", c.asignatura(), "docente", d == null ? "Por asignar" : d.nombre(), "parcial_1", Calculos.mostrar(p.get(0)),
                    "parcial_2", Calculos.mostrar(p.get(1)), "parcial_3", Calculos.mostrar(p.get(2)), "promedio", Calculos.mostrar(c.bruto()),
                    "final", Calculos.calificacionFinal(c.bruto()), "estatus", c.estatus()));
        }
        tabla.cargar(filas);
        Vista.poner(t, tabla);
        v.app.agregar(t);
        v.botonesPie(new String[][]{{"Ver desglose de calificaciones", "Mis calificaciones"}, {"Ver mi kárdex", "Kárdex"}});
        v.notaPie("Una materia se aprueba con promedio de 7.0 o más; la final se asienta entera (.5 sube) y un promedio menor a 7.0 "
                + "no se redondea hacia arriba. Riesgo ALTO: alguna materia reprobada o promedio menor a 7.0; MEDIO: promedio entre 7.0 y 7.5.");
    }

    /** Filas de parciales y evaluaciones de un curso para un alumno. */
    static List<Map<String, Object>> desglose(Vista v, String cuenta, String cursoId, boolean conEstado) {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Parcial p : v.g.parcialesDe(cursoId)) {
            Double notaP = v.g.califParcial(cuenta, p.id());
            String parcial = "Parcial " + p.numero() + " · " + Vista.pct(p.peso()) + " %";
            for (Evaluacion ev : v.g.evaluacionesDe(p.id())) {
                Double x = v.g.calificacion(cuenta, ev.id());
                Map<String, Object> f = fila("parcial", parcial, "nota_parcial", Calculos.mostrar(notaP),
                        "evaluacion", ev.nombre() + " · " + Vista.pct(ev.peso()) + " %",
                        "calificacion", x == null ? "Pendiente" : String.format(java.util.Locale.ROOT, "%.1f", x));
                if (conEstado) {
                    f.put("estado", x == null ? "Pendiente" : "Capturada");
                }
                filas.add(f);
            }
            if (v.g.evaluacionesDe(p.id()).isEmpty()) {
                filas.add(fila("parcial", parcial, "nota_parcial", null, "evaluacion", "Sin plan de evaluación", "calificacion", "Pendiente"));
            }
        }
        return filas;
    }

    /** Mis calificaciones: boleta descargable y desglose por materia, parcial y evaluación. */
    public static class Calificaciones extends Vista {
        public Calificaciones(App app) {
            super(app);
        }

        @Override
        public void dibujar() {
            app.encabezado("Alumno  /  Mis calificaciones", "Mis calificaciones", "Desglose por materia, parcial y evaluación. Solo lectura.");
            ResumenEstudiante r = g.resumenEstudiante(app.getUsuario());
            Documento boleta = app.documentos().boleta(app.getUsuario());
            JPanel t = tarjeta("Boleta de calificaciones", "Descarga tu boleta del periodo con encabezado institucional.");
            JPanel f = botonesExportar(boleta, null);
            ((Boton) f.getComponent(0)).setText("Descargar boleta PDF");
            poner(t, f);
            app.agregar(t);
            for (ResumenCurso c : r.cursos()) {
                String fin = c.bruto() == null ? "Pendiente" : Calculos.calificacionFinal(c.bruto()) + "  (promedio "
                        + Componentes.fmt(Calculos.mostrar(c.bruto())) + ") · " + Componentes.legible(c.estatus());
                JPanel tc = tarjeta(c.asignatura() + "   ·   Final: " + fin);
                List<Map<String, Object>> filas = desglose(this, app.getUsuario(), c.cursoId(), false);
                Tabla tabla = new Tabla(new String[][]{{"parcial", "Parcial"}, {"evaluacion", "Evaluación"}, {"calificacion", "Calificación"},
                        {"nota_parcial", "Nota del parcial"}}, Math.max(2, Math.min(10, filas.size())), List.of());
                tabla.cargar(filas);
                poner(tc, tabla);
                app.agregar(tc);
            }
            if (r.cursos().isEmpty()) {
                JPanel vacia = tarjeta(null);
                poner(vacia, etiqueta("No tienes materias inscritas.", 14, false, Tema.SUAVE));
                app.agregar(vacia);
            }
        }
    }

    /** Kárdex individual del alumno con su encabezado institucional. */
    public static class Kardex extends Vista {
        public Kardex(App app) {
            super(app);
        }

        @Override
        public void dibujar() {
            app.encabezado("Alumno  /  Kárdex", "Mi kárdex académico",
                    "Tu historial individual: materias cursadas, calificación final y situación académica.");
            Documento doc = app.documentos().kardex(app.getUsuario());
            app.agregar(botonesExportar(doc, Tema.FONDO,
                    new Object[]{"Boleta PDF", (Runnable) () -> exportar(app.documentos().boleta(app.getUsuario()), "pdf")}));
            app.agregar(vistaDocumento(doc));
        }
    }
}
