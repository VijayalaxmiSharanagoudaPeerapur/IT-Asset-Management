document.getElementById("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const err = document.getElementById("login-error");
  err.hidden = true;
  const form = new FormData(e.target);
  try {
    const data = await api("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username: form.get("username"), password: form.get("password") })
    });
    localStorage.setItem("itasm_token", data.token);
    localStorage.setItem("itasm_user", JSON.stringify(data));
    location.href = "/app.html";
  } catch (ex) {
    err.hidden = false;
    err.textContent = ex.message;
  }
});
