export function element(tag, attributes = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(attributes)) {
        if (value != null && value !== false) node.setAttribute(key, value === true ? "" : value);
    }
    for (const child of children.flat()) {
        if (child != null && child !== false) node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
}

export function button(label, action, kind = "subtle") {
    const node = element("button", {type: "button", class: "button " + kind}, label);
    node.addEventListener("click", action);
    return node;
}

export function page(title, description, action, ...sections) {
    return [
        element("header", {class: "page-header"},
            element("div", {}, element("h1", {}, title), element("p", {}, description)), action),
        ...sections
    ];
}

export function pill(label, tone = "") {
    return element("span", {class: "pill " + tone}, label);
}

export function person(name, secondary) {
    return element("div", {}, element("strong", {}, name), secondary && element("small", {}, secondary));
}

export function actionGroup(...actions) {
    return element("div", {class: "actions"}, ...actions);
}

export function table(rows, columns, options = {}) {
    const { title, search, filters = [], empty = "No records to display.", compact = false } = options;
    const body = element("tbody");
    const grid = element("table", {"aria-label": title},
        element("thead", {}, element("tr", {}, columns.map(([heading]) => element("th", {scope: "col"}, heading)))),
        body);
    const scroll = element("div", {class: "table-scroll"}, grid);
    const noRows = element("p", {class: "empty"});
    const panel = element("section", {class: "panel"}, scroll, noRows);
    const searchBox = search && element("input", {type: "search", "aria-label": "Search " + title,
        placeholder: "Search " + title.toLowerCase()});
    const selects = filters.map(filter => ({filter, control: element("select", {"aria-label": filter.label},
        element("option", {value: ""}, filter.all),
        filter.options.map(([value, label]) => element("option", {value}, label)))}));
    for (const {filter, control} of selects) control.value = filter.initial ?? "";
    if (searchBox || selects.length || compact) {
        panel.prepend(element("div", {class: "panel-header"}, element("h2", {}, title),
            element("div", {class: "table-controls"}, searchBox, selects.map(({control}) => control), options.link)));
    }

    let currentPage = 0;
    const pageSize = 12;
    const status = element("span", {"aria-live": "polite"});
    const previous = button("Previous", () => { currentPage -= 1; refresh(); });
    const next = button("Next", () => { currentPage += 1; refresh(); });
    const pager = element("div", {class: "table-footer"}, status, actionGroup(previous, next));
    if (!compact) panel.append(pager);

    function refresh() {
        const query = searchBox?.value.trim().toLowerCase() || "";
        const visible = rows.filter(row =>
            (!query || search(row).join(" ").toLowerCase().includes(query)) &&
            selects.every(({filter, control}) => !control.value || String(filter.value(row)) === control.value));
        const start = currentPage * pageSize;
        const end = Math.min(start + pageSize, visible.length);
        const batch = compact ? visible : visible.slice(start, end);
        body.replaceChildren(...batch.map(row => element("tr", {}, columns.map(([, render]) => element("td", {}, render(row))))));
        scroll.hidden = visible.length === 0;
        noRows.hidden = visible.length > 0;
        noRows.textContent = rows.length ? "No records match your filters." : empty;
        pager.hidden = visible.length <= pageSize;
        previous.disabled = currentPage === 0;
        next.disabled = end === visible.length;
        status.textContent = (start + 1) + "–" + end + " of " + visible.length;
    }

    function resetPage() { currentPage = 0; refresh(); }
    searchBox?.addEventListener("input", resetPage);
    selects.forEach(({control}) => control.addEventListener("change", resetPage));
    refresh();
    return panel;
}

// Search text, filters and page of each server-side table, kept so a table keeps its place after an edit.
const tableStates = new Map();

export function forgetTables() { tableStates.clear(); }

/**
 * A table whose rows come from a paged API. `load(query)` resolves to {items, totalItems, totalPages}.
 * Filters are selects ({name, label, options, all?, initial?}) or dates ({name, label, type: "date"}).
 * The first page loads before the table is returned, so a failure surfaces like any other view error.
 */
