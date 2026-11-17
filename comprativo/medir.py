"""Ejecución comparativa de las tres versiones (Python, JavaScript y Java).

Corre la línea de comandos de cada versión con los mismos datos (pequeñas, medias y grandes), en modo secuencial y
concurrente; mide el tiempo de cálculo que reporta cada versión y el tiempo total del proceso; compara que los
13 reportes generados sean idénticos entre lenguajes; cuenta líneas de código y escribe:

    comparativo/resultados.json   datos para el documento comparativo
    comparativo/resultados.js     los mismos datos para tablero.html (se abre con doble clic, sin servidor)

Uso (desde la raíz del proyecto):
    python3 comparativo/medir.py                 # 3 volúmenes, 3 repeticiones (mediana)
    python3 comparativo/medir.py --rapido        # sin el volumen grande, 1 repetición
    python3 comparativo/medir.py --repeticiones 5
Requiere python3, node y un JDK (javac y java) en el PATH.
"""
import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
from datetime import datetime
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
AQUI = RAIZ / "comparativo"
VOLUMENES = ["pequenas", "medias", "grandes"]
MODOS = ["secuencial", "concurrente"]
LENGUAJES = ["python", "javascript", "java"]
RE_TIEMPO = re.compile(r"\((secuencial|concurrente)\):\s*([\d.,]+)\s*ms")


def compilar_java() -> Path:
    """Compila la versión Java en comparativo/.java-out (si javac está disponible)."""
    salida = AQUI / ".java-out"
    fuentes = [str(p) for p in (RAIZ / "java" / "src").rglob("*.java")]
    if salida.exists():
        shutil.rmtree(salida)
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(salida), *fuentes], check=True)
    return salida


def comando(lenguaje: str, entrada: Path, salida: Path, modo: str, clases_java: Path) -> tuple[list[str], Path]:
    args = ["--cli", "--entrada", str(entrada), "--salida", str(salida), "--modo", modo]
    if lenguaje == "python":
        return [sys.executable, "-m", "src.main", *args], RAIZ / "python"
    if lenguaje == "javascript":
        return ["node", "src/main.js", *args], RAIZ / "javascript"
    return ["java", "-cp", str(clases_java), "mx.uaeh.syllabus.Main", *args], RAIZ / "java"


def normalizar(valor):
    """10 y 10.0 son el mismo valor (JavaScript no distingue enteros de decimales al escribir JSON)."""
    if isinstance(valor, dict):
        return {k: normalizar(v) for k, v in valor.items()}
    if isinstance(valor, list):
        return [normalizar(v) for v in valor]
    if isinstance(valor, (int, float)) and not isinstance(valor, bool):
        return float(valor)
    return valor


def huella_reportes(carpeta: Path) -> dict[str, str]:
    """Huella de cada reporte, normalizada (JSON por contenido, CSV sin BOM ni diferencias de fin de línea)."""
    huellas = {}
    for archivo in sorted(carpeta.iterdir()):
        texto = archivo.read_text(encoding="utf-8-sig")
        if archivo.suffix == ".json":
            texto = json.dumps(normalizar(json.loads(texto)), sort_keys=True, ensure_ascii=False)
        else:
            texto = texto.replace("\r\n", "\n")
        huellas[archivo.name] = hashlib.sha256(texto.encode("utf-8")).hexdigest()[:16]
    return huellas


def resumen_resultados(carpeta: Path) -> dict:
    """Resultados clave que produjo una versión (para mostrarlos lado a lado)."""
    estudiantes = json.loads((carpeta / "estudiantes.json").read_text(encoding="utf-8-sig"))
    errores = json.loads((carpeta / "errores.json").read_text(encoding="utf-8-sig"))
    filas = estudiantes if isinstance(estudiantes, list) else next(iter(estudiantes.values()))
    promedios = [f["promedio"] for f in filas if f.get("promedio") is not None]
    contar = lambda campo, valor: sum(1 for f in filas if str(f.get(campo, "")).upper() == valor)
    return {
        "estudiantes": len(filas),
        "promedio_general": round(sum(float(p) for p in promedios) / len(promedios), 2) if promedios else None,
        "aprobados": contar("estatus", "APROBADO"),
        "reprobados": contar("estatus", "REPROBADO"),
        "riesgo_alto": contar("riesgo", "ALTO"),
        "riesgo_medio": contar("riesgo", "MEDIO"),
        "rechazados": len(errores) if isinstance(errores, list) else len(errores.get("errores", [])),
    }


