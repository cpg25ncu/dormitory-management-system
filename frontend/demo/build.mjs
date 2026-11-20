import {cp, mkdir} from "node:fs/promises";
import {pathToFileURL, fileURLToPath} from "node:url";
import {resolve} from "node:path";

const destination = process.argv[2]
    ? pathToFileURL(resolve(process.argv[2]) + "/")
    : new URL("./dist/", import.meta.url);
await mkdir(destination, {recursive: true});
await cp(new URL("../src/", import.meta.url), destination, {recursive: true});
for (const file of ["transport.js", "fixtures.js"]) {
    await cp(new URL(file, import.meta.url), new URL(file, destination));
}
// The API reference renders the OpenAPI contract next to the demo.
await mkdir(new URL("api/", destination), {recursive: true});
await cp(new URL("api/index.html", import.meta.url), new URL("api/index.html", destination));
await cp(new URL("../../docs/openapi.yaml", import.meta.url), new URL("api/openapi.yaml", destination));
console.log("Built the browser demo and API reference in " + fileURLToPath(destination));
