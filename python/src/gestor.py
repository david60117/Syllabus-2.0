from collections import defaultdict

from . import validacion as v
from .calculos import clave_texto, evaluar_estatus_curso, evaluar_riesgo, limpiar10, motivo_riesgo
from .errores import ErrorValidacion
from .eventos import BusEventos
from .modelos import Curso, Docente, Estudiante, Evaluacion, Grupo, Parcial

PESOS_PARCIALES = (0.3, 0.3, 0.4)
TURNOS = ("Matutino", "Vespertino")

UNO = limpiar10(1.0)


class Calculadora:
    """Solo datos y cálculos (sin eventos), para poder enviarla a otros procesos."""

    def __init__(self):
        self.estudiantes: dict[str, Estudiante] = {}
        self.cursos: dict[str, Curso] = {}
        self.parciales: dict[str, Parcial] = {}
        self.evaluaciones: dict[str, Evaluacion] = {}
        self.inscripciones: set[tuple[str, str]] = set()
        self.calificaciones: dict[tuple[str, str], float] = {}
        self._indices = None
        self._version = 0

    def _cambio(self) -> None:
        """Invalida los índices: altas, bajas y ediciones pueden dejar los mismos tamaños con otro contenido."""
        self._version += 1
        self._indices = None

    def _indice(self) -> dict:
        """Índices por curso, parcial y estudiante; se reconstruyen cuando cambian los datos."""
        firma = (self._version, len(self.parciales), len(self.evaluaciones), len(self.inscripciones), len(self.cursos))
        if self._indices is None or self._indices["firma"] != firma:
            parciales, evaluaciones, cursos = {}, {}, {}
            for p in self.parciales.values():
                parciales.setdefault(p.curso_id, []).append(p)
            for e in self.evaluaciones.values():
                evaluaciones.setdefault(e.parcial_id, []).append(e)
            for n, c in self.inscripciones:
                cursos.setdefault(n, []).append(c)
            self._indices = {"firma": firma, "parciales": parciales, "evaluaciones": evaluaciones, "cursos": cursos}
        return self._indices

    def parciales_de(self, curso_id: str) -> list[Parcial]:
        return sorted(self._indice()["parciales"].get(curso_id, []), key=lambda p: p.numero)

    def evaluaciones_de(self, parcial_id: str) -> list[Evaluacion]:
        return list(self._indice()["evaluaciones"].get(parcial_id, []))

    def cursos_de(self, num_cuenta: str) -> list[Curso]:
        return sorted((self.cursos[c] for c in self._indice()["cursos"].get(num_cuenta, []) if c in self.cursos),
                      key=lambda c: c.id)

    def calif_parcial(self, num_cuenta: str, parcial_id: str):
        """Calificación del parcial; None mientras falte capturar alguna evaluación."""
        evals = self.evaluaciones_de(parcial_id)
        if not evals or any((num_cuenta, e.id) not in self.calificaciones for e in evals):
            return None
        return sum(self.calificaciones[(num_cuenta, e.id)] * e.peso for e in evals)

    def promedio_curso(self, num_cuenta: str, curso_id: str):
        parciales = self.parciales_de(curso_id)
        valores = [self.calif_parcial(num_cuenta, p.id) for p in parciales]
        if not parciales or any(x is None for x in valores):
            return None
        return sum(x * p.peso for x, p in zip(valores, parciales))

    def resumen_estudiante(self, num_cuenta: str) -> dict:
        e = self.estudiantes[num_cuenta]
        cursos = []
        for c in self.cursos_de(num_cuenta):
            bruto = self.promedio_curso(num_cuenta, c.id)
            cursos.append({
                "curso_id": c.id, "asignatura": c.asignatura, "grupo": c.grupo,
                "parciales": [self.calif_parcial(num_cuenta, p.id) for p in self.parciales_de(c.id)],
                "bruto": bruto,
                "estatus": "PENDIENTE" if bruto is None else evaluar_estatus_curso(bruto),
            })
        cerrados = [c["bruto"] for c in cursos if c["bruto"] is not None]
        general = sum(cerrados) / len(cerrados) if cerrados else None
        return {
            "num_cuenta": e.num_cuenta, "apellidos": e.apellidos, "nombre": e.nombre, "grupo": e.grupo,
            "cursos": cursos, "general": general,
            "estatus": "PENDIENTE" if general is None else evaluar_estatus_curso(general),
            "riesgo": None if general is None else evaluar_riesgo(cerrados, general),
            "motivo": "Sin cursos cerrados" if general is None else motivo_riesgo(cerrados, general),
            "pendientes": sum(1 for c in cursos if c["bruto"] is None),
        }


