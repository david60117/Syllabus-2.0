"""Interfaz Tkinter de Syllabus 2.0 (diseño de Figma "Syllabus 2.0 (Copy)").

Paradigma orientado a eventos: cada botón, filtro o clic dispara un callback, y el gestor
publica eventos (estudiante_registrado, calificacion_capturada, error_validacion...) a los
que la ventana está suscrita para refrescar la vista y el registro de actividad.
"""
import re
import tkinter as tk
from datetime import datetime
import tkinter.font  # noqa: F401  (tk.font)
from decimal import Decimal
from pathlib import Path
from tkinter import messagebox, ttk

from . import iconos, persistencia
from .archivos import cargar_json
from .autenticacion import cargar_usuarios, sincronizar
from .documentos import INSTITUCION
from .calculos import ordenar
from .errores import ErrorValidacion
from .eventos import BusEventos
from .gestor import GestorEscolar

RAIZ = Path(__file__).resolve().parents[2]

TEMAS = {
    "Rojo": ("#8b2024", "#f9eeea"),
    "Verde": ("#227e68", "#e6f2ef"),
    "Azul": ("#3b85df", "#eaf2fc"),
    "Morado": ("#7655be", "#f0ecf9"),
}
C = {"fondo": "#fcfaf8", "blanco": "#ffffff", "borde": "#e9dfd9", "texto": "#2c2426", "suave": "#766e6a",
     "campo": "#fbfaf9", "verde": "#2e6b3f", "verde_suave": "#e6f1e9", "rojo": "#a32a2a", "rojo_suave": "#f8e4e4",
     "ambar_suave": "#fbf1dc"}
FUENTE = "Helvetica"  # se reemplaza al crear la ventana: Inter si está instalada, si no la fuente del sistema


def elegir_fuente(raiz):
    """Inter (la fuente de Figma) si está instalada; si no, la del sistema: San Francisco en macOS,
    Segoe UI en Windows."""
    global FUENTE
    familias = set(tk.font.families(raiz))
    if "Inter" in familias:
        FUENTE = "Inter"
    else:
        FUENTE = tk.font.nametofont("TkDefaultFont", root=raiz).actual("family")
    for nombre in ("TkDefaultFont", "TkTextFont", "TkMenuFont", "TkHeadingFont"):
        tk.font.nametofont(nombre, root=raiz).configure(family=FUENTE)
    return FUENTE

MENUS = {  # (ícono Lucide, sección)
    "Administrador": [("inicio", "Inicio"), ("estudiantes", "Estudiantes"), ("grupos", "Grupos y materias"),
                      ("docentes", "Docentes"), ("inscripciones", "Inscripciones"), ("consultas", "Consultas"),
                      ("reportes", "Reportes"), ("usuarios", "Usuarios"), ("ajustes", "Personalización")],
    "Docente": [("inicio", "Inicio"), ("cursos", "Mis cursos"), ("plan", "Plan de evaluación"), ("captura", "Captura"),
                ("consultas", "Consultas"), ("reportes", "Reportes"), ("ajustes", "Personalización")],
    "Alumno": [("inicio", "Inicio"), ("calificaciones", "Mis calificaciones"), ("kardex", "Kárdex"), ("ajustes", "Personalización")],
}


def legible(texto) -> str:
    """Texto sin guiones bajos para mostrar: "SIN_RIESGO" → "Sin riesgo", "datos_cargados" → "Datos cargados"."""
    def cambiar(m):
        s = m.group(0).replace("_", " ").lower()
        return s[0].upper() + s[1:]
    return re.sub(r"\b[A-Za-z]+(?:_[A-Za-z0-9]+)+\b", cambiar, str(texto if texto is not None else ""))


NOMBRES_REPORTE = {"resumen_grupos": "Resumen por grupo", "estudiantes": "Estudiantes",
                   "parciales_por_asignatura": "Parciales por asignatura", "estadisticas_cursos": "Estadísticas por curso",
                   "riesgo_academico": "Riesgo académico", "kardex": "Kárdex"}


def fmt(x) -> str:
    if x is None:
        return "—"
    if isinstance(x, (Decimal, float)):
        return f"{x:.2f}"
    return str(x)



# ---------------------------------------------------------------- esquinas redondeadas
def fondo_de(widget):
    """Color de fondo del contenedor (los ttk.Frame no tienen opción bg: son tarjetas blancas)."""
    try:
        return widget.cget("bg")
    except tk.TclError:
        return C["blanco"]


def rectangulo_redondo(lienzo, x1, y1, x2, y2, r, fill="", outline="", width=1, tags=()):
    """Rectángulo con esquinas redondeadas dibujado con rectángulos, arcos y líneas rectas
    (un polígono suavizado ondula en los lados rectos)."""
    x1, y1, x2, y2 = round(x1), round(y1), round(x2), round(y2)
    r = int(max(0, min(r, (x2 - x1) / 2, (y2 - y1) / 2)))
    d = 2 * r
    if fill:
        lienzo.create_rectangle(x1 + r, y1, x2 - r, y2, fill=fill, outline="", tags=tags)
        lienzo.create_rectangle(x1, y1 + r, x2, y2 - r, fill=fill, outline="", tags=tags)
        for x, y in ((x1, y1), (x2 - d, y1), (x1, y2 - d), (x2 - d, y2 - d)):
            lienzo.create_oval(x, y, x + d, y + d, fill=fill, outline="", tags=tags)
    if outline:
        for inicio, x, y in ((90, x1, y1), (0, x2 - d, y1), (180, x1, y2 - d), (270, x2 - d, y2 - d)):
            lienzo.create_arc(x, y, x + d, y + d, start=inicio, extent=90, style="arc", outline=outline,
                              width=width, tags=tags)
        for coords in ((x1 + r, y1, x2 - r, y1), (x1 + r, y2, x2 - r, y2), (x1, y1 + r, x1, y2 - r), (x2, y1 + r, x2, y2 - r)):
            lienzo.create_line(*coords, fill=outline, width=width, tags=tags)


