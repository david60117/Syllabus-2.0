/**
 * Reportes institucionales, como en un sistema de control escolar real. Cada reporte es un documento
 * (objeto inmutable) con encabezado —institución, programa, ciclo, título, alcance, fecha de emisión y quién lo
 * emite—, cuerpo en tabla, resumen y, si aplica, firmas y nota. Se construyen con map/filter/reduce.
 *
 *   Por alumno:   boleta de calificaciones · kárdex (historial individual)
 *   Por materia:  acta de calificaciones
 *   Por grupo:    concentrado de calificaciones
 *   Control:      aprovechamiento · riesgo académico · aprobados y reprobados · usuarios y contraseñas
 */
import { calificacionFinal, claveTexto, compararTuplas, estadisticas, evaluarEstatusCurso, mostrar, ordenar, promedioFinales }
  from './calculos.js';
import { contrasenaVisible } from './autenticacion.js';

export const ESTADOS = { APROBADO: 'Aprobado', REPROBADO: 'Reprobado', PENDIENTE: 'Pendiente' };
const RIESGOS = { ALTO: 'Alto', MEDIO: 'Medio', SIN_RIESGO: 'Sin riesgo' };
const riesgo = (r) => (r === null || r === undefined ? 'Pendiente' : RIESGOS[r] ?? r);

export const cicloActual = (hoy = new Date()) => (hoy.getMonth() < 6 ? `Enero – Junio ${hoy.getFullYear()}` : `Julio – Diciembre ${hoy.getFullYear()}`);

export const NOTA_REDONDEO = 'Parciales y promedio con dos decimales. Calificación final asentada: si el promedio es 7.0 o más se '
  + 'redondea al entero más próximo (.5 sube); si es menor a 7.0 no se redondea hacia arriba (6.9 queda en 6) y la '
  + 'materia queda reprobada (examen extraordinario).';

export const INSTITUCION = Object.freeze({ institucion: 'Institución educativa', programa: 'Licenciatura en Ingeniería de Software', ciclo: cicloActual() });

/** reporte → [título, alcance, roles que lo pueden emitir] */
export const CATALOGO = Object.freeze({
  boleta: ['Boleta de calificaciones', 'alumno', ['Administrador', 'Alumno']],
  kardex: ['Kárdex académico', 'alumno', ['Administrador', 'Alumno']],
  acta: ['Acta de calificaciones', 'materia', ['Administrador', 'Docente']],
  concentrado: ['Concentrado de calificaciones por grupo', 'grupo', ['Administrador']],
  aprovechamiento: ['Aprovechamiento por grupo y asignatura', 'grupo_opcional', ['Administrador', 'Docente']],
  riesgo: ['Alumnos en riesgo académico', 'grupo_opcional', ['Administrador']],
  aprobacion: ['Aprobados y reprobados', 'materia_opcional', ['Administrador', 'Docente']],
  credenciales: ['Usuarios y contraseñas', 'usuarios', ['Administrador']],
});

const m = (x) => (x === null || x === undefined ? null : mostrar(x));
const dos = (n) => String(n).padStart(2, '0');
const emision = (d = new Date()) => `${dos(d.getDate())}/${dos(d.getMonth() + 1)}/${d.getFullYear()} ${dos(d.getHours())}:${dos(d.getMinutes())}`;
const pct = (x) => (x === null || x === undefined ? null : `${x} %`);

function documento(tipo, institucion, campos, columnas, filas, resumen, emitidoPor, firmas = [], nota = '') {
  const inst = { ...INSTITUCION, ...(institucion ?? {}) };
  return Object.freeze({
    tipo, titulo: CATALOGO[tipo][0],
    encabezado: { institucion: inst.institucion, programa: inst.programa, ciclo: inst.ciclo, titulo: CATALOGO[tipo][0],
      campos, emision: emision(), emitido_por: emitidoPor ?? '' },
    columnas, filas, resumen, firmas, nota,
  });
}

const ponderacion = (g, cursoId) => g.parcialesDe(cursoId).map((p) => `P${p.numero} ${+(p.peso * 100).toFixed(6)} %`).join(' / ') || '—';
const docente = (g, cursoId) => g.docenteDe(cursoId)?.nombre ?? 'Sin asignar';
const parciales = (ps) => Object.fromEntries([...ps, null, null, null].slice(0, 3).map((x, i) => [`p${i + 1}`, m(x)]));
const datosAlumno = (r) => [['Alumno', `${r.apellidos} ${r.nombre}`], ['Número de cuenta', r.num_cuenta], ['Grupo', r.grupo]];

