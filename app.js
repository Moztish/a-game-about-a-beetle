const form = document.querySelector('form');
const submitButton = form.querySelector('button');
const defaultButtonText = submitButton.firstChild;
const recoveryDialog = document.querySelector('.recovery-dialog');
const recoveryForm = document.querySelector('#recovery-form');
const recoveryMessage = document.querySelector('#recovery-message');

document.querySelector('#open-recovery').addEventListener('click', (event) => {
  event.preventDefault();
  recoveryDialog.showModal();
  recoveryForm.querySelector('input').focus();
});

document.querySelector('[data-close-recovery]').addEventListener('click', () => {
  recoveryDialog.close();
});

recoveryForm.addEventListener('submit', (event) => {
  event.preventDefault();
  recoveryMessage.textContent = 'Форма заполнена. Отправка писем пока не подключена.';
});

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
const gamePrologue = document.querySelector('#game-prologue');
const prologueName = document.querySelector('#prologue-name');
const beginJourneyButton = document.querySelector('#begin-journey');
const chapterInterlude = document.querySelector('#chapter-interlude');
const cowInterlude = document.querySelector('#cow-interlude');
const bogdanInterlude = document.querySelector('#bogdan-interlude');
const gameEnding = document.querySelector('#game-ending');
const continueChapterButton = document.querySelector('#continue-chapter');
const continueCowStageButton = document.querySelector('#continue-cow-stage');
const continueBogdanStageButton = document.querySelector('#continue-bogdan-stage');
const playAgainButton = document.querySelector('#play-again');
const scoreElement = document.querySelector('#score');
const bestScoreElement = document.querySelector('#best-score');
const levelNumberElement = document.querySelector('#level-number');
const levelNameElement = document.querySelector('#level-name');
const levelGoalElement = document.querySelector('#level-goal');
const gameControlsCopy = document.querySelector('#game-controls-copy');
const bossHud = document.querySelector('#boss-hud');
const bossHealthTrack = document.querySelector('#boss-health');
const bossHealthFill = document.querySelector('#boss-health-fill');
const bossHealthLabel = document.querySelector('#boss-health-label');
const bossNameLabel = document.querySelector('#boss-name');
const bossPlayerHealth = document.querySelector('#boss-player-health');
const playerHealthLabel = document.querySelector('#player-health-label');
const gameBoss = document.querySelector('#game-boss');
const egoriusBoss = document.querySelector('#egorius-boss');
const cowBoss = document.querySelector('#cow-boss');
const bogdanBoss = document.querySelector('#bogdan-boss');
const bossAttacksLayer = document.querySelector('#boss-attacks');
const mazeLayer = document.querySelector('#maze-layer');
const challengePanel = document.querySelector('#challenge-panel');
const challengeKicker = document.querySelector('#challenge-kicker');
const challengePrompt = document.querySelector('#challenge-prompt');
const challengeProgress = document.querySelector('#challenge-progress');
const challengeCue = document.querySelector('#challenge-cue');
const challengeCourse = document.querySelector('#challenge-course');
const itemsLayer = document.querySelector('#game-items');
const obstaclesLayer = document.querySelector('#game-obstacles');
const hint = document.querySelector('#game-hint');
const restartButton = document.querySelector('#restart-game');
const attackBossButton = document.querySelector('#attack-boss');
const challengeActionButton = document.querySelector('#challenge-action');
const dashButton = document.querySelector('#dash-button');
const dashButtonLabel = document.querySelector('#dash-button-label');
const soundToggleButton = document.querySelector('#sound-toggle');
const soundLabel = document.querySelector('#sound-label');
const touchButtons = document.querySelectorAll('.touch-button[data-direction]');
const mazeTouchButtons = document.querySelectorAll('[data-maze-direction]');
const bestScoreKey = 'navoznik-best-score';
let gameFrame;
let gameState;
let musicContext;
let musicMasterGain;
let musicDelay;
let musicTimer;
let musicStep = 0;
let nextMusicTime = 0;
let levelMessageTimer;
let dashResetTimer;

const melody = [440, 0, 523.25, 587.33, 0, 659.25, 587.33, 523.25, 392, 0, 440, 523.25, 0, 587.33, 523.25, 392];
const bassLine = [110, 87.31, 130.81, 98];
const bossMelody = [164.81, 0, 164.81, 0, 196, 0, 246.94, 196, 146.83, 0, 146.83, 0, 220, 0, 196, 164.81];
const bossBassLine = [55, 55, 49, 49];
const egoriusMelody = [329.63, 0, 392, 493.88, 0, 587.33, 493.88, 392, 349.23, 0, 440, 523.25, 0, 659.25, 523.25, 440];
const egoriusBassLine = [82.41, 98, 73.42, 110];
const cowMelody = [196, 0, 261.63, 0, 293.66, 261.63, 0, 220, 196, 0, 164.81, 196, 0, 220, 261.63, 0];
const bogdanMelody = [130.81, 0, 155.56, 0, 174.61, 0, 130.81, 0, 116.54, 0, 146.83, 0, 174.61, 155.56, 0, 130.81];
const leavesPerLevel = 10;
const bossMaxHealth = 12;
const egoriusMaxHealth = 16;
const cowMaxHealth = 20;
const bogdanMaxHealth = 24;
const playerMaxLives = 3;
const playerInvulnerabilityDuration = 950;
const dashDuration = 420;
const dashCooldown = 2200;
const doubleTapWindow = 320;
const valikStageIndex = 3;
const mazeMap = [
  '#############',
  '#.S.#......E#',
  '#.#.#.###.#.#',
  '#.#...#...#.#',
  '#.###.#.#.#.#',
  '#.....#.#...#',
  '###.#.#.###.#',
  '#...........#',
  '#############'
];
const levels = [
  { name: 'Сумеречная поляна', speed: 1, obstacles: 4 },
  { name: 'Лунный бурелом', speed: 1.35, obstacles: 5 },
  { name: 'Корневой слалом', mode: 'challenge', challenge: 'valik', target: 3 },
  { name: 'Логово Валика', speed: 1.85, obstacles: 7, boss: true, bossType: 'valik' },
  { name: 'Корневой лабиринт', mode: 'maze', speed: 1, obstacles: 0 },
  { name: 'Лагуна шепчущих семян', mode: 'location', speed: 1.15, obstacles: 5, target: 6, collectible: '◉' },
  { name: 'Зеркальный шифр', mode: 'challenge', challenge: 'egorius', target: 5 },
  { name: 'Арбузная арена', mode: 'duel', speed: 1.2, obstacles: 0, boss: true, bossType: 'egorius' },
  { name: 'Маслобойный такт', mode: 'challenge', challenge: 'cow', target: 5 },
  { name: 'КРАВА — Защитник класса', mode: 'duel', speed: 1.35, obstacles: 0, boss: true, bossType: 'cow' },
  { name: 'Платформы туманного хребта', mode: 'challenge', challenge: 'bogdan', target: 6 },
  { name: 'Вершина хранителя', mode: 'duel', speed: 1.5, obstacles: 0, boss: true, bossType: 'bogdan' }
];

