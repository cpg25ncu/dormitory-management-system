import {expect, test} from "@playwright/test";
import {randomUUID} from "node:crypto";
import {readFile} from "node:fs/promises";

async function signIn(page, account) {
    await page.getByRole("button", {name: "Use " + account}).click();
    await page.getByRole("button", {name: "Sign in", exact: true}).click();
    await expect(page.getByRole("heading", {name: "Overview", exact: true})).toBeVisible();
}

async function navigate(page, name) {
    await page.getByRole("navigation", {name: "Workspace"}).getByRole("link", {name, exact: true}).click();
    await expect(page.getByRole("heading", {level: 1, name, exact: true})).toBeVisible();
}

async function save(page) {
    await page.getByRole("dialog").getByRole("button", {name: "Save", exact: true}).click();
    await expect(page.getByRole("dialog")).toBeHidden();
}

async function read(page, path) {
    const response = await page.request.get("/api" + path);
    expect(response.ok(), "GET " + path).toBeTruthy();
    return response.json();
}

/** Today's date where the residence is, which can differ from the UTC date and the browser's date. */
async function residenceToday(page) {
    const {timeZone} = await read(page, "/session");
    const parts = new Intl.DateTimeFormat("en", {timeZone, year: "numeric", month: "2-digit", day: "2-digit"})
        .formatToParts(new Date());
    const part = type => parts.find(item => item.type === type).value;
    return part("year") + "-" + part("month") + "-" + part("day");
}

async function post(page, path, data, status = 201) {
    const {csrfHeader, csrfToken} = await read(page, "/session");
    const response = await page.request.post("/api" + path, {data, headers: {[csrfHeader]: csrfToken}});
    expect(response.status(), "POST " + path).toBe(status);
}

async function housingFixtures(page, studentCount) {
    await page.goto("/");
    await signIn(page, "administrator");
    const suffix = randomUUID().slice(0, 8);
    const buildingName = "Browser Hall " + suffix;
    await post(page, "/admin/buildings", {name: buildingName});
    const building = (await read(page, "/buildings")).find(item => item.name === buildingName);
    for (const roomNumber of ["101", "102"]) {
        await post(page, "/admin/rooms", {buildingId: building.id, roomNumber, capacity: 1});
    }

    const studentNumbers = [];
    for (let index = 1; index <= studentCount; index++) {
        const studentNumber = "BROWSER-" + suffix + "-" + index;
        await post(page, "/admin/students", {studentNumber, fullName: "Browser Resident " + studentNumber, gender: "FEMALE"});
        studentNumbers.push(studentNumber);
    }
    const rooms = (await read(page, "/rooms")).filter(room => room.buildingId === building.id);
    const roster = (await read(page, "/admin/students?size=100&q=" + suffix)).items;
    const students = studentNumbers.map(number => roster.find(student => student.studentNumber === number));
    return {rooms, students};
}

async function openAssignment(page, student, action) {
    await page.getByRole("searchbox", {name: "Search Students"}).fill(student.studentNumber);
    await page.getByRole("row").filter({hasText: student.studentNumber}).getByRole("button", {name: action, exact: true}).click();
    return page.getByLabel(action === "Transfer" ? "Destination room" : "Room", {exact: true});
}

async function rejectForm(page, path, status, message) {
    const dialog = page.getByRole("dialog");
    const [response] = await Promise.all([
        page.waitForResponse(response => new URL(response.url()).pathname === "/api" + path
            && response.request().method() === "POST"),
        dialog.getByRole("button", {name: "Save", exact: true}).click()
    ]);
    expect(response.status()).toBe(status);
    await expect(dialog.getByRole("alert")).toHaveText(message);
    await expect(dialog).toBeVisible();
}

async function housingState(page, student, rooms) {
    const [students, inventory, history] = await Promise.all([
        read(page, "/admin/students?q=" + student.studentNumber), read(page, "/rooms"),
        read(page, "/admin/housing/history?q=" + student.studentNumber)
    ]);
    const roomIds = new Set(rooms.map(room => room.id));
    return {
        student: students.items.find(item => item.id === student.id),
        rooms: inventory.filter(room => roomIds.has(room.id)),
        history: history.items.filter(stay => stay.studentNumber === student.studentNumber)
    };
}

