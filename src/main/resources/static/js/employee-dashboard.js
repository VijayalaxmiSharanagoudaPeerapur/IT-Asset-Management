const user = JSON.parse(localStorage.getItem("itasm_user") || "null");
if (!token() || !user) {
  localStorage.clear();
  location.href = "/employee-login.html";
}

document.getElementById("who").textContent = user.fullName + (user.role === "ADMIN" ? " (Admin)" : " (Employee)");
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
  if (name === "assets") return renderMyAssets();
  if (name === "requests") return renderMyRequests();
  if (name === "tickets") return renderMyTickets();
  if (name === "software") return renderMySoftware();
  if (name === "notifications") return renderNotifications();
  if (name === "profile") return renderMyProfile();
}

function badge(v) {
  return `<span class="badge ${v || ""}">${v || "-"}</span>`;
}

async function renderMyAssets() {
  const el = document.getElementById("view-assets");
  let assets = [];
  try {
    if (user.employeeId) {
      assets = await api("/api/assets/me");
    } else {
      const res = await api("/api/assets?status=ASSIGNED");
      assets = res.content || [];
    }
  } catch (err) {
    assets = [];
  }

  el.innerHTML = `
    <div class="toolbar">
      <p class="muted" style="margin:0; align-self:center;">These are the hardware assets currently assigned to you.</p>
    </div>
    <table>
      <thead><tr><th>Asset Tag</th><th>Equipment Model</th><th>Serial Number</th><th>Status</th><th>Actions</th></tr></thead>
      <tbody>
        ${assets.length === 0 ? '<tr><td colspan="5" class="muted">No assets are currently assigned to your account. Need equipment? Click "Request Equipment" in the sidebar.</td></tr>' : assets.map((a) => `
          <tr>
            <td><strong>${a.assetTag}</strong></td>
            <td>${a.brand || ""} ${a.model} (${a.type})</td>
            <td><code>${a.serialNumber}</code></td>
            <td>${badge(a.status)}</td>
            <td>
              <button class="ghost" data-qr="${a.id}" data-tag="${a.assetTag}" data-model="${a.brand || ''} ${a.model}">View QR Code</button>
              <button class="ghost" data-report-asset="${a.id}" data-tag="${a.assetTag}" style="color:var(--warn);">Report Issue</button>
            </td>
          </tr>`).join("")}
      </tbody>
    </table>`;

  document.querySelectorAll("[data-qr]").forEach((b) => b.onclick = async () => {
    try {
      const blob = await apiBlob("/api/assets/" + b.dataset.qr + "/qrcode");
      const imgUrl = URL.createObjectURL(blob);
      openModal(`
        <h3>QR Badge: ${b.dataset.tag}</h3>
        <p class="muted">${b.dataset.model}</p>
        <div class="qrcode-container">
          <div class="qrcode-frame"><img src="${imgUrl}" alt="Asset QR Code" /></div>
          <a href="${imgUrl}" download="${b.dataset.tag}-qrcode.png" style="color:var(--accent); font-weight:600; text-decoration:none;">⬇ Download QR Badge</a>
        </div>`);
    } catch (ex) { alert("Failed to load QR code: " + ex.message); }
  });

  document.querySelectorAll("[data-report-asset]").forEach((b) => b.onclick = () => {
    openModal(`
      <h3>Report Equipment Issue</h3>
      <p class="muted">Equipment: <strong>${b.dataset.tag}</strong></p>
      <form id="asset-ticket-form">
        <input type="hidden" name="assetId" value="${b.dataset.reportAsset}" />
        <label>Issue Title
          <input name="title" value="Technical issue with ${b.dataset.tag}" required />
        </label>
        <label>Detailed Description
          <textarea name="description" placeholder="Describe what is wrong (e.g. screen flickering, battery not charging, keys unresponsive)..." rows="3" required></textarea>
        </label>
        <label>Priority
          <select name="priority">
            <option>LOW</option>
            <option selected>MEDIUM</option>
            <option>HIGH</option>
            <option>CRITICAL</option>
          </select>
        </label>
        <button style="margin-top:14px; background:#38bdf8; color:#0c2d48;">Submit Ticket</button>
      </form>`);

    document.getElementById("asset-ticket-form").onsubmit = async (ev) => {
      ev.preventDefault();
      const f = Object.fromEntries(new FormData(ev.target).entries());
      f.assetId = Number(f.assetId);
      try {
        await api("/api/tickets", { method: "POST", body: JSON.stringify(f) });
        alert("Support ticket created! IT Support will review it shortly.");
        modal.hidden = true;
        document.querySelector('[data-view="tickets"]').click();
      } catch (ex) { alert(ex.message); }
    };
  });
}