class Interior(tk.Frame):
    """Contenido de una Caja. pack/grid/place se aplican a la caja que lo dibuja."""

    def __init__(self, caja, **kw):
        super().__init__(caja, **kw)
        self.caja = caja

    def pack(self, **kw):
        self.caja.pack(**kw)
        return self

    def grid(self, **kw):
        self.caja.grid(**kw)
        return self

    def place(self, **kw):
        self.caja.place(**kw)
        return self

    pack_configure, grid_configure, place_configure = pack, grid, place

    def pack_forget(self):
        self.caja.pack_forget()

    def destroy(self):
        caja = self.caja
        super().destroy()
        if not caja.destruyendo:
            caja.destroy()


class Caja(tk.Canvas):
    """Contenedor con fondo y borde de esquinas redondeadas (tarjetas, campos, botones, menú)."""

    def __init__(self, padre, radio=10, fondo=None, borde=None, grosor=1, padx=0, pady=0, cursor=""):
        super().__init__(padre, bg=fondo_de(padre), highlightthickness=0, bd=0, width=10, height=10, cursor=cursor)
        self.destruyendo = False
        self.radio, self.fondo, self.grosor = radio, fondo or C["blanco"], grosor
        self.borde = borde or self.fondo
        self.margen = int(radio * 0.3) + grosor + 1
        self.interior = Interior(self, bg=self.fondo, padx=max(0, padx - self.margen), pady=max(0, pady - self.margen),
                                 cursor=cursor)
        self._ventana = self.create_window(self.margen, self.margen, window=self.interior, anchor="nw")
        self.interior.bind("<Configure>", self._ajustar, add="+")
        self.bind("<Configure>", self._dibujar, add="+")

    def _ajustar(self, _evento=None):
        self.configure(width=self.interior.winfo_reqwidth() + 2 * self.margen,
                       height=self.interior.winfo_reqheight() + 2 * self.margen)

    def _dibujar(self, evento):
        self.delete("fondo")
        g = self.grosor
        m = g // 2
        rectangulo_redondo(self, m, m, evento.width - 1 - m, evento.height - 1 - m, self.radio,
                           fill=self.fondo, outline=self.borde, width=g, tags="fondo")
        self.tag_lower("fondo")
        self.itemconfigure(self._ventana, width=max(1, evento.width - 2 * self.margen))

    def destroy(self):
        self.destruyendo = True
        super().destroy()

    def al_clic(self, funcion):
        for w in (self, self.interior):
            w.bind("<Button-1>", lambda e: funcion(), add="+")
        return self


class BotonRedondo(tk.Canvas):
    """Botón con esquinas redondeadas (radio 7 como en Figma)."""

    def __init__(self, padre, texto, comando, fondo, color, borde, tam=11, radio=7):
        self.fuente = (FUENTE, tam, "bold")
        medida = tk.font.Font(font=self.fuente)
        ancho, alto = medida.measure(texto) + 36, medida.metrics("linespace") + 16
        super().__init__(padre, width=ancho, height=alto, bg=fondo_de(padre), highlightthickness=0, bd=0, cursor="hand2")
        self._datos = (texto, fondo, color, borde, radio)
        self.comando = comando
        self._pintar(ancho, alto)
        self.bind("<Button-1>", lambda e: comando())
        # si se estira con grid(sticky="ew") se vuelve a dibujar al nuevo ancho (botones del pie de Figma)
        self.bind("<Configure>", lambda e: self._pintar(e.width, e.height))

    def _pintar(self, ancho, alto):
        texto, fondo, color, borde, radio = self._datos
        self.delete("all")
        rectangulo_redondo(self, 0, 0, ancho - 1, alto - 1, radio, fill=fondo, outline=borde)
        self.create_text(ancho / 2, alto / 2, text=texto, font=self.fuente, fill=color)


def avatar(padre, texto, fuerte, suave, tam=32):
    c = tk.Canvas(padre, width=tam, height=tam, bg=fondo_de(padre), highlightthickness=0, bd=0)
    c.create_oval(1, 1, tam - 1, tam - 1, fill=suave, outline=suave)
    c.create_text(tam / 2, tam / 2, text=texto, font=(FUENTE, 12, "bold"), fill=fuerte)
    return c


