# travelstats
[Träwelling](https://traewelling.de) statistics, but maybe slightly better.

# Usage
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

# TODO
- [ ] Variable amount of input .json files
- [ ] Define operators in external, configurable JSON files instead of hard-coding
  - [ ] Perhaps even throw everything away and use some external API
- [ ] GUI (lmao god help me)