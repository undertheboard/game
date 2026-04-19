# Redistricting Fairness Analyzer

A Java Swing desktop app that:

- **Loads precinct data** from the
  [Redistricting Data Hub (RDH)](https://redistrictingdatahub.org/). On
  startup the app prompts you to load a statewide precinct file (or use
  the bundled sample) — redistricting works at the precinct level and
  every menu is unlocked once a base map is loaded.
- **Imports redistricting plans directly** from
  [Dave's Redistricting App (DRA)](https://davesredistricting.org/) — pick
  any file produced by DRA's *Export Map to a File* dialog (District Shapes
  `.geojson`, Map Archive `.json`, or District Data `.csv`) and the format
  is auto-detected. Map Archive `.json` files without embedded geometry
  are accepted as assignment-only overlays applied to the loaded base.
- **Bundled state presets** for one-click loading (no download needed):
  - **North Carolina — 2024 General (Presidential)**: 2,658 precincts
    with the 2024 Harris (D) and Trump (R) vote totals from the NC
    State Board of Elections (via RDH). Available from the startup
    dialog and *File → Load bundled preset*.
- **Saves plans** as precinct-level GeoJSON (round-trips cleanly through
  the importer) so you can move plans between sessions.
- Uses **native OS file dialogs** — Windows Explorer on Windows, Finder
  on macOS, GTK on most Linux desktops — instead of Swing's chooser.
- **Displays** the plan on a pannable / zoomable canvas with four
  independent view toggles in the *View* menu:
  - colour by district vs. by **partisan lean** (red ⇄ blue),
  - **show / hide precinct lines**,
  - **show / hide district lines**,
  - **show / hide district numbers** (rendered with a halo at each
    district's population-weighted centroid).
- **Analyses fairness** with an AI scorer (population deviation,
  Polsby-Popper compactness, efficiency gap, plus a combined unfairness score).
- **Generates plans** with one of five algorithms — see *Generators* below.
- Ships with a built-in **dark theme**.
- Includes an in-app **Tutorial** that walks new users through every step
  (auto-shown on first launch, re-openable from *Help → Tutorial*).

## Generators

`AI → Generate Plan…` opens a Simple / Advanced generator dialog. *Simple*
mode just asks for the number of districts and a seed and runs the
`SimpleAlgorithm`. *Advanced* mode exposes an algorithm dropdown and the
full set of objective sliders (partisan bias, county adherence,
compactness, population tolerance, reliability).

| Algorithm                     | Goal                                                                 |
| ----------------------------- | -------------------------------------------------------------------- |
| **Simple**                    | Fast equal-population region growing with a balancing pass           |
| **Advanced (multi-objective)**| Population + partisan target + county adherence + compactness, with multi-attempt growth and a boundary local-search refiner |
| **Compactness (Lloyd / k-means)** | Population-weighted Lloyd iterations producing geometrically tight districts |
| **Competitive**               | Boundary refinement that drives every district toward 50/50          |
| **Partisan target**           | Hits the bias slider's target Dem-seat count as closely as possible  |

All algorithms guarantee contiguous districts (a final
`GeographyUtils.repairContiguity` pass re-attaches any orphan island to
its strongest neighbouring district).

## Build

Requires JDK 17+ and Maven 3.8+.

```sh
mvn package
```

Produces `target/redistricting-app.jar`. A pre-built copy is also committed
at `dist/redistricting-app.jar`.

## Run

```sh
java -jar dist/redistricting-app.jar
```

## Importing from Dave's Redistricting

1. Open your plan in DRA, click **Export Map to a File**.
2. Save any of:
   - **District Shapes (.geojson)** — geometry + per-district stats.
   - **Map Archive (.json)** — full single-file roundtrip.
   - **District Data (.csv)** — enriches an already-loaded plan.
3. In this app: **File → Import from Dave's Redistricting…** and select
   the file. The format is sniffed automatically.

## Generating a plan

**AI → Generate Plan…** opens a dialog with sliders for:

| Knob | Meaning |
|------|---------|
| Districts | Number of seats |
| Precincts X × Y | Resolution of the synthetic precinct grid |
| Counties X × Y | County grouping over the precincts |
| Partisan bias | −100 (R+100) … 0 (proportional) … +100 (D+100) |
| County-line adherence | Penalty for splitting counties |
| Compactness | Weight on geometric compactness |
| Population tolerance (‰) | Allowed |deviation| from ideal pop |
| Reliability | Number of independent attempts (best is kept) |
| Random seed | Reproducibility |
