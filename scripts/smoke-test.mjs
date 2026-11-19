#!/usr/bin/env node
// Checks a running deployment: health, session bootstrap and administrator sign-in.
// With --write it also checks a student in, records an absence for the residence's
// current day and checks the student out. Use --write only on a disposable database.
//
// Usage: DORM_ADMIN_PASSWORD=... node scripts/smoke-test.mjs [--write] [base-url]

const args = process.argv.slice(2);
const write = args.includes("--write");
const base = new URL(args.find(arg => !arg.startsWith("--")) ?? "http://127.0.0.1:8080/");
const username = process.env.DORM_ADMIN_USERNAME || "admin";
const password = process.env.DORM_ADMIN_PASSWORD;
let cookie = "";

function check(condition, message) {
    if (!condition) throw new Error(message);
}

async function call(path, {method = "GET", json, form, session} = {}) {
    const headers = {Accept: "application/json"};
    if (cookie) headers.Cookie = cookie;
    if (session && method !== "GET") headers[session.csrfHeader] = session.csrfToken;
    let body;
    if (json !== undefined) {
        headers["Content-Type"] = "application/json";
        body = JSON.stringify(json);
    } else if (form) {
        body = new URLSearchParams(form);
    }
    const response = await fetch(new URL(path, base), {method, headers, body, redirect: "manual"});
    const sessionCookie = response.headers.getSetCookie().find(value => value.startsWith("JSESSIONID="));
    if (sessionCookie) cookie = sessionCookie.split(";")[0];
    const type = response.headers.get("Content-Type") || "";
    return {status: response.status, body: type.includes("json") ? await response.json() : null};
}

async function expectStatus(path, expected, options) {
    const result = await call(path, options);
    check(result.status === expected, `${options?.method ?? "GET"} ${path} returned ${result.status}, expected ${expected}`);
    return result.body;
}

async function waitForHealth() {
    for (let attempt = 1; ; attempt++) {
        try {
            const health = await call("actuator/health");
            if (health.status === 200 && health.body?.status === "UP") return;
        } catch {
            // The server is not accepting connections yet.
        }
        check(attempt < 60, "The application did not report UP within 120 seconds.");
        await new Promise(resolve => setTimeout(resolve, 2000));
    }
}

function today(timeZone) {
    const parts = new Intl.DateTimeFormat("en", {timeZone, year: "numeric", month: "2-digit", day: "2-digit"})
        .formatToParts(new Date());
    const part = type => parts.find(item => item.type === type).value;
    return `${part("year")}-${part("month")}-${part("day")}`;
}

async function housingWorkflow(session) {
    const suffix = Date.now().toString(36);
    const hall = `Smoke test hall ${suffix}`;
    await expectStatus("api/admin/buildings", 201, {method: "POST", json: {name: hall}, session});
    const building = (await expectStatus("api/buildings", 200)).find(item => item.name === hall);
    await expectStatus("api/admin/rooms", 201, {method: "POST", json: {buildingId: building.id, roomNumber: "S-01", capacity: 1}, session});
    const room = (await expectStatus("api/rooms", 200)).find(item => item.buildingId === building.id);

    const studentNumber = `SMOKE-${suffix}`;
    await expectStatus("api/admin/students", 201, {method: "POST", json: {studentNumber, fullName: "Smoke Test Resident", gender: "FEMALE"}, session});
    const student = (await expectStatus("api/admin/students", 200)).find(item => item.studentNumber === studentNumber);
    await expectStatus(`api/admin/students/${student.id}/check-in`, 201, {method: "POST", json: {roomId: room.id}, session});

    const resident = (await expectStatus("api/residents", 200)).find(item => item.studentId === student.id);
    const date = today(session.timeZone);
    await expectStatus("api/absences", 201, {method: "POST", json: {stayId: resident.stayId, absenceDate: date, reason: "Smoke test"}, session});
    const absences = await expectStatus("api/absences", 200);
    check(absences.some(item => item.studentNumber === studentNumber && item.absenceDate === date),
        "The recorded absence is missing from the absence log.");

    await expectStatus(`api/admin/students/${student.id}/checkout`, 204, {method: "POST", json: {reason: "Smoke test complete"}, session});
    const history = await expectStatus("api/admin/housing/history", 200);
    check(history.some(item => item.studentNumber === studentNumber && item.departureType === "CHECKOUT"),
        "The checkout is missing from housing history.");
    console.log(`✓ Check-in, absence on ${date} (${session.timeZone}) and checkout`);
}

async function main() {
    check(password, "Set DORM_ADMIN_PASSWORD to the administrator's password.");
    await waitForHealth();
    console.log(`✓ ${base.origin} reports UP`);

    let session = await expectStatus("api/session", 200);
    check(session.user === null, "A new client should not be signed in.");
    check(session.demo === false && session.demoAccounts.length === 0, "The deployment is running the demo profile.");
    const expectedZone = process.env.DORM_TIME_ZONE;
    check(!expectedZone || session.timeZone === expectedZone,
        `The residence time zone is ${session.timeZone}, expected ${expectedZone}.`);

    await expectStatus("api/login", 204, {method: "POST", form: {username, password}, session});
    session = await expectStatus("api/session", 200);
    check(session.user?.role === "ADMIN", `${username} is not an administrator.`);
    await expectStatus("api/buildings", 200);
    console.log(`✓ Signed in as ${username} (${session.timeZone})`);

    if (write) await housingWorkflow(session);
    await expectStatus("api/logout", 204, {method: "POST", session});
}

main().catch(error => {
    console.error("✗ " + error.message);
    process.exit(1);
});
