"""Pantallas de la interfaz. Cada función recibe la App y dibuja su vista en app.contenido."""
import re
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

from . import iconos, reportes
from .archivos import cargar_json
from .calculos import calificacion_final, estadisticas, filtro_estado, filtro_rango, mostrar
from .concurrencia import calcular_resumenes
from .errores import ErrorValidacion
from . import interfaz as ui
from .interfaz import C, TEMAS, Pestanas, Tabla, fmt, fondo_de, legible

def resumenes_visibles(app):
    cursos = {c.id for c in app.cursos_visibles()}
    resumenes = calcular_resumenes(app.gestor)
    if app.rol == "Administrador":
        return resumenes
    filtrados = []
    for r in resumenes:
        propios = [c for c in r["cursos"] if c["curso_id"] in cursos]
        if propios:
            filtrados.append({**r, "cursos": propios})
    return filtrados


def lista_actividad(app, padre, n, vacio="Sin eventos todavía."):
    """Actividad como en Figma: punto de color, nombre del evento, detalle y hora."""
    if not app.actividad:
        app.etiqueta(padre, vacio, 10, color=C["suave"]).pack(anchor="w")
        return
    for linea in app.actividad[:n]:
        m = re.match(r"(\d{2}:\d{2}:\d{2})\s+(.*)", linea)
        hora, resto = (m.group(1), m.group(2)) if m else ("", linea)
        evento, _, detalle = resto.partition(" · ")
        color = "#a32e34" if evento == "error_validacion" else "#247457"
        fila = tk.Frame(padre, bg=fondo_de(padre))
        fila.pack(fill="x", pady=(0, 10))
        punto = tk.Canvas(fila, width=8, height=8, bg=fondo_de(padre), highlightthickness=0)
        punto.create_oval(1, 1, 7, 7, fill=color, outline=color)
        punto.pack(side="left", anchor="n", pady=(5, 0), padx=(0, 8))
        textos = tk.Frame(fila, bg=fondo_de(padre))
        textos.pack(side="left", fill="x", expand=True)
        app.etiqueta(fila, hora, 9, color=C["suave"]).pack(side="right", anchor="n")
        app.etiqueta(textos, legible(evento), 10, "bold", color=color).pack(anchor="w")
        if detalle:
            app.etiqueta(textos, legible(detalle), 9, color=C["suave"], wraplength=300).pack(anchor="w")


def insignia(app, padre, texto, tam=12):
    """Número o letra sobre fondo suave con esquinas redondeadas (radio 8)."""
    caja = app.caja(padre, radio=8, fondo=app.acento_suave, borde=app.acento_suave, padx=11, pady=7)
    tk.Label(caja, text=texto, font=(ui.FUENTE, tam, "bold"), fg=app.acento, bg=app.acento_suave).pack()
    return caja


def columnas_grid(padre, n):
    for i in range(n):
        padre.columnconfigure(i, weight=1, uniform="col")


# ---------------------------------------------------------------- acceso
def acceso(app):
    """Pantalla de inicio de sesión (Figma: Acceso · Syllabus 2.0)."""
    cab = tk.Frame(app, bg=C["blanco"], height=122)
    cab.pack(fill="x")
    cab.pack_propagate(False)
    tk.Label(cab, text="S", font=(ui.FUENTE, 20, "bold"), fg=app.acento, bg=C["blanco"]).pack(side="left", padx=(72, 30))
    app.etiqueta(cab, "Syllabus 2.0", 22, "bold").pack(side="left")
    app.etiqueta(cab, "PLATAFORMA ACADÉMICA", 10, "bold", color=app.acento).pack(side="right", padx=(0, 240))
    tk.Frame(app, bg=C["borde"], height=1).pack(fill="x")
    cuerpo = tk.Frame(app, bg=C["fondo"])
    cuerpo.pack(fill="both", expand=True)
    tarjeta = app.caja(cuerpo, radio=14, padx=52, pady=48)
    tarjeta.pack(pady=(54, 16))
    marca = app.caja(tarjeta, radio=10, fondo=app.acento_suave, borde=app.acento_suave, padx=17, pady=10)
    tk.Label(marca, text="S", font=(ui.FUENTE, 18, "bold"), fg=app.acento, bg=app.acento_suave).pack()
    marca.pack(anchor="w")
    app.etiqueta(tarjeta, "Iniciar sesión", 26, "bold").pack(anchor="w", pady=(26, 2))
    app.etiqueta(tarjeta, "Entra a Syllabus 2.0 para continuar.", 12, color=C["suave"]).pack(anchor="w", pady=(0, 40))
    usuario, contrasena = tk.StringVar(), tk.StringVar()
    entradas = []
    for texto, var, oculto, ayuda in (("Correo o usuario", usuario, False, "Escribe tu correo o usuario"),
                                      ("Contraseña", contrasena, True, "••••••••")):
        app.etiqueta(tarjeta, texto, 11, "bold").pack(anchor="w", pady=(0, 10))
        borde = app.caja(tarjeta, radio=7, padx=16, pady=14)
        e = tk.Entry(borde, textvariable=var, width=56, font=(ui.FUENTE, 12), bg=C["blanco"], relief="flat", bd=0,
                     highlightthickness=0, show="•" if oculto else "")
        if oculto:
            _boton_ver(app, borde, e).pack(side="right", padx=(10, 0))
        e.pack(side="left", fill="x", expand=True)
        _texto_guia(e, var, ayuda)
        borde.pack(fill="x", pady=(0, 26))
        entradas.append(e)
    mensaje = app.etiqueta(tarjeta, "", 10, color=C["rojo"])
    mensaje.pack(anchor="w")

    def ingresar(_evento=None):
        from .autenticacion import iniciar_sesion
        try:
            cuenta = iniciar_sesion(app.usuarios, usuario.get(), contrasena.get(), app.gestor)
        except ErrorValidacion as e:
            mensaje.config(text=e.detalle[:1].upper() + e.detalle[1:] + ".")
            return
        app.entrar(cuenta)
    for e in entradas:
        e.bind("<Return>", ingresar)
    boton = app.caja(tarjeta, radio=7, fondo=app.acento, borde=app.acento, padx=18, pady=15, cursor="hand2")
    texto_boton = tk.Label(boton, text="Ingresa", font=(ui.FUENTE, 12, "bold"), fg="white", bg=app.acento, cursor="hand2")
    texto_boton.pack()
    boton.pack(fill="x", pady=(10, 0))
    for w in (boton, boton.caja, texto_boton):
        w.bind("<Button-1>", ingresar)
    for e in entradas:
        e.bind("<Button-1>", lambda ev: ev.widget.focus_force(), add="+")


def _texto_guia(entrada, var, texto):
    """Texto de ayuda gris dentro del campo, visible mientras esté vacío (como el placeholder del diseño)."""
    guia = tk.Label(entrada.master, text=texto, font=(ui.FUENTE, 11), fg="#9a928e", bg=entrada.cget("bg"), cursor="xterm")
    guia.bind("<Button-1>", lambda _e: entrada.focus_force())

    def actualizar(*_):
        if var.get():
            guia.place_forget()
        else:
            guia.place(in_=entrada, x=0, rely=0.5, anchor="w")
    var.trace_add("write", actualizar)
    entrada.after_idle(actualizar)


def _boton_ver(app, padre, entrada):
    """Ícono de ojo para mostrar u ocultar la contraseña (eye / eye-off de Lucide)."""
    lienzo = tk.Canvas(padre, width=22, height=22, bg=C["blanco"], highlightthickness=0, bd=0, cursor="hand2",
                       takefocus=1)

    def pintar():
        lienzo.delete("all")
        oculta = entrada.cget("show") != ""
        iconos.dibujar(lienzo, "ojo" if oculta else "ojo_cerrado", C["suave"] if oculta else app.acento, 18, 1.8, 2, 2)

    def alternar(_e=None):
        entrada.config(show="" if entrada.cget("show") else "•")
        pintar()
        entrada.focus_set()
        entrada.icursor("end")
    lienzo.bind("<Button-1>", alternar)
    lienzo.bind("<Return>", alternar)
    lienzo.bind("<space>", alternar)
    pintar()
    return lienzo


PANELES = {
    "Administrador": {
        "titulo": "Control escolar",
        "subtitulo": "Estudiantes, grupos, materias, docentes, inscripciones, consultas y reportes.",
        "etiqueta": "ADMINISTRACIÓN ESCOLAR", "banda": "Registra la información una sola vez: lo demás se genera solo.",
        "tarjetas": [("Estudiantes", "Alta con número de cuenta automático e inscripción al grupo.", "Abrir Estudiantes", "Estudiantes"),
                     ("Grupos y materias", "Grupos (semestre y turno) y materias con su docente.", "Abrir Grupos y materias", "Grupos y materias"),
                     ("Docentes", "Alta de docentes y asignación de materias.", "Abrir Docentes", "Docentes"),
                     ("Inscripciones", "Altas y bajas individuales de materias.", "Abrir Inscripciones", "Inscripciones"),
                     ("Consultas y kárdex", "Promedios, orden por promedio y riesgo académico.", "Abrir Consultas", "Consultas"),
                     ("Reportes y exportación", "Boleta, kárdex, actas y estadísticas en PDF, CSV o JSON.", "Abrir Reportes", "Reportes")]},
    "Docente": {
        "titulo": "Panel docente",
        "subtitulo": "Tus materias, su plan de evaluación y la captura de calificaciones.",
        "etiqueta": "DOCENCIA", "banda": "Define el plan de cada parcial y captura el acta del grupo completo.",
        "tarjetas": [("Mis cursos", "Materias y grupos que tienes asignados.", "Ver cursos", "Mis cursos"),
                     ("Plan de evaluación", "Evaluaciones y porcentajes de cada parcial.", "Abrir plan", "Plan de evaluación"),
                     ("Acta de calificaciones", "Captura todo el grupo y guarda de una vez.", "Abrir captura", "Captura"),
                     ("Consultas", "Promedios y kárdex de tus alumnos.", "Abrir Consultas", "Consultas"),
                     ("Riesgo académico", "Alumnos en riesgo de tus materias.", "Ver riesgo", "Consultas:1"),
                     ("Reportes", "Aprobados, reprobados y estadísticas.", "Abrir Reportes", "Reportes")]},
    "Alumno": {
        "titulo": "Mi espacio académico",
        "subtitulo": "Tus materias, calificaciones, promedio y estado académico.",
        "etiqueta": "ALUMNO", "banda": "Consulta tu avance; las calificaciones las registran tus docentes.",
        "tarjetas": []},
}


def ir_a(app, destino):
    """Destino "Vista:pestaña" (p. ej. "Consultas:1" abre Promedios y riesgo)."""
    vista, _, pestana = destino.partition(":")
    if pestana:
        app.filtros_guardados["pestana:" + vista] = int(pestana)
    app.ir(vista)


def ruta_de(app, modulo):
    return f"{'Docente' if app.rol == 'Docente' else 'Inicio'}  /  {modulo}"


def fila_columnas(app, padre, n, pady=(0, 0)):
    fila = tk.Frame(padre, bg=fondo_de(padre))
    fila.pack(fill="x", pady=pady)
    columnas_grid(fila, n)
    return fila


