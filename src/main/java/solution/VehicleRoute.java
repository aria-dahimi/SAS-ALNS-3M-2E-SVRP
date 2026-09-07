package solution;

import java.util.ArrayList;

import evaluation.DeterministicTimeTable;
import config.ProblemParameters;
import problem.*;

public abstract class VehicleRoute {

    public ArrayList<Node> route = new ArrayList<Node>();
    public Depot depot = null;
    public Parking parking = null;

    public int modificationFlag = 0;

    public double aggregateFailureValue = 0.0;
    public double departureTime = 0.0;
    public int departureTimeChangeFlag = 0;
    public int propagationIndex = 0;
    /** Generation of the deterministic schedule currently being propagated on this route. */
    public long deterministicScheduleGeneration = 0L;
    public long stochasticEvaluationGeneration = 0L;

    public abstract void generateRoute(Node visitToInsert);
    public abstract void computeCost();
    public abstract void calculateCostofRoute();
    public abstract int countRepeatedVehicleMeetingViolations();
    public abstract void computeLV();
    public abstract void computeUV();
    public abstract String toStrRouteNewReport();


    /**
     * Service time at a route position.
     * In DELLAERT, repeated logical records at one physical satellite share a single service interval.
     */
    protected double serviceTimeAt(int index) {
        Node node = getNode(index);
        double service = DeterministicTimeTable.getServiceTime(node);

        if (!ProblemParameters.isDellaert() || !(node instanceof VirtualMeetingPoint) || index == 0) {
            return service;
        }

        Node previous = getNode(index - 1);
        if (!(previous instanceof VirtualMeetingPoint)) {
            return service;
        }

        VirtualMeetingPoint currentMeeting = (VirtualMeetingPoint) node;
        VirtualMeetingPoint previousMeeting = (VirtualMeetingPoint) previous;
        if (currentMeeting.originalMeetingPoint == null || previousMeeting.originalMeetingPoint == null) {
            return service;
        }

        return currentMeeting.originalMeetingPoint.id.equals(previousMeeting.originalMeetingPoint.id) ? 0.0 : service;
    }

    protected int leadingTransferCount() {
        int count = 0;
        for (Node node : this.route) {
            if (!(node instanceof VirtualMeetingPoint)) {
                break;
            }
            count++;
        }
        return count;
    }


    public int routeSize() {
        return route.size();
    }
    public int countCustomers() {
        int count = 0;
        for (Node node : this.route) {
            if (node instanceof Customer) {
                count++;
            }
        }
        return count;
    }

    public Node getNode(int index) {
        return route.get(index);
    }
    public void addNodetoIndex(int index, Node node) {
        this.route.add(index, node);
    }

    public void removeNodebyIndex(int index) {
        this.route.remove(index);
    }

    public void removeNodes(NodeSet nodesToRemove) {
        /*
         * Record old synchronization dependencies before the structural links
         * can disappear.  Otherwise a meeting removed from this route can
         * disconnect its former counterpart route before dependency closure is
         * built.
         */
        for (Node nodeToRemove : nodesToRemove.nodes) {
            if (nodeToRemove.firstEchelonVehicle != null
                    && nodeToRemove.firstEchelonVehicle.route != null) {
                nodeToRemove.firstEchelonVehicle.route.modificationFlag = 1;
            }
            if (nodeToRemove.secondEchelonVehicle != null
                    && nodeToRemove.secondEchelonVehicle.route != null) {
                nodeToRemove.secondEchelonVehicle.route.modificationFlag = 1;
            }
        }
        for (Node nodeToRemove : nodesToRemove.nodes) {
            this.route.remove(nodeToRemove);
        }
        if (routeSize() != 0) {
            for (Node node : this.route) {
                if (node instanceof VirtualMeetingPoint) {
                    node.firstEchelonVehicle.route.modificationFlag = 1;
                    node.secondEchelonVehicle.route.modificationFlag = 1;
                } else {
                    if (node.firstEchelonVehicle != null) {
                        node.firstEchelonVehicle.route.modificationFlag = 1;
                    }
                    if (node.secondEchelonVehicle != null) {
                        node.secondEchelonVehicle.route.modificationFlag = 1;
                    }
                }
            }
            this.computeLV();
            this.computeUV();
            this.modificationFlag = 1;
        }
    }

    public void clearRoute() {
        for (Node node : this.route) {
            FirstEchelonVehicle oldFirstEchelonVehicle = node.firstEchelonVehicle;
            SecondEchelonVehicle oldSecondEchelonVehicle = node.secondEchelonVehicle;

            if (oldFirstEchelonVehicle != null && oldFirstEchelonVehicle.route != null) {
                oldFirstEchelonVehicle.route.modificationFlag = 1;
            }
            if (oldSecondEchelonVehicle != null && oldSecondEchelonVehicle.route != null) {
                oldSecondEchelonVehicle.route.modificationFlag = 1;
            }

            if (node instanceof VirtualMeetingPoint) {
                ((VirtualMeetingPoint) node).clearVirtualMeeting();
            } else {
                ((Customer) node).cleanANode();
            }
        }
        this.modificationFlag = 1;
        this.route.clear();
    }
    public String toStrOrdersNewReport() {
        String text = "";
        int columnWidth = 25;

        for (int i = 0; i < this.routeSize(); i++) {
            Node node = this.getNode(i);
            String formattedId = String.format("%-" + columnWidth + "s", node.id);
            text += formattedId;

            if (i < this.routeSize() - 1) {
                text += ", ";
            }
        }
        return text;
    }

    public String toStrTWNewReport() {
        String text = "";
        int columnWidth = 25;
        for (int i = 0; i < this.routeSize(); i++) {
            Node node = this.getNode(i);
            String formattedNode = String.format("%-" + columnWidth + "s", node.toStrTWNewReport());
            text += formattedNode;

            if (i < this.routeSize() - 1) {
                text += ", ";
            }
        }
        return text;
    }

    public String toStrRtVtNewReport() {
        String text = "";
        int columnWidth = 25;

        for (int i = 0; i < this.routeSize(); i++) {
            Node node = this.getNode(i);
            String formattedNode = String.format("%-" + columnWidth + "s", node.toStrRtVtNewReport());
            text += formattedNode;

            if (i < this.routeSize() - 1) {
                text += ", ";
            }
        }
        return text;
    }

    public String toStrWaitNewReport() {
        String text = "";
        int columnWidth = 25;

        for (int i = 0; i < this.routeSize(); i++) {
            Node node = this.getNode(i);
            String formattedNode = String.format("%-" + columnWidth + "s", node.toStrWaitNewReport());
            text += formattedNode;

            if (i < this.routeSize() - 1) {
                text += ", ";
            }
        }
        return text;
    }

    public String toStrWeightNewReport() {
        String text = "";
        int columnWidth = 25;

        for (int i = 0; i < this.routeSize(); i++) {
            Node node = this.getNode(i);
            String formattedNode = String.format("%-" + columnWidth + "s", node.toStrWeightNewReport());
            text += formattedNode;

            if (i < this.routeSize() - 1) {
                text += ", ";
            }
        }
        return text;
    }

    public String toStrFailPrNewReport() {
        String text = "";
        int columnWidth = 25;

        for (int i = 0; i < this.routeSize(); i++) {
            Node node = this.getNode(i);
            String formattedNode = String.format("%-" + columnWidth + "s", node.toStrFailNewReport());
            text += formattedNode;

            if (i < this.routeSize() - 1) {
                text += ", ";
            }
        }
        return text;
    }
}
