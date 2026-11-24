# LJCT — Meteor Addon

LJCT is a Meteor Client addon project targeting Minecraft 1.21.4 and Java 21.

## Builder progress

### Build Assist
- Highlights the block currently targeted by the crosshair.
- Previews the adjacent placement position when holding a block.
- Target and preview colors can be changed in module settings.
- This is a visual aid; movement and block placement remain under player control.
- Optional schematic outline preview is available after loading a `.schem` file. It starts at the player's block position; enable `schematic-preview` in Build Assist settings.
- `preview-block-limit` defaults to 256 to reduce rendering cost on mobile devices.
- With `compare-world` enabled, matching blocks use the normal schematic color, missing blocks use green, and different block types use red. Comparison is by block ID; block-state properties such as facing or rotation are not checked.

### Schematic reader
- Reads Sponge schematic v2 and v3 `.schem` files.
- Decodes the block palette and block-state data.
- Calculates a material-count summary.
- Limits schematic size to 16 million blocks and NBT input to 128 MiB.
- Does not automatically place blocks or modify the world.

Put a `.schem` file in the game's `schematics` folder, then use:
- `.ljct-schem load filename.schem` — load and summarize a schematic.
- `.ljct-schem info` — show the currently loaded schematic dimensions.
- `.ljct-schem materials` — compare required block counts with block items in the player's inventory (read-only).
- `.ljct-schem anchor` — set the schematic origin to your current block position.
- `.ljct-schem origin` — show the saved origin. Without an anchor, the preview follows the player's position.
- `.ljct-schem clear` — clear the in-memory schematic.

The command creates the `schematics` folder beside the game's normal `mods` and `config` folders if it does not exist. After loading, stand at the intended bottom-corner origin and run `.ljct-schem anchor` before enabling the preview.

## Build

The GitHub Actions workflow builds the addon on pushes to `master`, pull requests, and manual workflow dispatch. The JAR is uploaded as the `LJCT-Meteor-Addon` workflow artifact when a build succeeds.

For local development, use Java 21 and run:

```bash
./gradlew build
```

Install the resulting JAR alongside the matching Meteor Client build in your Fabric mods folder.
