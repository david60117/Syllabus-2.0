/**
 * Interfaz web de Syllabus 2.0. La pantalla es una función del estado: cada cambio actualiza
 * el estado y se vuelve a dibujar la vista (render). Las vistas solo describen qué mostrar.
 */
import { ErrorValidacion } from '../errores.js';
import { BusEventos } from '../eventos.js';
import { GestorEscolar } from '../gestor.js';
import { parsearJson } from '../estructura.js';
import { calcularSecuencial } from '../reportes.js';
import { compararTuplas } from '../calculos.js';
import { legible, MENUS, TEMAS, esc, icono } from './componentes.js';
import { VISTAS, acceso } from './vistas.js';
import { iniciarSesion, sincronizar } from '../autenticacion.js';
import { abrir, desdeSemilla, NO_GUARDAN, paraGuardar } from '../persistencia.js';
import { INSTITUCION } from '../documentos.js';

const raiz = document.getElementById('app');

const app = {
  estado: {
    rol: null, usuario: null, cuenta: null, vista: 'Inicio', tema: 'Rojo', archivo: null, cargando: false,
    opciones: { colorear: true, avisos: true, recordar: true },
    filtros: {}, orden: {}, pestanas: {}, parametros: {}, seleccion: {}, actividad: [], resultadosVarias: [],
  },
  gestor: null,
  usuarios: [],
  institucion: { ...INSTITUCION },
  persistir: true,
  manejadores: { accion: {}, form: {}, filtrar: {}, seleccionar: {}, entrada: {} },

  // ---------- datos y eventos ----------
  nuevoGestor() {
    const bus = new BusEventos();
    bus.suscribirTodos((m) => this.alEvento(m));
    this.gestor = new GestorEscolar(bus);
  },

  /** Guarda en datos/base.json (lo atiende el servidor) después de crear las cuentas que falten. */
  async guardarAhora() {
    this._guardado = null;
    if (sincronizar(this.usuarios, this.gestor)) this.registrarActividad('cuentas_creadas · usuario y contraseña inicial = número de cuenta o ID');
    if (!this.persistir) return;
    const r = await fetch('/api/base', { method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(paraGuardar(this.gestor, this.usuarios, this.institucion)) }).catch(() => null);
    if (!r?.ok) this.avisar('No se pudo guardar', 'Revisa que el servidor siga abierto (npm start).');
  },

  programarGuardado() {
    clearTimeout(this._guardado);
    this._guardado = setTimeout(() => this.guardarAhora(), 250);
  },

  /** Carga masiva: agrega los registros válidos de un JSON y devuelve los rechazados. */
  importar(datos, nombre) {
    const antes = this.gestor.errores.length;
    this.estado.cargando = true;
    try { this.gestor.cargar(datos); } finally { this.estado.cargando = false; }
    this.gestor._cambio();
    this.guardarAhora();
    this.registrarActividad(`carga_masiva · ${nombre} · ${this.gestor.errores.length - antes} rechazados`);
    return this.gestor.errores.slice(antes);
  },

  async restablecerEjemplo() {
    const datos = parsearJson(await fetch('/datos/solicitudes_pequenas.json').then((r) => r.text()), 'solicitudes_pequenas.json');
    const muestra = await fetch('/datos/usuarios.json').then((r) => r.json()).then((d) => d.usuarios);
    this.nuevoGestor();
    this.estado.cargando = true;
    ({ usuarios: this.usuarios, institucion: this.institucion } = desdeSemilla(this.gestor, datos, muestra));
    this.estado.cargando = false;
    await this.guardarAhora();
  },

  async leerArchivo(archivo) {
    try {
      return parsearJson(await archivo.text(), archivo.name);
    } catch (e) {
      this.avisar('No se pudo cargar', e.message);
      return null;
    }
  },

  alEvento({ evento, hora, detalle = '', codigo }) {
    if (this.estado.cargando) return;
    this.registrarActividad(`${hora}  ${evento} · ${detalle}`);
    if (evento === 'error_validacion' && this.estado.opciones.avisos) this.avisar(codigo, detalle);
    if (!NO_GUARDAN.has(evento)) this.programarGuardado();
    this.programarRender(); // varios eventos seguidos (una operación por lote) producen un solo dibujo
  },

  registrarActividad(texto) {
    const linea = /^\d{2}:\d{2}:\d{2}/.test(texto) ? texto : `${new Date().toTimeString().slice(0, 8)}  ${texto}`;
    this.estado.actividad = [linea, ...this.estado.actividad].slice(0, 30);
  },

  /** Ejecuta una acción del gestor; los errores llegan por el evento error_validacion. */
  intentar(accion) {
    return this.hacer(accion)[0];
  },

  /** Como intentar, pero devuelve también el resultado: [ok, resultado]. */
  hacer(accion) {
    try {
      return [true, accion()];
    } catch (e) {
      if (!(e instanceof ErrorValidacion)) throw e;
      this.gestor._error('interfaz', {}, e);
      return [false, null];
    }
  },

  confirmar(texto) { return window.confirm(texto); },

  avisar(titulo, texto) {
    const dialogo = document.getElementById('aviso');
    document.getElementById('aviso-titulo').textContent = legible(titulo);
    document.getElementById('aviso-texto').textContent = legible(texto);
    if (!dialogo.open) dialogo.showModal();
  },

  /**
   * Guarda un archivo con el explorador de archivos del sistema (File System Access API en Chrome y Edge);
   * en navegadores sin esa API se usa la descarga normal.
   */
  async guardarArchivo(nombre, contenido, tipo) {
    if (!window.showSaveFilePicker) return this.descargar(nombre, contenido, tipo), true;
    const extension = nombre.slice(nombre.lastIndexOf('.'));
    try {
      const manejador = await window.showSaveFilePicker({ suggestedName: nombre, types: [{ description: extension.slice(1).toUpperCase(), accept: { [tipo]: [extension] } }] });
      const escritor = await manejador.createWritable();
      await escritor.write(contenido);
      await escritor.close();
      return true;
    } catch (e) {
      if (e.name === 'AbortError') return false;
      throw e;
    }
  },

  /** Guarda varios archivos en la carpeta que el usuario elija en el explorador del sistema. */
  async guardarEnCarpeta(archivos) {
    if (!window.showDirectoryPicker) { archivos.forEach(([n, c, t]) => this.descargar(n, c, t)); return true; }
    try {
      const carpeta = await window.showDirectoryPicker({ mode: 'readwrite' });
      for (const [nombre, contenido] of archivos) {
        const escritor = await (await carpeta.getFileHandle(nombre, { create: true })).createWritable();
        await escritor.write(contenido);
        await escritor.close();
      }
      return true;
    } catch (e) {
      if (e.name === 'AbortError') return false;
      throw e;
    }
  },

  descargar(nombre, contenido, tipo) {
    const url = URL.createObjectURL(new Blob([contenido], { type: typeof contenido === 'string' ? `${tipo};charset=utf-8` : tipo }));
    Object.assign(document.createElement('a'), { href: url, download: nombre }).click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },

  // ---------- alcance por rol ----------
  cursosVisibles() {
    const { rol, usuario } = this.estado;
    if (rol === 'Docente') return this.gestor.cursosDeDocente(usuario);
    if (rol === 'Alumno') return this.gestor.cursosDe(usuario);
    return [...this.gestor.cursos.values()].sort((a, b) => compararTuplas([a.grupo, a.asignatura], [b.grupo, b.asignatura]));
  },

  resumenesVisibles() {
    const resumenes = calcularSecuencial(this.gestor);
    if (this.estado.rol === 'Administrador') return resumenes;
    const ids = new Set(this.cursosVisibles().map((c) => c.id));
    return resumenes
      .map((r) => ({ ...r, cursos: r.cursos.filter((c) => ids.has(c.curso_id)) }))
      .filter((r) => r.cursos.length);
  },

  nombreUsuario() {
    const { rol, usuario } = this.estado;
    if (rol === 'Docente') return this.gestor.docentes.get(usuario)?.nombre ?? '';
    if (rol === 'Alumno') {
      const e = this.gestor.estudiantes.get(usuario);
      return e ? `${e.apellidos} ${e.nombre}` : '';
    }
    return '';
  },

  // ---------- registro de manejadores usados por las vistas ----------
  accion(nombre, funcion, evento = 'click') {
    this.manejadores.accion[nombre] = funcion;
    return `data-accion="${nombre}" data-evento="${evento}"`;
  },
  formulario(nombre, funcion) {
    this.manejadores.form[nombre] = funcion;
    return `data-form="${nombre}"`;
  },
  alEscribir(nombre, funcion) { this.manejadores.entrada[nombre] = funcion; },
  filtro(grupo, valores) {
    this.estado.filtros[grupo] ??= { ...valores };
    return this.estado.filtros[grupo];
  },
  alFiltrar(grupo, funcion) { this.manejadores.filtrar[grupo] = funcion; },
  alSeleccionar(tablaId, funcion) { this.manejadores.seleccionar[tablaId] = funcion; },
  orden(tablaId, defecto = { col: 'apellidos', desc: false }) {
    this.estado.orden[tablaId] ??= defecto;
    return this.estado.orden[tablaId];
  },
  pestana(id) { return this.estado.pestanas[id] ?? 0; },
  reemplazar(id, html) {
    const el = document.getElementById(id);
    if (el) el.outerHTML = html;
  },

  // ---------- navegación ----------
  entrar(cuenta) {
    Object.assign(this.estado, { cuenta, rol: cuenta.rol, usuario: cuenta.referencia, vista: cuenta.debe_cambiar ? 'Mi cuenta' : 'Inicio',
      seleccion: {}, pestanas: {}, filtros: {}, parametros: {} });
    this.render();
  },
  salir() {
    Object.assign(this.estado, { rol: null, usuario: null, cuenta: null, mensajeAcceso: '' });
    this.render();
  },

  async iniciarSesion(usuario, contrasena) {
    try {
      const cuenta = await iniciarSesion(this.usuarios, usuario, contrasena, this.gestor);
      this.estado.mensajeAcceso = '';
      this.entrar(cuenta);
    } catch (e) {
      if (!(e instanceof ErrorValidacion)) throw e;
      document.getElementById('mensaje-acceso').textContent = e.detalle[0].toUpperCase() + e.detalle.slice(1) + '.';
    }
  },
  ir(vista, parametros = {}) {
    if (!this.estado.opciones.recordar) this.estado.filtros = {};
    const destino = this.estado.cuenta?.debe_cambiar ? 'Mi cuenta' : vista; // primero debe cambiar su contraseña inicial
    Object.assign(this.estado, { vista: destino, parametros, kardexDe: null });
    if (parametros.buscar !== undefined) this.estado.pestanas.consultas = 0;
    this.render();
  },

  programarRender() {
    if (this._pendiente) return;
    this._pendiente = true;
    queueMicrotask(() => { this._pendiente = false; this.render(); });
  },

  render() {
    const [fuerte, suave] = TEMAS[this.estado.tema];
    document.documentElement.style.setProperty('--acento', fuerte);
    document.documentElement.style.setProperty('--acento-suave', suave);
    document.body.classList.toggle('sin-color', !this.estado.opciones.colorear);
    this.manejadores = { accion: {}, form: {}, filtrar: {}, seleccionar: {}, entrada: {} };
    const desplazamiento = raiz.querySelector('.contenido')?.scrollTop ?? 0;
    raiz.innerHTML = this.estado.rol ? this.marco() : acceso(this);
    const contenido = raiz.querySelector('.contenido');
    if (contenido && this._ultimaVista === this.estado.vista) contenido.scrollTop = desplazamiento;
    this._ultimaVista = this.estado.vista;
  },

  marco() {
    const { rol, vista, cuenta } = this.estado;
    const nombre = this.nombreUsuario() || cuenta?.nombre || rol;
    return `<div class="marco">
      <header class="cabecera">
        <div class="logo">S</div>
        <div class="marca"><strong>Syllabus 2.0</strong><small>Gestión académica</small></div>
        <div class="espacio"></div>
        <span class="usuario">${esc(rol)}</span>
        <details class="menu-avatar"><summary class="avatar" title="${esc(nombre)}">${esc(nombre[0].toUpperCase())}</summary>
          <div class="desplegable-avatar"><div class="suave">${esc(nombre)}</div>
          <button type="button" data-ir="Mi cuenta">Mi cuenta y contraseña</button>
          <button type="button" data-accion="salir" data-evento="click">Cerrar sesión</button></div></details>
      </header>
      <nav class="lateral" aria-label="Menú principal">
        <div class="titulo">MENÚ PRINCIPAL</div>
        ${MENUS[rol].map(([ic, n]) => `<button type="button" class="menu-item${n === vista ? ' activo' : ''}" data-ir="${esc(n)}"${n === vista ? ' aria-current="page"' : ''}>
          <span class="icono" aria-hidden="true">${icono(ic)}</span><span class="texto">${esc(n)}</span></button>`).join('')}
        <div class="pie"><div class="suave">Perfil de institución</div><strong>Syllabus 2.0</strong></div>
      </nav>
      <main class="contenido">${VISTAS[`${rol}/${vista}`](this)}</main>
    </div>`;
  },
};