class Desplegable(tk.Frame):
    """Lista desplegable: el campo sigue el diseño de Figma y la lista es el menú nativo del sistema
    (el menú emergente de macOS o de Windows)."""

    def __init__(self, padre, var, opciones, ancho, acento, al_cambiar=None):
        super().__init__(padre, bg=fondo_de(padre), cursor="hand2")
        self.var, self.al_cambiar = var, al_cambiar
        self.etiqueta = tk.Label(self, textvariable=var, width=ancho, anchor="w", font=(FUENTE, 11), cursor="hand2",
                                 bg=fondo_de(padre), fg=C["texto"])
        self.etiqueta.pack(side="left", fill="x", expand=True)
        flecha = tk.Canvas(self, width=12, height=12, bg=fondo_de(padre), highlightthickness=0, cursor="hand2")
        flecha.create_line(2, 4, 6, 8, 10, 4, fill=acento, width=1.6, capstyle="round", joinstyle="round")
        flecha.pack(side="right", padx=(6, 0))
        self.menu = tk.Menu(self, tearoff=0)
        for opcion in opciones:
            self.menu.add_radiobutton(label=opcion, value=opcion, variable=var, command=self._elegido)
        for w in (self, self.etiqueta, flecha, padre, getattr(padre, "caja", padre)):
            w.bind("<Button-1>", self._abrir)

    def _abrir(self, _evento=None):
        x, y = self.winfo_rootx(), self.winfo_rooty() + self.winfo_height() + 4
        try:
            self.menu.tk_popup(x, y)
        finally:
            self.menu.grab_release()

    def _elegido(self):
        if self.al_cambiar:
            self.al_cambiar()


class Pestanas(tk.Frame):
    """Barra de pestañas como en Figma: contenedor redondeado y pestaña elegida con fondo suave y línea inferior."""

    def __init__(self, padre, app, clave, al_cambiar=None):
        super().__init__(padre, bg=C["fondo"])
        self.app, self.clave, self.al_cambiar = app, "pestana:" + clave, al_cambiar
        self.titulos, self.marcos = [], []
        self.barra = Caja(self, radio=6, fondo=C["blanco"], borde=C["borde"], padx=7, pady=5)
        self.barra.pack(fill="x")
        self.botones = tk.Frame(self.barra.interior, bg=C["blanco"])
        self.botones.pack(anchor="w")

    def add(self, marco, text=""):
        self.titulos.append(text)
        self.marcos.append(marco)
        self._dibujar()

    def actual(self):
        return min(self.app.filtros_guardados.get(self.clave, 0), len(self.marcos) - 1)

    def select(self, i):
        self.app.filtros_guardados[self.clave] = i
        if self.al_cambiar:  # la vista cambia su título según la pestaña
            self.after_idle(self.al_cambiar)
        else:
            self._dibujar()

    def _dibujar(self):
        for w in self.botones.winfo_children():
            w.destroy()
        activa = self.actual()
        for i, titulo in enumerate(self.titulos):
            elegida = i == activa
            # una sola pieza: esquinas redondeadas arriba, base recta y línea inferior del mismo ancho
            fuente = tk.font.Font(family=FUENTE, size=11, weight="bold" if elegida else "normal")
            ancho = fuente.measure(titulo) + 44
            alto = fuente.metrics("linespace") + 16
            c = tk.Canvas(self.botones, width=ancho, height=alto + 3, bg=C["blanco"], highlightthickness=0, bd=0,
                          cursor="hand2")
            c.pack(side="left", padx=(0, 6))
            if elegida:
                rectangulo_redondo(c, 0, 0, ancho - 1, alto + 6, 5, fill=self.app.acento_suave)
                c.create_rectangle(0, alto, ancho, alto + 3, fill=self.app.acento, outline="")
            c.create_text(ancho / 2, alto / 2, text=titulo, font=fuente, fill=self.app.acento if elegida else C["suave"])
            c.bind("<Button-1>", lambda e, n=i: self.select(n))
        for i, m in enumerate(self.marcos):
            if i == activa:
                m.pack(fill="x", pady=(14, 0))
            else:
                m.pack_forget()

# ---------------------------------------------------------------- componentes
TONOS = {"APROBADO": "#247457", "SIN_RIESGO": "#247457", "GUARDADA": "#247457", "CAPTURADA": "#247457",
         "INSCRITO": "#247457", "REPROBADO": "#a32e34", "ALTO": "#a32e34", "RECHAZADA": "#a32e34",
         "PENDIENTE": "#a26b34", "PENDIENTE DE CIERRE": "#a26b34", "MEDIO": "#a26b34", "ASIGNADO": "#247457",
         "ACTIVA": "#247457", "CONTRASEÑA INICIAL": "#a26b34", "EN CURSO": "#a26b34"}
COLUMNAS_ESTADO = ("estatus", "riesgo", "estado")
ORDENABLES = ("num_cuenta", "apellidos", "nombre", "promedio")


