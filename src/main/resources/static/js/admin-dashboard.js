const user = JSON.parse(localStorage.getItem("itasm_user") || "null");
if (!token() || !user || user.role !== "ADMIN") {
  localStorage.clear();
  location.href = "/admin-login.html";
}

document.getElementById("who").textContent = user.fullName + " (Admin)";
document.getElementById("logout").onclick = () => { localStorage.clear(); location.href = "/"; };

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
  if (name === "analytics" && window.renderAnalytics) return window.renderAnalytics();
  if (name === "reports" && window.renderReports) return window.renderReports();
  if (name === "audit" && window.renderAuditLogs) return window.renderAuditLogs();
  if (name === "security" && window.renderSecurity) return window.renderSecurity();
  if (name === "procurement" && window.renderProcurement) return window.renderProcurement();
}

function badge(v) {
  return `<span class="badge ${v || ""}">${v || "-"}</span>`;
}

async function renderDashboard() {
  const el = document.getElementById("view-dashboard");
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
      <button id="emp-add">+ Add Employee</button>
    </div>
    <table>
      <thead><tr><th>Code</th><th>Name</th><th>Dept</th><th>Email</th><th>Username</th><th>Status</th><th>Actions</th></tr></thead>
      <tbody>${employeeRows(data.content)}</tbody>
    </table>`;

  document.getElementById("emp-search").onclick = async () => {
    const q = document.getElementById("emp-q").value;
    const res = await api("/api/employees?size=50&q=" + encodeURIComponent(q));
    el.querySelector("tbody").innerHTML = employeeRows(res.content);
    bindEmployeeActions();
  };

  bindEmployeeActions();

  document.getElementById("emp-add").onclick = () => {
    openModal(`
      <h3>Create New Employee Account</h3>
      <p class="muted">Account will be saved in MySQL with BCrypt encryption. The employee can immediately log in.</p>
      <form id="emp-form">
        <label>Employee Code (ID)
          <input name="employeeCode" placeholder="e.g. EMP-105" required />
        </label>
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
          <label>First Name<input name="firstName" placeholder="First name" required /></label>
          <label>Last Name<input name="lastName" placeholder="Last name" required /></label>
        </div>
        <label>Work Email<input name="email" type="email" placeholder="name@company.com" required /></label>
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
          <label>Department<input name="department" placeholder="IT / Engineering / Finance" /></label>
          <label>Designation<input name="designation" placeholder="e.g. Software Engineer" /></label>
        </div>
        <label>Phone Number<input name="phone" placeholder="9876543210" /></label>
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
          <label>Login Username (min 6 chars)<input name="username" placeholder="username" minlength="6" required /></label>
          <label>Login Password (min 6 chars)<input name="password" type="password" placeholder="password" minlength="6" required /></label>
        </div>
        <button style="margin-top:14px;">Create Employee Account</button>
      </form>`);

    document.getElementById("emp-form").onsubmit = async (ev) => {
      ev.preventDefault();
      const f = Object.fromEntries(new FormData(ev.target).entries());
      try {
        await api("/api/employees", { method: "POST", body: JSON.stringify(f) });
        alert(`Employee ${f.firstName} ${f.lastName} (${f.username}) created successfully in MySQL!`);
        modal.hidden = true;
        renderEmployees();
      } catch (ex) { alert(ex.message); }
    };
  };
}

function employeeRows(items) {
  return items.map((e) => `<tr>
    <td><strong>${e.employeeCode}</strong></td>
    <td>${e.firstName} ${e.lastName}</td>
    <td>${e.department || "-"}</td>
    <td>${e.email}</td>
    <td><code>${e.username}</code></td>
    <td><span class="badge ${e.active ? 'AVAILABLE' : 'RETIRED'}">${e.active ? 'ACTIVE' : 'INACTIVE'}</span></td>
    <td>
      <button class="ghost" data-emp-edit="${e.id}">Edit</button>
      ${e.active ? `
        <button class="ghost" data-emp-deactivate="${e.id}" style="color:var(--danger);">Deactivate</button>
      ` : `
        <button class="ghost" data-emp-activate="${e.id}" style="color:var(--accent);">Activate</button>
      `}
    </td></tr>`).join("");
}

function bindEmployeeActions() {
  document.querySelectorAll("[data-emp-edit]").forEach((b) => b.onclick = async () => {
    try {
      const emp = await api("/api/employees/" + b.dataset.empEdit);
      openModal(`
        <h3>Edit Employee: ${emp.employeeCode}</h3>
        <form id="edit-emp-form">
          <input type="hidden" name="employeeCode" value="${emp.employeeCode}" />
          <input type="hidden" name="username" value="${emp.username}" />
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
            <label>First Name<input name="firstName" value="${emp.firstName}" required /></label>
            <label>Last Name<input name="lastName" value="${emp.lastName}" required /></label>
          </div>
          <label>Email<input name="email" type="email" value="${emp.email}" required /></label>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
            <label>Department<input name="department" value="${emp.department || ''}" /></label>
            <label>Designation<input name="designation" value="${emp.designation || ''}" /></label>
          </div>
          <label>Phone<input name="phone" value="${emp.phone || ''}" /></label>
          <label>Change Password (optional)
            <input name="password" placeholder="Leave empty to keep existing password" minlength="6" />
          </label>
          <button style="margin-top:14px;">Save Changes</button>
        </form>`);

      document.getElementById("edit-emp-form").onsubmit = async (ev) => {
        ev.preventDefault();
        const f = Object.fromEntries(new FormData(ev.target).entries());
        if (!f.password) delete f.password;
        try {
          await api("/api/employees/" + emp.id, { method: "PUT", body: JSON.stringify(f) });
          alert("Employee details updated!");
          modal.hidden = true;
          renderEmployees();
        } catch (ex) { alert(ex.message); }
      };
    } catch (ex) { alert(ex.message); }
  });

  document.querySelectorAll("[data-emp-deactivate]").forEach((b) => b.onclick = async () => {
    if (!confirm("Are you sure you want to deactivate this employee? They will no longer be allowed to log in.")) return;
    try {
      await api("/api/employees/" + b.dataset.empDeactivate, { method: "DELETE" });
      alert("Employee deactivated (status: INACTIVE). Login disabled.");
      renderEmployees();
    } catch (ex) { alert(ex.message); }
  });

  document.querySelectorAll("[data-emp-activate]").forEach((b) => b.onclick = async () => {
    try {
      await api("/api/employees/" + b.dataset.empActivate + "/activate", { method: "POST" });
      alert("Employee reactivated (status: ACTIVE)! Login access restored.");
      renderEmployees();
    } catch (ex) { alert(ex.message); }
  });
}

async function renderAssets() {
  const el = document.getElementById("view-assets");
  const data = await api("/api/assets?size=50");
  el.innerHTML = `
    <div class="toolbar">
      <input id="asset-q" placeholder="Search tag / serial / model" />
      <select id="asset-status">
        <option value="">All statuses</option>
        <option>AVAILABLE</option><option>ASSIGNED</option><option>REPAIR</option><option>RETIRED</option><option>PROCURED</option>
      </select>
      <button id="asset-search">Filter</button>
      <button id="asset-high-risk" class="ghost" style="color:var(--danger)">High Risk Assets</button>
      <button id="asset-export" class="ghost">Export All CSV</button>
      <label class="file-upload-label">
        <input type="file" id="asset-import-file" accept=".csv" />
        Import CSV
      </label>
      <button id="asset-add">+ Register Asset</button>
    </div>

    <!-- Phase 7: Bulk Operations Toolbar -->
    <div id="bulk-actions-bar" style="display:none; background:var(--card); border:1px solid var(--accent); border-radius:12px; padding:10px 16px; margin-bottom:14px; align-items:center; gap:12px; box-shadow:0 4px 16px rgba(0,0,0,0.25);">
      <span style="display:flex; align-items:center; gap:8px;">
        <span style="display:inline-block; width:10px; height:10px; border-radius:50%; background:var(--accent);"></span>
        <strong id="bulk-selected-count" style="color:var(--text); font-size:14px;">0 assets selected</strong>
      </span>
      <div style="display:flex; gap:8px; align-items:center; margin-left:auto; flex-wrap:wrap;">
        <button id="bulk-assign-btn" class="primary" style="padding:6px 14px; font-size:13px;">Bulk Assign</button>
        <button id="bulk-status-btn" class="ghost" style="padding:6px 14px; font-size:13px;">Change Status</button>
        <button id="bulk-retire-btn" class="ghost" style="padding:6px 14px; font-size:13px; color:var(--danger); border-color:var(--danger);">Bulk Retire</button>
        <button id="bulk-export-btn" class="ghost" style="padding:6px 14px; font-size:13px;">Export Selected CSV</button>
        <button id="bulk-clear-btn" class="ghost" style="padding:6px 10px; font-size:13px; color:var(--muted);">Deselect All</button>
      </div>
    </div>

    <table>
      <thead>
        <tr>
          <th style="width:38px; text-align:center;"><input type="checkbox" id="asset-select-all" title="Select All" /></th>
          <th>Tag</th>
          <th>Model</th>
          <th>Serial</th>
          <th>Status</th>
          <th>Risk</th>
          <th>Assigned to</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>${assetRows(data.content)}</tbody>
    </table>`;

  document.getElementById("asset-search").onclick = async () => {
    const q = document.getElementById("asset-q").value;
    const status = document.getElementById("asset-status").value;
    const qs = new URLSearchParams({ size: "50" });
    if (q) qs.set("q", q);
    if (status) qs.set("status", status);
    const res = await api("/api/assets?" + qs.toString());
    el.querySelector("tbody").innerHTML = assetRows(res.content);
    bindAssetActions();
    bindBulkOperations();
  };

  document.getElementById("asset-high-risk").onclick = async () => {
    try {
      const res = await api("/api/assets/high-risk");
      el.querySelector("tbody").innerHTML = assetRows(res);
      bindAssetActions();
      bindBulkOperations();
    } catch(e) { alert(e.message); }
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
    } catch (ex) { alert("CSV export failed: " + ex.message); }
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
      } catch (ex) { alert("Import failed: " + ex.message); }
      finally { importInput.value = ""; }
    };
  }

  bindAssetActions();
  bindBulkOperations();
  const add = document.getElementById("asset-add");
  if (add) add.onclick = () => {
    openModal(`
      <h3>Register New Asset</h3>
      <form id="asset-form">
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
          <label>Asset Tag<input name="assetTag" placeholder="e.g. LAP-1005" required /></label>
          <label>Serial Number<input name="serialNumber" placeholder="e.g. SN-DELL-1005" required /></label>
        </div>
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
          <label>Type<input name="type" placeholder="Laptop / Monitor" value="Laptop" required /></label>
          <label>Brand<input name="brand" placeholder="Dell / Lenovo / HP" required /></label>
        </div>
        <label>Model<input name="model" placeholder="Latitude 5540" required /></label>
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
          <label>Purchase Date<input name="purchaseDate" type="date" /></label>
          <label>Warranty Expiry<input name="warrantyExpiry" type="date" /></label>
        </div>
        <label>Purchase Price (₹)<input name="price" type="number" step="0.01" min="0" placeholder="e.g. 65000.00" /></label>
        <label>Notes / Specs<input name="notes" placeholder="i7 16GB RAM, 512GB SSD" /></label>
        <input type="hidden" name="status" value="AVAILABLE" />
        <button style="margin-top:14px;">Save Asset</button>
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
  if (!items || items.length === 0) {
    return '<tr><td colspan="8" style="text-align:center; padding:24px; color:var(--muted);">No assets found.</td></tr>';
  }
  return items.map((a) => `<tr>
    <td style="text-align:center;"><input type="checkbox" class="asset-checkbox" data-id="${a.id}" data-tag="${a.assetTag}" data-model="${a.brand || ''} ${a.model}" data-status="${a.status}" /></td>
    <td><strong>${a.assetTag}</strong></td>
    <td>${a.brand || ""} ${a.model}</td>
    <td><code>${a.serialNumber}</code></td>
    <td>${badge(a.status)}</td>
    <td><span class="badge" style="background:${a.riskScore >= 30 ? 'var(--danger)' : 'var(--muted)'}">${a.riskScore || 0}</span></td>
    <td>${a.assignedEmployeeName ? `<strong>${a.assignedEmployeeName}</strong>` : `<span class="muted">None</span>`}</td>
    <td>
      <button class="ghost" data-qr="${a.id}" data-tag="${a.assetTag}" data-model="${a.brand || ''} ${a.model}">QR Code</button>
      <button class="ghost" data-hist="${a.id}">History</button>
      ${a.status === 'AVAILABLE' ? `
        <button class="ghost" data-assign="${a.id}" data-tag="${a.assetTag}" data-model="${a.brand || ''} ${a.model}" style="color:var(--accent); font-weight:bold;">Assign</button>
      ` : ''}
      ${a.status === 'ASSIGNED' ? `
        <button class="ghost" data-unassign="${a.id}" data-tag="${a.assetTag}" data-emp="${a.assignedEmployeeName || ''}" style="color:var(--warn);">Unassign</button>
      ` : ''}
      ${a.status === 'AVAILABLE' ? `<button class="ghost" data-repair="${a.id}">Repair</button>` : ''}
      <button class="ghost" data-edit-asset="${a.id}">Edit</button>
    </td></tr>`).join("");
}

