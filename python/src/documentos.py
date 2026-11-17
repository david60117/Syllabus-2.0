"""Reportes institucionales, como en un sistema de control escolar real.

Cada reporte es un documento con encabezado (institución, programa, ciclo, título, datos del alcance,
fecha de emisión y quién lo emite), cuerpo en tabla, resumen y, si aplica, firmas y nota.

    Por alumno:   boleta de calificaciones · kárdex (historial individual)
    Por materia:  acta de calificaciones
    Por grupo:    concentrado de calificaciones
    Control:      aprovechamiento por grupo y asignatura · riesgo académico · aprobados y reprobados
"""
from datetime import datetime

from .calculos import (calificacion_final, clave_texto, estadisticas, evaluar_estatus_curso, mostrar, ordenar,
                       promedio_finales)

ESTADOS = {"APROBADO": "Aprobado", "REPROBADO": "Reprobado", "PENDIENTE": "Pendiente"}
RIESGOS = {"ALTO": "Alto", "MEDIO": "Medio", "SIN_RIESGO": "Sin riesgo", None: "Pendiente"}


def ciclo_actual(hoy: datetime | None = None) -> str:
    hoy = hoy or datetime.now()
    return f"Enero – Junio {hoy.year}" if hoy.month <= 6 else f"Julio – Diciembre {hoy.year}"


NOTA_REDONDEO = ("Parciales y promedio con dos decimales. Calificación final asentada: si el promedio es 7.0 o más se "
                 "redondea al entero más próximo (.5 sube); si es menor a 7.0 no se redondea hacia arriba (6.9 queda en 6) y la "
                 "materia queda reprobada (examen extraordinario).")

INSTITUCION = {"institucion": "Institución educativa", "programa": "Licenciatura en Ingeniería de Software",
               "ciclo": ciclo_actual()}

# reporte → (título, alcance, roles que lo pueden emitir)
CATALOGO = {
    "boleta": ("Boleta de calificaciones", "alumno", ("Administrador", "Alumno")),
    "kardex": ("Kárdex académico", "alumno", ("Administrador", "Alumno")),
    "acta": ("Acta de calificaciones", "materia", ("Administrador", "Docente")),
    "concentrado": ("Concentrado de calificaciones por grupo", "grupo", ("Administrador",)),
    "aprovechamiento": ("Aprovechamiento por grupo y asignatura", "grupo_opcional", ("Administrador", "Docente")),
    "riesgo": ("Alumnos en riesgo académico", "grupo_opcional", ("Administrador",)),
    "aprobacion": ("Aprobados y reprobados", "materia_opcional", ("Administrador", "Docente")),
    "credenciales": ("Usuarios y contraseñas", "usuarios", ("Administrador",)),
}


def _m(x):
    return None if x is None else mostrar(x)


def _documento(tipo, institucion, campos, columnas, filas, resumen, emitido_por, firmas=(), nota=""):
    inst = {**INSTITUCION, **(institucion or {})}
    return {"tipo": tipo, "titulo": CATALOGO[tipo][0],
            "encabezado": {"institucion": inst["institucion"], "programa": inst["programa"], "ciclo": inst["ciclo"],
                           "titulo": CATALOGO[tipo][0], "campos": list(campos),
                           "emision": datetime.now().strftime("%d/%m/%Y %H:%M"), "emitido_por": emitido_por},
            "columnas": list(columnas), "filas": filas, "resumen": list(resumen), "firmas": list(firmas), "nota": nota}


def _ponderacion(gestor, curso_id) -> str:
    return " / ".join(f"P{p.numero} {p.peso * 100:g} %" for p in gestor.parciales_de(curso_id)) or "—"


def _docente(gestor, curso_id) -> str:
    d = gestor.docente_de(curso_id)
    return d.nombre if d else "Sin asignar"


def _parciales(c) -> dict:
    p = (c["parciales"] + [None] * 3)[:3]
    return {"p1": _m(p[0]), "p2": _m(p[1]), "p3": _m(p[2])}


def _datos_alumno(r) -> list:
    return [("Alumno", f"{r['apellidos']} {r['nombre']}"), ("Número de cuenta", r["num_cuenta"]), ("Grupo", r["grupo"])]


def _resumen_alumno(r) -> list:
    cerrados = [c for c in r["cursos"] if c["bruto"] is not None]
    aprobadas = sum(1 for c in cerrados if c["estatus"] == "APROBADO")
    return [("Promedio general", _m(r["general"])), ("Materias aprobadas", aprobadas),
            ("Materias reprobadas", len(cerrados) - aprobadas), ("Materias pendientes", r["pendientes"]),
            ("Riesgo académico", RIESGOS.get(r["riesgo"], r["riesgo"]))]


