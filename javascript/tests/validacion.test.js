import { test, describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { GestorEscolar } from '../src/gestor.js';
import { mostrar } from '../src/calculos.js';

const datos = JSON.parse(readFileSync(new URL('../../datos/invalidos.json', import.meta.url), 'utf8'));
const pares = (xs) => xs.map((e) => `${e.caso}|${e.codigo}`).sort();

describe('registros inválidos', () => {
  const g = new GestorEscolar();
  g.cargar(datos);
  test('errores esperados', () => assert.deepEqual(pares(g.errores), pares(datos.errores_esperados)));
  test('VAL-01 continúa después de los errores', () => {
    assert.ok(g.estudiantes.has('545049'));
    assert.equal(String(mostrar(g.promedioCurso('545049', 'C01'))), '8.34');
  });
  test('INV-20 conserva la calificación anterior', () => assert.equal(g.calificacion('545049', 'EV01'), 8));
});

describe('altas individuales', () => {
  let g;
  beforeEach(() => {
    g = new GestorEscolar();
    g.registrarEstudiante({ num_cuenta: '500001', apellidos: 'García López', nombre: 'Ana', grupo: '301' });
    g.registrarCurso({ id: 'C01', asignatura: 'Bases de Datos', grupo: '301' });
  });
  test('los parciales no superan 1.0', () => {
    g.registrarParcial({ id: 'P01', curso_id: 'C01', numero: 1, peso: 0.6 });
    assert.throws(() => g.registrarParcial({ id: 'P02', curso_id: 'C01', numero: 2, peso: 0.5 }), { codigo: 'PORCENTAJES_INVALIDOS' });
  });
  test('inscripción a un curso de otro grupo', () => {
    g.registrarEstudiante({ num_cuenta: '500002', apellidos: 'Ruiz Mora', nombre: 'Luis', grupo: '302' });
    assert.throws(() => g.inscribir({ num_cuenta: '500002', curso_id: 'C01' }), { codigo: 'REFERENCIA_INEXISTENTE' });
  });
  test('eventos emitidos', () => {
    const vistos = [];
    g.bus.suscribir('inscripcion_registrada', (m) => vistos.push(m.detalle));
    g.inscribir({ num_cuenta: '500001', curso_id: 'C01' });
    assert.deepEqual(vistos, ['500001 → C01']);
  });
});
