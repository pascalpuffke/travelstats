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

## Build using Gradle (command line)

Make sure a recent version of Gradle is installed. Tested on Gradle 8.3.

Requires `jdk >= 17`.
If you get an error like `Unsupported class file major version xx`, you're not using a compatible JDK version.

1. **Clone this repo**: `git clone https://github.com/pascalpuffke/travelstats`
2. **Change directory**: `cd travelstats`
3. **Build**: `gradle build` (This will show a lot of warnings. This is due to poor programming, and not your fault. Ignore (or fix) them.)
4. **Run**: `gradle run --args="/path/to/Traewelling_export_xxxxxxx.json"`

## Build in an IDE (IntelliJ IDEA)

IntelliJ respects our Gradle build script and fetches dependencies automatically.

Open the Gradle tab and click **Reload All Gradle Projects** (the first option, top-left corner).
This will generate a bunch of tasks, the two we are interested in are `build/build` and `application/run`.

Again, you may get an error message if you use an outdated JDK version.
Fix the error by opening the **Settings** page and navigating to **Build, Execution, Deployment** > **Build Tools** > **Gradle**.
Change the **Gradle JVM** option at the bottom to at least version 17, and try again.

Start the application by double-clicking the `run` task in the `application` category.
After that, your selected **Run/Debug Configuration** will automatically be set, and from now on you can run the task
simply by pressing `Shift`+`F10` (`Cmd`+`R` on macOS).

Pass the json export file by editing the run configuration (**Select Run/Debug Configuration** > **Edit Configurations...**)
and replace the **Tasks and arguments** field with `run --args="/path/to/Traewelling_export_xxxxxxx.json"`

## TODO
- [ ] Variable amount of input .json files
- [ ] Define operators in external, configurable JSON files instead of hard-coding
  - [ ] Perhaps even throw everything away and use some external API
- [ ] GUI (lmao god help me)