# ---------------------------------------------------------------- por alumno
def boleta(gestor, num_cuenta, institucion=None, emitido_por=""):
    """Calificaciones del alumno en el periodo: parciales y final de cada materia."""
    r = gestor.resumen_estudiante(num_cuenta)
    filas = [{"clave": c["curso_id"], "asignatura": c["asignatura"], "docente": _docente(gestor, c["curso_id"]),
              **_parciales(c), "promedio": _m(c["bruto"]), "final": calificacion_final(c["bruto"]),
              "estado": ESTADOS[c["estatus"]]} for c in r["cursos"]]
    columnas = [("clave", "Clave"), ("asignatura", "Asignatura"), ("docente", "Docente"), ("p1", "P1"), ("p2", "P2"),
                ("p3", "P3"), ("promedio", "Promedio"), ("final", "Final"), ("estado", "Estado")]
    return _documento("boleta", institucion, _datos_alumno(r), columnas, filas, _resumen_alumno(r), emitido_por,
                      nota=NOTA_REDONDEO + " Las materias pendientes aún tienen evaluaciones sin capturar.")


def kardex(gestor, num_cuenta, institucion=None, emitido_por=""):
    """Historial académico individual: solo las materias y calificaciones finales de ese alumno."""
    inst = {**INSTITUCION, **(institucion or {})}
    r = gestor.resumen_estudiante(num_cuenta)
    filas = [{"no": i, "clave": c["curso_id"], "asignatura": c["asignatura"], "ciclo": inst["ciclo"],
              "tipo": "Ordinario", "promedio": _m(c["bruto"]), "final": calificacion_final(c["bruto"]),
              "estado": "En curso" if c["bruto"] is None else ESTADOS[c["estatus"]]}
             for i, c in enumerate(r["cursos"], 1)]
    columnas = [("no", "No."), ("clave", "Clave"), ("asignatura", "Asignatura"), ("ciclo", "Ciclo"),
                ("tipo", "Tipo de evaluación"), ("promedio", "Promedio"), ("final", "Calificación final"), ("estado", "Estado")]
    reprobadas = sum(1 for c in r["cursos"] if c["bruto"] is not None and c["estatus"] == "REPROBADO")
    campos = _datos_alumno(r) + [("Situación", "Irregular" if reprobadas else "Regular")]
    resumen = [("Materias cursadas", len(r["cursos"])),
               ("Promedio de calificaciones finales", promedio_finales([c["bruto"] for c in r["cursos"]]))]
    resumen += [x for x in _resumen_alumno(r) if x[0] != "Promedio general"]
    return _documento("kardex", institucion, campos, columnas, filas, resumen, emitido_por, firmas=["Control escolar"],
                      nota=NOTA_REDONDEO + " Documento informativo; no sustituye al certificado oficial de estudios.")


# ---------------------------------------------------------------- por materia
def acta(gestor, curso_id, institucion=None, emitido_por=""):
    """Acta de calificaciones de una materia: todos sus alumnos con parciales, final y estado."""
    c = gestor.cursos[curso_id]
    filas = []
    for i, e in enumerate(gestor.alumnos_de_curso(curso_id), 1):
        ps = [gestor.calif_parcial(e.num_cuenta, p.id) for p in gestor.parciales_de(curso_id)]
        final = gestor.promedio_curso(e.num_cuenta, curso_id)
        estado = "PENDIENTE" if final is None else evaluar_estatus_curso(final)
        filas.append({"no": i, "num_cuenta": e.num_cuenta, "alumno": e.nombre_completo, **_parciales({"parciales": ps}),
                      "promedio": _m(final), "final": calificacion_final(final), "estado": ESTADOS[estado], "_bruto": final})
    est = estadisticas([f["_bruto"] for f in filas if f["_bruto"] is not None])
    for f in filas:
        del f["_bruto"]
    campos = [("Asignatura", c.asignatura), ("Clave", c.id), ("Grupo", c.grupo), ("Docente", _docente(gestor, curso_id)),
              ("Ponderación", _ponderacion(gestor, curso_id))]
    columnas = [("no", "No."), ("num_cuenta", "Número de cuenta"), ("alumno", "Nombre del alumno"), ("p1", "P1"),
                ("p2", "P2"), ("p3", "P3"), ("promedio", "Promedio"), ("final", "Final"), ("estado", "Estado")]
    resumen = [("Inscritos", len(filas)), ("Aprobados", est["aprobados"]), ("Reprobados", est["reprobados"]),
               ("Pendientes", len(filas) - est["estudiantes"]), ("Promedio", est["promedio"]),
               ("Aprobación", None if est["aprobacion"] is None else f"{est['aprobacion']} %")]
    return _documento("acta", institucion, campos, columnas, filas, resumen, emitido_por,
                      firmas=[f"Docente: {_docente(gestor, curso_id)}", "Control escolar"], nota=NOTA_REDONDEO)


