// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

public final class FindMeetingQuery {

    ArrayList<EventPoint> pointList;
    ArrayList<Range> partitionedRanges;
    HashMap<String, Integer> optionalAttendees;
    HashMap<String, Integer> mandatoryAttendees;
    PriorityQueue<Range> rangeQueue;

    static final Comparator<Range> MAX_ATTENDEE_ORDER =
        (Range e1, Range e2) ->
        {
            if (!e1.mandatoryAvailable && !e2.mandatoryAvailable) {
                return 0;
            }
            else if (!e1.mandatoryAvailable || !e2.mandatoryAvailable) {
                return (e1.mandatoryAvailable ? -1 : 1);
            }
            else {
                return Integer.compare(e2.attendees.size(),
                    e1.attendees.size());
            }
        };

    static final Comparator<EventPoint> CHRONOLOGICAL_ORDER =
        (EventPoint e1, EventPoint e2) ->
        { return Long.compare(e1.time, e2.time); };

    public Collection<TimeRange> query(Collection<Event> events,
            MeetingRequest request) {
        pointList = new ArrayList<EventPoint>();
        partitionedRanges = new ArrayList<Range>();
        rangeQueue = new PriorityQueue<Range>(MAX_ATTENDEE_ORDER);

        preprocessEvents(events, request.getAttendees(),
            request.getOptionalAttendees());

        initializeHashmap(request.getAttendees(),
            request.getOptionalAttendees());

        EventPoint previousPoint = new EventPoint(TimeRange.START_OF_DAY,
            EventPoint.POINT_TYPE_START);

        for (int i = 0; i < pointList.size(); i++) {
            EventPoint currentPoint = pointList.get(i);

            boolean addToMap = false;
            if (currentPoint.pointType == EventPoint.POINT_TYPE_START) {
                addToMap = true;
            }
            else if (currentPoint.pointType == EventPoint.POINT_TYPE_END) {
                addToMap = false;
            }

            TimeRange currentRange = TimeRange.fromStartEnd(
                previousPoint.time, currentPoint.time, false);

            if (currentRange.duration() > 0) {
                Collection<String> freeAttendees = getFreeAttendees(
                    request.getAttendees(), request.getOptionalAttendees());
                boolean mandatoryAvailable = !(freeAttendees == null);

                Range range = new Range(mandatoryAvailable, currentRange,
                    freeAttendees, partitionedRanges.size());
                partitionedRanges.add(range);
                rangeQueue.add(range);
            }

            if (currentPoint.event == null) {
                break;
            }

            updateAttendeeCounter(currentPoint.event, addToMap);
            previousPoint = currentPoint;
        }

        ArrayList<TimeRange> finalList =
            processTimeRanges(request.getDuration());
        if (finalList == null) {
            return (new ArrayList<TimeRange>());
        }
        finalList.sort(TimeRange.ORDER_BY_START);

        List<TimeRange> removeDuplicates = new ArrayList<TimeRange>();

        TimeRange lastAdded = null;
        for (TimeRange timeRange : finalList) {
            if (!timeRange.equals(lastAdded)) {
                removeDuplicates.add(timeRange);
                lastAdded = timeRange;
            }
        }

        return removeDuplicates;
    }

    private ArrayList<TimeRange> processTimeRanges(long duration) {

        ArrayList<TimeRange> returnRange = new ArrayList<TimeRange>();
        Collection<String> workingAttendeeList = null;

        while (!rangeQueue.isEmpty()) {
            Range currentRange = rangeQueue.poll();

            if (!currentRange.mandatoryAvailable) {
                return null;
            }

            int startTime = cascadeLeft(currentRange);
            int endTime = cascadeRight(currentRange);

            TimeRange expandedTimeRange = TimeRange.fromStartEnd(startTime,
                endTime, false);

            if (expandedTimeRange.duration() >= duration) {
                workingAttendeeList = currentRange.attendees;
                returnRange.add(expandedTimeRange);
                break;
            }
        }

        if (workingAttendeeList == null) {
            return null;
        }

        while (!rangeQueue.isEmpty()) {
            // Add remaining events that fit the list given
            Range currentRange = rangeQueue.poll();

            if (!currentRange.mandatoryAvailable) {
                break;
            }

            if (currentRange.attendees.size() < workingAttendeeList.size()) {
                break;
            }

            if (currentRange.attendees.containsAll(workingAttendeeList)) {
                int startTime = cascadeLeft(currentRange);
                int endTime = cascadeRight(currentRange);

                TimeRange expandedTimeRange = TimeRange.fromStartEnd(startTime,
                    endTime, false);

                if (expandedTimeRange.duration() >= duration) {
                    returnRange.add(expandedTimeRange);
                }
            }
        }

        return returnRange;
    }

