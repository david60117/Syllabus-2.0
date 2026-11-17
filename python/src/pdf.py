"""Exportación a PDF sin dependencias externas (solo biblioteca estándar).

Dibuja el reporte como se ve en la aplicación: encabezado institucional con banda de color, datos del
alcance, tabla con encabezado repetido en cada página, renglones alternos, estados en color, resumen,
firmas, nota y pie con número de página. Usa las fuentes base Helvetica (codificación WinAnsi, que
incluye acentos y ñ).
"""
from decimal import Decimal
from pathlib import Path

# anchos de Helvetica y Helvetica-Bold (1/1000 em) para los caracteres 32..255 de cp1252
_ANCHOS = {
    "F1": [
    278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278,
    584, 584, 584, 556, 1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944,
    667, 667, 611, 278, 278, 278, 469, 556, 333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556, 556, 556, 333, 500,
    278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584, 761, 556, 0, 222, 556, 333, 1000, 556, 556, 333, 1000, 667, 333, 1000, 0, 611, 0,
    0, 222, 222, 333, 333, 350, 556, 1000, 333, 1000, 500, 333, 944, 0, 500, 667, 278, 333, 556, 556, 556, 556, 260, 556, 333, 737, 370, 556,
    584, 333, 737, 333, 400, 584, 333, 333, 333, 556, 537, 278, 333, 333, 365, 556, 834, 834, 834, 611, 667, 667, 667, 667, 667, 667, 1000, 722,
    667, 667, 667, 667, 278, 278, 278, 278, 722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611, 556, 556, 556, 556,
    556, 556, 889, 500, 556, 556, 556, 556, 278, 278, 278, 278, 556, 556, 556, 556, 556, 556, 556, 584, 611, 556, 556, 556, 556, 500, 556, 500,
    ],
    "F2": [
    278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333,
    584, 584, 584, 611, 975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944,
    667, 667, 611, 333, 278, 333, 584, 556, 333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611, 611, 611, 389, 556,
    333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584, 761, 556, 0, 278, 556, 500, 1000, 556, 556, 333, 1000, 667, 333, 1000, 0, 611, 0,
    0, 278, 278, 500, 500, 350, 556, 1000, 333, 1000, 556, 333, 944, 0, 500, 667, 278, 333, 556, 556, 556, 556, 280, 556, 333, 737, 370, 556,
    584, 333, 737, 333, 400, 584, 333, 333, 333, 611, 556, 278, 333, 333, 365, 556, 834, 834, 834, 611, 722, 722, 722, 722, 722, 722, 1000, 722,
    667, 667, 667, 667, 278, 278, 278, 278, 722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611, 556, 556, 556, 556,
    556, 556, 889, 556, 556, 556, 556, 556, 278, 278, 278, 278, 611, 611, 611, 611, 611, 611, 611, 584, 611, 611, 611, 611, 611, 556, 611, 556,
    ],
}

TONOS = {"Aprobado": "#247457", "Sin riesgo": "#247457", "Activa": "#247457", "Reprobado": "#a32e34", "Alto": "#a32e34",
         "Pendiente": "#a26b34", "Medio": "#a26b34", "En curso": "#a26b34", "Contraseña inicial": "#a26b34"}
TEXTO, SUAVE, BORDE, ZEBRA = "#2c2426", "#766e6a", "#e9dfd9", "#fafbfd"
MARGEN = 36


def _rgb(color: str) -> tuple:
    color = color.lstrip("#")
    return tuple(int(color[i:i + 2], 16) / 255 for i in (0, 2, 4))


def _suave(color: str, cuanto=0.9) -> str:
    r, g, b = (c + (1 - c) * cuanto for c in _rgb(color))
    return "#" + "".join(f"{round(x * 255):02x}" for x in (r, g, b))


def _texto(valor) -> str:
    if valor is None:
        return "—"
    if isinstance(valor, Decimal):
        return f"{valor:.2f}"
    if isinstance(valor, float):
        return f"{valor:g}"
    return str(valor)


def ancho(s: str, tam: float, negrita=False) -> float:
    tabla = _ANCHOS["F2" if negrita else "F1"]
    total = 0
    for b in s.encode("cp1252", "replace"):
        total += tabla[b - 32] if 32 <= b <= 255 else 556
    return total * tam / 1000


def recortar(s: str, maximo: float, tam: float, negrita=False) -> str:
    if ancho(s, tam, negrita) <= maximo:
        return s
    while s and ancho(s + "…", tam, negrita) > maximo:
        s = s[:-1]
    return s + "…"


def partir(s: str, maximo: float, tam: float) -> list[str]:
    lineas, actual = [], ""
    for palabra in s.split():
        prueba = f"{actual} {palabra}".strip()
        if ancho(prueba, tam) <= maximo:
            actual = prueba
        else:
            lineas.append(actual)
            actual = palabra
    return lineas + [actual] if actual else lineas