test("administrator and building staff complete a residence workflow", async ({page}) => {
    const studentNumber = "BROWSER-" + randomUUID().slice(0, 8);
    const studentName = "Browser Test Resident";
    await page.goto("/");
    await signIn(page, "administrator");
    await navigate(page, "Students");

    await page.getByRole("button", {name: "Add student"}).click();
    await page.getByLabel("Student number").fill(studentNumber);
    await page.getByLabel("Full name").fill(studentName);
    await page.getByLabel("Gender", {exact: true}).selectOption("Female");
    await save(page);
    await page.getByRole("searchbox", {name: "Search Students"}).fill(studentNumber);

    const student = page.getByRole("row").filter({hasText: studentNumber});
    await student.getByRole("button", {name: "Check in"}).click();
    await page.getByLabel("Room", {exact: true}).selectOption({label: "Cedar Hall · 101 (2 available)"});
    await save(page);
    await expect(student.getByText("Resident", {exact: true})).toBeVisible();

    await page.getByRole("button", {name: "Sign out"}).click();
    await signIn(page, "staff");
    await expect(page.getByRole("link", {name: "Students", exact: true})).toHaveCount(0);
    expect((await page.request.get("/api/admin/students")).status()).toBe(403);

    await navigate(page, "Buildings");
    const buildings = page.getByRole("table", {name: "Buildings"});
    await expect(buildings.getByText("Cedar Hall", {exact: true})).toBeVisible();
    await expect(buildings.getByText("Maple Hall", {exact: true})).toHaveCount(0);

    await navigate(page, "Absences");
    await page.getByRole("button", {name: "Record absence"}).click();
    await expect(page.getByLabel("Absence date")).toHaveValue(await residenceToday(page));
    await page.getByLabel("Resident", {exact: true}).selectOption({
        label: studentName + " (" + studentNumber + ") · Cedar Hall · 101"
    });
    await page.getByLabel("Reason").fill("Approved browser test leave");
    await save(page);
    await expect(student.getByText("Approved browser test leave")).toBeVisible();

    await page.getByRole("button", {name: "Sign out"}).click();
    await signIn(page, "administrator");
    await navigate(page, "Students");
    await page.getByRole("searchbox", {name: "Search Students"}).fill(studentNumber);
    await student.getByRole("button", {name: "Transfer"}).click();
    await page.getByLabel("Destination room").selectOption({label: "Willow Hall · A01 (1 available)"});
    await save(page);
    await expect(student.getByText("Willow Hall", {exact: true})).toBeVisible();

    await student.getByRole("button", {name: "Check out"}).click();
    await page.getByLabel("Reason").fill("End of residence");
    await page.getByRole("dialog").getByRole("button", {name: "Check out", exact: true}).click();
    await expect(student.getByText("Checked out", {exact: true})).toBeVisible();

    await navigate(page, "Housing history");
    await expect(student.filter({hasText: "Room transfer"})).toContainText("Cedar Hall");
    await expect(student.filter({hasText: "End of residence"})).toContainText("Willow Hall");
});

test("check-in refuses a room filled after the assignment form opened", async ({page}) => {
    const {rooms: [room, spare], students: [applicant, occupant]} = await housingFixtures(page, 2);
    await navigate(page, "Students");
    const select = await openAssignment(page, applicant, "Check in");
    await select.selectOption(String(room.id));

    await post(page, "/admin/students/" + occupant.id + "/check-in", {roomId: room.id});
    const before = await housingState(page, applicant, [room, spare]);
    await rejectForm(page, "/admin/students/" + applicant.id + "/check-in", 409,
        "The selected room has no available beds.");
    expect(await housingState(page, applicant, [room, spare])).toEqual(before);
    expect(before.student.status).toBe("UNASSIGNED");
    expect(before.rooms.find(item => item.id === room.id).occupied).toBe(1);

    await page.reload();
    const refreshed = await openAssignment(page, applicant, "Check in");
    await expect(refreshed.locator('option[value="' + room.id + '"]')).toHaveCount(0);
    await expect(refreshed.locator('option[value="' + spare.id + '"]')).toHaveCount(1);
});

test("a stale check-in form cannot give one student two active stays", async ({page}) => {
    const {rooms: [assigned, destination], students: [student]} = await housingFixtures(page, 1);
    await navigate(page, "Students");
    await (await openAssignment(page, student, "Check in")).selectOption(String(destination.id));

    const checkIn = "/admin/students/" + student.id + "/check-in";
    await post(page, checkIn, {roomId: assigned.id});
    const before = await housingState(page, student, [assigned, destination]);
    await rejectForm(page, checkIn, 409, "This student already has a room assignment.");
    expect(await housingState(page, student, [assigned, destination])).toEqual(before);

    const activeStays = (await read(page, "/residents")).filter(item => item.studentId === student.id);
    expect(activeStays).toHaveLength(1);
    expect(activeStays[0].roomId).toBe(assigned.id);
});

