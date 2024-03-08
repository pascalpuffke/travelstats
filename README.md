# travelstats
[Träwelling](https://traewelling.de) statistics, but maybe slightly better.

## Usage
Export one or multiple JSON files from the [Träwelling export page](https://traewelling.de/export).

Paste the full paths to each one as command-line arguments. Separate them using spaces.

The program will merge them together automatically, so you don't have to do one mega-export every time you want to use it.

Run it as a gradle project:
```shell
gradle run --args="--checkins /home/user/Documents/Traewelling_export_2022-06-01_to_2023-05-31.json /home/user/Documents/Traewelling_export_2023-06-01_to_2023-09-30.json /home/user/Documents/Traewelling_export_2023-09-01_to_2023-09-30.json"
```

All available options:
```markdown
--top-limit [int]: how many rows of values to show. 
                   Only affects "modes", "lines" and "operators".
                   Default: MAX_INT (${Int.MAX_VALUE})

--ansi-level [string]: the level of color support to use.
                       Valid options: ansi16, ansi256, truecolor
                       Default: truecolor   

--metadata: print basic statistics from the `meta` field.
            time range, total distance and duration, current and total points.

--checkins: print all checkins.
            line name, origin and destination stations, (optional) event,
            (optional) body text, departure and arrival times, delays,
            distance in km, duration in minutes, speed in km/h.

--events: print general event data as a table.
            
--modes: print general mode data as a table.

--mode-stats: more detailed statistics about general transit modes.
              categories: Fernverkehr, Regional, S-Bahn, U-Bahn, Tram, Bus
                          
              Fernverkehr: $LONG_DISTANCE_PREFIXES
              Regional: $REGIONAL_PREFIXES
                          
              number of check-ins, total distance (km), total duration (h),
              average speed (km/h)

--lines: print general line data as a table.

--all-lines: print all unique lines and their respective operators.
             Träwelling usually provides operator data in the export,
             but often fails at local operators; in which case, this program
             *guesses* which one it might be based on rules defined in
             the `operators.json` file. The ruleset is far from complete,
             but does a decent job of getting closer to 100% coverage.

--operators: print all operators as a table.
             Uses same operator guessing algorithm as described above.

--seen-stations: prints all the stations you have seen (visited) as a table.
                 counts both origin and destination stations.

--help, -h: prints this message and exits with code 0.
```

## Build using Gradle (command line)

Make sure a recent version of Gradle is installed. Tested on Gradle 8.3.

Requires `jdk >= 17`.
If you get an error like `Unsupported class file major version xx`, you're not using a compatible JDK version.

1. **Clone this repo**: `git clone https://github.com/pascalpuffke/travelstats`
2. **Change directory**: `cd travelstats`
3. **Build**: `gradle build` (This will show a lot of warnings. This is due to poor programming, and not your fault. Ignore (or fix) them.)
4. **Run**: `gradle run --args="[your options and input files go here]"`

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
- [x] Variable amount of input .json files
- [x] Define operators in external, configurable JSON files instead of hard-coding
- [ ] GUI (lmao god help me)