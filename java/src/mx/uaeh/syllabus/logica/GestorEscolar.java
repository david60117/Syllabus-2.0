package mx.uaeh.syllabus.logica;

import static mx.uaeh.syllabus.logica.Validacion.campos;
import static mx.uaeh.syllabus.logica.Validacion.formato;
import static mx.uaeh.syllabus.logica.Validacion.texto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import mx.uaeh.syllabus.modelo.BusEventos;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Docente;
import mx.uaeh.syllabus.modelo.ErrorValidacion;
import mx.uaeh.syllabus.modelo.Estudiante;
import mx.uaeh.syllabus.modelo.Evaluacion;
import mx.uaeh.syllabus.modelo.Grupo;
import mx.uaeh.syllabus.modelo.Parcial;
import mx.uaeh.syllabus.modelo.RegistroError;
import mx.uaeh.syllabus.modelo.ResumenEstudiante;

/** Reglas de negocio: control escolar (grupos, alumnos, materias, docentes, planes y actas), carga masiva y errores. */
public class GestorEscolar extends Calculadora {
    public static final List<Double> PESOS_PARCIALES = List.of(0.3, 0.3, 0.4);
    public static final List<String> TURNOS = List.of("Matutino", "Vespertino");

    /** Resultado de guardar un acta: contadores y renglones rechazados. */
    public record ResultadoActa(int capturadas, int modificadas, int borradas, List<Map<String, Object>> errores) {
    }

    /** Cambio de un acta: valor null borra la calificación. */
    public record CambioActa(String numCuenta, String evaluacionId, Object valor) {
    }

    /** Evaluación de un plan: id null si es nueva. */
    public record Rubro(String id, String nombre, Object peso) {
    }

    private final BusEventos bus;
    private final Map<String, Grupo> gruposRegistro = new LinkedHashMap<>();
    private final Map<String, Docente> docentes = new LinkedHashMap<>();
    private final Set<Par> asignaciones = new LinkedHashSet<>(); // (docente, curso)
    private final List<RegistroError> errores = new ArrayList<>();

    public GestorEscolar() {
        this(new BusEventos());
    }

    public GestorEscolar(BusEventos bus) {
        this.bus = bus;
    }

    // ---------- utilidades ----------
    @SuppressWarnings("unchecked")
    public void registrarError(String tipo, Object registro, ErrorValidacion e) {
        Object caso = registro instanceof Map ? ((Map<String, Object>) registro).get("caso") : null;
        errores.add(new RegistroError(caso, tipo, e.getCodigo(), e.getDetalle(), registro));
        bus.emitir("error_validacion", "codigo", e.getCodigo(), "detalle", e.getDetalle(), "tipo", tipo);
    }

    public boolean procesar(String tipo, Object registro, Consumer<Object> accion) {
        try {
            accion.accept(registro);
            return true;
        } catch (ErrorValidacion e) {
            registrarError(tipo, registro, e);
            return false;
        }
    }

    // ---------- altas individuales (las usa la interfaz) ----------
    public Estudiante registrarEstudiante(Object registro) {
        Map<String, Object> r = campos(registro, "num_cuenta", "apellidos", "nombre", "grupo");
        String nc = formato(texto(r.get("num_cuenta"), "num_cuenta"), Validacion.RE_CUENTA, "num_cuenta");
        String apellidos = formato(texto(r.get("apellidos"), "apellidos"), Validacion.RE_NOMBRE, "apellidos");
        String nombre = formato(texto(r.get("nombre"), "nombre"), Validacion.RE_NOMBRE, "nombre");
        String grupo = formato(texto(r.get("grupo"), "grupo"), Validacion.RE_GRUPO, "grupo");
        if (estudiantes.containsKey(nc)) {
            throw new ErrorValidacion("ID_DUPLICADO", "el estudiante " + nc + " ya está registrado");
        }
        Estudiante est = new Estudiante(nc, apellidos, nombre, grupo);
        estudiantes.put(nc, est);
        bus.emitir("estudiante_registrado", "detalle", nc + " · " + est.nombreCompleto());
        return est;
    }