const bossDefinitions = {
  valik: { name: 'Жук-олень Валик', shortName: 'Валик', maxHealth: bossMaxHealth, element: gameBoss },
  egorius: { name: 'Синий арбуз ЕГОРИУС', shortName: 'Егориус', maxHealth: egoriusMaxHealth, element: egoriusBoss },
  cow: { name: 'КРАВА — Защитник класса', shortName: 'КРАВА', maxHealth: cowMaxHealth, element: cowBoss },
  bogdan: { name: 'Богдан Хагрид', shortName: 'Богдан', maxHealth: bogdanMaxHealth, element: bogdanBoss }
};

function openGame(nickname) {
  document.querySelector('.auth-shell').hidden = true;
  gameScreen.hidden = true;
  document.querySelector('#game-title').firstChild.textContent = `${nickname}, кати. Собирай. `;
  prologueName.textContent = nickname;
  bestScoreElement.textContent = localStorage.getItem(bestScoreKey) || '0';
  showPrologue();
}

function startGame() {
  cancelAnimationFrame(gameFrame);
  window.clearTimeout(levelMessageTimer);
  window.clearTimeout(dashResetTimer);
  chapterInterlude.hidden = true;
  cowInterlude.hidden = true;
  bogdanInterlude.hidden = true;
  gameEnding.hidden = true;
  gameScreen.hidden = false;
  gameScreen.dataset.mode = 'runner';
  gameScreen.dataset.boss = 'false';
  gameBoard.dataset.boss = 'false';
  gameBoard.dataset.challenge = '';
  gameState = { score: 0, level: 0, mode: 'runner', playerX: 50, lastTime: 0, running: true, items: [], obstacles: [], bossEncounter: false, bossDefeated: false, bossHealth: bossMaxHealth, bossMaxHealth, bossType: 'valik', playerLives: playerMaxLives, playerInvulnerableUntil: 0, bossAim: null, bossShots: [], playerShots: [], nextBossAttackAt: 0, nextPlayerAttackAt: 0, nextDashAt: 0, lastDirection: 1 };
  scoreElement.textContent = '0';
  updatePlayerHealth();
  hint.classList.remove('is-hidden');
  hint.textContent = 'Нажми ← → или A D, чтобы начать';
  bossHud.hidden = true;
  bossPlayerHealth.hidden = false;
  gameBoss.hidden = true;
  egoriusBoss.hidden = true;
  cowBoss.hidden = true;
  bogdanBoss.hidden = true;
  mazeLayer.hidden = true;
  attackBossButton.hidden = true;
  dashButton.hidden = false;
  dashButton.disabled = false;
  dashButtonLabel.textContent = 'Рывок';
  challengePanel.hidden = true;
  challengeCourse.hidden = true;
  challengeCourse.replaceChildren();
  challengeActionButton.hidden = true;
  mazeLayer.replaceChildren();
  gameBoss.classList.remove('is-hit', 'is-defeated');
  egoriusBoss.classList.remove('is-hit', 'is-defeated');
  cowBoss.classList.remove('is-hit', 'is-defeated');
  bogdanBoss.classList.remove('is-hit', 'is-defeated');
  player.classList.remove('is-damaged');
  bossAttacksLayer.replaceChildren();
  itemsLayer.replaceChildren();
  obstaclesLayer.replaceChildren();
  updateLevelDisplay();
  createGameObjects();
  gameBoard.focus();
  gameFrame = requestAnimationFrame(gameLoop);
}

function startMazeChapter() {
  cancelAnimationFrame(gameFrame);
  window.clearTimeout(levelMessageTimer);
  chapterInterlude.hidden = true;
  cowInterlude.hidden = true;
  bogdanInterlude.hidden = true;
  gameScreen.hidden = false;
  gameScreen.dataset.mode = 'maze';
  gameScreen.dataset.boss = 'false';
  gameScreen.dataset.challenge = '';
  gameBoard.dataset.boss = 'false';
  gameBoard.dataset.challenge = '';
  const score = gameState.score;
  const playerLives = gameState.playerLives ?? playerMaxLives;
  const nextDashAt = gameState.nextDashAt ?? 0;
  gameState = { score, level: 4, mode: 'maze', running: true, mazeX: 2, mazeY: 1, mazeSeeds: 0, mazeSeedElements: [], mazeExit: null, playerLives, nextDashAt };
  gameBoard.dataset.mode = 'maze';
  gameBoard.dataset.level = '5';
  gameBoard.dataset.challenge = '';
  challengePanel.hidden = true;
  challengeCourse.hidden = true;
  challengeActionButton.hidden = true;
  scoreElement.textContent = String(score);
  mazeLayer.replaceChildren();
  mazeLayer.hidden = false;
  itemsLayer.replaceChildren();
  obstaclesLayer.replaceChildren();
  bossAttacksLayer.replaceChildren();
  bossHud.hidden = true;
  bossPlayerHealth.hidden = false;
  gameBoss.hidden = true;
  egoriusBoss.hidden = true;
  cowBoss.hidden = true;
  bogdanBoss.hidden = true;
  attackBossButton.hidden = true;
  challengePanel.hidden = true;
  challengeCourse.hidden = true;
  challengeActionButton.hidden = true;
  dashButton.hidden = false;
  dashButton.disabled = performance.now() < nextDashAt;
  dashButtonLabel.textContent = dashButton.disabled ? 'Перезарядка' : 'Рывок';
  updatePlayerHealth();
  player.classList.remove('is-damaged');
  updateLevelDisplay();
  createMaze();
  hint.textContent = 'Собери три семени и найди выход';
  hint.classList.remove('is-hidden');
  player.style.left = `${(gameState.mazeX + 0.5) / 13 * 100}%`;
  player.style.top = `${(gameState.mazeY + 0.5) / 9 * 100}%`;
  gameBoard.focus();
}

function createMaze() {
  mazeMap.forEach((row, y) => {
    [...row].forEach((cell, x) => {
      if (cell === '#') {
        const wall = document.createElement('span');
        wall.className = 'maze-wall';
        wall.style.left = `${x / 13 * 100}%`;
        wall.style.top = `${y / 9 * 100}%`;
        wall.style.width = `${100 / 13}%`;
        wall.style.height = `${100 / 9}%`;
        mazeLayer.append(wall);
      }
      if (cell === 'E') {
        const exit = document.createElement('span');
        exit.className = 'maze-exit is-locked';
        exit.textContent = '◇';
        exit.style.left = `${(x + 0.5) / 13 * 100}%`;
        exit.style.top = `${(y + 0.5) / 9 * 100}%`;
        mazeLayer.append(exit);
        gameState.mazeExit = { x, y, element: exit };
      }
    });
  });

  gameState.mazeSeedElements = [
    { x: 1, y: 5 },
    { x: 5, y: 3 },
    { x: 10, y: 5 }
  ].map((seed) => {
    const element = document.createElement('span');
    element.className = 'maze-seed';
    element.textContent = '✦';
    element.style.left = `${(seed.x + 0.5) / 13 * 100}%`;
    element.style.top = `${(seed.y + 0.5) / 9 * 100}%`;
    mazeLayer.append(element);
    return { ...seed, element, collected: false };
  });
}