function bindBulkOperations() {
  const selectAll = document.getElementById("asset-select-all");
  const bulkBar = document.getElementById("bulk-actions-bar");
  const countSpan = document.getElementById("bulk-selected-count");
  if (!bulkBar) return;

  const getSelectedCheckboxes = () => Array.from(document.querySelectorAll(".asset-checkbox:checked"));

  function updateBulkBar() {
    const selected = getSelectedCheckboxes();
    if (selected.length > 0) {
      bulkBar.style.display = "flex";
      countSpan.textContent = `${selected.length} asset${selected.length > 1 ? 's' : ''} selected`;
    } else {
      bulkBar.style.display = "none";
    }
    const allCbs = document.querySelectorAll(".asset-checkbox");
    if (selectAll) {
      selectAll.checked = allCbs.length > 0 && selected.length === allCbs.length;
      selectAll.indeterminate = selected.length > 0 && selected.length < allCbs.length;
    }
  }

  if (selectAll) {
    selectAll.onchange = () => {
      document.querySelectorAll(".asset-checkbox").forEach(cb => cb.checked = selectAll.checked);
      updateBulkBar();
    };
  }

  document.querySelectorAll(".asset-checkbox").forEach(cb => {
    cb.onchange = updateBulkBar;
  });

  const clearBtn = document.getElementById("bulk-clear-btn");
  if (clearBtn) {
    clearBtn.onclick = () => {
      document.querySelectorAll(".asset-checkbox").forEach(cb => cb.checked = false);
      if (selectAll) {
        selectAll.checked = false;
        selectAll.indeterminate = false;
      }
      updateBulkBar();
    };
  }

  // 1. Bulk Assign
  const assignBtn = document.getElementById("bulk-assign-btn");
  if (assignBtn) {
    assignBtn.onclick = async () => {
      const selected = getSelectedCheckboxes();
      if (selected.length === 0) return alert("Please select at least one asset.");
      const assetIds = selected.map(cb => Number(cb.dataset.id));
      const assetTags = selected.map(cb => cb.dataset.tag).join(", ");

      try {
        const empData = await api("/api/employees?size=100");
        const activeEmps = (empData.content || empData).filter(e => e.active);

        openModal(`
          <h3>Bulk Assign Assets (${assetIds.length})</h3>
          <p class="muted" style="margin-bottom:12px;">Assigning: <strong>${assetTags}</strong></p>
          <form id="bulk-assign-form">
            <label>Select Target Employee
              <select name="employeeId" required style="width:100%; margin-top:4px;">
                <option value="">-- Choose Active Employee --</option>
                ${activeEmps.map(e => `<option value="${e.id}">${e.firstName} ${e.lastName} (${e.employeeCode}) - ${e.department || 'General'}</option>`).join("")}
              </select>
            </label>
            <label style="margin-top:12px; display:block;">Assignment Notes (Optional)
              <input name="notes" placeholder="e.g. Assigned for team expansion" style="width:100%; margin-top:4px;" />
            </label>
            <div id="bulk-assign-error" style="color:var(--danger); background:rgba(255,107,122,0.1); border:1px solid var(--danger); border-radius:8px; padding:10px 12px; margin-top:12px; display:none; font-size:13px; line-height:1.4;"></div>
            <div style="display:flex; justify-content:flex-end; gap:8px; margin-top:18px;">
              <button type="button" class="ghost" onclick="modal.hidden=true">Cancel</button>
              <button type="submit" class="primary">Confirm Bulk Assignment</button>
            </div>
          </form>
        `);

        document.getElementById("bulk-assign-form").onsubmit = async (ev) => {
          ev.preventDefault();
          const empId = ev.target.employeeId.value;
          const notes = ev.target.notes.value;
          const errDiv = document.getElementById("bulk-assign-error");
          errDiv.style.display = "none";

          try {
            const res = await api("/api/assets/bulk-assign", {
              method: "POST",
              body: JSON.stringify({ assetIds, employeeId: Number(empId), notes })
            });

            if (!res.success) {
              errDiv.innerHTML = `<strong>${res.message}:</strong><br/>• ` + res.errors.join("<br/>• ");
              errDiv.style.display = "block";
              return;
            }

            alert(res.message || `Successfully assigned ${assetIds.length} assets!`);
            modal.hidden = true;
            renderAssets();
          } catch (ex) {
            errDiv.textContent = ex.message;
            errDiv.style.display = "block";
          }
        };
      } catch (ex) { alert("Failed to fetch employees: " + ex.message); }
    };
  }

  // 2. Bulk Status Update
  const statusBtn = document.getElementById("bulk-status-btn");
  if (statusBtn) {
    statusBtn.onclick = () => {
      const selected = getSelectedCheckboxes();
      if (selected.length === 0) return alert("Please select at least one asset.");
      const assetIds = selected.map(cb => Number(cb.dataset.id));
      const assetTags = selected.map(cb => cb.dataset.tag).join(", ");

      openModal(`
        <h3>Bulk Update Status (${assetIds.length})</h3>
        <p class="muted" style="margin-bottom:12px;">Selected: <strong>${assetTags}</strong></p>
        <form id="bulk-status-form">
          <label>Target Status
            <select name="status" required style="width:100%; margin-top:4px;">
              <option value="AVAILABLE">AVAILABLE (Unassigns & marks ready for deployment)</option>
              <option value="REPAIR">REPAIR (Sends to IT Maintenance)</option>
              <option value="RETIRED">RETIRED (Permanently decommissions asset)</option>
            </select>
          </label>
          <label style="margin-top:12px; display:block;">Status Change Reason / Notes
            <input name="notes" placeholder="e.g. Scheduled hardware maintenance" style="width:100%; margin-top:4px;" />
          </label>
          <div id="bulk-status-error" style="color:var(--danger); background:rgba(255,107,122,0.1); border:1px solid var(--danger); border-radius:8px; padding:10px 12px; margin-top:12px; display:none; font-size:13px; line-height:1.4;"></div>
          <div style="display:flex; justify-content:flex-end; gap:8px; margin-top:18px;">
            <button type="button" class="ghost" onclick="modal.hidden=true">Cancel</button>
            <button type="submit" class="primary">Update Status</button>
          </div>
        </form>
      `);

      document.getElementById("bulk-status-form").onsubmit = async (ev) => {
        ev.preventDefault();
        const st = ev.target.status.value;
        const notes = ev.target.notes.value;
        const errDiv = document.getElementById("bulk-status-error");
        errDiv.style.display = "none";

        try {
          const res = await api("/api/assets/bulk-status", {
            method: "POST",
            body: JSON.stringify({ assetIds, status: st, notes })
          });

          if (!res.success) {
            errDiv.innerHTML = `<strong>${res.message}:</strong><br/>• ` + res.errors.join("<br/>• ");
            errDiv.style.display = "block";
            return;
          }

          alert(res.message || `Successfully updated ${assetIds.length} assets!`);
          modal.hidden = true;
          renderAssets();
        } catch (ex) {
          errDiv.textContent = ex.message;
          errDiv.style.display = "block";
        }
      };
    };
  }

  // 3. Bulk Retire
  const retireBtn = document.getElementById("bulk-retire-btn");
  if (retireBtn) {
    retireBtn.onclick = () => {
      const selected = getSelectedCheckboxes();
      if (selected.length === 0) return alert("Please select at least one asset.");
      const assetIds = selected.map(cb => Number(cb.dataset.id));
      const assetTags = selected.map(cb => cb.dataset.tag).join(", ");

      openModal(`
        <h3 style="color:var(--danger);">Bulk Decommission / Retire (${assetIds.length})</h3>
        <p class="muted" style="margin-bottom:12px;">Are you sure you want to retire: <strong>${assetTags}</strong>?</p>
        <p style="font-size:13px; color:var(--warn); background:rgba(245,193,92,0.1); border:1px solid var(--warn); padding:10px 12px; border-radius:8px; margin-bottom:14px;">
          ⚠ Notice: Retiring assets will immediately unassign any linked employees and permanently mark the equipment as decommissioned.
        </p>
        <form id="bulk-retire-form">
          <label>Decommission Reason
            <input name="reason" placeholder="e.g. End of Life / Hardware lease expired" required style="width:100%; margin-top:4px;" />
          </label>
          <div id="bulk-retire-error" style="color:var(--danger); background:rgba(255,107,122,0.1); border:1px solid var(--danger); border-radius:8px; padding:10px 12px; margin-top:12px; display:none; font-size:13px; line-height:1.4;"></div>
          <div style="display:flex; justify-content:flex-end; gap:8px; margin-top:18px;">
            <button type="button" class="ghost" onclick="modal.hidden=true">Cancel</button>
            <button type="submit" style="background:var(--danger); color:#fff; border:none; padding:8px 16px; border-radius:8px; cursor:pointer; font-weight:600;">Confirm Retire</button>
          </div>
        </form>
      `);

      document.getElementById("bulk-retire-form").onsubmit = async (ev) => {
        ev.preventDefault();
        const reason = ev.target.reason.value;
        const errDiv = document.getElementById("bulk-retire-error");
        errDiv.style.display = "none";

        try {
          const res = await api("/api/assets/bulk-retire", {
            method: "POST",
            body: JSON.stringify({ assetIds, reason })
          });

          if (!res.success) {
            errDiv.innerHTML = `<strong>${res.message}:</strong><br/>• ` + res.errors.join("<br/>• ");
            errDiv.style.display = "block";
            return;
          }

          alert(res.message || `Successfully retired ${assetIds.length} assets!`);
          modal.hidden = true;
          renderAssets();
        } catch (ex) {
          errDiv.textContent = ex.message;
          errDiv.style.display = "block";
        }
      };
    };
  }

  // 4. Bulk Export Selected CSV
  const exportBtn = document.getElementById("bulk-export-btn");
  if (exportBtn) {
    exportBtn.onclick = async () => {
      const selected = getSelectedCheckboxes();
      if (selected.length === 0) return alert("Please select at least one asset to export.");
      const assetIds = selected.map(cb => Number(cb.dataset.id));

      try {
        const blob = await apiBlob("/api/assets/bulk-export", {
          method: "POST",
          body: JSON.stringify({ assetIds })
        });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `selected-assets-${assetIds.length}.csv`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
      } catch (ex) {
        alert("Bulk CSV export failed: " + ex.message);
      }
    };
  }
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
          <div class="qrcode-frame"><img src="${imgUrl}" alt="Asset QR Code" /></div>
          <a href="${imgUrl}" download="${b.dataset.tag}-qrcode.png" style="color:var(--accent); font-weight:600; text-decoration:none;">⬇ Download QR Image</a>
        </div>`);
    } catch (ex) { alert("Failed to load QR code: " + ex.message); }
  });

  document.querySelectorAll("[data-hist]").forEach((b) => b.onclick = async () => {
    const hist = await api("/api/assets/" + b.dataset.hist + "/history");
    openModal(`<h3>Asset Lifecycle History</h3><div class="timeline">${hist.map((h) =>
      `<p><strong>${h.action}</strong> ${h.fromStatus || ""} &rarr; ${h.toStatus || ""}<br/>
       ${h.employeeName ? `Employee: ${h.employeeName}<br/>` : ""}${h.notes || ""}<br/><span class="muted">${h.createdAt}</span></p>`).join("")}</div>`);
  });

  // Modal Laptop Assignment with active employee dropdown
  document.querySelectorAll("[data-assign]").forEach((b) => b.onclick = async () => {
    try {
      const empsRes = await api("/api/employees?active=true&size=100");
      const activeEmps = empsRes.content || [];
      if (activeEmps.length === 0) {
        alert("No active employees found to assign equipment to! Please create or activate an employee first.");
        return;
      }
      openModal(`
        <h3>Assign Laptop / Equipment</h3>
        <p class="muted" style="margin-bottom:16px;">
          Asset: <strong>${b.dataset.tag}</strong> (${b.dataset.model})
        </p>
        <form id="assign-laptop-form">
          <label>Select Employee to Assign:
            <select name="employeeId" required style="font-size:15px; padding:10px;">
              <option value="">-- Choose Employee --</option>
              ${activeEmps.map(e => `
                <option value="${e.id}">${e.employeeCode} — ${e.firstName} ${e.lastName} (${e.department || 'General'})</option>
              `).join("")}
            </select>
          </label>
          <div style="display:flex; justify-content:flex-end; gap:8px; margin-top:20px;">
            <button type="button" class="ghost" onclick="document.getElementById('modal').hidden = true;">Cancel</button>
            <button type="submit">Confirm Assignment</button>
          </div>
        </form>`);

      document.getElementById("assign-laptop-form").onsubmit = async (ev) => {
        ev.preventDefault();
        const f = Object.fromEntries(new FormData(ev.target).entries());
        try {
          await api("/api/assets/" + b.dataset.assign + "/assign", {
            method: "POST",
            body: JSON.stringify({ employeeId: Number(f.employeeId) })
          });
          alert(`Equipment ${b.dataset.tag} assigned successfully! Status is now ASSIGNED.`);
          modal.hidden = true;
          renderAssets();
        } catch (ex) { alert(ex.message); }
      };
    } catch (ex) { alert(ex.message); }
  });

  // Unassign action
  document.querySelectorAll("[data-unassign]").forEach((b) => b.onclick = async () => {
    if (!confirm(`Unassign ${b.dataset.tag} from ${b.dataset.emp}? The asset status will return to AVAILABLE.`)) return;
    try {
      await api("/api/assets/" + b.dataset.unassign + "/unassign", { method: "POST", body: "{}" });
      alert(`Equipment ${b.dataset.tag} unassigned! Status is now AVAILABLE.`);
      renderAssets();
    } catch (ex) { alert(ex.message); }
  });

  document.querySelectorAll("[data-repair]").forEach((b) => b.onclick = async () => {
    try {
      await api("/api/assets/" + b.dataset.repair + "/status", {
        method: "POST", body: JSON.stringify({ status: "REPAIR", notes: "Sent for maintenance" })
      });
      renderAssets();
    } catch (ex) { alert(ex.message); }
  });

  // Edit Asset modal
  document.querySelectorAll("[data-edit-asset]").forEach((b) => b.onclick = async () => {
    try {
      const a = await api("/api/assets/" + b.dataset.editAsset);
      openModal(`
        <h3>Edit Asset: ${a.assetTag}</h3>
        <form id="edit-asset-form">
          <input type="hidden" name="assetTag" value="${a.assetTag}" />
          <input type="hidden" name="serialNumber" value="${a.serialNumber}" />
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
            <label>Type<input name="type" value="${a.type}" required /></label>
            <label>Brand<input name="brand" value="${a.brand || ''}" required /></label>
          </div>
          <label>Model<input name="model" value="${a.model}" required /></label>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
            <label>Purchase Date<input name="purchaseDate" type="date" value="${a.purchaseDate || ''}" /></label>
            <label>Warranty Expiry<input name="warrantyExpiry" type="date" value="${a.warrantyExpiry || ''}" /></label>
          </div>
          <label>Purchase Price (₹)<input name="price" type="number" step="0.01" min="0" value="${a.price != null ? a.price : ''}" placeholder="e.g. 65000.00" /></label>
          <label>Status
            <select name="status">
              <option ${a.status === 'AVAILABLE' ? 'selected' : ''}>AVAILABLE</option>
              <option ${a.status === 'ASSIGNED' ? 'selected' : ''}>ASSIGNED</option>
              <option ${a.status === 'REPAIR' ? 'selected' : ''}>REPAIR</option>
              <option ${a.status === 'RETIRED' ? 'selected' : ''}>RETIRED</option>
            </select>
          </label>
          <label>Notes<input name="notes" value="${a.notes || ''}" /></label>
          <button style="margin-top:14px;">Save Asset Changes</button>
        </form>`);

      document.getElementById("edit-asset-form").onsubmit = async (ev) => {
        ev.preventDefault();
        const f = Object.fromEntries(new FormData(ev.target).entries());
        if (f.price === undefined) {
          f.price = a.price != null ? a.price : null;
        } else {
          f.price = f.price && f.price.trim() !== '' ? f.price.trim() : null;
        }
        if (!f.purchaseDate) f.purchaseDate = null;
        if (!f.warrantyExpiry) f.warrantyExpiry = null;
        try {
          await api("/api/assets/" + a.id, { method: "PUT", body: JSON.stringify(f) });
          alert("Asset updated successfully!");
          modal.hidden = true;
          renderAssets();
        } catch (ex) { alert(ex.message); }
      };
    } catch (ex) { alert(ex.message); }
  });
}

async function renderSoftware() {
  const el = document.getElementById("view-software");
  const data = await api("/api/software");

  function swStatusBadge(status) {
    const s = status || 'PERPETUAL';
    let bg = 'var(--muted)';
    if (s === 'ACTIVE') bg = '#28a745';
    else if (s === 'EXPIRING_SOON') bg = 'var(--danger)';
    else if (s === 'EXPIRING') bg = 'var(--warn)';
    else if (s === 'EXPIRED') bg = '#dc3545';
    else if (s === 'PERPETUAL') bg = '#17a2b8';
    return `<span class="badge" style="background:${bg}; color:#fff; font-size:11px;">${s.replace('_', ' ')}</span>`;
  }

  el.innerHTML = `
    <div class="toolbar">
      <button id="sw-add">Add Software License</button>
    </div>
    <table>
      <thead>
        <tr>
          <th>Software Name</th>
          <th>Vendor</th>
          <th>License Key</th>
          <th>Available / Total Seats</th>
          <th>Status</th>
          <th>Expiration Date</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        ${data.length === 0 ? '<tr><td colspan="7" class="muted" style="text-align:center; padding:24px;">No software licenses registered.</td></tr>' : data.map((s) => {
          const maskedKey = s.licenseKey ? (s.licenseKey.length > 4 ? '••••-••••-' + s.licenseKey.slice(-4) : '••••••••') : '-';
          return `
          <tr>
            <td><strong>${s.name}</strong></td>
            <td>${s.vendor || '<span class="muted">N/A</span>'}</td>
            <td><code>${maskedKey}</code></td>
            <td><strong>${s.availableSeats}</strong> / ${s.totalSeats} seats</td>
            <td>${swStatusBadge(s.status)}</td>
            <td>${s.expirationDate ? (s.daysUntilExpiry != null ? `${s.expirationDate} (${s.daysUntilExpiry}d)` : s.expirationDate) : "Perpetual"}</td>
            <td>
              <button class="ghost" data-sw-view="${s.id}" data-sw-name="${s.name}">Assignments</button>
              <button class="ghost" data-sw-edit="${s.id}">Edit</button>
              ${s.availableSeats > 0 ? `<button class="ghost" data-sw-assign="${s.id}" data-sw-name="${s.name}" style="color:var(--accent); font-weight:bold;">Assign</button>` : ''}
            </td>
          </tr>`;
        }).join("")}
      </tbody>
    </table>`;

  document.getElementById("sw-add").onclick = () => {
    openModal(`
      <h3>Register Software License</h3>
      <form id="sw-form">
        <label>Software Name<input name="name" placeholder="e.g. IntelliJ IDEA Ultimate" required /></label>
        <label>Vendor<input name="vendor" placeholder="e.g. JetBrains / Microsoft / Adobe" /></label>
        <label>License Key<input name="licenseKey" placeholder="e.g. XXXX-YYYY-ZZZZ-1234" /></label>
        <label>Total Seats<input name="totalSeats" type="number" min="0" value="5" required /></label>
        <label>Expiration Date<input name="expirationDate" type="date" /></label>
        <button style="margin-top:12px;">Save License</button>
      </form>`);
    document.getElementById("sw-form").onsubmit = async (ev) => {
      ev.preventDefault();
      const f = Object.fromEntries(new FormData(ev.target).entries());
      f.totalSeats = Number(f.totalSeats);
      if (!f.vendor || f.vendor.trim() === '') delete f.vendor;
      if (!f.expirationDate) delete f.expirationDate;
      try {
        await api("/api/software", { method: "POST", body: JSON.stringify(f) });
        modal.hidden = true;
        renderSoftware();
      } catch (ex) { alert(ex.message); }
    };
  };

  // Edit Software License
  document.querySelectorAll("[data-sw-edit]").forEach((b) => b.onclick = async () => {
    try {
      const s = await api("/api/software/" + b.dataset.swEdit);
      openModal(`
        <h3>Edit Software License: ${s.name}</h3>
        <form id="sw-edit-form">
          <label>Software Name<input name="name" value="${s.name || ''}" required /></label>
          <label>Vendor<input name="vendor" value="${s.vendor || ''}" placeholder="e.g. JetBrains / Microsoft" /></label>
          <label>License Key<input name="licenseKey" value="${s.licenseKey || ''}" placeholder="e.g. XXXX-YYYY-ZZZZ-1234" /></label>
          <label>Total Seats<input name="totalSeats" type="number" min="0" value="${s.totalSeats != null ? s.totalSeats : 0}" required /></label>
          <label>Expiration Date<input name="expirationDate" type="date" value="${s.expirationDate || ''}" /></label>
          <button style="margin-top:12px;">Update License</button>
        </form>`);
      document.getElementById("sw-edit-form").onsubmit = async (ev) => {
        ev.preventDefault();
        const f = Object.fromEntries(new FormData(ev.target).entries());
        f.totalSeats = Number(f.totalSeats);
        if (!f.vendor || f.vendor.trim() === '') f.vendor = null;
        if (!f.expirationDate || f.expirationDate.trim() === '') f.expirationDate = null;
        try {
          await api("/api/software/" + s.id, { method: "PUT", body: JSON.stringify(f) });
          alert("Software license updated successfully!");
          modal.hidden = true;
          renderSoftware();
        } catch (ex) { alert(ex.message); }
      };
    } catch (ex) { alert(ex.message); }
  });

  document.querySelectorAll("[data-sw-view]").forEach((b) => b.onclick = async () => {
    try {
      const assignments = await api("/api/software/" + b.dataset.swView + "/assignments");
      openModal(`
        <h3>Assignments: ${b.dataset.swName}</h3>
        ${assignments.length === 0 ? '<p class="muted">No employees currently assigned to this license.</p>' : `
          <table style="margin-top:12px">
            <thead><tr><th>Employee</th><th>Assigned Date</th><th>Action</th></tr></thead>
            <tbody>
              ${assignments.map(a => `
                <tr>
                  <td><strong>${a.employeeName}</strong></td>
                  <td>${new Date(a.assignedAt).toLocaleDateString()}</td>
                  <td><button class="ghost" data-unassign-sw="${a.id}">Unassign</button></td>
                </tr>`).join("")}
            </tbody>
          </table>`}
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
    try {
      const empsRes = await api("/api/employees?active=true&size=100");
      const activeEmps = (empsRes && empsRes.content) ? empsRes.content : (Array.isArray(empsRes) ? empsRes : []);

      if (activeEmps.length === 0) {
        alert("No active employees found to assign software licenses to! Please create or activate an employee first.");
        return;
      }

      openModal(`
        <h3>Assign License: ${b.dataset.swName}</h3>
        <form id="sw-assign-form">
          <label>Select Employee to Assign Seat:
            <select name="employeeId" required style="font-size:15px; padding:8px;">
              <option value="">-- Choose Employee --</option>
              ${activeEmps.map(e => `<option value="${e.id}">${e.firstName} ${e.lastName} (${e.employeeCode}) - ${e.department || 'General'}</option>`).join("")}
            </select>
          </label>
          <button style="margin-top:14px;">Assign Seat</button>
        </form>`);

      document.getElementById("sw-assign-form").onsubmit = async (ev) => {
        ev.preventDefault();
        const f = Object.fromEntries(new FormData(ev.target).entries());
        const empId = Number(f.employeeId);
        if (!empId || isNaN(empId)) {
          alert("Please select an employee.");
          return;
        }
        try {
          await api("/api/software/" + b.dataset.swAssign + "/assign", {
            method: "POST",
            body: JSON.stringify({ employeeId: empId })
          });
          alert("Seat assigned successfully!");
          modal.hidden = true;
          renderSoftware();
        } catch (ex) { alert(ex.message); }
      };
    } catch (ex) {
      alert("Failed to load employees: " + ex.message);
    }
  });
}