def ejecutar(lenguaje, volumen, modo, repeticiones, clases_java):
    entrada = RAIZ / "datos" / f"solicitudes_{volumen}.json"
    calculos, totales = [], []
    carpeta = None
    for _ in range(repeticiones):
        carpeta = Path(tempfile.mkdtemp(prefix=f"syllabus_{lenguaje}_"))
        cmd, cwd = comando(lenguaje, entrada, carpeta, modo, clases_java)
        inicio = time.perf_counter()
        proceso = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace")
        totales.append((time.perf_counter() - inicio) * 1000)
        if proceso.returncode != 0:
            raise RuntimeError(f"{lenguaje} {volumen} {modo} falló:\n{proceso.stderr}")
        encontrado = RE_TIEMPO.search(proceso.stdout)
        calculos.append(float(encontrado.group(2).replace(",", ".")) if encontrado else None)
    datos = {
        "calculo_ms": round(statistics.median([c for c in calculos if c is not None]), 2),
        "total_ms": round(statistics.median(totales), 1),
        "huellas": huella_reportes(carpeta),
        "resultados": resumen_resultados(carpeta),
    }
    shutil.rmtree(carpeta, ignore_errors=True)
    return datos


# ---------------------------------------------------------------- líneas de código
EXTENSIONES = {"python": {".py"}, "javascript": {".js", ".html", ".css"}, "java": {".java"}}


def lineas_de_codigo(archivo: Path) -> int:
    """Líneas no vacías que no son comentario (aproximado: #, //, /* */, docstrings de Python)."""
    n, en_bloque, en_doc = 0, False, False
    for linea in archivo.read_text(encoding="utf-8", errors="replace").splitlines():
        t = linea.strip()
        if not t:
            continue
        if archivo.suffix == ".py":
            if en_doc:
                en_doc = '"""' not in t
                continue
            if t.startswith('"""'):
                en_doc = t.count('"""') == 1
                continue
            if t.startswith("#"):
                continue
        else:
            if en_bloque:
                en_bloque = "*/" not in t
                continue
            if t.startswith("/*") or t.startswith("<!--"):
                en_bloque = "*/" not in t and "-->" not in t
                continue
            if t.startswith("//") or t.startswith("*"):
                continue
        n += 1
    return n


def contar_lineas() -> dict:
    conteo = {}
    for lenguaje in LENGUAJES:
        base = RAIZ / lenguaje
        partes = {"codigo": base / "src", "pruebas": base / "tests"}
        datos = {}
        for nombre, carpeta in partes.items():
            archivos = [p for p in carpeta.rglob("*") if p.suffix in EXTENSIONES[lenguaje] and "__pycache__" not in p.parts]
            datos[nombre] = sum(lineas_de_codigo(p) for p in archivos)
            datos[f"archivos_{nombre}"] = len(archivos)
        if lenguaje == "javascript":
            web = [p for p in (base / "src" / "web").rglob("*") if p.is_file()]
            datos["interfaz"] = sum(lineas_de_codigo(p) for p in web)
        elif lenguaje == "python":
            datos["interfaz"] = sum(lineas_de_codigo(base / "src" / n) for n in ("interfaz.py", "vistas.py", "iconos.py"))
        else:
            datos["interfaz"] = sum(lineas_de_codigo(p) for p in (base / "src" / "mx" / "uaeh" / "syllabus" / "ui").glob("*.java"))
        datos["nucleo"] = datos["codigo"] - datos["interfaz"]
        conteo[lenguaje] = datos
    return conteo


