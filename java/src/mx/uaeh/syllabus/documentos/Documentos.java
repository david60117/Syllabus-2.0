package mx.uaeh.syllabus.documentos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import mx.uaeh.syllabus.documentos.Documento.Columna;
import mx.uaeh.syllabus.documentos.Documento.Dato;
import mx.uaeh.syllabus.logica.Autenticacion;
import mx.uaeh.syllabus.logica.Calculos;
import mx.uaeh.syllabus.logica.GestorEscolar;
import mx.uaeh.syllabus.modelo.Cuenta;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Docente;
import mx.uaeh.syllabus.modelo.Estudiante;
import mx.uaeh.syllabus.modelo.Grupo;
import mx.uaeh.syllabus.modelo.ResumenCurso;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/**
 * Fábrica de reportes, como en un sistema de control escolar real.
 * Por alumno: boleta y kárdex · por materia: acta · por grupo: concentrado ·
 * control: aprovechamiento, riesgo académico, aprobados y reprobados, usuarios y contraseñas.
 */
public class Documentos {
    public static final Map<String, String> ESTADOS = Map.of("APROBADO", "Aprobado", "REPROBADO", "Reprobado", "PENDIENTE", "Pendiente");
    public static final String NOTA_REDONDEO = "Parciales y promedio con dos decimales. Calificación final asentada: si el promedio "
            + "es 7.0 o más se redondea al entero más próximo (.5 sube); si es menor a 7.0 no se redondea hacia arriba (6.9 queda en 6) "
            + "y la materia queda reprobada (examen extraordinario).";

    /** Tipo de reporte: título, alcance y roles que lo pueden emitir. */
    public record Tipo(String clave, String titulo, String alcance, List<String> roles) {
    }

    public static final Map<String, Tipo> CATALOGO = new LinkedHashMap<>();

    static {
        agregar("boleta", "Boleta de calificaciones", "alumno", "Administrador", "Alumno");
        agregar("kardex", "Kárdex académico", "alumno", "Administrador", "Alumno");
        agregar("acta", "Acta de calificaciones", "materia", "Administrador", "Docente");
        agregar("concentrado", "Concentrado de calificaciones por grupo", "grupo", "Administrador");
        agregar("aprovechamiento", "Aprovechamiento por grupo y asignatura", "grupo_opcional", "Administrador", "Docente");
        agregar("riesgo", "Alumnos en riesgo académico", "grupo_opcional", "Administrador");
        agregar("aprobacion", "Aprobados y reprobados", "materia_opcional", "Administrador", "Docente");
        agregar("credenciales", "Usuarios y contraseñas", "usuarios", "Administrador");
    }

    private static void agregar(String clave, String titulo, String alcance, String... roles) {
        CATALOGO.put(clave, new Tipo(clave, titulo, alcance, List.of(roles)));
    }

    public static String cicloActual() {
        LocalDateTime hoy = LocalDateTime.now();
        return (hoy.getMonthValue() <= 6 ? "Enero – Junio " : "Julio – Diciembre ") + hoy.getYear();
    }