class GestorEscolar(Calculadora):
    def __init__(self, bus: BusEventos | None = None):
        super().__init__()
        self.bus = bus or BusEventos()
        self.docentes: dict[str, Docente] = {}
        self.asignaciones: set[tuple[str, str]] = set()  # (docente_id, curso_id)
        self.grupos_registro: dict[str, Grupo] = {}
        self.errores: list[dict] = []

    # ---------- utilidades ----------
    def calculadora(self) -> Calculadora:
        c = Calculadora()
        c.estudiantes, c.cursos, c.parciales = dict(self.estudiantes), dict(self.cursos), dict(self.parciales)
        c.evaluaciones, c.inscripciones = dict(self.evaluaciones), set(self.inscripciones)
        c.calificaciones = dict(self.calificaciones)
        return c

    def _error(self, tipo: str, registro, e: ErrorValidacion) -> None:
        caso = registro.get("caso") if isinstance(registro, dict) else None
        self.errores.append({"caso": caso, "tipo": tipo, "codigo": e.codigo, "detalle": e.detalle, "registro": registro})
        self.bus.emitir("error_validacion", codigo=e.codigo, detalle=e.detalle, tipo=tipo)

    def _procesar(self, tipo: str, registro, accion) -> bool:
        try:
            accion(registro)
            return True
        except ErrorValidacion as e:
            self._error(tipo, registro, e)
            return False

    # ---------- altas individuales (las usa la interfaz) ----------
    def registrar_estudiante(self, r: dict) -> Estudiante:
        v.campos(r, "num_cuenta", "apellidos", "nombre", "grupo")
        nc = v.formato(v.texto(r["num_cuenta"], "num_cuenta"), v.RE_CUENTA, "num_cuenta")
        apellidos = v.formato(v.texto(r["apellidos"], "apellidos"), v.RE_NOMBRE, "apellidos")
        nombre = v.formato(v.texto(r["nombre"], "nombre"), v.RE_NOMBRE, "nombre")
        grupo = v.formato(v.texto(r["grupo"], "grupo"), v.RE_GRUPO, "grupo")
        if nc in self.estudiantes:
            raise ErrorValidacion("ID_DUPLICADO", f"el estudiante {nc} ya está registrado")
        est = Estudiante(nc, apellidos, nombre, grupo)
        self.estudiantes[nc] = est
        self.bus.emitir("estudiante_registrado", detalle=f"{nc} · {est.nombre_completo}")
        return est

    def registrar_curso(self, r: dict) -> Curso:
        v.campos(r, "id", "asignatura", "grupo")
        cid = v.formato(v.texto(r["id"], "id"), v.RE_CURSO, "id de curso")
        asignatura = v.texto(r["asignatura"], "asignatura")
        grupo = v.formato(v.texto(r["grupo"], "grupo"), v.RE_GRUPO, "grupo")
        if cid in self.cursos:
            raise ErrorValidacion("ID_DUPLICADO", f"el curso {cid} ya existe")
        if any(c.asignatura == asignatura and c.grupo == grupo for c in self.cursos.values()):
            raise ErrorValidacion("ID_DUPLICADO", f"{asignatura} ya existe en el grupo {grupo}")
        if not any(e.grupo == grupo for e in self.estudiantes.values()):
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"el grupo {grupo} no tiene estudiantes registrados")
        curso = Curso(cid, asignatura, grupo)
        self.cursos[cid] = curso
        self.bus.emitir("curso_registrado", detalle=f"{cid} · {asignatura} · grupo {grupo}")
        return curso

    def _validar_parcial(self, r: dict) -> Parcial:
        v.campos(r, "id", "curso_id", "numero", "peso")
        pid = v.formato(v.texto(r["id"], "id"), v.RE_PARCIAL, "id de parcial")
        curso_id = v.texto(r["curso_id"], "curso_id")
        numero = v.numero(r["numero"], "numero")
        if numero not in (1, 2, 3):
            raise ErrorValidacion("FORMATO_INVALIDO", f"el número de parcial debe ser 1, 2 o 3: {numero}")
        peso = v.peso(r["peso"])
        if pid in self.parciales:
            raise ErrorValidacion("ID_DUPLICADO", f"el parcial {pid} ya existe")
        if curso_id not in self.cursos:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el curso {curso_id}")
        if any(p.numero == numero for p in self.parciales_de(curso_id)):
            raise ErrorValidacion("ID_DUPLICADO", f"el curso {curso_id} ya tiene el parcial {int(numero)}")
        return Parcial(pid, curso_id, int(numero), peso)

    def registrar_parcial(self, r: dict) -> Parcial:
        """Alta individual: permite completar los 3 parciales poco a poco sin pasar de 1.0."""
        p = self._validar_parcial(r)
        actuales = self.parciales_de(p.curso_id)
        if len(actuales) >= 3 or limpiar10(sum(x.peso for x in actuales) + p.peso) > UNO:
            raise ErrorValidacion("PORCENTAJES_INVALIDOS", f"los parciales de {p.curso_id} superarían 1.0 o 3 parciales")
        self.parciales[p.id] = p
        self.bus.emitir("parcial_registrado", detalle=f"{p.id} · {p.curso_id} · peso {p.peso}")
        return p

    def _validar_evaluacion(self, r: dict) -> Evaluacion:
        v.campos(r, "id", "parcial_id", "nombre", "peso")
        eid = v.formato(v.texto(r["id"], "id"), v.RE_EVALUACION, "id de evaluación")
        parcial_id = v.texto(r["parcial_id"], "parcial_id")
        nombre = v.texto(r["nombre"], "nombre")
        peso = v.peso(r["peso"])
        if eid in self.evaluaciones:
            raise ErrorValidacion("ID_DUPLICADO", f"la evaluación {eid} ya existe")
        if parcial_id not in self.parciales:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el parcial {parcial_id}")
        if any(e.nombre.lower() == nombre.lower() for e in self.evaluaciones_de(parcial_id)):
            raise ErrorValidacion("ID_DUPLICADO", f"{nombre} ya existe en el parcial {parcial_id}")
        return Evaluacion(eid, parcial_id, nombre, peso)

    def registrar_evaluacion(self, r: dict) -> Evaluacion:
        e = self._validar_evaluacion(r)
        if limpiar10(sum(x.peso for x in self.evaluaciones_de(e.parcial_id)) + e.peso) > UNO:
            raise ErrorValidacion("PORCENTAJES_INVALIDOS", f"las evaluaciones de {e.parcial_id} superarían 1.0")
        self.evaluaciones[e.id] = e
        self.bus.emitir("evaluacion_registrada", detalle=f"{e.id} · {e.nombre} · {e.parcial_id}")
        return e

    def inscribir(self, r: dict) -> None:
        v.campos(r, "num_cuenta", "curso_id")
        nc, cid = v.texto(r["num_cuenta"], "num_cuenta"), v.texto(r["curso_id"], "curso_id")
        if nc not in self.estudiantes:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el estudiante {nc}")
        if cid not in self.cursos:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el curso {cid}")
        if self.cursos[cid].grupo != self.estudiantes[nc].grupo:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", "el curso no pertenece al grupo del estudiante")
        if (nc, cid) in self.inscripciones:
            raise ErrorValidacion("INSCRIPCION_DUPLICADA", f"{nc} ya está inscrito en {cid}")
        self.inscripciones.add((nc, cid))
        self.bus.emitir("inscripcion_registrada", detalle=f"{nc} → {cid}")

    def _validar_calificacion(self, r: dict) -> tuple[str, str, float]:
        v.campos(r, "num_cuenta", "evaluacion_id")
        valor = v.calificacion(r.get("valor"))
        nc, eid = v.texto(r["num_cuenta"], "num_cuenta"), v.texto(r["evaluacion_id"], "evaluacion_id")
        if eid not in self.evaluaciones:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe la evaluación {eid}")
        curso_id = self.parciales[self.evaluaciones[eid].parcial_id].curso_id
        if (nc, curso_id) not in self.inscripciones:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"{nc} no está inscrito en {curso_id}")
        return nc, eid, valor

    def capturar_calificacion(self, r: dict) -> float:
        nc, eid, valor = self._validar_calificacion(r)
        if (nc, eid) in self.calificaciones:
            raise ErrorValidacion("ID_DUPLICADO", f"{nc} ya tiene calificación en {eid}")
        self.calificaciones[(nc, eid)] = valor
        self.bus.emitir("calificacion_capturada", detalle=f"{nc} · {eid} · {valor}")
        return valor

    def modificar_calificacion(self, r: dict) -> float:
        nc, eid, valor = self._validar_calificacion(r)
        if (nc, eid) not in self.calificaciones:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"{nc} no tiene calificación en {eid}")
        self.calificaciones[(nc, eid)] = valor
        self.bus.emitir("calificacion_modificada", detalle=f"{nc} · {eid} · {valor}")
        return valor

    def consultar_kardex(self, num_cuenta: str) -> dict:
        if num_cuenta not in self.estudiantes:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el estudiante {num_cuenta}")
        return self.resumen_estudiante(num_cuenta)

    def registrar_docente(self, r: dict) -> Docente:
        v.campos(r, "id", "nombre")
        did = v.formato(v.texto(r["id"], "id"), v.RE_DOCENTE, "id de docente")
        nombre = v.formato(v.texto(r["nombre"], "nombre"), v.RE_NOMBRE, "nombre del docente")
        if did in self.docentes:
            raise ErrorValidacion("ID_DUPLICADO", f"el docente {did} ya existe")
        self.docentes[did] = Docente(did, nombre)
        self.bus.emitir("docente_registrado", detalle=f"{did} · {nombre}")
        return self.docentes[did]

    def asignar_docente(self, r: dict) -> None:
        v.campos(r, "docente_id", "curso_id")
        did, cid = r["docente_id"], r["curso_id"]
        if did not in self.docentes:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el docente {did}")
        if cid not in self.cursos:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el curso {cid}")
        self.asignaciones = {(d, c) for (d, c) in self.asignaciones if c != cid}  # un docente por curso
        self.asignaciones.add((did, cid))
        self.bus.emitir("docente_asignado", detalle=f"{did} → {cid}")

    def cursos_de_docente(self, docente_id: str) -> list[Curso]:
        return sorted((self.cursos[c] for (d, c) in self.asignaciones if d == docente_id and c in self.cursos),
                      key=lambda c: c.id)

    def docente_de(self, curso_id: str):
        return next((self.docentes[d] for (d, c) in self.asignaciones if c == curso_id and d in self.docentes), None)

    # ---------- control escolar: operaciones completas en un solo paso (las usa la interfaz) ----------
    @staticmethod
    def _siguiente(prefijo: str, existentes) -> str:
        """Siguiente ID libre con el formato del sistema: C07, P19, EV43, D03."""
        numeros = [int(x[len(prefijo):]) for x in existentes if x.startswith(prefijo) and x[len(prefijo):].isdigit()]
        return f"{prefijo}{(max(numeros) if numeros else 0) + 1:02d}"

    def siguiente_cuenta(self) -> str:
        numeros = [int(n) for n in self.estudiantes if n.isdigit()]
        return f"{(max(numeros) + 1) if numeros else 500001:06d}"

    def siguiente_docente(self) -> str:
        return self._siguiente("D", self.docentes)

    def grupos(self) -> list[str]:
        return sorted(set(self.grupos_registro) | {e.grupo for e in self.estudiantes.values()} | {c.grupo for c in self.cursos.values()})

    def _sincronizar_grupos(self) -> None:
        """Los grupos que vienen en los datos (estudiantes y cursos) quedan registrados; el semestre se toma
        del primer dígito de la clave (301 → 3) y el turno por omisión es matutino."""
        for clave in self.grupos():
            if clave not in self.grupos_registro:
                self.grupos_registro[clave] = Grupo(clave, int(clave[0]) if clave[:1].isdigit() and clave[0] != "0" else 1, TURNOS[0])

    def _datos_grupo(self, semestre, turno) -> tuple[int, str]:
        try:
            semestre = int(str(semestre).strip())
        except ValueError:
            raise ErrorValidacion("FORMATO_INVALIDO", f"el semestre debe ser un número del 1 al 9: {semestre}") from None
        if not 1 <= semestre <= 9:
            raise ErrorValidacion("FORMATO_INVALIDO", f"el semestre debe ser un número del 1 al 9: {semestre}")
        if turno not in TURNOS:
            raise ErrorValidacion("FORMATO_INVALIDO", f"el turno debe ser {' o '.join(TURNOS)}")
        return semestre, turno

    def alta_grupo(self, clave: str, semestre, turno: str) -> Grupo:
        v.campos({"clave": clave}, "clave")
        clave = v.formato(v.texto(clave, "clave"), v.RE_GRUPO, "clave del grupo (3 dígitos)")
        if clave in self.grupos():
            raise ErrorValidacion("ID_DUPLICADO", f"el grupo {clave} ya existe")
        semestre, turno = self._datos_grupo(semestre, turno)
        self.grupos_registro[clave] = Grupo(clave, semestre, turno)
        self.bus.emitir("grupo_registrado", detalle=f"{clave} · {semestre}.º semestre · {turno}")
        return self.grupos_registro[clave]

    def editar_grupo(self, clave: str, semestre, turno: str) -> Grupo:
        if clave not in self.grupos_registro:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el grupo {clave}")
        semestre, turno = self._datos_grupo(semestre, turno)
        self.grupos_registro[clave] = Grupo(clave, semestre, turno)
        self.bus.emitir("grupo_actualizado", detalle=f"{clave} · {semestre}.º semestre · {turno}")
        return self.grupos_registro[clave]

    def baja_grupo(self, clave: str) -> None:
        if clave not in self.grupos_registro:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el grupo {clave}")
        if any(e.grupo == clave for e in self.estudiantes.values()) or any(c.grupo == clave for c in self.cursos.values()):
            raise ErrorValidacion("OPERACION_NO_PERMITIDA", f"el grupo {clave} tiene alumnos o materias; primero dalos de baja")
        del self.grupos_registro[clave]
        self.bus.emitir("grupo_baja", detalle=clave)

    def _exigir_grupo(self, grupo: str) -> None:
        if grupo not in self.grupos_registro:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"el grupo {grupo} no está registrado; regístralo primero")

    def cursos_de_grupo(self, grupo: str) -> list[Curso]:
        return sorted((c for c in self.cursos.values() if c.grupo == grupo), key=lambda c: c.asignatura)

    def alumnos_de_curso(self, curso_id: str) -> list[Estudiante]:
        return sorted((self.estudiantes[n] for n, c in self.inscripciones if c == curso_id and n in self.estudiantes),
                      key=lambda e: (clave_texto(e.apellidos), clave_texto(e.nombre), e.num_cuenta))

    def _datos_persona(self, apellidos, nombre, grupo) -> tuple[str, str, str]:
        r = {"apellidos": apellidos, "nombre": nombre, "grupo": grupo}
        v.campos(r, "apellidos", "nombre", "grupo")
        return (v.formato(v.texto(apellidos, "apellidos"), v.RE_NOMBRE, "apellidos"),
                v.formato(v.texto(nombre, "nombre"), v.RE_NOMBRE, "nombre"),
                v.formato(v.texto(grupo, "grupo"), v.RE_GRUPO, "grupo"))

    def _inscribir_en_su_grupo(self, nc: str) -> int:
        grupo = self.estudiantes[nc].grupo
        nuevos = [(nc, c.id) for c in self.cursos.values() if c.grupo == grupo and (nc, c.id) not in self.inscripciones]
        self.inscripciones.update(nuevos)
        self._cambio()
        return len(nuevos)

    def _quitar_inscripcion(self, nc: str, curso_id: str) -> int:
        """Quita la inscripción y las calificaciones de ese curso; devuelve cuántas calificaciones se borraron."""
        self.inscripciones.discard((nc, curso_id))
        evals = {e.id for p in self.parciales_de(curso_id) for e in self.evaluaciones_de(p.id)}
        borrar = [k for k in self.calificaciones if k[0] == nc and k[1] in evals]
        for k in borrar:
            del self.calificaciones[k]
        self._cambio()
        return len(borrar)

    def alta_estudiante(self, apellidos: str, nombre: str, grupo: str, inscribir: bool = True) -> tuple[Estudiante, int]:
        """El número de cuenta se asigna solo; el alumno queda inscrito en todas las materias de su grupo."""
        apellidos, nombre, grupo = self._datos_persona(apellidos, nombre, grupo)
        self._exigir_grupo(grupo)
        est = self.registrar_estudiante({"num_cuenta": self.siguiente_cuenta(), "apellidos": apellidos,
                                         "nombre": nombre, "grupo": grupo})
        n = self._inscribir_en_su_grupo(est.num_cuenta) if inscribir else 0
        if n:
            self.bus.emitir("inscripcion_registrada", detalle=f"{est.num_cuenta} → {n} materias del grupo {grupo}")
        return est, n

    def editar_estudiante(self, nc: str, apellidos: str, nombre: str, grupo: str) -> Estudiante:
        """Cambiar de grupo da de baja sus materias anteriores (con sus calificaciones) y lo inscribe en las del nuevo."""
        if nc not in self.estudiantes:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el estudiante {nc}")
        apellidos, nombre, grupo = self._datos_persona(apellidos, nombre, grupo)
        self._exigir_grupo(grupo)
        anterior = self.estudiantes[nc]
        self.estudiantes[nc] = Estudiante(nc, apellidos, nombre, grupo)
        if anterior.grupo != grupo:
            for c in [c for n, c in self.inscripciones if n == nc]:
                self._quitar_inscripcion(nc, c)
            self._inscribir_en_su_grupo(nc)
        self._cambio()
        self.bus.emitir("estudiante_actualizado", detalle=f"{nc} · {apellidos} {nombre} · grupo {grupo}")
        return self.estudiantes[nc]

    def baja_estudiante(self, nc: str) -> None:
        if nc not in self.estudiantes:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el estudiante {nc}")
        for c in [c for n, c in self.inscripciones if n == nc]:
            self._quitar_inscripcion(nc, c)
        est = self.estudiantes.pop(nc)
        self._cambio()
        self.bus.emitir("estudiante_baja", detalle=f"{nc} · {est.nombre_completo}")

    def _validar_pesos_parciales(self, pesos) -> list[float]:
        if len(pesos) != 3:
            raise ErrorValidacion("PORCENTAJES_INVALIDOS", "se requieren exactamente 3 parciales")
        valores = [v.peso(p, f"peso del parcial {i}") for i, p in enumerate(pesos, 1)]
        if limpiar10(sum(valores)) != UNO:
            raise ErrorValidacion("PORCENTAJES_INVALIDOS", f"los 3 parciales deben sumar 100 % (suman {round(sum(valores) * 100)} %)")
        return valores

    def _validar_materia(self, asignatura, grupo, excluir: str | None = None) -> tuple[str, str]:
        v.campos({"asignatura": asignatura, "grupo": grupo}, "asignatura", "grupo")
        asignatura = v.texto(asignatura, "asignatura")
        grupo = v.formato(v.texto(grupo, "grupo"), v.RE_GRUPO, "grupo")
        if any(c.id != excluir and c.asignatura.lower() == asignatura.lower() and c.grupo == grupo for c in self.cursos.values()):
            raise ErrorValidacion("ID_DUPLICADO", f"{asignatura} ya existe en el grupo {grupo}")
        return asignatura, grupo

    def alta_curso(self, asignatura: str, grupo: str, pesos=PESOS_PARCIALES, docente_id: str | None = None) -> tuple[Curso, int]:
        """Crea la materia con sus 3 parciales, le asigna docente e inscribe al grupo completo.
        Los alumnos que se registren después en el grupo se inscriben solos."""
        asignatura, grupo = self._validar_materia(asignatura, grupo)
        self._exigir_grupo(grupo)
        pesos = self._validar_pesos_parciales(pesos)
        if docente_id and docente_id not in self.docentes:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el docente {docente_id}")
        curso = Curso(self._siguiente("C", self.cursos), asignatura, grupo)
        self.cursos[curso.id] = curso
        for numero, peso in enumerate(pesos, 1):
            pid = self._siguiente("P", self.parciales)
            self.parciales[pid] = Parcial(pid, curso.id, numero, peso)
        if docente_id:
            self.asignaciones.add((docente_id, curso.id))
        alumnos = [e.num_cuenta for e in self.estudiantes.values() if e.grupo == grupo]
        self.inscripciones.update((n, curso.id) for n in alumnos)
        self._cambio()
        self.bus.emitir("curso_registrado", detalle=f"{curso.id} · {asignatura} · grupo {grupo} · {len(alumnos)} inscritos")
        return curso, len(alumnos)

    def editar_curso(self, curso_id: str, asignatura: str, pesos, docente_id: str | None) -> Curso:
        if curso_id not in self.cursos:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el curso {curso_id}")
        actual = self.cursos[curso_id]
        asignatura, _ = self._validar_materia(asignatura, actual.grupo, excluir=curso_id)
        pesos = self._validar_pesos_parciales(pesos)
        if docente_id and docente_id not in self.docentes:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el docente {docente_id}")
        self.cursos[curso_id] = Curso(curso_id, asignatura, actual.grupo)
        for p, peso in zip(self.parciales_de(curso_id), pesos):
            self.parciales[p.id] = Parcial(p.id, curso_id, p.numero, peso)
        self.asignaciones = {(d, c) for d, c in self.asignaciones if c != curso_id}
        if docente_id:
            self.asignaciones.add((docente_id, curso_id))
        self._cambio()
        self.bus.emitir("curso_actualizado", detalle=f"{curso_id} · {asignatura}")
        return self.cursos[curso_id]

    def eliminar_curso(self, curso_id: str) -> None:
        """Solo se elimina una materia sin calificaciones capturadas (como en un sistema escolar real)."""
        if curso_id not in self.cursos:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el curso {curso_id}")
        parciales = [p.id for p in self.parciales_de(curso_id)]
        evals = {e.id for pid in parciales for e in self.evaluaciones_de(pid)}
        if any(eid in evals for _, eid in self.calificaciones):
            raise ErrorValidacion("OPERACION_NO_PERMITIDA", f"{curso_id} ya tiene calificaciones capturadas")
        for eid in evals:
            del self.evaluaciones[eid]
        for pid in parciales:
            del self.parciales[pid]
        self.inscripciones = {(n, c) for n, c in self.inscripciones if c != curso_id}
        self.asignaciones = {(d, c) for d, c in self.asignaciones if c != curso_id}
        curso = self.cursos.pop(curso_id)
        self._cambio()
        self.bus.emitir("curso_eliminado", detalle=f"{curso_id} · {curso.asignatura}")

    def alta_docente(self, nombre: str) -> Docente:
        return self.registrar_docente({"id": self.siguiente_docente(), "nombre": nombre})

    def asignar_cursos(self, docente_id: str, cursos_ids) -> int:
        """Deja al docente exactamente con esos cursos (cada curso tiene un solo docente)."""
        if docente_id not in self.docentes:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el docente {docente_id}")
        cursos_ids = set(cursos_ids)
        faltan = cursos_ids - set(self.cursos)
        if faltan:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existen los cursos {', '.join(sorted(faltan))}")
        self.asignaciones = {(d, c) for d, c in self.asignaciones if d != docente_id and c not in cursos_ids}
        self.asignaciones.update((docente_id, c) for c in cursos_ids)
        self.bus.emitir("docente_asignado", detalle=f"{docente_id} → {len(cursos_ids)} cursos")
        return len(cursos_ids)

    def actualizar_inscripciones(self, nc: str, cursos_ids) -> tuple[int, int]:
        """Altas y bajas individuales (excepciones). Devuelve (altas, bajas)."""
        if nc not in self.estudiantes:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el estudiante {nc}")
        grupo = self.estudiantes[nc].grupo
        cursos_ids = set(cursos_ids)
        for c in cursos_ids:
            if c not in self.cursos:
                raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el curso {c}")
            if self.cursos[c].grupo != grupo:
                raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"{c} no pertenece al grupo {grupo}")
        actuales = {c for n, c in self.inscripciones if n == nc}
        altas, bajas = cursos_ids - actuales, actuales - cursos_ids
        self.inscripciones.update((nc, c) for c in altas)
        for c in bajas:
            self._quitar_inscripcion(nc, c)
        self._cambio()
        if altas or bajas:
            self.bus.emitir("inscripcion_actualizada", detalle=f"{nc} · {len(altas)} altas, {len(bajas)} bajas")
        return len(altas), len(bajas)

    def inscribir_grupo(self, grupo: str) -> int:
        """Inscribe a cada alumno del grupo en las materias del grupo que le falten."""
        total = sum(self._inscribir_en_su_grupo(e.num_cuenta) for e in list(self.estudiantes.values()) if e.grupo == grupo)
        self.bus.emitir("inscripcion_registrada", detalle=f"grupo {grupo} · {total} inscripciones nuevas")
        return total

    def _validar_plan(self, parcial_id: str, rubros: list[dict]) -> list[tuple]:
        if parcial_id not in self.parciales:
            raise ErrorValidacion("REFERENCIA_INEXISTENTE", f"no existe el parcial {parcial_id}")
        numero = self.parciales[parcial_id].numero
        if not rubros:
            raise ErrorValidacion("CAMPO_FALTANTE", f"parcial {numero}: agrega al menos una evaluación")
        actuales = {e.id for e in self.evaluaciones_de(parcial_id)}
        limpios, nombres = [], set()
        for r in rubros:
            try:
                v.campos(r, "nombre", "peso")
                nombre = v.texto(r["nombre"], "nombre")
                peso = v.peso(r["peso"], f"peso de {nombre}")
            except ErrorValidacion as e:
                raise ErrorValidacion(e.codigo, f"parcial {numero}: {e.detalle}") from None
            if nombre.lower() in nombres:
                raise ErrorValidacion("ID_DUPLICADO", f"parcial {numero}: {nombre} está repetida")
            nombres.add(nombre.lower())
            limpios.append((r.get("id") if r.get("id") in actuales else None, nombre, peso))
        suma = limpiar10(sum(p for _, _, p in limpios))
        if suma != UNO:
            raise ErrorValidacion("PORCENTAJES_INVALIDOS", f"parcial {numero}: las evaluaciones deben sumar 100 % "
                                                           f"(suman {(suma * 100).normalize()} %)")
        return limpios

    def definir_plan(self, parcial_id: str, rubros: list[dict]) -> int:
        """Reemplaza las evaluaciones de un parcial: [{"id"?, "nombre", "peso"}] que sumen 1.0."""
        return self.definir_planes({parcial_id: rubros})

    def definir_planes(self, planes: dict[str, list[dict]]) -> int:
        """Guarda el plan de varios parciales a la vez: primero se validan todos y solo entonces se aplican.
        Los rubros que conservan su id mantienen sus calificaciones; devuelve cuántas calificaciones se borraron."""
        validados = {pid: self._validar_plan(pid, rubros) for pid, rubros in planes.items()}
        borradas = 0
        for parcial_id, limpios in validados.items():
            conservados = {eid for eid, _, _ in limpios if eid}
            for e in self.evaluaciones_de(parcial_id):
                if e.id not in conservados:
                    del self.evaluaciones[e.id]
                    for k in [k for k in self.calificaciones if k[1] == e.id]:
                        del self.calificaciones[k]
                        borradas += 1
            for eid, nombre, peso in limpios:
                eid = eid or self._siguiente("EV", self.evaluaciones)
                self.evaluaciones[eid] = Evaluacion(eid, parcial_id, nombre, peso)
            self._cambio()
        self.bus.emitir("plan_actualizado", detalle=f"{', '.join(validados)} · {sum(map(len, validados.values()))} evaluaciones")
        return borradas

    def guardar_acta(self, cambios: list[tuple[str, str, object]]) -> dict:
        """Captura por lote (acta): [(num_cuenta, evaluacion_id, valor | None)].
        None borra una calificación existente. Los renglones con error no detienen a los demás y se
        devuelven juntos, para avisar una sola vez."""
        resultado = {"capturadas": 0, "modificadas": 0, "borradas": 0, "errores": []}
        for nc, eid, valor in cambios:
            if valor is None:
                if self.calificaciones.pop((nc, eid), None) is not None:
                    resultado["borradas"] += 1
                continue
            try:
                nc, eid, x = self._validar_calificacion({"num_cuenta": nc, "evaluacion_id": eid, "valor": valor})
            except ErrorValidacion as e:
                registro = {"num_cuenta": nc, "evaluacion_id": eid, "valor": valor}
                self.errores.append({"caso": None, "tipo": "acta", "codigo": e.codigo, "detalle": e.detalle, "registro": registro})
                resultado["errores"].append({**registro, "codigo": e.codigo, "detalle": e.detalle})
                continue
            resultado["modificadas" if (nc, eid) in self.calificaciones else "capturadas"] += 1
            self.calificaciones[(nc, eid)] = x
        self.bus.emitir("acta_guardada", detalle=f"{resultado['capturadas']} nuevas, {resultado['modificadas']} modificadas, "
                                                 f"{resultado['borradas']} borradas, {len(resultado['errores'])} con error")
        return resultado

    # ---------- respaldo (sin validar: el estado guardado ya pasó por las validaciones) ----------
    def restaurar(self, d: dict) -> None:
        self.estudiantes = {r["num_cuenta"]: Estudiante(**r) for r in d.get("estudiantes", [])}
        self.cursos = {r["id"]: Curso(**r) for r in d.get("cursos", [])}
        self.parciales = {r["id"]: Parcial(r["id"], r["curso_id"], int(r["numero"]), float(r["peso"])) for r in d.get("parciales", [])}
        self.evaluaciones = {r["id"]: Evaluacion(r["id"], r["parcial_id"], r["nombre"], float(r["peso"]))
                             for r in d.get("evaluaciones", [])}
        self.inscripciones = {(r["num_cuenta"], r["curso_id"]) for r in d.get("inscripciones", [])}
        self.calificaciones = {(r["num_cuenta"], r["evaluacion_id"]): float(r["valor"]) for r in d.get("calificaciones", [])}
        self.docentes = {r["id"]: Docente(**r) for r in d.get("docentes", [])}
        self.asignaciones = {(r["docente_id"], r["curso_id"]) for r in d.get("asignaciones", [])}
        self.grupos_registro = {r["clave"]: Grupo(r["clave"], int(r["semestre"]), r["turno"]) for r in d.get("grupos", [])}
        self._sincronizar_grupos()
        self._cambio()
        self.bus.emitir("datos_restaurados", detalle=f"{len(self.estudiantes)} estudiantes, {len(self.cursos)} cursos")

    # ---------- carga masiva ----------
    def cargar(self, datos: dict) -> None:
        for r in datos.get("grupos", []):
            self._procesar("grupo", r, lambda r: self.alta_grupo(r.get("clave") if isinstance(r, dict) else None,
                                                                 r.get("semestre"), r.get("turno")))
        for r in datos.get("estudiantes", []):
            self._procesar("estudiante", r, self.registrar_estudiante)
        for r in datos.get("cursos", []):
            self._procesar("curso", r, self.registrar_curso)
        self._cargar_parciales(datos.get("parciales", []))
        self._cargar_evaluaciones(datos.get("evaluaciones", []))
        for r in datos.get("inscripciones", []):
            self._procesar("inscripcion", r, self.inscribir)
        for r in datos.get("calificaciones", []):
            self._procesar("calificacion", r, self.capturar_calificacion)
        for r in datos.get("docentes", []):
            self._procesar("docente", r, self.registrar_docente)
        for r in datos.get("asignaciones", []):
            self._procesar("asignacion", r, self.asignar_docente)
        acciones = {"modificar_calificacion": self.modificar_calificacion,
                    "consultar_kardex": lambda r: self.consultar_kardex(r.get("num_cuenta"))}
        for r in datos.get("operaciones", []):
            accion = acciones.get(r.get("tipo")) if isinstance(r, dict) else None
            if accion:
                self._procesar(r["tipo"], r, accion)
        self._sincronizar_grupos()
        self.bus.emitir("datos_cargados", detalle=f"{len(self.estudiantes)} estudiantes, {len(self.errores)} errores")

    def _cargar_parciales(self, registros: list) -> None:
        por_curso = defaultdict(list)
        for r in registros:
            try:
                p = self._validar_parcial(r)
                if any(x.id == p.id for lista in por_curso.values() for _, x in lista):
                    raise ErrorValidacion("ID_DUPLICADO", f"el parcial {p.id} ya existe")
                por_curso[p.curso_id].append((r, p))
            except ErrorValidacion as e:
                self._error("parcial", r, e)
        for curso_id, lista in por_curso.items():
            parciales = [p for _, p in lista]
            numeros = sorted(p.numero for p in parciales)
            if numeros != [1, 2, 3] or limpiar10(sum(p.peso for p in parciales)) != UNO:
                r = next((r for r, _ in lista if isinstance(r, dict) and r.get("caso")), lista[0][0])
                suma = limpiar10(sum(p.peso for p in parciales)).normalize()
                self._error("parcial", r, ErrorValidacion(
                    "PORCENTAJES_INVALIDOS", f"los parciales de {curso_id} deben ser 3 y sumar 1.0 (suman {suma})"))
                continue
            for p in parciales:
                self.parciales[p.id] = p
                self.bus.emitir("parcial_registrado", detalle=f"{p.id} · {p.curso_id} · peso {p.peso}")

    def _cargar_evaluaciones(self, registros: list) -> None:
        por_parcial = defaultdict(list)
        for r in registros:
            try:
                e = self._validar_evaluacion(r)
                if any(x.nombre.lower() == e.nombre.lower() for _, x in por_parcial[e.parcial_id]):
                    raise ErrorValidacion("ID_DUPLICADO", f"{e.nombre} repetida en {e.parcial_id}")
                if any(x.id == e.id for lista in por_parcial.values() for _, x in lista):
                    raise ErrorValidacion("ID_DUPLICADO", f"la evaluación {e.id} ya existe")
                por_parcial[e.parcial_id].append((r, e))
            except ErrorValidacion as err:
                self._error("evaluacion", r, err)
        for parcial_id, lista in por_parcial.items():
            suma = limpiar10(sum(e.peso for _, e in lista))
            if suma != UNO:
                r = next((r for r, _ in lista if isinstance(r, dict) and r.get("caso")), lista[0][0])
                self._error("evaluacion", r, ErrorValidacion(
                    "PORCENTAJES_INVALIDOS", f"las evaluaciones de {parcial_id} suman {suma.normalize()} y deben sumar 1.0"))
                continue
            for _, e in lista:
                self.evaluaciones[e.id] = e
                self.bus.emitir("evaluacion_registrada", detalle=f"{e.id} · {e.nombre} · {e.parcial_id}")

    # ---------- exportar el estado actual ----------
    def a_diccionario(self) -> dict:
        return {
            "grupos": [vars(g) for g in sorted(self.grupos_registro.values(), key=lambda g: g.clave)],
            "estudiantes": [vars(e) for e in self.estudiantes.values()],
            "cursos": [vars(c) for c in self.cursos.values()],
            "parciales": [vars(p) for p in self.parciales.values()],
            "evaluaciones": [vars(e) for e in self.evaluaciones.values()],
            "inscripciones": [{"num_cuenta": n, "curso_id": c} for n, c in sorted(self.inscripciones)],
            "calificaciones": [{"num_cuenta": n, "evaluacion_id": e, "valor": x}
                               for (n, e), x in sorted(self.calificaciones.items())],
            "docentes": [vars(d) for d in self.docentes.values()],
            "asignaciones": [{"docente_id": d, "curso_id": c} for d, c in sorted(self.asignaciones)],
        }
