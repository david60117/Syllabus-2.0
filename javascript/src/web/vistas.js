/**
 * Pantallas. Cada vista es una función (app) → HTML construida declarativamente con map/filter/reduce a partir del
 * estado del gestor; los cambios llegan por manejadores registrados con app.accion / app.formulario. Mismos módulos
 * por perfil que las versiones Python y Java (control escolar, docente y alumno).
 */
import { estadisticas, filtroEstado, filtroRango, mostrar, comparar, calificacionFinal } from '../calculos.js';
import { filaEstudiante, filasPorCurso, estadisticasCursos } from '../reportes.js';
import { cambiarContrasena, contrasenaVisible, restablecer } from '../autenticacion.js';
import { TURNOS } from '../gestor.js';
import * as D from '../documentos.js';
import { generar } from '../pdf.js';
import { parsearJson } from '../estructura.js';
import { boton, campo, encabezado, esc, fmt, icono, ICONO_DE, legible, mosaicos, pestanas, rejilla, tabla, tarjeta, TEMAS } from './componentes.js';

/** Textos del panel de cada rol. */
const PANELES = {
  Administrador: {
    titulo: 'Control escolar', subtitulo: 'Estudiantes, grupos, materias, docentes, inscripciones, consultas y reportes.',
    etiqueta: 'ADMINISTRACIÓN ESCOLAR', banda: 'Registra la información una sola vez: lo demás se genera solo.',
    tarjetas: [['Estudiantes', 'Alta con número de cuenta automático e inscripción al grupo.', 'Abrir Estudiantes', 'Estudiantes'],
      ['Grupos y materias', 'Grupos (semestre y turno) y materias con su docente.', 'Abrir Grupos y materias', 'Grupos y materias'],
      ['Docentes', 'Alta de docentes y asignación de materias.', 'Abrir Docentes', 'Docentes'],
      ['Inscripciones', 'Altas y bajas individuales de materias.', 'Abrir Inscripciones', 'Inscripciones'],
      ['Consultas y kárdex', 'Promedios, orden por promedio y riesgo académico.', 'Abrir Consultas', 'Consultas'],
      ['Reportes y exportación', 'Boleta, kárdex, actas y estadísticas en PDF, CSV o JSON.', 'Abrir Reportes', 'Reportes']],
  },
  Docente: {
    titulo: 'Panel docente', subtitulo: 'Tus materias, su plan de evaluación y la captura de calificaciones.',
    etiqueta: 'DOCENCIA', banda: 'Define el plan de cada parcial y captura el acta del grupo completo.',
    tarjetas: [['Mis cursos', 'Materias y grupos que tienes asignados.', 'Ver cursos', 'Mis cursos'],
      ['Plan de evaluación', 'Evaluaciones y porcentajes de cada parcial.', 'Abrir plan', 'Plan de evaluación'],
      ['Acta de calificaciones', 'Captura todo el grupo y guarda de una vez.', 'Abrir captura', 'Captura'],
      ['Consultas', 'Promedios y kárdex de tus alumnos.', 'Abrir Consultas', 'Consultas'],
      ['Riesgo académico', 'Alumnos en riesgo de tus materias.', 'Ver riesgo', 'Consultas:1'],
      ['Reportes', 'Aprobados, reprobados y estadísticas.', 'Abrir Reportes', 'Reportes']],
  },
  Alumno: {
    titulo: 'Mi espacio académico', subtitulo: 'Tus materias, calificaciones, promedio y estado académico.',
    etiqueta: 'ALUMNO', banda: 'Consulta tu avance; las calificaciones las registran tus docentes.', tarjetas: [],
  },
};

const id = (texto) => String(texto ?? '').split(' · ')[0];
const num = (t) => (typeof t === 'string' && t.trim() !== '' && !Number.isNaN(Number(t)) ? Number(t) : t);
/** Porcentaje escrito por el usuario (30) → peso decimal (0.3); el gestor valida el resto. */
const pctAPeso = (t) => { const x = num(t); return typeof x === 'number' ? Number((x / 100).toFixed(6)) : x; };
const txtPct = (peso) => String(+(peso * 100).toFixed(6));
const mostrarONulo = (x) => (x === null || x === undefined ? null : mostrar(x));
const unicos = (xs) => [...new Set(xs)].sort(comparar);
const opcionesCursos = (cursos) => cursos.map((c) => `${c.id} · ${c.asignatura}`);
const docenteTxt = (d) => (d ? `${d.id} · ${d.nombre}` : 'Sin asignar');
const emisor = (app) => app.estado.cuenta?.usuario ?? '';
const sumaPesos = (xs) => xs.reduce((a, x) => a + x.peso, 0);

/** Cuántos de los 3 parciales tienen evaluaciones que suman 100 %. */
const planCompleto = (g, cursoId) => g.parcialesDe(cursoId).filter((p) => g.evaluacionesDe(p.id).length && Math.abs(sumaPesos(g.evaluacionesDe(p.id)) - 1) < 1e-9).length;

function avanceCaptura(g, cursoId) {
  const alumnos = g.alumnosDeCurso(cursoId).map((e) => e.num_cuenta);
  const evals = g.parcialesDe(cursoId).flatMap((p) => g.evaluacionesDe(p.id)).map((e) => e.id);
  return [alumnos.flatMap((n) => evals.filter((e) => g.calificacion(n, e) !== null)).length, alumnos.length * evals.length];
}

/** Destino "Vista:pestaña" de los accesos (p. ej. "Consultas:1" abre Promedios y riesgo). */
const irA = (app, destino) => () => {
  const [vista, pestana] = destino.split(':');
  if (pestana !== undefined) app.estado.pestanas[vista.toLowerCase()] = Number(pestana);
  app.ir(vista);
};

const botonesPie = (app, botones) => `<div class="botones-pie">${botones.map(([texto, destino], i) =>
  `<button type="button" class="boton${i ? ' secundario' : ''} ancho" ${app.accion(`pie-${i}`, irA(app, destino))}>${esc(texto)}</button>`).join('')}</div>`;
const notaPie = (texto) => `<div class="tarjeta nota-pie">${esc(texto)}</div>`;

/** Ojo para mostrar u ocultar una contraseña (eye / eye-off de Lucide). */
const verContrasena = (app, nombre) => `<button type="button" class="ver-contrasena" aria-label="Mostrar contraseña" title="Mostrar contraseña"
  ${app.accion(`ver-${nombre}`, (el) => {
    const entrada = el.parentElement.querySelector('input');
    const oculta = entrada.type === 'password';
    entrada.type = oculta ? 'text' : 'password';
    el.innerHTML = icono(oculta ? 'ojo_cerrado' : 'ojo', 18);
    el.classList.toggle('activo', oculta);
    el.setAttribute('aria-label', oculta ? 'Ocultar contraseña' : 'Mostrar contraseña');
    entrada.focus();
  })}>${icono('ojo', 18)}</button>`;

// ------------------------------------------------------------------ acceso
/** Pantalla de inicio de sesión (Figma: Acceso · Syllabus 2.0). */
export function acceso(app) {
  return `<header class="cabecera-acceso"><div class="marca"><span class="letra">S</span><strong>Syllabus 2.0</strong></div>
      <span class="ruta">PLATAFORMA ACADÉMICA</span></header>
    <main class="pantalla-acceso">
      <form class="tarjeta tarjeta-acceso" ${app.formulario('acceso', (d) => app.iniciarSesion(d.usuario, d.contrasena))}>
        <div class="logo grande">S</div>
        <h1>Iniciar sesión</h1>
        <p class="suave">Entra a Syllabus 2.0 para continuar.</p>
        <label class="campo-acceso"><span>Correo o usuario</span><input type="text" name="usuario" placeholder="Escribe tu correo o usuario" autocomplete="username" autofocus></label>
        <label class="campo-acceso"><span>Contraseña</span><span class="con-ojo"><input type="password" name="contrasena" placeholder="••••••••" autocomplete="current-password">${verContrasena(app, 'acceso')}</span></label>
        <p class="mensaje-error" id="mensaje-acceso">${esc(app.estado.mensajeAcceso ?? '')}</p>
        <button class="boton ingresar">Ingresa</button>
      </form>
    </main>`;
}

// ------------------------------------------------------------------ inicio
function pendientesAdmin(app) {
  const g = app.gestor;
  const cursos = [...g.cursos.values()];
  const n = {
    sinDocente: cursos.filter((c) => !g.docenteDe(c.id)).length,
    sinPlan: cursos.filter((c) => planCompleto(g, c.id) < 3).length,
    sinMaterias: [...g.estudiantes.values()].filter((e) => !g.cursosDe(e.num_cuenta).length).length,
    iniciales: app.usuarios.filter((u) => u.debe_cambiar).length,
  };
  return [[`${n.sinDocente} materias sin docente asignado`, 'Grupos y materias', n.sinDocente > 0],
    [`${n.sinPlan} materias con plan de evaluación incompleto (lo define cada docente)`, 'Grupos y materias', n.sinPlan > 0],
    [`${n.sinMaterias} estudiantes sin materias inscritas`, 'Inscripciones', n.sinMaterias > 0],
    [`${n.iniciales} cuentas con contraseña inicial`, 'Usuarios', n.iniciales > 0]];
}

function pendientesDocente(app) {
  const g = app.gestor;
  const filas = app.cursosVisibles().flatMap((c) => {
    const [hechas, total] = avanceCaptura(g, c.id);
    const plan = planCompleto(g, c.id);
    if (plan < 3) return [[`${c.asignatura} (${c.grupo}): plan de evaluación ${plan}/3 parciales`, 'Plan de evaluación', true]];
    return hechas < total ? [[`${c.asignatura} (${c.grupo}): ${total - hechas} calificaciones por capturar`, 'Captura', true]] : [];
  });
  return filas.length ? filas : [['Sin pendientes: tus materias tienen plan y calificaciones completas.', 'Mis cursos', false]];
}

const tarjetasAcceso = (app, tarjetas) => (tarjetas.length ? `<div class="etiqueta-seccion">ACCESOS PRINCIPALES</div>
  ${rejilla(3, tarjetas.map(([t, texto, enlace, destino], i) => `<div class="tarjeta acceso">
    <span class="icono-acceso" aria-hidden="true">${icono(ICONO_DE[destino.split(':')[0]] ?? 'consultas', 18)}</span><h4>${esc(t)}</h4><p>${esc(texto)}</p>
    <button type="button" class="enlace" ${app.accion(`acceso-${i}`, irA(app, destino))}>${esc(enlace)}   →</button></div>`))}` : '');

function inicio(app) {
  const p = PANELES[app.estado.rol];
  const g = app.gestor;
  const cabeza = `${encabezado(`Inicio  /  ${app.estado.rol}`, p.titulo, p.subtitulo)}
    <div class="banda"><div class="textos"><div class="ruta">${p.etiqueta}</div>
      <h2>Hola, ${esc(app.nombreUsuario() || app.estado.cuenta?.nombre || app.estado.rol)}</h2><p class="suave">${esc(p.banda)}</p></div></div>`;
  if (app.estado.rol === 'Alumno') return cabeza + inicioAlumno(app);
  const resumenes = app.resumenesVisibles();
  const riesgo = resumenes.filter((r) => ['ALTO', 'MEDIO'].includes(r.riesgo)).length;
  const cursos = app.cursosVisibles();
  const indicadores = app.estado.rol === 'Administrador'
    ? [['Estudiantes', g.estudiantes.size], ['Grupos', g.grupos().length], ['Materias', g.cursos.size], ['Docentes', g.docentes.size], ['En riesgo', riesgo]]
    : [['Mis materias', cursos.length], ['Alumnos', resumenes.length],
      ['Por capturar', cursos.map((c) => avanceCaptura(g, c.id)).reduce((t, [h, n]) => t + n - h, 0)], ['En riesgo', riesgo]];
  const pendientes = app.estado.rol === 'Administrador' ? pendientesAdmin(app) : pendientesDocente(app);
  return `${cabeza}${mosaicos(indicadores)}
    ${tarjeta('Pendientes', `<ul class="pendientes">${pendientes.map(([texto, destino, alerta], i) => `<li class="${alerta ? 'alerta' : 'ok'}">
      <span class="punto"></span><span>${esc(texto)}</span>${alerta ? `<button type="button" class="enlace" ${app.accion(`pend-${i}`, irA(app, destino))}>Ir a ${esc(destino)}  →</button>` : ''}</li>`).join('')}</ul>`)}
    ${tarjetasAcceso(app, p.tarjetas)}`;
}