def en_columna(widget, i, n, sep=14):
    widget.grid(row=0, column=i, sticky="nsew", padx=(0 if i == 0 else sep // 2, 0 if i == n - 1 else sep // 2))
    return widget


def botones_pie(app, botones, padre=None):
    """Fila de botones del mismo ancho (el primero es el principal), como al pie de las vistas de Figma."""
    padre = padre or app.contenido
    fila = fila_columnas(app, padre, len(botones), pady=(14, 14) if padre is app.contenido else (0, 0))
    for i, (texto, destino) in enumerate(botones):
        b = app.boton(fila, texto, lambda d=destino: ir_a(app, d), primario=i == 0)
        en_columna(b, i, len(botones), 12)


def nota_pie(app, texto):
    t = app.tarjeta(app.contenido, fill="x")
    app.etiqueta(t, texto, 10, color=C["suave"], wraplength=1000).pack(anchor="w")


def nota(app, padre, texto):
    app.etiqueta(padre, texto, 10, color=C["suave"], wraplength=360).pack(anchor="w", pady=(6, 8))


def arbol(app, padre, nodos):
    """Árbol de texto con sangría: (nivel, texto, valor, color)."""
    for nivel, texto, valor, color in nodos:
        f = tk.Frame(padre, bg=fondo_de(padre))
        f.pack(fill="x", pady=2, padx=(nivel * 22, 0))
        app.etiqueta(f, texto, 11, "bold" if nivel == 0 else "normal", color=color).pack(side="left")
        if valor:
            app.etiqueta(f, valor, 10, "bold" if nivel == 0 else "normal", color=C["suave"]).pack(side="right")


def nodos_evaluaciones(app, g, cursos):
    def marca(s):
        return f"suma {s:.2f} {'✓' if abs(s - 1) < 1e-9 else '· incompleto'}"
    nodos = []
    for c in cursos:
        ps = g.parciales_de(c.id)
        nodos.append((0, f"Curso {c.id}  /  {c.asignatura}", marca(sum(p.peso for p in ps)), C["texto"]))
        for p in ps:
            es = g.evaluaciones_de(p.id)
            nodos.append((1, f"Parcial {p.id}  ·  %  {p.peso:.2f}", "evaluaciones " + marca(sum(e.peso for e in es)), app.acento))
            nodos += [(2, f"Evaluación {e.id}  ·  {e.nombre}  ·  %  {e.peso:.2f}", "", C["suave"]) for e in es]
    return nodos

# ---------------------------------------------------------------- utilidades de formularios
def _num(texto):
    try:
        return float(str(texto).strip())
    except (TypeError, ValueError):
        return texto


def _pct(texto):
    """Porcentaje escrito por el usuario (30) → peso decimal (0.3); el gestor valida el resto."""
    x = _num(texto)
    return round(x / 100, 6) if isinstance(x, float) else x


def _txt_pct(peso) -> str:
    return f"{peso * 100:g}"


def hacer(app, accion, *args):
    """Ejecuta una operación del gestor. Si falla, el error llega como evento (aviso en pantalla)."""
    try:
        return True, accion(*args)
    except ErrorValidacion as e:
        app.gestor._error("interfaz", {}, e)
        return False, None


def formulario(app, clave, valores):
    """Estado de un formulario que sobrevive a los refrescos de la vista (cada evento redibuja la pantalla)."""
    estado = app.filtros_guardados.setdefault(clave, dict(valores))
    variables = {}
    for k in valores:
        var = tk.StringVar(value=estado.get(k, ""))
        var.trace_add("write", lambda *_a, k=k, var=var: estado.__setitem__(k, var.get()))
        variables[k] = var
    return estado, variables


def _descendientes(widget):
    for w in widget.winfo_children():
        yield w
        yield from _descendientes(w)


def _ocultar(widget):
    for w in widget.winfo_children():
        if isinstance(w, tk.Entry):
            w.config(show="•")
        _ocultar(w)


def _docente_txt(d):
    return f"{d.id} · {d.nombre}" if d else "Sin asignar"


def _plan_completo(g, curso_id) -> int:
    """Cuántos de los 3 parciales tienen evaluaciones que suman 100 %."""
    return sum(1 for p in g.parciales_de(curso_id)
               if g.evaluaciones_de(p.id) and abs(sum(e.peso for e in g.evaluaciones_de(p.id)) - 1) < 1e-9)


def _avance_captura(g, curso_id) -> tuple[int, int]:
    alumnos = [e.num_cuenta for e in g.alumnos_de_curso(curso_id)]
    evals = [e.id for p in g.parciales_de(curso_id) for e in g.evaluaciones_de(p.id)]
    total = len(alumnos) * len(evals)
    hechas = sum(1 for n in alumnos for e in evals if (n, e) in g.calificaciones)
    return hechas, total


# ---------------------------------------------------------------- inicio por rol
def _pendientes_admin(app):
    g = app.gestor
    sin_docente = [c for c in g.cursos.values() if not g.docente_de(c.id)]
    sin_plan = [c for c in g.cursos.values() if _plan_completo(g, c.id) < 3]
    sin_materias = [e for e in g.estudiantes.values() if not g.cursos_de(e.num_cuenta)]
    iniciales = [u for u in app.usuarios if u.get("debe_cambiar")]
    return [(f"{len(sin_docente)} materias sin docente asignado", "Grupos y materias", bool(sin_docente)),
            (f"{len(sin_plan)} materias con plan de evaluación incompleto (lo define cada docente)", "Grupos y materias", bool(sin_plan)),
            (f"{len(sin_materias)} estudiantes sin materias inscritas", "Inscripciones", bool(sin_materias)),
            (f"{len(iniciales)} cuentas con contraseña inicial", "Usuarios", bool(iniciales))]


def _pendientes_docente(app):
    g = app.gestor
    filas = []
    for c in app.cursos_visibles():
        hechas, total = _avance_captura(g, c.id)
        plan = _plan_completo(g, c.id)
        if plan < 3:
            filas.append((f"{c.asignatura} ({c.grupo}): plan de evaluación {plan}/3 parciales", "Plan de evaluación", True))
        elif hechas < total:
            filas.append((f"{c.asignatura} ({c.grupo}): {total - hechas} calificaciones por capturar", "Captura", True))
    return filas or [("Sin pendientes: tus materias tienen plan y calificaciones completas.", "Mis cursos", False)]


def inicio(app):
    p = PANELES[app.rol]
    g = app.gestor
    app.encabezado(f"Inicio  /  {app.rol}", p["titulo"], p["subtitulo"])
    banda = app.caja(app.contenido, radio=11, fondo=app.acento_suave, borde=app.acento_suave, padx=24, pady=18)
    banda.pack(fill="x", pady=(0, 18))
    app.etiqueta(banda, p["etiqueta"], 9, "bold", color=app.acento).pack(anchor="w")
    app.etiqueta(banda, f"Hola, {app.nombre_usuario() or app.cuenta.get('nombre', app.rol)}", 16, "bold").pack(anchor="w", pady=(4, 2))
    app.etiqueta(banda, p["banda"], 11, color=C["suave"]).pack(anchor="w")
    if app.rol == "Alumno":
        _inicio_alumno(app)
        return
    resumenes = resumenes_visibles(app)
    riesgo = sum(1 for r in resumenes if r["riesgo"] in ("ALTO", "MEDIO"))
    if app.rol == "Administrador":
        app.mosaicos(app.contenido, [("Estudiantes", len(g.estudiantes)), ("Grupos", len(g.grupos())),
                                     ("Materias", len(g.cursos)), ("Docentes", len(g.docentes)), ("En riesgo", riesgo)])
        pendientes = _pendientes_admin(app)
    else:
        cursos = app.cursos_visibles()
        avance = [_avance_captura(g, c.id) for c in cursos]
        app.mosaicos(app.contenido, [("Mis materias", len(cursos)), ("Alumnos", len(resumenes)),
                                     ("Por capturar", sum(t - h for h, t in avance)), ("En riesgo", riesgo)])
        pendientes = _pendientes_docente(app)
    t = app.tarjeta(app.contenido, "Pendientes", fill="x", pady=(0, 18))
    for texto, destino, alerta in pendientes:
        f = tk.Frame(t, bg=C["blanco"])
        f.pack(fill="x", pady=3)
        punto = tk.Canvas(f, width=8, height=8, bg=C["blanco"], highlightthickness=0)
        color = "#a26b34" if alerta else "#247457"
        punto.create_oval(1, 1, 7, 7, fill=color, outline=color)
        punto.pack(side="left", padx=(0, 10))
        app.etiqueta(f, texto, 11).pack(side="left")
        if alerta:
            enlace = app.etiqueta(f, f"Ir a {destino}  →", 10, "bold", color=app.acento, cursor="hand2")
            enlace.pack(side="right")
            enlace.bind("<Button-1>", lambda e, d=destino: ir_a(app, d))
    _tarjetas_acceso(app, p["tarjetas"])


def _tarjetas_acceso(app, tarjetas):
    app.etiqueta(app.contenido, "ACCESOS PRINCIPALES", 10, "bold", color=C["suave"]).pack(anchor="w", pady=(0, 8))
    rejilla = tk.Frame(app.contenido, bg=C["fondo"])
    rejilla.pack(fill="x")
    columnas_grid(rejilla, 3)
    for i, (titulo, texto, enlace_txt, destino) in enumerate(tarjetas):
        t = app.caja(rejilla, radio=10, padx=19, pady=19)
        t.grid(row=i // 3, column=i % 3, sticky="nsew", padx=(0 if i % 3 == 0 else 16, 0), pady=(0, 16))
        caja = app.caja(t, radio=8, fondo=app.acento_suave, borde=app.acento_suave, padx=8, pady=8)
        caja.pack(anchor="w")
        iconos.icono(caja, destino.partition(":")[0], app.acento, app.acento_suave, 18).pack()
        app.etiqueta(t, titulo, 13, "bold").pack(anchor="w", pady=(10, 4))
        app.etiqueta(t, texto, 10, color=C["suave"], wraplength=300).pack(anchor="w")
        enlace = app.etiqueta(t, f"{enlace_txt}   →", 10, "bold", color=app.acento, cursor="hand2")
        enlace.pack(anchor="w", pady=(8, 0))
        enlace.bind("<Button-1>", lambda e, d=destino: ir_a(app, d))


# ---------------------------------------------------------------- control escolar: estudiantes
def estudiantes(app):
    app.encabezado("Control escolar  /  Estudiantes", "Estudiantes",
                   "El número de cuenta y la cuenta de usuario se generan solos; el alumno queda inscrito en las materias de su grupo.")
    pestanas = Pestanas(app.contenido, app, "Estudiantes", al_cambiar=app.refrescar)
    pestanas.pack(fill="x")
    marcos = [tk.Frame(pestanas, bg=C["fondo"], pady=12) for _ in range(2)]
    pestanas.add(marcos[0], text="Alta y edición")
    pestanas.add(marcos[1], text="Carga masiva")
    activa = pestanas.actual()
    (_estudiantes_alta if activa == 0 else _carga_masiva)(app, marcos[activa])


def _estudiantes_alta(app, padre):
    g = app.gestor
    grupos = sorted(g.grupos_registro)
    estado, v = formulario(app, "form_estudiante", {"sel": "", "apellidos": "", "nombre": "",
                                                     "grupo": grupos[0] if grupos else "", "inscribir": "1"})
    sel = estado["sel"] if estado["sel"] in g.estudiantes else ""
    fila = fila_columnas(app, padre, 2)
    form = en_columna(app.tarjeta(fila, f"Editar estudiante {sel}" if sel else "Nuevo estudiante"), 0, 2)
    app.campo(form, "Número de cuenta", tk.StringVar(value=sel or f"{g.siguiente_cuenta()}  (se asigna al guardar)"), 34,
              solo_lectura=True).pack(anchor="w", pady=3)
    app.campo(form, "Apellidos", v["apellidos"], 34).pack(anchor="w", pady=3)
    app.campo(form, "Nombre(s)", v["nombre"], 34).pack(anchor="w", pady=3)
    if grupos:
        app.campo(form, "Grupo", v["grupo"], 32, grupos).pack(anchor="w", pady=3)
    else:
        app.campo(form, "Grupo (3 dígitos)", v["grupo"], 34).pack(anchor="w", pady=3)
    inscribir = tk.BooleanVar(value=estado["inscribir"] == "1")
    if not sel:
        ttk.Checkbutton(form, text="Inscribir en todas las materias del grupo", variable=inscribir,
                        command=lambda: estado.__setitem__("inscribir", "1" if inscribir.get() else "0")).pack(anchor="w", pady=(6, 0))

    def limpiar():
        estado.update(sel="", apellidos="", nombre="")
        app.refrescar()

    def guardar():
        if sel:
            anterior = g.estudiantes[sel]
            if anterior.grupo != v["grupo"].get().strip() and not messagebox.askyesno(
                    "Cambio de grupo", "Al cambiar de grupo se le dan de baja sus materias actuales (con sus calificaciones) "
                                       "y se inscribe en las del nuevo grupo. ¿Continuar?"):
                return
            ok, _ = hacer(app, g.editar_estudiante, sel, v["apellidos"].get(), v["nombre"].get(), v["grupo"].get())
            if ok:
                limpiar()
            return
        ok, res = hacer(app, g.alta_estudiante, v["apellidos"].get(), v["nombre"].get(), v["grupo"].get(), inscribir.get())
        if ok:
            est, n = res
            estado.update(apellidos="", nombre="")
            messagebox.showinfo("Estudiante registrado",
                                f"{est.nombre_completo}\nNúmero de cuenta: {est.num_cuenta}\nInscrito en {n} materias del grupo {est.grupo}.\n\n"
                                f"Usuario: {est.num_cuenta}\nContraseña inicial: {est.num_cuenta} (se le pedirá cambiarla).")

    def baja():
        e = g.estudiantes[sel]
        if messagebox.askyesno("Dar de baja", f"¿Dar de baja a {e.nombre_completo} ({sel})?\n"
                                             "Se eliminan sus inscripciones, calificaciones y su cuenta de usuario."):
            if hacer(app, g.baja_estudiante, sel)[0]:
                limpiar()
    botones = tk.Frame(form, bg=C["blanco"])
    botones.pack(anchor="w", pady=(12, 0))
    app.boton(botones, "Guardar cambios" if sel else "Registrar estudiante", guardar).pack(side="left")
    if sel:
        app.boton(botones, "Nuevo", limpiar, primario=False).pack(side="left", padx=8)
        app.boton(botones, "Dar de baja", baja, primario=False).pack(side="left")
    nota(app, form, "Los grupos se registran en «Grupos y materias».")
    resumen = en_columna(app.tarjeta(fila, "Grupos"), 1, 2)
    tg = Tabla(resumen, app, [("grupo", "Grupo", 60), ("alumnos", "Alumnos", 80), ("materias", "Materias", 80)],
               alto=6, ordenables=())
    tg.pack(fill="both", expand=True)
    tg.cargar([{"grupo": gr, "alumnos": sum(1 for e in g.estudiantes.values() if e.grupo == gr),
                "materias": len(g.cursos_de_grupo(gr))} for gr in grupos])

    lista = app.tarjeta(padre, "Padrón de estudiantes", fill="x", pady=(14, 0))
    filtros = app.filtros_guardados.setdefault("padron", {"texto": "", "grupo": "Todos"})
    buscar, grupo = tk.StringVar(value=filtros["texto"]), tk.StringVar(value=filtros["grupo"])
    f = tk.Frame(lista, bg=C["blanco"])
    f.pack(fill="x", pady=(0, 10))
    app.campo(f, "Buscar (cuenta o nombre)", buscar, 28).pack(side="left", padx=(0, 10))
    app.campo(f, "Grupo", grupo, 8, ["Todos"] + grupos, lambda: aplicar()).pack(side="left")
    tabla = Tabla(lista, app, [("num_cuenta", "Núm. de cuenta", 110), ("apellidos", "Apellidos", 200), ("nombre", "Nombre", 150),
                               ("grupo", "Grupo", 60), ("materias", "Materias", 80), ("usuario", "Usuario", 110)], alto=10,
                  al_seleccionar=lambda x: editar(x["num_cuenta"]))
    tabla.pack(fill="both", expand=True)
    app.etiqueta(lista, "Haz clic en un estudiante para editarlo o darlo de baja.", 10, color=C["suave"]).pack(anchor="w", pady=(8, 0))
    usuarios = {u.get("referencia"): u["usuario"] for u in app.usuarios if u["rol"] == "Alumno"}
    todos = [{"num_cuenta": e.num_cuenta, "apellidos": e.apellidos, "nombre": e.nombre, "grupo": e.grupo,
              "materias": len(g.cursos_de(e.num_cuenta)), "usuario": usuarios.get(e.num_cuenta, "—")} for e in g.estudiantes.values()]

    def editar(nc):
        e = g.estudiantes[nc]
        estado.update(sel=nc, apellidos=e.apellidos, nombre=e.nombre, grupo=e.grupo)
        app.after_idle(app.refrescar)

    def aplicar(*_):
        filtros.update(texto=buscar.get(), grupo=grupo.get())
        t = buscar.get().strip().lower()
        tabla.cargar([x for x in todos if grupo.get() in ("Todos", x["grupo"])
                      and (not t or t in x["num_cuenta"] or t in f"{x['apellidos']} {x['nombre']}".lower())])
    buscar.trace_add("write", aplicar)
    aplicar()


def _carga_masiva(app, padre):
    t = app.tarjeta(padre, "Importar archivo JSON",
                    "Agrega estudiantes, materias, parciales, evaluaciones, inscripciones y calificaciones con el formato de "
                    "datos/ejemplo_entrada.json. Los registros válidos se guardan; los inválidos se listan sin detener la carga.",
                    fill="x")
    resultados = app.filtros_guardados.setdefault("carga_masiva", {"archivo": "", "errores": None})

    def importar():
        ruta = filedialog.askopenfilename(filetypes=[("JSON", "*.json")])
        if not ruta:
            return
        try:
            errores = app.importar_archivo(ruta)
        except ErrorValidacion as e:
            messagebox.showerror("Archivo inválido", legible(e.detalle))
            return
        resultados.update(archivo=Path(ruta).name, errores=errores)
        app.refrescar()
    app.boton(t, "Seleccionar archivo e importar", importar).pack(anchor="w", pady=(4, 0))
    if resultados["errores"] is not None:
        r = app.tarjeta(padre, f"Resultado · {resultados['archivo']}", fill="x", pady=(14, 0))
        app.etiqueta(r, f"Registros rechazados: {len(resultados['errores'])}", 11, "bold", color=app.acento).pack(anchor="w", pady=(0, 8))
        tabla = Tabla(r, app, [("tipo", "Tipo", 100), ("codigo", "Código", 200), ("detalle", "Detalle", 420)], alto=8, ordenables=())
        tabla.pack(fill="both", expand=True)
        tabla.cargar([{"tipo": e["tipo"], "codigo": e["codigo"], "detalle": e["detalle"]} for e in resultados["errores"]])


# ---------------------------------------------------------------- control escolar: grupos y materias
def materias(app):
    app.encabezado("Control escolar  /  Grupos y materias", "Grupos y materias",
                   "Registra los grupos y crea cada materia con sus 3 parciales y su docente; el grupo completo queda inscrito.")
    pestanas = Pestanas(app.contenido, app, "Grupos y materias", al_cambiar=app.refrescar)
    pestanas.pack(fill="x")
    marcos = [tk.Frame(pestanas, bg=C["fondo"], pady=12) for _ in range(2)]
    pestanas.add(marcos[0], text="Grupos")
    pestanas.add(marcos[1], text="Materias")
    activa = pestanas.actual()
    (_grupos if activa == 0 else _materias)(app, marcos[activa])


def _grupos(app, padre):
    from .gestor import TURNOS
    g = app.gestor
    estado, v = formulario(app, "form_grupo", {"sel": "", "clave": "", "semestre": "1", "turno": TURNOS[0]})
    sel = estado["sel"] if estado["sel"] in g.grupos_registro else ""
    fila = fila_columnas(app, padre, 2)
    form = en_columna(app.tarjeta(fila, f"Editar grupo {sel}" if sel else "Nuevo grupo"), 0, 2)
    if sel:
        app.campo(form, "Clave del grupo", v["clave"], 34, solo_lectura=True).pack(anchor="w", pady=3)
    else:
        app.campo(form, "Clave del grupo (3 dígitos, p. ej. 303)", v["clave"], 34).pack(anchor="w", pady=3)
    dos = fila_columnas(app, form, 2, pady=(3, 0))
    en_columna(app.campo(dos, "Semestre", v["semestre"], 12, [str(i) for i in range(1, 10)]), 0, 2)
    en_columna(app.campo(dos, "Turno", v["turno"], 12, list(TURNOS)), 1, 2)

    def limpiar():
        estado.update(sel="", clave="", semestre="1", turno=TURNOS[0])
        app.refrescar()

    def guardar():
        if sel:
            if hacer(app, g.editar_grupo, sel, v["semestre"].get(), v["turno"].get())[0]:
                limpiar()
            return
        ok, gr = hacer(app, g.alta_grupo, v["clave"].get(), v["semestre"].get(), v["turno"].get())
        if ok:
            estado.update(clave="")
            messagebox.showinfo("Grupo registrado", f"Grupo {gr.clave} · {gr.semestre}.º semestre · {gr.turno}\n\n"
                                                    "Ahora crea sus materias en la pestaña «Materias» y registra a sus alumnos.")

    def baja():
        if messagebox.askyesno("Eliminar grupo", f"¿Eliminar el grupo {sel}?"):
            if hacer(app, g.baja_grupo, sel)[0]:
                limpiar()
    botones = tk.Frame(form, bg=C["blanco"])
    botones.pack(anchor="w", pady=(12, 0))
    app.boton(botones, "Guardar cambios" if sel else "Registrar grupo", guardar).pack(side="left")
    if sel:
        app.boton(botones, "Nuevo", limpiar, primario=False).pack(side="left", padx=8)
        app.boton(botones, "Eliminar", baja, primario=False).pack(side="left")
    nota(app, form, "Un grupo solo se puede eliminar si no tiene alumnos ni materias.")
    pasos = en_columna(app.tarjeta(fila, "Orden de registro"), 1, 2)
    for i, texto in enumerate(("Registra el grupo (clave, semestre y turno).", "Crea sus materias y asigna el docente.",
                               "Registra a los alumnos: se inscriben solos en las materias del grupo.",
                               "Cada docente define su plan de evaluación y captura calificaciones."), 1):
        f = tk.Frame(pasos, bg=C["blanco"])
        f.pack(fill="x", pady=5)
        insignia(app, f, f"{i:02d}", 9).pack(side="left", padx=(0, 12))
        app.etiqueta(f, texto, 11, wraplength=380).pack(side="left")
    t = app.tarjeta(padre, "Grupos registrados", fill="x", pady=(14, 0))
    tabla = Tabla(t, app, [("grupo", "Grupo", 60), ("semestre", "Semestre", 80), ("turno", "Turno", 100),
                           ("alumnos", "Alumnos", 80), ("materias", "Materias", 80), ("sin_docente", "Sin docente", 90)],
                  alto=8, ordenables=(), al_seleccionar=lambda x: editar(x["grupo"]))
    tabla.pack(fill="both", expand=True)
    app.etiqueta(t, "Haz clic en un grupo para editarlo o eliminarlo.", 10, color=C["suave"]).pack(anchor="w", pady=(8, 0))
    tabla.cargar([{"grupo": gr.clave, "semestre": f"{gr.semestre}.º", "turno": gr.turno,
                   "alumnos": sum(1 for e in g.estudiantes.values() if e.grupo == gr.clave),
                   "materias": len(g.cursos_de_grupo(gr.clave)),
                   "sin_docente": sum(1 for c in g.cursos_de_grupo(gr.clave) if not g.docente_de(c.id))}
                  for gr in sorted(g.grupos_registro.values(), key=lambda x: x.clave)])

    def editar(clave):
        gr = g.grupos_registro[clave]
        estado.update(sel=clave, clave=clave, semestre=str(gr.semestre), turno=gr.turno)
        app.after_idle(app.refrescar)


def _materias(app, padre):
    g = app.gestor
    opciones_docente = ["Sin asignar"] + [_docente_txt(d) for d in sorted(g.docentes.values(), key=lambda d: d.id)]
    estado, v = formulario(app, "form_materia", {"sel": "", "asignatura": "", "grupo": "", "docente": "Sin asignar",
                                                 "p1": "30", "p2": "30", "p3": "40"})
    sel = estado["sel"] if estado["sel"] in g.cursos else ""
    fila = fila_columnas(app, padre, 2)
    form = en_columna(app.tarjeta(fila, f"Editar materia {sel}" if sel else "Nueva materia"), 0, 2)
    app.campo(form, "Asignatura", v["asignatura"], 34).pack(anchor="w", pady=3)
    if sel:
        app.campo(form, "Grupo", v["grupo"], 34, solo_lectura=True).pack(anchor="w", pady=3)
    else:
        grupos = sorted(g.grupos_registro)
        app.campo(form, "Grupo", v["grupo"], 32, grupos or ["Registra un grupo primero"]).pack(anchor="w", pady=3)
    app.campo(form, "Docente", v["docente"], 32, opciones_docente).pack(anchor="w", pady=3)
    app.etiqueta(form, "Peso de cada parcial en la calificación final (%)", 10, color=C["suave"]).pack(anchor="w", pady=(8, 0))
    pesos = tk.Frame(form, bg=C["blanco"])
    pesos.pack(anchor="w")
    for i in (1, 2, 3):
        app.campo(pesos, f"Parcial {i}", v[f"p{i}"], 6).pack(side="left", padx=(0, 10))

    def docente_id():
        return None if v["docente"].get() == "Sin asignar" else v["docente"].get().split(" · ")[0]

    def limpiar():
        estado.update(sel="", asignatura="", docente="Sin asignar", p1="30", p2="30", p3="40")
        app.refrescar()

    def guardar():
        pesos_ = [_pct(v[f"p{i}"].get()) for i in (1, 2, 3)]
        if sel:
            if hacer(app, g.editar_curso, sel, v["asignatura"].get(), pesos_, docente_id())[0]:
                limpiar()
            return
        ok, res = hacer(app, g.alta_curso, v["asignatura"].get(), v["grupo"].get(), pesos_, docente_id())
        if ok:
            curso, n = res
            estado.update(asignatura="")
            messagebox.showinfo("Materia creada", f"{curso.id} · {curso.asignatura} · grupo {curso.grupo}\n"
                                                  f"Parciales creados: 3\nAlumnos inscritos: {n}\n\n"
                                                  "El docente asignado define las evaluaciones de cada parcial en «Plan de evaluación».")

    def eliminar():
        c = g.cursos[sel]
        if messagebox.askyesno("Eliminar materia", f"¿Eliminar {c.id} · {c.asignatura} ({c.grupo})?\n"
                                                    "Se quitan sus parciales, evaluaciones e inscripciones."):
            if hacer(app, g.eliminar_curso, sel)[0]:
                limpiar()
    botones = tk.Frame(form, bg=C["blanco"])
    botones.pack(anchor="w", pady=(12, 0))
    app.boton(botones, "Guardar cambios" if sel else "Crear materia", guardar).pack(side="left")
    if sel:
        app.boton(botones, "Nueva", limpiar, primario=False).pack(side="left", padx=8)
        app.boton(botones, "Eliminar", eliminar, primario=False).pack(side="left")
    resumen = en_columna(app.tarjeta(fila, "Grupos"), 1, 2)
    tg = Tabla(resumen, app, [("grupo", "Grupo", 60), ("semestre", "Semestre", 70), ("turno", "Turno", 90),
                              ("materias", "Materias", 70), ("sin_docente", "Sin docente", 90)], alto=6, ordenables=())
    tg.pack(fill="both", expand=True)
    tg.cargar([{"grupo": gr.clave, "semestre": f"{gr.semestre}.º", "turno": gr.turno, "materias": len(g.cursos_de_grupo(gr.clave)),
                "sin_docente": sum(1 for c in g.cursos_de_grupo(gr.clave) if not g.docente_de(c.id))}
               for gr in sorted(g.grupos_registro.values(), key=lambda x: x.clave)])
    nota(app, resumen, "Los grupos se registran en la pestaña «Grupos».")

    t = app.tarjeta(padre, "Materias", fill="x", pady=(14, 0))
    tabla = Tabla(t, app, [("clave", "Clave", 60), ("asignatura", "Asignatura", 220), ("grupo", "Grupo", 60),
                           ("docente", "Docente", 180), ("inscritos", "Inscritos", 70), ("pesos", "Parciales %", 100),
                           ("plan", "Plan de evaluación", 130)], alto=10, ordenables=(),
                  al_seleccionar=lambda x: editar(x["clave"]))
    tabla.pack(fill="both", expand=True)
    app.etiqueta(t, "Haz clic en una materia para editarla, cambiar su docente o eliminarla.", 10, color=C["suave"]).pack(anchor="w", pady=(8, 0))
    tabla.cargar([{"clave": c.id, "asignatura": c.asignatura, "grupo": c.grupo, "docente": _docente_txt(g.docente_de(c.id)),
                   "inscritos": len(g.alumnos_de_curso(c.id)),
                   "pesos": " / ".join(_txt_pct(p.peso) for p in g.parciales_de(c.id)),
                   "plan": "Completo" if _plan_completo(g, c.id) == 3 else f"Pendiente {_plan_completo(g, c.id)}/3"}
                  for c in sorted(g.cursos.values(), key=lambda c: (c.grupo, c.asignatura))])

    def editar(cid):
        c = g.cursos[cid]
        ps = g.parciales_de(cid)
        estado.update(sel=cid, asignatura=c.asignatura, grupo=c.grupo, docente=_docente_txt(g.docente_de(cid)),
                      **{f"p{i}": _txt_pct(p.peso) for i, p in enumerate(ps, 1)})
        app.after_idle(app.refrescar)


# ---------------------------------------------------------------- control escolar: docentes
def docentes(app):
    g = app.gestor
    app.encabezado("Control escolar  /  Docentes", "Docentes",
                   "Al registrar un docente se crea su cuenta. Cada materia tiene un solo docente.")
    fila = fila_columnas(app, app.contenido, 2)
    alta = en_columna(app.tarjeta(fila, "Nuevo docente"), 0, 2)
    estado, v = formulario(app, "form_docente", {"nombre": "", "docente": ""})
    sig = g.siguiente_docente()
    app.campo(alta, "ID", tk.StringVar(value=f"{sig}  (se asigna al guardar)"), 34, solo_lectura=True).pack(anchor="w", pady=3)
    app.campo(alta, "Nombre completo", v["nombre"], 34).pack(anchor="w", pady=3)

    def registrar():
        ok, d = hacer(app, g.alta_docente, v["nombre"].get())
        if ok:
            estado.update(nombre="", docente=_docente_txt(d))
            messagebox.showinfo("Docente registrado", f"{d.id} · {d.nombre}\n\nUsuario: {d.id.lower()}\n"
                                                      f"Contraseña inicial: {d.id.lower()} (se le pedirá cambiarla).\n\n"
                                                      "Ahora asígnale sus materias.")
    app.boton(alta, "Registrar docente", registrar).pack(anchor="w", pady=(12, 0))

    asig = en_columna(app.tarjeta(fila, "Materias del docente"), 1, 2)
    opciones = [_docente_txt(d) for d in sorted(g.docentes.values(), key=lambda d: d.id)]
    if not opciones:
        app.etiqueta(asig, "Registra un docente primero.", 10, color=C["suave"]).pack(anchor="w")
    else:
        if estado["docente"] not in opciones:
            v["docente"].set(opciones[0])
        app.campo(asig, "Docente", v["docente"], 32, opciones, app.refrescar).pack(anchor="w", pady=(0, 8))
        did = v["docente"].get().split(" · ")[0]
        marcas = {}
        lista = app.caja(asig, radio=7, padx=12, pady=8)
        lista.pack(fill="x")
        for c in sorted(g.cursos.values(), key=lambda c: (c.grupo, c.asignatura)):
            actual = g.docente_de(c.id)
            marcas[c.id] = tk.BooleanVar(value=bool(actual and actual.id == did))
            extra = "" if not actual or actual.id == did else f"   · ahora: {actual.nombre}"
            ttk.Checkbutton(lista, text=f"{c.id}  {c.asignatura} · {c.grupo}{extra}", variable=marcas[c.id]).pack(anchor="w", pady=1)
        if not g.cursos:
            app.etiqueta(lista, "No hay materias registradas.", 10, color=C["suave"]).pack(anchor="w")

        def guardar():
            hacer(app, g.asignar_cursos, did, [c for c, m in marcas.items() if m.get()])
        app.boton(asig, "Guardar asignación", guardar).pack(anchor="w", pady=(10, 0))
        nota(app, asig, "Si marcas una materia de otro docente, pasa a este docente.")
    t = app.tarjeta(app.contenido, "Plantilla docente", fill="x", pady=(14, 0))
    tabla = Tabla(t, app, [("id", "ID", 60), ("nombre", "Nombre", 200), ("usuario", "Usuario", 100),
                           ("materias", "Materias", 320), ("alumnos", "Alumnos", 80)], alto=8, ordenables=())
    tabla.pack(fill="both", expand=True)
    usuarios = {u.get("referencia"): u["usuario"] for u in app.usuarios if u["rol"] == "Docente"}
    tabla.cargar([{"id": d.id, "nombre": d.nombre, "usuario": usuarios.get(d.id, "—"),
                   "materias": ", ".join(f"{c.asignatura} ({c.grupo})" for c in g.cursos_de_docente(d.id)) or "Sin materias",
                   "alumnos": sum(len(g.alumnos_de_curso(c.id)) for c in g.cursos_de_docente(d.id))}
                  for d in sorted(g.docentes.values(), key=lambda d: d.id)])


# ---------------------------------------------------------------- control escolar: inscripciones
def inscripciones(app):
    g = app.gestor
    app.encabezado("Control escolar  /  Inscripciones", "Inscripciones",
                   "La inscripción al grupo es automática; aquí se atienden altas y bajas individuales.")
    grupos = g.grupos()
    estado, v = formulario(app, "form_inscripcion", {"grupo": grupos[0] if grupos else "", "alumno": ""})
    if estado["grupo"] not in grupos and grupos:
        v["grupo"].set(grupos[0])
    grupo = v["grupo"].get()
    alumnos = sorted((e for e in g.estudiantes.values() if e.grupo == grupo), key=lambda e: (e.apellidos, e.nombre))
    nombres = [f"{e.num_cuenta} · {e.nombre_completo}" for e in alumnos]
    if estado["alumno"] not in nombres:
        v["alumno"].set(nombres[0] if nombres else "")
    fila = fila_columnas(app, app.contenido, 2)
    ind = en_columna(app.tarjeta(fila, "Por estudiante"), 0, 2)
    dos = fila_columnas(app, ind, 2)
    en_columna(app.campo(dos, "Grupo", v["grupo"], 10, grupos or ["—"], app.refrescar), 0, 2)
    en_columna(app.campo(dos, "Estudiante", v["alumno"], 30, nombres or ["—"], app.refrescar), 1, 2)
    nc = v["alumno"].get().split(" · ")[0]
    marcas = {}
    lista = app.caja(ind, radio=7, padx=12, pady=8)
    lista.pack(fill="x", pady=(10, 0))
    for c in g.cursos_de_grupo(grupo):
        marcas[c.id] = tk.BooleanVar(value=(nc, c.id) in g.inscripciones)
        ttk.Checkbutton(lista, text=f"{c.id}  {c.asignatura}", variable=marcas[c.id]).pack(anchor="w", pady=1)
    if not marcas:
        app.etiqueta(lista, "El grupo no tiene materias.", 10, color=C["suave"]).pack(anchor="w")

    def guardar():
        if nc not in g.estudiantes:
            return
        elegidos = {c for c, m in marcas.items() if m.get()}
        quitar = [c for c, m in marcas.items() if not m.get() and (nc, c) in g.inscripciones]
        con_notas = [c for c in quitar if any(k[0] == nc and k[1] in {e.id for p in g.parciales_de(c) for e in g.evaluaciones_de(p.id)}
                                              for k in g.calificaciones)]
        if con_notas and not messagebox.askyesno("Baja de materias", f"Se borrarán las calificaciones de {len(con_notas)} "
                                                                     "materia(s) que ya tienen captura. ¿Continuar?"):
            return
        ok, res = hacer(app, g.actualizar_inscripciones, nc, elegidos)
        if ok:
            messagebox.showinfo("Inscripción actualizada", f"Altas: {res[0]}   ·   Bajas: {res[1]}")
    app.boton(ind, "Guardar inscripción", guardar).pack(anchor="w", pady=(10, 0))
    grp = en_columna(app.tarjeta(fila, "Por grupo"), 1, 2)
    faltan = sum(1 for e in alumnos for c in g.cursos_de_grupo(grupo) if (e.num_cuenta, c.id) not in g.inscripciones)
    app.etiqueta(grp, f"Grupo {grupo or '—'}: {len(alumnos)} alumnos, {len(g.cursos_de_grupo(grupo))} materias", 12, "bold").pack(anchor="w")
    app.etiqueta(grp, f"Inscripciones faltantes: {faltan}", 11, color=app.acento if faltan else C["suave"]).pack(anchor="w", pady=(4, 10))
    app.boton(grp, "Inscribir al grupo en todas sus materias",
              lambda: hacer(app, g.inscribir_grupo, grupo) if grupo else None, primario=faltan > 0).pack(anchor="w")
    nota(app, grp, "Útil si se dieron bajas individuales por error o después de una carga masiva.")
    t = app.tarjeta(app.contenido, "Inscritos por materia", fill="x", pady=(14, 0))
    tabla = Tabla(t, app, [("clave", "Clave", 60), ("asignatura", "Asignatura", 240), ("grupo", "Grupo", 60),
                           ("inscritos", "Inscritos", 80), ("del_grupo", "Alumnos del grupo", 120), ("estado", "Estado", 110)],
                  alto=10, ordenables=())
    tabla.pack(fill="both", expand=True)
    filas = []
    for c in sorted(g.cursos.values(), key=lambda c: (c.grupo, c.asignatura)):
        n, total = len(g.alumnos_de_curso(c.id)), sum(1 for e in g.estudiantes.values() if e.grupo == c.grupo)
        filas.append({"clave": c.id, "asignatura": c.asignatura, "grupo": c.grupo, "inscritos": n, "del_grupo": total,
                      "estado": "Completo" if n == total else "Pendiente"})
    tabla.cargar(filas)


# ---------------------------------------------------------------- control escolar: usuarios
def usuarios(app):
    from .autenticacion import contrasena_visible, restablecer
    g = app.gestor
    app.encabezado("Control escolar  /  Usuarios", "Usuarios y accesos",
                   "Haz clic en una cuenta para ver su usuario y contraseña. Restablecer deja la contraseña igual al usuario.")
    filtros = app.filtros_guardados.setdefault("usuarios", {"texto": "", "rol": "Todos", "grupo": "Todos", "sel": ""})
    filtros.setdefault("grupo", "Todos")

    def grupo_de(u):
        e = g.estudiantes.get(u.get("referencia")) if u["rol"] == "Alumno" else None
        return e.grupo if e else "—"
    elegido = next((u for u in app.usuarios if u["usuario"] == filtros["sel"]), None)
    fila = fila_columnas(app, app.contenido, 2)
    det = en_columna(app.tarjeta(fila, "Cuenta seleccionada"), 0, 2)
    if not elegido:
        app.etiqueta(det, "Selecciona una cuenta en la tabla.", 11, color=C["suave"]).pack(anchor="w")
    else:
        visible = contrasena_visible(elegido)
        datos = [("Usuario", elegido["usuario"]), ("Nombre", elegido["nombre"]), ("Rol", elegido["rol"]),
                 ("Grupo" if elegido["rol"] == "Alumno" else "Materias",
                  grupo_de(elegido) if elegido["rol"] == "Alumno" else
                  ", ".join(c.id for c in g.cursos_de_docente(elegido.get("referencia"))) or "—")]
        rej = tk.Frame(det, bg=C["blanco"])
        rej.pack(fill="x")
        for i, (titulo, valor) in enumerate(datos):
            celda = tk.Frame(rej, bg=C["blanco"])
            celda.grid(row=i // 2, column=i % 2, sticky="w", padx=(0, 40), pady=4)
            app.etiqueta(celda, titulo, 10, color=C["suave"]).pack(anchor="w")
            app.etiqueta(celda, valor, 13, "bold").pack(anchor="w")
        caja = app.caja(det, radio=8, fondo=app.acento_suave, borde=app.acento_suave, padx=14, pady=10)
        caja.pack(fill="x", pady=(10, 0))
        app.etiqueta(caja, "Contraseña", 10, color=C["suave"], bg=app.acento_suave).pack(anchor="w")
        if visible:
            app.etiqueta(caja, visible, 16, "bold", color=app.acento, bg=app.acento_suave).pack(anchor="w")
            app.etiqueta(caja, "Contraseña inicial: se le pedirá cambiarla al entrar.", 10, color=C["suave"],
                         bg=app.acento_suave).pack(anchor="w")
        else:
            app.etiqueta(caja, "Personalizada por el usuario", 14, "bold", color=app.acento, bg=app.acento_suave).pack(anchor="w")
            app.etiqueta(caja, "Está cifrada y no se puede consultar. Restablécela para asignarle una nueva.", 10,
                         color=C["suave"], bg=app.acento_suave, wraplength=420).pack(anchor="w")

        def restablecer_():
            if messagebox.askyesno("Restablecer contraseña", f"La contraseña de {elegido['usuario']} será «{elegido['usuario']}» "
                                                             "y deberá cambiarla al entrar. ¿Continuar?"):
                restablecer(elegido)
                app.guardar_ahora()
                app.registrar_actividad(f"contrasena_restablecida · {elegido['usuario']}")
                app.refrescar()
        if elegido["usuario"] != (app.cuenta or {}).get("usuario"):
            app.boton(det, "Restablecer contraseña", restablecer_, primario=False).pack(anchor="w", pady=(12, 0))
    rep = en_columna(app.tarjeta(fila, "Reporte de usuarios y contraseñas"), 1, 2)
    app.etiqueta(rep, "Genera la lista con encabezado institucional en PDF, CSV o JSON.", 10, color=C["suave"]).pack(anchor="w")

    def abrir_reporte(filtro):
        app.filtros_guardados["reporte"] = {"tipo": "credenciales", "grupo": "", "alumno": "", "materia": "",
                                            "orden": "Por apellidos", "estado": "Todos", "usuarios": filtro}
        app.ir("Reportes")
    botones = tk.Frame(rep, bg=C["blanco"])
    botones.pack(anchor="w", pady=(10, 0))
    for i, filtro in enumerate(["Docentes"] + [f"Grupo {x}" for x in g.grupos()] + ["Todos"]):
        app.boton(botones, filtro, lambda f_=filtro: abrir_reporte(f_), primario=i == 0).grid(row=i // 4, column=i % 4, padx=(0, 8),
                                                                                             pady=4, sticky="w")

    t = app.tarjeta(app.contenido, "Cuentas", fill="x", pady=(14, 0))
    f = tk.Frame(t, bg=C["blanco"])
    f.pack(fill="x", pady=(0, 10))
    texto, rol, grupo = tk.StringVar(value=filtros["texto"]), tk.StringVar(value=filtros["rol"]), tk.StringVar(value=filtros["grupo"])
    app.campo(f, "Buscar", texto, 24).pack(side="left", padx=(0, 10))
    app.campo(f, "Rol", rol, 14, ["Todos", "Administrador", "Docente", "Alumno"], lambda: aplicar()).pack(side="left", padx=(0, 10))
    app.campo(f, "Grupo", grupo, 8, ["Todos"] + g.grupos(), lambda: aplicar()).pack(side="left")
    tabla = Tabla(t, app, [("usuario", "Usuario", 100), ("nombre", "Nombre", 220), ("rol", "Rol", 110), ("grupo", "Grupo", 70),
                           ("contrasena", "Contraseña", 130), ("estado", "Estado", 150)], alto=12, ordenables=(),
                  al_seleccionar=lambda x: (filtros.__setitem__("sel", x["usuario"]), app.after_idle(app.refrescar)))
    tabla.pack(fill="both", expand=True)
    filas = [{"usuario": u["usuario"], "nombre": u["nombre"], "rol": u["rol"], "grupo": grupo_de(u),
              "contrasena": contrasena_visible(u) or "Personalizada",
              "estado": "Contraseña inicial" if contrasena_visible(u) else "Activa"} for u in app.usuarios]

    def aplicar(*_):
        filtros.update(texto=texto.get(), rol=rol.get(), grupo=grupo.get())
        q = texto.get().strip().lower()
        tabla.cargar([x for x in filas if rol.get() in ("Todos", x["rol"]) and grupo.get() in ("Todos", x["grupo"])
                      and (not q or q in x["usuario"].lower() or q in x["nombre"].lower())])
    texto.trace_add("write", aplicar)
    aplicar()

    r = app.tarjeta(app.contenido, "Respaldo de datos", "Todo se guarda automáticamente en datos/base.json después de cada cambio.",
                    fill="x", pady=(14, 0))
    botones = tk.Frame(r, bg=C["blanco"])
    botones.pack(anchor="w")

    def respaldo():
        ruta = filedialog.asksaveasfilename(defaultextension=".json", initialfile="respaldo_syllabus.json")
        if ruta:
            from . import persistencia
            persistencia.guardar(app.gestor, app.usuarios, ruta)
            app.registrar_actividad(f"respaldo_exportado · {Path(ruta).name}")

    def reiniciar():
        if messagebox.askyesno("Restablecer datos de ejemplo", "Se borrarán todos los cambios y se volverá a los datos de ejemplo. "
                                                               "¿Continuar?"):
            app.restablecer_ejemplo()
            app.mostrar_acceso()
    app.boton(botones, "Exportar respaldo", respaldo).pack(side="left")
    app.boton(botones, "Restablecer datos de ejemplo", reiniciar, primario=False).pack(side="left", padx=8)


# ---------------------------------------------------------------- mi cuenta (todos los perfiles)
def mi_cuenta(app):
    from .autenticacion import cambiar_contrasena
    cuenta = app.cuenta
    app.encabezado(f"{app.rol}  /  Mi cuenta", "Mi cuenta", "Datos de acceso y cambio de contraseña.")
    if cuenta.get("debe_cambiar"):
        banda = app.caja(app.contenido, radio=11, fondo=C["ambar_suave"], borde=C["ambar_suave"], padx=18, pady=14)
        banda.pack(fill="x", pady=(0, 14))
        app.etiqueta(banda, "Estás usando la contraseña inicial. Cámbiala para continuar.", 12, "bold", color="#a26b34").pack(anchor="w")
    fila = fila_columnas(app, app.contenido, 2)
    datos = en_columna(app.tarjeta(fila, "Datos de la cuenta"), 0, 2)
    for titulo, valor in (("Usuario", cuenta["usuario"]), ("Nombre", cuenta["nombre"]), ("Rol", cuenta["rol"]),
                          ("Referencia", cuenta.get("referencia") or "—")):
        app.etiqueta(datos, titulo, 10, color=C["suave"]).pack(anchor="w")
        app.etiqueta(datos, valor, 12, "bold").pack(anchor="w", pady=(0, 8))
    form = en_columna(app.tarjeta(fila, "Cambiar contraseña"), 1, 2)
    v = {k: tk.StringVar() for k in ("actual", "nueva", "confirmacion")}
    for clave, texto in (("actual", "Contraseña actual"), ("nueva", "Nueva contraseña"), ("confirmacion", "Confirmar nueva contraseña")):
        c = app.campo(form, texto, v[clave], 30)
        _ocultar(c)
        entrada = next(w for w in _descendientes(c) if isinstance(w, tk.Entry))
        entrada.pack_configure(side="left")
        _boton_ver(app, entrada.master, entrada).pack(side="left", padx=(8, 0))
        c.pack(anchor="w", pady=3)
    nota(app, form, "Mínimo 8 caracteres, con letras y números.")

    def guardar():
        forzado = cuenta.get("debe_cambiar")
        try:
            cambiar_contrasena(cuenta, v["actual"].get(), v["nueva"].get(), v["confirmacion"].get())
        except ErrorValidacion as e:
            messagebox.showerror("No se cambió la contraseña", legible(e.detalle[:1].upper() + e.detalle[1:]))
            return
        app.guardar_ahora()
        app.registrar_actividad(f"contrasena_cambiada · {cuenta['usuario']}")
        messagebox.showinfo("Contraseña actualizada", "Tu contraseña se cambió correctamente.")
        app.ir("Inicio" if forzado else "Mi cuenta")
    app.boton(form, "Guardar contraseña", guardar).pack(anchor="w")


# ---------------------------------------------------------------- docente: plan de evaluación
PLANTILLA = [("Examen", "60"), ("Tareas", "20"), ("Actividades", "20")]


def _selector_curso(app, padre, clave, cursos, ancho=36):
    opciones = [f"{c.id} · {c.asignatura} · {c.grupo}" for c in cursos]
    estado = app.filtros_guardados.setdefault(clave, {"curso": opciones[0] if opciones else ""})
    if estado["curso"] not in opciones and opciones:
        estado["curso"] = opciones[0]
    var = tk.StringVar(value=estado["curso"])

    def cambiar():
        estado["curso"] = var.get()
        app.refrescar()
    app.campo(padre, "Materia", var, ancho, opciones or ["—"], cambiar).pack(side="left", padx=(0, 14))
    return app.gestor.cursos.get(estado["curso"].split(" · ")[0])


def plan_evaluacion(app):
    g = app.gestor
    app.encabezado("Docente  /  Plan de evaluación", "Plan de evaluación",
                   "Define las evaluaciones de cada parcial y su porcentaje. Cada parcial debe sumar 100 %.")
    cursos = app.cursos_visibles()
    barra = app.tarjeta(app.contenido, fill="x")
    f = tk.Frame(barra, bg=C["blanco"])
    f.pack(fill="x")
    curso = _selector_curso(app, f, "plan_curso", cursos)
    if not curso:
        app.etiqueta(barra, "No tienes materias asignadas.", 11, color=C["suave"]).pack(anchor="w")
        return
    borradores = app.filtros_guardados.setdefault("plan", {})
    parciales = g.parciales_de(curso.id)
    for p in parciales:
        borradores.setdefault(p.id, [{"id": e.id, "nombre": e.nombre, "pct": _txt_pct(e.peso)} for e in g.evaluaciones_de(p.id)])

    def plantilla():
        for p in parciales:
            if not g.evaluaciones_de(p.id) or messagebox.askyesno("Usar plantilla", f"El parcial {p.numero} ya tiene plan. ¿Reemplazarlo?"):
                borradores[p.id] = [{"id": None, "nombre": n, "pct": x} for n, x in PLANTILLA]
        app.refrescar()
    app.boton(f, "Usar plantilla (Examen 60 · Tareas 20 · Actividades 20)", plantilla, primario=False).pack(side="left", anchor="s")
    fila = fila_columnas(app, app.contenido, 3, pady=(14, 0))
    for i, p in enumerate(parciales):
        _tarjeta_parcial(app, en_columna(app.tarjeta(fila, f"Parcial {p.numero}  ·  {_txt_pct(p.peso)} % del curso"), i, 3),
                         p, borradores)

    def guardar():
        planes = {p.id: [{"id": r["id"], "nombre": r["nombre"], "peso": _pct(r["pct"])} for r in borradores[p.id]]
                  for p in parciales}
        perdidas = sum(1 for p in parciales for e in g.evaluaciones_de(p.id)
                       if e.id not in {r["id"] for r in borradores[p.id]} for k in g.calificaciones if k[1] == e.id)
        if perdidas and not messagebox.askyesno("Quitar evaluaciones", f"Se borrarán {perdidas} calificaciones ya capturadas. ¿Continuar?"):
            return
        if hacer(app, g.definir_planes, planes)[0]:
            for p in parciales:
                borradores.pop(p.id, None)
            messagebox.showinfo("Plan guardado", f"Plan de evaluación de {curso.asignatura} guardado. Ya puedes capturar calificaciones.")

    def descartar():
        for p in parciales:
            borradores.pop(p.id, None)
        app.refrescar()
    botones = tk.Frame(app.contenido, bg=C["fondo"])
    botones.pack(anchor="w", pady=(14, 14))
    app.boton(botones, "Guardar plan", guardar).pack(side="left")
    app.boton(botones, "Descartar cambios", descartar, primario=False).pack(side="left", padx=8)
    nota_pie(app, "Al quitar una evaluación que ya tiene calificaciones, esas calificaciones se borran (se te pedirá confirmar). "
                  "Cambiar porcentajes recalcula los promedios.")


def _tarjeta_parcial(app, t, p, borradores):
    filas = borradores[p.id]
    suma = app.etiqueta(t, "", 11, "bold")

    def actualizar_suma(*_):
        total = sum(x for x in (_num(r["pct"]) for r in filas) if isinstance(x, float))
        ok = abs(total - 100) < 1e-9
        suma.config(text=f"Suma: {total:g} %  {'✓' if ok else '· debe ser 100 %'}", fg="#247457" if ok else "#a32e34")
    cab = tk.Frame(t, bg=C["blanco"])
    cab.pack(fill="x")
    app.etiqueta(cab, "Evaluación", 10, color=C["suave"], width=18).pack(side="left")
    app.etiqueta(cab, "%", 10, color=C["suave"]).pack(side="left")
    for r in filas:
        f = tk.Frame(t, bg=C["blanco"])
        f.pack(fill="x", pady=2)
        for clave, ancho in (("nombre", 18), ("pct", 5)):
            var = tk.StringVar(value=r[clave])
            var.trace_add("write", lambda *_a, r=r, clave=clave, var=var: (r.__setitem__(clave, var.get()), actualizar_suma()))
            borde = app.caja(f, radio=5, padx=7, pady=4)
            tk.Entry(borde, textvariable=var, width=ancho, font=(ui.FUENTE, 11), bg=C["blanco"], relief="flat", bd=0,
                     highlightthickness=0).pack()
            borde.pack(side="left", padx=(0, 6))
        x = app.etiqueta(f, "✕", 12, "bold", color=C["suave"], cursor="hand2")
        x.pack(side="left", padx=4)
        x.bind("<Button-1>", lambda e, r=r: (filas.remove(r), app.refrescar()))
    agregar = app.etiqueta(t, "+  Agregar evaluación", 10, "bold", color=app.acento, cursor="hand2")
    agregar.pack(anchor="w", pady=(6, 6))
    agregar.bind("<Button-1>", lambda e: (filas.append({"id": None, "nombre": "", "pct": ""}), app.refrescar()))
    suma.pack(anchor="w", pady=(0, 4))
    actualizar_suma()


# ---------------------------------------------------------------- docente: captura por acta
def captura(app):
    activa = app.filtros_guardados.get("pestana:captura", 0)
    titulo, subtitulo = (("Acta de calificaciones", "Captura las calificaciones de todo el grupo y guárdalas juntas.") if activa == 0 else
                         ("Importar calificaciones", "Carga y procesa calificaciones de varios estudiantes desde un archivo."))
    app.encabezado(ruta_de(app, "Captura"), titulo, subtitulo)
    pestanas = Pestanas(app.contenido, app, "captura", al_cambiar=app.refrescar)
    pestanas.pack(fill="x")
    acta = tk.Frame(pestanas, bg=C["fondo"], pady=12)
    varias = tk.Frame(pestanas, bg=C["fondo"], pady=12)
    pestanas.add(acta, text="Acta de calificaciones")
    pestanas.add(varias, text="Importar archivo")
    (_acta if pestanas.actual() == 0 else _captura_varias)(app, acta if pestanas.actual() == 0 else varias)


def _valida(texto) -> bool:
    t = texto.strip()
    if not t:
        return True
    try:
        x = float(t)
    except ValueError:
        return False
    return 0 <= x <= 10 and abs(round(x, 1) - x) < 1e-9


def _acta(app, padre):
    g = app.gestor
    cursos = app.cursos_visibles()
    barra = app.tarjeta(padre, fill="x")
    f = tk.Frame(barra, bg=C["blanco"])
    f.pack(fill="x")
    curso = _selector_curso(app, f, "acta_curso", cursos)
    if not curso:
        app.etiqueta(barra, "No tienes materias asignadas.", 11, color=C["suave"]).pack(anchor="w")
        return
    parciales = g.parciales_de(curso.id)
    nombres = [f"Parcial {p.numero} · {_txt_pct(p.peso)} %" for p in parciales]
    estado = app.filtros_guardados.setdefault("acta_parcial", {"parcial": nombres[0] if nombres else ""})
    if estado["parcial"] not in nombres:
        estado["parcial"] = nombres[0] if nombres else ""
    var_p = tk.StringVar(value=estado["parcial"])
    app.campo(f, "Parcial", var_p, 20, nombres or ["—"], lambda: (estado.__setitem__("parcial", var_p.get()), app.refrescar())).pack(side="left")
    parcial = parciales[nombres.index(estado["parcial"])] if nombres else None
    evals = g.evaluaciones_de(parcial.id) if parcial else []
    if not evals or abs(sum(e.peso for e in evals) - 1) > 1e-9:
        t = app.tarjeta(padre, fill="x", pady=(14, 0))
        app.etiqueta(t, "Este parcial no tiene su plan de evaluación completo (100 %).", 12, "bold", color="#a26b34").pack(anchor="w")
        app.boton(t, "Definir plan de evaluación", lambda: app.ir("Plan de evaluación")).pack(anchor="w", pady=(10, 0))
        return
    alumnos = g.alumnos_de_curso(curso.id)
    cambios = app.filtros_guardados.setdefault("acta", {}).setdefault(parcial.id, {})

    def guardado(nc, eid):
        x = g.calificaciones.get((nc, eid))
        return "" if x is None else f"{x:g}"

    t = app.tarjeta(padre, f"{curso.asignatura} · grupo {curso.grupo} · {estado['parcial']}",
                    "Escribe calificaciones de 0 a 10 con un decimal. Enter o ↓ baja al siguiente alumno; deja vacío para no capturar.",
                    fill="x", pady=(14, 0))
    rejilla = tk.Frame(t, bg=C["blanco"])
    rejilla.pack(fill="x")
    titulos = ["No.", "Cuenta", "Alumno"] + [f"{e.nombre} {_txt_pct(e.peso)} %" for e in evals] + ["Parcial", "Estado"]
    for j, texto in enumerate(titulos):
        app.etiqueta(rejilla, texto, 10, "bold", color=C["suave"]).grid(row=0, column=j, sticky="w", padx=6, pady=(0, 6))
    rejilla.columnconfigure(2, weight=1)
    entradas = {}
    contador = app.etiqueta(t, "", 10, color=app.acento)

    def recalcular(nc, fila_lbls):
        textos = [entradas[(nc, e.id)][0].get() for e in evals]
        validos = [_valida(x) for x in textos]
        for e, ok in zip(evals, validos):
            entradas[(nc, e.id)][1].config(bg=C["blanco"] if ok else C["rojo_suave"])
            entradas[(nc, e.id)][2].config(bg=C["blanco"] if ok else C["rojo_suave"])
        if all(validos) and all(x.strip() for x in textos):
            nota_p = sum(float(x) * e.peso for x, e in zip(textos, evals))
            fila_lbls[0].config(text=fmt(mostrar(nota_p)))
            fila_lbls[1].config(text="Completo", fg="#247457")
        else:
            fila_lbls[0].config(text="—")
            fila_lbls[1].config(text="Con errores" if not all(validos) else "Incompleto",
                                fg="#a32e34" if not all(validos) else "#a26b34")
        contador.config(text=f"Cambios sin guardar: {len(cambios)}" if cambios else "Sin cambios pendientes")

    def mover(evento, i, j, paso):
        destino = entradas.get((alumnos[i + paso].num_cuenta, evals[j].id)) if 0 <= i + paso < len(alumnos) else None
        if destino:
            destino[2].focus_set()
            destino[2].select_range(0, "end")
        return "break"

    for i, e in enumerate(alumnos):
        fila = i + 1
        for j, texto in enumerate((f"{i + 1}", e.num_cuenta, e.nombre_completo)):
            tk.Label(rejilla, text=texto, font=(ui.FUENTE, 11), bg=C["blanco"], fg=C["texto"], anchor="w").grid(
                row=fila, column=j, sticky="w", padx=6, pady=2)
        lbls = (tk.Label(rejilla, text="—", font=(ui.FUENTE, 11, "bold"), bg=C["blanco"], fg=app.acento, width=6, anchor="w"),
                tk.Label(rejilla, text="", font=(ui.FUENTE, 10), bg=C["blanco"], width=11, anchor="w"))
        for j, ev in enumerate(evals):
            clave = (e.num_cuenta, ev.id)
            var = tk.StringVar(value=cambios.get(f"{e.num_cuenta}|{ev.id}", guardado(*clave)))
            borde = tk.Frame(rejilla, bg=C["blanco"], highlightthickness=1, highlightbackground=C["borde"])
            entrada = tk.Entry(borde, textvariable=var, width=7, font=(ui.FUENTE, 11), bg=C["blanco"], relief="flat", bd=0,
                               highlightthickness=0, justify="center")
            entrada.pack(padx=4, pady=3)
            borde.grid(row=fila, column=3 + j, sticky="w", padx=6, pady=2)
            entradas[clave] = (var, borde, entrada)

            def escrito(*_a, clave=clave, var=var, nc=e.num_cuenta, lbls=lbls):
                k = f"{clave[0]}|{clave[1]}"
                if var.get().strip() == guardado(*clave):
                    cambios.pop(k, None)
                else:
                    cambios[k] = var.get()
                recalcular(nc, lbls)
            var.trace_add("write", escrito)
            for tecla, paso in (("<Return>", 1), ("<Down>", 1), ("<Up>", -1)):
                entrada.bind(tecla, lambda ev_, i=i, j=j, paso=paso: mover(ev_, i, j, paso))
        lbls[0].grid(row=fila, column=3 + len(evals), sticky="w", padx=6)
        lbls[1].grid(row=fila, column=4 + len(evals), sticky="w", padx=6)
        recalcular(e.num_cuenta, lbls)

    if not alumnos:
        app.etiqueta(t, "No hay alumnos inscritos en esta materia.", 10, color=C["suave"]).pack(anchor="w")
    contador.pack(anchor="w", pady=(10, 0))

    def guardar():
        lote = []
        for k, texto in cambios.items():
            nc, eid = k.split("|")
            lote.append((nc, eid, None if not texto.strip() else _num(texto)))
        if not lote:
            messagebox.showinfo("Acta", "No hay cambios por guardar.")
            return
        borrar = sum(1 for _, _, x in lote if x is None)
        if borrar and not messagebox.askyesno("Borrar calificaciones", f"Dejaste vacías {borrar} calificaciones que ya estaban "
                                                                       "capturadas; se borrarán. ¿Continuar?"):
            return
        r = g.guardar_acta(lote)
        con_error = {f"{x['num_cuenta']}|{x['evaluacion_id']}" for x in r["errores"]}
        for k in list(cambios):
            if k not in con_error:
                del cambios[k]
        mensaje = f"Nuevas: {r['capturadas']}   ·   Modificadas: {r['modificadas']}   ·   Borradas: {r['borradas']}"
        if r["errores"]:
            detalle = "\n".join(f"{x['num_cuenta']} · {x['evaluacion_id']}: {legible(x['detalle'])}" for x in r["errores"][:8])
            messagebox.showwarning("Acta guardada con errores", f"{mensaje}\n\nNo se guardaron {len(r['errores'])}:\n{detalle}")
        else:
            messagebox.showinfo("Acta guardada", mensaje)

    def descartar():
        cambios.clear()
        app.refrescar()
    botones = tk.Frame(t, bg=C["blanco"])
    botones.pack(anchor="w", pady=(12, 0))
    app.boton(botones, "Guardar acta", guardar).pack(side="left")
    app.boton(botones, "Descartar cambios", descartar, primario=False).pack(side="left", padx=8)
    if alumnos:
        primera = entradas[(alumnos[0].num_cuenta, evals[0].id)][2]
        app.after(150, lambda: primera.winfo_exists() and primera.focus_set())


# ---------------------------------------------------------------- alumno
def _mi_resumen(app):
    return app.gestor.resumen_estudiante(app.usuario)


def _cuenta(cursos, estatus):
    return sum(1 for c in cursos if c["bruto"] is not None and c["estatus"] == estatus)


def _inicio_alumno(app):
    g, r = app.gestor, _mi_resumen(app)
    app.mosaicos(app.contenido, [("Promedio general", None if r["general"] is None else mostrar(r["general"])),
                                 ("Aprobadas", _cuenta(r["cursos"], "APROBADO")), ("Reprobadas", _cuenta(r["cursos"], "REPROBADO")),
                                 ("Pendientes", r["pendientes"])])
    riesgo = r["riesgo"]
    tonos = {"ALTO": (C["rojo_suave"], "#a32e34"), "MEDIO": (C["ambar_suave"], "#a26b34")}
    fondo, color = tonos.get(riesgo, (C["verde_suave"], "#247457"))
    banda = app.caja(app.contenido, radio=11, fondo=fondo, borde=fondo, padx=18, pady=14)
    banda.pack(fill="x", pady=(0, 14))
    app.etiqueta(banda, f"Riesgo académico: {legible(riesgo or 'PENDIENTE')}", 13, "bold", color=color).pack(anchor="w")
    app.etiqueta(banda, r["motivo"], 10, color=color).pack(anchor="w")
    t = app.tarjeta(app.contenido, "Mis materias", fill="x")
    tabla = Tabla(t, app, [("asignatura", "Asignatura", 220), ("docente", "Docente", 180), ("parcial_1", "P1", 60),
                           ("parcial_2", "P2", 60), ("parcial_3", "P3", 60), ("promedio", "Promedio", 80), ("final", "Final", 60),
                           ("estatus", "Estado", 100)],
                  alto=max(3, min(8, len(r["cursos"]))), ordenables=())
    tabla.pack(fill="both", expand=True)
    tabla.cargar([{"asignatura": c["asignatura"], "docente": (g.docente_de(c["curso_id"]).nombre if g.docente_de(c["curso_id"]) else "Por asignar"),
                   **{f"parcial_{i + 1}": None if x is None else mostrar(x) for i, x in enumerate((c["parciales"] + [None] * 3)[:3])},
                   "promedio": None if c["bruto"] is None else mostrar(c["bruto"]), "final": calificacion_final(c["bruto"]),
                   "estatus": c["estatus"]} for c in r["cursos"]])
    botones_pie(app, [("Ver desglose de calificaciones", "Mis calificaciones"), ("Ver mi kárdex", "Kárdex")])
    nota_pie(app, "Una materia se aprueba con promedio de 7.0 o más; la final se asienta entera (.5 sube) y un promedio menor "
                  "a 7.0 no se redondea hacia arriba. Riesgo ALTO: alguna materia reprobada o promedio menor a 7.0; "
                  "MEDIO: promedio entre 7.0 y 7.5.")


def mis_calificaciones(app):
    g, r = app.gestor, _mi_resumen(app)
    from . import documentos as D
    app.encabezado("Alumno  /  Mis calificaciones", "Mis calificaciones",
                   "Desglose por materia, parcial y evaluación. Solo lectura.")
    boleta = D.boleta(g, app.usuario, app.institucion, _emisor(app))
    t = app.tarjeta(app.contenido, "Boleta de calificaciones", "Descarga tu boleta del periodo con encabezado institucional.",
                    fill="x", pady=(0, 14))
    fila = tk.Frame(t, bg=C["blanco"])
    fila.pack(anchor="w")
    app.boton(fila, "Descargar boleta PDF", lambda: exportar_doc(app, boleta, "pdf")).pack(side="left")
    app.boton(fila, "CSV", lambda: exportar_doc(app, boleta, "csv"), primario=False).pack(side="left", padx=8)
    app.boton(fila, "JSON", lambda: exportar_doc(app, boleta, "json"), primario=False).pack(side="left")
    for c in r["cursos"]:
        final = ("Pendiente" if c["bruto"] is None else
                 f"{calificacion_final(c['bruto'])}  (promedio {fmt(mostrar(c['bruto']))}) · {legible(c['estatus'])}")
        t = app.tarjeta(app.contenido, f"{c['asignatura']}   ·   Final: {final}", fill="x", pady=(0, 14))
        filas = []
        for p in g.parciales_de(c["curso_id"]):
            nota_p = g.calif_parcial(app.usuario, p.id)
            for ev in g.evaluaciones_de(p.id):
                x = g.calificaciones.get((app.usuario, ev.id))
                filas.append({"parcial": f"Parcial {p.numero} · {_txt_pct(p.peso)} %", "evaluacion": f"{ev.nombre} · {_txt_pct(ev.peso)} %",
                              "calificacion": "Pendiente" if x is None else f"{x:.1f}",
                              "nota_parcial": None if nota_p is None else mostrar(nota_p)})
            if not g.evaluaciones_de(p.id):
                filas.append({"parcial": f"Parcial {p.numero} · {_txt_pct(p.peso)} %", "evaluacion": "Sin plan de evaluación",
                              "calificacion": "Pendiente", "nota_parcial": None})
        tabla = Tabla(t, app, [("parcial", "Parcial", 150), ("evaluacion", "Evaluación", 220), ("calificacion", "Calificación", 100),
                               ("nota_parcial", "Nota del parcial", 120)], alto=max(2, min(10, len(filas))), ordenables=())
        tabla.pack(fill="both", expand=True)
        tabla.cargar(filas)
    if not r["cursos"]:
        app.etiqueta(app.tarjeta(app.contenido, fill="x"), "No tienes materias inscritas.", 11, color=C["suave"]).pack(anchor="w")


def kardex(app):
    from . import documentos as D
    app.encabezado("Alumno  /  Kárdex", "Mi kárdex académico",
                   "Tu historial individual: materias cursadas, calificación final y situación académica.")
    doc = D.kardex(app.gestor, app.usuario, app.institucion, _emisor(app))
    botones_exportar(app, app.contenido, doc,
                     [("Boleta PDF", lambda: exportar_doc(app, D.boleta(app.gestor, app.usuario, app.institucion, _emisor(app)), "pdf"))])
    vista_documento(app, app.contenido, doc)


def _captura_varias(app, padre):
    g = app.gestor
    t = app.tarjeta(padre, "Captura de varias calificaciones", fill="x")
    ruta = tk.StringVar(value=app.filtros_guardados.get("archivo_varias", ""))
    fila = tk.Frame(t, bg=C["blanco"])
    fila.pack(fill="x", pady=6)
    caja = tk.Frame(fila, bg=C["blanco"])
    caja.pack(side="left")
    app.etiqueta(caja, "Archivo de calificaciones", 10, color=C["suave"]).pack(anchor="w")
    marco_ruta = app.caja(caja, radio=5, padx=9, pady=6)
    tk.Label(marco_ruta, text=Path(ruta.get()).name if ruta.get() else "Seleccionar archivo...", width=48, anchor="w",
             font=(ui.FUENTE, 11), bg=C["blanco"], fg=C["texto"] if ruta.get() else C["suave"]).pack(fill="x")
    marco_ruta.pack(anchor="w", pady=(3, 0))

    def elegir():
        r = filedialog.askopenfilename(filetypes=[("JSON", "*.json")])
        if r:
            app.filtros_guardados["archivo_varias"] = r
            app.refrescar()
    resultados = app.filtros_guardados.setdefault("resultados_varias", [])

    def procesar():
        if not ruta.get():
            messagebox.showwarning("Falta archivo", "Selecciona un archivo JSON y pulsa Cargar.")
            return
        try:
            datos = cargar_json(ruta.get())
        except Exception as e:  # noqa: BLE001 - se muestra al usuario
            messagebox.showerror("Archivo inválido", str(e))
            return
        resultados.clear()
        app._cargando = True
        for i, r in enumerate(datos.get("calificaciones", []), 1):
            antes = len(g.errores)
            ok = g._procesar("calificacion", r, g.capturar_calificacion)
            resultados.append({"no": i, "num_cuenta": str(r.get("num_cuenta", "—")), "evaluacion": r.get("evaluacion_id", "—"),
                               "valor": r.get("valor"), "estado": "Guardada" if ok else "Rechazada",
                               "detalle": "" if ok else f"{g.errores[antes]['codigo']}: {g.errores[antes]['detalle']}"})
        app._cargando = False
        app.registrar_actividad(f"procesamiento · {len(resultados)} recibidas")
        app.refrescar()
    app.boton(fila, "Cargar", elegir).pack(side="left", padx=(10, 8), anchor="s")
    app.boton(fila, "Procesar calificaciones", procesar).pack(side="left", anchor="s")
    app.etiqueta(t, "Se validan los registros antes de guardar las calificaciones.", 10, color=C["suave"]).pack(anchor="w", pady=(10, 2))
    app.etiqueta(t, "Los registros con errores se identifican sin detener el procesamiento de los demás.", 10,
                 color=C["suave"]).pack(anchor="w")
    r = app.tarjeta(padre, "Resultados de la captura", fill="x", pady=(14, 0))
    tabla = Tabla(r, app, [("no", "No.", 50), ("num_cuenta", "Estudiante", 110), ("evaluacion", "Evaluación", 100),
                           ("valor", "Calificación", 100), ("estado", "Estado", 100), ("detalle", "Detalle", 420)],
                  alto=10, ordenables=())
    tabla.pack(fill="both", expand=True)
    tabla.cargar(resultados)
    ok = sum(1 for x in resultados if x["estado"] == "Guardada")
    s = app.tarjeta(padre, "Resumen del procesamiento", fill="x", pady=(14, 0))
    n = len(resultados)
    app.etiqueta(s, f"Recibidos: {n or '—'}     Procesados: {ok if n else '—'}     Rechazados: {n - ok if n else '—'}",
                 11, "bold", color=app.acento).pack(anchor="w")


# ---------------------------------------------------------------- kárdex completo
def kardex_completo(app, cuenta, resumen=None, solo_materias=False):
    """Expediente del estudiante: datos, desglose por evaluación (desplegable por materia), resumen final y reportes.
    Con solo_materias (docente) se muestran únicamente sus materias, sin promedio general ni riesgo."""
    g = app.gestor
    r = resumen or g.resumen_estudiante(cuenta)
    e = g.estudiantes.get(cuenta)
    if not r or not e:
        app.etiqueta(app.tarjeta(app.contenido, fill="x"), "Estudiante sin información.", 11, color=C["suave"]).pack(anchor="w")
        return
    tonos = {"APROBADO": "#247457", "REPROBADO": "#a32e34"}
    datos = app.tarjeta(app.contenido, fill="x", pady=(0, 14))
    fila = fila_columnas(app, datos, 4)
    for i, (titulo, valor, color) in enumerate((("Número de cuenta", cuenta, None), ("Estudiante", f"{e.apellidos} {e.nombre}", None),
                                               ("Grupo", e.grupo, None),
                                               ("Materias contigo", str(len(r["cursos"])), None) if solo_materias else
                                               ("Estado académico", legible(r["estatus"]), tonos.get(r["estatus"], "#a26b34")))):
        celda = en_columna(tk.Frame(fila, bg=C["blanco"]), i, 4)
        app.etiqueta(celda, titulo, 10, color=C["suave"]).pack(anchor="w")
        app.etiqueta(celda, valor, 13, "bold", color=color).pack(anchor="w")
    app.mosaicos(app.contenido, ([] if solo_materias else [("Promedio general", None if r["general"] is None else mostrar(r["general"]))])
                 + [("Aprobadas", _cuenta(r["cursos"], "APROBADO")), ("Reprobadas", _cuenta(r["cursos"], "REPROBADO")),
                    ("Pendientes", sum(1 for c in r["cursos"] if c["bruto"] is None))])
    d = app.tarjeta(app.contenido, "Desglose por evaluación",
                    "Abre cada materia para ver sus parciales y evaluaciones con su peso.", fill="x")
    abiertas = app.filtros_guardados.setdefault("kardex_abiertas", set())
    for c in r["cursos"]:
        estado = "Pendiente de cierre" if c["bruto"] is None else ("Aprobado" if c["estatus"] == "APROBADO" else "Reprobado")
        abierta = (cuenta, c["curso_id"]) in abiertas
        caja = app.caja(d, radio=10, padx=14, pady=10, cursor="hand2")
        caja.pack(fill="x", pady=(0, 8))
        cab = tk.Frame(caja, bg=C["blanco"], cursor="hand2")
        cab.pack(fill="x")
        piezas = [app.etiqueta(cab, "⌄" if abierta else "›", 13, "bold", color=app.acento),
                  app.etiqueta(cab, c["curso_id"], 10, "bold", color=app.acento),
                  app.etiqueta(cab, c["asignatura"], 12, "bold")]
        for w in piezas:
            w.pack(side="left", padx=(0, 10))
        derecha = [app.etiqueta(cab, estado, 11, color=tonos.get(c["estatus"] if c["bruto"] is not None else "", "#a26b34")),
                   app.etiqueta(cab, f"Promedio: {fmt(None if c['bruto'] is None else mostrar(c['bruto']))}   ·   "
                                     f"Final: {fmt(calificacion_final(c['bruto']))}", 11, color=C["suave"])]
        for w in derecha:
            w.pack(side="right", padx=(10, 0))

        def alternar(_e=None, clave=(cuenta, c["curso_id"])):
            abiertas.symmetric_difference_update({clave})
            app.after_idle(app.refrescar)
        for w in [caja, caja.caja, cab] + piezas + derecha:
            w.bind("<Button-1>", alternar)
        if abierta:
            filas = []
            for p in g.parciales_de(c["curso_id"]):
                nota_p = g.calif_parcial(cuenta, p.id)
                for ev in g.evaluaciones_de(p.id):
                    x = g.calificaciones.get((cuenta, ev.id))
                    filas.append({"parcial": f"Parcial {p.numero} · {round(p.peso * 100)} %",
                                  "nota_parcial": None if nota_p is None else mostrar(nota_p),
                                  "evaluacion": f"{ev.nombre} · {round(ev.peso * 100)} %",
                                  "calificacion": "Pendiente" if x is None else f"{x:.1f}",
                                  "estado": "Pendiente" if x is None else "Capturada"})
            t = Tabla(caja, app, [("parcial", "Parcial", 140), ("nota_parcial", "Nota del parcial", 120),
                                  ("evaluacion", "Evaluación", 220), ("calificacion", "Calificación", 100), ("estado", "Estado", 100)],
                      alto=max(2, min(10, len(filas))), ordenables=())
            t.pack(fill="x", pady=(10, 0))
            t.cargar(filas)
    if not r["cursos"]:
        app.etiqueta(d, "Sin materias inscritas.", 10, color=C["suave"]).pack(anchor="w")
    if solo_materias:
        return
    res = app.tarjeta(app.contenido, "Resumen final", fill="x", pady=(14, 0))
    linea = tk.Frame(res, bg=C["blanco"])
    linea.pack(anchor="w")
    app.etiqueta(linea, "Promedio general: ", 12).pack(side="left")
    app.etiqueta(linea, fmt(None if r["general"] is None else mostrar(r["general"])), 12, "bold", color=app.acento).pack(side="left")
    app.etiqueta(linea, "     ·     Riesgo académico: ", 12).pack(side="left")
    app.etiqueta(linea, legible(r["riesgo"] or "PENDIENTE"), 12, "bold",
                 color={"ALTO": "#a32e34", "MEDIO": "#a26b34"}.get(r["riesgo"], "#247457")).pack(side="left")
    app.etiqueta(res, r.get("motivo") or "", 10, color=C["suave"]).pack(anchor="w", pady=(6, 10))
    from . import documentos as D
    botones_exportar(app, res, D.kardex(g, cuenta, app.institucion, _emisor(app)),
                     [("Boleta PDF", lambda: exportar_doc(app, D.boleta(g, cuenta, app.institucion, _emisor(app)), "pdf"))])


def consultas(app):
    cuenta = app.filtros_guardados.get("kardex_de")
    if cuenta:
        docente = app.rol == "Docente"
        app.encabezado(f"{ruta_de(app, 'Consultas')}  /  {'Alumno' if docente else 'Kárdex'}",
                       "Calificaciones del alumno en tus materias" if docente else "Expediente del estudiante",
                       "Solo se muestran las materias que impartes." if docente else
                       "Datos, desglose por evaluación, resumen final y reportes del alumno (kárdex y boleta).")
        resumen = next((r for r in resumenes_visibles(app) if r["num_cuenta"] == cuenta), None)
        kardex_completo(app, cuenta, resumen, solo_materias=docente)

        def volver():
            app.filtros_guardados.pop("kardex_de", None)
            app.refrescar()
        app.boton(app.contenido, "←   Volver a consultas", volver, primario=False).pack(anchor="w", pady=(14, 0))
        return
    app.encabezado(f"{app.rol}  /  Consultas", "Consultas y kárdex",
                   "Busca por estudiante, grupo o asignatura; ordena por promedio y revisa el riesgo académico.")
    pestanas = Pestanas(app.contenido, app, app.vista)
    pestanas.pack(fill="x")
    tabs = [tk.Frame(pestanas, bg=C["fondo"], pady=12) for _ in range(3)]
    for f, texto in zip(tabs, ("Estudiantes y kárdex", "Promedios y riesgo académico", "Estadísticas por curso")):
        pestanas.add(f, text=texto)
    resumenes = resumenes_visibles(app)
    _consulta_estudiantes(app, tabs[0], resumenes)
    _consulta_riesgo(app, tabs[1], resumenes)
    _consulta_estadisticas(app, tabs[2], resumenes)


def _consulta_estudiantes(app, padre, resumenes):
    filas_curso = reportes.filas_por_curso(resumenes)
    guardados = app.filtros_guardados.setdefault("consultas", {}) if app.opciones["recordar"].get() else {}
    f = {k: tk.StringVar(value=guardados.get(k, d)) for k, d in
         (("estudiante", app.parametros.get("buscar", "")), ("grupo", "Todos"), ("asignatura", "Todas"),
          ("estado", "todos"), ("minima", ""), ("maxima", ""))}
    if app.parametros.get("buscar"):
        f["estudiante"].set(app.parametros["buscar"])
    t = app.tarjeta(padre, "Buscar y filtrar", fill="x")
    fila = tk.Frame(t, bg=C["blanco"])
    fila.pack(fill="x")
    app.campo(fila, "Estudiante (cuenta o nombre)", f["estudiante"], 20).pack(side="left", padx=(0, 10))
    app.campo(fila, "Grupo", f["grupo"], 8, ["Todos"] + sorted({x["grupo"] for x in filas_curso})).pack(side="left", padx=10)
    app.campo(fila, "Asignatura", f["asignatura"], 24, ["Todas"] + sorted({x["asignatura"] for x in filas_curso})).pack(side="left", padx=10)
    app.campo(fila, "Estado", f["estado"], 11, ["todos", "aprobado", "reprobado", "pendiente", "riesgo"]).pack(side="left", padx=10)
    app.campo(fila, "Mínima", f["minima"], 6).pack(side="left", padx=10)
    app.campo(fila, "Máxima", f["maxima"], 6).pack(side="left", padx=10)
    ttk.Checkbutton(fila, text="Colorear estados", variable=app.opciones["colorear"],
                    command=lambda: aplicar()).pack(side="left", padx=10, pady=(18, 0))
    lista = app.tarjeta(padre, "Estudiantes por asignatura", fill="x", pady=(14, 0))
    tabla = Tabla(lista, app, [("num_cuenta", "Núm. de cuenta", 110), ("apellidos", "Apellidos", 160),
                               ("nombre", "Nombre", 100), ("grupo", "Grupo", 60), ("asignatura", "Asignatura", 180),
                               ("parcial_1", "P1", 60), ("parcial_2", "P2", 60), ("parcial_3", "P3", 60),
                               ("promedio", "Promedio", 80), ("estatus", "Estado", 100)], alto=10,
                  al_seleccionar=lambda fila: fila and abrir_kardex(fila["num_cuenta"]))
    tabla.pack(fill="both", expand=True)
    app.etiqueta(lista, "Haz clic en un estudiante para abrir su kárdex.", 10, color=C["suave"]).pack(anchor="w", pady=(8, 0))

    def abrir_kardex(cuenta):
        app.filtros_guardados["kardex_de"] = cuenta
        app.after_idle(app.refrescar)
    riesgo_por = {r["num_cuenta"]: r for r in resumenes}

    def aplicar(*_):
        for k, var in f.items():
            guardados[k] = var.get()
        texto = f["estudiante"].get().strip().lower()

        def num(s):
            try:
                return float(s)
            except ValueError:
                return None
        pred_estado = filtro_estado(f["estado"].get())
        pred_rango = filtro_rango(num(f["minima"].get()), num(f["maxima"].get()))
        visibles = [x for x in filas_curso
                    if (not texto or texto in x["num_cuenta"] or texto in f"{x['apellidos']} {x['nombre']}".lower())
                    and f["grupo"].get() in ("Todos", x["grupo"]) and f["asignatura"].get() in ("Todas", x["asignatura"])
                    and pred_estado({**x, "riesgo": riesgo_por[x["num_cuenta"]]["riesgo"]}) and pred_rango(x)]
        tabla.cargar(visibles)

    for var in f.values():
        var.trace_add("write", aplicar)
    aplicar()


def _consulta_riesgo(app, padre, resumenes):
    cursos = app.cursos_visibles()
    prom = app.tarjeta(padre, "Promedio del curso", fill="x")
    curso = tk.StringVar(value=f"{cursos[0].id} · {cursos[0].asignatura}" if cursos else "")
    resultado = app.etiqueta(prom, "", 14, "bold", color=app.acento)

    def consultar():
        cid = curso.get().split(" · ")[0]
        est = estadisticas([c["bruto"] for r in resumenes for c in r["cursos"] if c["curso_id"] == cid and c["bruto"] is not None])
        resultado.config(text=f"Promedio del curso: {fmt(est['promedio'])}   ·   Máx {fmt(est['maximo'])}   ·   "
                         f"Mín {fmt(est['minimo'])}   ·   Aprobación {fmt(est['aprobacion'])} %")
    app.campo(prom, "Curso", curso, 34, [f"{c.id} · {c.asignatura}" for c in cursos], consultar).pack(anchor="w")
    resultado.pack(anchor="w", pady=10)
    consultar()
    t = app.tarjeta(padre, "Promedio general por estudiante", fill="x", pady=(14, 0))
    tabla = Tabla(t, app, [("num_cuenta", "Número de cuenta", 120), ("apellidos", "Apellidos", 180), ("nombre", "Nombre", 120),
                           ("grupo", "Grupo", 70), ("promedio", "Promedio", 90), ("estatus", "Estado", 110),
                           ("riesgo", "Riesgo", 110)], alto=8)
    tabla.pack(fill="both", expand=True)
    filas = [reportes.fila_estudiante(r) for r in resumenes]
    tabla.orden, tabla.desc = "promedio", True
    tabla.cargar(filas)

    r = app.tarjeta(padre, "Estudiantes en riesgo académico",
                    "ALTO: algún curso reprobado o promedio general < 7.0 · MEDIO: promedio general entre 7.0 y 7.5.",
                    fill="x", pady=(14, 0))
    tr = Tabla(r, app, [("num_cuenta", "Número de cuenta", 120), ("apellidos", "Apellidos", 180), ("nombre", "Nombre", 120),
                        ("grupo", "Grupo", 70), ("promedio", "Promedio", 90), ("riesgo", "Riesgo", 90),
                        ("motivo", "Motivo", 260)], alto=8)
    tr.pack(fill="both", expand=True)
    tr.cargar([f_ for f_ in filas if f_["riesgo"] in ("ALTO", "MEDIO")])


def _consulta_estadisticas(app, padre, resumenes):
    filas = [f for f in reportes.estadisticas_cursos(app.gestor, resumenes)
             if f["curso_id"] in {c.id for c in app.cursos_visibles()}]
    cerrados = [c["bruto"] for r in resumenes for c in r["cursos"] if c["bruto"] is not None]
    est = estadisticas(cerrados)
    app.mosaicos(padre, [("Promedio de cursos", est["promedio"]), ("Calificación máxima", est["maximo"]),
                         ("Calificación mínima", est["minimo"]), ("Aprobación (%)", est["aprobacion"])])
    t = app.tarjeta(padre, "Resultados agrupados por asignatura y grupo", fill="x")
    tabla = Tabla(t, app, [("curso_id", "Curso", 70), ("asignatura", "Asignatura", 200), ("grupo", "Grupo", 60),
                           ("docente", "Docente", 150), ("inscritos", "Estudiantes", 90), ("promedio", "Promedio", 80),
                           ("maximo", "Máx.", 70), ("minimo", "Mín.", 70), ("aprobacion", "% aprobado", 90)],
                  alto=10, ordenables=())
    tabla.pack(fill="both", expand=True)
    tabla.cargar(filas)


# ---------------------------------------------------------------- reportes institucionales
ANCHOS = {"asignatura": 3, "alumno": 3, "docente": 2, "motivo": 3, "reprobadas": 3}


def vista_documento(app, padre, doc):
    """El reporte como se ve impreso: encabezado institucional, datos del alcance, tabla, resumen, firmas y nota."""
    enc = doc["encabezado"]
    hoja = app.caja(padre, radio=10, padx=0, pady=0)
    hoja.pack(fill="x", pady=(14, 0))
    cab = tk.Frame(hoja, bg=app.acento_suave, padx=24, pady=16)
    cab.pack(fill="x")
    arriba = tk.Frame(cab, bg=app.acento_suave)
    arriba.pack(fill="x")
    izq = tk.Frame(arriba, bg=app.acento_suave)
    izq.pack(side="left")
    app.etiqueta(izq, enc["institucion"], 14, "bold").pack(anchor="w")
    app.etiqueta(izq, enc["programa"], 10, color=C["suave"]).pack(anchor="w")
    der = tk.Frame(arriba, bg=app.acento_suave)
    der.pack(side="right")
    app.etiqueta(der, "CICLO ESCOLAR", 9, "bold", color=C["suave"]).pack(anchor="e")
    app.etiqueta(der, enc["ciclo"], 11, "bold").pack(anchor="e")
    app.etiqueta(cab, enc["titulo"].upper(), 16, "bold", color=app.acento).pack(anchor="w", pady=(12, 8))
    campos = tk.Frame(cab, bg=app.acento_suave)
    campos.pack(fill="x")
    for i, (etiqueta, valor) in enumerate(enc["campos"]):
        celda = tk.Frame(campos, bg=app.acento_suave)
        celda.grid(row=i // 3, column=i % 3, sticky="w", padx=(0, 40), pady=2)
        app.etiqueta(celda, etiqueta, 9, color=C["suave"]).pack(anchor="w")
        app.etiqueta(celda, fmt(valor), 11, "bold").pack(anchor="w")
    app.etiqueta(cab, f"Emitido el {enc['emision']}" + (f" por {enc['emitido_por']}" if enc["emitido_por"] else ""), 9,
                 color=C["suave"]).pack(anchor="e", pady=(8, 0))
    cuerpo = tk.Frame(hoja, bg=C["blanco"], padx=24, pady=16)
    cuerpo.pack(fill="x")
    tabla = Tabla(cuerpo, app, [(k, t, ANCHOS.get(k, 1)) for k, t in doc["columnas"]],
                  alto=max(3, min(14, len(doc["filas"]))), ordenables=())
    tabla.orden = None  # se respeta el orden del reporte
    tabla.pack(fill="both", expand=True)
    tabla.cargar(doc["filas"])
    resumen = tk.Frame(cuerpo, bg=C["blanco"])
    resumen.pack(fill="x", pady=(14, 0))
    for i, (etiqueta, valor) in enumerate(doc["resumen"]):
        caja = app.caja(resumen, radio=8, fondo=C["campo"], borde=C["borde"], padx=12, pady=8)
        caja.grid(row=0, column=i, sticky="nsew", padx=(0 if i == 0 else 8, 0))
        resumen.columnconfigure(i, weight=1)
        app.etiqueta(caja, etiqueta, 9, color=C["suave"]).pack(anchor="w")
        app.etiqueta(caja, fmt(valor), 12, "bold", color=app.acento).pack(anchor="w")
    if doc["firmas"]:
        firmas = tk.Frame(cuerpo, bg=C["blanco"])
        firmas.pack(fill="x", pady=(30, 0))
        for i, f in enumerate(doc["firmas"]):
            celda = tk.Frame(firmas, bg=C["blanco"])
            celda.grid(row=0, column=i, padx=30, sticky="ew")
            firmas.columnconfigure(i, weight=1)
            tk.Frame(celda, bg=C["texto"], height=1, width=240).pack(fill="x")
            app.etiqueta(celda, f, 10, color=C["suave"]).pack(pady=(4, 0))
    if doc["nota"]:
        app.etiqueta(cuerpo, doc["nota"], 9, color=C["suave"], wraplength=1000).pack(anchor="w", pady=(14, 0))


def exportar_doc(app, doc, formato):
    from .archivos import exportar_documento
    from .documentos import nombre_archivo
    ruta = filedialog.asksaveasfilename(title=f"Exportar {doc['titulo']}", defaultextension=f".{formato}",
                                        initialfile=f"{nombre_archivo(doc)}.{formato}",
                                        filetypes=[(formato.upper(), f"*.{formato}")])
    if not ruta:
        return
    exportar_documento(doc, ruta, formato, app.acento)
    app.registrar_actividad(f"reporte_exportado · {Path(ruta).name}")
    messagebox.showinfo("Reporte exportado", f"{doc['titulo']}\n{ruta}")


def botones_exportar(app, padre, doc, extra=()):
    fila = tk.Frame(padre, bg=fondo_de(padre))
    fila.pack(anchor="w", pady=(12, 0))
    app.boton(fila, "Exportar PDF", lambda: exportar_doc(app, doc, "pdf")).pack(side="left")
    app.boton(fila, "CSV", lambda: exportar_doc(app, doc, "csv"), primario=False).pack(side="left", padx=8)
    app.boton(fila, "JSON", lambda: exportar_doc(app, doc, "json"), primario=False).pack(side="left", padx=(0, 8))
    for texto, accion in extra:
        app.boton(fila, texto, accion, primario=False).pack(side="left", padx=(0, 8))


def _emisor(app):
    return app.cuenta["usuario"] if app.cuenta else ""


def reportes_vista(app):
    from . import documentos as D
    g = app.gestor
    tipos = [t for t, (_, _, roles) in D.CATALOGO.items() if app.rol in roles]
    titulos = [D.CATALOGO[t][0] for t in tipos]
    f = app.filtros_guardados.setdefault("reporte", {"tipo": tipos[0], "grupo": "", "alumno": "", "materia": "",
                                                     "orden": "Por apellidos", "estado": "Todos", "usuarios": "Docentes"})
    f.setdefault("usuarios", "Docentes")
    if f["tipo"] not in tipos:
        f["tipo"] = tipos[0]
    titulo, alcance, _ = D.CATALOGO[f["tipo"]]
    app.encabezado(ruta_de(app, "Reportes"), "Reportes",
                   "Documentos con encabezado institucional: por alumno, por materia, por grupo y de control escolar.")
    cursos = app.cursos_visibles()
    grupos = sorted({c.grupo for c in cursos}) if app.rol == "Docente" else g.grupos()
    t = app.tarjeta(app.contenido, "Generar reporte", fill="x")
    fila = tk.Frame(t, bg=C["blanco"])
    fila.pack(fill="x")
    v = {k: tk.StringVar(value=f[k]) for k in f}

    def cambiar():
        f.update({k: x.get() for k, x in v.items()})
        f["tipo"] = tipos[titulos.index(v["titulo"].get())]
        app.refrescar()
    v["titulo"] = tk.StringVar(value=titulo)
    app.campo(fila, "Reporte", v["titulo"], 36, titulos, cambiar).pack(side="left", padx=(0, 12))

    def elegir(clave, opciones, todos=None):
        opciones = ([todos] if todos else []) + opciones
        if v[clave].get() not in opciones:
            v[clave].set(opciones[0] if opciones else "")
            f[clave] = v[clave].get()
        return opciones
    materias = [f"{c.id} · {c.asignatura} · {c.grupo}" for c in sorted(cursos, key=lambda c: (c.grupo, c.asignatura))]
    doc, extra = None, ()
    if alcance == "alumno":
        app.campo(fila, "Grupo", v["grupo"], 8, elegir("grupo", grupos) or ["—"], cambiar).pack(side="left", padx=(0, 12))
        alumnos = sorted((e for e in g.estudiantes.values() if e.grupo == v["grupo"].get()), key=lambda e: e.nombre_completo)
        app.campo(fila, "Alumno", v["alumno"], 30, elegir("alumno", [f"{e.num_cuenta} · {e.nombre_completo}" for e in alumnos]) or ["—"],
                  cambiar).pack(side="left")
        nc = v["alumno"].get().split(" · ")[0]
        if nc in g.estudiantes:
            doc = getattr(D, f["tipo"])(g, nc, app.institucion, _emisor(app))
            extra = [(f"PDF de todo el grupo {v['grupo'].get()}", lambda: _exportar_por_alumno(app, f["tipo"], alumnos))]
    elif alcance in ("materia", "materia_opcional"):
        app.campo(fila, "Materia", v["materia"], 34, elegir("materia", materias, "Todas" if alcance == "materia_opcional" else None)
                  or ["—"], cambiar).pack(side="left", padx=(0, 12))
        cid = v["materia"].get().split(" · ")[0]
        if alcance == "materia" and cid in g.cursos:
            doc = D.acta(g, cid, app.institucion, _emisor(app))
        elif alcance == "materia_opcional":
            app.campo(fila, "Estado", v["estado"], 12, ["Todos", "Aprobado", "Reprobado", "Pendiente"], cambiar).pack(side="left")
            doc = D.aprobacion(g, cursos, app.institucion, _emisor(app), None if cid == "Todas" else cid, v["estado"].get())
    elif alcance == "usuarios":
        app.campo(fila, "Usuarios", v["usuarios"], 14, elegir("usuarios", ["Docentes"] + [f"Grupo {x}" for x in grupos] + ["Todos"]),
                  cambiar).pack(side="left")
        doc = D.credenciales(g, app.usuarios, v["usuarios"].get(), app.institucion, _emisor(app))
    else:
        opciones = elegir("grupo", grupos, "Todos" if alcance == "grupo_opcional" else None)
        app.campo(fila, "Grupo", v["grupo"], 8, opciones or ["—"], cambiar).pack(side="left", padx=(0, 12))
        grupo = None if v["grupo"].get() in ("Todos", "") else v["grupo"].get()
        if f["tipo"] == "concentrado":
            app.campo(fila, "Orden", v["orden"], 14, ["Por apellidos", "Por promedio"], cambiar).pack(side="left")
            if grupo:
                doc = D.concentrado(g, grupo, app.institucion, _emisor(app), "promedio" if v["orden"].get() == "Por promedio" else "apellidos")
        elif f["tipo"] == "aprovechamiento":
            doc = D.aprovechamiento(g, cursos, app.institucion, _emisor(app), grupo)
        else:
            doc = D.riesgo(g, app.institucion, _emisor(app), grupo)
    if doc is None:
        app.etiqueta(t, "No hay información para este reporte.", 11, color=C["suave"]).pack(anchor="w", pady=(10, 0))
        return
    botones_exportar(app, t, doc, extra)
    vista_documento(app, app.contenido, doc)


def _exportar_por_alumno(app, tipo, alumnos):
    from . import documentos as D
    from .archivos import exportar_documento
    carpeta = filedialog.askdirectory(title="Carpeta de destino")
    if not carpeta:
        return
    for e in alumnos:
        doc = getattr(D, tipo)(app.gestor, e.num_cuenta, app.institucion, _emisor(app))
        exportar_documento(doc, Path(carpeta) / f"{D.nombre_archivo(doc)}.pdf", "pdf", app.acento)
    app.registrar_actividad(f"reportes_exportados · {len(alumnos)} {tipo}")
    messagebox.showinfo("Reportes exportados", f"Se generó un {D.CATALOGO[tipo][0].lower()} en PDF por cada uno de los "
                                               f"{len(alumnos)} alumnos en:\n{carpeta}")


def _datos_institucion(app):
    t = app.tarjeta(app.contenido, "Datos del encabezado de los reportes",
                    "Aparecen en la boleta, el kárdex, las actas y los demás reportes.", fill="x", pady=(14, 0))
    v = {k: tk.StringVar(value=app.institucion.get(k, "")) for k in ("institucion", "programa", "ciclo")}
    fila = fila_columnas(app, t, 3)
    for i, (clave, texto) in enumerate((("institucion", "Institución"), ("programa", "Programa educativo"),
                                        ("ciclo", "Ciclo escolar"))):
        en_columna(app.campo(fila, texto, v[clave], 34), i, 3)

    def guardar():
        if not all(x.get().strip() for x in v.values()):
            messagebox.showwarning("Faltan datos", "Llena institución, programa y ciclo escolar.")
            return
        app.institucion.update({k: x.get().strip() for k, x in v.items()})
        app.guardar_ahora()
        app.registrar_actividad("institucion_actualizada · encabezado de reportes")
        messagebox.showinfo("Datos guardados", "Los reportes usarán estos datos en su encabezado.")
    app.boton(t, "Guardar datos", guardar).pack(anchor="w", pady=(12, 0))


def personalizacion(app):
    app.encabezado(f"{'Inicio' if app.rol == 'Administrador' else app.rol}  /  Personalización", "Personalización",
                   "Ajusta la apariencia")
    pendiente = app.filtros_guardados.get("tema_pendiente") or app.tema
    fila = tk.Frame(app.contenido, bg=C["fondo"])
    fila.pack(fill="x")
    fila.columnconfigure(0, weight=2, uniform="p")
    fila.columnconfigure(1, weight=1, uniform="p")
    t = app.tarjeta(fila, "Apariencia por institución")
    t.grid(row=0, column=0, sticky="nsew", padx=(0, 7))
    for i, (nombre, (fuerte, _suave)) in enumerate(TEMAS.items()):
        f = tk.Frame(t, bg=C["blanco"])
        f.pack(fill="x", pady=6)
        m = tk.Canvas(f, width=28, height=28, bg=C["blanco"], highlightthickness=0)
        ui.rectangulo_redondo(m, 0, 0, 27, 27, 7, fill=fuerte, outline=fuerte)
        m.pack(side="left", padx=(0, 12))
        textos = tk.Frame(f, bg=C["blanco"])
        textos.pack(side="left", fill="x", expand=True)
        app.etiqueta(textos, nombre, 12, "bold").pack(anchor="w")
        app.etiqueta(textos, "Predeterminado" if i == 0 else "Tema alternativo", 10, color=C["suave"]).pack(anchor="w")
        elegido = nombre == pendiente
        app.boton(f, "● Seleccionado" if elegido else "○ Elegir",
                  lambda n=nombre: (app.filtros_guardados.__setitem__("tema_pendiente", n), app.refrescar()),
                  primario=False).pack(side="right")

    def guardar():
        app.tema = pendiente
        app.filtros_guardados.pop("tema_pendiente", None)
        app.construir_marco()
    app.boton(t, "Guardar apariencia", guardar).pack(anchor="w", pady=(12, 0))
    o = app.tarjeta(fila, "Opciones de configuración")
    o.grid(row=0, column=1, sticky="nsew", padx=(7, 0))
    for clave, texto in (("colorear", "Resaltar aprobados y reprobados"), ("avisos", "Mostrar eventos de validación"),
                         ("recordar", "Recordar filtros seleccionados")):
        ttk.Checkbutton(o, text=texto, variable=app.opciones[clave]).pack(anchor="w", pady=3)
    app.etiqueta(o, "La personalización solo cambia la apariencia.", 10, color=C["suave"]).pack(anchor="w", pady=(20, 0))
    if app.rol == "Administrador":
        _datos_institucion(app)
    fuerte = TEMAS[pendiente][0]
    p = app.tarjeta(app.contenido, "Vista previa", fill="x", pady=(14, 0))
    marco = app.caja(p, radio=10, padx=0, pady=0)
    marco.pack(fill="x")
    tk.Label(marco, text="Sistema académico", font=(ui.FUENTE, 12, "bold"), fg="white", bg=fuerte, anchor="w",
             padx=16, pady=10).pack(fill="x")
    cuerpo = tk.Frame(marco, bg=C["blanco"])
    cuerpo.pack(fill="x")
    nav = tk.Frame(cuerpo, bg=C["blanco"], padx=16, pady=12)
    nav.pack(side="left", fill="y")
    for i, n in enumerate(("Registro", "Captura", "Reportes")):
        app.etiqueta(nav, n, 11, "bold" if i == 0 else "normal", color=fuerte if i == 0 else C["texto"]).pack(anchor="w", pady=3)
    tk.Frame(cuerpo, bg=C["borde"], width=1).pack(side="left", fill="y")
    cont = tk.Frame(cuerpo, bg=C["blanco"], padx=16, pady=12)
    cont.pack(side="left", fill="both", expand=True)
    app.etiqueta(cont, "Datos escolares", 12, "bold").pack(anchor="w")
    app.etiqueta(cont, "El tema cambia la apariencia, no la estructura de la información.", 10, color=C["suave"]).pack(anchor="w")


# ---------------------------------------------------------------- docente
def mis_cursos(app):
    app.encabezado("Docente  /  Cursos", "Mis cursos asignados",
                   "Consulta los grupos y asignaturas a tu cargo. Solo puedes capturar notas de tus cursos.")
    g = app.gestor
    cursos = app.cursos_visibles()
    resumenes = resumenes_visibles(app)
    pendientes = sum(1 for c in cursos for (n, cid) in g.inscripciones if cid == c.id
                     for p in g.parciales_de(c.id) for e in g.evaluaciones_de(p.id) if (n, e.id) not in g.calificaciones)
    app.mosaicos(app.contenido, [("Cursos asignados", len(cursos)), ("Grupos activos", len({c.grupo for c in cursos})),
                                 ("Evaluaciones pendientes", pendientes), ("Estudiantes", len(resumenes))])
    t = app.tarjeta(app.contenido, fill="x")
    tabla = Tabla(t, app, [("curso_id", "Curso", 80), ("asignatura", "Asignatura", 240), ("grupo", "Grupo", 70),
                           ("inscritos", "Inscritos", 90), ("promedio", "Promedio", 90), ("estado", "Estado", 100)],
                  alto=max(3, min(8, len(cursos))), ordenables=())
    tabla.pack(fill="both", expand=True)
    est = {f["curso_id"]: f for f in reportes.estadisticas_cursos(g, resumenes)}
    tabla.cargar([{"curso_id": c.id, "asignatura": c.asignatura, "grupo": c.grupo,
                   "inscritos": est.get(c.id, {}).get("inscritos", 0), "promedio": est.get(c.id, {}).get("promedio"),
                   "estado": "Asignado"} for c in cursos])
    botones_pie(app, [("Plan de evaluación", "Plan de evaluación"), ("Capturar calificaciones", "Captura"),
                      ("Promedios y riesgo", "Consultas:1")])
    nota_pie(app, "La asignación de cursos se determina por el administrador.")


# ---------------------------------------------------------------- alumno
VISTAS = {
    ("Administrador", "Inicio"): inicio, ("Docente", "Inicio"): inicio, ("Alumno", "Inicio"): inicio,
    ("Administrador", "Estudiantes"): estudiantes, ("Administrador", "Grupos y materias"): materias,
    ("Administrador", "Docentes"): docentes, ("Administrador", "Inscripciones"): inscripciones,
    ("Administrador", "Consultas"): consultas, ("Docente", "Consultas"): consultas,
    ("Administrador", "Reportes"): reportes_vista, ("Docente", "Reportes"): reportes_vista,
    ("Administrador", "Usuarios"): usuarios,
    ("Docente", "Mis cursos"): mis_cursos, ("Docente", "Plan de evaluación"): plan_evaluacion, ("Docente", "Captura"): captura,
    ("Alumno", "Mis calificaciones"): mis_calificaciones, ("Alumno", "Kárdex"): kardex,
    ("Administrador", "Personalización"): personalizacion, ("Docente", "Personalización"): personalizacion,
    ("Alumno", "Personalización"): personalizacion,
    ("Administrador", "Mi cuenta"): mi_cuenta, ("Docente", "Mi cuenta"): mi_cuenta, ("Alumno", "Mi cuenta"): mi_cuenta,
}
