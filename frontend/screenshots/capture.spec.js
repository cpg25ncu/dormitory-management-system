import {expect, test} from "@playwright/test";
import {fileURLToPath} from "node:url";

// Writes the README screenshots to docs/media. Run with `npm run screenshots` after packaging.
const media = name => fileURLToPath(new URL("../../docs/media/" + name, import.meta.url));

async function signIn(page, account) {
    await page.getByRole("button", {name: "Use " + account}).click();
    await page.getByRole("button", {name: "Sign in", exact: true}).click();
    await expect(page.getByRole("heading", {name: "Overview", exact: true})).toBeVisible();
}

async function signOut(page) {
    await page.getByRole("button", {name: "Sign out"}).click();
    await expect(page.getByRole("heading", {name: "Sign in", exact: true})).toBeVisible();
}

async function open(page, name) {
    await page.getByRole("navigation", {name: "Workspace"}).getByRole("link", {name, exact: true}).click();
    await expect(page.getByRole("heading", {level: 1, name, exact: true})).toBeVisible();
    await expect(page.locator("[aria-busy]")).toHaveCount(0);
}

async function send(page, method, path, data) {
    const session = await (await page.request.get("/api/session")).json();
    const response = await page.request.fetch("/api" + path, {method, data, headers: {[session.csrfHeader]: session.csrfToken}});
    expect(response.ok(), method + " " + path).toBeTruthy();
}

// Every capture shows a settled page: nothing loading and no toast.
async function capture(page, name) {
    await expect(page.locator("[aria-busy]")).toHaveCount(0);
    await expect(page.locator("#toast")).toBeHidden();
    await page.screenshot({path: media(name)});
}

async function absence(page, reason) {
    const absences = await (await page.request.get("/api/absences?q=" + encodeURIComponent(reason))).json();
    return absences.items[0];
}

test("README screenshots", async ({page}) => {
    await page.goto("/");
    await expect(page.getByRole("button", {name: "Use administrator"})).toBeVisible();
    await capture(page, "sign-in.png");

    await signIn(page, "administrator");
    await expect(page.locator(".occupancy-item")).toHaveCount(3);
    await capture(page, "overview.png");

    await open(page, "Students");
    await capture(page, "student-housing.png");

    await open(page, "Rooms");
    await capture(page, "room-allocation.png");

    await signOut(page);
    await signIn(page, "staff");
    const corrected = await absence(page, "Late return reported");
    await send(page, "PUT", "/absences/" + corrected.id,
        {absenceDate: corrected.absenceDate, reason: "Late return confirmed by the night desk"});
    const withdrawn = await absence(page, "Approved overnight leave");
    await send(page, "POST", "/absences/" + withdrawn.id + "/withdraw", {reason: "Recorded for the wrong resident"});
    await open(page, "Absences");
    await page.getByRole("combobox", {name: "Filter by record status"}).selectOption({label: "All records"});
    await expect(page.getByText("Recorded for the wrong resident")).toBeVisible();
    await capture(page, "staff-absences.png");
});