class Tabla(tk.Frame):
    """Tabla dibujada en un Canvas como en Figma: encabezado blanco con íconos de orden simétricos,
    filas alternas, texto de estado en verde, rojo o ámbar y barra de desplazamiento nativa.
    Solo se dibujan las filas visibles, así que soporta miles de registros."""

    ALTO_FILA, ALTO_CABEZA, MARGEN = 34, 36, 12

    def __init__(self, padre, app, columnas, alto=8, ordenables=ORDENABLES, al_seleccionar=None):
        super().__init__(padre, bg=fondo_de(padre))
        self.app, self.columnas, self.ordenables = app, columnas, ordenables
        self.al_seleccionar = al_seleccionar
        self.filas, self.visibles, self.orden, self.desc = [], [], "apellidos", False
        self.inicio, self.elegida = 0, None
        self.fuente = tk.font.Font(family=FUENTE, size=11)
        self.fuente_cabeza = tk.font.Font(family=FUENTE, size=10, weight="bold")
        caja = Caja(self, radio=4, fondo=C["blanco"], borde=C["borde"])
        caja.pack(fill="both", expand=True)
        dentro = caja.interior
        self.cabeza = tk.Canvas(dentro, height=self.ALTO_CABEZA, bg=C["blanco"], highlightthickness=0, bd=0)
        self.cabeza.pack(fill="x")
        cuerpo = tk.Frame(dentro, bg=C["blanco"])
        cuerpo.pack(fill="both", expand=True)
        self.barra = tk.Scrollbar(cuerpo, orient="vertical", command=self._desplazar)
        self.lienzo = tk.Canvas(cuerpo, height=alto * self.ALTO_FILA, bg=C["blanco"], highlightthickness=0, bd=0)
        self.lienzo.pack(side="left", fill="both", expand=True)
        self.lienzo.bind("<Configure>", lambda e: self._dibujar())
        self.cabeza.bind("<Button-1>", self._clic_cabeza)
        self.lienzo.bind("<Button-1>", self._clic_fila)
        for w in (self.lienzo, self.cabeza):
            w.bind("<MouseWheel>", lambda e: self._rueda(-e.delta if abs(e.delta) < 120 else -e.delta / 40))
            w.bind("<Button-4>", lambda e: self._rueda(-90))
            w.bind("<Button-5>", lambda e: self._rueda(90))

    # ---------- datos ----------
    def cargar(self, filas, _tags=None):
        self.filas = list(filas)
        conCuenta = self.filas and "num_cuenta" in self.filas[0] and self.orden in self.filas[0]
        self.visibles = ordenar(self.filas, self.orden, self.desc) if conCuenta else self.filas
        self.inicio, self.elegida = 0, None
        self._cache_minimos = None
        self._dibujar()

    def seleccion(self):
        return None if self.elegida is None else self.visibles[self.elegida]

    def ordenar_por(self, columna, desc):
        self.orden, self.desc = columna, desc
        self.cargar(self.filas)

    # ---------- geometría ----------
    def _minimos(self):
        """Ancho mínimo de cada columna: su encabezado (con el ícono de orden) y su contenido más largo
        (se miden hasta 300 filas y como máximo 280 px por columna)."""
        minimos = []
        muestra = self.visibles[:300]
        for clave, titulo, _ in self.columnas:
            m = self.fuente_cabeza.measure(titulo) + (20 if self._ordenable(clave) else 0)
            for fila in muestra:
                m = max(m, min(280, self.fuente.measure(legible(fmt(fila.get(clave))))))
            minimos.append(m + 2 * self.MARGEN)
        return minimos

    def _anchos(self):
        """Cada columna recibe su mínimo y el espacio sobrante se reparte según su peso."""
        total = max(1, self.lienzo.winfo_width())
        if getattr(self, "_cache_minimos", None) is None:
            self._cache_minimos = self._minimos()
        minimos = self._cache_minimos
        pesos = [c[2] for c in self.columnas]
        sobrante = total - sum(minimos)
        if sobrante >= 0:
            anchos = [m + sobrante * p / sum(pesos) for m, p in zip(minimos, pesos)]
        else:
            # falta espacio: las columnas cortas (números, estados) conservan su ancho y solo se
            # acortan las de texto largo
            base = [min(m, 130) for m in minimos]
            exceso = [m - b for m, b in zip(minimos, base)]
            libre = max(0, total - sum(base))
            anchos = [b + (e * libre / sum(exceso) if sum(exceso) else 0) for b, e in zip(base, exceso)]
        xs, x = [], 0
        for a in anchos:
            xs.append(x)
            x += a
        return xs + [total]

    def _ordenable(self, clave):
        return clave in self.ordenables and (not self.filas or "num_cuenta" in self.filas[0])

    def _recortar(self, texto, ancho, fuente):
        if fuente.measure(texto) <= ancho:
            return texto
        while texto and fuente.measure(texto + "…") > ancho:
            texto = texto[:-1]
        return texto + "…"

    # ---------- dibujo ----------
    def _dibujar(self):
        alto = self.lienzo.winfo_height()
        total = len(self.visibles) * self.ALTO_FILA
        self.inicio = max(0, min(self.inicio, total - alto))
        xs = self._anchos()
        self._dibujar_cabeza(xs)
        c = self.lienzo
        c.delete("all")
        colorear = self.app.opciones["colorear"].get()
        if not self.visibles:
            c.create_text(c.winfo_width() / 2, self.ALTO_FILA / 2, text="Sin resultados", fill=C["suave"], font=self.fuente)
        primera = self.inicio // self.ALTO_FILA
        for i in range(primera, min(len(self.visibles), primera + alto // self.ALTO_FILA + 2)):
            y = i * self.ALTO_FILA - self.inicio
            fondo = self.app.acento_suave if i == self.elegida else ("#fafbfd" if i % 2 else C["blanco"])
            c.create_rectangle(0, y, xs[-1], y + self.ALTO_FILA, fill=fondo, outline="")
            fila = self.visibles[i]
            for j, (clave, _t, _a) in enumerate(self.columnas):
                texto = fmt(fila.get(clave))
                color = C["texto"]
                if i == self.elegida:
                    color = self.app.acento
                elif colorear and clave in COLUMNAS_ESTADO:
                    color = TONOS.get(str(texto).upper(), C["texto"])
                ancho = xs[j + 1] - xs[j] - 2 * self.MARGEN
                c.create_text(xs[j] + self.MARGEN, y + self.ALTO_FILA / 2, anchor="w", fill=color, font=self.fuente,
                              text=self._recortar(legible(texto), ancho, self.fuente))
        if total > alto > 1:
            self.barra.pack(side="right", fill="y")
            self.barra.set(self.inicio / total, (self.inicio + alto) / total)
        else:
            self.barra.pack_forget()

    def _dibujar_cabeza(self, xs):
        c = self.cabeza
        c.delete("all")
        h = self.ALTO_CABEZA
        for j, (clave, titulo, _a) in enumerate(self.columnas):
            activa = clave == self.orden and self._ordenable(clave)
            color = C["texto"] if activa else C["suave"]
            c.create_text(xs[j] + self.MARGEN, h / 2, anchor="w", text=titulo, fill=color, font=self.fuente_cabeza)
            if self._ordenable(clave):
                x = xs[j] + self.MARGEN + self.fuente_cabeza.measure(titulo) + 10
                arriba = self.app.acento if activa and not self.desc else "#cdc3be"
                abajo = self.app.acento if activa and self.desc else "#cdc3be"
                c.create_line(x - 3, h / 2 - 2, x, h / 2 - 5, x + 3, h / 2 - 2, fill=arriba, width=1.6,
                              capstyle="round", joinstyle="round")
                c.create_line(x - 3, h / 2 + 2, x, h / 2 + 5, x + 3, h / 2 + 2, fill=abajo, width=1.6,
                              capstyle="round", joinstyle="round")
        c.create_line(0, h - 1, xs[-1] + 40, h - 1, fill=C["borde"])

    # ---------- eventos ----------
    def _desplazar(self, accion, cantidad, unidad=None):
        total, alto = len(self.visibles) * self.ALTO_FILA, self.lienzo.winfo_height()
        if accion == "moveto":
            self.inicio = int(float(cantidad) * total)
        else:
            paso = self.ALTO_FILA if unidad == "units" else alto
            self.inicio += int(cantidad) * paso
        self._dibujar()

    def _rueda(self, pixeles):
        if len(self.visibles) * self.ALTO_FILA <= self.lienzo.winfo_height():
            return None
        self.inicio += int(pixeles)
        self._dibujar()
        return "break"

    def _clic_cabeza(self, evento):
        xs = self._anchos()
        for j, (clave, _t, _a) in enumerate(self.columnas):
            if xs[j] <= evento.x < xs[j + 1] and self._ordenable(clave):
                self.ordenar_por(clave, not self.desc if clave == self.orden else False)

    def _clic_fila(self, evento):
        i = (evento.y + self.inicio) // self.ALTO_FILA
        if 0 <= i < len(self.visibles):
            self.elegida = i
            self._dibujar()
            if self.al_seleccionar:
                self.al_seleccionar(self.visibles[i])


class App(tk.Tk):
    def __init__(self, entrada=None):
        iconos.registrar_fuente()  # antes de crear la ventana, para que Tk encuentre la fuente de íconos
        super().__init__()
        elegir_fuente(self)
        self.title("Syllabus 2.0 · Sistema de Gestión Académica (Python)")
        self.geometry("1360x860")
        self.minsize(1100, 700)
        self.configure(bg=C["fondo"])
        self.tema = "Rojo"
        self.opciones = {"colorear": tk.BooleanVar(value=True), "avisos": tk.BooleanVar(value=True),
                         "recordar": tk.BooleanVar(value=True)}
        self.filtros_guardados = {}
        self.rol, self.usuario, self.vista, self.cuenta = None, None, "Inicio", None
        self.actividad: list[str] = []
        self._cargando = False
        self._refresco = self._guardado = None
        # sin --entrada se trabaja sobre datos/base.json y cada cambio se guarda solo;
        # con --entrada se abre ese archivo como demostración, sin guardar
        self.persistir = entrada is None
        self.institucion = dict(INSTITUCION)
        self.nuevo_gestor()
        self._cargando = True
        try:
            if self.persistir:
                self.usuarios = persistencia.abrir(self.gestor)
                self.institucion = persistencia.leer_institucion()
            else:
                self.gestor.cargar(cargar_json(entrada))
                self.usuarios = cargar_usuarios()
                sincronizar(self.usuarios, self.gestor)
        except (ErrorValidacion, OSError, ValueError, KeyError) as e:
            messagebox.showerror("No se pudieron abrir los datos", str(e))
            self.usuarios = cargar_usuarios()
        self._cargando = False
        if self.persistir and not persistencia.BASE.exists():
            self.guardar_ahora()
        self.aplicar_estilos()
        self.mostrar_acceso()

    # ---------- datos y eventos ----------
    def nuevo_gestor(self):
        self.bus = BusEventos()
        self.bus.suscribir_todos(self.al_evento)
        self.gestor = GestorEscolar(self.bus)

    def guardar_ahora(self):
        self._guardado = None
        if sincronizar(self.usuarios, self.gestor):
            self.registrar_actividad("cuentas_creadas · usuario y contraseña inicial = número de cuenta o ID")
        if self.persistir:
            try:
                persistencia.guardar(self.gestor, self.usuarios, institucion=self.institucion)
            except OSError as e:
                messagebox.showerror("No se pudo guardar", str(e))

    def importar_archivo(self, ruta):
        """Carga masiva: agrega al sistema los registros válidos de un JSON y devuelve los rechazados."""
        datos = cargar_json(ruta)
        antes = len(self.gestor.errores)
        self._cargando = True
        try:
            self.gestor.cargar(datos)
        finally:
            self._cargando = False
        self.gestor._cambio()
        self.guardar_ahora()
        self.registrar_actividad(f"carga_masiva · {Path(ruta).name} · {len(self.gestor.errores) - antes} rechazados")
        self.refrescar()
        return self.gestor.errores[antes:]

    def restablecer_ejemplo(self):
        if persistencia.BASE.exists():
            persistencia.BASE.unlink()
        self.nuevo_gestor()
        self._cargando = True
        self.usuarios = persistencia.abrir(self.gestor)
        self.institucion = persistencia.leer_institucion()
        self._cargando = False
        self.guardar_ahora()

    def al_evento(self, evento, hora, **datos):
        if self._cargando:
            return
        self.registrar_actividad(f"{hora}  {evento} · {datos.get('detalle', '')}")
        if evento == "error_validacion" and self.opciones["avisos"].get():
            messagebox.showerror(legible(datos.get("codigo", "Error")), legible(datos.get("detalle", "")))
        if evento not in persistencia.NO_GUARDAN and self._guardado is None:
            self._guardado = self.after(250, self.guardar_ahora)
        # varios eventos seguidos (una operación por lote) producen un solo refresco
        if self._refresco is None:
            self._refresco = self.after_idle(self._refrescar_pendiente)

    def _refrescar_pendiente(self):
        self._refresco = None
        self.refrescar()

    def registrar_actividad(self, texto):
        if not re.match(r"\d{2}:\d{2}:\d{2}", texto):
            texto = f"{datetime.now():%H:%M:%S}  {texto}"
        self.actividad.insert(0, texto)
        del self.actividad[30:]

    def intentar(self, accion, *args):
        """Ejecuta una acción del gestor; los errores llegan por el evento error_validacion."""
        try:
            accion(*args)
            return True
        except ErrorValidacion as e:
            self.gestor._error("interfaz", {}, e)
            return False

    # ---------- estilo ----------
    @property
    def acento(self):
        return TEMAS[self.tema][0]

    @property
    def acento_suave(self):
        return TEMAS[self.tema][1]

    def aplicar_estilos(self):
        s = ttk.Style(self)
        s.theme_use("clam")
        s.configure(".", font=(FUENTE, 11), background=C["fondo"], foreground=C["texto"])
        s.configure("Tarjeta.TFrame", background=C["blanco"])
        s.configure("Fondo.TFrame", background=C["fondo"])
        s.configure("Treeview", background=C["blanco"], fieldbackground=C["blanco"], foreground=C["texto"],
                    rowheight=26, borderwidth=0, font=(FUENTE, 11))
        s.configure("Treeview.Heading", background=C["campo"], foreground=C["suave"], relief="flat",
                    font=(FUENTE, 10, "bold"), padding=(6, 6))
        s.map("Treeview", background=[("selected", self.acento_suave)], foreground=[("selected", self.acento)])
        s.configure("TNotebook", background=C["fondo"], borderwidth=0)
        s.configure("TNotebook.Tab", background=C["fondo"], foreground=C["suave"], padding=(14, 6),
                    font=(FUENTE, 11, "bold"))
        s.map("TNotebook.Tab", background=[("selected", C["blanco"])], foreground=[("selected", self.acento)])
        s.configure("TCombobox", fieldbackground=C["campo"], background=C["campo"], arrowcolor=self.acento)
        s.configure("Campo.TCombobox", fieldbackground=C["blanco"], background=C["blanco"], arrowcolor=self.acento,
                    bordercolor=C["blanco"], lightcolor=C["blanco"], darkcolor=C["blanco"], borderwidth=0, padding=0)
        s.map("Campo.TCombobox", fieldbackground=[("readonly", C["blanco"])], selectbackground=[("readonly", C["blanco"])],
              selectforeground=[("readonly", C["texto"])])
        s.configure("Treeview", borderwidth=0, relief="flat")
        s.layout("Treeview", [("Treeview.treearea", {"sticky": "nswe"})])
        s.configure("TCheckbutton", background=C["blanco"])
        s.map("TCheckbutton", background=[("disabled", C["blanco"]), ("active", C["blanco"])],
              foreground=[("disabled", C["suave"])])
        s.configure("TRadiobutton", background=C["blanco"])

    # ---------- piezas de diseño ----------
    def etiqueta(self, padre, texto, tam=11, peso="normal", color=None, bg=None, **kw):
        return tk.Label(padre, text=texto, font=(FUENTE, tam, peso), fg=color or C["texto"],
                        bg=bg or padre.cget("bg"), anchor="w", justify="left", **kw)

    def boton(self, padre, texto, comando, primario=True, **kw):
        if primario:
            return BotonRedondo(padre, texto, comando, self.acento, "white", self.acento)
        return BotonRedondo(padre, texto, comando, C["blanco"], self.acento, C["borde"])

    def tarjeta(self, padre, titulo=None, subtitulo=None, **pack):
        marco = self.caja(padre, radio=10, padx=18, pady=16)
        if pack:
            marco.pack(**pack)
        if titulo:
            self.etiqueta(marco, titulo, 13, "bold").pack(anchor="w")
            tk.Frame(marco, bg=C["borde"], height=1).pack(fill="x", pady=(10, 10))
        if subtitulo:
            self.etiqueta(marco, subtitulo, 10, color=C["suave"], wraplength=900).pack(anchor="w", pady=(2, 8))
        return marco

    def caja(self, padre, radio=10, fondo=None, borde=None, padx=0, pady=0, grosor=1, cursor=""):
        """Contenedor redondeado; devuelve el interior (se empaca como cualquier Frame)."""
        return Caja(padre, radio=radio, fondo=fondo or C["blanco"], borde=borde or C["borde"], grosor=grosor,
                    padx=padx, pady=pady, cursor=cursor).interior

    def campo(self, padre, texto, var, ancho=20, opciones=None, al_cambiar=None, solo_lectura=False):
        caja = tk.Frame(padre, bg=padre.cget("bg"))
        self.etiqueta(caja, texto, 10, color=C["suave"]).pack(anchor="w")
        borde = Caja(caja, radio=5, fondo=C["blanco"], borde=C["borde"], padx=9, pady=6)
        borde.pack(anchor="w", pady=(3, 0))
        if opciones is not None:
            if opciones and not var.get():
                var.set(opciones[0])
            w = Desplegable(borde.interior, var, opciones, ancho, self.acento, al_cambiar)
        else:
            w = tk.Entry(borde.interior, textvariable=var, width=ancho, font=(FUENTE, 11), bg=C["blanco"], relief="flat",
                         bd=0, highlightthickness=0, insertbackground=C["texto"])
            if solo_lectura:
                w.config(state="readonly", readonlybackground=C["blanco"], fg=C["suave"])
        w.pack(anchor="w", fill="x")
        return caja

    def mosaicos(self, padre, datos):
        fila = tk.Frame(padre, bg=C["fondo"])
        fila.pack(fill="x", pady=(0, 14))
        for i, (titulo, valor) in enumerate(datos):
            t = self.caja(fila, radio=10, padx=16, pady=12)
            t.grid(row=0, column=i, sticky="nsew", padx=(0 if i == 0 else 12, 0))
            fila.columnconfigure(i, weight=1)
            self.etiqueta(t, titulo, 10, color=C["suave"]).pack(anchor="w")
            self.etiqueta(t, fmt(valor), 20, "bold", color=self.acento).pack(anchor="w")
        return fila


    # ---------- pantallas ----------
    def limpiar(self):
        for w in self.winfo_children():
            w.destroy()

    def mostrar_acceso(self):
        from .vistas import acceso
        self.rol = self.usuario = self.cuenta = None
        self.limpiar()
        acceso(self)
        self.activar_ventana()

    def activar_ventana(self):
        """En macOS, una ventana de Tk abierta desde la Terminal puede no recibir el teclado
        hasta que se activa: se trae al frente y se le da el foco."""
        self.lift()
        self.attributes("-topmost", True)
        self.after(300, lambda: self.attributes("-topmost", False))
        self.focus_force()

    def entrar(self, cuenta):
        self.cuenta = cuenta
        self.rol, self.usuario = cuenta["rol"], cuenta.get("referencia")
        self.filtros_guardados = {}
        self.vista = "Mi cuenta" if cuenta.get("debe_cambiar") else "Inicio"
        self.construir_marco()

    def construir_marco(self):
        self.limpiar()
        self.aplicar_estilos()
        cab = tk.Frame(self, bg=C["blanco"], height=64, highlightthickness=1, highlightbackground=C["borde"])
        cab.pack(fill="x")
        logo = self.caja(cab, radio=8, fondo=self.acento_suave, borde=self.acento_suave, padx=12, pady=6)
        tk.Label(logo, text="S", font=(FUENTE, 16, "bold"), fg=self.acento, bg=self.acento_suave).pack()
        logo.pack(side="left", padx=(20, 12), pady=14)
        titulos = tk.Frame(cab, bg=C["blanco"])
        titulos.pack(side="left")
        self.etiqueta(titulos, "Syllabus 2.0", 17, "bold").pack(anchor="w")
        self.etiqueta(titulos, "Gestión académica", 10, color=C["suave"]).pack(anchor="w")
        perfil = ((self.cuenta or {}).get("nombre") or self.rol)[0].upper()
        circulo = avatar(cab, perfil, self.acento, self.acento_suave)
        circulo.configure(cursor="hand2")
        circulo.pack(side="right", padx=(8, 20))
        # el avatar abre el menú nativo del sistema con la opción de cerrar sesión
        menu = tk.Menu(circulo, tearoff=0)
        menu.add_command(label=self.nombre_usuario() or self.rol, state="disabled")
        menu.add_separator()
        menu.add_command(label="Mi cuenta y contraseña", command=lambda: self.ir("Mi cuenta"))
        menu.add_command(label="Cerrar sesión", command=self.mostrar_acceso)

        def abrir_menu(_e=None):
            try:
                menu.tk_popup(circulo.winfo_rootx(), circulo.winfo_rooty() + circulo.winfo_height() + 4)
            finally:
                menu.grab_release()
        circulo.bind("<Button-1>", abrir_menu)
        self.menu_avatar = menu
        pastilla = self.caja(cab, radio=18, fondo=self.acento_suave, borde=self.acento_suave, padx=16, pady=9)
        self.etiqueta(pastilla, self.rol, 10, "bold", color=self.acento).pack()
        pastilla.pack(side="right", padx=12)

        cuerpo = tk.Frame(self, bg=C["fondo"])
        cuerpo.pack(fill="both", expand=True)
        lateral = tk.Frame(cuerpo, bg=C["blanco"], width=240, highlightthickness=1, highlightbackground=C["borde"])
        lateral.pack(side="left", fill="y")
        lateral.pack_propagate(False)
        self.etiqueta(lateral, "MENÚ PRINCIPAL", 9, "bold", color=C["suave"]).pack(anchor="w", padx=20, pady=(22, 10))
        for icono, nombre in MENUS[self.rol]:
            activo = nombre == self.vista
            fondo = self.acento_suave if activo else C["blanco"]
            fila = self.caja(lateral, radio=14, fondo=fondo, borde=fondo, padx=8, pady=0, cursor="hand2")
            fila.pack(fill="x", padx=10, pady=2)
            fila.caja.bind("<Button-1>", lambda e, n=nombre: self.ir(n))
            # ícono Lucide de 20 px (trazo 1.8 en escala de 24) centrado en un área de 28 px y separado 16 px de la etiqueta
            dibujo = iconos.icono(fila, icono, self.acento if activo else C["suave"], fondo, 20, 1.8, caja=28)
            dibujo.configure(cursor="hand2")
            dibujo.pack(side="left", padx=(2, 16), pady=7)
            texto = tk.Label(fila, text=nombre, font=(FUENTE, 11, "bold" if activo else "normal"),
                             bg=fondo, fg=self.acento if activo else C["texto"], anchor="w", cursor="hand2")
            texto.pack(side="left", pady=7)
            for w in (fila, dibujo, texto):
                w.bind("<Button-1>", lambda e, n=nombre: self.ir(n))
            if activo:
                barra = tk.Canvas(fila, width=4, height=28, bg=self.acento, highlightthickness=0, bd=0)
                barra.pack(side="right", anchor="center")
        pie = tk.Frame(lateral, bg=C["blanco"])
        pie.pack(side="bottom", fill="x", padx=20, pady=16)
        tk.Frame(pie, bg=C["borde"], height=1).pack(fill="x", pady=(0, 10))
        self.etiqueta(pie, "Perfil de institución", 9, color=C["suave"]).pack(anchor="w")
        self.etiqueta(pie, "Syllabus 2.0", 9, "bold").pack(anchor="w")

        # contenido con desplazamiento
        lienzo = tk.Canvas(cuerpo, bg=C["fondo"], highlightthickness=0)
        barra = tk.Scrollbar(cuerpo, orient="vertical", command=lienzo.yview)
        self.contenido = tk.Frame(lienzo, bg=C["fondo"], padx=30, pady=22)
        self.contenido.bind("<Configure>", lambda e: lienzo.configure(scrollregion=lienzo.bbox("all")))
        ventana = lienzo.create_window((0, 0), window=self.contenido, anchor="nw")
        lienzo.bind("<Configure>", lambda e: lienzo.itemconfigure(ventana, width=e.width))
        lienzo.configure(yscrollcommand=barra.set)
        lienzo.pack(side="left", fill="both", expand=True)
        barra.pack(side="right", fill="y")
        self.bind_all("<MouseWheel>", lambda e: lienzo.yview_scroll(int(-e.delta / 120) or -e.delta, "units"))
        self.bind_all("<Button-4>", lambda e: lienzo.yview_scroll(-3, "units"))
        self.bind_all("<Button-5>", lambda e: lienzo.yview_scroll(3, "units"))
        self.mostrar_vista()

    def nombre_usuario(self):
        if self.rol == "Docente" and self.usuario in self.gestor.docentes:
            return self.gestor.docentes[self.usuario].nombre
        if self.rol == "Alumno" and self.usuario in self.gestor.estudiantes:
            return self.gestor.estudiantes[self.usuario].nombre_completo
        return ""

    def encabezado(self, ruta, titulo, subtitulo):
        self.etiqueta(self.contenido, ruta.upper(), 10, "bold", color=self.acento).pack(anchor="w")
        self.etiqueta(self.contenido, titulo, 24, "bold").pack(anchor="w", pady=(6, 2))
        self.etiqueta(self.contenido, subtitulo, 11, color=C["suave"]).pack(anchor="w", pady=(0, 16))

    def ir(self, vista, **kw):
        if self.cuenta and self.cuenta.get("debe_cambiar"):
            vista = "Mi cuenta"  # primero debe cambiar su contraseña inicial
        self.vista = vista
        self.filtros_guardados.pop("kardex_de", None)
        self.parametros = kw
        self.construir_marco()

    def refrescar(self):
        if self.rol and hasattr(self, "contenido") and self.contenido.winfo_exists():
            for w in self.contenido.winfo_children():
                w.destroy()
            self.mostrar_vista()

    def mostrar_vista(self):
        from . import vistas
        self.parametros = getattr(self, "parametros", {})
        vistas.VISTAS[(self.rol, self.vista)](self)

    # ---------- alcance por rol ----------
    def cursos_visibles(self):
        if self.rol == "Docente":
            return self.gestor.cursos_de_docente(self.usuario)
        if self.rol == "Alumno":
            return self.gestor.cursos_de(self.usuario)
        return sorted(self.gestor.cursos.values(), key=lambda c: (c.grupo, c.asignatura))


def iniciar(entrada=None):
    App(entrada).mainloop()
