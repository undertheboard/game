# Redistricting Fairness Analyzer

A Java Swing desktop app that:

- **Imports redistricting plans directly** from
  [Dave's Redistricting App (DRA)](https://davesredistricting.org/) — pick
  any file produced by DRA's *Export Map to a File* dialog (District Shapes
  `.geojson`, Map Archive `.json`, or District Data `.csv`) and the format
  is auto-detected.
- **Displays** the plan on a pannable / zoomable canvas with district colours,
  per-precinct tooltips, and a legend.
- **Analyses fairness** with an AI scorer (population deviation,
  Polsby-Popper compactness, efficiency gap, plus a combined unfairness score).
- **Optimises** precinct-level plans with a hill-climbing search.
- **Generates plans** of its own from a slider panel that controls
  partisan bias (R+100 ⇄ D+100), county-line adherence, compactness,
  population tolerance, reliability (restart count), and a reproducible seed.
- Includes an in-app **Tutorial** that walks new users through every step
  (auto-shown on first launch, re-openable from *Help → Tutorial*).

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