function resumenAlumno(r) {
  const cerrados = r.cursos.filter((c) => c.bruto !== null);
  const aprobadas = cerrados.filter((c) => c.estatus === 'APROBADO').length;
  return [['Promedio general', m(r.general)], ['Materias aprobadas', aprobadas], ['Materias reprobadas', cerrados.length - aprobadas],
    ['Materias pendientes', r.pendientes], ['Riesgo académico', riesgo(r.riesgo)]];
}

// ---------------------------------------------------------------- por alumno
/** Calificaciones del alumno en el periodo: parciales, promedio y final de cada materia. */
export function boleta(g, numCuenta, institucion, emitidoPor = '') {
  const r = g.resumenEstudiante(numCuenta);
  const filas = r.cursos.map((c) => ({ clave: c.curso_id, asignatura: c.asignatura, docente: docente(g, c.curso_id), ...parciales(c.parciales),
    promedio: m(c.bruto), final: calificacionFinal(c.bruto), estado: ESTADOS[c.estatus] }));
  const columnas = [['clave', 'Clave'], ['asignatura', 'Asignatura'], ['docente', 'Docente'], ['p1', 'P1'], ['p2', 'P2'], ['p3', 'P3'],
    ['promedio', 'Promedio'], ['final', 'Final'], ['estado', 'Estado']];
  return documento('boleta', institucion, datosAlumno(r), columnas, filas, resumenAlumno(r), emitidoPor, [],
    `${NOTA_REDONDEO} Las materias pendientes aún tienen evaluaciones sin capturar.`);
}

/** Historial académico individual: solo las materias y calificaciones de ese alumno. */
export function kardex(g, numCuenta, institucion, emitidoPor = '') {
  const inst = { ...INSTITUCION, ...(institucion ?? {}) };
  const r = g.resumenEstudiante(numCuenta);
  const filas = r.cursos.map((c, i) => ({ no: i + 1, clave: c.curso_id, asignatura: c.asignatura, ciclo: inst.ciclo, tipo: 'Ordinario',
    promedio: m(c.bruto), final: calificacionFinal(c.bruto), estado: c.bruto === null ? 'En curso' : ESTADOS[c.estatus] }));
  const columnas = [['no', 'No.'], ['clave', 'Clave'], ['asignatura', 'Asignatura'], ['ciclo', 'Ciclo'], ['tipo', 'Tipo de evaluación'],
    ['promedio', 'Promedio'], ['final', 'Calificación final'], ['estado', 'Estado']];
  const reprobadas = r.cursos.filter((c) => c.bruto !== null && c.estatus === 'REPROBADO').length;
  const resumen = [['Materias cursadas', r.cursos.length], ['Promedio de calificaciones finales', promedioFinales(r.cursos.map((c) => c.bruto))],
    ...resumenAlumno(r).filter(([k]) => k !== 'Promedio general')];
  return documento('kardex', institucion, [...datosAlumno(r), ['Situación', reprobadas ? 'Irregular' : 'Regular']], columnas, filas, resumen,
    emitidoPor, ['Control escolar'], `${NOTA_REDONDEO} Documento informativo; no sustituye al certificado oficial de estudios.`);
}

// ---------------------------------------------------------------- por materia
/** Acta de calificaciones de una materia: todos sus alumnos con parciales, promedio, final y estado. */
export function acta(g, cursoId, institucion, emitidoPor = '') {
  const c = g.cursos.get(cursoId);
  const filas = g.alumnosDeCurso(cursoId).map((e, i) => {
    const bruto = g.promedioCurso(e.num_cuenta, cursoId);
    return { no: i + 1, num_cuenta: e.num_cuenta, alumno: `${e.apellidos} ${e.nombre}`,
      ...parciales(g.parcialesDe(cursoId).map((p) => g.califParcial(e.num_cuenta, p.id))),
      promedio: m(bruto), final: calificacionFinal(bruto), estado: ESTADOS[bruto === null ? 'PENDIENTE' : evaluarEstatusCurso(bruto)], bruto };
  });
  const est = estadisticas(filas.filter((f) => f.bruto !== null).map((f) => f.bruto));
  const campos = [['Asignatura', c.asignatura], ['Clave', c.id], ['Grupo', c.grupo], ['Docente', docente(g, cursoId)],
    ['Ponderación', ponderacion(g, cursoId)]];
  const columnas = [['no', 'No.'], ['num_cuenta', 'Número de cuenta'], ['alumno', 'Nombre del alumno'], ['p1', 'P1'], ['p2', 'P2'],
    ['p3', 'P3'], ['promedio', 'Promedio'], ['final', 'Final'], ['estado', 'Estado']];
  const resumen = [['Inscritos', filas.length], ['Aprobados', est.aprobados], ['Reprobados', est.reprobados],
    ['Pendientes', filas.length - est.estudiantes], ['Promedio', est.promedio], ['Aprobación', pct(est.aprobacion)]];
  return documento('acta', institucion, campos, columnas, filas.map(({ bruto, ...f }) => f), resumen, emitidoPor,
    [`Docente: ${docente(g, cursoId)}`, 'Control escolar'], NOTA_REDONDEO);
}