function moveInMaze(direction) {
  if (gameState?.mode !== 'maze' || !gameState.running) return;
  const destinations = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] };
  const [deltaX, deltaY] = destinations[direction];
  const nextX = gameState.mazeX + deltaX;
  const nextY = gameState.mazeY + deltaY;
  if (mazeMap[nextY]?.[nextX] === '#') return;

  gameState.mazeX = nextX;
  gameState.mazeY = nextY;
  player.style.left = `${(nextX + 0.5) / 13 * 100}%`;
  player.style.top = `${(nextY + 0.5) / 9 * 100}%`;

  const seed = gameState.mazeSeedElements.find((item) => !item.collected && item.x === nextX && item.y === nextY);
  if (seed) {
    seed.collected = true;
    seed.element.remove();
    gameState.mazeSeeds += 1;
    gameState.score += 1;
    scoreElement.textContent = String(gameState.score);
    levelGoalElement.textContent = `Семена: ${gameState.mazeSeeds} / 3 · найди выход`;
    if (gameState.mazeSeeds === 3) {
      gameState.mazeExit.element.classList.remove('is-locked');
      hint.textContent = 'Выход открыт. Найди его!';
      hint.classList.remove('is-hidden');
    }
  }

  if (nextX === gameState.mazeExit.x && nextY === gameState.mazeExit.y && gameState.mazeSeeds === 3) {
    startLocation(5);
  }
}

function startChallenge(levelIndex) {
  cancelAnimationFrame(gameFrame);
  window.clearTimeout(levelMessageTimer);
  const previousState = gameState;
  const level = levels[levelIndex];
  const challengeType = level.challenge;
  gameState = {
    score: previousState.score,
    level: levelIndex,
    mode: 'challenge',
    challengeType,
    challengeProgress: 0,
    challengeTarget: level.target,
    challengeStartedAt: performance.now(),
    challengePattern: [1, -1, 1, 1, -1],
    challengeGates: [26, 74, 26],
    challengeLane: 1,
    challengeLanes: [1, 2, 0, 1, 2, 1],
    running: true,
    playerX: 50,
    lastTime: 0,
    playerLives: previousState.playerLives ?? playerMaxLives,
    playerInvulnerableUntil: previousState.playerInvulnerableUntil ?? 0,
    nextDashAt: previousState.nextDashAt ?? 0,
    lastDirection: previousState.lastDirection ?? 1,
    lastDirectionalInput: null
  };

  gameScreen.hidden = false;
  gameScreen.dataset.mode = 'challenge';
  gameScreen.dataset.boss = 'false';
  gameScreen.dataset.challenge = challengeType;
  gameBoard.dataset.mode = 'challenge';
  gameBoard.dataset.boss = 'false';
  gameBoard.dataset.challenge = challengeType;
  gameBoard.dataset.challenge = challengeType;
  gameBoard.dataset.level = String(levelIndex + 1);
  challengePanel.hidden = false;
  challengeCourse.replaceChildren();
  challengeCourse.hidden = false;
  mazeLayer.hidden = true;
  itemsLayer.replaceChildren();
  obstaclesLayer.replaceChildren();
  bossAttacksLayer.replaceChildren();
  bossHud.hidden = true;
  bossPlayerHealth.hidden = false;
  Object.values(bossDefinitions).forEach((boss) => { boss.element.hidden = true; });
  attackBossButton.hidden = true;
  dashButton.hidden = challengeType !== 'valik';
  dashButton.disabled = performance.now() < gameState.nextDashAt;
  dashButtonLabel.textContent = dashButton.disabled ? 'Перезарядка' : 'Рывок';
  challengeActionButton.hidden = !['cow', 'bogdan'].includes(challengeType);
  challengeActionButton.textContent = challengeType === 'cow' ? 'Сбить такт · пробел' : 'Взойти на платформу ↑';
  player.classList.remove('is-damaged');
  player.style.left = '50%';
  player.style.top = challengeType === 'bogdan' ? '82%' : '';
  scoreElement.textContent = String(gameState.score);
  updatePlayerHealth();
  updateLevelDisplay();
  createChallengeCourse();
  updateChallengeDisplay();
  hint.classList.add('is-hidden');
  gameBoard.focus();
  gameFrame = requestAnimationFrame(gameLoop);
}

function createChallengeCourse() {
  if (gameState.challengeType === 'valik') {
    gameState.challengeGates.forEach((position, index) => {
      const gate = document.createElement('span');
      gate.className = 'dash-gate';
      gate.textContent = '◇';
      gate.style.left = `${position}%`;
      gate.dataset.gate = String(index);
      challengeCourse.append(gate);
    });
    challengeCourse.hidden = false;
  }

  if (gameState.challengeType === 'cow') {
    challengeCourse.innerHTML = '<div class="rhythm-track"><span class="rhythm-zone"></span><span class="rhythm-marker"></span></div>';
    challengeCourse.hidden = false;
  }

  if (gameState.challengeType === 'bogdan') {
    gameState.challengeLanes.forEach((lane, index) => {
      const platform = document.createElement('span');
      platform.className = 'climb-platform';
      platform.style.left = `${[25, 50, 75][lane]}%`;
      platform.style.bottom = `${12 + index * 12}%`;
      platform.dataset.step = String(index);
      challengeCourse.append(platform);
    });
    challengeCourse.hidden = false;
    updateClimberPosition();
  }
}

function updateChallengeDisplay() {
  const type = gameState.challengeType;
  const prompts = {
    valik: ['Корневой слалом', 'Рывком пройди через три арки'],
    egorius: ['Зеркальный шифр', 'Повтори последовательность отражений'],
    cow: ['Маслобойный такт', 'Жми в ритм, когда искра в светлой зоне'],
    bogdan: ['Платформы туманного хребта', 'Выбери соседнюю платформу и поднимайся']
  };
  challengeKicker.textContent = prompts[type][0];
  challengePrompt.textContent = prompts[type][1];
  challengeProgress.textContent = `${gameState.challengeProgress} / ${gameState.challengeTarget}`;

  if (type === 'valik') {
    const gate = gameState.challengeGates[gameState.challengeProgress];
    challengeCue.textContent = gate ? `Следующая арка: ${gate < 50 ? '←' : '→'}` : 'Путь открыт';
    challengeCourse.querySelectorAll('.dash-gate').forEach((element, index) => {
      element.classList.toggle('is-current', index === gameState.challengeProgress);
      element.classList.toggle('is-cleared', index < gameState.challengeProgress);
    });
  } else if (type === 'egorius') {
    challengeCue.textContent = gameState.challengePattern.map((direction) => direction < 0 ? '←' : '→').join('   ');
  } else if (type === 'cow') {
    challengeCue.textContent = 'Лови светлый такт';
  } else {
    challengeCue.textContent = `Платформа ${gameState.challengeProgress + 1} / ${gameState.challengeTarget}`;
    challengeCourse.querySelectorAll('.climb-platform').forEach((element, index) => {
      element.classList.toggle('is-current', index === gameState.challengeProgress);
      element.classList.toggle('is-cleared', index < gameState.challengeProgress);
    });
  }
}

function completeChallenge() {
  const bossByChallenge = { valik: 'valik', egorius: 'egorius', cow: 'cow', bogdan: 'bogdan' };
  gameState.score += gameState.challengeTarget;
  scoreElement.textContent = String(gameState.score);
  startBossArena(bossByChallenge[gameState.challengeType]);
}

function inputMirror(direction) {
  const expected = gameState.challengePattern[gameState.challengeProgress];
  if (direction === expected) {
    gameState.challengeProgress += 1;
    if (gameState.challengeProgress >= gameState.challengeTarget) {
      completeChallenge();
      return;
    }
  } else {
    gameState.challengeProgress = 0;
    hitPlayer(performance.now(), 'зеркальное эхо');
  }
  updateChallengeDisplay();
}

