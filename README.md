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

### Schematic reader
- Reads Sponge schematic v2 and v3 `.schem` files.
- Decodes the block palette and block-state data.
- Calculates a material-count summary.
- Limits schematic size to 16 million blocks and NBT input to 128 MiB.
- Does not automatically place blocks or modify the world.

Put a `.schem` file in the game's `schematics` folder, then use:
- `.ljct-schem load filename.schem` — load and summarize a schematic.
- `.ljct-schem info` — show the currently loaded schematic dimensions.
- `.ljct-schem clear` — clear the in-memory schematic.

The command creates the `schematics` folder beside the game's normal `mods` and `config` folders if it does not exist.

## Build

The GitHub Actions workflow builds the addon on pushes to `master`, pull requests, and manual workflow dispatch. The JAR is uploaded as the `LJCT-Meteor-Addon` workflow artifact when a build succeeds.

For local development, use Java 21 and run:

```bash
./gradlew build
```

Install the resulting JAR alongside the matching Meteor Client build in your Fabric mods folder.