// ------------------------------------------------------------------ control escolar: estudiantes
function estudiantes(app) {
  const activa = app.pestana('estudiantes');
  return `${encabezado('Control escolar  /  Estudiantes', 'Estudiantes',
    'El número de cuenta y la cuenta de usuario se generan solos; el alumno queda inscrito en las materias de su grupo.')}
    ${pestanas('estudiantes', ['Alta y edición', 'Carga masiva'], activa)}
    ${activa === 0 ? estudiantesAlta(app) : cargaMasiva(app)}`;
}

function estudiantesAlta(app) {
  const g = app.gestor;
  const grupos = [...g.gruposRegistro.keys()].sort(comparar);
  const f = app.filtro('form_estudiante', { sel: '', apellidos: '', nombre: '', grupo: grupos[0] ?? '', inscribir: 'si' });
  const sel = g.estudiantes.has(f.sel) ? f.sel : '';
  const limpiar = () => { Object.assign(f, { sel: '', apellidos: '', nombre: '' }); app.render(); };
  const guardar = () => {
    if (sel) {
      if (g.estudiantes.get(sel).grupo !== f.grupo && !app.confirmar('Al cambiar de grupo se le dan de baja sus materias actuales (con sus '
        + 'calificaciones) y se inscribe en las del nuevo grupo. ¿Continuar?')) return;
      if (app.intentar(() => g.editarEstudiante(sel, f.apellidos, f.nombre, f.grupo))) limpiar();
      return;
    }
    const [ok, res] = app.hacer(() => g.altaEstudiante(f.apellidos, f.nombre, f.grupo, f.inscribir === 'si'));
    if (!ok) return;
    const [est, n] = res;
    Object.assign(f, { apellidos: '', nombre: '' });
    app.render();
    app.avisar('Estudiante registrado', `${est.apellidos} ${est.nombre} · Número de cuenta: ${est.num_cuenta} · inscrito en ${n} materias del grupo ${est.grupo}. `
      + `Usuario: ${est.num_cuenta} · contraseña inicial: ${est.num_cuenta} (se le pedirá cambiarla).`);
  };
  const baja = () => {
    const e = g.estudiantes.get(sel);
    if (app.confirmar(`¿Dar de baja a ${e.apellidos} ${e.nombre} (${sel})? Se eliminan sus inscripciones, calificaciones y su cuenta de usuario.`)
      && app.intentar(() => g.bajaEstudiante(sel))) limpiar();
  };
  const filtros = app.filtro('padron', { texto: '', grupo: 'Todos' });
  const usuarios = new Map(app.usuarios.filter((u) => u.rol === 'Alumno').map((u) => [u.referencia, u.usuario]));
  const todos = [...g.estudiantes.values()].map((e) => ({ num_cuenta: e.num_cuenta, apellidos: e.apellidos, nombre: e.nombre, grupo: e.grupo,
    materias: g.cursosDe(e.num_cuenta).length, usuario: usuarios.get(e.num_cuenta) ?? '—' }));
  const visibles = () => {
    const t = filtros.texto.trim().toLowerCase();
    return todos.filter((x) => [x.grupo, 'Todos'].includes(filtros.grupo) && (!t || x.num_cuenta.includes(t) || `${x.apellidos} ${x.nombre}`.toLowerCase().includes(t)));
  };
  const columnas = [['num_cuenta', 'Núm. de cuenta'], ['apellidos', 'Apellidos'], ['nombre', 'Nombre'], ['grupo', 'Grupo'], ['materias', 'Materias'], ['usuario', 'Usuario']];
  const tablaHtml = () => tabla('padron', columnas, visibles(), { orden: app.orden('padron'), seleccionable: true, seleccionada: sel });
  app.alFiltrar('padron', () => app.reemplazar('tabla-padron', tablaHtml()));
  app.alSeleccionar('padron', (nc) => { const e = g.estudiantes.get(nc); Object.assign(f, { sel: nc, apellidos: e.apellidos, nombre: e.nombre, grupo: e.grupo }); app.render(); });
  const attr = 'data-filtro="form_estudiante"';
  return `${rejilla(2, [
    tarjeta(sel ? `Editar estudiante ${sel}` : 'Nuevo estudiante', `<div class="pila">
      ${campo('Número de cuenta', 'cuenta', { valor: sel || `${g.siguienteCuenta()}  (se asigna al guardar)`, atributos: 'readonly' })}
      ${campo('Apellidos', 'apellidos', { valor: f.apellidos, atributos: attr })}
      ${campo('Nombre(s)', 'nombre', { valor: f.nombre, atributos: attr })}
      ${campo('Grupo', 'grupo', { valor: f.grupo, opciones: grupos.length ? grupos : ['—'], atributos: attr + ' data-refrescar' })}
      ${sel ? '' : `<label class="check"><input type="checkbox" ${f.inscribir === 'si' ? 'checked' : ''} ${app.accion('inscribir-grupo', (el) => { f.inscribir = el.checked ? 'si' : 'no'; }, 'change')}> Inscribir en todas las materias del grupo</label>`}
      <div class="acciones">${boton(sel ? 'Guardar cambios' : 'Registrar estudiante', app.accion('guardar-estudiante', guardar))}
        ${sel ? boton('Nuevo', app.accion('nuevo-estudiante', limpiar), true) + boton('Dar de baja', app.accion('baja-estudiante', baja), true) : ''}</div>
      <p class="nota">Los grupos se registran en «Grupos y materias».</p></div>`),
    tarjeta('Grupos', tabla('grupos-resumen', [['grupo', 'Grupo'], ['alumnos', 'Alumnos'], ['materias', 'Materias']],
      grupos.map((gr) => ({ grupo: gr, alumnos: [...g.estudiantes.values()].filter((e) => e.grupo === gr).length, materias: g.cursosDeGrupo(gr).length })), { ordenables: [] })),
  ])}
  ${tarjeta('Padrón de estudiantes', `<div class="fila">
      ${campo('Buscar (cuenta o nombre)', 'texto', { valor: filtros.texto, ancho: 26, atributos: 'data-filtro="padron"' })}
      ${campo('Grupo', 'grupo', { valor: filtros.grupo, opciones: ['Todos', ...grupos], atributos: 'data-filtro="padron" data-refrescar' })}</div>
    ${tablaHtml()}<p class="nota">Haz clic en un estudiante para editarlo o darlo de baja.</p>`)}`;
}

function cargaMasiva(app) {
  const r = app.estado.cargaMasiva ?? { archivo: '', errores: null };
  const importar = async (el) => {
    const archivo = el.files[0];
    if (!archivo) return;
    try {
      const errores = app.importar(parsearJson(await archivo.text(), archivo.name), archivo.name);
      app.estado.cargaMasiva = { archivo: archivo.name, errores };
    } catch (e) {
      app.avisar('Archivo inválido', e.detalle ?? e.message);
    }
    app.render();
  };
  return `${tarjeta('Importar archivo JSON', `<label class="boton selector-archivo">Seleccionar archivo e importar
      <input type="file" accept=".json,application/json" ${app.accion('importar', importar, 'change')}></label>`,
    'Agrega estudiantes, materias, parciales, evaluaciones, inscripciones y calificaciones con el formato de datos/ejemplo_entrada.json. '
    + 'Los registros válidos se guardan; los inválidos se listan sin detener la carga.')}
    ${r.errores ? tarjeta(`Resultado · ${r.archivo}`, `<p class="resumen">Registros rechazados: <b>${r.errores.length}</b></p>
      ${tabla('rechazados', [['tipo', 'Tipo'], ['codigo', 'Código'], ['detalle', 'Detalle']], r.errores, { ordenables: [] })}`) : ''}`;
}

// ------------------------------------------------------------------ control escolar: grupos y materias
function materias(app) {
  const activa = app.pestana('materias');
  return `${encabezado('Control escolar  /  Grupos y materias', 'Grupos y materias',
    'Registra los grupos y crea cada materia con sus 3 parciales y su docente; el grupo completo queda inscrito.')}
    ${pestanas('materias', ['Grupos', 'Materias'], activa)}
    ${activa === 0 ? grupos(app) : materiasDelGrupo(app)}`;
}

function grupos(app) {
  const g = app.gestor;
  const f = app.filtro('form_grupo', { sel: '', clave: '', semestre: '1', turno: TURNOS[0] });
  const sel = g.gruposRegistro.has(f.sel) ? f.sel : '';
  const limpiar = () => { Object.assign(f, { sel: '', clave: '', semestre: '1', turno: TURNOS[0] }); app.render(); };
  const guardar = () => {
    if (sel) { if (app.intentar(() => g.editarGrupo(sel, f.semestre, f.turno))) limpiar(); return; }
    const [ok, gr] = app.hacer(() => g.altaGrupo(f.clave, f.semestre, f.turno));
    if (!ok) return;
    f.clave = '';
    app.render();
    app.avisar('Grupo registrado', `Grupo ${gr.clave} · ${gr.semestre}.º semestre · ${gr.turno}. Ahora crea sus materias en la pestaña «Materias» y registra a sus alumnos.`);
  };
  const baja = () => { if (app.confirmar(`¿Eliminar el grupo ${sel}?`) && app.intentar(() => g.bajaGrupo(sel))) limpiar(); };
  app.alSeleccionar('grupos', (clave) => { const gr = g.gruposRegistro.get(clave); Object.assign(f, { sel: clave, clave, semestre: String(gr.semestre), turno: gr.turno }); app.render(); });
  const attr = 'data-filtro="form_grupo"';
  const pasos = ['Registra el grupo (clave, semestre y turno).', 'Crea sus materias y asigna el docente.',
    'Registra a los alumnos: se inscriben solos en las materias del grupo.', 'Cada docente define su plan de evaluación y captura calificaciones.'];
  const filas = [...g.gruposRegistro.values()].sort((a, b) => comparar(a.clave, b.clave)).map((gr) => ({
    grupo: gr.clave, semestre: `${gr.semestre}.º`, turno: gr.turno, alumnos: [...g.estudiantes.values()].filter((e) => e.grupo === gr.clave).length,
    materias: g.cursosDeGrupo(gr.clave).length, sin_docente: g.cursosDeGrupo(gr.clave).filter((c) => !g.docenteDe(c.id)).length }));
  return `${rejilla(2, [
    tarjeta(sel ? `Editar grupo ${sel}` : 'Nuevo grupo', `<div class="pila">
      ${campo(sel ? 'Clave del grupo' : 'Clave del grupo (3 dígitos, p. ej. 303)', 'clave', { valor: sel || f.clave, atributos: sel ? 'readonly' : attr })}
      <div class="dos">${campo('Semestre', 'semestre', { valor: f.semestre, opciones: ['1', '2', '3', '4', '5', '6', '7', '8', '9'], atributos: attr })}
        ${campo('Turno', 'turno', { valor: f.turno, opciones: TURNOS, atributos: attr })}</div>
      <div class="acciones">${boton(sel ? 'Guardar cambios' : 'Registrar grupo', app.accion('guardar-grupo', guardar))}
        ${sel ? boton('Nuevo', app.accion('nuevo-grupo', limpiar), true) + boton('Eliminar', app.accion('baja-grupo', baja), true) : ''}</div>
      <p class="nota">Un grupo solo se puede eliminar si no tiene alumnos ni materias.</p></div>`),
    tarjeta('Orden de registro', pasos.map((t, i) => `<p class="regla"><span class="numero">${String(i + 1).padStart(2, '0')}</span>${esc(t)}</p>`).join('')),
  ])}
  ${tarjeta('Grupos registrados', `${tabla('grupos', [['grupo', 'Grupo'], ['semestre', 'Semestre'], ['turno', 'Turno'], ['alumnos', 'Alumnos'], ['materias', 'Materias'],
    ['sin_docente', 'Sin docente']], filas, { ordenables: [], seleccionable: true, clave: 'grupo', seleccionada: sel })}
    <p class="nota">Haz clic en un grupo para editarlo o eliminarlo.</p>`)}`;
}

