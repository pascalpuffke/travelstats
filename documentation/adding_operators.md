# operators.json format

The `operators.json` file defines operators and their associated matching rules.

## Operator object structure

JSON root is an array of these objects.

- **`name` (string, required):** *Full* name of the operator.
  Include suffixes like 'GmbH' or 'AG' too, as abbreviations often include those.
    - Example: `"name": "Hallesche Verkehrs-AG"`
- **`types` (array of strings, required):** An array of types this operator operates.
  The type is determined by the prefix of the line name.
  For example, given a line name like "STR 42", type will be "STR".
    - Example: `"types": [ "STR", "Bus" ]`
- **`regex` (string, optional):** A regular expression to match on line names.
  On success, this will skip checking the types array.
- **`match-all-stations-containing` (array of strings, optional):** An array of station names or parts of station names.
  If a vehicles origin or destination station contains any of these strings, it will be considered as operated by this
  operator.
    - Example: `"match-all-stations-containing": [ "Halle(Saale)", "Bad Dürrenberg" ]`
- **`lines` (array of objects, optional):** An array of specific lines of this operator.
  Each line object should have the following fields:
    - **`line` (string, required):** The name of the line.
        - Example: `"line": "RE 30"`
    - **`from` (string, optional):** The origin station of the line.
      If this field is null or not present, any origin station will be matched.
        - Example: `"from": "Magdeburg Hbf"`
    - **`to` (string, optional):** The destination station of the line.
      If this field is null or not present, any destination station will be matched.
        - Examples: `"to": "Halle(Saale)Hbf"`, `"to": null`

A full example ruleset may look like this (if you're lucky):

```json5
{
  "name": "S-Bahn Berlin",
  "types": [
    "S"
    // The S-Bahn Berlin only runs S-Bahn services,
    // allowing us to immediately skip any trip where `lineName.startsWith("S") == false`.
  ],
  "match-all-stations-containing": [
    "Berlin",
    // Match if the origin OR destination contains "Berlin"...
    "Bernau",
    // ... or Bernau...
    "Potsdam"
    // ... or Potsdam.
  ]
  // The rules above are sufficient for accurately determining Berlin S-Bahn trains.
  // Most other operators will require some use of the "lines" list.
}
```

Here's how to use regular expressions:

```json5
{
  "name": "Hallesche Verkehrs-AG",
  "types": [
    "STR",
    "Bus"
  ],
  "regex": "^(STR|Bus) (E|\\d{1,2})$", // Match any lineName starting with "STR" or "Bus",
                                       // followed by a space, then with either the letter 'E'
                                       // or a two-digit number at the end.
  "match-all-stations-containing": [
    "Halle",
    "Bad Dürrenberg"
  ]
}
```