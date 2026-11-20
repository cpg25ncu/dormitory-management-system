import {defineConfig} from "@playwright/test";
import {demoServer} from "./application.js";

// UTC+14 keeps the residence's date apart from the UTC date for most of the day,
// so the suite fails if any code falls back to UTC calendar days.
const {baseURL, webServer} = demoServer(18080, "Pacific/Kiritimati");

export default defineConfig({
    testDir: "./tests",
    forbidOnly: Boolean(process.env.CI),
    reporter: "list",
    use: {
        baseURL,
        channel: process.env.DORM_TEST_BROWSER_CHANNEL || undefined,
        trace: "retain-on-failure"
    },
    webServer
});
