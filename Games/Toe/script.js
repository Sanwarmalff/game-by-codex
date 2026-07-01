const navToggle = document.querySelector('.nav-toggle');
const navLinks = document.querySelector('[data-nav-links]');
const year = document.querySelector('#year');
const cannedReplies = [
  'Sanjiai can train on your docs, website pages, FAQs, and product knowledge base.',
  'Yes. You can connect Slack, HubSpot, Salesforce, Zapier, webhooks, and email alerts.',
  'Pricing starts at $19/month, and the Growth plan includes automations and CRM sync.',
  'A human handoff can send the full transcript to your team whenever confidence is low.',
  'You can install the widget with a single script tag and customize it to match your brand.'
];

if (year) year.textContent = new Date().getFullYear();

navToggle?.addEventListener('click', () => {
  const isOpen = navLinks.classList.toggle('open');
  navToggle.setAttribute('aria-expanded', String(isOpen));
});

navLinks?.addEventListener('click', (event) => {
  if (event.target.matches('a')) {
    navLinks.classList.remove('open');
    navToggle?.setAttribute('aria-expanded', 'false');
  }
});

function addMessage(container, text, sender = 'bot') {
  const message = document.createElement('div');
  message.className = `message ${sender}`;
  message.textContent = text;
  container.appendChild(message);
  container.scrollTop = container.scrollHeight;
}

function getReply(text) {
  const normalized = text.toLowerCase();
  if (normalized.includes('price') || normalized.includes('cost') || normalized.includes('plan')) return cannedReplies[2];
  if (normalized.includes('slack') || normalized.includes('crm') || normalized.includes('connect')) return cannedReplies[1];
  if (normalized.includes('demo') || normalized.includes('book')) return 'Great choice. Share your work email and the sales team can schedule a tailored demo.';
  if (normalized.includes('handoff') || normalized.includes('human')) return cannedReplies[3];
  if (normalized.includes('install') || normalized.includes('widget')) return cannedReplies[4];
  return cannedReplies[Math.floor(Math.random() * cannedReplies.length)];
}

function wireChat(formId, inputId, messagesId) {
  const form = document.querySelector(formId);
  const input = document.querySelector(inputId);
  const messages = document.querySelector(messagesId);
  form?.addEventListener('submit', (event) => {
    event.preventDefault();
    const text = input.value.trim();
    if (!text) return;
    addMessage(messages, text, 'user');
    input.value = '';
    setTimeout(() => addMessage(messages, getReply(text), 'bot'), 450);
  });
}

wireChat('#heroChatForm', '#heroChatInput', '#heroMessages');
wireChat('#demoForm', '#demoInput', '#demoMessages');

document.querySelector('#signupForm')?.addEventListener('submit', (event) => {
  event.preventDefault();
  const email = document.querySelector('#emailInput').value.trim();
  const note = document.querySelector('#formNote');
  note.textContent = email ? `Thanks! We created a demo workspace invite for ${email}.` : '';
  event.target.reset();
});
