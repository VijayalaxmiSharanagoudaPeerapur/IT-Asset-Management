const API = "";

function token() {
  return localStorage.getItem("itasm_token");
}

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (!(options.body instanceof FormData)) {
    headers["Content-Type"] = headers["Content-Type"] || "application/json";
  }
  if (token()) headers.Authorization = "Bearer " + token();
  const res = await fetch(API + path, { ...options, headers });
  if (res.status === 401) {
    localStorage.clear();
    if (!location.pathname.endsWith("/") && !location.pathname.endsWith("index.html")) {
      location.href = "/";
    }
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const msg = data.message || "Request failed";
    const fields = data.fieldErrors ? " " + JSON.stringify(data.fieldErrors) : "";
    throw new Error(msg + fields);
  }
  return data;
}

async function apiBlob(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body && !(options.body instanceof FormData)) {
    headers["Content-Type"] = headers["Content-Type"] || "application/json";
  }
  if (token()) headers.Authorization = "Bearer " + token();
  const res = await fetch(API + path, { ...options, headers });
  if (res.status === 401) {
    localStorage.clear();
    location.href = "/";
  }
  if (!res.ok) {
    throw new Error("Download failed with status: " + res.status);
  }
  return res.blob();
}