// ---------- delegación de eventos: un solo oyente por tipo para toda la página ----------
raiz.addEventListener('click', (e) => {
  const el = e.target.closest('[data-ir], [data-accion][data-evento="click"], th[data-orden], tr[data-fila], [data-pestana]');
  if (!el) return;
  if (el.dataset.ir) return app.ir(el.dataset.ir);
  if (el.dataset.accion === 'salir') return app.salir();
  if (el.dataset.accion) return app.manejadores.accion[el.dataset.accion]?.(el);
  if (el.dataset.orden) {
    const actual = app.orden(el.dataset.orden);
    app.estado.orden[el.dataset.orden] = { col: el.dataset.col, desc: actual.col === el.dataset.col ? !actual.desc : false };
    return app.render();
  }
  if (el.dataset.pestana) {
    app.estado.pestanas[el.dataset.pestana] = Number(el.dataset.i);
    return app.render();
  }
  if (el.dataset.fila) app.manejadores.seleccionar[el.dataset.fila]?.(el.dataset.cuenta);
});

raiz.addEventListener('change', (e) => {
  const el = e.target;
  if (el.dataset.accion && el.dataset.evento === 'change') return app.manejadores.accion[el.dataset.accion]?.(el);
  if (el.dataset.filtro !== undefined && el.hasAttribute('data-refrescar')) {
    app.filtro(el.dataset.filtro, {})[el.name] = el.value;
    app.render();
  }
});