function hitCowBeat() {
  const phase = (performance.now() - gameState.challengeStartedAt) % 1000;
  if (phase >= 390 && phase <= 610) {
    gameState.challengeProgress += 1;
    if (gameState.challengeProgress >= gameState.challengeTarget) {
      completeChallenge();
      return;
    }
    hint.textContent = 'Точно в такт! Ещё раз.';
  } else {
    hitPlayer(performance.now(), 'сбитый такт');
    hint.textContent = 'Мимо ритма. Следи за светлой зоной.';
  }
  hint.classList.remove('is-hidden');
  updateChallengeDisplay();
}

function moveClimber(direction) {
  gameState.challengeLane = Math.max(0, Math.min(2, gameState.challengeLane + direction));
  updateClimberPosition();
}

function updateClimberPosition() {
  gameState.playerX = [25, 50, 75][gameState.challengeLane];
  player.style.left = `${gameState.playerX}%`;
  player.style.top = `${82 - gameState.challengeProgress * 10}%`;
}

function climbPlatform() {
  if (gameState.challengeLane !== gameState.challengeLanes[gameState.challengeProgress]) {
    hitPlayer(performance.now(), 'шаткая платформа');
    hint.textContent = 'Найди соседнюю платформу, прежде чем подниматься.';
    hint.classList.remove('is-hidden');
    return;
  }
  gameState.challengeProgress += 1;
  if (gameState.challengeProgress >= gameState.challengeTarget) {
    completeChallenge();
    return;
  }
  updateClimberPosition();
  updateChallengeDisplay();
}

function startLocation(levelIndex) {
  cancelAnimationFrame(gameFrame);
  window.clearTimeout(levelMessageTimer);
  const score = gameState.score;
  const playerLives = gameState.playerLives ?? playerMaxLives;
  const playerInvulnerableUntil = gameState.playerInvulnerableUntil ?? 0;
  const nextDashAt = gameState.nextDashAt ?? 0;
  const lastDirection = gameState.lastDirection ?? 1;
  gameState = { score, level: levelIndex, mode: 'location', playerX: 50, lastTime: 0, running: true, items: [], obstacles: [], locationProgress: 0, playerLives, playerInvulnerableUntil, nextDashAt, lastDirection, lastDirectionalInput: null };
  gameScreen.hidden = false;
  gameScreen.dataset.mode = 'location';
  gameScreen.dataset.boss = 'false';
  gameScreen.dataset.challenge = '';
  gameBoard.dataset.mode = 'location';
  gameBoard.dataset.boss = 'false';
  gameBoard.dataset.challenge = '';
  challengePanel.hidden = true;
  challengeCourse.hidden = true;
  challengeActionButton.hidden = true;
  mazeLayer.hidden = true;
  bossHud.hidden = true;
  bossPlayerHealth.hidden = false;
  gameBoss.hidden = true;
  egoriusBoss.hidden = true;
  cowBoss.hidden = true;
  bogdanBoss.hidden = true;
  attackBossButton.hidden = true;
  challengePanel.hidden = true;
  challengeCourse.hidden = true;
  challengeCourse.replaceChildren();
  challengeActionButton.hidden = true;
  dashButton.hidden = false;
  dashButton.disabled = performance.now() < nextDashAt;
  dashButtonLabel.textContent = dashButton.disabled ? 'Перезарядка' : 'Рывок';
  itemsLayer.replaceChildren();
  obstaclesLayer.replaceChildren();
  bossAttacksLayer.replaceChildren();
  player.classList.remove('is-damaged');
  updatePlayerHealth();
  player.style.left = '50%';
  player.style.top = '';
  scoreElement.textContent = String(score);
  updateLevelDisplay();
  createLocationObjects();
  hint.textContent = `Собери ${levels[levelIndex].target} семян и не попади в течение`;
  hint.classList.remove('is-hidden');
  gameBoard.focus();
  gameFrame = requestAnimationFrame(gameLoop);
}

function createLocationObjects() {
  const level = levels[gameState.level];
  for (let index = 0; index < 9; index += 1) {
    const item = { x: 8 + Math.random() * 84, y: -10 - index * 14, speed: 0.018 + Math.random() * 0.012 };
    const element = document.createElement('span');
    element.className = 'game-location-item';
    element.textContent = level.collectible;
    itemsLayer.append(element);
    gameState.items.push({ ...item, element });
  }
  for (let index = 0; index < level.obstacles; index += 1) addObstacle(index);
}

function startEgoriusArena() {
  startBossArena('egorius');
}

function startBossArena(bossType) {
  cancelAnimationFrame(gameFrame);
  const score = gameState.score;
  gameScreen.dataset.mode = 'duel';
  gameScreen.dataset.boss = 'true';
  const boss = bossDefinitions[bossType];
  const levelIndex = levels.findIndex((level) => level.bossType === bossType);
  gameState = { score, level: levelIndex, mode: 'duel', playerX: 50, lastTime: 0, running: true, items: [], obstacles: [], bossEncounter: true, bossDefeated: false, bossType, bossHealth: boss.maxHealth, bossMaxHealth: boss.maxHealth, playerLives: gameState.playerLives ?? playerMaxLives, playerInvulnerableUntil: gameState.playerInvulnerableUntil ?? 0, bossAim: null, bossShots: [], playerShots: [], nextBossAttackAt: performance.now() + 1200, nextPlayerAttackAt: 0, nextDashAt: gameState.nextDashAt ?? 0, lastDirection: gameState.lastDirection ?? 1, lastDirectionalInput: null, bossX: 50, bossDirection: 1, bossChargingUntil: 0, nextBossChargeAt: performance.now() + 1800 };
  gameBoard.dataset.mode = 'duel';
  gameBoard.dataset.boss = bossType;
  gameScreen.dataset.challenge = '';
  gameBoard.dataset.challenge = '';
  challengePanel.hidden = true;
  challengeCourse.hidden = true;
  challengeActionButton.hidden = true;
  gameBoard.dataset.level = String(levelIndex + 1);
  bossHealthTrack.setAttribute('aria-label', `Здоровье: ${boss.name}`);
  mazeLayer.hidden = true;
  bossAttacksLayer.replaceChildren();
  itemsLayer.replaceChildren();
  obstaclesLayer.replaceChildren();
  bossNameLabel.textContent = boss.name;
  bossHud.hidden = false;
  bossPlayerHealth.hidden = false;
  Object.values(bossDefinitions).forEach((entry) => {
    entry.element.hidden = entry !== boss;
    entry.element.classList.remove('is-hit', 'is-defeated');
  });
  attackBossButton.hidden = false;
  dashButton.hidden = false;
  dashButton.disabled = false;
  dashButtonLabel.textContent = 'Рывок';
  gameScreen.dataset.boss = 'true';
  gameBoard.dataset.boss = bossType;
  player.classList.remove('is-damaged');
  scoreElement.textContent = String(score);
  updateLevelDisplay();
  updateBossHealth();
  updatePlayerHealth();
  player.style.left = '50%';
  player.style.top = '';
  boss.element.style.left = '50%';
  boss.element.style.top = '';
  boss.element.style.scale = '1 1';
  player.focus?.();
  hint.textContent = 'Уворачивайся от семян и жми пробел, чтобы бросать в ответ';
  hint.classList.remove('is-hidden');
  gameBoard.focus();
  gameFrame = requestAnimationFrame(gameLoop);
}