    public Curso registrarCurso(Object registro) {
        Map<String, Object> r = campos(registro, "id", "asignatura", "grupo");
        String id = formato(texto(r.get("id"), "id"), Validacion.RE_CURSO, "id de curso");
        String asignatura = texto(r.get("asignatura"), "asignatura");
        String grupo = formato(texto(r.get("grupo"), "grupo"), Validacion.RE_GRUPO, "grupo");
        if (cursos.containsKey(id)) {
            throw new ErrorValidacion("ID_DUPLICADO", "el curso " + id + " ya existe");
        }
        if (cursos.values().stream().anyMatch(c -> c.asignatura().equals(asignatura) && c.grupo().equals(grupo))) {
            throw new ErrorValidacion("ID_DUPLICADO", asignatura + " ya existe en el grupo " + grupo);
        }
        if (estudiantes.values().stream().noneMatch(e -> e.grupo().equals(grupo))) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "el grupo " + grupo + " no tiene estudiantes registrados");
        }
        Curso curso = new Curso(id, asignatura, grupo);
        cursos.put(id, curso);
        bus.emitir("curso_registrado", "detalle", id + " · " + asignatura + " · grupo " + grupo);
        return curso;
    }

    private Parcial validarParcial(Object registro) {
        Map<String, Object> r = campos(registro, "id", "curso_id", "numero", "peso");
        String id = formato(texto(r.get("id"), "id"), Validacion.RE_PARCIAL, "id de parcial");
        String cursoId = texto(r.get("curso_id"), "curso_id");
        double numero = Validacion.numero(r.get("numero"), "numero");
        if (numero != 1 && numero != 2 && numero != 3) {
            throw new ErrorValidacion("FORMATO_INVALIDO", "el número de parcial debe ser 1, 2 o 3: " + Validacion.pyNum(numero));
        }
        double peso = Validacion.peso(r.get("peso"), "peso");
        if (parciales.containsKey(id)) {
            throw new ErrorValidacion("ID_DUPLICADO", "el parcial " + id + " ya existe");
        }
        if (!cursos.containsKey(cursoId)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el curso " + cursoId);
        }
        if (parcialesDe(cursoId).stream().anyMatch(p -> p.numero() == numero)) {
            throw new ErrorValidacion("ID_DUPLICADO", "el curso " + cursoId + " ya tiene el parcial " + (int) numero);
        }
        return new Parcial(id, cursoId, (int) numero, peso);
    }

    /** Alta individual: permite completar los 3 parciales poco a poco sin pasar de 1.0. */
    public Parcial registrarParcial(Object registro) {
        Parcial p = validarParcial(registro);
        List<Parcial> actuales = parcialesDe(p.cursoId());
        double suma = actuales.stream().mapToDouble(Parcial::peso).sum() + p.peso();
        if (actuales.size() >= 3 || Calculos.pasaDeUno(suma)) {
            throw new ErrorValidacion("PORCENTAJES_INVALIDOS", "los parciales de " + p.cursoId() + " superarían 1.0 o 3 parciales");
        }
        parciales.put(p.id(), p);
        bus.emitir("parcial_registrado", "detalle", p.id() + " · " + p.cursoId() + " · peso " + p.peso());
        return p;
    }

    private Evaluacion validarEvaluacion(Object registro) {
        Map<String, Object> r = campos(registro, "id", "parcial_id", "nombre", "peso");
        String id = formato(texto(r.get("id"), "id"), Validacion.RE_EVALUACION, "id de evaluación");
        String parcialId = texto(r.get("parcial_id"), "parcial_id");
        String nombre = texto(r.get("nombre"), "nombre");
        double peso = Validacion.peso(r.get("peso"), "peso");
        if (evaluaciones.containsKey(id)) {
            throw new ErrorValidacion("ID_DUPLICADO", "la evaluación " + id + " ya existe");
        }
        if (!parciales.containsKey(parcialId)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el parcial " + parcialId);
        }
        if (evaluacionesDe(parcialId).stream().anyMatch(e -> e.nombre().equalsIgnoreCase(nombre))) {
            throw new ErrorValidacion("ID_DUPLICADO", nombre + " ya existe en el parcial " + parcialId);
        }
        return new Evaluacion(id, parcialId, nombre, peso);
    }

    public Evaluacion registrarEvaluacion(Object registro) {
        Evaluacion e = validarEvaluacion(registro);
        double suma = evaluacionesDe(e.parcialId()).stream().mapToDouble(Evaluacion::peso).sum() + e.peso();
        if (Calculos.pasaDeUno(suma)) {
            throw new ErrorValidacion("PORCENTAJES_INVALIDOS", "las evaluaciones de " + e.parcialId() + " superarían 1.0");
        }
        evaluaciones.put(e.id(), e);
        bus.emitir("evaluacion_registrada", "detalle", e.id() + " · " + e.nombre() + " · " + e.parcialId());
        return e;
    }

    public void inscribir(Object registro) {
        Map<String, Object> r = campos(registro, "num_cuenta", "curso_id");
        String nc = texto(r.get("num_cuenta"), "num_cuenta");
        String cid = texto(r.get("curso_id"), "curso_id");
        if (!estudiantes.containsKey(nc)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el estudiante " + nc);
        }
        if (!cursos.containsKey(cid)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el curso " + cid);
        }
        if (!cursos.get(cid).grupo().equals(estudiantes.get(nc).grupo())) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "el curso no pertenece al grupo del estudiante");
        }
        if (!inscripciones.add(new Par(nc, cid))) {
            throw new ErrorValidacion("INSCRIPCION_DUPLICADA", nc + " ya está inscrito en " + cid);
        }
        bus.emitir("inscripcion_registrada", "detalle", nc + " → " + cid);
    }

    private Par validarCalificacion(Map<String, Object> r) {
        String nc = texto(r.get("num_cuenta"), "num_cuenta");
        String eid = texto(r.get("evaluacion_id"), "evaluacion_id");
        if (!evaluaciones.containsKey(eid)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe la evaluación " + eid);
        }
        String cursoId = parciales.get(evaluaciones.get(eid).parcialId()).cursoId();
        if (!inscripciones.contains(new Par(nc, cursoId))) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", nc + " no está inscrito en " + cursoId);
        }
        return new Par(nc, eid);
    }

    public double capturarCalificacion(Object registro) {
        Map<String, Object> r = campos(registro, "num_cuenta", "evaluacion_id");
        double valor = Validacion.calificacion(r.get("valor"));
        Par clave = validarCalificacion(r);
        if (calificaciones.containsKey(clave)) {
            throw new ErrorValidacion("ID_DUPLICADO", clave.a() + " ya tiene calificación en " + clave.b());
        }
        calificaciones.put(clave, valor);
        bus.emitir("calificacion_capturada", "detalle", clave.a() + " · " + clave.b() + " · " + valor);
        return valor;
    }

    public double modificarCalificacion(Object registro) {
        Map<String, Object> r = campos(registro, "num_cuenta", "evaluacion_id");
        double valor = Validacion.calificacion(r.get("valor"));
        Par clave = validarCalificacion(r);
        if (!calificaciones.containsKey(clave)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", clave.a() + " no tiene calificación en " + clave.b());
        }
        calificaciones.put(clave, valor);
        bus.emitir("calificacion_modificada", "detalle", clave.a() + " · " + clave.b() + " · " + valor);
        return valor;
    }

    public ResumenEstudiante consultarKardex(String numCuenta) {
        if (!estudiantes.containsKey(numCuenta)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el estudiante " + numCuenta);
        }
        return resumenEstudiante(numCuenta);
    }

    public Docente registrarDocente(Object registro) {
        Map<String, Object> r = campos(registro, "id", "nombre");
        String id = formato(texto(r.get("id"), "id"), Validacion.RE_DOCENTE, "id de docente");
        String nombre = formato(texto(r.get("nombre"), "nombre"), Validacion.RE_NOMBRE, "nombre del docente");
        if (docentes.containsKey(id)) {
            throw new ErrorValidacion("ID_DUPLICADO", "el docente " + id + " ya existe");
        }
        Docente d = new Docente(id, nombre);
        docentes.put(id, d);
        bus.emitir("docente_registrado", "detalle", id + " · " + nombre);
        return d;
    }

    public void asignarDocente(Object registro) {
        Map<String, Object> r = campos(registro, "docente_id", "curso_id");
        String did = String.valueOf(r.get("docente_id"));
        String cid = String.valueOf(r.get("curso_id"));
        if (!docentes.containsKey(did)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el docente " + did);
        }
        if (!cursos.containsKey(cid)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el curso " + cid);
        }
        asignaciones.removeIf(a -> a.b().equals(cid)); // un docente por curso
        asignaciones.add(new Par(did, cid));
        bus.emitir("docente_asignado", "detalle", did + " → " + cid);
    }

    public List<Curso> cursosDeDocente(String docenteId) {
        return asignaciones.stream().filter(a -> a.a().equals(docenteId) && cursos.containsKey(a.b()))
                .map(a -> cursos.get(a.b())).sorted(Comparator.comparing(Curso::id)).toList();
    }

    public Docente docenteDe(String cursoId) {
        return asignaciones.stream().filter(a -> a.b().equals(cursoId) && docentes.containsKey(a.a()))
                .map(a -> docentes.get(a.a())).findFirst().orElse(null);
    }

    // ---------- control escolar: operaciones completas en un solo paso (las usa la interfaz) ----------
    /** Siguiente ID libre con el formato del sistema: C07, P19, EV43, D03. */
    static String siguiente(String prefijo, java.util.Collection<String> existentes) {
        int mayor = 0;
        for (String x : existentes) {
            String resto = x.startsWith(prefijo) ? x.substring(prefijo.length()) : "";
            if (!resto.isEmpty() && resto.chars().allMatch(Character::isDigit)) {
                mayor = Math.max(mayor, Integer.parseInt(resto));
            }
        }
        return String.format("%s%02d", prefijo, mayor + 1);
    }

    public String siguienteCuenta() {
        int mayor = estudiantes.keySet().stream().filter(n -> n.chars().allMatch(Character::isDigit))
                .mapToInt(Integer::parseInt).max().orElse(500000);
        return String.format("%06d", mayor + 1);
    }

    public String siguienteDocente() {
        return siguiente("D", docentes.keySet());
    }

    public List<String> grupos() {
        java.util.TreeSet<String> g = new java.util.TreeSet<>(gruposRegistro.keySet());
        estudiantes.values().forEach(e -> g.add(e.grupo()));
        cursos.values().forEach(c -> g.add(c.grupo()));
        return new ArrayList<>(g);
    }

    /** Los grupos que vienen en los datos quedan registrados: semestre del primer dígito (301 → 3), turno matutino. */
    private void sincronizarGrupos() {
        for (String clave : grupos()) {
            if (!gruposRegistro.containsKey(clave)) {
                char d = clave.isEmpty() ? '0' : clave.charAt(0);
                gruposRegistro.put(clave, new Grupo(clave, Character.isDigit(d) && d != '0' ? d - '0' : 1, TURNOS.get(0)));
            }
        }
    }

    private static int semestre(Object semestre) {
        try {
            int n = semestre instanceof Number x ? (int) x.doubleValue() : Integer.parseInt(String.valueOf(semestre).strip());
            if (n >= 1 && n <= 9) {
                return n;
            }
        } catch (NumberFormatException ignorado) {
            // se reporta abajo
        }
        throw new ErrorValidacion("FORMATO_INVALIDO", "el semestre debe ser un número del 1 al 9: " + semestre);
    }

    private static String turno(String turno) {
        if (!TURNOS.contains(turno)) {
            throw new ErrorValidacion("FORMATO_INVALIDO", "el turno debe ser " + String.join(" o ", TURNOS));
        }
        return turno;
    }

    public Grupo altaGrupo(String clave, Object semestre, String turno) {
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("clave", clave);
        campos(r, "clave");
        String c = formato(texto(clave, "clave"), Validacion.RE_GRUPO, "clave del grupo (3 dígitos)");
        if (grupos().contains(c)) {
            throw new ErrorValidacion("ID_DUPLICADO", "el grupo " + c + " ya existe");
        }
        Grupo g = new Grupo(c, semestre(semestre), turno(turno));
        gruposRegistro.put(c, g);
        bus.emitir("grupo_registrado", "detalle", c + " · " + g.semestre() + ".º semestre · " + g.turno());
        return g;
    }

    public Grupo editarGrupo(String clave, Object semestre, String turno) {
        if (!gruposRegistro.containsKey(clave)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el grupo " + clave);
        }
        Grupo g = new Grupo(clave, semestre(semestre), turno(turno));
        gruposRegistro.put(clave, g);
        bus.emitir("grupo_actualizado", "detalle", clave + " · " + g.semestre() + ".º semestre · " + g.turno());
        return g;
    }

    public void bajaGrupo(String clave) {
        if (!gruposRegistro.containsKey(clave)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el grupo " + clave);
        }
        if (estudiantes.values().stream().anyMatch(e -> e.grupo().equals(clave))
                || cursos.values().stream().anyMatch(c -> c.grupo().equals(clave))) {
            throw new ErrorValidacion("OPERACION_NO_PERMITIDA", "el grupo " + clave + " tiene alumnos o materias; primero dalos de baja");
        }
        gruposRegistro.remove(clave);
        bus.emitir("grupo_baja", "detalle", clave);
    }

    private void exigirGrupo(String grupo) {
        if (!gruposRegistro.containsKey(grupo)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "el grupo " + grupo + " no está registrado; regístralo primero");
        }
    }

    public List<Curso> cursosDeGrupo(String grupo) {
        return cursos.values().stream().filter(c -> c.grupo().equals(grupo)).sorted(Comparator.comparing(Curso::asignatura)).toList();
    }

    public List<Estudiante> alumnosDeCurso(String cursoId) {
        return inscripciones.stream().filter(i -> i.b().equals(cursoId) && estudiantes.containsKey(i.a()))
                .map(i -> estudiantes.get(i.a()))
                .sorted(Comparator.comparing((Estudiante e) -> Calculos.claveTexto(e.apellidos()))
                        .thenComparing(e -> Calculos.claveTexto(e.nombre())).thenComparing(Estudiante::numCuenta))
                .toList();
    }

    private String[] datosPersona(String apellidos, String nombre, String grupo) {
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("apellidos", apellidos);
        r.put("nombre", nombre);
        r.put("grupo", grupo);
        campos(r, "apellidos", "nombre", "grupo");
        return new String[]{formato(texto(apellidos, "apellidos"), Validacion.RE_NOMBRE, "apellidos"),
                formato(texto(nombre, "nombre"), Validacion.RE_NOMBRE, "nombre"),
                formato(texto(grupo, "grupo"), Validacion.RE_GRUPO, "grupo")};
    }

    private int inscribirEnSuGrupo(String nc) {
        String grupo = estudiantes.get(nc).grupo();
        int n = 0;
        for (Curso c : cursos.values()) {
            if (c.grupo().equals(grupo) && inscripciones.add(new Par(nc, c.id()))) {
                n++;
            }
        }
        cambio();
        return n;
    }

    /** Quita la inscripción y las calificaciones de ese curso; devuelve cuántas calificaciones se borraron. */
    private int quitarInscripcion(String nc, String cursoId) {
        inscripciones.remove(new Par(nc, cursoId));
        Set<String> evals = new java.util.HashSet<>();
        parcialesDe(cursoId).forEach(p -> evaluacionesDe(p.id()).forEach(e -> evals.add(e.id())));
        int antes = calificaciones.size();
        calificaciones.keySet().removeIf(k -> k.a().equals(nc) && evals.contains(k.b()));
        cambio();
        return antes - calificaciones.size();
    }

    private List<String> cursosInscritos(String nc) {
        return inscripciones.stream().filter(i -> i.a().equals(nc)).map(Par::b).toList();
    }

    /** El número de cuenta se asigna solo; el alumno queda inscrito en todas las materias de su grupo. */
    public Estudiante altaEstudiante(String apellidos, String nombre, String grupo) {
        String[] d = datosPersona(apellidos, nombre, grupo);
        exigirGrupo(d[2]);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("num_cuenta", siguienteCuenta());
        r.put("apellidos", d[0]);
        r.put("nombre", d[1]);
        r.put("grupo", d[2]);
        Estudiante est = registrarEstudiante(r);
        int n = inscribirEnSuGrupo(est.numCuenta());
        if (n > 0) {
            bus.emitir("inscripcion_registrada", "detalle", est.numCuenta() + " → " + n + " materias del grupo " + d[2]);
        }
        return est;
    }

    /** Cambiar de grupo da de baja sus materias anteriores (con calificaciones) y lo inscribe en las del nuevo. */
    public Estudiante editarEstudiante(String nc, String apellidos, String nombre, String grupo) {
        if (!estudiantes.containsKey(nc)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el estudiante " + nc);
        }
        String[] d = datosPersona(apellidos, nombre, grupo);
        exigirGrupo(d[2]);
        Estudiante anterior = estudiantes.get(nc);
        estudiantes.put(nc, new Estudiante(nc, d[0], d[1], d[2]));
        if (!anterior.grupo().equals(d[2])) {
            cursosInscritos(nc).forEach(c -> quitarInscripcion(nc, c));
            inscribirEnSuGrupo(nc);
        }
        cambio();
        bus.emitir("estudiante_actualizado", "detalle", nc + " · " + d[0] + " " + d[1] + " · grupo " + d[2]);
        return estudiantes.get(nc);
    }

    public void bajaEstudiante(String nc) {
        if (!estudiantes.containsKey(nc)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el estudiante " + nc);
        }
        cursosInscritos(nc).forEach(c -> quitarInscripcion(nc, c));
        Estudiante e = estudiantes.remove(nc);
        cambio();
        bus.emitir("estudiante_baja", "detalle", nc + " · " + e.nombreCompleto());
    }

    private List<Double> validarPesosParciales(List<?> pesos) {
        if (pesos.size() != 3) {
            throw new ErrorValidacion("PORCENTAJES_INVALIDOS", "se requieren exactamente 3 parciales");
        }
        List<Double> valores = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            valores.add(Validacion.peso(pesos.get(i), "peso del parcial " + (i + 1)));
        }
        double suma = Calculos.suma(valores);
        if (!Calculos.esUno(suma)) {
            throw new ErrorValidacion("PORCENTAJES_INVALIDOS", "los 3 parciales deben sumar 100 % (suman " + Math.round(suma * 100) + " %)");
        }
        return valores;
    }

    private String[] validarMateria(String asignatura, String grupo, String excluir) {
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("asignatura", asignatura);
        r.put("grupo", grupo);
        campos(r, "asignatura", "grupo");
        String a = texto(asignatura, "asignatura");
        String g = formato(texto(grupo, "grupo"), Validacion.RE_GRUPO, "grupo");
        if (cursos.values().stream().anyMatch(c -> !c.id().equals(excluir) && c.asignatura().equalsIgnoreCase(a) && c.grupo().equals(g))) {
            throw new ErrorValidacion("ID_DUPLICADO", a + " ya existe en el grupo " + g);
        }
        return new String[]{a, g};
    }

    private void exigirDocente(String docenteId) {
        if (docenteId != null && !docenteId.isEmpty() && !docentes.containsKey(docenteId)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el docente " + docenteId);
        }
    }

    /** Crea la materia con sus 3 parciales, le asigna docente e inscribe al grupo completo. */
    public Curso altaCurso(String asignatura, String grupo, List<?> pesos, String docenteId) {
        String[] m = validarMateria(asignatura, grupo, null);
        exigirGrupo(m[1]);
        List<Double> ps = validarPesosParciales(pesos);
        exigirDocente(docenteId);
        Curso curso = new Curso(siguiente("C", cursos.keySet()), m[0], m[1]);
        cursos.put(curso.id(), curso);
        for (int i = 0; i < 3; i++) {
            String pid = siguiente("P", parciales.keySet());
            parciales.put(pid, new Parcial(pid, curso.id(), i + 1, ps.get(i)));
        }
        if (docenteId != null && !docenteId.isEmpty()) {
            asignaciones.add(new Par(docenteId, curso.id()));
        }
        List<String> alumnos = estudiantes.values().stream().filter(e -> e.grupo().equals(m[1])).map(Estudiante::numCuenta).toList();
        alumnos.forEach(n -> inscripciones.add(new Par(n, curso.id())));
        cambio();
        bus.emitir("curso_registrado", "detalle", curso.id() + " · " + m[0] + " · grupo " + m[1] + " · " + alumnos.size() + " inscritos");
        return curso;
    }

    public Curso editarCurso(String cursoId, String asignatura, List<?> pesos, String docenteId) {
        if (!cursos.containsKey(cursoId)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el curso " + cursoId);
        }
        Curso actual = cursos.get(cursoId);
        String[] m = validarMateria(asignatura, actual.grupo(), cursoId);
        List<Double> ps = validarPesosParciales(pesos);
        exigirDocente(docenteId);
        cursos.put(cursoId, new Curso(cursoId, m[0], actual.grupo()));
        List<Parcial> lista = parcialesDe(cursoId);
        for (int i = 0; i < Math.min(3, lista.size()); i++) {
            Parcial p = lista.get(i);
            parciales.put(p.id(), new Parcial(p.id(), cursoId, p.numero(), ps.get(i)));
        }
        asignaciones.removeIf(a -> a.b().equals(cursoId));
        if (docenteId != null && !docenteId.isEmpty()) {
            asignaciones.add(new Par(docenteId, cursoId));
        }
        cambio();
        bus.emitir("curso_actualizado", "detalle", cursoId + " · " + m[0]);
        return cursos.get(cursoId);
    }

    /** Solo se elimina una materia sin calificaciones capturadas (como en un sistema escolar real). */
    public void eliminarCurso(String cursoId) {
        if (!cursos.containsKey(cursoId)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el curso " + cursoId);
        }
        List<String> ps = parcialesDe(cursoId).stream().map(Parcial::id).toList();
        Set<String> evals = new java.util.HashSet<>();
        ps.forEach(pid -> evaluacionesDe(pid).forEach(e -> evals.add(e.id())));
        if (calificaciones.keySet().stream().anyMatch(k -> evals.contains(k.b()))) {
            throw new ErrorValidacion("OPERACION_NO_PERMITIDA", cursoId + " ya tiene calificaciones capturadas");
        }
        evals.forEach(evaluaciones::remove);
        ps.forEach(parciales::remove);
        inscripciones.removeIf(i -> i.b().equals(cursoId));
        asignaciones.removeIf(a -> a.b().equals(cursoId));
        Curso c = cursos.remove(cursoId);
        cambio();
        bus.emitir("curso_eliminado", "detalle", cursoId + " · " + c.asignatura());
    }

    public Docente altaDocente(String nombre) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", siguienteDocente());
        r.put("nombre", nombre);
        return registrarDocente(r);
    }

    /** Deja al docente exactamente con esos cursos (cada curso tiene un solo docente). */
    public int asignarCursos(String docenteId, java.util.Collection<String> cursosIds) {
        if (!docentes.containsKey(docenteId)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el docente " + docenteId);
        }
        Set<String> ids = new java.util.TreeSet<>(cursosIds);
        List<String> faltan = ids.stream().filter(c -> !cursos.containsKey(c)).toList();
        if (!faltan.isEmpty()) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existen los cursos " + String.join(", ", faltan));
        }
        asignaciones.removeIf(a -> a.a().equals(docenteId) || ids.contains(a.b()));
        ids.forEach(c -> asignaciones.add(new Par(docenteId, c)));
        bus.emitir("docente_asignado", "detalle", docenteId + " → " + ids.size() + " cursos");
        return ids.size();
    }

    /** Altas y bajas individuales (excepciones). Devuelve {altas, bajas}. */
    public int[] actualizarInscripciones(String nc, java.util.Collection<String> cursosIds) {
        if (!estudiantes.containsKey(nc)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el estudiante " + nc);
        }
        String grupo = estudiantes.get(nc).grupo();
        Set<String> ids = new LinkedHashSet<>(cursosIds);
        for (String c : ids) {
            if (!cursos.containsKey(c)) {
                throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el curso " + c);
            }
            if (!cursos.get(c).grupo().equals(grupo)) {
                throw new ErrorValidacion("REFERENCIA_INEXISTENTE", c + " no pertenece al grupo " + grupo);
            }
        }
        Set<String> actuales = new LinkedHashSet<>(cursosInscritos(nc));
        List<String> altas = ids.stream().filter(c -> !actuales.contains(c)).toList();
        List<String> bajas = actuales.stream().filter(c -> !ids.contains(c)).toList();
        altas.forEach(c -> inscripciones.add(new Par(nc, c)));
        bajas.forEach(c -> quitarInscripcion(nc, c));
        cambio();
        if (!altas.isEmpty() || !bajas.isEmpty()) {
            bus.emitir("inscripcion_actualizada", "detalle", nc + " · " + altas.size() + " altas, " + bajas.size() + " bajas");
        }
        return new int[]{altas.size(), bajas.size()};
    }

    /** Inscribe a cada alumno del grupo en las materias del grupo que le falten. */
    public int inscribirGrupo(String grupo) {
        int total = 0;
        for (Estudiante e : new ArrayList<>(estudiantes.values())) {
            if (e.grupo().equals(grupo)) {
                total += inscribirEnSuGrupo(e.numCuenta());
            }
        }
        bus.emitir("inscripcion_registrada", "detalle", "grupo " + grupo + " · " + total + " inscripciones nuevas");
        return total;
    }

    private List<Evaluacion> validarPlan(String parcialId, List<Rubro> rubros) {
        if (!parciales.containsKey(parcialId)) {
            throw new ErrorValidacion("REFERENCIA_INEXISTENTE", "no existe el parcial " + parcialId);
        }
        int numero = parciales.get(parcialId).numero();
        if (rubros == null || rubros.isEmpty()) {
            throw new ErrorValidacion("CAMPO_FALTANTE", "parcial " + numero + ": agrega al menos una evaluación");
        }
        Set<String> actuales = new java.util.HashSet<>();
        evaluacionesDe(parcialId).forEach(e -> actuales.add(e.id()));
        Set<String> nombres = new java.util.HashSet<>();
        List<Evaluacion> limpios = new ArrayList<>();
        for (Rubro r : rubros) {
            String nombre;
            double peso;
            try {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("nombre", r.nombre());
                m.put("peso", r.peso());
                campos(m, "nombre", "peso");
                nombre = texto(r.nombre(), "nombre");
                peso = Validacion.peso(r.peso(), "peso de " + nombre);
            } catch (ErrorValidacion e) {
                throw new ErrorValidacion(e.getCodigo(), "parcial " + numero + ": " + e.getDetalle());
            }
            if (!nombres.add(nombre.toLowerCase())) {
                throw new ErrorValidacion("ID_DUPLICADO", "parcial " + numero + ": " + nombre + " está repetida");
            }
            limpios.add(new Evaluacion(actuales.contains(r.id()) ? r.id() : null, parcialId, nombre, peso));
        }
        double suma = limpios.stream().mapToDouble(Evaluacion::peso).sum();
        if (!Calculos.esUno(suma)) {
            throw new ErrorValidacion("PORCENTAJES_INVALIDOS", "parcial " + numero + ": las evaluaciones deben sumar 100 % (suman "
                    + Calculos.limpiar10(suma).multiply(java.math.BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + " %)");
        }
        return limpios;
    }

    public int definirPlan(String parcialId, List<Rubro> rubros) {
        return definirPlanes(Map.of(parcialId, rubros));
    }

    /**
     * Guarda el plan de varios parciales a la vez: primero se validan todos y solo entonces se aplican.
     * Los rubros que conservan su id mantienen sus calificaciones; devuelve cuántas calificaciones se borraron.
     */
    public int definirPlanes(Map<String, List<Rubro>> planes) {
        Map<String, List<Evaluacion>> validados = new LinkedHashMap<>();
        planes.forEach((pid, rubros) -> validados.put(pid, validarPlan(pid, rubros)));
        int borradas = 0;
        int total = 0;
        for (Map.Entry<String, List<Evaluacion>> e : validados.entrySet()) {
            Set<String> conservados = new java.util.HashSet<>();
            e.getValue().stream().filter(x -> x.id() != null).forEach(x -> conservados.add(x.id()));
            for (Evaluacion vieja : evaluacionesDe(e.getKey())) {
                if (!conservados.contains(vieja.id())) {
                    evaluaciones.remove(vieja.id());
                    int antes = calificaciones.size();
                    calificaciones.keySet().removeIf(k -> k.b().equals(vieja.id()));
                    borradas += antes - calificaciones.size();
                }
            }
            for (Evaluacion x : e.getValue()) {
                String eid = x.id() != null ? x.id() : siguiente("EV", evaluaciones.keySet());
                evaluaciones.put(eid, new Evaluacion(eid, e.getKey(), x.nombre(), x.peso()));
            }
            total += e.getValue().size();
            cambio();
        }
        bus.emitir("plan_actualizado", "detalle", String.join(", ", validados.keySet()) + " · " + total + " evaluaciones");
        return borradas;
    }

    /**
     * Captura por lote (acta). Valor null borra una calificación existente. Los renglones con error no detienen
     * a los demás y se devuelven juntos, para avisar una sola vez.
     */
    public ResultadoActa guardarActa(List<CambioActa> cambios) {
        int capturadas = 0;
        int modificadas = 0;
        int borradas = 0;
        List<Map<String, Object>> errs = new ArrayList<>();
        for (CambioActa c : cambios) {
            if (c.valor() == null) {
                if (calificaciones.remove(new Par(c.numCuenta(), c.evaluacionId())) != null) {
                    borradas++;
                }
                continue;
            }
            Map<String, Object> registro = new LinkedHashMap<>();
            registro.put("num_cuenta", c.numCuenta());
            registro.put("evaluacion_id", c.evaluacionId());
            registro.put("valor", c.valor());
            Par clave;
            double valor;
            try {
                valor = Validacion.calificacion(c.valor());
                clave = validarCalificacion(registro);
            } catch (ErrorValidacion e) {
                errores.add(new RegistroError(null, "acta", e.getCodigo(), e.getDetalle(), registro));
                Map<String, Object> err = new LinkedHashMap<>(registro);
                err.put("codigo", e.getCodigo());
                err.put("detalle", e.getDetalle());
                errs.add(err);
                continue;
            }
            if (calificaciones.containsKey(clave)) {
                modificadas++;
            } else {
                capturadas++;
            }
            calificaciones.put(clave, valor);
        }
        bus.emitir("acta_guardada", "detalle", capturadas + " nuevas, " + modificadas + " modificadas, " + borradas + " borradas, "
                + errs.size() + " con error");
        return new ResultadoActa(capturadas, modificadas, borradas, errs);
    }

    // ---------- respaldo (sin validar: el estado guardado ya pasó por las validaciones) ----------
    private static String s(Map<String, Object> r, String k) {
        return String.valueOf(r.get(k));
    }

    private static double d(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapas(Map<String, Object> datos, String llave) {
        return (List<Map<String, Object>>) (List<?>) lista(datos, llave);
    }

    public void restaurar(Map<String, Object> d) {
        estudiantes.clear();
        cursos.clear();
        parciales.clear();
        evaluaciones.clear();
        inscripciones.clear();
        calificaciones.clear();
        docentes.clear();
        asignaciones.clear();
        gruposRegistro.clear();
        mapas(d, "estudiantes").forEach(r -> estudiantes.put(s(r, "num_cuenta"),
                new Estudiante(s(r, "num_cuenta"), s(r, "apellidos"), s(r, "nombre"), s(r, "grupo"))));
        mapas(d, "cursos").forEach(r -> cursos.put(s(r, "id"), new Curso(s(r, "id"), s(r, "asignatura"), s(r, "grupo"))));
        mapas(d, "parciales").forEach(r -> parciales.put(s(r, "id"),
                new Parcial(s(r, "id"), s(r, "curso_id"), (int) d(r, "numero"), d(r, "peso"))));
        mapas(d, "evaluaciones").forEach(r -> evaluaciones.put(s(r, "id"),
                new Evaluacion(s(r, "id"), s(r, "parcial_id"), s(r, "nombre"), d(r, "peso"))));
        mapas(d, "inscripciones").forEach(r -> inscripciones.add(new Par(s(r, "num_cuenta"), s(r, "curso_id"))));
        mapas(d, "calificaciones").forEach(r -> calificaciones.put(new Par(s(r, "num_cuenta"), s(r, "evaluacion_id")), d(r, "valor")));
        mapas(d, "docentes").forEach(r -> docentes.put(s(r, "id"), new Docente(s(r, "id"), s(r, "nombre"))));
        mapas(d, "asignaciones").forEach(r -> asignaciones.add(new Par(s(r, "docente_id"), s(r, "curso_id"))));
        mapas(d, "grupos").forEach(r -> gruposRegistro.put(s(r, "clave"), new Grupo(s(r, "clave"), (int) d(r, "semestre"), s(r, "turno"))));
        sincronizarGrupos();
        cambio();
        bus.emitir("datos_restaurados", "detalle", estudiantes.size() + " estudiantes, " + cursos.size() + " cursos");
    }

    /** Estado actual con el mismo formato que usan las versiones de Python y JavaScript (datos/base.json). */
    public Map<String, Object> aDiccionario() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("grupos", gruposRegistro.values().stream().sorted(Comparator.comparing(Grupo::clave))
                .map(g -> fila("clave", g.clave(), "semestre", g.semestre(), "turno", g.turno())).toList());
        d.put("estudiantes", estudiantes.values().stream()
                .map(e -> fila("num_cuenta", e.numCuenta(), "apellidos", e.apellidos(), "nombre", e.nombre(), "grupo", e.grupo())).toList());
        d.put("cursos", cursos.values().stream().map(c -> fila("id", c.id(), "asignatura", c.asignatura(), "grupo", c.grupo())).toList());
        d.put("parciales", parciales.values().stream()
                .map(p -> fila("id", p.id(), "curso_id", p.cursoId(), "numero", p.numero(), "peso", p.peso())).toList());
        d.put("evaluaciones", evaluaciones.values().stream()
                .map(e -> fila("id", e.id(), "parcial_id", e.parcialId(), "nombre", e.nombre(), "peso", e.peso())).toList());
        Comparator<Par> porPar = Comparator.comparing(Par::a).thenComparing(Par::b);
        d.put("inscripciones", inscripciones.stream().sorted(porPar).map(i -> fila("num_cuenta", i.a(), "curso_id", i.b())).toList());
        d.put("calificaciones", calificaciones.entrySet().stream().sorted(Map.Entry.comparingByKey(porPar))
                .map(e -> fila("num_cuenta", e.getKey().a(), "evaluacion_id", e.getKey().b(), "valor", e.getValue())).toList());
        d.put("docentes", docentes.values().stream().map(x -> fila("id", x.id(), "nombre", x.nombre())).toList());
        d.put("asignaciones", asignaciones.stream().sorted(porPar).map(a -> fila("docente_id", a.a(), "curso_id", a.b())).toList());
        return d;
    }

    private static Map<String, Object> fila(Object... kv) {
        Map<String, Object> f = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            f.put((String) kv[i], kv[i + 1]);
        }
        return f;
    }

    // ---------- carga masiva ----------
    @SuppressWarnings("unchecked")
    private static List<Object> lista(Map<String, Object> datos, String llave) {
        Object v = datos.get(llave);
        return v instanceof List ? (List<Object>) v : List.of();
    }

    @SuppressWarnings("unchecked")
    public void cargar(Map<String, Object> datos) {
        for (Object r : lista(datos, "grupos")) {
            procesar("grupo", r, x -> {
                Map<String, Object> m = x instanceof Map ? (Map<String, Object>) x : Map.of();
                altaGrupo(m.get("clave") instanceof String c ? c : null, m.get("semestre"), String.valueOf(m.get("turno")));
            });
        }
        lista(datos, "estudiantes").forEach(r -> procesar("estudiante", r, this::registrarEstudiante));
        lista(datos, "cursos").forEach(r -> procesar("curso", r, this::registrarCurso));
        cargarParciales(lista(datos, "parciales"));
        cargarEvaluaciones(lista(datos, "evaluaciones"));
        lista(datos, "inscripciones").forEach(r -> procesar("inscripcion", r, this::inscribir));
        lista(datos, "calificaciones").forEach(r -> procesar("calificacion", r, this::capturarCalificacion));
        lista(datos, "docentes").forEach(r -> procesar("docente", r, this::registrarDocente));
        lista(datos, "asignaciones").forEach(r -> procesar("asignacion", r, this::asignarDocente));
        for (Object r : lista(datos, "operaciones")) {
            if (!(r instanceof Map)) {
                continue;
            }
            Object tipo = ((Map<String, Object>) r).get("tipo");
            if ("modificar_calificacion".equals(tipo)) {
                procesar("modificar_calificacion", r, this::modificarCalificacion);
            } else if ("consultar_kardex".equals(tipo)) {
                procesar("consultar_kardex", r, x -> consultarKardex(String.valueOf(((Map<String, Object>) x).get("num_cuenta"))));
            }
        }
        sincronizarGrupos();
        cambio();
        bus.emitir("datos_cargados", "detalle", estudiantes.size() + " estudiantes, " + errores.size() + " errores");
    }

    /** Registro que representa al grupo en el error: el primero que trae "caso", si alguno lo trae. */
    @SuppressWarnings("unchecked")
    private static Object representante(List<Object[]> lista) {
        for (Object[] par : lista) {
            if (par[0] instanceof Map && ((Map<String, Object>) par[0]).get("caso") != null
                    && !"".equals(((Map<String, Object>) par[0]).get("caso"))) {
                return par[0];
            }
        }
        return lista.get(0)[0];
    }

    private void cargarParciales(List<Object> registros) {
        Map<String, List<Object[]>> porCurso = new LinkedHashMap<>();
        for (Object r : registros) {
            try {
                Parcial p = validarParcial(r);
                boolean repetido = porCurso.values().stream().flatMap(List::stream).anyMatch(x -> ((Parcial) x[1]).id().equals(p.id()));
                if (repetido) {
                    throw new ErrorValidacion("ID_DUPLICADO", "el parcial " + p.id() + " ya existe");
                }
                porCurso.computeIfAbsent(p.cursoId(), k -> new ArrayList<>()).add(new Object[]{r, p});
            } catch (ErrorValidacion e) {
                registrarError("parcial", r, e);
            }
        }
        porCurso.forEach((cursoId, lista) -> {
            List<Parcial> ps = lista.stream().map(x -> (Parcial) x[1]).toList();
            List<Integer> numeros = ps.stream().map(Parcial::numero).sorted().toList();
            double suma = ps.stream().mapToDouble(Parcial::peso).sum();
            if (!numeros.equals(List.of(1, 2, 3)) || !Calculos.esUno(suma)) {
                registrarError("parcial", representante(lista), new ErrorValidacion("PORCENTAJES_INVALIDOS",
                        "los parciales de " + cursoId + " deben ser 3 y sumar 1.0 (suman " + Calculos.normalizado(suma) + ")"));
                return;
            }
            ps.forEach(p -> {
                parciales.put(p.id(), p);
                bus.emitir("parcial_registrado", "detalle", p.id() + " · " + p.cursoId() + " · peso " + p.peso());
            });
        });
    }

    private void cargarEvaluaciones(List<Object> registros) {
        Map<String, List<Object[]>> porParcial = new LinkedHashMap<>();
        for (Object r : registros) {
            try {
                Evaluacion e = validarEvaluacion(r);
                if (porParcial.getOrDefault(e.parcialId(), List.of()).stream()
                        .anyMatch(x -> ((Evaluacion) x[1]).nombre().equalsIgnoreCase(e.nombre()))) {
                    throw new ErrorValidacion("ID_DUPLICADO", e.nombre() + " repetida en " + e.parcialId());
                }
                if (porParcial.values().stream().flatMap(List::stream).anyMatch(x -> ((Evaluacion) x[1]).id().equals(e.id()))) {
                    throw new ErrorValidacion("ID_DUPLICADO", "la evaluación " + e.id() + " ya existe");
                }
                porParcial.computeIfAbsent(e.parcialId(), k -> new ArrayList<>()).add(new Object[]{r, e});
            } catch (ErrorValidacion err) {
                registrarError("evaluacion", r, err);
            }
        }
        porParcial.forEach((parcialId, lista) -> {
            double suma = lista.stream().mapToDouble(x -> ((Evaluacion) x[1]).peso()).sum();
            if (!Calculos.esUno(suma)) {
                registrarError("evaluacion", representante(lista), new ErrorValidacion("PORCENTAJES_INVALIDOS",
                        "las evaluaciones de " + parcialId + " suman " + Calculos.normalizado(suma) + " y deben sumar 1.0"));
                return;
            }
            lista.forEach(x -> {
                Evaluacion e = (Evaluacion) x[1];
                evaluaciones.put(e.id(), e);
                bus.emitir("evaluacion_registrada", "detalle", e.id() + " · " + e.nombre() + " · " + e.parcialId());
            });
        });
    }

    public Map<String, Grupo> getGruposRegistro() {
        return gruposRegistro;
    }

    public BusEventos getBus() {
        return bus;
    }

    public Map<String, Docente> getDocentes() {
        return docentes;
    }

    public Set<Par> getAsignaciones() {
        return asignaciones;
    }

    public List<RegistroError> getErrores() {
        return errores;
    }
}