// ---------------------------------------------------------------- por grupo
/** Sábana del grupo: una columna por materia con la calificación final de cada alumno. */
export function concentrado(g, grupo, institucion, emitidoPor = '', orden = 'apellidos') {
  const cursos = g.cursosDeGrupo(grupo);
  const resumenes = [...g.estudiantes.values()].filter((e) => e.grupo === grupo).map((e) => g.resumenEstudiante(e.num_cuenta));
  const base = resumenes.map((r) => ({ num_cuenta: r.num_cuenta, apellidos: r.apellidos, nombre: r.nombre,
    ...Object.fromEntries(cursos.map((c) => [c.id, calificacionFinal(r.cursos.find((x) => x.curso_id === c.id)?.bruto ?? null)])),
    promedio: m(r.general), estado: ESTADOS[r.estatus], riesgo: riesgo(r.riesgo) }));
  const filas = (orden === 'promedio' ? ordenar(base, 'promedio', true) : ordenar(base))
    .map(({ num_cuenta, apellidos, nombre, ...resto }, i) => ({ no: i + 1, num_cuenta, alumno: `${apellidos} ${nombre}`, ...resto }));
  const columnas = [['no', 'No.'], ['num_cuenta', 'Número de cuenta'], ['alumno', 'Nombre del alumno'], ...cursos.map((c) => [c.id, c.id]),
    ['promedio', 'Promedio'], ['estado', 'Estado'], ['riesgo', 'Riesgo']];
  const est = estadisticas(resumenes.filter((r) => r.general !== null).map((r) => r.general));
  const resumen = [['Alumnos', filas.length], ['Materias', cursos.length], ['Promedio del grupo', est.promedio], ['Máxima', est.maximo],
    ['Mínima', est.minimo], ['Aprobación', pct(est.aprobacion)]];
  const datos = g.gruposRegistro?.get(grupo);
  const campos = [['Grupo', grupo], ...(datos ? [['Semestre', `${datos.semestre}.º`], ['Turno', datos.turno]] : []),
    ['Orden', orden === 'promedio' ? 'Por promedio (mayor a menor)' : 'Por apellidos']];
  return documento('concentrado', institucion, campos, columnas, filas, resumen, emitidoPor, [],
    `Materias — ${cursos.map((c) => `${c.id}: ${c.asignatura}`).join('   ')}. Por materia se muestra la calificación final asentada `
    + '(entera); el promedio usa los promedios con dos decimales de cada materia.');
}

// ---------------------------------------------------------------- control
const porGrupoYMateria = (a, b) => compararTuplas([a.grupo, claveTexto(a.asignatura)], [b.grupo, claveTexto(b.asignatura)]);

/** Estadísticas por materia (promedio, máxima, mínima y aprobación) y totales generales. */
export function aprovechamiento(g, cursos, institucion, emitidoPor = '', grupo = null) {
  const elegidos = [...cursos].filter((c) => grupo === null || c.grupo === grupo).sort(porGrupoYMateria);
  const finalesDe = (c) => g.alumnosDeCurso(c.id).map((e) => g.promedioCurso(e.num_cuenta, c.id)).filter((x) => x !== null);
  const filas = elegidos.map((c) => {
    const est = estadisticas(finalesDe(c));
    return { grupo: c.grupo, clave: c.id, asignatura: c.asignatura, docente: docente(g, c.id), inscritos: g.alumnosDeCurso(c.id).length,
      promedio: est.promedio, maximo: est.maximo, minimo: est.minimo, aprobados: est.aprobados, reprobados: est.reprobados, aprobacion: est.aprobacion };
  });
  const est = estadisticas(elegidos.flatMap(finalesDe));
  const columnas = [['grupo', 'Grupo'], ['clave', 'Clave'], ['asignatura', 'Asignatura'], ['docente', 'Docente'], ['inscritos', 'Insc.'],
    ['promedio', 'Prom.'], ['maximo', 'Máx.'], ['minimo', 'Mín.'], ['aprobados', 'Aprob.'], ['reprobados', 'Reprob.'], ['aprobacion', '% aprob.']];
  const resumen = [['Materias', filas.length], ['Calificaciones finales', est.estudiantes], ['Promedio general', est.promedio],
    ['Máxima', est.maximo], ['Mínima', est.minimo], ['Aprobación', pct(est.aprobacion)]];
  return documento('aprovechamiento', institucion, [['Grupo', grupo ?? 'Todos']], columnas, filas, resumen, emitidoPor);
}

