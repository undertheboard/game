# Redistricting Fairness Analyzer

A Java Swing desktop application that loads redistricting maps from JSON files,
displays them on an interactive canvas, and uses an AI algorithm to evaluate
and improve the **fairness** of the district plan.

## Features

- **Load redistricting maps** from a simple JSON format (precincts with polygon
  coordinates, population, and partisan vote counts; assignment of precincts to
  districts).
- **GUI display** built with Java Swing — districts are rendered as colored
  polygons on a zoomable/pannable canvas with a legend and per-district summary.
- **AI fairness analysis** computes three standard metrics:
  - **Population equality** (max deviation from ideal district size)
  - **Compactness** (Polsby–Popper score, area / perimeter ratio)
  - **Partisan fairness** (efficiency gap of "wasted" votes)
- **AI optimizer**: a hill-climbing / simulated-annealing search that swaps
  precincts between adjacent districts to lower the combined unfairness score
  while keeping districts contiguous.
- **Sample map** is bundled as a classpath resource so the app is usable
  immediately on launch.

## Build

Requires JDK 17+ and Maven 3.8+.

```sh
mvn package
```

This compiles the sources, runs the tests, and produces an executable JAR at
`target/redistricting-app.jar`. A copy of the built JAR is also committed to
this repository at `dist/redistricting-app.jar` for convenience.

## Run

```sh
java -jar dist/redistricting-app.jar
```

or, after building locally:

```sh
java -jar target/redistricting-app.jar
```

On launch the bundled sample map is displayed. Use **File → Open Map…** to
load your own JSON map, **AI → Analyze Fairness** to view the metrics, and
**AI → Optimize Fairness** to let the AI search for a fairer plan.

## Map JSON format

```json
{
  "name": "Sample State",
  "districts": 3,
  "precincts": [
    {
      "id": "P1",
      "district": 0,
      "population": 1000,
      "demVotes": 520,
      "repVotes": 480,
      "polygon": [[0,0],[10,0],[10,10],[0,10]]
    }
  ]
}
```
