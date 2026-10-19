package com.stipend.service.Controller;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.Model.Student;
import com.stipend.service.service.StudentRegistrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
public class CreateStudentController {

    private final StudentRegistrationService studentRegistrationService;

    public CreateStudentController(StudentRegistrationService studentRegistrationService) {
        this.studentRegistrationService = studentRegistrationService;
    }

    @PostMapping
    public ResponseEntity<String> createStudent(@RequestBody StudentRegisteredEvent event) {
        studentRegistrationService.register(event);
        return ResponseEntity.ok("Student registered successfully");
    }
}
