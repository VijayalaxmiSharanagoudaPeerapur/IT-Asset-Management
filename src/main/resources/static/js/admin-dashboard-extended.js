// Extensions for Enterprise Features

document.addEventListener('DOMContentLoaded', () => {
    let assetChartInstance = null;
    let ticketChartInstance = null;

    // Analytics View
    window.renderAnalytics = async () => {
        const sec = document.getElementById('view-analytics');
        if (!sec || sec.hidden) return;

        const assetContainer = document.getElementById('assetStatusChart').parentElement;
        const ticketContainer = document.getElementById('ticketPriorityChart').parentElement;

        try {
            const [data, stats, perf] = await Promise.all([
                api('/api/dashboard/analytics'),
                api('/api/dashboard/stats'),
                api('/api/dashboard/performance').catch(() => null)
            ]);
            
            // Populate metrics
            const empEl = document.getElementById('metric-employees');
            if (empEl) empEl.textContent = stats.totalEmployees;
            const astEl = document.getElementById('metric-assets');
            if (astEl) astEl.textContent = stats.totalAssets;

            // Populate live measured performance metrics
            if (perf) {
                const reqEl = document.getElementById('metric-requests');
                if (reqEl) reqEl.textContent = perf.totalRequests.toLocaleString();

                const latEl = document.getElementById('metric-avg-latency');
                if (latEl) latEl.textContent = perf.measuredAvgResponseTimeMs + ' ms';

                const errEl = document.getElementById('metric-error-rate');
                if (errEl) errEl.textContent = perf.errorRatePercent + '% (' + perf.totalErrors + ' errs)';

                const upEl = document.getElementById('metric-uptime');
                if (upEl) upEl.textContent = perf.uptimeFormatted;

                const memEl = document.getElementById('metric-memory');
                if (memEl) memEl.textContent = perf.jvmUsedMemoryMb + ' MB / ' + perf.jvmMaxMemoryMb + ' MB';

                const thrEl = document.getElementById('metric-threads');
                if (thrEl) thrEl.textContent = perf.activeThreads;

                // Endpoint performance breakdown table
                const epTbody = document.getElementById('endpoint-metrics-tbody');
                if (epTbody && perf.topEndpoints) {
                    if (perf.topEndpoints.length === 0) {
                        epTbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--muted); padding:16px;">No endpoint calls recorded yet.</td></tr>';
                    } else {
                        epTbody.innerHTML = perf.topEndpoints.map(e => `
                            <tr>
                                <td><span class="role-badge ${e.method === 'GET' ? 'admin' : (e.method === 'POST' ? 'employee' : 'it-support')}" style="font-size:11px;">${e.method}</span></td>
                                <td><code>${e.endpoint}</code></td>
                                <td><strong>${e.requests.toLocaleString()}</strong></td>
                                <td style="color:${e.avgResponseTimeMs < 100 ? '#28a745' : (e.avgResponseTimeMs < 200 ? 'var(--warn)' : 'var(--danger)')}; font-weight:600;">
                                    ${e.avgResponseTimeMs} ms
                                </td>
                                <td>${e.maxResponseTimeMs} ms</td>
                                <td><span class="${e.errors > 0 ? 'badge-danger' : 'muted'}">${e.errors}</span></td>
                            </tr>
                        `).join('');
                    }
                }
            }
            
            // Asset Chart
            const assetValues = Object.values(data.assetStatusDistribution);
            const totalAssets = assetValues.reduce((a, b) => a + b, 0);
            
            if (totalAssets === 0) {
                assetContainer.innerHTML = '<div style="display:flex; height:100%; align-items:center; justify-content:center; color:var(--muted)">No asset data available</div>';
            } else {
                if (!document.getElementById('assetStatusChart')) {
                    assetContainer.innerHTML = '<canvas id="assetStatusChart"></canvas>';
                }
                const assetCtx = document.getElementById('assetStatusChart');
                if (assetCtx && window.Chart) {
                    if (assetChartInstance) assetChartInstance.destroy();
                    assetChartInstance = new Chart(assetCtx, {
                        type: 'pie',
                        data: {
                            labels: Object.keys(data.assetStatusDistribution),
                            datasets: [{
                                data: assetValues,
                                backgroundColor: ['#28a745', '#007bff', '#ffc107', '#dc3545', '#17a2b8', '#6c757d']
                            }]
                        },
                        options: { maintainAspectRatio: false, plugins: { legend: { position: 'right', labels: { color: '#e2e8f0' } } } }
                    });
                }
            }

            // Ticket Chart
            const ticketValues = Object.values(data.ticketPriorityDistribution);
            const totalTickets = ticketValues.reduce((a, b) => a + b, 0);

            if (totalTickets === 0) {
                ticketContainer.innerHTML = '<div style="display:flex; height:100%; align-items:center; justify-content:center; color:var(--muted)">No ticket data available</div>';
            } else {
                if (!document.getElementById('ticketPriorityChart')) {
                    ticketContainer.innerHTML = '<canvas id="ticketPriorityChart"></canvas>';
                }
                const ticketCtx = document.getElementById('ticketPriorityChart');
                if (ticketCtx && window.Chart) {
                    if (ticketChartInstance) ticketChartInstance.destroy();
                    ticketChartInstance = new Chart(ticketCtx, {
                        type: 'bar',
                        data: {
                            labels: Object.keys(data.ticketPriorityDistribution),
                            datasets: [{
                                label: 'Tickets',
                                data: ticketValues,
                                backgroundColor: '#6b46c1'
                            }]
                        },
                        options: { 
                            maintainAspectRatio: false, 
                            scales: { 
                                y: { ticks: { precision: 0, color: '#e2e8f0' }, grid: { color: 'rgba(255,255,255,0.1)' } },
                                x: { ticks: { color: '#e2e8f0' }, grid: { display: false } }
                            },
                            plugins: { legend: { display: false } }
                        }
                    });
                }
            }
        } catch (e) {
            console.error('Failed to load analytics', e);
            assetContainer.innerHTML = `<div style="color:var(--danger); padding:16px;">Failed to load analytics: ${e.message}</div>`;
            ticketContainer.innerHTML = `<div style="color:var(--danger); padding:16px;">Failed to load analytics: ${e.message}</div>`;
        }
    };

    // Global Search
    const searchInput = document.getElementById('global-search-input');
    const searchResults = document.getElementById('global-search-results');
    let searchTimeout = null;

    if (searchInput && searchResults) {
        searchInput.addEventListener('input', (e) => {
            clearTimeout(searchTimeout);
            const query = e.target.value.trim();
            if (query.length < 2) {
                searchResults.style.display = 'none';
                return;
            }

            searchTimeout = setTimeout(async () => {
                try {
                    const results = await api('/api/search?q=' + encodeURIComponent(query));
                    if (results.length === 0) {
                        searchResults.innerHTML = '<div style="padding:12px;color:var(--muted)">No results found.</div>';
                    } else {
                        searchResults.innerHTML = results.map(r => `
                            <div style="padding: 12px; border-bottom: 1px solid var(--border); cursor: pointer;" onclick="alert('Clicked ${r.type} ${r.id}')">
                                <span class="role-badge ${r.type.toLowerCase()}">${r.type}</span>
                                <strong style="margin-left: 8px;">${r.title}</strong>
                                <div style="font-size: 12px; color: var(--muted); margin-top: 4px;">Status: ${r.status}</div>
                            </div>
                        `).join('');
                    }
                    searchResults.style.display = 'block';
                } catch (e) {
                    console.error('Search failed', e);
                }
            }, 300);
        });

        document.addEventListener('click', (e) => {
            if (!searchInput.contains(e.target) && !searchResults.contains(e.target)) {
                searchResults.style.display = 'none';
            }
        });
    }

    // Audit Log rendering
    window.renderAuditLogs = async () => {
        const sec = document.getElementById('view-audit');
        try {
            const data = await api('/api/audit?size=50');
            sec.innerHTML = `
                <h2>Global Audit Logs</h2>
                <div class="card" style="overflow-x:auto;">
                    <table>
                        <thead>
                            <tr>
                                <th>Timestamp</th>
                                <th>Entity</th>
                                <th>ID</th>
                                <th>Action</th>
                                <th>Old Value</th>
                                <th>New Value</th>
                                <th>User</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${data.content.map(log => `
                                <tr>
                                    <td>${new Date(log.timestamp).toLocaleString()}</td>
                                    <td>${log.entityType}</td>
                                    <td>${log.entityId}</td>
                                    <td><span class="role-badge ${log.action.toLowerCase()}">${log.action}</span></td>
                                    <td class="muted">${log.oldValue || '-'}</td>
                                    <td>${log.newValue || '-'}</td>
                                    <td>${log.performedBy ? log.performedBy.username : 'SYSTEM'}</td>
                                </tr>
                            `).join('')}
                            ${data.content.length === 0 ? '<tr><td colspan="7">No audit logs found.</td></tr>' : ''}
                        </tbody>
                    </table>
                </div>
            `;
        } catch(e) {
            sec.innerHTML = `<div class="card" style="color:red">Failed to load audit logs: ${e.message}</div>`;
        }
    };

    // Security Events rendering
    window.renderSecurity = async () => {
        const sec = document.getElementById('view-security');
        try {
            const data = await api('/api/security-events');
            sec.innerHTML = `
                <h2>Security Monitoring & Events</h2>
                <div class="card" style="overflow-x:auto;">
                    <table>
                        <thead>
                            <tr>
                                <th>Timestamp</th>
                                <th>Event Type</th>
                                <th>Username</th>
                                <th>IP Address</th>
                                <th>Details</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${data.map(log => `
                                <tr>
                                    <td>${new Date(log.timestamp).toLocaleString()}</td>
                                    <td><span class="role-badge ${log.eventType.toLowerCase()}">${log.eventType}</span></td>
                                    <td>${log.username || '-'}</td>
                                    <td>${log.ipAddress || '-'}</td>
                                    <td>${log.details || '-'}</td>
                                </tr>
                            `).join('')}
                            ${data.length === 0 ? '<tr><td colspan="5">No security events found.</td></tr>' : ''}
                        </tbody>
                    </table>
                </div>
            `;
        } catch(e) {
            sec.innerHTML = `<div class="card" style="color:red">Failed to load security events: ${e.message}</div>`;
        }
    };

    // Procurement rendering
    window.renderProcurement = async () => {
        const sec = document.getElementById('view-procurement');
        try {
            const vendors = await api('/api/procurement/vendors');
            const orders = await api('/api/procurement/orders');
            
            sec.innerHTML = `
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
                    <h2>Procurement Management</h2>
                    <div>
                        <button class="btn" onclick="openCreateVendorModal()">New Vendor</button>
                        <button class="btn primary" onclick="openCreatePoModal()">New PO</button>
                    </div>
                </div>
                
                <div style="display:grid; grid-template-columns: 1fr 2fr; gap: 24px;">
                    <div class="card">
                        <h3>Vendors</h3>
                        <ul>
                            ${vendors.map(v => `<li style="padding:8px 0; border-bottom:1px solid var(--border)"><strong>${v.name}</strong> <br><span class="muted">${v.contactPerson || ''} - ${v.email || ''}</span></li>`).join('')}
                            ${vendors.length === 0 ? '<li class="muted">No vendors registered.</li>' : ''}
                        </ul>
                    </div>
                    
                    <div class="card" style="overflow-x:auto;">
                        <h3>Purchase Orders</h3>
                        <table>
                            <thead>
                                <tr>
                                    <th>PO Number</th>
                                    <th>Vendor</th>
                                    <th>Qty</th>
                                    <th>Cost</th>
                                    <th>Status</th>
                                    <th>Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${orders.map(o => `
                                    <tr>
                                        <td>${o.poNumber}</td>
                                        <td>${o.vendor ? o.vendor.name : 'Unknown'}</td>
                                        <td>${o.quantity}</td>
                                        <td>$${o.totalCost ? o.totalCost.toFixed(2) : '0.00'}</td>
                                        <td><span class="role-badge ${o.status.toLowerCase()}">${o.status}</span></td>
                                        <td>
                                            ${o.status === 'PENDING' ? `<button class="ghost" onclick="markPoDelivered(${o.id})">Mark Delivered</button>` : 'Received'}
                                        </td>
                                    </tr>
                                `).join('')}
                                ${orders.length === 0 ? '<tr><td colspan="6">No purchase orders found.</td></tr>' : ''}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
        } catch(e) {
            sec.innerHTML = `<div class="card" style="color:red">Failed to load procurement data: ${e.message}</div>`;
        }
    };

    window.markPoDelivered = async (id) => {
        if(!confirm('Mark this PO as delivered? This will automatically generate assets in PROCURED status.')) return;
        try {
            await api(`/api/procurement/orders/${id}/deliver`, { method: 'POST' });
            alert('PO Marked Delivered & Assets Generated!');
            renderProcurement();
        } catch(e) {
            alert('Failed: ' + e.message);
        }
    };

    window.openCreateVendorModal = () => {
        openModal(`
            <h3>Register New Vendor</h3>
            <form id="vendor-form">
                <label>Vendor Name<input name="name" required /></label>
                <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
                    <label>Contact Person<input name="contactPerson" /></label>
                    <label>Email<input name="email" type="email" /></label>
                </div>
                <label>Phone<input name="phone" /></label>
                <label>Address<input name="address" /></label>
                <button style="margin-top:14px;">Save Vendor</button>
            </form>
        `);
        document.getElementById('vendor-form').onsubmit = async (e) => {
            e.preventDefault();
            const f = Object.fromEntries(new FormData(e.target));
            try {
                await api('/api/procurement/vendors', { method: 'POST', body: JSON.stringify(f) });
                modal.hidden = true;
                renderProcurement();
            } catch (ex) { alert(ex.message); }
        };
    };

    window.openCreatePoModal = async () => {
        try {
            const vendors = await api('/api/procurement/vendors');
            if (vendors.length === 0) {
                alert('Please create a vendor first.');
                return;
            }
            openModal(`
                <h3>Create Purchase Order</h3>
                <form id="po-form">
                    <label>PO Number<input name="poNumber" required placeholder="PO-1001" /></label>
                    <label>Vendor
                        <select name="vendorId" required>
                            <option value="">-- Select Vendor --</option>
                            ${vendors.map(v => `<option value="${v.id}">${v.name}</option>`).join('')}
                        </select>
                    </label>
                    <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
                        <label>Quantity<input type="number" name="quantity" required min="1" value="1" /></label>
                        <label>Total Cost ($)<input type="number" step="0.01" name="totalCost" required /></label>
                    </div>
                    <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
                        <label>Order Date<input type="date" name="orderDate" required /></label>
                        <label>Expected Delivery<input type="date" name="expectedDelivery" /></label>
                    </div>
                    <label>Notes<input name="notes" /></label>
                    <button style="margin-top:14px;">Create PO</button>
                </form>
            `);
            document.getElementById('po-form').onsubmit = async (e) => {
                e.preventDefault();
                const f = Object.fromEntries(new FormData(e.target));
                f.vendor = { id: Number(f.vendorId) };
                delete f.vendorId;
                f.quantity = Number(f.quantity);
                f.totalCost = Number(f.totalCost);
                if (!f.expectedDelivery) delete f.expectedDelivery;
                try {
                    await api('/api/procurement/orders', { method: 'POST', body: JSON.stringify(f) });
                    modal.hidden = true;
                    renderProcurement();
                } catch (ex) { alert(ex.message); }
            };
        } catch (ex) { alert(ex.message); }
    };

    // Management Reports rendering
    window.downloadReportCsv = async (endpoint, filename) => {
        try {
            const blob = await apiBlob(endpoint);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
        } catch (e) {
            alert('Failed to export CSV: ' + e.message);
        }
    };

    window.renderReports = async () => {
        const sec = document.getElementById('view-reports');
        if (!sec) return;
        sec.innerHTML = '<div class="card" style="text-align:center; padding:32px; color:var(--muted)">Generating Executive Management Report...</div>';

        try {
            const r = await api('/api/reports/management');
            const ast = r.assetSummary || {};
            const tkt = r.ticketSummary || {};
            const sw = r.softwareSummary || {};
            const war = r.warrantySummary || {};
            const rsk = r.riskSummary || {};

            const formattedGenTime = r.generatedAt ? new Date(r.generatedAt).toLocaleString() : new Date().toLocaleString();
            const totalValFormatted = (ast.totalValuation != null) ? Number(ast.totalValuation).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '0.00';
            const avgValFormatted = (ast.averageValue != null) ? Number(ast.averageValue).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '0.00';

            const deptEntries = Object.entries(ast.assetsByDepartment || {});
            const typeEntries = Object.entries(ast.assetsByType || {});
            const statusEntries = Object.entries(ast.assetsByStatus || {});
            const priorityEntries = Object.entries(tkt.ticketsByPriority || {});

            // Valuation card text logic
            let valuationHtml = '';
            if (!ast.totalAssets || ast.totalAssets === 0) {
                valuationHtml = `
                    <div class="stat">
                        Total Asset Valuation
                        <strong style="color:var(--muted);">₹0.00</strong>
                        <span class="muted" style="font-size:12px; display:block; margin-top:4px;">No assets in inventory</span>
                    </div>`;
            } else if (!ast.valuationAvailable || (ast.pricedAssetsCount || 0) === 0) {
                valuationHtml = `
                    <div class="stat">
                        Total Asset Valuation
                        <strong style="color:var(--warn); font-size:20px;">Data Unavailable</strong>
                        <span class="muted" style="font-size:12px; display:block; margin-top:4px;">No purchase price recorded for ${ast.totalAssets} asset(s)</span>
                    </div>`;
            } else if ((ast.unpricedAssetsCount || 0) > 0) {
                valuationHtml = `
                    <div class="stat">
                        Total Asset Valuation
                        <strong style="color:var(--accent);">₹${totalValFormatted}*</strong>
                        <span class="muted" style="font-size:12px; display:block; margin-top:4px;">Avg: ₹${avgValFormatted} (${ast.pricedAssetsCount}/${ast.totalAssets} priced &bull; ${ast.unpricedAssetsCount} unpriced)</span>
                    </div>`;
            } else {
                valuationHtml = `
                    <div class="stat">
                        Total Asset Valuation
                        <strong style="color:var(--accent);">₹${totalValFormatted}</strong>
                        <span class="muted" style="font-size:12px; display:block; margin-top:4px;">Avg: ₹${avgValFormatted} (All ${ast.totalAssets} assets priced)</span>
                    </div>`;
            }

            // Software license utilization card text logic
            let softwareCardHtml = '';
            const totalLicenses = sw.totalLicenses || 0;
            if (totalLicenses === 0) {
                softwareCardHtml = `
                    <div class="stat">
                        License Utilization
                        <strong style="color:var(--muted); font-size:20px;">No Licenses</strong>
                        <span class="muted" style="font-size:12px; display:block; margin-top:4px;">0 licenses registered in inventory</span>
                    </div>`;
            } else {
                softwareCardHtml = `
                    <div class="stat">
                        License Utilization
                        <strong>${sw.overallUtilizationRate || 0}%</strong>
                        <span class="muted" style="font-size:12px; display:block; margin-top:4px;">${sw.allocatedSeats || 0} / ${sw.totalSeats || 0} Seats In Use (${totalLicenses} title${totalLicenses > 1 ? 's' : ''})</span>
                    </div>`;
            }

            sec.innerHTML = `
                <div class="report-header">
                    <div>
                        <div class="print-only report-org">Smart IT Asset &amp; Service Management System (ITASM)</div>
                        <h2>Executive Management &amp; Operational Report</h2>
                        <div class="muted report-meta" style="font-size:13px; margin-top:4px;">
                            Generated at: <strong>${formattedGenTime}</strong> &bull; Authorized by: <strong>Administrator</strong> &bull; Scope: <strong>Complete Organization Audit</strong>
                        </div>
                    </div>
                    <div class="report-actions no-print" style="display:flex; gap:8px; flex-wrap:wrap;">
                        <button class="btn primary" onclick="window.print()" title="Print executive report or save as PDF">
                            🖨️ Print / Save PDF
                        </button>
                        <button class="btn ghost" onclick="downloadReportCsv('/api/reports/assets-valuation/export', 'asset-valuation-report.csv')">
                            📥 Assets CSV
                        </button>
                        <button class="btn ghost" onclick="downloadReportCsv('/api/reports/tickets-sla/export', 'tickets-sla-report.csv')">
                            📥 Tickets &amp; SLA CSV
                        </button>
                        <button class="btn ghost" onclick="downloadReportCsv('/api/reports/software-licenses/export', 'software-licenses-report.csv')">
                            📥 Software CSV
                        </button>
                    </div>
                </div>

                <div class="stats report-stats" style="margin-bottom:24px;">
                    ${valuationHtml}
                    <div class="stat">
                        Total Tracked Assets
                        <strong>${ast.totalAssets || 0}</strong>
                        <span class="muted" style="font-size:12px; display:block; margin-top:4px;">${ast.pricedAssetsCount || 0} priced &bull; ${ast.unpricedAssetsCount || 0} unpriced</span>
                    </div>
                    <div class="stat">
                        Service Desk SLA Breaches
                        <strong style="color:${(tkt.slaBreachedTickets || 0) > 0 ? 'var(--danger)' : '#28a745'};">
                            ${tkt.slaBreachedTickets || 0}
                        </strong>
                        <span class="muted" style="font-size:12px; display:block; margin-top:4px;">Breach Rate: ${tkt.slaBreachRatePercent || 0}% (${tkt.openTickets || 0} open)</span>
                    </div>
                    ${softwareCardHtml}
                    <div class="stat">
                        Warranty Health
                        <strong style="color:${(war.expiredCount || 0) > 0 ? 'var(--warn)' : '#28a745'};">
                            ${war.activeWarrantyCount || 0} Active
                        </strong>
                        <span class="muted" style="font-size:12px; display:block; margin-top:4px;">${war.expiringIn30Days || 0} Expiring Soon &bull; ${war.expiredCount || 0} Expired</span>
                    </div>
                    <div class="stat">
                        High-Risk Hardware
                        <strong style="color:${(rsk.highRiskCount || 0) > 0 ? 'var(--danger)' : '#28a745'};">
                            ${rsk.highRiskCount || 0}
                        </strong>
                        <span class="muted" style="font-size:12px; display:block; margin-top:4px;">${rsk.criticalRiskCount || 0} Critical Risk (Score &ge; 70)</span>
                    </div>
                </div>

                <div class="report-row-grid">
                    <div class="card report-card">
                        <h3>Asset Valuation &amp; Department Allocation</h3>
                        <div style="overflow-x:auto; margin-top:10px;">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Department / Unit</th>
                                        <th>Asset Count</th>
                                        <th>Allocation Share</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${deptEntries.map(([dept, count]) => {
                                        const pct = ast.totalAssets > 0 ? Math.round((count / ast.totalAssets) * 100) : 0;
                                        return `
                                            <tr>
                                                <td><strong>${dept}</strong></td>
                                                <td>${count}</td>
                                                <td>
                                                    <div style="display:flex; align-items:center; gap:8px;">
                                                        <div class="print-progress-bg" style="flex:1; background:var(--line); height:6px; border-radius:3px; overflow:hidden;">
                                                            <div class="print-progress-bar" style="background:var(--accent); width:${pct}%; height:100%;"></div>
                                                        </div>
                                                        <span style="font-size:12px; min-width:32px;">${pct}%</span>
                                                    </div>
                                                </td>
                                            </tr>
                                        `;
                                    }).join('')}
                                    ${deptEntries.length === 0 ? '<tr><td colspan="3" class="muted">No department asset data.</td></tr>' : ''}
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <div class="card report-card">
                        <h3>Asset Classification &amp; Lifecycle Status</h3>
                        <div style="display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-top:10px;">
                            <div>
                                <h4 style="font-size:13px; color:var(--muted); margin-bottom:8px;">Status Breakdown</h4>
                                <table>
                                    <thead><tr><th>Status</th><th>Count</th></tr></thead>
                                    <tbody>
                                        ${statusEntries.map(([st, cnt]) => `
                                            <tr>
                                                <td><span class="role-badge ${st.toLowerCase()}">${st}</span></td>
                                                <td><strong>${cnt}</strong></td>
                                            </tr>
                                        `).join('')}
                                    </tbody>
                                </table>
                            </div>
                            <div>
                                <h4 style="font-size:13px; color:var(--muted); margin-bottom:8px;">Category Distribution</h4>
                                <table>
                                    <thead><tr><th>Hardware Type</th><th>Count</th></tr></thead>
                                    <tbody>
                                        ${typeEntries.map(([tp, cnt]) => `
                                            <tr>
                                                <td>${tp}</td>
                                                <td><strong>${cnt}</strong></td>
                                            </tr>
                                        `).join('')}
                                        ${typeEntries.length === 0 ? '<tr><td colspan="2" class="muted">No type data.</td></tr>' : ''}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="report-row-grid">
                    <div class="card report-card">
                        <h3>Support Ticket &amp; SLA Compliance Overview</h3>
                        <div style="display:grid; grid-template-columns:repeat(4, 1fr); gap:8px; margin:12px 0 16px;">
                            <div style="background:rgba(255,255,255,0.03); padding:10px; border-radius:6px; text-align:center;">
                                <div class="muted" style="font-size:11px;">OPEN</div>
                                <strong style="font-size:18px; color:var(--accent);">${tkt.openTickets || 0}</strong>
                            </div>
                            <div style="background:rgba(255,255,255,0.03); padding:10px; border-radius:6px; text-align:center;">
                                <div class="muted" style="font-size:11px;">IN PROGRESS</div>
                                <strong style="font-size:18px; color:var(--warn);">${tkt.inProgressTickets || 0}</strong>
                            </div>
                            <div style="background:rgba(255,255,255,0.03); padding:10px; border-radius:6px; text-align:center;">
                                <div class="muted" style="font-size:11px;">RESOLVED</div>
                                <strong style="font-size:18px; color:#28a745;">${tkt.resolvedTickets || 0}</strong>
                            </div>
                            <div style="background:rgba(255,255,255,0.03); padding:10px; border-radius:6px; text-align:center;">
                                <div class="muted" style="font-size:11px;">CLOSED</div>
                                <strong style="font-size:18px;">${tkt.closedTickets || 0}</strong>
                            </div>
                        </div>

                        <h4 style="font-size:13px; color:var(--muted); margin-bottom:8px;">Severity &amp; Priority Breakdown</h4>
                        <table>
                            <thead><tr><th>Priority</th><th>Total Tickets</th></tr></thead>
                            <tbody>
                                ${priorityEntries.map(([pri, cnt]) => `
                                    <tr>
                                        <td><span class="role-badge ${pri.toLowerCase()}">${pri}</span></td>
                                        <td><strong>${cnt}</strong></td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>

                    <div class="card report-card">
                        <h3>Software License Utilization &amp; Compliance</h3>
                        <div style="overflow-x:auto; margin-top:10px;">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Application</th>
                                        <th>Allocated / Total</th>
                                        <th>Available</th>
                                        <th>Utilization</th>
                                        <th>Expiry</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${(sw.topLicenses || []).map(lic => `
                                        <tr>
                                            <td><strong>${lic.name}</strong></td>
                                            <td>${lic.allocatedSeats} / ${lic.totalSeats}</td>
                                            <td>${lic.availableSeats}</td>
                                            <td>
                                                <div style="display:flex; align-items:center; gap:6px;">
                                                    <div class="print-progress-bg" style="flex:1; background:var(--line); height:6px; border-radius:3px; overflow:hidden; min-width:40px;">
                                                        <div class="print-progress-bar" style="background:${lic.utilizationPercent >= 90 ? 'var(--danger)' : (lic.utilizationPercent >= 75 ? 'var(--warn)' : '#28a745')}; width:${Math.min(lic.utilizationPercent, 100)}%; height:100%;"></div>
                                                    </div>
                                                    <span style="font-size:12px;">${lic.utilizationPercent}%</span>
                                                </div>
                                            </td>
                                            <td>
                                                ${lic.expirationDate ? lic.expirationDate : '<span class="muted">Perpetual</span>'}
                                            </td>
                                        </tr>
                                    `).join('')}
                                    ${(!sw.topLicenses || sw.topLicenses.length === 0) ? '<tr><td colspan="5" class="muted" style="text-align:center; padding:18px;">No software license records registered. Licenses can be added via the Software Licenses module.</td></tr>' : ''}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <div class="card report-card" style="margin-bottom:24px;">
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
                        <h3>High-Risk Assets &amp; Predictive Maintenance Watchlist</h3>
                        <span class="badge ${rsk.highRiskCount > 0 ? 'danger' : 'success'}">
                            ${rsk.highRiskCount || 0} Assets Flagged (Score &ge; 30)
                        </span>
                    </div>
                    <div style="overflow-x:auto;">
                        <table>
                            <thead>
                                <tr>
                                    <th>Asset Tag</th>
                                    <th>Model</th>
                                    <th>Status</th>
                                    <th>Assigned Custodian</th>
                                    <th>Predictive Risk Score</th>
                                    <th>Risk Level</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${(rsk.topHighRiskAssets || []).map(a => `
                                    <tr>
                                        <td><strong><code>${a.assetTag}</code></strong></td>
                                        <td>${a.model}</td>
                                        <td><span class="role-badge ${a.status ? a.status.toLowerCase() : ''}">${a.status}</span></td>
                                        <td>${a.assignedEmployee || '<span class="muted">Unassigned</span>'}</td>
                                        <td><strong style="color:${a.riskScore >= 70 ? 'var(--danger)' : 'var(--warn)'}; font-size:14px;">${a.riskScore} / 100</strong></td>
                                        <td>
                                            <span class="badge ${a.riskScore >= 70 ? 'danger' : 'warn'}">
                                                ${a.riskScore >= 70 ? 'CRITICAL' : 'ELEVATED'}
                                            </span>
                                        </td>
                                    </tr>
                                `).join('')}
                                ${(!rsk.topHighRiskAssets || rsk.topHighRiskAssets.length === 0) ? '<tr><td colspan="6" style="text-align:center; padding:16px; color:#28a745;">No high-risk assets currently detected. All active hardware meets stability benchmarks.</td></tr>' : ''}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
        } catch (e) {
            console.error('Failed to load reports', e);
            sec.innerHTML = `<div class="card" style="color:var(--danger); padding:20px;">Failed to load management reports: ${e.message}</div>`;
        }
    };
});
