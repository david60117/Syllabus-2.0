import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, readFile, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { cargarJson, exportarCsv, exportarJson, exportarReportes } from '../src/archivos.js';
import { Dec } from '../src/calculos.js';

const DATOS = fileURLToPath(new URL('../../datos/', import.meta.url));
const MAIN = fileURLToPath(new URL('../src/main.js', import.meta.url));
const ejecutar = promisify(execFile);
const temporal = () => mkdtemp(join(tmpdir(), 'syllabus-'));
const escribir = async (texto) => { const r = join(await temporal(), 'x.json'); await writeFile(r, texto); return r; };

describe('cargarJson', () => {
  test('carga el ejemplo válido', async () => {
    const d = await cargarJson(join(DATOS, 'ejemplo_entrada.json'));
    assert.equal(d.estudiantes.length, 4);
    assert.equal(d.parciales.length, 3);
  });
  test('archivo inexistente', () => assert.rejects(cargarJson('no_existe.json'), { codigo: 'ESTRUCTURA_INVALIDA' }));
  test('JSON mal formado', async () => assert.rejects(cargarJson(await escribir('{"estudiantes": [')), { codigo: 'ESTRUCTURA_INVALIDA' }));
  test('faltan llaves', async () => assert.rejects(cargarJson(await escribir('{"estudiantes": [], "cursos": []}')), /parciales/));
  test('llave que no es lista', async () => {
    const c = Object.fromEntries(['estudiantes', 'cursos', 'parciales', 'evaluaciones', 'inscripciones', 'calificaciones'].map((k) => [k, []]));
    await assert.rejects(cargarJson(await escribir(JSON.stringify({ ...c, cursos: {} }))), /cursos/);
  });
  test('raíz que no es objeto', async () => assert.rejects(cargarJson(await escribir('[]')), { codigo: 'ESTRUCTURA_INVALIDA' }));
});

describe('exportar', () => {
  const filas = [{ num_cuenta: '500005', apellidos: 'Álvarez Ruiz', nombre: 'Elena', promedio: new Dec(9) },
    { num_cuenta: '500007', apellidos: 'Peña Flores', nombre: 'Gloria', promedio: new Dec(7.4) }];
  test('JSON con acentos y números', async () => {
    const r = await exportarJson(filas, join(await temporal(), 'r.json'));
    const texto = await readFile(r, 'utf8');
    assert.match(texto, /Álvarez/);
    assert.equal(JSON.parse(texto)[1].promedio, 7.4);
  });
  test('CSV con 2 decimales y BOM', async () => {
    const r = await exportarCsv(filas, join(await temporal(), 'r.csv'));
    const texto = await readFile(r, 'utf8');
    assert.ok(texto.startsWith('﻿'));
    assert.equal(texto.split('\r\n')[2], '500007,Peña Flores,Gloria,7.40');
  });
  test('exportarReportes genera todos los archivos', async () => {
    const carpeta = await temporal();
    await exportarReportes({ grupo_estudiantes: filas }, [{ codigo: 'ID_DUPLICADO' }], carpeta);
    assert.deepEqual((await readdir(carpeta)).sort(), ['errores.json', 'grupo_estudiantes.csv', 'grupo_estudiantes.json']);
  });
});

describe('línea de comandos', () => {
  test('genera reportes con el ejemplo', async () => {
    const salida = await temporal();
    await ejecutar('node', [MAIN, '--cli', '--entrada', join(DATOS, 'ejemplo_entrada.json'), '--salida', salida]);
    const resumen = JSON.parse(await readFile(join(salida, 'resumen_grupos.json'), 'utf8'));
    assert.ok((await readdir(salida)).includes('parciales_por_asignatura.csv'));
    assert.equal(resumen[0].promedio, 7.6);
  });
  test('archivo inexistente: código 1 sin traza', async () => {
    const e = await ejecutar('node', [MAIN, '--cli', '--entrada', 'no_existe.json']).catch((x) => x);
    assert.equal(e.code, 1);
    assert.match(e.stderr, /ESTRUCTURA_INVALIDA/);
    assert.doesNotMatch(e.stderr, /at .*\.js/);
  });
  test('modo concurrente da lo mismo que secuencial', async () => {
    const [a, b] = [await temporal(), await temporal()];
    const entrada = join(DATOS, 'solicitudes_pequenas.json');
    await ejecutar('node', [MAIN, '--cli', '--entrada', entrada, '--salida', a]);
    await ejecutar('node', [MAIN, '--cli', '--entrada', entrada, '--salida', b, '--modo', 'concurrente']);
    assert.equal(await readFile(join(a, 'kardex.csv'), 'utf8'), await readFile(join(b, 'kardex.csv'), 'utf8'));
  });
});
