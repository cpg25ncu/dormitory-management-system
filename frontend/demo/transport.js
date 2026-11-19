import {demoFixtures, localDay} from "./fixtures.js";

const storageKey = "dormitory-browser-demo-v1";
const staff = {id: 2, role: "STAFF", username: "staff", displayName: "Morgan Reed", enabled: true};
const admin = {id: 1, role: "ADMIN", username: "admin", displayName: "System Administrator"};
const accounts = {admin, staff};
const demoAccounts = [
    {role: "ADMIN", username: "admin", password: "browser-demo"},
    {role: "STAFF", username: "staff", password: "browser-demo"}
];
const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

function loadDemo() {
    try {
        return JSON.parse(sessionStorage.getItem(storageKey)) ?? demoFixtures();
    } catch {
        return demoFixtures();
    }
}

let data = loadDemo();

function saveDemo() {
    try {
        sessionStorage.setItem(storageKey, JSON.stringify(data));
    } catch {
        // Keep the current tab usable when browser storage is blocked.
    }
}

export const browserDemo = {
    reset() {
        if (!window.confirm("Restore the fictional records and sign out of this demo?")) return;
        data = demoFixtures();
        saveDemo();
        history.replaceState(null, "", location.pathname + location.search);
        location.reload();
    }
};

class DemoError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}

function requireCondition(condition, status, message) {
    if (!condition) throw new DemoError(status, message);
}

function find(records, id) {
    const record = records.find(item => item.id === id);
    requireCondition(record, 404, "This record was not found.");
    return record;
}

function visibleBuilding(building, user) {
    return user.role === "ADMIN" || building.managerId === user.id;
}

function roomLocation(room) {
    const {id: buildingId, name: buildingName} = find(data.buildings, room.buildingId);
    return {roomId: room.id, roomNumber: room.roomNumber, buildingId, buildingName};
}

function resident(stay) {
    const {id: studentId, studentNumber, fullName} = find(data.students, stay.studentId);
    return {stayId: stay.id, studentId, studentNumber, fullName, ...roomLocation(find(data.rooms, stay.roomId))};
}

function activeStay(studentId) {
    return data.stays.find(stay => stay.studentId === studentId && !stay.checkOutAt);
}

function occupied(roomId) {
    return data.stays.filter(stay => stay.roomId === roomId && !stay.checkOutAt).length;
}

function list(route, user) {
    const visibleBuildings = data.buildings.filter(building => visibleBuilding(building, user));
    const visibleIds = new Set(visibleBuildings.map(building => building.id));
    switch (route) {
        case "buildings":
            return visibleBuildings.map(building => ({
                ...building, managerName: building.managerId === staff.id ? staff.displayName : null
            }));
        case "rooms":
            return data.rooms.filter(room => visibleIds.has(room.buildingId)).map(room => ({
                ...room, buildingName: roomLocation(room).buildingName, occupied: occupied(room.id)
            }));
        case "residents":
            return data.stays.filter(stay => !stay.checkOutAt).map(resident)
                .filter(row => visibleIds.has(row.buildingId));
        case "absences":
            return data.absences.map(absence => ({
                ...resident(find(data.stays, absence.stayId)), ...absence
            })).filter(row => visibleIds.has(row.buildingId))
                .sort((a, b) => b.absenceDate.localeCompare(a.absenceDate) || b.id - a.id);
        case "admin/staff":
            return [staff];
        case "admin/students":
            return data.students.map(student => {
                const stay = activeStay(student.id);
                if (stay) return {...resident(stay), ...student, status: "RESIDENT"};
                const pastStay = data.stays.some(item => item.studentId === student.id);
                return {...student, stayId: null, roomId: null, roomNumber: null, buildingName: null,
                    status: pastStay ? "CHECKED_OUT" : "UNASSIGNED"};
            }).sort((a, b) => a.fullName.localeCompare(b.fullName));
        case "admin/housing/history":
            return data.stays.filter(stay => stay.checkOutAt)
                .map(stay => ({...resident(stay), ...stay}))
                .sort((a, b) => b.checkOutAt.localeCompare(a.checkOutAt) || b.id - a.id);
        default:
            throw new DemoError(404, "This view is unavailable.");
    }
}

function housing(studentId, action, input, user) {
    find(data.students, studentId);
    const stay = activeStay(studentId);
    requireCondition(action === "check-in" ? !stay : stay, 409,
        action === "check-in" ? "This student already has a room assignment." : "This student is not checked in.");
    const now = new Date().toISOString();
    if (action !== "checkout") {
        const room = find(data.rooms, input.roomId);
        requireCondition(room.id !== stay?.roomId, 409, "Select a different room.");
        requireCondition(occupied(room.id) < room.capacity, 409, "The selected room has no available beds.");
        data.stays.push({id: data.nextId++, studentId, roomId: room.id, checkInAt: now});
    }
    if (stay) Object.assign(stay, {
        checkOutAt: now, processedBy: user.displayName,
        departureType: action === "transfer" ? "TRANSFER" : "CHECKOUT",
        departureReason: action === "transfer" ? "Room transfer" : input.reason.trim()
    });
    return action === "check-in" ? 201 : 204;
}