class Pagina:
    def __init__(self, ancho_, alto_):
        self.w, self.h, self.ops = ancho_, alto_, []

    def rect(self, x, y, w, h, relleno=None, borde=None, grosor=0.6):
        if relleno:
            self.ops.append("%.3f %.3f %.3f rg" % _rgb(relleno))
        if borde:
            self.ops.append("%.3f %.3f %.3f RG %.2f w" % (*_rgb(borde), grosor))
        op = "B" if relleno and borde else ("f" if relleno else "S")
        self.ops.append(f"{x:.2f} {self.h - y - h:.2f} {w:.2f} {h:.2f} re {op}")

    def linea(self, x1, y1, x2, y2, color=BORDE, grosor=0.6):
        self.ops.append("%.3f %.3f %.3f RG %.2f w" % (*_rgb(color), grosor))
        self.ops.append(f"{x1:.2f} {self.h - y1:.2f} m {x2:.2f} {self.h - y2:.2f} l S")

    def texto(self, x, y, s, tam=9, negrita=False, color=TEXTO, alinear="izq"):
        """y es la línea base medida desde arriba."""
        if alinear == "der":
            x -= ancho(s, tam, negrita)
        elif alinear == "centro":
            x -= ancho(s, tam, negrita) / 2
        datos = s.encode("cp1252", "replace").replace(b"\\", b"\\\\").replace(b"(", b"\\(").replace(b")", b"\\)")
        self.ops.append("%.3f %.3f %.3f rg" % _rgb(color))
        self.ops.append(f"BT /{'F2' if negrita else 'F1'} {tam} Tf {x:.2f} {self.h - y:.2f} Td (" +
                        datos.decode("latin-1") + ") Tj ET")


def _anchos_columnas(doc, disponible) -> list[float]:
    naturales = []
    for clave, titulo in doc["columnas"]:
        m = ancho(titulo, 8, True)
        for f in doc["filas"][:400]:
            m = max(m, ancho(_texto(f.get(clave)), 8.5))
        naturales.append(m + 12)
    if sum(naturales) <= disponible:
        extra = (disponible - sum(naturales)) / len(naturales)
        return [n + extra for n in naturales]
    bajo, alto = 0, max(naturales)  # tope común para las columnas largas
    for _ in range(40):
        medio = (bajo + alto) / 2
        if sum(min(n, medio) for n in naturales) > disponible:
            alto = medio
        else:
            bajo = medio
    return [min(n, bajo) for n in naturales]


