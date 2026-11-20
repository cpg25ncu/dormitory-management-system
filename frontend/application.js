import {readdirSync} from "node:fs";

/** The packaged Spring Boot JAR, relative to this directory, whatever the project version. */
export function applicationJar() {
    let names = [];
    try {
        names = readdirSync(new URL("../target/", import.meta.url));
    } catch {
        // No build output yet.
    }
    const jar = names.find(name => /^dormitory-management-system-.+\.jar$/.test(name));
    if (!jar) throw new Error("Package the application first: ./mvnw -B clean verify");
    return "../target/" + jar;
}

/** Starts the packaged application with the demo profile on a port, in a residence time zone. */
export function demoServer(port, timeZone) {
    const baseURL = "http://127.0.0.1:" + port;
    return {
        baseURL,
        webServer: {
            command: `java -jar ${applicationJar()} --spring.profiles.active=demo --server.port=${port}`
                + ` --dormitory.time-zone=${timeZone}`,
            url: baseURL + "/api/session",
            timeout: 60_000,
            stdout: "ignore",
            stderr: "pipe"
        }
    };
}
