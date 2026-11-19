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
    const { title, search, filter, empty = "No records to display.", compact = false } = options;
    const body = element("tbody");
    const grid = element("table", {"aria-label": title},
        element("thead", {}, element("tr", {}, columns.map(([heading]) => element("th", {scope: "col"}, heading)))),
        body);
    const scroll = element("div", {class: "table-scroll"}, grid);
    const noRows = element("p", {class: "empty"});
    const panel = element("section", {class: "panel"}, scroll, noRows);
    const searchBox = search && element("input", {type: "search", "aria-label": "Search " + title,
        placeholder: "Search " + title.toLowerCase()});
    const select = filter && element("select", {"aria-label": filter.label},
        element("option", {value: ""}, filter.all),
        filter.options.map(([value, label]) => element("option", {value}, label)));
    if (searchBox || select || compact) {
        panel.prepend(element("div", {class: "panel-header"}, element("h2", {}, title),
            element("div", {class: "table-controls"}, searchBox, select, options.link)));
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
        const selected = select?.value || "";
        const visible = rows.filter(row =>
            (!query || search(row).join(" ").toLowerCase().includes(query)) &&
            (!selected || String(filter.value(row)) === selected));
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
    select?.addEventListener("change", resetPage);
    refresh();
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

export function utcDate() { return new Date().toISOString().slice(0, 10); }

export function formatDate(iso) {
    return new Intl.DateTimeFormat("en", {dateStyle: "medium", timeZone: "UTC"}).format(new Date(iso));
}

export function formatTimestamp(iso) {
    return new Intl.DateTimeFormat("en", {dateStyle: "medium", timeStyle: "short", timeZone: "UTC"}).format(new Date(iso));
}
