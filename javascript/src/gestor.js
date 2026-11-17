/**
 * Datos del sistema y reglas de negocio (versión declarativa).
 * Los cálculos se expresan con map / filter / reduce sobre colecciones; no hay ciclos con estado.
 */
import { ErrorValidacion } from './errores.js';
import { BusEventos } from './eventos.js';
import * as v from './validacion.js';
import { claveTexto, evaluarEstatusCurso, evaluarRiesgo, motivoRiesgo, normalizar, suma, comparar, compararTuplas } from './calculos.js';

export const PESOS_PARCIALES = [0.3, 0.3, 0.4];
export const TURNOS = ['Matutino', 'Vespertino'];

const clave = (a, b) => `${a}|${b}`;
const agrupar = (xs, k) => xs.reduce((m, x) => m.set(k(x), [...(m.get(k(x)) ?? []), x]), new Map());

/** Solo datos y cálculos (sin eventos), para poder enviarla a otros hilos. */
export class Calculadora {
  constructor() {
    this.estudiantes = new Map();
    this.cursos = new Map();
    this.parciales = new Map();
    this.evaluaciones = new Map();
    this.inscripciones = new Set(); // "num_cuenta|curso_id"
    this.calificaciones = new Map(); // "num_cuenta|evaluacion_id" -> valor
    this._indices = null;
    this._version = 0;
  }

  /** Invalida los índices: altas, bajas y ediciones pueden dejar los mismos tamaños con otro contenido. */
  _cambio() {
    this._version += 1;
    this._indices = null;
  }

  _indice() {
    const firma = [this._version, this.parciales.size, this.evaluaciones.size, this.inscripciones.size, this.cursos.size].join();
    if (this._indices?.firma !== firma) {
      this._indices = {
        firma,
        parciales: agrupar([...this.parciales.values()], (p) => p.curso_id),
        evaluaciones: agrupar([...this.evaluaciones.values()], (e) => e.parcial_id),
        cursos: agrupar([...this.inscripciones].map((i) => i.split('|')), ([n]) => n),
      };
    }
    return this._indices;
  }

  parcialesDe(cursoId) {
    return [...(this._indice().parciales.get(cursoId) ?? [])].sort((a, b) => a.numero - b.numero);
  }

  evaluacionesDe(parcialId) {
    return [...(this._indice().evaluaciones.get(parcialId) ?? [])];
  }

  cursosDe(numCuenta) {
    return (this._indice().cursos.get(numCuenta) ?? [])
      .map(([, c]) => this.cursos.get(c))
      .filter(Boolean)
      .sort((a, b) => comparar(a.id, b.id));
  }

  /** Calificación del parcial; null mientras falte capturar alguna evaluación. */
  califParcial(numCuenta, parcialId) {
    const evals = this.evaluacionesDe(parcialId);
    if (!evals.length || evals.some((e) => !this.calificaciones.has(clave(numCuenta, e.id)))) return null;
    return suma(evals.map((e) => this.calificaciones.get(clave(numCuenta, e.id)) * e.peso));
  }

  promedioCurso(numCuenta, cursoId) {
    const parciales = this.parcialesDe(cursoId);
    const valores = parciales.map((p) => this.califParcial(numCuenta, p.id));
    if (!parciales.length || valores.some((x) => x === null)) return null;
    return suma(valores.map((x, i) => x * parciales[i].peso));
  }

  resumenEstudiante(numCuenta) {
    const e = this.estudiantes.get(numCuenta);
    const cursos = this.cursosDe(numCuenta).map((c) => {
      const bruto = this.promedioCurso(numCuenta, c.id);
      return {
        curso_id: c.id, asignatura: c.asignatura, grupo: c.grupo,
        parciales: this.parcialesDe(c.id).map((p) => this.califParcial(numCuenta, p.id)),
        bruto,
        estatus: bruto === null ? 'PENDIENTE' : evaluarEstatusCurso(bruto),
      };
    });
    const cerrados = cursos.filter((c) => c.bruto !== null).map((c) => c.bruto);
    const general = cerrados.length ? suma(cerrados) / cerrados.length : null;
    return {
      num_cuenta: e.num_cuenta, apellidos: e.apellidos, nombre: e.nombre, grupo: e.grupo,
      cursos, general,
      estatus: general === null ? 'PENDIENTE' : evaluarEstatusCurso(general),
      riesgo: general === null ? null : evaluarRiesgo(cerrados, general),
      motivo: general === null ? 'Sin cursos cerrados' : motivoRiesgo(cerrados, general),
      pendientes: cursos.filter((c) => c.bruto === null).length,
    };
  }

  /** Estado serializable (para hilos de trabajo y para exportar). */
  aObjeto() {
    const pares = (s) => [...s].map((x) => x.split('|'));
    return {
      estudiantes: [...this.estudiantes.values()],
      cursos: [...this.cursos.values()],
      parciales: [...this.parciales.values()],
      evaluaciones: [...this.evaluaciones.values()],
      inscripciones: pares(this.inscripciones).sort((a, b) => comparar(a.join('|'), b.join('|')))
        .map(([num_cuenta, curso_id]) => ({ num_cuenta, curso_id })),
      calificaciones: [...this.calificaciones].sort(([a], [b]) => comparar(a, b))
        .map(([k, valor]) => ({ num_cuenta: k.split('|')[0], evaluacion_id: k.split('|')[1], valor })),
    };
  }

