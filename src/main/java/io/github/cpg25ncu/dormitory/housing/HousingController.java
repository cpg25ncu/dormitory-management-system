package io.github.cpg25ncu.dormitory.housing;

import static io.github.cpg25ncu.dormitory.common.CsvWriter.label;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import io.github.cpg25ncu.dormitory.common.CsvDownload;
import io.github.cpg25ncu.dormitory.common.Page;
import io.github.cpg25ncu.dormitory.common.ResidenceTime;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HousingController {
    private final Housing housing;
    private final Rooms rooms;
    private final ResidenceTime time;

    HousingController(Housing housing, Rooms rooms, ResidenceTime time) {
        this.housing = housing;
        this.rooms = rooms;
        this.time = time;
    }

    @GetMapping("/rooms")
    List<Rooms.Room> rooms(@AuthenticationPrincipal AccountPrincipal account) { return rooms.list(account); }

    @PostMapping("/admin/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    void createRoom(@Valid @RequestBody Rooms.NewRoom input) { rooms.create(input); }

    @PutMapping("/admin/rooms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateRoom(@PathVariable long id, @Valid @RequestBody Rooms.RoomUpdate input) { rooms.update(id, input); }

    @DeleteMapping("/admin/rooms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRoom(@PathVariable long id) { rooms.delete(id); }

    @GetMapping("/residents")
    List<Housing.Resident> residents(@AuthenticationPrincipal AccountPrincipal account) { return housing.residents(account); }

    @GetMapping("/admin/housing/history")
    Page<Housing.Departure> history(@RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return housing.history(Housing.HistoryFilter.of(q, type, from, to), Page.Request.of(page, size));
    }

    @GetMapping("/admin/housing/history/export.csv")
    void exportHistory(@RequestParam(required = false) String q, @RequestParam(required = false) String type,
                       @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                       HttpServletResponse response) throws IOException {
        var filter = Housing.HistoryFilter.of(q, type, from, to);
        try (var csv = CsvDownload.attachment(response, "housing-history", time.today())) {
            csv.row("Student number", "Full name", "Building", "Room", "Checked in", "Closed", "Type", "Reason", "Processed by");
            housing.exportHistory(filter, departure -> csv.row(departure.studentNumber(), departure.fullName(),
                    departure.buildingName(), departure.roomNumber(), time.format(departure.checkInAt()),
                    time.format(departure.checkOutAt()), label(departure.departureType()), departure.departureReason(),
                    departure.processedBy()));
        }
    }

    @PostMapping("/admin/students/{id}/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    void checkIn(@PathVariable long id, @Valid @RequestBody Housing.Assignment input,
                 @AuthenticationPrincipal AccountPrincipal account) {
        housing.checkIn(id, input.roomId(), account.id());
    }

    @PostMapping("/admin/students/{id}/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void transfer(@PathVariable long id, @Valid @RequestBody Housing.Assignment input,
                  @AuthenticationPrincipal AccountPrincipal account) {
        housing.transfer(id, input.roomId(), account.id());
    }

    @PostMapping("/admin/students/{id}/checkout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void checkout(@PathVariable long id, @Valid @RequestBody Housing.Checkout input,
                  @AuthenticationPrincipal AccountPrincipal account) {
        housing.checkout(id, input.reason(), account.id());
    }
}
