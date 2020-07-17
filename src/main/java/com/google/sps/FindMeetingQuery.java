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
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.Iterator;

public final class FindMeetingQuery {

    private ArrayList<TimeRange> queryResult;
    private PriorityQueue<Event> guestEventQueue;
    private PriorityQueue<Event> eventQueue;

    public Collection<TimeRange> query(Collection<Event> events,
            MeetingRequest request) {
        queryResult = new ArrayList<TimeRange>();
        eventQueue =
            new PriorityQueue<Event>((Event e1, Event e2) ->
                TimeRange.ORDER_BY_START.compare(e1.getWhen(), e2.getWhen()));
        guestEventQueue =
            new PriorityQueue<Event>((Event e1, Event e2) ->
                TimeRange.ORDER_BY_START.compare(e1.getWhen(), e2.getWhen()));

        TimeRange currentRange = TimeRange.WHOLE_DAY;

        checkEvents(events, request.getAttendees(),
            request.getOptionalAttendees());

        while (!eventQueue.isEmpty()) {
            TimeRange currentEventTimeRange = eventQueue.poll().getWhen();

            if (!currentEventTimeRange.overlaps(currentRange)) {
                continue;
            }

            TimeRange addRange = TimeRange.fromStartEnd(currentRange.start(),
                                 currentEventTimeRange.start(), false);

            if (addRange.duration() >= request.getDuration()) {
                queryResult.add(addRange);
            }

            currentRange = TimeRange.fromStartEnd(currentEventTimeRange.end(),
                           currentRange.end(), false);
            if (currentRange.duration() <= 0) {
                break;
            }
        }

        if (currentRange.duration() >= request.getDuration()) {
            queryResult.add(currentRange);
        }

        Collection<TimeRange> optionalCheck = considerOptional(
            request.getDuration());

        if (optionalCheck.isEmpty()) {
            return queryResult;
        }

        return optionalCheck;
    }

    private Collection<TimeRange> considerOptional(long duration) {
        ArrayList<TimeRange> optionalResult = new ArrayList<TimeRange>();

        if (queryResult.isEmpty()) {
            return optionalResult;
        }

        if (guestEventQueue.isEmpty()) {
            return optionalResult;
        }

        Iterator<TimeRange> availableRangeIterator = queryResult.iterator();
        TimeRange workingTimeRange = availableRangeIterator.next();
        TimeRange currentEventTimeRange = guestEventQueue.poll().getWhen();

        eventIteration:
        while (true) {

            if (workingTimeRange.start() >= currentEventTimeRange.end()) {
                if (guestEventQueue.isEmpty()) {
                    break eventIteration;
                }

                currentEventTimeRange = guestEventQueue.poll().getWhen();
            }

            while (!currentEventTimeRange.overlaps(workingTimeRange)
                    && workingTimeRange.start() < currentEventTimeRange.end()) {
                if (!availableRangeIterator.hasNext()) {
                    // Might be able too immediately return here.
                    break eventIteration;
                }

                optionalResult.add(workingTimeRange);
                workingTimeRange = availableRangeIterator.next();
            }

            if (currentEventTimeRange.overlaps(workingTimeRange)) {
                TimeRange[] rangeCut = handleOverlap(
                    currentEventTimeRange, workingTimeRange, duration);

                if (rangeCut[0] != null) {
                    optionalResult.add(rangeCut[0]);
                }
                if (rangeCut[1] != null) {
                    workingTimeRange = rangeCut[1];
                }
                else {
                    if (!availableRangeIterator.hasNext()) {
                        workingTimeRange = null;
                        break eventIteration;
                    }

                    workingTimeRange = availableRangeIterator.next();
                }
            }
        }

        while (workingTimeRange != null) {
            optionalResult.add(workingTimeRange);

            if (availableRangeIterator.hasNext()) {
                workingTimeRange = availableRangeIterator.next();
            }
            else {
                workingTimeRange = null;
            }
        }

        return optionalResult;
    }

    private TimeRange[] handleOverlap(TimeRange eventRange,
            TimeRange workingRange, long duration) {
        TimeRange[] returnRange = new TimeRange[2];

        if (eventRange.contains(workingRange)) {
            return returnRange;
        }
        else if (workingRange.contains(eventRange)) {
            TimeRange before = TimeRange.fromStartEnd(workingRange.start(),
                eventRange.start(), false);
            TimeRange after = TimeRange.fromStartEnd(eventRange.end(),
                workingRange.end(), false);

            if (before.duration() >= duration) {
                returnRange[0] = before;
            }
            if (after.duration() >= duration) {
                returnRange[1] = after;
            }
        }
        else if (workingRange.start() < eventRange.start()) {
            TimeRange trimRange = TimeRange.fromStartEnd(workingRange.start(),
                eventRange.start(), false);

            if (trimRange.duration() >= duration) {
                returnRange[0] = trimRange;
            }
        }
        else {
            TimeRange trimRange = TimeRange.fromStartEnd(eventRange.end(),
                workingRange.end(), false);

            if (trimRange.duration() >= duration) {
                returnRange[1] = trimRange;
            }
        }

        return returnRange;
    }

    private void checkEvents(Collection<Event> events,
            Collection<String> guestList, Collection<String> optionalList) {

        for (Event event : events) {
            Collection<String> eventGuestList = event.getAttendees();
            boolean hasAttendee = false;
            boolean hasOptionalAttendee = false;

            for (String guest : eventGuestList) {
                if (guestList.contains(guest)) {
                    hasAttendee = true;
                }
                if (optionalList.contains(guest)) {
                    hasOptionalAttendee = true;
                }
            }

            if (hasAttendee) {
                eventQueue.add(event);
            }
            else if (hasOptionalAttendee) {
                guestEventQueue.add(event);
            }
        }
    }
}
