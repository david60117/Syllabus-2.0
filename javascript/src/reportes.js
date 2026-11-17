/** Reportes como transformaciones declarativas de los resúmenes por estudiante. */
import { estadisticas, mostrar, ordenar, comparar, compararTuplas } from './calculos.js';

/** Resumen de cada estudiante, ordenado por número de cuenta (lo usa también la interfaz web). */
export const calcularSecuencial = (gestor) =>
  [...gestor.estudiantes.keys()].sort(comparar).map((nc) => gestor.resumenEstudiante(nc));

const m = (x) => (x === null || x === undefined ? null : mostrar(x));
const agrupar = (xs, k) => xs.reduce((mapa, x) => mapa.set(k(x), [...(mapa.get(k(x)) ?? []), x]), new Map());

export const filaEstudiante = (r) => ({
  grupo: r.grupo, num_cuenta: r.num_cuenta, apellidos: r.apellidos, nombre: r.nombre,
  promedio: m(r.general), estatus: r.estatus, riesgo: r.riesgo ?? 'PENDIENTE', motivo: r.motivo,
});

export function resumenGrupos(resumenes) {
  const grupos = agrupar(resumenes, (r) => r.grupo);
  return [...grupos.keys()].sort(comparar).map((g) => {
    const cerrados = grupos.get(g).filter((r) => r.general !== null).map((r) => r.general);
    const est = estadisticas(cerrados);
    return {
      grupo: g, estudiantes: grupos.get(g).length, promedio: est.promedio, maximo: est.maximo,
      minimo: est.minimo, aprobacion: est.aprobacion, pendientes: grupos.get(g).length - cerrados.length,
    };
  });
}

export function filasPorCurso(resumenes) {
  const filas = resumenes.flatMap((r) => r.cursos.map((c) => {
    const p = [...c.parciales, null, null, null].slice(0, 3);
    return {
      grupo: c.grupo, curso_id: c.curso_id, asignatura: c.asignatura,
      num_cuenta: r.num_cuenta, apellidos: r.apellidos, nombre: r.nombre,
      parcial_1: m(p[0]), parcial_2: m(p[1]), parcial_3: m(p[2]), promedio: m(c.bruto), estatus: c.estatus,
    };
  }));
  const porCurso = agrupar(filas, (f) => JSON.stringify([f.grupo, f.asignatura, f.curso_id]));
  return [...porCurso.keys()]
    .sort((a, b) => compararTuplas(JSON.parse(a), JSON.parse(b)))
    .flatMap((k) => ordenar(porCurso.get(k)));
}

export function estadisticasCursos(gestor, resumenes) {
  const cursosR = resumenes.flatMap((r) => r.cursos);
  return [...gestor.cursos.values()]
    .sort((a, b) => compararTuplas([a.grupo, a.asignatura], [b.grupo, b.asignatura]))
    .map((c) => {
      const propios = cursosR.filter((x) => x.curso_id === c.id);
      const est = estadisticas(propios.filter((x) => x.bruto !== null).map((x) => x.bruto));
      const docente = gestor.docenteDe ? gestor.docenteDe(c.id) : null;
      return {
        curso_id: c.id, asignatura: c.asignatura, grupo: c.grupo, docente: docente ? docente.nombre : '',
        inscritos: propios.length, promedio: est.promedio, maximo: est.maximo, minimo: est.minimo,
        aprobados: est.aprobados, reprobados: est.reprobados, aprobacion: est.aprobacion,
      };
    });
}

export function kardex(gestor, resumenes) {
  return ordenar(resumenes).flatMap((r) => r.cursos.flatMap((c) => {
    const base = { num_cuenta: r.num_cuenta, apellidos: r.apellidos, nombre: r.nombre, curso_id: c.curso_id, asignatura: c.asignatura };
    const parciales = gestor.parcialesDe(c.curso_id).map((p) => {
      const calif = gestor.califParcial(r.num_cuenta, p.id);
      return {
        ...base, parcial: p.numero, ponderacion: `${Math.round(p.peso * 100)}%`,
        calificacion: m(calif), aporte: calif === null ? null : mostrar(calif * p.peso),
      };
    });
    return [...parciales, { ...base, parcial: 'Promedio', ponderacion: '100%', calificacion: null, aporte: m(c.bruto) }];
  }));
}

export function generarTodos(gestor, resumenes) {
  const estudiantes = ordenar(resumenes.map(filaEstudiante));
  return {
    resumen_grupos: resumenGrupos(resumenes),
    estudiantes: [...estudiantes].sort((a, b) => comparar(a.grupo, b.grupo)),
    parciales_por_asignatura: filasPorCurso(resumenes),
    estadisticas_cursos: estadisticasCursos(gestor, resumenes),
    riesgo_academico: estudiantes.filter((f) => ['ALTO', 'MEDIO'].includes(f.riesgo)),
    kardex: kardex(gestor, resumenes),
  };
}

/** CSV como lo genera el módulo csv de Python: comillas solo cuando hacen falta y fin de línea \r\n. */
export function aCsv(filas) {
  const columnas = [...new Set(filas.flatMap((f) => Object.keys(f)))];
  const celda = (x) => {
    const s = x === null || x === undefined ? '' : String(x);
    return /[",\r\n]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
  };
  return [columnas, ...filas.map((f) => columnas.map((c) => f[c]))].map((fila) => fila.map(celda).join(',')).join('\r\n') + '\r\n';
}