function materiasDelGrupo(app) {
  const g = app.gestor;
  const grupos = [...g.gruposRegistro.keys()].sort(comparar);
  const opcionesDocente = ['Sin asignar', ...[...g.docentes.values()].sort((a, b) => comparar(a.id, b.id)).map(docenteTxt)];
  const f = app.filtro('form_materia', { sel: '', asignatura: '', grupo: grupos[0] ?? '', docente: 'Sin asignar', p1: '30', p2: '30', p3: '40' });
  const sel = g.cursos.has(f.sel) ? f.sel : '';
  const docenteId = () => (f.docente === 'Sin asignar' ? null : id(f.docente));
  const limpiar = () => { Object.assign(f, { sel: '', asignatura: '', docente: 'Sin asignar', p1: '30', p2: '30', p3: '40' }); app.render(); };
  const pesos = () => [f.p1, f.p2, f.p3].map(pctAPeso);
  const guardar = () => {
    if (sel) { if (app.intentar(() => g.editarCurso(sel, f.asignatura, pesos(), docenteId()))) limpiar(); return; }
    const [ok, res] = app.hacer(() => g.altaCurso(f.asignatura, f.grupo, pesos(), docenteId()));
    if (!ok) return;
    const [curso, n] = res;
    f.asignatura = '';
    app.render();
    app.avisar('Materia creada', `${curso.id} · ${curso.asignatura} · grupo ${curso.grupo}. Parciales creados: 3 · alumnos inscritos: ${n}. `
      + 'El docente asignado define las evaluaciones de cada parcial en «Plan de evaluación».');
  };
  const eliminar = () => {
    const c = g.cursos.get(sel);
    if (app.confirmar(`¿Eliminar ${c.id} · ${c.asignatura} (${c.grupo})? Se quitan sus parciales, evaluaciones e inscripciones.`)
      && app.intentar(() => g.eliminarCurso(sel))) limpiar();
  };
  app.alSeleccionar('materias', (cid) => {
    const c = g.cursos.get(cid);
    Object.assign(f, { sel: cid, asignatura: c.asignatura, grupo: c.grupo, docente: docenteTxt(g.docenteDe(cid)),
      ...Object.fromEntries(g.parcialesDe(cid).map((p, i) => [`p${i + 1}`, txtPct(p.peso)])) });
    app.render();
  });
  const attr = 'data-filtro="form_materia"';
  const filas = [...g.cursos.values()].sort((a, b) => comparar(a.grupo + a.asignatura, b.grupo + b.asignatura)).map((c) => ({
    clave: c.id, asignatura: c.asignatura, grupo: c.grupo, docente: docenteTxt(g.docenteDe(c.id)), inscritos: g.alumnosDeCurso(c.id).length,
    pesos: g.parcialesDe(c.id).map((p) => txtPct(p.peso)).join(' / '),
    plan: planCompleto(g, c.id) === 3 ? 'Completo' : `Pendiente ${planCompleto(g, c.id)}/3` }));
  return `${rejilla(2, [
    tarjeta(sel ? `Editar materia ${sel}` : 'Nueva materia', `<div class="pila">
      ${campo('Asignatura', 'asignatura', { valor: f.asignatura, atributos: attr })}
      ${sel ? campo('Grupo', 'grupo_fijo', { valor: f.grupo, atributos: 'readonly' })
    : campo('Grupo', 'grupo', { valor: f.grupo, opciones: grupos.length ? grupos : ['Registra un grupo primero'], atributos: attr })}
      ${campo('Docente', 'docente', { valor: f.docente, opciones: opcionesDocente, atributos: attr })}
      <p class="nota">Peso de cada parcial en la calificación final (%)</p>
      <div class="tres">${[1, 2, 3].map((i) => campo(`Parcial ${i}`, `p${i}`, { valor: f[`p${i}`], ancho: 6, atributos: attr })).join('')}</div>
      <div class="acciones">${boton(sel ? 'Guardar cambios' : 'Crear materia', app.accion('guardar-materia', guardar))}
        ${sel ? boton('Nueva', app.accion('nueva-materia', limpiar), true) + boton('Eliminar', app.accion('eliminar-materia', eliminar), true) : ''}</div></div>`),
    tarjeta('Grupos', `${tabla('grupos-materias', [['grupo', 'Grupo'], ['semestre', 'Semestre'], ['turno', 'Turno'], ['materias', 'Materias'], ['sin_docente', 'Sin docente']],
      [...g.gruposRegistro.values()].sort((a, b) => comparar(a.clave, b.clave)).map((gr) => ({ grupo: gr.clave, semestre: `${gr.semestre}.º`, turno: gr.turno,
        materias: g.cursosDeGrupo(gr.clave).length, sin_docente: g.cursosDeGrupo(gr.clave).filter((c) => !g.docenteDe(c.id)).length })), { ordenables: [] })}
      <p class="nota">Los grupos se registran en la pestaña «Grupos».</p>`),
  ])}
  ${tarjeta('Materias', `${tabla('materias', [['clave', 'Clave'], ['asignatura', 'Asignatura'], ['grupo', 'Grupo'], ['docente', 'Docente'], ['inscritos', 'Inscritos'],
    ['pesos', 'Parciales %'], ['plan', 'Plan de evaluación']], filas, { ordenables: [], seleccionable: true, clave: 'clave', seleccionada: sel })}
    <p class="nota">Haz clic en una materia para editarla, cambiar su docente o eliminarla.</p>`)}`;
}

// ------------------------------------------------------------------ control escolar: docentes
function docentes(app) {
  const g = app.gestor;
  const lista = [...g.docentes.values()].sort((a, b) => comparar(a.id, b.id));
  const f = app.filtro('form_docente', { nombre: '', docente: lista[0] ? docenteTxt(lista[0]) : '' });
  if (lista.length && !lista.some((d) => docenteTxt(d) === f.docente)) f.docente = docenteTxt(lista[0]);
  const did = id(f.docente);
  const registrar = () => {
    const [ok, d] = app.hacer(() => g.altaDocente(f.nombre));
    if (!ok) return;
    Object.assign(f, { nombre: '', docente: docenteTxt(d) });
    app.render();
    app.avisar('Docente registrado', `${d.id} · ${d.nombre}. Usuario: ${d.id.toLowerCase()} · contraseña inicial: ${d.id.toLowerCase()} (se le pedirá cambiarla). Ahora asígnale sus materias.`);
  };
  const guardar = () => app.intentar(() => g.asignarCursos(did, [...document.querySelectorAll('.lista-materias input:checked')].map((x) => x.value)));
  const usuarios = new Map(app.usuarios.filter((u) => u.rol === 'Docente').map((u) => [u.referencia, u.usuario]));
  const cursos = [...g.cursos.values()].sort((a, b) => comparar(a.grupo + a.asignatura, b.grupo + b.asignatura));
  return `${encabezado('Control escolar  /  Docentes', 'Docentes', 'Al registrar un docente se crea su cuenta. Cada materia tiene un solo docente.')}
    ${rejilla(2, [
    tarjeta('Nuevo docente', `<div class="pila">${campo('ID', 'id', { valor: `${g.siguienteDocente()}  (se asigna al guardar)`, atributos: 'readonly' })}
      ${campo('Nombre completo', 'nombre', { valor: f.nombre, atributos: 'data-filtro="form_docente"' })}
      <div class="acciones">${boton('Registrar docente', app.accion('registrar-docente', registrar))}</div></div>`),
    tarjeta('Materias del docente', lista.length ? `${campo('Docente', 'docente', { valor: f.docente, opciones: lista.map(docenteTxt), atributos: 'data-filtro="form_docente" data-refrescar' })}
      <div class="lista-alumnos lista-materias">${cursos.map((c) => {
        const actual = g.docenteDe(c.id);
        return `<label class="check"><input type="checkbox" value="${esc(c.id)}" ${actual?.id === did ? 'checked' : ''}>
          <span class="cuenta">${esc(c.id)}</span> ${esc(c.asignatura)} · ${esc(c.grupo)}${actual && actual.id !== did ? ` <small>· ahora: ${esc(actual.nombre)}</small>` : ''}</label>`;
      }).join('') || '<p class="nota">No hay materias registradas.</p>'}</div>
      <div class="acciones">${boton('Guardar asignación', app.accion('guardar-asignacion', guardar))}</div>
      <p class="nota">Si marcas una materia de otro docente, pasa a este docente.</p>` : '<p class="nota">Registra un docente primero.</p>'),
  ])}
    ${tarjeta('Plantilla docente', tabla('plantilla', [['id', 'ID'], ['nombre', 'Nombre'], ['usuario', 'Usuario'], ['materias', 'Materias'], ['alumnos', 'Alumnos']],
      lista.map((d) => ({ id: d.id, nombre: d.nombre, usuario: usuarios.get(d.id) ?? '—',
        materias: g.cursosDeDocente(d.id).map((c) => `${c.asignatura} (${c.grupo})`).join(', ') || 'Sin materias',
        alumnos: g.cursosDeDocente(d.id).reduce((t, c) => t + g.alumnosDeCurso(c.id).length, 0) })), { ordenables: [] }))}`;
}

