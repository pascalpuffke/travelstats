# travelstats
[Träwelling](https://traewelling.de) statistics, but maybe slightly better.

## Usage
Don't

Alternatively, if you hate yourself: Export exactly two JSON files from the [Träwelling export](https://traewelling.de/export) page.
Paste the path of the older data as the first, and only, command-line argument.
Then, put the path of the newer data here, in Main.kt:
```kotlin
val anotherOne = json.decodeFromStream<TraewellingJson>(
  Path.of("/home/.../Documents/some-newer-export.json").inputStream()
)
```

I told you to not use this yet!

## Build using Gradle

Make sure a recent version of Gradle is installed. Tested on Gradle 8.3.

Requires `jdk >= 17`.
If you get an error like `Unsupported class file major version xx`, you're not using a compatible JDK version.

1. **Clone this repo**: `git clone https://github.com/pascalpuffke/travelstats`
2. **Change directory**: `cd travelstats`
3. **Build**: `gradle build` (This will show a lot of warnings. This is due to poor programming, and not your fault. Ignore (or fix) them.)
4. **Run**: `gradle run --args="/path/to/Traewelling_export_xxxxxxx.json"`

## TODO
- [ ] Variable amount of input .json files
- [ ] Define operators in external, configurable JSON files instead of hard-coding
  - [ ] Perhaps even throw everything away and use some external API
- [ ] GUI (lmao god help me)