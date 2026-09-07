# SAS-ALNS for the 3M-2E-SVRP

This repository contains the Java/Maven implementation and benchmark instances accompanying the manuscript **"Stochastic Two-Echelon Vehicle Routing with Mobile Satellites: Adaptive Large Neighborhood Search with Stochastic Approximation Scheduling."**

The implementation solves the stochastic multi-commodity, multi-depot two-echelon vehicle routing problem with mobile satellites (3M-2E-SVRP). It combines adaptive large neighborhood search (ALNS), deterministic approximation scheduling (DAS), and stochastic approximation scheduling (SAS), and supports the penalty-based model (PBM) and chance-constrained model (CCM) used in the paper.

## Repository contents

- `src/` - SAS-ALNS Java source code and tests.
- `input/3M-2E-VRP/` - benchmark instances used by the 3M-2E-VRP experiments.
- `input/DELLAERT-2E-VRP/` - deterministic comparison instances supported by the implementation.
- `config.properties` - the single experiment configuration file. Its comments explain the available parameters and the settings that normally need to be changed.
- `SEED_POLICY.md` - random-seed policy used for benchmark generation, search runs, and Monte Carlo evaluation.
- `pom.xml` - Maven build configuration.

Reported result files are not included in this repository.

## Requirements

- Java 17
- Apache Maven
- Gurobi 9.5.1 installation available through the `GUROBI_HOME` environment variable

Apache Commons Math and the test dependencies are managed by Maven. The source tree contains the optional exact-scheduling implementation, so the Gurobi JAR must be available when compiling the complete project. A Gurobi license is required when an exact Gurobi-based scheduling mode or verification is executed.
For the reported stochastic experiments, `schedulingMode=APPROXIMATE` is used; `schedulingMode=EXACT` is retained for deterministic solution generation and exact schedule verification.

For example, on Windows, `GUROBI_HOME` should point to the Gurobi installation root containing `lib/gurobi.jar`.

## Build

From the repository root:

```bash
mvn clean package
```

## Run

The program reads `config.properties` from the repository root by default:

```bash
mvn exec:java
```

A different configuration-file path can also be supplied:

```bash
mvn exec:java -Dexec.args="path/to/config.properties"
```

Outputs are written under the directory configured by `resultDirectory` (default: `result/`). Each run stores a copy of the configuration used, together with run summaries, validation information, and final solution files.

## Main paper settings

The default `config.properties` is a base **CCM/SAS** configuration for the 2-depot, 3-parking, 50-customer group. The paper's medium and large benchmark sets are obtained by running the following four instance groups, with samples 1--5 and 10 independent runs per instance:

| Depots | Parking locations | Customers | `operatorRepetitions` |
|---:|---:|---:|---:|
| 2 | 3 | 50 | 40 |
| 3 | 5 | 50 | 40 |
| 2 | 3 | 100 | 60 |
| 3 | 5 | 100 | 60 |

For PBM, change `stochasticMode=CCM` to `stochasticMode=PBM`. Other sensitivity and evaluator settings can be reproduced by changing the corresponding documented entries in the same `config.properties` file.

The physical instance filenames use the form

```text
3M-Cb-<depots>-<parking locations>-<customers>-<sample>.txt
```

Thus, the paper's 50-customer instances correspond to the medium class and the 100-customer instances to the large class.

## Reproducibility

The base stochastic setting uses truncated log-normal travel and service times, a 5th--95th percentile truncation interval, 20 CDF grid points, and two previous-arrival subintervals. The default final Monte Carlo validation uses 100,000 replications. Exact seed values and their roles are documented in [`SEED_POLICY.md`](SEED_POLICY.md).

When modifying an experiment, keep the configuration file with the run. The implementation automatically copies the active `config.properties` into the generated output directory.

## Eclipse

This is a standard Maven project. In Eclipse, import it using **File > Import > Maven > Existing Maven Projects** and select the repository root containing `pom.xml`.