def generar(doc: dict, ruta, acento="#8b2024") -> Path:
    enc = doc["encabezado"]
    natural = sum(ancho(t, 8, True) + 40 for _, t in doc["columnas"])
    horizontal = len(doc["columnas"]) > 7 or natural > 595 - 2 * MARGEN
    W, H = (842, 595) if horizontal else (595, 842)
    util = W - 2 * MARGEN
    paginas = []

    def nueva():
        p = Pagina(W, H)
        paginas.append(p)
        return p

    # ---------- encabezado institucional (primera página) ----------
    p = nueva()
    campos = enc["campos"]
    filas_campos = -(-len(campos) // 3)
    alto_banda = 92 + filas_campos * 26
    p.rect(MARGEN, MARGEN, util, alto_banda, relleno=_suave(acento, 0.9))
    p.rect(MARGEN, MARGEN, 4, alto_banda, relleno=acento)
    x0 = MARGEN + 18
    p.texto(x0, MARGEN + 24, enc["institucion"], 13, True)
    p.texto(x0, MARGEN + 38, enc["programa"], 8.5, color=SUAVE)
    p.texto(MARGEN + util - 16, MARGEN + 22, "CICLO ESCOLAR", 7, True, SUAVE, "der")
    p.texto(MARGEN + util - 16, MARGEN + 36, enc["ciclo"], 10, True, alinear="der")
    p.texto(x0, MARGEN + 64, enc["titulo"].upper(), 15, True, acento)
    col = (util - 36) / 3
    for i, (etiqueta, valor) in enumerate(campos):
        cx, cy = x0 + (i % 3) * col, MARGEN + 84 + (i // 3) * 26
        p.texto(cx, cy, etiqueta, 7, color=SUAVE)
        p.texto(cx, cy + 11, recortar(_texto(valor), col - 12, 9.5, True), 9.5, True)
    p.texto(MARGEN + util - 16, MARGEN + alto_banda - 8,
            f"Emitido el {enc['emision']}" + (f" por {enc['emitido_por']}" if enc["emitido_por"] else ""), 7, color=SUAVE,
            alinear="der")
    y = MARGEN + alto_banda + 18

    # ---------- tabla ----------
    anchos = _anchos_columnas(doc, util)
    xs = [MARGEN]
    for a in anchos:
        xs.append(xs[-1] + a)
    FILA, CABEZA, LIMITE = 17, 20, H - MARGEN - 28

    def cabeza(pag, y):
        pag.rect(MARGEN, y, util, CABEZA, relleno="#ffffff")
        for j, (_, titulo) in enumerate(doc["columnas"]):
            pag.texto(xs[j] + 6, y + 13.5, recortar(titulo, anchos[j] - 10, 8, True), 8, True, SUAVE)
        pag.linea(MARGEN, y + CABEZA, MARGEN + util, y + CABEZA, BORDE, 0.8)
        return y + CABEZA

    inicio_tabla = y
    y = cabeza(p, y)
    if not doc["filas"]:
        p.texto(MARGEN + util / 2, y + 13, "Sin registros", 9, color=SUAVE, alinear="centro")
        y += FILA
    for i, fila in enumerate(doc["filas"]):
        if y + FILA > LIMITE:
            p.rect(MARGEN, inicio_tabla, util, y - inicio_tabla, borde=BORDE)
            p = nueva()
            p.texto(MARGEN, MARGEN + 12, f"{enc['titulo']} (continuación)", 10, True, acento)
            inicio_tabla = MARGEN + 22
            y = cabeza(p, inicio_tabla)
        if i % 2:
            p.rect(MARGEN, y, util, FILA, relleno=ZEBRA)
        for j, (clave, _) in enumerate(doc["columnas"]):
            s = _texto(fila.get(clave))
            p.texto(xs[j] + 6, y + 12, recortar(s, anchos[j] - 10, 8.5), 8.5, color=TONOS.get(s, TEXTO))
        y += FILA
    p.rect(MARGEN, inicio_tabla, util, y - inicio_tabla, borde=BORDE)
    y += 14

    # ---------- resumen, firmas y nota ----------
    if doc["resumen"]:
        if y + 40 > LIMITE:
            p, y = nueva(), MARGEN
        n = len(doc["resumen"])
        caja = (util - 8 * (n - 1)) / n
        for i, (etiqueta, valor) in enumerate(doc["resumen"]):
            x = MARGEN + i * (caja + 8)
            p.rect(x, y, caja, 36, relleno="#fbfaf9", borde=BORDE)
            p.texto(x + 8, y + 13, recortar(etiqueta, caja - 14, 7), 7, color=SUAVE)
            p.texto(x + 8, y + 28, recortar(_texto(valor), caja - 14, 11, True), 11, True, acento)
        y += 50
    if doc["firmas"]:
        if y + 70 > LIMITE:
            p, y = nueva(), MARGEN
        n = len(doc["firmas"])
        tramo = util / n
        for i, f in enumerate(doc["firmas"]):
            cx = MARGEN + tramo * i + tramo / 2
            p.linea(cx - min(110, tramo / 2 - 20), y + 46, cx + min(110, tramo / 2 - 20), y + 46, TEXTO, 0.7)
            p.texto(cx, y + 58, f, 8, color=SUAVE, alinear="centro")
        y += 72
    if doc["nota"]:
        for linea in partir(doc["nota"], util, 7.5):
            if y + 10 > LIMITE:
                p, y = nueva(), MARGEN
            p.texto(MARGEN, y + 8, linea, 7.5, color=SUAVE)
            y += 11

    # ---------- pie de página ----------
    for i, pag in enumerate(paginas, 1):
        pag.linea(MARGEN, H - MARGEN - 14, W - MARGEN, H - MARGEN - 14)
        pag.texto(MARGEN, H - MARGEN, f"Syllabus 2.0 · {enc['titulo']}", 7, color=SUAVE)
        pag.texto(W - MARGEN, H - MARGEN, f"Página {i} de {len(paginas)}", 7, color=SUAVE, alinear="der")
    return _escribir(paginas, ruta, enc["titulo"])


def _escribir(paginas, ruta, titulo) -> Path:
    objetos = []

    def agregar(contenido: bytes) -> int:
        objetos.append(contenido)
        return len(objetos)
    catalogo = agregar(b"")
    raiz = agregar(b"")
    f1 = agregar(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    f2 = agregar(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
    hijos = []
    for pag in paginas:
        flujo = "\n".join(pag.ops).encode("latin-1")
        contenido = agregar(b"<< /Length %d >>\nstream\n" % len(flujo) + flujo + b"\nendstream")
        hijos.append(agregar(("<< /Type /Page /Parent %d 0 R /MediaBox [0 0 %d %d] /Contents %d 0 R "
                              "/Resources << /Font << /F1 %d 0 R /F2 %d 0 R >> >> >>" % (raiz, pag.w, pag.h, contenido, f1, f2)).encode()))
    objetos[catalogo - 1] = b"<< /Type /Catalog /Pages %d 0 R >>" % raiz
    objetos[raiz - 1] = ("<< /Type /Pages /Kids [%s] /Count %d >>" % (" ".join(f"{h} 0 R" for h in hijos), len(hijos))).encode()
    info = agregar(b"<< /Title (" + titulo.encode("cp1252", "replace").replace(b"(", b"").replace(b")", b"") +
                   b") /Producer (Syllabus 2.0) >>")
    salida = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    posiciones = []
    for i, obj in enumerate(objetos, 1):
        posiciones.append(len(salida))
        salida += b"%d 0 obj\n" % i + obj + b"\nendobj\n"
    xref = len(salida)
    salida += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objetos) + 1)
    salida += b"".join(b"%010d 00000 n \n" % pos for pos in posiciones)
    salida += b"trailer\n<< /Size %d /Root %d 0 R /Info %d 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objetos) + 1, catalogo, info, xref)
    ruta = Path(ruta)
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_bytes(bytes(salida))
    return ruta
