# Fresco

A NeoForge content mod for Minecraft 1.21.1, built on top of the [Painter](https://github.com/Steve10086/neoforge_pigmentum) canvas API. Fresco adds a set of brush-like tools — paintbrushes, stamps, spray cans, and cloths — that let players paint, copy, spray, and blend pixels on any block surface.

## Requirements

- **Minecraft** 1.21.1
- **NeoForge** 21.1.228+
- **[Painter](https://modrinth.com/mod/pigmentum)** 0.5.6.9beta+ (bundled as dependency)

## Items

### Palette
Pick a color from the world. With the palette in your offhand, press the pick-key to grab the pixel color under your crosshair. The palette stores a single ARGB color and can be used by other tools as a color source.

> **Recipe:** 7 dyes (white/black/red/yellow/blue/brown/green) + any sign → Palette

---

### Custom Paintbrush
A circular brush that paints with the color from your offhand item:
- **Dye items** → that dye's color
- **Ink Sac** → black
- **Palette** → the palette's stored color
- **Other / empty** → white

Shift + right-click opens a config screen where you can adjust brush size, opacity, and blend mode (overwrite / add / multiply / erase).

> **Recipe:** stick + ink sac + string×2 + button + feather → Custom Paintbrush

---

### Glow Paintbrush
Inherits the Custom Paintbrush's circular pattern and config screen, but additionally writes a glow effect layer to every painted pixel. The glow pipeline (BlendFunction → CanvasImageProvider → CanvasPixelRenderer) renders with full brightness, producing an emissive night-glow effect.

> **Recipe:** stick + glow ink sac + string×2 + button + feather → Glow Paintbrush

---

### Spray Can
A circular spray brush with adjustable feather, density (random pixel skipping), opacity, and size. Blend mode is fixed to **add**. The spray size increases and opacity decreases based on distance from the target (1 block → ×1.0 size, 4 blocks → ×1.5 size / ×0.5 opacity).

Right-click with a dye in your offhand to tint the spray can. When tinted, density-skipped pixels are filled with a brightness-varied version of the tint color instead of being empty. The tinted dye is stored in the spray can's internal container and can be retrieved.

Shift + right-click opens the config screen.

> **Recipe:** iron nugget + button / paper + glass bottle + paper / iron ingot → Spray Can

---

### Stamp
Copies pixel data from a canvas face and replaces it elsewhere. Two modes, toggled by shift + right-click on air:

- **Default** — copies only the canvas pixel data
- **Background** — captures the block face texture + overlays canvas pixels

Shift + right-click a block to store pixels. The stored face is preserved (non-destructive) and can be placed multiple times. Craft two stamps together to clear stored data.

> **Recipe:** stick + iron ingot×2 + plank + paper → Stamp

---

### Cloth
A blending tool that reads surrounding canvas pixels, computes a blurred composite matrix, and paints the averaged result back. As you paint, the cloth accumulates tint from the canvas pixels (1% blend weight per stroke, up to 100% saturation). The accumulated tint is injected back into the blurred output, creating a color-mixing effect.

Shift + right-click on air resets the tint. Shift + right-click on a block opens the config screen (size, opacity, feather). The cloth texture changes based on saturation level (clean → cloth_30 → cloth_60 → cloth_dirty).

> **Recipe:** white wool×3 → Cloth

---

## Dependencies

- **[Painter (Pigmentum)](https://github.com/Steve10086/neoforge_pigmentum)** — the underlying canvas API that enables painting on block surfaces

## License

MIT License.
