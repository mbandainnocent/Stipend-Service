package com.stipend.service.service;

import com.stipend.service.Event.AttendanceEvent;

public interface AttendanceEventService {
    void processAttendanceEvent(AttendanceEvent event);
}