function showChapterInterlude() {
  gameScreen.hidden = true;
  chapterInterlude.hidden = false;
  continueChapterButton.focus();
}

function showCowInterlude() {
  gameScreen.hidden = true;
  cowInterlude.hidden = false;
  continueCowStageButton.focus();
}

function showBogdanInterlude() {
  gameScreen.hidden = true;
  bogdanInterlude.hidden = false;
  continueBogdanStageButton.focus();
}

function continueCowStage() {
  cowInterlude.hidden = true;
  startChallenge(8);
}

function continueBogdanStage() {
  bogdanInterlude.hidden = true;
  startChallenge(10);
}

function showGameEnding() {
  gameScreen.hidden = true;
  gameEnding.hidden = false;
  playAgainButton.focus();
}

function showPrologue() {
  gameScreen.hidden = true;
  chapterInterlude.hidden = true;
  cowInterlude.hidden = true;
  bogdanInterlude.hidden = true;
  gameEnding.hidden = true;
  gamePrologue.hidden = false;
  beginJourneyButton.focus();
}

function beginJourney() {
  gamePrologue.hidden = true;
  gameScreen.hidden = false;
  startGame();
}

function startOver() {
  showPrologue();
}

function createMusicContext() {
  const AudioContextClass = window.AudioContext || window.webkitAudioContext;
  if (!AudioContextClass) throw new Error('Web Audio is unavailable.');

  musicContext = new AudioContextClass();
  musicMasterGain = musicContext.createGain();
  musicMasterGain.gain.value = 0;
  musicMasterGain.connect(musicContext.destination);

  musicDelay = musicContext.createDelay();
  musicDelay.delayTime.value = 0.28;
  const delayFeedback = musicContext.createGain();
  delayFeedback.gain.value = 0.18;
  musicDelay.connect(musicMasterGain);
  musicDelay.connect(delayFeedback);
  delayFeedback.connect(musicDelay);
}

function playMusicTone(frequency, time, duration, type, volume) {
  const oscillator = musicContext.createOscillator();
  const envelope = musicContext.createGain();
  const filter = musicContext.createBiquadFilter();
  filter.type = 'lowpass';
  filter.frequency.value = type === 'triangle' ? 1800 : 900;
  oscillator.type = type;
  oscillator.frequency.setValueAtTime(frequency, time);
  envelope.gain.setValueAtTime(0.0001, time);
  envelope.gain.exponentialRampToValueAtTime(volume, time + 0.025);
  envelope.gain.exponentialRampToValueAtTime(0.0001, time + duration);
  oscillator.connect(filter);
  filter.connect(envelope);
  envelope.connect(musicMasterGain);
  envelope.connect(musicDelay);
  oscillator.start(time);
  oscillator.stop(time + duration + 0.03);
}

function scheduleMusicStep(step, time) {
  if (gameState?.bossEncounter && !gameState.bossDefeated) {
    if (gameState.bossType === 'cow') {
      const note = cowMelody[step];
      if (note) playMusicTone(note, time, 0.18, 'triangle', 0.05);
      if (step % 4 === 0) playMusicTone(65.41, time, 0.38, 'sine', 0.12);
      return;
    }

    if (gameState.bossType === 'bogdan') {
      const note = bogdanMelody[step];
      if (note) playMusicTone(note, time, 0.22, 'sine', 0.06);
      if (step % 4 === 0) playMusicTone(49, time, 0.48, 'triangle', 0.14);
      return;
    }

    if (gameState.bossType === 'egorius') {
      const note = egoriusMelody[step];
      if (note) playMusicTone(note, time, 0.11, 'square', 0.035);
      if (step % 4 === 0) {
        playMusicTone(egoriusBassLine[step / 4], time, 0.34, 'triangle', 0.13);
        playMusicTone(65.41, time, 0.09, 'sine', 0.1);
      }
      return;
    }

    const bossNote = bossMelody[step];
    if (bossNote) playMusicTone(bossNote, time, 0.13, 'sawtooth', 0.04);
    if (step % 4 === 0) {
      playMusicTone(bossBassLine[step / 4], time, 0.38, 'sine', 0.13);
      playMusicTone(48, time, 0.12, 'sine', 0.12);
    }
    return;
  }

  const melodyNote = melody[step];
  if (melodyNote) playMusicTone(melodyNote, time, 0.26, 'triangle', 0.055);
  if (step % 4 === 0) {
    playMusicTone(bassLine[step / 4], time, 0.9, 'sine', 0.09);
  }
}

function updateMusic() {
  const isBossTheme = gameState?.bossEncounter && !gameState.bossDefeated;
  const bossTempos = { valik: 0.21, egorius: 0.16, cow: 0.24, bogdan: 0.29 };
  const stepDuration = isBossTheme ? bossTempos[gameState.bossType] : 0.42;
  while (nextMusicTime < musicContext.currentTime + 0.1) {
    scheduleMusicStep(musicStep, nextMusicTime);
    nextMusicTime += stepDuration;
    musicStep = (musicStep + 1) % melody.length;
  }
}

async function toggleMusic() {
  const isEnabled = soundToggleButton.getAttribute('aria-pressed') !== 'true';
  try {
    if (!musicContext) createMusicContext();
    await musicContext.resume();

    if (isEnabled) {
      musicMasterGain.gain.setTargetAtTime(0.7, musicContext.currentTime, 0.08);
      nextMusicTime = musicContext.currentTime + 0.05;
      musicTimer = window.setInterval(updateMusic, 25);
    } else {
      window.clearInterval(musicTimer);
      musicMasterGain.gain.setTargetAtTime(0, musicContext.currentTime, 0.06);
    }

    soundToggleButton.setAttribute('aria-pressed', String(isEnabled));
    soundToggleButton.setAttribute('aria-label', `${isEnabled ? 'Выключить' : 'Включить'} музыку`);
    soundLabel.textContent = `Музыка ${isEnabled ? 'включена' : 'выключена'}`;
  } catch {
    soundLabel.textContent = 'Звук недоступен';
    soundToggleButton.disabled = true;
  }
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

  for (let index = 0; index < levels[0].obstacles; index += 1) addObstacle(index);
}

function addObstacle(index) {
  const obstacle = { x: 8 + Math.random() * 84, y: -30 - index * 25, speed: 0.014 + Math.random() * 0.009 };
  const element = document.createElement('span');
  element.className = gameState.mode === 'location' ? 'game-stone location-hazard' : 'game-stone';
  obstaclesLayer.append(element);
  gameState.obstacles.push({ ...obstacle, element });
}

