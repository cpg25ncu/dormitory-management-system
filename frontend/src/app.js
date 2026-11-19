import {element, field, useTimeZone} from "./ui.js";
import {views} from "./views.js";
import {browserDemo, request} from "./transport.js";

const byId = id => document.getElementById(id);
const view = byId("view");
const signIn = byId("sign-in");
const shell = byId("shell");
const loginForm = byId("login-form");
const dialog = byId("dialog");
const dialogForm = byId("dialog-form");
const base = new URL(".", location.href);
let session;
let routeVersion = 0;
let dialogAction;
let toastTimer;

class RequestError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}

async function api(path, method = "GET", body) {
    const headers = new Headers({Accept: "application/json"});
    if (method !== "GET") headers.set(session.csrfHeader, session.csrfToken);
    let payload = body;
    if (body != null && !(body instanceof URLSearchParams)) {
        headers.set("Content-Type", "application/json");
        payload = JSON.stringify(body);
    }
    const response = await request(new URL("api" + path, base), {method, headers, body: payload});
    const contentType = response.headers.get("Content-Type") || "";
    const result = contentType.includes("json") ? await response.json() : null;
    if (!response.ok) {
        const messages = {
            401: "Sign in to continue.",
            403: "You do not have access to perform this action. If your session expired, sign in again.",
            404: "This record was not found.",
            409: "This change conflicts with an existing record."
        };
        throw new RequestError(response.status, result?.detail || messages[response.status] || "The request could not be completed.");
    }
    return result;
}

async function loadSession() {
    session = await api("/session");
    useTimeZone(session.timeZone);
    byId("loading").hidden = true;
    byId("demo-options").hidden = !session.demo;
    signIn.hidden = Boolean(session.user);
    shell.hidden = !session.user;
    if (session.user) {
        byId("display-name").textContent = session.user.displayName;
        byId("role").textContent = session.user.role === "ADMIN" ? "Administrator" : "Dormitory staff";
        byId("avatar").textContent = session.user.displayName.split(/\s+/).slice(0, 2).map(word => word[0]).join("").toUpperCase();
        await renderRoute();
    }
}

async function renderRoute() {
    if (!session?.user) return;
    const version = ++routeVersion;
    const isAdmin = session.user.role === "ADMIN";
    const available = Object.entries(views).filter(([name, entry]) => {
        if (browserDemo && name === "staff") return false;
        return isAdmin ? !entry.staffOnly : !entry.adminOnly;
    });
    const wanted = location.hash.slice(1);
    const [key, entry] = available.find(([name]) => name === wanted) || available[0];
    byId("navigation").replaceChildren(...available.map(([name, item]) =>
        element("a", {class: "nav-link", href: "#" + name, "aria-current": name === key ? "page" : null},
            element("span", {class: "nav-symbol", "aria-hidden": "true"}, item.symbol), item.label)));
    byId("breadcrumb").textContent = entry.label;
    view.setAttribute("aria-busy", "true");
    view.replaceChildren(element("p", {class: "loading", role: "status"}, "Loading " + entry.label.toLowerCase() + "…"));
    try {
        const content = await entry.render({api, isAdmin, openForm, notify});
        if (version === routeVersion) view.replaceChildren(...content);
    } catch (error) {
        if (version !== routeVersion) return;
        if (error.status === 401) {
            await loadSession();
            notify("Your session expired. Sign in to continue.");
            return;
        }
        view.replaceChildren(element("p", {class: "inline-error", role: "alert"}, error.message));
    } finally {
        if (version === routeVersion) view.removeAttribute("aria-busy");
    }
}

function notify(message) {
    clearTimeout(toastTimer);
    byId("toast").textContent = message;
    byId("toast").hidden = false;
    toastTimer = setTimeout(() => { byId("toast").hidden = true; }, 4600);
}

function openForm({title, fields = [], submit = "Save", action, success, note, destructive = false, onDone}) {
    byId("dialog-title").textContent = title;
    byId("dialog-fields").replaceChildren(...[note && element("p", {}, note), ...fields.map(field)].filter(Boolean));
    const submitButton = byId("dialog-submit");
    submitButton.textContent = submit;
    submitButton.className = "button " + (destructive ? "danger" : "primary");
    byId("dialog-error").hidden = true;
    dialogAction = {action, success, onDone};
    dialog.showModal();
}

