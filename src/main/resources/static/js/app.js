const user = JSON.parse(localStorage.getItem("itasm_user") || "null");
if (!token() || !user) location.href = "/";

const isAdmin = user.role === "ADMIN";
document.getElementById("who").textContent = user.fullName + " · " + user.role;
document.getElementById("logout").onclick = () => { localStorage.clear(); location.href = "/"; };

if (!isAdmin) {
  document.querySelectorAll("[data-admin]").forEach((el) => el.remove());
}

const modal = document.getElementById("modal");
document.getElementById("modal-close").onclick = () => modal.hidden = true;

function openModal(html) {
  document.getElementById("modal-body").innerHTML = html;
  modal.hidden = false;
}

function bindNav() {
  document.querySelectorAll(".sidebar nav button").forEach((btn) => {
    btn.onclick = () => {
      document.querySelectorAll(".sidebar nav button").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const view = btn.dataset.view;
      document.getElementById("view-title").textContent = btn.textContent;
      document.querySelectorAll(".view").forEach((v) => v.hidden = true);
      document.getElementById("view-" + view).hidden = false;
      loadView(view);
    };
  });
}

async function loadView(name) {
  if (name === "dashboard") return renderDashboard();
  if (name === "employees") return renderEmployees();
  if (name === "assets") return renderAssets();
  if (name === "software") return renderSoftware();
  if (name === "requests") return renderRequests();
  if (name === "tickets") return renderTickets();
  if (name === "notifications") return renderNotifications();
}

function badge(v) {
  return `<span class="badge ${v || ""}">${v || "-"}</span>`;
}

async function renderDashboard() {
  const el = document.getElementById("view-dashboard");
  if (!isAdmin) {
    el.innerHTML = "<p>Use Tickets and Assets to report issues and view assigned equipment.</p>";
    return;
  }
  const s = await api("/api/dashboard/stats");
  el.innerHTML = `
    <div class="stats">
      <div class="stat">Total Assets<strong>${s.totalAssets}</strong></div>
      <div class="stat">Assigned<strong>${s.assignedAssets}</strong></div>
      <div class="stat">Available<strong>${s.availableAssets}</strong></div>
      <div class="stat">Under Repair<strong>${s.repairAssets}</strong></div>
      <div class="stat">Open Tickets<strong>${s.openTickets}</strong></div>
      <div class="stat">In Progress<strong>${s.inProgressTickets}</strong></div>
      <div class="stat">High/Critical Open<strong>${s.highPriorityOpen}</strong></div>
      <div class="stat">Resolved This Month<strong>${s.resolvedThisMonth}</strong></div>
    </div>`;
}

async function renderEmployees() {
  const el = document.getElementById("view-employees");
  const data = await api("/api/employees?size=50");
  el.innerHTML = `
    <div class="toolbar">
      <input id="emp-q" placeholder="Search name / code / email" />
      <button id="emp-search">Search</button>
      <button id="emp-add">Add employee</button>
    </div>
    <table><thead><tr><th>Code</th><th>Name</th><th>Dept</th><th>Email</th><th>Active</th></tr></thead>
    <tbody>${data.content.map((e) => `<tr>
      <td>${e.employeeCode}</td><td>${e.firstName} ${e.lastName}</td>
      <td>${e.department || ""}</td><td>${e.email}</td><td>${e.active}</td></tr>`).join("")}</tbody></table>`;
  document.getElementById("emp-search").onclick = async () => {
    const q = document.getElementById("emp-q").value;
    const res = await api("/api/employees?size=50&q=" + encodeURIComponent(q));
    el.querySelector("tbody").innerHTML = res.content.map((e) => `<tr>
      <td>${e.employeeCode}</td><td>${e.firstName} ${e.lastName}</td>
      <td>${e.department || ""}</td><td>${e.email}</td><td>${e.active}</td></tr>`).join("");
  };
  document.getElementById("emp-add").onclick = () => {
    openModal(`
      <h3>New employee</h3>
      <form id="emp-form">
        <input name="employeeCode" placeholder="EMP-200" required />
        <input name="firstName" placeholder="First name" required />
        <input name="lastName" placeholder="Last name" required />
        <input name="email" placeholder="email@company.com" required />
        <input name="department" placeholder="Department" />
        <input name="designation" placeholder="Designation" />
        <input name="phone" placeholder="Phone" />
        <input name="username" placeholder="username" required />
        <input name="password" placeholder="password" required />
        <button>Create</button>
      </form>`);
    document.getElementById("emp-form").onsubmit = async (ev) => {
      ev.preventDefault();
      const f = Object.fromEntries(new FormData(ev.target).entries());
      try {
        await api("/api/employees", { method: "POST", body: JSON.stringify(f) });
        modal.hidden = true;
        renderEmployees();
      } catch (ex) { alert(ex.message); }
    };
  };
}