function updateLevelDisplay() {
  const level = levels[gameState.level];
  const mode = level.mode || 'runner';
  const isBossLevel = Boolean(level.boss);
  gameBoard.dataset.level = String(gameState.level + 1);
  gameBoard.dataset.mode = mode;
  gameScreen.dataset.mode = mode;
  levelNumberElement.textContent = `Этап ${gameState.level + 1} / ${levels.length}`;
  levelNameElement.textContent = level.name;
  if (mode === 'maze') levelGoalElement.textContent = `Семена: ${gameState.mazeSeeds || 0} / 3 · найди выход`;
  else if (mode === 'challenge') levelGoalElement.textContent = `Испытание: ${gameState.challengeProgress || 0} / ${level.target}`;
  else if (mode === 'duel') levelGoalElement.textContent = `Атакуй: ${bossDefinitions[level.bossType].name}`;
  else if (mode === 'location') levelGoalElement.textContent = `Семена: ${gameState.locationProgress} / ${level.target} · уклоняйся от течений`;
  else if (isBossLevel) levelGoalElement.textContent = 'Атакуй Валика листьями';
  else levelGoalElement.textContent = `Собери ${leavesPerLevel} листьев для перехода`;
  if (mode === 'maze') gameControlsCopy.textContent = 'Стрелки или WASD · собери три семени · найди выход';
  else if (mode === 'challenge' && level.challenge === 'valik') gameControlsCopy.textContent = 'Двойное нажатие ←/→ — рывок через арки';
  else if (mode === 'challenge' && level.challenge === 'egorius') gameControlsCopy.textContent = 'Повтори зеркальный код стрелками ←/→';
  else if (mode === 'challenge' && level.challenge === 'cow') gameControlsCopy.textContent = 'Пробел или кнопка — сбить такт в светлой зоне';
  else if (mode === 'challenge' && level.challenge === 'bogdan') gameControlsCopy.textContent = '←/→ — выбрать платформу · ↑ — подняться';
  else if (mode === 'duel') gameControlsCopy.textContent = '← → или A D — уклонение · двойное нажатие — рывок · пробел — атака';
  else if (mode === 'location') gameControlsCopy.textContent = '← → или A D — уклоняйся · двойное нажатие — рывок · собирай семена';
  else gameControlsCopy.innerHTML = '<span class="key">←</span><span class="key">→</span> двигай жука <span class="control-separator">·</span> двойное нажатие — рывок <span class="control-separator">·</span> собирай листья';
}

function advanceLevel() {
  updateLevelDisplay();
  const level = levels[gameState.level];
  if (level.mode === 'challenge') {
    startChallenge(gameState.level);
    return;
  }
  while (gameState.obstacles.length < level.obstacles) {
    addObstacle(gameState.obstacles.length);
  }
  if (level.boss) startBossEncounter();
  hint.textContent = `Новый этап: ${level.name}`;
  hint.classList.remove('is-hidden');
  window.clearTimeout(levelMessageTimer);
  levelMessageTimer = window.setTimeout(() => hint.classList.add('is-hidden'), 1800);
}

function startBossEncounter() {
  gameState.bossEncounter = true;
  gameState.bossType = 'valik';
  gameState.bossHealth = bossMaxHealth;
  gameState.bossMaxHealth = bossMaxHealth;
  gameState.nextBossAttackAt = performance.now() + 950;
  gameState.nextDashAt = 0;
  gameState.lastDirection = 1;
  gameState.lastDirectionalInput = null;
  gameState.bossX = 50;
  gameState.bossDirection = 1;
  gameBoard.dataset.boss = 'valik';
  gameScreen.dataset.boss = 'true';
  bossHud.hidden = false;
  bossPlayerHealth.hidden = false;
  Object.values(bossDefinitions).forEach((entry) => { entry.element.hidden = entry !== bossDefinitions.valik; });
  gameBoss.style.left = '50%';
  gameBoss.style.top = '';
  gameBoss.style.scale = '1 1';
  bossNameLabel.textContent = bossDefinitions.valik.name;
  bossHealthTrack.setAttribute('aria-label', `Здоровье: ${bossDefinitions.valik.name}`);
  dashButton.hidden = false;
  dashButton.disabled = false;
  dashButtonLabel.textContent = 'Рывок';
  updateBossHealth();
  updatePlayerHealth();
}

function updateBossHealth() {
  bossHealthTrack.setAttribute('aria-valuemax', String(gameState.bossMaxHealth));
  bossHealthTrack.setAttribute('aria-valuenow', String(gameState.bossHealth));
  bossHealthFill.style.width = `${gameState.bossHealth / gameState.bossMaxHealth * 100}%`;
  bossHealthLabel.textContent = `${gameState.bossHealth} / ${gameState.bossMaxHealth}`;
}

function updatePlayerHealth() {
  playerHealthLabel.textContent = `${gameState.playerLives} / ${playerMaxLives}`;
}

function updateBossAttacks(timestamp, elapsed) {
  if (!gameState.running || !gameState.bossEncounter || gameState.bossDefeated) return;

  if (!gameState.bossAim && timestamp >= gameState.nextBossAttackAt) {
    const marker = document.createElement('span');
    const attackType = gameState.bossType === 'cow' ? 'stomp' : gameState.bossType === 'bogdan' ? 'hail' : 'targeted';
    marker.className = `boss-aim boss-aim-${attackType}`;
    marker.style.left = `${attackType === 'stomp' ? 50 : gameState.playerX}%`;
    bossAttacksLayer.append(marker);
    gameState.bossAim = { x: gameState.playerX, type: attackType, remaining: attackType === 'stomp' ? 900 : 650, element: marker };
  }

  if (gameState.bossAim) {
    gameState.bossAim.remaining -= elapsed;
    if (gameState.bossAim.remaining <= 0) {
      const targetX = gameState.bossAim.x;
      const attackType = gameState.bossAim.type;
      gameState.bossAim.element.remove();
      gameState.bossAim = null;
      if (attackType === 'stomp') {
        const element = document.createElement('span');
        element.className = 'boss-projectile boss-projectile-wave';
        element.style.left = '50%';
        bossAttacksLayer.append(element);
        gameState.bossShots.push({ x: 50, y: 25, speed: 0.07, type: 'wave', element });
      } else {
        const offsets = attackType === 'hail' ? [-18, -9, 0, 9, 18] : gameState.bossHealth <= gameState.bossMaxHealth / 2 ? [-10, 10] : [0];
        offsets.forEach((offset) => {
          const element = document.createElement('span');
          element.className = `boss-projectile${attackType === 'hail' ? ' boss-projectile-stone' : ''}`;
          const x = Math.max(6, Math.min(94, targetX + offset));
          element.style.left = `${x}%`;
          bossAttacksLayer.append(element);
          gameState.bossShots.push({ x, y: 25, speed: attackType === 'hail' ? 0.075 : 0.052, type: attackType, element });
        });
      }
      const cooldown = gameState.bossHealth <= gameState.bossMaxHealth / 2 ? 1550 : 2200;
      gameState.nextBossAttackAt = timestamp + (attackType === 'stomp' ? Math.max(cooldown, 2700) : attackType === 'hail' ? Math.max(cooldown, 2400) : cooldown);
    }
  }

  const attackState = gameState;
  const activeShots = attackState.bossShots;
  for (let index = activeShots.length - 1; index >= 0; index -= 1) {
    if (gameState !== attackState || !attackState.running) break;
    const shot = activeShots[index];
    if (!shot) continue;
    shot.y += shot.speed * elapsed * levels[gameState.level].speed;
    shot.element.style.top = `${shot.y}%`;
    if (shot.y >= 82) {
      if (shot.y <= 96 && (shot.type === 'wave' || Math.abs(shot.x - gameState.playerX) < (shot.type === 'hail' ? 9 : 7))) hitPlayer(timestamp);
      shot.element.remove();
      activeShots.splice(index, 1);
    }
  }
}