async function renderRequests() {
  const el = document.getElementById("view-requests");
  const list = await api("/api/asset-requests");
  el.innerHTML = `
    <div class="toolbar">
      <p class="muted" style="margin:0; align-self:center;">Review incoming hardware and peripheral requests submitted by employees.</p>
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
          </tr>`).join("")}
      </tbody>
    </table>`;

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
    const note = prompt("Reason for rejection:", "Out of budget / Hardware unavailable");
    if (!note) return;
    try {
      await api("/api/asset-requests/" + b.dataset.rejectReq + "/resolve", {
        method: "POST",
        body: JSON.stringify({ status: "REJECTED", adminNotes: note })
      });
      renderRequests();
    } catch (ex) { alert(ex.message); }
  });
}

async function renderTickets() {
  const el = document.getElementById("view-tickets");
  const data = await api("/api/tickets?size=50");
  el.innerHTML = `
    <div class="toolbar">
      <select id="t-status"><option value="">All statuses</option><option>OPEN</option><option>IN_PROGRESS</option><option>RESOLVED</option><option>CLOSED</option></select>
      <select id="t-priority"><option value="">All priorities</option><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select>
      <button id="t-filter">Filter</button>
      <button id="t-add">New Ticket</button>
    </div>
    <table>
      <thead><tr><th>#</th><th>Title</th><th>Priority</th><th>Status</th><th>SLA Status</th><th>Asset</th><th>Reporter</th><th>Actions</th></tr></thead>
      <tbody>${data.content.map((t) => `<tr>
        <td><strong>${t.ticketNumber}</strong></td><td>${t.title}</td>
        <td>${badge(t.priority)}</td><td>${badge(t.status)}</td>
        <td><span class="badge ${t.slaStatus.toLowerCase()}">${t.slaStatus.replace('_', ' ')}</span></td>
        <td>${t.assetTag || "-"}</td><td>${t.reportedByName}</td>
        <td>
          <button class="ghost" data-progress="${t.id}">In Progress</button>
          <button class="ghost" data-resolve="${t.id}">Resolve</button>
        </td></tr>`).join("")}</tbody>
    </table>`;

  document.getElementById("t-filter").onclick = async () => {
    const status = document.getElementById("t-status").value;
    const priority = document.getElementById("t-priority").value;
    const qs = new URLSearchParams({ size: "50" });
    if (status) qs.set("status", status);
    if (priority) qs.set("priority", priority);
    const res = await api("/api/tickets?" + qs.toString());
    el.querySelector("tbody").innerHTML = res.content.map((t) => `<tr>
      <td><strong>${t.ticketNumber}</strong></td><td>${t.title}</td>
      <td>${badge(t.priority)}</td><td>${badge(t.status)}</td>
      <td><span class="badge ${t.slaStatus.toLowerCase()}">${t.slaStatus.replace('_', ' ')}</span></td>
      <td>${t.assetTag || "-"}</td><td>${t.reportedByName}</td>
      <td>
        <button class="ghost" data-progress="${t.id}">In Progress</button>
        <button class="ghost" data-resolve="${t.id}">Resolve</button>
      </td></tr>`).join("");
    bindTicketActions();
  };

  bindTicketActions();

  document.getElementById("t-add").onclick = () => {
    openModal(`
      <h3>Create Service Ticket</h3>
      <form id="t-form">
        <label>Issue Title<input name="title" placeholder="Issue title" required /></label>
        <label>Description<textarea name="description" placeholder="Explain the technical problem..." required></textarea></label>
        <label>Priority
          <select name="priority"><option>LOW</option><option selected>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select>
        </label>
        <label>Asset ID (Optional)<input name="assetId" placeholder="Asset ID (e.g. 1)" /></label>
        <button style="margin-top:12px;">Create Ticket</button>
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
    const notes = prompt("Resolution notes:", "Resolved by IT Support");
    if (notes === null) return;
    try {
      await api("/api/tickets/" + b.dataset.resolve + "/status", {
        method: "POST", body: JSON.stringify({ status: "RESOLVED", resolutionNotes: notes })
      });
      renderTickets();
    } catch (ex) { alert(ex.message); }
  });
}

async function renderNotifications() {
  const el = document.getElementById("view-notifications");
  const data = await api("/api/notifications?size=50");
  el.innerHTML = data.content.map((n) => `
    <div class="stat" style="margin-bottom:12px; display:flex; justify-content:space-between; align-items:center;">
      <div>
        <p style="margin:0 0 4px 0;"><strong>${n.message}</strong></p>
        <p class="muted" style="margin:0; font-size:12px;">${n.createdAt} · ${n.read ? "read" : "unread"}</p>
      </div>
      ${n.read ? "" : `<button class="ghost" data-read="${n.id}">Mark Read</button>`}
    </div>`).join("") || "<p class='muted'>No notifications.</p>";

  document.querySelectorAll("[data-read]").forEach((b) => b.onclick = async () => {
    await api("/api/notifications/" + b.dataset.read + "/read", { method: "POST" });
    renderNotifications();
  });
}

bindNav();
loadView("dashboard");