  static desdeObjeto(o) {
    const c = new Calculadora();
    o.estudiantes.forEach((x) => c.estudiantes.set(x.num_cuenta, x));
    o.cursos.forEach((x) => c.cursos.set(x.id, x));
    o.parciales.forEach((x) => c.parciales.set(x.id, x));
    o.evaluaciones.forEach((x) => c.evaluaciones.set(x.id, x));
    o.inscripciones.forEach((x) => c.inscripciones.add(clave(x.num_cuenta, x.curso_id)));
    o.calificaciones.forEach((x) => c.calificaciones.set(clave(x.num_cuenta, x.evaluacion_id), x.valor));
    return c;
  }
}

export class GestorEscolar extends Calculadora {
  constructor(bus = new BusEventos()) {
    super();
    this.bus = bus;
    this.docentes = new Map();
    this.asignaciones = new Set(); // "docente_id|curso_id"
    this.gruposRegistro = new Map(); // clave -> { clave, semestre, turno }
    this.errores = [];
  }

  _error(tipo, registro, e) {
    const caso = registro !== null && typeof registro === 'object' ? registro.caso ?? null : null;
    this.errores.push({ caso, tipo, codigo: e.codigo, detalle: e.detalle, registro });
    this.bus.emitir('error_validacion', { codigo: e.codigo, detalle: e.detalle, tipo });
  }

  _procesar(tipo, registro, accion) {
    try {
      accion(registro);
      return true;
    } catch (e) {
      if (!(e instanceof ErrorValidacion)) throw e;
      this._error(tipo, registro, e);
      return false;
    }
  }

  // ---------- altas individuales (las usa la interfaz) ----------
  registrarEstudiante(r) {
    v.campos(r, 'num_cuenta', 'apellidos', 'nombre', 'grupo');
    const nc = v.formato(v.texto(r.num_cuenta, 'num_cuenta'), v.RE_CUENTA, 'num_cuenta');
    const apellidos = v.formato(v.texto(r.apellidos, 'apellidos'), v.RE_NOMBRE, 'apellidos');
    const nombre = v.formato(v.texto(r.nombre, 'nombre'), v.RE_NOMBRE, 'nombre');
    const grupo = v.formato(v.texto(r.grupo, 'grupo'), v.RE_GRUPO, 'grupo');
    if (this.estudiantes.has(nc)) throw new ErrorValidacion('ID_DUPLICADO', `el estudiante ${nc} ya está registrado`);
    const est = Object.freeze({ num_cuenta: nc, apellidos, nombre, grupo });
    this.estudiantes.set(nc, est);
    this.bus.emitir('estudiante_registrado', { detalle: `${nc} · ${apellidos} ${nombre}` });
    return est;
  }