// ------------------------------------------------------------------ control escolar: inscripciones
function inscripciones(app) {
  const g = app.gestor;
  const grupos = g.grupos();
  const f = app.filtro('form_inscripcion', { grupo: grupos[0] ?? '', alumno: '' });
  if (!grupos.includes(f.grupo)) f.grupo = grupos[0] ?? '';
  const alumnos = [...g.estudiantes.values()].filter((e) => e.grupo === f.grupo).sort((a, b) => comparar(a.apellidos + a.nombre, b.apellidos + b.nombre));
  const nombres = alumnos.map((e) => `${e.num_cuenta} · ${e.apellidos} ${e.nombre}`);
  if (!nombres.includes(f.alumno)) f.alumno = nombres[0] ?? '';
  const nc = id(f.alumno);
  const cursosGrupo = g.cursosDeGrupo(f.grupo);
  const inscritoEn = new Set(g.listaInscripciones().filter(([n]) => n === nc).map(([, c]) => c));
  const guardar = () => {
    if (!g.estudiantes.has(nc)) return;
    const elegidos = [...document.querySelectorAll('.lista-inscripcion input:checked')].map((x) => x.value);
    const quitar = [...inscritoEn].filter((c) => !elegidos.includes(c));
    const conNotas = quitar.filter((c) => g.parcialesDe(c).flatMap((p) => g.evaluacionesDe(p.id)).some((e) => g.calificacion(nc, e.id) !== null));
    if (conNotas.length && !app.confirmar(`Se borrarán las calificaciones de ${conNotas.length} materia(s) que ya tienen captura. ¿Continuar?`)) return;
    const [ok, res] = app.hacer(() => g.actualizarInscripciones(nc, elegidos));
    if (ok) app.avisar('Inscripción actualizada', `Altas: ${res[0]}   ·   Bajas: ${res[1]}`);
  };
  const faltan = alumnos.reduce((t, e) => t + cursosGrupo.filter((c) => !g.listaInscripciones().some(([n, x]) => n === e.num_cuenta && x === c.id)).length, 0);
  const filas = [...g.cursos.values()].sort((a, b) => comparar(a.grupo + a.asignatura, b.grupo + b.asignatura)).map((c) => {
    const n = g.alumnosDeCurso(c.id).length;
    const total = [...g.estudiantes.values()].filter((e) => e.grupo === c.grupo).length;
    return { clave: c.id, asignatura: c.asignatura, grupo: c.grupo, inscritos: n, del_grupo: total, estado: n === total ? 'Completo' : 'Pendiente' };
  });
  const attr = 'data-filtro="form_inscripcion" data-refrescar';
  return `${encabezado('Control escolar  /  Inscripciones', 'Inscripciones', 'La inscripción al grupo es automática; aquí se atienden altas y bajas individuales.')}
    ${rejilla(2, [
    tarjeta('Por estudiante', `<div class="dos">${campo('Grupo', 'grupo', { valor: f.grupo, opciones: grupos.length ? grupos : ['—'], atributos: attr })}
        ${campo('Estudiante', 'alumno', { valor: f.alumno, opciones: nombres.length ? nombres : ['—'], atributos: attr })}</div>
      <div class="lista-alumnos lista-inscripcion">${cursosGrupo.map((c) => `<label class="check"><input type="checkbox" value="${esc(c.id)}" ${inscritoEn.has(c.id) ? 'checked' : ''}>
        <span class="cuenta">${esc(c.id)}</span> ${esc(c.asignatura)}</label>`).join('') || '<p class="nota">El grupo no tiene materias.</p>'}</div>
      <div class="acciones">${boton('Guardar inscripción', app.accion('guardar-inscripcion', guardar))}</div>`),
    tarjeta('Por grupo', `<p class="resumen"><b>Grupo ${esc(f.grupo || '—')}: ${alumnos.length} alumnos, ${cursosGrupo.length} materias</b></p>
      <p class="${faltan ? 'tono-ambar' : 'suave'}">Inscripciones faltantes: ${faltan}</p>
      <div class="acciones">${boton('Inscribir al grupo en todas sus materias', app.accion('inscribir-grupo', () => f.grupo && app.intentar(() => g.inscribirGrupo(f.grupo))), !faltan)}</div>
      <p class="nota">Útil si se dieron bajas individuales por error o después de una carga masiva.</p>`),
  ])}
    ${tarjeta('Inscritos por materia', tabla('inscritos', [['clave', 'Clave'], ['asignatura', 'Asignatura'], ['grupo', 'Grupo'], ['inscritos', 'Inscritos'],
      ['del_grupo', 'Alumnos del grupo'], ['estado', 'Estado']], filas, { ordenables: [] }))}`;
}

// ------------------------------------------------------------------ control escolar: usuarios
function usuarios(app) {
  const g = app.gestor;
  const f = app.filtro('usuarios', { texto: '', rol: 'Todos', grupo: 'Todos', sel: '' });
  const grupoDe = (u) => (u.rol === 'Alumno' ? g.estudiantes.get(u.referencia)?.grupo ?? '—' : '—');
  const elegido = app.usuarios.find((u) => u.usuario === f.sel);
  const visible = elegido ? contrasenaVisible(elegido) : null;
  const restablecer_ = () => {
    if (!app.confirmar(`La contraseña de ${elegido.usuario} será «${elegido.usuario}» y deberá cambiarla al entrar. ¿Continuar?`)) return;
    restablecer(elegido);
    app.guardarAhora();
    app.registrarActividad(`contrasena_restablecida · ${elegido.usuario}`);
    app.render();
  };
  const abrirReporte = (filtro) => () => {
    app.estado.filtros.reporte = { tipo: 'credenciales', grupo: '', alumno: '', materia: '', orden: 'Por apellidos', estado: 'Todos', usuarios: filtro };
    app.ir('Reportes');
  };
  const filas = app.usuarios.map((u) => ({ usuario: u.usuario, nombre: u.nombre, rol: u.rol, grupo: grupoDe(u),
    contrasena: contrasenaVisible(u) ?? 'Personalizada', estado: contrasenaVisible(u) ? 'Contraseña inicial' : 'Activa' }));
  const visibles = () => {
    const q = f.texto.trim().toLowerCase();
    return filas.filter((x) => [x.rol, 'Todos'].includes(f.rol) && [x.grupo, 'Todos'].includes(f.grupo)
      && (!q || x.usuario.toLowerCase().includes(q) || x.nombre.toLowerCase().includes(q)));
  };
  const tablaHtml = () => tabla('usuarios', [['usuario', 'Usuario'], ['nombre', 'Nombre'], ['rol', 'Rol'], ['grupo', 'Grupo'], ['contrasena', 'Contraseña'],
    ['estado', 'Estado']], visibles(), { ordenables: [], seleccionable: true, clave: 'usuario', seleccionada: f.sel });
  app.alFiltrar('usuarios', () => app.reemplazar('tabla-usuarios', tablaHtml()));
  app.alSeleccionar('usuarios', (u) => { f.sel = u; app.render(); });
  const detalle = elegido ? `<div class="datos-cuenta">${[['Usuario', elegido.usuario], ['Nombre', elegido.nombre], ['Rol', elegido.rol],
    elegido.rol === 'Alumno' ? ['Grupo', grupoDe(elegido)] : ['Materias', g.cursosDeDocente(elegido.referencia).map((c) => c.id).join(', ') || '—']]
    .map(([k, v]) => `<div><small>${esc(k)}</small><strong>${esc(v)}</strong></div>`).join('')}</div>
      <div class="caja-contrasena"><small>Contraseña</small>${visible
    ? `<strong class="grande">${esc(visible)}</strong><p class="suave">Contraseña inicial: se le pedirá cambiarla al entrar.</p>`
    : '<strong>Personalizada por el usuario</strong><p class="suave">Está cifrada y no se puede consultar. Restablécela para asignarle una nueva.</p>'}</div>
      ${elegido.usuario !== app.estado.cuenta?.usuario ? `<div class="acciones">${boton('Restablecer contraseña', app.accion('restablecer', restablecer_), true)}</div>` : ''}`
    : '<p class="nota">Selecciona una cuenta en la tabla.</p>';
  const respaldo = () => app.guardarArchivo('respaldo_syllabus.json', JSON.stringify({ institucion: app.institucion, datos: g.aObjeto(), usuarios: app.usuarios }, null, 1), 'application/json');
  const reiniciar = async () => {
    if (!app.confirmar('Se borrarán todos los cambios y se volverá a los datos de ejemplo. ¿Continuar?')) return;
    await app.restablecerEjemplo();
    app.salir();
  };
  return `${encabezado('Control escolar  /  Usuarios', 'Usuarios y accesos',
    'Haz clic en una cuenta para ver su usuario y contraseña. Restablecer deja la contraseña igual al usuario.')}
    ${rejilla(2, [tarjeta('Cuenta seleccionada', detalle),
    tarjeta('Reporte de usuarios y contraseñas', `<p class="nota">Genera la lista con encabezado institucional en PDF, CSV o JSON.</p>
      <div class="botones-filtro">${['Docentes', ...g.grupos().map((x) => `Grupo ${x}`), 'Todos'].map((x, i) => boton(x, app.accion(`rep-${i}`, abrirReporte(x)), i > 0)).join('')}</div>`)])}
    ${tarjeta('Cuentas', `<div class="fila">${campo('Buscar', 'texto', { valor: f.texto, ancho: 24, atributos: 'data-filtro="usuarios"' })}
      ${campo('Rol', 'rol', { valor: f.rol, opciones: ['Todos', 'Administrador', 'Docente', 'Alumno'], atributos: 'data-filtro="usuarios" data-refrescar' })}
      ${campo('Grupo', 'grupo', { valor: f.grupo, opciones: ['Todos', ...g.grupos()], atributos: 'data-filtro="usuarios" data-refrescar' })}</div>${tablaHtml()}`)}
    ${tarjeta('Respaldo de datos', `<div class="acciones">${boton('Exportar respaldo', app.accion('respaldo', respaldo))}${boton('Restablecer datos de ejemplo', app.accion('reiniciar', reiniciar), true)}</div>`,
    'Todo se guarda automáticamente en datos/base.json después de cada cambio.')}`;
}

// ------------------------------------------------------------------ mi cuenta (todos los perfiles)
function miCuenta(app) {
  const cuenta = app.estado.cuenta;
  const guardar = (d) => {
    const forzado = cuenta.debe_cambiar;
    try {
      cambiarContrasena(cuenta, d.actual, d.nueva, d.confirmacion);
    } catch (e) {
      return app.avisar('No se cambió la contraseña', e.detalle[0].toUpperCase() + e.detalle.slice(1));
    }
    app.guardarAhora();
    app.registrarActividad(`contrasena_cambiada · ${cuenta.usuario}`);
    app.avisar('Contraseña actualizada', 'Tu contraseña se cambió correctamente.');
    app.ir(forzado ? 'Inicio' : 'Mi cuenta');
  };
  const secreto = (etiqueta, nombre) => `<label class="campo"><span>${esc(etiqueta)}</span><span class="con-ojo"><input type="password" name="${nombre}" autocomplete="${nombre === 'actual' ? 'current-password' : 'new-password'}">${verContrasena(app, nombre)}</span></label>`;
  return `${encabezado(`${app.estado.rol}  /  Mi cuenta`, 'Mi cuenta', 'Datos de acceso y cambio de contraseña.')}
    ${cuenta.debe_cambiar ? '<div class="aviso-ambar">Estás usando la contraseña inicial. Cámbiala para continuar.</div>' : ''}
    ${rejilla(2, [
    tarjeta('Datos de la cuenta', `<div class="datos-cuenta vertical">${[['Usuario', cuenta.usuario], ['Nombre', cuenta.nombre], ['Rol', cuenta.rol],
      ['Referencia', cuenta.referencia ?? '—']].map(([k, v]) => `<div><small>${esc(k)}</small><strong>${esc(v)}</strong></div>`).join('')}</div>`),
    `<form class="tarjeta pila" ${app.formulario('contrasena', guardar)}><h3>Cambiar contraseña</h3>
      ${secreto('Contraseña actual', 'actual')}${secreto('Nueva contraseña', 'nueva')}${secreto('Confirmar nueva contraseña', 'confirmacion')}
      <p class="nota">Mínimo 8 caracteres, con letras y números.</p><div class="acciones"><button class="boton">Guardar contraseña</button></div></form>`,
  ])}`;
}

// ------------------------------------------------------------------ docente: plan de evaluación
const PLANTILLA = [['Examen', '60'], ['Tareas', '20'], ['Actividades', '20']];

const selectorCurso = (app, clave, cursos) => {
  const opciones = cursos.map((c) => `${c.id} · ${c.asignatura} · ${c.grupo}`);
  const f = app.filtro(clave, { curso: opciones[0] ?? '' });
  if (!opciones.includes(f.curso)) f.curso = opciones[0] ?? '';
  return [app.gestor.cursos.get(id(f.curso)), campo('Materia', 'curso', { valor: f.curso, opciones: opciones.length ? opciones : ['—'], atributos: `data-filtro="${clave}" data-refrescar` })];
};

