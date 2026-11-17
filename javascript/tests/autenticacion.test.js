import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { iniciarSesion } from '../src/autenticacion.js';
import { GestorEscolar } from '../src/gestor.js';

const leer = (n) => readFileSync(new URL(`../../datos/${n}`, import.meta.url), 'utf8');
const usuarios = JSON.parse(leer('usuarios.json')).usuarios;
const gestor = new GestorEscolar();
gestor.cargar(JSON.parse(leer('solicitudes_pequenas.json')));

test('un usuario de muestra por rol', () => assert.deepEqual(usuarios.map((u) => u.rol).sort(), ['Administrador', 'Alumno', 'Docente']));

test('usuarios de muestra inician sesión', async () => {
  assert.equal((await iniciarSesion(usuarios, 'admin', 'Admin123', gestor)).rol, 'Administrador');
  assert.equal((await iniciarSesion(usuarios, 'PAOLA.AVILA@syllabus.mx', 'Docente123', gestor)).referencia, 'D01');
  assert.equal((await iniciarSesion(usuarios, '500001', 'Alumno123', gestor)).referencia, '500001');
});

test('contraseña incorrecta', () => assert.rejects(iniciarSesion(usuarios, 'admin', 'otra', gestor), { codigo: 'CREDENCIALES_INVALIDAS' }));

test('cuenta que no está en los datos cargados', () =>
  assert.rejects(iniciarSesion(usuarios, 'pavila', 'Docente123', new GestorEscolar()), { codigo: 'REFERENCIA_INEXISTENTE' }));
