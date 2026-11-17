package mx.uaeh.syllabus.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Colores y tipografía del diseño de Figma "Syllabus 2.0 (Copy)". */
public final class Tema {
    public static final Color FONDO = new Color(0xfcfaf8);
    public static final Color BLANCO = Color.WHITE;
    public static final Color BORDE = new Color(0xe9dfd9);
    public static final Color TEXTO = new Color(0x2c2426);
    public static final Color SUAVE = new Color(0x766e6a);
    public static final Color CAMPO = new Color(0xfbfaf9);
    public static final Color VERDE_SUAVE = new Color(0xe6f1e9);
    public static final Color ROJO = new Color(0xa32a2a);
    public static final Color ROJO_SUAVE = new Color(0xf8e4e4);
    public static final Color AMBAR_SUAVE = new Color(0xfbf1dc);

    public static final Map<String, Color[]> TEMAS = new LinkedHashMap<>();

    static {
        TEMAS.put("Rojo", new Color[]{new Color(0x8b2024), new Color(0xf9eeea)});
        TEMAS.put("Verde", new Color[]{new Color(0x227e68), new Color(0xe6f2ef)});
        TEMAS.put("Azul", new Color[]{new Color(0x3b85df), new Color(0xeaf2fc)});
        TEMAS.put("Morado", new Color[]{new Color(0x7655be), new Color(0xf0ecf9)});
    }

    /** Inter (la fuente de Figma) si está instalada; si no, la del sistema: San Francisco en macOS, Segoe UI en Windows. */
    private static final String FAMILIA = elegirFamilia();

    private static String elegirFamilia() {
        List<String> familias = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        if (familias.contains("Inter")) {
            return "Inter";
        }
        // con el aspecto del sistema, Label.font es la fuente de la interfaz de macOS o de Windows
        Font sistema = javax.swing.UIManager.getFont("Label.font");
        String familia = sistema == null ? Font.DIALOG : sistema.getFamily();
        if (familia.equals(Font.DIALOG) && familias.contains("Segoe UI")) {
            return "Segoe UI";
        }
        return familia;
    }

    private static String actual = "Rojo";

    private Tema() {
    }

    public static Font fuente(int tam, boolean negrita) {
        return new Font(FAMILIA, negrita ? Font.BOLD : Font.PLAIN, tam);
    }

    public static Color acento() {
        return TEMAS.get(actual)[0];
    }

    public static Color acentoSuave() {
        return TEMAS.get(actual)[1];
    }

    public static String getActual() {
        return actual;
    }

    public static void setActual(String nombre) {
        actual = nombre;
    }
}
