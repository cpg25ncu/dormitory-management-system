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
