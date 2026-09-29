const form = document.querySelector('form');
const submitButton = form.querySelector('button');
const defaultButtonText = submitButton.firstChild;

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  submitButton.disabled = true;
  defaultButtonText.textContent = 'Сохраняем нору... ';

  const formData = new FormData(form);
  const payload = {
    nickname: formData.get('nickname'),
    shellColor: formData.get('shell-color'),
    email: formData.get('email'),
    password: formData.get('password')
  };

  try {
    const response = await fetch('/api/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || 'Не удалось зарегистрироваться.');

    form.reset();
    showMessage(result.message, 'success');
    openGame(payload.nickname);
  } catch (error) {
    showMessage(error.message, 'error');
  } finally {
    submitButton.disabled = false;
    defaultButtonText.textContent = 'Выкатиться в игру ';
  }
});

function showMessage(message, type) {
  let messageElement = document.querySelector('.form-message');
  if (!messageElement) {
    messageElement = document.createElement('p');
    messageElement.className = 'form-message';
    form.after(messageElement);
  }
  messageElement.className = `form-message ${type}`;
  messageElement.textContent = message;
}

const gameScreen = document.querySelector('.game-screen');
const gameBoard = document.querySelector('#game-board');
const player = document.querySelector('#player-beetle');
const scoreElement = document.querySelector('#score');
const bestScoreElement = document.querySelector('#best-score');
const itemsLayer = document.querySelector('#game-items');
const obstaclesLayer = document.querySelector('#game-obstacles');
const hint = document.querySelector('#game-hint');
const restartButton = document.querySelector('#restart-game');
const touchButtons = document.querySelectorAll('.touch-button');
const bestScoreKey = 'navoznik-best-score';
let gameFrame;
let gameState;

function openGame(nickname) {
  document.querySelector('.auth-shell').hidden = true;
  gameScreen.hidden = false;
  document.querySelector('#game-title').firstChild.textContent = `${nickname}, кати. Собирай. `;
  bestScoreElement.textContent = localStorage.getItem(bestScoreKey) || '0';
  startGame();
}

function startGame() {
  cancelAnimationFrame(gameFrame);
  gameState = { score: 0, playerX: 50, lastTime: 0, running: true, items: [], obstacles: [] };
  scoreElement.textContent = '0';
  hint.classList.remove('is-hidden');
  itemsLayer.replaceChildren();
  obstaclesLayer.replaceChildren();
  createGameObjects();
  gameBoard.focus();
  gameFrame = requestAnimationFrame(gameLoop);
}

function createGameObjects() {
  for (let index = 0; index < 8; index += 1) {
    const item = { x: 8 + Math.random() * 84, y: -10 - index * 15, speed: 0.018 + Math.random() * 0.012 };
    const element = document.createElement('span');
    element.className = 'game-leaf';
    element.textContent = '✦';
    itemsLayer.append(element);
    gameState.items.push({ ...item, element });
  }

  for (let index = 0; index < 4; index += 1) {
    const obstacle = { x: 8 + Math.random() * 84, y: -30 - index * 25, speed: 0.014 + Math.random() * 0.009 };
    const element = document.createElement('span');
    element.className = 'game-stone';
    obstaclesLayer.append(element);
    gameState.obstacles.push({ ...obstacle, element });
  }
}

function gameLoop(timestamp) {
  if (!gameState.running) return;
  const elapsed = Math.min(timestamp - gameState.lastTime || 16, 40);
  gameState.lastTime = timestamp;
  gameState.playerX += ((gameState.targetX || gameState.playerX) - gameState.playerX) * 0.014 * elapsed;
  player.style.left = `${gameState.playerX}%`;

  moveObjects(gameState.items, elapsed, true);
  moveObjects(gameState.obstacles, elapsed, false);
  gameFrame = requestAnimationFrame(gameLoop);
}

function moveObjects(objects, elapsed, isLeaf) {
  objects.forEach((object) => {
    object.y += object.speed * elapsed;
    if (object.y > 110) {
      object.y = -10 - Math.random() * 20;
      object.x = 7 + Math.random() * 86;
    }
    object.element.style.left = `${object.x}%`;
    object.element.style.top = `${object.y}%`;
    if (Math.abs(object.x - gameState.playerX) < (isLeaf ? 6 : 8) && object.y > 76 && object.y < 94) {
      if (isLeaf) collectLeaf(object);
      else endGame();
    }
  });
}

function collectLeaf(item) {
  item.y = -12;
  item.x = 7 + Math.random() * 86;
  gameState.score += 1;
  scoreElement.textContent = gameState.score;
  hint.classList.add('is-hidden');
}

function endGame() {
  gameState.running = false;
  cancelAnimationFrame(gameFrame);
  const bestScore = Math.max(gameState.score, Number(localStorage.getItem(bestScoreKey) || 0));
  localStorage.setItem(bestScoreKey, bestScore);
  bestScoreElement.textContent = bestScore;
  hint.textContent = `Камень остановил забег. Листьев собрано: ${gameState.score}`;
  hint.classList.remove('is-hidden');
}

function movePlayer(direction) {
  if (!gameState) return;
  gameState.targetX = Math.max(7, Math.min(93, (gameState.targetX || gameState.playerX) + direction * 7));
  hint.classList.add('is-hidden');
}

document.addEventListener('keydown', (event) => {
  if (event.key === 'ArrowLeft' || event.key.toLowerCase() === 'a') {
    event.preventDefault();
    movePlayer(-1);
  }
  if (event.key === 'ArrowRight' || event.key.toLowerCase() === 'd') {
    event.preventDefault();
    movePlayer(1);
  }
});

restartButton.addEventListener('click', startGame);
touchButtons.forEach((button) => {
  button.addEventListener('click', () => movePlayer(Number(button.dataset.direction)));
});
