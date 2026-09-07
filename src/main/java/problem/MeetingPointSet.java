package problem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeetingPointSet {

	public List<MeetingPoint> meetingPoints = new ArrayList<>();

	/* ID lookup cache. Direct list-size changes also invalidate it. */
	private final Map<String, MeetingPoint> meetingById = new HashMap<>();
	private boolean isMeetingByIdIndexDirty = true;
	private int indexedMeetingPointCount = -1;

	public MeetingPointSet(){
		meetingPoints = new ArrayList<MeetingPoint>();
	}

	public static MeetingPointSet copyMeetingPoints(MeetingPointSet sourceMeetingPoints) {
		MeetingPointSet copiedMeetingPoints = new MeetingPointSet();
		for (MeetingPoint sourceMeeting : sourceMeetingPoints.meetingPoints) {
			MeetingPoint copiedMeeting = new MeetingPoint(sourceMeeting);
			copiedMeetingPoints.addMeeting(copiedMeeting);
		}
		return copiedMeetingPoints;
	}


    public int size(){
        return this.meetingPoints.size();
    }


	public void addMeeting(MeetingPoint meetingPoint) {
		this.meetingPoints.add(meetingPoint);
		this.isMeetingByIdIndexDirty = true;
	}

	public MeetingPoint getMeeting(int index) {
		return this.meetingPoints.get(index);
	}

	public MeetingPoint getMeetingById(String meetingPointId) {
		ensureMeetingPointIndex();
		return this.meetingById.get(meetingPointId);
	}

	private void ensureMeetingPointIndex() {
		/* Direct changes to the public list can bypass the mutating helpers. */
		if (!this.isMeetingByIdIndexDirty
				&& this.indexedMeetingPointCount == this.meetingPoints.size()) {
			return;
		}

		this.meetingById.clear();
		for (MeetingPoint meetingPoint : this.meetingPoints) {
			// Keep the last match if malformed duplicate IDs are present.
			this.meetingById.put(meetingPoint.id, meetingPoint);
		}

		this.indexedMeetingPointCount = this.meetingPoints.size();
		this.isMeetingByIdIndexDirty = false;
	}
	public String toStrNewReport() {
        String text = "";
        for (int i = 0; i < size(); i++) {
        	if (i < size()-1) {
				text += getMeeting(i).id+"-";
        	} else {
				text += getMeeting(i).id;
        	}
        }
        return text;
    }

}
