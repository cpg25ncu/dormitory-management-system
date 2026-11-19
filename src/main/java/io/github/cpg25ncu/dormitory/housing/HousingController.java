package io.github.cpg25ncu.dormitory.housing;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HousingController {
    private final Housing housing;
    private final Rooms rooms;

    HousingController(Housing housing, Rooms rooms) {
        this.housing = housing;
        this.rooms = rooms;
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
    List<Housing.Departure> history() { return housing.history(); }

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