function planEvaluacion(app) {
  const g = app.gestor;
  const [curso, selector] = selectorCurso(app, 'plan_curso', app.cursosVisibles());
  const cabeza = encabezado('Docente  /  Plan de evaluación', 'Plan de evaluación', 'Define las evaluaciones de cada parcial y su porcentaje. Cada parcial debe sumar 100 %.');
  if (!curso) return `${cabeza}${tarjeta('', '<p class="nota">No tienes materias asignadas.</p>')}`;
  const borradores = (app.estado.plan ??= {});
  const parciales = g.parcialesDe(curso.id);
  parciales.forEach((p) => { borradores[p.id] ??= g.evaluacionesDe(p.id).map((e) => ({ id: e.id, nombre: e.nombre, pct: txtPct(e.peso) })); });
  const sumaTxt = (pid) => {
    const total = borradores[pid].map((r) => num(r.pct)).filter((x) => typeof x === 'number').reduce((a, b) => a + b, 0);
    const ok = Math.abs(total - 100) < 1e-9;
    return `<span class="${ok ? 'tono-verde' : 'tono-rojo'}">Suma: ${+total.toFixed(6)} %  ${ok ? '✓' : '· debe ser 100 %'}</span>`;
  };
  app.alEscribir('plan', (el) => {
    borradores[el.dataset.pid][Number(el.dataset.i)][el.name] = el.value;
    document.getElementById(`suma-${el.dataset.pid}`).innerHTML = sumaTxt(el.dataset.pid);
  });
  const plantilla = () => {
    parciales.forEach((p) => {
      if (!g.evaluacionesDe(p.id).length || app.confirmar(`El parcial ${p.numero} ya tiene plan. ¿Reemplazarlo?`)) {
        borradores[p.id] = PLANTILLA.map(([nombre, pct]) => ({ id: null, nombre, pct }));
      }
    });
    app.render();
  };
  const guardar = () => {
    const planes = Object.fromEntries(parciales.map((p) => [p.id, borradores[p.id].map((r) => ({ id: r.id, nombre: r.nombre, peso: pctAPeso(r.pct) }))]));
    const perdidas = parciales.flatMap((p) => g.evaluacionesDe(p.id).filter((e) => !borradores[p.id].some((r) => r.id === e.id)))
      .reduce((t, e) => t + g.alumnosDeCurso(curso.id).filter((a) => g.calificacion(a.num_cuenta, e.id) !== null).length, 0);
    if (perdidas && !app.confirmar(`Se borrarán ${perdidas} calificaciones ya capturadas. ¿Continuar?`)) return;
    if (app.intentar(() => g.definirPlanes(planes))) {
      parciales.forEach((p) => delete borradores[p.id]);
      app.avisar('Plan guardado', `Plan de evaluación de ${curso.asignatura} guardado. Ya puedes capturar calificaciones.`);
    }
  };
  const descartar = () => { parciales.forEach((p) => delete borradores[p.id]); app.render(); };
  const tarjetaParcial = (p) => `<section class="tarjeta"><h3>Parcial ${p.numero}  ·  ${txtPct(p.peso)} % del curso</h3>
    <div class="rubro cabeza"><span>Evaluación</span><span>%</span></div>
    ${borradores[p.id].map((r, i) => `<div class="rubro">
      <input type="text" name="nombre" value="${esc(r.nombre)}" data-entrada="plan" data-pid="${p.id}" data-i="${i}" aria-label="Nombre de la evaluación">
      <input type="text" name="pct" value="${esc(r.pct)}" data-entrada="plan" data-pid="${p.id}" data-i="${i}" class="corto" aria-label="Porcentaje">
      <button type="button" class="quitar" title="Quitar" ${app.accion(`quitar-${p.id}-${i}`, () => { borradores[p.id].splice(i, 1); app.render(); })}>✕</button></div>`).join('')}
    <button type="button" class="enlace" ${app.accion(`agregar-${p.id}`, () => { borradores[p.id].push({ id: null, nombre: '', pct: '' }); app.render(); })}>+  Agregar evaluación</button>
    <p class="suma" id="suma-${p.id}">${sumaTxt(p.id)}</p></section>`;
  return `${cabeza}
    ${tarjeta('', `<div class="fila">${selector}${boton('Usar plantilla (Examen 60 · Tareas 20 · Actividades 20)', app.accion('plantilla', plantilla), true)}</div>`)}
    ${rejilla(3, parciales.map(tarjetaParcial))}
    <div class="acciones">${boton('Guardar plan', app.accion('guardar-plan', guardar))}${boton('Descartar cambios', app.accion('descartar-plan', descartar), true)}</div>
    ${notaPie('Al quitar una evaluación que ya tiene calificaciones, esas calificaciones se borran (se te pedirá confirmar). Cambiar porcentajes recalcula los promedios.')}`;
}

// ------------------------------------------------------------------ docente: captura por acta
function captura(app) {
  const activa = app.pestana('captura');
  const [titulo, subtitulo] = activa === 0 ? ['Acta de calificaciones', 'Captura las calificaciones de todo el grupo y guárdalas juntas.']
    : ['Importar calificaciones', 'Carga y procesa calificaciones de varios estudiantes desde un archivo.'];
  return `${encabezado('Docente  /  Captura', titulo, subtitulo)}${pestanas('captura', ['Acta de calificaciones', 'Importar archivo'], activa)}
    ${activa === 0 ? acta(app) : capturaVarias(app)}`;
}

const valida = (t) => {
  const s = t.trim();
  if (!s) return true;
  const x = Number(s);
  return s !== '' && !Number.isNaN(x) && x >= 0 && x <= 10 && Math.abs(Math.round(x * 10) / 10 - x) < 1e-9;
};

function acta(app) {
  const g = app.gestor;
  const [curso, selector] = selectorCurso(app, 'acta_curso', app.cursosVisibles());
  if (!curso) return tarjeta('', '<p class="nota">No tienes materias asignadas.</p>');
  const parciales = g.parcialesDe(curso.id);
  const nombres = parciales.map((p) => `Parcial ${p.numero} · ${txtPct(p.peso)} %`);
  const fp = app.filtro('acta_parcial', { parcial: nombres[0] ?? '' });
  if (!nombres.includes(fp.parcial)) fp.parcial = nombres[0] ?? '';
  const parcial = parciales[nombres.indexOf(fp.parcial)];
  const evals = parcial ? g.evaluacionesDe(parcial.id) : [];
  const barra = tarjeta('', `<div class="fila">${selector}${campo('Parcial', 'parcial', { valor: fp.parcial, opciones: nombres, atributos: 'data-filtro="acta_parcial" data-refrescar' })}</div>`);
  if (!evals.length || Math.abs(sumaPesos(evals) - 1) > 1e-9) {
    return `${barra}${tarjeta('', `<p class="tono-ambar"><b>Este parcial no tiene su plan de evaluación completo (100 %).</b></p>
      <div class="acciones">${boton('Definir plan de evaluación', app.accion('ir-plan', () => app.ir('Plan de evaluación')))}</div>`)}`;
  }
  const alumnos = g.alumnosDeCurso(curso.id);
  const cambios = ((app.estado.acta ??= {})[parcial.id] ??= {});
  const guardado = (nc, eid) => { const x = g.calificacion(nc, eid); return x === null ? '' : String(x); };
  const valorDe = (nc, eid) => cambios[`${nc}|${eid}`] ?? guardado(nc, eid);
  const resumenFila = (nc) => {
    const textos = evals.map((e) => valorDe(nc, e.id));
    const validos = textos.map(valida);
    if (!validos.every(Boolean)) return ['—', 'Con errores', 'rojo'];
    if (!textos.every((t) => t.trim())) return ['—', 'Incompleto', 'ambar'];
    return [fmt(mostrar(textos.reduce((t, x, i) => t + Number(x) * evals[i].peso, 0))), 'Completo', 'verde'];
  };
  const contador = () => (Object.keys(cambios).length ? `Cambios sin guardar: ${Object.keys(cambios).length}` : 'Sin cambios pendientes');
  app.alEscribir('acta', (el) => {
    const [nc, eid] = [el.dataset.cuenta, el.dataset.eval];
    const k = `${nc}|${eid}`;
    if (el.value.trim() === guardado(nc, eid)) delete cambios[k]; else cambios[k] = el.value;
    el.classList.toggle('invalida', !valida(el.value));
    const [nota, estado, tono] = resumenFila(nc);
    const fila = el.closest('tr');
    fila.querySelector('.nota-parcial').textContent = nota;
    Object.assign(fila.querySelector('.estado-fila'), { textContent: estado, className: `estado-fila tono-${tono}` });
    document.getElementById('contador-acta').textContent = contador();
  });
  const guardar = () => {
    const lote = Object.entries(cambios).map(([k, t]) => [...k.split('|'), t.trim() ? num(t) : null]);
    if (!lote.length) return app.avisar('Acta', 'No hay cambios por guardar.');
    const borrar = lote.filter(([, , x]) => x === null).length;
    if (borrar && !app.confirmar(`Dejaste vacías ${borrar} calificaciones que ya estaban capturadas; se borrarán. ¿Continuar?`)) return;
    const r = g.guardarActa(lote);
    const conError = new Set(r.errores.map((x) => `${x.num_cuenta}|${x.evaluacion_id}`));
    Object.keys(cambios).filter((k) => !conError.has(k)).forEach((k) => delete cambios[k]);
    const mensaje = `Nuevas: ${r.capturadas}   ·   Modificadas: ${r.modificadas}   ·   Borradas: ${r.borradas}`;
    app.avisar(r.errores.length ? 'Acta guardada con errores' : 'Acta guardada', r.errores.length
      ? `${mensaje}. No se guardaron ${r.errores.length}: ${r.errores.slice(0, 8).map((x) => `${x.num_cuenta} · ${x.evaluacion_id}: ${x.detalle}`).join('; ')}`
      : mensaje);
  };
  const descartar = () => { Object.keys(cambios).forEach((k) => delete cambios[k]); app.render(); };
  const cuerpo = alumnos.map((e, i) => {
    const [nota, estado, tono] = resumenFila(e.num_cuenta);
    return `<tr><td>${i + 1}</td><td>${esc(e.num_cuenta)}</td><td>${esc(e.apellidos)} ${esc(e.nombre)}</td>
      ${evals.map((ev, j) => {
        const valor = valorDe(e.num_cuenta, ev.id);
        return `<td><input type="text" inputmode="decimal" class="celda${valida(valor) ? '' : ' invalida'}" value="${esc(valor)}" data-entrada="acta"
          data-cuenta="${esc(e.num_cuenta)}" data-eval="${esc(ev.id)}" data-renglon="${i}" data-columna="${j}" aria-label="${esc(ev.nombre)} de ${esc(e.apellidos)} ${esc(e.nombre)}"></td>`;
      }).join('')}
      <td class="nota-parcial">${nota}</td><td class="estado-fila tono-${tono}">${estado}</td></tr>`;
  }).join('');
  return `${barra}
    ${tarjeta(`${curso.asignatura} · grupo ${curso.grupo} · ${fp.parcial}`, `<div class="tabla-caja"><table class="acta"><thead><tr><th>No.</th><th>Cuenta</th><th>Alumno</th>
      ${evals.map((e) => `<th>${esc(e.nombre)} ${txtPct(e.peso)} %</th>`).join('')}<th>Parcial</th><th>Estado</th></tr></thead>
      <tbody>${cuerpo || `<tr class="vacio"><td colspan="${evals.length + 5}">No hay alumnos inscritos en esta materia.</td></tr>`}</tbody></table></div>
      <p class="contador" id="contador-acta">${contador()}</p>
      <div class="acciones">${boton('Guardar acta', app.accion('guardar-acta', guardar))}${boton('Descartar cambios', app.accion('descartar-acta', descartar), true)}</div>`,
    'Escribe calificaciones de 0 a 10 con un decimal. Enter o ↓ baja al siguiente alumno; deja vacío para no capturar.')}`;
}