function recordAbsence(input, user) {
    const stay = find(data.stays, input.stayId);
    const building = find(data.buildings, find(data.rooms, stay.roomId).buildingId);
    requireCondition(input.absenceDate <= localDay(), 400, "Absence dates must be today or earlier.");
    requireCondition(!stay.checkOutAt && visibleBuilding(building, user)
        && localDay(stay.checkInAt) <= input.absenceDate, 404, "Active room assignment for that date was not found.");
    requireCondition(!data.absences.some(item => item.stayId === stay.id && item.absenceDate === input.absenceDate),
        409, "This resident already has an absence for that date.");
    data.absences.push({id: data.nextId++, stayId: stay.id, absenceDate: input.absenceDate,
        reason: input.reason.trim(), recordedBy: user.displayName});
    return 201;
}

function updateDirectory(collection, id, method, input) {
    const records = data[collection];
    const entry = method === "POST" ? null : find(records, id);
    if (method === "DELETE") {
        const linked = collection === "buildings" ? data.rooms.some(room => room.buildingId === id)
            : data.stays.some(stay => (collection === "rooms" ? stay.roomId : stay.studentId) === id);
        requireCondition(!linked, 409, "A linked record prevents this operation.");
        records.splice(records.indexOf(entry), 1);
        return 204;
    }

    let fields;
    let uniqueKey;
    switch (collection) {
        case "buildings":
            fields = {name: input.name.trim(), description: input.description.trim(), managerId: input.managerId};
            uniqueKey = "name";
            break;
        case "rooms":
            fields = {buildingId: entry?.buildingId ?? input.buildingId,
                roomNumber: input.roomNumber.trim(), capacity: input.capacity};
            find(data.buildings, fields.buildingId);
            requireCondition(!entry || input.capacity >= occupied(id), 409,
                "Capacity cannot be less than the number of residents.");
            uniqueKey = "roomNumber";
            break;
        case "students":
            fields = {studentNumber: input.studentNumber.trim(), fullName: input.fullName.trim(), program: input.program.trim()};
            uniqueKey = "studentNumber";
            break;
    }
    requireCondition(fields[uniqueKey] && (collection !== "students" || fields.fullName), 400, "Complete the required fields.");
    requireCondition(!records.some(record => record.id !== id && record.buildingId === fields.buildingId
        && record[uniqueKey].toLowerCase() === fields[uniqueKey].toLowerCase()), 409, "This record already exists.");
    if (entry) Object.assign(entry, fields);
    else records.push({id: data.nextId++, ...fields});
    return entry ? 204 : 201;
}

function json(value, status = 200) {
    return new Response(JSON.stringify(value), {status, headers: {"Content-Type": "application/json"}});
}

export function request(url, {method, body}) {
    const route = url.pathname.split("/api/")[1];
    const user = data.activeUser ? accounts[data.activeUser] : null;
    try {
        if (route === "session") {
            return json({user, csrfHeader: "X-CSRF-TOKEN", csrfToken: "demo", timeZone, demo: true, demoAccounts});
        }
        if (route === "login") {
            const username = body.get("username");
            requireCondition(username === "admin" || username === "staff", 401, "Choose a demo role.");
            data.activeUser = username;
        } else if (route === "logout") {
            data.activeUser = null;
        } else {
            requireCondition(user, 401, "Choose a demo role.");
            requireCondition(!route.startsWith("admin/") || user.role === "ADMIN", 403, "This role cannot perform that action.");
            if (method === "GET") return json(list(route, user));
            const input = body ? JSON.parse(body) : null;
            const [, collection, recordId, action] = route.split("/");
            let status;
            if (route === "absences") status = recordAbsence(input, user);
            else if (collection === "students" && ["check-in", "transfer", "checkout"].includes(action)) {
                status = housing(Number(recordId), action, input, user);
            } else if (["students", "rooms", "buildings"].includes(collection) && !action) {
                status = updateDirectory(collection, Number(recordId), method, input);
            } else throw new DemoError(404, "This action is unavailable in the browser demo.");
            saveDemo();
            return new Response(null, {status});
        }
        saveDemo();
        return new Response(null, {status: 204});
    } catch (error) {
        if (error instanceof DemoError) return json({detail: error.message}, error.status);
        throw error;
    }
}
