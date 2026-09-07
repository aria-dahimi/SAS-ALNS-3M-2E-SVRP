package optimization;

import gurobi.*;
import solution.Solution;
import evaluation.DeterministicTimeTable;
import config.ProblemParameters;
import problem.*;

public final class ExactScheduleOptimizer {

	private ExactScheduleOptimizer() {
	}

	/** Optimizes the schedule of the supplied solution and writes the exact schedule back. */
	public static ExactScheduleResult optimizeSchedule(Solution solution) {
		return optimizeScheduleInternal(solution, false);
	}

	/**
	 * Independently verifies a solution without modifying the search-owned copy.
	 * When fixedDepartures is true, every used route departure is fixed to the
	 * departure chosen by the stochastic search.
	 */
	public static ExactScheduleResult verifySchedule(
			Solution sourceSolution,
			boolean fixedDepartures) {
		if (sourceSolution == null) {
			throw new IllegalArgumentException("Exact verification solution cannot be null.");
		}
		if (sourceSolution.getRunContext() == null) {
			throw new IllegalStateException("Exact verification solution has no run context.");
		}
		Solution verificationCopy = Solution.fromPreparedProblem(
				sourceSolution.problem,
				sourceSolution.getRunContext());
		verificationCopy.problem.alignVirtualMeetingCopiesWith(sourceSolution.problem);
		verificationCopy.copyFrom(sourceSolution);
		return optimizeScheduleInternal(verificationCopy, fixedDepartures);
	}