// ------------------------------------------------------------------ reportes institucionales
/** El reporte como se ve impreso: encabezado institucional, datos del alcance, tabla, resumen, firmas y nota. */
function vistaDocumento(doc) {
  const e = doc.encabezado;
  return `<article class="documento">
    <header class="documento-cabeza">
      <div class="documento-arriba"><div><strong class="institucion">${esc(e.institucion)}</strong><small>${esc(e.programa)}</small></div>
        <div class="ciclo"><small>CICLO ESCOLAR</small><strong>${esc(e.ciclo)}</strong></div></div>
      <h2>${esc(e.titulo.toUpperCase())}</h2>
      <div class="documento-campos">${e.campos.map(([k, v]) => `<div><small>${esc(k)}</small><strong>${esc(fmt(v))}</strong></div>`).join('')}</div>
      <p class="emision">Emitido el ${esc(e.emision)}${e.emitido_por ? ` por ${esc(e.emitido_por)}` : ''}</p>
    </header>
    <div class="documento-cuerpo">
      ${tabla(`doc-${doc.tipo}`, doc.columnas, doc.filas, { ordenables: [], sinOrden: true })}
      <div class="documento-resumen">${doc.resumen.map(([k, v]) => `<div><small>${esc(k)}</small><strong>${esc(fmt(v))}</strong></div>`).join('')}</div>
      ${doc.firmas.length ? `<div class="firmas">${doc.firmas.map((x) => `<div><span class="linea"></span><small>${esc(x)}</small></div>`).join('')}</div>` : ''}
      ${doc.nota ? `<p class="nota">${esc(doc.nota)}</p>` : ''}
    </div></article>`;
}

async function exportarDoc(app, doc, formato) {
  const nombre = `${D.nombreArchivo(doc)}.${formato}`;
  const [contenido, tipo] = formato === 'pdf' ? [generar(doc, TEMAS[app.estado.tema][0]), 'application/pdf']
    : formato === 'json' ? [JSON.stringify(D.aJson(doc), null, 2), 'application/json'] : [D.aCsvDocumento(doc), 'text/csv'];
  if (await app.guardarArchivo(nombre, contenido, tipo)) app.registrarActividad(`reporte_exportado · ${nombre}`);
}

const botonesExportar = (app, doc, extra = []) => `<div class="acciones">${boton('Exportar PDF', app.accion('exp-pdf', () => exportarDoc(app, doc, 'pdf')))}
  ${boton('CSV', app.accion('exp-csv', () => exportarDoc(app, doc, 'csv')), true)}${boton('JSON', app.accion('exp-json', () => exportarDoc(app, doc, 'json')), true)}
  ${extra.map(([t, f], i) => boton(t, app.accion(`exp-extra-${i}`, f), true)).join('')}</div>`;

async function exportarPorAlumno(app, tipo, alumnos) {
  const archivos = alumnos.map((e) => {
    const doc = D[tipo](app.gestor, e.num_cuenta, app.institucion, emisor(app));
    return [`${D.nombreArchivo(doc)}.pdf`, generar(doc, TEMAS[app.estado.tema][0]), 'application/pdf'];
  });
  if (await app.guardarEnCarpeta(archivos)) {
    app.registrarActividad(`reportes_exportados · ${alumnos.length} ${tipo}`);
    app.avisar('Reportes exportados', `Se generó un ${D.CATALOGO[tipo][0].toLowerCase()} en PDF por cada uno de los ${alumnos.length} alumnos.`);
  }
}

function reportesVista(app) {
  const g = app.gestor;
  const rol = app.estado.rol;
  const tipos = Object.entries(D.CATALOGO).filter(([, [, , roles]]) => roles.includes(rol)).map(([t]) => t);
  const f = app.filtro('reporte', { tipo: tipos[0], grupo: '', alumno: '', materia: '', orden: 'Por apellidos', estado: 'Todos', usuarios: 'Docentes' });
  f.usuarios ??= 'Docentes';
  if (!tipos.includes(f.tipo)) f.tipo = tipos[0];
  const [, alcance] = D.CATALOGO[f.tipo];
  const cursos = app.cursosVisibles();
  const grupos = rol === 'Docente' ? unicos(cursos.map((c) => c.grupo)) : g.grupos();
  const elegir = (clave, opciones, todos = null) => {
    const lista = [...(todos ? [todos] : []), ...opciones];
    if (!lista.includes(f[clave])) f[clave] = lista[0] ?? '';
    return lista;
  };
  const sel = (etiqueta, clave, opciones, ancho) => campo(etiqueta, clave, { valor: f[clave], opciones: opciones.length ? opciones : ['—'], ancho, atributos: 'data-filtro="reporte" data-refrescar' });
  const materias = [...cursos].sort((a, b) => comparar(a.grupo + a.asignatura, b.grupo + b.asignatura)).map((c) => `${c.id} · ${c.asignatura} · ${c.grupo}`);
  const inst = app.institucion;
  let controles = '';
  let doc = null;
  let extra = [];
  if (alcance === 'alumno') {
    const listaGrupos = elegir('grupo', grupos);
    const alumnos = [...g.estudiantes.values()].filter((e) => e.grupo === f.grupo).sort((a, b) => comparar(`${a.apellidos} ${a.nombre}`, `${b.apellidos} ${b.nombre}`));
    const listaAlumnos = elegir('alumno', alumnos.map((e) => `${e.num_cuenta} · ${e.apellidos} ${e.nombre}`));
    controles = sel('Grupo', 'grupo', listaGrupos, 8) + sel('Alumno', 'alumno', listaAlumnos, 30);
    if (g.estudiantes.has(id(f.alumno))) {
      doc = D[f.tipo](g, id(f.alumno), inst, emisor(app));
      extra = [[`PDF de todo el grupo ${f.grupo}`, () => exportarPorAlumno(app, f.tipo, alumnos)]];
    }
  } else if (alcance === 'materia' || alcance === 'materia_opcional') {
    const lista = elegir('materia', materias, alcance === 'materia_opcional' ? 'Todas' : null);
    controles = sel('Materia', 'materia', lista, 34);
    if (alcance === 'materia' && g.cursos.has(id(f.materia))) doc = D.acta(g, id(f.materia), inst, emisor(app));
    if (alcance === 'materia_opcional') {
      controles += sel('Estado', 'estado', ['Todos', 'Aprobado', 'Reprobado', 'Pendiente'], 12);
      doc = D.aprobacion(g, cursos, inst, emisor(app), f.materia === 'Todas' ? null : id(f.materia), f.estado);
    }
  } else if (alcance === 'usuarios') {
    controles = sel('Usuarios', 'usuarios', elegir('usuarios', ['Docentes', ...grupos.map((x) => `Grupo ${x}`), 'Todos']), 14);
    doc = D.credenciales(g, app.usuarios, f.usuarios, inst, emisor(app));
  } else {
    controles = sel('Grupo', 'grupo', elegir('grupo', grupos, alcance === 'grupo_opcional' ? 'Todos' : null), 8);
    const grupo = ['Todos', ''].includes(f.grupo) ? null : f.grupo;
    if (f.tipo === 'concentrado') {
      controles += sel('Orden', 'orden', ['Por apellidos', 'Por promedio'], 14);
      if (grupo) doc = D.concentrado(g, grupo, inst, emisor(app), f.orden === 'Por promedio' ? 'promedio' : 'apellidos');
    } else if (f.tipo === 'aprovechamiento') doc = D.aprovechamiento(g, cursos, inst, emisor(app), grupo);
    else doc = D.riesgoAcademico(g, inst, emisor(app), grupo);
  }
  const titulos = tipos.map((t) => [t, D.CATALOGO[t][0]]);
  return `${encabezado(`${rol === 'Docente' ? 'Docente' : 'Inicio'}  /  Reportes`, 'Reportes',
    'Documentos con encabezado institucional: por alumno, por materia, por grupo y de control escolar.')}
    ${tarjeta('Generar reporte', `<div class="fila">${campo('Reporte', 'tipo', { valor: f.tipo, opciones: titulos, atributos: 'data-filtro="reporte" data-refrescar' })}${controles}</div>
      ${doc ? botonesExportar(app, doc, extra) : '<p class="nota">No hay información para este reporte.</p>'}`)}
    ${doc ? vistaDocumento(doc) : ''}`;
}

// ------------------------------------------------------------------ expediente del alumno (consultas)
/** Datos, desglose por evaluación y reportes del alumno. Con soloMaterias (docente) solo se ven sus materias. */
function kardexCompleto(app, cuenta, resumen = null, soloMaterias = false) {
  const g = app.gestor;
  const r = resumen ?? g.resumenEstudiante(cuenta);
  const e = g.estudiantes.get(cuenta);
  if (!r || !e) return tarjeta('', '<p class="nota">Estudiante sin información.</p>');
  const cuentaEstado = (x) => r.cursos.filter((c) => c.bruto !== null && c.estatus === x).length;
  const pct = (x) => `${Math.round(x * 100)} %`;
  const desglose = r.cursos.map((c) => {
    const filas = g.parcialesDe(c.curso_id).flatMap((p) => g.evaluacionesDe(p.id).map((ev) => {
      const nota = g.calificacion(cuenta, ev.id);
      return { parcial: `Parcial ${p.numero} · ${pct(p.peso)}`, nota_parcial: mostrarONulo(g.califParcial(cuenta, p.id)),
        evaluacion: `${ev.nombre} · ${pct(ev.peso)}`, calificacion: nota === null ? 'Pendiente' : nota.toFixed(1), estado: nota === null ? 'Pendiente' : 'Capturada' };
    }));
    const estado = c.bruto === null ? 'Pendiente de cierre' : c.estatus === 'APROBADO' ? 'Aprobado' : 'Reprobado';
    return `<details class="materia"><summary><span class="clave">${esc(c.curso_id)}</span><strong>${esc(c.asignatura)}</strong>
        <span class="final">Promedio: ${fmt(mostrarONulo(c.bruto))}   ·   Final: ${fmt(calificacionFinal(c.bruto))}</span>
        <span class="tono-${estado === 'Aprobado' ? 'verde' : estado === 'Reprobado' ? 'rojo' : 'ambar'}">${estado}</span></summary>
      ${tabla(`desglose-${c.curso_id}`, [['parcial', 'Parcial'], ['nota_parcial', 'Nota del parcial'], ['evaluacion', 'Evaluación'], ['calificacion', 'Calificación'], ['estado', 'Estado']], filas, { ordenables: [] })}</details>`;
  }).join('');
  const datos = `<section class="tarjeta datos-alumno">
      <div><small>Número de cuenta</small><strong>${esc(cuenta)}</strong></div>
      <div><small>Estudiante</small><strong>${esc(e.apellidos)} ${esc(e.nombre)}</strong></div>
      <div><small>Grupo</small><strong>${esc(e.grupo)}</strong></div>
      ${soloMaterias ? `<div><small>Materias contigo</small><strong>${r.cursos.length}</strong></div>`
    : `<div><small>Estado académico</small><strong class="tono-${r.estatus === 'APROBADO' ? 'verde' : r.estatus === 'REPROBADO' ? 'rojo' : 'ambar'}">${esc(legible(r.estatus))}</strong></div>`}</section>`;
  const indicadores = [...(soloMaterias ? [] : [['Promedio general', mostrarONulo(r.general)]]), ['Aprobadas', cuentaEstado('APROBADO')],
    ['Reprobadas', cuentaEstado('REPROBADO')], ['Pendientes', r.cursos.filter((c) => c.bruto === null).length]];
  const final = soloMaterias ? '' : tarjeta('Resumen final', `<p class="resumen-kardex">Promedio general: <b>${fmt(mostrarONulo(r.general))}</b>   ·   Riesgo académico:
      <b class="tono-${r.riesgo === 'ALTO' ? 'rojo' : r.riesgo === 'MEDIO' ? 'ambar' : 'verde'}">${esc(legible(r.riesgo ?? 'PENDIENTE'))}</b></p>
      <p class="nota">${esc(r.motivo ?? '')}</p>
      ${botonesExportar(app, D.kardex(g, cuenta, app.institucion, emisor(app)), [['Boleta PDF', () => exportarDoc(app, D.boleta(g, cuenta, app.institucion, emisor(app)), 'pdf')]])}`);
  return `${datos}${mosaicos(indicadores)}
    ${tarjeta('Desglose por evaluación', desglose || '<p class="nota">Sin materias inscritas.</p>', 'Abre cada materia para ver sus parciales y evaluaciones con su peso.')}${final}`;
}

