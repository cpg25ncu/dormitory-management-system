import {actionGroup, button, element, formatDate, formatTimestamp, page, person, pill, table, utcDate} from "./ui.js";

const roomName = data => data.buildingName + " · " + data.roomNumber;
const locationCell = row => person(row.roomNumber, row.buildingName);
const option = (label = "Select an option") => ["", label];
const primary = (label, action) => button(label, action, "primary");
const tableLink = (label, href) => element("a", {class: "button subtle", href: "#" + href}, label);
const emptyState = label => element("p", {class: "empty"}, label);

function metric(label, value) {
    return element("div", {class: "metric"}, element("dt", {}, label), element("dd", {}, value));
}

function form(ctx, title, fields, path, method, transform, success) {
    ctx.openForm({title, fields, action: data => ctx.api(path, method, transform(data)), success});
}

function remove(ctx, noun, name, path) {
    ctx.openForm({
        title: "Delete " + noun, submit: "Delete", destructive: true,
        note: "Delete " + name + "? Records linked to housing history cannot be deleted.",
        action: () => ctx.api(path, "DELETE"), success: "Record deleted."
    });
}

async function overview(ctx) {
    const [buildings, rooms, absences] = await Promise.all([
        ctx.api("/buildings"), ctx.api("/rooms"), ctx.api("/absences")
    ]);
    const occupied = rooms.reduce((count, room) => count + room.occupied, 0);
    const capacity = rooms.reduce((count, room) => count + room.capacity, 0);
    const metrics = element("dl", {class: "metrics"},
        metric("Buildings", buildings.length), metric("Rooms", rooms.length),
        metric("Residents", occupied), metric("Available beds", capacity - occupied));
    const byBuilding = new Map(buildings.map(building => [building.id, {capacity: 0, occupied: 0}]));
    for (const room of rooms) {
        const totals = byBuilding.get(room.buildingId);
        if (totals) {
            totals.capacity += room.capacity;
            totals.occupied += room.occupied;
        }
    }
    const occupancy = buildings.map(building => {
        const {capacity, occupied} = byBuilding.get(building.id);
        return element("div", {class: "occupancy-item"},
            element("div", {class: "occupancy-label"}, element("strong", {}, building.name),
                element("span", {}, occupied + " / " + capacity + " occupied")),
            element("progress", {max: capacity || 1, value: occupied, "aria-label": building.name + " occupancy"}));
    });
    const panels = element("div", {class: "dashboard-panels"},
        element("section", {class: "panel"},
            element("header", {class: "panel-header"}, element("h2", {}, "Occupancy by building"), tableLink("View rooms", "rooms")),
            occupancy.length ? element("div", {class: "occupancy-list"}, occupancy) : emptyState("No buildings in your workspace.")),
        table(absences.slice(0, 5), [
            ["Resident", row => person(row.fullName, row.studentNumber)],
            ["Building", row => row.buildingName],
            ["Date (UTC)", row => formatDate(row.absenceDate)]
        ], {title: "Recent absences", compact: true, link: tableLink("View records", "absences"),
            empty: "No absences have been recorded."}));
    return page("Overview", "Current room availability and recent residence activity.", null, metrics, panels);
}

async function buildingView(ctx) {
    const [buildings, staff] = await Promise.all([ctx.api("/buildings"), ctx.isAdmin ? ctx.api("/admin/staff") : []]);
    function edit(building) {
        const managerOptions = staff.filter(account => account.enabled || account.id === building?.managerId)
            .map(account => [account.id, account.displayName + (account.enabled ? "" : " (disabled)")]);
        form(ctx, building ? "Edit building" : "Add building", [
            {name: "name", label: "Building name", value: building?.name, maxLength: 80},
            {name: "description", label: "Description", type: "textarea", value: building?.description, maxLength: 240, required: false},
            {name: "managerId", label: "Dormitory staff", value: building?.managerId,
                options: [option("Unassigned"), ...managerOptions], required: false}
        ], "/admin/buildings" + (building ? "/" + building.id : ""), building ? "PUT" : "POST",
        data => ({...data, managerId: data.managerId ? Number(data.managerId) : null}), "Building saved.");
    }
    const columns = [
        ["Building", row => person(row.name, row.description)],
        ["Assigned staff", row => row.managerName || "Unassigned"]
    ];
    if (ctx.isAdmin) {
        columns.push(["Actions", row => actionGroup(button("Edit", () => edit(row)),
            button("Delete", () => remove(ctx, "building", row.name, "/admin/buildings/" + row.id)))]);
    }
    return page("Buildings", "Residence buildings and their assigned staff.", ctx.isAdmin && primary("Add building", () => edit()),
        table(buildings, columns, {title: "Buildings", search: row => [row.name, row.description, row.managerName]}));
}

