#!/usr/bin/env python3
"""Generate core/designsystem/.../theme/Palettes.kt from the palette seeds.

Every Material 3 colour role of each palette, light and dark, comes from Material Color Utilities'
fidelity scheme (contrast level 0), which keeps the seed's own hue and chroma in the primary
container. Edit SEEDS and run this rather than hand-editing roles, so that the contrast between
pairs such as primary and onPrimary stays guaranteed.

Needs the Python port of Material Color Utilities, for example in a throwaway virtual environment:

    python3 -m venv /tmp/mcu && /tmp/mcu/bin/pip install materialyoucolor
    /tmp/mcu/bin/python tools/theme_palettes.py
"""

from pathlib import Path

from materialyoucolor.dynamiccolor.material_dynamic_colors import MaterialDynamicColors
from materialyoucolor.hct import Hct
from materialyoucolor.scheme.scheme_fidelity import SchemeFidelity

# Enum constant, Kotlin name, seed. The order is the order of the swatches in Settings.
SEEDS = [
    ("INDIGO", "Indigo", "#4F46E5"),
    ("AMBER", "Amber", "#B8871B"),
    ("GREEN", "Green", "#3F8F6F"),
    ("BLUE", "Blue", "#2F6FD0"),
]

ROLES = [
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
    "error", "onError", "errorContainer", "onErrorContainer",
    "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
    "outline", "outlineVariant", "scrim", "inverseSurface", "inverseOnSurface", "inversePrimary",
    "surfaceDim", "surfaceBright", "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
    "surfaceContainerHigh", "surfaceContainerHighest", "surfaceTint",
]

OUTPUT = Path(__file__).resolve().parent.parent / "core/designsystem/src/main/kotlin/app/pacinglab/core/designsystem/theme/Palettes.kt"


def scheme(seed: str, dark: bool) -> dict[str, int]:
    """ARGB of every role for a seed such as "#4F46E5"."""
    fidelity = SchemeFidelity(Hct.from_int(0xFF000000 | int(seed[1:], 16)), dark, 0.0)
    return {role: getattr(MaterialDynamicColors, role).get_argb(fidelity) & 0xFFFFFFFF for role in ROLES}


def main() -> None:
    lines = [
        "package app.pacinglab.core.designsystem.theme",
        "",
        "import androidx.compose.material3.ColorScheme",
        "import androidx.compose.material3.darkColorScheme",
        "import androidx.compose.material3.lightColorScheme",
        "import androidx.compose.ui.graphics.Color",
        "",
        "// Material 3 colour roles generated with Material Color Utilities (fidelity scheme, contrast 0) from",
        "// each palette's seed. Regenerate rather than hand-editing individual roles, otherwise contrast",
        "// guarantees between pairs such as primary/onPrimary are lost.",
        "",
    ]
    for _, name, seed in SEEDS:
        for dark in (False, True):
            roles = scheme(seed, dark)
            lines.append(f"/** {name}, seed {seed.upper()}, {'dark' if dark else 'light'}. */")
            lines.append(f"internal val {name}{'Dark' if dark else 'Light'}: ColorScheme = {'darkColorScheme' if dark else 'lightColorScheme'}(")
            lines.extend(f"    {role} = Color(0x{roles[role]:08X})," for role in ROLES)
            lines.append(")")
            lines.append("")
    OUTPUT.write_text("\n".join(lines).rstrip("\n") + "\n")
    print(f"wrote {OUTPUT}")


if __name__ == "__main__":
    main()