    private int cascadeLeft(Range startRange) {
        Range breakRange = partitionedRanges.get(0);

        for (int i = startRange.index; i >= 0; i--) {
            Range currentRange = partitionedRanges.get(i);

            if (!currentRange.mandatoryAvailable) {
                breakRange = partitionedRanges.get(i + 1);
                break;
            }

            if (!currentRange.attendees.containsAll(startRange.attendees)) {
                breakRange = partitionedRanges.get(i + 1);
                break;
            }
        }

        return breakRange.timeRange.start();
    }

    private int cascadeRight(Range startRange) {
        Range breakRange = partitionedRanges.get(partitionedRanges.size() - 1);

        for (int i = startRange.index; i < partitionedRanges.size(); i++) {
            Range currentRange = partitionedRanges.get(i);

            if (!currentRange.mandatoryAvailable) {
                breakRange = partitionedRanges.get(i - 1);
                break;
            }

            if (!currentRange.attendees.containsAll(startRange.attendees)) {
                breakRange = partitionedRanges.get(i - 1);
                break;
            }
        }

        return breakRange.timeRange.end();
    }

    private Collection<String> getFreeAttendees(
            Collection<String> mandatoryList,
            Collection<String> optionalList) {

        ArrayList<String> returnList = new ArrayList<String>();

        for (String attendee : mandatoryList) {
            if (mandatoryAttendees.get(attendee) > 0) {
                return null;
            }
        }

        for (String attendee : optionalList) {
            if (optionalAttendees.get(attendee) == 0) {
                returnList.add(attendee);
            }
        }

        return returnList;
    }

    private void updateAttendeeCounter(Event event, boolean add) {
        int change = add ? 1 : -1;

        for (String attendee : event.getAttendees()) {
            if (optionalAttendees.containsKey(attendee)) {
                optionalAttendees.put(attendee,
                    optionalAttendees.get(attendee) + change);
            }
            else if (mandatoryAttendees.containsKey(attendee)) {
                mandatoryAttendees.put(attendee,
                    mandatoryAttendees.get(attendee) + change);
            }
        }
    }

    private void initializeHashmap(Collection<String> mandatoryAttendeesList,
            Collection<String> optionalAttendeesList) {
        optionalAttendees = new HashMap<String, Integer>();
        mandatoryAttendees = new HashMap<String, Integer>();

        for (String attendee : mandatoryAttendeesList) {
            mandatoryAttendees.put(attendee, 0);
        }

        for (String attendee : optionalAttendeesList) {
            optionalAttendees.put(attendee, 0);
        }
    }

    private void preprocessEvents(Collection<Event> events,
            Collection<String> mandatoryAttendees,
            Collection<String> optionalAttendees) {

        for (Event event : events) {
            Collection<String> currentEventAttendees = event.getAttendees();

            for (String attendee : currentEventAttendees) {
                if (mandatoryAttendees.contains(attendee) ||
                    optionalAttendees.contains(attendee)) {

                    EventPoint currentEventStart = new EventPoint(event,
                        EventPoint.POINT_TYPE_START);
                    EventPoint currentEventEnd = new EventPoint(event,
                        EventPoint.POINT_TYPE_END);

                    pointList.add(currentEventStart);
                    pointList.add(currentEventEnd);
                }
            }
        }

        pointList.add(new EventPoint(TimeRange.END_OF_DAY + 1,
            EventPoint.POINT_TYPE_END));

        pointList.sort(CHRONOLOGICAL_ORDER);
    }

    private class Range {
        boolean mandatoryAvailable;
        TimeRange timeRange;
        Collection<String> attendees;
        int index;

        public Range(boolean mandatoryAvailable, TimeRange timeRange,
                Collection<String> attendees, int index) {
            this.mandatoryAvailable = mandatoryAvailable;
            this.timeRange = timeRange;
            this.attendees = attendees;
            this.index = index;
        }
    }

    private class EventPoint {

        static final int POINT_TYPE_START = 0;
        static final int POINT_TYPE_END = 1;

        Event event;
        int time;
        int pointType;

        public EventPoint(Event event, int pointType) {
            this.event = event;
            this.pointType = pointType;

            if (pointType == POINT_TYPE_START) {
                time = event.getWhen().start();
            }
            else if (pointType == POINT_TYPE_END) {
                time = event.getWhen().end();
            }
        }

        public EventPoint(int time, int pointType) {
            this.event = null;
            this.time = time;
            this.pointType = pointType;
        }
    }
}