function hitPlayer(timestamp = performance.now(), cause = 'Удар') {
  if (!gameState.running || gameState.playerLives <= 0 || timestamp < gameState.playerInvulnerableUntil) return;
  gameState.playerLives -= 1;
  gameState.playerInvulnerableUntil = timestamp + playerInvulnerabilityDuration;
  updatePlayerHealth();
  player.classList.remove('is-damaged');
  gameBoard.classList.remove('is-hit');
  void player.offsetWidth;
  void gameBoard.offsetWidth;
  player.classList.add('is-damaged');
  gameBoard.classList.add('is-hit');

  if (gameState.playerLives === 0) {
    const message = gameState.bossEncounter
      ? `${bossDefinitions[gameState.bossType].shortName} разбил твой панцирь. Забег окончен.`
      : 'Панцирь разбит. Забег окончен.';
    endGame(message);
    return;
  }

  hint.textContent = `Попадание: ${cause}. Осталось жизней: ${gameState.playerLives}.`;
  hint.classList.remove('is-hidden');
  window.clearTimeout(levelMessageTimer);
  levelMessageTimer = window.setTimeout(() => hint.classList.add('is-hidden'), 1000);
}

function hitBoss() {
  if (!gameState.running || gameState.bossDefeated) return;
  gameState.bossHealth -= 1;
  updateBossHealth();
  const bossElement = bossDefinitions[gameState.bossType].element;
  bossElement.classList.remove('is-hit');
  void bossElement.offsetWidth;
  bossElement.classList.add('is-hit');

  if (gameState.bossHealth === 0) {
    gameState.bossDefeated = true;
    gameState.running = false;
    cancelAnimationFrame(gameFrame);
    const bestScore = Math.max(gameState.score, Number(localStorage.getItem(bestScoreKey) || 0));
    localStorage.setItem(bestScoreKey, bestScore);
    bestScoreElement.textContent = bestScore;
    bossElement.classList.add('is-defeated');
    clearBossAttacks();
    if (gameState.bossType === 'valik') showChapterInterlude();
    else if (gameState.bossType === 'egorius') showCowInterlude();
    else if (gameState.bossType === 'cow') showBogdanInterlude();
    else showGameEnding();
    return;
  }

  hint.textContent = `Попадание! У босса осталось ${gameState.bossHealth}.`;
  hint.classList.remove('is-hidden');
  window.clearTimeout(levelMessageTimer);
  levelMessageTimer = window.setTimeout(() => hint.classList.add('is-hidden'), 1000);
}

function updateBossMovement(timestamp, elapsed) {
  const boss = bossDefinitions[gameState.bossType].element;
  let horizontalDirection = gameState.bossDirection;

  if (gameState.bossType === 'egorius') {
    const phase = timestamp / 1050;
    gameState.bossX = 50 + Math.sin(phase) * 30;
    horizontalDirection = Math.cos(phase) >= 0 ? 1 : -1;
    boss.style.top = `${17 + Math.sin(timestamp / 360) * 4}%`;
  } else if (gameState.bossType === 'bogdan') {
    const phase = timestamp / 1450;
    gameState.bossX = 50 + Math.sin(phase) * 24;
    horizontalDirection = Math.cos(phase) >= 0 ? 1 : -1;
    boss.style.top = `${12 + (Math.sin(timestamp / 510) + 1) * 3}%`;
  } else {
    if (gameState.bossType === 'cow' && timestamp >= gameState.nextBossChargeAt) {
      gameState.bossChargingUntil = timestamp + 460;
      gameState.nextBossChargeAt = timestamp + (gameState.bossHealth <= gameState.bossMaxHealth / 2 ? 1900 : 2700);
      gameState.bossDirection = gameState.playerX >= gameState.bossX ? 1 : -1;
    }

    const isCharging = gameState.bossType === 'cow' && timestamp < gameState.bossChargingUntil;
    const speed = gameState.bossType === 'cow' ? (isCharging ? 0.105 : 0.024) : 0.019;
    gameState.bossX += gameState.bossDirection * speed * elapsed;
    const bounds = gameState.bossType === 'cow' ? [16, 84] : [28, 72];
    if (gameState.bossX < bounds[0] || gameState.bossX > bounds[1]) {
      gameState.bossX = Math.max(bounds[0], Math.min(bounds[1], gameState.bossX));
      gameState.bossDirection *= -1;
    }
    horizontalDirection = gameState.bossDirection;
    boss.classList.toggle('is-charging', isCharging);
  }

  boss.style.left = `${gameState.bossX}%`;
  boss.style.scale = `${horizontalDirection < 0 ? -1 : 1} 1`;
}

function gameLoop(timestamp) {
  if (!gameState.running) return;
  if (gameState.mode === 'maze') return;
  const loopState = gameState;
  const elapsed = Math.min(timestamp - gameState.lastTime || 16, 40);
  gameState.lastTime = timestamp;
  gameState.playerX += ((gameState.targetX || gameState.playerX) - gameState.playerX) * 0.014 * elapsed;
  player.style.left = `${gameState.playerX}%`;

  if (gameState.mode === 'runner' || gameState.mode === 'location') {
    moveObjects(gameState.items, elapsed, true);
    moveObjects(gameState.obstacles, elapsed, false);
  }
  if (gameState !== loopState) return;
  if (gameState.bossEncounter) {
    updateBossMovement(timestamp, elapsed);
    updateBossAttacks(timestamp, elapsed);
  }
  if (gameState.mode === 'duel') updatePlayerShots(elapsed);
  if (!gameState.running) return;
  gameFrame = requestAnimationFrame(gameLoop);
}

function attackEgorius() {
  if (gameState?.mode !== 'duel' || !gameState.running || performance.now() < gameState.nextPlayerAttackAt) return;
  gameState.nextPlayerAttackAt = performance.now() + 500;
  const shot = document.createElement('span');
  shot.className = 'player-shot';
  shot.style.left = `${gameState.playerX}%`;
  bossAttacksLayer.append(shot);
  gameState.playerShots.push({ x: gameState.playerX, y: 82, element: shot });
}

function updatePlayerShots(elapsed) {
  for (let index = gameState.playerShots.length - 1; index >= 0; index -= 1) {
    const shot = gameState.playerShots[index];
    shot.y -= 0.12 * elapsed;
    shot.element.style.top = `${shot.y}%`;
    if (shot.y <= 32) {
      shot.element.remove();
      gameState.playerShots.splice(index, 1);
      hitBoss();
    }
  }
}

function moveObjects(objects, elapsed, isLeaf) {
  const activeState = gameState;
  objects.forEach((object) => {
    if (gameState !== activeState || !activeState.running) return;
    object.y += object.speed * elapsed * levels[gameState.level].speed;
    if (object.y > 110) {
      object.y = -10 - Math.random() * 20;
      object.x = 7 + Math.random() * 86;
    }
    object.element.style.left = `${object.x}%`;
    object.element.style.top = `${object.y}%`;
    if (Math.abs(object.x - gameState.playerX) < (isLeaf ? 6 : 8) && object.y > 76 && object.y < 94) {
      if (isLeaf && gameState.mode === 'location') collectLocationItem(object);
      else if (isLeaf) collectLeaf(object);
      else {
        object.y = -10 - Math.random() * 20;
        object.x = 7 + Math.random() * 86;
        hitPlayer(performance.now(), gameState.mode === 'location' ? 'течение' : 'камень');
      }
    }
  });
}