test("staff can neither see nor record an absence for another building", async ({page}) => {
    await page.goto("/");
    await signIn(page, "administrator");
    const outside = (await read(page, "/residents")).find(item => item.buildingName === "Maple Hall");
    expect(outside).toBeDefined();
    const reason = "Out-of-scope absence " + randomUUID().slice(0, 8);

    await page.getByRole("button", {name: "Sign out"}).click();
    await signIn(page, "staff");
    await navigate(page, "Rooms");
    await expect(page.getByRole("table", {name: "Rooms"}).getByText("Cedar Hall").first()).toBeVisible();
    await expect(page.getByRole("table", {name: "Rooms"}).getByText("Maple Hall")).toHaveCount(0);
    await navigate(page, "Absences");
    await page.getByRole("button", {name: "Record absence"}).click();
    await expect(page.getByLabel("Resident", {exact: true}).locator('option[value="' + outside.stayId + '"]')).toHaveCount(0);

    await post(page, "/absences", {
        stayId: outside.stayId, absenceDate: await residenceToday(page), reason
    }, 404);
    await page.getByRole("dialog").getByRole("button", {name: "Cancel"}).click();
    await page.getByRole("button", {name: "Sign out"}).click();
    await signIn(page, "administrator");
    expect((await read(page, "/absences?size=100")).items.some(absence => absence.reason === reason)).toBe(false);
});

test("server validation displays a field error without adding an invalid student", async ({page}) => {
    const studentNumber = "BROWSER-FORM-" + randomUUID().slice(0, 8);
    await page.goto("/");
    await signIn(page, "administrator");
    await navigate(page, "Students");
    await page.getByRole("button", {name: "Add student"}).click();
    await page.getByLabel("Student number").fill(studentNumber);
    await page.getByLabel("Full name").fill("   ");
    await page.getByLabel("Gender", {exact: true}).selectOption("Male");

    await rejectForm(page, "/admin/students", 400, "fullName must not be blank");
    expect((await read(page, "/admin/students?q=" + studentNumber)).items.some(student => student.studentNumber === studentNumber)).toBe(false);
    await expect(page.getByLabel("Student number")).toHaveValue(studentNumber);
    await page.getByLabel("Full name").fill("Corrected Browser Resident");
    await save(page);
    await page.getByRole("searchbox", {name: "Search Students"}).fill(studentNumber);
    await expect(page.getByRole("row").filter({hasText: studentNumber})).toContainText("Corrected Browser Resident");
});

test("a rejected transfer leaves occupancy, active stay and history unchanged", async ({page}) => {
    const {rooms: [source, destination], students: [resident, occupant]} = await housingFixtures(page, 2);
    await post(page, "/admin/students/" + resident.id + "/check-in", {roomId: source.id});
    await navigate(page, "Students");
    await (await openAssignment(page, resident, "Transfer")).selectOption(String(destination.id));

    await post(page, "/admin/students/" + occupant.id + "/check-in", {roomId: destination.id});
    const before = await housingState(page, resident, [source, destination]);
    await rejectForm(page, "/admin/students/" + resident.id + "/transfer", 409,
        "The selected room has no available beds.");
    expect(await housingState(page, resident, [source, destination])).toEqual(before);
    expect(before.student.roomId).toBe(source.id);
    expect(before.student.stayId).toBeTruthy();

    await page.getByRole("dialog").getByRole("button", {name: "Cancel"}).click();
    await navigate(page, "Housing history");
    await expect(page.getByRole("row").filter({hasText: resident.studentNumber})).toHaveCount(0);
});

test("room choices follow the building policy and the gender of current residents", async ({page}) => {
    await page.goto("/");
    await signIn(page, "administrator");
    const studentNumber = "BROWSER-GENDER-" + randomUUID().slice(0, 8);
    await post(page, "/admin/students", {studentNumber, fullName: "Browser Gender Resident", gender: "MALE"});
    await navigate(page, "Students");
    const rooms = await (await openAssignment(page, {studentNumber}, "Check in")).locator("option").allTextContents();

    // Maple Hall houses male students, Willow Hall female students, and Cedar Hall is mixed.
    expect(rooms.some(room => room.startsWith("Maple Hall · "))).toBe(true);
    expect(rooms.some(room => room.startsWith("Willow Hall · "))).toBe(false);
    expect(rooms.some(room => room.startsWith("Cedar Hall · 102 "))).toBe(true);
    expect(rooms.some(room => room.startsWith("Cedar Hall · 101 "))).toBe(false);
    await page.getByRole("dialog").getByRole("button", {name: "Cancel"}).click();

    await navigate(page, "Buildings");
    const buildings = page.getByRole("table", {name: "Buildings"});
    await expect(buildings.getByRole("row").filter({hasText: "Willow Hall"})).toContainText("Female only");
});