async function roomView(ctx) {
    const [rooms, buildings] = await Promise.all([ctx.api("/rooms"), ctx.api("/buildings")]);
    function edit(room) {
        if (!buildings.length) { ctx.notify("Add a building before creating a room."); return; }
        const fields = [
            {name: "roomNumber", label: "Room number", value: room?.roomNumber, maxLength: 30},
            {name: "capacity", label: "Bed capacity", type: "number", value: room?.capacity || 4, min: 1, max: 30}
        ];
        if (!room) fields.unshift({name: "buildingId", label: "Building", options: [option(), ...buildings.map(b => [b.id, b.name])]});
        form(ctx, room ? "Edit room" : "Add room", fields,
            "/admin/rooms" + (room ? "/" + room.id : ""), room ? "PUT" : "POST",
            data => ({...data, capacity: Number(data.capacity), ...(room ? {} : {buildingId: Number(data.buildingId)})}), "Room saved.");
    }
    const columns = [
        ["Room", locationCell],
        ["Capacity", row => row.capacity],
        ["Occupied", row => row.occupied],
        ["Available", row => pill(row.capacity === row.occupied ? "Full" : String(row.capacity - row.occupied),
            row.capacity === row.occupied ? "warn" : "good")]
    ];
    if (ctx.isAdmin) columns.push(["Actions", row => actionGroup(button("Edit", () => edit(row)),
        button("Delete", () => remove(ctx, "room", roomName(row), "/admin/rooms/" + row.id)))]);
    return page("Rooms", "Review room capacity and available beds.", ctx.isAdmin && primary("Add room", () => edit()),
        table(rooms, columns, {
            title: "Rooms", search: row => [row.roomNumber, row.buildingName],
            filter: {label: "Filter by building", all: "All buildings",
                options: buildings.map(b => [b.id, b.name]), value: row => row.buildingId}
        }));
}

async function studentView(ctx) {
    const [students, rooms] = await Promise.all([ctx.api("/admin/students"), ctx.api("/rooms")]);
    function edit(student) {
        form(ctx, student ? "Edit student" : "Add student", [
            {name: "studentNumber", label: "Student number", value: student?.studentNumber, maxLength: 40},
            {name: "fullName", label: "Full name", value: student?.fullName, maxLength: 80},
            {name: "program", label: "Program", value: student?.program, maxLength: 100, required: false}
        ], "/admin/students" + (student ? "/" + student.id : ""), student ? "PUT" : "POST", data => data, "Student saved.");
    }
    function assign(student) {
        const transfer = Boolean(student.roomId);
        const roomsWithBeds = rooms.filter(room => room.id !== student.roomId && room.occupied < room.capacity);
        if (!roomsWithBeds.length) { ctx.notify("There are no available beds in an eligible room."); return; }
        const operation = transfer ? "transfer" : "check-in";
        form(ctx, (transfer ? "Transfer " : "Check in ") + student.fullName, [
            {name: "roomId", label: transfer ? "Destination room" : "Room",
                options: [option(), ...roomsWithBeds.map(room => [room.id, roomName(room) + " (" + (room.capacity - room.occupied) + " available)"])]}
        ], "/admin/students/" + student.id + "/" + operation, "POST", data => ({roomId: Number(data.roomId)}),
        transfer ? "Room transfer completed." : "Student checked in.");
    }
    function checkout(student) {
        ctx.openForm({
            title: "Check out " + student.fullName, submit: "Check out",
            note: "This releases the bed and records the departure in housing history.",
            fields: [{name: "reason", label: "Reason", type: "textarea", maxLength: 240}],
            action: data => ctx.api("/admin/students/" + student.id + "/checkout", "POST", data),
            success: "Student checked out."
        });
    }
    const status = {RESIDENT: "Resident", UNASSIGNED: "Unassigned", CHECKED_OUT: "Checked out"};
    return page("Students", "Manage student records, room assignments and departures.", primary("Add student", () => edit()),
        table(students, [
            ["Student", row => person(row.fullName, row.studentNumber)],
            ["Program", row => row.program || "—"],
            ["Room", row => row.roomId ? locationCell(row) : "—"],
            ["Status", row => pill(status[row.status], row.status === "RESIDENT" ? "good" : "")],
            ["Actions", row => actionGroup(
                button("Edit", () => edit(row)),
                button(row.roomId ? "Transfer" : "Check in", () => assign(row)),
                row.roomId && button("Check out", () => checkout(row)),
                row.status === "UNASSIGNED" && button("Delete", () => remove(ctx, "student", row.fullName, "/admin/students/" + row.id)))]
        ], {
            title: "Students", search: row => [row.fullName, row.studentNumber, row.program, row.buildingName, row.roomNumber],
            filter: {label: "Filter by housing status", all: "All statuses", options: Object.entries(status), value: row => row.status}
        }));
}

