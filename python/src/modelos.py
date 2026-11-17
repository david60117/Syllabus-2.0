from dataclasses import dataclass


@dataclass(frozen=True)
class Estudiante:
    num_cuenta: str
    apellidos: str
    nombre: str
    grupo: str

    @property
    def nombre_completo(self) -> str:
        return f"{self.apellidos} {self.nombre}"


@dataclass(frozen=True)
class Curso:
    id: str
    asignatura: str
    grupo: str


@dataclass(frozen=True)
class Parcial:
    id: str
    curso_id: str
    numero: int
    peso: float


@dataclass(frozen=True)
class Evaluacion:
    id: str
    parcial_id: str
    nombre: str
    peso: float


@dataclass(frozen=True)
class Docente:
    id: str
    nombre: str


@dataclass(frozen=True)
class Grupo:
    clave: str
    semestre: int
    turno: str