// ------------------------------------------------------------------ importar calificaciones desde archivo
function capturaVarias(app) {
  const g = app.gestor;
  const resultados = app.estado.resultadosVarias;
  const cargar = async (archivo) => {
    const datos = await app.leerArchivo(archivo);
    app.estado.archivoVarias = datos ? { nombre: archivo.name, datos } : null;
    app.render();
  };
  const procesar = () => {
    const pendiente = app.estado.archivoVarias;
    if (!pendiente) return app.avisar('Falta archivo', 'Selecciona un archivo JSON y pulsa Cargar.');
    app.estado.cargando = true;
    app.estado.resultadosVarias = (pendiente.datos.calificaciones ?? []).map((r, i) => {
      const antes = g.errores.length;
      const ok = g._procesar('calificacion', r, (x) => g.capturarCalificacion(x));
      return { no: i + 1, num_cuenta: String(r?.num_cuenta ?? '—'), evaluacion: r?.evaluacion_id ?? '—', valor: r?.valor ?? null,
        estado: ok ? 'Guardada' : 'Rechazada', detalle: ok ? '' : `${g.errores[antes].codigo}: ${g.errores[antes].detalle}` };
    });
    app.estado.cargando = false;
    app.registrarActividad(`procesamiento · ${app.estado.resultadosVarias.length} recibidas`);
    app.render();
  };
  const ok = resultados.filter((x) => x.estado === 'Guardada').length;
  return `${tarjeta('Captura de varias calificaciones', `
      <div class="fila-archivo">
        <label class="campo"><span>Archivo de calificaciones</span>
          <span class="selector-archivo">${esc(app.estado.archivoVarias?.nombre ?? 'Seleccionar archivo...')}
            <input type="file" accept=".json,application/json" ${app.accion('archivo-varias', (el) => el.files[0] && cargar(el.files[0]), 'change')}></span></label>
        ${boton('Cargar', app.accion('cargar-varias', () => document.querySelector('.selector-archivo input').click()))}
        ${boton('Procesar calificaciones', app.accion('procesar-varias', procesar))}
      </div>
      <p class="nota">Se validan los registros antes de guardar las calificaciones.</p>
      <p class="nota">Los registros con errores se identifican sin detener el procesamiento de los demás.</p>`)}
    ${tarjeta('Resultados de la captura', tabla('varias', [['no', 'No.'], ['num_cuenta', 'Estudiante'], ['evaluacion', 'Evaluación'], ['valor', 'Calificación'], ['estado', 'Estado'], ['detalle', 'Detalle']], resultados, { ordenables: [] }))}
    ${tarjeta('Resumen del procesamiento', `<p class="resumen-procesamiento">Recibidos: <b>${resultados.length || '—'}</b>   Procesados: <b>${resultados.length ? ok : '—'}</b>   Rechazados: <b>${resultados.length ? resultados.length - ok : '—'}</b></p>`)}`;
}

// ------------------------------------------------------------------ consultas
function consultas(app) {
  if (app.estado.kardexDe) {
    const docente = app.estado.rol === 'Docente';
    return `${encabezado(`${docente ? 'Docente' : 'Inicio'}  /  Consultas  /  ${docente ? 'Alumno' : 'Kárdex'}`,
    docente ? 'Calificaciones del alumno en tus materias' : 'Expediente del estudiante',
    docente ? 'Solo se muestran las materias que impartes.' : 'Datos, desglose por evaluación, resumen final y reportes del alumno (kárdex y boleta).')}
      ${kardexCompleto(app, app.estado.kardexDe, app.resumenesVisibles().find((r) => r.num_cuenta === app.estado.kardexDe), docente)}
      <div class="acciones"><button type="button" class="boton secundario" ${app.accion('volver-consultas', () => { app.estado.kardexDe = null; app.render(); })}>←   Volver a consultas</button></div>`;
  }
  const activa = app.pestana('consultas');
  const resumenes = app.resumenesVisibles();
  const vistas = [consultaEstudiantes, consultaRiesgo, consultaEstadisticas];
  return `${encabezado(`${app.estado.rol}  /  Consultas`, 'Consultas y kárdex', 'Busca por estudiante, grupo o asignatura; ordena por promedio y revisa el riesgo académico.')}
    ${pestanas('consultas', ['Estudiantes y kárdex', 'Promedios y riesgo académico', 'Estadísticas por curso'], activa)}
    ${vistas[activa](app, resumenes)}`;
}

function consultaEstudiantes(app, resumenes) {
  const filasCurso = filasPorCurso(resumenes);
  const riesgoPor = new Map(resumenes.map((r) => [r.num_cuenta, r]));
  const f = app.filtro('consultas', { estudiante: '', grupo: 'Todos', asignatura: 'Todas', estado: 'todos', minima: '', maxima: '' });
  if (app.estado.parametros.buscar !== undefined) { f.estudiante = app.estado.parametros.buscar; delete app.estado.parametros.buscar; }
  const columnas = [['num_cuenta', 'Núm. de cuenta'], ['apellidos', 'Apellidos'], ['nombre', 'Nombre'], ['grupo', 'Grupo'], ['asignatura', 'Asignatura'],
    ['parcial_1', 'P1'], ['parcial_2', 'P2'], ['parcial_3', 'P3'], ['promedio', 'Promedio'], ['estatus', 'Estado']];
  const aNumero = (s) => (s.trim() === '' || Number.isNaN(Number(s)) ? null : Number(s));
  const visibles = () => {
    const texto = f.estudiante.trim().toLowerCase();
    const [pe, pr] = [filtroEstado(f.estado), filtroRango(aNumero(f.minima), aNumero(f.maxima))];
    return filasCurso.filter((x) => (!texto || x.num_cuenta.includes(texto) || `${x.apellidos} ${x.nombre}`.toLowerCase().includes(texto))
      && [x.grupo, 'Todos'].includes(f.grupo) && [x.asignatura, 'Todas'].includes(f.asignatura)
      && pe({ ...x, riesgo: riesgoPor.get(x.num_cuenta).riesgo }) && pr(x));
  };
  const tablaHtml = () => tabla('consultas', columnas, visibles(), { orden: app.orden('consultas'), seleccionable: true });
  app.alFiltrar('consultas', () => app.reemplazar('tabla-consultas', tablaHtml()));
  app.alSeleccionar('consultas', (cuenta) => { app.estado.kardexDe = cuenta; app.render(); });
  const unicos = (k) => [...new Set(filasCurso.map((x) => x[k]))].sort(comparar);
  return `${tarjeta('Buscar y filtrar', `<div class="fila">
      ${campo('Estudiante (cuenta o nombre)', 'estudiante', { valor: f.estudiante, ancho: 24, atributos: 'data-filtro="consultas"' })}
      ${campo('Grupo', 'grupo', { valor: f.grupo, opciones: ['Todos', ...unicos('grupo')], atributos: 'data-filtro="consultas"' })}
      ${campo('Asignatura', 'asignatura', { valor: f.asignatura, opciones: ['Todas', ...unicos('asignatura')], atributos: 'data-filtro="consultas"' })}
      ${campo('Estado', 'estado', { valor: f.estado, opciones: ['todos', 'aprobado', 'reprobado', 'pendiente', 'riesgo'], atributos: 'data-filtro="consultas"' })}
      ${campo('Mínima', 'minima', { valor: f.minima, ancho: 6, atributos: 'data-filtro="consultas"' })}
      ${campo('Máxima', 'maxima', { valor: f.maxima, ancho: 6, atributos: 'data-filtro="consultas"' })}
      <label class="check"><input type="checkbox" ${app.estado.opciones.colorear ? 'checked' : ''} ${app.accion('colorear', (el) => { app.estado.opciones.colorear = el.checked; app.render(); }, 'change')}> Colorear estados</label>
    </div>`)}
    ${tarjeta('Estudiantes por asignatura', tablaHtml(), 'Haz clic en un estudiante para abrir su kárdex.')}`;
}

function consultaRiesgo(app, resumenes) {
  const cursos = app.cursosVisibles();
  const f = app.filtro('riesgo', { curso: cursos[0] ? `${cursos[0].id} · ${cursos[0].asignatura}` : '' }, true);
  const cid = id(f.curso);
  const est = estadisticas(resumenes.flatMap((r) => r.cursos).filter((c) => c.curso_id === cid && c.bruto !== null).map((c) => c.bruto));
  const filas = resumenes.map(filaEstudiante);
  const orden = app.orden('promedios', { col: 'promedio', desc: true });
  return `${tarjeta('Promedio del curso', `<div class="fila">${campo('Curso', 'curso', { valor: f.curso, opciones: opcionesCursos(cursos), atributos: 'data-filtro="riesgo" data-refrescar' })}</div>
      <p class="resumen" style="font-size:16px">Promedio del curso: ${fmt(est.promedio)}   ·   Máx ${fmt(est.maximo)}   ·   Mín ${fmt(est.minimo)}   ·   Aprobación ${fmt(est.aprobacion)} %</p>`)}
    ${tarjeta('Promedio general por estudiante', tabla('promedios', [['num_cuenta', 'Número de cuenta'], ['apellidos', 'Apellidos'], ['nombre', 'Nombre'], ['grupo', 'Grupo'], ['promedio', 'Promedio'], ['estatus', 'Estado'], ['riesgo', 'Riesgo']], filas, { orden }))}
    ${tarjeta('Estudiantes en riesgo académico', tabla('riesgo', [['num_cuenta', 'Número de cuenta'], ['apellidos', 'Apellidos'], ['nombre', 'Nombre'], ['grupo', 'Grupo'], ['promedio', 'Promedio'], ['riesgo', 'Riesgo'], ['motivo', 'Motivo']],
      filas.filter((x) => ['ALTO', 'MEDIO'].includes(x.riesgo)), { orden: app.orden('riesgo') }),
      'ALTO: algún curso reprobado o promedio general < 7.0 · MEDIO: promedio general entre 7.0 y 7.5.')}`;
}

function consultaEstadisticas(app, resumenes) {
  const ids = new Set(app.cursosVisibles().map((c) => c.id));
  const filas = estadisticasCursos(app.gestor, resumenes).filter((f) => ids.has(f.curso_id));
  const est = estadisticas(resumenes.flatMap((r) => r.cursos).filter((c) => c.bruto !== null).map((c) => c.bruto));
  return `${mosaicos([['Promedio de cursos', est.promedio], ['Calificación máxima', est.maximo], ['Calificación mínima', est.minimo], ['Aprobación (%)', est.aprobacion]])}
    ${tarjeta('Resultados agrupados por asignatura y grupo', tabla('estadisticas', [['curso_id', 'Curso'], ['asignatura', 'Asignatura'], ['grupo', 'Grupo'], ['docente', 'Docente'], ['inscritos', 'Estudiantes'], ['promedio', 'Promedio'], ['maximo', 'Máx.'], ['minimo', 'Mín.'], ['aprobacion', '% aprobado']], filas, { ordenables: [] }))}`;
}

