from collections import defaultdict
from decimal import Decimal

from .calculos import estadisticas, mostrar, ordenar
from .concurrencia import calcular_resumenes


def _m(x) -> Decimal | None:
    return None if x is None else mostrar(x)


def fila_estudiante(r: dict) -> dict:
    return {"grupo": r["grupo"], "num_cuenta": r["num_cuenta"], "apellidos": r["apellidos"], "nombre": r["nombre"],
            "promedio": _m(r["general"]), "estatus": r["estatus"], "riesgo": r["riesgo"] or "PENDIENTE",
            "motivo": r["motivo"]}


def resumen_grupos(resumenes: list[dict]) -> list[dict]:
    grupos = defaultdict(list)
    for r in resumenes:
        grupos[r["grupo"]].append(r)
    filas = []
    for g in sorted(grupos):
        cerrados = [r["general"] for r in grupos[g] if r["general"] is not None]
        est = estadisticas(cerrados)
        filas.append({"grupo": g, "estudiantes": len(grupos[g]), "promedio": est["promedio"],
                      "maximo": est["maximo"], "minimo": est["minimo"], "aprobacion": est["aprobacion"],
                      "pendientes": len(grupos[g]) - len(cerrados)})
    return filas


def filas_por_curso(resumenes: list[dict]) -> list[dict]:
    filas = []
    for r in resumenes:
        for c in r["cursos"]:
            p = c["parciales"] + [None] * (3 - len(c["parciales"]))
            filas.append({"grupo": c["grupo"], "curso_id": c["curso_id"], "asignatura": c["asignatura"],
                          "num_cuenta": r["num_cuenta"], "apellidos": r["apellidos"], "nombre": r["nombre"],
                          "parcial_1": _m(p[0]), "parcial_2": _m(p[1]), "parcial_3": _m(p[2]),
                          "promedio": _m(c["bruto"]), "estatus": c["estatus"]})
    por_curso = defaultdict(list)
    for f in filas:
        por_curso[(f["grupo"], f["asignatura"], f["curso_id"])].append(f)
    return [f for k in sorted(por_curso) for f in ordenar(por_curso[k])]


def estadisticas_cursos(gestor, resumenes: list[dict]) -> list[dict]:
    brutos = defaultdict(list)
    inscritos = defaultdict(int)
    for r in resumenes:
        for c in r["cursos"]:
            inscritos[c["curso_id"]] += 1
            if c["bruto"] is not None:
                brutos[c["curso_id"]].append(c["bruto"])
    filas = []
    for c in sorted(gestor.cursos.values(), key=lambda c: (c.grupo, c.asignatura)):
        est = estadisticas(brutos[c.id])
        docente = gestor.docente_de(c.id) if hasattr(gestor, "docente_de") else None
        filas.append({"curso_id": c.id, "asignatura": c.asignatura, "grupo": c.grupo,
                      "docente": docente.nombre if docente else "",
                      "inscritos": inscritos[c.id], "promedio": est["promedio"], "maximo": est["maximo"],
                      "minimo": est["minimo"], "aprobados": est["aprobados"], "reprobados": est["reprobados"],
                      "aprobacion": est["aprobacion"]})
    return filas


def kardex(gestor, resumenes: list[dict]) -> list[dict]:
    filas = []
    for r in ordenar(resumenes):
        for c in r["cursos"]:
            for p in gestor.parciales_de(c["curso_id"]):
                calif = gestor.calif_parcial(r["num_cuenta"], p.id)
                filas.append({"num_cuenta": r["num_cuenta"], "apellidos": r["apellidos"], "nombre": r["nombre"],
                              "curso_id": c["curso_id"], "asignatura": c["asignatura"], "parcial": p.numero,
                              "ponderacion": f"{round(p.peso * 100)}%", "calificacion": _m(calif),
                              "aporte": None if calif is None else mostrar(calif * p.peso)})
            filas.append({"num_cuenta": r["num_cuenta"], "apellidos": r["apellidos"], "nombre": r["nombre"],
                          "curso_id": c["curso_id"], "asignatura": c["asignatura"], "parcial": "Promedio",
                          "ponderacion": "100%", "calificacion": None, "aporte": _m(c["bruto"])})
    return filas


def generar_todos(gestor, modo: str = "secuencial", resumenes: list[dict] | None = None) -> dict[str, list[dict]]:
    resumenes = resumenes if resumenes is not None else calcular_resumenes(gestor, modo)
    estudiantes = ordenar([fila_estudiante(r) for r in resumenes])
    return {
        "resumen_grupos": resumen_grupos(resumenes),
        "estudiantes": sorted(estudiantes, key=lambda f: f["grupo"]),
        "parciales_por_asignatura": filas_por_curso(resumenes),
        "estadisticas_cursos": estadisticas_cursos(gestor, resumenes),
        "riesgo_academico": [f for f in estudiantes if f["riesgo"] in ("ALTO", "MEDIO")],
        "kardex": kardex(gestor, resumenes),
    }
