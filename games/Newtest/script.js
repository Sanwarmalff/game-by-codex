let score = 0;
const scoreDisplay = document.getElementById('score');
const target = document.getElementById('target');
const gameArea = document.querySelector('.game-area');

const emojis = ['🎯', '👾', '🚀', '💎', '🔥', '👑'];

function moveTarget() {
    // Game area ki width aur height nikalna
    const areaWidth = gameArea.clientWidth;
    const areaHeight = gameArea.clientHeight;
    
    // Random position calculate karna (emoji ka size minus karke taaki bahar na jaye)
    const randomX = Math.floor(Math.random() * (areaWidth - 50));
    const randomY = Math.floor(Math.random() * (areaHeight - 50));
    
    // Random emoji select karna
    const randomEmoji = emojis[Math.floor(Math.random() * emojis.length)];
    
    // Position aur text update karna
    target.style.left = randomX + 'px';
    target.style.top = randomY + 'px';
    target.textContent = randomEmoji;
}

// Click event handler
target.addEventListener('click', () => {
    score++;
    scoreDisplay.textContent = score;
    moveTarget();
});

// Mobile screens par touch smooth karne ke liye
target.addEventListener('touchstart', (e) => {
    e.preventDefault(); // Double tap zoom rokne ke liye
    score++;
    scoreDisplay.textContent = score;
    moveTarget();
});

// Game start hone par pehli baar move karna
moveTarget();
