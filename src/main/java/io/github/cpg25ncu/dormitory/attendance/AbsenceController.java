package io.github.cpg25ncu.dormitory.attendance;

import static io.github.cpg25ncu.dormitory.common.CsvWriter.label;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import io.github.cpg25ncu.dormitory.common.CsvDownload;
import io.github.cpg25ncu.dormitory.common.Page;
import io.github.cpg25ncu.dormitory.common.ResidenceTime;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/absences")
public class AbsenceController {
    private final Absences absences;
    private final ResidenceTime time;

    AbsenceController(Absences absences, ResidenceTime time) {
        this.absences = absences;
        this.time = time;
    }

    @GetMapping
    Page<Absences.Absence> list(@AuthenticationPrincipal AccountPrincipal account,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) Long buildingId,
                                @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return absences.search(account, Absences.Filter.of(q, status, buildingId, from, to), Page.Request.of(page, size));
    }

    @GetMapping("/export.csv")
    void export(@AuthenticationPrincipal AccountPrincipal account,
                @RequestParam(required = false) String q,
                @RequestParam(required = false) String status,
                @RequestParam(required = false) Long buildingId,
                @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                HttpServletResponse response) throws IOException {
        var filter = Absences.Filter.of(q, status, buildingId, from, to);
        try (var csv = CsvDownload.attachment(response, "absences", time.today())) {
            csv.row("Date", "Student number", "Full name", "Building", "Room", "Reason", "Status", "Recorded by",
                    "Recorded at", "Corrected by", "Corrected at", "Withdrawn by", "Withdrawn at", "Withdrawal reason");
            absences.export(account, filter, record -> csv.row(record.absenceDate(), record.studentNumber(),
                    record.fullName(), record.buildingName(), record.roomNumber(), record.reason(), label(record.status()),
                    record.recordedBy(), time.format(record.recordedAt()), record.updatedBy(), time.format(record.updatedAt()),
                    record.withdrawnBy(), time.format(record.withdrawnAt()), record.withdrawalReason()));
        }
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