async function residentView(ctx) {
    const residents = await ctx.api("/residents");
    return page("Residents", "Students with an active room assignment in your workspace.", null,
        table(residents, [
            ["Resident", row => person(row.fullName, row.studentNumber)], ["Room", locationCell]
        ], {title: "Residents", search: row => [row.fullName, row.studentNumber, row.buildingName, row.roomNumber]}));
}

async function absenceView(ctx) {
    const [absences, residents] = await Promise.all([ctx.api("/absences"), ctx.api("/residents")]);
    function addAbsence() {
        if (!residents.length) { ctx.notify("There are no active residents in your workspace."); return; }
        form(ctx, "Record absence", [
            {name: "stayId", label: "Resident", options: [option(), ...residents.map(resident => [
                resident.stayId, resident.fullName + " (" + resident.studentNumber + ") · " + roomName(resident)
            ])]},
            {name: "absenceDate", label: "Absence date (UTC)", type: "date", value: utcDate(), max: utcDate()},
            {name: "reason", label: "Reason", type: "textarea", maxLength: 240}
        ], "/absences", "POST", data => ({...data, stayId: Number(data.stayId)}), "Absence recorded.");
    }
    return page("Absences", "Record resident absences and review the log for your buildings.", primary("Record absence", addAbsence),
        table(absences, [
            ["Resident", row => person(row.fullName, row.studentNumber)], ["Room", locationCell],
            ["Date (UTC)", row => formatDate(row.absenceDate)], ["Reason", row => row.reason], ["Recorded by", row => row.recordedBy]
        ], {title: "Absence records", search: row => [row.fullName, row.studentNumber, row.buildingName, row.absenceDate, row.reason]}));
}

async function historyView(ctx) {
    const history = await ctx.api("/admin/housing/history");
    const types = {TRANSFER: "Room transfer", CHECKOUT: "Check-out"};
    return page("Housing history", "Review prior room assignments, transfers and check-outs.", null,
        table(history, [
            ["Student", row => person(row.fullName, row.studentNumber)], ["Previous room", locationCell],
            ["Checked in (UTC)", row => formatTimestamp(row.checkInAt)],
            ["Closed (UTC)", row => formatTimestamp(row.checkOutAt)], ["Type", row => pill(types[row.departureType])],
            ["Reason", row => row.departureReason], ["Processed by", row => row.processedBy]
        ], {
            title: "Housing history", search: row => [row.fullName, row.studentNumber, row.buildingName, row.departureReason],
            filter: {label: "Filter by event type", all: "All events", options: Object.entries(types), value: row => row.departureType}
        }));
}

async function staffView(ctx) {
    const staff = await ctx.api("/admin/staff");
    const password = {name: "password", label: "New password", type: "password", minLength: 12, maxLength: 72, autocomplete: "new-password"};
    function create() {
        form(ctx, "Add staff account", [
            {name: "displayName", label: "Display name", maxLength: 80}, {name: "username", label: "Username", maxLength: 40}, password
        ], "/admin/staff", "POST", data => data, "Staff account created.");
    }
    function edit(account) {
        form(ctx, "Edit " + account.displayName, [
            {name: "displayName", label: "Display name", value: account.displayName, maxLength: 80},
            {name: "enabled", label: "Account status", options: [["true", "Enabled"], ["false", "Disabled"]], value: String(account.enabled)}
        ], "/admin/staff/" + account.id, "PUT", data => ({...data, enabled: data.enabled === "true"}), "Staff account updated.");
    }
    function reset(account) {
        ctx.openForm({
            title: "Reset password for " + account.displayName,
            note: "Resetting a password ends this account’s existing sessions.",
            fields: [password],
            action: data => ctx.api("/admin/staff/" + account.id + "/password", "PUT", data),
            success: "Password reset."
        });
    }
    return page("Staff accounts", "Manage access for the staff assigned to residence buildings.", primary("Add staff", create),
        table(staff, [
            ["Staff member", row => person(row.displayName, row.username)],
            ["Status", row => pill(row.enabled ? "Enabled" : "Disabled", row.enabled ? "good" : "warn")],
            ["Actions", row => actionGroup(button("Edit", () => edit(row)), button("Reset password", () => reset(row)))]
        ], {title: "Staff accounts", search: row => [row.displayName, row.username]}));
}

export const views = {
    overview: {label: "Overview", symbol: "▦", render: overview},
    buildings: {label: "Buildings", symbol: "▥", render: buildingView},
    rooms: {label: "Rooms", symbol: "▣", render: roomView},
    students: {label: "Students", symbol: "♙", render: studentView, adminOnly: true},
    residents: {label: "Residents", symbol: "♙", render: residentView, staffOnly: true},
    absences: {label: "Absences", symbol: "◷", render: absenceView},
    history: {label: "Housing history", symbol: "↻", render: historyView, adminOnly: true},
    staff: {label: "Staff accounts", symbol: "◉", render: staffView, adminOnly: true}
};