function collectLocationItem(item) {
  const level = levels[gameState.level];
  item.y = -12;
  item.x = 7 + Math.random() * 86;
  gameState.score += 1;
  gameState.locationProgress += 1;
  scoreElement.textContent = String(gameState.score);
  levelGoalElement.textContent = `Семена: ${gameState.locationProgress} / ${level.target} · уклоняйся от течений`;

  if (gameState.locationProgress >= level.target) {
    if (gameState.level === 5) startChallenge(6);
  }
}

function collectLeaf(item) {
  item.y = -12;
  item.x = 7 + Math.random() * 86;
  gameState.score += 1;
  scoreElement.textContent = gameState.score;
  const nextLevel = Math.min(Math.floor(gameState.score / leavesPerLevel), valikStageIndex);
  const enteredBoss = nextLevel !== gameState.level && Boolean(levels[nextLevel].boss);
  if (nextLevel !== gameState.level) {
    gameState.level = nextLevel;
    advanceLevel();
  } else {
    hint.classList.add('is-hidden');
  }
  if (gameState.bossEncounter && !gameState.bossDefeated && !enteredBoss) hitBoss();
}

function endGame(message = `Камень остановил забег. Листьев собрано: ${gameState.score}`) {
  gameState.running = false;
  cancelAnimationFrame(gameFrame);
  clearBossAttacks();
  const bestScore = Math.max(gameState.score, Number(localStorage.getItem(bestScoreKey) || 0));
  localStorage.setItem(bestScoreKey, bestScore);
  bestScoreElement.textContent = bestScore;
  hint.textContent = message;
  hint.classList.remove('is-hidden');
}

function clearBossAttacks() {
  bossAttacksLayer.replaceChildren();
  if (gameState) {
    gameState.bossAim = null;
    gameState.bossShots = [];
  }
}

function movePlayer(direction) {
  if (!gameState?.running || gameState.mode === 'maze') return;
  gameState.lastDirection = direction;
  gameState.targetX = Math.max(7, Math.min(93, (gameState.targetX || gameState.playerX) + direction * 7));
  hint.classList.add('is-hidden');
}

function dashPlayer(direction = gameState?.lastDirection || 1) {
  if (!gameState?.running || gameState.mode === 'maze' || performance.now() < gameState.nextDashAt) return;
  const now = performance.now();
  let destination = Math.max(7, Math.min(93, gameState.playerX + direction * 24));
  if (destination === gameState.playerX) destination = Math.max(7, Math.min(93, gameState.playerX - direction * 24));
  gameState.playerX = destination;
  gameState.targetX = destination;
  gameState.nextDashAt = now + dashCooldown;
  gameState.playerInvulnerableUntil = Math.max(gameState.playerInvulnerableUntil, now + dashDuration);
  player.classList.remove('is-dashing');
  void player.offsetWidth;
  player.classList.add('is-dashing');
  dashButton.disabled = true;
  dashButtonLabel.textContent = 'Перезарядка';
  hint.textContent = 'Рывок! На мгновение ты неуязвим.';
  hint.classList.remove('is-hidden');
  window.clearTimeout(dashResetTimer);
  window.setTimeout(() => player.classList.remove('is-dashing'), dashDuration);
  dashResetTimer = window.setTimeout(() => {
    dashButton.disabled = false;
    dashButtonLabel.textContent = 'Рывок';
  }, dashCooldown);

  if (gameState.mode === 'challenge' && gameState.challengeType === 'valik') {
    const gatePosition = gameState.challengeGates[gameState.challengeProgress];
    if (Math.abs(gameState.playerX - gatePosition) <= 9) {
      gameState.challengeProgress += 1;
      updateChallengeDisplay();
      if (gameState.challengeProgress >= gameState.challengeTarget) completeChallenge();
    } else {
      challengeCue.textContent = `Арка впереди ${gatePosition < gameState.playerX ? '←' : '→'} — выровняйся перед рывком`;
    }
  }
  return true;
}

function handleDirectionalInput(direction, isRepeat = false) {
  if (gameState?.mode === 'challenge') {
    if (isRepeat) return;
    if (gameState.challengeType === 'egorius') {
      inputMirror(direction);
      return;
    }
    if (gameState.challengeType === 'bogdan') {
      moveClimber(direction);
      return;
    }
    if (gameState.challengeType === 'cow') return;
  }

  const canDash = gameState?.running && gameState.mode !== 'maze';
  if (canDash && isRepeat) {
    gameState.lastDirectionalInput = null;
    movePlayer(direction);
    return;
  }

  if (canDash) {
    const now = performance.now();
    const previousInput = gameState.lastDirectionalInput;
    if (previousInput?.direction === direction && now - previousInput.time <= doubleTapWindow) {
      gameState.lastDirectionalInput = null;
      if (dashPlayer(direction)) return;
    }
    gameState.lastDirectionalInput = { direction, time: now };
  }
  movePlayer(direction);
}

document.addEventListener('keydown', (event) => {
  const key = event.key.toLowerCase();
  if (gameState?.mode === 'maze') {
    const directions = {
      arrowup: 'up', w: 'up',
      arrowdown: 'down', s: 'down',
      arrowleft: 'left', a: 'left',
      arrowright: 'right', d: 'right'
    };
    if (directions[key]) {
      event.preventDefault();
      moveInMaze(directions[key]);
    }
    return;
  }

  if (gameState?.mode === 'challenge' && gameState.challengeType === 'bogdan' && (event.key === 'ArrowUp' || key === 'w')) {
    event.preventDefault();
    climbPlatform();
    return;
  }
  if (gameState?.mode === 'challenge' && gameState.challengeType === 'cow' && event.code === 'Space') {
    event.preventDefault();
    hitCowBeat();
    return;
  }

  if (event.key === 'ArrowLeft' || key === 'a') {
    event.preventDefault();
    handleDirectionalInput(-1, event.repeat);
  }
  if (event.key === 'ArrowRight' || key === 'd') {
    event.preventDefault();
    handleDirectionalInput(1, event.repeat);
  }
  if ((event.code === 'Space' || key === ' ') && gameState?.mode === 'duel') {
    event.preventDefault();
    attackEgorius();
  }
  if (key === 'shift' && gameState?.bossEncounter) {
    event.preventDefault();
    dashPlayer();
  }
});

restartButton.addEventListener('click', startGame);
attackBossButton.addEventListener('click', attackEgorius);
dashButton.addEventListener('click', dashPlayer);
beginJourneyButton.addEventListener('click', beginJourney);
continueChapterButton.addEventListener('click', startMazeChapter);
continueCowStageButton.addEventListener('click', continueCowStage);
continueBogdanStageButton.addEventListener('click', continueBogdanStage);
playAgainButton.addEventListener('click', startOver);
soundToggleButton.addEventListener('click', toggleMusic);
challengeActionButton.addEventListener('click', () => {
  if (gameState?.challengeType === 'cow') hitCowBeat();
  else if (gameState?.challengeType === 'bogdan') climbPlatform();
});
touchButtons.forEach((button) => {
  button.addEventListener('click', () => handleDirectionalInput(Number(button.dataset.direction)));
});
mazeTouchButtons.forEach((button) => {
  button.addEventListener('click', () => moveInMaze(button.dataset.mazeDirection));
});
