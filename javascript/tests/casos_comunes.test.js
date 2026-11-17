import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { GestorEscolar } from '../src/gestor.js';
import { estadisticas, evaluarRiesgo, mostrar, ordenar } from '../src/calculos.js';

const DATOS = new URL('../../datos/', import.meta.url);
const leer = (n) => JSON.parse(readFileSync(new URL(n, DATOS), 'utf8'));
const CASOS = Object.fromEntries(leer('casos_prueba.json').casos.map((c) => [c.id, c]));
const caso = (cp) => { const g = new GestorEscolar(); g.cargar(CASOS[cp].entrada); return g; };
const txt = (x) => String(mostrar(x));

test('CP-01 promedio ponderado', () => {
  const g = caso('CP-01');
  assert.equal(txt(g.promedioCurso('600001', 'C91')), '7.95');
  assert.equal(g.resumenEstudiante('600001').cursos[0].estatus, 'APROBADO');
});

test('CP-02 límite de aprobación', () => {
  const g = caso('CP-02');
  assert.equal(txt(g.promedioCurso('600001', 'C92')), '7.00');
  assert.equal(g.resumenEstudiante('600001').cursos[0].estatus, 'APROBADO');
});

test('CP-03 reprobación y riesgo alto', () => {
  const r = caso('CP-03').resumenEstudiante('600001');
  assert.equal(txt(r.cursos[0].bruto), '6.99');
  assert.equal(r.cursos[0].estatus, 'REPROBADO');
  assert.equal(r.riesgo, 'ALTO');
});

test('CP-04 estadísticas del curso', () => {
  const g = caso('CP-04');
  const est = estadisticas(['600001', '600002', '600003'].map((nc) => g.promedioCurso(nc, 'C94')));
  assert.deepEqual([est.promedio, est.maximo, est.minimo, est.aprobacion].map(String), ['7.33', '9.00', '6.00', '66.67']);
  assert.deepEqual([est.aprobados, est.reprobados], [2, 1]);
});

test('CP-05 calificación fuera de rango', () => {
  const g = caso('CP-05');
  assert.deepEqual(g.errores.map((e) => e.codigo), ['CALIFICACION_FUERA_DE_RANGO']);
  assert.equal(g.calificaciones.size, 0);
});

test('redondeo institucional', () => {
  assert.equal(txt(6.996), '6.99');
  assert.equal(txt(7.955), '7.96');
  assert.equal(txt(0.3 * 7 + 0.3 * 7 + 0.4 * 7), '7.00');
});

test('fronteras de riesgo', () => {
  assert.equal(evaluarRiesgo([6.996], 6.996), 'ALTO');
  assert.equal(evaluarRiesgo([7.0, 7.0], 7.0), 'MEDIO');
  assert.equal(evaluarRiesgo([7.5, 7.5], 7.5), 'MEDIO');
  assert.equal(evaluarRiesgo([7.51, 7.51], 7.51), 'SIN_RIESGO');
  assert.equal(evaluarRiesgo([8.5, 6.98], 7.74), 'ALTO');
});

test('orden por apellidos sin acentos y desempate por cuenta', () => {
  const filas = [{ num_cuenta: '3', apellidos: 'García', promedio: 8 },
    { num_cuenta: '2', apellidos: 'Álvarez', promedio: 9 },
    { num_cuenta: '1', apellidos: 'Núñez', promedio: 9 }];
  assert.deepEqual(ordenar(filas).map((f) => f.num_cuenta), ['2', '3', '1']);
  assert.deepEqual(ordenar(filas, 'promedio', true).map((f) => f.num_cuenta), ['1', '2', '3']);
});

test('ejemplo de entrada por parciales', () => {
  const g = new GestorEscolar();
  g.cargar(leer('ejemplo_entrada.json'));
  assert.deepEqual(g.errores, []);
  const r = g.resumenEstudiante('500001');
  assert.deepEqual(r.cursos[0].parciales.map(txt), ['9.00', '10.00', '8.25']);
  assert.equal(txt(r.general), '9.00');
  assert.equal(g.resumenEstudiante('500007').riesgo, 'MEDIO');
});