  registrarCurso(r) {
    v.campos(r, 'id', 'asignatura', 'grupo');
    const id = v.formato(v.texto(r.id, 'id'), v.RE_CURSO, 'id de curso');
    const asignatura = v.texto(r.asignatura, 'asignatura');
    const grupo = v.formato(v.texto(r.grupo, 'grupo'), v.RE_GRUPO, 'grupo');
    const cursos = [...this.cursos.values()];
    if (this.cursos.has(id)) throw new ErrorValidacion('ID_DUPLICADO', `el curso ${id} ya existe`);
    if (cursos.some((c) => c.asignatura === asignatura && c.grupo === grupo)) {
      throw new ErrorValidacion('ID_DUPLICADO', `${asignatura} ya existe en el grupo ${grupo}`);
    }
    if (![...this.estudiantes.values()].some((e) => e.grupo === grupo)) {
      throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `el grupo ${grupo} no tiene estudiantes registrados`);
    }
    const curso = Object.freeze({ id, asignatura, grupo });
    this.cursos.set(id, curso);
    this.bus.emitir('curso_registrado', { detalle: `${id} · ${asignatura} · grupo ${grupo}` });
    return curso;
  }

  _validarParcial(r) {
    v.campos(r, 'id', 'curso_id', 'numero', 'peso');
    const id = v.formato(v.texto(r.id, 'id'), v.RE_PARCIAL, 'id de parcial');
    const curso_id = v.texto(r.curso_id, 'curso_id');
    const numero = v.numero(r.numero, 'numero');
    if (![1, 2, 3].includes(numero)) {
      throw new ErrorValidacion('FORMATO_INVALIDO', `el número de parcial debe ser 1, 2 o 3: ${v.pyNum(numero)}`);
    }
    const peso = v.peso(r.peso);
    if (this.parciales.has(id)) throw new ErrorValidacion('ID_DUPLICADO', `el parcial ${id} ya existe`);
    if (!this.cursos.has(curso_id)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el curso ${curso_id}`);
    if (this.parcialesDe(curso_id).some((p) => p.numero === numero)) {
      throw new ErrorValidacion('ID_DUPLICADO', `el curso ${curso_id} ya tiene el parcial ${numero}`);
    }
    return Object.freeze({ id, curso_id, numero, peso });
  }

  /** Alta individual: permite completar los 3 parciales poco a poco sin pasar de 1.0. */
  registrarParcial(r) {
    const p = this._validarParcial(r);
    const actuales = this.parcialesDe(p.curso_id);
    if (actuales.length >= 3 || normalizar(suma(actuales.map((x) => x.peso)) + p.peso) > 1) {
      throw new ErrorValidacion('PORCENTAJES_INVALIDOS', `los parciales de ${p.curso_id} superarían 1.0 o 3 parciales`);
    }
    this.parciales.set(p.id, p);
    this.bus.emitir('parcial_registrado', { detalle: `${p.id} · ${p.curso_id} · peso ${p.peso}` });
    return p;
  }

  _validarEvaluacion(r) {
    v.campos(r, 'id', 'parcial_id', 'nombre', 'peso');
    const id = v.formato(v.texto(r.id, 'id'), v.RE_EVALUACION, 'id de evaluación');
    const parcial_id = v.texto(r.parcial_id, 'parcial_id');
    const nombre = v.texto(r.nombre, 'nombre');
    const peso = v.peso(r.peso);
    if (this.evaluaciones.has(id)) throw new ErrorValidacion('ID_DUPLICADO', `la evaluación ${id} ya existe`);
    if (!this.parciales.has(parcial_id)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el parcial ${parcial_id}`);
    if (this.evaluacionesDe(parcial_id).some((e) => e.nombre.toLowerCase() === nombre.toLowerCase())) {
      throw new ErrorValidacion('ID_DUPLICADO', `${nombre} ya existe en el parcial ${parcial_id}`);
    }
    return Object.freeze({ id, parcial_id, nombre, peso });
  }

  registrarEvaluacion(r) {
    const e = this._validarEvaluacion(r);
    if (normalizar(suma(this.evaluacionesDe(e.parcial_id).map((x) => x.peso)) + e.peso) > 1) {
      throw new ErrorValidacion('PORCENTAJES_INVALIDOS', `las evaluaciones de ${e.parcial_id} superarían 1.0`);
    }
    this.evaluaciones.set(e.id, e);
    this.bus.emitir('evaluacion_registrada', { detalle: `${e.id} · ${e.nombre} · ${e.parcial_id}` });
    return e;
  }

  inscribir(r) {
    v.campos(r, 'num_cuenta', 'curso_id');
    const nc = v.texto(r.num_cuenta, 'num_cuenta');
    const cid = v.texto(r.curso_id, 'curso_id');
    if (!this.estudiantes.has(nc)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el estudiante ${nc}`);
    if (!this.cursos.has(cid)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el curso ${cid}`);
    if (this.cursos.get(cid).grupo !== this.estudiantes.get(nc).grupo) {
      throw new ErrorValidacion('REFERENCIA_INEXISTENTE', 'el curso no pertenece al grupo del estudiante');
    }
    if (this.inscripciones.has(clave(nc, cid))) throw new ErrorValidacion('INSCRIPCION_DUPLICADA', `${nc} ya está inscrito en ${cid}`);
    this.inscripciones.add(clave(nc, cid));
    this.bus.emitir('inscripcion_registrada', { detalle: `${nc} → ${cid}` });
  }

  _validarCalificacion(r) {
    v.campos(r, 'num_cuenta', 'evaluacion_id');
    const valor = v.calificacion(r.valor);
    const nc = v.texto(r.num_cuenta, 'num_cuenta');
    const eid = v.texto(r.evaluacion_id, 'evaluacion_id');
    if (!this.evaluaciones.has(eid)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe la evaluación ${eid}`);
    const cursoId = this.parciales.get(this.evaluaciones.get(eid).parcial_id).curso_id;
    if (!this.inscripciones.has(clave(nc, cursoId))) {
      throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `${nc} no está inscrito en ${cursoId}`);
    }
    return [nc, eid, valor];
  }

  capturarCalificacion(r) {
    const [nc, eid, valor] = this._validarCalificacion(r);
    if (this.calificaciones.has(clave(nc, eid))) throw new ErrorValidacion('ID_DUPLICADO', `${nc} ya tiene calificación en ${eid}`);
    this.calificaciones.set(clave(nc, eid), valor);
    this.bus.emitir('calificacion_capturada', { detalle: `${nc} · ${eid} · ${v.pyNum(valor)}` });
    return valor;
  }

  modificarCalificacion(r) {
    const [nc, eid, valor] = this._validarCalificacion(r);
    if (!this.calificaciones.has(clave(nc, eid))) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `${nc} no tiene calificación en ${eid}`);
    this.calificaciones.set(clave(nc, eid), valor);
    this.bus.emitir('calificacion_modificada', { detalle: `${nc} · ${eid} · ${v.pyNum(valor)}` });
    return valor;
  }

  calificacion(numCuenta, evaluacionId) {
    return this.calificaciones.get(clave(numCuenta, evaluacionId)) ?? null;
  }

  listaInscripciones() {
    return [...this.inscripciones].map((i) => i.split('|'));
  }

  consultarKardex(numCuenta) {
    if (!this.estudiantes.has(numCuenta)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el estudiante ${numCuenta}`);
    return this.resumenEstudiante(numCuenta);
  }

  registrarDocente(r) {
    v.campos(r, 'id', 'nombre');
    const id = v.formato(v.texto(r.id, 'id'), v.RE_DOCENTE, 'id de docente');
    const nombre = v.formato(v.texto(r.nombre, 'nombre'), v.RE_NOMBRE, 'nombre del docente');
    if (this.docentes.has(id)) throw new ErrorValidacion('ID_DUPLICADO', `el docente ${id} ya existe`);
    this.docentes.set(id, Object.freeze({ id, nombre }));
    this.bus.emitir('docente_registrado', { detalle: `${id} · ${nombre}` });
    return this.docentes.get(id);
  }

  asignarDocente(r) {
    v.campos(r, 'docente_id', 'curso_id');
    const { docente_id: did, curso_id: cid } = r;
    if (!this.docentes.has(did)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el docente ${did}`);
    if (!this.cursos.has(cid)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el curso ${cid}`);
    this.asignaciones = new Set([...this.asignaciones].filter((a) => a.split('|')[1] !== cid)); // un docente por curso
    this.asignaciones.add(clave(did, cid));
    this.bus.emitir('docente_asignado', { detalle: `${did} → ${cid}` });
  }

  cursosDeDocente(docenteId) {
    return [...this.asignaciones].map((a) => a.split('|'))
      .filter(([d, c]) => d === docenteId && this.cursos.has(c))
      .map(([, c]) => this.cursos.get(c))
      .sort((a, b) => comparar(a.id, b.id));
  }

  docenteDe(cursoId) {
    const par = [...this.asignaciones].map((a) => a.split('|')).find(([d, c]) => c === cursoId && this.docentes.has(d));
    return par ? this.docentes.get(par[0]) : null;
  }


  // ---------- control escolar: operaciones completas en un solo paso (las usa la interfaz) ----------
  /** Siguiente ID libre con el formato del sistema: C07, P19, EV43, D03. */
  static _siguiente(prefijo, existentes) {
    const numeros = [...existentes].filter((x) => x.startsWith(prefijo) && /^\d+$/.test(x.slice(prefijo.length)))
      .map((x) => Number(x.slice(prefijo.length)));
    return `${prefijo}${String((numeros.length ? Math.max(...numeros) : 0) + 1).padStart(2, '0')}`;
  }

  siguienteCuenta() {
    const numeros = [...this.estudiantes.keys()].filter((n) => /^\d+$/.test(n)).map(Number);
    return String(numeros.length ? Math.max(...numeros) + 1 : 500001).padStart(6, '0');
  }

  siguienteDocente() { return GestorEscolar._siguiente('D', this.docentes.keys()); }

  grupos() {
    return [...new Set([...this.gruposRegistro.keys(), ...[...this.estudiantes.values()].map((e) => e.grupo),
      ...[...this.cursos.values()].map((c) => c.grupo)])].sort(comparar);
  }

  cursosDeGrupo(grupo) {
    return [...this.cursos.values()].filter((c) => c.grupo === grupo).sort((a, b) => comparar(a.asignatura, b.asignatura));
  }

  alumnosDeCurso(cursoId) {
    return this.listaInscripciones().filter(([n, c]) => c === cursoId && this.estudiantes.has(n))
      .map(([n]) => this.estudiantes.get(n))
      .sort((a, b) => compararTuplas([claveTexto(a.apellidos), claveTexto(a.nombre), a.num_cuenta],
        [claveTexto(b.apellidos), claveTexto(b.nombre), b.num_cuenta]));
  }

  // ----- grupos -----
  /** Los grupos que vienen en los datos quedan registrados; el semestre sale del primer dígito (301 → 3). */
  _sincronizarGrupos() {
    this.grupos().filter((g) => !this.gruposRegistro.has(g)).forEach((g) => this.gruposRegistro.set(g,
      Object.freeze({ clave: g, semestre: /^[1-9]/.test(g) ? Number(g[0]) : 1, turno: TURNOS[0] })));
  }

  _datosGrupo(semestre, turno) {
    const s = Number(String(semestre ?? '').trim());
    if (!Number.isInteger(s) || s < 1 || s > 9 || String(semestre ?? '').trim() === '') {
      throw new ErrorValidacion('FORMATO_INVALIDO', `el semestre debe ser un número del 1 al 9: ${semestre}`);
    }
    if (!TURNOS.includes(turno)) throw new ErrorValidacion('FORMATO_INVALIDO', `el turno debe ser ${TURNOS.join(' o ')}`);
    return [s, turno];
  }

  altaGrupo(clave_, semestre, turno) {
    v.campos({ clave: clave_ }, 'clave');
    const c = v.formato(v.texto(clave_, 'clave'), v.RE_GRUPO, 'clave del grupo (3 dígitos)');
    if (this.grupos().includes(c)) throw new ErrorValidacion('ID_DUPLICADO', `el grupo ${c} ya existe`);
    const [s, t] = this._datosGrupo(semestre, turno);
    this.gruposRegistro.set(c, Object.freeze({ clave: c, semestre: s, turno: t }));
    this.bus.emitir('grupo_registrado', { detalle: `${c} · ${s}.º semestre · ${t}` });
    return this.gruposRegistro.get(c);
  }

  editarGrupo(c, semestre, turno) {
    if (!this.gruposRegistro.has(c)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el grupo ${c}`);
    const [s, t] = this._datosGrupo(semestre, turno);
    this.gruposRegistro.set(c, Object.freeze({ clave: c, semestre: s, turno: t }));
    this.bus.emitir('grupo_actualizado', { detalle: `${c} · ${s}.º semestre · ${t}` });
    return this.gruposRegistro.get(c);
  }

  bajaGrupo(c) {
    if (!this.gruposRegistro.has(c)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el grupo ${c}`);
    if ([...this.estudiantes.values()].some((e) => e.grupo === c) || [...this.cursos.values()].some((x) => x.grupo === c)) {
      throw new ErrorValidacion('OPERACION_NO_PERMITIDA', `el grupo ${c} tiene alumnos o materias; primero dalos de baja`);
    }
    this.gruposRegistro.delete(c);
    this.bus.emitir('grupo_baja', { detalle: c });
  }

  _exigirGrupo(grupo) {
    if (!this.gruposRegistro.has(grupo)) {
      throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `el grupo ${grupo} no está registrado; regístralo primero`);
    }
  }

  // ----- estudiantes -----
  _datosPersona(apellidos, nombre, grupo) {
    v.campos({ apellidos, nombre, grupo }, 'apellidos', 'nombre', 'grupo');
    return [v.formato(v.texto(apellidos, 'apellidos'), v.RE_NOMBRE, 'apellidos'),
      v.formato(v.texto(nombre, 'nombre'), v.RE_NOMBRE, 'nombre'),
      v.formato(v.texto(grupo, 'grupo'), v.RE_GRUPO, 'grupo')];
  }

  _inscribirEnSuGrupo(nc) {
    const grupo = this.estudiantes.get(nc).grupo;
    const nuevos = [...this.cursos.values()].filter((c) => c.grupo === grupo && !this.inscripciones.has(clave(nc, c.id)));
    nuevos.forEach((c) => this.inscripciones.add(clave(nc, c.id)));
    this._cambio();
    return nuevos.length;
  }

  _evaluacionesDeCurso(cursoId) {
    return new Set(this.parcialesDe(cursoId).flatMap((p) => this.evaluacionesDe(p.id)).map((e) => e.id));
  }

  /** Quita la inscripción y las calificaciones de ese curso; devuelve cuántas calificaciones se borraron. */
  _quitarInscripcion(nc, cursoId) {
    const evals = this._evaluacionesDeCurso(cursoId);
    const borrar = [...this.calificaciones.keys()].filter((k) => k.split('|')[0] === nc && evals.has(k.split('|')[1]));
    this.inscripciones.delete(clave(nc, cursoId));
    borrar.forEach((k) => this.calificaciones.delete(k));
    this._cambio();
    return borrar.length;
  }

  /** El número de cuenta se asigna solo; el alumno queda inscrito en todas las materias de su grupo. */
  altaEstudiante(apellidos, nombre, grupo, inscribir = true) {
    const [a, n, g] = this._datosPersona(apellidos, nombre, grupo);
    this._exigirGrupo(g);
    const est = this.registrarEstudiante({ num_cuenta: this.siguienteCuenta(), apellidos: a, nombre: n, grupo: g });
    const total = inscribir ? this._inscribirEnSuGrupo(est.num_cuenta) : 0;
    if (total) this.bus.emitir('inscripcion_registrada', { detalle: `${est.num_cuenta} → ${total} materias del grupo ${g}` });
    return [est, total];
  }

  /** Cambiar de grupo da de baja sus materias anteriores (con sus calificaciones) y lo inscribe en las del nuevo. */
  editarEstudiante(nc, apellidos, nombre, grupo) {
    if (!this.estudiantes.has(nc)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el estudiante ${nc}`);
    const [a, n, g] = this._datosPersona(apellidos, nombre, grupo);
    this._exigirGrupo(g);
    const anterior = this.estudiantes.get(nc);
    this.estudiantes.set(nc, Object.freeze({ num_cuenta: nc, apellidos: a, nombre: n, grupo: g }));
    if (anterior.grupo !== g) {
      this.listaInscripciones().filter(([x]) => x === nc).forEach(([, c]) => this._quitarInscripcion(nc, c));
      this._inscribirEnSuGrupo(nc);
    }
    this._cambio();
    this.bus.emitir('estudiante_actualizado', { detalle: `${nc} · ${a} ${n} · grupo ${g}` });
    return this.estudiantes.get(nc);
  }

  bajaEstudiante(nc) {
    if (!this.estudiantes.has(nc)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el estudiante ${nc}`);
    this.listaInscripciones().filter(([x]) => x === nc).forEach(([, c]) => this._quitarInscripcion(nc, c));
    const est = this.estudiantes.get(nc);
    this.estudiantes.delete(nc);
    this._cambio();
    this.bus.emitir('estudiante_baja', { detalle: `${nc} · ${est.apellidos} ${est.nombre}` });
  }

  // ----- materias -----
  _validarPesosParciales(pesos) {
    if (pesos.length !== 3) throw new ErrorValidacion('PORCENTAJES_INVALIDOS', 'se requieren exactamente 3 parciales');
    const valores = pesos.map((p, i) => v.peso(p, `peso del parcial ${i + 1}`));
    if (normalizar(suma(valores)) !== 1) {
      throw new ErrorValidacion('PORCENTAJES_INVALIDOS', `los 3 parciales deben sumar 100 % (suman ${Math.round(suma(valores) * 100)} %)`);
    }
    return valores;
  }

  _validarMateria(asignatura, grupo, excluir = null) {
    v.campos({ asignatura, grupo }, 'asignatura', 'grupo');
    const a = v.texto(asignatura, 'asignatura');
    const g = v.formato(v.texto(grupo, 'grupo'), v.RE_GRUPO, 'grupo');
    if ([...this.cursos.values()].some((c) => c.id !== excluir && c.asignatura.toLowerCase() === a.toLowerCase() && c.grupo === g)) {
      throw new ErrorValidacion('ID_DUPLICADO', `${a} ya existe en el grupo ${g}`);
    }
    return [a, g];
  }

  _exigirDocente(docenteId) {
    if (docenteId && !this.docentes.has(docenteId)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el docente ${docenteId}`);
  }

  /** Crea la materia con sus 3 parciales, le asigna docente e inscribe al grupo completo. */
  altaCurso(asignatura, grupo, pesos = PESOS_PARCIALES, docenteId = null) {
    const [a, g] = this._validarMateria(asignatura, grupo);
    this._exigirGrupo(g);
    const valores = this._validarPesosParciales(pesos);
    this._exigirDocente(docenteId);
    const curso = Object.freeze({ id: GestorEscolar._siguiente('C', this.cursos.keys()), asignatura: a, grupo: g });
    this.cursos.set(curso.id, curso);
    valores.forEach((peso, i) => {
      const id = GestorEscolar._siguiente('P', this.parciales.keys());
      this.parciales.set(id, Object.freeze({ id, curso_id: curso.id, numero: i + 1, peso }));
    });
    if (docenteId) this.asignaciones.add(clave(docenteId, curso.id));
    const alumnos = [...this.estudiantes.values()].filter((e) => e.grupo === g);
    alumnos.forEach((e) => this.inscripciones.add(clave(e.num_cuenta, curso.id)));
    this._cambio();
    this.bus.emitir('curso_registrado', { detalle: `${curso.id} · ${a} · grupo ${g} · ${alumnos.length} inscritos` });
    return [curso, alumnos.length];
  }

  editarCurso(cursoId, asignatura, pesos, docenteId) {
    if (!this.cursos.has(cursoId)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el curso ${cursoId}`);
    const actual = this.cursos.get(cursoId);
    const [a] = this._validarMateria(asignatura, actual.grupo, cursoId);
    const valores = this._validarPesosParciales(pesos);
    this._exigirDocente(docenteId);
    this.cursos.set(cursoId, Object.freeze({ id: cursoId, asignatura: a, grupo: actual.grupo }));
    this.parcialesDe(cursoId).forEach((p, i) => this.parciales.set(p.id, Object.freeze({ ...p, peso: valores[i] })));
    this.asignaciones = new Set([...this.asignaciones].filter((x) => x.split('|')[1] !== cursoId));
    if (docenteId) this.asignaciones.add(clave(docenteId, cursoId));
    this._cambio();
    this.bus.emitir('curso_actualizado', { detalle: `${cursoId} · ${a}` });
    return this.cursos.get(cursoId);
  }

  /** Solo se elimina una materia sin calificaciones capturadas. */
  eliminarCurso(cursoId) {
    if (!this.cursos.has(cursoId)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el curso ${cursoId}`);
    const evals = this._evaluacionesDeCurso(cursoId);
    if ([...this.calificaciones.keys()].some((k) => evals.has(k.split('|')[1]))) {
      throw new ErrorValidacion('OPERACION_NO_PERMITIDA', `${cursoId} ya tiene calificaciones capturadas`);
    }
    evals.forEach((e) => this.evaluaciones.delete(e));
    this.parcialesDe(cursoId).forEach((p) => this.parciales.delete(p.id));
    this.inscripciones = new Set([...this.inscripciones].filter((x) => x.split('|')[1] !== cursoId));
    this.asignaciones = new Set([...this.asignaciones].filter((x) => x.split('|')[1] !== cursoId));
    const curso = this.cursos.get(cursoId);
    this.cursos.delete(cursoId);
    this._cambio();
    this.bus.emitir('curso_eliminado', { detalle: `${cursoId} · ${curso.asignatura}` });
  }

  // ----- docentes e inscripciones -----
  altaDocente(nombre) { return this.registrarDocente({ id: this.siguienteDocente(), nombre }); }

  /** Deja al docente exactamente con esos cursos (cada curso tiene un solo docente). */
  asignarCursos(docenteId, cursosIds) {
    if (!this.docentes.has(docenteId)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el docente ${docenteId}`);
    const ids = new Set(cursosIds);
    const faltan = [...ids].filter((c) => !this.cursos.has(c)).sort(comparar);
    if (faltan.length) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existen los cursos ${faltan.join(', ')}`);
    this.asignaciones = new Set([...[...this.asignaciones].filter((x) => { const [d, c] = x.split('|'); return d !== docenteId && !ids.has(c); }),
      ...[...ids].map((c) => clave(docenteId, c))]);
    this.bus.emitir('docente_asignado', { detalle: `${docenteId} → ${ids.size} cursos` });
    return ids.size;
  }

  /** Altas y bajas individuales (excepciones). Devuelve [altas, bajas]. */
  actualizarInscripciones(nc, cursosIds) {
    if (!this.estudiantes.has(nc)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el estudiante ${nc}`);
    const grupo = this.estudiantes.get(nc).grupo;
    const ids = new Set(cursosIds);
    [...ids].forEach((c) => {
      if (!this.cursos.has(c)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el curso ${c}`);
      if (this.cursos.get(c).grupo !== grupo) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `${c} no pertenece al grupo ${grupo}`);
    });
    const actuales = new Set(this.listaInscripciones().filter(([x]) => x === nc).map(([, c]) => c));
    const altas = [...ids].filter((c) => !actuales.has(c));
    const bajas = [...actuales].filter((c) => !ids.has(c));
    altas.forEach((c) => this.inscripciones.add(clave(nc, c)));
    bajas.forEach((c) => this._quitarInscripcion(nc, c));
    this._cambio();
    if (altas.length || bajas.length) this.bus.emitir('inscripcion_actualizada', { detalle: `${nc} · ${altas.length} altas, ${bajas.length} bajas` });
    return [altas.length, bajas.length];
  }

  /** Inscribe a cada alumno del grupo en las materias del grupo que le falten. */
  inscribirGrupo(grupo) {
    const total = [...this.estudiantes.values()].filter((e) => e.grupo === grupo)
      .reduce((t, e) => t + this._inscribirEnSuGrupo(e.num_cuenta), 0);
    this.bus.emitir('inscripcion_registrada', { detalle: `grupo ${grupo} · ${total} inscripciones nuevas` });
    return total;
  }

  // ----- plan de evaluación y acta -----
  _validarPlan(parcialId, rubros) {
    if (!this.parciales.has(parcialId)) throw new ErrorValidacion('REFERENCIA_INEXISTENTE', `no existe el parcial ${parcialId}`);
    const numero = this.parciales.get(parcialId).numero;
    if (!rubros.length) throw new ErrorValidacion('CAMPO_FALTANTE', `parcial ${numero}: agrega al menos una evaluación`);
    const actuales = new Set(this.evaluacionesDe(parcialId).map((e) => e.id));
    const limpios = rubros.reduce((acc, r) => {
      let nombre;
      let peso;
      try {
        v.campos(r, 'nombre', 'peso');
        nombre = v.texto(r.nombre, 'nombre');
        peso = v.peso(r.peso, `peso de ${nombre}`);
      } catch (e) {
        if (!(e instanceof ErrorValidacion)) throw e;
        throw new ErrorValidacion(e.codigo, `parcial ${numero}: ${e.detalle}`);
      }
      if (acc.some((x) => x.nombre.toLowerCase() === nombre.toLowerCase())) {
        throw new ErrorValidacion('ID_DUPLICADO', `parcial ${numero}: ${nombre} está repetida`);
      }
      return [...acc, { id: actuales.has(r.id) ? r.id : null, nombre, peso }];
    }, []);
    const total = normalizar(suma(limpios.map((x) => x.peso)));
    if (total !== 1) {
      throw new ErrorValidacion('PORCENTAJES_INVALIDOS', `parcial ${numero}: las evaluaciones deben sumar 100 % (suman ${Number((total * 100).toFixed(6))} %)`);
    }
    return limpios;
  }

  definirPlan(parcialId, rubros) { return this.definirPlanes({ [parcialId]: rubros }); }

  /** Guarda el plan de varios parciales a la vez: primero se validan todos y solo entonces se aplican. */
  definirPlanes(planes) {
    const validados = Object.entries(planes).map(([pid, rubros]) => [pid, this._validarPlan(pid, rubros)]);
    const borradas = validados.reduce((total, [pid, limpios]) => {
      const conservados = new Set(limpios.filter((x) => x.id).map((x) => x.id));
      const quitar = this.evaluacionesDe(pid).filter((e) => !conservados.has(e.id)).map((e) => e.id);
      const notas = [...this.calificaciones.keys()].filter((k) => quitar.includes(k.split('|')[1]));
      quitar.forEach((e) => this.evaluaciones.delete(e));
      notas.forEach((k) => this.calificaciones.delete(k));
      limpios.forEach((x) => {
        const id = x.id ?? GestorEscolar._siguiente('EV', this.evaluaciones.keys());
        this.evaluaciones.set(id, Object.freeze({ id, parcial_id: pid, nombre: x.nombre, peso: x.peso }));
      });
      this._cambio();
      return total + notas.length;
    }, 0);
    this.bus.emitir('plan_actualizado', { detalle: `${validados.map(([p]) => p).join(', ')} · ${suma(validados.map(([, l]) => l.length))} evaluaciones` });
    return borradas;
  }

  /**
   * Captura por lote (acta): [[num_cuenta, evaluacion_id, valor | null]]. null borra una calificación existente.
   * Los renglones con error no detienen a los demás y se devuelven juntos.
   */
  guardarActa(cambios) {
    const resultado = cambios.reduce((r, [nc, eid, valor]) => {
      if (valor === null || valor === undefined) {
        return this.calificaciones.delete(clave(nc, eid)) ? { ...r, borradas: r.borradas + 1 } : r;
      }
      try {
        const [n, e, x] = this._validarCalificacion({ num_cuenta: nc, evaluacion_id: eid, valor });
        const tipo = this.calificaciones.has(clave(n, e)) ? 'modificadas' : 'capturadas';
        this.calificaciones.set(clave(n, e), x);
        return { ...r, [tipo]: r[tipo] + 1 };
      } catch (err) {
        if (!(err instanceof ErrorValidacion)) throw err;
        const registro = { num_cuenta: nc, evaluacion_id: eid, valor };
        this.errores.push({ caso: null, tipo: 'acta', codigo: err.codigo, detalle: err.detalle, registro });
        return { ...r, errores: [...r.errores, { ...registro, codigo: err.codigo, detalle: err.detalle }] };
      }
    }, { capturadas: 0, modificadas: 0, borradas: 0, errores: [] });
    this.bus.emitir('acta_guardada', { detalle: `${resultado.capturadas} nuevas, ${resultado.modificadas} modificadas, ${resultado.borradas} borradas, ${resultado.errores.length} con error` });
    return resultado;
  }

  // ---------- respaldo (sin validar: el estado guardado ya pasó por las validaciones) ----------
  restaurar(d) {
    const congelar = (x) => Object.freeze({ ...x });
    this.estudiantes = new Map((d.estudiantes ?? []).map((r) => [r.num_cuenta, congelar(r)]));
    this.cursos = new Map((d.cursos ?? []).map((r) => [r.id, congelar(r)]));
    this.parciales = new Map((d.parciales ?? []).map((r) => [r.id, congelar({ ...r, numero: Number(r.numero), peso: Number(r.peso) })]));
    this.evaluaciones = new Map((d.evaluaciones ?? []).map((r) => [r.id, congelar({ ...r, peso: Number(r.peso) })]));
    this.inscripciones = new Set((d.inscripciones ?? []).map((r) => clave(r.num_cuenta, r.curso_id)));
    this.calificaciones = new Map((d.calificaciones ?? []).map((r) => [clave(r.num_cuenta, r.evaluacion_id), Number(r.valor)]));
    this.docentes = new Map((d.docentes ?? []).map((r) => [r.id, congelar(r)]));
    this.asignaciones = new Set((d.asignaciones ?? []).map((r) => clave(r.docente_id, r.curso_id)));
    this.gruposRegistro = new Map((d.grupos ?? []).map((r) => [r.clave, congelar({ ...r, semestre: Number(r.semestre) })]));
    this._sincronizarGrupos();
    this._cambio();
    this.bus.emitir('datos_restaurados', { detalle: `${this.estudiantes.size} estudiantes, ${this.cursos.size} cursos` });
  }

  // ---------- carga masiva ----------
  cargar(datos) {
    const procesarTodos = (tipo, lista, accion) => (lista ?? []).forEach((r) => this._procesar(tipo, r, accion));
    procesarTodos('grupo', datos.grupos, (r) => this.altaGrupo(r?.clave, r?.semestre, r?.turno));
    procesarTodos('estudiante', datos.estudiantes, (r) => this.registrarEstudiante(r));
    procesarTodos('curso', datos.cursos, (r) => this.registrarCurso(r));
    this._cargarParciales(datos.parciales ?? []);
    this._cargarEvaluaciones(datos.evaluaciones ?? []);
    procesarTodos('inscripcion', datos.inscripciones, (r) => this.inscribir(r));
    procesarTodos('calificacion', datos.calificaciones, (r) => this.capturarCalificacion(r));
    procesarTodos('docente', datos.docentes, (r) => this.registrarDocente(r));
    procesarTodos('asignacion', datos.asignaciones, (r) => this.asignarDocente(r));
    const acciones = {
      modificar_calificacion: (r) => this.modificarCalificacion(r),
      consultar_kardex: (r) => this.consultarKardex(r.num_cuenta),
    };
    (datos.operaciones ?? [])
      .filter((r) => r && typeof r === 'object' && acciones[r.tipo])
      .forEach((r) => this._procesar(r.tipo, r, acciones[r.tipo]));
    this._sincronizarGrupos();
    this.bus.emitir('datos_cargados', { detalle: `${this.estudiantes.size} estudiantes, ${this.errores.length} errores` });
  }

  /** Registro que representa al grupo en el error: el primero que trae "caso", si alguno lo trae. */
  static _representante(lista) {
    return (lista.find(([r]) => r && typeof r === 'object' && r.caso) ?? lista[0])[0];
  }

  _cargarParciales(registros) {
    const porCurso = registros.reduce((m, r) => {
      try {
        const p = this._validarParcial(r);
        if ([...m.values()].flat().some(([, x]) => x.id === p.id)) throw new ErrorValidacion('ID_DUPLICADO', `el parcial ${p.id} ya existe`);
        return m.set(p.curso_id, [...(m.get(p.curso_id) ?? []), [r, p]]);
      } catch (e) {
        if (!(e instanceof ErrorValidacion)) throw e;
        this._error('parcial', r, e);
        return m;
      }
    }, new Map());
    porCurso.forEach((lista, cursoId) => {
      const parciales = lista.map(([, p]) => p);
      const numeros = parciales.map((p) => p.numero).sort().join();
      const total = normalizar(suma(parciales.map((p) => p.peso)));
      if (numeros !== '1,2,3' || total !== 1) {
        this._error('parcial', GestorEscolar._representante(lista), new ErrorValidacion(
          'PORCENTAJES_INVALIDOS', `los parciales de ${cursoId} deben ser 3 y sumar 1.0 (suman ${total})`));
        return;
      }
      parciales.forEach((p) => {
        this.parciales.set(p.id, p);
        this.bus.emitir('parcial_registrado', { detalle: `${p.id} · ${p.curso_id} · peso ${p.peso}` });
      });
    });
  }

  _cargarEvaluaciones(registros) {
    const porParcial = registros.reduce((m, r) => {
      try {
        const e = this._validarEvaluacion(r);
        if ((m.get(e.parcial_id) ?? []).some(([, x]) => x.nombre.toLowerCase() === e.nombre.toLowerCase())) {
          throw new ErrorValidacion('ID_DUPLICADO', `${e.nombre} repetida en ${e.parcial_id}`);
        }
        if ([...m.values()].flat().some(([, x]) => x.id === e.id)) throw new ErrorValidacion('ID_DUPLICADO', `la evaluación ${e.id} ya existe`);
        return m.set(e.parcial_id, [...(m.get(e.parcial_id) ?? []), [r, e]]);
      } catch (err) {
        if (!(err instanceof ErrorValidacion)) throw err;
        this._error('evaluacion', r, err);
        return m;
      }
    }, new Map());
    porParcial.forEach((lista, parcialId) => {
      const total = normalizar(suma(lista.map(([, e]) => e.peso)));
      if (total !== 1) {
        this._error('evaluacion', GestorEscolar._representante(lista), new ErrorValidacion(
          'PORCENTAJES_INVALIDOS', `las evaluaciones de ${parcialId} suman ${total} y deben sumar 1.0`));
        return;
      }
      lista.forEach(([, e]) => {
        this.evaluaciones.set(e.id, e);
        this.bus.emitir('evaluacion_registrada', { detalle: `${e.id} · ${e.nombre} · ${e.parcial_id}` });
      });
    });
  }

  aObjeto() {
    return {
      grupos: [...this.gruposRegistro.values()].sort((a, b) => comparar(a.clave, b.clave)),
      ...super.aObjeto(),
      docentes: [...this.docentes.values()],
      asignaciones: [...this.asignaciones].sort(comparar).map((a) => ({ docente_id: a.split('|')[0], curso_id: a.split('|')[1] })),
    };
  }
}
