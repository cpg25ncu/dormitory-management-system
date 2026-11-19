package io.github.cpg25ncu.dormitory.account;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/staff")
public class StaffController {
    private final Accounts accounts;

    StaffController(Accounts accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    List<Accounts.Staff> list() { return accounts.staff(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    void create(@Valid @RequestBody Accounts.NewStaff input) { accounts.createStaff(input); }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@PathVariable long id, @Valid @RequestBody Accounts.StaffUpdate input) {
        accounts.updateStaff(id, input);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@PathVariable long id, @Valid @RequestBody Accounts.PasswordReset input) {
        accounts.resetStaffPassword(id, input.password());
    }
}
