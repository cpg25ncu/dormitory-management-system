import {defineConfig} from "@playwright/test";
import {demoServer} from "./application.js";

// Captures the README screenshots from the packaged application with its fictional demo records.
const {baseURL, webServer} = demoServer(18081, "Asia/Shanghai");

export default defineConfig({
    testDir: "./screenshots",
    reporter: "list",
    workers: 1,
    use: {
        baseURL,
        channel: process.env.DORM_TEST_BROWSER_CHANNEL || undefined,
        viewport: {width: 1440, height: 960},
        colorScheme: "light",
        locale: "en-US",
        // Date inputs follow the browser's interface language, so pin it as well.
        launchOptions: {args: ["--lang=en-US"]}
    },
    webServer
});
