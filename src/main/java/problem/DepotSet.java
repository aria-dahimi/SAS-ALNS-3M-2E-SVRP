package problem;

import java.util.ArrayList;

public class DepotSet {
	
	public ArrayList<Depot> depots;
	
	public DepotSet() {
		this.depots = new ArrayList<Depot>();		
	}
	public void copyFrom(DepotSet sourceDepotSet){
		this.depots.clear();
		for (Depot sourceDepot : sourceDepotSet.depots) {
			Depot copiedDepot = new Depot(sourceDepot);
			this.addDepot(copiedDepot);
		}
	}
	public void addDepot(Depot depot) {
		depots.add(depot);
    }
    public Depot getDepot(int index){
        return this.depots.get(index);
    }
    public Depot getDepotID(String depotId){
    	Depot depot = new Depot();
    	for (Depot candidateDepot : this.depots) {
    		if (candidateDepot.id.equals(depotId)) {
    			depot = candidateDepot;
    			break;
    		}
    	}
        return depot;
    }
    public int size(){
        return this.depots.size();
    }
}
