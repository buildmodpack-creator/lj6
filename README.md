# LJCT — Meteor Addon

LJCT is a Meteor Client addon project targeting Minecraft 1.21.4 and Java 21.

## Current modules

### Builder
- **Build Assist** highlights the block currently targeted by the crosshair.
- The marker color can be changed in the module settings.
- This first milestone is a visual building aid; it does not automatically move the player or place blocks.

## Build

The GitHub Actions workflow builds the addon on pushes to `master`, pull requests, and manual workflow dispatch. The JAR is uploaded as the `LJCT-Meteor-Addon` workflow artifact when a build succeeds.

For local development, use Java 21 and run:

```bash
./gradlew build
```

Install the resulting JAR alongside the matching Meteor Client build in your Fabric mods folder.
