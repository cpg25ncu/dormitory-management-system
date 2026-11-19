package io.github.cpg25ncu.dormitory.directory;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DirectoryController {
    private final StudentDirectory students;
    private final BuildingDirectory buildings;

    DirectoryController(StudentDirectory students, BuildingDirectory buildings) {
        this.students = students;
        this.buildings = buildings;
    }

    @GetMapping("/buildings")
    List<BuildingDirectory.Building> buildings(@AuthenticationPrincipal AccountPrincipal account) {
        return buildings.list(account);
    }

    @PostMapping("/admin/buildings")
    @ResponseStatus(HttpStatus.CREATED)
    void createBuilding(@Valid @RequestBody BuildingDirectory.BuildingInput input) { buildings.create(input); }

    @PutMapping("/admin/buildings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateBuilding(@PathVariable long id, @Valid @RequestBody BuildingDirectory.BuildingInput input) {
        buildings.update(id, input);
    }

    @DeleteMapping("/admin/buildings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteBuilding(@PathVariable long id) { buildings.delete(id); }

    @GetMapping("/admin/students")
    List<StudentDirectory.Student> students() { return students.list(); }

    @PostMapping("/admin/students")
    @ResponseStatus(HttpStatus.CREATED)
    void createStudent(@Valid @RequestBody StudentDirectory.StudentInput input) { students.create(input); }

    @PutMapping("/admin/students/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateStudent(@PathVariable long id, @Valid @RequestBody StudentDirectory.StudentInput input) {
        students.update(id, input);
    }

    @DeleteMapping("/admin/students/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteStudent(@PathVariable long id) { students.delete(id); }
}