// ------------------------------------------------------------------ personalización y docente
function personalizacion(app) {
  const { opciones, rol } = app.estado;
  const pendiente = app.estado.temaPendiente ?? app.estado.tema;
  const elegir = (n) => () => { app.estado.temaPendiente = n; app.render(); };
  const guardar = () => { app.estado.tema = pendiente; app.estado.temaPendiente = null; app.render(); };
  const [fuerte] = TEMAS[pendiente];
  return `${encabezado(`${rol === 'Administrador' ? 'Inicio' : rol}  /  Personalización`, 'Personalización', 'Ajusta la apariencia')}
    <div class="rejilla dos-uno">
      ${tarjeta('Apariencia por institución', `${Object.entries(TEMAS).map(([n, [color]], i) => `
        <div class="tema-fila"><span class="muestra" style="background:${color}"></span>
          <div><strong>${n}</strong><small>${i === 0 ? 'Predeterminado' : 'Tema alternativo'}</small></div>
          <button type="button" class="elegir${n === pendiente ? ' elegido' : ''}" ${app.accion(`tema-${i}`, elegir(n))}>${n === pendiente ? '● Seleccionado' : '○ Elegir'}</button></div>`).join('')}
        <div class="acciones">${boton('Guardar apariencia', app.accion('guardar-tema', guardar))}</div>`)}
      ${tarjeta('Opciones de configuración', `${[['colorear', 'Resaltar aprobados y reprobados'], ['avisos', 'Mostrar eventos de validación'], ['recordar', 'Recordar filtros seleccionados']]
        .map(([k, t]) => `<label class="check" style="display:flex"><input type="checkbox" ${opciones[k] ? 'checked' : ''} ${app.accion(`opcion-${k}`, (el) => { opciones[k] = el.checked; }, 'change')}> ${t}</label>`).join('')}
        <p class="nota" style="margin-top:28px">La personalización solo cambia la apariencia.</p>`)}
    </div>
    ${tarjeta('Vista previa', `<div class="vista-previa"><div class="marca-previa" style="background:${fuerte}">Sistema académico</div>
      <div class="cuerpo-previa"><nav><b style="color:${fuerte}">Registro</b><span>Captura</span><span>Reportes</span></nav>
      <div class="contenido-previa"><strong>Datos escolares</strong><p class="suave">El tema cambia la apariencia, no la estructura de la información.</p></div></div></div>`)}
    ${rol === 'Administrador' ? datosInstitucion(app) : ''}`;
}

// ------------------------------------------------------------------ docente
function misCursos(app) {
  const g = app.gestor;
  const cursos = app.cursosVisibles();
  const resumenes = app.resumenesVisibles();
  const pendientes = cursos.flatMap((c) => g.listaInscripciones().filter(([, cid]) => cid === c.id)
    .flatMap(([n]) => g.parcialesDe(c.id).flatMap((p) => g.evaluacionesDe(p.id)).filter((e) => g.calificacion(n, e.id) === null))).length;
  const est = new Map(estadisticasCursos(g, resumenes).map((f) => [f.curso_id, f]));
  return `${encabezado('Docente  /  Cursos', 'Mis cursos asignados', 'Consulta los grupos y asignaturas a tu cargo. Solo puedes capturar notas de tus cursos.')}
    ${mosaicos([['Cursos asignados', cursos.length], ['Grupos activos', new Set(cursos.map((c) => c.grupo)).size], ['Evaluaciones pendientes', pendientes], ['Estudiantes', resumenes.length]])}
    ${tarjeta('', tabla('mis-cursos', [['curso_id', 'Curso'], ['asignatura', 'Asignatura'], ['grupo', 'Grupo'], ['inscritos', 'Inscritos'], ['promedio', 'Promedio'], ['estado', 'Estado']],
      cursos.map((c) => ({ curso_id: c.id, asignatura: c.asignatura, grupo: c.grupo, inscritos: est.get(c.id)?.inscritos ?? 0, promedio: est.get(c.id)?.promedio ?? null, estado: 'Asignado' })), { ordenables: [] }))}
    ${botonesPie(app, [['Plan de evaluación', 'Plan de evaluación'], ['Capturar calificaciones', 'Captura'], ['Promedios y riesgo', 'Consultas:1']])}
    ${notaPie('La asignación de cursos se determina por el administrador.')}`;
}

// ------------------------------------------------------------------ personalización: datos del encabezado de reportes
function datosInstitucion(app) {
  const f = app.filtro('institucion', { ...app.institucion });
  const guardar = () => {
    if (!['institucion', 'programa', 'ciclo'].every((k) => String(f[k] ?? '').trim())) return app.avisar('Faltan datos', 'Llena institución, programa y ciclo escolar.');
    Object.assign(app.institucion, Object.fromEntries(['institucion', 'programa', 'ciclo'].map((k) => [k, f[k].trim()])));
    app.guardarAhora();
    app.registrarActividad('institucion_actualizada · encabezado de reportes');
    app.avisar('Datos guardados', 'Los reportes usarán estos datos en su encabezado.');
  };
  const attr = 'data-filtro="institucion"';
  return tarjeta('Datos del encabezado de los reportes', `<div class="tres">${campo('Institución', 'institucion', { valor: f.institucion, atributos: attr })}
    ${campo('Programa educativo', 'programa', { valor: f.programa, atributos: attr })}${campo('Ciclo escolar', 'ciclo', { valor: f.ciclo, atributos: attr })}</div>
    <div class="acciones">${boton('Guardar datos', app.accion('guardar-institucion', guardar))}</div>`, 'Aparecen en la boleta, el kárdex, las actas y los demás reportes.');
}

// ------------------------------------------------------------------ alumno
const miResumen = (app) => app.gestor.resumenEstudiante(app.estado.usuario);
const cuentaCursos = (cursos, e) => cursos.filter((c) => c.bruto !== null && c.estatus === e).length;

function inicioAlumno(app) {
  const g = app.gestor;
  const r = miResumen(app);
  const tono = { ALTO: 'rojo', MEDIO: 'ambar' }[r.riesgo] ?? 'verde';
  return `${mosaicos([['Promedio general', mostrarONulo(r.general)], ['Aprobadas', cuentaCursos(r.cursos, 'APROBADO')],
    ['Reprobadas', cuentaCursos(r.cursos, 'REPROBADO')], ['Pendientes', r.pendientes]])}
    <div class="aviso-riesgo tono-fondo-${tono}"><strong>Riesgo académico: ${esc(legible(r.riesgo ?? 'PENDIENTE'))}</strong><small>${esc(r.motivo)}</small></div>
    ${tarjeta('Mis materias', tabla('mis-materias', [['asignatura', 'Asignatura'], ['docente', 'Docente'], ['p1', 'P1'], ['p2', 'P2'], ['p3', 'P3'],
      ['promedio', 'Promedio'], ['final', 'Final'], ['estatus', 'Estado']], r.cursos.map((c) => ({ asignatura: c.asignatura,
      docente: g.docenteDe(c.curso_id)?.nombre ?? 'Por asignar', ...Object.fromEntries([...c.parciales, null, null, null].slice(0, 3).map((x, i) => [`p${i + 1}`, mostrarONulo(x)])),
      promedio: mostrarONulo(c.bruto), final: calificacionFinal(c.bruto), estatus: c.estatus })), { ordenables: [] }))}
    ${botonesPie(app, [['Ver desglose de calificaciones', 'Mis calificaciones'], ['Ver mi kárdex', 'Kárdex']])}
    ${notaPie('Una materia se aprueba con promedio de 7.0 o más; la final se asienta entera (.5 sube) y un promedio menor a 7.0 no se redondea hacia arriba. '
      + 'Riesgo ALTO: alguna materia reprobada o promedio menor a 7.0; MEDIO: promedio entre 7.0 y 7.5.')}`;
}

function misCalificaciones(app) {
  const g = app.gestor;
  const r = miResumen(app);
  const boleta = D.boleta(g, app.estado.usuario, app.institucion, emisor(app));
  const materias = r.cursos.map((c) => {
    const final = c.bruto === null ? 'Pendiente' : `${calificacionFinal(c.bruto)}  (promedio ${fmt(mostrar(c.bruto))}) · ${legible(c.estatus)}`;
    const filas = g.parcialesDe(c.curso_id).flatMap((p) => {
      const nota = mostrarONulo(g.califParcial(app.estado.usuario, p.id));
      const evals = g.evaluacionesDe(p.id);
      return evals.length ? evals.map((ev) => {
        const x = g.calificacion(app.estado.usuario, ev.id);
        return { parcial: `Parcial ${p.numero} · ${txtPct(p.peso)} %`, evaluacion: `${ev.nombre} · ${txtPct(ev.peso)} %`, calificacion: x === null ? 'Pendiente' : x.toFixed(1), nota_parcial: nota };
      }) : [{ parcial: `Parcial ${p.numero} · ${txtPct(p.peso)} %`, evaluacion: 'Sin plan de evaluación', calificacion: 'Pendiente', nota_parcial: null }];
    });
    return tarjeta(`${c.asignatura}   ·   Final: ${final}`, tabla(`boleta-${c.curso_id}`, [['parcial', 'Parcial'], ['evaluacion', 'Evaluación'], ['calificacion', 'Calificación'], ['nota_parcial', 'Nota del parcial']], filas, { ordenables: [] }));
  }).join('');
  return `${encabezado('Alumno  /  Mis calificaciones', 'Mis calificaciones', 'Desglose por materia, parcial y evaluación. Solo lectura.')}
    ${tarjeta('Boleta de calificaciones', botonesExportar(app, boleta), 'Descarga tu boleta del periodo con encabezado institucional.')}
    ${materias || tarjeta('', '<p class="nota">No tienes materias inscritas.</p>')}`;
}

function kardexAlumno(app) {
  const g = app.gestor;
  const doc = D.kardex(g, app.estado.usuario, app.institucion, emisor(app));
  return `${encabezado('Alumno  /  Kárdex', 'Mi kárdex académico', 'Tu historial individual: materias cursadas, calificación final y situación académica.')}
    ${botonesExportar(app, doc, [['Boleta PDF', () => exportarDoc(app, D.boleta(g, app.estado.usuario, app.institucion, emisor(app)), 'pdf')]])}
    ${vistaDocumento(doc)}`;
}

export const VISTAS = {
  'Administrador/Inicio': inicio, 'Docente/Inicio': inicio, 'Alumno/Inicio': inicio,
  'Administrador/Estudiantes': estudiantes, 'Administrador/Grupos y materias': materias, 'Administrador/Docentes': docentes,
  'Administrador/Inscripciones': inscripciones, 'Administrador/Usuarios': usuarios,
  'Administrador/Consultas': consultas, 'Docente/Consultas': consultas,
  'Administrador/Reportes': reportesVista, 'Docente/Reportes': reportesVista,
  'Docente/Mis cursos': misCursos, 'Docente/Plan de evaluación': planEvaluacion, 'Docente/Captura': captura,
  'Alumno/Mis calificaciones': misCalificaciones, 'Alumno/Kárdex': kardexAlumno,
  'Administrador/Personalización': personalizacion, 'Docente/Personalización': personalizacion, 'Alumno/Personalización': personalizacion,
  'Administrador/Mi cuenta': miCuenta, 'Docente/Mi cuenta': miCuenta, 'Alumno/Mi cuenta': miCuenta,
};
