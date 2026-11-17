import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { GestorEscolar } from '../src/gestor.js';
import * as auth from '../src/autenticacion.js';
import { abrir, paraGuardar } from '../src/persistencia.js';
import { calificacionFinal, promedioFinales } from '../src/calculos.js';

const leer = (n) => readFileSync(new URL(`../../datos/${n}`, import.meta.url), 'utf8');
const nuevo = () => { const g = new GestorEscolar(); g.cargar(JSON.parse(leer('solicitudes_pequenas.json'))); return g; };
const codigo = (c) => ({ codigo: c });

test('materia nueva con 3 parciales, docente y grupo inscrito', () => {
  const g = nuevo();
  const [curso, n] = g.altaCurso('Ética Profesional', '301', [0.3, 0.3, 0.4], 'D01');
  assert.equal(curso.id, 'C07');
  assert.deepEqual(g.parcialesDe(curso.id).map((p) => p.numero), [1, 2, 3]);
  assert.equal(n, 6);
  assert.equal(g.docenteDe(curso.id).id, 'D01');
  assert.throws(() => g.altaCurso('Ética', '301', [0.3, 0.3, 0.3]), codigo('PORCENTAJES_INVALIDOS'));
  assert.throws(() => g.altaCurso('redes', '302'), codigo('ID_DUPLICADO'));
});

test('alumno con cuenta automática e inscripción a su grupo', () => {
  const g = nuevo();
  const [est, n] = g.altaEstudiante('Pérez Soto', 'Juan', '302');
  assert.equal(est.num_cuenta, '500013');
  assert.equal(n, 3);
});

test('grupo registrado manualmente', () => {
  const g = nuevo();
  assert.throws(() => g.altaCurso('Álgebra', '303'), codigo('REFERENCIA_INEXISTENTE'));
  const gr = g.altaGrupo('303', '3', 'Vespertino');
  assert.deepEqual([gr.semestre, gr.turno], [3, 'Vespertino']);
  assert.throws(() => g.altaGrupo('303', 3, 'Matutino'), codigo('ID_DUPLICADO'));
  assert.throws(() => g.altaGrupo('304', 12, 'Matutino'), codigo('FORMATO_INVALIDO'));
  assert.equal(g.altaCurso('Álgebra', '303')[1], 0);
  assert.equal(g.altaEstudiante('Luna Díaz', 'Marta', '303')[1], 1);
  assert.throws(() => g.bajaGrupo('303'), codigo('OPERACION_NO_PERMITIDA'));
  assert.equal(g.gruposRegistro.get('301').semestre, 3);
});

test('cambio de grupo y baja', () => {
  const g = nuevo();
  g.editarEstudiante('500001', 'Martínez García', 'Lucía', '302');
  assert.deepEqual([...new Set(g.cursosDe('500001').map((c) => c.grupo))], ['302']);
  assert.ok(![...g.calificaciones.keys()].some((k) => k.startsWith('500001|')));
  g.bajaEstudiante('500001');
  assert.ok(!g.estudiantes.has('500001'));
});

test('plan de evaluación y acta por lote', () => {
  const g = nuevo();
  const [curso] = g.altaCurso('Ética', '301');
  const p1 = g.parcialesDe(curso.id)[0];
  assert.throws(() => g.definirPlan(p1.id, [{ nombre: 'Examen', peso: 0.6 }, { nombre: 'Tareas', peso: 0.3 }]), codigo('PORCENTAJES_INVALIDOS'));
  g.definirPlan(p1.id, [{ nombre: 'Examen', peso: 0.6 }, { nombre: 'Tareas', peso: 0.4 }]);
  const [examen, tareas] = g.evaluacionesDe(p1.id);
  const alumnos = g.alumnosDeCurso(curso.id).map((e) => e.num_cuenta);
  let r = g.guardarActa([...alumnos.map((n) => [n, examen.id, 8]), ...alumnos.map((n) => [n, tareas.id, 10]), [alumnos[0], tareas.id, 9.55]]);
  assert.equal(r.capturadas, 12);
  assert.deepEqual(r.errores.map((e) => e.codigo), ['FORMATO_INVALIDO']);
  assert.ok(Math.abs(g.califParcial(alumnos[0], p1.id) - 8.8) < 1e-9);
  r = g.guardarActa([[alumnos[0], examen.id, 9], [alumnos[1], examen.id, null]]);
  assert.deepEqual([r.modificadas, r.borradas], [1, 1]);
  assert.equal(g.definirPlan(p1.id, [{ id: examen.id, nombre: 'Examen', peso: 1 }]), 6);
  assert.equal(g.calificacion(alumnos[0], examen.id), 9);
  assert.throws(() => g.eliminarCurso(curso.id), codigo('OPERACION_NO_PERMITIDA'));
});

test('asignación de docentes y excepciones de inscripción', () => {
  const g = nuevo();
  const d = g.altaDocente('Ramón Gil');
  assert.equal(d.id, 'D03');
  g.asignarCursos(d.id, ['C01', 'C04']);
  assert.deepEqual(g.cursosDeDocente(d.id).map((c) => c.id), ['C01', 'C04']);
  assert.deepEqual(g.actualizarInscripciones('500001', ['C01']), [0, 2]);
  assert.throws(() => g.actualizarInscripciones('500001', ['C04']), codigo('REFERENCIA_INEXISTENTE'));
  assert.equal(g.inscribirGrupo('301'), 2);
});

test('cuentas, contraseñas y respaldo', async () => {
  const g = nuevo();
  const usuarios = JSON.parse(leer('usuarios.json')).usuarios;
  auth.sincronizar(usuarios, g);
  const cuenta = await auth.iniciarSesion(usuarios, '500002', '500002', g);
  assert.equal(cuenta.debe_cambiar, true);
  assert.throws(() => auth.cambiarContrasena(cuenta, '500002', 'corta1', 'corta1'), codigo('FORMATO_INVALIDO'));
  auth.cambiarContrasena(cuenta, '500002', 'Nueva2026', 'Nueva2026');
  assert.equal(auth.contrasenaVisible(cuenta), null);
  g.altaDocente('Ramón Gil');
  assert.equal(auth.sincronizar(usuarios, g), 1);
  g.bajaEstudiante('500003');
  auth.sincronizar(usuarios, g);
  assert.equal(auth.buscar(usuarios, '500003'), null);
  const copia = JSON.parse(JSON.stringify(paraGuardar(g, usuarios)));
  const otro = new GestorEscolar();
  const { usuarios: u2 } = abrir(otro, copia);
  assert.deepEqual(JSON.parse(JSON.stringify(otro.aObjeto())), JSON.parse(JSON.stringify(g.aObjeto())));
  assert.equal((await auth.iniciarSesion(u2, '500002', 'Nueva2026', otro)).rol, 'Alumno');
});

test('calificación final: regla del .5', () => {
  assert.deepEqual([8.5, 8.4, 7, 9.5, 0.3 * 8.5 + 0.3 * 8.5 + 0.4 * 8.5, 6.9, 6.96, 6.5].map(calificacionFinal), [9, 8, 7, 10, 9, 6, 6, 6]);
  assert.equal(calificacionFinal(null), null);
  assert.equal(String(promedioFinales([8.5, 6.9, null])), '7.50');
});

test('SHA-256 propio coincide con node:crypto', async () => {
  const { createHash } = await import('node:crypto');
  ['', 'Admin123', 'ñandú 2026', 'x'.repeat(130)].forEach((t) => assert.equal(auth.resumen(t), createHash('sha256').update(t).digest('hex')));
});