raiz.addEventListener('input', (e) => {
  const el = e.target;
  if (el.dataset.entrada) return app.manejadores.entrada[el.dataset.entrada]?.(el);
  if (el.dataset.filtro === undefined || el.hasAttribute('data-refrescar')) return;
  app.filtro(el.dataset.filtro, {})[el.name] = el.value;
  app.manejadores.filtrar[el.dataset.filtro]?.();
});

// Enter, ↑ y ↓ recorren las celdas del acta de calificaciones como en una hoja de cálculo
raiz.addEventListener('keydown', (e) => {
  const el = e.target;
  if (el.dataset.renglon === undefined || !['Enter', 'ArrowDown', 'ArrowUp'].includes(e.key)) return;
  e.preventDefault();
  const destino = raiz.querySelector(`[data-renglon="${Number(el.dataset.renglon) + (e.key === 'ArrowUp' ? -1 : 1)}"][data-columna="${el.dataset.columna}"]`);
  destino?.focus();
  destino?.select();
});

raiz.addEventListener('submit', (e) => {
  e.preventDefault();
  const datos = Object.fromEntries(new FormData(e.target));
  app.manejadores.form[e.target.dataset.form]?.(datos, e.submitter?.value);
});

// ---------- arranque ----------
// Sin --entrada se trabaja sobre datos/base.json y cada cambio se guarda solo; con --entrada se abre ese archivo
// como demostración, sin guardar. La primera vez se parte de los datos de ejemplo.
const leerTexto = (ruta) => fetch(ruta).then((r) => (r.ok ? r.text() : '')).catch(() => '');
const muestra = await fetch('/datos/usuarios.json').then((r) => r.json()).then((d) => d.usuarios).catch(() => []);
const demostracion = (await leerTexto('/entrada.json')).trim();
app.nuevoGestor();
app.estado.cargando = true;
try {
  if (demostracion) {
    app.persistir = false;
    ({ usuarios: app.usuarios, institucion: app.institucion } = desdeSemilla(app.gestor, parsearJson(demostracion, 'entrada'), muestra));
  } else {
    const base = await fetch('/api/base').then((r) => (r.ok ? r.json() : null)).catch(() => null);
    if (base) ({ usuarios: app.usuarios, institucion: app.institucion } = abrir(app.gestor, base));
    else {
      const semilla = parsearJson(await leerTexto('/datos/solicitudes_pequenas.json'), 'solicitudes_pequenas.json');
      ({ usuarios: app.usuarios, institucion: app.institucion } = desdeSemilla(app.gestor, semilla, muestra));
      app.estado.cargando = false;
      await app.guardarAhora();
    }
  }
} catch (e) {
  app.avisar('No se pudieron abrir los datos', e.message);
}
app.estado.cargando = false;
app.render();
window.syllabus = app; // acceso desde la consola del navegador para pruebas