def contar_pruebas() -> dict:
    """Corre las pruebas automatizadas de Python y JavaScript y cuenta las de Java (requiere Maven para correrlas)."""
    resultado = {}
    py = subprocess.run([sys.executable, "-m", "unittest", "discover", "-s", "tests", "-t", "."], cwd=RAIZ / "python",
                        capture_output=True, text=True)
    m = re.search(r"Ran (\d+) tests", py.stderr)
    resultado["python"] = {"pruebas": int(m.group(1)) if m else None, "ok": py.returncode == 0}
    js = subprocess.run(["npm", "test", "--silent"], cwd=RAIZ / "javascript", capture_output=True, text=True,
                        shell=os.name == "nt")
    p = re.search(r"# pass (\d+)", js.stdout)
    f = re.search(r"# fail (\d+)", js.stdout)
    resultado["javascript"] = {"pruebas": int(p.group(1)) if p else None, "ok": js.returncode == 0 and f and f.group(1) == "0"}
    total = sum(t.read_text(encoding="utf-8").count("@Test") for t in (RAIZ / "java" / "tests").rglob("*.java"))
    ok = None
    if shutil.which("mvn"):
        mvn = subprocess.run(["mvn", "-q", "test"], cwd=RAIZ / "java", capture_output=True, text=True, shell=os.name == "nt")
        ok = mvn.returncode == 0
    resultado["java"] = {"pruebas": total, "ok": ok}
    return resultado


def version(cmd: list[str]) -> str:
    try:
        p = subprocess.run(cmd, capture_output=True, text=True)
        texto = (p.stdout or p.stderr).strip().splitlines()
        return next((x for x in texto if "JAVA_TOOL" not in x), "")
    except OSError:
        return "no disponible"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repeticiones", type=int, default=3)
    parser.add_argument("--rapido", action="store_true", help="sin el volumen grande y con 1 repetición")
    args = parser.parse_args()
    volumenes = VOLUMENES[:2] if args.rapido else VOLUMENES
    repeticiones = 1 if args.rapido else args.repeticiones

    print("Compilando la versión Java…")
    clases_java = compilar_java()
    tiempos, paridad, resultados = {}, {}, {}
    for volumen in volumenes:
        for modo in MODOS:
            huellas = {}
            for lenguaje in LENGUAJES:
                print(f"  {volumen:<9} {modo:<12} {lenguaje:<11}", end="", flush=True)
                d = ejecutar(lenguaje, volumen, modo, repeticiones, clases_java)
                tiempos.setdefault(lenguaje, {}).setdefault(volumen, {})[modo] = {"calculo_ms": d["calculo_ms"], "total_ms": d["total_ms"]}
                huellas[lenguaje] = d["huellas"]
                if modo == "secuencial":
                    resultados.setdefault(volumen, {})[lenguaje] = d["resultados"]
                print(f"cálculo {d['calculo_ms']:>10.2f} ms   total {d['total_ms']:>9.1f} ms")
            iguales = all(huellas[l] == huellas["python"] for l in LENGUAJES)
            paridad[f"{volumen}/{modo}"] = {"reportes": len(huellas["python"]), "identicos": iguales}
            print(f"  → reportes idénticos entre lenguajes: {'sí' if iguales else 'NO'}")

    print("Contando líneas y corriendo pruebas…")
    datos_entrada = {}
    for v in volumenes:
        d = json.loads((RAIZ / "datos" / f"solicitudes_{v}.json").read_text(encoding="utf-8"))
        datos_entrada[v] = {k: len(x) for k, x in d.items()}
    valoracion = json.loads((AQUI / "valoracion.json").read_text(encoding="utf-8"))
    salida = {
        "generado": datetime.now().strftime("%Y-%m-%d %H:%M"),
        "equipo": {
            "sistema": f"{platform.system()} {platform.release()} ({platform.machine()})",
            "nucleos": os.cpu_count(),
            "python": platform.python_version(),
            "node": version(["node", "--version"]),
            "java": version(["java", "-version"]),
        },
        "repeticiones": repeticiones,
        "datos": datos_entrada,
        "tiempos": tiempos,
        "paridad": paridad,
        "resultados": resultados,
        "lineas": contar_lineas(),
        "pruebas": contar_pruebas(),
        "valoracion": valoracion,
    }
    (AQUI / "resultados.json").write_text(json.dumps(salida, ensure_ascii=False, indent=2), encoding="utf-8")
    (AQUI / "resultados.js").write_text("window.RESULTADOS = " + json.dumps(salida, ensure_ascii=False, indent=2) + ";\n",
                                         encoding="utf-8")
    print("Listo: comparativo/resultados.json y comparativo/resultados.js (abre comparativo/tablero.html)")


if __name__ == "__main__":
    main()