# ---------------------------------------------------------------- por grupo
def concentrado(gestor, grupo, institucion=None, emitido_por="", orden="apellidos"):
    """Sábana del grupo: una columna por materia con la calificación final de cada alumno."""
    cursos = gestor.cursos_de_grupo(grupo)
    resumenes = [gestor.resumen_estudiante(e.num_cuenta) for e in gestor.estudiantes.values() if e.grupo == grupo]
    filas = []
    for r in resumenes:
        finales = {c["curso_id"]: calificacion_final(c["bruto"]) for c in r["cursos"]}
        filas.append({"num_cuenta": r["num_cuenta"], "apellidos": r["apellidos"], "nombre": r["nombre"],
                      **{c.id: finales.get(c.id) for c in cursos}, "promedio": _m(r["general"]),
                      "estado": ESTADOS[r["estatus"]], "riesgo": RIESGOS.get(r["riesgo"], r["riesgo"])})
    filas = ordenar(filas, "promedio", True) if orden == "promedio" else ordenar(filas)
    filas = [{"no": i, "num_cuenta": f["num_cuenta"], "alumno": f"{f['apellidos']} {f['nombre']}",
              **{k: v for k, v in f.items() if k not in ("num_cuenta", "apellidos", "nombre")}} for i, f in enumerate(filas, 1)]
    columnas = ([("no", "No."), ("num_cuenta", "Número de cuenta"), ("alumno", "Nombre del alumno")]
                + [(c.id, c.id) for c in cursos] + [("promedio", "Promedio"), ("estado", "Estado"), ("riesgo", "Riesgo")])
    est = estadisticas([r["general"] for r in resumenes if r["general"] is not None])
    resumen = [("Alumnos", len(filas)), ("Materias", len(cursos)), ("Promedio del grupo", est["promedio"]),
               ("Máxima", est["maximo"]), ("Mínima", est["minimo"]),
               ("Aprobación", None if est["aprobacion"] is None else f"{est['aprobacion']} %")]
    datos_grupo = getattr(gestor, "grupos_registro", {}).get(grupo)
    campos = [("Grupo", grupo)] + ([("Semestre", f"{datos_grupo.semestre}.º"), ("Turno", datos_grupo.turno)] if datos_grupo else [])
    campos.append(("Orden", "Por promedio (mayor a menor)" if orden == "promedio" else "Por apellidos"))
    claves = "   ".join(f"{c.id}: {c.asignatura}" for c in cursos)
    return _documento("concentrado", institucion, campos, columnas, filas, resumen, emitido_por,
                      nota=f"Materias — {claves}. Por materia se muestra la calificación final asentada (entera); el promedio usa "
                           "los promedios con dos decimales de cada materia.")


# ---------------------------------------------------------------- control
def aprovechamiento(gestor, cursos, institucion=None, emitido_por="", grupo=None):
    """Estadísticas por materia (promedio, máxima, mínima y aprobación) y totales generales."""
    cursos = sorted((c for c in cursos if grupo in (None, c.grupo)), key=lambda c: (c.grupo, clave_texto(c.asignatura)))
    filas, todos = [], []
    for c in cursos:
        finales = [f for f in (gestor.promedio_curso(e.num_cuenta, c.id) for e in gestor.alumnos_de_curso(c.id)) if f is not None]
        todos += finales
        est = estadisticas(finales)
        filas.append({"grupo": c.grupo, "clave": c.id, "asignatura": c.asignatura, "docente": _docente(gestor, c.id),
                      "inscritos": len(gestor.alumnos_de_curso(c.id)), "promedio": est["promedio"], "maximo": est["maximo"],
                      "minimo": est["minimo"], "aprobados": est["aprobados"], "reprobados": est["reprobados"],
                      "aprobacion": est["aprobacion"]})
    columnas = [("grupo", "Grupo"), ("clave", "Clave"), ("asignatura", "Asignatura"), ("docente", "Docente"),
                ("inscritos", "Insc."), ("promedio", "Prom."), ("maximo", "Máx."), ("minimo", "Mín."),
                ("aprobados", "Aprob."), ("reprobados", "Reprob."), ("aprobacion", "% aprob.")]
    est = estadisticas(todos)
    resumen = [("Materias", len(filas)), ("Calificaciones finales", est["estudiantes"]), ("Promedio general", est["promedio"]),
               ("Máxima", est["maximo"]), ("Mínima", est["minimo"]),
               ("Aprobación", None if est["aprobacion"] is None else f"{est['aprobacion']} %")]
    return _documento("aprovechamiento", institucion, [("Grupo", grupo or "Todos")], columnas, filas, resumen, emitido_por)


