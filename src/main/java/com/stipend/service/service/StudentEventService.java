package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;

public interface StudentEventService {

    @SuppressWarnings("unused")
    void processStudentRegistrationEvent(StudentRegisteredEvent event);
}