async function renderMyRequests() {
  const el = document.getElementById("view-requests");
  const list = await api("/api/asset-requests/me");
  el.innerHTML = `
    <div class="toolbar">
      <button id="req-create" style="background:#38bdf8; color:#0c2d48;">+ Request New Equipment</button>
    </div>
    <table>
      <thead><tr><th>Requested Item</th><th>Reason</th><th>Status</th><th>Submitted</th><th>Admin Response</th></tr></thead>
      <tbody>
        ${list.length === 0 ? '<tr><td colspan="5" class="muted">You have not submitted any equipment requests yet.</td></tr>' : list.map((r) => `
          <tr>
            <td><strong>${r.requestedItem}</strong></td>
            <td>${r.reason || "-"}</td>
            <td>${badge(r.status)}</td>
            <td>${new Date(r.createdAt).toLocaleDateString()}</td>
            <td>${r.adminNotes || "<span class='muted'>Pending admin review</span>"}</td>
          </tr>`).join("")}
      </tbody>
    </table>`;

  document.getElementById("req-create").onclick = () => {
    openModal(`
      <h3>Request Equipment / Peripheral</h3>
      <form id="req-form">
        <label>Requested Item
          <input name="requestedItem" placeholder="e.g. 27-inch 4K Monitor, Ergonomic Keyboard, Laptop Upgrade" required />
        </label>
        <label>Business Justification / Reason
          <textarea name="reason" placeholder="Please explain why you need this hardware..." rows="3" required></textarea>
        </label>
        <button style="margin-top:12px; background:#38bdf8; color:#0c2d48;">Submit Request</button>
      </form>`);
    document.getElementById("req-form").onsubmit = async (ev) => {
      ev.preventDefault();
      const f = Object.fromEntries(new FormData(ev.target).entries());
      try {
        await api("/api/asset-requests", { method: "POST", body: JSON.stringify(f) });
        modal.hidden = true;
        renderMyRequests();
      } catch (ex) { alert(ex.message); }
    };
  };
}

async function renderMyTickets() {
  const el = document.getElementById("view-tickets");
  const data = await api("/api/tickets/me?size=50");
  const myTickets = data.content || [];

  el.innerHTML = `
    <div class="toolbar">
      <button id="t-add" style="background:#38bdf8; color:#0c2d48;">+ Report Technical Issue</button>
    </div>
    <table>
      <thead><tr><th>Ticket #</th><th>Title</th><th>Priority</th><th>Status</th><th>SLA Status</th><th>Asset Tag</th><th>Submitted</th></tr></thead>
      <tbody>
        ${myTickets.length === 0 ? '<tr><td colspan="7" class="muted">You have not raised any support tickets.</td></tr>' : myTickets.map((t) => `
          <tr>
            <td><strong>${t.ticketNumber}</strong></td>
            <td>${t.title}</td>
            <td>${badge(t.priority)}</td>
            <td>${badge(t.status)}</td>
            <td><span class="badge ${t.slaStatus.toLowerCase()}">${t.slaStatus.replace('_', ' ')}</span></td>
            <td>${t.assetTag || "-"}</td>
            <td>${new Date(t.createdAt).toLocaleDateString()}</td>
          </tr>`).join("")}
      </tbody>
    </table>`;

  document.getElementById("t-add").onclick = () => {
    openModal(`
      <h3>Report an Issue / New Ticket</h3>
      <form id="t-form">
        <label>Issue Title<input name="title" placeholder="e.g. Laptop display flickering, Wi-Fi connectivity error" required /></label>
        <label>Description<textarea name="description" placeholder="Describe the problem in detail..." rows="3" required></textarea></label>
        <label>Priority
          <select name="priority"><option>LOW</option><option selected>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select>
        </label>
        <label>Asset ID (Optional)<input name="assetId" placeholder="Asset ID (if related to specific hardware)" /></label>
        <button style="margin-top:12px; background:#38bdf8; color:#0c2d48;">Submit Ticket</button>
      </form>`);
    document.getElementById("t-form").onsubmit = async (ev) => {
      ev.preventDefault();
      const f = Object.fromEntries(new FormData(ev.target).entries());
      if (f.assetId) f.assetId = Number(f.assetId); else delete f.assetId;
      try {
        await api("/api/tickets", { method: "POST", body: JSON.stringify(f) });
        modal.hidden = true;
        renderMyTickets();
      } catch (ex) { alert(ex.message); }
    };
  };
}

