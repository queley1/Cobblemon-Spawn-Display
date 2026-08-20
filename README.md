# Cobblemon Spawn Display

A client-side Fabric mod for Minecraft 1.21.1 and the Cobblemon 1.8.0 build in
the Cobblemon Realms pack. It adds a compact HUD grid in the top-left corner
containing every Pokemon entity currently loaded by the client.

Entries are sorted by Mythical, Legendary, Ultra Beast, Paradox, Alpha, Shiny,
Fossil, Tera, standard rarity from ultra rare to common, and distance (nearest
first). Special skins display their marker without affecting sort order.
Each 30-pixel square contains:

- Cobblemon's own live species/form avatar
- a main-class badge in the top-left: `C`, `U`, `R`, or `UR` when a standard
  rarity exists, otherwise `L`, `M`, `P`, `UB`, or `F` when applicable
- `S`, `A`, a white `T`, a gold five-point star, and `F` status badges in the
  top-right for Shiny, Alpha, Tera, special skin, and Fossil; `F` is omitted
  there when Fossil is already the main class
- a main-class tile treatment: Legendary orange, Mythical yellow, Ultra Beast
  blue with a yellow border, Paradox violet with a red border and `P`, Fossil
  brown, or the standard rarity color
- an eight-direction arrow and live distance beneath the avatar

The Ultra Beast `UB` badge uses the same yellow as its border, and the Paradox
`P` badge uses the same red as its border. Shiny, Alpha, Tera, and Fossil status
colors blend with the main class color in a slowly animated border. Tera adds
its configured color, white by default, to that palette. For example, a shiny
ultra rare has a light-blue and pink border; Alpha adds red, Fossil adds brown,
and a shiny Alpha Fossil cycles through all four colors. A Fossil used as the
main class already uses brown as its base. These statuses remain overlays and do
not replace the main class's tile colors. There is no header or Pokemon name, and the class badge
has no background plate. Tile interiors are translucent and darkened for avatar
contrast. Ultra rare remains pink by default. By default, 30-pixel tiles fill
from left to right and wrap after eight entries.

Open **Configure** from Mod Menu to access **Spawn Display Settings**. Each
class and split-color role has its own selector button on the Colors page, so
none are hidden behind a cycling control. Each selector's background previews
its currently configured color. An optional shortcut can be assigned under
Controls. Settings are stored client-side in
`config/cobblemon_spawn_display.json` and include background opacity, custom
HSL colors for each rarity, Legendary, Mythical, Fossil, both Ultra Beast color
roles, both Paradox color roles, Shiny, Alpha, and Tera; row length; tile size;
spacing; the entity refresh interval (in ticks); and whether ordinary common
Pokemon are shown. Commons with a Tera marker or special skin remain visible,
like Shiny and Alpha commons. Animations can also be disabled; this freezes both avatars
and animated borders while preserving the multi-color border treatment. They
remain enabled by default. Shiny and Alpha commons are always shown.

Tera is identified from the server's synced `mythical_wildtera` overworld
aspect, Mega Showdown's `msd:tera_<type>` battle aspect, and its brief
`play_tera` transition. The `mythical_radiant` effect that accompanies a wild
Tera encounter is treated as part of the Tera presentation rather than a skin.
The gold five-point star is reserved for explicit MythicalCobbled and other server skin
aspects. Regional and other alternate forms do not receive the skin marker.

When neither the sheet nor a loaded spawn file provides a standard rarity,
Legendary, Mythical, Paradox, and Ultra Beast classification comes from
Cobblemon's species and form labels. Because dedicated-server species sync does
not include those labels, the mod also indexes them from the locally installed
species data. If none of those special classes applies, the Pokemon inherits the
lowest standard rarity found elsewhere in its evolution family. Fossils that
are still unclassified use `F` as their brown main class; fossils with another
main class show `F` as a status instead. These fallback classes replace the `?`
badge; unrelated Pokemon without any of these sources still display `?`.

Changes are applied and saved as they are entered. The game remains sharp behind
the settings screen so HUD layout and styling changes can be previewed live.
Each slider has its own reset control, alongside the full reset option.
Background opacity blends the tile interior directly with the game world, and
rarity, direction, and distance annotations scale with the selected tile
size.

Spawn Display contains its own rarity index and does not require Cobblemon
Rarity for WTHIT. It indexes the server's published spawn-bucket sheet by
National Dex number and chooses the lowest listed tier in `C`, `U`, `R`, `UR`
order. When the sheet has no entry, it falls back to the lowest bucket found in
the loaded mods' spawn files. A client-only mod can only list Pokemon entities
that the server has sent to the client.

Run `scripts/update-rarity-data.ps1` from this directory to refresh the bundled
snapshot from the published spawn-bucket sheet.

## Build

The project requires Java 21 plus local copies of the Cobblemon 1.8.0 Fabric
JAR and Mod Menu 11.0.4 JAR. Point Gradle at the installed JARs, then build
from the repository root on Windows:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:COBBLEMON_JAR = "C:\path\to\Cobblemon-fabric-1.8.0+1.21.1.jar"
$env:MODMENU_JAR = "C:\path\to\modmenu-11.0.4.jar"
.\gradlew.bat build
```

Alternatively, copy the dependencies to `libs/Cobblemon.jar` and
`libs/modmenu.jar`. The `libs` JARs are intentionally ignored by Git.

The finished JAR is written to `build/libs/`.

For this Mythical Launcher instance, place the JAR in the instance's
`user-mods` directory so the launcher keeps it across pack updates.
