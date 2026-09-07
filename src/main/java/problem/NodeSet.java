package problem;

import java.util.ArrayList;
import search.RandomizationUtils;


public class NodeSet {
	public ArrayList<Node> nodes;
	
	public NodeSet(){
		nodes = new ArrayList<Node>();
	}
	
	public void shuffleNodes(java.util.Random randomGenerator){
		RandomizationUtils.shuffle(nodes, randomGenerator);
    }
	public void addMtoVisits(VirtualMeetingPointSet activeVirtualMeetings){
		for (VirtualMeetingPoint virtualMeeting : activeVirtualMeetings.virtualMeetingPoints) {
			nodes.add(virtualMeeting);
		}
    }

	public int getVisitCount() {
		return nodes.size();
	}

	public Node getNode(int index) {
		return nodes.get(index);
	}
	
	public void addNode(Node visit) {
		this.nodes.add(visit);
    }
	
	public void removeNode(Node visit) {
		this.nodes.remove(visit);
    }
	public void clear() {
		this.nodes.clear();
	}
}