	private static ExactScheduleResult optimizeScheduleInternal(
			Solution solution,
			boolean fixedDepartures) {
		if (ProblemParameters.isDellaert()) {
			/* DELLAERT uses its existing scheduler; this model assumes simultaneous rendezvous. */
			return ExactScheduleResult.skipped(
					"Exact rendezvous verification is not used for DELLAERT-2E-VRP.");
		}

		long optimizationStartMillis = System.currentTimeMillis();

		int optimizationGuardFlag = 0;

		if (optimizationGuardFlag == 0 && solution.feasibilityStatus == 0) {

			GRBEnv environment = null;
			GRBModel model = null;
			try {
				environment = new GRBEnv(true);
				environment.set(GRB.IntParam.OutputFlag, 0);
				environment.start();

				model = new GRBModel(environment);

				GRBVar[][] firstEchelonVisitTimes = new GRBVar[solution.firstEchelon.fleet.size()][solution.firstEchelon.fleet.size() + solution.activeVirtualMeetingPoints.getVirtualMeetingCount()];
				// Visit times for first-echelon route positions.
				for (int firstEchelonVehicleIndex = 0; firstEchelonVehicleIndex < solution.firstEchelon.fleet.size(); firstEchelonVehicleIndex++) {
					FirstEchelonVehicle firstEchelonVehicle = solution.firstEchelon.fleet.getVehicle(firstEchelonVehicleIndex);
				    for (int visitPosition = 0; visitPosition < firstEchelonVehicle.getVisitCount()+1; visitPosition++) {
				    	if (visitPosition == 0) {
					    	firstEchelonVisitTimes[firstEchelonVehicleIndex][visitPosition] = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "t1" + "-" + firstEchelonVehicle.id + "-" + firstEchelonVehicle.depot.id);
					    	continue;
				    	}
				    	int previousVisitIndex = visitPosition - 1;
				    	Node firstEchelonNode = firstEchelonVehicle.route.route.get(previousVisitIndex);
				    	firstEchelonVisitTimes[firstEchelonVehicleIndex][visitPosition] = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "t1" + "-" + firstEchelonVehicle.id + "-" + firstEchelonNode.id);
				    }
				}
				GRBVar[][] secondEchelonVisitTimes = new GRBVar[solution.secondEchelon.fleet.size()][solution.secondEchelon.fleet.size() + solution.secondEchelonCustomers.getCustomerCount() + solution.activeVirtualMeetingPoints.getVirtualMeetingCount()];
				// Visit times for second-echelon route positions.
				for (int secondEchelonVehicleIndex = 0; secondEchelonVehicleIndex < solution.secondEchelon.fleet.size(); secondEchelonVehicleIndex++) {
					SecondEchelonVehicle secondEchelonVehicle = solution.secondEchelon.fleet.getVehicle(secondEchelonVehicleIndex);
				    for (int visitPosition = 0; visitPosition < secondEchelonVehicle.getVisitCount()+1; visitPosition++) {
				    	if (visitPosition == 0) {
					    	secondEchelonVisitTimes[secondEchelonVehicleIndex][visitPosition] = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "t2" + "-" + secondEchelonVehicle.id + "-" + secondEchelonVehicle.parking.id);
					    	continue;
				    	}
				    	int previousVisitIndex = visitPosition - 1;
				    	Node secondEchelonNode = secondEchelonVehicle.route.route.get(previousVisitIndex);
				    	if (secondEchelonNode instanceof Customer) {
				    	secondEchelonVisitTimes[secondEchelonVehicleIndex][visitPosition] = model.addVar(secondEchelonNode.readyTime, secondEchelonNode.dueTime, 0, GRB.CONTINUOUS, "t2" + "-"+ secondEchelonVehicle.id + "-" + secondEchelonNode.id);
				    	}else {
				    	secondEchelonVisitTimes[secondEchelonVehicleIndex][visitPosition] = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "t2" + "-"+ secondEchelonVehicle.id + "-" + secondEchelonNode.id);
				    	}
				    }
				}
				GRBVar[][] firstEchelonWaitingTimes = new GRBVar[solution.firstEchelon.fleet.size()][solution.activeVirtualMeetingPoints.getVirtualMeetingCount()+solution.firstEchelon.fleet.size()];
				for (int firstEchelonVehicleIndex = 0; firstEchelonVehicleIndex < solution.firstEchelon.fleet.size(); firstEchelonVehicleIndex++) {
					FirstEchelonVehicle firstEchelonVehicle = solution.firstEchelon.fleet.getVehicle(firstEchelonVehicleIndex);
				    for (int previousVisitIndex = 1; previousVisitIndex < firstEchelonVehicle.getVisitCount()+1; previousVisitIndex++) {
				    	Node firstEchelonNode = firstEchelonVehicle.route.route.get(previousVisitIndex-1);
				    	firstEchelonWaitingTimes[firstEchelonVehicleIndex][previousVisitIndex] = model.addVar(0, GRB.INFINITY, ProblemParameters.firstEchelonVehicleWageCostEuroPerMinute, GRB.CONTINUOUS, "wt1" + "-"+ firstEchelonVehicle.id + "-" + firstEchelonNode.id);
				    }
				}
				GRBVar[][] secondEchelonWaitingTimes = new GRBVar[solution.secondEchelon.fleet.size()][solution.secondEchelonCustomers.getCustomerCount()+solution.activeVirtualMeetingPoints.getVirtualMeetingCount()+solution.secondEchelon.fleet.size()];
				for (int secondEchelonVehicleIndex = 0; secondEchelonVehicleIndex < solution.secondEchelon.fleet.size(); secondEchelonVehicleIndex++) {
					SecondEchelonVehicle secondEchelonVehicle = solution.secondEchelon.fleet.getVehicle(secondEchelonVehicleIndex);
				    for (int previousVisitIndex = 1; previousVisitIndex < secondEchelonVehicle.getVisitCount()+1; previousVisitIndex++) {
				    	Node secondEchelonNode = secondEchelonVehicle.route.route.get(previousVisitIndex-1);
				    	secondEchelonWaitingTimes[secondEchelonVehicleIndex][previousVisitIndex] = model.addVar(0, GRB.INFINITY, ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute, GRB.CONTINUOUS, "wt2" + "-"+ secondEchelonVehicle.id + "-" + secondEchelonNode.id);
				    }
				}

				if (fixedDepartures) {
					for (int firstEchelonVehicleIndex = 0; firstEchelonVehicleIndex < solution.firstEchelon.fleet.size(); firstEchelonVehicleIndex++) {
						FirstEchelonVehicle vehicle = solution.firstEchelon.fleet.getVehicle(firstEchelonVehicleIndex);
						GRBLinExpr fixedDeparture = new GRBLinExpr();
						fixedDeparture.addTerm(1.0, firstEchelonVisitTimes[firstEchelonVehicleIndex][0]);
						model.addConstr(
							fixedDeparture,
							GRB.EQUAL,
							vehicle.route.departureTime,
							"fixedDepartureFEV-" + vehicle.id);
					}
					for (int secondEchelonVehicleIndex = 0; secondEchelonVehicleIndex < solution.secondEchelon.fleet.size(); secondEchelonVehicleIndex++) {
						SecondEchelonVehicle vehicle = solution.secondEchelon.fleet.getVehicle(secondEchelonVehicleIndex);
						GRBLinExpr fixedDeparture = new GRBLinExpr();
						fixedDeparture.addTerm(1.0, secondEchelonVisitTimes[secondEchelonVehicleIndex][0]);
						model.addConstr(
							fixedDeparture,
							GRB.EQUAL,
							vehicle.route.departureTime,
							"fixedDepartureSEV-" + vehicle.id);
					}
				}

				model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);

				GRBLinExpr constraintExpression = new GRBLinExpr();
				for (int firstEchelonVehicleIndex = 0; firstEchelonVehicleIndex < solution.firstEchelon.fleet.size(); firstEchelonVehicleIndex++) {
					FirstEchelonVehicle firstEchelonVehicle = solution.firstEchelon.fleet.getVehicle(firstEchelonVehicleIndex);

					for (int visitPosition = 0; visitPosition < firstEchelonVehicle.getVisitCount(); visitPosition++) {
						constraintExpression = new GRBLinExpr();

						int previousVisitIndex = 0;
						int currentVisitIndex = 0;
						double rightHandSide = 0;

						Node firstEchelonFromNode;
						Node firstEchelonToNode;

						if (visitPosition == 0) {
							currentVisitIndex = visitPosition;
							firstEchelonToNode = firstEchelonVehicle.route.route.get(currentVisitIndex);

							rightHandSide =
									DeterministicTimeTable.getTravelTime(
											firstEchelonVehicle.depot,
											firstEchelonToNode,
											"FEV"
									);

						} else {
							previousVisitIndex = visitPosition - 1;
							currentVisitIndex = visitPosition;

							firstEchelonFromNode = firstEchelonVehicle.route.route.get(previousVisitIndex);
							firstEchelonToNode = firstEchelonVehicle.route.route.get(currentVisitIndex);

							rightHandSide =
									DeterministicTimeTable.getTravelTime(
											firstEchelonFromNode,
											firstEchelonToNode,
											"FEV"
									)
									+ DeterministicTimeTable.getServiceTime(firstEchelonFromNode);
						}

						constraintExpression.addTerm(-1.0, firstEchelonVisitTimes[firstEchelonVehicleIndex][visitPosition]);
						constraintExpression.addTerm(+1.0, firstEchelonVisitTimes[firstEchelonVehicleIndex][visitPosition + 1]);

						model.addConstr(constraintExpression, GRB.GREATER_EQUAL, rightHandSide, "c0" + firstEchelonVehicleIndex + previousVisitIndex + currentVisitIndex);
					}
				}
				for (int secondEchelonVehicleIndex = 0; secondEchelonVehicleIndex < solution.secondEchelon.fleet.size(); secondEchelonVehicleIndex++) {
					SecondEchelonVehicle secondEchelonVehicle = solution.secondEchelon.fleet.getVehicle(secondEchelonVehicleIndex);

					for (int visitPosition = 0; visitPosition < secondEchelonVehicle.getVisitCount(); visitPosition++) {
						constraintExpression = new GRBLinExpr();

						int previousVisitIndex = 0;
						int currentVisitIndex = 0;
						double rightHandSide = 0;

						Node secondEchelonFromNode;
						Node secondEchelonToNode;

						if (visitPosition == 0) {
							currentVisitIndex = visitPosition;
							secondEchelonToNode = secondEchelonVehicle.route.route.get(currentVisitIndex);

							rightHandSide =
									DeterministicTimeTable.getTravelTime(
											secondEchelonVehicle.parking,
											secondEchelonToNode,
											"SEV"
									);

						} else {
							previousVisitIndex = visitPosition - 1;
							currentVisitIndex = visitPosition;

							secondEchelonFromNode = secondEchelonVehicle.route.route.get(previousVisitIndex);
							secondEchelonToNode = secondEchelonVehicle.route.route.get(currentVisitIndex);

							rightHandSide =
									DeterministicTimeTable.getTravelTime(
											secondEchelonFromNode,
											secondEchelonToNode,
											"SEV"
									)
									+ DeterministicTimeTable.getServiceTime(secondEchelonFromNode);
						}

						constraintExpression.addTerm(-1.0, secondEchelonVisitTimes[secondEchelonVehicleIndex][visitPosition]);
						constraintExpression.addTerm(+1.0, secondEchelonVisitTimes[secondEchelonVehicleIndex][visitPosition + 1]);

						model.addConstr(constraintExpression, GRB.GREATER_EQUAL, rightHandSide, "c1" + secondEchelonVehicleIndex + previousVisitIndex + currentVisitIndex);
					}
				}

				for (int firstEchelonVehicleIndex = 0; firstEchelonVehicleIndex < solution.firstEchelon.fleet.size(); firstEchelonVehicleIndex++) {
					FirstEchelonVehicle firstEchelonVehicle = solution.firstEchelon.fleet.getVehicle(firstEchelonVehicleIndex);

					for (int visitPosition = 0; visitPosition < firstEchelonVehicle.getVisitCount(); visitPosition++) {
						constraintExpression = new GRBLinExpr();

						int previousVisitIndex = 0;
						int currentVisitIndex = 0;
						double rightHandSide = 0;

						Node firstEchelonFromNode;
						Node firstEchelonToNode;

						if (visitPosition == 0) {
							currentVisitIndex = visitPosition;
							firstEchelonToNode = firstEchelonVehicle.route.route.get(currentVisitIndex);

							rightHandSide =
									DeterministicTimeTable.getTravelTime(
											firstEchelonVehicle.depot,
											firstEchelonToNode,
											"FEV"
									);

						} else {
							previousVisitIndex = visitPosition - 1;
							currentVisitIndex = visitPosition;

							firstEchelonFromNode = firstEchelonVehicle.route.route.get(previousVisitIndex);
							firstEchelonToNode = firstEchelonVehicle.route.route.get(currentVisitIndex);

							rightHandSide =
									DeterministicTimeTable.getTravelTime(
											firstEchelonFromNode,
											firstEchelonToNode,
											"FEV"
									)
									+ DeterministicTimeTable.getServiceTime(firstEchelonFromNode);
						}

						constraintExpression.addTerm(-1.0, firstEchelonVisitTimes[firstEchelonVehicleIndex][visitPosition]);
						constraintExpression.addTerm(+1.0, firstEchelonVisitTimes[firstEchelonVehicleIndex][visitPosition + 1]);
						constraintExpression.addTerm(-1.0, firstEchelonWaitingTimes[firstEchelonVehicleIndex][visitPosition + 1]);

						model.addConstr(constraintExpression, GRB.LESS_EQUAL, rightHandSide, "c2" + firstEchelonVehicleIndex + previousVisitIndex + currentVisitIndex);
					}
				}
				for (int secondEchelonVehicleIndex = 0; secondEchelonVehicleIndex < solution.secondEchelon.fleet.size(); secondEchelonVehicleIndex++) {
					SecondEchelonVehicle secondEchelonVehicle = solution.secondEchelon.fleet.getVehicle(secondEchelonVehicleIndex);

					for (int visitPosition = 0; visitPosition < secondEchelonVehicle.getVisitCount(); visitPosition++) {
						constraintExpression = new GRBLinExpr();

						int previousVisitIndex = 0;
						int currentVisitIndex = 0;
						double rightHandSide = 0;

						Node secondEchelonFromNode;
						Node secondEchelonToNode;

						if (visitPosition == 0) {
							currentVisitIndex = visitPosition;
							secondEchelonToNode = secondEchelonVehicle.route.route.get(currentVisitIndex);

							rightHandSide =
									DeterministicTimeTable.getTravelTime(
											secondEchelonVehicle.parking,
											secondEchelonToNode,
											"SEV"
									);

						} else {
							previousVisitIndex = visitPosition - 1;
							currentVisitIndex = visitPosition;

							secondEchelonFromNode = secondEchelonVehicle.route.route.get(previousVisitIndex);
							secondEchelonToNode = secondEchelonVehicle.route.route.get(currentVisitIndex);

							rightHandSide =
									DeterministicTimeTable.getTravelTime(
											secondEchelonFromNode,
											secondEchelonToNode,
											"SEV"
									)
									+ DeterministicTimeTable.getServiceTime(secondEchelonFromNode);
						}

						constraintExpression.addTerm(-1.0, secondEchelonVisitTimes[secondEchelonVehicleIndex][visitPosition]);
						constraintExpression.addTerm(+1.0, secondEchelonVisitTimes[secondEchelonVehicleIndex][visitPosition + 1]);
						constraintExpression.addTerm(-1.0, secondEchelonWaitingTimes[secondEchelonVehicleIndex][visitPosition + 1]);

						model.addConstr(constraintExpression, GRB.LESS_EQUAL, rightHandSide, "c3" + secondEchelonVehicleIndex + previousVisitIndex + currentVisitIndex);
					}
				}

				for (int firstEchelonVehicleIndex = 0; firstEchelonVehicleIndex < solution.firstEchelon.fleet.size(); firstEchelonVehicleIndex++) {
					FirstEchelonVehicle firstEchelonVehicle = solution.firstEchelon.fleet.getVehicle(firstEchelonVehicleIndex);

					for (int firstEchelonVisitIndex = 0; firstEchelonVisitIndex < firstEchelonVehicle.getVisitCount(); firstEchelonVisitIndex++) {
						Node firstEchelonFromNode = firstEchelonVehicle.route.route.get(firstEchelonVisitIndex);

						constraintExpression = new GRBLinExpr();
						constraintExpression.addTerm(1.0, firstEchelonVisitTimes[firstEchelonVehicleIndex][firstEchelonVisitIndex + 1]);

						for (int secondEchelonVehicleIndex = 0; secondEchelonVehicleIndex < solution.secondEchelon.fleet.size(); secondEchelonVehicleIndex++) {
							SecondEchelonVehicle secondEchelonVehicle = solution.secondEchelon.fleet.getVehicle(secondEchelonVehicleIndex);
							for (int secondEchelonVisitIndex = 0; secondEchelonVisitIndex < secondEchelonVehicle.getVisitCount(); secondEchelonVisitIndex++) {
								Node secondEchelonFromNode = secondEchelonVehicle.route.route.get(secondEchelonVisitIndex);
								if (secondEchelonFromNode instanceof VirtualMeetingPoint && secondEchelonFromNode.id.equals(firstEchelonFromNode.id)) {
									constraintExpression.addTerm(-1.0, secondEchelonVisitTimes[secondEchelonVehicleIndex][secondEchelonVisitIndex + 1]);
									model.addConstr(constraintExpression, GRB.EQUAL, 0, "c4" + firstEchelonVehicleIndex + secondEchelonVehicleIndex + firstEchelonVisitIndex);
								}
							}
						}

					}
				}
				model.optimize();
				int solverStatus = model.get(GRB.IntAttr.Status);
				if (solverStatus == GRB.Status.INF_OR_UNBD) {
					model.set(GRB.IntParam.DualReductions, 0);
					model.optimize();
					solverStatus = model.get(GRB.IntAttr.Status);
				}
				if (solverStatus != GRB.Status.OPTIMAL) {
					solution.feasibilityStatus = 100;
					model.dispose();
					environment.dispose();
					long optimizationEndMillis = System.currentTimeMillis();
					long runtimeMillis = optimizationEndMillis - optimizationStartMillis;
					if (solverStatus == GRB.Status.INFEASIBLE
							|| solverStatus == GRB.Status.INF_OR_UNBD) {
						return ExactScheduleResult.infeasible(
								fixedDepartures,
								solverStatus,
								runtimeMillis,
								"The exact scheduling model is infeasible.");
					}
					return ExactScheduleResult.notOptimal(
							fixedDepartures,
							solverStatus,
							runtimeMillis,
							"Gurobi terminated without proving an optimal schedule. Status code: " + solverStatus);
				}


				for (int firstEchelonVehicleIndex = 0; firstEchelonVehicleIndex < solution.firstEchelon.fleet.size(); firstEchelonVehicleIndex++) {
					FirstEchelonVehicle firstEchelonVehicle = solution.firstEchelon.fleet.getVehicle(firstEchelonVehicleIndex);

					firstEchelonVehicle.route.modificationFlag = 1;

					Node firstNode = firstEchelonVehicle.route.getNode(0);

					double firstTravelTime =
							DeterministicTimeTable.getTravelTime(
									firstEchelonVehicle.depot,
									firstNode,
									"FEV"
							);

					firstEchelonVehicle.route.departureTime =
							firstEchelonVisitTimes[firstEchelonVehicleIndex][1].get(GRB.DoubleAttr.X)
							- firstEchelonWaitingTimes[firstEchelonVehicleIndex][1].get(GRB.DoubleAttr.X)
							- firstTravelTime;

					for (int visitPosition = 1; visitPosition < firstEchelonVehicle.getVisitCount() + 1; visitPosition++) {

						Node firstEchelonNode = firstEchelonVehicle.route.route.get(visitPosition - 1);

						firstEchelonNode.firstEchelonArrivalTime =
								firstEchelonVisitTimes[firstEchelonVehicleIndex][visitPosition].get(GRB.DoubleAttr.X)
								- firstEchelonWaitingTimes[firstEchelonVehicleIndex][visitPosition].get(GRB.DoubleAttr.X);

						firstEchelonNode.firstEchelonVisitTime =
								firstEchelonVisitTimes[firstEchelonVehicleIndex][visitPosition].get(GRB.DoubleAttr.X);

						firstEchelonNode.firstEchelonWaitingTime =
								firstEchelonWaitingTimes[firstEchelonVehicleIndex][visitPosition].get(GRB.DoubleAttr.X);
					}
				}

				for (int secondEchelonVehicleIndex = 0; secondEchelonVehicleIndex < solution.secondEchelon.fleet.size(); secondEchelonVehicleIndex++) {
					SecondEchelonVehicle secondEchelonVehicle = solution.secondEchelon.fleet.getVehicle(secondEchelonVehicleIndex);

					secondEchelonVehicle.route.modificationFlag = 1;

					Node firstNode = secondEchelonVehicle.route.getNode(0);

					double firstTravelTime =
							DeterministicTimeTable.getTravelTime(
									secondEchelonVehicle.parking,
									firstNode,
									"SEV"
							);

					secondEchelonVehicle.route.departureTime =
							secondEchelonVisitTimes[secondEchelonVehicleIndex][1].get(GRB.DoubleAttr.X)
							- secondEchelonWaitingTimes[secondEchelonVehicleIndex][1].get(GRB.DoubleAttr.X)
							- firstTravelTime;

					for (int visitPosition = 1; visitPosition < secondEchelonVehicle.getVisitCount() + 1; visitPosition++) {

						Node secondEchelonNode = secondEchelonVehicle.route.route.get(visitPosition - 1);

						secondEchelonNode.secondEchelonArrivalTime =
								secondEchelonVisitTimes[secondEchelonVehicleIndex][visitPosition].get(GRB.DoubleAttr.X)
								- secondEchelonWaitingTimes[secondEchelonVehicleIndex][visitPosition].get(GRB.DoubleAttr.X);

						secondEchelonNode.secondEchelonVisitTime =
								secondEchelonVisitTimes[secondEchelonVehicleIndex][visitPosition].get(GRB.DoubleAttr.X);

						secondEchelonNode.secondEchelonWaitingTime =
								secondEchelonWaitingTimes[secondEchelonVehicleIndex][visitPosition].get(GRB.DoubleAttr.X);
					}
				}

				solution.updateAllRouteCosts();
				solution.updateObjective();
				solution.updateFeasibilityStatus();

				long optimizationEndMillis = System.currentTimeMillis();
				try {solution.largeNeighborhoodSearch.simulatedAnnealing.searchEngine.gurobiRuntimeMillis += (optimizationEndMillis - optimizationStartMillis);
				} catch (Exception ignoredException) {}
				return ExactScheduleResult.optimal(
						fixedDepartures,
						solverStatus,
						solution.objective,
						optimizationEndMillis - optimizationStartMillis,
						ScheduleSnapshot.capture(solution),
						solution);

			} catch (GRBException gurobiException) {
				solution.feasibilityStatus = 100;
				long optimizationEndMillis = System.currentTimeMillis();
				try {
					solution.largeNeighborhoodSearch.simulatedAnnealing.searchEngine.gurobiRuntimeMillis += (optimizationEndMillis - optimizationStartMillis);
				} catch (Exception ignoredException) {
					// Final verification copies are not attached to the search engine.
				}
				return ExactScheduleResult.operationalFailure(
						fixedDepartures,
						optimizationEndMillis - optimizationStartMillis,
						"Gurobi error " + gurobiException.getErrorCode() + ": " + gurobiException.getMessage());
			} finally {
				if (model != null) {
					try {
						model.dispose();
					} catch (Exception ignoredException) {
						// Do not let cleanup hide the scheduling result.
					}
				}
				if (environment != null) {
					try {
						environment.dispose();
					} catch (Exception ignoredException) {
						// Do not let cleanup hide the scheduling result.
					}
				}
			}
		}
		return ExactScheduleResult.operationalFailure(
				fixedDepartures,
				System.currentTimeMillis() - optimizationStartMillis,
				"Exact scheduling was skipped because the supplied solution was already infeasible.");
	}
}
