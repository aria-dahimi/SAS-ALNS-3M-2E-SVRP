package problem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualMeetingPointSetTest {

    @Test
    void addVirtualMeetingsAddsOnlyMissingMeetings() {
        MeetingPoint firstPhysical = new MeetingPoint("S1", 0, 0, 0, 100, 0);
        MeetingPoint secondPhysical = new MeetingPoint("S2", 1, 1, 0, 100, 0);
        VirtualMeetingPoint first = new VirtualMeetingPoint("S1v1", firstPhysical);
        VirtualMeetingPoint second = new VirtualMeetingPoint("S2v1", secondPhysical);

        VirtualMeetingPointSet destination = new VirtualMeetingPointSet();
        destination.addVirtualMeeting(first);

        VirtualMeetingPointSet incoming = new VirtualMeetingPointSet();
        incoming.addVirtualMeeting(first);
        incoming.addVirtualMeeting(second);

        destination.addVirtualMeetings(incoming);

        assertEquals(2, destination.getVirtualMeetingCount());
        assertEquals(1, destination.virtualMeetingPoints.stream()
                .filter(meeting -> meeting.id.equals("S1v1"))
                .count());
        assertEquals(1, destination.virtualMeetingPoints.stream()
                .filter(meeting -> meeting.id.equals("S2v1"))
                .count());
    }

    @Test
    void addVirtualMeetingsIsIdempotent() {
        MeetingPoint physical = new MeetingPoint("S1", 0, 0, 0, 100, 0);
        VirtualMeetingPoint meeting = new VirtualMeetingPoint("S1v1", physical);

        VirtualMeetingPointSet destination = new VirtualMeetingPointSet();
        VirtualMeetingPointSet incoming = new VirtualMeetingPointSet();
        incoming.addVirtualMeeting(meeting);

        destination.addVirtualMeetings(incoming);
        destination.addVirtualMeetings(incoming);

        assertEquals(1, destination.getVirtualMeetingCount());
    }
}
