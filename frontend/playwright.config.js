import {defineConfig} from "@playwright/test";

const baseURL = "http://127.0.0.1:18080";

export default defineConfig({
    testDir: "./tests",
    forbidOnly: Boolean(process.env.CI),
    reporter: "list",
    use: {
        baseURL,
        channel: process.env.DORM_TEST_BROWSER_CHANNEL || undefined,
        trace: "retain-on-failure"
    },
    webServer: {
        command: "java -jar ../target/dormitory-management-system-1.0.0.jar --spring.profiles.active=demo --server.port=18080",
        url: baseURL + "/api/session",
        timeout: 60_000,
        stdout: "ignore",
        stderr: "pipe"
    }
});
