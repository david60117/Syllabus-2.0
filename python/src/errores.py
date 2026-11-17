class ErrorValidacion(Exception):
    """Error de datos con un código común a las versiones Python, JavaScript y Java."""

    def __init__(self, codigo: str, detalle: str):
        super().__init__(f"{codigo}: {detalle}")
        self.codigo = codigo
        self.detalle = detalle