def riesgo(gestor, institucion=None, emitido_por="", grupo=None):
    resumenes = [gestor.resumen_estudiante(n) for n, e in gestor.estudiantes.items() if grupo in (None, e.grupo)]
    filas = [{"num_cuenta": r["num_cuenta"], "apellidos": r["apellidos"], "nombre": r["nombre"], "grupo": r["grupo"],
              "promedio": _m(r["general"]), "riesgo": RIESGOS[r["riesgo"]], "motivo": r["motivo"],
              "reprobadas": ", ".join(c["curso_id"] for c in r["cursos"] if c["bruto"] is not None and c["estatus"] == "REPROBADO") or "—"}
             for r in resumenes if r["riesgo"] in ("ALTO", "MEDIO")]
    filas = sorted(ordenar(filas), key=lambda f: (f["grupo"], f["riesgo"] != "Alto"))
    filas = [{"no": i, "num_cuenta": f["num_cuenta"], "alumno": f"{f['apellidos']} {f['nombre']}",
              **{k: f[k] for k in ("grupo", "promedio", "riesgo", "motivo", "reprobadas")}} for i, f in enumerate(filas, 1)]
    columnas = [("no", "No."), ("num_cuenta", "Número de cuenta"), ("alumno", "Nombre del alumno"), ("grupo", "Grupo"),
                ("promedio", "Promedio"), ("riesgo", "Riesgo"), ("motivo", "Motivo"), ("reprobadas", "Materias reprobadas")]
    resumen = [("Alumnos evaluados", len(resumenes)), ("Riesgo alto", sum(f["riesgo"] == "Alto" for f in filas)),
               ("Riesgo medio", sum(f["riesgo"] == "Medio" for f in filas))]
    return _documento("riesgo", institucion, [("Grupo", grupo or "Todos")], columnas, filas, resumen, emitido_por,
                      nota="Alto: alguna materia reprobada o promedio general menor a 7.0. Medio: promedio general entre 7.0 y 7.5.")


def aprobacion(gestor, cursos, institucion=None, emitido_por="", curso_id=None, estado="Todos"):
    cursos = [c for c in cursos if curso_id in (None, c.id)]
    filas = []
    for c in sorted(cursos, key=lambda c: (c.grupo, clave_texto(c.asignatura))):
        for e in gestor.alumnos_de_curso(c.id):
            final = gestor.promedio_curso(e.num_cuenta, c.id)
            est = ESTADOS["PENDIENTE" if final is None else evaluar_estatus_curso(final)]
            if estado in ("Todos", est):
                filas.append({"grupo": c.grupo, "asignatura": c.asignatura, "num_cuenta": e.num_cuenta,
                              "alumno": e.nombre_completo, "promedio": _m(final), "final": calificacion_final(final),
                              "estado": est})
    columnas = [("grupo", "Grupo"), ("asignatura", "Asignatura"), ("num_cuenta", "Número de cuenta"),
                ("alumno", "Nombre del alumno"), ("promedio", "Promedio"), ("final", "Final"), ("estado", "Estado")]
    resumen = [("Registros", len(filas))] + [(e, sum(f["estado"] == e for f in filas)) for e in ("Aprobado", "Reprobado", "Pendiente")]
    materia = next((f"{c.id} · {c.asignatura} ({c.grupo})" for c in cursos if c.id == curso_id), "Todas")
    return _documento("aprobacion", institucion, [("Materia", materia), ("Estado", estado)], columnas, filas, resumen, emitido_por)