    /** Datos del encabezado por omisión; se personalizan en Personalización y se guardan en base.json. */
    public static Map<String, String> institucionPorOmision() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("institucion", "Institución educativa");
        m.put("programa", "Licenciatura en Ingeniería de Software");
        m.put("ciclo", cicloActual());
        return m;
    }

    private final GestorEscolar gestor;
    private final Map<String, String> institucion;
    private final String emitidoPor;

    public Documentos(GestorEscolar gestor, Map<String, ?> institucion, String emitidoPor) {
        this.gestor = gestor;
        this.institucion = institucionPorOmision();
        if (institucion != null) {
            institucion.forEach((k, v) -> {
                if (v != null) {
                    this.institucion.put(k, String.valueOf(v));
                }
            });
        }
        this.emitidoPor = emitidoPor == null ? "" : emitidoPor;
    }

    // ---------------------------------------------------------------- utilidades
    private static BigDecimal m(Double x) {
        return Calculos.mostrar(x);
    }

    private static Map<String, Object> fila(Object... kv) {
        Map<String, Object> f = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            f.put((String) kv[i], kv[i + 1]);
        }
        return f;
    }

    private static List<Columna> columnas(String... kv) {
        List<Columna> c = new ArrayList<>();
        for (int i = 0; i < kv.length; i += 2) {
            c.add(new Columna(kv[i], kv[i + 1]));
        }
        return c;
    }

    private static List<Dato> datos(Object... kv) {
        List<Dato> d = new ArrayList<>();
        for (int i = 0; i < kv.length; i += 2) {
            d.add(new Dato((String) kv[i], kv[i + 1]));
        }
        return d;
    }

    private static String riesgo(String r) {
        if (r == null) {
            return "Pendiente";
        }
        return switch (r) {
            case "ALTO" -> "Alto";
            case "MEDIO" -> "Medio";
            case "SIN_RIESGO" -> "Sin riesgo";
            default -> r;
        };
    }

    private static String pct(BigDecimal aprobacion) {
        return aprobacion == null ? null : aprobacion.toPlainString() + " %";
    }

    private Documento documento(String tipo, List<Dato> campos, List<Columna> columnas, List<Map<String, Object>> filas,
                                List<Dato> resumen, List<String> firmas, String nota) {
        String titulo = CATALOGO.get(tipo).titulo();
        Documento.Encabezado enc = new Documento.Encabezado(institucion.get("institucion"), institucion.get("programa"),
                institucion.get("ciclo"), titulo, campos, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                emitidoPor);
        return new Documento(tipo, enc, columnas, filas, resumen, firmas, nota);
    }

    private String ponderacion(String cursoId) {
        String s = gestor.parcialesDe(cursoId).stream()
                .map(p -> "P" + p.numero() + " " + Calculos.normalizado(p.peso() * 100) + " %").collect(Collectors.joining(" / "));
        return s.isEmpty() ? "—" : s;
    }

    private String docente(String cursoId) {
        Docente d = gestor.docenteDe(cursoId);
        return d == null ? "Sin asignar" : d.nombre();
    }

    private static void parciales(Map<String, Object> f, List<Double> ps) {
        List<Double> p = new ArrayList<>(ps);
        while (p.size() < 3) {
            p.add(null);
        }
        f.put("p1", m(p.get(0)));
        f.put("p2", m(p.get(1)));
        f.put("p3", m(p.get(2)));
    }

    private static List<Dato> datosAlumno(ResumenEstudiante r) {
        return datos("Alumno", r.apellidos() + " " + r.nombre(), "Número de cuenta", r.numCuenta(), "Grupo", r.grupo());
    }

    private static List<Dato> resumenAlumno(ResumenEstudiante r) {
        List<ResumenCurso> cerrados = r.cursos().stream().filter(c -> c.bruto() != null).toList();
        long aprobadas = cerrados.stream().filter(c -> "APROBADO".equals(c.estatus())).count();
        return datos("Promedio general", m(r.general()), "Materias aprobadas", aprobadas, "Materias reprobadas",
                cerrados.size() - aprobadas, "Materias pendientes", r.pendientes(), "Riesgo académico", riesgo(r.riesgo()));
    }

    // ---------------------------------------------------------------- por alumno
    /** Calificaciones del alumno en el periodo: parciales y final de cada materia. */
    public Documento boleta(String numCuenta) {
        ResumenEstudiante r = gestor.resumenEstudiante(numCuenta);
        List<Map<String, Object>> filas = new ArrayList<>();
        for (ResumenCurso c : r.cursos()) {
            Map<String, Object> f = fila("clave", c.cursoId(), "asignatura", c.asignatura(), "docente", docente(c.cursoId()));
            parciales(f, c.parciales());
            f.put("promedio", m(c.bruto()));
            f.put("final", Calculos.calificacionFinal(c.bruto()));
            f.put("estado", ESTADOS.get(c.estatus()));
            filas.add(f);
        }
        return documento("boleta", datosAlumno(r), columnas("clave", "Clave", "asignatura", "Asignatura", "docente", "Docente",
                        "p1", "P1", "p2", "P2", "p3", "P3", "promedio", "Promedio", "final", "Final", "estado", "Estado"),
                filas, resumenAlumno(r), List.of(), NOTA_REDONDEO + " Las materias pendientes aún tienen evaluaciones sin capturar.");
    }

    /** Historial académico individual: solo las materias y calificaciones finales de ese alumno. */
    public Documento kardex(String numCuenta) {
        ResumenEstudiante r = gestor.resumenEstudiante(numCuenta);
        List<Map<String, Object>> filas = new ArrayList<>();
        int i = 1;
        for (ResumenCurso c : r.cursos()) {
            filas.add(fila("no", i++, "clave", c.cursoId(), "asignatura", c.asignatura(), "ciclo", institucion.get("ciclo"),
                    "tipo", "Ordinario", "promedio", m(c.bruto()), "final", Calculos.calificacionFinal(c.bruto()),
                    "estado", c.bruto() == null ? "En curso" : ESTADOS.get(c.estatus())));
        }
        long reprobadas = r.cursos().stream().filter(c -> c.bruto() != null && "REPROBADO".equals(c.estatus())).count();
        List<Dato> campos = new ArrayList<>(datosAlumno(r));
        campos.add(new Dato("Situación", reprobadas > 0 ? "Irregular" : "Regular"));
        List<Dato> resumen = new ArrayList<>(datos("Materias cursadas", r.cursos().size(), "Promedio de calificaciones finales",
                Calculos.promedioFinales(r.cursos().stream().map(ResumenCurso::bruto).toList())));
        resumenAlumno(r).stream().filter(d -> !d.etiqueta().equals("Promedio general")).forEach(resumen::add);
        return documento("kardex", campos, columnas("no", "No.", "clave", "Clave", "asignatura", "Asignatura", "ciclo", "Ciclo",
                        "tipo", "Tipo de evaluación", "promedio", "Promedio", "final", "Calificación final", "estado", "Estado"),
                filas, resumen, List.of("Control escolar"),
                NOTA_REDONDEO + " Documento informativo; no sustituye al certificado oficial de estudios.");
    }

    // ---------------------------------------------------------------- por materia
    /** Acta de calificaciones de una materia: todos sus alumnos con parciales, final y estado. */
    public Documento acta(String cursoId) {
        Curso c = gestor.getCursos().get(cursoId);
        List<Map<String, Object>> filas = new ArrayList<>();
        List<Double> brutos = new ArrayList<>();
        int i = 1;
        for (Estudiante e : gestor.alumnosDeCurso(cursoId)) {
            List<Double> ps = gestor.parcialesDe(cursoId).stream().map(p -> gestor.califParcial(e.numCuenta(), p.id())).toList();
            Double fin = gestor.promedioCurso(e.numCuenta(), cursoId);
            Map<String, Object> f = fila("no", i++, "num_cuenta", e.numCuenta(), "alumno", e.nombreCompleto());
            parciales(f, ps);
            f.put("promedio", m(fin));
            f.put("final", Calculos.calificacionFinal(fin));
            f.put("estado", ESTADOS.get(fin == null ? "PENDIENTE" : Calculos.evaluarEstatusCurso(fin)));
            filas.add(f);
            if (fin != null) {
                brutos.add(fin);
            }
        }
        Map<String, Object> est = Calculos.estadisticas(brutos);
        return documento("acta", datos("Asignatura", c.asignatura(), "Clave", c.id(), "Grupo", c.grupo(), "Docente", docente(cursoId),
                        "Ponderación", ponderacion(cursoId)),
                columnas("no", "No.", "num_cuenta", "Número de cuenta", "alumno", "Nombre del alumno", "p1", "P1", "p2", "P2",
                        "p3", "P3", "promedio", "Promedio", "final", "Final", "estado", "Estado"),
                filas, datos("Inscritos", filas.size(), "Aprobados", est.get("aprobados"), "Reprobados", est.get("reprobados"),
                        "Pendientes", filas.size() - (int) est.get("estudiantes"), "Promedio", est.get("promedio"),
                        "Aprobación", pct((BigDecimal) est.get("aprobacion"))),
                List.of("Docente: " + docente(cursoId), "Control escolar"), NOTA_REDONDEO);
    }

    // ---------------------------------------------------------------- por grupo
    /** Sábana del grupo: una columna por materia con la calificación final de cada alumno. */
    public Documento concentrado(String grupo, String orden) {
        List<Curso> cursos = gestor.cursosDeGrupo(grupo);
        List<ResumenEstudiante> resumenes = gestor.getEstudiantes().values().stream().filter(e -> e.grupo().equals(grupo))
                .map(e -> gestor.resumenEstudiante(e.numCuenta())).toList();
        List<Map<String, Object>> base = new ArrayList<>();
        for (ResumenEstudiante r : resumenes) {
            Map<String, Integer> finales = new LinkedHashMap<>();
            r.cursos().forEach(c -> finales.put(c.cursoId(), Calculos.calificacionFinal(c.bruto())));
            Map<String, Object> f = fila("num_cuenta", r.numCuenta(), "apellidos", r.apellidos(), "nombre", r.nombre());
            cursos.forEach(c -> f.put(c.id(), finales.get(c.id())));
            f.put("promedio", m(r.general()));
            f.put("estado", ESTADOS.get(r.estatus()));
            f.put("riesgo", riesgo(r.riesgo()));
            base.add(f);
        }
        base = "promedio".equals(orden) ? Calculos.ordenar(base, "promedio", true) : Calculos.ordenar(base);
        List<Map<String, Object>> filas = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> f : base) {
            Map<String, Object> n = fila("no", i++, "num_cuenta", f.get("num_cuenta"), "alumno", f.get("apellidos") + " " + f.get("nombre"));
            f.forEach((k, v) -> {
                if (!List.of("num_cuenta", "apellidos", "nombre").contains(k)) {
                    n.put(k, v);
                }
            });
            filas.add(n);
        }
        List<Columna> cols = columnas("no", "No.", "num_cuenta", "Número de cuenta", "alumno", "Nombre del alumno");
        cursos.forEach(c -> cols.add(new Columna(c.id(), c.id())));
        cols.addAll(columnas("promedio", "Promedio", "estado", "Estado", "riesgo", "Riesgo"));
        Map<String, Object> est = Calculos.estadisticas(resumenes.stream().map(ResumenEstudiante::general).filter(Objects::nonNull).toList());
        List<Dato> campos = new ArrayList<>(datos("Grupo", grupo));
        Grupo g = gestor.getGruposRegistro().get(grupo);
        if (g != null) {
            campos.addAll(datos("Semestre", g.semestre() + ".º", "Turno", g.turno()));
        }
        campos.add(new Dato("Orden", "promedio".equals(orden) ? "Por promedio (mayor a menor)" : "Por apellidos"));
        String claves = cursos.stream().map(c -> c.id() + ": " + c.asignatura()).collect(Collectors.joining("   "));
        return documento("concentrado", campos, cols, filas, datos("Alumnos", filas.size(), "Materias", cursos.size(),
                        "Promedio del grupo", est.get("promedio"), "Máxima", est.get("maximo"), "Mínima", est.get("minimo"),
                        "Aprobación", pct((BigDecimal) est.get("aprobacion"))), List.of(),
                "Materias — " + claves + ". Por materia se muestra la calificación final asentada (entera); el promedio usa "
                        + "los promedios con dos decimales de cada materia.");
    }

    // ---------------------------------------------------------------- control
    private static Comparator<Curso> porGrupoYAsignatura() {
        return Comparator.comparing(Curso::grupo).thenComparing(c -> Calculos.claveTexto(c.asignatura()));
    }

    /** Estadísticas por materia (promedio, máxima, mínima y aprobación) y totales generales. */
    public Documento aprovechamiento(List<Curso> cursos, String grupo) {
        List<Curso> lista = cursos.stream().filter(c -> grupo == null || c.grupo().equals(grupo)).sorted(porGrupoYAsignatura()).toList();
        List<Map<String, Object>> filas = new ArrayList<>();
        List<Double> todos = new ArrayList<>();
        for (Curso c : lista) {
            List<Estudiante> alumnos = gestor.alumnosDeCurso(c.id());
            List<Double> finales = alumnos.stream().map(e -> gestor.promedioCurso(e.numCuenta(), c.id())).filter(Objects::nonNull).toList();
            todos.addAll(finales);
            Map<String, Object> est = Calculos.estadisticas(finales);
            filas.add(fila("grupo", c.grupo(), "clave", c.id(), "asignatura", c.asignatura(), "docente", docente(c.id()),
                    "inscritos", alumnos.size(), "promedio", est.get("promedio"), "maximo", est.get("maximo"), "minimo", est.get("minimo"),
                    "aprobados", est.get("aprobados"), "reprobados", est.get("reprobados"), "aprobacion", est.get("aprobacion")));
        }
        Map<String, Object> est = Calculos.estadisticas(todos);
        return documento("aprovechamiento", datos("Grupo", grupo == null ? "Todos" : grupo),
                columnas("grupo", "Grupo", "clave", "Clave", "asignatura", "Asignatura", "docente", "Docente", "inscritos", "Insc.",
                        "promedio", "Prom.", "maximo", "Máx.", "minimo", "Mín.", "aprobados", "Aprob.", "reprobados", "Reprob.",
                        "aprobacion", "% aprob."),
                filas, datos("Materias", filas.size(), "Calificaciones finales", est.get("estudiantes"), "Promedio general",
                        est.get("promedio"), "Máxima", est.get("maximo"), "Mínima", est.get("minimo"),
                        "Aprobación", pct((BigDecimal) est.get("aprobacion"))), List.of(), "");
    }

    public Documento riesgoAcademico(String grupo) {
        List<ResumenEstudiante> resumenes = gestor.getEstudiantes().values().stream()
                .filter(e -> grupo == null || e.grupo().equals(grupo)).map(e -> gestor.resumenEstudiante(e.numCuenta())).toList();
        List<Map<String, Object>> base = new ArrayList<>();
        for (ResumenEstudiante r : resumenes) {
            if (!"ALTO".equals(r.riesgo()) && !"MEDIO".equals(r.riesgo())) {
                continue;
            }
            String reprobadas = r.cursos().stream().filter(c -> c.bruto() != null && "REPROBADO".equals(c.estatus()))
                    .map(ResumenCurso::cursoId).collect(Collectors.joining(", "));
            base.add(fila("num_cuenta", r.numCuenta(), "apellidos", r.apellidos(), "nombre", r.nombre(), "grupo", r.grupo(),
                    "promedio", m(r.general()), "riesgo", riesgo(r.riesgo()), "motivo", r.motivo(),
                    "reprobadas", reprobadas.isEmpty() ? "—" : reprobadas));
        }
        List<Map<String, Object>> ordenadas = new ArrayList<>(Calculos.ordenar(base));
        ordenadas.sort(Comparator.comparing((Map<String, Object> f) -> (String) f.get("grupo")).thenComparing(f -> !"Alto".equals(f.get("riesgo"))));
        List<Map<String, Object>> filas = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> f : ordenadas) {
            filas.add(fila("no", i++, "num_cuenta", f.get("num_cuenta"), "alumno", f.get("apellidos") + " " + f.get("nombre"),
                    "grupo", f.get("grupo"), "promedio", f.get("promedio"), "riesgo", f.get("riesgo"), "motivo", f.get("motivo"),
                    "reprobadas", f.get("reprobadas")));
        }
        return documento("riesgo", datos("Grupo", grupo == null ? "Todos" : grupo),
                columnas("no", "No.", "num_cuenta", "Número de cuenta", "alumno", "Nombre del alumno", "grupo", "Grupo",
                        "promedio", "Promedio", "riesgo", "Riesgo", "motivo", "Motivo", "reprobadas", "Materias reprobadas"),
                filas, datos("Alumnos evaluados", resumenes.size(), "Riesgo alto", filas.stream().filter(f -> "Alto".equals(f.get("riesgo"))).count(),
                        "Riesgo medio", filas.stream().filter(f -> "Medio".equals(f.get("riesgo"))).count()), List.of(),
                "Alto: alguna materia reprobada o promedio general menor a 7.0. Medio: promedio general entre 7.0 y 7.5.");
    }

    public Documento aprobacion(List<Curso> cursos, String cursoId, String estado) {
        List<Curso> lista = cursos.stream().filter(c -> cursoId == null || c.id().equals(cursoId)).toList();
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Curso c : lista.stream().sorted(porGrupoYAsignatura()).toList()) {
            for (Estudiante e : gestor.alumnosDeCurso(c.id())) {
                Double fin = gestor.promedioCurso(e.numCuenta(), c.id());
                String est = ESTADOS.get(fin == null ? "PENDIENTE" : Calculos.evaluarEstatusCurso(fin));
                if ("Todos".equals(estado) || est.equals(estado)) {
                    filas.add(fila("grupo", c.grupo(), "asignatura", c.asignatura(), "num_cuenta", e.numCuenta(),
                            "alumno", e.nombreCompleto(), "promedio", m(fin), "final", Calculos.calificacionFinal(fin), "estado", est));
                }
            }
        }
        List<Dato> resumen = new ArrayList<>(datos("Registros", filas.size()));
        for (String e : List.of("Aprobado", "Reprobado", "Pendiente")) {
            resumen.add(new Dato(e, filas.stream().filter(f -> e.equals(f.get("estado"))).count()));
        }
        String materia = lista.stream().filter(c -> c.id().equals(cursoId)).map(c -> c.id() + " · " + c.asignatura() + " (" + c.grupo() + ")")
                .findFirst().orElse("Todas");
        return documento("aprobacion", datos("Materia", materia, "Estado", estado),
                columnas("grupo", "Grupo", "asignatura", "Asignatura", "num_cuenta", "Número de cuenta", "alumno", "Nombre del alumno",
                        "promedio", "Promedio", "final", "Final", "estado", "Estado"), filas, resumen, List.of(), "");
    }

    // ---------------------------------------------------------------- cuentas de acceso
    /**
     * Usuarios y contraseñas por grupo (alumnos), de docentes o de todos. Solo se muestra la contraseña inicial;
     * las que cambió cada usuario están cifradas y aparecen como personalizadas.
     */
    public Documento credenciales(List<Cuenta> usuarios, String filtro) {
        List<Map<String, Object>> base = new ArrayList<>();
        for (Cuenta u : usuarios) {
            String grupo = null;
            String detalle;
            if ("Alumno".equals(u.rol())) {
                Estudiante e = gestor.getEstudiantes().get(u.referencia());
                grupo = e == null ? "—" : e.grupo();
                detalle = grupo;
            } else if ("Docente".equals(u.rol())) {
                String ids = gestor.cursosDeDocente(u.referencia()).stream().map(Curso::id).collect(Collectors.joining(", "));
                detalle = ids.isEmpty() ? "Sin materias" : ids;
            } else {
                detalle = "Control escolar";
            }
            if ("Docentes".equals(filtro) && !"Docente".equals(u.rol()) || filtro.startsWith("Grupo ") && !filtro.substring(6).equals(grupo)) {
                continue;
            }
            String visible = Autenticacion.contrasenaVisible(u);
            base.add(fila("usuario", u.usuario(), "nombre", u.nombre(), "rol", u.rol(), "detalle", detalle,
                    "contrasena", visible == null ? "Personalizada" : visible, "estado", visible == null ? "Activa" : "Contraseña inicial"));
        }
        Map<String, Integer> orden = Map.of("Administrador", 0, "Docente", 1, "Alumno", 2);
        base.sort(Comparator.comparing((Map<String, Object> f) -> orden.get((String) f.get("rol")))
                .thenComparing(f -> "Alumno".equals(f.get("rol")) ? (String) f.get("detalle") : "")
                .thenComparing(f -> Calculos.claveTexto((String) f.get("nombre"))));
        List<Map<String, Object>> filas = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> f : base) {
            Map<String, Object> n = fila("no", i++);
            n.putAll(f);
            filas.add(n);
        }
        long iniciales = filas.stream().filter(f -> "Contraseña inicial".equals(f.get("estado"))).count();
        return documento("credenciales", datos("Usuarios", filtro),
                columnas("no", "No.", "usuario", "Usuario", "nombre", "Nombre", "rol", "Rol", "detalle", "Grupo / materias",
                        "contrasena", "Contraseña", "estado", "Estado"),
                filas, datos("Cuentas", filas.size(), "Con contraseña inicial", iniciales, "Personalizadas", filas.size() - iniciales),
                List.of("Control escolar"), "CONFIDENCIAL. Entregar a cada usuario solo su renglón. La contraseña inicial se debe "
                        + "cambiar al primer ingreso; las contraseñas personalizadas están cifradas y no se pueden consultar "
                        + "(se restablecen en Usuarios).");
    }

    // ---------------------------------------------------------------- exportación
    /** El documento como renglones de CSV: encabezado, tabla, resumen y firmas (lo que se ve impreso). */
    public static List<List<Object>> aTabla(Documento doc) {
        Documento.Encabezado enc = doc.encabezado();
        List<List<Object>> r = new ArrayList<>();
        r.add(List.of(enc.institucion()));
        r.add(List.of(enc.programa()));
        r.add(List.of(enc.titulo().toUpperCase()));
        r.add(List.of("Ciclo escolar", enc.ciclo()));
        enc.campos().forEach(d -> r.add(par(d.etiqueta(), d.valor())));
        r.add(List.of("Fecha de emisión", enc.emision()));
        r.add(List.of("Emitido por", enc.emitidoPor()));
        r.add(List.of());
        r.add(doc.columnas().stream().map(c -> (Object) c.titulo()).toList());
        for (Map<String, Object> f : doc.filas()) {
            List<Object> renglon = new ArrayList<>();
            doc.columnas().forEach(c -> renglon.add(f.get(c.clave())));
            r.add(renglon);
        }
        r.add(List.of());
        r.add(List.of("RESUMEN"));
        doc.resumen().forEach(d -> r.add(par(d.etiqueta(), d.valor())));
        if (!doc.firmas().isEmpty()) {
            r.add(List.of());
            doc.firmas().forEach(f -> r.add(List.of(f, "_______________________________")));
        }
        if (!doc.nota().isEmpty()) {
            r.add(List.of());
            r.add(List.of(doc.nota()));
        }
        return r;
    }

    private static List<Object> par(String a, Object b) {
        List<Object> l = new ArrayList<>();
        l.add(a);
        l.add(b);
        return l;
    }

    private static String llave(String s) {
        return Calculos.claveTexto(s).replace(" ", "_");
    }

    public static Map<String, Object> aJson(Documento doc) {
        Documento.Encabezado enc = doc.encabezado();
        Map<String, Object> e = fila("institucion", enc.institucion(), "programa", enc.programa(), "reporte", enc.titulo(),
                "ciclo_escolar", enc.ciclo());
        enc.campos().forEach(d -> e.put(llave(d.etiqueta()), d.valor()));
        e.put("fecha_emision", enc.emision());
        e.put("emitido_por", enc.emitidoPor());
        Map<String, Object> resumen = new LinkedHashMap<>();
        doc.resumen().forEach(d -> resumen.put(llave(d.etiqueta()), d.valor()));
        return fila("encabezado", e, "columnas", doc.columnas().stream().map(c -> fila("clave", c.clave(), "titulo", c.titulo())).toList(),
                "registros", doc.filas(), "resumen", resumen, "firmas", doc.firmas(), "nota", doc.nota());
    }

    public static String nombreArchivo(Documento doc) {
        Documento.Encabezado enc = doc.encabezado();
        Object sufijo = enc.campo("Número de cuenta");
        if (sufijo == null) {
            sufijo = enc.campo("Clave");
        }
        if (sufijo == null) {
            sufijo = enc.campo("Grupo");
        }
        String n = doc.tipo() + "_" + (sufijo == null ? "" : sufijo);
        while (n.endsWith("_")) {
            n = n.substring(0, n.length() - 1);
        }
        return n.replace(" ", "_");
    }
}
