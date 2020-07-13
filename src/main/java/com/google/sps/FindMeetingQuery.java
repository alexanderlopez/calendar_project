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

public final class FindMeetingQuery {
    public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {
        ArrayList<TimeRange> returnList = new ArrayList<TimeRange>();
        returnList.add(TimeRange.WHOLE_DAY);

        trimList(returnList, request.getAttendees(), events);

        removeSmallSlots(returnList, request.getDuration());

        return returnList;
    }

    private void removeSmallSlots(ArrayList<TimeRange> timeList, long duration) {
        for (TimeRange timeSlot : (ArrayList<TimeRange>) timeList.clone()) {
            if (timeSlot.duration() < duration) {
                timeList.remove(timeSlot);
            }
        }
    }

    private void trimList(ArrayList<TimeRange> timeList, Collection<String> participants,
                          Collection<Event> events) {
        for (Event event : events) {
            boolean trivialIntersection = true;
            for(String participant : participants) {
                if (event.getAttendees().contains(participant)) {
                    trivialIntersection = false;
                }
            }

            if (trivialIntersection) {
                continue;
            }

            TimeRange eventRange = event.getWhen();
            for (TimeRange timeSlot : (ArrayList<TimeRange>) timeList.clone()) {
                if(!timeSlot.overlaps(eventRange)) {
                    continue;
                }

                splitRange(timeList, timeSlot, eventRange);
            }
        }
    }

    private void splitRange(ArrayList<TimeRange> timeList, TimeRange current, TimeRange event) {
        if (current.contains(event)) {
            TimeRange before = TimeRange.fromStartEnd(current.start(), event.start(), false);
            TimeRange after = TimeRange.fromStartEnd(event.end(), current.end(), false);

            timeList.remove(current);
            if (before.duration() > 0) {
                timeList.add(before);
            }
            if (after.duration() > 0) {
                timeList.add(after);
            }
        }
        else if (event.contains(current)) {
            timeList.remove(current);
        }
        else if (event.contains(current.start())) {
            TimeRange updated = TimeRange.fromStartEnd(event.end(), current.end(), false);
            timeList.remove(current);
            timeList.add(updated);
        }
        else if (current.contains(event.start())) {
            TimeRange updated = TimeRange.fromStartEnd(current.start(), event.start(), false);
            timeList.remove(current);
            timeList.add(updated);
        }
        else {
            System.out.println("This is wrong.");
        }
    }

    private TimeRange intersect() {
        return null;
    }
}