# ---------------------------------------------------------------- cuentas de acceso
def credenciales(gestor, usuarios, filtro="Todos", institucion=None, emitido_por=""):
    """Usuarios y contraseñas por grupo (alumnos), de docentes o de todos. Solo se muestra la contraseña
    inicial; las que cambió cada usuario están cifradas y aparecen como personalizadas."""
    from .autenticacion import contrasena_visible
    filas = []
    for u in usuarios:
        ref = u.get("referencia")
        if u["rol"] == "Alumno":
            e = gestor.estudiantes.get(ref)
            grupo, detalle = (e.grupo if e else "—"), (e.grupo if e else "—")
        elif u["rol"] == "Docente":
            grupo, detalle = None, ", ".join(c.id for c in gestor.cursos_de_docente(ref)) or "Sin materias"
        else:
            grupo, detalle = None, "Control escolar"
        if filtro == "Docentes" and u["rol"] != "Docente" or filtro.startswith("Grupo ") and grupo != filtro[6:]:
            continue
        visible = contrasena_visible(u)
        filas.append({"usuario": u["usuario"], "nombre": u["nombre"], "rol": u["rol"], "detalle": detalle,
                      "contrasena": visible or "Personalizada", "estado": "Contraseña inicial" if visible else "Activa"})
    orden = {"Administrador": 0, "Docente": 1, "Alumno": 2}
    filas.sort(key=lambda f: (orden[f["rol"]], f["detalle"] if f["rol"] == "Alumno" else "", clave_texto(f["nombre"])))
    filas = [{"no": i, **f} for i, f in enumerate(filas, 1)]
    columnas = [("no", "No."), ("usuario", "Usuario"), ("nombre", "Nombre"), ("rol", "Rol"),
                ("detalle", "Grupo / materias"), ("contrasena", "Contraseña"), ("estado", "Estado")]
    iniciales = sum(f["estado"] == "Contraseña inicial" for f in filas)
    resumen = [("Cuentas", len(filas)), ("Con contraseña inicial", iniciales), ("Personalizadas", len(filas) - iniciales)]
    return _documento("credenciales", institucion, [("Usuarios", filtro)], columnas, filas, resumen, emitido_por,
                      firmas=["Control escolar"],
                      nota="CONFIDENCIAL. Entregar a cada usuario solo su renglón. La contraseña inicial se debe cambiar al primer "
                           "ingreso; las contraseñas personalizadas están cifradas y no se pueden consultar (se restablecen en Usuarios).")


# ---------------------------------------------------------------- exportación
def a_tabla(doc) -> list[list]:
    """El documento como renglones de CSV: encabezado, tabla, resumen y firmas (lo que se ve impreso)."""
    enc = doc["encabezado"]
    renglones = [[enc["institucion"]], [enc["programa"]], [enc["titulo"].upper()], ["Ciclo escolar", enc["ciclo"]]]
    renglones += [[etiqueta, valor] for etiqueta, valor in enc["campos"]]
    renglones += [["Fecha de emisión", enc["emision"]], ["Emitido por", enc["emitido_por"]], []]
    renglones.append([titulo for _, titulo in doc["columnas"]])
    renglones += [[f.get(clave) for clave, _ in doc["columnas"]] for f in doc["filas"]]
    renglones += [[], ["RESUMEN"]] + [[etiqueta, valor] for etiqueta, valor in doc["resumen"]]
    if doc["firmas"]:
        renglones += [[]] + [[f, "_______________________________"] for f in doc["firmas"]]
    if doc["nota"]:
        renglones += [[], [doc["nota"]]]
    return renglones


def a_json(doc) -> dict:
    enc = doc["encabezado"]
    return {"encabezado": {"institucion": enc["institucion"], "programa": enc["programa"], "reporte": enc["titulo"],
                           "ciclo_escolar": enc["ciclo"], **{clave_texto(k).replace(" ", "_"): v for k, v in enc["campos"]},
                           "fecha_emision": enc["emision"], "emitido_por": enc["emitido_por"]},
            "columnas": [{"clave": k, "titulo": t} for k, t in doc["columnas"]], "registros": doc["filas"],
            "resumen": {clave_texto(k).replace(" ", "_"): v for k, v in doc["resumen"]},
            "firmas": doc["firmas"], "nota": doc["nota"]}


def nombre_archivo(doc) -> str:
    campos = dict(doc["encabezado"]["campos"])
    sufijo = campos.get("Número de cuenta") or campos.get("Clave") or campos.get("Grupo") or ""
    return f"{doc['tipo']}_{sufijo}".rstrip("_").replace(" ", "_")
