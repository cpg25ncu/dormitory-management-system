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
    List<Absences.Absence> list(@AuthenticationPrincipal AccountPrincipal account,
                                @RequestParam(defaultValue = "active") String status) {
        return absences.list(account, Absences.Status.parse(status));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    void record(@Valid @RequestBody Absences.NewAbsence input, @AuthenticationPrincipal AccountPrincipal account) {
        absences.record(input, account);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void correct(@PathVariable long id, @Valid @RequestBody Absences.Correction input,
                 @AuthenticationPrincipal AccountPrincipal account) {
        absences.correct(id, input, account);
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@PathVariable long id, @Valid @RequestBody Absences.Withdrawal input,
                  @AuthenticationPrincipal AccountPrincipal account) {
        absences.withdraw(id, input, account);
    }
}