async function renderAssets() {
  const el = document.getElementById("view-assets");
  const data = await api("/api/assets?size=50");
  el.innerHTML = `
    <div class="toolbar">
      <input id="asset-q" placeholder="Search tag / serial / model" />
      <select id="asset-status">
        <option value="">All statuses</option>
        <option>AVAILABLE</option><option>ASSIGNED</option><option>REPAIR</option><option>RETIRED</option>
      </select>
      <button id="asset-search">Filter</button>
      <button id="asset-export" class="ghost">Export CSV</button>
      ${isAdmin ? `
        <label class="file-upload-label">
          <input type="file" id="asset-import-file" accept=".csv" />
          Import CSV
        </label>
        <button id="asset-add">Add asset</button>
      ` : ""}
    </div>
    <table><thead><tr><th>Tag</th><th>Model</th><th>Serial</th><th>Status</th><th>Assigned to</th><th></th></tr></thead>
    <tbody>${assetRows(data.content)}</tbody></table>`;

  document.getElementById("asset-search").onclick = async () => {
    const q = document.getElementById("asset-q").value;
    const status = document.getElementById("asset-status").value;
    const qs = new URLSearchParams({ size: "50" });
    if (q) qs.set("q", q);
    if (status) qs.set("status", status);
    const res = await api("/api/assets?" + qs.toString());
    el.querySelector("tbody").innerHTML = assetRows(res.content);
    bindAssetActions();
  };

  document.getElementById("asset-export").onclick = async () => {
    try {
      const blob = await apiBlob("/api/assets/export");
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "assets.csv";
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (ex) {
      alert("CSV export failed: " + ex.message);
    }
  };

  const importInput = document.getElementById("asset-import-file");
  if (importInput) {
    importInput.onchange = async (ev) => {
      const file = ev.target.files[0];
      if (!file) return;
      const fd = new FormData();
      fd.append("file", file);
      try {
        const res = await api("/api/assets/import", { method: "POST", body: fd });
        alert(res.message || "CSV imported successfully!");
        renderAssets();
      } catch (ex) {
        alert("Import failed: " + ex.message);
      } finally {
        importInput.value = "";
      }
    };
  }

  bindAssetActions();
  const add = document.getElementById("asset-add");
  if (add) add.onclick = () => {
    openModal(`
      <h3>Register asset</h3>
      <form id="asset-form">
        <input name="assetTag" placeholder="L130" required />
        <input name="serialNumber" placeholder="SN-XXXX" required />
        <input name="type" placeholder="Laptop" required />
        <input name="brand" placeholder="Dell" />
        <input name="model" placeholder="Latitude 5540" required />
        <input name="purchaseDate" type="date" />
        <input name="warrantyExpiry" type="date" />
        <input name="price" type="number" step="0.01" min="0" placeholder="Price (₹ e.g. 65000.00)" />
        <input type="hidden" name="status" value="AVAILABLE" />
        <button>Create</button>
      </form>`);
    document.getElementById("asset-form").onsubmit = async (ev) => {
      ev.preventDefault();
      const f = Object.fromEntries(new FormData(ev.target).entries());
      f.status = "AVAILABLE";
      if (f.price !== undefined) {
        f.price = f.price && f.price.trim() !== '' ? f.price.trim() : null;
      }
      if (!f.purchaseDate) f.purchaseDate = null;
      if (!f.warrantyExpiry) f.warrantyExpiry = null;
      try {
        await api("/api/assets", { method: "POST", body: JSON.stringify(f) });
        modal.hidden = true;
        renderAssets();
      } catch (ex) { alert(ex.message); }
    };
  };
}

function assetRows(items) {
  return items.map((a) => `<tr>
    <td>${a.assetTag}</td><td>${a.brand || ""} ${a.model}</td><td>${a.serialNumber}</td>
    <td>${badge(a.status)}</td><td>${a.assignedEmployeeName || "-"}</td>
    <td>
      <button class="ghost" data-qr="${a.id}" data-tag="${a.assetTag}" data-model="${a.brand || ''} ${a.model}">QR Code</button>
      <button class="ghost" data-hist="${a.id}">History</button>
      ${isAdmin ? `<button class="ghost" data-assign="${a.id}">Assign</button>
                   <button class="ghost" data-unassign="${a.id}">Unassign</button>
                   <button class="ghost" data-repair="${a.id}">Repair</button>` : ""}
    </td></tr>`).join("");
}

function bindAssetActions() {
  document.querySelectorAll("[data-qr]").forEach((b) => b.onclick = async () => {
    try {
      const blob = await apiBlob("/api/assets/" + b.dataset.qr + "/qrcode");
      const imgUrl = URL.createObjectURL(blob);
      openModal(`
        <h3>QR Code: ${b.dataset.tag}</h3>
        <p class="muted">${b.dataset.model}</p>
        <div class="qrcode-container">
          <div class="qrcode-frame">
            <img src="${imgUrl}" alt="Asset QR Code" />
          </div>
          <a href="${imgUrl}" download="${b.dataset.tag}-qrcode.png" style="color:var(--accent); font-weight:600; text-decoration:none;">⬇ Download QR Image</a>
        </div>
      `);
    } catch (ex) { alert("Failed to load QR code: " + ex.message); }
  });
  document.querySelectorAll("[data-hist]").forEach((b) => b.onclick = async () => {
    const hist = await api("/api/assets/" + b.dataset.hist + "/history");
    openModal(`<h3>Asset history</h3><div class="timeline">${hist.map((h) =>
      `<p><strong>${h.action}</strong> ${h.fromStatus || ""} → ${h.toStatus || ""}<br/>
       ${h.employeeName || ""} ${h.notes || ""}<br/><span class="muted">${h.createdAt}</span></p>`).join("")}</div>`);
  });
  document.querySelectorAll("[data-assign]").forEach((b) => b.onclick = async () => {
    const empId = prompt("Employee ID to assign (e.g. 2)");
    if (!empId) return;
    try {
      await api("/api/assets/" + b.dataset.assign + "/assign", {
        method: "POST", body: JSON.stringify({ employeeId: Number(empId) })
      });
      renderAssets();
    } catch (ex) { alert(ex.message); }
  });
  document.querySelectorAll("[data-unassign]").forEach((b) => b.onclick = async () => {
    try {
      await api("/api/assets/" + b.dataset.unassign + "/unassign", { method: "POST", body: "{}" });
      renderAssets();
    } catch (ex) { alert(ex.message); }
  });
  document.querySelectorAll("[data-repair]").forEach((b) => b.onclick = async () => {
    try {
      await api("/api/assets/" + b.dataset.repair + "/status", {
        method: "POST", body: JSON.stringify({ status: "REPAIR", notes: "Sent for repair" })
      });
      renderAssets();
    } catch (ex) { alert(ex.message); }
  });
}

async function renderTickets() {
  const el = document.getElementById("view-tickets");
  const data = await api("/api/tickets?size=50");
  el.innerHTML = `
    <div class="toolbar">
      <select id="t-status"><option value="">All</option><option>OPEN</option><option>IN_PROGRESS</option><option>RESOLVED</option><option>CLOSED</option></select>
      <select id="t-priority"><option value="">All priorities</option><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select>
      <button id="t-filter">Filter</button>
      <button id="t-add">New ticket</button>
    </div>
    <table><thead><tr><th>#</th><th>Title</th><th>Priority</th><th>Status</th><th>Asset</th><th>Reporter</th><th></th></tr></thead>
    <tbody>${ticketRows(data.content)}</tbody></table>`;
  document.getElementById("t-filter").onclick = async () => {
    const status = document.getElementById("t-status").value;
    const priority = document.getElementById("t-priority").value;
    const qs = new URLSearchParams({ size: "50" });
    if (status) qs.set("status", status);
    if (priority) qs.set("priority", priority);
    const res = await api("/api/tickets?" + qs.toString());
    el.querySelector("tbody").innerHTML = ticketRows(res.content);
    bindTicketActions();
  };
  bindTicketActions();
  document.getElementById("t-add").onclick = () => {
    openModal(`
      <h3>Report issue</h3>
      <form id="t-form">
        <input name="title" placeholder="Issue title" required />
        <textarea name="description" placeholder="What happened?" required></textarea>
        <select name="priority"><option>LOW</option><option selected>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select>
        <input name="assetId" placeholder="Asset ID (optional)" />
        <button>Create ticket</button>
      </form>`);
    document.getElementById("t-form").onsubmit = async (ev) => {
      ev.preventDefault();
      const f = Object.fromEntries(new FormData(ev.target).entries());
      if (f.assetId) f.assetId = Number(f.assetId); else delete f.assetId;
      try {
        await api("/api/tickets", { method: "POST", body: JSON.stringify(f) });
        modal.hidden = true;
        renderTickets();
      } catch (ex) { alert(ex.message); }
    };
  };
}

function ticketRows(items) {
  return items.map((t) => `<tr>
    <td>${t.ticketNumber}</td><td>${t.title}</td>
    <td>${badge(t.priority)}</td><td>${badge(t.status)}</td>
    <td>${t.assetTag || "-"}</td><td>${t.reportedByName}</td>
    <td>${isAdmin ? `<button class="ghost" data-progress="${t.id}">In progress</button>
                     <button class="ghost" data-resolve="${t.id}">Resolve</button>` : ""}</td>
  </tr>`).join("");
}

function bindTicketActions() {
  document.querySelectorAll("[data-progress]").forEach((b) => b.onclick = async () => {
    try {
      await api("/api/tickets/" + b.dataset.progress + "/status", {
        method: "POST", body: JSON.stringify({ status: "IN_PROGRESS" })
      });
      renderTickets();
    } catch (ex) { alert(ex.message); }
  });
  document.querySelectorAll("[data-resolve]").forEach((b) => b.onclick = async () => {
    try {
      await api("/api/tickets/" + b.dataset.resolve + "/status", {
        method: "POST", body: JSON.stringify({ status: "RESOLVED", resolutionNotes: "Issue fixed" })
      });
      renderTickets();
    } catch (ex) { alert(ex.message); }
  });
}

async function renderNotifications() {
  const el = document.getElementById("view-notifications");
  const data = await api("/api/notifications?size=50");
  el.innerHTML = data.content.map((n) => `
    <div class="stat" style="margin-bottom:8px">
      <p>${n.message}</p>
      <p class="muted">${n.createdAt} · ${n.read ? "read" : "unread"}</p>
      ${n.read ? "" : `<button class="ghost" data-read="${n.id}">Mark read</button>`}
    </div>`).join("") || "<p>No notifications.</p>";
  document.querySelectorAll("[data-read]").forEach((b) => b.onclick = async () => {
    await api("/api/notifications/" + b.dataset.read + "/read", { method: "POST" });
    renderNotifications();
  });
}

async function renderSoftware() {
  const el = document.getElementById("view-software");
  const data = await api("/api/software");
  el.innerHTML = `
    <div class="toolbar">
      ${isAdmin ? '<button id="sw-add">Add Software License</button>' : ''}
    </div>
    <table>
      <thead>
        <tr><th>Software Name</th><th>License Key</th><th>Available / Total Seats</th><th>Expiration Date</th><th>Actions</th></tr>
      </thead>
      <tbody>
        ${data.length === 0 ? '<tr><td colspan="5" class="muted">No software licenses found.</td></tr>' : data.map((s) => `
          <tr>
            <td><strong>${s.name}</strong></td>
            <td><code>${s.licenseKey || "-"}</code></td>
            <td>${s.availableSeats} / ${s.totalSeats} seats</td>
            <td>${s.expirationDate || "Permanent"}</td>
            <td>
              <button class="ghost" data-sw-view="${s.id}" data-sw-name="${s.name}">View Assignments</button>
              ${isAdmin && s.availableSeats > 0 ? `<button class="ghost" data-sw-assign="${s.id}" data-sw-name="${s.name}">Assign Seat</button>` : ''}
            </td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;

  const addBtn = document.getElementById("sw-add");
  if (addBtn) {
    addBtn.onclick = () => {
      openModal(`
        <h3>Add Software License</h3>
        <form id="sw-form">
          <label>Software Name
            <input name="name" placeholder="e.g. IntelliJ IDEA Ultimate" required />
          </label>
          <label>License Key
            <input name="licenseKey" placeholder="e.g. XXXX-YYYY-ZZZZ" />
          </label>
          <label>Total Seats
            <input name="totalSeats" type="number" min="1" value="5" required />
          </label>
          <label>Expiration Date
            <input name="expirationDate" type="date" />
          </label>
          <button type="submit">Save License</button>
        </form>
      `);
      document.getElementById("sw-form").onsubmit = async (ev) => {
        ev.preventDefault();
        const f = Object.fromEntries(new FormData(ev.target).entries());
        f.totalSeats = Number(f.totalSeats);
        if (!f.expirationDate) delete f.expirationDate;
        try {
          await api("/api/software", { method: "POST", body: JSON.stringify(f) });
          modal.hidden = true;
          renderSoftware();
        } catch (ex) { alert(ex.message); }
      };
    };
  }

  document.querySelectorAll("[data-sw-view]").forEach((b) => b.onclick = async () => {
    try {
      const assignments = await api("/api/software/" + b.dataset.swView + "/assignments");
      openModal(`
        <h3>Assignments: ${b.dataset.swName}</h3>
        ${assignments.length === 0 ? '<p class="muted">No employees currently assigned to this license.</p>' : `
          <table style="margin-top:12px">
            <thead><tr><th>Employee</th><th>Assigned Date</th>${isAdmin ? '<th>Action</th>' : ''}</tr></thead>
            <tbody>
              ${assignments.map(a => `
                <tr>
                  <td>${a.employeeName}</td>
                  <td>${new Date(a.assignedAt).toLocaleDateString()}</td>
                  ${isAdmin ? `<td><button class="ghost" data-unassign-sw="${a.id}">Unassign</button></td>` : ''}
                </tr>
              `).join("")}
            </tbody>
          </table>
        `}
      `);
      document.querySelectorAll("[data-unassign-sw]").forEach(ub => ub.onclick = async () => {
        try {
          await api("/api/software/assignments/" + ub.dataset.unassignSw, { method: "DELETE" });
          modal.hidden = true;
          renderSoftware();
        } catch (ex) { alert(ex.message); }
      });
    } catch (ex) { alert(ex.message); }
  });

  document.querySelectorAll("[data-sw-assign]").forEach((b) => b.onclick = async () => {
    const empId = prompt("Enter Employee ID to assign a license seat (e.g. 1 or 2):");
    if (!empId) return;
    try {
      await api("/api/software/" + b.dataset.swAssign + "/assign", {
        method: "POST",
        body: JSON.stringify({ employeeId: Number(empId) })
      });
      alert("Seat assigned successfully!");
      renderSoftware();
    } catch (ex) { alert(ex.message); }
  });
}

async function renderRequests() {
  const el = document.getElementById("view-requests");
  if (isAdmin) {
    const list = await api("/api/asset-requests");
    el.innerHTML = `
      <div class="toolbar">
        <p class="muted" style="margin:0; align-self:center;">Review and approve/reject hardware & peripheral requests from employees.</p>
      </div>
      <table>
        <thead>
          <tr><th>Employee</th><th>Requested Item</th><th>Reason</th><th>Status</th><th>Submitted</th><th>Admin Notes</th><th>Actions</th></tr>
        </thead>
        <tbody>
          ${list.length === 0 ? '<tr><td colspan="7" class="muted">No asset requests found.</td></tr>' : list.map((r) => `
            <tr>
              <td><strong>${r.employeeName}</strong> (${r.employeeCode})</td>
              <td>${r.requestedItem}</td>
              <td>${r.reason || "-"}</td>
              <td>${badge(r.status)}</td>
              <td>${new Date(r.createdAt).toLocaleDateString()}</td>
              <td>${r.adminNotes || "-"}</td>
              <td>
                ${r.status === "PENDING" ? `
                  <button class="ghost" data-approve-req="${r.id}">Approve</button>
                  <button class="ghost" data-reject-req="${r.id}">Reject</button>
                ` : `<span class="muted">${r.resolvedAt ? new Date(r.resolvedAt).toLocaleDateString() : "-"}</span>`}
              </td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;

    document.querySelectorAll("[data-approve-req]").forEach((b) => b.onclick = async () => {
      const note = prompt("Approval notes (optional):", "Approved by IT Admin");
      if (note === null) return;
      try {
        await api("/api/asset-requests/" + b.dataset.approveReq + "/resolve", {
          method: "POST",
          body: JSON.stringify({ status: "APPROVED", adminNotes: note })
        });
        renderRequests();
      } catch (ex) { alert(ex.message); }
    });

    document.querySelectorAll("[data-reject-req]").forEach((b) => b.onclick = async () => {
      const note = prompt("Reason for rejection:", "Out of budget / Not permitted");
      if (!note) return;
      try {
        await api("/api/asset-requests/" + b.dataset.rejectReq + "/resolve", {
          method: "POST",
          body: JSON.stringify({ status: "REJECTED", adminNotes: note })
        });
        renderRequests();
      } catch (ex) { alert(ex.message); }
    });

  } else {
    const list = await api("/api/asset-requests/me");
    el.innerHTML = `
      <div class="toolbar">
        <button id="req-create">Request New Asset</button>
      </div>
      <table>
        <thead>
          <tr><th>Requested Item</th><th>Reason</th><th>Status</th><th>Submitted</th><th>Admin Notes</th></tr>
        </thead>
        <tbody>
          ${list.length === 0 ? '<tr><td colspan="5" class="muted">You have not submitted any asset requests.</td></tr>' : list.map((r) => `
            <tr>
              <td><strong>${r.requestedItem}</strong></td>
              <td>${r.reason || "-"}</td>
              <td>${badge(r.status)}</td>
              <td>${new Date(r.createdAt).toLocaleDateString()}</td>
              <td>${r.adminNotes || "-"}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;

    document.getElementById("req-create").onclick = () => {
      openModal(`
        <h3>New Asset Request</h3>
        <form id="req-form">
          <label>Requested Item
            <input name="requestedItem" placeholder="e.g. 27-inch 4K Monitor, Wireless Mouse" required />
          </label>
          <label>Reason for Request
            <textarea name="reason" placeholder="Explain why you need this equipment..." rows="3" required></textarea>
          </label>
          <button type="submit">Submit Request</button>
        </form>
      `);
      document.getElementById("req-form").onsubmit = async (ev) => {
        ev.preventDefault();
        const f = Object.fromEntries(new FormData(ev.target).entries());
        try {
          await api("/api/asset-requests", { method: "POST", body: JSON.stringify(f) });
          modal.hidden = true;
          renderRequests();
        } catch (ex) { alert(ex.message); }
      };
    };
  }
}

bindNav();
if (!isAdmin) {
  document.querySelector('[data-view="tickets"]').click();
} else {
  loadView("dashboard");
}
