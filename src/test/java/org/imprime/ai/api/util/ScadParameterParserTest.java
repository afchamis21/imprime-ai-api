package org.imprime.ai.api.util;

import org.imprime.ai.api.http.response.model3d.ScadParameterDefinition;
import org.imprime.ai.api.model.enums.ScadParameterType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScadParameterParserTest {

    @Test
    void parsesAllTopLevelParameterTypesAndCustomizerAnnotations() {
        String scad = """
                /* [Dimensões] */
                // Largura da caixa (mm)
                boxWidth = 80; // [20:5:200]
                // Espessura da parede (mm)
                wall_thickness = 2.4; // [1.2:6.0]
                offset_z = -1.5;
                // Arredondar cantos
                rounded = true;
                // Texto frontal
                label_text = "IMPRIME";
                // Estilo do encaixe
                fit_mode = "snap"; // [tight, snap, loose]
                size_preset = 50; // [30, 50, 80]

                $fn = 48;
                computed_area = boxWidth * wall_thickness;

                /* [Hidden] */
                hidden_margin = 99;

                module body_part() {
                    local_var = 15;
                    cube([boxWidth, 40, wall_thickness]);
                }
                """;

        List<ScadParameterDefinition> parsed = ScadParameterParser.parse(scad);

        assertEquals(7, parsed.size());

        ScadParameterDefinition boxWidth = parsed.get(0);
        assertEquals("boxWidth", boxWidth.name());
        assertEquals("Largura da caixa (mm)", boxWidth.label());
        assertEquals(ScadParameterType.NUMBER, boxWidth.type());
        assertEquals(new BigDecimal("80"), boxWidth.defaultValue());
        assertEquals(new BigDecimal("20"), boxWidth.min());
        assertEquals(new BigDecimal("5"), boxWidth.step());
        assertEquals(new BigDecimal("200"), boxWidth.max());
        assertEquals("mm", boxWidth.unit());

        ScadParameterDefinition wall = parsed.get(1);
        assertEquals("wall_thickness", wall.name());
        assertEquals(ScadParameterType.NUMBER, wall.type());
        assertEquals(new BigDecimal("1.2"), wall.min());
        assertEquals(new BigDecimal("0.1"), wall.step());
        assertEquals(new BigDecimal("6.0"), wall.max());
        assertEquals("mm", wall.unit());

        ScadParameterDefinition offset = parsed.get(2);
        assertEquals("offset_z", offset.name());
        assertEquals("Offset Z", offset.label());
        assertEquals(new BigDecimal("-1.5"), offset.defaultValue());
        assertNull(offset.min());

        ScadParameterDefinition rounded = parsed.get(3);
        assertEquals(ScadParameterType.BOOLEAN, rounded.type());
        assertEquals(Boolean.TRUE, rounded.defaultValue());

        ScadParameterDefinition labelText = parsed.get(4);
        assertEquals(ScadParameterType.STRING, labelText.type());
        assertEquals("IMPRIME", labelText.defaultValue());

        ScadParameterDefinition fitMode = parsed.get(5);
        assertEquals(ScadParameterType.SELECT, fitMode.type());
        assertEquals("snap", fitMode.defaultValue());
        assertEquals(List.of("tight", "snap", "loose"), fitMode.options());

        ScadParameterDefinition sizePreset = parsed.get(6);
        assertEquals(ScadParameterType.SELECT, sizePreset.type());
        assertEquals("50", sizePreset.defaultValue());
        assertEquals(List.of("30", "50", "80"), sizePreset.options());
    }

    @Test
    void sanitizesInvalidAndOutOfOrderDefinitions() {
        List<ScadParameterDefinition> raw = List.of(
                new ScadParameterDefinition(" $fn ", "Bad", ScadParameterType.NUMBER, 32, null, null, null, null, null),
                new ScadParameterDefinition("width", null, ScadParameterType.NUMBER, 40, new BigDecimal("100"), new BigDecimal("10"), BigDecimal.ZERO, "mm", null),
                new ScadParameterDefinition("width", "Duplicado", ScadParameterType.NUMBER, 50, null, null, null, null, null),
                new ScadParameterDefinition("shape", "Formato", ScadParameterType.SELECT, "hex", null, null, null, null, List.of(" round ", "hex", "hex"))
        );

        List<ScadParameterDefinition> sanitized = ScadParameterParser.sanitize(raw);

        assertEquals(2, sanitized.size());
        assertEquals("width", sanitized.get(0).name());
        assertEquals("Width", sanitized.get(0).label());
        assertEquals(new BigDecimal("10"), sanitized.get(0).min());
        assertEquals(new BigDecimal("100"), sanitized.get(0).max());
        assertNull(sanitized.get(0).step());

        assertEquals("shape", sanitized.get(1).name());
        assertEquals(ScadParameterType.SELECT, sanitized.get(1).type());
        assertEquals(List.of("round", "hex"), sanitized.get(1).options());
    }

    @Test
    void parsesMakerWorldFontOptionsWithStyleAndParenthesizedCommas() {
        String scad = """
                /* [Name] */
                // Script font
                Name_font="Lobster";//[Lobster: Lobster (bold script - like the photo), Dancing Script:style=Bold: Dancing Script Bold, Great Vibes: Great Vibes (thin, elegant), Fredoka:style=Bold: Fredoka Bold (not script, round)]
                /* [View] */
                // What to show
                Show="assembled";//[assembled: Assembled, exploded: Exploded, plate: Print plate (all parts), body: Main body (print)]
                /* [Hidden] */
                Local_preview=false;
                """;

        List<ScadParameterDefinition> parsed = ScadParameterParser.parse(scad);

        assertEquals(2, parsed.size());

        ScadParameterDefinition fontParam = parsed.get(0);
        assertEquals("Name_font", fontParam.name());
        assertEquals(ScadParameterType.SELECT, fontParam.type());
        assertEquals("Lobster", fontParam.defaultValue());
        assertEquals(
                List.of("Lobster", "Dancing Script:style=Bold", "Great Vibes", "Fredoka:style=Bold"),
                fontParam.options()
        );
        assertFalse(fontParam.options().contains("round)"));

        ScadParameterDefinition showParam = parsed.get(1);
        assertEquals("Show", showParam.name());
        assertEquals(ScadParameterType.SELECT, showParam.type());
        assertEquals("assembled", showParam.defaultValue());
        assertEquals(List.of("assembled", "exploded", "plate", "body"), showParam.options());
    }
}
