package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.ClassSessionResponse;
import group5.example.sportCenter.dto.CreateClassSessionRequest;
import group5.example.sportCenter.dto.UpdateClassSessionRequest;
import group5.example.sportCenter.dto.UpdateClassSessionStatusRequest;
import group5.example.sportCenter.service.ClassSessionService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/class-sessions")
public class ClassSessionController {

    private final ClassSessionService classSessionService;

    public ClassSessionController(ClassSessionService classSessionService) {
        this.classSessionService = classSessionService;
    }

    @GetMapping
    public List<ClassSessionResponse> getAll() {
        return classSessionService.getAll();
    }

    @GetMapping("/{id}")
    public ClassSessionResponse getById(@PathVariable Integer id) {
        return classSessionService.getById(id);
    }

    @GetMapping("/class/{classId}")
    public List<ClassSessionResponse> getByClassId(@PathVariable Integer classId) {
        return classSessionService.getByClassId(classId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassSessionResponse create(@Valid @RequestBody CreateClassSessionRequest request) {
        return classSessionService.create(request);
    }

    @PutMapping("/{id}")
    public ClassSessionResponse update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateClassSessionRequest request) {
        return classSessionService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ClassSessionResponse updateStatus(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateClassSessionStatusRequest request) {
        return classSessionService.updateStatus(id, request);
    }
}
