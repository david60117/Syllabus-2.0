import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { GestorEscolar } from '../src/gestor.js';
import * as D from '../src/documentos.js';
import * as auth from '../src/autenticacion.js';
import { generar } from '../src/pdf.js';

const leer = (n) => readFileSync(new URL(`../../datos/${n}`, import.meta.url), 'utf8');
const g = new GestorEscolar();
g.cargar(JSON.parse(leer('solicitudes_pequenas.json')));
const INST = { institucion: 'Universidad de prueba', programa: 'Ingeniería de Software', ciclo: 'Julio – Diciembre 2026' };
const usuarios = JSON.parse(leer('usuarios.json')).usuarios;
auth.sincronizar(usuarios, g);

test('encabezado en todos los reportes', () => {
  [D.boleta(g, '500001', INST, 'admin'), D.kardex(g, '500001', INST), D.acta(g, 'C01', INST), D.concentrado(g, '301', INST),
    D.aprovechamiento(g, g.cursos.values(), INST), D.riesgoAcademico(g, INST), D.aprobacion(g, g.cursos.values(), INST),
    D.credenciales(g, usuarios, 'Todos', INST)].forEach((doc) => {
    assert.deepEqual([doc.encabezado.institucion, doc.encabezado.ciclo], [INST.institucion, INST.ciclo]);
    assert.ok(doc.encabezado.titulo && doc.encabezado.emision);
  });
});

test('kárdex individual', () => {
  const doc = D.kardex(g, '500001', INST);
  assert.equal(Object.fromEntries(doc.encabezado.campos)['Número de cuenta'], '500001');
  assert.deepEqual(new Set(doc.filas.map((f) => f.clave)), new Set(g.cursosDe('500001').map((c) => c.id)));
});

test('acta: resumen y calificación final entera', () => {
  const doc = D.acta(g, 'C01', INST);
  const r = Object.fromEntries(doc.resumen);
  assert.equal(r.Aprobados + r.Reprobados + r.Pendientes, r.Inscritos);
  assert.deepEqual(doc.filas.map((f) => f.final), [5, 7, 8, 5, 9, 10]);
});

test('concentrado ordenado por promedio', () => {
  const p = D.concentrado(g, '301', INST, '', 'promedio').filas.map((f) => Number(f.promedio));
  assert.deepEqual(p, [...p].sort((a, b) => b - a));
});

test('credenciales por grupo y docentes', () => {
  const filas = D.credenciales(g, usuarios, 'Grupo 301', INST).filas;
  assert.equal(filas.length, 6);
  assert.equal(filas.find((f) => f.usuario === '500003').contrasena, '500003');
  assert.equal(filas.find((f) => f.usuario === '500001').contrasena, 'Personalizada');
  assert.deepEqual(D.credenciales(g, usuarios, 'Docentes', INST).filas.map((f) => f.usuario), ['pavila', 'd02']);
});

test('CSV y PDF con encabezado', () => {
  const doc = D.acta(g, 'C01', INST, 'admin');
  const renglones = D.aCsvDocumento(doc).replace(/^﻿/, '').split('\r\n');
  assert.equal(renglones[0], INST.institucion);
  assert.equal(renglones[2], 'ACTA DE CALIFICACIONES');
  assert.ok(renglones.includes('Clave,C01'));
  assert.equal(D.aJson(doc).encabezado.reporte, 'Acta de calificaciones');
  const pdf = Buffer.from(generar(D.aprobacion(g, g.cursos.values(), INST)));
  assert.ok(pdf.subarray(0, 8).toString('latin1') === '%PDF-1.4' && pdf.toString('latin1').trimEnd().endsWith('%%EOF'));
  assert.ok(pdf.toString('latin1').includes('/Count 2'));
});