export function riesgoAcademico(g, institucion, emitidoPor = '', grupo = null) {
  const resumenes = [...g.estudiantes.values()].filter((e) => grupo === null || e.grupo === grupo).map((e) => g.resumenEstudiante(e.num_cuenta));
  const base = resumenes.filter((r) => ['ALTO', 'MEDIO'].includes(r.riesgo)).map((r) => ({
    num_cuenta: r.num_cuenta, apellidos: r.apellidos, nombre: r.nombre, grupo: r.grupo, promedio: m(r.general), riesgo: riesgo(r.riesgo),
    motivo: r.motivo, reprobadas: r.cursos.filter((c) => c.bruto !== null && c.estatus === 'REPROBADO').map((c) => c.curso_id).join(', ') || '—' }));
  const filas = [...ordenar(base)].sort((a, b) => compararTuplas([a.grupo, a.riesgo !== 'Alto' ? 1 : 0], [b.grupo, b.riesgo !== 'Alto' ? 1 : 0]))
    .map(({ apellidos, nombre, num_cuenta, ...f }, i) => ({ no: i + 1, num_cuenta, alumno: `${apellidos} ${nombre}`, ...f }));
  const columnas = [['no', 'No.'], ['num_cuenta', 'Número de cuenta'], ['alumno', 'Nombre del alumno'], ['grupo', 'Grupo'], ['promedio', 'Promedio'],
    ['riesgo', 'Riesgo'], ['motivo', 'Motivo'], ['reprobadas', 'Materias reprobadas']];
  const resumen = [['Alumnos evaluados', resumenes.length], ['Riesgo alto', filas.filter((f) => f.riesgo === 'Alto').length],
    ['Riesgo medio', filas.filter((f) => f.riesgo === 'Medio').length]];
  return documento('riesgo', institucion, [['Grupo', grupo ?? 'Todos']], columnas, filas, resumen, emitidoPor, [],
    'Alto: alguna materia reprobada o promedio general menor a 7.0. Medio: promedio general entre 7.0 y 7.5.');
}

export function aprobacion(g, cursos, institucion, emitidoPor = '', cursoId = null, estado = 'Todos') {
  const elegidos = [...cursos].filter((c) => cursoId === null || c.id === cursoId);
  const filas = [...elegidos].sort(porGrupoYMateria).flatMap((c) => g.alumnosDeCurso(c.id).map((e) => {
    const bruto = g.promedioCurso(e.num_cuenta, c.id);
    return { grupo: c.grupo, asignatura: c.asignatura, num_cuenta: e.num_cuenta, alumno: `${e.apellidos} ${e.nombre}`,
      promedio: m(bruto), final: calificacionFinal(bruto), estado: ESTADOS[bruto === null ? 'PENDIENTE' : evaluarEstatusCurso(bruto)] };
  })).filter((f) => estado === 'Todos' || f.estado === estado);
  const columnas = [['grupo', 'Grupo'], ['asignatura', 'Asignatura'], ['num_cuenta', 'Número de cuenta'], ['alumno', 'Nombre del alumno'],
    ['promedio', 'Promedio'], ['final', 'Final'], ['estado', 'Estado']];
  const resumen = [['Registros', filas.length], ...['Aprobado', 'Reprobado', 'Pendiente'].map((e) => [e, filas.filter((f) => f.estado === e).length])];
  const materia = elegidos.find((c) => c.id === cursoId);
  return documento('aprobacion', institucion, [['Materia', materia ? `${materia.id} · ${materia.asignatura} (${materia.grupo})` : 'Todas'],
    ['Estado', estado]], columnas, filas, resumen, emitidoPor);
}

