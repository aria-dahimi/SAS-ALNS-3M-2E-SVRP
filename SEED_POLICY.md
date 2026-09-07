# Seed policy

The implementation separates stochastic-instance generation, heuristic search, and Monte Carlo evaluation so that each source of randomness is reproducible.

- **Stochastic benchmark generation:** `distributionSeed=0`. Travel- and service-time CVs are assigned by a stable hash of this seed and the node/arc identity. Therefore, the same primitive stochastic distributions are obtained for a given benchmark instance independently of loop or enumeration order.
- **Independent SAS-ALNS runs:** `searchSeedStart=1230` with `numberOfRuns=10`, giving search seeds **1230--1239**. Run `r` uses `searchSeedStart + (r-1)`.
- **Search-time Monte Carlo:** `simulationSearchSeed=1230`. This is used only when `stochasticEvaluator=SIMULATION`. With `simulationUseCommonRandomNumbers=true`, candidate evaluations use common random numbers.
- **Final Monte Carlo validation:** `finalSimulationSeed=987654321`. This stream is separate from the heuristic search seed and is used for the independent final-solution validation. The reported final validation uses `finalSimulationReplications=100000`.

All compared planning approaches use the same generated stochastic benchmark distributions when the same `distributionSeed` and CV ranges are used. The seed values are also stored in the copied `config.properties` written to each experiment-output directory.