test("staff correct an absence record and then withdraw it", async ({page}) => {
    await page.goto("/");
    await signIn(page, "staff");
    const shift = (isoDate, days) => new Date(Date.parse(isoDate + "T00:00:00Z") + days * 86_400_000).toISOString().slice(0, 10);
    const today = await residenceToday(page);
    const resident = (await read(page, "/residents")).find(item => item.studentNumber === "DEMO-005");
    const reason = "Browser correction " + randomUUID().slice(0, 8);
    await post(page, "/absences", {stayId: resident.stayId, absenceDate: shift(today, -2), reason});

    await navigate(page, "Absences");
    await page.getByRole("row").filter({hasText: reason}).getByRole("button", {name: "Correct"}).click();
    await page.getByLabel("Absence date").fill(shift(today, -1));
    await page.getByLabel("Reason", {exact: true}).fill(reason + " (corrected)");
    await save(page);
    const corrected = page.getByRole("row").filter({hasText: reason + " (corrected)"});
    await expect(corrected).toContainText("Corrected by Morgan Reed");

    await corrected.getByRole("button", {name: "Withdraw"}).click();
    await page.getByLabel("Reason for withdrawal").fill("Entered by mistake");
    await page.getByRole("dialog").getByRole("button", {name: "Withdraw", exact: true}).click();
    await expect(page.getByRole("dialog")).toBeHidden();
    await expect(corrected).toHaveCount(0);

    await page.getByRole("combobox", {name: "Filter by record status"}).selectOption({label: "Withdrawn records"});
    await expect(corrected).toContainText("Withdrawn by Morgan Reed");
    await expect(corrected).toContainText("Entered by mistake");
    const record = (await read(page, "/absences?status=withdrawn&q=" + encodeURIComponent(reason))).items[0];
    expect(record.absenceDate).toBe(shift(today, -1));
});

test("student search pages server results and exports the matching rows", async ({page}) => {
    await page.goto("/");
    await signIn(page, "administrator");
    const prefix = "PAGED-" + randomUUID().slice(0, 6).toUpperCase();
    const number = index => prefix + "-" + String(index).padStart(2, "0");
    for (let index = 1; index <= 23; index++) {
        await post(page, "/admin/students", {studentNumber: number(index), fullName: "Paged Resident " + number(index), gender: "FEMALE"});
    }
    await navigate(page, "Students");
    await page.getByRole("searchbox", {name: "Search Students"}).fill(prefix);
    const students = page.getByRole("table", {name: "Students"});
    await expect(page.getByText("1–20 of 23")).toBeVisible();
    await expect(students.getByRole("row")).toHaveCount(21);
    await page.getByRole("button", {name: "Next"}).click();
    await expect(page.getByText("21–23 of 23")).toBeVisible();
    await expect(students.getByText(number(23), {exact: true})).toBeVisible();

    const download = page.waitForEvent("download");
    await page.getByRole("button", {name: "Export CSV"}).click();
    const file = await download;
    expect(file.suggestedFilename()).toMatch(/^students-\d{4}-\d{2}-\d{2}\.csv$/);
    const csv = await readFile(await file.path(), "utf8");
    expect(csv.startsWith("\uFEFF")).toBe(true);
    const lines = csv.slice(1).trimEnd().split("\r\n");
    expect(lines[0]).toBe("Student number,Full name,Gender,Program,Phone,Email,Housing status,Building,Room");
    expect(lines.slice(1).map(line => line.split(",")[0])).toEqual(Array.from({length: 23}, (_, index) => number(index + 1)));
});

test("repeated failed sign-ins lock the account on this device with a clear message", async ({page}) => {
    await page.goto("/");
    await signIn(page, "administrator");
    // A throwaway account keeps the shared demo accounts usable for the other tests.
    const username = "locked-" + randomUUID().slice(0, 8);
    await post(page, "/admin/staff", {username, displayName: "Locked Browser Staff", password: "Correct-Browser-Password"});
    await page.getByRole("button", {name: "Sign out"}).click();
    await expect(page.getByRole("heading", {name: "Sign in", exact: true})).toBeVisible();

    const alert = page.locator("#login-error");
    async function attempt(password, message) {
        await page.getByLabel("Username", {exact: true}).fill(username);
        await page.getByLabel("Password", {exact: true}).fill(password);
        const response = page.waitForResponse(response => new URL(response.url()).pathname === "/api/login");
        await page.getByRole("button", {name: "Sign in", exact: true}).click();
        await response;
        await expect(alert).toHaveText(message);
    }
    for (let count = 1; count <= 4; count++) await attempt("Wrong-Browser-Password", "Invalid username or password.");
    await attempt("Wrong-Browser-Password", "Too many failed sign-in attempts. Try again in 15 minutes.");
    await attempt("Correct-Browser-Password", "Too many failed sign-in attempts. Try again in 15 minutes.");
});
