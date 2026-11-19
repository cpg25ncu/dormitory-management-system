package io.github.cpg25ncu.dormitory.attendance;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/absences")
public class AbsenceController {
    private final Absences absences;

    AbsenceController(Absences absences) {
        this.absences = absences;
    }

    @GetMapping
    List<Absences.Absence> list(@AuthenticationPrincipal AccountPrincipal account) { return absences.list(account); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    void record(@Valid @RequestBody Absences.NewAbsence input, @AuthenticationPrincipal AccountPrincipal account) {
        absences.record(input, account);
    }
}
