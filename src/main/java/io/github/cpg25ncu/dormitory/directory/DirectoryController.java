package io.github.cpg25ncu.dormitory.directory;

import static io.github.cpg25ncu.dormitory.common.CsvWriter.label;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import io.github.cpg25ncu.dormitory.common.CsvDownload;
import io.github.cpg25ncu.dormitory.common.Page;
import io.github.cpg25ncu.dormitory.common.ResidenceTime;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DirectoryController {
    private final StudentDirectory students;
    private final BuildingDirectory buildings;
    private final ResidenceTime time;

    DirectoryController(StudentDirectory students, BuildingDirectory buildings, ResidenceTime time) {
        this.students = students;
        this.buildings = buildings;
        this.time = time;
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
    Page<StudentDirectory.Student> students(@RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String gender) {
        return students.search(StudentDirectory.Filter.of(q, status, gender), Page.Request.of(page, size));
    }

    @GetMapping("/admin/students/export.csv")
    void exportStudents(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                        @RequestParam(required = false) String gender, HttpServletResponse response) throws IOException {
        var filter = StudentDirectory.Filter.of(q, status, gender);
        try (var csv = CsvDownload.attachment(response, "students", time.today())) {
            csv.row("Student number", "Full name", "Gender", "Program", "Phone", "Email", "Housing status", "Building", "Room");
            students.export(filter, student -> csv.row(student.studentNumber(), student.fullName(), label(student.gender()),
                    student.program(), student.phone(), student.email(), label(student.status()),
                    student.buildingName(), student.roomNumber()));
        }
    }

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