export async function pagedTable(load, columns, options) {
    const {title, filters = [], empty = "No records to display.", exportTo, pageSize = 20} = options;
    const state = tableStates.get(title) ?? {q: "", page: 0, values: {}};
    tableStates.set(title, state);
    const body = element("tbody");
    const scroll = element("div", {class: "table-scroll"}, element("table", {"aria-label": title},
        element("thead", {}, element("tr", {}, columns.map(([heading]) => element("th", {scope: "col"}, heading)))),
        body));
    const noRows = element("p", {class: "empty"});
    const searchBox = element("input", {type: "search", "aria-label": "Search " + title, placeholder: "Search " + title.toLowerCase()});
    searchBox.value = state.q;
    const controls = filters.map(filter => {
        const control = filter.type === "date"
            ? element("input", {type: "date", "aria-label": filter.label, title: filter.label})
            : element("select", {"aria-label": filter.label}, filter.all && element("option", {value: ""}, filter.all),
                filter.options.map(([value, label]) => element("option", {value}, label)));
        control.value = state.values[filter.name] ?? filter.initial ?? "";
        return {filter, control};
    });
    const exportButton = exportTo && button("Export CSV", () => exportTo(query(false)));
    const status = element("span", {"aria-live": "polite"});
    const previous = button("Previous", () => { state.page -= 1; refresh().catch(showError); });
    const next = button("Next", () => { state.page += 1; refresh().catch(showError); });
    const pager = element("div", {class: "table-footer"}, status, actionGroup(previous, next));
    const panel = element("section", {class: "panel"},
        element("div", {class: "panel-header"}, element("h2", {}, title),
            element("div", {class: "table-controls"}, searchBox, controls.map(({control}) => control), exportButton)),
        scroll, noRows, pager);

    function query(paged) {
        const params = new URLSearchParams();
        if (searchBox.value.trim()) params.set("q", searchBox.value.trim());
        for (const {filter, control} of controls) if (control.value) params.set(filter.name, control.value);
        if (paged) {
            params.set("page", state.page);
            params.set("size", pageSize);
        }
        return params;
    }

    function showError(error) {
        scroll.hidden = true;
        pager.hidden = true;
        noRows.hidden = false;
        noRows.textContent = error.message;
        panel.removeAttribute("aria-busy");
    }

    let version = 0;
    async function refresh() {
        const current = ++version;
        panel.setAttribute("aria-busy", "true");
        const result = await load(query(true));
        // A slower response to an earlier search must not replace a newer one.
        if (current !== version) return;
        if (!result.items.length && result.totalItems > 0 && state.page > 0) {
            state.page = result.totalPages - 1;
            return refresh();
        }
        body.replaceChildren(...result.items.map(row => element("tr", {}, columns.map(([, render]) => element("td", {}, render(row))))));
        const filtered = searchBox.value.trim() || controls.some(({filter, control}) => control.value !== (filter.initial ?? ""));
        scroll.hidden = result.items.length === 0;
        noRows.hidden = result.items.length > 0;
        noRows.textContent = filtered ? "No records match your filters." : empty;
        const start = state.page * pageSize;
        pager.hidden = result.totalPages <= 1;
        previous.disabled = state.page === 0;
        next.disabled = state.page >= result.totalPages - 1;
        status.textContent = (start + 1) + "–" + (start + result.items.length) + " of " + result.totalItems;
        panel.removeAttribute("aria-busy");
    }

    function restart() {
        state.q = searchBox.value;
        for (const {filter, control} of controls) state.values[filter.name] = control.value;
        state.page = 0;
        refresh().catch(showError);
    }

    let typing;
    searchBox.addEventListener("input", () => { clearTimeout(typing); typing = setTimeout(restart, 300); });
    controls.forEach(({control}) => control.addEventListener("change", restart));
    await refresh();
    return panel;
}

export function field({ name, label, type = "text", value = "", options, required = true, maxLength,
                        minLength, min, max, autocomplete }) {
    const id = "form-field-" + name;
    const attrs = {id, name, required, maxlength: maxLength, minlength: minLength, min, max, autocomplete};
    let control;
    if (options) {
        control = element("select", attrs, options.map(([id, text]) => element("option", {value: id}, text)));
    } else if (type === "textarea") {
        control = element("textarea", {...attrs, rows: 3});
    } else {
        control = element("input", {...attrs, type});
    }
    control.value = value ?? "";
    return element("div", {class: "form-field"}, element("label", {for: id}, label), control);
}

// The residence's IANA time zone, supplied by the session. Calendar days and timestamps follow it.
let residenceZone = "UTC";

export function useTimeZone(zone) { residenceZone = zone || "UTC"; }

export function timeZone() { return residenceZone; }

/** Today's date in the residence, as YYYY-MM-DD. */
export function today() {
    const parts = new Intl.DateTimeFormat("en", {timeZone: residenceZone, year: "numeric", month: "2-digit", day: "2-digit"})
        .formatToParts(new Date());
    const part = type => parts.find(item => item.type === type).value;
    return part("year") + "-" + part("month") + "-" + part("day");
}

/** Formats a calendar date such as 2026-03-02. Dates have no time of day, so no zone conversion applies. */
export function formatDate(isoDate) {
    return new Intl.DateTimeFormat("en", {dateStyle: "medium", timeZone: "UTC"}).format(new Date(isoDate + "T00:00:00Z"));
}

/** Formats an instant in the residence's time zone. */
export function formatTimestamp(iso) {
    return new Intl.DateTimeFormat("en", {dateStyle: "medium", timeStyle: "short", timeZone: residenceZone}).format(new Date(iso));
}
