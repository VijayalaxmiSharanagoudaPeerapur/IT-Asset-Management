document.addEventListener('DOMContentLoaded', () => {
    const aiWidget = document.getElementById('ai-chat-widget');
    const aiToggle = document.getElementById('ai-toggle');
    const aiHeader = document.getElementById('ai-header');
    const aiMessages = document.getElementById('ai-messages');
    const aiInput = document.getElementById('ai-input');
    const aiSend = document.getElementById('ai-send');

    if (!aiWidget) return;

    aiHeader.addEventListener('click', () => {
        aiWidget.classList.toggle('collapsed');
        aiToggle.innerHTML = aiWidget.classList.contains('collapsed') ? '&uarr;' : '&darr;';
    });

    aiSend.addEventListener('click', sendMessage);
    aiInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendMessage();
    });

    async function sendMessage() {
        const text = aiInput.value.trim();
        if (!text) return;

        appendMessage(text, 'user');
        aiInput.value = '';
        
        const loadingId = appendLoading();
        try {
            const token = localStorage.getItem('itasm_token') || localStorage.getItem('jwt_token');
            const res = await fetch('/api/ai/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify({ prompt: text })
            });

            removeLoading(loadingId);

            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                appendMessage(err.message || 'Error connecting to AI service.', 'error');
                return;
            }

            const data = await res.json();
            appendMessage(data.response, 'ai');
        } catch (e) {
            removeLoading(loadingId);
            appendMessage('Network error. Please try again later.', 'error');
        }
    }

    function appendMessage(text, sender) {
        const div = document.createElement('div');
        div.className = 'message ' + sender;
        div.textContent = text;
        aiMessages.appendChild(div);
        aiMessages.scrollTop = aiMessages.scrollHeight;
    }

    function appendLoading() {
        const id = 'loading-' + Date.now();
        const div = document.createElement('div');
        div.id = id;
        div.className = 'message ai loading';
        div.textContent = 'Thinking...';
        aiMessages.appendChild(div);
        aiMessages.scrollTop = aiMessages.scrollHeight;
        return id;
    }

    function removeLoading(id) {
        const el = document.getElementById(id);
        if (el) el.remove();
    }
});