function fillDemo(role) {
    const account = session.demoAccounts.find(item => item.role === role);
    if (!account) return;
    loginForm.elements.username.value = account.username;
    loginForm.elements.password.value = account.password;
    if (browserDemo) loginForm.requestSubmit();
    else loginForm.querySelector("button[type=submit]").focus();
}

loginForm.addEventListener("submit", async event => {
    event.preventDefault();
    const submit = loginForm.querySelector("button[type=submit]");
    const errorMessage = byId("login-error");
    errorMessage.hidden = true;
    submit.disabled = true;
    try {
        await api("/login", "POST", new URLSearchParams(new FormData(loginForm)));
        loginForm.reset();
        await loadSession();
    } catch (error) {
        errorMessage.textContent = error.status === 401 ? "Invalid username or password." : error.message;
        errorMessage.hidden = false;
        if (error.status === 403) await loadSession();
    } finally {
        submit.disabled = false;
    }
});

dialogForm.addEventListener("submit", async event => {
    event.preventDefault();
    const submit = byId("dialog-submit");
    const errorMessage = byId("dialog-error");
    const active = dialogAction;
    const input = Object.fromEntries(new FormData(dialogForm));
    const cancelButtons = [byId("cancel-dialog"), byId("close-dialog")];
    submit.disabled = true;
    cancelButtons.forEach(button => { button.disabled = true; });
    errorMessage.hidden = true;
    try {
        await active.action(input);
        dialog.close();
        if (active.success) notify(active.success);
        await (active.onDone ? active.onDone() : renderRoute());
    } catch (error) {
        if (error.status === 401) {
            dialog.close();
            await loadSession();
            notify("Your session expired. Sign in to continue.");
        } else {
            errorMessage.textContent = error.message;
            errorMessage.hidden = false;
        }
    } finally {
        submit.disabled = false;
        cancelButtons.forEach(button => { button.disabled = false; });
    }
});

dialog.addEventListener("cancel", event => {
    if (byId("dialog-submit").disabled) event.preventDefault();
});
byId("close-dialog").addEventListener("click", () => dialog.close());
byId("cancel-dialog").addEventListener("click", () => dialog.close());
byId("logout").addEventListener("click", async () => {
    try {
        await api("/logout", "POST");
        history.replaceState(null, "", location.pathname + location.search);
        await loadSession();
    } catch (error) {
        if (error.status === 401 || error.status === 403) await loadSession();
        if (session.user) notify(error.message);
    }
});
byId("change-password").addEventListener("click", () => openForm({
    title: "Change password",
    fields: [
        {name: "currentPassword", label: "Current password", type: "password", maxLength: 72, autocomplete: "current-password"},
        {name: "newPassword", label: "New password", type: "password", minLength: 12, maxLength: 72, autocomplete: "new-password"}
    ],
    note: "Use at least 12 characters. You will be signed out after saving.",
    action: input => api("/password", "PUT", input),
    success: "Password changed. Sign in with your new password.",
    onDone: loadSession
}));
byId("demo-admin").addEventListener("click", () => fillDemo("ADMIN"));
byId("demo-staff").addEventListener("click", () => fillDemo("STAFF"));
window.addEventListener("hashchange", renderRoute);

if (browserDemo) {
    for (const control of loginForm.querySelectorAll("label, button[type=submit]")) control.hidden = true;
    byId("auth-heading").textContent = "Try a workspace";
    byId("demo-options").querySelector("p").textContent =
        "Fictional data for this browser tab. Use the administrator role to manage housing or the staff role to record absences.";
    const passwordButton = byId("change-password");
    passwordButton.hidden = true;
    const reset = element("button", {class: "button subtle", type: "button"}, "Reset demo");
    reset.addEventListener("click", browserDemo.reset);
    passwordButton.before(reset);
}

loadSession().catch(error => {
    byId("loading").textContent = "Could not connect to Dormitory Manager. " + error.message;
});