async function renderMySoftware() {
  const el = document.getElementById("view-software");
  const data = await api("/api/software/me");
  el.innerHTML = `
    <div class="toolbar">
      <p class="muted" style="margin:0; align-self:center;">Company licensed software available for employees.</p>
    </div>
    <table>
      <thead><tr><th>Software Application</th><th>Available Seats</th><th>Validity / Expiration</th></tr></thead>
      <tbody>
        ${data.length === 0 ? '<tr><td colspan="3" class="muted">No software licenses assigned to you.</td></tr>' : data.map((s) => `
          <tr>
            <td><strong>${s.softwareName}</strong></td>
            <td>Assigned</td>
            <td>${new Date(s.assignedAt).toLocaleDateString()}</td>
          </tr>`).join("")}
      </tbody>
    </table>`;
}

async function renderNotifications() {
  const el = document.getElementById("view-notifications");
  const data = await api("/api/notifications/me?size=50");
  el.innerHTML = data.content.map((n) => `
    <div class="stat" style="margin-bottom:12px; display:flex; justify-content:space-between; align-items:center;">
      <div>
        <p style="margin:0 0 4px 0;"><strong>${n.message}</strong></p>
        <p class="muted" style="margin:0; font-size:12px;">${n.createdAt} · ${n.read ? "read" : "unread"}</p>
      </div>
      ${n.read ? "" : `<button class="ghost" data-read="${n.id}">Mark Read</button>`}
    </div>`).join("") || "<p class='muted'>No notifications right now.</p>";

  document.querySelectorAll("[data-read]").forEach((b) => b.onclick = async () => {
    await api("/api/notifications/" + b.dataset.read + "/read", { method: "POST" });
    renderNotifications();
  });
}

async function renderMyProfile() {
  const el = document.getElementById("view-profile");
  let emp = null;
  try {
    if (user.employeeId) {
      emp = await api("/api/employees/me");
    }
  } catch (e) {}

  el.innerHTML = `
    <div style="max-width: 650px;">
      <div class="card" style="padding: 24px; margin-bottom: 24px;">
        <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom: 16px;">
          <div>
            <h2 style="margin:0 0 4px 0;">${emp ? `${emp.firstName} ${emp.lastName}` : user.fullName}</h2>
            <span class="badge AVAILABLE">${emp && emp.active ? 'ACTIVE EMPLOYEE' : 'ACTIVE'}</span>
          </div>
          <span style="font-size: 32px;">👤</span>
        </div>
        <table style="border:none;">
          <tbody>
            <tr><td style="width:140px; color:var(--muted);">Employee ID</td><td><strong>${emp ? emp.employeeCode : "-"}</strong></td></tr>
            <tr><td style="color:var(--muted);">Department</td><td>${emp ? emp.department || "-" : "-"}</td></tr>
            <tr><td style="color:var(--muted);">Designation</td><td>${emp ? emp.designation || "-" : "-"}</td></tr>
            <tr><td style="color:var(--muted);">Work Email</td><td>${emp ? emp.email : "-"}</td></tr>
            <tr><td style="color:var(--muted);">Phone</td><td>${emp ? emp.phone || "-" : "-"}</td></tr>
            <tr><td style="color:var(--muted);">Username</td><td><code>${user.username}</code></td></tr>
          </tbody>
        </table>
      </div>

      <div class="card" style="padding: 24px;">
        <h3 style="margin-top:0;">Change Password</h3>
        <p class="muted" style="font-size:14px;">Update your login password for the Employee Portal.</p>
        <form id="change-pwd-form">
          <label>New Password (min 6 characters)
            <input name="newPassword" type="password" placeholder="Enter new secure password" minlength="6" required />
          </label>
          <label>Confirm Password
            <input name="confirmPassword" type="password" placeholder="Re-enter new password" minlength="6" required />
          </label>
          <button style="margin-top:14px; background:#38bdf8; color:#0c2d48;">Update Password</button>
        </form>
      </div>
    </div>
  `;

  document.getElementById("change-pwd-form").onsubmit = async (ev) => {
    ev.preventDefault();
    const f = Object.fromEntries(new FormData(ev.target).entries());
    if (f.newPassword !== f.confirmPassword) {
      alert("New password and confirm password do not match!");
      return;
    }
    if (!emp) {
      alert("Employee record not found. Unable to update password.");
      return;
    }
    try {
      await api("/api/employees/me", {
        method: "PUT",
        body: JSON.stringify({
          employeeCode: emp.employeeCode,
          firstName: emp.firstName,
          lastName: emp.lastName,
          email: emp.email,
          department: emp.department,
          designation: emp.designation,
          phone: emp.phone,
          username: emp.username,
          password: f.newPassword
        })
      });
      alert("Password updated successfully! Please use your new password on your next login.");
      ev.target.reset();
    } catch (ex) { alert(ex.message); }
  };
}

bindNav();
loadView("assets");
