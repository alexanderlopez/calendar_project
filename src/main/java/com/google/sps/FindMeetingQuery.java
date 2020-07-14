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

public final class FindMeetingQuery {

    ArrayList<TimeRange> queryResult;

    public Collection<TimeRange> query(Collection<Event> events,
            MeetingRequest request) {
        queryResult = new ArrayList<TimeRange>();

        TimeRange currentRange = TimeRange.WHOLE_DAY;

        PriorityQueue<Event> eventQueue =
                new PriorityQueue<Event>(new Comparator<Event>() {
            @Override
            public int compare(Event e1, Event e2) {
                return TimeRange.ORDER_BY_START.compare(
                        e1.getWhen(), e2.getWhen());
            }
        });

        for (Event event : events) {
            if (eventIntersectsGuestlist(event, request.getAttendees())) {
                eventQueue.add(event);
            }
        }

        while (!eventQueue.isEmpty()) {
            TimeRange currentEvent = eventQueue.poll().getWhen();

            if (!currentEvent.overlaps(currentRange)) {
                continue;
            }

            TimeRange addRange = TimeRange.fromStartEnd(currentRange.start(),
                                 currentEvent.start(), false);

            if (addRange.duration() >= request.getDuration()) {
                queryResult.add(addRange);
            }

            currentRange = TimeRange.fromStartEnd(currentEvent.end(),
                           currentRange.end(), false);
            if (currentRange.duration() <= 0) {
                break;
            }
        }

        if (currentRange.duration() >= request.getDuration()) {
            queryResult.add(currentRange);
        }

        return queryResult;
    }

    private boolean eventIntersectsGuestlist(Event event,
            Collection<String> guestList) {
        Collection<String> eventGuestlist = event.getAttendees();

        for (String name : guestList) {
            if (eventGuestlist.contains(name)) {
                return true;
            }
        }

        return false;
    }
}
