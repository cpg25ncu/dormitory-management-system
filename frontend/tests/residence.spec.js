import {expect, test} from "@playwright/test";
import {randomUUID} from "node:crypto";

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
        await post(page, "/admin/students", {studentNumber, fullName: "Browser Resident " + studentNumber});
        studentNumbers.push(studentNumber);
    }
    const rooms = (await read(page, "/rooms")).filter(room => room.buildingId === building.id);
    const roster = await read(page, "/admin/students");
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
        read(page, "/admin/students"), read(page, "/rooms"), read(page, "/admin/housing/history")
    ]);
    const roomIds = new Set(rooms.map(room => room.id));
    return {
        student: students.find(item => item.id === student.id),
        rooms: inventory.filter(room => roomIds.has(room.id)),
        history: history.filter(stay => stay.studentNumber === student.studentNumber)
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
    await save(page);

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
    await student.getByRole("button", {name: "Transfer"}).click();
    await page.getByLabel("Destination room").selectOption({label: "Maple Hall · 110 (2 available)"});
    await save(page);
    await expect(student.getByText("Maple Hall", {exact: true})).toBeVisible();

    await student.getByRole("button", {name: "Check out"}).click();
    await page.getByLabel("Reason").fill("End of residence");
    await page.getByRole("dialog").getByRole("button", {name: "Check out", exact: true}).click();
    await expect(student.getByText("Checked out", {exact: true})).toBeVisible();

    await navigate(page, "Housing history");
    await expect(student.filter({hasText: "Room transfer"})).toContainText("Cedar Hall");
    await expect(student.filter({hasText: "End of residence"})).toContainText("Maple Hall");
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
    expect((await read(page, "/absences")).some(absence => absence.reason === reason)).toBe(false);
});

test("server validation displays a field error without adding an invalid student", async ({page}) => {
    const studentNumber = "BROWSER-FORM-" + randomUUID().slice(0, 8);
    await page.goto("/");
    await signIn(page, "administrator");
    await navigate(page, "Students");
    await page.getByRole("button", {name: "Add student"}).click();
    await page.getByLabel("Student number").fill(studentNumber);
    await page.getByLabel("Full name").fill("   ");

    await rejectForm(page, "/admin/students", 400, "fullName must not be blank");
    expect((await read(page, "/admin/students")).some(student => student.studentNumber === studentNumber)).toBe(false);
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