/** Usuarios y contraseñas por grupo (alumnos), de docentes o de todos; solo se muestra la contraseña inicial. */
export function credenciales(g, usuarios, filtro = 'Todos', institucion, emitidoPor = '') {
  const ORDEN = { Administrador: 0, Docente: 1, Alumno: 2 };
  const base = usuarios.map((u) => {
    const e = u.rol === 'Alumno' ? g.estudiantes.get(u.referencia) : null;
    const detalle = u.rol === 'Alumno' ? (e?.grupo ?? '—')
      : u.rol === 'Docente' ? (g.cursosDeDocente(u.referencia).map((c) => c.id).join(', ') || 'Sin materias') : 'Control escolar';
    const visible = contrasenaVisible(u);
    return { usuario: u.usuario, nombre: u.nombre, rol: u.rol, detalle, grupo: e?.grupo ?? null,
      contrasena: visible ?? 'Personalizada', estado: visible ? 'Contraseña inicial' : 'Activa' };
  }).filter((f) => !(filtro === 'Docentes' && f.rol !== 'Docente') && !(filtro.startsWith('Grupo ') && f.grupo !== filtro.slice(6)));
  const filas = [...base].sort((a, b) => compararTuplas([ORDEN[a.rol], a.rol === 'Alumno' ? a.detalle : '', claveTexto(a.nombre)],
    [ORDEN[b.rol], b.rol === 'Alumno' ? b.detalle : '', claveTexto(b.nombre)]))
    .map(({ grupo, ...f }, i) => ({ no: i + 1, ...f }));
  const columnas = [['no', 'No.'], ['usuario', 'Usuario'], ['nombre', 'Nombre'], ['rol', 'Rol'], ['detalle', 'Grupo / materias'],
    ['contrasena', 'Contraseña'], ['estado', 'Estado']];
  const iniciales = filas.filter((f) => f.estado === 'Contraseña inicial').length;
  return documento('credenciales', institucion, [['Usuarios', filtro]], columnas, filas,
    [['Cuentas', filas.length], ['Con contraseña inicial', iniciales], ['Personalizadas', filas.length - iniciales]], emitidoPor,
    ['Control escolar'], 'CONFIDENCIAL. Entregar a cada usuario solo su renglón. La contraseña inicial se debe cambiar al primer ingreso; '
    + 'las contraseñas personalizadas están cifradas y no se pueden consultar (se restablecen en Usuarios).');
}

// ---------------------------------------------------------------- exportación
const sinAcentos = (k) => claveTexto(k).replace(/ /g, '_');

/** El documento como renglones de CSV: encabezado, tabla, resumen y firmas (lo que se ve impreso). */
export function aTabla(doc) {
  const e = doc.encabezado;
  return [[e.institucion], [e.programa], [e.titulo.toUpperCase()], ['Ciclo escolar', e.ciclo], ...e.campos,
    ['Fecha de emisión', e.emision], ['Emitido por', e.emitido_por], [],
    doc.columnas.map(([, t]) => t), ...doc.filas.map((f) => doc.columnas.map(([k]) => f[k] ?? null)),
    [], ['RESUMEN'], ...doc.resumen,
    ...(doc.firmas.length ? [[], ...doc.firmas.map((f) => [f, '_______________________________'])] : []),
    ...(doc.nota ? [[], [doc.nota]] : [])];
}

export const aJson = (doc) => ({
  encabezado: { institucion: doc.encabezado.institucion, programa: doc.encabezado.programa, reporte: doc.encabezado.titulo,
    ciclo_escolar: doc.encabezado.ciclo, ...Object.fromEntries(doc.encabezado.campos.map(([k, v]) => [sinAcentos(k), v])),
    fecha_emision: doc.encabezado.emision, emitido_por: doc.encabezado.emitido_por },
  columnas: doc.columnas.map(([clave, titulo]) => ({ clave, titulo })), registros: doc.filas,
  resumen: Object.fromEntries(doc.resumen.map(([k, v]) => [sinAcentos(k), v])), firmas: doc.firmas, nota: doc.nota,
});

const celdaCsv = (v) => {
  const s = v === null || v === undefined ? '' : String(v);
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};
export const aCsvDocumento = (doc) => `﻿${aTabla(doc).map((r) => r.map(celdaCsv).join(',')).join('\r\n')}\r\n`;

export function nombreArchivo(doc) {
  const campos = Object.fromEntries(doc.encabezado.campos);
  const sufijo = campos['Número de cuenta'] ?? campos.Clave ?? campos.Grupo ?? '';
  return `${doc.tipo}_${sufijo}`.replace(/_$/, '').replace(/ /g, '_');
}
